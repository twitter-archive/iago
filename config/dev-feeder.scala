import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.parrot.config.ParrotFeederConfig

new ParrotFeederConfig {
  victimPort = 8888
  victimHosts = List("localhost")
  inputLog = "/Users/jw/logs/20"
  admin.httpPort = 9900

  parrotPort = 9999
  parrotHosts = List("localhost")

  batchSize = 1000
  maxRequests = 20

  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = new ConsoleHandlerConfig()
  )
}
