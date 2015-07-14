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
import com.twitter.parrot.util.Uri
import com.twitter.util.Promise

class ParrotRequest(
  val hostHeader: Option[(String, Int)] = None,
  val rawHeaders: Seq[(String, String)] = Nil,
  val uri: Uri = Uri("/", Nil),
  val rawLine: String = "",
  val timestamp: Option[Long] = None,
  val message: ThriftClientRequest = new ThriftClientRequest(Array(), false),
  var response: Promise[_] = new Promise(),
  val cookies: Seq[(String, String)] = Seq(),
  val method: String = "GET",
  val body: String = "",
  val bodyInputStream: Option[java.io.ByteArrayInputStream] = None,
  val weight: Int = 1
  ) {
  val headers: Seq[(String, String)] =
    hostHeader.toSeq.map {
      case (host, port) =>
        ("Host", host + ":" + port.toString)
    } ++ rawHeaders
}
