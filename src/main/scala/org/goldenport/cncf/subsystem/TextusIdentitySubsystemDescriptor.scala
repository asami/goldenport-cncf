package org.goldenport.cncf.subsystem

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence

/*
 * @since   Mar. 26, 2026
 *  version Apr. 23, 2026
 * @version Jul. 19, 2026
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
    GenericSubsystemDescriptor.load(path).flatMap { descriptor =>
      Consequence {
        val component = descriptor.componentBindings.headOption.getOrElse(
          throw new IllegalArgumentException(s"missing component binding in $path")
        )
        val componentVersion = component.componentVersion.getOrElse(
          throw new IllegalArgumentException(s"missing component version in $path")
        )
        TextusIdentitySubsystemDescriptor(
          path = path,
          subsystemName = descriptor.subsystemName,
          componentName = component.componentName,
          componentVersion = componentVersion
        )
      }
    }

  def coordinateParts(coordinate: String): Vector[String] =
    coordinate.split(':').toVector.map(_.trim).filter(_.nonEmpty)

  def coordinateVersion(coordinate: String): Option[String] =
    GenericSubsystemDescriptor.coordinateVersion(coordinate)
}
