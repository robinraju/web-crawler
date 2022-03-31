package com.robinraju

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import org.slf4j.{ Logger, LoggerFactory }

object Main {

  /**
    * Here is a good idea to try to force an early initialisation of SLF4J to avoid error codes like described here:
    * http://www.slf4j.org/codes.html#replay
    */
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.trace("Web Crawler startup...")

    ActorSystem[Nothing](
      RootBehavior(),
      "web-crawler"
    )
  }
}

object RootBehavior {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
//      implicit val system: ActorSystem[_] = context.system
//      implicit val log: Logger            = system.log
//      implicit val ec: ExecutionContext   = system.executionContext

      val greeter = context.spawn(Greeter(), "greeter")
      val replyTo = context.spawn(GreeterBot(max = 3), "Robin")

      greeter ! Greeter.Greet("Robin", replyTo)

      Behaviors.empty[Nothing]
    }
}
