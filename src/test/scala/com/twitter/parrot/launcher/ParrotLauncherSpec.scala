package com.twitter.parrot.launcher

import com.twitter.parrot.config.ParrotLauncherConfig

import org.junit.runner.RunWith
import org.scalatest.OneInstancePerTest
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.MustMatchers

@RunWith(classOf[JUnitRunner])
class ParrotLauncherSpec extends WordSpec with MustMatchers with OneInstancePerTest {
  val config =
    new ParrotLauncherConfig {
      localMode = true
      jobName = "test"
      log = "test"
      victims = "localhost:80"
    }

  "ParrotLauncher" should {

    for (xport <- Set("FinagleTransport", "ThriftTransport", "KestrelTransport", "MemcacheTransport")) {
      "rewrite old '%s' transport configs as '%sFactory(this)'".format(xport, xport) in {
        config.transport = xport
        val launcher = new ParrotLauncher(config)
        launcher.readSymbols("transport") must be(xport + "Factory(this)")
      }
    }

    "not rewrite other transport configs" in {
      for (xport <- Set("FinagleTransportFactory(this)", "KestrelTransportFactory", "CustomTransport")) {
        config.transport = xport
        val launcher = new ParrotLauncher(config)
        launcher.readSymbols("transport") must be(xport)
      }
    }

  }
}
