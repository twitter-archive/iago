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

import com.twitter.conversions.time._
import com.twitter.logging.{Logger, LoggerFactory}
import com.twitter.ostrich.admin._
import com.twitter.parrot.processor._
import com.twitter.parrot.server._
import com.twitter.parrot.thrift.TargetHost
import com.twitter.parrot.util.{PoissonProcess, RequestDistribution, ParrotCluster}
import com.twitter.util.{Duration, Config}
import org.jboss.netty.handler.codec.http.HttpResponse

trait ParrotServerConfig[Req <: ParrotRequest, Rep] extends Config[RuntimeEnvironment => Service]
  with ParrotCommonConfig
  with CommonParserConfig
{
  var loggers: List[LoggerFactory] = Nil
  var thriftServer: Option[ThriftServer] = None
  var clusterService: Option[ParrotCluster] = None
  var transport: Option[ParrotTransport[Req, Rep]] = None
  var queue: Option[RequestQueue[Req, Rep]] = None

  var maxJobs = 100 // How many jobs can we have running simultaneously?

  var statsName = "parrot"
  var thriftName = "parrot"
  var minThriftThreads = 10
  var httpPort = 9994
  var httpHostHeader: Option[String] = None

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
  var requestTimeoutInMs = Integer.MAX_VALUE
  var tcpConnectTimeoutInMs = Integer.MAX_VALUE
  var idleTimeoutInSec = 300
  var reuseConnections = true
  var thriftClientId = ""

  // compare mode off by setting this to None
  var baseline: Option[TargetHost] = None

  var slopTimeInMs = 0L
  var thinkTime = 0L
  var replayTimeCheck = false
  var charEncoding: Option[String] = None
  var printResponses = false
  var requestQueueDepth = 10000

  //  Parrot as a library support hooks
  var loadTestName = "thrift"
  var loadTestInstance: Option[RecordProcessor] = None

  lazy val service = transport.map { _.createService(this) }

  // Customizable Request Distribution
  var createDistribution: Int => RequestDistribution = { rate =>
    new PoissonProcess(rate)
  }

  def apply() = { (runtime: RuntimeEnvironment) =>
    Logger.configure(loggers)

    val adminPort = runtime.arguments.get("httpPort").map(_.toInt).getOrElse(httpPort)
    parrotPort = runtime.arguments.getOrElse("thriftPort", parrotPort.toString).toInt

    var admin = new AdminServiceFactory (
      adminPort,
      statsNodes = new StatsFactory(
        reporters = new JsonStatsLoggerFactory(
          period = 1.minute,
          serviceName = Some(statsName)
        ) :: new TimeSeriesCollectorFactory()
      )
    )(runtime)

    val server = new ParrotServerImpl(this)

    // Processor registration
    service.map { svc => svc.registerDefaultProcessors() }
    loadTestInstance foreach { RecordProcessorFactory.registerProcessor(loadTestName, _) }

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
