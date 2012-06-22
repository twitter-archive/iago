import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.logging._
import com.twitter.parrot.feeder.LogSourceImpl
import com.twitter.util.Duration
import java.util.concurrent.TimeUnit

new ParrotFeederConfig {
  val victimList = "#{victims}"
  jobName = "#{jobName}"
  victimHosts = victimList.split(',').toList
  victimPort = #{port}
  victimScheme = "#{scheme}"
  inputLog = "#{fullLog}"
  httpPort = 9900

  zkHostName = None
  zkPort = -1
  zkNode = ""

  linesToSkip = 0

  numInstances = #{numInstances}
  maxRequests = #{maxRequests}
  batchSize = math.min(maxRequests, 1000)
  duration = Duration(#{duration}, TimeUnit.#{timeUnit})
  reuseFile = #{reuseFile}
  requestRate = #{requestRate}
  parser = "#{processor}"
  #{customLogSource}

  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = FileHandler(
      filename = "parrot-feeder.log",
      rollPolicy = Policy.Hourly,
      rotateCount = 6
    )
  )
}
