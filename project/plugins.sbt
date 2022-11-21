//sbt-revolver - for starting and stopping app in the background
//see: https://github.com/spray/sbt-revolver
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

// scalafmt -  Format source files
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")

// scalafix - Rules for code format: organize imports
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")

//Kamon
addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.0.14")
