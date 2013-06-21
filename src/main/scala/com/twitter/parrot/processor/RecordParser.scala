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
package com.twitter.parrot.processor

import collection.JavaConverters._
import com.twitter.ostrich.stats.Stats
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.util.Uri
import java.util.{List => JList}

trait Record {
  def uri: Uri
  def rawLine: String
  def timestamp: Long
}

/**
 * parses lines into records, which are mapped to results
 */
trait RecordParser[Res] {
  def apply(lines: JList[String]): JList[Res] = this(lines.asScala).asJava

  def apply(lines: Seq[String]): Seq[Res]

  def splitWords(l: String): Array[String] = l.toString.trim.split("[ \\t]+")
}
