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
package com.twitter.parrot.feeder

import java.util.concurrent.LinkedBlockingQueue

import com.twitter.logging.Logger
import com.twitter.parrot.util.InternalCounter
import com.twitter.parrot.util.RemoteParrot
import com.twitter.util.Await
import com.twitter.util.Promise

class FeedConsumer(parrot: RemoteParrot, state: => FeederState.Value) extends Thread {
  private[this] val log = Logger(getClass.getName)
  // The queue capacity should be cachedSeconds * requestRate / batchSize at most. The problem is
  // requestRate varies dynamically
  val queue = new LinkedBlockingQueue[List[String]](100)
  private[this] val done = Promise[Unit]

  override def start() {
    this.setDaemon(true)
    super.start()
  }

  override def run() {
    while (state == FeederState.RUNNING) {
      if (parrot.isBusy) {
        Thread.sleep(ParrotPoller.pollRate)
      } else {
        if (queue.isEmpty) {
          log.info("Queue is empty for server %s:%d", parrot.host, parrot.port)
          Thread.sleep(ParrotPoller.pollRate) // don't spin wait on the queue
        } else send
      }
    }
    while (!queue.isEmpty() && state != FeederState.TIMEOUT)
      if (parrot.isBusy)
        Thread.sleep(ParrotPoller.pollRate)
      else
        send
    done.setValue(())
  }

  private def send {
    try {
      sendRequest(parrot, queue.take())
    } catch {
      case t: Throwable => log.error(t, "Error sending request: %s", t.getClass.getName)
    }
  }

  def shutdown {
    Await.ready(done)
  }

  def addRequest(request: List[String]) {
    log.trace("parrot[%s:%d] adding requests of size=%d to the queue",
      parrot.host,
      parrot.port,
      request.size)
    queue.put(request)
    log.trace("parrot[%s:%d] added requests of size=%d to the queue",
      parrot.host,
      parrot.port,
      request.size)
  }

  private[this] def sendRequest(parrot: RemoteParrot, request: List[String]) {
    val success = parrot.sendRequest(request)
    log.info("FeedConsumer.sendRequest: wrote batch of size %d to %s:%d rps=%g depth=%g status=%s lines=%d",
      request.size,
      parrot.host,
      parrot.port,
      success.requestsPerSecond getOrElse 0d,
      success.queueDepth getOrElse 0d,
      success.status,
      success.linesProcessed getOrElse 0)

    val linesProcessed = success.linesProcessed getOrElse 0
    parrot.results.add(new InternalCounter(linesProcessed, request.length - linesProcessed))
    parrot.queueDepth = success.queueDepth getOrElse 0d
  }
}
