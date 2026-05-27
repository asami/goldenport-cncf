package org.goldenport.cncf.http

import java.nio.file.{Path, Paths}

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Apr. 14, 2026
 *  version Apr. 14, 2026
 * @version May. 27, 2026
 * @author  ASAMI, Tomoharu
 */
object WebDescriptorResolver {
  def resolve(
    subsystem: Subsystem
  ): Consequence[WebDescriptor] = {
    val descriptorpath = _subsystem_web_descriptor_path(subsystem)
    val exclude = descriptorpath.toSet
    val base = (
      _load_component_web_descriptors(subsystem, exclude) ++
        _load_configuration_component_dev_web_descriptors(subsystem.configuration, exclude)
    )
      .foldLeft(WebDescriptor.empty)(_.mergeOverride(_))
    val withdescriptor = descriptorpath match {
      case Some(path) =>
        WebDescriptor.load(path) match {
          case Consequence.Success(value) => base.mergeOverride(value)
          case Consequence.Failure(_) => base
        }
      case None => base
    }
    RuntimeConfig.getString(subsystem.configuration, RuntimeConfig.WebDescriptorKey) match {
      case Some(path) =>
        WebDescriptor.load(Paths.get(path)).map(withdescriptor.mergeOverride)
      case None => Consequence.success(withdescriptor)
    }
  }

  def resolve(
    configuration: ResolvedConfiguration
  ): Consequence[WebDescriptor] =
    RuntimeConfig.getString(configuration, RuntimeConfig.WebDescriptorKey) match {
      case Some(path) => WebDescriptor.load(Paths.get(path))
      case None => Consequence.success(WebDescriptor.empty)
    }

  private def _load_component_web_descriptors(
    subsystem: Subsystem,
    exclude: Set[Path]
  ): Vector[WebDescriptor] =
    _component_web_descriptor_paths(subsystem)
      .filterNot(exclude.contains)
      .flatMap { path =>
      WebDescriptor.load(path).toOption
    }

  private def _load_configuration_component_dev_web_descriptors(
    configuration: ResolvedConfiguration,
    exclude: Set[Path]
  ): Vector[WebDescriptor] =
    _configuration_component_dev_paths(configuration)
      .filterNot(exclude.contains)
      .flatMap(path => WebDescriptor.load(path).toOption)

  private def _configuration_component_dev_paths(
    configuration: ResolvedConfiguration
  ): Vector[Path] =
    Vector(
      RuntimeConfig.ComponentDevDirKey,
      "cncf.component.dev.dir"
    ).flatMap(key => RuntimeConfig.getString(configuration, key).toVector)
      .flatMap(_split_path_values)
      .map(_strip_repository_prefix)
      .filter(_.nonEmpty)
      .map(path => Paths.get(path).toAbsolutePath.normalize)
      .distinct

  private def _split_path_values(value: String): Vector[String] =
    value.split(",").toVector.map(_.trim).filter(_.nonEmpty)

  private def _strip_repository_prefix(value: String): String =
    if (value.startsWith("component-dev-dir:"))
      value.stripPrefix("component-dev-dir:").trim
    else
      value.trim

  private def _component_web_descriptor_paths(
    subsystem: Subsystem
  ): Vector[Path] =
    subsystem.components
      .flatMap(_.artifactMetadata.flatMap(_.archivePath))
      .map(path => Paths.get(path).toAbsolutePath.normalize)
      .distinct

  private def _subsystem_web_descriptor_path(
    subsystem: Subsystem
  ): Option[Path] =
    subsystem.descriptor.map(_.path.toAbsolutePath.normalize)
}
