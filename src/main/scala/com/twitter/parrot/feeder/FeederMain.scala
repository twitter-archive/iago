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
package com.twitter.parrot.feeder

import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.logging.Logger

object FeederMain {
  val log = Logger.get(getClass.getName)

  def main(args: Array[String]) {
    val runtime = RuntimeEnvironment(this, args)
    val feeder: ParrotFeeder = runtime.loadRuntimeConfig()
    log.info("Starting Parrot Feeder...")
    try {
      feeder.start()
    } catch {
      case t: Throwable =>
        log.error(t, "Unexpected exception: %s", t)
    }
  }
}
