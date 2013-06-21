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

import java.util.concurrent.TimeUnit

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.thrift.ClientId
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.Promise

class ThriftTransport(config: ParrotServerConfig[_, _])
  extends ParrotTransport[ParrotRequest, Array[Byte]] {
  val thriftClientId =
    config.thriftClientId match {
      case "" => None
      case id => Some(ClientId(id))
    }

  val builder = ClientBuilder()
    .codec(ThriftClientFramedCodec(thriftClientId))
    .hostConnectionCoresize(config.hostConnectionCoresize)
    .hostConnectionIdleTime(Duration(config.hostConnectionIdleTimeInMs, TimeUnit.MILLISECONDS))
    .hostConnectionLimit(config.hostConnectionLimit)
    .hostConnectionMaxIdleTime(Duration(config.hostConnectionMaxIdleTimeInMs,
      TimeUnit.MILLISECONDS))
    .hostConnectionMaxLifeTime(Duration(config.hostConnectionMaxLifeTimeInMs,
      TimeUnit.MILLISECONDS))
    .reportTo(new OstrichStatsReceiver)

  val builder2 = {
    if (config.transportScheme == config.TransportScheme.THRIFTS)
      builder.tlsWithoutValidation()
    else builder
  }

  val builder3 = {
    config.victim.value match {
      case config.HostPortListVictim(victims) => builder2.hosts(victims)
      case config.ServerSetVictim(cluster)    => builder2.cluster(cluster)
    }
  }

  val service = builder3.build()

  override protected[server] def sendRequest(request: ParrotRequest): Future[Array[Byte]] = {
    val result = service(request.message)
    val response = request.response.asInstanceOf[Promise[Array[Byte]]]
    result proxyTo response
    result
  }

  override def createService(config: ParrotServerConfig[ParrotRequest, Array[Byte]]) =
    new ParrotThriftService(config)

  override def shutdown() {
    service.close()
  }
}
