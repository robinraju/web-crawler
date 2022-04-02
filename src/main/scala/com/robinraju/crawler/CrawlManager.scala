package com.robinraju.crawler

import java.net.URL

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }

import com.robinraju.cache.WebCrawlerCache
import com.robinraju.core.CrawledPageResult
import com.robinraju.io.TSVWriter
import com.robinraju.util.DomainRatioUtil

object CrawlManager {

  sealed trait ManagerCommand
  final case class StartCrawling(seedUrl: URL) extends ManagerCommand

  final case class HarvesterResponseWrapper(response: LinkHarvester.HarvesterResponse) extends ManagerCommand

  def apply(maxDepth: Int, tsvWriter: ActorRef[TSVWriter.IOCommand], cache: WebCrawlerCache): Behavior[ManagerCommand] =
    Behaviors.setup[ManagerCommand] { context =>
      val harvesterResponseMapper = context.messageAdapter(response => HarvesterResponseWrapper(response))
      val linkHarvester: ActorRef[LinkHarvester.HarvesterCommand] =
        context.spawn(
          LinkHarvester(harvesterResponseMapper, cache),
          "link-harvester"
        )
      new CrawlManager(linkHarvester, tsvWriter, context).crawling(0, maxDepth)
    }
}

class CrawlManager private (
    linkHarvester: ActorRef[LinkHarvester.HarvesterCommand],
    tsvWriter: ActorRef[TSVWriter.IOCommand],
    context: ActorContext[CrawlManager.ManagerCommand]
) {
  import CrawlManager._

  def crawling(currentDepth: Int, maxDepth: Int): Behavior[ManagerCommand] = Behaviors.receiveMessage {
    case StartCrawling(seedUrl) =>
      val depth = currentDepth + 1
      linkHarvester ! LinkHarvester.HarvestLinks(Set(seedUrl), depth)
      crawling(depth, maxDepth)

    case HarvesterResponseWrapper(response) =>
      response match {
        case LinkHarvester.HarvestedLinks(parentUrl, childUrls, previousDepth) =>
          val sameDomainRatio = DomainRatioUtil.calculateSameDomainRatio(parentUrl, childUrls)

          context.log.info(s"URL: $parentUrl  Depth: $currentDepth  Ratio: $sameDomainRatio")
          context.log.info(s"<< Current Depth: $currentDepth Max: $maxDepth")
          tsvWriter ! TSVWriter.WriteToFile(CrawledPageResult(parentUrl.toString, currentDepth, sameDomainRatio))
          if (currentDepth == maxDepth) {
            Behaviors.same
          } else {
            linkHarvester ! LinkHarvester.HarvestLinks(childUrls, previousDepth + 1)
            if (currentDepth == previousDepth)
              Behaviors.same
            else
              crawling(currentDepth + 1, maxDepth)
          }
      }
  }
}
