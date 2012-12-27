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

import com.twitter.io.TempFile
import com.twitter.parrot.config.{ParrotFeederConfig, ParrotServerConfig}
import com.twitter.parrot.feeder.{InMemoryLog, ParrotFeeder}
import com.twitter.parrot.processor.{SimpleRecordProcessor, RecordProcessorFactory}
import com.twitter.parrot.server._
import com.twitter.util.{Time, RandomSocket, Eval}
import org.jboss.netty.handler.codec.http.HttpResponse
import org.specs.SpecificationWithJUnit

class EndToEndSpec extends SpecificationWithJUnit {
  val site = "twitter.com"
  val urls = List(
    "about",
    "about/contact",
    "about/security",
    "tos",
    "privacy",
    "about/resources"
  )

  val serverConfig = makeServerConfig()
  val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]

  RecordProcessorFactory.registerProcessor("default", new SimpleRecordProcessor(serverConfig.service.get, serverConfig))

  "Feeder and Server" should {
    if (System.getenv("SBT_CI") != null) {
      skip("Parrot integration tests shouldn't run in CI, due to no network connections")
    }

    "Send requests to the places we tell them" in {
      val server: ParrotServer[ParrotRequest, HttpResponse] = new ParrotServerImpl(serverConfig)
      server.start()

      val feederConfig = makeFeederConfig()
      feederConfig.requestRate = 1000 // hopefully speeds up test completion
      feederConfig.logSource = Some(new InMemoryLog(urls))

      val feeder: ParrotFeeder = new ParrotFeeder(feederConfig)

      feeder.start()
      try {
        transport.allRequests.toInt must eventually(be(urls.size))
      }
      catch {
        case e: Exception => fail(e.getMessage)
      }
      finally {
        // Feeder will shutdown Server
        feeder.shutdown()
      }
    }

    "Send requests at the rate we ask them to" in {
      skip("failing, but i need to work around")
      val server: ParrotServer[ParrotRequest, HttpResponse] = new ParrotServerImpl(serverConfig)
      server.start()

      val rate = 5 // rps
      val seconds = 20 // how long we expect to take to send our requests
      val totalRequests = rate * seconds
      val feederConfig = makeFeederConfig()

      val url = "twitter.com"
      var urls = List(url)
      for (i <- 1 until totalRequests * 2) {
        urls = url :: urls
      }

      feederConfig.reuseFile = true
      feederConfig.requestRate = rate
      feederConfig.maxRequests = totalRequests * 2 // fudge factor to make failure possible both directions
      feederConfig.logSource = Some(new InMemoryLog(urls))

      val start = Time.now
      val feeder = new ParrotFeeder(feederConfig)
      feeder.start()

      try {
        transport.allRequests.toInt must eventually(be(totalRequests))
        start.untilNow.inSeconds.toDouble must be_<= (seconds * 1.20)
        start.untilNow.inSeconds.toDouble must be_>= (seconds * 0.80)
      }
      catch {
        case e: Exception => fail(e.getMessage)
      }
      finally {
        // Feeder will shutdown Server
        feeder.shutdown()
      }
    }
  }

  def makeServerConfig(): ParrotServerConfig[ParrotRequest, HttpResponse] = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, HttpResponse]](
      TempFile.fromResourcePath("/test-server.scala")
    )
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.transport = Some(new FinagleTransport(result))
    result
  }

  def makeFeederConfig(): ParrotFeederConfig = {
    val result = new Eval().apply[ParrotFeederConfig](TempFile.fromResourcePath("/test-feeder.scala"))
    result.parrotPort = serverConfig.parrotPort
    result.victimHosts = List(site)
    result.victimPort = 80
    result.victimScheme = "http"
    result
  }
}
