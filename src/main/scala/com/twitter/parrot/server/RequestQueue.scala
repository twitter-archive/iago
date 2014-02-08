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

import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util._
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

class RequestQueue[Req <: ParrotRequest, Rep](
    consumer: RequestConsumer[Req, Rep],
    transport: ParrotTransport[Req, Rep]
) {

  private[this] val log = Logger.get(getClass)

  def addRequest(request: Req): Future[Rep] = {
    val response = new Promise[Rep]()
    request.response = response
    consumer.offer(request)
    response
  }

  def pause() {
    consumer.pause()
  }

  def resume() {
    consumer.continue()
  }

  def setRate(newRate: Int) {
    consumer.setRate(newRate)
  }

  def start() {
    log.debug("starting RequestQueue")
    Stats.addGauge("queue_depth") { queueDepth }
    Stats.addGauge("clock_error") { clockError }
    transport respond {
      case Return(response) => transport.stats(response) map {
        Stats.incr(_)
      }
      case Throw(t) => t match {
        case e: ConnectException =>
          if (e.getMessage.contains("timed out")) Stats.incr("response_timeout")
          if (e.getMessage.contains("refused")) Stats.incr("connection_refused")
        case t => {
          Stats.incr("unexpected_error")
          Stats.incr("unexpected_error/" + t.getClass.getName)
          log.error("unexpected error: %s", t)
        }
      }
    }
    consumer.start
  }

  def queueDepth = consumer.size
  def totalProcessed = consumer.totalProcessed
  def clockError = consumer.clockError
  
  /** indicate when the first record has been received */
  def firstRecordReceivedFromFeeder: Future[Unit] = consumer.started

  def shutdown() {
    consumer.shutdown
    log.trace("RequestQueue: shutdown")
  }
}
