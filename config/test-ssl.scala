import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server.ParrotRequest
import com.twitter.parrot.thrift.TargetHost

new ParrotServerConfig[ParrotRequest, Unit] {
  // DIFFERENCES
  testPort = 443
  testScheme = "https"
  baseline = Option(new TargetHost("https", "api.twitter.com", 443))

  // SAME AS test-server.scala BELOW HERE

  loggers = new LoggerConfig {
    level = Level.DEBUG
    handlers = new ConsoleHandlerConfig()
  }

  thinkTime = 0
  replayTimeCheck = false
  slopTimeInMs = 100
  testHosts = List("api.twitter.com")
  charEncoding = "deflate"
  httpHostHeader = "api.twitter.com"

  // for thrift
  parrotPort = 9999
  thriftName = "parrot"
  clientIdleTimeoutInMs = 15000
  idleTimeoutInSec = 300
  minThriftThreads = 10

}
