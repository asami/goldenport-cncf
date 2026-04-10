package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence

/*
 * @since   Mar. 26, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextusIdentitySubsystemDescriptor(
  path: Path,
  subsystemName: String,
  componentName: String,
  coordinate: String
) {
  def componentVersion: Option[String] =
    TextusIdentitySubsystemDescriptor.coordinateParts(coordinate).lift(2)
}

object TextusIdentitySubsystemDescriptor {
  val DefaultPath: Path =
    Paths.get("/Users/asami/src/dev2026/textus-identity/subsystem-descriptor.yaml")

  def default(path: Path = DefaultPath): TextusIdentitySubsystemDescriptor =
    TextusIdentitySubsystemDescriptor(
      path = path,
      subsystemName = "textus-identity",
      componentName = "textus-user-account",
      coordinate = "org.simplemodeling.car:textus-user-account:0.1.0"
    )

  def load(path: Path = DefaultPath): Consequence[TextusIdentitySubsystemDescriptor] =
    Consequence {
      val lines =
        Files.readAllLines(path).asScala.toVector.map(_.trim).filterNot(_.isEmpty)
      val subsystemName = _single_value(lines, "subsystem", path)
      val componentName = _single_value(lines, "component", path)
      val coordinate = _single_value(lines, "coordinate", path)
      val parts = coordinateParts(coordinate)
      require(parts.size == 3, s"invalid component coordinate: $coordinate")
      require(
        parts(1) == componentName,
        s"component coordinate artifact must match component name: component=$componentName coordinate=$coordinate"
      )
      TextusIdentitySubsystemDescriptor(
        path = path,
        subsystemName = subsystemName,
        componentName = componentName,
        coordinate = coordinate
      )
    }

  def coordinateParts(coordinate: String): Vector[String] =
    coordinate.split(':').toVector.map(_.trim).filter(_.nonEmpty)

  private def _single_value(
    lines: Vector[String],
    key: String,
    path: Path
  ): String = {
    val prefixes = Vector(s"$key ", s"$key:", s"- $key ", s"- $key:")
    lines.collectFirst {
      case line if prefixes.exists(line.startsWith) =>
        prefixes.find(line.startsWith) match {
          case Some(prefix) => line.substring(prefix.length).trim
          case None => ""
        }
    } match {
      case Some(value) if value.nonEmpty => value
      case _ => throw new IllegalArgumentException(s"missing $key in ${path}")
    }
  }
}
