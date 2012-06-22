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
package com.twitter.parrot.util

import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import org.apache.thrift.TBase

/**
 * OutputBuffers are convenient ways of getting at TProtocols for
 * output to byte arrays. I had to steal this from Finagle.
 */

private[parrot] object OutputBuffer {
  private[parrot] val protocolFactory = new TBinaryProtocol.Factory()

  def messageToArray(message: TBase[_, _]) = {
    val buffer = new OutputBuffer
    message.write(buffer())
    buffer.toArray
  }
}

private[parrot] class OutputBuffer {
  import OutputBuffer._

  private[this] val memoryBuffer = new TMemoryBuffer(512)
  private[this] val oprot = protocolFactory.getProtocol(memoryBuffer)

  def apply() = oprot

  def toArray = {
    oprot.getTransport.flush()
    java.util.Arrays.copyOfRange(
      memoryBuffer.getArray, 0, memoryBuffer.length())
  }
}

