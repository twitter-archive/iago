import com.twitter.logging._
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server._
import com.twitter.parrot.util.ParrotClusterImpl
#{responseTypeImport}

new ParrotServerConfig[#{requestType}, #{responseType}] {
  loggers = new LoggerFactory(
    level = Level.INFO,
    handlers = FileHandler(
      filename = "parrot-server.log",
      rollPolicy = Policy.Hourly,
      rotateCount = 6
    )
  ) :: new LoggerFactory(
    node = "stats",
    level = Level.INFO,
    useParents = false,
    handlers = ScribeHandler(
      hostname = "localhost",
      category = "cuckoo_json",
      maxMessagesPerTransaction = 100,
      formatter = BareFormatter
    )
  ) :: loggers

  statsName = "parrot_#{jobName}"
  thinkTime = 0
  replayTimeCheck = false
  slopTimeInMs = 100
  testHosts = List("api.twitter.com")
  charEncoding = "deflate"
  httpHostHeader = Some("#{header}")
  thriftClientId = "#{thriftClientId}"
  reuseConnections = #{reuseConnections}
  hostConnectionLimit = #{hostConnectionLimit}
  hostConnectionCoresize = #{hostConnectionCoresize}
  hostConnectionIdleTimeInMs = #{hostConnectionIdleTimeInMs}
  hostConnectionMaxIdleTimeInMs = #{hostConnectionMaxIdleTimeInMs}
  hostConnectionMaxLifeTimeInMs = #{hostConnectionMaxLifeTimeInMs}

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
