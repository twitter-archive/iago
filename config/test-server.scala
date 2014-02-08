import com.twitter.logging.LoggerFactory
import com.twitter.logging.Level
import com.twitter.logging.config._
import com.twitter.ostrich.admin.AdminServiceFactory
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server._
import com.twitter.parrot.util.LocalCluster
import com.twitter.util.RandomSocket
import org.jboss.netty.handler.codec.http.HttpResponse

new ParrotServerConfig[ParrotRequest, HttpResponse] {

  com.twitter.parrot.util.ConsoleHandler.start(Level.ALL)

  httpPort = RandomSocket().getPort

  thriftServer = Some(new ThriftServer {
    def start(server: ParrotServer[_, _], port: Int){}
    def shutdown() {}
  })

  clusterService = Some(new LocalCluster)
  transport = Some(new DumbTransport)

  thinkTime = 0
  replayTimeCheck = false
  testHosts = List("api.twitter.com")
  charEncoding = "deflate"
    
  victim = HostPortListVictim("twitter.com:80")

  // for thrift
  parrotPort = 9999
  thriftName = "parrot"
  clientIdleTimeoutInMs = 15000
  idleTimeoutInSec = 300
  minThriftThreads = 10
}
