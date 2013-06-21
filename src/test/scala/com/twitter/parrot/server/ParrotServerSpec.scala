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

import java.util.ArrayList
import java.util.concurrent.TimeUnit
import org.jboss.netty.handler.codec.http.HttpResponse
import org.junit.runner.RunWith
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers
import com.twitter.logging.Logger
import com.twitter.parrot.processor.SimpleRecordProcessor
import com.twitter.parrot.thrift.ParrotState
import com.twitter.util.Duration
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import org.scalatest.time.Span
import org.scalatest.time.Seconds

@RunWith(classOf[JUnitRunner])
class ParrotServerSpec extends WordSpec with MustMatchers with OneInstancePerTest with Eventually with ServerFixture {
  val log = Logger.get(getClass.getName)

  val server: ParrotServer[ParrotRequest, HttpResponse] = new ParrotServerImpl(config)
  config.loadTestInstance = Some(new SimpleRecordProcessor(config.service.get, config))
  server.start()

  "ParrotServer" should {
    val emptyLines = new ArrayList[String]()
    val defaultLines = new ArrayList[String]()
    defaultLines.add("/search.json?q=%23playerofseason&since_id=68317051210563584&rpp=30")

    def resolve[A](future: Future[A]): A = {
      future.get(Duration(1000, TimeUnit.MILLISECONDS)) match {
        case Return(res) => res
        case Throw(t)    => throw t
      }
    }

    "not try to do anything with an empty list" in {
      val response = resolve(server.sendRequest(emptyLines))
      response.getLinesProcessed must be(0)
    }

    "allow us to send a single, valid request" in {
      val response = resolve(server.sendRequest(defaultLines))
      response.getLinesProcessed must be(1)
      eventually { transport.sent must be(1) }
    }

    "support being paused and resumed" in {
      server.pause()
      val response = resolve(server.sendRequest(defaultLines))
      response.status must be(ParrotState.PAUSED)
      transport.sent must be(0)

      server.resume()
      eventually(timeout(Span(2, Seconds))) {
        transport.sent must be(1)
      }
    }

    "support being shutdown" in {
      server.shutdown()

      val response = resolve(server.sendRequest(defaultLines))
      response.status must be(ParrotState.SHUTDOWN)
      // transport.sent must be(1)
    }
  }
}
