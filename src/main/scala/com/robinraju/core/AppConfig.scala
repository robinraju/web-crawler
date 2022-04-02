package com.robinraju.core

import java.net.URL

import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.Config

final case class AppConfig(
    outputDirectory: String,
    seedUrl: URL,
    maxCrawlDepth: Int,
    cacheConfig: CacheConfig
)

object AppConfig {
  def apply(rootConfig: Config): AppConfig =
    AppConfig(
      outputDirectory = rootConfig.getString("web-crawler.output-directory"),
      seedUrl = new URL(rootConfig.getString("web-crawler.seed-url")),
      maxCrawlDepth = rootConfig.getInt("web-crawler.max-crawl-depth"),
      cacheConfig = CacheConfig(rootConfig.getConfig("web-crawler.cache"))
    )
}

final case class CacheConfig(maxCacheSize: Int, cacheExpiry: FiniteDuration)

object CacheConfig {
  def apply(rootConfig: Config): CacheConfig =
    CacheConfig(
      maxCacheSize = rootConfig.getInt("in-memory-max-cache-size"),
      cacheExpiry = Duration.fromNanos(rootConfig.getDuration("in-memory-cache-expiry").toNanos)
    )
}
