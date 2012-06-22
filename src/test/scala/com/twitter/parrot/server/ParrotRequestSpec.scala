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
package com.twitter.parrot.server

import com.twitter.parrot.thrift.TargetHost
import org.specs.SpecificationWithJUnit

class ParrotRequestSpec extends SpecificationWithJUnit {
  val target = new TargetHost("http", "foo", 1234)

  "ParrotRequest.headers" should {
    "no headers, no host header" in {
      new ParrotRequest(target).headers mustEqual Nil
    }
    "no headers, host header" in {
      new ParrotRequest(target, Some("bar" -> 2345)).headers mustEqual Seq("Host" -> "bar:2345")
    }
    "headers, no host header" in {
      val headers = Seq("foo" -> "bar", "baz" -> "blarg")
      new ParrotRequest(target, None, headers).headers mustEqual headers
    }
    "headers, host header" in {
      val request = new ParrotRequest(target, Some("bar" -> 2345), Seq("foo" -> "bar"))
      request.headers mustEqual Seq("Host" -> "bar:2345", "foo" -> "bar")
    }
  }
}
