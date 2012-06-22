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
package com.twitter.parrot.integration

import org.apache.thrift.protocol.TBinaryProtocol
import java.net.InetSocketAddress
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.util.Future
import com.twitter.logging.Logger
import com.twitter.parrot.thrift.EchoService
import java.util.concurrent.atomic.AtomicInteger

object EchoServer {
  val log = Logger.get(getClass)
  val requestCount = new AtomicInteger(0)

  def serve(port: Int) {
    // Implement the Thrift Interface
    val processor = new EchoService.ServiceIface {
      def echo(message: String) = {
        log.info("echoing message: %s", message)
        requestCount.incrementAndGet
        Future.value(message)
      }
    }

    // Convert the Thrift Processor to a Finagle Service
    val service = new EchoService.Service(processor, new TBinaryProtocol.Factory())

    val server: Server = ServerBuilder()
      .bindTo(new InetSocketAddress(port))
      .codec(ThriftServerFramedCodec())
      .name("thriftserver")
      .build(service)
  }

  def getRequestCount = requestCount.get
}
