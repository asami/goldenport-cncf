package org.goldenport.cncf.component.repository

import java.net.URLClassLoader
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.lang.reflect.Modifier
import java.util.ServiceLoader
import java.util.jar.JarFile
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.protocol.Protocol
import org.goldenport.provisional.observation.{Observation, ObservationRender, Taxonomy}
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.observability.global.{GlobalObservable, ObservabilityScopeDefaults, PersistentBootstrapLog}
import org.goldenport.cncf.component.*
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.backend.collaborator.{CollaboratorClassLoader, CollaboratorFactory}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * @since   Jan. 12, 2026
 *  version Jan. 29, 2026
 *  version Feb.  5, 2026
 *  version Mar. 22, 2026
 *  version Apr. 25, 2026
 * @version May.  1, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class ComponentRepository {
  def discover(): Seq[Component]
}

object ComponentRepository extends GlobalObservable {
  private val _scala_cli_type = "scala-cli"
  private val _component_dir_type = "component-dir"
  private val _component_file_type = "component-file"
  private val _component_dev_dir_type = "component-dev-dir"
  private val _subsystem_dev_dir_type = "subsystem-dev-dir"
  private val _scala_cli_default_dir = ".scala-build"
  private val _component_dir_default_dir = "component.dir"
  private val _standard_repository_group_path = Paths.get("org", "simplemodeling", "car")
  private val _standard_subsystem_group_path = Paths.get("org", "simplemodeling", "sar")

  sealed abstract class Specification {
    def build(params: ComponentCreate): ComponentRepository
    def resolveSubsystemDescriptor(
      subsystemName: String
    ): Option[GenericSubsystemDescriptor] = None
    def resolveComponentDescriptor(
      componentName: String
    ): Option[ComponentDescriptor] = None
    def resolveComponentArchivePath(
      componentName: String
    ): Option[Path] = None
  }

