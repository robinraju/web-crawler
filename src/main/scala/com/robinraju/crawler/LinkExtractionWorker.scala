package com.robinraju.crawler

import java.net.URL

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import org.jsoup.Jsoup

/**
 * This actor performs the actual work of loading a webpage and extract child urls from it.
 * It uses JSoup under the hood to perform URL parsing.
 *
 * On successful URL download, it will return `LinkExtractionSuccess` to LinkHarvester.
 * On any failure, it will return `LinkExtractionFailed` to LinkHarvester. 
 * */
object LinkExtractionWorker {

  sealed trait WorkerCommand
  final case class StartExtraction(parentUrl: URL, currentDepth: Int, replyTo: ActorRef[WorkerResponse])
      extends WorkerCommand

  final case class URLFetchSuccess(
      parentUrl: URL,
      childUrls: Set[URL],
      currentDepth: Int,
      replyTo: ActorRef[WorkerResponse]
  ) extends WorkerCommand
  final case class UrlFetchFailure(exception: Throwable, currentDepth: Int, replyTo: ActorRef[WorkerResponse])
      extends WorkerCommand

  sealed trait WorkerResponse
  final case class LinkExtractionSuccess(parentPage: URL, childUrls: Set[URL], currentDepth: Int) extends WorkerResponse
  final case class LinkExtractionFailed(currentDepth: Int)                                        extends WorkerResponse

  def apply(): Behavior[WorkerCommand] = Behaviors.setup { context =>
    implicit val ec: ExecutionContext = context.executionContext
    inProgress()
  }

  def inProgress()(implicit ec: ExecutionContext): Behavior[WorkerCommand] = Behaviors.receive { (context, message) =>
    message match {
      case StartExtraction(parentUrl, currentDepth, replyTo) =>
        context.log.info("Starting link extraction for {}", parentUrl)

        context.pipeToSelf(fetchLinksFromUrl(parentUrl)) {
          case Failure(exception) => UrlFetchFailure(exception, currentDepth, replyTo)
          case Success(childUrls) => URLFetchSuccess(parentUrl, childUrls, currentDepth, replyTo)
        }
        Behaviors.same

      case URLFetchSuccess(parentUrl, childUrls, currentDepth, replyTo) =>
        replyTo ! LinkExtractionSuccess(parentUrl, childUrls, currentDepth)
        Behaviors.stopped

      case UrlFetchFailure(exception, currentDepth, replyTo) =>
        exception match {
          case e: java.net.MalformedURLException =>
            context.log.error("Failed fetching url {}", e.getMessage)
            replyTo ! LinkExtractionFailed(currentDepth)
            Behaviors.stopped

          case e: java.net.SocketTimeoutException =>
            context.log.error("Request timeout, {}}", e.getMessage)
            replyTo ! LinkExtractionFailed(currentDepth)
            Behaviors.stopped

          case e: org.jsoup.HttpStatusException =>
            context.log.error("Request to {} failed with status code {}", e.getUrl, e.getStatusCode)
            replyTo ! LinkExtractionFailed(currentDepth)
            Behaviors.stopped

          case e: Throwable =>
            context.log.error("Failed fetching url {}", e.getMessage)
            replyTo ! LinkExtractionFailed(currentDepth)
            Behaviors.stopped
        }

    }
  }

  private def isFile(url: String): Boolean = {
    val regEx = """.*\.(\w+)""".r
    url match {
      case regEx(_) if !url.endsWith("html") => true
      case _                                 => false
    }
  }

  private def isInvalidUrl(url: String): Boolean =
    List(
      "#",
      "javascript",
      "mailto",
    ).exists(url.contains) || isFile(url)

  private def fetchLinksFromUrl(
      url: URL
  )(implicit ec: ExecutionContext): Future[Set[URL]] =
    Future {
      Jsoup
        .connect(url.toString)
        .userAgent("Akka Web Crawler")
        .timeout(3000)
        .get()
    }.map { htmlDocument =>
      htmlDocument
        .select("a[href]")
        .asScala
        .map(_.attr("abs:href")) // get all urls as absolute address
        .toSet
        .filterNot(url => isInvalidUrl(url))
        .flatMap(url => Try(new URL(url)).toOption) // ignore invalid urls for now.
    }
}
