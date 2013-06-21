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

import org.jboss.netty.handler.codec.http.HttpResponse
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.feeder.InMemoryLog
import com.twitter.parrot.feeder.ParrotFeeder
import com.twitter.parrot.server.FinagleTransport
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotServer
import com.twitter.parrot.server.ParrotServerImpl
import com.twitter.parrot.server.ThriftServerImpl
import com.twitter.util.Eval
import com.twitter.util.RandomSocket

@RunWith(classOf[JUnitRunner])
class EndToEndSpec extends WordSpec with MustMatchers with Eventually {
  private val log = Logger.get(getClass.getName)
  val site = "twitter.com"
  val urls = List(
    "about",
    "about/contact",
    "about/security",
    "tos",
    "privacy",
    "about/resources")

  val serverConfig = makeServerConfig()

  // Parrot integration tests shouldn't run in CI, due to no network connections

  if (System.getenv.get("SBT_CI") == null && System.getProperty("SBT_CI") == null) "Feeder and Server" should {

    "Send requests to the places we tell them" in {
      val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
      val server: ParrotServer[ParrotRequest, HttpResponse] = new ParrotServerImpl(serverConfig)
      server.start()

      val feederConfig = makeFeederConfig()
      feederConfig.requestRate = 1000 // hopefully speeds up test completion
      feederConfig.logSource = Some(new InMemoryLog(urls))

      val feeder: ParrotFeeder = new ParrotFeeder(feederConfig)

      feeder.start()
      try {
        eventually(timeout(Span(2, Seconds))) {
          transport.allRequests.toInt must be(urls.size)
        }
        assert(serverConfig.loadTestInstance.get.asInstanceOf[TestRecordProcessor].responded)
      } finally {
        // The Feeder will shut down the Server for us
        feeder.shutdown()
      }
      eventually(timeout(Span(1, Seconds))) {
        assert(serverConfig.loadTestInstance.get.asInstanceOf[TestRecordProcessor].properlyShutDown)
      }
    }

    /*
     * This test is failing. Since WordSpec lacks "skip", we comment it out. It should be fixed
     * some day but has been broken for several months.

    "Send requests at the rate we ask them to" in {
      val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
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
        eventually(timeout(Span(2, Seconds))) {
          transport.allRequests.toInt must be(totalRequests)
        }
        val untilNow = start.untilNow.inSeconds.toDouble
        untilNow must be <= (seconds * 1.20)
        untilNow must be >= (seconds * 0.80)
      } finally {
        // The Feeder will shut down the Server for us
        feeder.shutdown()
        //transport.shutdown
      }
    }

    */
  }

  def makeServerConfig(): ParrotServerConfig[ParrotRequest, HttpResponse] = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, HttpResponse]](
      TempFile.fromResourcePath("/test-server.scala"))
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.transport = Some(new FinagleTransport(result))
    result.loadTestInstance = Some(new TestRecordProcessor(result.service.get, result))
    result
  }

  def makeFeederConfig(): ParrotFeederConfig = {
    val result = new Eval().apply[ParrotFeederConfig](TempFile.fromResourcePath("/test-feeder.scala"))
    result.parrotPort = serverConfig.parrotPort
    result
  }
}
