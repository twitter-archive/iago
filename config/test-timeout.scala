import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server.ParrotRequest

new ParrotServerConfig[ParrotRequest, Unit] {
  // DIFFERENCES
  connectionTimeoutInMs = 10L

  // SAME AS test-server.scala BELOW HERE

  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = new ConsoleHandlerConfig()
  )

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
