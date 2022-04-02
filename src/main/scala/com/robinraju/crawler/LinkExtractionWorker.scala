package com.robinraju.crawler

import java.net.URL

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import org.jsoup.Jsoup

object LinkExtractionWorker {

  sealed trait WorkerCommand
  final case class StartExtraction(parentUrl: URL, currentDepth: Int, replyTo: ActorRef[WorkerResponse])
      extends WorkerCommand
  final case class UrlFetchFailure(exception: Throwable) extends WorkerCommand

  sealed trait WorkerResponse
  final case class LinkExtractionSuccess(parentPage: URL, childUrls: Set[URL], currentDepth: Int) extends WorkerResponse

  def apply(): Behavior[WorkerCommand] = Behaviors
    .supervise(inProgress())
    .onFailure[IllegalArgumentException](
      // if exception occur repeatedly, worker will be restarted with backoff delays
      // this will prevent sending too many requests to the host server.
      SupervisorStrategy.restartWithBackoff(minBackoff = 200.millis, maxBackoff = 10.seconds, randomFactor = 0.1)
    )

  def inProgress(): Behavior[WorkerCommand] = Behaviors.receive { (context, message) =>
    message match {
      case StartExtraction(parentUrl, currentDepth, replyTo) =>
        context.log.info("Starting link extraction for {}", parentUrl)
        fetchLinksFromURl(parentUrl, context).fold(inProgress()) { childUrls =>
          replyTo ! LinkExtractionSuccess(parentUrl, childUrls, currentDepth)
          Behaviors.stopped
        }

      case UrlFetchFailure(exception) =>
        exception match {
          case e: java.net.MalformedURLException =>
            context.log.error("Failed fetching url {}", e)
            Behaviors.stopped

          case e: java.net.SocketTimeoutException =>
            context.log.error("Request timeout, restarting worker")
            throw new IllegalStateException(e)

          case e: org.jsoup.HttpStatusException =>
            context.log.error("Request to {} failed with status code {}", e.getUrl, e.getStatusCode)
            Behaviors.stopped
        }

    }
  }

  private def isInvalidUrl(url: String): Boolean =
    List(
      "#",
      "javascript",
      ".jpg",
      ".JPG",
      ".BMP",
      ".bmp",
      ".png",
      ".PNG",
      ".jpeg",
      ".JPEG",
      ".MP4",
      ".mp4",
      ".flv",
      ".pdf",
      ".PDF",
      ".eps",
      ".EPS",
      ".svg",
      ".SVG",
      ".webp",
      ".webm"
    ).exists(url.contains)

  private def fetchLinksFromURl(url: URL, context: ActorContext[WorkerCommand]): Option[Set[URL]] = {
    val htmlDocument = Try(
      Jsoup
        .connect(url.toString)
        .userAgent("LT Web Crawler")
        .timeout(3000)
        .get()
    ).fold(
      error => {
        context.self ! UrlFetchFailure(error)
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
