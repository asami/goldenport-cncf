package org.goldenport.cncf.config

import java.nio.file.{Path, Paths}

import scala.collection.mutable.ArrayBuffer

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingCollection

/**
 * Value-only repository selection policy resolved before Subsystem construction.
 *
 * Repository source selection belongs to the process bootstrap boundary. It
 * deliberately exposes neither source configuration nor binding provenance.
 */
final case class RepositoryBootstrapPolicy(
  repositoryDirs: Vector[String] = Vector.empty,
  repositoryComponentDevDirs: Vector[String] = Vector.empty,
  componentDirs: Vector[String] = Vector.empty,
  componentDevDirs: Vector[String] = Vector.empty,
  componentCarDirs: Vector[String] = Vector.empty,
  componentFiles: Vector[String] = Vector.empty,
  subsystemDevDirs: Vector[String] = Vector.empty,
  subsystemSarDirs: Vector[String] = Vector.empty,
  collaboratorRepositories: Vector[String] = Vector.empty,
  baseDirectory: Path = Paths.get("").toAbsolutePath.normalize,
  defaultRepositoriesEnabled: Boolean = true
) {
  /**
   * Resolved collaborator repository directories. Missing or blank input has
   * the established bootstrap-relative default; an explicit non-existent path
   * deliberately remains explicit and is not replaced by that default.
   */
  def collaboratorRepositoryPaths: Vector[Path] = {
    val values = collaboratorRepositories.map(_.trim).filter(_.nonEmpty)
    val effective = if (values.nonEmpty) values else Vector("collaborator.d")
    effective.map { value =>
      val path = Paths.get(value)
      if (path.isAbsolute) path.normalize else baseDirectory.resolve(path).normalize
    }.distinct
  }
}

object RepositoryBootstrapPolicy {
  /**
   * Admits legacy repository CLI values at the bootstrap boundary and removes
   * them from the runtime argument stream. Runtime consumers receive only the
   * resulting value policy.
   */
  def admitArguments(
    policy: RepositoryBootstrapPolicy,
    args: Array[String]
  ): (RepositoryBootstrapPolicy, Array[String]) = {
    val repositorydirs = ArrayBuffer.empty[String]
    val repositorycomponentdevdirs = ArrayBuffer.empty[String]
    val componentdirs = ArrayBuffer.empty[String]
    val componentdevdirs = ArrayBuffer.empty[String]
    val componentcardirs = ArrayBuffer.empty[String]
    val componentfiles = ArrayBuffer.empty[String]
    val subsystemdevdirs = ArrayBuffer.empty[String]
    val subsystemsardirs = ArrayBuffer.empty[String]
    val residual = ArrayBuffer.empty[String]
    var defaultrepositoriesenabled = policy.defaultRepositoriesEnabled
    var aftersentinel = false
    var index = 0

    def values(value: String): Vector[String] =
      value.split(",", -1).toVector.map(_.trim).filter(_.nonEmpty)

    def add(flag: String, value: String): Unit =
      flag match {
        case "repository-dir" => repositorydirs ++= values(value)
        case "repository-component-dev-dir" => repositorycomponentdevdirs ++= values(value)
        case "component-dir" => componentdirs ++= values(value)
        case "component-dev-dir" => componentdevdirs ++= values(value)
        case "component-car-dir" => componentcardirs ++= values(value)
        case "component-file" => componentfiles ++= values(value)
        case "subsystem-dev-dir" => subsystemdevdirs ++= values(value)
        case "subsystem-sar-dir" => subsystemsardirs ++= values(value)
      }

    def canonical(flag: String): Option[String] =
      flag match {
        case "repository-dir" | RuntimeConfig.repositoryDirKey => Some("repository-dir")
        case "repository-component-dev-dir" | RuntimeConfig.repositoryComponentDevDirKey | "cncf.repository.component.dev.dir" => Some("repository-component-dev-dir")
        case "component-dir" | RuntimeConfig.componentDirKey => Some("component-dir")
        case "component-dev-dir" | RuntimeConfig.componentDevDirKey | "cncf.component.dev.dir" => Some("component-dev-dir")
        case "component-car-dir" | RuntimeConfig.componentCarDirKey | "cncf.component.car.dir" => Some("component-car-dir")
        case "component-file" | RuntimeConfig.componentFileKey | RuntimeConfig.runtimeComponentFileKey => Some("component-file")
        case "subsystem-dev-dir" | RuntimeConfig.subsystemDevDirKey | RuntimeConfig.runtimeSubsystemDevDirKey | "cncf.subsystem.dev.dir" | "cncf.runtime.subsystem.dev.dir" => Some("subsystem-dev-dir")
        case "subsystem-sar-dir" | RuntimeConfig.subsystemSarDirKey | RuntimeConfig.runtimeSubsystemSarDirKey | "cncf.subsystem.sar.dir" | "cncf.runtime.subsystem.sar.dir" => Some("subsystem-sar-dir")
        case _ => None
      }

    while (index < args.length) {
      val arg = args(index)
      if (aftersentinel) {
        residual += arg
        index += 1
      } else if (arg == "--") {
        residual += arg
        aftersentinel = true
        index += 1
      } else if (arg == "--no-default-components") {
        defaultrepositoriesenabled = false
        residual += arg
        index += 1
      } else if (arg.startsWith("--")) {
        val option = arg.drop(2)
        val separator = option.indexOf('=')
        val flag = if (separator < 0) option else option.take(separator)
        canonical(flag) match {
          case Some(kind) if separator >= 0 =>
            add(kind, option.drop(separator + 1))
            index += 1
          case Some(kind) if index + 1 < args.length =>
            add(kind, args(index + 1))
            index += 2
          case Some(_) =>
            throw new IllegalArgumentException(s"--${flag} requires a value")
          case None =>
            residual += arg
            index += 1
        }
      } else {
        residual += arg
        index += 1
      }
    }
    val admitted = policy.copy(
      repositoryDirs = (policy.repositoryDirs ++ repositorydirs).distinct,
      repositoryComponentDevDirs = (policy.repositoryComponentDevDirs ++ repositorycomponentdevdirs).distinct,
      componentDirs = (policy.componentDirs ++ componentdirs).distinct,
      componentDevDirs = (policy.componentDevDirs ++ componentdevdirs).distinct,
      componentCarDirs = (policy.componentCarDirs ++ componentcardirs).distinct,
      componentFiles = (policy.componentFiles ++ componentfiles).distinct,
      subsystemDevDirs = (policy.subsystemDevDirs ++ subsystemdevdirs).distinct,
      subsystemSarDirs = (policy.subsystemSarDirs ++ subsystemsardirs).distinct,
      defaultRepositoriesEnabled = defaultrepositoriesenabled
    )
    (admitted, residual.toArray)
  }

