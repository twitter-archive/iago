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

import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.ParrotJob
import com.twitter.parrot.util.{RequestDistribution}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicReference, AtomicBoolean}

class RequestConsumer[Req <: ParrotRequest](config: ParrotServerConfig[Req, _], aJob: ParrotJob) extends Thread {
  private[this] val log = Logger.get(getClass)
  private[this] val transport = config.transport.getOrElse(throw new Exception("unspecified transport"))
  private[this] val running = new AtomicBoolean(false)
  private[this] val queue = new LinkedBlockingQueue[Req]()

  private[this] val process = new AtomicReference[RequestDistribution](config.createDistribution(aJob.getArrivalRate))
  private[this] val job = new AtomicReference[ParrotJob](aJob)

  private[server] var totalClockError = 0L

  var totalProcessed = 0

  def offer(request: Req) {
    queue.offer(request)
  }

  override def start() {
    this.setName(job.get.getName)
    running.set(true)
    super.start()
  }

  override def run() {
    while (running.get) {
      val start = System.nanoTime()

      try {
        Stats.incr("requests_sent")
        transport.apply(
          queue.take()
        ).
        respond {
          _ => totalProcessed = totalProcessed + 1
        }

      } catch { case t =>
        log.error(t, "Exception sending request: %s", t)
      }

      if (job.get.getArrivalRate > 0) {
        val waitTime = process.get.timeToNextArrival().inNanoseconds - totalClockError
        totalClockError = 0L

        waitForNextRequest(waitTime)

        totalClockError += System.nanoTime() - start - waitTime
      }
    }
  }

  def pause() {
    running.set(false)
    this.interrupt()
  }

  def size = {
    queue.size
  }

  def clockError = {
    totalClockError
  }

  def jobChanged(newJob: ParrotJob) {
    log.debug("Job Changed event received for Job: %s", newJob.toString)
    job.set(newJob)
    process.set(config.createDistribution(job.get.getArrivalRate))
  }

  private[this] def waitForNextRequest(waitTime: Long) {
    val millis = waitTime / 1000000L
    val remainder = waitTime % 1000000L

    if (millis > 0) {
      Thread.sleep(millis)
      busyWait(remainder)
    }
    else if (waitTime < 0) {
      ()
    }
    else {
      busyWait(waitTime)
    }
  }

  /**
   * You can't Thread.sleep for less than a millisecond in Java
   */
  private[this] def busyWait(wait: Long) {
    val endAt = System.nanoTime + wait
    var counter = 0L
    var done = false
    while (!done) {
      counter += 1
      if (counter % 1000 == 0 && System.nanoTime > endAt) done = true
    }
  }
}
