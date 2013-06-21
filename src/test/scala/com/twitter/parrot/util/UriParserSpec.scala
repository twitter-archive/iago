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


import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

import com.twitter.util.Return

@RunWith(classOf[JUnitRunner])
class UriParserSpec extends WordSpec with MustMatchers {
  "UriParser" should {
    "extract /" in {
      UriParser("/") must be === Return(Uri("/", Nil))
    }
    "extract / with args" in {
      UriParser("/?foo") must be(Return(Uri("/", Seq("foo" -> ""))))
    }
    "extract non-toplevel" in {
      UriParser("foo") must be(Return(Uri("foo", Nil)))
    }
    "extract non-toplevel with args" in {
      UriParser("foo?bar=baz") must be(Return(Uri("foo", Seq("bar" -> "baz"))))
    }
    "extract long path" in {
      UriParser("/foo/bar/baz.html") must be(Return(Uri("/foo/bar/baz.html", Nil)))
    }
    "extract long path with args" in {
      val uri = UriParser("/foo/bar/baz.html?foo&bar=&foo=bar")
      uri must be(Return(Uri("/foo/bar/baz.html", Seq("foo" -> "", "bar" -> "", "foo" -> "bar"))))
    }
    "handle for empty input" in {
      UriParser("") must be(Return(Uri("", Nil)))
    }
    "handle for null input" in {
      evaluating {UriParser(null)()} must produce [Throwable]
    }
    "handle bad input" in {
      evaluating {UriParser("@)(#*&$@#)")()} must produce [Throwable]
    }
    "strip oauth by default" in {
      UriParser("/?oauth_foo=bar") must be(Return(Uri("/", Nil)))
    }
    "don't strip oauth if asked not to" in {
       UriParser("/?oauth_foo=bar", false) must be(Return(Uri("/", Seq("oauth_foo" -> "bar"))))
    }
  }

  "Uri" should {
    "handle Nil args" in {
      val uri = Uri("/foo", Nil)
      uri.queryString must be("")
      uri.toString must be("/foo")
    }
    "handle args" in {
      val uri = Uri("/foo", Seq("foo" -> "", "bar" -> "", "foo" -> "bar"))
      uri.queryString must be("foo=&bar=&foo=bar")
      uri.toString must be("/foo?foo=&bar=&foo=bar")
    }
  }
}
