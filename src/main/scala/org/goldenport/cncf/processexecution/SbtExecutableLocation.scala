package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.{Cause, Descriptor}

/*
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object SbtExecutableLocation {
  def resolveC(path: Path, environment: Map[String, String]): Consequence[Path] = {
    if (path == Paths.get("sbt")) {
      val value = environment.get("PATH").orElse(sys.env.get("PATH")).getOrElse("")
      val paths = value.split(java.io.File.pathSeparator).filter(_.nonEmpty).toVector.flatMap { entry =>
        try Some(Paths.get(entry)) catch { case NonFatal(_) => None }
      }
      onPath(paths).flatMap(_available).map(Consequence.success).getOrElse(
        Consequence.configurationInvalid(unavailableMessage, Cause.Kind.Capability,
          Vector(Descriptor.Facet.Policy("sbt.execution.executable"), Descriptor.Facet.Reason("unavailable"))))
    } else if (path != null && path.isAbsolute && !path.toString.exists(_.isControl)) {
      _available(path).map(Consequence.success).getOrElse(_invalid_location_c)
    } else _invalid_location_c
  }

  val unavailableMessage = "Normal sbt executable is unavailable on PATH"

  def isUnavailable(conclusion: Conclusion): Boolean = {
    val cause = conclusion.observation.cause
    cause.kind.contains(Cause.Kind.Capability) &&
      cause.descriptor.facets.contains(Descriptor.Facet.Policy("sbt.execution.executable")) &&
      cause.descriptor.facets.contains(Descriptor.Facet.Reason("unavailable"))
  }

  def onPath(paths: Vector[Path]): Option[Path] =
    paths.iterator.map(_.resolve("sbt")).find(path => Files.isRegularFile(path) && Files.isExecutable(path))

  private def _available(path: Path): Option[Path] =
    try {
      val real = path.toRealPath()
      Option.when(Files.isRegularFile(real) && Files.isExecutable(real))(real)
    } catch { case NonFatal(_) => None }

  private def _invalid_location_c: Consequence[Path] =
    Consequence.argumentPolicyViolation(
      "sbt", "sbt.execution.runtime-location", "absolute available sbt executable or normal sbt", "invalid"
    )
}
