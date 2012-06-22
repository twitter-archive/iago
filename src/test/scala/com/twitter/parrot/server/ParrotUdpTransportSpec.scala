/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.parrot.server

import com.twitter.conversions.time._
import com.twitter.finagle.RequestTimeoutException
import com.twitter.io.TempFile
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.config.{ParrotFeederConfig, ParrotServerConfig}
import com.twitter.parrot.feeder.{InMemoryLog, ParrotFeeder}
import com.twitter.parrot.processor.{RecordProcessor, RecordProcessorFactory}
import com.twitter.parrot.thrift.ParrotJob
import com.twitter.parrot.thrift.TargetHost
import com.twitter.util.Try
import com.twitter.util.{RandomSocket, Eval}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory
import org.jboss.netty.handler.codec.string.{StringDecoder, StringEncoder}
import org.jboss.netty.util.CharsetUtil
import org.specs.SpecificationWithJUnit

class ParrotUdpTransportSpec extends SpecificationWithJUnit {
  "Parrot UDP Transport" should {
    "work inside a server config" in {
      val serverConfig = makeServerConfig()
      val server: ParrotServer[ParrotRequest, String] = new ParrotServerImpl(serverConfig)
      server mustNotBe null
    }

    "send requests to an 'echo' service" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig()
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val victim = new UdpEchoServer(victimPort)
      victim.start()

      val script = List("abc", "is as easy as", "123")
      var targetHost = new TargetHost("", "127.0.0.1", victimPort)
      script.foreach { request =>
        val parrotRequest = new ParrotRequest(targetHost, rawLine = request)
        val response: String = transport.sendRequest(parrotRequest).get()

        response mustEqual "echo<" + request + ">"
      }

      transport.shutdown()
      victim.stop()
    }

    "timeout if the service does not respond quickly enough" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig()
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))
      transport.asInstanceOf[ParrotUdpTransport[ParrotRequest, String]].requestTimeout = Some(100.milliseconds)

      val victim = new UdpEchoServer(victimPort, true)
      victim.start()

      var targetHost = new TargetHost("", "localhost", victimPort)
      val parrotRequest = new ParrotRequest(targetHost, rawLine = "data")

      Stats.getCounter("udp_request_timeout").reset()

      val result: Try[String] = transport.sendRequest(parrotRequest).get(1.minute)
      result() must throwA[RequestTimeoutException]
      Stats.getCounter("udp_request_timeout")() must eventually(be(1L))

      transport.shutdown()
      victim.stop()
    }

    "work in the context of feeder and server" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig()

      RecordProcessorFactory.registerProcessor("default", new RecordProcessor {
        val service = serverConfig.service.get
        def processLines(job: ParrotJob, lines: Seq[String]) {
          lines flatMap { line =>
            val target = job.victims.get(0)
            Some(service(new ParrotRequest(target, None, Nil, null, line)))
          }
        }
      })

      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val victim = new UdpEchoServer(victimPort)
      victim.start()

      val requestStrings = List("a", "square peg", "cannot fit into a round hole")

      val server: ParrotServer[ParrotRequest, String] = new ParrotServerImpl(serverConfig)
      server.start()

      val feederConfig = makeFeederConfig(serverConfig, victimPort)
      feederConfig.logSource = Some(new InMemoryLog(requestStrings))

      val feeder = new ParrotFeeder(feederConfig)
      feeder.start()

      try {
        { transport.asInstanceOf[ParrotUdpTransport[ParrotRequest, String]].allRequests.get } must
          eventually(be(requestStrings.size))
      } finally {
        feeder.shutdown()
        server.shutdown()
      }
    }
  }

  def makeServerConfig() = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, String]](
      TempFile.fromResourcePath("/test-udp.scala")
    )
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl) // ParrotServer throws otherwise
    result
  }

  def makeFeederConfig(serverConfig: ParrotServerConfig[ParrotRequest, String], victimPort: Int) = {
    val result = new Eval().apply[ParrotFeederConfig](TempFile.fromResourcePath("/test-feeder.scala"))
    result.parrotPort = serverConfig.parrotPort
    result.victimHosts = List("localhost")
    result.victimPort = victimPort
    result.requestRate = 1000
    result
  }
}

class UdpEchoHandler(val blackHole: Boolean) extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val input = e.getMessage.asInstanceOf[String]
    if (!blackHole) {
      // NIO: this will fail with NioDatagramChannelFactory (see NIO below)
      val writeFuture = e.getChannel.write("echo<" + input + ">", e.getRemoteAddress)
      writeFuture.addListener(new ChannelFutureListener {
        def operationComplete(f: ChannelFuture) {
          if (!f.isSuccess) {
            println("response write failed: " + f.getCause.toString)
            f.getCause.printStackTrace
          }
        }
      })
    }
  }
}

class UdpEchoServer(val port: Int, val blackHole: Boolean = false) {
  // NIO: Netty 3.4.0.Alpha1 NioDatagramChannelFactory seems broken (see NIO above)
  val factory = new OioDatagramChannelFactory(Executors.newCachedThreadPool());

  @volatile var channel: Option[Channel] = None

  def start() {
    val bootstrap = new ConnectionlessBootstrap(factory);

    // Configure the pipeline factory.
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      override def getPipeline(): ChannelPipeline = {
        Channels.pipeline(
          new StringEncoder(CharsetUtil.UTF_8),
          new StringDecoder(CharsetUtil.UTF_8),
          new UdpEchoHandler(blackHole))
      }
    });

    channel = Some(bootstrap.bind(new InetSocketAddress(port)))
  }

  def stop() {
    channel.foreach { _.close() }
    factory.releaseExternalResources()
  }
}
