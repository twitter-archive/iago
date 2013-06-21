/*
Copyright 2013 Twitter, Inc.

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

package com.twitter.parrot.server

import java.util.ArrayList

import org.jboss.netty.handler.codec.http.HttpResponse
import org.scalatest.AbstractSuite
import org.scalatest.Suite

import com.twitter.io.TempFile
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.Eval

trait ServerFixture extends AbstractSuite { this: Suite =>

  val config = {
    val eval = new Eval
    val configFile = TempFile.fromResourcePath("/test-server.scala")
    eval[ParrotServerConfig[ParrotRequest, HttpResponse]](configFile)
  }
  
  config.victim = config.HostPortListVictim("localhost:0")

  val transport = config.transport match {
    case Some(dt: DumbTransport) => dt
    case _                       => throw new Exception("Wrong ParrotTransport, test will fail")
  }

}
