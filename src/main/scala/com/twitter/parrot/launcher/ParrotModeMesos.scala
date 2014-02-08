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

import java.io.File

import scala.collection.mutable

import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig
import com.twitter.util.Config.fromRequired

/**
 * At Twitter we use Mesos, Hadoop, and ZooKeeper. Configurations and actions
 * peculiar to that environment are kept here.
 */

class ParrotModeMesos(config: ParrotLauncherConfig) extends ParrotMode(config) {
  private[this] val log = Logger.get(getClass)
  private[this] val logName = config.log.split("/").last
  private[this] val user = config.role
  override val logPath = "log/" + logName
  val proxy = new SMFProxy(config)

  override def adjust(change: String) {
    val adjustment = change.toInt
    log.info("Adjusting load by %d RPS for job %s", adjustment, jobName)
    config.zkNode = config.zkNode.format(jobName)
    config.requestRate += adjustment
    Adjustor.adjust(config, jobName, config.requestRate)
    CommandRunner.shutdown()
  }

  def cleanup() {
    config.parrotTasks foreach { task =>
      CommandRunner("rm %s.zip".format(task))
    }
  }

  def createConfig(templatize: (String, String) => Unit) {
    packageMesosBundle
    createMesosConfig(templatize)
  }

  def createJobs {
    config.parrotTasks foreach { task =>
      proxy.createMesosJob(config.mesosJobname(task), jobName, config.distDir)

      // Mesos workaround to deal with race conditions with HDFS
      Thread.sleep(2000)
    }
  }

  def includeLog(symbols: mutable.Map[String, String]) = {
    if (logFile.contains("hdfs://")) {
      symbols += ("hdfsCommand" ->
        "mkdir log && hadoop --config %s fs -copyToLocal %s log && ".format(
          config.hadoopConfig, logFile))
    } else {
      val logDir = "%s/log".format(config.distDir)
      if (!(new File(logDir).isDirectory)) {
        new File(logDir).mkdir()
      }
      CommandRunner("cp -v %s %s/%s".format(logFile, logDir, logName))
      symbols += ("hdfsCommand" -> "")
    }
  }

  override def kill {
    log.info("Killing Parrot job named: %s".format(jobName))
    config.parrotTasks foreach { task =>
      proxy.killMesosJob(config.mesosJobname(task))
    }
    CommandRunner.shutdown()
  }

  private[this] def packageMesosBundle() {
    makeZip(config.parrotTasks(0))
    proxy.uploadMesosPackage(config.parrotTasks(0), jobName, user)

    for (i <- 1 until config.parrotTasks.length) {
      CommandRunner("cp %s.zip %s.zip".format(config.parrotTasks(0), config.parrotTasks(i)))
      proxy.uploadMesosPackage(config.parrotTasks(i), jobName, user)
    }
  }

  private[this] def makeZip(name: String) {
    log.info("Building Mesos Bundle...")
    CommandRunner.timeRun(config.archiveCommand(name))
  }

  private[this] def createMesosConfig(templatize: (String, String) => Unit) {
    List(("/templates/template.mesos", "/config.mesos")) foreach {
      case (src, dst) => templatize(src, targetDstFolder + dst)
    }
  }

  def modeSymbols(symbols: mutable.Map[String, String]) {
    symbols("feederDiskInMb") = (config.feederDiskInMb + calculateLogFileSize).toString
    symbols("hadoopConfig") = config.hadoopConfig
    symbols("maxPerHost") = config.maxPerHost.toString
    symbols("mesosCluster") = config.mesosCluster
    symbols("mesosServerRamInMb") = config.mesosServerRamInMb.getOrElse(

      /* When RSS (the Resident Set Size) exceeds mesosRamInMb the Mesos job is killed. The
         following voodoo is based on the assumption that RSS and Xmx (the maximum heap
         size specified for the Java Virtual Machine) is linearly related (probably a
         bad assumption) and the observation that the difference between RSS and Xmx for
         RSS = 16000 and 8000 is

           16000x + k = 404
            8000x + k = 384

         so

           mesosRam/400 + 364 = mesosRam - Xmx
      */

      400 * (config.serverXmx + 364) / 399).toString

    symbols("mesosFeederRamInMb") = config.mesosFeederRamInMb.getOrElse(
        
        // a 3 gigabyte kludge factor
        
      config.feederXmx + 3000).toString

    symbols("mesosServerNumCpus") = config.mesosServerNumCpus.toString
    symbols("mesosFeederNumCpus") = config.mesosFeederNumCpus.toString
    symbols("serverDiskInMb") = config.serverDiskInMb.toString

    symbols("statlogger") = """ :: new LoggerFactory(
    node = "stats",
    level = Level.INFO,
    useParents = false,
    handlers = ScribeHandler(
      hostname = "localhost",
      category = "cuckoo_json",
      maxMessagesPerTransaction = 100,
      formatter = BareFormatter
    )
  ) """

    symbols("user") = user
    symbols("zkHostName") = config.zkHostName match {
      case Some(host) => """Some("%s")""".format(host)
      case None       => "None"
    }
    symbols("zkNode") = ("/twitter/service/parrot2/" + jobName)
    symbols("zkPort") = "2181"
  }

  private[this] def calculateLogFileSize(): Long = {
    if (logFile.startsWith("hdfs://")) {
      if (proxy.exists(logFile)) {
        println("Found log file in HDFS! Continuing...")
        proxy.sizeInMb(logFile)
      } else {
        throw new Exception("Couldn't find HDFS logfile: %s".format(logFile))
      }
    } else {
      val f = new File(logFile)
      if (f.exists) {
        println("Found the local log file %s. We'll include it in our zip.".format(logFile))
        math.ceil(f.length / 1024 / 1024).toLong
      } else {
        throw new Exception("Couldn't find local logfile: %s".format(logFile))
      }
    }
  }

  def verifyConfig {}
}
