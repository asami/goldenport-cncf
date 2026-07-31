import sbt.TestFrameworks
import sbt.Tests
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.goldenport.cncf.phase51.build.{CncfGenerationBuildContract, CncfGenerationInputs}

val scala3Version = "3.3.8"
val cozyGeneratorVersion = "0.3.1-SNAPSHOT"

Compile / javacOptions ++= Seq("--release", "8")
Test / javacOptions := Seq("--release", "14")

lazy val generateTextusRuntimeCatalog = taskKey[File]("Generate Textus runtime catalog metadata for the warehouse repository.")
lazy val exportTextusRuntimeCatalog = taskKey[File]("Export Textus runtime catalog metadata for local development consumers.")
lazy val generateCncfRuntimeDescriptor = taskKey[File]("Generate CNCF runtime self descriptor for the runtime jar.")
lazy val cncfRuntimeClasspathFile = taskKey[File]("Write the local CNCF runtime classpath consumed by cncf --runtime-dev-dir.")
lazy val resolveInformationCmlGenerationInputs = taskKey[CncfGenerationInputs]("Resolve the exact Cozy generator, CNCF target, and runtime descriptor for Information CML generation.")
lazy val generateInformationCmlModel = taskKey[Seq[File]]("Generate CNCF Information vocabulary model from CML.")
lazy val verifyInformationCmlGenerationDeterminism = taskKey[Unit]("Verify repeated Information CML generation emits the same Scala file set and bytes.")
lazy val copyTextusRuntimeCatalog = taskKey[File]("Copy the checked-in Textus runtime catalog into the warehouse repository.")
lazy val publishTextusRuntimeCatalog = taskKey[File]("Publish Textus runtime catalog metadata into the warehouse repository.")

def textusBaseProvidedModules(
  dependencies: Seq[ModuleID],
  organizationName: String,
  moduleName: String,
  scalaBinaryVersion: String
): Vector[String] = {
  def _is_test_dependency_(module: ModuleID): Boolean =
    module.configurations.exists(_.toLowerCase.contains("test"))
  def _artifact_name_(module: ModuleID): String =
    if (module.crossVersion == CrossVersion.disabled)
      module.name
    else
      s"${module.name}_$scalaBinaryVersion"
  val self = s"$organizationName:${moduleName}_$scalaBinaryVersion"
  (Vector(self) ++
    dependencies
      .filterNot(_is_test_dependency_)
      .map(module => s"${module.organization}:${_artifact_name_(module)}"))
    .distinct
    .toVector
}

