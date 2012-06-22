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

import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig
import java.io.{File, PrintWriter, InputStreamReader, BufferedReader}

class ParrotLauncher(config: ParrotLauncherConfig) {
  lazy val time = System.currentTimeMillis()

  val user = if (config.role.length() > 0) {
    config.role
  }
  else {
    System.getenv("USER")
  }
  val job: String = config.jobName
  val logFile: String = config.log
  val victims: String = config.victims
  val port: Int = config.port
  val distDir: String = config.distDir

  private[this] val log = Logger.get(getClass)
  private[this] val logName = config.log.split("/").last

  private[this] var diskUsed = 50L // we start with 50 MB because Parrot is 24MB and Mesos doubles it
  private[this] val remoteLog = logFile

  private[this] val zookeeper = config.zkHostName match {
    case Some(host) => """Some("%s")""".format(host)
    case None => "None"
  }

  private[this] val symbols = Map[String, String](
    ("batchSize" -> config.batchSize.toString),
    ("createDistribution" -> config.createDistribution),
    ("customLogSource" -> config.customLogSource),
    ("diskUsed" -> diskUsed.toString),
    ("doAuth" -> config.doOAuth.toString),
    ("duration" -> config.duration.toString),
    ("header" -> config.header),
    ("hostConnectionCoresize" -> config.hostConnectionCoresize.toString),
    ("hostConnectionIdleTimeInMs" -> config.hostConnectionIdleTimeInMs.toString),
    ("hostConnectionLimit" -> config.hostConnectionLimit.toString),
    ("hostConnectionMaxIdleTimeInMs" -> config.hostConnectionMaxIdleTimeInMs.toString),
    ("hostConnectionMaxLifeTimeInMs" -> config.hostConnectionMaxLifeTimeInMs.toString),
    ("jobName" -> job),
    ("loadTest" -> config.loadTest),
    ("logFile" -> logName),
    ("maxPerHost" -> config.maxPerHost.toString),
    ("maxRequests" -> config.maxRequests.toString),
    ("numCpus" -> config.numCpus.toString),
    ("numFeederInstances" -> config.numFeederInstances.toString),
    ("numInstances" -> config.numInstances.toString),
    ("port" -> port.toString),
    ("processor" -> config.parser),
    ("remoteLog" -> remoteLog),
    ("requestRate" -> config.requestRate.toString),
    ("requestType" -> config.requestType),
    ("responseType" -> config.responseType),
    ("responseTypeImport" -> config.imports),
    ("reuseConnections" -> config.reuseConnections.toString),
    ("reuseFile" -> config.reuseFile.toString),
    ("scheme" -> config.scheme),
    ("serverXmx" -> config.serverXmx.toString),
    ("thriftClientId" -> config.thriftClientId),
    ("timeUnit" -> config.timeUnit),
    ("transport" -> config.transport),
    ("user" -> user),
    ("victims" -> victims),
    ("zookeeper" -> zookeeper)
  )

  def scriptsDstFolder = distDir + "/scripts"
  def configDstFolder = distDir + "/config"
  def targetDstFolder = configDstFolder + "/target"

  def start() {
    log.info("Starting Parrot job named %s", job)

    try {
      handleLogFile()
      createConfigs()
      createScripts()
      pauseUntilReady()
      cleanup()
    }
    catch {
      case t: Throwable => {
        println("Caught exception: " + t.getMessage)
        t.printStackTrace()
      }
    }
    finally {
      CommandRunner.shutdown()
    }
  }

  def kill() {
    log.info("Killing Parrot job named: %s", job: String)
    config.parrotTasks foreach { task =>
      // TBD
    }
    CommandRunner.shutdown()
  }

  def adjust(change: String) {
    val adjustment = change.toInt
    log.info("Adjusting load by %d RPS for job %s", adjustment, job: String)
    config.zkNode = config.zkNode.format(job)
    Adjustor.adjust(config, job, adjustment)
    CommandRunner.shutdown()
  }

  private[this] def handleLogFile() {
    if (!(new File(logFile).exists)) {
      throw new Exception("Couldn't find local logfile: %s".format(logFile))
    }
  }

  private[this] def createConfigs() {
    log.debug("Creating configs.")

    List( ("/templates/template.mesos",         targetDstFolder + "/config.mesos"),
          ("/templates/template-feeder.scala",  targetDstFolder + "/mesos-feeder.scala"),
          ("/templates/template-server.scala",  targetDstFolder + "/mesos-server.scala") ) foreach {
      case (src, dst) => templatize(src, dst, symbols)
    }
  }

  private[this] def createScripts() {
    log.debug("Creating scripts.")
    if(!(new File(scriptsDstFolder).isDirectory)){
      new File(scriptsDstFolder).mkdir()
    }

    List("parrot-feeder.sh", "parrot-server.sh") map { s =>
      ("/scripts/" + s, scriptsDstFolder + "/" + s)
    } foreach { case (src, dst) =>
      templatize(src, dst, symbols)
    }
  }

  private[this] def pauseUntilReady() {
    if (!config.doConfirm) return

    var response = "no"
    while (response != "yes" && response != "") {
      print("Configs generated, are you ready to do some damage? [yes] ")
      response = Console.readLine()
      if (response == "quit") throw new Exception("Quitting.")
    }
  }

  private[this] def cleanup() {
    config.parrotTasks foreach { task =>
      CommandRunner("rm %s-%s.zip".format(task, time))
    }
  }

  private[this] def makeZip(name: String) {
    println("Building Iago Bundle...")

    //creates dist/project from root folder, as if dist/project is current dir
    //CommandRunner("tar -zcf %s-%s.zip -C %s .".format(name, time, distDir))
    CommandRunner("jar -Mcf %s-%s.zip -C %s .".format(name, time, distDir))
  }

  private[this] def writeToFile(f: File)(op: PrintWriter => Unit) {
    val p = new PrintWriter(f)
    try {
      op(p)
    }
    finally {
      p.close()
    }
  }

  private[this] def templatize(src: String, dst: String, symbols: Map[String, String]) {
    log.debug("opening resource: " + src)

    val is = getClass.getResourceAsStream(src)
    val br = new BufferedReader(new InputStreamReader(is))

    if(!(new File(configDstFolder).isDirectory)){
      throw new Exception("Did you package-dist? %s doesn't exist".format(configDstFolder))
    }

    if(!(new File (targetDstFolder).isDirectory)){
      new File(targetDstFolder).mkdir()
    }


    writeToFile(new File(dst)) { p =>
      var line = ""
      while (line != null) {
        line = br.readLine()
        if (line != null) p.println(processLine(line, symbols))
      }
    }
  }

  private[this] def processLine(line: String, symbols: Map[String, String]) =
    symbols.foldLeft(line) { case (result, (symbol, value)) =>
      result.replaceAll("""\#\{%s\}""".format(symbol), value.toString)
    }
}
