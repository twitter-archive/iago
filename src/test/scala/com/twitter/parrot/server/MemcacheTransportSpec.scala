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

import java.net.InetSocketAddress

import org.jboss.netty.buffer.ChannelBuffers
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.finagle.memcached.{ Server => MemcacheServer }
import com.twitter.finagle.memcached.protocol.Get
import com.twitter.finagle.memcached.protocol.Gets
import com.twitter.finagle.memcached.protocol.Response
import com.twitter.finagle.memcached.protocol.Set
import com.twitter.finagle.memcached.protocol.Stored
import com.twitter.finagle.memcached.protocol.Value
import com.twitter.finagle.memcached.protocol.Values
import com.twitter.io.TempFile
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.Eval
import com.twitter.util.RandomSocket
import com.twitter.util.Time

@RunWith(classOf[JUnitRunner])
class MemcacheTransportSpec extends WordSpec with MustMatchers {
  implicit def stringToChannelBuffer(s: String) = ChannelBuffers.wrappedBuffer(s.getBytes)

  if(!sys.props.contains("SKIP_FLAKY")) {
    "Memcache Transport" should {
      "work inside a server config" in {
        val victimPort = RandomSocket.nextPort()
        val serverConfig = makeServerConfig(victimPort)
        val server: ParrotServer[ParrotRequest, Response] = new ParrotServerImpl(serverConfig)
        server must not be null
      }

      "send requests to a Memcache service" in {
        val victimPort = RandomSocket.nextPort()
        val serverConfig = makeServerConfig(victimPort)
        val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

        val server = new MemcacheServer(new InetSocketAddress(victimPort))
        server.start()

        val script = List[(String, Response)](
          "set mah-key 0 0 8\r\nDEADBEEF" -> Stored(),
          "set mah-other-key 0 0 8\r\nABADCAFE" -> Stored(),
          "get mah-key\r\n" -> Values(List(Value("mah-key", "DEADBEEF", None, Some("0")))),
          "gets mah-key mah-other-key\r\n" -> Values(List(Value("mah-key", "DEADBEEF", None, Some("0")),
            Value("mah-other-key", "ABADCAFE", None, Some("0")))))
        script.foreach {
          case (rawCommand, expectedResponse) =>
            val request = new ParrotRequest(rawLine = rawCommand)
            val resp: Response = transport.sendRequest(request).get()
            resp must be(expectedResponse)
        }
        server.stop()
      }
    }

    "MemcacheCommandExtractor" should {
      "parse GET commands" in {
        MemcacheCommandExtractor.unapply("GET FOO\r\n") must be(Some(Get(Seq("FOO"))))
        MemcacheCommandExtractor.unapply("get foo\r\n") must be(Some(Get(Seq("foo"))))
        MemcacheCommandExtractor.unapply("get foo") must be(Some(Get(Seq("foo"))))
        MemcacheCommandExtractor.unapply("get  foo  \r\n") must be(Some(Get(Seq("foo"))))

        MemcacheCommandExtractor.unapply("get foo bar\r\n") must be(Some(Get(Seq("foo", "bar"))))
        MemcacheCommandExtractor.unapply("get foo bar") must be(Some(Get(Seq("foo", "bar"))))
        MemcacheCommandExtractor.unapply("get  foo  bar  \r\n") must be(Some(Get(Seq("foo", "bar"))))

        MemcacheCommandExtractor.unapply("get") must be(None)
        MemcacheCommandExtractor.unapply("get ") must be(None)
      }

      "parse GETS commands" in {
        MemcacheCommandExtractor.unapply("GETS FOO\r\n") must be(Some(Gets(Seq("FOO"))))
        MemcacheCommandExtractor.unapply("gets foo\r\n") must be(Some(Gets(Seq("foo"))))
        MemcacheCommandExtractor.unapply("gets foo") must be(Some(Gets(Seq("foo"))))
        MemcacheCommandExtractor.unapply("gets  foo  \r\n") must be(Some(Gets(Seq("foo"))))

        MemcacheCommandExtractor.unapply("gets FOO BAR\r\n") must be(Some(Gets(Seq("FOO", "BAR"))))
        MemcacheCommandExtractor.unapply("gets foo bar\r\n") must be(Some(Gets(Seq("foo", "bar"))))
        MemcacheCommandExtractor.unapply("gets foo bar") must be(Some(Gets(Seq("foo", "bar"))))
        MemcacheCommandExtractor.unapply("gets  foo  bar  \r\n") must be(Some(Gets(Seq("foo", "bar"))))

        MemcacheCommandExtractor.unapply("gets") must be(None)
        MemcacheCommandExtractor.unapply("gets ") must be(None)
      }

      "parse SET commands" in {
        MemcacheCommandExtractor.unapply("SET FOO 0 0 8\r\n12345678") must
          be(Some(Set("FOO", 0, Time.fromSeconds(0), "12345678")))

        MemcacheCommandExtractor.unapply("set foo 123 100 8\r\n12345678") must
          be(Some(Set("foo", 123, Time.fromSeconds(100), "12345678")))

        MemcacheCommandExtractor.unapply("set foo 123 100 10\r\n1234\r\n5678") must
          be(Some(Set("foo", 123, Time.fromSeconds(100), "1234\r\n5678")))

        MemcacheCommandExtractor.unapply("set foo 0 0 100\r\n12345678") must be(None)
        MemcacheCommandExtractor.unapply("set foo 0 0\r\n1234") must be(None)
      }
    }
  }

  def makeServerConfig(victimPort: Int) = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Response]](
      TempFile.fromResourcePath("/test-memcache.scala"))
    result.parrotPort = RandomSocket().getPort
    result.victim = result.HostPortListVictim("localhost:" + victimPort)
    result.transport = Some(MemcacheTransportFactory(result))
    result
  }
}
