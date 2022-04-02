package com.robinraju.io

import java.nio.file.{ Files, Path, Paths, StandardOpenOption }

import scala.util.{ Failure, Success, Try }

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }

import com.robinraju.core.{ AppConfig, CrawledPageResult }

object TSVWriter {

  sealed trait IOCommand
  final case class WriteToFile(content: CrawledPageResult) extends IOCommand

  def apply(appConfig: AppConfig): Behavior[IOCommand] = Behaviors.setup[IOCommand] { context =>
    val outputFilePath = initOutputFIle(appConfig.outputDirectory, context)
    new TSVWriter(context).inProgress(outputFilePath)
  }

  def initOutputFIle(outputPath: String, context: ActorContext[IOCommand]): Path = {
    val outputDirectory = Paths.get(outputPath)
    val outputFilePath  = outputDirectory.resolve(s"crawler-result-${System.currentTimeMillis()}.tsv")
    Try(Files.createDirectories(outputDirectory)) match {
      case Failure(exception) => context.log.error(s"Error initializing output directory: $exception")
      case Success(_) => Files.writeString(outputFilePath, CrawledPageResult.tsvHeader, StandardOpenOption.CREATE)
    }
    outputFilePath
  }
}

class TSVWriter(context: ActorContext[TSVWriter.IOCommand]) {

  def inProgress(outputFIle: Path): Behavior[TSVWriter.IOCommand] = {
    import TSVWriter._

    Behaviors.receiveMessage {
      case WriteToFile(pageResult) =>
        Try(
          Files.writeString(
            outputFIle,
            pageResult.toTsvRecord,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
          )
        ) match {
          case Failure(exception) => context.log.error(exception.getMessage)
          case Success(_)         => ()
        }
        Behaviors.same
    }
  }
}
