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
package com.twitter.parrot.feeder

import com.hadoop.compression.lzo.LzopCodec
import java.io.FileInputStream
import org.apache.hadoop.conf.Configuration

class LzoFileLogSource(filename: String) extends LogSource {
  private[this] var source = init()

  private[this] def init(): Iterator[String] = {
    val codec = new LzopCodec
    codec.setConf(new Configuration)
    val decompressedStream = codec.createInputStream(new FileInputStream(filename))
    io.Source.fromInputStream(decompressedStream)("UTF-8").getLines()
  }

  def next(): String = source.next()
  def hasNext: Boolean = source.hasNext

  def reset() {
    source = init()
  }
}
