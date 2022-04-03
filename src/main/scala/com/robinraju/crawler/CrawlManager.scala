package com.robinraju.crawler

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }
import com.robinraju.cache.WebCrawlerCache
import com.robinraju.core.CrawledPageResult
import com.robinraju.io.TSVWriter
import com.robinraju.util.DomainRatioUtil

import java.net.URL

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
      new CrawlManager(linkHarvester, tsvWriter, context).crawling(0, maxDepth, Map())
    }
}

class CrawlManager private (
    linkHarvester: ActorRef[LinkHarvester.HarvesterCommand],
    tsvWriter: ActorRef[TSVWriter.IOCommand],
    context: ActorContext[CrawlManager.ManagerCommand]
) {
  import CrawlManager._

  def crawling(currentDepth: Int, maxDepth: Int, queuedRequests: Map[Int, Int]): Behavior[ManagerCommand] =
    Behaviors.receiveMessage {
      case StartCrawling(seedUrl) =>
        val depth = currentDepth + 1
        linkHarvester ! LinkHarvester.HarvestLinks(Set(seedUrl), depth)
        crawling(depth, maxDepth, Map(depth -> 1))

      case HarvesterResponseWrapper(response) =>
        response match {
          case LinkHarvester.HarvestedLinks(parentUrl, childUrls, depth) =>
            val sameDomainRatio = DomainRatioUtil.calculateSameDomainRatio(parentUrl, childUrls)

            context.log.info(s"URL: $parentUrl  Depth: $depth  Ratio: $sameDomainRatio")
            context.log.info(s"Current Depth: $currentDepth Max: $maxDepth")
            tsvWriter ! TSVWriter.WriteToFile(CrawledPageResult(parentUrl.toString, depth, sameDomainRatio))

            val requestsInFlight = queuedRequests.updated(depth, Math.max(queuedRequests(depth) - 1, 0))

            if (currentDepth == maxDepth) {
              Behaviors.same
            } else {
              if (requestsInFlight(currentDepth) == 0) {
                linkHarvester ! LinkHarvester.HarvestLinks(childUrls, depth + 1)
                crawling(
                  currentDepth + 1,
                  maxDepth,
                  requestsInFlight.removed(currentDepth).updated(currentDepth + 1, childUrls.size)
                )
              } else {
                linkHarvester ! LinkHarvester.HarvestLinks(childUrls, depth + 1)
                crawling(currentDepth, maxDepth, requestsInFlight.updated(depth + 1, childUrls.size))
              }
            }
          case LinkHarvester.LinkHarvestFailed(depth) =>
            val updatedRequests = queuedRequests.updated(depth, Math.max(queuedRequests(depth) - 1, 0))
            crawling(currentDepth, maxDepth, updatedRequests)
        }
    }
}
