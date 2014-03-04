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

import java.net.URI
import com.twitter.util.{Return, Throw, Try}

case class Uri(path: String, args: Seq[(String, String)]) {
  val queryString = args map { case (key, value) =>
    key + "=" + value
  } mkString("&")

  override val toString = args match {
    case Nil | Seq() => path
    case _ => path + "?" + queryString
  }
}

/**
 * This is a special purpose parser for Twitter URIs. The thing
 * that makes it special is optionally stripping off oauth parameters.
 * It's also optionally adjusting since_id and max_id to keep them relative to "now".
 * That and the fact that a Twitter employee wrote it, of course.
 */
object UriParser {
  def apply(str: String, stripOauth: Boolean = true, rewriteSince: Boolean = true, timestamp: Long = 0): Try[Uri] = try {
    val uri = new URI(str)
    val args: Seq[(String, String)] = uri.getRawQuery match {
      case null => Nil
      case query =>
        query.split("&") flatMap { kv =>
          kv.split("=") match {
            case Array(key, value) => Some(key -> value)
            case Array(key) => Some(key -> "")
            case _ => None
          }
        } filter { case (key, _) =>
          !stripOauth || !key.startsWith("oauth_")
        } map { case (key, value) =>
          if (rewriteSince && (key == "since_id" || key == "max_id")) {
            try {
              (key -> shiftSnowflakeTime(timestamp, value.toLong).toString)
            }
            catch {
              case e: NumberFormatException => (key -> value)
            }
          }
          else {
            (key -> value)
          }
        }
    }
    Return(Uri(uri.getPath, if (args.isEmpty) Nil else args))
  } catch { case t: Throwable =>
    Throw(t)
  }

  // Snowflake related variables.
  val Twepoch = 1288834974657L
  val DatacenterIdBits = 5L
  val WorkerIdBits = 5L
  val SequenceBits = 12L

  val WorkerIdShift = SequenceBits
  val DatacenterIdShift = WorkerIdShift + WorkerIdBits
  val TimestampLeftShift = DatacenterIdShift + DatacenterIdBits

  def shiftSnowflakeTime(from: Long, to: Long) = {
    val snowtime = to >> TimestampLeftShift
    val realtwime = Twepoch + snowtime
    val delta = from - realtwime
    val newTime = System.currentTimeMillis() - delta

    (newTime - Twepoch) << TimestampLeftShift
  }
}
