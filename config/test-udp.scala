import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.ostrich.admin.config.AdminServiceConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server._
import com.twitter.parrot.util.LocalCluster
import com.twitter.util.RandomSocket
import org.jboss.netty.handler.codec.string.{StringDecoder, StringEncoder}
import org.jboss.netty.util.CharsetUtil

new ParrotServerConfig[ParrotRequest, String] {
  com.twitter.parrot.util.ConsoleHandler.start(Level.ALL)

  httpPort = RandomSocket().getPort

  thriftServer = None
  clusterService = Some(new LocalCluster)
}
