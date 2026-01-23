package org.goldenport.cncf.component.repository

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.lang.reflect.Modifier
import java.util.ServiceLoader
import java.util.jar.JarFile
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.observability.global.{GlobalObservable, ObservabilityScopeDefaults, PersistentBootstrapLog}
import org.goldenport.cncf.component.*

/*
 * @since   Jan. 12, 2026
 * @version Jan. 23, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class ComponentRepository {
  def discover(): Seq[Component]
}

object ComponentRepository extends GlobalObservable {
  private val _scala_cli_type = "scala-cli"
  private val _component_dir_type = "component-dir"
  private val _scala_cli_default_dir = ".scala-build"
  private val _component_dir_default_dir = "component.dir"

  sealed abstract class Specification {
    def build(params: ComponentCreate): ComponentRepository
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
        val loader = _class_loader(classDirs)
        _discover_by_scan_ordered(loader, params, classDirs, packagePrefixes, log) match {
          case Consequence.Success(comps) => comps
          case Consequence.Failure(conclusion) =>
            log.warn(s"component discovery failed: ${conclusion.message}")
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
        // TEMPORARY: Simple JAR class scanning. TODO: Add META-INF/component.yaml-based discovery.
        val components = _discover_from_jars(baseDir, params, origin, log)
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
    }
  }

  private def _class_loader(
    classDirs: Seq[Path]
  ): URLClassLoader = {
    val urls = classDirs.map(_.toUri.toURL).toArray
    new URLClassLoader(urls, getClass.getClassLoader)
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
        .load(classOf[Component.Factory], loader)
        .iterator
        .asScala
        .toVector
    val fromFactories = factories.flatMap(_.create(withOrigin))
    val direct = components.map(_initialize_component(withOrigin))
    direct ++ fromFactories
  }

  private def _discover_from_jars(
    baseDir: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] = {
    val jarFiles = _list_jar_files(baseDir)
    jarFiles.flatMap { jar =>
      _discover_component_from_jar(jar, params, origin, log)
    }
  }

  private def _list_jar_files(baseDir: Path): Vector[Path] = {
    if (!Files.exists(baseDir)) {
      Vector.empty
    } else {
      val stream = Files.list(baseDir)
      try {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar"))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _discover_component_from_jar(
    jarPath: Path,
    params: ComponentCreate,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Seq[Component] = {
    val withOrigin = params.withOrigin(origin)
    Using.resource(new URLClassLoader(Array(jarPath.toUri.toURL), getClass.getClassLoader)) { loader =>
      val classNames = _jar_class_names(jarPath)
      if (classNames.isEmpty) {
        log.warn(s"[component-dir] jar=${jarPath.getFileName} contains no class entries")
        Vector.empty
      } else {
        val factoryComponents = _instantiate_factory_components(loader, classNames, params, log)
        if (factoryComponents.nonEmpty) {
          factoryComponents.foreach { comp =>
            log.info(
              s"[component-dir] initialized component=${comp.core.name} class=${comp.getClass.getName}"
            )
          }
          factoryComponents
        } else {
          _build_sources(classNames, loader, origin, log, tolerant = true) match {
            case Consequence.Success(sources) =>
              _provide_components(sources, withOrigin, log) match {
                case Consequence.Success(components) =>
                  components.headOption match {
                    case Some(first) =>
                      log.info(s"[componet-dir] jar=${jarPath.getFileName} provides component=${first.core.name}")
                      Vector(first)
                    case None =>
                      log.warn(s"[component-dir] jar=${jarPath.getFileName} contains no valid components")
                      Vector.empty
                  }
                case Consequence.Failure(conclusion) =>
                  log.warn(s"[component-dir] component creation failed for jar=${jarPath.getFileName} cause=${conclusion.message}")
                  Vector.empty
              }
            case Consequence.Failure(conclusion) =>
              log.warn(s"[component-dir] component discovery failed for jar=${jarPath.getFileName} cause=${conclusion.message}")
              Vector.empty
          }
        }
      }
    }
  }

  private def _jar_class_names(jarPath: Path): Vector[String] = {
    Using.resource(new JarFile(jarPath.toFile)) { jar =>
      jar
        .entries()
        .asScala
        .filter(e => !e.isDirectory && e.getName.endsWith(".class"))
        .map(e => _class_name_from_entry(e.getName))
        .toVector
    }
  }

  private def _class_name_from_entry(entryName: String): String = {
    val withoutExtension = entryName.substring(0, entryName.length - ".class".length)
    withoutExtension.replace('/', '.')
  }

  private def _instantiate_factory_components(
    loader: URLClassLoader,
    classNames: Seq[String],
    params: ComponentCreate,
    log: BootstrapLog
  ): Seq[Component] = {
    _find_factory_class(loader, classNames) match {
      case Some(factoryClass) => _create_components_from_factory(factoryClass, params, log)
      case None => Vector.empty
    }
  }

  private def _find_factory_class(
    loader: URLClassLoader,
    classNames: Seq[String]
  ): Option[Class[_ <: Component.Factory]] = {
    classNames.view.flatMap { className =>
      _load_class(className, loader).flatMap { cls =>
        if (
          classOf[Component.Factory].isAssignableFrom(cls) &&
          !cls.isInterface &&
          !Modifier.isAbstract(cls.getModifiers) &&
          !cls.getName.endsWith("$")
        ) {
          Some(cls.asInstanceOf[Class[_ <: Component.Factory]])
        } else {
          None
        }
      }
    }.headOption
  }

  private def _create_components_from_factory(
    factoryClass: Class[_ <: Component.Factory],
    params: ComponentCreate,
    log: BootstrapLog
  ): Seq[Component] = {
    try {
      val factory = factoryClass.getDeclaredConstructor().newInstance().asInstanceOf[Component.Factory]
      factory.create(params)
    } catch {
      case NonFatal(e) =>
        log.warn(s"component factory initialization failed for ${factoryClass.getName}: ${e.getMessage}")
        Vector.empty
    }
  }

  private def _load_class(
    className: String,
    loader: URLClassLoader
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(className, false, loader))
    } catch {
      case NonFatal(_) => None
    }
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
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    val names = _discover_class_names(classDirs, packagePrefixes)
    _discover_components_with_names(
      loader,
      params,
      names,
      origin,
      log,
      tolerant = false
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
  ): Consequence[Vector[ComponentSource]] = {
    var acc = Vector.empty[ComponentSource]
    classNames.foreach { className =>
      log.info(s"candidate class=${className}")
      ComponentFactory.build(Seq(className), loader, origin.label) match {
        case Consequence.Success(sources) =>
          sources.foreach {
            case ComponentSource.ClassDef(_, _) =>
              log.info(s"accepted component class=${className}")
          }
          acc = acc ++ sources
        case Consequence.Failure(conclusion) =>
          log.warn(s"failed to build source: ${className} cause=${conclusion.message}")
          if (!tolerant) {
            return Consequence.Failure(conclusion)
          }
      }
    }
    Consequence.Success(acc)
  }

  private def _provide_components(
    sources: Seq[ComponentSource],
    params: ComponentCreate,
    log: BootstrapLog
  ): Consequence[Vector[Component]] = {
    var acc = Vector.empty[Component]
    sources.foreach { source =>
      ComponentProvider.provide(source, params.subsystem, params.origin) match {
        case Consequence.Success(component) =>
          acc = acc :+ component
        case Consequence.Failure(conclusion) =>
          log.warn(s"failed to instantiate component cause=${conclusion.message}")
          return Consequence.Failure(conclusion)
      }
    }
    Consequence.Success(acc)
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
