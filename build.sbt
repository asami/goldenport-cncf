import sbt.TestFrameworks
import sbt.Tests

val scala3version = "3.3.7"

lazy val generateTextusRuntimeCatalog = taskKey[File]("Generate Textus runtime catalog metadata for the warehouse repository.")
lazy val exportTextusRuntimeCatalog = taskKey[File]("Export Textus runtime catalog metadata for local development consumers.")
lazy val copyTextusRuntimeCatalog = taskKey[File]("Copy the checked-in Textus runtime catalog into the warehouse repository.")
lazy val publishTextusRuntimeCatalog = taskKey[File]("Publish Textus runtime catalog metadata into the warehouse repository.")

def textusBaseProvidedModules(
  dependencies: Seq[ModuleID],
  organizationname: String,
  modulename: String,
  scalabinaryversion: String
): Vector[String] = {
  def _is_test_dependency_(module: ModuleID): Boolean =
    module.configurations.exists(_.toLowerCase.contains("test"))
  def _artifact_name_(module: ModuleID): String =
    if (module.crossVersion == CrossVersion.disabled)
      module.name
    else
      s"${module.name}_$scalabinaryversion"
  val self = s"$organizationname:${modulename}_$scalabinaryversion"
  (Vector(self) ++
    dependencies
      .filterNot(_is_test_dependency_)
      .map(module => s"${module.organization}:${_artifact_name_(module)}"))
    .distinct
    .toVector
}

def textusRuntimeCatalogText(
  existingtext: String,
  cncfversion: String,
  scalabinaryversion: String,
  generatedat: String,
  baseprovidedmodules: Vector[String]
): String = {
  def _existing_value_(key: String): Option[String] =
    existingtext.linesIterator.find(_.startsWith(s"$key:")).map(_.drop(key.length + 1).trim)
      .filter(_.nonEmpty)
  def _version_blocks_(text: String): Vector[(String, String)] = {
    val blocks = Vector.newBuilder[(String, String)]
    var currentversion: Option[String] = None
    var currentlines = Vector.empty[String]
    def _flush_(): Unit =
      currentversion.foreach { v =>
        blocks += v -> currentlines.mkString("\n")
      }
    text.linesIterator.foreach { line =>
      if (line.startsWith("  - version: ")) {
        _flush_()
        currentversion = Some(line.stripPrefix("  - version: ").trim)
        currentlines = Vector(line)
      } else if (currentversion.nonEmpty && line.startsWith("    ")) {
        currentlines :+= line
      }
    }
    _flush_()
    blocks.result()
  }
  def _preserved_version_lines_(version: String): Vector[String] = {
    val generatedkeys =
      Set("channel", "status", "scalaBinaryVersion", "module", "publishedAt", "metadataUrl")
    _version_blocks_(existingtext)
      .find(_._1 == version)
      .map { case (_, block) =>
        val lines = block.linesIterator.toVector.drop(1)
        val preserved = Vector.newBuilder[String]
        var keep = false
        lines.foreach { line =>
          if (line.startsWith("    ") && !line.startsWith("      ")) {
            val key = line.trim.takeWhile(_ != ':')
            keep = !generatedkeys.contains(key)
          }
          if (keep)
            preserved += line
        }
        preserved.result()
      }
      .getOrElse(Vector.empty)
  }
  val baseprovidedblock =
    baseprovidedmodules.map(module => s"  - $module").mkString("baseProvided:\n", "\n", "")
  val channel =
    if (cncfversion.endsWith("-SNAPSHOT")) "snapshot" else "stable"
  val recommended =
    _existing_value_("recommended").getOrElse(cncfversion)
  val lateststable =
    if (channel == "stable") cncfversion else _existing_value_("latestStable").getOrElse("")
  val latestsnapshot =
    if (channel == "snapshot") cncfversion else _existing_value_("latestSnapshot").getOrElse("")
  val currentblock =
    s"""  - version: $cncfversion
       |    channel: $channel
       |    status: active
       |    scalaBinaryVersion: "$scalabinaryversion"
       |    module: org.goldenport:goldenport-cncf_$scalabinaryversion:$cncfversion
       |    publishedAt: $generatedat
       |    metadataUrl: https://www.simplemodeling.org/repository/maven/org/goldenport/goldenport-cncf_$scalabinaryversion/maven-metadata.xml""".stripMargin +
      _preserved_version_lines_(cncfversion).map("\n" + _).mkString
  val historyblocks =
    _version_blocks_(existingtext)
      .foldLeft(Vector.empty[(String, String)]) { case (acc, (v, block)) =>
        acc.filterNot(_._1 == v) :+ (v -> block)
      }
      .filterNot(_._1 == cncfversion)
      .map(_._2)
  val versions =
    (historyblocks :+ currentblock).mkString("\n")
  s"""schemaVersion: 1
     |generatedAt: $generatedat
     |recommended: $recommended
     |latestStable: $lateststable
     |latestSnapshot: $latestsnapshot
     |mavenRepositories:
     |  - https://www.simplemodeling.org/repository/maven
     |carRepositories:
     |  - https://www.simplemodeling.org/repository/car
     |sarRepositories:
     |  - https://www.simplemodeling.org/repository/sar
     |coursierRepositories:
     |  - https://www.simplemodeling.org/repository/maven
     |$baseprovidedblock
     |versions:
     |$versions
     |""".stripMargin
}

