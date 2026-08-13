package org.goldenport.cncf.subsystem

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId

/*
 * @since   Mar. 26, 2026
 *  version Apr. 23, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextusIdentitySubsystemDescriptor(
  path: Path,
  subsystemName: String,
  componentId: ComponentId,
  release: String
) {
  def componentName: String = componentId.name

  def componentVersion: String = release

  def componentVersionOption: Option[String] =
    Option(release).map(_.trim).filter(_.nonEmpty)

  def toGenericDescriptor: GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = path,
      subsystemName = subsystemName,
      version = componentVersionOption,
      componentBindings = Vector(
        GenericSubsystemComponentBinding(
          componentName = componentName,
          version = componentVersionOption,
          componentId = Some(componentId)
        )
      )
    )
}

object TextusIdentitySubsystemDescriptor {
  val DefaultPath: Path =
    Paths.get("/Users/asami/src/dev2026/textus-identity/subsystem-descriptor.yaml")

  def default(path: Path = DefaultPath): TextusIdentitySubsystemDescriptor =
    TextusIdentitySubsystemDescriptor(
      path = path,
      subsystemName = "textus-identity",
      componentId = ComponentId("org.simplemodeling.textus.UserAccount"),
      release = "0.6.0-SNAPSHOT"
    )

  def load(path: Path = DefaultPath): Consequence[TextusIdentitySubsystemDescriptor] =
    GenericSubsystemDescriptor.load(path).flatMap { descriptor =>
      Consequence {
        val component = descriptor.componentBindings.headOption.getOrElse(
          throw new IllegalArgumentException(s"missing component binding in $path")
        )
        val componentid = component.componentId.getOrElse(
          throw new IllegalArgumentException(s"missing canonical component id in $path")
        )
        val release = component.componentVersion.getOrElse(
          throw new IllegalArgumentException(s"missing component version in $path")
        )
        TextusIdentitySubsystemDescriptor(
          path = path,
          subsystemName = descriptor.subsystemName,
          componentId = componentid,
          release = release
        )
      }
    }

  def coordinateParts(coordinate: String): Vector[String] =
    coordinate.split(':').toVector.map(_.trim).filter(_.nonEmpty)

  def coordinateVersion(coordinate: String): Option[String] =
    GenericSubsystemDescriptor.coordinateVersion(coordinate)
}
