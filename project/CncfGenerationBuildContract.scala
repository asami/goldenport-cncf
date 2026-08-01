package org.goldenport.cncf.phase51.build

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

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
    outputDirectory: File
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
      Right(Vector(
        "cozy",
        "--runtime",
        inputs.cozyGeneratorVersion,
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
    outputDirectory: File
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
      Right(Vector(
        "cozy",
        "--runtime",
        inputs.cozyGeneratorVersion,
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
