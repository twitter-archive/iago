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
import com.twitter.parrot.thrift.TargetHost
import com.twitter.parrot.util.OutputBuffer
import com.twitter.util.{RandomSocket, Eval}
import java.util.concurrent.atomic.AtomicInteger
import org.apache.thrift.protocol._
import org.specs.SpecificationWithJUnit

class ThriftTransportSpec extends SpecificationWithJUnit {
  val log = Logger.get(getClass)
  var seqId = new AtomicInteger(0)

  "Thrift Transport" should {
    "work inside a server config" in {
      val serverConfig = makeServerConfig()
      val server: ParrotServer[ParrotRequest, Array[Byte]] = new ParrotServerImpl(serverConfig)
      server mustNotBe null
    }

    "send requests to a Thrift service" in {
      val victimPort = RandomSocket.nextPort()
      val message = new ThriftClientRequest(serialize("echo", "message", "hello"), false)
      val request = new ParrotRequest(new TargetHost("", "localhost", victimPort), message = message)
      val serverConfig = makeServerConfig()
      val transport = serverConfig.transport.getOrElse(fail("no transport configured"))

      EchoServer.serve(victimPort)

      val rep: Array[Byte] = transport.sendRequest(request).get()

      EchoServer.getRequestCount mustNotBe 0
      rep.containsSlice("hello".getBytes) mustBe true
    }
  }

  def makeServerConfig() = {
    val result = new Eval().apply[ParrotServerConfig[ParrotRequest, Array[Byte]]](
      TempFile.fromResourcePath("/test-thrift.scala")
    )
    result.parrotPort = RandomSocket().getPort
    result.thriftServer = Some(new ThriftServerImpl)
    result.transport = Some(new ThriftTransport(Some(result)))
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
