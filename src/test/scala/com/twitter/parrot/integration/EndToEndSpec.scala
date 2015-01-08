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
import com.twitter.parrot.feeder.{FeederState, InMemoryLog, ParrotFeeder}
import com.twitter.parrot.server._
import com.twitter.parrot.util.ConsoleHandler
import com.twitter.parrot.util.PrettyDuration
import com.twitter.util._

@RunWith(classOf[JUnitRunner])
class EndToEndSpec extends WordSpec with MustMatchers with FeederFixture {
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
        val feederConfig = makeFeederConfig(serverConfig.parrotPort, urls)
        feederConfig.requestRate = 1000
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
        val feederConfig = makeFeederConfig(serverConfig.parrotPort, twitters(totalRequests))

        feederConfig.reuseFile = true
        feederConfig.requestRate = rate
        feederConfig.maxRequests = totalRequests
        feederConfig.batchSize = 3

        val feeder = new ParrotFeeder(feederConfig)
        feeder.start() // shuts down when maxRequests have been sent
        waitUntilFirstRecord(server, totalRequests)
        val start = Time.now
        waitForServer(server.done, seconds * 2)
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        log.debug("EndToEndSpec: transport.allRequests (%d) == totalRequests (%d): " + (transport.allRequests == totalRequests), transport.allRequests, totalRequests)
        transport.allRequests must be(totalRequests)
        val untilNow = start.untilNow.inNanoseconds.toDouble / Duration.NanosPerSecond.toDouble
        untilNow must be < (seconds * 1.20)
        untilNow must be > (seconds * 0.80)
        log.debug("EndToEndSpec: transport.allRequests (%d) == requestsRead (%d): " + (transport.allRequests == requestsRead), transport.allRequests, requestsRead)
        transport.allRequests must be(requestsRead)
        log.debug("EndToEndSpec: rp.responded (%d) == requestsRead (%d): " + (rp.responded == requestsRead), rp.responded, requestsRead)
        rp.responded must be(requestsRead)
        assert(rp.properlyShutDown)
      }

      "honor timeouts" in {
        val serverConfig = makeServerConfig(true)
        serverConfig.cachedSeconds = 1
        val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
        val server: ParrotServerImpl[ParrotRequest, HttpResponse] =
          new ParrotServerImpl(serverConfig)
        server.start()

        val rate = 1 // rps ... requests per second
        val secondsToRun = 10
        val expectedRequests = (rate * secondsToRun) // expect the number of requests we can send before timeout
        val feederConfig = makeFeederConfig(serverConfig.parrotPort, twitters(expectedRequests))

        feederConfig.reuseFile = true
        feederConfig.requestRate = rate
        feederConfig.duration = secondsToRun.seconds
        feederConfig.maxRequests = Integer.MAX_VALUE // allow for unlimited requests so that timeout can occur
        feederConfig.batchSize = 3

        val feederDone = Promise[Unit]
        val feeder = new ParrotFeeder(feederConfig) {
          override def shutdown() = {
            feederDone.setDone() // give this test ability to wait on shutdown call to test for timeout
            super.shutdown()
          }
        }
        feeder.start()
        try {
          Await.ready(feederDone, (secondsToRun + 1).seconds) // time out if feeder timeout is not honored
        } catch {
          case e: TimeoutException =>
            fail(String.format("Server did not time out in %s", PrettyDuration(secondsToRun.seconds)))
        }
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        allRequests must be <= expectedRequests + 1 // allow for small margin due to race condition
      }

      "honor the max request limit" in {
        val serverConfig = makeServerConfig(true)
        serverConfig.cachedSeconds = 1
        val transport = serverConfig.transport.get.asInstanceOf[FinagleTransport]
        val server: ParrotServerImpl[ParrotRequest, HttpResponse] =
          new ParrotServerImpl(serverConfig)
        server.start()

        val rate = 1 // rps ... requests per second
        val requestLimit = 10 // how many requests we want to send
        val secondsToRun = (rate * requestLimit * 2) // run much longer than needed to send the max number of requests
        val feederConfig = makeFeederConfig(serverConfig.parrotPort, twitters(requestLimit))

        feederConfig.reuseFile = true
        feederConfig.requestRate = rate
        feederConfig.duration = secondsToRun.seconds
        feederConfig.maxRequests = requestLimit
        feederConfig.batchSize = 3

        val feeder = new ParrotFeeder(feederConfig)
        feeder.start() // shuts down when maxRequests have been sent
        waitForServer(server.done, secondsToRun)
        val (requestsRead, allRequests, rp) = report(feeder, transport, serverConfig)
        allRequests must be === requestLimit
        assert(rp.properlyShutDown)
      }
    }

  private[this] def waitForServer(done: Future[Unit], seconds: Double) {
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

  private[this] def twitters(totalRequests: Int) =
    List.fill[String](totalRequests * 2)("twitter.com")

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
    result.transport = Some(FinagleTransportFactory(result))
    result.loadTestInstance = Some(new TestRecordProcessor(result.service.get, result))
    result
  }
}
