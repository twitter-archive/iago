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

import org.jboss.netty.handler.codec.http.HttpResponse
import org.junit.runner.RunWith
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.integration.HttpServer
import com.twitter.util.RandomSocket

@RunWith(classOf[JUnitRunner])
class FinagleTransportSpec extends WordSpec with MustMatchers with OneInstancePerTest {

  if (!sys.props.contains("SKIP_FLAKY")) "FinagleTransport" should {

    // local http server
    val victimPort = RandomSocket.nextPort()
    HttpServer.serve(victimPort)

    val config = new ParrotServerConfig[ParrotRequest, HttpResponse] {
      victim = HostPortListVictim("localhost:" + victimPort)
    }

    "allow us to send http requests to web servers" in {
      val transport = FinagleTransportFactory(config)
      val request = new ParrotRequest
      val future = transport.sendRequest(request)
      future.get() must not be null
      HttpServer.getAndResetRequests() must be(1)
    }

    // TODO: Add an SSL HTTP server so we can catch problems there

    "cache factories" in {
      // factories are only cached if needed
      config.reuseConnections = false
      val transport = FinagleTransportFactory(config)
      val request = new ParrotRequest
      transport.sendRequest(request).get()
      transport.sendRequest(request).get()
      HttpServer.getAndResetRequests() must be(2)
    }
  }
}
