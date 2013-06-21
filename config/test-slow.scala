import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server.ParrotRequest

new ParrotServerConfig[ParrotRequest, Unit] {
  // DIFFERENCES
  replayTimeCheck = true
  slopTimeInMs = 500
  thinkTime = 500

  com.twitter.parrot.util.ConsoleHandler.start(Level.ALL)

  testHosts = List("api.twitter.com")
  charEncoding = "deflate"

  // for thrift
  parrotPort = 9999
  thriftName = "parrot"
  clientIdleTimeoutInMs = 15000
  idleTimeoutInSec = 300
  minThriftThreads = 10
}