  def resolve(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[RepositoryBootstrapPolicy] =
    if (bindings == null)
      Consequence.configurationInvalid("repository bootstrap configuration bindings are required")
    else
      for {
        repositorydirs <- bindings.value(CncfConfigurationParameterCatalog.repositoryDir)
        repositorycomponentdevdirs <- bindings.value(CncfConfigurationParameterCatalog.repositoryComponentDevDir)
        componentdirs <- bindings.value(CncfConfigurationParameterCatalog.componentDir)
        componentdevdirs <- bindings.value(CncfConfigurationParameterCatalog.componentDevDir)
        componentcardirs <- bindings.value(CncfConfigurationParameterCatalog.componentCarDir)
        componentfiles <- bindings.value(CncfConfigurationParameterCatalog.componentFile)
        subsystemdevdirs <- bindings.value(CncfConfigurationParameterCatalog.subsystemDevDir)
        subsystemsardirs <- bindings.value(CncfConfigurationParameterCatalog.subsystemSarDir)
        collaboratorrepositories <- bindings.value(CncfConfigurationParameterCatalog.collaboratorRepositories)
      } yield RepositoryBootstrapPolicy(
        repositoryDirs = repositorydirs.getOrElse(Vector.empty),
        repositoryComponentDevDirs = repositorycomponentdevdirs.getOrElse(Vector.empty),
        componentDirs = componentdirs.getOrElse(Vector.empty),
        componentDevDirs = componentdevdirs.getOrElse(Vector.empty),
        componentCarDirs = componentcardirs.getOrElse(Vector.empty),
        componentFiles = componentfiles.getOrElse(Vector.empty),
        subsystemDevDirs = subsystemdevdirs.getOrElse(Vector.empty),
        subsystemSarDirs = subsystemsardirs.getOrElse(Vector.empty),
        collaboratorRepositories = collaboratorrepositories.getOrElse(Vector.empty)
      )
}
