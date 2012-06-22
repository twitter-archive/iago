import com.twitter.finagle.memcached.protocol.Response
import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.ostrich.admin.config.AdminServiceConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server._
import com.twitter.parrot.util.LocalCluster
import com.twitter.util.RandomSocket

new ParrotServerConfig[ParrotRequest, Response] {
  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = new ConsoleHandlerConfig()
  )

  httpPort = RandomSocket().getPort

  thriftServer = None
  clusterService = Some(new LocalCluster)
  transport = Some(new MemcacheTransport)
  queue = new RequestQueue(this)
}
