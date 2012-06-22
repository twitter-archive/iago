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

import scala.collection.mutable.{ LinkedHashMap, Map }

/**
 * This simple json parser decomposes json input into a single flat HashMap
 * where the keys correspond to the depth-first path to the value, separated
 * by '/', and including array indices.  Right hand values are just uninterpreted
 * strings, meaning escape characters have not been processed.  String values
 * include open and close quotes, allowing you to distinguish strings from tokens.
 */
object FlatJsonParser extends (String => Map[String, String]) {
  def apply(json: String): Map[String, String] = {
    val map = new LinkedHashMap[String, String]
    var path: List[String] = Nil

    def add(value: String) = map(path.reverse.mkString("/")) = value

    def matchValue(start: Int): Int = json.charAt(start) match {
      case '{' => matchObject(start + 1)
      case '[' => matchArray(start + 1, 0)
      case '"' =>
        val end = findEndQuote(start + 1)
        add(json.substring(start, end + 1)) // include quotes
        end + 1
      case ' ' | '\t' | '\n' | '\r' | '\f' => matchValue(start + 1)
      case _ =>
        val end = findTokenEnd(start)
        add(json.substring(start, end))
        end
    }

    def findEndQuote(start: Int): Int = json.charAt(start) match {
      case '"' => start
      case '\\' => findEndQuote(start + 2)
      case _ => findEndQuote(start + 1)
    }

    def findTokenEnd(start: Int): Int = json.charAt(start) match {
      case ',' | ']' | '}' | ' ' | '\t' | '\n' | '\r' | '\f' => start
      case _ => findTokenEnd(start + 1)
    }

    def matchObject(start: Int): Int = json.charAt(start) match {
      case ' ' | '\t' | '\n' | '\r' | '\f' => matchObject(start + 1)
      case '}' => start + 1
      case '"' =>
        var end = findEndQuote(start + 1)
        path = json.substring(start + 1, end) :: path
        end = skipWs(end + 1)
        assert(json.charAt(end) == ':')
        end = skipWs(matchValue(end + 1))
        path = path.tail
        json.charAt(end) match {
          case ',' => matchObject(end + 1)
          case '}' => end + 1
          case c => throw new IllegalStateException("expected ',' or '}', found '" + c + "'")
        }
    }

    def matchArray(start: Int, index: Int): Int = json.charAt(start) match {
      case ' ' | '\t' | '\n' | '\r' | '\f' => matchArray(start + 1, index)
      case ']' => start + 1
      case _ =>
        path = index.toString :: path
        var end = skipWs(matchValue(start))
        path = path.tail
        json.charAt(end) match {
          case ',' => matchArray(end + 1, index + 1)
          case ']' => end + 1
          case c => throw new IllegalStateException("expected ',' or ']', found '" + c + "'")
        }
    }

    def skipWs(start: Int): Int = json.charAt(start) match {
      case ' ' | '\t' | '\n' | '\r' | '\f' => skipWs(start + 1)
      case _ => start
    }

    matchValue(0)
    map
  }
}

