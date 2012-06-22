import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.logging._
import com.twitter.util.Duration
import java.util.concurrent.TimeUnit

new ParrotFeederConfig {
  val victimList = "#{victims}"
  jobName = "#{jobName}"
  victimHosts = victimList.split(',').toList
  victimPort = #{port}
  victimScheme = "#{scheme}"
  inputLog = "log/#{logFile}"
  httpPort = 9900

  zkHostName = #{zookeeper}
  zkPort = 2181
  zkNode = "/twitter/service/parrot2/#{jobName}"

  linesToSkip = 0

  numInstances = #{numInstances}
  maxRequests = #{maxRequests}
  batchSize = #{batchSize}
  duration = Duration(#{duration}, TimeUnit.#{timeUnit})
  reuseFile = #{reuseFile}
  requestRate = #{requestRate}
  parser = "#{processor}"
  #{customLogSource}

  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = FileHandler(
      filename = "parrot.log",
      rollPolicy = Policy.Hourly,
      rotateCount = 6
    )
  )
}
