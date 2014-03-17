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

import collection.mutable
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.{ ServiceFactory, Service }
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.{ Promise, Duration, Future }
import java.nio.ByteOrder.BIG_ENDIAN
import java.util.concurrent.TimeUnit
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util.CharsetUtil.UTF_8
import com.twitter.util._

object FinagleTransportFactory extends ParrotTransportFactory[ParrotRequest, HttpResponse] {
  def apply(config: ParrotServerConfig[ParrotRequest, HttpResponse]) = {
    val statsReceiver = new OstrichStatsReceiver

    val builder = ClientBuilder()
      .codec(Http())
      .daemon(true)
      .hostConnectionCoresize(config.hostConnectionCoresize)
      .hostConnectionIdleTime(Duration(config.hostConnectionIdleTimeInMs, TimeUnit.MILLISECONDS))
      .hostConnectionLimit(config.hostConnectionLimit)
      .hostConnectionMaxIdleTime(Duration(config.hostConnectionMaxIdleTimeInMs, TimeUnit.MILLISECONDS))
      .hostConnectionMaxLifeTime(Duration(config.hostConnectionMaxLifeTimeInMs, TimeUnit.MILLISECONDS))
      .requestTimeout(Duration(config.requestTimeoutInMs, TimeUnit.MILLISECONDS))
      .tcpConnectTimeout(Duration(config.tcpConnectTimeoutInMs, TimeUnit.MILLISECONDS))
      .keepAlive(true)
      .reportTo(statsReceiver)
      .tracer(ZipkinTracer.mk(statsReceiver))

    val builder2 = {
      if (config.transportScheme == config.TransportScheme.HTTPS)
        builder.tlsWithoutValidation()
      else builder
    }

    val builder3 = config.victim.value match {
      case config.HostPortListVictim(victims) => builder2.hosts(victims)
      case config.ServerSetVictim(cluster)    => builder2.cluster(cluster)
    }

    val service =
      if (config.reuseConnections)
        FinagleService(new RefcountedService(builder3.build()))
      else
        FinagleServiceFactory(builder3.buildFactory())

    new FinagleTransport(service, config.includeParrotHeader)
  }
}


class FinagleTransport(service: FinagleServiceAbstraction, includeParrotHeader: Boolean)
  extends ParrotTransport[ParrotRequest, HttpResponse] {

  var allRequests = 0
  override def stats(response: HttpResponse) = Seq(response.getStatus.getCode.toString)

  override protected[server] def sendRequest(request: ParrotRequest): Future[HttpResponse] = {
    val requestMethod = request.method match {
      case "POST" => HttpMethod.POST
      case _      => HttpMethod.GET
    }
    val httpRequest =
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, requestMethod, request.uri.toString)
    request.headers foreach {
      case (key, value) =>
        httpRequest.setHeader(key, value)
    }
    httpRequest.setHeader("Cookie", request.cookies map {
      case (name, value) => name + "=" + value
    } mkString (";"))
    httpRequest.setHeader("User-Agent", "com.twitter.parrot")
    if (includeParrotHeader) {
      httpRequest.setHeader("X-Parrot", "true")
    }
    httpRequest.setHeader("X-Forwarded-For", randomIp)

    if (request.method == "POST" && request.body.length > 0) {
      val buffer = ChannelBuffers.copiedBuffer(BIG_ENDIAN, request.body, UTF_8)
      httpRequest.setHeader(CONTENT_LENGTH, buffer.readableBytes)
      httpRequest.setContent(buffer)
    }

    allRequests += 1

    log.debug(
      """
===================== HttpRequest ======================
%s
========================================================"""
        .format(httpRequest.toString))

    service.send(httpRequest, request)
  }

  override def close(deadline: Time): Future[Unit] = service.close(deadline)
}
