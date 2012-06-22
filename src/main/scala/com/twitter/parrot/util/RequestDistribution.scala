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
import java.util.concurrent.TimeUnit

trait RequestDistribution {
  def timeToNextArrival(): Duration
}

class UniformDistribution(arrivalsPerSecond: Int) extends RequestDistribution {
  if (arrivalsPerSecond <= 0) {
    throw new IllegalArgumentException("arrivals must be >= 1")
  }

  def timeToNextArrival(): Duration = {
    val nanosToNextArrival = 1000000000.0 / arrivalsPerSecond
    Duration(nanosToNextArrival.toLong, TimeUnit.NANOSECONDS)
  }
}

