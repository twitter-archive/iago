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

import com.twitter.logging.Formatter
import com.twitter.logging.Handler
import com.twitter.logging.Level
import com.twitter.logging.Logger
import com.twitter.logging.LoggerFactory

/**
 * Set up logging to the console.
 *
 * Normally we'd call this with a prefix indicating which program is setting up
 * console logging. This is to avoid confusion when more than one process is
 * writing to the console.
 *
 * Errors are written to Console.err, other output to Console.out. This looks
 * pretty in Eclipse.
 *
 * Example:
 *
 * ConsoleLogging.start("SERVER") results in every logged message being prefixed
 * with "SERVER "
 *
 * From the REPL:
 *
 * com.twitter.parrot.util.ConsoleLogging.start("repl")
 * com.twitter.logging.Logger.get(getClass).info("hi")
 */
class ConsoleHandler(formatter: Formatter, level: Option[Level])
  extends Handler(formatter, level) {

  def publish(record: java.util.logging.LogRecord) = {
    var x = Console.out
    if (Level.INFO.intValue() < record.getLevel().intValue()) x = Console.err
    x.print(getFormatter().format(record))
  }

  def close() = {}

  def flush() = Console.flush
}

object ConsoleHandler {
  private[this] val log = Logger.get(getClass)

  private[this] def handle(prefix: String, level: Option[Level]) = {
    new ConsoleHandler(new Formatter(prefix = prefix + "%s %s <HH:mm:ss.SSS> ",
      useFullPackageNames = true, truncateStackTracesAt = 999), level)
  }

  def apply(level: Level = Level.INFO, prefix: String = "") =
    (() => handle(prefix, Some(level)))

  def loggers(level0: Level = Level.INFO, prefix: String = "") = {
    List(new LoggerFactory(
      level = Some(level0),
      handlers = List(apply(level0, prefix))))
  }

  def start(level: Level = Level.INFO, prefix: String = "") {
    val level0 = (if (System.getProperty("MVN_CI") == null) level else Level.OFF)
    val prefix0 = (if (prefix == "") "" else prefix + " ")
    Logger.configure(loggers(level0, prefix0))
    log.debug("logging to console enabled")
  }
}
