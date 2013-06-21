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

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory
import org.jboss.netty.handler.codec.string.StringDecoder
import org.jboss.netty.handler.codec.string.StringEncoder
import org.jboss.netty.util.CharsetUtil
import org.junit.runner.RunWith
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.RequestTimeoutException
import com.twitter.io.TempFile
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.feeder.InMemoryLog
import com.twitter.parrot.feeder.ParrotFeeder
import com.twitter.parrot.processor.RecordProcessor
import com.twitter.util.Eval
import com.twitter.util.RandomSocket
import com.twitter.util.Try

@RunWith(classOf[JUnitRunner])
class ParrotUdpTransportSpec extends WordSpec with MustMatchers with OneInstancePerTest with Eventually {

  val victimPort = RandomSocket.nextPort()
  val serverConfig = makeServerConfig(victimPort)

  "Parrot UDP Transport" should {
    "work inside a server config" in {
      val server: ParrotServer[ParrotRequest, String] = new ParrotServerImpl(serverConfig)
      server must not be null
    }

    "send requests to an 'echo' service" in {
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val victim = new UdpEchoServer(victimPort)
      victim.start()

      val script = List("abc", "is as easy as", "123")
      script.foreach { request =>
        val parrotRequest = new ParrotRequest(rawLine = request)
        val response: String = transport.sendRequest(parrotRequest).get()

        response must be("echo<" + request + ">")
      }

      transport.shutdown()
      victim.stop()
    }

    "timeout if the service does not respond quickly enough" in {
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))
      transport.asInstanceOf[ParrotUdpTransport[String]].requestTimeout = Some(100.milliseconds)

      val victim = new UdpEchoServer(victimPort, true)
      victim.start()

      val parrotRequest = new ParrotRequest(rawLine = "data")

      Stats.getCounter("udp_request_timeout").reset()

      val result: Try[String] = transport.sendRequest(parrotRequest).get(1.minute)
      evaluating { result() } must produce[RequestTimeoutException]
      eventually {
        Stats.getCounter("udp_request_timeout")() must be(1L)
      }

      transport.shutdown()
      victim.stop()
    }

    "work in the context of feeder and server" in {

      serverConfig.loadTestInstance = Some(new RecordProcessor {
        val service = serverConfig.service.get
        def processLines(lines: Seq[String]) {
          lines flatMap { line =>
            Some(service(new ParrotRequest(None, Nil, null, line)))
          }
        }
      })

      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val victim = new UdpEchoServer(victimPort)
      victim.start()

      val requestStrings = List("a", "square peg", "cannot fit into a round hole")

      val server: ParrotServer[ParrotRequest, String] = new ParrotServerImpl(serverConfig)
      server.start()

      val feederConfig = makeFeederConfig(serverConfig)
      feederConfig.logSource = Some(new InMemoryLog(requestStrings))

      val feeder = new ParrotFeeder(feederConfig)
      feeder.start()

      try {
        {
          eventually(timeout(Span(5, Seconds)), interval(Span(500, Millis))) {
            transport.asInstanceOf[ParrotUdpTransport[String]].allRequests.get must
              be(requestStrings.size)
          }
        }
      } finally {
        // The Feeder will shut down the Server for us
        feeder.shutdown()
      }
    }
  }

  def makeServerConfig(victimPort: Int) = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, String]](
      TempFile.fromResourcePath("/test-udp.scala"))
    result.victim = result.HostPortListVictim("localhost:" + victimPort)
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl) // ParrotServer throws otherwise
    result.transport = Some(new ParrotUdpTransport[String](result) {
      val requestEncoder = Some(new StringEncoder(CharsetUtil.UTF_8))
      val responseDecoder = Some(new StringDecoder(CharsetUtil.UTF_8))
    })
    result.queue = Some(new RequestQueue(result))
    result
  }

  def makeFeederConfig(serverConfig: ParrotServerConfig[ParrotRequest, String]) = {
    val result = new Eval().apply[ParrotFeederConfig](TempFile.fromResourcePath("/test-feeder.scala"))
    result.parrotPort = serverConfig.parrotPort
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
