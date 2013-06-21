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

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.kestrel.protocol.Abort
import com.twitter.finagle.kestrel.protocol.Close
import com.twitter.finagle.kestrel.protocol.CloseAndOpen
import com.twitter.finagle.kestrel.protocol.Get
import com.twitter.finagle.kestrel.protocol.Open
import com.twitter.finagle.kestrel.protocol.Peek
import com.twitter.finagle.kestrel.protocol.Response
import com.twitter.finagle.kestrel.protocol.Set
import com.twitter.finagle.kestrel.protocol.Stored
import com.twitter.finagle.kestrel.protocol.Value
import com.twitter.finagle.kestrel.protocol.Values
import com.twitter.finagle.memcached.{ Server => MemcacheServer }
import com.twitter.io.TempFile
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.Eval
import com.twitter.util.RandomSocket
import com.twitter.util.Time

@RunWith(classOf[JUnitRunner])
class KestrelTransportSpec extends WordSpec with MustMatchers {
  implicit def stringToChannelBuffer(s: String) = ChannelBuffers.wrappedBuffer(s.getBytes)

  "Kestrel Transport" should {
    "work inside a server config" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig(victimPort)
      val server: ParrotServer[ParrotRequest, Response] = new ParrotServerImpl(serverConfig)
      server must not be null
    }

    "send requests to a 'Kestrel' service" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig(victimPort)
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val server = new MemcacheServer(new InetSocketAddress(victimPort))
      server.start()

      val script = List[(String, Response)](
        "set mah-key 0 0 8\r\nDEADBEEF" -> Stored(),
        "set mah-other-key 0 0 8\r\nABADCAFE" -> Stored(),
        "get mah-key\r\n" -> Values(List(Value("mah-key", "DEADBEEF"))))
      script.foreach {
        case (rawCommand, expectedResponse) =>
          val request = new ParrotRequest(rawLine = rawCommand)

          val rep: Response = transport.sendRequest(request).get()

          rep must be(expectedResponse)
      }
      server.stop()
    }
  }

  "KestrelCommandExtractor" should {
    "parse GET commands" in {
      KestrelCommandExtractor.unapply("GET FOO\r\n") must be(Some(Get("FOO", None)))
      KestrelCommandExtractor.unapply("get foo\r\n") must be(Some(Get("foo", None)))
      KestrelCommandExtractor.unapply("get foo") must be(Some(Get("foo", None)))
      KestrelCommandExtractor.unapply("get  foo  \r\n") must be(Some(Get("foo", None)))

      KestrelCommandExtractor.unapply("get foo/t=100\r\n") must be(Some(Get("foo", Some(100.milliseconds))))

      KestrelCommandExtractor.unapply("get foo bar\r\n") must be(None)
      KestrelCommandExtractor.unapply("get") must be(None)
      KestrelCommandExtractor.unapply("get ") must be(None)
    }

    "parse GET command flags" in {
      KestrelCommandExtractor.unapply("get q/abort") must be(Some(Abort("q", None)))
      KestrelCommandExtractor.unapply("get q/close") must be(Some(Close("q", None)))
      KestrelCommandExtractor.unapply("get q/open") must be(Some(Open("q", None)))
      KestrelCommandExtractor.unapply("get q/peek") must be(Some(Peek("q", None)))
      KestrelCommandExtractor.unapply("get q/close/open") must be(Some(CloseAndOpen("q", None)))
      KestrelCommandExtractor.unapply("get q/open/close") must be(Some(CloseAndOpen("q", None)))

      val timeout = Some(100.milliseconds)
      KestrelCommandExtractor.unapply("get q/abort/t=100") must be(Some(Abort("q", None)))
      KestrelCommandExtractor.unapply("get q/close/t=100") must be(Some(Close("q", None)))
      KestrelCommandExtractor.unapply("get q/open/t=100") must be(Some(Open("q", timeout)))
      KestrelCommandExtractor.unapply("get q/peek/t=100") must be(Some(Peek("q", timeout)))
      KestrelCommandExtractor.unapply("get q/close/open/t=100") must be(Some(CloseAndOpen("q", timeout)))
      KestrelCommandExtractor.unapply("get q/open/close/t=100") must be(Some(CloseAndOpen("q", timeout)))

      KestrelCommandExtractor.unapply("get q/say-what-now") must be(None)
    }

    "parse SET commands" in {
      KestrelCommandExtractor.unapply("SET FOO 0 0 8\r\n12345678") must
        be(Some(Set("FOO", Time.fromSeconds(0), "12345678")))

      KestrelCommandExtractor.unapply("set foo 123 100 8\r\n12345678") must
        be(Some(Set("foo", Time.fromSeconds(100), "12345678")))

      KestrelCommandExtractor.unapply("set foo 123 100 10\r\n1234\r\n5678") must
        be(Some(Set("foo", Time.fromSeconds(100), "1234\r\n5678")))

      KestrelCommandExtractor.unapply("set foo 0 0 100\r\n12345678") must be(None)
      KestrelCommandExtractor.unapply("set foo 0 0\r\n1234") must be(None)
    }
  }

  def makeServerConfig(victimPort: Int) = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Response]](
      TempFile.fromResourcePath("/test-kestrel.scala"))
    result.parrotPort = RandomSocket().getPort
    result.victim = result.HostPortListVictim("localhost:" + victimPort)
    result.transport = Some(new KestrelTransport(result))
    result.queue = Some(new RequestQueue(result))
    result
  }
}
