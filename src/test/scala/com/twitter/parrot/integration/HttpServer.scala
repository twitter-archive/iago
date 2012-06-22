package com.twitter.parrot.integration

import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.util.CharsetUtil.UTF_8

/**
 * This is a simple HTTP server that simply returns "hello world" and counts
 * how many requests it receives for the purposes of integration testing. It
 * also handles exceptions just in case odd things happen.
 */
object HttpServer {
  val requestCount = new AtomicInteger(0)

  class HandleExceptions extends SimpleFilter[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {

      // `handle` asynchronously handles exceptions.
      service(request) handle { case error =>
        val statusCode = error match {
          case _: IllegalArgumentException =>
            FORBIDDEN
          case _ =>
            INTERNAL_SERVER_ERROR
        }
        val errorResponse = new DefaultHttpResponse(HTTP_1_1, statusCode)
        errorResponse.setContent(copiedBuffer(error.getStackTraceString, UTF_8))

        errorResponse
      }
    }
  }

  /**
   * The service itself. Simply echos back "hello world"
   */
  class Respond extends Service[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest) = {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)
      response.setContent(copiedBuffer("hello world", UTF_8))
      requestCount.incrementAndGet
      Future.value(response)
    }
  }

  def serve(port: Int) {
    val handleExceptions = new HandleExceptions
    val respond = new Respond

    // compose the Filters and Service together:
    val myService: Service[HttpRequest, HttpResponse] = handleExceptions andThen respond

    val server: Server = ServerBuilder()
      .codec(Http())
      .bindTo(new InetSocketAddress(port))
      .name("httpserver")
      .build(myService)
  }

  def getAndResetRequests(): Int = {
    requestCount.getAndSet(0)
  }
}