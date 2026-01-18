val scala3Version = "3.3.7"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "org.goldenport",
    name := "goldenport-cncf",
    version := "0.3.1",

    scalaVersion := scala3Version,

    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/maven",

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

      "org.goldenport" %% "goldenport-core" % "0.2.1",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    ),

    publishTo := {
      val repo = sys.env.get("SIMPLEMODELING_MAVEN_LOCAL")
        .map(file)
        .getOrElse(baseDirectory.value / "maven-local")

      Some(
        Resolver.file(
          "local-simplemodeling-maven",
          repo
        )
      )
    },

    publishMavenStyle := true
  )

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixOnCompile := false

addCommandAlias("fmt",      ";scalafmtAll;scalafmtSbt")
addCommandAlias("fmtCheck", ";scalafmtCheckAll;scalafmtSbtCheck")
addCommandAlias("fix",      ";scalafixAll")
addCommandAlias("fixCheck", ";scalafixAll --check")

assembly / assemblyJarName := "goldenport-cncf.jar"

assembly / mainClass := Some("org.goldenport.cncf.CncfMain")

assembly / test := {}

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    xs match {
      case "MANIFEST.MF" :: Nil => MergeStrategy.discard
      case _                    => MergeStrategy.first
    }
  case _ => MergeStrategy.first
}

// ---- Docker / Dist integration ----

lazy val copyJar = taskKey[Unit]("Copy CNCF fat jar to dist/")

copyJar := {
  val jar = (Compile / assembly).value
  val dist = baseDirectory.value / "dist"
  IO.createDirectory(dist)
  IO.copyFile(
    jar,
    dist / "goldenport-cncf.jar",
    preserveLastModified = true
  )
  streams.value.log.info("Copied goldenport-cncf.jar to dist/")
}

lazy val dockerBuild = taskKey[Unit]("Build Docker image for CNCF (local only)")

dockerBuild := {
  val log = streams.value.log

  // 1) Build fat jar
  (Compile / assembly).value

  // 2) Copy jar to dist/
  copyJar.value

  // 3) Docker build (local)
  val latest = "goldenport-cncf:latest"
  val verTag = s"goldenport-cncf:${version.value}"

  val cmd =
    s"docker build --no-cache -t $latest -t $verTag ."

  if (sys.process.Process(cmd).! != 0)
    sys.error("Docker build failed")

  log.info(s"Docker images built locally: $latest, $verTag")
}

lazy val dockerPush = taskKey[Unit]("Push CNCF Docker image to GHCR")

dockerPush := {
  val log = streams.value.log

  val repo = "ghcr.io/asami/goldenport-cncf"
  val verTag = s"$repo:${version.value}"
  val latest = s"$repo:latest"

  val tagCmds = Seq(
    s"docker tag goldenport-cncf:${version.value} $verTag",
    s"docker tag goldenport-cncf:latest $latest"
  )

  tagCmds.foreach { cmd =>
    if (sys.process.Process(cmd).! != 0)
      sys.error(s"Tagging failed: $cmd")
  }

  val pushCmd = s"docker push $repo --all-tags"
  if (sys.process.Process(pushCmd).! != 0)
    sys.error("Docker push failed")

  log.info("Docker images pushed to GHCR.")
}

lazy val dockerDeploy = taskKey[Unit]("Build and push CNCF Docker image")

dockerDeploy := Def.sequential(
  dockerBuild,
  dockerPush
).value
