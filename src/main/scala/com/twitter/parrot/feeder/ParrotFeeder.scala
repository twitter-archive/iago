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

import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable

import com.twitter.logging.Logger
import com.twitter.ostrich.admin.BackgroundProcess
import com.twitter.ostrich.admin.Service
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.util.ParrotClusterImpl
import com.twitter.parrot.util.PrettyDuration
import com.twitter.parrot.util.RemoteParrot
import com.twitter.util.Duration

object FeederState extends Enumeration {
  val EOF, TIMEOUT, RUNNING = Value
}

case class Results(success: Int, failure: Int)

class ParrotFeeder(config: ParrotFeederConfig) extends Service {
  private[this] val log = Logger.get(getClass.getName)
  val requestsRead = new AtomicLong(0)
  @volatile
  private[this] var state = FeederState.RUNNING

  private[this] val initializedParrots = mutable.Set[RemoteParrot]()

  private[this] val allServers = new CountDownLatch(config.numInstances)
  private[this] lazy val cluster = new ParrotClusterImpl(Some(config))
  private[this] lazy val poller = new ParrotPoller(cluster, allServers)

  private[this] lazy val lines = config.logSource.getOrElse(
    throw new Exception("Unconfigured logSource"))

  /**
   * Starts up the whole schebang. Called from FeederMain.
   */
  def start() {

    if (config.duration.inMillis > 0) {
      shutdownAfter(config.duration)
    }

    // Poller is starting here so that we can validate that we get enough servers, ie
    // so that validatePreconditions has a chance to pass without a 5 minute wait.
    poller.start()

    if (!validatePreconditions()) {
      shutdown()
    } else {

      BackgroundProcess {
        runLoad()
        reportResults()
        shutdown()
      }
    }
  }

  /**
   * Called internally when we're done for any number of reasons. Also can be
   * called remotely if the web management interface is enabled.
   */
  def shutdown() {
    log.trace("ParrotFeeder: shutting down ...")
    if (state == FeederState.RUNNING)
      state = FeederState.TIMEOUT // shuts down immediately when timeout
    poller.shutdown()
    cluster.shutdown()
    ServiceTracker.shutdown()
    log.trace("ParrotFeeder: shut down")
  }

  private[this] def validatePreconditions(): Boolean = {
    if (config.batchSize <= 0) {
      log.error("batchSize is set <= 0! Shutting down")
      return false
    }

    if (config.maxRequests <= 0) {
      log.error("maxRequests is <= 0! Shutting down")
      return false
    }

    if (!lines.hasNext) {
      log.error("The log file is empty! Shutting down")
      return false
    }

    // Must call this before blocking below
    cluster.connectParrots()

    // This gives our server(s) a chance to start up by waiting on a latch the Poller manages.
    // This technically could have a bug -- if a server were to start up and then disappear,
    // the latch would still potentially tick down in the poller, and we'd end up with
    // fewer servers than expected. The code further down will cover that condition.
    log.info("Awaiting %d servers to stand up and be recognized.", config.numInstances)
    allServers.await(5, TimeUnit.MINUTES)

    if (cluster.runningParrots.isEmpty) {
      log.error("Empty Parrots list! Is Parrot running somewhere?")
      return false
    }

    val actualInstances = cluster.runningParrots.size
    val wantedInstances = config.numInstances

    if (actualInstances != wantedInstances) {
      log.error("Found %d servers, expected %d", actualInstances, wantedInstances)
      cluster.runningParrots foreach { parrot => log.error("Server: %s:%s", parrot.host, parrot.port) }
      if (wantedInstances > actualInstances) {
        return false
      } else {
        log.error("Continuing anyway, engaging manual override.")
      }
    }
    true
  }

  // TODO: What happens if our parrot membership changes? We should use the most current
  // TODO: cluster members, but that implies iterating through the set of parrots in an
  // TODO: ordered way even as they get disconnected and removed from the set. This would
  // TODO: require something like a consistent hash. Oof.
  // TEST: make sure that if we are only supposed to send a partial batch, it happens correctly
  private[this] def runLoad() {

    skipForward(config.linesToSkip)

    while (state == FeederState.RUNNING) {
      // limit the number of parrots we use to what we were configured to use
      val parrots = cluster.runningParrots.slice(0, config.numInstances)

      parrots foreach { parrot =>

        if (!initialized(parrot)) {
          initialize(parrot)
        }

        parrot.queueBatch {
          readBatch(linesToRead)
        }

        if (config.maxRequests - requestsRead.get <= 0) {
          log.info("ParrotFeeder.runLoad: exiting because config.maxRequests = %d and requestsRead.get = %d",
            config.maxRequests, requestsRead.get)
          state = FeederState.EOF
        } else if (!lines.hasNext) {
          if (config.reuseFile) {
            log.info("ParrotFeeder.runLoad: inputLog is exhausted, restarting reader.")
            lines.reset()
          } else {
            log.info("ParrotFeeder.runLoad: exiting because log exhausted")
            state = FeederState.EOF
          }
        }
      }
    }
  }

  private[this] def skipForward(skip: Int) {
    for (i <- 1 to skip if lines.hasNext) {
      lines.next()
    }
  }

  private[this] def initialized(parrot: RemoteParrot): Boolean = {
    initializedParrots.contains(parrot)
  }

  private[this] def initialize(parrot: RemoteParrot) {
    parrot.targetDepth = config.cachedSeconds * config.requestRate
    parrot.setRate(config.requestRate)
    parrot.createConsumer {
      state
    }
    initializedParrots += parrot
  }

  private[this] def linesToRead: Int = {
    val batchSize = config.batchSize
    val linesLeft = config.maxRequests - requestsRead.get

    if (!lines.hasNext || linesLeft <= 0) 0
    else if (batchSize <= linesLeft) batchSize
    else linesLeft.toInt
  }

  private[this] def readBatch(readSize: Int): List[String] = {
    log.debug("Reading %d log lines.", readSize)

    val result = for (i <- 1 to readSize if lines.hasNext) yield lines.next()

    requestsRead.addAndGet(result.size)
    result.toList
  }

  // TEST: How do we handle the case where we feed a parrot for a while and then it dies,
  // TEST: but we continue the run with the rest of the cluster?
  private[this] def reportResults() {
    val results = getResults(cluster.parrots)
    log.info("Lines played: %d failed: %d", results.success, results.failure)
  }

  // TEST: confirm that a set of parrots add up to the expected total
  private[this] def getResults(remotes: Set[RemoteParrot]): Results = {
    var successes = 0
    var failures = 0

    remotes foreach (successes += _.results.success)
    remotes foreach (failures += _.results.failure)

    Results(successes, failures)
  }

  private[this] def shutdownAfter(duration: Duration) {
    val timer = new Timer
    timer.schedule(new TimerTask {
      def run() {
        timer.cancel()
        log.info("ParrotFeeder.shutdownAfter: shutting down due to duration timeout of %s",
          PrettyDuration(duration))
        state = FeederState.TIMEOUT
      }
    },
      duration.inMillis)
  }
}
