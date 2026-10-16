package org.goldenport.cncf.phase51.build

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import scala.util.control.NonFatal

/*
 * @since   Jul. 28, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CncfGenerationInputs(
  cozyGeneratorVersion: String,
  cncfTargetVersion: String,
  runtimeDescriptor: File,
  runtimeDescriptorSha256: String,
  source: File,
  sourceIdentity: String,
  sourceSha256: String
)

final case class CncfGenerationSnapshot(
  scalaArtifacts: Vector[(String, String)],
  provenanceSha256: String
)

object CncfGenerationBuildContract {
  val GENERATION_PROVENANCE_PATH = "target/cozy/generation-provenance.json"

  def resolveCozyDevelopmentDirectory(
    projectDirectory: File,
    selection: Option[String]
  ): Either[Vector[String], Option[File]] = selection match {
    case None => Right(None)
    case Some(selected) if selected.trim.isEmpty =>
      Left(Vector("The explicit Cozy development directory must not be blank."))
    case Some(selected) =>
      try {
        val source = new File(selected)
        if (!source.isAbsolute)
          Left(Vector("The explicit Cozy development directory must be an absolute path."))
        else {
          val lexicalproject = projectDirectory.getAbsoluteFile.toPath.normalize()
          val project = projectDirectory.getCanonicalFile.toPath
          val lexicalsource = source.toPath.normalize()
          val canonicalsource = source.getCanonicalFile
          val sourcepath = canonicalsource.toPath
          val checkoutparent = lexicalproject.resolve(".codex-workflow/checkouts")
          val dedicatedchild =
            sourcepath.getParent == checkoutparent &&
              lexicalsource.getParent == checkoutparent &&
              canonicalsource.getName.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")
          val errors = Vector.newBuilder[String]
          if (project.startsWith(sourcepath))
            errors += "The Cozy development directory must not equal or contain the CNCF project directory."
          if (
            (lexicalsource.startsWith(lexicalproject) || lexicalsource.startsWith(project)) &&
            !sourcepath.startsWith(project)
          )
            errors += "The explicit Cozy development directory must not escape the CNCF project through a symlink."
          if (sourcepath.startsWith(project) && sourcepath != project && !dedicatedchild)
            errors += "A nested Cozy development directory must be a canonical direct .codex-workflow/checkouts child with a matching lexical parent and legal checkout name."
          if (!new File(canonicalsource, "build.sbt").isFile)
            errors += "The Cozy development directory must contain build.sbt."
          if (!new File(canonicalsource, "src/main/scala/cozy/Cozy.scala").isFile)
            errors += "The Cozy development directory must contain src/main/scala/cozy/Cozy.scala."
          val result = errors.result()
          if (result.nonEmpty) Left(result) else Right(Some(canonicalsource))
        }
      } catch {
        case NonFatal(error) =>
          Left(Vector(s"Cannot canonicalize the explicit Cozy development directory '$selected': ${error.getMessage}"))
      }
  }

  def isGenerationNotice(message: String): Boolean =
    Option(message).exists { value =>
      val normalized = value.trim
      normalized.startsWith("[cozy.generation.acceptance]") &&
        normalized.contains(" level=notice ")
    }

  def resolve(
    cozyGeneratorVersion: String,
    cncfTargetVersion: String,
    runtimeDescriptor: File,
    projectDirectory: File,
    source: File
  ): Either[Vector[String], CncfGenerationInputs] = {
    val cozyversion = Option(cozyGeneratorVersion).map(_.trim).getOrElse("")
    val cncfversion = Option(cncfTargetVersion).map(_.trim).getOrElse("")
    val descriptor = _normalize_file(runtimeDescriptor)
    val projectdir = _normalize_file(projectDirectory)
    val sourcefile = _normalize_file(source)
    val errors = Vector.newBuilder[String]
    if (cozyversion.isEmpty)
      errors += "The exact Cozy generator version is required."
    if (cncfversion.isEmpty)
      errors += "The exact CNCF target version is required."
    if (!descriptor.exists(_.isFile))
      errors += "The generated CNCF runtime descriptor is required."
    if (!projectdir.exists(_.isDirectory))
      errors += "The CNCF project directory is required."
    if (!sourcefile.exists(_.isFile))
      errors += "The Information CML source is required."
    if (
      projectdir.exists(_.isDirectory) &&
      sourcefile.exists(_.isFile) &&
      !sourcefile.get.toPath.startsWith(projectdir.get.toPath)
    )
      errors += "The Information CML source must be inside the CNCF project directory."
    val result = errors.result()
    if (result.nonEmpty)
      Left(result)
    else {
      val sourceidentity =
        projectdir.get.toPath
          .relativize(sourcefile.get.toPath)
          .toString
          .replace(File.separatorChar, '/')
      Right(CncfGenerationInputs(
        cozyversion,
        cncfversion,
        descriptor.get,
        _sha256(descriptor.get),
        sourcefile.get,
        sourceidentity,
        _sha256(sourcefile.get)
      ))
    }
  }

  def command(
    inputs: CncfGenerationInputs,
    outputDirectory: File,
    cozyDevelopmentDirectory: Option[File] = None
  ): Either[Vector[String], Vector[String]] = {
    val outputdir = _normalize_file(outputDirectory)
    val errors = Vector.newBuilder[String]
    if (!inputs.source.isFile)
      errors += "The Information CML source is required."
    else if (_sha256(inputs.source) != inputs.sourceSha256)
      errors += "The Information CML source changed after generation inputs were resolved."
    if (outputdir.isEmpty)
      errors += "The generation output directory is required."
    val result = errors.result()
    if (result.nonEmpty)
      Left(result)
    else
      Right(_cozy_command_prefix(inputs, cozyDevelopmentDirectory) ++ Vector(
        "modeler-scala-value",
        inputs.source.getAbsolutePath,
        "--cncf-version",
        inputs.cncfTargetVersion,
        "--cozy-generator-version",
        inputs.cozyGeneratorVersion,
        "--cncf-runtime-descriptor",
        inputs.runtimeDescriptor.getAbsolutePath,
        "--cncf-runtime-descriptor-sha256",
        inputs.runtimeDescriptorSha256,
        "--generation-source-identity",
        inputs.sourceIdentity,
        s"--save=${outputdir.get.getAbsolutePath}"
      ))
  }

  def generationFailure(
    inputs: CncfGenerationInputs,
    exitCode: Int
  ): String =
    s"CNCF Information generation failed with exit code $exitCode. " +
      s"generator=org.simplemodeling:cozy_2.12:${inputs.cozyGeneratorVersion} " +
      s"target=org.goldenport:goldenport-cncf_3:${inputs.cncfTargetVersion} " +
      "recovery=publish-or-select-the-exact-project-owned-Cozy-generator-coordinate"

  def validationCommand(
    inputs: CncfGenerationInputs,
    outputDirectory: File,
    cozyDevelopmentDirectory: Option[File] = None
  ): Either[Vector[String], Vector[String]] = {
    val outputdir = _normalize_file(outputDirectory)
    val errors = Vector.newBuilder[String]
    if (!inputs.source.isFile)
      errors += "The Information CML source is required."
    if (outputdir.isEmpty)
      errors += "The generation output directory is required."
    val result = errors.result()
    if (result.nonEmpty)
      Left(result)
    else
      Right(_cozy_command_prefix(inputs, cozyDevelopmentDirectory) ++ Vector(
        "generation-provenance-validate",
        inputs.source.getAbsolutePath,
        s"--save=${outputdir.get.getAbsolutePath}",
        "--cncf-version",
        inputs.cncfTargetVersion,
        "--cncf-runtime-descriptor-sha256",
        inputs.runtimeDescriptorSha256,
        "--cozy-generator-version",
        inputs.cozyGeneratorVersion,
        "--generation-source-identity",
        inputs.sourceIdentity,
        "--generation-source-sha256",
        inputs.sourceSha256
      ))
  }

  def provenanceFile(outputDirectory: File): File =
    new File(outputDirectory.getAbsoluteFile.toPath.normalize().toFile, GENERATION_PROVENANCE_PATH)

  def snapshot(
    outputDirectory: File
  ): Either[Vector[String], CncfGenerationSnapshot] = {
    val outputdir = _normalize_file(outputDirectory)
    val errors = Vector.newBuilder[String]
    if (!outputdir.exists(_.isDirectory))
      errors += "The generation output directory is required."
    val provenance = outputdir.map(provenanceFile)
    if (outputdir.exists(_.isDirectory) && !provenance.exists(_.isFile))
      errors += s"Generation provenance is required at $GENERATION_PROVENANCE_PATH."
    val result = errors.result()
    if (result.nonEmpty)
      Left(result)
    else {
      val target = new File(outputdir.get, "target")
      val artifacts =
        _files_below(target)
          .filter(file => file.isFile && file.getName.endsWith(".scala"))
          .sortBy(_.getAbsolutePath)
          .map { file =>
            val relative =
              target.toPath
                .relativize(file.toPath)
                .toString
                .replace(File.separatorChar, '/')
            relative -> _sha256(file)
          }
      if (artifacts.isEmpty)
        Left(Vector("At least one generated Scala artifact is required."))
      else
        Right(CncfGenerationSnapshot(artifacts, _sha256(provenance.get)))
    }
  }

  private def _cozy_command_prefix(
    inputs: CncfGenerationInputs,
    cozydevelopmentdirectory: Option[File]
  ): Vector[String] =
    Vector("cozy", "--runtime", inputs.cozyGeneratorVersion) ++
      cozydevelopmentdirectory.toVector.flatMap(directory =>
        Vector("--runtime-dev-dir", directory.getAbsolutePath)
      )

  private def _normalize_file(file: File): Option[File] =
    Option(file).map(_.getAbsoluteFile.toPath.normalize().toFile)

  private def _files_below(file: File): Vector[File] =
    if (!file.exists())
      Vector.empty
    else if (file.isFile)
      Vector(file)
    else
      Option(file.listFiles())
        .map(_.toVector.flatMap(_files_below))
        .getOrElse(Vector.empty)

  private def _sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").
      digest(Files.readAllBytes(file.toPath)).
      map(byte => f"${byte & 0xff}%02x").
      mkString
}
