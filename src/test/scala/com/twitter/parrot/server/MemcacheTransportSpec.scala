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

import com.twitter.finagle.memcached.protocol._
import com.twitter.finagle.memcached.{Server => MemcacheServer}
import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.TargetHost
import com.twitter.parrot.util.OutputBuffer
import com.twitter.util.{RandomSocket, Eval, Time}
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.specs.SpecificationWithJUnit

class MemcacheTransportSpec extends SpecificationWithJUnit {
  implicit def stringToChannelBuffer(s: String) = ChannelBuffers.wrappedBuffer(s.getBytes)

  "Memcache Transport" should {
    "work inside a server config" in {
      val serverConfig = makeServerConfig()
      val server: ParrotServer[ParrotRequest, Response] = new ParrotServerImpl(serverConfig)
      server mustNotBe null
    }

    "send requests to a Memcache service" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig()
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val server = new MemcacheServer(new InetSocketAddress(victimPort))
      server.start()

      val script = List[(String, Response)](
        "set mah-key 0 0 8\r\nDEADBEEF"       -> Stored(),
        "set mah-other-key 0 0 8\r\nABADCAFE" -> Stored(),
        "get mah-key\r\n"                     -> Values(List(Value("mah-key",       "DEADBEEF", None, Some("0")))),
        "gets mah-key mah-other-key\r\n"      -> Values(List(Value("mah-key",       "DEADBEEF", None, Some("0")),
                                                             Value("mah-other-key", "ABADCAFE", None, Some("0")))))
      script.foreach { case (rawCommand, expectedResponse) =>
        val request = new ParrotRequest(new TargetHost("", "localhost", victimPort), rawLine = rawCommand)

        val resp: Response = transport.sendRequest(request).get()

        resp mustEqual expectedResponse
      }
      server.stop()
    }
  }

  "MemcacheCommandExtractor" should {
    "parse GET commands" in {
      MemcacheCommandExtractor.unapply("GET FOO\r\n")    mustEqual Some(Get(Seq("FOO")))
      MemcacheCommandExtractor.unapply("get foo\r\n")    mustEqual Some(Get(Seq("foo")))
      MemcacheCommandExtractor.unapply("get foo")        mustEqual Some(Get(Seq("foo")))
      MemcacheCommandExtractor.unapply("get  foo  \r\n") mustEqual Some(Get(Seq("foo")))

      MemcacheCommandExtractor.unapply("get foo bar\r\n")     mustEqual Some(Get(Seq("foo", "bar")))
      MemcacheCommandExtractor.unapply("get foo bar")         mustEqual Some(Get(Seq("foo", "bar")))
      MemcacheCommandExtractor.unapply("get  foo  bar  \r\n") mustEqual Some(Get(Seq("foo", "bar")))

      MemcacheCommandExtractor.unapply("get")  mustEqual None
      MemcacheCommandExtractor.unapply("get ") mustEqual None
    }

    "parse GETS commands" in {
      MemcacheCommandExtractor.unapply("GETS FOO\r\n")    mustEqual Some(Gets(Seq("FOO")))
      MemcacheCommandExtractor.unapply("gets foo\r\n")    mustEqual Some(Gets(Seq("foo")))
      MemcacheCommandExtractor.unapply("gets foo")        mustEqual Some(Gets(Seq("foo")))
      MemcacheCommandExtractor.unapply("gets  foo  \r\n") mustEqual Some(Gets(Seq("foo")))

      MemcacheCommandExtractor.unapply("gets FOO BAR\r\n")     mustEqual Some(Gets(Seq("FOO", "BAR")))
      MemcacheCommandExtractor.unapply("gets foo bar\r\n")     mustEqual Some(Gets(Seq("foo", "bar")))
      MemcacheCommandExtractor.unapply("gets foo bar")         mustEqual Some(Gets(Seq("foo", "bar")))
      MemcacheCommandExtractor.unapply("gets  foo  bar  \r\n") mustEqual Some(Gets(Seq("foo", "bar")))

      MemcacheCommandExtractor.unapply("gets")  mustEqual None
      MemcacheCommandExtractor.unapply("gets ") mustEqual None
    }

    "parse SET commands" in {
      MemcacheCommandExtractor.unapply("SET FOO 0 0 8\r\n12345678") mustEqual
        Some(Set("FOO", 0, Time.fromSeconds(0), "12345678"))

      MemcacheCommandExtractor.unapply("set foo 123 100 8\r\n12345678") mustEqual
        Some(Set("foo", 123, Time.fromSeconds(100), "12345678"))

      MemcacheCommandExtractor.unapply("set foo 123 100 10\r\n1234\r\n5678") mustEqual
        Some(Set("foo", 123, Time.fromSeconds(100), "1234\r\n5678"))

      MemcacheCommandExtractor.unapply("set foo 0 0 100\r\n12345678") mustEqual None
      MemcacheCommandExtractor.unapply("set foo 0 0\r\n1234")         mustEqual None
    }
  }

  def makeServerConfig() = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Response]](
      TempFile.fromResourcePath("/test-memcache.scala")
    )
    result.parrotPort = RandomSocket().getPort
    result.transport = Some(new MemcacheTransport(Some(result)))
    result
  }
}
