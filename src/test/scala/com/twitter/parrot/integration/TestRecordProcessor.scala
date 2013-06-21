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
package com.twitter.parrot.integration

import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotService
import com.twitter.parrot.util.UriParser
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.parrot.processor.RecordProcessor

/**
 * This processor just takes a line-separated list of URIs and turns them into requests, for instance:
 * /search.json?q=%23playerofseason&since_id=68317051210563584&rpp=30
 * needs to have the target scheme, host, and port included, but otherwise is a complete URL
 * Empty lines and lines starting with '#' will be ignored.
 */

class TestRecordProcessor(service: ParrotService[ParrotRequest, HttpResponse],
  config: ParrotServerConfig[ParrotRequest, HttpResponse])
  extends RecordProcessor {
  private[this] val log = Logger.get(getClass)
  private[this] var exceptionCount = 0
  private[this] val hostHeader = Some((config.httpHostHeader, config.httpHostHeaderPort))
  var responded = false
  var properlyShutDown = false

  def processLines(lines: Seq[String]) {
    log.trace("SimpleRecordProcessor.processLines: processing %d lines", lines.size)
    for(line <- lines) {
      val p = UriParser(line)
      log.trace("SimpleRecordProcessor.processLines: line is %s", line)
      UriParser(line) match {
        case Return(uri) =>
          if (!uri.path.isEmpty && !line.startsWith("#"))
            service(new ParrotRequest(hostHeader, Nil, uri, line)) respond { response =>
              log.debug("response was %s", response.toString)
              responded = true
          }
        case Throw(t) =>
          if (exceptionCount < 3)
            log.warning("exception\n\t%s\nwhile processing line\n\t%s",t.getMessage(), line)
          else if (exceptionCount == 3) log.warning("more exceptions ...")
          exceptionCount += 1
          Stats.incr("bad_lines")
          Stats.incr("bad_lines/" + t.getClass.getName)
      }
    }
  }
  override def shutdown() {
    properlyShutDown = true
  }
}
