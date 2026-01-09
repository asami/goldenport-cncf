val scala3Version = "3.6.2"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "org.goldenport",
    name := "goldenport-cncf",
    version := "0.2.3",

    scalaVersion := scala3Version,

    resolvers += "GitHub Packages" at "https://maven.pkg.github.com/asami/maven-repository",

    libraryDependencies ++= Seq(
      // Functional core
      "org.typelevel" %% "cats-core"   % "2.10.0",
      "org.typelevel" %% "cats-free"   % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",

      "org.http4s" %% "http4s-ember-server" % "0.23.27",
      "org.http4s" %% "http4s-core"         % "0.23.27",
      "org.http4s" %% "http4s-dsl"          % "0.23.27",

      // Actor system (Akka-compatible, Scala 3 supported)
      "org.apache.pekko" %% "pekko-actor"  % "1.0.2",
      "org.apache.pekko" %% "pekko-stream" % "1.0.2",

      // JSON
      "io.circe" %% "circe-core"    % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser"  % "0.14.6",

      "org.slf4j" % "slf4j-simple" % "2.0.12",

      "org.goldenport" %% "goldenport-core" % "0.1.2",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    ),

    publishTo := Some(
      "GitHub Packages" at "https://maven.pkg.github.com/asami/maven-repository"
    ),

    credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),

    publishMavenStyle := true
  )
