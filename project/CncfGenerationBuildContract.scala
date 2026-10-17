package org.goldenport.cncf.phase51.build

import java.io.File
import scala.util.control.NonFatal

/*
 * @since   Jul. 28, 2026
 * @version Oct. 17, 2026
 */
final case class CncfGenerationInputs(
  cozyGeneratorVersion: String,
  cncfTargetVersion: String,
  runtimeDescriptor: File,
  source: File,
  sourceIdentity: String
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

  def selectedGeneratorRoot(
    propertyValue: Option[String],
    environmentValue: Option[String]
  ): Either[Vector[String], Option[File]] = {
    propertyValue.orElse(environmentValue) match {
      case None => Right(None)
      case Some(value) =>
        val selected = value.trim
        def _failure_(reason: String): Left[Vector[String], Option[File]] = {
          Left(Vector(
            s"The selected Cozy generator root '$selected' $reason. " +
              "recovery=set-cncf.cozyGeneratorRoot-or-CNCF_COZY_GENERATOR_ROOT-to-an-existing-Cozy-project-directory-containing-build.sbt"
          ))
        }
        if (selected.isEmpty)
          _failure_("is blank")
        else {
          try {
            val root = new File(selected).getCanonicalFile
            if (!root.isDirectory)
              _failure_("must be an existing directory")
            else if (!new File(root, "build.sbt").isFile)
              _failure_("must contain a regular build.sbt file")
            else
              Right(Some(root))
          } catch {
            case NonFatal(_) => _failure_("cannot be canonicalized or inspected")
          }
        }
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
        sourcefile.get,
        sourceidentity
      ))
    }
  }

  def command(
    inputs: CncfGenerationInputs,
    outputDirectory: File,
    generatorRoot: Option[File] = None
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
        inputs.cozyGeneratorVersion
      ) ++ generatorRoot.toVector.flatMap(root => Vector("--runtime-dev-dir", root.getCanonicalPath)) ++ Vector(
        "modeler-scala-value",
        inputs.source.getAbsolutePath,
        "--cncf-version",
        inputs.cncfTargetVersion,
        "--cozy-generator-version",
        inputs.cozyGeneratorVersion,
        "--cncf-runtime-descriptor",
        inputs.runtimeDescriptor.getAbsolutePath,
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
    generatorRoot: Option[File] = None
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
        inputs.cozyGeneratorVersion
      ) ++ generatorRoot.toVector.flatMap(root => Vector("--runtime-dev-dir", root.getCanonicalPath)) ++ Vector(
        "generation-provenance-validate",
        inputs.source.getAbsolutePath,
        s"--save=${outputdir.get.getAbsolutePath}",
        "--cncf-version",
        inputs.cncfTargetVersion,
        "--cozy-generator-version",
        inputs.cozyGeneratorVersion,
        "--generation-source-identity",
        inputs.sourceIdentity
      ))
  }

  def provenanceFile(outputDirectory: File): File =
    new File(outputDirectory.getAbsoluteFile.toPath.normalize().toFile, GENERATION_PROVENANCE_PATH)

  private def _normalize_file(file: File): Option[File] =
    Option(file).map(_.getAbsoluteFile.toPath.normalize().toFile)
}
