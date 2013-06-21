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

import org.junit.runner.RunWith
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.logging.Logger

@RunWith(classOf[JUnitRunner])
class RequestQueueSpec
  extends WordSpec
  with MustMatchers with OneInstancePerTest with ServerFixture {

  val log = Logger.get(getClass.getName)

  "RequestQueue" should {
    if (System.getenv("SBT_CI") == null) {

      "achieve the specified requestRate when it's in excess of 500rps" in {
        val queue = new RequestQueue(config)
        queue.start()

        val seconds = 30
        val rate = 1000
        val numRequests = seconds * rate * 2

        queue.setRate(rate)

        for (i <- 1 until numRequests) {
          queue.addRequest(new ParrotRequest)
        }
        Thread.sleep(seconds * 1000L)

        transport.sent.toDouble must be >= (seconds * rate * 0.95)
        transport.sent.toDouble must be <= (seconds * rate * 1.05)
      }

      "accurately report queue_depth" in {
        val queue = new RequestQueue(config)

        val seconds = 10
        val rate = 100
        val numRequests = seconds * rate

        queue.setRate(rate)
        for (i <- 0 until numRequests) {
          queue.addRequest(new ParrotRequest)
        }
        log.info("queue depth is %d", queue.queueDepth)
        queue.queueDepth must be(numRequests)
      }
    }
  }
}
