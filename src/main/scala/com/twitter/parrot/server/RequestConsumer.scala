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
import com.twitter.parrot.util.{ RequestDistribution }
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{ AtomicReference, AtomicBoolean }
import com.twitter.util.Promise
import com.twitter.util.Await
import com.twitter.util.Stopwatch
import com.twitter.parrot.util.PrettyDuration

class RequestConsumer[Req <: ParrotRequest](config: ParrotServerConfig[Req, _]) extends Thread {

  private[this] val log = Logger.get(getClass)
  private[this] val queue = new LinkedBlockingQueue[Req]()
  private[this] var rate: Int = 1
  private[this] val done = Promise[Unit]

  val started = Promise[Unit]

  def createDistribution = config.createDistribution(rate)

  private[this] val process =
    new AtomicReference[RequestDistribution](createDistribution)

  private[server] var totalClockError = 0L

  var totalProcessed = 0

  def offer(request: Req) {
    queue.offer(request)
  }

  override def start() {
    super.start()
  }

  /* The feeder goes at full speed until the queue is full, so queue.take() takes a long time the
     first time, then takes no time after that. If we were starting the timer BEFORE starting the
     process of sending requests, we clock a "huge delay" (aka, time from when we start the clock to
     when we do queue.take()). The rate then tries to compensate for that, "rapid-firing" requests
     until the "clock error" is back to 0. By starting the clock AFTER we take our first request, we
     are getting rid of the false initial delay.
  */

  override def run() {
    log.trace("RequestConsumer: beginning run")
    try {
      val request = queue.take()
      started.setValue(())
      send(request, System.nanoTime())
      while (true) takeOne
    } catch {
      case e: InterruptedException =>
    }
    while (queue.size() > 0) takeOne
    done.setValue(())
  }

  private def send(request: Req, start: Long) {
    send(request)
    sleep(request.weight, start)
  }

  private def send(request: Req) {
    val transport = config.transport.getOrElse(throw new Exception("unspecified transport"))
    try {
      val future = transport(request)
      Stats.incr("requests_sent")
      future.respond { _ =>
        totalProcessed += 1
      }
    } catch {
      case t =>
        log.error(t, "Exception sending request: %s", t)
    }
  }

  private def sleep(weight: Int, start: Long) {
    if (rate > 0) {
      val waitTime =
        ((1 to weight).map { _ =>
          process.get.timeToNextArrival().inNanoseconds
        }).sum - totalClockError
      totalClockError = 0L
      waitForNextRequest(waitTime)
      totalClockError += System.nanoTime() - start - waitTime
    }
  }

  private def takeOne {
    val start = System.nanoTime()
    val request = queue.take()
    send(request, start)
  }

  def pause() {
    suspend
  }

  def continue() {
    resume
  }

  def size = {
    queue.size
  }

  def clockError = {
    totalClockError
  }

  def setRate(newRate: Int) {
    rate = newRate
    process.set(createDistribution)
  }

  def shutdown: Unit = {
    val elapsed = Stopwatch.start()
    interrupt
    Await.ready(done)
    log.trace("RequestConsumer shut down in %s", PrettyDuration(elapsed()))
  }

  private[this] def waitForNextRequest(waitTime: Long) {
    val millis = waitTime / 1000000L
    val remainder = waitTime % 1000000L

    if (millis > 0) {
      Thread.sleep(millis)
      busyWait(remainder)
    } else if (waitTime < 0) {
      ()
    } else {
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
