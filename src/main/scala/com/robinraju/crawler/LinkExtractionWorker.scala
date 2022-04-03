package com.robinraju.crawler

import java.net.URL

import scala.jdk.CollectionConverters._
import scala.util.Try

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
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
  final case class UrlFetchFailure(exception: Throwable, replyTo: ActorRef[WorkerResponse]) extends WorkerCommand

  sealed trait WorkerResponse
  final case class LinkExtractionSuccess(parentPage: URL, childUrls: Set[URL], currentDepth: Int) extends WorkerResponse
  final case class LinkExtractionFailed(currentDepth: Int)                                        extends WorkerResponse

  def apply(): Behavior[WorkerCommand] = inProgress(0)

  def inProgress(currentDepth: Int): Behavior[WorkerCommand] = Behaviors.receive { (context, message) =>
    message match {
      case StartExtraction(parentUrl, currentDepth, replyTo) =>
        context.log.info("Starting link extraction for {}", parentUrl)
        fetchLinksFromURl(parentUrl, context, replyTo).fold(inProgress(currentDepth)) { childUrls =>
          replyTo ! LinkExtractionSuccess(parentUrl, childUrls, currentDepth)
          Behaviors.stopped
        }

      case UrlFetchFailure(exception, replyTo) =>
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

  private def fetchLinksFromURl(
      url: URL,
      context: ActorContext[WorkerCommand],
      replyTo: ActorRef[WorkerResponse]
  ): Option[Set[URL]] = {
    val htmlDocument = Try(
      Jsoup
        .connect(url.toString)
        .userAgent("LT Web Crawler")
        .timeout(3000)
        .get()
    ).fold(
      error => {
        context.self ! UrlFetchFailure(error, replyTo)
        None
      },
      Some(_)
    )

    htmlDocument.map { doc =>
      doc
        .select("a[href]")
        .asScala
        .map(_.attr("abs:href")) // get all urls as absolute address
        .toSet
        .filterNot(url => isInvalidUrl(url))
        .flatMap(url => Try(new URL(url)).toOption) // ignore invalid urls for now.
    }
  }

}
