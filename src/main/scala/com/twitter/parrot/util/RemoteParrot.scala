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
package com.twitter.parrot.util

import java.net.InetSocketAddress
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.logging.Logger
import com.twitter.parrot.feeder.FeedConsumer
import com.twitter.parrot.thrift.ParrotServerService
import com.twitter.parrot.thrift.ParrotStatus
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.parrot.feeder.FeederState
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finagle.stats.OstrichStatsReceiver

class InternalCounter(var success: Int = 0, var failure: Int = 0) {
  def add(counter: InternalCounter) {
    success += counter.success
    failure += counter.failure
  }
}

class RemoteParrot(val name: String,
  val results: InternalCounter,
  val host: String,
  val port: Int,
  val finagleTimeout: Duration = 5.seconds,
  var queueDepth: Double = 0.0,
  var targetDepth: Double = 0.0) {
  private[this] val log = Logger(getClass.getName)
  private[this] var consumer: FeedConsumer = null
  private[this] var traceCount: Long = 0

  private[this] val (service, client) = connect(host, port)

  def createConsumer(state: => FeederState.Value) {
    log.trace("RemoteParrot: creating consumer")
    consumer = new FeedConsumer(this, state)
    consumer.start()
    log.trace("RemoteParrot: consumer created")
  }

  def hasCapacity = consumer.queue.remainingCapacity > 0

  def addRequest(batch: List[String]) {
    consumer.addRequest(batch)
  }

  def queueBatch(batchFun: => List[String]) {
    if (hasCapacity) {
      traceCount = 0
      val batch = batchFun
      if (batch.size > 0) {
        log.trace("RemoteParrot: Queuing batch for %s:%d with %d requests", host, port, batch.size)
        addRequest(batch)
      }
    } else {
      if (traceCount < 2)
        log.trace("RemoteParrot: parrot[%s:%d] queue is over capacity", host, port)
      else if (traceCount == 2)
        log.trace("RemoteParrot: parrot[%s:%d] more over capacity warnings ...Ï", host, port)
      traceCount += 1
    }
  }

  def setRate(newRate: Int) {
    log.trace("RemoteParrot: setting rate %d", newRate)
    waitFor(client.setRate(newRate))
    log.trace("RemoteParrot: rate set")
  }

  def sendRequest(batch: Seq[String]): ParrotStatus = {
    log.trace("RemoteParrot.sendRequest: parrot[%s:%d] sending requests of size=%d to the server",
      host,
      port,
      batch.size)
    val result = waitFor(client.sendRequest(batch))
    log.trace("RemoteParrot.sendRequest: parrot[%s:%d] done sending requests of size=%d to the server",
      host,
      port,
      batch.size)
    result
  }

  def getStatus: ParrotStatus = {
    waitFor(client.getStatus)
  }

  def pause() {
    waitFor(client.pause())
  }

  def resume() {
    waitFor(client.resume())
  }

  def shutdown() {
    consumer.shutdown
    waitFor(client.shutdown(), Duration.Top)
    service.close()
  }

  def isConnected() = {
    service.isAvailable
  }

  override def equals(that: Any): Boolean = {
    that match {
      case other: RemoteParrot => other.host == host && other.port == port
      case _                   => false
    }
  }

  override lazy val hashCode = host.hashCode + port.hashCode

  def isBusy = queueDepth > targetDepth

  private[this] def connect(host: String, port: Int) = {
    val statsReceiver = new OstrichStatsReceiver

    val service: Service[ThriftClientRequest, Array[Byte]] = ClientBuilder()
      .tracer(ZipkinTracer.mk(statsReceiver))
      .hosts(new InetSocketAddress(host, port))
      .codec(ThriftClientFramedCodec())
      .daemon(true)
      .hostConnectionLimit(1)
      .retries(2)
      // Enable only for debugging
      //      .reportTo(statsReceiver)
      //      .logger(JLogger.getLogger("thrift"))
      .build()

    val client = new ParrotServerService.FinagledClient(service, new TBinaryProtocol.Factory())

    (service, client)
  }

  private[this] def waitFor[A](future: Future[A], timeout: Duration = finagleTimeout): A = {
    future.get(timeout) match {
      case Return(res) => res
      case Throw(t)    => throw t
    }
  }
}

