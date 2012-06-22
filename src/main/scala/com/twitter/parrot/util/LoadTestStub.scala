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

import com.twitter.parrot.processor.RecordProcessor
import com.twitter.parrot.server.{ParrotService, ParrotRequest}
import com.twitter.parrot.thrift.ParrotJob
import org.jboss.netty.handler.codec.http._
import util.Random

class LoadTestStub(service: ParrotService[ParrotRequest, HttpResponse]) extends RecordProcessor {
  val rnd = new Random(System.currentTimeMillis())
  def processLines(job: ParrotJob, lines: Seq[String]) {
    val target = job.victims.get(rnd.nextInt(job.victims.size))
    lines foreach { line =>
      service(new ParrotRequest(target, rawLine = line))
    }
  }
}
