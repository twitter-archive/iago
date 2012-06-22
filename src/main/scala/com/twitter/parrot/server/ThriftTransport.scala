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
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.thrift.{ClientId, ThriftClientRequest, ThriftClientFramedCodec}
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.{Promise, Duration, Future}
import java.util.concurrent.TimeUnit

class ThriftTransport(config: Option[ParrotServerConfig[_, _]] = None) extends ParrotTransport[ParrotRequest, Array[Byte]] {
  val clients = new mutable.HashMap[String, Service[ThriftClientRequest, Array[Byte]]]()
  val thriftClientId = config flatMap {
    _.thriftClientId match {
      case "" => None
      case id => Some(ClientId(id))
    }
  }
  val builder = ClientBuilder()
    .codec(ThriftClientFramedCodec(thriftClientId))
    .hostConnectionCoresize(config.get.hostConnectionCoresize)
    .hostConnectionIdleTime(Duration(config.get.hostConnectionIdleTimeInMs, TimeUnit.MILLISECONDS))
    .hostConnectionLimit(config.get.hostConnectionLimit)
    .hostConnectionMaxIdleTime(Duration(config.get.hostConnectionMaxIdleTimeInMs, TimeUnit.MILLISECONDS))
    .hostConnectionMaxLifeTime(Duration(config.get.hostConnectionMaxLifeTimeInMs, TimeUnit.MILLISECONDS))
    .reportTo(new OstrichStatsReceiver)

  override protected[server] def sendRequest(request: ParrotRequest): Future[Array[Byte]] = {
    val key = request.target.host + ":" + request.target.port
    val service = clients.getOrElseUpdate(key, builder.hosts(key).build())

    val result = service(request.message)
    val response = request.response.asInstanceOf[Promise[Array[Byte]]]
    result proxyTo response
    result
  }

  override def createService(config: ParrotServerConfig[ParrotRequest, Array[Byte]]) =
    new ParrotThriftService(config)

  override def shutdown() {
    clients.values.foreach { _.release() }
  }
}
