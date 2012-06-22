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

import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.TargetHost
import com.twitter.parrot.integration.HttpServer
import com.twitter.util.RandomSocket
import org.jboss.netty.handler.codec.http.HttpResponse
import org.specs.SpecificationWithJUnit

class FinagleTransportSpec extends SpecificationWithJUnit {
  "FinagleTransport" should {

    val config = new ParrotServerConfig[ParrotRequest, HttpResponse] { }

    // local http server
    val victimPort = RandomSocket.nextPort()
    HttpServer.serve(victimPort)

    "allow us to send http requests to web servers" in {
      val transport = new FinagleTransport(config)
      val target = new TargetHost("http", "localhost", victimPort)
      val request = new ParrotRequest(target)
      val future = transport.sendRequest(request)
      future.get() must notBeNull
      HttpServer.getAndResetRequests() must_== 1
    }

    // TODO: Add an SSL HTTP server so we can catch problems there

    "cache factories" in {
      // factories are only cached if needed
      config.reuseConnections = false
      val transport = new FinagleTransport(config)
      val target = new TargetHost("http", "localhost", victimPort)
      val request = new ParrotRequest(target)
      transport.sendRequest(request).get()
      transport.sendRequest(request).get()
      transport.factories.size must_== 1
      HttpServer.getAndResetRequests() must_== 2
    }
  }
}
