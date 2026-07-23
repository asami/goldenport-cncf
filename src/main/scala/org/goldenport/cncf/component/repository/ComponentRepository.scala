package org.goldenport.cncf.component.repository

import java.net.{URI, URLClassLoader}
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, Paths, StandardCopyOption}
import java.lang.reflect.Modifier
import java.util.ServiceLoader
import java.util.jar.JarFile
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.Using
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.observation.Descriptor
import org.goldenport.protocol.Protocol
import org.goldenport.observation.{Observation, ObservationRender, Taxonomy}
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.observability.global.{GlobalObservable, ObservabilityScopeDefaults, PersistentBootstrapLog}
import org.goldenport.cncf.component.*
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.backend.collaborator.{CollaboratorClassLoader, CollaboratorFactory}
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Jan. 12, 2026
 *  version Jan. 29, 2026
 *  version Feb.  5, 2026
 *  version Mar. 22, 2026
 *  version Apr. 25, 2026
 *  version May. 25, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class ComponentRepository {
  private var _assembly_api_class_loader: Option[ClassLoader] = None

  def discover(): Seq[Component]

  private[repository] def prepareAssemblyApi(): Consequence[AssemblyApiMetadata] =
    Consequence.success(AssemblyApiMetadata())

  private[repository] def installAssemblyApiClassLoader(loader: ClassLoader): Unit =
    _assembly_api_class_loader = Some(loader)

  protected final def with_assembly_api_class_loader(params: ComponentCreate): ComponentCreate =
    _assembly_api_class_loader.map(params.withAssemblyApiClassLoader).getOrElse(params)
}

object ComponentRepository extends GlobalObservable {
  private val _scala_cli_type = "scala-cli"
  private val _component_dir_type = "component-dir"
  private val _component_file_type = "component-file"
  private val _component_dev_dir_type = "component-dev-dir"
  private val _invalid_component_dev_dir_type = "invalid-component-dev-dir"
  private val _subsystem_dev_dir_type = "subsystem-dev-dir"
  private val _standard_repository_type = "standard-repository"
  private val _scala_cli_default_dir = ".scala-build"
  private val _component_dir_default_dir = "component.dir"
  private val _standard_component_repository_url = "https://www.simplemodeling.org/repository/car"
  private val _standard_subsystem_repository_url = "https://www.simplemodeling.org/repository/sar"
  private val _standard_component_repository_path = Paths.get("car")
  private val _standard_subsystem_repository_path = Paths.get("sar")
  private val _legacy_standard_component_repository_path = Paths.get("org", "simplemodeling", "car")
  private val _legacy_standard_subsystem_repository_path = Paths.get("org", "simplemodeling", "sar")
  private val _remote_connect_timeout_ms = 2000
  private val _remote_read_timeout_ms = 5000

  def discoverAssembly(
    repositories: Vector[ComponentRepository],
    runtimeParent: ClassLoader = getClass.getClassLoader
  ): Vector[Component] =
    _required(discoverAssemblyC(repositories, runtimeParent))

  def discoverAssemblyC(
    repositories: Vector[ComponentRepository],
    runtimeParent: ClassLoader = getClass.getClassLoader
  ): Consequence[Vector[Component]] =
    _discover_assembly_c(repositories, repositories, runtimeParent)

  def discoverAssembly(
    assemblyRepositories: Vector[ComponentRepository],
    discoveryRepositories: Vector[ComponentRepository]
  ): Vector[Component] =
    _required(discoverAssemblyC(assemblyRepositories, discoveryRepositories))

  def discoverAssemblyC(
    assemblyRepositories: Vector[ComponentRepository],
    discoveryRepositories: Vector[ComponentRepository]
  ): Consequence[Vector[Component]] =
    _discover_assembly_c(assemblyRepositories, discoveryRepositories, getClass.getClassLoader)

  private def _discover_assembly_c(
    assemblyrepositories: Vector[ComponentRepository],
    discoveryrepositories: Vector[ComponentRepository],
    runtimeparent: ClassLoader
  ): Consequence[Vector[Component]] =
    try {
      val metadatac = assemblyrepositories.foldLeft(Consequence.success(AssemblyApiMetadata())) { (z, repository) =>
        for {
          acc <- z
          metadata <- repository.prepareAssemblyApi()
        } yield acc ++ metadata
      }
      metadatac.flatMap { metadata =>
        AssemblyApiClassLoader.create(runtimeparent, metadata).map { loader =>
          assemblyrepositories.foreach(_.installAssemblyApiClassLoader(loader))
          discoveryrepositories.flatMap(_.discover())
        }
      }
    } catch {
      case e: ComponentRepositoryDiscoveryFailure => Consequence.Failure(e.conclusion)
      case NonFatal(e) => Consequence.componentInvalid(e)
    }

  private def _required[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => Consequence.Failure[A](conclusion).RAISE
    }

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
    def resolveComponentArchivePath(
      componentName: String,
      version: Option[String]
    ): Option[Path] =
      resolveComponentArchivePath(componentName)
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
    Paths.get(sys.props.getOrElse("user.home", "."), ".cncf", "cache").normalize

  def defaultLocalRepositoryDir(): Path =
    Paths.get(sys.props.getOrElse("user.home", "."), ".cncf", "local").normalize

  def defaultLocalComponentRepositoryDir(): Path =
    defaultLocalRepositoryDir().resolve("repository").resolve("car")

  def defaultLocalSubsystemRepositoryDir(): Path =
    defaultLocalRepositoryDir().resolve("repository").resolve("sar")

  def standardComponentRepositoryUrl(): String =
    _standard_component_repository_url

  def standardSubsystemRepositoryUrl(): String =
    _standard_subsystem_repository_url

  def standardComponentRepositorySpec(): ComponentRepository.StandardRepository.Specification =
    StandardRepository.Specification(
      StandardRepositoryKind.Car,
      _standard_component_repository_url,
      defaultStandardRepositoryDir()
    )

  def standardSubsystemRepositorySpec(): ComponentRepository.StandardRepository.Specification =
    StandardRepository.Specification(
      StandardRepositoryKind.Sar,
      _standard_subsystem_repository_url,
      defaultStandardRepositoryDir()
    )

  private def _parse_spec(
    spec: String,
    basedir: Path
  ): Either[String, Specification] =
    _parse_standard_url_spec(spec).getOrElse {
    val (kind, diropt) = _split_spec(spec)
    kind match {
      case `_scala_cli_type` =>
        val dir = _resolve_dir(diropt, _scala_cli_default_dir, basedir)
        Right(ScalaCliRepository.Specification(dir))
      case `_component_dir_type` =>
        val dir = _resolve_dir(diropt, _component_dir_default_dir, basedir)
        Right(ComponentDirRepository.Specification(dir))
      case `_component_file_type` =>
        diropt match {
          case Some(_) =>
            val file = _resolve_dir(diropt, "", basedir)
            Right(ComponentFileRepository.Specification(file))
          case None =>
            Left("component-file repository requires a CAR path")
        }
      case `_component_dev_dir_type` =>
        val dir = _resolve_dir(diropt, ".", basedir)
        ComponentDevDirRepository.validate(dir).map(_ =>
          ComponentDevDirRepository.Specification(dir)
        )
      case `_invalid_component_dev_dir_type` =>
        Left("component development directory configuration must be a plain path or component-dev-dir:path; use component-dir/component-file settings for packaged CARs")
      case `_subsystem_dev_dir_type` =>
        val dir = _resolve_dir(diropt, ".", basedir)
        Right(SubsystemDevDirRepository.Specification(dir))
      case `_standard_repository_type` =>
        diropt match {
          case Some(value) =>
            _parse_standard_url_spec(value).map(_.left.map(_.replace("unsupported standard component repository URL", "unsupported standard repository URL")))
              .getOrElse(Left(s"standard-repository requires a URL"))
          case None =>
            Left("standard-repository requires a URL")
        }
      case other =>
        Left(s"unknown component repository type: ${other}")
    }
  }

