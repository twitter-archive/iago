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

import org.specs.SpecificationWithJUnit
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.{Duration, MockTimer, Time}
import java.util.concurrent.TimeUnit

class RequestDistributionSpec extends SpecificationWithJUnit {
  val log = Logger.get(getClass)

  def simulate(dist: RequestDistribution, numSeconds: Int): Double = {
    var time = Duration(0, TimeUnit.MINUTES)
    var count = 0

    while (time.inSeconds < numSeconds) {
      time += dist.timeToNextArrival()
      count += 1
    }
    count.toDouble
  }

  "PoissonProcess" should {
    "distribute requests across a minute, across a range of likely rps" in {
      val numSeconds = 60
      for (rate <- 20 to 1020 by 200) {
        val count = simulate(new PoissonProcess(rate), numSeconds)
        count must be_>=(rate * numSeconds * 0.90)
        count must be_<=(rate * numSeconds * 1.10)
      }
    }
  }

  "SlowStartPoissonProcess" should {
    "distribute requests across a minute, across a range of likely rps, at the initial rate" in {
      val numSeconds = 60
      for (rate <- 20 to 1020 by 200) {
        val distribution = new SlowStartPoissonProcess(rate * 10, 2.hours, rate)
        val count = simulate(distribution, numSeconds)

        val finalRate = distribution.currentRate.toDouble
        finalRate must be_<=(rate * 1.10)

        count must be_>=(rate * numSeconds * 0.90)
        count must be_<=(finalRate * numSeconds * 1.10)
      }
    }

    "distribute requests across a minute, across a range of likely rps, at the final rate" in {
      val numSeconds = 60
      for (rate <- 20 to 1020 by 200) {
        val process = new SlowStartPoissonProcess(rate, 1.millisecond, rate / 2)
        (1 to rate * 2).foreach { _ => process.timeToNextArrival() } // get to final rate
        val count = simulate(process, numSeconds)
        count must be_>=(rate * numSeconds * 0.90)
        count must be_<=(rate * numSeconds * 1.10)
      }
    }

    "set request rate correctly over time" in {
      val minRate = 500
      val maxRate =  1000
      val process = new SlowStartPoissonProcess(maxRate, 1.hour, minRate)
      for (minute <- 0 to 60) {
        val count = simulate(process, 60)
        val expectedRate = minRate + ((maxRate - minRate) / 60.0 * minute)
        count must be_>=(expectedRate * 60 * 0.90)
        count must be_<=(expectedRate * 60 * 1.10)
      }
    }

    "set decreasing request rate correctly over time" in {
      val minRate = 500
      val maxRate =  1000
      val process = new SlowStartPoissonProcess(minRate, 1.hour, maxRate)
      for (minute <- 0 to 60) {
        val count = simulate(process, 60)
        val expectedRate = maxRate + ((minRate - maxRate) / 60.0 * minute)
        count must be_>=(expectedRate * 60 * 0.90)
        count must be_<=(expectedRate * 60 * 1.10)
      }
    }
  }

  "SinusoidalPoissonProcess" should {
    import scala.math._

    lazy val mockTimer = new MockTimer

    def makeProcess(minRate: Int, maxRate: Int, period: Duration, rampUp: Option[Duration] = None) = {
      new SinusoidalPoissonProcess(minRate, maxRate, period, rampUp) {
        override val timer = mockTimer
      }
    }

    "start at initial rate" in {
      val process = makeProcess(100, 500, 10.minutes)
      process.timeToNextArrival()
      process.currentRate must be_==((100 + 500) / 2)
    }

    "ramp up to initial rate if requested" in {
      Time.withCurrentTimeFrozen { tc =>
        val process = makeProcess(100, 500, 10.minutes, Some(10.seconds))
        process.timeToNextArrival()
        Seq(1, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300).foreach { nextExpectedRate =>
          process.currentRate must be_==(nextExpectedRate)
          tc.advance(1.second)
          mockTimer.tick()
        }
      }
    }

    "vary rate between min and max using sine" in {
      Time.withCurrentTimeFrozen { tc =>
        val process = makeProcess(100, 500, 100.seconds)
        process.timeToNextArrival()
        (0 to 100).foreach { second =>
          // sine wave from 100 -> 500, period 100 seconds
          val expectedRate = round(200.0 * sin(2.0 * Pi * second.toDouble / 100.0) + 300.0).toInt

          process.currentRate must be_==(expectedRate)
          tc.advance(1.second)
          mockTimer.tick()
        }
      }
    }
  }

  "UniformDistribution" should {
    "distribute requests uniformly across a minute" in {
      val numSeconds = 60
      val rate = 1000
      val count = simulate(new UniformDistribution(rate), numSeconds)
      count must be_== (rate * numSeconds)
    }
  }
}
