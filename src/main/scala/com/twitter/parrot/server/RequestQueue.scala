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
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.ParrotJob
import com.twitter.util._
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

class RequestQueue[Req <: ParrotRequest, Rep](config: ParrotServerConfig[Req, Rep]) {
  private[this] val log = Logger.get(getClass)
  private[this] val jobs = new mutable.HashMap[ParrotJob, RequestConsumer[Req]]()
  private[this] val running = new AtomicBoolean(false)

  private[this] lazy val transport = config.transport.getOrElse(throw new Exception("Unconfigured transport"))

  def addRequest(job: ParrotJob, request: Req, response: Future[_]) {
    if (jobs.get(job) == None) {
      addJob(job)
    }
    jobs.get(job).foreach( _.offer(request) )
  }

  def addRequest(job: ParrotJob, req: Req): Promise[Rep] = {
    val response = new Promise[Rep]()
    req.response = response
    addRequest(job, req, response)
    response
  }

  private[this] def addJob(job: ParrotJob) {
    val consumer = new RequestConsumer[Req](config, job)
    jobs.put(job, consumer)
    if (running.get) {
      consumer.start()
    }
  }

  def pause() {
    running.set(false)
    jobs.values.foreach( _.pause() )
  }

  def resume() {
    running.set(true)
    jobs.values.foreach( _.start() )
  }

  def jobChanged(job: ParrotJob) {
    log.debug("jobChanged called on RequestQueue")
    jobs.get(job) foreach {
      log.debug("Calling jobChanged on job")
      _.jobChanged(job)
    }
  }

  def start() {
    log.debug("starting RequestQueue")
    running.set(true)
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
  }

  def queueDepth = jobs.values.foldLeft(0.0)(_+_.size)
  def totalProcessed = jobs.values.foldLeft(0)(_+_.totalProcessed)
  def clockError = jobs.values.foldLeft(0.0)(_+_.clockError)

  def shutdown() {

    running.set(false)
    jobs.values.foreach( _.pause() )
  }
}