def textusWriteRuntimeCatalog(
  sourcefile: File,
  targetfile: File,
  cncfversion: String,
  scalabinaryversion: String,
  baseprovidedmodules: Vector[String]
): File = {
  val existingtext =
    if (sourcefile.isFile)
      IO.read(sourcefile)
    else
      ""
  val text =
    textusRuntimeCatalogText(
      existingtext,
      cncfversion,
      scalabinaryversion,
      java.time.Instant.now().toString,
      baseprovidedmodules
    )
  IO.createDirectory(targetfile.getParentFile)
  IO.write(targetfile, text)
  targetfile
}

def textusPublishRepositoryFile(resolver: Resolver): Option[File] =
  resolver match {
    case m: MavenRepository =>
      val root = m.root
      if (root.startsWith("file:"))
        Some(new File(new java.net.URI(root)))
      else
        Some(file(root))
    case f: FileRepository =>
      f.patterns.artifactPatterns.headOption.flatMap { pattern =>
        val marker = "/[organisation]/"
        val index = pattern.indexOf(marker)
        if (index >= 0)
          Some(file(pattern.take(index)))
        else
          None
      }
    case _ =>
      None
  }

def textusWarehouseDirFromMavenRepository(repository: File): File =
  repository.getCanonicalFile match {
    case canonical
        if canonical.getName == "maven" &&
          canonical.getParentFile != null &&
          canonical.getParentFile.getName == "repository" =>
      canonical.getParentFile.getParentFile
    case canonical if canonical.getName == "maven" =>
      canonical.getParentFile
    case canonical =>
      sys.error(
        s"Textus runtime catalog publish requires publishTo to point at a Maven repository " +
          s"under a warehouse, but got: ${canonical}"
      )
  }

