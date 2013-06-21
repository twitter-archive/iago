package com.twitter.parrot.launcher

import java.io.File
import scala.collection.mutable
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig

/**
 * launch the feeder and server on localhost
 */
class ParrotModeLocal(config: ParrotLauncherConfig) extends ParrotMode(config) {
  private[this] val log = Logger.get(getClass)

  def adjust(change: String) {
    log.error("sorry, adjust is not implemented")
    System.exit(1)
  }

  def cleanup {}

  def createConfig(templatize: (String, String) => Unit) {}

  def createJobs {
    CommandRunner("sh scripts/local-parrot.sh", true)
  }

  def includeLog(symbols: mutable.Map[String, String]) {}

  private[this] val killRegex =
    ("""^\s*(\d+)\s+(.*java .*parrot-(?:""" + config.parrotTasks.mkString("|") + """)\.scala)$""").r

  def kill {
    val processLister = new CommandRunner("ps -eo pid,command", false)
    processLister.run
    val pids = processLister.getOutput.split("\n").flatMap { line =>
      killRegex.findFirstMatchIn(line).map { matched =>
        val command = matched.group(2)
        log.info("Killing %s", command)
        val pid = matched.group(1)
        pid
      }
    }
    if (pids.isEmpty)
      log.warning("couldn't find any processes to kill")
    else
      CommandRunner("kill -9 " + pids.mkString(" "), true)
    CommandRunner.shutdown()
  }

  def modeSymbols(symbols: mutable.Map[String, String]) {
    symbols("statlogger") = """ :: new LoggerFactory(
    node = "stats",
    level = Level.INFO,
    useParents = false,
    handlers = FileHandler(
      filename = "parrot-server-stats.log",
      rollPolicy = Policy.MaxSize(100.megabytes),
      rotateCount = 6
    )
  ) """
    symbols("zkHostName") = "None"
    symbols("zkNode") = ""
    symbols("zkPort") = "-1"
  }

  def verifyConfig {
    val f = new File(logFile)
    if (!f.exists) {
      log.fatal("Couldn't find logfile: %s".format(logFile))
      System.exit(1)
    }
  }
}
