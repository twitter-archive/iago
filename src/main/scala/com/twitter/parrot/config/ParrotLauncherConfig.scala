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

import com.twitter.logging.LoggerFactory
import com.twitter.parrot.launcher.{CommandRunner, ParrotLauncher}
import com.twitter.util.Config

trait ParrotLauncherConfig extends Config[ParrotLauncher] with ParrotCommonConfig {
  var loggers: List[LoggerFactory] = Nil
  var batchSize = 1000
  var customLogSource = "" // Raw scala can be inserted in here and will end up in the Feeder config
  var distDir = "."
  var doConfirm = true
  var doOAuth = true
  var duration = 5
  var header = ""
  var hostConnectionCoresize = 1
  var hostConnectionIdleTimeInMs = 60000 // 1m
  var hostConnectionLimit = Integer.MAX_VALUE
  var hostConnectionMaxIdleTimeInMs = 300000 // 5m
  var hostConnectionMaxLifeTimeInMs = Integer.MAX_VALUE
  var jobName = required[String]
  var localMode = false
  var log = required[String]
  var maxPerHost = 1
  var maxRequests = 1000
  var numCpus = 1.0
  var numFeederInstances = 1
  var numInstances = 1
  var parser = "http"
  var port = required[Int]
  var requestRate = 1
  var reuseConnections = true
  var reuseFile = true
  var role = ""
  var scheme = "http"
  var serverXmx = 4000
  var suppressUrlMap = true
  var thriftClientId = ""
  var timeUnit = "MINUTES"
  var verboseCmd = false
  var victims = required[String]


  // Extension points
  var imports =
"""import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.parrot.util.LoadTestStub
  """
  var requestType = "ParrotRequest"
  var responseType = "HttpResponse"
  var transport = "FinagleTransport"
  var loadTest = "new LoadTestStub(service.get)"

  // e.g., createDistribution = "createDistribution = { rate => new MyDistribution(rate) }"
  // see also imports, must create a subclass of RequestDistribution
  var createDistribution = ""

  // Override these at your own risk
  val parrotTasks = List("server", "feeder")

  def apply() = {
    val missing = missingValues
    if (missing.isEmpty) {
      CommandRunner.setVerbose(verboseCmd)
      new ParrotLauncher(this)
    }
    else {
      missing foreach {config: String => println("Config parameter missing: " + config) }
      throw new Exception("Launcher creation failed with unspecified required configs")
    }
  }

}
