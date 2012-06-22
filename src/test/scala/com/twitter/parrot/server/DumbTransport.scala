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

import com.twitter.util.{Promise, Future}
import java.util.concurrent.atomic.AtomicLong
import org.jboss.netty.handler.codec.http.HttpResponse

/**
 * This class doesn't actually send requests, but it remembers
 * that we asked to send them, so we can test logic that is
 * dependent on ParrotTransport.
 */
class DumbTransport() extends ParrotTransport[ParrotRequest, HttpResponse] {
  val counter = new AtomicLong(0)

  override protected[server] def sendRequest(request: ParrotRequest): Future[HttpResponse] = {
    counter.getAndIncrement
    new Promise[HttpResponse]
  }

  def sent = counter.get
}
