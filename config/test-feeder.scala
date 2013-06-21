import com.twitter.logging.LoggerFactory
import com.twitter.logging.Level
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.util.RandomSocket

new ParrotFeederConfig {
  com.twitter.parrot.util.ConsoleHandler.start(Level.ALL)
  httpPort = RandomSocket().getPort
  parrotHosts = List("localhost")

  batchSize = 1000
  maxRequests = 20

}
