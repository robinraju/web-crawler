// *****************************************************************************
// Build settings
// *****************************************************************************

inThisBuild(
  Seq(
    organization     := "com.robinraju",
    organizationName := "Robin Raju",
    startYear        := Some(2022),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/robinraju/web-crawler"),
        "git@github.com:robinraju/web-crawler.git"
      )
    ),
    developers := List(
      Developer(
        "robinraju",
        "Robin Raju",
        "hello@robinraju.com",
        url("https://github.com/robinraju")
      )
    ),
    scalaVersion      := "2.13.8",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies += library.scalafix,
    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-Xfatal-warnings",
      "-Ywarn-unused"
    ),
    scalafmtOnCompile := true
  )
)

// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `web-crawler` =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        library.akka,
        library.akkaLogging,
        library.logBack,
        library.jSoup,
        library.sCaffeine   % Compile,
        library.akkaTestKit % Test,
        library.scalaTest   % Test,
      ),
      Compile / run / mainClass := Some("com.robinraju.Main")
    )

// *****************************************************************************
// Project settings
// *****************************************************************************

lazy val commonSettings =
  Seq(
    // Also (automatically) format build definition together with sources
    Compile / scalafmt := {
      val _ = (Compile / scalafmtSbt).value
      (Compile / scalafmt).value
    }
  )

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
fork := true

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val AkkaVersion      = "2.6.19"
      val ScalaTestVersion = "3.2.11"
      val ScalafixVersion  = "0.6.0"
      val LogbackVersion   = "1.2.11"
      val JSoupVersion     = "1.14.3"
      val SCaffeineVersion = "5.1.2"
    }
    val akka        = "com.typesafe.akka"  %% "akka-actor-typed"         % Version.AkkaVersion
    val akkaTestKit = "com.typesafe.akka"  %% "akka-actor-testkit-typed" % Version.AkkaVersion
    val scalaTest   = "org.scalatest"      %% "scalatest"                % Version.ScalaTestVersion
    val akkaLogging = "com.typesafe.akka"  %% "akka-slf4j"               % Version.AkkaVersion
    val logBack     = "ch.qos.logback"      % "logback-classic"          % Version.LogbackVersion
    val jSoup       = "org.jsoup"           % "jsoup"                    % Version.JSoupVersion
    val sCaffeine   = "com.github.blemale" %% "scaffeine"                % Version.SCaffeineVersion

    val scalafix = "com.github.liancheng" %% "organize-imports" % Version.ScalafixVersion
  }

addCommandAlias(
  "styleCheck",
  "; scalafmtCheckAll; scalafmtSbtCheck ; scalafixAll --check"
)
