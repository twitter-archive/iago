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
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.{ServiceFactory, Service}
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.TargetHost
import com.twitter.util.{Promise, Duration, Future}
import java.nio.ByteOrder.BIG_ENDIAN
import java.util.concurrent.TimeUnit
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.util.CharsetUtil.UTF_8

class FinagleTransport(config: ParrotServerConfig[ParrotRequest, HttpResponse])
  extends ParrotTransport[ParrotRequest, HttpResponse]
{
  val clients = new mutable.HashMap[String, Service[HttpRequest, HttpResponse]]()
  val factories = new mutable.HashMap[String, ServiceFactory[HttpRequest, HttpResponse]]()

  val builder = ClientBuilder()
    .codec(Http())
    .hostConnectionCoresize(config.hostConnectionCoresize)
    .hostConnectionIdleTime(Duration(config.hostConnectionIdleTimeInMs, TimeUnit.MILLISECONDS))
    .hostConnectionLimit(config.hostConnectionLimit)
    .hostConnectionMaxIdleTime(Duration(config.hostConnectionMaxIdleTimeInMs, TimeUnit.MILLISECONDS))
    .hostConnectionMaxLifeTime(Duration(config.hostConnectionMaxLifeTimeInMs, TimeUnit.MILLISECONDS))
    .keepAlive(true)
    .reportTo(new OstrichStatsReceiver)

  var allRequests = 0

  override def stats(response: HttpResponse) = Seq(response.getStatus.getCode.toString)

  override protected[server] def sendRequest(request: ParrotRequest): Future[HttpResponse] = {
    val client = getClientForHost(request.target)
    val requestMethod = request.method match {
      case "POST" => HttpMethod.POST
      case _ => HttpMethod.GET
    }
    val httpRequest =
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, requestMethod, request.uri.toString)
    request.headers foreach { case (key, value) =>
      httpRequest.setHeader(key, value)
    }
    httpRequest.setHeader("Cookie", request.cookies map { case (name, value) => name + "=" + value } mkString(";"))
    httpRequest.setHeader("User-Agent", "com.twitter.parrot")
    httpRequest.setHeader("X-Parrot", "true")
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
      .format(httpRequest.toString)
    )

    client flatMap { service: Service[HttpRequest, HttpResponse] =>
      val result = service.apply(httpRequest)
      val response = request.response.asInstanceOf[Promise[HttpResponse]]
      result proxyTo response
      result
    }
  }

  private[this] def getClientForHost(target: TargetHost): Future[Service[HttpRequest, HttpResponse]] = {
    import target.{scheme, host, port}
    val key = host + ":" + port
    var localBuilder = {
      if (scheme == "https")
        builder.tlsWithoutValidation()
      else
        builder
    }

    if (config.reuseConnections) {
      val service = clients.getOrElseUpdate(key, localBuilder.hosts(key).build())
      Future.value(service)
    }
    else {
      val factory = factories.getOrElseUpdate(key, localBuilder.hosts(key).buildFactory())
      factory()
    }
  }

  override def createService(config: ParrotServerConfig[ParrotRequest, HttpResponse]) = new ParrotHttpService(config)

  override def shutdown() {
    clients.values.foreach { _.release() }
  }
}
