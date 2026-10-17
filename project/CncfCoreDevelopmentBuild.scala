package org.goldenport.cncf.phase51.build

import java.io.File
import java.nio.file.Path
import scala.util.control.NonFatal

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object CncfCoreDevelopmentBuild {
  def resolveSourceDirectory(
    projectDirectory: File,
    selection: Option[String]
  ): Either[Vector[String], Option[File]] = selection match {
    case None => Right(None)
    case Some(raw) if raw.trim.isEmpty =>
      _failure("path must not be empty or whitespace-only")
    case Some(raw) =>
      val candidate = new File(raw)
      if (!candidate.isAbsolute)
        _failure(s"path must be absolute: $raw")
      else
        try {
          val consumer = projectDirectory.getCanonicalFile.toPath
          val consumerlexical = projectDirectory.getAbsoluteFile.toPath.normalize()
          val selected = candidate.toPath.normalize()
          val source = candidate.getCanonicalFile
          val sourcepath = source.toPath
          val consumercontained = selected.startsWith(consumerlexical) || selected.startsWith(consumer)
          val checkoutparent = consumerlexical.resolve(".codex-workflow/checkouts")
          val dedicatedchild =
            selected.getParent == checkoutparent &&
              sourcepath.getParent == checkoutparent &&
              _legal_leaf(selected) && _legal_leaf(sourcepath)

          if (sourcepath == consumer)
            _failure(s"consumer cannot select itself: $source")
          else if (consumer.startsWith(sourcepath))
            _failure(s"consumer must not be inside source: $source, $consumer")
          else if (consumercontained && !sourcepath.startsWith(consumer))
            _failure(s"consumer-contained path must not canonically escape: $selected -> $source")
          else if ((consumercontained || sourcepath.startsWith(consumer)) && !dedicatedchild)
            _failure(s"roots must be disjoint or source must be a direct lexical and canonical .codex-workflow/checkouts child with a legal checkout name: $source, $consumer")
          else {
            val markers = Vector(
              "build.sbt",
              "src/main/scala/org/goldenport/http/HttpResponse.scala"
            )
            val errors = markers.flatMap { marker =>
              val file = new File(source, marker)
              if (!file.isFile)
                Vector(s"missing $marker in $source")
              else if (!file.getCanonicalFile.toPath.startsWith(sourcepath))
                Vector(s"source marker must remain within selected source: $file")
              else
                Vector.empty
            }
            if (errors.isEmpty) Right(Some(source))
            else Left(errors.map(_diagnostic))
          }
        } catch {
          case NonFatal(error) =>
            _failure(s"cannot canonicalize or inspect selected source $raw: ${error.getMessage}")
        }
  }

  def validateCoordinates(
    sourceOrganization: String,
    sourceModuleName: String,
    sourceVersion: String,
    sourceScalaVersion: String,
    expectedVersion: String,
    expectedScalaVersion: String
  ): Either[Vector[String], Unit] = {
    val errors = Vector(
      ("organization", "org.goldenport", sourceOrganization),
      ("moduleName", "goldenport-core", sourceModuleName),
      ("version", expectedVersion, sourceVersion),
      ("scalaVersion", expectedScalaVersion, sourceScalaVersion)
    ).collect {
      case (label, expected, actual) if expected != actual =>
        _diagnostic(s"$label expected '$expected', actual '$actual'")
    }
    if (errors.isEmpty) Right(()) else Left(errors)
  }

  private def _legal_leaf(path: Path): Boolean =
    Option(path.getFileName).exists(_.toString.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"))

  private def _diagnostic(message: String): String =
    s"Invalid TEXTUS_GOLDENPORT_CORE_CNCF_SOURCE_ROOT source checkout: $message"

  private def _failure(message: String): Left[Vector[String], Nothing] =
    Left(Vector(_diagnostic(message)))
}
