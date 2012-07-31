import com.twitter.parrot.config.ParrotLauncherConfig

new ParrotLauncherConfig {
  distDir = "."
  jobName = "load_echo"
  port = 50017
  victims = "localhost"
  log = "config/sample.log"
  requestRate = 1
  numInstances = 1
  duration = 5
  timeUnit = "MINUTES"

  imports = "import com.twitter.jexample.EchoLoadTest"
  responseType = "Array[Byte]"
  transport = "ThriftTransport"
  loadTest = "new EchoLoadTest(service.get)"
  parser = "thrift"
}

