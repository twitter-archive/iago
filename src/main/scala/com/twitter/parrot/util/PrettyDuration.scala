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

package com.twitter.parrot.util

import com.twitter.util.Duration
import com.twitter.util.Duration._

object PrettyDuration {

  /**
   * Returns a human-friendly portrayal of com.twitter.util.Duration: the
   * appropriate units and only the first 3 significant digits.
   *
   * Here's a typical usage:
   *
   *   val (result, duration) = Duration.inNanoseconds(somethingThatTakesTime)
   *   log.info("Something took " + duration.pretty)
   *
   * Here's something for REPL
   *
   * com.twitter.util.Duration(9999999, java.util.concurrent.TimeUnit.MICROSECONDS).pretty
   *
   * res0: String = 10.0 seconds
   */

  def apply(duration: Duration): String = {

    val nanos = duration.inNanoseconds
    val nanosD = math.abs(nanos.toDouble)

    def walk: String = {
      for(Unit(limit, divisor, nameOfUnit) <- units){
          val next = nanosD / divisor
          if (math.round(next) < limit)
            return threeSignificantDigits(next) + " " + nameOfUnit
      }
      throw new Exception("Can't happen")
    }
    if (nanos < 0) return "-" + walk
    walk
  }

  private def roundToHundredths(x: Double) = math.round(x * 100d) / 100d
  private def roundToTenths(x: Double) = math.round(x * 10d) / 10d

  private def threeSignificantDigits(xx: Double): String = {
    val h = roundToHundredths(xx)
    if (h < 10d) "%1.2f".format(h)
    else {
      val t = roundToTenths(xx)
      if (t < 100d) "%2.1f".format(t)
      else math.round(xx).toString
    }
  }

  private case class Unit(limit: Long, nextFactor: Long, nameOfUnit: String)

  private val units = List(
    Unit(1000, 1, "nanoseconds"),
    Unit(1000, NanosPerMicrosecond, "microseconds"),
    Unit(1000, NanosPerMillisecond, "milliseconds"),
    Unit(100, NanosPerSecond, "seconds"),
    Unit(100, NanosPerMinute, "minutes"),
    Unit(48, NanosPerHour, "hours"),
    Unit(Long.MaxValue, NanosPerDay, "days"))
}