def textusRuntimeCatalogText(
  existingText: String,
  cncfVersion: String,
  scalaBinaryVersion: String,
  generatedAt: String,
  baseProvidedModules: Vector[String]
): String = {
  def _existing_value_(key: String): Option[String] =
    existingText.linesIterator.find(_.startsWith(s"$key:")).map(_.drop(key.length + 1).trim)
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
  def _version_block_(version: String): Option[String] =
    _version_blocks_(existingText).find(_._1 == version).map(_._2)
  def _version_value_(version: String, key: String): Option[String] =
    _version_block_(version).flatMap { block =>
      block.linesIterator
        .map(_.trim)
        .find(_.startsWith(s"$key:"))
        .map(_.drop(key.length + 1).trim)
        .filter(_.nonEmpty)
    }
  def _boolean_(value: String): Option[Boolean] =
    value.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "yes" | "on" | "1" => Some(true)
      case "false" | "no" | "off" | "0" => Some(false)
      case _ => None
    }
  def _runtime_recommended_opt_out_(): Boolean = {
    val configured =
      sys.props.get("textus.runtime.catalog.recommended")
        .orElse(sys.env.get("TEXTUS_RUNTIME_CATALOG_RECOMMENDED"))
        .flatMap(_boolean_)
    val versionblock =
      _version_value_(cncfVersion, "recommended")
        .orElse(_version_value_(cncfVersion, "recommend"))
        .flatMap(_boolean_)
    configured.orElse(versionblock).contains(false)
  }
  def _preserved_version_lines_(version: String): Vector[String] = {
    val generatedkeys =
      Set("channel", "status", "scalaBinaryVersion", "module", "publishedAt", "metadataUrl")
    _version_blocks_(existingText)
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
    baseProvidedModules.map(module => s"  - $module").mkString("baseProvided:\n", "\n", "")
  val channel =
    if (cncfVersion.endsWith("-SNAPSHOT")) "snapshot" else "stable"
  val recommendcurrent =
    channel == "stable" && !_runtime_recommended_opt_out_()
  val recommended =
    if (recommendcurrent)
      cncfVersion
    else
      _existing_value_("recommended").getOrElse(cncfVersion)
  val lateststable =
    if (channel == "stable") cncfVersion else _existing_value_("latestStable").getOrElse("")
  val latestsnapshot =
    if (channel == "snapshot") cncfVersion else _existing_value_("latestSnapshot").getOrElse("")
  val currentblock =
    s"""  - version: $cncfVersion
       |    channel: $channel
       |    status: active
       |    scalaBinaryVersion: "$scalaBinaryVersion"
       |    module: org.goldenport:goldenport-cncf_$scalaBinaryVersion:$cncfVersion
       |    publishedAt: $generatedAt
       |    metadataUrl: https://www.simplemodeling.org/repository/maven/org/goldenport/goldenport-cncf_$scalaBinaryVersion/maven-metadata.xml""".stripMargin +
      _preserved_version_lines_(cncfVersion).map("\n" + _).mkString
  val historyblocks =
    _version_blocks_(existingText)
      .foldLeft(Vector.empty[(String, String)]) { case (acc, (v, block)) =>
        acc.filterNot(_._1 == v) :+ (v -> block)
      }
      .filterNot(_._1 == cncfVersion)
      .map(_._2)
  val versions =
    (historyblocks :+ currentblock).mkString("\n")
  s"""schemaVersion: 1
     |generatedAt: $generatedAt
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

def cncfBuildInfoSource(
  targetDir: File,
  packageName: String,
  cncfVersion: String
): File = {
  val file = targetDir / packageName.replace('.', '/') / "CncfBuildInfo.scala"
  IO.createDirectory(file.getParentFile)
  IO.write(
    file,
    s"""package $packageName
       |
       |object CncfBuildInfo {
       |  val name: String = "cncf"
       |  val version: String = "$cncfVersion"
       |}
       |""".stripMargin
  )
  file
}

def cncfRuntimeDescriptorText(
  cncfVersion: String,
  scalaBinaryVersion: String,
  baseProvidedModules: Vector[String],
  predefinedResultCatalog: String,
  componentStyleCatalog: Array[Byte]
): String = {
  val baseprovidedblock =
    baseProvidedModules.map(module => s"  - $module").mkString("baseProvided:\n", "\n", "")
  val cataloglines = predefinedResultCatalog.trim.linesIterator.toVector
  val predefinedresultsblock = cataloglines.headOption.map { head =>
    (s"predefinedResults: $head" +: cataloglines.drop(1).map(line => s"  $line")).mkString("\n")
  }.getOrElse("predefinedResults: {}")
  val componentstylecatalogblock = Vector(
    "componentStyleCatalog:",
    "  schemaVersion: cncf.component-style-catalog-carrier.v1",
    "  resource: META-INF/cncf/component-style-catalog.json",
    "  encoding: base64",
    s"  sha256: ${sha256Bytes(componentStyleCatalog)}",
    "  bytes: \"" + Base64.getEncoder.encodeToString(componentStyleCatalog) + "\""
  ).mkString("\n")
  s"""schemaVersion: 1
     |runtime: cncf
     |version: $cncfVersion
     |scalaBinaryVersion: "$scalaBinaryVersion"
     |module: org.goldenport:goldenport-cncf_$scalaBinaryVersion:$cncfVersion
     |$baseprovidedblock
     |$predefinedresultsblock
     |$componentstylecatalogblock
     |""".stripMargin
}

def sha256Bytes(bytes: Array[Byte]): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

def runInformationCmlGeneration(
  inputs: CncfGenerationInputs,
  outputDirectory: File,
  baseDirectory: File,
  log: sbt.util.Logger
): Seq[File] = {
  IO.delete(outputDirectory)
  IO.createDirectory(outputDirectory)
  val command = CncfGenerationBuildContract.command(
    inputs,
    outputDirectory
  ).fold(
    errors => sys.error(errors.mkString("CNCF generation command preparation failed: ", " ", "")),
    identity
  )
  log.info(
    s"Generating ${inputs.sourceIdentity}@${inputs.sourceSha256} with Cozy ${inputs.cozyGeneratorVersion} for CNCF ${inputs.cncfTargetVersion} using ${inputs.runtimeDescriptor.getAbsolutePath}"
  )
  val exitcode = scala.sys.process.Process(command, baseDirectory).!(log)
  if (exitcode != 0)
    sys.error(CncfGenerationBuildContract.generationFailure(inputs, exitcode))
  val files =
    ((outputDirectory / "target") ** "*.scala").get
      .sortBy(_.getAbsolutePath)
  if (files.isEmpty)
    sys.error(s"no CNCF Information CML sources generated from: ${inputs.source.getAbsolutePath}")
  val validationcommand =
    CncfGenerationBuildContract.validationCommand(inputs, outputDirectory).fold(
      errors => sys.error(errors.mkString("CNCF generation provenance validation preparation failed: ", " ", "")),
      identity
    )
  val validationexitcode =
    scala.sys.process.Process(validationcommand, baseDirectory).!(log)
  if (validationexitcode != 0)
    sys.error(
      s"failed to validate CNCF Information generation provenance: ${CncfGenerationBuildContract.provenanceFile(outputDirectory).getAbsolutePath}"
    )
  files
}

def textusWriteRuntimeCatalog(
  sourceFile: File,
  targetFile: File,
  cncfVersion: String,
  scalaBinaryVersion: String,
  baseProvidedModules: Vector[String]
): File = {
  val existingtext =
    if (sourceFile.isFile)
      IO.read(sourceFile)
    else
      ""
  val text =
    textusRuntimeCatalogText(
      existingtext,
      cncfVersion,
      scalaBinaryVersion,
      java.time.Instant.now().toString,
      baseProvidedModules
    )
  IO.createDirectory(targetFile.getParentFile)
  IO.write(targetFile, text)
  targetFile
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
  sourceFile: File,
  publishResolver: Option[Resolver],
  baseDir: File,
  log: sbt.util.Logger
): File = {
  val warehousedir =
    publishResolver
      .flatMap(textusPublishRepositoryFile)
      .map(textusWarehouseDirFromMavenRepository)
      .getOrElse(textusWarehouseDirFromMavenRepository(baseDir / "maven-local"))
  val target = warehousedir / "repository" / "textus" / "runtime-catalog.yaml"
  val text = IO.read(sourceFile)
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
  IO.copyFile(sourceFile, target)
  log.info(s"Published Textus runtime catalog to ${target}")
  target
}

lazy val root = project
  .in(file("."))
  .settings(
    organization := "org.goldenport",
    name := "goldenport-cncf",
    version := "0.5.2-SNAPSHOT",

    scalaVersion := scala3Version,

    Test / unmanagedSources += baseDirectory.value / "project" / "CncfGenerationBuildContract.scala",

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
      "com.vladsch.flexmark" % "flexmark" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-tables" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-gfm-strikethrough" % "0.62.2",
      "com.vladsch.flexmark" % "flexmark-ext-gfm-tasklist" % "0.62.2",

      "org.slf4j" % "slf4j-simple" % "2.0.12",

      "org.goldenport" %% "goldenport-core" % "0.4.1",
      "org.simplemodeling" %% "simplemodeling-model" % "0.2.1-SNAPSHOT",
      "org.goldenport" % "cncf-collaborator-api" % "0.1.0",

      // Testing
      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "org.scalacheck" %% "scalacheck" % "1.17.1" % Test,
      "com.mysql" % "mysql-connector-j" % "8.4.0" % Test,
      "org.testcontainers" % "testcontainers-mysql" % "2.0.5" % Test
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

    generateCncfRuntimeDescriptor := {
      val file = (Compile / resourceManaged).value / "META-INF" / "cncf" / "runtime.yaml"
      IO.createDirectory(file.getParentFile)
      IO.write(
        file,
        cncfRuntimeDescriptorText(
          version.value,
          scalaBinaryVersion.value,
          textusBaseProvidedModules(
            libraryDependencies.value,
            organization.value,
            name.value,
            scalaBinaryVersion.value
          ),
          IO.read(baseDirectory.value / "src" / "main" / "resources" / "META-INF" / "cncf" / "predefined-results.json"),
          IO.readBytes(baseDirectory.value / "src" / "main" / "resources" / "META-INF" / "cncf" / "component-style-catalog.json")
        )
      )
      file
    },

    cncfRuntimeClasspathFile := {
      val file = target.value / "cncf.d" / "runtime-classpath.txt"
      val classpath = (Runtime / fullClasspath).value.map(_.data.getAbsolutePath).mkString(java.io.File.pathSeparator)
      IO.createDirectory(file.getParentFile)
      IO.write(file, classpath + "\n")
      file
    },

    resolveInformationCmlGenerationInputs := {
      CncfGenerationBuildContract.resolve(
        cozyGeneratorVersion,
        version.value,
        generateCncfRuntimeDescriptor.value,
        baseDirectory.value,
        baseDirectory.value / "src/main/cozy/information.cml"
      ).fold(
        errors => sys.error(errors.mkString("CNCF generation input resolution failed: ", " ", "")),
        identity
      )
    },

    generateInformationCmlModel := {
      val outputdir = target.value / "cncf-information-cml"
      val generationinputs = resolveInformationCmlGenerationInputs.value
      runInformationCmlGeneration(
        generationinputs,
        outputdir,
        baseDirectory.value,
        streams.value.log
      )
    },

    verifyInformationCmlGenerationDeterminism := {
      val outputdir = target.value / "cncf-information-cml-determinism"
      val generationinputs = resolveInformationCmlGenerationInputs.value
      val log = streams.value.log
      runInformationCmlGeneration(generationinputs, outputdir, baseDirectory.value, log)
      val coldsnapshot =
        CncfGenerationBuildContract.snapshot(outputdir).fold(
          errors => sys.error(errors.mkString("Cold Information CML snapshot failed: ", " ", "")),
          identity
        )
      runInformationCmlGeneration(generationinputs, outputdir, baseDirectory.value, log)
      val repeatedsnapshot =
        CncfGenerationBuildContract.snapshot(outputdir).fold(
          errors => sys.error(errors.mkString("Repeated Information CML snapshot failed: ", " ", "")),
          identity
        )
      if (repeatedsnapshot != coldsnapshot)
        sys.error(
          s"Information CML generation is not deterministic. Cold=$coldsnapshot repeated=$repeatedsnapshot"
        )
      log.info(
        s"Information CML generation determinism verified for ${coldsnapshot.scalaArtifacts.size} Scala files and provenance ${coldsnapshot.provenanceSha256}."
      )
    },

    Compile / resourceGenerators += Def.task {
      Seq(generateCncfRuntimeDescriptor.value)
    }.taskValue,

    Compile / sourceGenerators += Def.task {
      Seq(cncfBuildInfoSource((Compile / sourceManaged).value, "org.goldenport.cncf", version.value))
    }.taskValue,

    Compile / sourceGenerators += generateInformationCmlModel.taskValue,

    Test / test := (Test / test).dependsOn(verifyInformationCmlGenerationDeterminism).value,

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
