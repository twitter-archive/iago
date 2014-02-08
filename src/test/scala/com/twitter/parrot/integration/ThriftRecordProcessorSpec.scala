/*
Copyright 2013 Twitter, Inc.

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

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.finagle.TimeoutException
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.feeder.ParrotFeeder
import com.twitter.parrot.processor.ThriftRecordProcessor
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotServerImpl
import com.twitter.parrot.server.ParrotService
import com.twitter.parrot.server.ThriftServerImpl
import com.twitter.parrot.server.ThriftTransportFactory
import com.twitter.parrot.util.PrettyDuration
import com.twitter.parrot.util.ThriftFixture
import com.twitter.util.Await
import com.twitter.util.Config.toSpecified
import com.twitter.util.Duration
import com.twitter.util.Eval
import com.twitter.util.Future
import com.twitter.util.RandomSocket
import com.twitter.finagle.tracing.Trace

@RunWith(classOf[JUnitRunner])
class ThriftRecordProcessorSpec extends WordSpec with MustMatchers with FeederFixture {

  private val log = Logger.get(getClass)
  if (!sys.props.contains("SKIP_FLAKY")) "Thrift Record Processor" should {

    "get responses from a Thrift service" in {
      val victimPort = RandomSocket.nextPort()
      EchoServer.serve(victimPort)
      val serverConfig = makeServerConfig(victimPort)
      val rp = new TestThriftRecordProcessor(serverConfig.service.get)
      serverConfig.loadTestInstance = Some(rp)
      val server = new ParrotServerImpl(serverConfig)
      server.start()
      val feederConfig = makeFeederConfig(serverConfig.parrotPort, List("hello"))
      val feeder = new ParrotFeeder(feederConfig)
      feeder.start() // shuts down when it reaches the end of the log
      val successful = waitForServer(server.done, feederConfig.cutoff)
      if (successful) {
        EchoServer.getRequestCount must not be 0
        rp.response must not be ""
      }
      EchoServer.close
    }
  }

  private def makeServerConfig(victimPort: Int) = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Array[Byte]]](
      TempFile.fromResourcePath("/test-thrift.scala"))
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.victim = result.HostPortListVictim("localhost:" + victimPort)
    result.transport = Some(ThriftTransportFactory(result))
    result
  }

  private def waitForServer(done: Future[Unit], seconds: Double): Boolean = {
    val duration = Duration.fromNanoseconds((seconds * Duration.NanosPerSecond.toDouble).toLong)
    try {
      Await.ready(done, duration)
      true
    } catch {
      case timeout: TimeoutException =>
        log.warning("ThriftRecordProcessorSpec: timed out in %s", PrettyDuration(duration))
        false
      case interrupted: InterruptedException =>
        log.warning("ThriftRecordProcessorSpec: test was shut down")
        false
      case other: Exception =>
        log.warning("Error message: %s", other.getMessage)
        log.warning(other.getStackTraceString)
        false
    }
  }
}

private class TestThriftRecordProcessor(pService: ParrotService[ParrotRequest, Array[Byte]]) extends ThriftRecordProcessor(pService) with ThriftFixture {
  private val log = Logger.get(getClass)
  var response = ""
  def processLines(lines: Seq[String]) {
    lines foreach { line =>
      Trace.traceService("Parrot", "TestThriftRecordProcessor.processLines") {
        val future = service(new ThriftClientRequest(serialize("echo", "message", "hello"), false))
        future onSuccess { bytes =>
          response = bytes.toString
          log.debug("ThriftRecordProcessorSpec: response: %s", response)
        }
        future onFailure { throwable =>
          log.error(throwable, "ThriftRecordProcessorSpec: failure response")
        }
      }
    }
  }
}
