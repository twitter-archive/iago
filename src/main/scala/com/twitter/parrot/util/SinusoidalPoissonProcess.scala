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

import java.util.Random
import com.twitter.conversions.time._
import com.twitter.util.{Duration, JavaTimer, Time, Timer}
import org.apache.commons.math.distribution.ExponentialDistributionImpl
import java.util.concurrent.TimeUnit
import java.lang.IllegalArgumentException

/**
 * SinusoidalPoissonProcess is a RequestDistribution that oscillates sinusoidally between minRate and maxRate.
 * If warmUpDuration is None, the initial rate is the average of minRate and maxRate, otherwise the request
 * rate ramps linearly from 0 to that average. Once warm up is complete, the request rate starts following
 * a sine wave pattern, rising to maxRate in 1/4 period and returning to maxRate very period thereafter.
 *
 * Rates are updated once per second -- periods of less than 1 minute are not recommended.
 */
class SinusoidalPoissonProcess(
  val minRate: Int,
  val maxRate: Int,
  val period: Duration,
  val warmUpDuration: Option[Duration] = None)
extends RequestDistribution {
  import scala.math._

  if (minRate > maxRate) {
    throw new IllegalArgumentException("min arrival rate (%d) must be less than max (%d)".format(
      minRate, maxRate))
  }

  if (minRate <= 0 || maxRate <= 0) {
    throw new IllegalArgumentException("min (%d) and max (%d) arrival rates must be greater than 0".format(
      minRate, maxRate))
  }

  if (period < 1.second) {
    throw new IllegalArgumentException("minimum period is 1.second: " + period)
  }

  val rand = new Random(Time.now.inMillis)
  val timer: Timer = new JavaTimer(true)

  @volatile private[this] var dist: Option[ExponentialDistributionImpl] = None
  @volatile private[this] var outputFunction: (Int) => Unit = null
  private[this] var currentArrivalRate = 0

  def currentRate = currentArrivalRate

  private def linear(numSteps: Int, target: Int)(step: Int) {
    val nextArrivalRate = round(step.toDouble * target.toDouble / numSteps.toDouble).toInt max 1

    if (nextArrivalRate >= target) {
      outputFunction = sinusoid(period.inSeconds)
      outputFunction(0)
    } else {
      setArrivalRate(step, nextArrivalRate)
    }
  }

  private def sinusoid(stepsPerPeriod: Int)(step: Int) {
    val rate =
      (maxRate - minRate).toDouble / 2.0 * sin(2.0 * Pi * step.toDouble / stepsPerPeriod.toDouble) +
      (maxRate + minRate).toDouble / 2.0
    setArrivalRate(step, round(rate).toInt)
  }

  private def updateDistribution() {
    synchronized {
      warmUpDuration match {
        case Some(d) => outputFunction = linear(d.inSeconds, (maxRate + minRate) / 2)
        case None => outputFunction = sinusoid(period.inSeconds)
      }

      outputFunction(0)
    }
  }

  protected def setArrivalRate(step: Int, nextArrivalRate: Int) {
    currentArrivalRate = nextArrivalRate
    dist = Some(new ExponentialDistributionImpl(1000000000.0 / nextArrivalRate))
    timer.schedule(Time.now + 1.second) { outputFunction(step + 1) }
  }

  def stop() {
    timer.stop()
  }

  def timeToNextArrival(): Duration = {
    if (!dist.isDefined) {
      updateDistribution()
    }

    val nanosToNextArrival = dist.get.inverseCumulativeProbability(rand.nextDouble())
    Duration(nanosToNextArrival.toLong, TimeUnit.NANOSECONDS)
  }
}
