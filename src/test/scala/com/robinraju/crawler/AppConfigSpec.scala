package com.robinraju.crawler

import java.net.URL

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.robinraju.core.{ AppConfig, CacheConfig }

class AppConfigSpec extends AnyWordSpecLike with Matchers {

  "AppConfig" should {
    "load a correct configuration" in {

      // loads config from application-test.conf
      val rootConfig = ConfigFactory.parseString("""
          |web-crawler {
          |  output-directory = "tests-output"
          |  max-crawl-depth = 2
          |  seed-url = "https://crawler-test.com/"
          |
          |  cache {
          |    in-memory-max-cache-size = 100
          |    in-memory-cache-expiry = 3 minutes
          |  }
          |}
          |""".stripMargin)

      val appConfig = AppConfig(rootConfig)

      appConfig shouldBe AppConfig(
        outputDirectory = "tests-output",
        seedUrl = new URL("https://crawler-test.com/"),
        maxCrawlDepth = 2,
        cacheConfig = CacheConfig(
          maxCacheSize = 100,
          cacheExpiry = 3.minutes
        )
      )
    }
  }

}
