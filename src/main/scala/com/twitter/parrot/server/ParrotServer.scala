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

import java.io.File
import java.io.FileReader
import java.io.IOException
import scala.collection.mutable
import scala.xml.Elem
import scala.xml.Node
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.ParrotLog
import com.twitter.parrot.thrift.ParrotServerService
import com.twitter.parrot.thrift.ParrotState
import com.twitter.parrot.thrift.ParrotStatus
import com.twitter.parrot.util.PrettyDuration
import com.twitter.util.Future
import com.twitter.util.Promise
import com.twitter.util.Stopwatch
import java.util.concurrent.atomic.AtomicBoolean

trait ParrotServer[Req <: ParrotRequest, Rep] extends ParrotServerService.FutureIface {
  val config: ParrotServerConfig[Req, Rep]
}

class ParrotServerImpl[Req <: ParrotRequest, Rep](val config: ParrotServerConfig[Req, Rep])
  extends ParrotServer[Req, Rep] {
  private[this] val log = Logger.get(getClass)
  private[this] var status: ParrotStatus = ParrotStatus(status = Some(ParrotState.Unknown))
  val done = Promise[Unit]

  private[this] val isShutdown = new AtomicBoolean(false)

  private[this] lazy val thriftServer = config.thriftServer.getOrElse(throw new Exception("Unconfigured thrift service"))
  private[this] lazy val clusterService = config.clusterService.getOrElse(throw new Exception("Unconfigured cluster service"))
  private[this] lazy val transport = config.transport.getOrElse(throw new Exception("Unconfigured transport"))
  private[this] lazy val queue = config.queue.getOrElse(throw new Exception("Unconfigured request queue"))

  def start(): Future[Unit] = {
    status = status.copy(status = Some(ParrotState.Starting))
    transport.start()
    queue.start()
    thriftServer.start(this, config.parrotPort)
    clusterService.start(config.parrotPort)
    status = status.copy(status = Some(ParrotState.Running))
    log.info("using record processor %s", config.recordProcessor.getClass().getName())
    config.recordProcessor.start()
    Future.Unit
  }

  /** indicate when the first record has been received */
  def firstRecordReceivedFromFeeder: Future[Unit] = queue.firstRecordReceivedFromFeeder

  def shutdown(): Future[Unit] = {

    /* Calling ServiceTracker.shutdown() causes all the Ostrich threads to go away. It also results
     * in all its managed services to be shutdown. That includes this service. We put a guard
     * here so we don't end up calling ourselves twice.
     */

    if (isShutdown.compareAndSet(false, true)) {
      Future {
        status = status.copy(status = Some(ParrotState.Stopping))
        log.trace("shutting down")
        clusterService.shutdown()
        queue.shutdown()
        shutdownTransport
        status = status.copy(status = Some(ParrotState.Shutdown))
        config.recordProcessor.shutdown()
        ServiceTracker.shutdown()
        thriftServer.shutdown()
        log.trace("shut down")
        done.setValue(null)
      }
    }
    done
  }

  private[this] def shutdownTransport: Unit = {
    val elapsed = Stopwatch.start()
    transport.shutdown()
    log.trace("transport shut down in %s", PrettyDuration(elapsed()))
  }

  def setRate(newRate: Int): Future[Unit] = {
    log.info("setting rate %d RPS", newRate)
    queue.setRate(newRate)
    Future.Unit
  }

  def sendRequest(lines: Seq[String]): Future[ParrotStatus] = {
    log.trace("sendRequest: calling process lines with %d lines", lines.size)
    config.recordProcessor.processLines(lines)
    log.trace("sendRequest: done calling process lines with %d lines", lines.size)

    Stats.incr("records-read", lines.size)
    val result = getStatus.map(_.copy(linesProcessed = Some(lines.size)))
    log.trace("exiting sendRequest")
    result
  }

  def getStatus: Future[ParrotStatus] = Future {
    val collection = Stats.get("global")

    val rps = collection.getGauge("qps") match {
      case Some(d) => d
      case None    => 0.0
    }

    val depth = collection.getGauge("queue_depth") match {
      case Some(d) => d
      case None    => 0.0
    }

    val processed = queue.totalProcessed

    ParrotStatus(
      queueDepth = Some(depth),
      requestsPerSecond = Some(rps),
      status = status.status,
      totalProcessed = Some(processed)
    )
  }

  def pause(): Future[Unit] = {
    log.debug("pausing server")
    queue.pause()
    status = status.copy(status = Some(ParrotState.Paused))
    Future.Unit
  }

  def resume(): Future[Unit] = {
    log.debug("resuming server")
    queue.resume()
    status = status.copy(status = Some(ParrotState.Running))
    Future.Unit
  }

  def getRootThreadGroup = {
    var result = Thread.currentThread.getThreadGroup
    while (result.getParent != null) {
      result = result.getParent
    }
    result
  }

  def listThreads(group: ThreadGroup, container: Node): Node = {
    val result = <g name={ group.getName } class={ group.getClass.toString }/>
    val threads = new Array[Thread](group.activeCount * 2 + 10)
    val nt = group.enumerate(threads, false)
    var threadNodes = mutable.ListBuffer[Elem]()
    for (i <- 0 until nt) {
      val t = threads(i)
      var methods = mutable.ListBuffer[Elem]()
      t.getStackTrace foreach { e => methods += <method>{ e }</method> }
      threadNodes += <foo name={ t.getName } class={ t.getClass.toString }>{ methods }</foo>
    }
    var ng = group.activeGroupCount
    val groups = new Array[ThreadGroup](ng * 2 + 10)
    ng = group.enumerate(groups, false)
    for (i <- 0 until ng) {
      listThreads(groups(i), result)
    }
    <g name={ group.getName } class={ group.getClass.toString }>{ result }</g>
  }

  def fetchThreadStacks(): Future[String] = {
    val result = <root/>
    listThreads(getRootThreadGroup, result)
    Future.value(result.toString)
  }

  private def wandering(fileName: String): Boolean = {
    val f = new File(fileName)
    val p = f.getParent()
    if (p == null) return false
    val there = try {
      new File(p).getCanonicalPath()
    } catch {
      case e: IOException =>
        log.info("bad log file requested: %s: %s", fileName, e)
        throw new RuntimeException("Can't find parent of log file %s".format(fileName), e)
    }
    there == new File(".").getCanonicalPath()
  }

  def getLog(offset: Long, length: Int, fileName: java.lang.String): Future[ParrotLog] = Future {

    if (wandering(fileName))
      throw new RuntimeException("can only reference files at the top of this sandbox")

    val fl = new File(fileName)
    val sz = fl.length()

    // an offset of -1 means please position me near the end
    var theLen = length
    var theOffset = offset
    if (offset == -1) {
      theLen = 5000
      theOffset = sz - theLen
    }

    val fr = new FileReader(fl)
    val off = math.min(sz, math.max(0, theOffset))
    fr.skip(off)
    val len = math.max(0, math.min(theLen, math.min(Int.MaxValue.toLong, sz - off).toInt))
    var buf = new Array[Char](len)
    fr.read(buf)
    fr.close
    ParrotLog(off, len, new String(buf))
  }
}