def textusPublishRuntimeCatalogFile(
  source: File,
  publishresolver: Option[Resolver],
  basedir: File,
  log: sbt.util.Logger
): File = {
  val warehousedir =
    publishresolver
      .flatMap(textusPublishRepositoryFile)
      .map(textusWarehouseDirFromMavenRepository)
      .getOrElse(textusWarehouseDirFromMavenRepository(basedir / "maven-local"))
  val target = warehousedir / "repository" / "textus" / "runtime-catalog.yaml"
  val text = IO.read(source)
  def _value_of_(key: String): Option[String] =
    text.linesIterator.find(_.startsWith(s"$key:")).map(_.drop(key.length + 1).trim).filter(_.nonEmpty)
  val versionlines =
    text.linesIterator.filter(_.startsWith("  - version: ")).map(_.stripPrefix("  - version: ").trim).toVector
  def _status_of_(version: String): Option[String] = {
    val lines = text.linesIterator.toVector
    val start = lines.indexWhere(_ == s"  - version: $version")
    if (start < 0) {
      None
    } else {
      val block = lines.drop(start + 1).takeWhile(_.startsWith("    "))
      block.find(_.trim.startsWith("status:")).map(_.trim.stripPrefix("status:").trim).filter(_.nonEmpty)
    }
  }
  val versionlinecount = versionlines.size
  val distinctversioncount = versionlines.distinct.size
  if (versionlinecount == 0)
    sys.error("Textus runtime catalog has no versions")
  if (versionlinecount != distinctversioncount)
    sys.error("Textus runtime catalog has duplicate versions")
  _value_of_("recommended").foreach { v =>
    if (!versionlines.contains(v))
      sys.error(s"Textus runtime catalog recommended version is not listed: $v")
    if (_status_of_(v).contains("disabled"))
      sys.error(s"Textus runtime catalog recommended version is disabled: $v")
  }
  _value_of_("latestStable").foreach { v =>
    if (!versionlines.contains(v))
      sys.error(s"Textus runtime catalog latestStable version is not listed: $v")
  }
  _value_of_("latestSnapshot").foreach { v =>
    if (!versionlines.contains(v))
      sys.error(s"Textus runtime catalog latestSnapshot version is not listed: $v")
  }
  IO.createDirectory(target.getParentFile)
  IO.copyFile(source, target)
  log.info(s"Published Textus runtime catalog to ${target}")
  target
}

lazy val root = project
  .in(file("."))
  .settings(
    organization := "org.goldenport",
    name := "goldenport-cncf",
    version := "0.4.10-SNAPSHOT",

    scalaVersion := scala3version,

    resolvers ++= Seq(
      Resolver.defaultLocal,
      Resolver.mavenLocal,
      "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
    ),

    libraryDependencies ++= Seq(
      // Functional core
      "org.typelevel" %% "cats-core"   % "2.10.0",
      "org.typelevel" %% "cats-free"   % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",

      "org.http4s" %% "http4s-ember-server" % "0.23.27",
      "org.http4s" %% "http4s-core"         % "0.23.27",
      "org.http4s" %% "http4s-dsl"          % "0.23.27",

      // SQL
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.xerial" % "sqlite-jdbc" % "3.45.2.0",

      // Actor system (Akka-compatible, Scala 3 supported)
      "org.apache.pekko" %% "pekko-actor"  % "1.0.2",
      "org.apache.pekko" %% "pekko-stream" % "1.0.2",

      // JSON
      "io.circe" %% "circe-core"    % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser"  % "0.14.6",
      "org.yaml" % "snakeyaml" % "2.4",
      "com.vladsch.flexmark" % "flexmark" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-tables" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-gfm-strikethrough" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-gfm-tasklist" % "0.62.2",

      "org.slf4j" % "slf4j-simple" % "2.0.12",

      "org.goldenport" %% "goldenport-core" % "0.3.7",
      "org.simplemodeling" %% "simplemodeling-model" % "0.1.7",
      "org.goldenport" % "cncf-collaborator-api" % "0.1.0",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "org.scalacheck" %% "scalacheck" % "1.17.1" % Test
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

    publishMavenStyle := true,
    Compile / packageDoc / publishArtifact := false,

    generateTextusRuntimeCatalog := {
      val file = baseDirectory.value / "src/main/warehouse/repository/textus/runtime-catalog.yaml"
      textusWriteRuntimeCatalog(
        file,
        file,
        version.value,
        scalaBinaryVersion.value,
        textusBaseProvidedModules(
          libraryDependencies.value,
          organization.value,
          name.value,
          scalaBinaryVersion.value
        )
      )
    },

    exportTextusRuntimeCatalog := {
      val source = baseDirectory.value / "src/main/warehouse/repository/textus/runtime-catalog.yaml"
      val exportfile = target.value / "cncf.d" / "runtime-catalog.yaml"
      textusWriteRuntimeCatalog(
        source,
        exportfile,
        version.value,
        scalaBinaryVersion.value,
        textusBaseProvidedModules(
          libraryDependencies.value,
          organization.value,
          name.value,
          scalaBinaryVersion.value
        )
      )
    },

    copyTextusRuntimeCatalog := {
      val source = baseDirectory.value / "src/main/warehouse/repository/textus/runtime-catalog.yaml"
      textusPublishRuntimeCatalogFile(source, publishTo.value, baseDirectory.value, streams.value.log)
    },

    publishTextusRuntimeCatalog := {
      val source = generateTextusRuntimeCatalog.value
      textusPublishRuntimeCatalogFile(source, publishTo.value, baseDirectory.value, streams.value.log)
    },

    publish / packagedArtifacts := {
      publishTextusRuntimeCatalog.value
      (publish / packagedArtifacts).value
    },

    publishLocal := (publishLocal dependsOn exportTextusRuntimeCatalog).value,

    Test / fork := false,
    Test / parallelExecution := false,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    Test / javaOptions += "-Dtextus.test=true",
    (Test / test / testOptions) += Tests.Setup(() => System.setProperty("textus.test", "true")),
    (Test / test / testOptions) += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-l",
      "org.goldenport.tags.ManualSpec",
      "-l",
      "org.goldenport.tags.TimingSpec"
    ),
    Test / testOnly / fork := true,
    Test / testOnly / testOptions := Seq.empty
  )
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixOnCompile := false

