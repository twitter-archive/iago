import com.twitter.conversions.storage._
import com.twitter.logging._
import com.twitter.parrot.config.ParrotFeederConfig
import com.twitter.util.Duration
import java.util.concurrent.TimeUnit
#{imports}

new ParrotFeederConfig {
  jobName = "#{jobName}"
  inputLog = "#{logFile}"
  httpPort = 9900

  zkHostName = #{zkHostName}
  zkNode = "#{zkNode}"
  zkPort = #{zkPort}

  linesToSkip = 0

  numInstances = #{numInstances}
  maxRequests = #{maxRequests}
  batchSize = #{batchSize}
  duration = Duration(#{duration}, TimeUnit.#{timeUnit})
  reuseFile = #{reuseFile}
  requestRate = #{requestRate}
  #{customLogSource}

  loggers = new LoggerFactory(
    level = Level.#{traceLevel},
    handlers = FileHandler(
      filename = "parrot-feeder.log",
      rollPolicy = Policy.MaxSize(100.megabytes),
      rotateCount = 6
    )
  )
}
