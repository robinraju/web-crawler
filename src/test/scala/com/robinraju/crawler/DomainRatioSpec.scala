package com.robinraju.crawler

import java.net.URL

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.robinraju.util.DomainRatioUtil

class DomainRatioSpec extends AnyWordSpecLike with Matchers {

  "DomainRatioUtil" should {
    "calculate the same domain ratio of a link" in {

      val parentUrl = new URL("https://example.com/path/test.html")
      val childUrls =
        Set(
          "https://example.com/path1/a.html",
          "https://example.com/path1/b.html",
          "https://example.com/path2/c.html",
          "https:/foo.example.com/path1/a.html",
          "http:/bar.example.com/path1/a.html",
          "https://crawler-test.com/",
        ).map(new URL(_))

      val sameDomainRatio = DomainRatioUtil.calculateSameDomainRatio(parentUrl, childUrls)

      sameDomainRatio shouldBe 0.3333
    }
  }

}
