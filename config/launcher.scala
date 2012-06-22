import com.twitter.parrot.config.ParrotLauncherConfig

new ParrotLauncherConfig {
  localMode = true
  jobName = "testrun"
  port = 80
  victims = "www.google.com"
  log = "config/replay.log"
  requestRate = 5
}

