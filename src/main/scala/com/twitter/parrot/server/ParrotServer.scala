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

import collection.JavaConversions._
import collection.mutable
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.processor.{RecordProcessor,RecordProcessorFactory}
import com.twitter.parrot.thrift._
import com.twitter.util.Future
import java.util.{List => JList}
import java.util.concurrent.atomic.AtomicReference
import xml.{Elem, Node}

trait ParrotServer[Req <: ParrotRequest, Rep] extends ParrotServerService.ServiceIface {
  val config: ParrotServerConfig[Req, Rep]
  def createJob(job: ParrotJob): Future[ParrotJobRef]
  def adjustRateForJob(name: String, adjustment: Int): Future[Void]
  def sendRequest(job: ParrotJobRef, lines: JList[String]): Future[ParrotStatus]
  def getStatus: Future[ParrotStatus]
  def start(): Future[Void]
  def shutdown(): Future[Void]
  def pause(): Future[Void]
  def resume(): Future[Void]
  def fetchThreadStacks(): Future[String]
}

class ParrotServerImpl[Req <: ParrotRequest, Rep](val config: ParrotServerConfig[Req, Rep])
  extends ParrotServer[Req, Rep]
{
  private[this] val log = Logger.get(getClass)
  private[this] val jobRef = new AtomicReference[ParrotJob]()
  private[this] val status = new ParrotStatus().setStatus(ParrotState.UNKNOWN)

  private[this] lazy val thriftServer = config.thriftServer.getOrElse(throw new Exception("Unconfigured thrift service"))
  private[this] lazy val clusterService = config.clusterService.getOrElse(throw new Exception("Unconfigured cluster service"))
  private[this] lazy val transport = config.transport.getOrElse(throw new Exception("Unconfigured transport"))
  private[this] lazy val queue = config.queue.getOrElse(throw new Exception("Unconfigured request queue"))

  def start(): Future[Void] = {
    status.setStatus(ParrotState.STARTING)
    transport.start(config)
    queue.start()
    thriftServer.start(this, config.parrotPort)
    clusterService.start(config.parrotPort)
    status.setStatus(ParrotState.RUNNING)
    Future.Void
  }

  def shutdown(): Future[Void] = {
    status.setStatus(ParrotState.STOPPING)
    clusterService.shutdown()
    queue.shutdown()
    transport.shutdown()
    status.setStatus(ParrotState.SHUTDOWN)
    val job = jobRef.get()
    if (job != null) {
      RecordProcessorFactory.getProcessorForJob(job).shutdown()
    }
    ServiceTracker.shutdown()
    thriftServer.shutdown()
    Future.Void
  }

  def createJob(job: ParrotJob): Future[ParrotJobRef] = {
    synchronized {
      if (jobRef.get() == null) {
        log.info("Creating job named %s", job.getName)
        job.setCreated(System.currentTimeMillis)
        jobRef.set(job)
        RecordProcessorFactory.getProcessorForJob(job).start(job)
        config.service foreach { _.setJob(job) }
      }
    }
    Future.value(new ParrotJobRef(0))
  }

  def adjustRateForJob(name: String, adjustment: Int): Future[Void] = {
    log.info("Adjusting rate for job %s by %d RPS", name, adjustment)
    val job = jobRef.get()
    if (job != null) {
      log.debug("calling job changed")
      job.arrivalRate += adjustment
      queue.jobChanged(job)
    }
    else {
      log.error("Got adjust for job %s but no createJob called yet", name)
    }
    Future.Void
  }

  def sendRequest(pjr: ParrotJobRef, lines: JList[String]): Future[ParrotStatus] = {
    if (pjr.getJobId > 0 || jobRef.get() == null) {
      return Future.value(
        new ParrotStatus().setStatus(ParrotState.UNKNOWN).setLinesProcessed(0))
    }
    val job = jobRef.get()
    RecordProcessorFactory.getProcessorForJob(job).processLines(job, lines)

    Stats.incr("records-read", lines.size)
    getStatus.map(status => status.setLinesProcessed(lines.size))
  }

  def getStatus: Future[ParrotStatus] = {
    val result = new ParrotStatus
    val collection = Stats.get("global")

    val rps = collection.getGauge("qps") match {
      case Some(d) => d
      case None => 0.0
    }

    val depth = collection.getGauge("queue_depth") match {
      case Some(d) => d
      case None => 0.0
    }

    val processed = queue.totalProcessed

    result.setQueueDepth(depth)
    result.setRequestsPerSecond(rps)
    result.setStatus(status.getStatus)
    result.setJob(jobRef.get)
    Future.value(result.setTotalProcessed(processed))
  }

  def pause(): Future[Void] = {
    log.debug("pausing server")
    queue.pause()
    status.setStatus(ParrotState.PAUSED)
    Future.Void
  }

  def resume(): Future[Void] = {
    log.debug("resuming server")
    queue.resume()
    status.setStatus(ParrotState.RUNNING)
    Future.Void
  }

  def getRootThreadGroup = {
    var result = Thread.currentThread.getThreadGroup
    while (result.getParent != null) {
      result = result.getParent
    }
    result
  }

  def listThreads(group: ThreadGroup, container: Node): Node = {
    val result = <g name={group.getName} class={group.getClass.toString}/>
    val threads = new Array[Thread](group.activeCount * 2 + 10)
    val nt = group.enumerate(threads, false)
    var threadNodes = mutable.ListBuffer[Elem]()
    for (i <- 0 until nt) {
      val t = threads(i)
      var methods = mutable.ListBuffer[Elem]()
      t.getStackTrace foreach { e => methods += <method>{e}</method> }
      threadNodes += <foo name={t.getName} class={t.getClass.toString}>{methods}</foo>
    }
    var ng = group.activeGroupCount
    val groups = new Array[ThreadGroup](ng * 2 + 10)
    ng = group.enumerate(groups, false)
    for (i <- 0 until ng) {
      listThreads(groups(i), result)
    }
    <g name={group.getName} class={group.getClass.toString}>{result}</g>
  }

  def fetchThreadStacks(): Future[String] = {
    val result = <root/>
    listThreads(getRootThreadGroup, result)
    Future.value(result.toString)
  }
}
