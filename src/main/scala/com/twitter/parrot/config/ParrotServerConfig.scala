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
package com.twitter.parrot.config

import java.net.InetAddress
import java.net.InetSocketAddress

import scala.collection.JavaConverters.asJavaIterableConverter

import com.twitter.common.quantity.Amount
import com.twitter.common.quantity.Time
import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.AdminServiceFactory
import com.twitter.ostrich.admin.JsonStatsLoggerFactory
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.ostrich.admin.Service
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.ostrich.admin.StatsFactory
import com.twitter.ostrich.admin.TimeSeriesCollectorFactory
import com.twitter.parrot.processor.RecordProcessor
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.server.ParrotServerImpl
import com.twitter.parrot.server.ParrotTransport
import com.twitter.parrot.server.RequestQueue
import com.twitter.parrot.server.ThriftServer
import com.twitter.parrot.util.ParrotCluster
import com.twitter.parrot.util.PoissonProcess
import com.twitter.parrot.util.RequestDistribution
import com.twitter.util.Config

trait ParrotServerConfig[Req <: ParrotRequest, Rep] extends Config[RuntimeEnvironment => Service]
  with ParrotCommonConfig
  with CommonParserConfig {

  sealed abstract class Victim

  /**
   * @param hostnamePortCombinations is of the form "foo:49,bar:55". You can uses spaces instead of
   * commas.
   */
  case class HostPortListVictim(hostnamePortCombinations: String) extends Victim

  case class ServerSetVictim(cluster: ZookeeperServerSetCluster) extends Victim

  object ServerSetVictim {
    def apply(path: String, zk: String = "sdzookeeper.local.twitter.com", zkPort: Int = 2181): ServerSetVictim = {
      val zookeeperClient = new ZooKeeperClient(Amount.of(1, Time.SECONDS),
        Seq(InetSocketAddress.createUnresolved(zk, zkPort)).asJava)
      val serverSet = new ServerSetImpl(zookeeperClient, path)
      ServerSetVictim(new ZookeeperServerSetCluster(serverSet))
    }
  }

  var victim = required[Victim]

  object TransportScheme extends Enumeration {
    val HTTPS = Value("https")
    val HTTP = Value("http")
    val THRIFTS = Value("thrifts")
  }
  
  var transportScheme = TransportScheme.HTTP

  var thriftServer: Option[ThriftServer] = None
  var clusterService: Option[ParrotCluster] = None
  var transport: Option[ParrotTransport[Req, Rep]] = None
  var queue: Option[RequestQueue[Req, Rep]] = None

  var maxJobs = 100 // How many jobs can we have running simultaneously?

  var statsName = "parrot-server"
  var thriftName = "parrot"
  var minThriftThreads = 10
  var httpPort = 9994
  var httpHostHeader = ""
  var httpHostHeaderPort = 80

  var numWorkers = 5

  var testHosts = List("")
  var testPort = 80
  var testScheme = "http"

  // Finagle stuff
  var clientIdleTimeoutInMs = 15000
  var connectionTimeoutInMs = 0L
  var hostConnectionCoresize = 1
  var hostConnectionIdleTimeInMs = 5000
  var hostConnectionLimit = Integer.MAX_VALUE
  var hostConnectionMaxIdleTimeInMs = 5000
  var hostConnectionMaxLifeTimeInMs = Integer.MAX_VALUE
  var requestTimeoutInMs = 30 * 1000
  var tcpConnectTimeoutInMs = Integer.MAX_VALUE
  var idleTimeoutInSec = 300
  var reuseConnections = true
  var thriftClientId = ""

  var thinkTime = 0L
  var replayTimeCheck = false
  var charEncoding: Option[String] = None
  var printResponses = false
  var requestQueueDepth = 10000

  //  Parrot as a library support hooks
  var loadTestName = "thrift"
  var loadTestInstance: Option[RecordProcessor] = None

  def recordProcessor: RecordProcessor = loadTestInstance.get

  lazy val service = transport.map { _.createService(this) }

  // Customizable Request Distribution
  var createDistribution: Int => RequestDistribution = { rate =>
    new PoissonProcess(rate)
  }

  def apply() = { (runtime: RuntimeEnvironment) =>
    Logger.configure(loggers)

    val adminPort = runtime.arguments.get("httpPort").map(_.toInt).getOrElse(httpPort)
    parrotPort = runtime.arguments.getOrElse("thriftPort", parrotPort.toString).toInt
    this.runtime = runtime

    var admin = new AdminServiceFactory(
      adminPort,
      statsNodes = new StatsFactory(
        reporters = new JsonStatsLoggerFactory(
          period = 1.minute,
          serviceName = statsName) :: new TimeSeriesCollectorFactory()))(runtime)

    val server = new ParrotServerImpl(this)

    val result = new Service() {
      def start() { server.start }
      def shutdown() {
        ServiceTracker.shutdown()
        server.shutdown
      }
    }
    ServiceTracker.register(result)
    result
  }
}
