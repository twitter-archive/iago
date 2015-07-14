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

import scala.collection.mutable
import scala.util.Random
import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.parrot.util.{IgnorantHostnameVerifier, IgnorantTrustManager}
import com.twitter.util.Future
import com.twitter.util.Time
import com.twitter.util.Try
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import com.twitter.util.Await

trait ParrotTransport[Req <: ParrotRequest, Rep] extends Service[Req, Rep] {
  val log = Logger.get(getClass.getName)
  private[this] val responseHandlers = new mutable.ListBuffer[Try[Rep] => Unit]()
  private[this] val requestHandlers = new mutable.ListBuffer[Req => Unit]()
  private[this] val responseReqHandlers = new mutable.ListBuffer[(Try[Rep], Req) => Unit]()
  override def apply(request: Req): Future[Rep] = {
    requestHandlers foreach { _(request) }

    sendRequest(request) respond { k =>
      log.debug("Response: " + k.toString)
      responseHandlers foreach { _(k) }
      responseReqHandlers foreach { _(k, request) }
    }
  }

  protected[server] def sendRequest(request: Req): Future[Rep]

  def createService(queue: RequestQueue[Req, Rep]) = new ParrotService[Req, Rep](queue)

  def shutdown(): Unit = Await.ready(close())

  def stats(response: Rep): Seq[String] = Nil

  def respond(f: Try[Rep] => Unit) {
    responseHandlers += f
  }

  def request(f: Req => Unit) {
    requestHandlers += f
  }

  def respondReq(f: (Try[Rep], Req) => Unit) {
    responseReqHandlers += f
  }

  def start() {
    // Works around change in Java 6u22 that would otherwise prevent setting some http headers
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

    // Let's just trust everything, otherwise self-signed testing servers will barf.
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCertificates(), new java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier(new IgnorantHostnameVerifier())
  }

  private[this] def trustAllCertificates(): Array[TrustManager] = Array(new IgnorantTrustManager)

  // Random IP generation support
  private[server] val rnd = new Random(Time.now.inMillis)
  private[server] def octet = rnd.nextInt(254) + 1
  private[server] def randomIp = "%d.%d.%d.%d".format(octet, octet, octet, octet)
}
