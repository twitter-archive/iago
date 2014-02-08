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
import com.twitter.finagle.util.{ TimerFromNettyTimer => FinagleTimer }
import com.twitter.finagle.{ ChannelException, RequestTimeoutException }
import com.twitter.ostrich.stats.Stats
import com.twitter.util._
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ ConcurrentHashMap, Executors, TimeUnit }
import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.group.{ ChannelGroup, DefaultChannelGroup }
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory
import org.jboss.netty.util.HashedWheelTimer
import scala.collection.JavaConverters._

abstract class ParrotUdpTransport[Rep](victim: InetSocketAddress) extends ParrotTransport[ParrotRequest, Rep] {

  def requestEncoder: Option[ChannelDownstreamHandler]
  def responseDecoder: Option[ChannelUpstreamHandler] // N.B.: must decode to objects of type Rep

  var requestTimeout: Option[Duration] = None
  val connectTimeout = 10.seconds

  val allRequests = new AtomicInteger(0)

  val clientHandler = new SimpleChannelUpstreamHandler {
    private[this] def reply(message: Try[Rep], channel: Channel) {
      val future = Option(channelFutureMap.get(channel.getId))
      future match {
        case Some(f) => f() = message
        case None    => ()
      }
      channel.close()
    }

    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      reply(Return(e.getMessage.asInstanceOf[Rep]), e.getChannel)
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
      reply(Throw(ChannelException(e.getCause, e.getChannel.getRemoteAddress)), e.getChannel)
  }

  lazy val udpChannelFactory = new NioDatagramChannelFactory(Executors.newCachedThreadPool())
  lazy val bootstrap = {
    val b = new ConnectionlessBootstrap(udpChannelFactory)
    b.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline(): ChannelPipeline = {
        (requestEncoder, responseDecoder) match {
          case (Some(enc), Some(dec)) => Channels.pipeline(enc, dec, clientHandler)
          case (Some(enc), None)      => Channels.pipeline(enc, clientHandler)
          case (None, Some(dec))      => Channels.pipeline(dec, clientHandler)
          case (None, None)           => Channels.pipeline(clientHandler)
        }
      }
    })
    b
  }

  val channelFutureMap = new ConcurrentHashMap[java.lang.Integer, Promise[Rep]]()
  val channelGroup = new DefaultChannelGroup

  val timer = new FinagleTimer(new HashedWheelTimer(100, TimeUnit.MILLISECONDS))

  override protected[server] def sendRequest(request: ParrotRequest): Future[Rep] = {

    val data = request.rawLine

    log.debug("sending request: %s to %s", data, victim.toString)
    allRequests.incrementAndGet()

    val connFuture = bootstrap.connect(victim)
    if (!connFuture.awaitUninterruptibly(connectTimeout.inMilliseconds)) {
      return Future.exception[Rep](new RequestTimeoutException(connectTimeout, "connecting"))
    }

    val channel = connFuture.getChannel

    val future = new Promise[Rep]
    channelFutureMap.put(channel.getId, future)

    val start = Time.now
    channel.write(data)

    val channelCloseFuture = channel.getCloseFuture()
    channelCloseFuture.addListener(new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture) {
        channelFutureMap.remove(future.getChannel.getId)
      }
    })

    val timerTask = requestTimeout.map { timeout =>
      timer.schedule(start + timeout) {
        future.updateIfEmpty(
          Throw[Rep](new RequestTimeoutException(timeout, "waiting for UDP response")))
        channel.close()
        Stats.incr("udp_request_timeout")
      }
    }

    future onSuccess { reply =>
      timerTask.foreach { _.cancel() }
      val usec = (Time.now - start).inMicroseconds.toInt max 0
      Stats.addMetric("udp_request_latency_usec", usec)
    }
  }

  override def shutdown() {
    channelFutureMap.values.asScala.foreach { _.raise(new FutureCancelledException) }
    channelGroup.disconnect().awaitUninterruptibly(5000)
    channelGroup.close().awaitUninterruptibly(5000)
    timer.stop()
    udpChannelFactory.releaseExternalResources()
  }
}
