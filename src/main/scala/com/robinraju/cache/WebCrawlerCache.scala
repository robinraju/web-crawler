package com.robinraju.cache

import java.net.URL

import scala.concurrent.Future

trait WebCrawlerCache {
  def get(key: URL): Future[Option[Set[URL]]]
  def set(key: URL, value: Set[URL]): Future[Unit]
}
