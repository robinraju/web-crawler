package com.robinraju.cache

import java.net.URL

import scala.concurrent.Future
import scala.concurrent.duration._

import com.github.blemale.scaffeine.{ Cache, Scaffeine }

class InMemoryCrawlerCache(cache: Cache[URL, Set[URL]]) extends WebCrawlerCache {
  override def get(key: URL): Future[Option[Set[URL]]] =
    Future.successful(cache.getIfPresent(key))

  override def set(key: URL, value: Set[URL]): Future[Unit] =
    Future.successful(cache.put(key, value))
}

object InMemoryCrawlerCache {
  def apply(maxSize: Int, cacheExpiry: FiniteDuration): InMemoryCrawlerCache = {
    val cache: Cache[URL, Set[URL]] =
      Scaffeine()
        .expireAfterWrite(cacheExpiry)
        .maximumSize(maxSize)
        .build[URL, Set[URL]]()

    new InMemoryCrawlerCache(cache)
  }
}