  def parseSpecs(
    input: String,
    baseDir: Path
  ): Either[String, Vector[Specification]] = {
    val trimmed = input.trim
    if (trimmed.isEmpty) {
      Right(Vector.empty)
    } else {
      val parts = trimmed.split(",").toVector.map(_.trim).filter(_.nonEmpty)
      val parsed = parts.map(p => _parse_spec(p, baseDir))
      val errors = parsed.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(errors.mkString("; "))
      } else {
        Right(parsed.collect { case Right(s) => s })
      }
    }
  }

  def resolvePackagePrefixes(): Vector[String] = {
    // TEMPORARY:
    // CNCF_DISCOVER_PREFIX is a transitional tuning knob.
    // It will be integrated into the unified Config mechanism
    // and removed from direct env access in a future revision.
    sys.env.get("CNCF_DISCOVER_PREFIX") match {
      case Some(value) =>
        value
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .toVector
      case None =>
        Vector.empty
    }
  }

  def defaultStandardRepositoryDir(): Path =
    Paths.get(sys.props.getOrElse("user.home", "."), ".cncf", "repository").normalize

  private def _parse_spec(
    spec: String,
    baseDir: Path
  ): Either[String, Specification] = {
    val (kind, dirOpt) = _split_spec(spec)
    kind match {
      case `_scala_cli_type` =>
        val dir = _resolve_dir(dirOpt, _scala_cli_default_dir, baseDir)
        Right(ScalaCliRepository.Specification(dir))
      case `_component_dir_type` =>
        val dir = _resolve_dir(dirOpt, _component_dir_default_dir, baseDir)
        Right(ComponentDirRepository.Specification(dir))
      case `_component_file_type` =>
        dirOpt match {
          case Some(_) =>
            val file = _resolve_dir(dirOpt, "", baseDir)
            Right(ComponentFileRepository.Specification(file))
          case None =>
            Left("component-file repository requires a CAR path")
        }
      case `_component_dev_dir_type` =>
        val dir = _resolve_dir(dirOpt, ".", baseDir)
        Right(ComponentDevDirRepository.Specification(dir))
      case `_subsystem_dev_dir_type` =>
        val dir = _resolve_dir(dirOpt, ".", baseDir)
        Right(SubsystemDevDirRepository.Specification(dir))
      case other =>
        Left(s"unknown component repository type: ${other}")
    }
  }

  private def _split_spec(
    spec: String
  ): (String, Option[String]) = {
    val idx = spec.indexOf(':')
    if (idx < 0) {
      (spec, None)
    } else {
      val kind = spec.substring(0, idx)
      val dir = spec.substring(idx + 1)
      val normalized = if (dir.isEmpty) None else Some(dir)
      (kind, normalized)
    }
  }

  private def _resolve_dir(
    dirOpt: Option[String],
    defaultDir: String,
    baseDir: Path
  ): Path = {
    val dir = dirOpt.getOrElse(defaultDir)
    val path = Paths.get(dir)
    if (path.isAbsolute) {
      path
    } else {
      baseDir.resolve(path).normalize
    }
  }

  final class ScalaCliRepository(
    baseDir: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    def discover(): Seq[Component] = {
      val log = PersistentBootstrapLog.forClass(classOf[ScalaCliRepository], ObservabilityScopeDefaults.Bootstrap)
      log.info(s"scala-cli repository baseDir=${baseDir}")
      val classDirs = _resolve_class_dirs()
      log.info(s"classDirs=${classDirs.mkString(",")}")
      if (classDirs.isEmpty) {
        Nil
      } else {
        val loader = _class_loader_from_paths(classDirs, getClass.getClassLoader)
        _discover_by_scan_ordered(loader, params, classDirs, packagePrefixes, log) match {
          case Consequence.Success(comps) => comps
          case Consequence.Failure(conclusion) =>
            log.warn(s"component discovery failed: ${conclusion.show}")
            Nil
        }
      }
    }

    private def _resolve_class_dirs(): Vector[Path] = {
      if (!Files.exists(baseDir)) {
        Vector.empty
      } else {
        val stream = Files.walk(baseDir)
        try {
          stream
            .iterator()
            .asScala
            .filter(p => Files.isDirectory(p))
            .filter(p => p.getFileName.toString == "classes")
            .toVector
        } finally {
          stream.close()
        }
      }
    }
  }
  object ScalaCliRepository {
    final case class Specification(
      baseDir: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository = {
        new ScalaCliRepository(
          baseDir = baseDir,
          params = params,
          packagePrefixes = ComponentRepository.resolvePackagePrefixes()
        )
      }
    }
  }

  final class ComponentDirRepository(
    baseDir: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    def discover(): Seq[Component] = {
      if (!Files.exists(baseDir)) {
        Nil
      } else {
        val log = PersistentBootstrapLog.forClass(classOf[ComponentDirRepository], ObservabilityScopeDefaults.Bootstrap)
        val origin = ComponentOrigin.Repository("component-dir")
        val artifacts = _requested_component_artifacts(baseDir, params)
        val components =
          if (artifacts.nonEmpty)
            artifacts.flatMap(_discover_artifact(_, params, origin, log))
          else
            _discover_from_artifacts(basedir = baseDir, params = params, origin = origin, log = log)
        if (components.nonEmpty) {
          components
        } else {
          log.info("component-dir contains no valid demo JAR components")
          Nil
        }
      }
    }
  }
  object ComponentDirRepository {
    final case class Specification(
      baseDir: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository = {
        new ComponentDirRepository(
          baseDir = baseDir,
          params = params,
          packagePrefixes = ComponentRepository.resolvePackagePrefixes()
        )
      }

      override def resolveSubsystemDescriptor(
        subsystemName: String
      ): Option[GenericSubsystemDescriptor] =
        ComponentRepository.resolveSubsystemDescriptorFromComponentDir(baseDir, subsystemName)

      override def resolveComponentDescriptor(
        componentName: String
      ): Option[ComponentDescriptor] =
        ComponentRepository.resolveComponentDescriptorFromComponentDir(baseDir, componentName)

      override def resolveComponentArchivePath(
        componentName: String
      ): Option[Path] =
        ComponentRepository.resolveComponentArchivePathFromComponentDir(baseDir, componentName)
    }
  }

  final class ComponentFileRepository(
    file: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    def discover(): Seq[Component] = {
      if (!Files.isRegularFile(file)) {
        Nil
      } else {
        val log = PersistentBootstrapLog.forClass(classOf[ComponentFileRepository], ObservabilityScopeDefaults.Bootstrap)
        val origin = ComponentOrigin.Repository("component-file")
        val artifact = Artifact(file, ArtifactKind.Car)
        _discover_artifact(artifact, params, origin, log)
      }
    }
  }

  object ComponentFileRepository {
    final case class Specification(
      file: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository =
        new ComponentFileRepository(
          file = file,
          params = params,
          packagePrefixes = ComponentRepository.resolvePackagePrefixes()
        )

      override def resolveComponentDescriptor(
        componentName: String
      ): Option[ComponentDescriptor] =
        if (Files.isRegularFile(file))
          ComponentDescriptorLoader.loadArchive(file).toOption.filter(_matches_component_descriptor(_, componentName))
        else
          None

      override def resolveComponentArchivePath(
        componentName: String
      ): Option[Path] =
        resolveComponentDescriptor(componentName).map(_ => file)
    }
  }

  final class ComponentDevDirRepository(
    baseDir: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    def discover(): Seq[Component] = {
      val log = PersistentBootstrapLog.forClass(classOf[ComponentDevDirRepository], ObservabilityScopeDefaults.Bootstrap)
      val classpath = _dev_runtime_classpath(baseDir)
      if (classpath.isEmpty) {
        log.warn(s"[component-dev-dir] runtime classpath file missing or empty under ${baseDir}")
        Vector.empty
      } else {
        val classDirs = classpath.filter(Files.isDirectory(_))
        if (classDirs.isEmpty) {
          log.warn(s"[component-dev-dir] runtime classpath contains no class directories: ${baseDir}")
          Vector.empty
        } else {
          val loader = _class_loader_from_paths(classpath, getClass.getClassLoader)
          _discover_components(
            loader,
            params,
            classDirs,
            packagePrefixes,
            ComponentOrigin.Repository("component-dev-dir"),
            log,
            tolerant = true
          ) match {
            case Consequence.Success(components) =>
              components.map(_.withArtifactMetadata(_dev_artifact_metadata(baseDir)))
            case Consequence.Failure(conclusion) =>
              log.warn(s"[component-dev-dir] discovery failed cause=${conclusion.show}")
              Vector.empty
          }
        }
      }
    }

    private def _dev_runtime_classpath(base: Path): Vector[Path] = {
      val file = base.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
      if (!Files.isRegularFile(file)) {
        Vector.empty
      } else {
        Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
          .map(_.trim)
          .filter(_.nonEmpty)
          .flatMap(_.split(java.util.regex.Pattern.quote(File.pathSeparator)).toVector)
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(p => Paths.get(p).toAbsolutePath.normalize)
          .filter(Files.exists(_))
          .distinct
      }
    }

    private def _dev_artifact_metadata(base: Path): Component.ArtifactMetadata = {
      val descriptor = _dev_component_descriptors(base).headOption
      Component.ArtifactMetadata(
        sourceType = "component-dev-dir",
        name = descriptor.flatMap(_.name).orElse(descriptor.flatMap(_.componentName)).getOrElse(base.getFileName.toString),
        version = descriptor.flatMap(_.version).getOrElse("0.1.0"),
        component = descriptor.flatMap(_.componentName),
        subsystem = descriptor.flatMap(_.subsystemName),
        archivePath = Some(base.toString),
        effectiveExtensions = descriptor.map(_.extensions).getOrElse(Map.empty),
        effectiveConfig = descriptor.map(_.config).getOrElse(Map.empty)
      )
    }

    private def _dev_component_descriptors(base: Path): Vector[ComponentDescriptor] =
      Vector(
        base.resolve("car.d"),
        base.resolve("src").resolve("main").resolve("car")
      ).flatMap { dir =>
        ComponentDescriptorLoader.load(dir) match {
          case Consequence.Success(xs) => xs
          case Consequence.Failure(_) => Vector.empty
        }
      }
  }

  object ComponentDevDirRepository {
    final case class Specification(
      baseDir: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository =
        new ComponentDevDirRepository(
          baseDir = baseDir,
          params = params,
          packagePrefixes = ComponentRepository.resolvePackagePrefixes()
        )
    }
  }

  final class SubsystemDevDirRepository(
    baseDir: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    def discover(): Seq[Component] = {
      val specs = _component_specs(baseDir)
      if (specs.isEmpty) {
        val log = PersistentBootstrapLog.forClass(classOf[SubsystemDevDirRepository], ObservabilityScopeDefaults.Bootstrap)
        log.warn(s"[subsystem-dev-dir] component development source not found under ${baseDir}")
        Vector.empty
      } else {
        val requested = _requested_component_names(params)
        val discovered = specs.flatMap(_.build(params).discover()).distinctBy(_.name)
        if (requested.isEmpty)
          discovered
        else
          discovered.filter(component => requested.contains(NamingConventions.toComparisonKey(component.name)))
      }
    }

    private def _requested_component_names(params: ComponentCreate): Set[String] =
      params.componentDescriptors.flatMap(d => d.componentName.orElse(d.name))
        .map(NamingConventions.toComparisonKey)
        .toSet

    private def _component_specs(base: Path): Vector[ComponentRepository.Specification] = {
      val componentdir = base.resolve("component").normalize
      if (!Files.isDirectory(componentdir)) {
        Vector.empty
      } else if (Files.isRegularFile(componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"))) {
        Vector(ComponentDevDirRepository.Specification(componentdir))
      } else {
        Vector(ComponentDirRepository.Specification(componentdir))
      }
    }
  }

  object SubsystemDevDirRepository {
    final case class Specification(
      baseDir: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository =
        new SubsystemDevDirRepository(
          baseDir = baseDir,
          params = params,
          packagePrefixes = ComponentRepository.resolvePackagePrefixes()
        )

      override def resolveSubsystemDescriptor(
        subsystemName: String
      ): Option[GenericSubsystemDescriptor] =
        GenericSubsystemDescriptor.load(baseDir).toOption
          .filter(_matches_subsystem_descriptor(_, subsystemName))

      override def resolveComponentDescriptor(
        componentName: String
      ): Option[ComponentDescriptor] =
        ComponentRepository.resolveComponentDescriptorFromComponentDir(baseDir.resolve("component").normalize, componentName)

      override def resolveComponentArchivePath(
        componentName: String
      ): Option[Path] =
        ComponentRepository.resolveComponentArchivePathFromComponentDir(baseDir.resolve("component").normalize, componentName)
    }
  }

  def resolveSubsystemDescriptor(
    specs: Seq[Specification],
    subsystemName: String
  ): Option[GenericSubsystemDescriptor] =
    specs.iterator.flatMap(_.resolveSubsystemDescriptor(subsystemName)).toSeq.headOption

  def resolveComponentDescriptor(
    specs: Seq[Specification],
    componentName: String
  ): Option[ComponentDescriptor] =
    specs.iterator.flatMap(_.resolveComponentDescriptor(componentName)).toSeq.headOption

  def resolveComponentArchivePath(
    specs: Seq[Specification],
    componentName: String
  ): Option[Path] =
    specs.iterator.flatMap(_.resolveComponentArchivePath(componentName)).toSeq.headOption

  def resolveSubsystemDescriptorFromComponentDir(
    baseDir: Path,
    subsystemName: String
  ): Option[GenericSubsystemDescriptor] = {
    if (!Files.isDirectory(baseDir)) {
      None
    } else {
      _list_artifacts(baseDir).iterator.flatMap {
        case Artifact(path, ArtifactKind.Sar) =>
          GenericSubsystemDescriptor.load(path).toOption
        case Artifact(path, ArtifactKind.SarDir) =>
          GenericSubsystemDescriptor.load(path).toOption
        case _ =>
          None
      }.find(_matches_subsystem_descriptor(_, subsystemName))
        .orElse(_resolve_standard_subsystem_descriptor(baseDir, subsystemName))
    }
  }

  def resolveComponentDescriptorFromComponentDir(
    baseDir: Path,
    componentName: String
  ): Option[ComponentDescriptor] = {
    if (!Files.isDirectory(baseDir)) {
      None
    } else {
      _list_artifacts(baseDir).iterator.flatMap {
        case Artifact(path, ArtifactKind.Car) =>
          ComponentDescriptorLoader.loadArchive(path).toOption
        case Artifact(path, ArtifactKind.CarDir) =>
          ComponentDescriptorLoader.load(path).toOption.flatMap(_.headOption)
        case _ =>
          None
      }.find(_matches_component_descriptor(_, componentName))
        .orElse(_resolve_standard_component_descriptor(baseDir, componentName))
    }
  }

  def resolveComponentArchivePathFromComponentDir(
    baseDir: Path,
    componentName: String
  ): Option[Path] = {
    if (!Files.isDirectory(baseDir)) {
      None
    } else {
      _list_artifacts(baseDir).iterator.flatMap {
        case Artifact(path, ArtifactKind.Car) =>
          ComponentDescriptorLoader.loadArchive(path).toOption
            .filter(_matches_component_descriptor(_, componentName))
            .map(_ => path)
        case _ =>
          None
      }.toSeq.headOption
        .orElse(_resolve_standard_component_artifact(baseDir, componentName, None).collect {
          case Artifact(path, ArtifactKind.Car) => path
        })
    }
  }

  private def _matches_subsystem_descriptor(
    descriptor: GenericSubsystemDescriptor,
    subsystemName: String
  ): Boolean = {
    val requested = subsystemName.trim
    val versionedName =
      descriptor.version.map(v => s"${descriptor.subsystemName}-${v}")
    descriptor.subsystemName == requested ||
      versionedName.contains(requested) ||
      descriptor.path.getFileName.toString.stripSuffix(".sar").stripSuffix(".zip") == requested
  }

  private def _matches_component_descriptor(
    descriptor: ComponentDescriptor,
    componentName: String
  ): Boolean = {
    val requested = componentName.trim
    val names = Vector(
      descriptor.componentName,
      descriptor.name
    ).flatten ++ descriptor.componentlets.map(_.name)
    names.contains(requested)
  }

  private def _class_loader_from_paths(
    paths: Seq[Path],
    parent: ClassLoader
  ): URLClassLoader = {
    val urls = paths.map(_.toUri.toURL).toArray
    new URLClassLoader(urls, parent)
  }

  private def _discover_service_loader(
    loader: URLClassLoader,
    params: ComponentCreate,
    origin: ComponentOrigin
  ): Vector[Component] = {
    val withOrigin = params.withOrigin(origin)
    val components =
      ServiceLoader.load(classOf[Component], loader).iterator.asScala.toVector
    val factories =
      ServiceLoader
        .load(classOf[Component.BundleFactory], loader)
        .iterator
        .asScala
        .toVector
    val fromFactories = factories.flatMap(_.create(withOrigin).participants)
    val direct = components.map(_initialize_component(withOrigin))
    if (fromFactories.nonEmpty)
      fromFactories ++ direct.filterNot(d => fromFactories.exists(f => NamingConventions.equivalentByNormalized(f.name, d.name)))
    else
      direct
  }

  private def _discover_from_artifacts(
    basedir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] = {
    val artifacts = _list_artifacts(basedir)
    artifacts.flatMap { artifact =>
      artifact.kind match {
        case ArtifactKind.Jar => _discover_component_from_jar(artifact.path, params, origin, log)
        case ArtifactKind.Car => _discover_component_from_car(artifact.path, params, origin, log)
        case ArtifactKind.CarDir => _discover_component_from_car_dir(artifact.path, params, origin, log)
        case ArtifactKind.Sar => _discover_component_from_sar(artifact.path, params, origin, log)
        case ArtifactKind.SarDir => _discover_component_from_sar_dir(artifact.path, params, origin, log)
      }
    }
  }

  private def _list_artifacts(basedir: Path): Vector[Artifact] = {
    if (!Files.exists(basedir)) {
      Vector.empty
    } else if (_looks_like_sar_dir(basedir)) {
      Vector(Artifact(basedir, ArtifactKind.SarDir))
    } else if (_looks_like_car_dir(basedir)) {
      Vector(Artifact(basedir, ArtifactKind.CarDir))
    } else {
      val stream = Files.list(basedir)
      try {
        stream
          .iterator()
          .asScala
          .flatMap { p =>
            val fileName = p.getFileName.toString
            if (Files.isRegularFile(p)) {
              if (fileName.endsWith(".car")) {
                Some(Artifact(p, ArtifactKind.Car))
              } else if (fileName.endsWith(".sar")) {
                Some(Artifact(p, ArtifactKind.Sar))
              } else if (fileName.endsWith(".jar")) {
                Some(Artifact(p, ArtifactKind.Jar))
              } else {
                None
              }
            } else if (Files.isDirectory(p) && _looks_like_sar_dir(p)) {
              Some(Artifact(p, ArtifactKind.SarDir))
            } else if (Files.isDirectory(p) && _looks_like_car_dir(p)) {
              Some(Artifact(p, ArtifactKind.CarDir))
            } else {
              None
            }
          }
          .toVector
          .sortBy(_.path.toString)
      } finally {
        stream.close()
      }
    }
  }

  private def _requested_component_artifacts(
    basedir: Path,
    params: ComponentCreate
  ): Vector[Artifact] = {
    val requests =
      params.componentDescriptors.flatMap { d =>
        d.componentName.orElse(d.name).map(n => (n, d.version))
      }.distinct
    requests.flatMap { case (name, version) =>
      _resolve_requested_component_artifact(basedir, name, version)
    }.distinct
  }

  private def _resolve_requested_component_artifact(
    basedir: Path,
    componentName: String,
    version: Option[String]
  ): Vector[Artifact] = {
    val flat = _list_matching_artifacts(basedir, componentName, version)
    if (flat.nonEmpty)
      flat
    else
      _resolve_standard_component_artifact(basedir, componentName, version).toVector
  }

  private def _list_matching_artifacts(
    basedir: Path,
    componentName: String,
    version: Option[String]
  ): Vector[Artifact] = {
    val prefix = version.map(v => s"${componentName}-${v}").getOrElse(componentName)
    _list_artifacts(basedir).filter { artifact =>
      val filename = artifact.path.getFileName.toString
      artifact.kind match {
        case ArtifactKind.Car | ArtifactKind.CarDir =>
          filename == s"${componentName}.car" ||
          filename == s"${componentName}.zip" ||
          filename.startsWith(prefix)
        case _ =>
          false
      }
    }
  }

  private def _resolve_standard_component_descriptor(
    basedir: Path,
    componentName: String
  ): Option[ComponentDescriptor] =
    _resolve_standard_component_artifact(basedir, componentName, None).iterator.flatMap {
      case Artifact(path, ArtifactKind.Car) =>
        ComponentDescriptorLoader.loadArchive(path).toOption
      case Artifact(path, ArtifactKind.CarDir) =>
        ComponentDescriptorLoader.load(path).toOption.flatMap(_.headOption)
      case _ =>
        None
    }.toSeq.headOption

  private def _resolve_standard_component_artifact(
    basedir: Path,
    componentName: String,
    version: Option[String]
  ): Option[Artifact] = {
    val componentRoot = basedir.resolve(_standard_repository_group_path).resolve(componentName)
    if (!Files.isDirectory(componentRoot)) {
      None
    } else {
      val versions =
        version.map(v => Vector(v)).getOrElse(_version_dirs_desc(componentRoot))
      versions.iterator.flatMap { v =>
        val artifact = componentRoot.resolve(v).resolve(s"${componentName}-${v}.car")
        if (Files.isRegularFile(artifact)) Some(Artifact(artifact, ArtifactKind.Car)) else None
      }.toSeq.headOption
    }
  }

  private def _resolve_standard_subsystem_descriptor(
    basedir: Path,
    subsystemName: String
  ): Option[GenericSubsystemDescriptor] =
    _resolve_standard_subsystem_artifact(basedir, subsystemName).flatMap {
      case Artifact(path, ArtifactKind.Sar) => GenericSubsystemDescriptor.load(path).toOption
      case Artifact(path, ArtifactKind.SarDir) => GenericSubsystemDescriptor.load(path).toOption
      case _ => None
    }

  private def _resolve_standard_subsystem_artifact(
    basedir: Path,
    subsystemName: String
  ): Option[Artifact] = {
    val subsystemRoot = basedir.resolve(_standard_subsystem_group_path).resolve(subsystemName)
    if (!Files.isDirectory(subsystemRoot)) {
      None
    } else {
      _version_dirs_desc(subsystemRoot).iterator.flatMap { v =>
        val artifact = subsystemRoot.resolve(v).resolve(s"${subsystemName}-${v}.sar")
        if (Files.isRegularFile(artifact)) Some(Artifact(artifact, ArtifactKind.Sar)) else None
      }.toSeq.headOption
    }
  }

  private def _version_dirs_desc(root: Path): Vector[String] = {
    val stream = Files.list(root)
    try {
      stream.iterator.asScala
        .filter(Files.isDirectory(_))
        .map(_.getFileName.toString)
        .toVector
        .sorted(Ordering[String].reverse)
    } finally {
      stream.close()
    }
  }

  private def _discover_artifact(
    artifact: Artifact,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    artifact.kind match {
      case ArtifactKind.Jar => _discover_component_from_jar(artifact.path, params, origin, log)
      case ArtifactKind.Car => _discover_component_from_car(artifact.path, params, origin, log)
      case ArtifactKind.CarDir => _discover_component_from_car_dir(artifact.path, params, origin, log)
      case ArtifactKind.Sar => _discover_component_from_sar(artifact.path, params, origin, log)
      case ArtifactKind.SarDir => _discover_component_from_sar_dir(artifact.path, params, origin, log)
    }

  private enum ArtifactKind {
    case Jar
    case Car
    case CarDir
    case Sar
    case SarDir
  }

  private case class Artifact(
    path: Path,
    kind: ArtifactKind
  )

  private def _discover_component_from_jar(
    jarpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    _discover_component_from_artifact(
      artifactname = jarpath.getFileName.toString,
      loaderclasspath = Seq(jarpath),
      scanclasspath = Seq(jarpath),
      params = params,
      origin = origin,
      log = log
    )

  private def _discover_component_from_car(
    carpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sarDescriptor: Option[GenericSubsystemDescriptor] = None
  ): Seq[Component] = {
    val areas = GlobalContext.globalContext.workAreaSpace
    CarExtractor.withExtracted(carpath, areas) { extracted =>
      _discover_component_from_car_common(
        extracted = extracted,
        artifactPath = carpath,
        params = params,
        origin = origin,
        log = log,
        sarDescriptor = sarDescriptor,
        sourceType = sarDescriptor.map(_ => "sar+car").getOrElse("car")
      )
    } match {
      case Consequence.Success(components) => components
      case Consequence.Failure(conclusion) =>
        log.warn(s"[component-dir] car=${carpath.getFileName} invalid car cause=${conclusion.show}")
        Vector.empty
    }
  }

  private def _discover_component_from_car_dir(
    cardir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sarDescriptor: Option[GenericSubsystemDescriptor] = None
  ): Seq[Component] =
    CarExtractor.resolveDirectory(cardir) match {
      case Consequence.Success(extracted) =>
        _discover_component_from_car_common(
          extracted = extracted,
          artifactPath = cardir,
          params = params,
          origin = origin,
          log = log,
          sarDescriptor = sarDescriptor,
          sourceType = sarDescriptor.map(_ => "sar+car-dir").getOrElse("car-dir")
        ) match {
          case Consequence.Success(components) => components
          case Consequence.Failure(conclusion) =>
            log.warn(s"[component-dir] car-dir=${cardir.getFileName} invalid car-dir cause=${conclusion.show}")
            Vector.empty
        }
      case Consequence.Failure(conclusion) =>
        log.warn(s"[component-dir] car-dir=${cardir.getFileName} invalid car-dir cause=${conclusion.show}")
        Vector.empty
    }

  private def _discover_component_from_car_common(
    extracted: CarExtracted,
    artifactPath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sarDescriptor: Option[GenericSubsystemDescriptor],
    sourceType: String
  ): Consequence[Vector[Component]] = {
    val baseOrigin = _component_origin_for_archive(
      repositoryType = origin.label,
      carDescriptor = extracted.descriptor,
      sarDescriptor = sarDescriptor,
      fallback = origin
    )
    val (effectiveExtensions, effectiveConfig) =
      _effective_extensions_config(extracted.descriptor, sarDescriptor)
    val artifactMetadata = Component.ArtifactMetadata(
      sourceType = sourceType,
      name = extracted.descriptor.name.orElse(extracted.descriptor.componentName).getOrElse(artifactPath.getFileName.toString),
      version = extracted.descriptor.version.getOrElse("0.1.0"),
      component = extracted.descriptor.componentName,
      subsystem = sarDescriptor.map(_.subsystemName).orElse(extracted.descriptor.subsystemName),
      archivePath = Some(artifactPath.toString),
      effectiveExtensions = effectiveExtensions,
      effectiveConfig = effectiveConfig
    )
    Using.resource(_class_loader_from_paths(extracted.componentClasspath, getClass.getClassLoader)) { componentLoader =>
      val components0 =
        _discover_component_from_artifact_with_loader(
          artifactname = artifactPath.getFileName.toString,
          loader = componentLoader,
          // Scan only the component's main archive. Dependency jars may contain
          // demo or builtin components that must not be treated as packaged
          // component definitions for this CAR.
          scanclasspath = Vector(extracted.componentMain),
          params = params,
          origin = baseOrigin,
          log = log
        ).toVector
      val components = components0.map(_.withArtifactMetadata(artifactMetadata))
      val collaboratorComponents = components.collect {
        case comp: CollaboratorComponent => comp
      }
      if (collaboratorComponents.isEmpty) {
        Consequence.success(components)
      } else {
        extracted.collaboratorClasspath match {
          case Some(paths) if paths.nonEmpty =>
            Using.resource(CollaboratorClassLoader(paths)) { collaboratorLoader =>
              CollaboratorFactory.create(collaboratorLoader, paths) match {
                case Consequence.Success(collaborator) =>
                  collaboratorComponents.foreach(_.setCollaborator(collaborator))
                  Consequence.success(components)
                case Consequence.Failure(conclusion) =>
                  log.warn(s"[component-dir] artifact=${artifactPath.getFileName} collaborator init failed cause=${conclusion.show}")
                  Consequence.success(Vector.empty)
              }
            }
          case _ =>
            log.warn(s"[component-dir] artifact=${artifactPath.getFileName} collaborator classpath missing")
            Consequence.success(Vector.empty)
        }
      }
    }
  }

  private def _discover_component_from_sar(
    sarpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] = {
    val areas = GlobalContext.globalContext.workAreaSpace
    SarExtractor.withExtracted(sarpath, areas) { extracted =>
      _discover_component_from_sar_extracted(extracted, params, origin, log)
    } match {
      case Consequence.Success(components) => components
      case Consequence.Failure(conclusion) =>
        log.warn(s"[component-dir] sar=${sarpath.getFileName} invalid sar cause=${conclusion.show}")
        Vector.empty
    }
  }

  private def _discover_component_from_sar_dir(
    sardir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    SarExtractor.resolveDirectory(sardir) match {
      case Consequence.Success(extracted) =>
        _discover_component_from_sar_extracted(extracted, params, origin, log) match {
          case Consequence.Success(components) => components
          case Consequence.Failure(conclusion) =>
            log.warn(s"[component-dir] sar-dir=${sardir.getFileName} invalid sar-dir cause=${conclusion.show}")
            Vector.empty
        }
      case Consequence.Failure(conclusion) =>
        log.warn(s"[component-dir] sar-dir=${sardir.getFileName} invalid sar-dir cause=${conclusion.show}")
        Vector.empty
    }

  private def _discover_component_from_sar_extracted(
    extracted: SarExtracted,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    if (extracted.carArtifacts.isEmpty && extracted.carDirectories.isEmpty) {
      log.warn(s"[component-dir] sar=${extracted.root.getFileName} contains no embedded component artifact")
      Consequence.success(Vector.empty)
    } else {
      val fromCars = extracted.carArtifacts.sortBy(_.toString).toVector.flatMap { car =>
        _discover_component_from_car(
          carpath = car,
          params = params,
          origin = origin,
          log = log,
          sarDescriptor = Some(extracted.descriptor)
        )
      }
      val fromCarDirs = extracted.carDirectories.sortBy(_.toString).toVector.flatMap { cardir =>
        _discover_component_from_car_dir(
          cardir = cardir,
          params = params,
          origin = origin,
          log = log,
          sarDescriptor = Some(extracted.descriptor)
        )
      }
      Consequence.success((fromCars ++ fromCarDirs).distinctBy(_.name))
    }
  }

  private def _looks_like_sar_dir(
    p: Path
  ): Boolean =
    GenericSubsystemDescriptor.looksLikeArchiveDirectory(p)

  private def _looks_like_car_dir(
    p: Path
  ): Boolean =
    ComponentDescriptorLoader.looksLikeArchiveDirectory(p)

  private def _effective_extensions_config(
    carDescriptor: ComponentDescriptor,
    sarDescriptor: Option[GenericSubsystemDescriptor]
  ): (Map[String, String], Map[String, String]) = {
    val extensions = carDescriptor.extensions ++ sarDescriptor.map(_.extensions).getOrElse(Map.empty)
    val config = carDescriptor.config ++ sarDescriptor.map(_.config).getOrElse(Map.empty)
    (extensions, config)
  }

  private def _component_origin_for_archive(
    repositoryType: String,
    carDescriptor: ComponentDescriptor,
    sarDescriptor: Option[GenericSubsystemDescriptor],
    fallback: ComponentOrigin
  ): ComponentOrigin =
    fallback match {
      case ComponentOrigin.Repository(_) =>
        val carName = carDescriptor.name.orElse(carDescriptor.componentName).getOrElse("component")
        val carVersion = carDescriptor.version.getOrElse("0.1.0")
        val label = sarDescriptor match {
          case Some(sar) =>
            s"${repositoryType}:sar:${sar.subsystemName}:${sar.version.getOrElse("0.1.0")}:car:${carName}:${carVersion}"
          case None =>
            s"${repositoryType}:car:${carName}:${carVersion}"
        }
        ComponentOrigin.Repository(label)
      case other =>
        other
    }

  private def _discover_component_from_artifact(
    artifactname: String,
    loaderclasspath: Seq[Path],
    scanclasspath: Seq[Path],
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    Using.resource(_class_loader_from_paths(loaderclasspath, getClass.getClassLoader)) { loader =>
      _discover_component_from_artifact_with_loader(
        artifactname = artifactname,
        loader = loader,
        scanclasspath = scanclasspath,
        params = params,
        origin = origin,
        log = log
      )
    }

  private def _discover_component_from_artifact_with_loader(
    artifactname: String,
    loader: URLClassLoader,
    scanclasspath: Seq[Path],
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] = {
    val withOrigin = params.withOrigin(origin)
    val classNames = _jar_class_names_from_paths(scanclasspath)
    if (classNames.isEmpty) {
      log.warn(s"[component-dir] artifact=${artifactname} contains no class entries")
      Vector.empty
    } else {
      val factoryComponents = _instantiate_factory_components(
        loader = loader,
        classNames = classNames,
        params = params,
        origin = origin,
        log = log,
        artifactname = artifactname,
        repositoryType = _component_dir_type
      )
      if (factoryComponents.nonEmpty) {
        factoryComponents
      } else {
        _build_sources(classNames, loader, origin, log, tolerant = true) match {
          case Consequence.Success(sources) =>
            _provide_components(sources, withOrigin, log) match {
              case Consequence.Success(components) =>
                components.headOption match {
                  case Some(first) =>
                    val canonicalName =
                      params.componentDescriptors.headOption
                        .flatMap(x => x.componentName.orElse(x.name))
                        .getOrElse(first.core.name)
                    log.info(s"[component-dir] artifact=${artifactname} provides component=${canonicalName}")
                    Vector(first)
                  case None =>
                    log.warn(s"[component-dir] artifact=${artifactname} contains no valid components")
                    Vector.empty
                }
              case Consequence.Failure(conclusion) =>
                log.warn(s"[component-dir] component creation failed for artifact=${artifactname} cause=${conclusion.show}")
                Vector.empty
            }
          case Consequence.Failure(conclusion) =>
            log.warn(s"[component-dir] component discovery failed for artifact=${artifactname} cause=${conclusion.show}")
            Vector.empty
        }
      }
    }
  }

  private def _jar_class_names_from_paths(jars: Seq[Path]): Vector[String] =
    jars.flatMap(_jar_class_names).toVector

  private def _jar_class_names(jarpath: Path): Vector[String] = {
    Using.resource(new JarFile(jarpath.toFile)) { jar =>
      jar
        .entries()
        .asScala
        .filter(e => !e.isDirectory && e.getName.endsWith(".class"))
        .map(e => _class_name_from_entry(e.getName))
        .toVector
    }
  }

  private def _class_name_from_entry(entryname: String): String = {
    val withoutExtension = entryname.substring(0, entryname.length - ".class".length)
    withoutExtension.replace('/', '.')
  }

  private def _instantiate_factory_components(
    loader: URLClassLoader,
    classNames: Seq[String],
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    artifactname: String,
    repositoryType: String
  ): Seq[Component] = {
    _find_factory_class(
      loader = loader,
      classNames = classNames,
      artifactname = artifactname,
      repositoryType = repositoryType
    ) match {
      case Some(factoryClass) => _create_components_from_factory(factoryClass, params.withOrigin(origin), log)
      case None => Vector.empty
    }
  }

  private def _find_factory_class(
    loader: URLClassLoader,
    classNames: Seq[String],
    artifactname: String,
    repositoryType: String
  ): Option[Class[_ <: Component.BundleFactory]] = {
    val factories = classNames.view.flatMap { className =>
      _load_class(
        className = className,
        loader = loader,
        artifactname = artifactname,
        repositoryType = repositoryType
      ).flatMap { cls =>
        if (
          classOf[Component.BundleFactory].isAssignableFrom(cls) &&
          !cls.isInterface &&
          !Modifier.isAbstract(cls.getModifiers) &&
          !cls.getName.endsWith("$")
        ) {
          Some(cls.asInstanceOf[Class[_ <: Component.BundleFactory]])
        } else {
          None
        }
      }
    }.toVector
    factories.sortBy(_factory_priority).headOption
  }

  private def _factory_priority(
    factoryClass: Class[_ <: Component.BundleFactory]
  ): (Int, String) = {
    val name = factoryClass.getName
    val nestedPenalty = if (name.contains("$") || factoryClass.getEnclosingClass != null) 1 else 0
    (nestedPenalty, name)
  }

  private def _create_components_from_factory(
    factoryClass: Class[_ <: Component.BundleFactory],
    params: ComponentCreate,
    log: BootstrapLog
  ): Seq[Component] = {
    try {
      val factory = factoryClass.getDeclaredConstructor().newInstance().asInstanceOf[Component.BundleFactory]
      factory.create(params).participants
    } catch {
      case NonFatal(e) =>
        log.warn(s"component factory initialization failed for ${factoryClass.getName}: ${e.getMessage}")
        Vector.empty
    }
  }

  private def _load_class(
    className: String,
    loader: URLClassLoader,
    artifactname: String,
    repositoryType: String
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(className, false, loader))
    } catch {
      case e: ClassNotFoundException =>
        _observe_component_load_error(className, e, artifactname, repositoryType)
        None
      case e: NoClassDefFoundError =>
        _observe_component_load_error(className, e, artifactname, repositoryType)
        None
      case e: LinkageError =>
        _observe_component_load_error(className, e, artifactname, repositoryType)
        None
      case NonFatal(e) =>
        _observe_component_load_error(className, e, artifactname, repositoryType)
        None
    }
  }

  private def _observe_component_load_error(
    className: String,
    e: Throwable,
    artifactname: String,
    repositoryType: String
  ): Unit = {
    val taxonomy = e match {
      case _: ClassNotFoundException => Taxonomy.componentUnavailable
      case _: NoClassDefFoundError => Taxonomy.componentUnavailable
      case _: LinkageError => Taxonomy.componentInvalid
      case _ => Taxonomy.componentCorrupted
    }
    val observation = Observation.failure(
      taxonomy,
      Descriptor.Facet.ClassName(className),
      Descriptor.Facet.Artifact(artifactname),
      Descriptor.Facet.RepositoryType(repositoryType),
      Descriptor.Facet.Exception(e)
    )
    val message = ObservationRender.warnMessage(observation)
    observe_warn(s"[component-dir] ignored class load error $message")
  }

  private def _discover_by_scan(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String],
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    _discover_components(loader, params, classDirs, packagePrefixes, ComponentOrigin.Repository("component-dir"), log)
  }

  private def _discover_by_scan_ordered(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String],
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    val names = _discover_class_names(classDirs, packagePrefixes)
    val normalized = _normalize_class_names(names)
    log.info(s"normalizedCandidatesCount=${normalized.size}")
    log.info(s"normalizedCandidates=${normalized.mkString(",")}")
    names.foreach { name =>
      val variants = _normalize_class_names(Vector(name))
      log.info(s"candidateNormalize: ${name} -> ${variants.mkString(",")}")
    }
    _discover_components_with_names(
      loader,
      params,
      normalized,
      ComponentOrigin.Repository("scala-cli"),
      log,
      tolerant = true
    )
  }

  private def _discover_components(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String],
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean = false
  ): Consequence[Vector[Component]] = {
    val names = _discover_class_names(classDirs, packagePrefixes)
    _discover_components_with_names(
      loader,
      params,
      names,
      origin,
      log,
      tolerant = tolerant
    )
  }

  private def _discover_components_with_names(
    loader: URLClassLoader,
    params: ComponentCreate,
    classNames: Seq[String],
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean
  ): Consequence[Vector[Component]] = {
    val withOrigin = params.withOrigin(origin)
    for {
      sources <- _build_sources(classNames, loader, origin, log, tolerant)
      components <- _provide_components(sources, withOrigin, log)
    } yield components
  }

  private def _discover_class_names(
    classDirs: Seq[Path],
    packagePrefixes: Seq[String]
  ): Vector[String] = {
    val seen = mutable.Set.empty[String]
    classDirs.foreach { root =>
      _class_files(root).foreach { classFile =>
        val className = _class_name(root, classFile)
        val baseName = _base_class_name(className)
        if (_accept_class(baseName, packagePrefixes) && !seen.contains(baseName)) {
          seen += baseName
        }
      }
    }
    seen.toVector.sorted
  }

  private def _initialize_component(
    params: ComponentCreate
  )(
    comp: Component
  ): Component = {
    val core = Component.createScriptCore()
    comp.initialize(params.toInit(core))
    comp
  }

  private def _class_files(root: Path): Vector[Path] = {
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p))
          .filter(p => p.toString.endsWith(".class"))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _class_name(root: Path, classFile: Path): String = {
    val relative = root.relativize(classFile).toString
    val noExt =
      if (relative.endsWith(".class")) {
        relative.substring(0, relative.length - ".class".length)
      } else {
        relative
      }
    noExt.replace('/', '.').replace('\\', '.')
  }

  private def _normalize_class_names(
    names: Seq[String]
  ): Vector[String] = {
    val seen = mutable.Set.empty[String]
    val results = Vector.newBuilder[String]
    names.foreach { n =>
      val base = n.trim
      val stripped = base.stripPrefix("main.")
      val prefixed = if (base.startsWith("main.")) "" else s"main.${base}"
      val baseCandidates = Vector(base, stripped, prefixed).filter(_.nonEmpty)
      val withDollar = baseCandidates.map { c =>
        if (c.endsWith("$")) c else s"${c}$$"
      }
      val withoutDollar = baseCandidates.map(_.stripSuffix("$"))
      val candidates = baseCandidates ++ withDollar ++ withoutDollar
      candidates.foreach { c =>
        if (!seen.contains(c)) {
          seen += c
          results += c
        }
      }
    }
    results.result()
  }

  private def _base_class_name(name: String): String = {
    val base =
      if (name.endsWith("$")) {
        name.substring(0, name.length - 1)
      } else {
        name
      }
    val idx = base.indexOf('$')
    if (idx >= 0) {
      base.substring(0, idx)
    } else {
      base
    }
  }

  private def _accept_class(
    name: String,
    packagePrefixes: Seq[String]
  ): Boolean = {
    if (packagePrefixes.isEmpty) {
      true
    } else {
      packagePrefixes.exists(prefix => name.startsWith(prefix))
    }
  }

  private def _build_sources(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean
  ): Consequence[Vector[ComponentSource]] =
    classNames.foldLeft(Consequence.success(Vector.empty[ComponentSource])) { (result, className) =>
      result.flatMap { acc =>
        log.info(s"candidate class=${className}")
        ComponentFactory.build(Seq(className), loader, origin.label) match {
          case Consequence.Success(sources) =>
            sources.foreach {
              case ComponentSource.ClassDef(_, _) =>
                log.info(s"accepted component class=${className}")
            }
            Consequence.success(acc ++ sources)
          case Consequence.Failure(conclusion) =>
            log.warn(s"failed to build source: ${className} cause=${conclusion.show}")
            if (tolerant) {
              Consequence.success(acc)
            } else {
              Consequence.Failure(conclusion)
            }
        }
      }
    }

  private def _provide_components(
    sources: Seq[ComponentSource],
    params: ComponentCreate,
    log: BootstrapLog
  ): Consequence[Vector[Component]] =
    sources.foldLeft(Consequence.success(Vector.empty[Component])) { (result, source) =>
      result.flatMap { acc =>
        ComponentProvider.provide(source, params.subsystem, params.origin) match {
          case Consequence.Success(component) =>
            Consequence.success(acc :+ component)
          case Consequence.Failure(conclusion) =>
            log.warn(s"failed to instantiate component cause=${conclusion.show}")
            Consequence.Failure(conclusion)
        }
      }
    }
}

sealed trait ComponentSource {
  def origin: String
}

object ComponentSource {
  final case class ClassDef(
    componentClass: Class[_ <: Component],
    origin: String
  ) extends ComponentSource
}
