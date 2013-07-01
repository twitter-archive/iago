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
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.io.TempFile
import com.twitter.logging.Level
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.feeder.InMemoryLog
import com.twitter.parrot.feeder.ParrotFeeder
import com.twitter.parrot.server.FinagleTransport
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotServerImpl
import com.twitter.parrot.server.ThriftServerImpl
import com.twitter.parrot.util.ConsoleHandler
import com.twitter.parrot.util.PrettyDuration
import com.twitter.util.Await
import com.twitter.util.Duration
import com.twitter.util.Eval
import com.twitter.util.Future
import com.twitter.util.RandomSocket
import com.twitter.util.Stopwatch
import com.twitter.util.Time
import com.twitter.util.TimeoutException

@RunWith(classOf[JUnitRunner])
class EndToEndSpec extends WordSpec with MustMatchers {
  private[this] val log = Logger.get(getClass.getName)
  private[this] val site = "twitter.com"
  private[this] val urls = List(
    "about",
    "about/contact",
    "about/security",
    "tos",
    "privacy",
    "about/resources")

  // Parrot integration tests shouldn't run in CI, due to no network connections

  if (System.getenv.get("SBT_CI") == null && System.getProperty("SBT_CI") == null)
    "Feeder and Server" should {

      "Send requests to the places we tell them" in {
        val serverConfig = makeServerConfig(true)
        val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
        val server: ParrotServerImpl[ParrotRequest, HttpResponse] =
          new ParrotServerImpl(serverConfig)
        server.start()
        val feederConfig = makeFeederConfig(serverConfig.parrotPort)
        feederConfig.requestRate = 1000
        feederConfig.logSource = Some(new InMemoryLog(urls))
        val feeder: ParrotFeeder = new ParrotFeeder(feederConfig)
        feeder.start() // shuts down when it reaches the end of the log
        waitForServer(server.done, feederConfig.cutoff)
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        requestsRead must be(urls.size)
        allRequests must be(requestsRead)
        rp.responded must be(requestsRead)
        assert(rp.properlyShutDown)
      }

      "Send requests at the rate we ask them to" in {
        val serverConfig = makeServerConfig(false)
        val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
        val server: ParrotServerImpl[ParrotRequest, HttpResponse] =
          new ParrotServerImpl(serverConfig)
        server.start()

        val rate = 5 // rps ... requests per second
        val seconds = 20 // how long we expect to take to send our requests
        val totalRequests = (rate * seconds).toInt
        val feederConfig = makeFeederConfig(serverConfig.parrotPort)

        feederConfig.reuseFile = true
        feederConfig.requestRate = rate
        feederConfig.maxRequests = totalRequests
        feederConfig.logSource = memoryLog(totalRequests)
        feederConfig.batchSize = 3

        val feeder = new ParrotFeeder(feederConfig)
        ConsoleHandler.start(Level.INFO)
        feeder.start() // shuts down when maxRequests have been sent
        waitUntilFirstRecord(server, totalRequests)
        val start = Time.now
        waitForServer(server.done, seconds * 2)
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        log.debug("EndToEndSpec: done waiting for the parrot server to finish")
        transport.allRequests must be(totalRequests)
        val untilNow = start.untilNow.inNanoseconds.toDouble / Duration.NanosPerSecond.toDouble
        untilNow must be < (seconds * 1.20)
        untilNow must be > (seconds * 0.80)
        transport.allRequests must be(requestsRead)
        rp.responded must be(requestsRead)
        assert(rp.properlyShutDown)
      }

      "honor timouts" in {
        val serverConfig = makeServerConfig(true)
        serverConfig.cachedSeconds = 1
        val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
        val server: ParrotServerImpl[ParrotRequest, HttpResponse] =
          new ParrotServerImpl(serverConfig)
        server.start()

        val rate = 1 // rps ... requests per second
        val seconds = 20 // how long we expect to take to send our requests
        val totalRequests = (rate * seconds).toInt
        val feederConfig = makeFeederConfig(serverConfig.parrotPort)

        feederConfig.reuseFile = true
        feederConfig.requestRate = rate
        feederConfig.logSource = memoryLog(totalRequests)
        feederConfig.duration = (seconds / 2).toInt.seconds
        feederConfig.batchSize = 3

        val feeder = new ParrotFeeder(feederConfig)
        feeder.start() // shuts down when maxRequests have been sent
        waitForServer(server.done, seconds * 2)
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        allRequests must be < requestsRead.toInt
        assert(rp.properlyShutDown)
      }
    }

  private[this] def waitForServer(done: Future[Void], seconds: Double) {
    val duration = Duration.fromNanoseconds((seconds * Duration.NanosPerSecond.toDouble).toLong)
    try {
      Await.ready(done, duration)
    } catch {
      case e: TimeoutException =>
        log.warning("EndToEndSpec: timed out in %s", PrettyDuration(duration))
    }
  }

  private[this] def report(feeder: ParrotFeeder, transport: FinagleTransport, serverConfig: ParrotServerConfig[ParrotRequest,HttpResponse]): (Long, Int, TestRecordProcessor) = {
    val result = (feeder.requestsRead.get(), transport.allRequests, serverConfig.loadTestInstance.get.asInstanceOf[TestRecordProcessor])
    val (requestsRead, allRequests, rp) = result
    log.info("EndToEndSpec: done waiting for the parrot server to finish. %d lines read, %d lines sent to victim, %d responses back",
      requestsRead, allRequests, rp.responded)
    result
  }

  private[this] def memoryLog(totalRequests: Int) =
    Some(new InMemoryLog(List.fill[String](totalRequests * 2)("twitter.com")))

  private[this] def waitUntilFirstRecord(server: ParrotServerImpl[ParrotRequest, HttpResponse],
    totalRequests: Int): Unit =
    {
      val elapsed = Stopwatch.start()
      Await.ready(server.firstRecordReceivedFromFeeder)
      log.debug("EndToEndSpec: totalRequests = %d, time to first message was %s", totalRequests,
        PrettyDuration(elapsed()))
    }

  private[this] def makeServerConfig(reuseConnections: Boolean): ParrotServerConfig[ParrotRequest, HttpResponse] = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, HttpResponse]](
      TempFile.fromResourcePath("/test-server.scala"))
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.requestTimeoutInMs = 500
    result.reuseConnections = reuseConnections
    result.transport = Some(new FinagleTransport(result))
    result.loadTestInstance = Some(new TestRecordProcessor(result.service.get, result))
    result
  }

  private[this] def makeFeederConfig(parrotPort: Int): ParrotFeederConfig = {
    val result = new Eval().apply[ParrotFeederConfig](TempFile.fromResourcePath(
      "/test-feeder.scala"))
    result.parrotPort = parrotPort
    result
  }
}
