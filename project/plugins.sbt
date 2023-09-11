//sbt-revolver - for starting and stopping app in the background
//see: https://github.com/spray/sbt-revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

// scalafmt -  Format source files
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// scalafix - Rules for code format: organize imports
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")

//Kamon
addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.0.14")
