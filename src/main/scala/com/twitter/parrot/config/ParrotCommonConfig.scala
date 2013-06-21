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

import com.twitter.util.Duration
import java.util.concurrent.TimeUnit
import com.twitter.logging.LoggerFactory
import com.twitter.ostrich.admin.RuntimeEnvironment

trait ParrotCommonConfig {
  var zkHostName: Option[String] = None
  var zkPort = 2181
  var zkNode = "/twitter/service/parrot/disco"

  var parrotPort = 9999
  var parrotHosts = List("localhost")
  var finagleTimeout = Duration(5, TimeUnit.SECONDS)

  var classPath="${APP_HOME}/*:${APP_HOME}/libs/*"

  var loggers: List[LoggerFactory] = Nil
  var runtime: Option[RuntimeEnvironment] = None
  
  var cachedSeconds = 60

}
