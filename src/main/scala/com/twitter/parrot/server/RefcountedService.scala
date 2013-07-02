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

import com.twitter.finagle.{Service, ServiceProxy}
import com.twitter.finagle.util.AsyncLatch
import com.twitter.util.{Future, Promise, Time}

/** Ensure that all responses have come in before we close the service.
 *  
 *  from finagle-core/src/main/scala/com/twitter/finagle/service/RefcountedService.scala.
 */

class RefcountedService[Req, Rep](underlying: Service[Req, Rep])
  extends ServiceProxy[Req, Rep](underlying)
{
  protected[this] val replyLatch = new AsyncLatch

  override def apply(request: Req) = {
    replyLatch.incr()
    underlying(request) ensure { replyLatch.decr() }
  }

  override final def close(deadline: Time): Future[Unit] = {
    val p = new Promise[Unit]
    replyLatch.await {
      p.become(underlying.close(deadline))
    }
    p
  }
}
