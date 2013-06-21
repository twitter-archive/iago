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

import org.jboss.netty.handler.codec.http.HttpResponse

import com.twitter.logging.Logger
import com.twitter.parrot.processor.RecordProcessor
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotService

class LoadTestStub(service: ParrotService[ParrotRequest, HttpResponse]) extends RecordProcessor {
  val log = Logger.get(getClass.getName)
  def processLines(lines: Seq[String]) {
    lines foreach { line =>
      log.debug("LoadTestStub: " + line)
      service(new ParrotRequest(rawLine = line))
    }
  }
}
