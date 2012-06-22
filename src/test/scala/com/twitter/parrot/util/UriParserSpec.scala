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

import com.twitter.util.Return
import org.specs.SpecificationWithJUnit

class UriParserSpec extends SpecificationWithJUnit {
  "UriParser" should {
    "extract /" in {
      UriParser("/") mustEqual Return(Uri("/", Nil))
    }
    "extract / with args" in {
      UriParser("/?foo") mustEqual Return(Uri("/", Seq("foo" -> "")))
    }
    "extract non-toplevel" in {
      UriParser("foo") mustEqual Return(Uri("foo", Nil))
    }
    "extract non-toplevel with args" in {
      UriParser("foo?bar=baz") mustEqual Return(Uri("foo", Seq("bar" -> "baz")))
    }
    "extract long path" in {
      UriParser("/foo/bar/baz.html") mustEqual Return(Uri("/foo/bar/baz.html", Nil))
    }
    "extract long path with args" in {
      val uri = UriParser("/foo/bar/baz.html?foo&bar=&foo=bar")
      uri mustEqual Return(Uri("/foo/bar/baz.html", Seq("foo" -> "", "bar" -> "", "foo" -> "bar")))
    }
    "handle for empty input" in {
      UriParser("") mustEqual Return(Uri("", Nil))
    }
    "handle for null input" in {
      UriParser(null)() must throwA[Throwable]
    }
    "handle bad input" in {
      UriParser("@)(#*&$@#)")() must throwA[Throwable]
    }
    "strip oauth by default" in {
      UriParser("/?oauth_foo=bar") mustEqual Return(Uri("/", Nil))
    }
    "don't strip oauth if asked not to" in {
       UriParser("/?oauth_foo=bar", false) mustEqual Return(Uri("/", Seq("oauth_foo" -> "bar")))
    }
  }

  "Uri" should {
    "handle Nil args" in {
      val uri = Uri("/foo", Nil)
      uri.queryString mustEqual ""
      uri.toString mustEqual "/foo"
    }
    "handle args" in {
      val uri = Uri("/foo", Seq("foo" -> "", "bar" -> "", "foo" -> "bar"))
      uri.queryString mustEqual ("foo=&bar=&foo=bar")
      uri.toString mustEqual "/foo?foo=&bar=&foo=bar"
    }
  }
}
