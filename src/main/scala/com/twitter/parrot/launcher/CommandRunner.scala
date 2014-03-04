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

import java.io.{ OutputStream, InputStream }
import java.util.concurrent.{ Future, Callable, Executors }
import com.twitter.logging.Logger
import com.twitter.logging.Level
import com.twitter.parrot.util.PrettyDuration
import com.twitter.util.Time
import com.twitter.util.Stopwatch

object CommandRunner {
  private[launcher] val threadPool = Executors.newFixedThreadPool(3)
  private[this] val log = Logger.get(getClass)
  var verbose = false

  def apply(command: String): Int = { apply(command, verbose) }

  def apply(command: String, verbose: Boolean): Int = {
    val runner = new CommandRunner(command, verbose)
    runner.run()
  }

  def exists(command: String): Boolean = { exists(command, verbose) }

  def exists(command: String, verbose: Boolean) = {
    try {
      val runner = new CommandRunner("which %s".format(command), verbose)
      runner.run()
      runner.getOutput.length > 0
    } catch {
      case _: Throwable => false
    }
  }

  def shutdown() {
    threadPool.shutdownNow()
  }

  def setVerbose(verbose: Boolean) { this.verbose = verbose }

  def timeRun(command: String): Int = {
    val elapsed = Stopwatch.start()
    val result = apply(command, true)
    println(PrettyDuration(elapsed()))
    result
  }
}

class CommandRunner(command: String, verbose: Boolean = false) {
  private[this] val process = Runtime.getRuntime.exec(command)
  private[this] val inputStream = process.getInputStream
  private[this] val errorStream = process.getErrorStream
  private[this] val outputStream = process.getOutputStream

  private[this] val output = new StreamConsumer(inputStream, outputStream, verbose)
  private[this] val error = new StreamConsumer(errorStream, outputStream, verbose)

  var fOutput: Future[String] = null
  var fError: Future[String] = null

  def run(): Int = {
    fOutput = CommandRunner.threadPool.submit(output)
    fError = CommandRunner.threadPool.submit(error)

    if (verbose) println(command)
    val status = process.waitFor()

    if (verbose) dumpOutput()

    status
  }

  def dumpOutput() {
    echo("stdout: ", getOutput, Console.out)
    echo("stderr: ", getError, Console.err)
  }

  private def echo(prefix: String, msg: String, out: java.io.PrintStream) {
    if (msg.trim.length != 0)
      for (i <- msg.split("\n")) {
        out.print(prefix)
        out.println(i)
      }
  }

  def getOutput = {
    fOutput.get
  }

  def getError = {
    fError.get
  }
}

class StreamConsumer(stream: InputStream,
  reader: OutputStream,
  verbose: Boolean) extends Callable[String] {
  override def call() = {
    var running = true
    val buffer = new StringBuilder
    while (running) {
      val c = stream.read
      if (c == -1) {
        running = false
      } else {
        buffer.append(c.toChar)

        // This is a Mesos-related hack, since currently it needs our passwords the first
        // time we run the Mesos client on a system
        if (verbose && buffer.endsWith("assword: ")) {
          readPassword()
        }
      }
    }
    buffer.toString()
  }

  private[this] def readPassword() {
    val result = Executors.newSingleThreadExecutor()
    result.submit(new PasswordReader(reader))
    result.shutdown()
  }
}

class PasswordReader(stream: OutputStream) extends Callable[Unit] {
  private[this] val input = System.in

  override def call() {
    val buffer = new StringBuilder
    var running = true

    while (running) {
      try {
        val c = input.read.toChar
        buffer.append(c)
        if (c == '\n') {
          stream.write(buffer.toString().getBytes)
          stream.flush()
          running = false
        }
      } catch {
        case _: Throwable => println("exception in password reader, continuing...")
      }
    }
  }
}
