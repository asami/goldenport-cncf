package org.goldenport.cncf.http

import java.nio.file.{Files, Path, Paths}

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Apr. 14, 2026
 *  version Apr. 14, 2026
 *  version Jul. 30, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object WebDescriptorResolver {
  def resolveForRuntimeSubsystem(
    subsystem: Subsystem
  ): Consequence[WebDescriptor] =
    if (subsystem == null)
      Consequence.configurationInvalid("runtime Web descriptor Subsystem is required")
    else
      for {
        configured <- subsystem.runtimeWebDescriptorPathC
        componentdevdirs <- subsystem.runtimeComponentDevDirsC
        descriptor <- _resolve(subsystem, configured, componentdevdirs)
      } yield descriptor

  def resolve(
    subsystem: Subsystem
  ): Consequence[WebDescriptor] = {
    _resolve(subsystem, RuntimeConfig.getString(subsystem.configuration, RuntimeConfig.webDescriptorKey).map(Paths.get(_)), _configuration_component_dev_paths(subsystem.configuration))
  }

  private def _resolve(
    subsystem: Subsystem,
    configured: Option[Path],
    componentdevdirs: Vector[Path]
  ): Consequence[WebDescriptor] = {
    val descriptorpath = _subsystem_web_descriptor_path(subsystem)
    val exclude = descriptorpath.toSet
    val base = (
      _load_component_web_descriptors(subsystem, exclude) ++
        _load_component_dev_web_descriptors(componentdevdirs, exclude)
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
    configured match {
      case Some(path) =>
        WebDescriptor.load(path).map(withdescriptor.mergeOverride)
      case None => Consequence.success(withdescriptor)
    }
  }

  def resolve(
    configuration: ResolvedConfiguration
  ): Consequence[WebDescriptor] =
    RuntimeConfig.getString(configuration, RuntimeConfig.webDescriptorKey) match {
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

  private def _load_component_dev_web_descriptors(
    componentdevdirs: Vector[Path],
    exclude: Set[Path]
  ): Vector[WebDescriptor] =
    componentdevdirs
      .filterNot(exclude.contains)
      .flatMap(path => WebDescriptor.load(_component_dev_descriptor_path(path)).toOption)

  private def _component_dev_descriptor_path(path: Path): Path =
    if (Files.isDirectory(path)) path.resolve("web.yaml") else path

  private def _configuration_component_dev_paths(
    configuration: ResolvedConfiguration
  ): Vector[Path] =
    Vector(
      RuntimeConfig.componentDevDirKey,
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
