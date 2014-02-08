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
package com.twitter.parrot.launcher

import util.matching.Regex

import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig

class SMFProxy(config: ParrotLauncherConfig) {
  import config.{ env, proxyCp, proxyMkdir, proxyShell, hadoopCmd, hadoopFS, hadoopConfig, hadoopNS, mesosCluster => cluster }

  private[this] val log = Logger.get(getClass)
  private[this] val fileSystem = "%s --config %s %s ".format(hadoopCmd, hadoopConfig, hadoopFS)
  val useProxy = config.proxy.isDefined
  val proxy = config.proxy.getOrElse("")

  private[this] def pwd() = {
    val command = proxyCmd("pwd")
    val runner = new CommandRunner(command)
    if (runner.run() != 0) {
      runner.dumpOutput
      throw new RuntimeException("failed: " + command)
    }
    runner.getOutput.trim
  }

  private[this] val targetDir = pwd + (if (useProxy) "/parrotTarget/" else "/")
  if (useProxy) {
    val command = proxyCmd("mkdir -p %s".format(targetDir))
    val runner = new CommandRunner(command)
    val status = runner.run()
    if (!(0 until 2 contains status)) {
      runner.dumpOutput()
      throw new RuntimeException("failed: " + command)
    }
  }

  val user = System.getenv("USER")
  private[this] def fs(cmd0: String) = proxyCmd(fileSystem + cmd0)
  private[this] def hadoopURI(name: String) = {
    if (name.startsWith("hdfs://"))
      name
    else
      "%s/%s".format(hadoopNS, name)
  }

  def exists(name: String) =
    CommandRunner(fs("-ls %s".format(hadoopURI(name)))) == 0

  def mkDir(name: String) {
    val command = fs("-mkdir %s".format(hadoopURI(name)))
    val runner = new CommandRunner(command)
    val status = runner.run()
    if (!(0 until 2 contains status)) {
      runner.dumpOutput()
      throw new RuntimeException("failed: " + command)
    }
  }

  def sizeInMb(name: String): Long = {
    val command = fs("-ls %s".format(hadoopURI(name)))
    val runner = new CommandRunner(command)
    val status = runner.run()
    if (status == 0) {
      try {
        val lsOutput = runner.getOutput
        val lsNextLine = lsOutput.split("""\n+""")(1)
        lsNextLine.split("""\s+""")(4).toLong / 1024 / 1024
      } catch { case e => 0L }
    } else if (status == 1) 0L
    else {
      runner.dumpOutput()
      throw new RuntimeException("failed: " + command)
    }
  }

  def uploadMesosPackage(task: String, job: String, role: String) {
    // create&kill use cluster/role/env/name, but package_add_version still uses the old format
    val jobName = "parrot_%s_%s".format(task, job)
    val zip = task + ".zip"
    val upload = "aurora package_add_version --cluster=%s %s %s %s".format(cluster, role, jobName, targetDir + zip)

    if (useProxy)
      noisyTime("rsync %s %s:%s".format(zip, proxy, targetDir))

    noisyTime(proxyCmd(upload))
  }

  def noisy(command: String) {
    if (0 != CommandRunner(command, true))
      throw new RuntimeException("failed: " + command)
  }

  def noisyTime(command: String) {
    if (0 != CommandRunner.timeRun(command))
      throw new RuntimeException("failed: " + command)
  }

  def createMesosJob(jobName: String, job: String, distDir: String) {
    val config = if (useProxy) targetDir + job + "-config.mesos" else "config/target/config.mesos"
    val create = "aurora create %s %s --log_to_stderr=INFO".format(jobName, config)

    if (useProxy)
      noisy("rsync %s/config/target/config.mesos %s:%s".format(distDir, proxy, config))

    noisyTime(proxyCmd(create))
  }

  def killMesosJob(jobName: String) {
    val killCmd = "aurora kill %s"
    val command = proxyCmd(killCmd.format(jobName))
    val runner = new CommandRunner(command, true)
    val status = runner.run()
    if (!(0 until 2 contains status)) {
      runner.dumpOutput()
      throw new RuntimeException("failed: " + command)
    }
  }

  private[this] def proxyCmd(command: String) = {
    if (useProxy) {
      "%s %s %s".format(proxyShell, proxy, command)
    } else {
      command
    }
  }
}
