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

import com.twitter.finagle.Service
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.util.{Future, Promise}

class ParrotThriftService(config: ParrotServerConfig[ParrotRequest, Array[Byte]])
  extends ParrotService[ParrotRequest, Array[Byte]](config)

class ParrotThriftServiceWrapper(val service: ParrotService[ParrotRequest, Array[Byte]])
  extends Service[ThriftClientRequest, Array[Byte]]
{
  def apply(req: ThriftClientRequest): Future[Array[Byte]] = {
    val job = service.jobRef.get
    val target = service.chooseRandomVictim

    val response = new Promise[Array[Byte]]()
    val request = new ParrotRequest(target, message = req, response = response)
    service.queue.addRequest(job, request, response)
    response
  }

  override def release() {
    service.release()
  }

  override def isAvailable = service.isAvailable
}
