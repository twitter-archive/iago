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

import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.thrift.{ThriftClientFramedCodec, ThriftClientRequest}
import com.twitter.parrot.thrift.{ParrotStatus, ParrotJob, ParrotJobRef, ParrotServerService}
import com.twitter.parrot.feeder.FeedConsumer
import com.twitter.util.{Duration, Future, Return, Throw}
import java.net.InetSocketAddress
import java.util.logging.Logger
import org.apache.thrift.protocol.TBinaryProtocol

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
                   var targetDepth: Double = 0.0)
{
  private[this] var jobRef: ParrotJobRef = null
  private[this] var consumer: FeedConsumer = null

  private[this] val (service, client) = connect(host, port)

  def createJob(job: ParrotJob): ParrotJobRef = {
    if (jobRef == null) {
      jobRef = waitFor(client.createJob(job))
    }
    jobRef
  }

  def createConsumer() {
    consumer = new FeedConsumer(this)
    consumer.start()
  }

  def addRequest(batch: List[String]) {
    consumer.addRequest(batch)
  }

  def adjustRateForJob(name: String, adjustment: Int) {
    waitFor(client.adjustRateForJob(name, adjustment))
  }

  def sendRequest(batch: java.util.List[String]): ParrotStatus = {
    waitFor(client.sendRequest(jobRef, batch))
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
	consumer.isShutdown.set(true)
	println("going to shut down client")
    waitFor(client.shutdown())
    service.release
  }

  def isConnected() = {
    service.isAvailable
  }

  override def equals(that: Any): Boolean = {
    that match {
      case other: RemoteParrot => other.host == host && other.port == port
      case _ => false
    }
  }

  override lazy val hashCode = host.hashCode + port.hashCode

  def isBusy = queueDepth > targetDepth

  private[this] def connect(host: String, port: Int) = {
    val service: Service[ThriftClientRequest, Array[Byte]] = ClientBuilder()
      .hosts(new InetSocketAddress(host, port))
      .codec(ThriftClientFramedCodec())
      .hostConnectionLimit(1)
      .retries(2)
// Enable only for debugging
//      .reportTo(new OstrichStatsReceiver)
//      .logger(Logger.getLogger("thrift"))
      .build()

    val client = new ParrotServerService.ServiceToClient(service, new TBinaryProtocol.Factory())

    (service, client)
  }

  private[this] def waitFor[A](future: Future[A]): A = {
    future.get(finagleTimeout) match {
      case Return(res) => res
      case Throw(t) => throw t
    }
  }
}

