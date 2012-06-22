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
package com.twitter.parrot.config

import com.twitter.io.TempFile
import com.twitter.util.Eval
import org.specs.SpecificationWithJUnit

class ConfigSpec extends SpecificationWithJUnit {
  "/config" should {
    val eval = new Eval
    val configFiles = Seq(
      "/dev-feeder.scala",
      "/test-server.scala",
      "/test-compare.scala",
      "/test-server.scala",
      "/test-slow.scala",
      "/test-ssl.scala",
      "/test-timeout.scala",
      "/test-memcache.scala",
      "/test-udp.scala",
      "/test-kestrel.scala",
      "/test-thrift.scala"
    ).map { TempFile.fromResourcePath(_) }

    for (file <- configFiles) {
      file.getName() in {
        try {
          val config = eval[ParrotCommonConfig](file)
          config must notBeNull
        }
        catch {
          case t: Throwable => {
            t.printStackTrace()
          }
        }
      }
    }
  }
}
