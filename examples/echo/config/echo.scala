import com.twitter.parrot.config.ParrotLauncherConfig

new ParrotLauncherConfig {
  distDir = "."
  jobName = "load_echo"
  port = 8081
  victims = "localhost"
  //log = "config/sample.log"
  log = "/Users/thowland/src/osiago/examples/echo/config/echo.scala"
  requestRate = 1
  numInstances = 1
  duration = 5
  timeUnit = "MINUTES"
  reuseFile = true
  localMode = true

  imports = "import com.twitter.example.EchoLoadTest"
  responseType = "Array[Byte]"
  transport = "ThriftTransport"
  loadTest = "new EchoLoadTest(service.get)"
}
