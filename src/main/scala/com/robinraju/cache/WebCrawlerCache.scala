package com.robinraju.cache

import java.net.URL

import scala.concurrent.Future

/**
 * Cache used by the web crawler
 * Use this as a base trait for any implementations
 * eg: InMemoryCache, RedisCache
 * */
trait WebCrawlerCache {
  def get(key: URL): Future[Option[Set[URL]]]
  def set(key: URL, value: Set[URL]): Future[Unit]
}
