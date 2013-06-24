package com.twitter.example

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger
import org.apache.thrift.protocol.TBinaryProtocol

import collection.JavaConversions._

import com.twitter.common.quantity.{Time, Amount}
import com.twitter.common.zookeeper.{ServerSetImpl, ZooKeeperClient}
import com.twitter.finagle.builder.{ServerBuilder}
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.logging.Logger
import com.twitter.util.Future

import thrift.EchoService

object EchoServer {
  var port = 8081
  val log = Logger.get(getClass)
  val requestCount = new AtomicInteger(0)

  def main(args: Array[String]) {
    args foreach { arg =>
      val splits = arg.split("=")
      if (splits(0) == "thriftPort") {
        port = splits(1).toInt
      }
    }
    serve(port)
  }

  def serve(port: Int) {
    // Implement the Thrift Interface
    val processor = new EchoService.ServiceIface {
      def echo(message: String) = {
        log.info("echoing message: %s", message)
        requestCount.incrementAndGet
        Future.value(message)
      }
    }

    // Convert the Thrift Processor to a Finagle Service
    val service = new EchoService.Service(processor, new TBinaryProtocol.Factory())

    val address = new InetSocketAddress(port)

    ServerBuilder()
      .bindTo(address)
      .codec(ThriftServerFramedCodec())
      .name("thriftserver")
      .build(service)

  }

  def getRequestCount = requestCount.get
}
