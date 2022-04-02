package com.robinraju.core

case class CrawledPageResult(url: String, depth: Int, sameDomainRatio: Double) {
  def toTsvRecord: String = s"$url\t$depth\t$sameDomainRatio\n"
}

object CrawledPageResult {
  val tsvHeader = "url\tdepth\tratio\n"
}
