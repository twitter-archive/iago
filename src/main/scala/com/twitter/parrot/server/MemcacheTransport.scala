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

import com.twitter.finagle.Service
import com.twitter.finagle.memcached.protocol.text.Memcached
import com.twitter.finagle.memcached.protocol.{Command, Get, Gets, Response, Set}
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.Time
import org.jboss.netty.buffer.ChannelBuffers

object MemcacheCommandExtractor extends MemcacheLikeCommandExtractor[Command] {
  // currently handles get, gets & set only
  def unapply(rawCommand: String): Option[Command] = {
    val delim = rawCommand.indexOf("\r\n")
    val (command, data) =
      if (delim == -1)  {
        (rawCommand, None)
      } else {
        (rawCommand.substring(0, delim), Some(rawCommand.substring(delim + 2)))
      }

    val tokens = command.split("\\s+")
    tokens(0).toLowerCase match {
      case "get" => get(tokens.drop(1))
      case "gets" => gets(tokens.drop(1))
      case "set" => set(tokens.drop(1), data)
      case _ => None
    }
  }

  private def get(keys: Array[String]): Option[Get] =
    keys match {
      case Array() => None
      case _ =>
        val buffers = keys.map { key => ChannelBuffers.wrappedBuffer(key.getBytes) }
        Some(Get(buffers))
    }

  private def gets(keys: Array[String]): Option[Gets] =
    keys match {
      case Array() => None
      case _ =>
        val buffers = keys.map { key => ChannelBuffers.wrappedBuffer(key.getBytes) }
        Some(Gets(buffers))
    }

  private def set(args: Array[String], data: Option[String]): Option[Set] =
    args match {
      case a if a.length < 4 => None
      case _ =>
        val key = ChannelBuffers.wrappedBuffer(args(0).getBytes)
        val flags = args(1).toInt
        val expiry = Time.fromSeconds(args(2).toInt)
        val byteCount = args(3).toInt
        val bytes = data.getOrElse("").getBytes

        if (bytes.length != byteCount) {
          None
        } else {
          Some(Set(key, flags, expiry, ChannelBuffers.wrappedBuffer(bytes)))
        }
    }
}

object MemcacheTransportFactory extends MemcacheLikeTransportFactory[Command, Response] {
  override def codec() = Memcached()
  override def fromService(service: Service[Command, Response]) = new MemcacheTransport(service)
}

class MemcacheTransport(service: Service[Command, Response])
  extends MemcacheLikeTransport[Command, Response](MemcacheCommandExtractor, service)
