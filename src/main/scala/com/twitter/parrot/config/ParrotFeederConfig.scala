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
package com.twitter.parrot.config

import com.twitter.conversions.time._
import com.twitter.logging.{Logger, LoggerFactory}
import com.twitter.ostrich.admin._
import com.twitter.parrot.feeder.{LogSource, LogSourceImpl, ParrotFeeder}
import com.twitter.util.{Duration, Config}
import java.util.concurrent.TimeUnit

trait ParrotFeederConfig extends Config[RuntimeEnvironment => Service]
  with ParrotCommonConfig
{
  var batchSize = 1000
  var busyCutoff = 10000
  var duration = Duration(0, TimeUnit.MILLISECONDS)
  var httpPort = 9993
  var inputLog: String = ""
  var jobName = "parrot"
  var linesToSkip = 0
  var maxRequests = 0
  var numInstances = 1
  var numThreads = 0
  var pollInterval = Duration(1, TimeUnit.SECONDS)
  var requestRate = 1
  var reuseFile = false
  var statsName = "parrot-feeder"

  // runtime configured
  var logSource: Option[LogSource] = None

  def apply() = { (runtime: RuntimeEnvironment) =>
    val adminPort = runtime.arguments.get("httpPort").map(_.toInt).getOrElse(httpPort)

    Logger.configure(loggers)

    inputLog = runtime.arguments.getOrElse("log", inputLog)

    var admin = new AdminServiceFactory (
      adminPort,
      statsNodes = new StatsFactory(
        reporters = new JsonStatsLoggerFactory(
          period = 1.minute,
          serviceName = statsName
        ) :: new TimeSeriesCollectorFactory()
      )
    )(runtime)

    val feeder = new ParrotFeeder(this)
    ServiceTracker.register(feeder)

    if (this.logSource == None) {
      this.logSource = Some(new LogSourceImpl(this.inputLog))
    }
    feeder
  }
}
