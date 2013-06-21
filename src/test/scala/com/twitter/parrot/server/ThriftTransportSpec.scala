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
import com.twitter.io.TempFile
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.integration.EchoServer
import com.twitter.parrot.util.OutputBuffer
import com.twitter.util.{ RandomSocket, Eval }
import java.util.concurrent.atomic.AtomicInteger
import org.apache.thrift.protocol._
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers
import org.scalatest.OneInstancePerTest

@RunWith(classOf[JUnitRunner])
class ThriftTransportSpec extends WordSpec with MustMatchers with OneInstancePerTest {
  val log = Logger.get(getClass)
  var seqId = new AtomicInteger(0)

  "Thrift Transport" should {
    "work inside a server config" in {
      val victimPort = RandomSocket.nextPort()
      val serverConfig = makeServerConfig(victimPort)
      val server: ParrotServer[ParrotRequest, Array[Byte]] = new ParrotServerImpl(serverConfig)
      server must not be null
    }

    "send requests to a Thrift service" in {
      val victimPort = RandomSocket.nextPort()
      val message = new ThriftClientRequest(serialize("echo", "message", "hello"), false)
      val request = new ParrotRequest(message = message)
      val serverConfig = makeServerConfig(victimPort)

      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      EchoServer.serve(victimPort)

      val rep: Array[Byte] = transport.sendRequest(request).get()

      EchoServer.getRequestCount must not be 0
      rep.containsSlice("hello".getBytes) must be(true)
    }
  }

  def makeServerConfig(victimPort: Int) = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Array[Byte]]](
      TempFile.fromResourcePath("/test-thrift.scala"))
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.victim = result.HostPortListVictim("localhost:" + victimPort)
    result.transport = Some(new ThriftTransport(result))
    result.queue = Some(new RequestQueue(result))
    result
  }

  def serialize(method: String, field: String, msg: String) = {
    val oBuffer = new OutputBuffer
    oBuffer().writeMessageBegin(new TMessage(method, TMessageType.CALL, seqId.incrementAndGet()))
    oBuffer().writeStructBegin(new TStruct(method + "_args"))
    oBuffer().writeFieldBegin(new TField(field, TType.STRING, 1))
    oBuffer().writeString(msg)
    oBuffer().writeFieldEnd()
    oBuffer().writeFieldStop()
    oBuffer().writeStructEnd()
    oBuffer().writeMessageEnd()
    oBuffer.toArray
  }
}
