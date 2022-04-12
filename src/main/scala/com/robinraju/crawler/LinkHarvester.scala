package com.robinraju.crawler

import java.net.URL
import java.util.UUID

import scala.util.{ Failure, Success }

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior, DispatcherSelector }

import com.robinraju.cache.WebCrawlerCache

/**
 * Another Actor coordinates url downloads and cache access.
 * 
 * It creates N child actors (LinkExtractionWorker) based on the number of child Urls a page has.
 * Each child actor will download and parse a single URL.
 * 
 * 1. on receiving message `HarvestLinks`, it checks cache for the URL.
 * 1.1. If an entry is found in Cache, it will return the cached value to `CrawlManager`
 * 1.2. If no entry is found in cache, it will spawn a worker and send a `StartExtraction` message to it.
 * 2. When a response is obtained from a worker,
 * 2.1. It will be written to cache
 * 2.2. return urls to `CrawlManager` 
 * */
object LinkHarvester {

  sealed trait HarvesterCommand
  final case class HarvestLinks(links: Set[URL], currentDepth: Int) extends HarvesterCommand

  // messages for internal actor communication
  final case class CachedUrlResult(cachedUrls: Option[Set[URL]], parentUrl: URL, currentDepth: Int)
      extends HarvesterCommand
  final case class WriteToCacheSuccess(parentUrl: URL, childUrls: Set[URL], currentDepth: Int) extends HarvesterCommand
  final case class CacheAccessFailure(error: Throwable)                                        extends HarvesterCommand

  sealed trait HarvesterResponse
  final case class HarvestedLinks(parentUrl: URL, childUrls: Set[URL], currentDepth: Int, isCached: Boolean = false)
      extends HarvesterResponse
  final case class LinkHarvestFailed(currentDepth: Int) extends HarvesterResponse

  final case class WorkerResponseWrapper(response: LinkExtractionWorker.WorkerResponse) extends HarvesterCommand

  def apply(
      manager: ActorRef[LinkHarvester.HarvesterResponse],
      cache: WebCrawlerCache,
      workerBehavior: Behavior[LinkExtractionWorker.WorkerCommand]
  ): Behavior[HarvesterCommand] =
    Behaviors.setup[HarvesterCommand] { context =>
      new LinkHarvester(manager, context, cache, workerBehavior).inProgress()
    }
}

class LinkHarvester private (
    manager: ActorRef[LinkHarvester.HarvesterResponse],
    context: ActorContext[LinkHarvester.HarvesterCommand],
    cache: WebCrawlerCache,
    workerBehavior: Behavior[LinkExtractionWorker.WorkerCommand]
) {
  import LinkHarvester._

  val responseWrapper: ActorRef[LinkExtractionWorker.WorkerResponse] =
    context.messageAdapter(response => WorkerResponseWrapper(response))

  def inProgress(): Behavior[HarvesterCommand] = Behaviors.receiveMessagePartial {
    case HarvestLinks(links, currentDepth) =>
      links.foreach { link =>
        context.pipeToSelf(cache.get(link)) {
          case Failure(exception) => CacheAccessFailure(exception)
          case Success(cachedUrl) => CachedUrlResult(cachedUrl, link, currentDepth)
        }
      }
      Behaviors.same

    case CachedUrlResult(cachedUrls, parentUrl, currentDepth) =>
      cachedUrls match {
        case Some(cachedUrls) =>
          context.log.info("URL {} found from cache", parentUrl)
          manager ! HarvestedLinks(parentUrl, cachedUrls, currentDepth, isCached = true)
        case None =>
          val worker = context.spawn(
            workerBehavior,
            s"link-extraction-worker-${UUID.randomUUID().toString}",
            DispatcherSelector.fromConfig("worker-dispatcher") // use custom dispatcher for blocking operation
          )
          worker ! LinkExtractionWorker.StartExtraction(parentUrl, currentDepth, responseWrapper)
      }
      Behaviors.same

    case WriteToCacheSuccess(parentUrl, childUrls, currentDepth) =>
      manager ! HarvestedLinks(parentUrl, childUrls, currentDepth)
      Behaviors.same

    case CacheAccessFailure(error) =>
      context.log.error("Failure accessing cache: {}", error)
      // Stopping here, since we use an in memory cache
      // A cache failure shouldn't be a bottleneck to the system.
      // This can be escalated further and continue without cache if required.
      Behaviors.stopped

    case WorkerResponseWrapper(response) =>
      response match {
        case LinkExtractionWorker.LinkExtractionSuccess(parentUrl, childUrls, currentDepth) =>
          context.log.info(
            "Link Extraction Success for {}. Obtained [{}] child urls, writing to cache",
            parentUrl,
            childUrls.size
          )
          context.pipeToSelf(cache.set(parentUrl, childUrls)) {
            case Failure(exception) => CacheAccessFailure(exception)
            case Success(_)         => WriteToCacheSuccess(parentUrl, childUrls, currentDepth)
          }
          Behaviors.same
        case LinkExtractionWorker.LinkExtractionFailed(currentDepth) =>
          manager ! LinkHarvestFailed(currentDepth)
          Behaviors.same
      }
  }
}
