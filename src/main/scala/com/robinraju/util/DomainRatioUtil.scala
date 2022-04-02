package com.robinraju.util

import java.net.URL

import scala.util.Try

object DomainRatioUtil {

  def calculateSameDomainRatio(parentPage: URL, childLinks: Set[URL]): Double = {
    val sameDomainLinksCount = childLinks.count(_.getHost == parentPage.getHost)

    Try(
      BigDecimal(1.0 / sameDomainLinksCount)
        .setScale(4, BigDecimal.RoundingMode.HALF_UP)
    ).toOption.fold(0.0)(_.toDouble)
  }
}
