package org.goldenport.cncf.component.repository

import java.nio.file.{Files, Path}

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan. 30, 2026
 *  version Feb.  5, 2026
 *  version Mar. 26, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositorySpace(
  private val _entries: Vector[ComponentRepositorySpace.Slot] = Vector.empty
) {
  def discover(): Vector[Component] =
    _entries.flatMap(_.repository.discover())
}

object ComponentRepositorySpace {
  final case class ExtractedArgs(
    active: Either[String, Vector[String]],
    search: Either[String, Vector[String]],
    residual: Array[String],
    noDefault: Boolean
  )

  case class Slot(
    repository: ComponentRepository,
    origin: ComponentOrigin
  )

  // ComponentFactory
  def create(
    subsystem: Subsystem,
    cwd: Path,
    configuration: ResolvedConfiguration,
    componentDescriptors: Vector[ComponentDescriptor] = Vector.empty
  ): ComponentRepositorySpace = {
    val specs = _make_specs(cwd, configuration)
    val entries = _make_repositories(subsystem, specs, componentDescriptors)
    ComponentRepositorySpace(entries.toVector)
  }

  private def _make_specs(
    cwd: Path,
    configuration: ResolvedConfiguration
  ) = {
    val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array[String]())
    ComponentRepositorySpace.resolveSpecifications(extracted.active, cwd, extracted.noDefault) match {
      case Left(err) => Vector.empty
      case Right(specs) => specs
    }
  }

  private def _make_repositories(
    subsystem: Subsystem,
    specs: Seq[ComponentRepository.Specification],
    componentDescriptors: Vector[ComponentDescriptor]
  ): Seq[Slot] = {
    specs.map { spec =>
      val origin = _origin_for_spec(spec)
      val params = ComponentCreate(subsystem, origin, componentDescriptors)
      val repo = spec.build(params)
      Slot(repo, origin)
    }
  }

  private def _origin_for_spec(
    spec: ComponentRepository.Specification
  ): ComponentOrigin =
    spec match {
      case _: ComponentRepository.ComponentDirRepository.Specification =>
        ComponentOrigin.Repository("component-dir")
      case _: ComponentRepository.ScalaCliRepository.Specification =>
        ComponentOrigin.Repository("scala-cli")
    }

  // Legacy
  // ComponentFactory
  def create(
    subsystem: Subsystem,
    c: ResolvedConfiguration,
    repositorySpecs: Vector[ComponentRepository.Specification],
    componentDescriptors: Vector[ComponentDescriptor]
  ): ComponentRepositorySpace = {
    build(subsystem, repositorySpecs, componentDescriptors)
  }

  // Legacy
  private def build(
    subsystem: Subsystem,
    specs: Seq[ComponentRepository.Specification],
    componentDescriptors: Vector[ComponentDescriptor]
  ): ComponentRepositorySpace = {
    val entries = specs.toVector.map { spec =>
      val origin = originForSpec(spec)
      val params = ComponentCreate(subsystem, origin, componentDescriptors)
      val repo = spec.build(params)
      Slot(repo, origin)
    }
    new ComponentRepositorySpace(entries)
  }

  // CncfRuntime
  def resolveSpecifications(
    result: Either[String, Vector[String]],
    cwd: Path,
    noDefault: Boolean
  ): Either[String, Vector[ComponentRepository.Specification]] =
    result match {
      case Left(err) =>
        Left(err)
      case Right(values) =>
        val parsed = Vector.newBuilder[ComponentRepository.Specification]
        var error: Option[String] = None
        values.foreach { value =>
          if (error.isEmpty) {
            ComponentRepository.parseSpecs(value, cwd) match {
              case Left(err) => error = Some(err)
              case Right(xs) => parsed ++= xs
            }
          }
        }
        val specsResult =
          error match {
            case Some(err) => Left(err)
            case None => Right(parsed.result())
          }
        specsResult
    }

  private def originForSpec(
    spec: ComponentRepository.Specification
  ): ComponentOrigin =
    spec match {
      case _: ComponentRepository.ComponentDirRepository.Specification =>
        ComponentOrigin.Repository("component-dir")
      case _: ComponentRepository.ScalaCliRepository.Specification =>
        ComponentOrigin.Repository("scala-cli")
    }

  // CncfRuntime
  def extractRepositoryArgs(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): ExtractedArgs = {
    val residual = Vector.newBuilder[String]
    val active = Vector.newBuilder[String]
    val search = Vector.newBuilder[String]
    search ++= _config_search_repository_specs(configuration)
    active ++= _config_active_repository_specs(configuration)
    var noDefault = false
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg == _no_default_components_flag) {
        noDefault = true
        i += 1
      } else if (arg.startsWith("--repository-dir=")) {
        search += s"component-dir:${arg.stripPrefix("--repository-dir=")}"
        i += 1
      } else if (arg == "--repository-dir") {
        if (i + 1 >= args.length) {
          return ExtractedArgs(Left("--repository-dir requires a value"), Right(Vector.empty), args, noDefault)
        }
        search += s"component-dir:${args(i + 1)}"
        i += 2
      } else if (arg.startsWith("--component-dir=")) {
        active += s"component-dir:${arg.stripPrefix("--component-dir=")}"
        i += 1
      } else if (arg == "--component-dir") {
        if (i + 1 >= args.length) {
          return ExtractedArgs(Right(Vector.empty), Left("--component-dir requires a value"), args, noDefault)
        }
        active += s"component-dir:${args(i + 1)}"
        i += 2
      } else {
        residual += arg
        i += 1
      }
    }
    ExtractedArgs(
      active = Right(active.result()),
      search = Right(search.result()),
      residual = residual.result().toArray,
      noDefault = noDefault
    )
  }

  // Legacy
  def extractArgs(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Either[String, Vector[String]], Array[String], Boolean) = {
    val extracted = extractRepositoryArgs(configuration, args)
    (extracted.search, extracted.residual, extracted.noDefault)
  }

  private val _no_default_components_flag = "--no-default-components"

  private def _config_search_repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[String] =
    configuration.get[String](RuntimeConfig.RepositoryDirKey) match {
      case Consequence.Success(Some(value)) =>
        value
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .toVector
      case _ => Vector.empty
    }

  private def _config_active_repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[String] =
    configuration.get[String](RuntimeConfig.ComponentDirKey) match {
      case Consequence.Success(Some(value)) =>
        value
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(v => if (v.startsWith("component-dir:") || v.contains(":")) v else s"component-dir:${v}")
          .toVector
      case _ => Vector.empty
    }

  def appendDefaultSearchRepositories(
    result: Either[String, Vector[ComponentRepository.Specification]],
    active: Vector[ComponentRepository.Specification],
    cwd: Path,
    noDefault: Boolean
  ): Either[String, Vector[ComponentRepository.Specification]] =
    result match {
      case left @ Left(_) => left
      case Right(specs) if noDefault => Right(specs)
      case Right(specs) =>
        val merged = active.foldLeft(specs) { (z, spec) =>
          _append_spec_if_missing(z, spec)
        }
        _default_repository_dir(cwd) match {
          case Some(dir) =>
            _append_default_standard_repository(merged, Some(dir))
          case None =>
            _append_default_standard_repository(merged, None)
        }
    }

  def appendDefaultActiveRepositories(
    result: Either[String, Vector[ComponentRepository.Specification]],
    cwd: Path,
    noDefault: Boolean
  ): Either[String, Vector[ComponentRepository.Specification]] =
    result match {
      case left @ Left(_) => left
      case Right(specs) if noDefault => Right(specs)
      case Right(specs) =>
        val defaults = Vector(_default_component_dir(cwd), _default_car_dir(cwd), _default_sar_dir(cwd)).flatten
        Right(defaults.foldLeft(specs) { (z, dir) =>
          _append_spec_if_missing(z, ComponentRepository.ComponentDirRepository.Specification(dir))
        })
    }

  private def _default_component_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("component.d").normalize
    if (Files.isDirectory(dir)) Some(dir) else None
  }

  private def _default_repository_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("repository.d").normalize
    if (Files.isDirectory(dir)) Some(dir) else None
  }

  private def _default_standard_repository_dir(): Option[Path] = {
    val dir = ComponentRepository.defaultStandardRepositoryDir()
    if (Files.isDirectory(dir)) Some(dir) else None
  }

  private def _default_car_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("car.d").normalize
    if (Files.isDirectory(dir)) Some(dir) else None
  }

  private def _default_sar_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("sar.d").normalize
    if (Files.isDirectory(dir)) Some(dir) else None
  }

  private def _has_default_components_spec(
    specs: Vector[ComponentRepository.Specification],
    dir: Path
  ): Boolean =
    specs.exists {
      case ComponentRepository.ComponentDirRepository.Specification(base) =>
        base.normalize == dir.normalize
      case _ => false
    }

  private def _append_spec_if_missing(
    specs: Vector[ComponentRepository.Specification],
    spec: ComponentRepository.Specification
  ): Vector[ComponentRepository.Specification] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(base) =>
        if (_has_default_components_spec(specs, base)) specs else specs :+ spec
      case _ =>
        if (specs.contains(spec)) specs else specs :+ spec
    }

  private def _append_default_standard_repository(
    specs: Vector[ComponentRepository.Specification],
    existingSearchDir: Option[Path]
  ): Either[String, Vector[ComponentRepository.Specification]] = {
    val withSearch =
      existingSearchDir match {
        case Some(dir) =>
          _append_spec_if_missing(specs, ComponentRepository.ComponentDirRepository.Specification(dir))
        case None =>
          specs
      }
    _default_standard_repository_dir() match {
      case Some(dir) =>
        Right(_append_spec_if_missing(withSearch, ComponentRepository.ComponentDirRepository.Specification(dir)))
      case None =>
        Right(withSearch)
    }
  }

  def component_extra_function(
    specs: Seq[ComponentRepository.Specification]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => build(subsystem, specs, Vector.empty).discover()
}
