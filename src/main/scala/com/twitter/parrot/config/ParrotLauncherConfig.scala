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

import com.twitter.logging.LoggerFactory
import com.twitter.parrot.launcher.{ CommandRunner, ParrotLauncher }
import com.twitter.util.Config
import com.twitter.logging.Level

trait ParrotLauncherConfig extends Config[ParrotLauncher] with ParrotCommonConfig {
  var batchSize = 1000
  var customLogSource = "" // Raw scala can be inserted in here and will end up in the Feeder config
  var configType = "ParrotServerConfig"
  var cronSchedule: Option[String] = None
  var distDir = "."
  var doConfirm = true
  var doOAuth = true
  var duration = 5
  var env = "devel"	// the mesos environment
  var feederDiskInMb: Long = 120
  var feederXmx = 1744
  var header = ""
  var hostConnectionCoresize = 1
  var hostConnectionIdleTimeInMs = 60000 // 1m
  var hostConnectionLimit = Integer.MAX_VALUE
  var hostConnectionMaxIdleTimeInMs = 300000 // 5m
  var hostConnectionMaxLifeTimeInMs = Integer.MAX_VALUE
  var jobName = required[String]
  var localMode = false
  var log = required[String]
  var maxPerHost = 1
  var maxRequests = 1000
  var mesosEnv = "devel"
  var mesosFeederRamInMb: Option[Int] = None
  var mesosServerRamInMb: Option[Int] = None
  
  var numFeederInstances = 1
  var numInstances = 1
  var requestTimeoutInMs = 30 * 1000
  var requestRate = 1
  var reuseConnections = true
  var reuseFile = true
  var role = System.getenv("USER")
  var scheme = "http"
  var serverDiskInMb = 120
  var serverXmx = 4000
  var tcpConnectTimeoutInMs = Integer.MAX_VALUE
  var thriftClientId = ""
  var timeUnit = "MINUTES"
  var traceLevel: Level = Level.INFO
  var verboseCmd = false
  var includeParrotHeader = true
  var mesosFeederNumCpus = 5.0
  var mesosServerNumCpus = 4.0


  // victims. When victimClusterType is "static", we set victims and
  // port. victims can be a single host name, a host:port pair, or a
  // list of host:port pairs separated with commas or spaces. The port
  // is used for two things: to provide a port if none were specified in
  // victims, and to provide a port for the host header using a
  // FinagleTransport. Note that ParrotUdpTransport can only handle a
  // single host:port pair.

  var victimClusterType = "static"

  var victims = required[String]
  var port = 80

  // When victimClusterType is "sdzk", the victim is considered to be a
  // server set, referenced with victims, victimZk, and victimZkPort. An
  // example victims in this case would be
  // "/twitter/service/devprod/devel/echo"

  var victimZk = "sdzookeeper.local.twitter.com"
  var victimZkPort = 2181

  // Extension points
  var imports =
    """import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.parrot.util.LoadTestStub
  """
  var requestType = "ParrotRequest"
  var responseType = "HttpResponse"
  var transport = "FinagleTransportFactory(this)"
  var thriftProtocolFactory = "new org.apache.thrift.protocol.TBinaryProtocol.Factory()"
  var loadTest = "new LoadTestStub(service.get)"

  // e.g., createDistribution = "createDistribution = { rate => new MyDistribution(rate) }"
  // see also imports, must create a subclass of RequestDistribution
  var createDistribution = ""

  // these variables are data-center specific
  var hadoopNS = "hdfs://hadoop-nn.smf1.twitter.com"
  var hadoopConfig = "/etc/hadoop/hadoop-conf-smf1"
  var mesosCluster = "smf1"
  zkHostName = Some("zookeeper.smf1.twitter.com")

  var proxy: Option[String] = Some("nest2.corp.twitter.com")

  // Override these at your own risk
  val parrotTasks = List("server", "feeder")
  val parrotLogsDir = "/mesos/pkg/parrot/logs"
  val proxyShell = "ssh -o ForwardX11=no"
  val proxyCp = "scp"
  val proxyMkdir = "mkdir -p"
  val hadoopFS = "fs"
  val hadoopCmd = "hadoop"

  zkPort = 2181
  zkNode = "/twitter/service/parrot2/%s"

  def apply() = {
    val missing = missingValues
    if (missing.isEmpty) {
      CommandRunner.setVerbose(verboseCmd)
      new ParrotLauncher(this)
    } else {
      missing foreach { config: String => println("Config parameter missing: " + config) }
      throw new Exception("Launcher creation failed with unspecified required configs")
    }
  }

  // You should probably override this. It is the command for making an
  // archive of your parrot application. The default copies too much in
  // general.
  def archiveCommand(name: String) =
    "jar Mcf %s.zip -C %s .".format(name, distDir)

  def mesosJobname(task: String) =
    "%s/%s/%s/parrot_%s_%s".format(mesosCluster, role, mesosEnv, task, jobName.value)
}
