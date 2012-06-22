import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._
import com.twitter.ostrich.admin.config.AdminServiceConfig
import com.twitter.parrot.config.ParrotServerConfig
import com.twitter.parrot.server._
import com.twitter.parrot.util.LocalCluster
import com.twitter.util.RandomSocket
import org.jboss.netty.handler.codec.http.HttpResponse

// HttpResponse here is because DumbTransport is a ParrotTransport[ParrotRequest, HttpResponse]
new ParrotServerConfig[ParrotRequest, HttpResponse] {
  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = new ConsoleHandlerConfig()
  )

  httpPort = RandomSocket().getPort

  thriftServer = Some(new ThriftServer {
    def start(server: ParrotServer[_, _], port: Int){}
    def shutdown() {}
  })

  clusterService = Some(new LocalCluster)
  transport = Some(new DumbTransport)
  queue = new RequestQueue(this)

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
