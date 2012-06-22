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

import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.processor.{SimpleRecordProcessor, RecordProcessorFactory}
import com.twitter.parrot.thrift._
import com.twitter.util._
import com.twitter.conversions.time._
import java.util.ArrayList
import org.jboss.netty.handler.codec.http.HttpResponse
import org.specs.SpecificationWithJUnit

class ParrotServerSpec extends SpecificationWithJUnit {
  val log = Logger.get(getClass.getName)
  val config = {
    val eval = new Eval
    val configFile = TempFile.fromResourcePath("/test-server.scala")
    eval[ParrotServerConfig[ParrotRequest, HttpResponse]](configFile)
  }
  val transport = config.transport match {
    case Some(dt: DumbTransport) => dt
    case _ => throw new Exception("Wrong ParrotTransport, test will fail")
  }

  val server: ParrotServer[ParrotRequest, HttpResponse] = new ParrotServerImpl(config)
  server.start()

  RecordProcessorFactory.registerProcessor("default", new SimpleRecordProcessor(config.service.get, config))

  def makeJob() = {
    val victims = new ArrayList[TargetHost]()
    victims.add(new TargetHost("http", "localhost", 0))

    val result = new ParrotJob()
    result.setVictims(victims)
    result.setName("ParrotServerSpec")
    result.setArrivalRate(1)
  }

  "ParrotServer" should {
    val emptyLines = new ArrayList[String]()
    val defaultLines = new ArrayList[String]()
    defaultLines.add("/search.json?q=%23playerofseason&since_id=68317051210563584&rpp=30")

    def resolve[A](future: Future[A]): A = {
      future.get(1.second) match {
        case Return(res) => res
        case Throw(t) => throw t
      }
    }

    val defaultJob = makeJob()
    val defaultJobRef = resolve(server.createJob(defaultJob))

    "create its first job with id 0" in {
      defaultJobRef.getJobId must_== 0
    }

    "not try to do anything with an empty list" in {
      val response = resolve(server.sendRequest(defaultJobRef, emptyLines))
      response.getLinesProcessed must_== 0
    }

    "allow us to send a single, valid request" in {
      val response = resolve(server.sendRequest(defaultJobRef, defaultLines))
      response.getLinesProcessed must_== 1
      transport.sent must eventually(be_==(1))
    }

    "support multiple jobs" in {
      val response0 = resolve(server.sendRequest(defaultJobRef, defaultLines))
      val response1 = resolve(server.sendRequest(defaultJobRef, defaultLines))

      response0.getLinesProcessed must_== 1
      response1.getLinesProcessed must_== 1

      transport.sent must eventually(be_==(2))
    }

    "not support jobs that don't exist" in {
      val response0 = resolve(server.sendRequest(defaultJobRef, defaultLines))
      val response1 = resolve(server.sendRequest(new ParrotJobRef(1), defaultLines))
      response0.getLinesProcessed must_== 1
      response1.getLinesProcessed must_== 0
      response1.getStatus must_== ParrotState.UNKNOWN

      transport.sent must eventually(be_==(1))
    }

    "support being paused and resumed" in {
      server.pause()

      val response = resolve(server.sendRequest(defaultJobRef, defaultLines))
      response.status must_== ParrotState.PAUSED
      transport.sent must_== 0

      server.resume()

      transport.sent must eventually(be_==(1))
    }

    "support being shutdown" in {
      server.shutdown()

      val response = resolve(server.sendRequest(defaultJobRef, defaultLines))
      response.status must_== ParrotState.SHUTDOWN
      transport.sent must_== 0
    }
  }
}
