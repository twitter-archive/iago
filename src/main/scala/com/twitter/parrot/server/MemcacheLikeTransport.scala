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
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finagle.{ CodecFactory, Service }
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.{ Duration, Promise, Future }
import java.util.concurrent.TimeUnit
import com.twitter.util.Time
import com.twitter.util.Await

trait MemcacheLikeCommandExtractor[T] {
  def unapply(rawCommand: String): Option[T]
}

abstract class MemcacheLikeTransportFactory[Req, Rep] extends ParrotTransportFactory[ParrotRequest, Rep]
{

  protected def codec(): CodecFactory[Req, Rep]

  protected def fromService(service: Service[Req, Rep]): MemcacheLikeTransport[Req, Rep]

  def apply(config: ParrotServerConfig[ParrotRequest, Rep]) = {
    val statsReceiver = new OstrichStatsReceiver

    val builder = ClientBuilder()
      .codec(codec)
      .daemon(true)
      .hostConnectionCoresize(config.hostConnectionCoresize)
      .hostConnectionIdleTime(Duration(config.hostConnectionIdleTimeInMs, TimeUnit.MILLISECONDS))
      .hostConnectionLimit(config.hostConnectionLimit)
      .hostConnectionMaxIdleTime(Duration(config.hostConnectionMaxIdleTimeInMs,
        TimeUnit.MILLISECONDS))
      .hostConnectionMaxLifeTime(Duration(config.hostConnectionMaxLifeTimeInMs,
        TimeUnit.MILLISECONDS))
      .requestTimeout(Duration(config.requestTimeoutInMs, TimeUnit.MILLISECONDS))
      .tcpConnectTimeout(Duration(config.tcpConnectTimeoutInMs, TimeUnit.MILLISECONDS))
      .keepAlive(true)
      .reportTo(statsReceiver)
      .tracer(ZipkinTracer.mk(statsReceiver))

    val builder2 = {
      config.victim.value match {
        case config.HostPortListVictim(victims) => builder.hosts(victims)
        case config.ServerSetVictim(cluster)    => builder.cluster(cluster)
      }
    }

    fromService(new RefcountedService(builder2.build))
  }
}

class MemcacheLikeTransport[Req, Rep](
  commandExtractor: MemcacheLikeCommandExtractor[Req],
  service: Service[Req, Rep])
  extends ParrotTransport[ParrotRequest, Rep] {

  override protected[server] def sendRequest(request: ParrotRequest): Future[Rep] = {

    val command = request.rawLine match {
      case commandExtractor(command) => command
      case _ =>
        throw new IllegalArgumentException("could not parse command {%s}".format(request.rawLine))
    }

    log.debug("sending request: %s", command)

    try {
      val result = service(command)
      val response = request.response.asInstanceOf[Promise[Rep]]
      result proxyTo response
      result
    } catch {
      case e: Throwable =>
        log.error(e, "error executing request %s", command)
        throw e
    }
  }

  override def close(deadline: Time): Future[Unit] = service.close(deadline)
}
