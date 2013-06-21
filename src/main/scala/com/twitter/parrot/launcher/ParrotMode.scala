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
package com.twitter.parrot.launcher

import scala.collection.mutable
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig

/**
 * The Iago launcher can launch locally or launch into a cluster using Mesos. This class tries
 * to hide those details from the main logic of the launcher.
 */
abstract class ParrotMode(config: ParrotLauncherConfig) {
  private[this] val log = Logger.get(getClass)

  val configDstFolder = config.distDir + "/config"
  val jobName = config.jobName.value
  val logFile: String = config.log.value
  val logPath = logFile
  val ram = config.serverXmx
  val targetDstFolder = configDstFolder + "/target"

  def adjust(change: String)
  def cleanup
  def createConfig(templatize: (String, String) => Unit)
  def createJobs
  def kill
  def includeLog(symbols: mutable.Map[String, String])
  def modeSymbols(symbols: mutable.Map[String, String])
  def verifyConfig
}
