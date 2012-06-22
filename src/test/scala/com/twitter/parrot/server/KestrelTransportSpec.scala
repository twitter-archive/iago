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

import com.twitter.finagle.kestrel.protocol._
import com.twitter.finagle.memcached.{Server => MemcacheServer}
import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.thrift.TargetHost
import com.twitter.parrot.util.OutputBuffer
import com.twitter.util.{RandomSocket, Duration, Eval, Time}
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.buffer.ChannelBuffers
import org.specs.SpecificationWithJUnit

class KestrelTransportSpec extends SpecificationWithJUnit {
  implicit def stringToChannelBuffer(s: String) = ChannelBuffers.wrappedBuffer(s.getBytes)

  "Kestrel Transport" should {
    "work inside a server config" in {
      val serverConfig = makeServerConfig()
      val server: ParrotServer[ParrotRequest, Response] = new ParrotServerImpl(serverConfig)
      server mustNotBe null
    }

    "send requests to a 'Kestrel' service" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig()
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      val server = new MemcacheServer(new InetSocketAddress(victimPort))
      server.start()

      val script = List[(String, Response)](
        "set mah-key 0 0 8\r\nDEADBEEF"       -> Stored(),
        "set mah-other-key 0 0 8\r\nABADCAFE" -> Stored(),
        "get mah-key\r\n"                     -> Values(List(Value("mah-key", "DEADBEEF"))))
      script.foreach { case (rawCommand, expectedResponse) =>
        val request = new ParrotRequest(new TargetHost("", "localhost", victimPort), rawLine = rawCommand)

        val rep: Response = transport.sendRequest(request).get()

        rep mustEqual expectedResponse
      }
      server.stop()
    }
  }

  "KestrelCommandExtractor" should {
    "parse GET commands" in {
      KestrelCommandExtractor.unapply("GET FOO\r\n")    mustEqual Some(Get("FOO", None))
      KestrelCommandExtractor.unapply("get foo\r\n")    mustEqual Some(Get("foo", None))
      KestrelCommandExtractor.unapply("get foo")        mustEqual Some(Get("foo", None))
      KestrelCommandExtractor.unapply("get  foo  \r\n") mustEqual Some(Get("foo", None))

      val timeout = Duration(100, TimeUnit.MILLISECONDS)
      KestrelCommandExtractor.unapply("get foo/t=100\r\n") mustEqual Some(Get("foo", Some(timeout)))

      KestrelCommandExtractor.unapply("get foo bar\r\n") mustEqual None
      KestrelCommandExtractor.unapply("get")             mustEqual None
      KestrelCommandExtractor.unapply("get ")            mustEqual None
    }

    "parse SET commands" in {
      KestrelCommandExtractor.unapply("SET FOO 0 0 8\r\n12345678") mustEqual
        Some(Set("FOO", Time.fromSeconds(0), "12345678"))

      KestrelCommandExtractor.unapply("set foo 123 100 8\r\n12345678") mustEqual
        Some(Set("foo", Time.fromSeconds(100), "12345678"))

      KestrelCommandExtractor.unapply("set foo 123 100 10\r\n1234\r\n5678") mustEqual
        Some(Set("foo", Time.fromSeconds(100), "1234\r\n5678"))

      KestrelCommandExtractor.unapply("set foo 0 0 100\r\n12345678") mustEqual None
      KestrelCommandExtractor.unapply("set foo 0 0\r\n1234")         mustEqual None
    }
  }

  def makeServerConfig() = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Response]](
      TempFile.fromResourcePath("/test-kestrel.scala")
    )
    result.parrotPort = RandomSocket().getPort
    result.transport = Some(new KestrelTransport(Some(result)))
    result
  }
}
