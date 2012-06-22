import com.twitter.parrot.config.ParrotLauncherConfig

new ParrotLauncherConfig {
  localMode = true
  jobName = "testrun"
  port = 80
  victims = "twitter.com"
  log = "config/replay.log"
  requestRate = 5
  maxRequests = 20
  reuseFile = false
}