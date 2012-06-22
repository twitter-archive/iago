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

import util.Random
import com.twitter.conversions.time._
import com.twitter.util.{Duration, Time}
import org.apache.commons.math.distribution.ExponentialDistributionImpl
import java.util.concurrent.TimeUnit
import java.lang.IllegalArgumentException

class SlowStartPoissonProcess(finalArrivalsPerSecond: Int,
                              rampDuration: Duration,
                              initialArrivalsPerSecond: Int = 1)
extends RequestDistribution {
  if (finalArrivalsPerSecond <= 0) {
    throw new IllegalArgumentException("final arrivals must be >= 1")
  }

  if (initialArrivalsPerSecond <= 0) {
    throw new IllegalArgumentException("initial arrivals must be >= 1")
  }

  if (rampDuration < 1.millisecond) {
    throw new IllegalArgumentException("ramp duration must be >= 1 millisecond")
  }

  private[this] val rand = new Random(Time.now.inMillis)
  private[this] var dist = new ExponentialDistributionImpl(1000000000.0 / initialArrivalsPerSecond)
  private[this] val stepPerMilli =
    (finalArrivalsPerSecond - initialArrivalsPerSecond).toDouble / rampDuration.inMilliseconds.toDouble

  private[this] var arrivals = 0
  private[this] var currentArrivalsPerSecond = initialArrivalsPerSecond.toDouble
  private[this] var nextStepPoint = (currentArrivalsPerSecond / 1000.0) + (stepPerMilli / 2.0)

  private[this] def reachedFinalRate: Boolean = {
    if (finalArrivalsPerSecond >= initialArrivalsPerSecond) {
      currentArrivalsPerSecond.toInt >= finalArrivalsPerSecond
    } else {
      currentArrivalsPerSecond.toInt <= finalArrivalsPerSecond
    }
  }

  def currentRate: Int = {
    if (reachedFinalRate) {
      finalArrivalsPerSecond
    } else {
      currentArrivalsPerSecond.toInt
    }
  }

  def timeToNextArrival(): Duration = {
    if (!reachedFinalRate) {
      arrivals += 1
      if (arrivals >= nextStepPoint.toInt) {
        // Compute when to next update rate (numerically integrate number of arrivals over the next ms).
        // Loops handles the case where step points increase more quickly than arrivals.
        while (arrivals >= nextStepPoint.toInt) {
          nextStepPoint += (currentArrivalsPerSecond / 1000.0) + (stepPerMilli / 2.0)
          currentArrivalsPerSecond += stepPerMilli
        }

        // clamp rate to final arrival rate and only update distribution if it has changed (by 1+ RPS)
        val mean = 1000000000.0 / currentRate
        if (dist.getMean != mean) {
          dist = new ExponentialDistributionImpl(mean)
        }
      }
    }

    val nanosToNextArrival = dist.inverseCumulativeProbability(rand.nextDouble())
    Duration(nanosToNextArrival.toLong, TimeUnit.NANOSECONDS)
  }

}
