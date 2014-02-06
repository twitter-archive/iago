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

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import scala.util.matching.Regex
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.parrot.util.ConsoleHandler
import com.twitter.util.Eval
import com.twitter.util.Config.fromRequired
import scala.collection.mutable
import java.util.regex.Pattern
import java.net.URLClassLoader

class ParrotLauncher(config: ParrotLauncherConfig) {
  private[this] val log = Logger.get(getClass)
  private[this] val pmode =
    if (config.localMode)
      new ParrotModeLocal(config)
    else
      new ParrotModeMesos(config)
  private[this] val scheme = config.scheme match {
    case "https"   => "TransportScheme.HTTPS"
    case "thrifts" => "TransportScheme.THRIFTS"
    case _         => "TransportScheme.HTTP"
  }
  private[this] val scriptsDstFolder = config.distDir + "/scripts"
  private[this] val victim = {
    val victims = config.victims.value
    config.victimClusterType match {
      case "sdzk" =>
        """ServerSetVictim("%s", "%s", %d)""".format(victims, config.victimZk,
          config.victimZkPort)
      case _ =>
        """HostPortListVictim("%s")""".format(
          if (victims.contains(":"))
            victims
          else

            // given a string of the form "host0,host1,..", create a HostPortListVictim using the
            // default port.

            victims.split("[ ,]").toList.map(_ + ":" + config.port).mkString(","))
    }
  }

  private[this] val symbols = mutable.Map[String, String](
    ("batchSize" -> config.batchSize.toString),
    ("classPath" -> config.classPath),
    ("configType" -> config.configType),
    ("createDistribution" -> config.createDistribution),
    ("customLogSource" -> config.customLogSource),
    ("duration" -> config.duration.toString),
    ("header" -> config.header),
    ("hostConnectionCoresize" -> config.hostConnectionCoresize.toString),
    ("hostConnectionIdleTimeInMs" -> config.hostConnectionIdleTimeInMs.toString),
    ("hostConnectionLimit" -> config.hostConnectionLimit.toString),
    ("hostConnectionMaxIdleTimeInMs" -> config.hostConnectionMaxIdleTimeInMs.toString),
    ("hostConnectionMaxLifeTimeInMs" -> config.hostConnectionMaxLifeTimeInMs.toString),
    ("httpHostHeaderPort" -> config.port.toString),
    ("imports" -> config.imports),
    ("jobName" -> pmode.jobName),
    ("loadTest" -> config.loadTest),
    ("logFile" -> pmode.logPath),
    ("maxRequests" -> config.maxRequests.toString),
    ("numFeederInstances" -> config.numFeederInstances.toString),
    ("numInstances" -> config.numInstances.toString),
    ("port" -> config.port.toString),
    ("requestRate" -> config.requestRate.toString),
    ("requestTimeoutInMs" -> config.requestTimeoutInMs.toString),
    ("requestType" -> config.requestType),
    ("responseType" -> config.responseType),
    ("reuseConnections" -> config.reuseConnections.toString),
    ("reuseFile" -> config.reuseFile.toString),
    ("scheme" -> scheme),
    ("serverXmx" -> config.serverXmx.toString),
    ("tcpConnectTimeoutInMs" -> config.tcpConnectTimeoutInMs.toString),
    ("thriftClientId" -> config.thriftClientId),
    ("timeUnit" -> config.timeUnit),
    ("traceLevel" -> config.traceLevel.toString),
    ("extraLoggers" -> config.extraLoggers),
    ("transport" -> config.transport),
    ("victim" -> victim))

  private[this] lazy val regex = ("#\\{(" + symbols.keys.mkString("|") + ")\\}").r

  def adjust(change: String) = pmode.adjust(change)

  def kill = pmode.kill

  def start() {
    ConsoleHandler.start(config.traceLevel)
    //printClassPath
    log.info("Starting Parrot job named %s", pmode.jobName)
    try {
      verifyConfig
      pmode.cleanup
      pmode.includeLog(symbols)
      pmode.modeSymbols(symbols)
      escapeValues
      createScripts
      createParrotConfigs
      pmode.createConfig(templatize)
      pauseUntilReady
      pmode.createJobs
      pmode.cleanup
    } finally {
      CommandRunner.shutdown()
    }
  }

  private[this] def verifyConfig {
    if ("^[_A-Za-z0-9]+$".r.findFirstIn(pmode.jobName).isEmpty) {
      log.fatal("job names can only be composed of letters, numbers, and underscores")
      System.exit(1)
    }
    pmode.verifyConfig
    requireDirectory(pmode.configDstFolder)
    requireDirectory(pmode.targetDstFolder)
  }

  private[this] def requireDirectory(directory: String) {
    if (!(new File(directory).isDirectory))
      new File(directory).mkdir()
  }

  private[this] def escapeValues {
    symbols.foreach { case (k, v) => symbols(k) = v.replace("$", "\\$") }
  }

  private[this] def printClassPath {
    val cl: URLClassLoader = ClassLoader.getSystemClassLoader.asInstanceOf[URLClassLoader]
    println(cl.getURLs.map(_.getFile).mkString(":"))
  }

  private[this] def createScripts() {
    log.debug("Creating scripts.")
    requireDirectory(scriptsDstFolder)
    List("common.sh", "local-parrot.sh", "parrot-feeder.sh", "parrot-server.sh").foreach(x =>
      templatize("/scripts/" + x, scriptsDstFolder + "/" + x))
  }

  private[this] def createParrotConfigs() {
    log.debug("Creating configs.")
    val eval = new Eval()
    config.parrotTasks.foreach { root =>
      val name = root + ".scala"
      val path = pmode.targetDstFolder + "/parrot-" + name
      val file = templatize("/templates/template-" + name, path)
      eval.compile("class Test {" + eval.toSource(file) + "}")
    }
  }

  private[this] def templatize(src: String, dst: String): File = {

    log.debug("opening resource: " + src)
    val is = getClass.getResourceAsStream(src)
    val br = new BufferedReader(new InputStreamReader(is))

    val df = new File(dst)
    writeToFile(df) { p =>
      var line = ""
      while ({ line = br.readLine; line != null })
        p.println(regex.replaceAllIn(line, m => symbols(m group 1)))
    }

    br.close
    df
  }

  private[this] def writeToFile(f: File)(op: PrintWriter => Unit) {
    val p = new PrintWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }

  private[this] def pauseUntilReady() {
    if (!config.doConfirm) return

    var response = "no"
    while (response != "") {
      print("Configurations generated. Type 'quit' to quit or press enter to launch. ")
      response = Console.readLine()
      if (response == "quit") throw new Exception("Quitting")
    }
  }
}
