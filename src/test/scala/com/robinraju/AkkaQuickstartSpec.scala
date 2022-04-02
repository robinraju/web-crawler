////#full-example
//package com.robinraju
//
//import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
//import org.scalatest.wordspec.AnyWordSpecLike
//
//import com.robinraju.Greeter.{ Greet, Greeted }
//
////#definition
//class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
////#definition
//
//  "A Greeter" must {
//    // #test
//    "reply to greeted" in {
//      val replyProbe = createTestProbe[Greeted]()
//      val underTest  = spawn(Greeter())
//      underTest ! Greet("Santa", replyProbe.ref)
//      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
//    }
//    // #test
//  }
//
//}
////#full-example
