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

import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse

import com.twitter.finagle.Service
import com.twitter.finagle.ServiceFactory
import com.twitter.util.Future
import com.twitter.util.Promise
import com.twitter.util.Time

/**
 * FinagleServiceAbstraction is used to hide that we may or may not be reusing connections from
 * FinagleTransport.
 */
private[server] sealed abstract class FinagleServiceAbstraction {
  def close(deadline: Time): Future[Unit]
  def send(httpRequest: DefaultHttpRequest, request: ParrotRequest): Future[HttpResponse]
  protected def send(httpRequest: DefaultHttpRequest, service: Service[HttpRequest, HttpResponse],
    request: ParrotRequest): Future[HttpResponse] =
    {
      val result = service.apply(httpRequest)
      val response = request.response.asInstanceOf[Promise[HttpResponse]]
      result proxyTo response
      result
    }
}

/** We use FinagleService when reuseConnections = true, which is the default. */
private[server] case class FinagleService(service: Service[HttpRequest, HttpResponse])
  extends FinagleServiceAbstraction {
  def close(deadline: Time): Future[Unit] = service.close(deadline)
  def send(httpRequest: DefaultHttpRequest, request: ParrotRequest): Future[HttpResponse] =
    send(httpRequest, service, request)
}

/** We use FinagleServiceFactory when reuseConnections = false */
private[server] case class FinagleServiceFactory(factory: ServiceFactory[HttpRequest, HttpResponse])
  extends FinagleServiceAbstraction {
  def close(deadline: Time): Future[Unit] = factory.close(deadline)
  def send(httpRequest: DefaultHttpRequest, request: ParrotRequest): Future[HttpResponse] =
    send(httpRequest, factory.toService, request)
}
