val scala3Version = "3.6.2"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "org.simplemodeling",
    name := "component-framework",
    version := "0.1.2",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      // Functional core
      "org.typelevel" %% "cats-core"   % "2.10.0",
      "org.typelevel" %% "cats-free"   % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",

      // Actor system (Akka-compatible, Scala 3 supported)
      "org.apache.pekko" %% "pekko-actor"  % "1.0.2",
      "org.apache.pekko" %% "pekko-stream" % "1.0.2",

      // JSON
      "io.circe" %% "circe-core"    % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser"  % "0.14.6",

      "org.simplemodeling" %% "core" % "0.0.1-SNAPSHOT",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )
