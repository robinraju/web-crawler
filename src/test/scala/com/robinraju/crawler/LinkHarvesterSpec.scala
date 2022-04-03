package com.robinraju.crawler

import java.net.URL

import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.FutureOutcome
import org.scalatest.wordspec.FixtureAsyncWordSpecLike

import com.robinraju.cache.InMemoryCrawlerCache
import com.robinraju.crawler.LinkHarvesterSpec.LinkHarvesterFixture

class LinkHarvesterSpec extends ScalaTestWithActorTestKit with FixtureAsyncWordSpecLike {

  override type FixtureParam = LinkHarvesterFixture

  override def withFixture(test: OneArgAsyncTest): FutureOutcome =
    complete {
      withFixture(test.toNoArgAsyncTest(new LinkHarvesterFixture() {}))
    } lastly {}

  "LinkHarvester" should {
    "fetch all urls for a webpage from cache" in { f =>
      import f._

      // Given
      val managerProbe  = testKit.createTestProbe[LinkHarvester.HarvesterResponse]()
      val linkHarvester = testKit.spawn(LinkHarvester(managerProbe.ref, crawlerCache, Behaviors.empty))

      // When
      for {
        _ <- crawlerCache.set(testUrl1, testUrl1Children)
      } yield {
        linkHarvester ! LinkHarvester.HarvestLinks(Set(testUrl1), 1)

        // Then
        val response = managerProbe.expectMessageType[LinkHarvester.HarvesterResponse]
        response shouldBe LinkHarvester.HarvestedLinks(testUrl1, testUrl1Children, 1)
      }
    }

    "fetch all urls for a webpage using workers if not found in cache" in { f =>
      import f._

      // Given
      val managerProbe  = testKit.createTestProbe[LinkHarvester.HarvesterResponse]()
      val linkHarvester = testKit.spawn(LinkHarvester(managerProbe.ref, crawlerCache, mockedWorkerWithSuccess))

      // When
      linkHarvester ! LinkHarvester.HarvestLinks(Set(testUrl2), 1)

      // Then
      val response = managerProbe.expectMessageType[LinkHarvester.HarvesterResponse]
      response shouldBe LinkHarvester.HarvestedLinks(testUrl2, testUrl2Children, 1)
    }

    "receive a failure response when worker fails to extract url" in { f =>
      import f._

      // Given
      val managerProbe  = testKit.createTestProbe[LinkHarvester.HarvesterResponse]()
      val linkHarvester = testKit.spawn(LinkHarvester(managerProbe.ref, crawlerCache, mockedWorkerWithFailure))

      // When
      linkHarvester ! LinkHarvester.HarvestLinks(Set(testUrl2), 2)

      // Then
      val response = managerProbe.expectMessageType[LinkHarvester.HarvesterResponse]
      response shouldBe LinkHarvester.LinkHarvestFailed(2)
    }
  }
}

object LinkHarvesterSpec {
  trait LinkHarvesterFixture {
    val crawlerCache = InMemoryCrawlerCache(100, 5.minutes)

    val mockedWorkerWithSuccess = Behaviors.receiveMessagePartial[LinkExtractionWorker.WorkerCommand] {
      case LinkExtractionWorker.StartExtraction(parentUrl, currentDepth, replyTo) =>
        replyTo ! LinkExtractionWorker.LinkExtractionSuccess(parentUrl, testUrl2Children, currentDepth)
        Behaviors.same
    }

    val mockedWorkerWithFailure = Behaviors.receiveMessagePartial[LinkExtractionWorker.WorkerCommand] {
      case LinkExtractionWorker.StartExtraction(_, currentDepth, replyTo) =>
        replyTo ! LinkExtractionWorker.LinkExtractionFailed(currentDepth)
        Behaviors.same
    }

    val testUrl1         = new URL("https://example.com/test.html")
    val testUrl1Children = Set("https://example.com/test2.html", "https://example.com/tes3.html").map(new URL(_))

    val testUrl2 = new URL("https://example.com/path1/test.html")
    val testUrl2Children =
      Set("https://example.com/path1/test2.html", "https://example.com/path2/tes3.html").map(new URL(_))
  }
}
