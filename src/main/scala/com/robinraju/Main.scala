package com.robinraju

import java.net.URL

import scala.util.Try

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior, Terminated }
import com.typesafe.config.ConfigFactory
import org.slf4j.{ Logger, LoggerFactory }

import com.robinraju.cache.InMemoryCrawlerCache
import com.robinraju.core.AppConfig
import com.robinraju.crawler.CrawlManager
import com.robinraju.io.TSVWriter

object Main {

  /**
    * Force an early initialisation of SLF4J to avoid error codes like described here:
    * http://www.slf4j.org/codes.html#replay
    */
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("Web Crawler startup...")

    val appConfig = AppConfig(ConfigFactory.load())

    val seedUrl  = args.headOption.map(new URL(_)).getOrElse(appConfig.seedUrl)
    val maxDepth = Try(args.tail).toOption.flatMap(_.headOption.map(_.toInt)).getOrElse(appConfig.maxCrawlDepth)

    ActorSystem[NotUsed](
      RootBehavior(appConfig.copy(seedUrl = seedUrl, maxCrawlDepth = maxDepth)),
      "web-crawler"
    )

  }
}

object RootBehavior {
  def apply(appConfig: AppConfig): Behavior[NotUsed] =
    Behaviors.setup[NotUsed] { context =>
      val inMemoryCache = InMemoryCrawlerCache(appConfig.cacheConfig.maxCacheSize, appConfig.cacheConfig.cacheExpiry)

      val tsvWriter    = context.spawn(TSVWriter(appConfig), "tsv-writer")
      val crawlManager = context.spawn(CrawlManager(appConfig.maxCrawlDepth, tsvWriter, inMemoryCache), "crawl-manager")

      // Start crawling from the seed url
      crawlManager ! CrawlManager.StartCrawling(appConfig.seedUrl)

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
}
