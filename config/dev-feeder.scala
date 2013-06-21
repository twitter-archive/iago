import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotFeederConfig

new ParrotFeederConfig {
  inputLog = "/Users/jw/logs/20"

  parrotPort = 9999
  parrotHosts = List("localhost")

  batchSize = 1000
  maxRequests = 20

  loggers = new LoggerFactory(
    level = Level.ALL,
    handlers = new ConsoleHandlerConfig()
  )
}
