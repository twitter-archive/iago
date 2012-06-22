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

import collection.JavaConversions._
import collection.mutable
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{BackgroundProcess, ServiceTracker, Service}
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.thrift._
import com.twitter.parrot.util.{ParrotClusterImpl, RemoteParrot}
import com.twitter.util.Duration
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.{TimerTask, Timer}

case class Results(success: Int, failure: Int)

class ParrotFeeder(config: ParrotFeederConfig) extends Service {
  private[this] val log = Logger.get(getClass.getName)
  private[this] val requestsRead = new AtomicInteger(0)
  private[this] val isShutdown = new AtomicBoolean(false)

  private[this] val victims = createVictims()
  private[this] val parrotJob = new ParrotJob()
      .setVictims(victims)
      .setProcessor(config.parser)
      .setName(config.jobName)

  // arrivalRate trumps concurrency
  if (config.requestRate != 0 && config.numThreads != 0) {
    parrotJob.setArrivalRate(config.requestRate)
  }
  else {
    parrotJob.setConcurrency(config.numThreads)
    parrotJob.setArrivalRate(config.requestRate)
  }

  private[this] val initializedParrots = mutable.Set[RemoteParrot]()
  private[this] val jobs = mutable.Map[RemoteParrot, ParrotJobRef]()

  private[this] val allServers = new CountDownLatch(config.numInstances)
  private[this] lazy val cluster = new ParrotClusterImpl(Some(config))
  private[this] lazy val poller = new ParrotPoller(cluster, allServers)

  private[this] lazy val lines = config.logSource.getOrElse(
    throw new Exception("Unconfigured logSource")
  )

  /**
   * Starts up the whole schebang. Called from FeederMain.
   */
  def start() {

    if (config.duration.inMillis > 0) {
      shutdownAfter(config.duration)
      config.maxRequests = Integer.MAX_VALUE // don't terminate prematurely
    }

    // Poller is starting here so that we can validate that we get enough servers, ie
    // so that validatePreconditions has a chance to pass without a 5 minute wait.
    poller.start()

    if (!validatePreconditions()) {
      shutdown()
    }
    else {

      BackgroundProcess {
        runLoad()
        drainServers()
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
    cluster.shutdown()
    poller.shutdown()
    ServiceTracker.shutdown()
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
      }
      else {
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

    while (!isShutdown.get) {
      // limit the number of parrots we use to what we were configured to use
      val parrots = cluster.runningParrots.slice(0, config.numInstances)

      parrots foreach { parrot =>

        if (!initialized(parrot)) {
          initialize(parrot)
        }

        val batch = readBatch(linesToRead)
        if (batch.size > 0) {

          writeBatch(parrot, batch)

          if (config.maxRequests - requestsRead.get <= 0) {
            isShutdown.set(true)
          }
        }

        if(!lines.hasNext) {
          if (config.reuseFile) {
            log.warning("inputLog is exhausted, restarting reader.")
            lines.reset()
          } else {
            isShutdown.set(true)
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
    parrot.targetDepth = 60 * config.requestRate
    parrot.createJob(parrotJob)
    parrot.createConsumer()
    initializedParrots += parrot
  }

  private[this] def linesToRead = {
    val batchSize = config.batchSize
    val linesLeft = config.maxRequests - requestsRead.get

    if (!lines.hasNext || linesLeft <= 0) 0
    else if (batchSize <= linesLeft) batchSize
    else linesLeft
  }

  private[this] def readBatch(readSize: Int): List[String] = {
    log.debug("Reading %d log lines.", readSize)

    val result = for (i <- 1 to readSize if lines.hasNext) yield lines.next()

    requestsRead.addAndGet(result.size)
    result.toList
  }

  private[this] def writeBatch(parrot: RemoteParrot, batch: List[String]) {
    if (!isShutdown.get) {
      log.debug("Queuing batch for %s:%d with %d requests", parrot.host, parrot.port, batch.size)
      parrot.addRequest(batch)
    }
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
        isShutdown.set(true)
      }
    },
      duration.inMillis)
  }

  private[this] def drainServers() {
    def totalProcessed = cluster.parrots.foldLeft(0.0)((sum, p) => sum + p.getStatus.getTotalProcessed)
    def queueDepth = cluster.parrots.foldLeft(0.0)((sum, p) => sum + p.getStatus.getQueueDepth)

    val maxIterations = config.busyCutoff/config.pollInterval.inMilliseconds
    var counter = 0

    while((requestsRead.get != totalProcessed || queueDepth > 0) && counter < maxIterations) {
      Thread.sleep(config.pollInterval.inMilliseconds)
      counter = counter + 1
    }
  }

  private[this] def createVictims(): List[TargetHost] = {
    config.victimTargets match {
      case Some(victims: List[_]) => victims
      case _ => config.victimHosts map { host =>
        new TargetHost(config.victimScheme, host, config.victimPort)
      }
    }
  }
}
