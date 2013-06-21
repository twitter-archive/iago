import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server.ParrotRequest

new ParrotServerConfig[ParrotRequest, Unit] {
  // DIFFERENCES
  testPort = 443
  testScheme = "https"

  // SAME AS test-server.scala BELOW HERE

  com.twitter.parrot.util.ConsoleHandler.start(Level.ALL)
  
  thinkTime = 0
  replayTimeCheck = false
  slopTimeInMs = 100
  testHosts = List("api.twitter.com")
  charEncoding = "deflate"

  // for thrift
  parrotPort = 9999
  thriftName = "parrot"
  clientIdleTimeoutInMs = 15000
  idleTimeoutInSec = 300
  minThriftThreads = 10

}