  private def _parse_standard_url_spec(
    spec: String
  ): Option[Either[String, Specification]] = {
    val trimmed = spec.trim
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      None
    } else {
      val normalized = _strip_trailing_slash(trimmed)
      if (normalized == _standard_component_repository_url)
        Some(Right(standardComponentRepositorySpec()))
      else if (normalized == _standard_subsystem_repository_url)
        Some(Right(standardSubsystemRepositorySpec()))
      else
        Some(Left(s"unsupported standard component repository URL: ${trimmed}"))
    }
  }

  private def _strip_trailing_slash(
    value: String
  ): String =
    value.reverse.dropWhile(_ == '/').reverse

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
    diropt: Option[String],
    defaultdir: String,
    basedir: Path
  ): Path = {
    val dir = diropt.getOrElse(defaultdir)
    val path = Paths.get(dir)
    if (path.isAbsolute) {
      path
    } else {
      basedir.resolve(path).normalize
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
      val classdirs = _resolve_class_dirs()
      log.info(s"classdirs=${classdirs.mkString(",")}")
      if (classdirs.isEmpty) {
        Nil
      } else {
        val loader = _class_loader_from_paths(classdirs, getClass.getClassLoader)
        _discover_by_scan_ordered(loader, params, classdirs, packagePrefixes, log) match {
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
    packagePrefixes: Seq[String],
    releaseonly: Boolean = false
  ) extends ComponentRepository {
    override private[repository] def prepareAssemblyApi(): Consequence[AssemblyApiMetadata] = {
      val hasrequests = _requested_components(params).nonEmpty
      val requested = _requested_component_artifacts(baseDir, params, releaseonly)
      val artifacts = if (hasrequests) requested else _list_artifacts(baseDir)
      _load_assembly_api_artifacts(artifacts)
    }

    def discover(): Seq[Component] = {
      if (!Files.exists(baseDir)) {
        Nil
      } else {
        val effectiveparams = with_assembly_api_class_loader(params)
        val log = PersistentBootstrapLog.forClass(classOf[ComponentDirRepository], ObservabilityScopeDefaults.Bootstrap)
        val origin = ComponentOrigin.Repository("component-dir")
        val hasrequests = _requested_components(effectiveparams).nonEmpty
        val artifacts = _requested_component_artifacts(baseDir, effectiveparams, releaseonly)
        val components =
          if (hasrequests)
            artifacts.flatMap(_discover_artifact(_, effectiveparams, origin, log))
          else
            _discover_from_artifacts(basedir = baseDir, params = effectiveparams, origin = origin, log = log, releaseonly = releaseonly)
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

      override def resolveComponentArchivePath(
        componentname: String,
        version: Option[String]
      ): Option[Path] =
        ComponentRepository.resolveComponentArchivePathFromComponentDir(baseDir, componentname, version)
    }
  }

  final class ComponentFileRepository(
    file: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    override private[repository] def prepareAssemblyApi(): Consequence[AssemblyApiMetadata] =
      AssemblyApiClassLoader.loadCar(file)

    def discover(): Seq[Component] = {
      if (!Files.isRegularFile(file)) {
        Nil
      } else {
        val effectiveparams = with_assembly_api_class_loader(params)
        val log = PersistentBootstrapLog.forClass(classOf[ComponentFileRepository], ObservabilityScopeDefaults.Bootstrap)
        val origin = ComponentOrigin.Repository("component-file")
        val artifact = Artifact(file, ArtifactKind.Car)
        _discover_artifact(artifact, effectiveparams, origin, log)
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

      override def resolveSubsystemDescriptor(
        subsystemName: String
      ): Option[GenericSubsystemDescriptor] =
        if (Files.isRegularFile(file))
          GenericSubsystemDescriptor.loadComponentArchive(file).toOption.filter(_matches_subsystem_descriptor(_, subsystemName))
        else
          None

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

      override def resolveComponentArchivePath(
        componentname: String,
        version: Option[String]
      ): Option[Path] =
        if (Files.isRegularFile(file))
          ComponentDescriptorLoader.loadArchive(file).toOption
            .filter(_matches_component_descriptor(_, componentname, version))
            .map(_ => file)
        else
          None
    }
  }

  final class ComponentDevDirRepository(
    baseDir: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    override private[repository] def prepareAssemblyApi(): Consequence[AssemblyApiMetadata] =
      AssemblyApiClassLoader.loadDirectory(ComponentDevDirRepository.devComponentApiDirectory(baseDir))

    def discover(): Seq[Component] = {
      val log = PersistentBootstrapLog.forClass(classOf[ComponentDevDirRepository], ObservabilityScopeDefaults.Bootstrap)
      ComponentDevDirRepository.validate(baseDir) match {
        case Left(message) =>
          throw new IllegalStateException(message)
        case Right(_) =>
          ()
      }
      val classpath = ComponentDevDirRepository.devRuntimeClasspath(baseDir)
      {
        val classdirs = classpath.filter(Files.isDirectory(_))
        if (classdirs.isEmpty) {
          throw new IllegalStateException(ComponentDevDirRepository.noClassDirectoryMessage(baseDir))
        } else {
          val effectiveparams = with_assembly_api_class_loader(params)
          val origin = ComponentOrigin.Repository("component-dev-dir")
          val loader = ComponentLocalFirstClassLoader(
            classpath,
            effectiveparams.assemblyApiClassLoader.getOrElse(getClass.getClassLoader)
          )
          val classnames = _discover_class_names(classdirs, packagePrefixes)
          val factorycomponents = _instantiate_factory_components(
            loader = loader,
            classnames = classnames,
            params = effectiveparams,
            origin = origin,
            log = log,
            artifactname = baseDir.getFileName.toString,
            repositorytype = "component-dev-dir"
          )
          val discovered =
            if (factorycomponents.nonEmpty)
              Consequence.success(factorycomponents.toVector)
            else
              _discover_components(
                loader,
                effectiveparams,
                classdirs,
                packagePrefixes,
                origin,
                log,
                tolerant = true
              )
          discovered match {
            case Consequence.Success(components) =>
              components.map(component => component.withArtifactMetadata(_dev_artifact_metadata(baseDir, component)))
            case Consequence.Failure(conclusion) =>
              log.warn(s"[component-dev-dir] discovery failed cause=${conclusion.show}")
              Vector.empty
          }
        }
      }
    }

    private def _dev_artifact_metadata(
      base: Path,
      component: Component
    ): Component.ArtifactMetadata = {
      val componentname = component.core.name
      val descriptor = ComponentDevDirRepository.devComponentDescriptors(base).find { candidate =>
        _component_descriptor_names(candidate).exists(_matches_dev_component_name(_, componentname))
      }
      Component.ArtifactMetadata(
        sourceType = "component-dev-dir",
        name = descriptor.flatMap(_.name).orElse(descriptor.flatMap(_.componentName)).getOrElse(componentname),
        version = descriptor.flatMap(_.version).getOrElse("0.1.0"),
        component = descriptor.flatMap(_.componentName).orElse(Some(componentname)),
        subsystem = descriptor.flatMap(_.subsystemName),
        archivePath = Some(base.toString),
        effectiveExtensions = descriptor.map(_.extensions).getOrElse(Map.empty),
        effectiveConfig = descriptor.map(_.config).getOrElse(Map.empty)
      )
    }

    private def _matches_dev_component_name(
      descriptorname: String,
      runtimename: String
    ): Boolean = {
      val normalized = descriptorname.trim
      val withouttextus =
        if (normalized.startsWith("textus-")) normalized.stripPrefix("textus-")
        else if (normalized.startsWith("textus_")) normalized.stripPrefix("textus_")
        else normalized
      NamingConventions.equivalentByNormalized(normalized, runtimename) ||
        NamingConventions.equivalentByNormalized(withouttextus, runtimename)
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

      override def resolveComponentDescriptor(
        componentName: String
      ): Option[ComponentDescriptor] =
        ComponentDevDirRepository.devComponentDescriptors(baseDir)
          .find(_matches_component_descriptor(_, componentName))
          .orElse {
            ComponentDevDirRepository.inferComponentDescriptors(baseDir)
              .find(_matches_component_descriptor(_, componentName))
          }
    }

    def runtimeClasspathFile(base: Path): Path =
      base.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")

    def devComponentApiDirectory(base: Path): Path =
      base.resolve("target").resolve("cozy")

    def inferComponentDescriptors(base: Path): Vector[ComponentDescriptor] = {
      val log = PersistentBootstrapLog.forClass(classOf[ComponentDevDirRepository], ObservabilityScopeDefaults.Bootstrap)
      validate(base) match {
        case Left(_) =>
          Vector.empty
        case Right(_) =>
          val classpath = devRuntimeClasspath(base)
          val classdirs = classpath.filter(Files.isDirectory(_))
          if (classdirs.isEmpty) {
            Vector.empty
          } else {
            val subsystem = Subsystem(
              name = "component-dev-dir",
              configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
            )
            val params = ComponentCreate(subsystem, ComponentOrigin.Repository("component-dev-dir"))
            val loader = _class_loader_from_paths(classpath, getClass.getClassLoader)
            val origin = ComponentOrigin.Repository("component-dev-dir")
            val packageprefixes = ComponentRepository.resolvePackagePrefixes()
            val classnames = _discover_class_names(classdirs, packageprefixes)
            val factorycomponents = _instantiate_factory_components(
              loader = loader,
              classnames = classnames,
              params = params,
              origin = origin,
              log = log,
              artifactname = base.getFileName.toString,
              repositorytype = "component-dev-dir"
            )
            val components =
              if (factorycomponents.nonEmpty)
                factorycomponents.toVector
              else
                _discover_components(
                  loader,
                  params,
                  classdirs,
                  packageprefixes,
                  origin,
                  log,
                  tolerant = true
                ).toOption.getOrElse(Vector.empty)
            components
              .map { component =>
                val name = component.core.name
                ComponentDescriptor(
                  name = Some(name),
                  version = component.artifactMetadata.map(_.version).orElse(Some("0.1.0")),
                  componentName = Some(name)
                )
              }
              .distinctBy(x => x.componentName.orElse(x.name))
          }
      }
    }

    def inferComponentNames(base: Path): Vector[String] =
      inferComponentDescriptors(base)
        .flatMap(x => x.componentName.orElse(x.name))
        .distinct

    def devRuntimeClasspath(base: Path): Vector[Path] = {
      val file = runtimeClasspathFile(base)
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

    def validate(base: Path): Either[String, Unit] = {
      val file = runtimeClasspathFile(base)
      if (!Files.isDirectory(base))
        Left(s"[component-dev-dir] component development directory not found: ${base}")
      else if (!Files.isRegularFile(file))
        Left(missingRuntimeClasspathMessage(base))
      else if (Files.size(file) == 0L)
        Left(missingRuntimeClasspathMessage(base))
      else
        Right(())
    }

    def missingRuntimeClasspathMessage(base: Path): String = {
      val file = runtimeClasspathFile(base)
      s"[component-dev-dir] runtime classpath file is missing or empty: ${file}. " +
        s"Run '${base.resolve("scripts").resolve("update-runtime-classpath.sh")}' once for this development component, " +
        "then restart the application server. CNCF will not fall back to a packaged CAR while component-dev-dir is explicit."
    }

    def noClassDirectoryMessage(base: Path): String =
      s"[component-dev-dir] runtime classpath contains no class directories: ${runtimeClasspathFile(base)}. " +
        s"Run 'sbt --batch compile' in ${base}, then restart the application server."

    def devComponentDescriptors(base: Path): Vector[ComponentDescriptor] =
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

      override def resolveComponentArchivePath(
        componentname: String,
        version: Option[String]
      ): Option[Path] =
        ComponentRepository.resolveComponentArchivePathFromComponentDir(baseDir.resolve("component").normalize, componentname, version)
    }
  }

  enum StandardRepositoryKind {
    case Car
    case Sar
  }

  final class StandardRepository(
    kind: StandardRepositoryKind,
    baseUrl: String,
    cacheRoot: Path,
    params: ComponentCreate,
    packagePrefixes: Seq[String]
  ) extends ComponentRepository {
    override private[repository] def prepareAssemblyApi(): Consequence[AssemblyApiMetadata] = {
      _ensure_requested_component_artifacts()
      _load_assembly_api_artifacts(_requested_component_artifacts(cacheRoot, params, releaseonly = true))
    }

    def discover(): Seq[Component] = {
      _ensure_requested_component_artifacts()
      val repository = new ComponentDirRepository(
        cacheRoot,
        with_assembly_api_class_loader(params),
        packagePrefixes,
        releaseonly = true
      )
      repository.discover()
    }

    private def _ensure_requested_component_artifacts(): Unit =
      kind match {
        case StandardRepositoryKind.Car =>
          _requested_components(params).filterNot { case (name, version) =>
            version.exists(_is_snapshot_version) ||
            _satisfied_by_active_development_component(params, name)
          }.foreach { case (name, version) =>
            val resolved = _resolve_standard_component_artifact(cacheRoot, name, version, releaseonly = true)
              .orElse(_fetch_standard_component_artifact(baseUrl, cacheRoot, name, version))
            if (resolved.isEmpty) {
              val versiontext = version.getOrElse("latest")
              Consequence.resourceNotFound[Unit](
                s"requested component CAR not found: $name",
                Vector(
                  Descriptor.Facet.Component(name),
                  Descriptor.Facet.Artifact(s"$name:$versiontext"),
                  Descriptor.Facet.RepositoryType("standard-car"),
                  Descriptor.Facet.Properties(Map(
                    "repository" -> baseUrl,
                    "cache" -> cacheRoot.toString
                  ))
                )
              ).RAISE
            }
          }
        case StandardRepositoryKind.Sar =>
          ()
      }
  }

  object StandardRepository {
    final case class Specification(
      kind: StandardRepositoryKind,
      baseUrl: String,
      cacheRoot: Path
    ) extends ComponentRepository.Specification {
      def build(params: ComponentCreate): ComponentRepository =
        new StandardRepository(
          kind,
          baseUrl,
          cacheRoot,
          params,
          ComponentRepository.resolvePackagePrefixes()
        )

      override def resolveSubsystemDescriptor(
        subsystemName: String
      ): Option[GenericSubsystemDescriptor] =
        kind match {
          case StandardRepositoryKind.Sar =>
            _resolve_standard_subsystem_descriptor(cacheRoot, subsystemName, releaseonly = true)
              .orElse(_fetch_standard_subsystem_descriptor(baseUrl, cacheRoot, subsystemName))
          case _ =>
            None
        }

      override def resolveComponentDescriptor(
        componentName: String
      ): Option[ComponentDescriptor] =
        kind match {
          case StandardRepositoryKind.Car =>
            _resolve_standard_component_descriptor(cacheRoot, componentName, releaseonly = true)
              .orElse(_fetch_standard_component_descriptor(baseUrl, cacheRoot, componentName))
          case _ =>
            None
        }

      override def resolveComponentArchivePath(
        componentName: String
      ): Option[Path] =
        resolveComponentArchivePath(componentName, None)

      override def resolveComponentArchivePath(
        componentName: String,
        version: Option[String]
      ): Option[Path] =
        kind match {
          case StandardRepositoryKind.Car =>
            _resolve_standard_component_artifact(cacheRoot, componentName, version, releaseonly = true)
              .orElse(_fetch_standard_component_artifact(baseUrl, cacheRoot, componentName, version))
              .collect { case Artifact(path, ArtifactKind.Car) => path }
          case _ =>
            None
        }
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

  private[cncf] def descriptorsForSpecification(
    spec: Specification,
    previousspecs: Seq[Specification],
    descriptors: Vector[ComponentDescriptor]
  ): Vector[ComponentDescriptor] = {
    val unresolved = descriptors.filterNot(_is_descriptor_satisfied_by_specs(_, previousspecs))
    spec match {
      case _: ComponentFileRepository.Specification =>
        unresolved.filter(_is_descriptor_satisfied_by_specs(_, Seq(spec)))
      case _ =>
        unresolved
    }
  }

  private[cncf] def unresolvedDescriptorsForSearch(
    previousspecs: Seq[Specification],
    descriptors: Vector[ComponentDescriptor]
  ): Vector[ComponentDescriptor] =
    descriptors.filterNot(_is_descriptor_satisfied_by_specs(_, previousspecs))

  private def _is_descriptor_satisfied_by_specs(
    descriptor: ComponentDescriptor,
    specs: Seq[Specification]
  ): Boolean =
    _component_descriptor_names(descriptor).exists { name =>
      specs.filterNot(_.isInstanceOf[StandardRepository.Specification]).exists { spec =>
        spec.resolveComponentDescriptor(name).exists(_matches_component_descriptor(_, name, descriptor.version)) ||
          spec.resolveComponentArchivePath(name, descriptor.version).nonEmpty
      }
    }

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
  ): Option[Path] =
    resolveComponentArchivePathFromComponentDir(baseDir, componentName, None)

  def resolveComponentArchivePathFromComponentDir(
    baseDir: Path,
    componentName: String,
    version: Option[String]
  ): Option[Path] = {
    if (!Files.isDirectory(baseDir)) {
      None
    } else {
      _list_artifacts(baseDir).iterator.flatMap {
        case Artifact(path, ArtifactKind.Car) =>
          ComponentDescriptorLoader.loadArchive(path).toOption
            .filter(_matches_component_descriptor(_, componentName, version))
            .map(_ => path)
        case _ =>
          None
      }.toSeq.headOption
        .orElse(_resolve_standard_component_artifact(baseDir, componentName, version).collect {
          case Artifact(path, ArtifactKind.Car) => path
        })
    }
  }

  private def _matches_subsystem_descriptor(
    descriptor: GenericSubsystemDescriptor,
    subsystemname: String
  ): Boolean = {
    val requested = subsystemname.trim
    val versionedname =
      descriptor.version.map(v => s"${descriptor.subsystemName}-${v}")
    descriptor.subsystemName == requested ||
      versionedname.contains(requested) ||
      descriptor.path.getFileName.toString.stripSuffix(".sar").stripSuffix(".zip") == requested
  }

  private def _matches_component_descriptor(
    descriptor: ComponentDescriptor,
    componentname: String
  ): Boolean = {
    val requested = componentname.trim
    val names = _component_descriptor_names(descriptor)
    names.exists { name =>
      NamingConventions.equivalentByNormalized(name, requested)
    }
  }

  private def _matches_component_descriptor(
    descriptor: ComponentDescriptor,
    componentname: String,
    version: Option[String]
  ): Boolean =
    _matches_component_descriptor(descriptor, componentname) &&
      version.forall(v => descriptor.version.forall(_ == v))

  private def _component_descriptors_for_artifact(
    params: ComponentCreate,
    descriptor: ComponentDescriptor
  ): Vector[ComponentDescriptor] = {
    val names = _component_descriptor_names(descriptor)
    val matched =
      params.componentDescriptors.filter { requested =>
        _component_descriptor_names(requested).exists { requestedname =>
          names.exists(name => NamingConventions.equivalentByNormalized(name, requestedname))
        }
      }
    if (matched.nonEmpty)
      matched.map(requested => _merge_packaged_descriptor(descriptor, requested))
    else
      Vector(descriptor)
  }

  private def _merge_packaged_descriptor(
    packaged: ComponentDescriptor,
    requested: ComponentDescriptor
  ): ComponentDescriptor =
    packaged.copy(
      extensionBindings = packaged.extensionBindings ++ requested.extensionBindings
    )

  private def _component_descriptor_names(
    descriptor: ComponentDescriptor
  ): Vector[String] =
    (Vector(descriptor.componentName, descriptor.name).flatten ++ descriptor.componentlets.map(_.name))
      .filter(_.trim.nonEmpty)

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
    val withorigin = params.withOrigin(origin)
    val components =
      ServiceLoader.load(classOf[Component], loader).iterator.asScala.toVector
    val factories =
      ServiceLoader
        .load(classOf[Component.BundleFactory], loader)
        .iterator
        .asScala
        .toVector
    val fromfactories = factories.flatMap(_.create(withorigin).participants)
    val direct = components.map(_initialize_component(withorigin))
    if (fromfactories.nonEmpty)
      fromfactories ++ direct.filterNot(d => fromfactories.exists(f => NamingConventions.equivalentByNormalized(f.name, d.name)))
    else
      direct
  }

  private def _discover_from_artifacts(
    basedir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    releaseonly: Boolean = false
  ): Seq[Component] = {
    val artifacts = _list_artifacts(basedir).filterNot(artifact => releaseonly && _is_snapshot_artifact(artifact))
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
            val filename = p.getFileName.toString
            if (Files.isRegularFile(p)) {
              if (filename.endsWith(".car")) {
                Some(Artifact(p, ArtifactKind.Car))
              } else if (filename.endsWith(".sar")) {
                Some(Artifact(p, ArtifactKind.Sar))
              } else if (filename.endsWith(".jar")) {
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

  private def _load_assembly_api_artifacts(
    artifacts: Vector[Artifact]
  ): Consequence[AssemblyApiMetadata] =
    artifacts.foldLeft(Consequence.success(AssemblyApiMetadata())) { (z, artifact) =>
      for {
        acc <- z
        metadata <- artifact.kind match {
          case ArtifactKind.Car => AssemblyApiClassLoader.loadCar(artifact.path)
          case ArtifactKind.CarDir => AssemblyApiClassLoader.loadDirectory(artifact.path)
          case ArtifactKind.Sar => AssemblyApiClassLoader.loadSar(artifact.path)
          case ArtifactKind.SarDir => AssemblyApiClassLoader.loadSarDirectory(artifact.path)
          case _ => Consequence.success(AssemblyApiMetadata())
        }
      } yield acc ++ metadata
    }

  private def _requested_component_artifacts(
    basedir: Path,
    params: ComponentCreate,
    releaseonly: Boolean = false
  ): Vector[Artifact] = {
    _requested_components(params).flatMap { case (name, version) =>
      _resolve_requested_component_artifact(basedir, name, version, releaseonly)
    }.distinct
  }

  private def _requested_components(
    params: ComponentCreate
  ): Vector[(String, Option[String])] =
    params.componentDescriptors.flatMap { d =>
      d.componentName.orElse(d.name).map(n => (n, d.version))
    }.distinct

  private def _satisfied_by_active_development_component(
    params: ComponentCreate,
    componentname: String
  ): Boolean = {
    val target = NamingConventions.toComparisonKey(componentname)
    params.subsystem.components.exists { component =>
      component.origin match {
        case ComponentOrigin.Repository("component-dev-dir") =>
          NamingConventions.toComparisonKey(component.core.name) == target
        case _ =>
          false
      }
    }
  }

  private def _resolve_requested_component_artifact(
    basedir: Path,
    componentname: String,
    version: Option[String],
    releaseonly: Boolean = false
  ): Vector[Artifact] = {
    val flat = _list_matching_artifacts(basedir, componentname, version)
    val artifacts =
      if (flat.nonEmpty)
        flat
      else
        _resolve_standard_component_artifact(basedir, componentname, version, releaseonly).toVector
    if (releaseonly)
      artifacts.filterNot(_is_snapshot_artifact)
    else
      artifacts
  }

  private def _list_matching_artifacts(
    basedir: Path,
    componentname: String,
    version: Option[String]
  ): Vector[Artifact] = {
    val prefix = version.map(v => s"${componentname}-${v}").getOrElse(componentname)
    _list_artifacts(basedir).filter { artifact =>
      val filename = artifact.path.getFileName.toString
      artifact.kind match {
        case ArtifactKind.Car | ArtifactKind.CarDir =>
          filename == s"${componentname}.car" ||
          filename == s"${componentname}.zip" ||
          filename.startsWith(prefix) ||
          _artifact_matches_component_descriptor(artifact, componentname, version)
        case ArtifactKind.Sar | ArtifactKind.SarDir =>
          _artifact_matches_subsystem_component(artifact, componentname, version)
        case ArtifactKind.Jar => false
      }
    }
  }

  private def _artifact_matches_component_descriptor(
    artifact: Artifact,
    componentname: String,
    version: Option[String]
  ): Boolean = {
    val descriptor = artifact.kind match {
      case ArtifactKind.Car =>
        ComponentDescriptorLoader.loadArchive(artifact.path).toOption
      case ArtifactKind.CarDir =>
        ComponentDescriptorLoader.load(artifact.path).toOption.flatMap(_.headOption)
      case _ =>
        None
    }
    descriptor.exists(_matches_component_descriptor(_, componentname, version))
  }

  private def _artifact_matches_subsystem_component(
    artifact: Artifact,
    componentname: String,
    version: Option[String]
  ): Boolean = {
    val descriptor = artifact.kind match {
      case ArtifactKind.Sar | ArtifactKind.SarDir =>
        GenericSubsystemDescriptor.load(artifact.path).toOption
      case _ =>
        None
    }
    descriptor.exists { subsystemdescriptor =>
      subsystemdescriptor.componentBindings.exists { binding =>
        NamingConventions.equivalentByNormalized(binding.componentName, componentname) &&
          version.forall(v => binding.componentVersion.forall(_ == v))
      }
    }
  }

  private def _resolve_standard_component_descriptor(
    basedir: Path,
    componentname: String,
    releaseonly: Boolean = false
  ): Option[ComponentDescriptor] =
    _resolve_standard_component_artifact(basedir, componentname, None, releaseonly).iterator.flatMap {
      case Artifact(path, ArtifactKind.Car) =>
        ComponentDescriptorLoader.loadArchive(path).toOption
      case Artifact(path, ArtifactKind.CarDir) =>
        ComponentDescriptorLoader.load(path).toOption.flatMap(_.headOption)
      case _ =>
        None
    }.toSeq.headOption

  private def _resolve_standard_component_artifact(
    basedir: Path,
    componentname: String,
    version: Option[String],
    releaseonly: Boolean = false
  ): Option[Artifact] =
    _standard_component_repository_roots(basedir).iterator.flatMap { root =>
      _resolve_standard_artifact(root, componentname, version, ".car", ArtifactKind.Car, releaseonly)
    }.toSeq.headOption

  private def _resolve_standard_subsystem_descriptor(
    basedir: Path,
    subsystemname: String,
    releaseonly: Boolean = false
  ): Option[GenericSubsystemDescriptor] =
    _resolve_standard_subsystem_artifact(basedir, subsystemname, releaseonly).flatMap {
      case Artifact(path, ArtifactKind.Sar) => GenericSubsystemDescriptor.load(path).toOption
      case Artifact(path, ArtifactKind.SarDir) => GenericSubsystemDescriptor.load(path).toOption
      case _ => None
    }

  private def _resolve_standard_subsystem_artifact(
    basedir: Path,
    subsystemname: String,
    releaseonly: Boolean = false
  ): Option[Artifact] =
    _standard_subsystem_repository_roots(basedir).iterator.flatMap { root =>
      _resolve_standard_artifact(root, subsystemname, None, ".sar", ArtifactKind.Sar, releaseonly)
    }.toSeq.headOption

  private def _standard_component_repository_roots(
    basedir: Path
  ): Vector[Path] =
    Vector(
      basedir,
      basedir.resolve("repository").resolve(_standard_component_repository_path),
      basedir.resolve(_standard_component_repository_path),
      basedir.resolve(_legacy_standard_component_repository_path)
    ).distinct

  private def _standard_subsystem_repository_roots(
    basedir: Path
  ): Vector[Path] =
    Vector(
      basedir,
      basedir.resolve("repository").resolve(_standard_subsystem_repository_path),
      basedir.resolve(_standard_subsystem_repository_path),
      basedir.resolve(_legacy_standard_subsystem_repository_path)
    ).distinct

  private def _resolve_standard_artifact(
    repositoryroot: Path,
    name: String,
    version: Option[String],
    suffix: String,
    kind: ArtifactKind,
    releaseonly: Boolean = false
  ): Option[Artifact] = {
    val artifactroot = repositoryroot.resolve(name)
    if (!Files.isDirectory(artifactroot)) {
      None
    } else {
      val versions0 =
        version.map(v => Vector(v)).getOrElse(_version_dirs_desc(artifactroot))
      val versions =
        if (releaseonly) versions0.filterNot(_is_snapshot_version) else versions0
      versions.iterator.flatMap { v =>
        val artifact = artifactroot.resolve(v).resolve(s"${name}-${v}${suffix}")
        if (Files.isRegularFile(artifact)) Some(Artifact(artifact, kind)) else None
      }.toSeq.headOption
    }
  }

  private def _fetch_standard_component_descriptor(
    baseurl: String,
    cacheroot: Path,
    componentname: String
  ): Option[ComponentDescriptor] =
    _fetch_standard_component_artifact(baseurl, cacheroot, componentname, None).iterator.flatMap {
      case Artifact(path, ArtifactKind.Car) =>
        ComponentDescriptorLoader.loadArchive(path).toOption
      case _ =>
        None
    }.toSeq.headOption

  private def _fetch_standard_component_artifact(
    baseurl: String,
    cacheroot: Path,
    componentname: String,
    version: Option[String]
  ): Option[Artifact] =
    _standard_versions(baseurl, cacheroot.resolve(_standard_component_repository_path), componentname, version).iterator.flatMap { v =>
      _fetch_standard_artifact(
        baseurl,
        cacheroot.resolve(_standard_component_repository_path),
        componentname,
        v,
        ".car",
        ArtifactKind.Car
      )
    }.toSeq.headOption

  private def _fetch_standard_subsystem_descriptor(
    baseurl: String,
    cacheroot: Path,
    subsystemname: String
  ): Option[GenericSubsystemDescriptor] =
    _fetch_standard_subsystem_artifact(baseurl, cacheroot, subsystemname, None).flatMap {
      case Artifact(path, ArtifactKind.Sar) =>
        GenericSubsystemDescriptor.load(path).toOption
      case _ =>
        None
    }

  private def _fetch_standard_subsystem_artifact(
    baseurl: String,
    cacheroot: Path,
    subsystemname: String,
    version: Option[String]
  ): Option[Artifact] =
    _standard_versions(baseurl, cacheroot.resolve(_standard_subsystem_repository_path), subsystemname, version).iterator.flatMap { v =>
      _fetch_standard_artifact(
        baseurl,
        cacheroot.resolve(_standard_subsystem_repository_path),
        subsystemname,
        v,
        ".sar",
        ArtifactKind.Sar
      )
    }.toSeq.headOption

  private def _standard_versions(
    baseurl: String,
    repositoryroot: Path,
    name: String,
    version: Option[String]
  ): Vector[String] =
    version match {
      case Some(v) if _is_snapshot_version(v) => Vector.empty
      case Some(v) => Vector(v)
      case None =>
      val local = {
        val artifactroot = repositoryroot.resolve(name)
        if (Files.isDirectory(artifactroot)) _version_dirs_desc(artifactroot).filterNot(_is_snapshot_version) else Vector.empty
      }
      if (local.nonEmpty) local else _fetch_standard_metadata_versions(baseurl, name)
    }

  private def _fetch_standard_metadata_versions(
    baseurl: String,
    name: String
  ): Vector[String] =
    _read_remote_text(_join_url(baseurl, name, "maven-metadata.xml")).map { text =>
      val latest = _first_xml_tag(text, "latest").orElse(_first_xml_tag(text, "release")).toVector
      val versions = "<version>([^<]+)</version>".r.findAllMatchIn(text).map(_.group(1).trim).filter(_.nonEmpty).toVector
      (latest ++ versions.reverse).distinct.filterNot(_is_snapshot_version)
    }.getOrElse(Vector.empty)

  private def _is_snapshot_version(
    version: String
  ): Boolean =
    version.toUpperCase(java.util.Locale.ROOT).endsWith("-SNAPSHOT")

  private def _is_snapshot_artifact(
    artifact: Artifact
  ): Boolean =
    artifact.path.getFileName.toString.toUpperCase(java.util.Locale.ROOT).contains("-SNAPSHOT")

  private def _fetch_standard_artifact(
    baseurl: String,
    repositoryroot: Path,
    name: String,
    version: String,
    suffix: String,
    kind: ArtifactKind
  ): Option[Artifact] = {
    val filename = s"${name}-${version}${suffix}"
    val target = repositoryroot.resolve(name).resolve(version).resolve(filename)
    if (Files.isRegularFile(target)) {
      Some(Artifact(target, kind))
    } else {
      Files.createDirectories(target.getParent)
      val tmp = target.resolveSibling(s"${filename}.tmp")
      try {
        val url = _join_url(baseurl, name, version, filename)
        _copy_remote(url, tmp)
        try {
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch {
          case _: AtomicMoveNotSupportedException =>
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        Some(Artifact(target, kind))
      } catch {
        case NonFatal(_) =>
          try Files.deleteIfExists(tmp) catch {
            case NonFatal(_) => ()
          }
          None
      }
    }
  }

  private def _read_remote_text(
    url: String
  ): Option[String] =
    try {
      val uri = URI.create(url)
      val connection = uri.toURL.openConnection()
      connection.setConnectTimeout(_remote_connect_timeout_ms)
      connection.setReadTimeout(_remote_read_timeout_ms)
      Using.resource(connection.getInputStream) { in =>
        new String(in.readAllBytes(), StandardCharsets.UTF_8)
      } match {
        case text if text.trim.nonEmpty => Some(text)
        case _ => None
      }
    } catch {
      case NonFatal(_) => None
    }

  private def _copy_remote(
    url: String,
    target: Path
  ): Unit = {
    val uri = URI.create(url)
    val connection = uri.toURL.openConnection()
    connection.setConnectTimeout(_remote_connect_timeout_ms)
    connection.setReadTimeout(_remote_read_timeout_ms)
    Using.resource(connection.getInputStream) { in =>
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _first_xml_tag(
    text: String,
    tag: String
  ): Option[String] = {
    val pattern = s"<${tag}>([^<]+)</${tag}>".r
    pattern.findFirstMatchIn(text).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _join_url(
    base: String,
    parts: String*
  ): String =
    (_strip_trailing_slash(base) +: parts.map(_.stripPrefix("/").stripSuffix("/"))).mkString("/")

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
    sardescriptor: Option[GenericSubsystemDescriptor] = None
  ): Seq[Component] =
    _repository_result(
      _discover_component_from_car_c(carpath, params, origin, log, sardescriptor),
      params,
      log,
      s"[component-dir] car=${carpath.getFileName} invalid car"
    )

  private def _discover_component_from_car_c(
    carpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sardescriptor: Option[GenericSubsystemDescriptor] = None
  ): Consequence[Vector[Component]] = {
    val areas = GlobalContext.globalContext.workAreaSpace
    CarExtractor.withExtracted(carpath, areas) { extracted =>
      _discover_component_from_car_common(
        extracted = extracted,
        artifactpath = carpath,
        params = params,
        origin = origin,
        log = log,
        sardescriptor = sardescriptor,
        sourcetype = sardescriptor.map(_ => "sar+car").getOrElse("car")
      )
    }
  }

  private def _discover_component_from_car_dir(
    cardir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sardescriptor: Option[GenericSubsystemDescriptor] = None
  ): Seq[Component] =
    _repository_result(
      _discover_component_from_car_dir_c(cardir, params, origin, log, sardescriptor),
      params,
      log,
      s"[component-dir] car-dir=${cardir.getFileName} invalid car-dir"
    )

  private def _discover_component_from_car_dir_c(
    cardir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sardescriptor: Option[GenericSubsystemDescriptor] = None
  ): Consequence[Vector[Component]] =
    CarExtractor.resolveDirectory(cardir).flatMap { extracted =>
      _discover_component_from_car_common(
        extracted = extracted,
        artifactpath = cardir,
        params = params,
        origin = origin,
        log = log,
        sardescriptor = sardescriptor,
        sourcetype = sardescriptor.map(_ => "sar+car-dir").getOrElse("car-dir")
      )
    }

  private def _discover_component_from_car_common(
    extracted: CarExtracted,
    artifactpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sardescriptor: Option[GenericSubsystemDescriptor],
    sourcetype: String
  ): Consequence[Vector[Component]] = {
    _required_car_descriptor_value(
      extracted.descriptor.name.orElse(extracted.descriptor.componentName),
      "name",
      artifactpath
    ).flatMap { carname =>
      _required_car_descriptor_value(extracted.descriptor.version, "version", artifactpath).flatMap { carversion =>
        _required_car_descriptor_value(
          extracted.descriptor.componentName.orElse(extracted.descriptor.name),
          "component",
          artifactpath
        ).flatMap { componentname =>
          ComponentDependencyResolver.resolve(
            extracted.root,
            componentname,
            params.subsystem.configuration
          ).flatMap { dependencies =>
            _discover_component_from_car_common(
              extracted,
              artifactpath,
              params,
              origin,
              log,
              sardescriptor,
              sourcetype,
              carname,
              carversion,
              componentname,
              dependencies
            )
          }
        }
      }
    }
  }

  private def _discover_component_from_car_common(
    extracted: CarExtracted,
    artifactpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    sardescriptor: Option[GenericSubsystemDescriptor],
    sourcetype: String,
    carname: String,
    carversion: String,
    componentname: String,
    dependencies: ComponentDependencyResolution
  ): Consequence[Vector[Component]] = try {
    val baseorigin = _component_origin_for_archive(
      repositorytype = origin.label,
      carname = carname,
      carversion = carversion,
      sardescriptor = sardescriptor,
      fallback = origin
    )
    val (effectiveextensions, effectiveconfig) =
      _effective_extensions_config(extracted.descriptor, sardescriptor)
    val artifactmetadata = Component.ArtifactMetadata(
      sourceType = sourcetype,
      name = carname,
      version = carversion,
      component = Some(componentname),
      subsystem = sardescriptor.map(_.subsystemName).orElse(extracted.descriptor.subsystemName),
      archivePath = Some(artifactpath.toString),
      effectiveExtensions = effectiveextensions,
      effectiveConfig = effectiveconfig
    )
    val componentparams =
      params.withComponentDescriptors(_component_descriptors_for_artifact(params, extracted.descriptor))
    // Component classes can load declared local dependencies after factory
    // discovery. The live component classes retain this loader for their
    // runtime lifetime, so closing it here would break deferred class loading.
    val componentloader = dependencies.componentClassLoader(
      Vector(extracted.componentMain),
      extracted.componentLibs,
      params.assemblyApiClassLoader.getOrElse(getClass.getClassLoader)
    )
    val components0 =
      _discover_component_from_artifact_with_loader(
        artifactname = artifactpath.getFileName.toString,
        loader = componentloader,
        // Scan only the component's main archive. Dependency jars may contain
        // demo or builtin components that must not be treated as packaged
        // component definitions for this CAR.
        scanclasspath = Vector(extracted.componentMain),
        params = componentparams,
        origin = baseorigin,
        log = log
      ).toVector
    val components = components0.map(_.withArtifactMetadata(artifactmetadata))
    val collaboratorcomponents = components.collect {
      case comp: CollaboratorComponent => comp
    }
    if (collaboratorcomponents.isEmpty) {
      Consequence.success(components)
    } else {
      extracted.collaboratorClasspath match {
        case Some(paths) if paths.nonEmpty =>
          Using.resource(CollaboratorClassLoader(paths)) { collaboratorLoader =>
            CollaboratorFactory.create(collaboratorLoader, paths) match {
              case Consequence.Success(collaborator) =>
                collaboratorcomponents.foreach(_.setCollaborator(collaborator))
                Consequence.success(components)
              case Consequence.Failure(conclusion) =>
                log.warn(s"[component-dir] artifact=${artifactpath.getFileName} collaborator init failed cause=${conclusion.show}")
                Consequence.Failure(conclusion)
            }
          }
        case _ =>
          log.warn(s"[component-dir] artifact=${artifactpath.getFileName} collaborator classpath missing")
          Consequence.componentInvalid(
            s"collaborator classpath is missing for component artifact: ${artifactpath.getFileName}"
          )
      }
    }
  } catch {
    case e: ComponentRepositoryDiscoveryFailure => Consequence.Failure(e.conclusion)
  }

  private def _discover_component_from_sar(
    sarpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    _repository_result(
      _discover_component_from_sar_c(sarpath, params, origin, log),
      params,
      log,
      s"[component-dir] sar=${sarpath.getFileName} invalid sar"
    )

  private def _discover_component_from_sar_c(
    sarpath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    val areas = GlobalContext.globalContext.workAreaSpace
    SarExtractor.withExtracted(sarpath, areas) { extracted =>
      _discover_component_from_sar_extracted(extracted, params, origin, log)
    }
  }

  private def _discover_component_from_sar_dir(
    sardir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] =
    _repository_result(
      SarExtractor.resolveDirectory(sardir).flatMap(
        _discover_component_from_sar_extracted(_, params, origin, log)
      ),
      params,
      log,
      s"[component-dir] sar-dir=${sardir.getFileName} invalid sar-dir"
    )

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
      for {
        fromcars <- _sequence_component_discovery(
          extracted.carArtifacts.sortBy(_.toString).toVector.map { car =>
            _discover_component_from_car_c(
              carpath = car,
              params = params,
              origin = origin,
              log = log,
              sardescriptor = Some(extracted.descriptor)
            )
          }
        )
        fromcardirs <- _sequence_component_discovery(
          extracted.carDirectories.sortBy(_.toString).toVector.map { cardir =>
            _discover_component_from_car_dir_c(
              cardir = cardir,
              params = params,
              origin = origin,
              log = log,
              sardescriptor = Some(extracted.descriptor)
            )
          }
        )
      } yield (fromcars ++ fromcardirs).distinctBy(_.name)
    }
  }

  private def _sequence_component_discovery(
    values: Vector[Consequence[Vector[Component]]]
  ): Consequence[Vector[Component]] =
    values.foldLeft(Consequence.success(Vector.empty[Component])) { (result, value) =>
      for {
        components <- result
        discovered <- value
      } yield components ++ discovered
    }

  private def _repository_result(
    result: Consequence[Vector[Component]],
    params: ComponentCreate,
    log: BootstrapLog,
    message: String
  ): Vector[Component] =
    result match {
      case Consequence.Success(components) => components
      case Consequence.Failure(conclusion) if params.componentDescriptors.isEmpty =>
        log.warn(s"${message} cause=${conclusion.show}")
        Vector.empty
      case Consequence.Failure(conclusion) =>
        log.warn(s"${message} cause=${conclusion.show}")
        throw new ComponentRepositoryDiscoveryFailure(conclusion)
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
    cardescriptor: ComponentDescriptor,
    sardescriptor: Option[GenericSubsystemDescriptor]
  ): (Map[String, String], Map[String, String]) = {
    val extensions = cardescriptor.extensions ++ sardescriptor.map(_.extensions).getOrElse(Map.empty)
    val config = cardescriptor.config ++ sardescriptor.map(_.config).getOrElse(Map.empty)
    (extensions, config)
  }

  private def _required_car_descriptor_value(
    value: Option[String],
    field: String,
    artifactpath: Path
  ): Consequence[String] =
    value.map(_.trim).filter(_.nonEmpty)
      .map(Consequence.success)
      .getOrElse(Consequence.resourceInvalid(s"CAR component-descriptor.json must declare ${field}: ${artifactpath}"))

  private def _component_origin_for_archive(
    repositorytype: String,
    carname: String,
    carversion: String,
    sardescriptor: Option[GenericSubsystemDescriptor],
    fallback: ComponentOrigin
  ): ComponentOrigin =
    fallback match {
      case ComponentOrigin.Repository(_) =>
        val label = sardescriptor match {
          case Some(sar) =>
            s"${repositorytype}:sar:${sar.subsystemName}:${sar.version.getOrElse("0.1.0")}:car:${carname}:${carversion}"
          case None =>
            s"${repositorytype}:car:${carname}:${carversion}"
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
    val withorigin = params.withOrigin(origin)
    val classnames = _jar_class_names_from_paths(scanclasspath)
    if (classnames.isEmpty) {
      log.warn(s"[component-dir] artifact=${artifactname} contains no class entries")
      Vector.empty
    } else {
      val factorycomponents = _instantiate_factory_components(
        loader = loader,
        classnames = classnames,
        params = params,
        origin = origin,
        log = log,
        artifactname = artifactname,
        repositorytype = _component_dir_type
      )
      if (factorycomponents.nonEmpty) {
        factorycomponents
      } else {
        _build_sources(classnames, loader, origin, log, tolerant = true) match {
          case Consequence.Success(sources) =>
            _provide_components(sources, withorigin, log) match {
              case Consequence.Success(components) =>
                components.headOption match {
                  case Some(first) =>
                    val canonicalname =
                      params.componentDescriptors.headOption
                        .flatMap(x => x.componentName.orElse(x.name))
                        .getOrElse(first.core.name)
                    log.info(s"[component-dir] artifact=${artifactname} provides component=${canonicalname}")
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
    val withoutextension = entryname.substring(0, entryname.length - ".class".length)
    withoutextension.replace('/', '.')
  }

  private def _instantiate_factory_components(
    loader: URLClassLoader,
    classnames: Seq[String],
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog,
    artifactname: String,
    repositorytype: String
  ): Seq[Component] = {
    _find_factory_class(
      loader = loader,
      classnames = classnames,
      artifactname = artifactname,
      repositorytype = repositorytype
    ) match {
      case Some(factoryclass) => _create_components_from_factory(factoryclass, params.withOrigin(origin), log)
      case None => Vector.empty
    }
  }

  private def _find_factory_class(
    loader: URLClassLoader,
    classnames: Seq[String],
    artifactname: String,
    repositorytype: String
  ): Option[Class[_]] = {
    val factories = classnames.view.flatMap { classname =>
      _load_class(
        classname = classname,
        loader = loader,
        artifactname = artifactname,
        repositorytype = repositorytype
      ).flatMap { cls =>
        if (
          _is_factory_class(cls) &&
          !cls.isInterface &&
          !Modifier.isAbstract(cls.getModifiers) &&
          !cls.getName.endsWith("$")
        ) {
          Some(cls)
        } else {
          None
        }
      }
    }.toVector
    factories.sortBy(_factory_priority).headOption
  }

  private def _is_factory_class(cls: Class[_]): Boolean =
    classOf[Component.BundleFactory].isAssignableFrom(cls) ||
      classOf[Component.Factory].isAssignableFrom(cls)

  private def _factory_priority(
    factoryclass: Class[_]
  ): (Int, Int, String) = {
    val name = factoryclass.getName
    val bundlepenalty = if (classOf[Component.BundleFactory].isAssignableFrom(factoryclass)) 0 else 1
    val nestedpenalty = if (name.contains("$") || factoryclass.getEnclosingClass != null) 1 else 0
    (bundlepenalty, nestedpenalty, name)
  }

  private def _create_components_from_factory(
    factoryclass: Class[_],
    params: ComponentCreate,
    log: BootstrapLog
  ): Seq[Component] = {
    try {
      factoryclass.getDeclaredConstructor().newInstance() match {
        case factory: Component.BundleFactory =>
          val declarations =
            factory.primaryFactory.initializationParameterDeclarations ++
              factory.componentletFactories.flatMap(_.initializationParameterDeclarations)
          val effectiveparams = if (declarations.nonEmpty) params else params.copy(instanceMetadata = None)
          factory.createC(effectiveparams) match {
            case Consequence.Success(bundle) => bundle.participants
            case Consequence.Failure(conclusion) =>
              if (params.componentDescriptors.isEmpty) Vector.empty
              else throw new ComponentRepositoryDiscoveryFailure(conclusion)
          }
        case factory: Component.Factory =>
          val effectiveparams =
            if (factory.initializationParameterDeclarations.nonEmpty) params
            else params.copy(instanceMetadata = None)
          factory.createPrimaryC(effectiveparams) match {
            case Consequence.Success(component) => Vector(component)
            case Consequence.Failure(conclusion) =>
              if (params.componentDescriptors.isEmpty) Vector.empty
              else throw new ComponentRepositoryDiscoveryFailure(conclusion)
          }
      }
    } catch {
      case e: ComponentRepositoryDiscoveryFailure =>
        log.warn(s"component factory initialization failed for ${factoryclass.getName}: ${e.conclusion.display}")
        throw e
      case NonFatal(e) =>
        log.warn(s"component factory initialization failed for ${factoryclass.getName}: ${e.getMessage}")
        throw e
    }
  }

  private final class ComponentRepositoryDiscoveryFailure(
    val conclusion: Conclusion
  ) extends RuntimeException(conclusion.display)

  private def _load_class(
    classname: String,
    loader: URLClassLoader,
    artifactname: String,
    repositorytype: String
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(classname, false, loader))
    } catch {
      case e: ClassNotFoundException =>
        _observe_component_load_error(classname, e, artifactname, repositorytype)
        None
      case e: NoClassDefFoundError =>
        _observe_component_load_error(classname, e, artifactname, repositorytype)
        None
      case e: LinkageError =>
        _observe_component_load_error(classname, e, artifactname, repositorytype)
        None
      case NonFatal(e) =>
        _observe_component_load_error(classname, e, artifactname, repositorytype)
        None
    }
  }

  private def _observe_component_load_error(
    classname: String,
    e: Throwable,
    artifactname: String,
    repositorytype: String
  ): Unit = {
    val taxonomy = e match {
      case _: ClassNotFoundException => Taxonomy.componentUnavailable
      case _: NoClassDefFoundError => Taxonomy.componentUnavailable
      case _: LinkageError => Taxonomy.componentInvalid
      case _ => Taxonomy.componentCorrupted
    }
    val observation = Observation.failure(
      taxonomy,
      Descriptor.Facet.ClassName(classname),
      Descriptor.Facet.Artifact(artifactname),
      Descriptor.Facet.RepositoryType(repositorytype),
      Descriptor.Facet.Exception(e)
    )
    val message = ObservationRender.warnMessage(observation)
    observe_warn(s"[component-dir] ignored class load error $message")
  }

  private def _discover_by_scan(
    loader: URLClassLoader,
    params: ComponentCreate,
    classdirs: Seq[Path],
    packageprefixes: Seq[String],
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    _discover_components(loader, params, classdirs, packageprefixes, ComponentOrigin.Repository("component-dir"), log)
  }

  private def _discover_by_scan_ordered(
    loader: URLClassLoader,
    params: ComponentCreate,
    classdirs: Seq[Path],
    packageprefixes: Seq[String],
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    val names = _discover_class_names(classdirs, packageprefixes)
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
    classdirs: Seq[Path],
    packageprefixes: Seq[String],
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean = false
  ): Consequence[Vector[Component]] = {
    val names = _discover_class_names(classdirs, packageprefixes)
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
    classnames: Seq[String],
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean
  ): Consequence[Vector[Component]] = {
    val withorigin = params.withOrigin(origin)
    for {
      sources <- _build_sources(classnames, loader, origin, log, tolerant)
      components <- _provide_components(sources, withorigin, log)
    } yield components
  }

  private def _discover_class_names(
    classdirs: Seq[Path],
    packageprefixes: Seq[String]
  ): Vector[String] = {
    val seen = mutable.Set.empty[String]
    classdirs.foreach { root =>
      _class_files(root).foreach { classfile =>
        val classname = _class_name(root, classfile)
        val basename = _base_class_name(classname)
        if (
          _is_discoverable_component_class(classname) &&
          _accept_class(basename, packageprefixes) &&
          !seen.contains(basename)
        ) {
          seen += basename
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

  private def _class_name(root: Path, classfile: Path): String = {
    val relative = root.relativize(classfile).toString
    val noext =
      if (relative.endsWith(".class")) {
        relative.substring(0, relative.length - ".class".length)
      } else {
        relative
      }
    noext.replace('/', '.').replace('\\', '.')
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
      val basecandidates = Vector(base, stripped, prefixed).filter(_.nonEmpty)
      val withdollar = basecandidates.map { c =>
        if (c.endsWith("$")) c else s"${c}$$"
      }
      val withoutdollar = basecandidates.map(_.stripSuffix("$"))
      val candidates = basecandidates ++ withdollar ++ withoutdollar
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

  // Scala 3 compiles top-level declarations in Foo.scala to Foo$package classes.
  // They are implementation containers, never component or factory classes.
  private[repository] def _is_discoverable_component_class(name: String): Boolean =
    !name.matches(".*\\$package(?:\\$.*)?")

  private def _accept_class(
    name: String,
    packageprefixes: Seq[String]
  ): Boolean = {
    if (packageprefixes.isEmpty) {
      true
    } else {
      packageprefixes.exists(prefix => name.startsWith(prefix))
    }
  }

  private def _build_sources(
    classnames: Seq[String],
    loader: ClassLoader,
    origin: ComponentOrigin,
    log: BootstrapLog,
    tolerant: Boolean
  ): Consequence[Vector[ComponentSource]] =
    classnames.foldLeft(Consequence.success(Vector.empty[ComponentSource])) { (result, classname) =>
      result.flatMap { acc =>
        log.info(s"candidate class=${classname}")
        ComponentFactory.build(Seq(classname), loader, origin.label) match {
          case Consequence.Success(sources) =>
            sources.foreach {
              case ComponentSource.ClassDef(_, _) =>
                log.info(s"accepted component class=${classname}")
            }
            Consequence.success(acc ++ sources)
          case Consequence.Failure(conclusion) =>
            log.warn(s"failed to build source: ${classname} cause=${conclusion.show}")
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
