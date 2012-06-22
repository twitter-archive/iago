import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.util.RandomSocket

new ParrotFeederConfig {
  httpPort = RandomSocket().getPort
  parrotHosts = List("localhost")

  batchSize = 1000
  maxRequests = 20

  loggers = new LoggerFactory(
    level = Level.INFO,
    handlers = new ConsoleHandlerConfig()
  )
}
