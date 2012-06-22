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

import com.twitter.logging.Logger
import com.twitter.parrot.thrift.{ParrotServerService, ParrotState, ParrotStatus}
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TSocket
import com.twitter.parrot.util.{ParrotCluster, RemoteParrot}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

object ParrotPoller {
  val pollRate = 1000
}

class ParrotPoller(cluster: ParrotCluster, serverLatch: CountDownLatch) extends Thread {
  val log = Logger.get(getClass)
  val isRunning = new AtomicBoolean(true)
  var parrotsSnap = 0

  override def start() {
    log.info("Starting ParrotPoller")
    super.setName("ParrotPoller")
    super.setDaemon(false)
    super.start()
  }

  override def run() {
    while (isRunning.get) {
      pollParrots()
      Thread.sleep(ParrotPoller.pollRate)
    }
  }

  def shutdown() {
    isRunning.set(false)
  }

  private[this] def pollParrots() {
    cluster.parrots foreach { pollParrot(_) }

    // Note that cluster.parrots can change during execution of this block!
    val curParrots = cluster.parrots.size
    val newParrots = curParrots - parrotsSnap;
    for (_ <- 1 to newParrots) serverLatch.countDown()
    parrotsSnap = curParrots
  }

  private[this] def pollParrot(parrot: RemoteParrot) {
    val status = parrot.getStatus
    parrot.queueDepth = status.queueDepth
    log.debug("pollParrot: depth is %f for %s:%d", parrot.queueDepth, parrot.host, parrot.port)
  }

  private[this] def makeThriftClient(host: String, port: Int): ParrotServerService.Client = {
    val result = new ParrotServerService.Client(new TBinaryProtocol(new TSocket(host, port)))
    try {
      result.getInputProtocol.getTransport.open()
    }
    catch {
      case t: Throwable => log.error(t, "Error connecting to %s %d: %s", host, port, t.getClass.getName)
    }
    result
  }
}
