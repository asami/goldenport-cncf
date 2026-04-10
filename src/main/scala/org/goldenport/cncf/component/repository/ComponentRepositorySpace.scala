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
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositorySpace(
  private val _entries: Vector[ComponentRepositorySpace.Slot] = Vector.empty
) {
  def discover(): Vector[Component] =
    _entries.flatMap(_.repository.discover())
}

object ComponentRepositorySpace {
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
    val args = Array[String]()
    val (result, rest, nodefault) = ComponentRepositorySpace.extractArgs(configuration, args)
    ComponentRepositorySpace.resolveSpecifications(result, cwd, nodefault) match {
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
      case _ =>
        ComponentOrigin.Repository("component-repository")
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
      case _ =>
        ComponentOrigin.Repository("component-repository")
    }

  // CncfRuntime
  def extractArgs(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Either[String, Vector[String]], Array[String], Boolean) = {
    val buffer = Vector.newBuilder[String]
    val specs = Vector.newBuilder[String]
    specs ++= _config_component_repository_specs(configuration)
    var noDefault = false
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg == _no_default_components_flag) {
        noDefault = true
        i += 1
      } else if (arg.startsWith("--component-repository=")) {
        specs += arg.stripPrefix("--component-repository=")
        i += 1
      } else if (arg == "--component-repository") {
        if (i + 1 >= args.length) {
          return (Left("--component-repository requires a value"), args, noDefault)
        }
        specs += args(i + 1)
        i += 2
      } else {
        buffer += arg
        i += 1
      }
    }
    (Right(specs.result()), buffer.result().toArray, noDefault)
  }

  private val _no_default_components_flag = "--no-default-components"

  private def _config_component_repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[String] =
    configuration.get[String](RuntimeConfig.ComponentRepositoryKey) match {
      case Consequence.Success(Some(value)) =>
        value
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .toVector
      case _ => Vector.empty
    }

  def appendDefaultComponentRepository(
    result: Either[String, Vector[ComponentRepository.Specification]],
    cwd: Path,
    noDefault: Boolean
  ): Either[String, Vector[ComponentRepository.Specification]] =
    result match {
      case left @ Left(_) => left
      case Right(specs) if noDefault => Right(specs)
      case Right(specs) if specs.nonEmpty => Right(specs)
      case Right(specs) =>
        _default_components_dir(cwd) match {
          case Some(dir) if !_has_default_components_spec(specs, dir) =>
            Right(specs :+ ComponentRepository.ComponentDirRepository.Specification(dir))
          case _ => Right(specs)
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
        val defaults = Vector(_default_car_dir(cwd), _default_sar_dir(cwd)).flatten
        Right(defaults.foldLeft(specs) { (z, dir) =>
          if (_has_default_components_spec(z, dir)) z
          else z :+ ComponentRepository.ComponentDirRepository.Specification(dir)
        })
    }

  private def _default_components_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("component.d").normalize
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

  def component_extra_function(
    specs: Seq[ComponentRepository.Specification]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => build(subsystem, specs, Vector.empty).discover()
}
