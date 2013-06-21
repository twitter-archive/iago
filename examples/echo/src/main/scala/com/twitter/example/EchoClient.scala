package com.twitter.example

import java.net.InetSocketAddress
import org.apache.thrift.protocol.TBinaryProtocol

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.Service
import com.twitter.finagle.thrift.{ThriftClientFramedCodec, ThriftClientRequest}

import thrift.EchoService

object EchoClient {
  def main(args: Array[String]) {
    // Create a raw Thrift client service. This implements the
    // ThriftClientRequest => Future[Array[Byte]] interface.
    val service: Service[ThriftClientRequest, Array[Byte]] = ClientBuilder()
      .hosts(new InetSocketAddress(EchoServer.port))
      .codec(ThriftClientFramedCodec())
      .hostConnectionLimit(1)
      .build()

    // Wrap the raw Thrift service in a Client decorator. The client
    // provides a convenient procedural interface for accessing the Thrift
    // server.
    val client = new EchoService.ServiceToClient(service, new TBinaryProtocol.Factory())

    client.echo("hello") onSuccess { response =>
      println("Received response: " + response)
    } ensure {
      service.release()
    }
  }
}
