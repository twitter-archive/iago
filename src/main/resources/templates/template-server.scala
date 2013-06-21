import com.twitter.conversions.storage._
import com.twitter.logging._
import com.twitter.parrot.config._
import com.twitter.parrot.server._
import com.twitter.parrot.util.ParrotClusterImpl

#{imports}

new #{configType}[#{requestType}, #{responseType}] {

  loggers = new LoggerFactory(
    level = Level.#{traceLevel},
    handlers = FileHandler(
      filename = "parrot-server.log",
      rollPolicy = Policy.MaxSize(100.megabytes),
      rotateCount = 6
    )
  ) #{statlogger} :: loggers

  zkHostName = #{zkHostName}
  zkNode = "#{zkNode}"
  zkPort = #{zkPort}

  victim = #{victim}

  statsName = "parrot_#{jobName}"
  thinkTime = 0
  replayTimeCheck = false
  slopTimeInMs = 100
  testHosts = List("api.twitter.com")
  charEncoding = None
  httpHostHeader = "#{header}"
  httpHostHeaderPort = #{httpHostHeaderPort}
  thriftClientId = "#{thriftClientId}"
  reuseConnections = #{reuseConnections}
  hostConnectionLimit = #{hostConnectionLimit}
  hostConnectionCoresize = #{hostConnectionCoresize}
  hostConnectionIdleTimeInMs = #{hostConnectionIdleTimeInMs}
  hostConnectionMaxIdleTimeInMs = #{hostConnectionMaxIdleTimeInMs}
  hostConnectionMaxLifeTimeInMs = #{hostConnectionMaxLifeTimeInMs}
  requestTimeoutInMs = #{requestTimeoutInMs}
  tcpConnectTimeoutInMs = #{tcpConnectTimeoutInMs}

  // for thrift
  parrotPort = 9999
  thriftName = "parrot"
  clientIdleTimeoutInMs = 15000
  idleTimeoutInSec = 300
  minThriftThreads = 10

  // request distribution -- default will be to do nada
  #{createDistribution}

  transport = Some(new #{transport}(this))
  queue = Some(new RequestQueue(this))
  thriftServer = Some(new ThriftServerImpl)
  clusterService = Some(new ParrotClusterImpl(this))

  // configure after transport so that service is valid
  loadTestInstance = Some(#{loadTest})

  // Put config options past this point at your own risk
}
