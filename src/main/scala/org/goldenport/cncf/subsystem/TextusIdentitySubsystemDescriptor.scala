package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence

/*
 * @since   Mar. 26, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextusIdentitySubsystemDescriptor(
  path: Path,
  subsystemName: String,
  componentName: String,
  componentVersion: String
) {
  def componentVersionOption: Option[String] =
    Option(componentVersion).map(_.trim).filter(_.nonEmpty)
}

object TextusIdentitySubsystemDescriptor {
  val DefaultPath: Path =
    Paths.get("/Users/asami/src/dev2026/textus-identity/subsystem-descriptor.yaml")

  def default(path: Path = DefaultPath): TextusIdentitySubsystemDescriptor =
    TextusIdentitySubsystemDescriptor(
      path = path,
      subsystemName = "textus-identity",
      componentName = "textus-user-account",
      componentVersion = "0.1.0"
    )

  def load(path: Path = DefaultPath): Consequence[TextusIdentitySubsystemDescriptor] =
    Consequence {
      val lines =
        Files.readAllLines(path).asScala.toVector.map(_.trim).filterNot(_.isEmpty)
      val subsystemName = _single_value(lines, "subsystem", path)
      val componentName = _single_value(lines, "component", path)
      val componentVersion =
        _optional_single_value(lines, "version")
          .orElse(_optional_single_value(lines, "componentVersion"))
          .orElse(_optional_single_value(lines, "coordinate").flatMap(coordinateVersion))
          .getOrElse(throw new IllegalArgumentException(s"missing component version in ${path}"))
      TextusIdentitySubsystemDescriptor(
        path = path,
        subsystemName = subsystemName,
        componentName = componentName,
        componentVersion = componentVersion
      )
    }

  def coordinateParts(coordinate: String): Vector[String] =
    coordinate.split(':').toVector.map(_.trim).filter(_.nonEmpty)

  def coordinateVersion(coordinate: String): Option[String] =
    coordinateParts(coordinate) match {
      case Vector(_, _, version) => Some(version)
      case Vector(_, version) => Some(version)
      case _ => None
    }

  private def _single_value(
    lines: Vector[String],
    key: String,
    path: Path
  ): String = {
    _optional_single_value(lines, key).getOrElse(throw new IllegalArgumentException(s"missing $key in ${path}"))
  }

  private def _optional_single_value(
    lines: Vector[String],
    key: String
  ): Option[String] = {
    val prefixes = Vector(s"$key ", s"$key:", s"- $key ", s"- $key:")
    lines.collectFirst {
      case line if prefixes.exists(line.startsWith) =>
        prefixes.find(line.startsWith) match {
          case Some(prefix) => line.substring(prefix.length).trim
          case None => ""
        }
    }.filter(_.nonEmpty)
  }
}
