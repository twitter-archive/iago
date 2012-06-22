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
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.BackgroundProcess
import com.twitter.parrot.thrift.{ParrotState, ParrotStatus, ParrotServerService}
import java.net.InetSocketAddress
import java.util.logging.{Logger => JLogger}
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.server.TThreadPoolServer
import org.apache.thrift.transport.TServerSocket

trait ThriftServer {
  def start(server: ParrotServer[_, _], port: Int)
  def shutdown()
}

class ThriftServerImpl extends ThriftServer {
  private[this] val log = Logger.get(getClass.getName)
  val unknownStatus = new ParrotStatus().setStatus(ParrotState.UNKNOWN).setLinesProcessed(0)

  var service: ParrotServerService.Service = null
  var server: Server = null

  def start(parrotServer: ParrotServer[_, _], port: Int) {
    try {
      service = new ParrotServerService.Service(parrotServer, new TBinaryProtocol.Factory())
      server = ServerBuilder()
        .bindTo(new InetSocketAddress(port))
        .codec(ThriftServerFramedCodec())
        .name("finagle thrift server")
// Enable only for debugging purposes.
//        .logger(JLogger.getLogger("com.twitter.finagle"))
//        .reportTo(new OstrichStatsReceiver)
        .build(service)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        log.error(e, "Unexpected exception: %s", e.getMessage)
    }
  }

  override def shutdown() {
    if (server != null) {
      server.close(1.second)
    }
  }
}
