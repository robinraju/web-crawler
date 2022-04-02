package com.robinraju.crawler

import java.net.URL
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior }

import com.robinraju.cache.WebCrawlerCache

object LinkHarvester {

  sealed trait HarvesterCommand
  final case class HarvestLinks(links: Set[URL], currentDepth: Int) extends HarvesterCommand

  // messages for internal actor communication
  final case class CachedUrlResult(cachedUrls: Option[Set[URL]], parentUrl: URL, currentDepth: Int)
      extends HarvesterCommand
  final case class WriteToCacheSuccess(parentUrl: URL, childUrls: Set[URL], currentDepth: Int) extends HarvesterCommand
  final case class CacheAccessFailure(error: Throwable)                                        extends HarvesterCommand

  sealed trait HarvesterResponse
  final case class HarvestedLinks(parentUrl: URL, childUrls: Set[URL], currentDepth: Int) extends HarvesterResponse

  final case class WorkerResponseWrapper(response: LinkExtractionWorker.WorkerResponse) extends HarvesterCommand

  def apply(manager: ActorRef[LinkHarvester.HarvesterResponse], cache: WebCrawlerCache): Behavior[HarvesterCommand] =
    Behaviors.setup[HarvesterCommand] { context =>
      new LinkHarvester(manager, context, cache).inProgress()
    }
}

class LinkHarvester private (
    manager: ActorRef[LinkHarvester.HarvesterResponse],
    context: ActorContext[LinkHarvester.HarvesterCommand],
    cache: WebCrawlerCache
) {
  import LinkHarvester._

  implicit val ec: ExecutionContext = context.system.executionContext

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
          manager ! HarvestedLinks(parentUrl, cachedUrls, currentDepth)
        case None =>
          val worker = context.spawn(
            LinkExtractionWorker(),
            s"link-extraction-worker-${UUID.randomUUID().toString}"
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
      }
  }
}
