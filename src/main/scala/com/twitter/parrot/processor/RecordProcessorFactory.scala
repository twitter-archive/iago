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
package com.twitter.parrot.processor

import com.twitter.logging.Logger
import com.twitter.parrot.thrift.ParrotJob
import collection.mutable

object RecordProcessorFactory {
  val log = Logger.get(getClass)
  private[this] val processors = mutable.Map[String, RecordProcessor]()

  def getProcessorForJob(job: ParrotJob): RecordProcessor = {
    processors.getOrElse(job.processor,
      processors.getOrElse("default", new RecordProcessor() {
        def processLines(job: ParrotJob, lines: Seq[String]) {
          throw new Exception("No default record processor found! Config screwed up.")
        }
      }))
  }

  def registerProcessor(name: String, processor: RecordProcessor) {
    log.info("Registered processor '%s'", name)
    processors += (name -> processor)
  }
}