addCommandAlias("fmt",      ";scalafmtAll;scalafmtSbt")
addCommandAlias("fmtCheck", ";scalafmtCheckAll;scalafmtSbtCheck")
addCommandAlias("fix",      ";scalafixAll")
addCommandAlias("fixCheck", ";scalafixAll --check")
addCommandAlias(
  "testEntityAuthorization",
  "testOnly org.goldenport.cncf.entity.EntityCreateDefaultsPolicySpec org.goldenport.cncf.security.EntityAuthorizationProfileSpec org.goldenport.cncf.security.SecuritySubjectSpec org.goldenport.cncf.unitofwork.UnitOfWorkTargetAuthorizationSpec org.goldenport.cncf.unitofwork.UnitOfWorkSearchAuthorizationSpec org.goldenport.cncf.action.ActionEngineAuthorizationFailureCommitSpec org.goldenport.cncf.action.ActionCallEntityAccessMetricsSpec org.goldenport.cncf.http.StaticFormAppRendererSpec"
)

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
  val vertag = s"goldenport-cncf:${version.value}"

  val cmd =
    s"docker build --no-cache -t $latest -t $vertag ."

  if (sys.process.Process(cmd).! != 0)
    sys.error("Docker build failed")

  log.info(s"Docker images built locally: $latest, $vertag")
}

lazy val dockerPush = taskKey[Unit]("Push CNCF Docker image to GHCR")

dockerPush := {
  val log = streams.value.log

  val repo = "ghcr.io/asami/goldenport-cncf"
  val vertag = s"$repo:${version.value}"
  val latest = s"$repo:latest"

  val tagcmds = Seq(
    s"docker tag goldenport-cncf:${version.value} $vertag",
    s"docker tag goldenport-cncf:latest $latest"
  )

  tagcmds.foreach { cmd =>
    if (sys.process.Process(cmd).! != 0)
      sys.error(s"Tagging failed: $cmd")
  }

  val pushcmd = s"docker push $repo --all-tags"
  if (sys.process.Process(pushcmd).! != 0)
    sys.error("Docker push failed")

  log.info("Docker images pushed to GHCR.")
}

lazy val dockerDeploy = taskKey[Unit]("Build and push CNCF Docker image")

dockerDeploy := Def.sequential(
  dockerBuild,
  dockerPush
).value
