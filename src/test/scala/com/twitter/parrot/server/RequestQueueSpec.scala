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

import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.{TargetHost, ParrotJob}
import com.twitter.util.Eval
import java.util.ArrayList
import org.jboss.netty.handler.codec.http.HttpResponse
import org.specs.SpecificationWithJUnit

class RequestQueueSpec extends SpecificationWithJUnit {
  val log = Logger.get(getClass.getName)
  val config = {
    val eval = new Eval
    val configFile = TempFile.fromResourcePath("/test-server.scala")
    eval[ParrotServerConfig[ParrotRequest, HttpResponse]](configFile)
  }
  val transport = config.transport match {
    case Some(dt: DumbTransport) => dt
    case _ => throw new Exception("Wrong ParrotTransport, test will fail")
  }

  /**
   * TODO: Duping code here from ParrotServerSpec
   */
  def makeJob() = {
    val victims = new ArrayList[TargetHost]()
    victims.add(new TargetHost("http", "localhost", 0))

    val result = new ParrotJob()
    result.setVictims(victims)
    result.setName("RequestQueueSpec")
  }

  "RequestQueue" should {
    if (System.getenv("SBT_CI") != null) {
      skip("This is really an integration test")
    }
    val job = makeJob()

    "achieve the specified requestRate when it's in excess of 500rps" in {
      val queue = new RequestQueue(config)
      queue.start()

      val seconds = 30
      val rate = 1000
      val numRequests = seconds * rate * 2

      job.setArrivalRate(rate)

      for (i <- 1 until numRequests) {
        queue.addRequest(job, new ParrotRequest(job.victims.get(0)))
      }
      Thread.sleep(seconds * 1000L)

      transport.sent.toDouble must be_>=(seconds * rate * 0.95)
      transport.sent.toDouble must be_<=(seconds * rate * 1.05)
    }

    "accurately report queue_depth" in {
      val queue = new RequestQueue(config)

      val seconds = 10
      val rate = 100
      val numRequests = seconds * rate

      job.setArrivalRate(rate)
      for (i <- 0 until numRequests) {
        queue.addRequest(job, new ParrotRequest(job.victims.get(0)))
      }
      log.info("queue depth is %g", queue.queueDepth)
      queue.queueDepth mustEqual numRequests
    }
  }
}
