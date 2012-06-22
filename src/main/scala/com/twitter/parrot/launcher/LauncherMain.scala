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

import java.io.File

import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotLauncherConfig
import com.twitter.util.Eval

object LauncherMain {
  private[this] val log = Logger.get(getClass)

  private[this] var killJob = false
  private[this] var adjustJob = false
  private[this] var adjustment = "0"
  private[this] var filename = ""

  def main(args: Array[String]) {
    val eval = new Eval()
    val config = findConfig(args) map { eval[ParrotLauncherConfig](_) }
    val launcher = config map { _.apply() }
    try {
      launcher map {
        if (killJob) _.kill()
        else if (adjustJob) _.adjust(adjustment)
        else _.start()
      }
    }
    catch {
      case t: Throwable => log.error(t, "Unexpected exception: %s", t)
    }
  }

  private[this] def findConfig(args: Array[String]): Option[File] = {
    parseArgs(args.toList)
    val file = new File(filename)
    if (file.exists && file.isFile) Some(file)
    else {
      log.error("Couldn't find config file: %s", filename)
      None
    }
  }

  private[this] def parseArgs(args: List[String]) {
    args match {
      case "-f" :: name :: xs => filename = name; parseArgs(xs)
      case "-k" :: xs => killJob = true; parseArgs(xs)
      case "-a" :: change :: xs => adjustJob = true; adjustment = change; parseArgs(xs)
      case _ :: xs => parseArgs(xs)
      case Nil =>
    }
  }
}
