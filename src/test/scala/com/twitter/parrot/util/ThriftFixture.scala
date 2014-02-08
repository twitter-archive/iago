/*
Copyright 2013 Twitter, Inc.

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

package com.twitter.parrot.util

import java.util.concurrent.atomic.AtomicInteger
import org.apache.thrift.protocol.TField
import org.apache.thrift.protocol.TMessage
import org.apache.thrift.protocol.TMessageType
import org.apache.thrift.protocol.TStruct
import org.apache.thrift.protocol.TType

trait ThriftFixture {
  
  private var seqId = new AtomicInteger(0)

  protected def serialize(method: String, field: String, msg: String): Array[Byte] = {
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
