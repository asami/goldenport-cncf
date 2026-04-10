package org.goldenport.cncf

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.ServiceLoader
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolver, ConfigurationSources, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.file.SimpleFileConfigLoader
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentCreate, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.repository.{ComponentProvider, ComponentRepository, ComponentRepositorySpace, ComponentSource}
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.subsystem.{GenericSubsystemFactory, Subsystem}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 23, 2026
 *  version Feb.  1, 2026
 *  version Mar. 26, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain extends GlobalObservable {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  def main(args: Array[String]): Unit = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val configuration = _resolve_configuration(cwd, args)
    val (reposResult, args1, noDefaultComponents) =
      _take_component_repository(configuration, args, cwd)
    val (factoryClasses, args2) = _take_component_factory_classes(configuration, args1)
    val (discover, args3) = _take_discover_classes(configuration, args2)
    val (workspace, args4) = _take_workspace(configuration, args3)
    val (forceExit, args5) = _take_force_exit(configuration, args4)
    val (noExit, rest) = _take_no_exit(configuration, args5)
    val enabled = discover

    val code: Int =
      try {
        val activeRepos = reposResult
        val searchRepos =
          _append_default_component_repository(reposResult, cwd, noDefaultComponents)
        (activeRepos, searchRepos) match {
          case (Left(message), _) =>
            Console.err.println(message)
            2
          case (_, Left(message)) =>
            Console.err.println(message)
            2
          case (Right(activeSpecs), Right(searchSpecs)) =>
            val baseExtras = _component_extra_function(activeSpecs, enabled, workspace, factoryClasses)
            val extras = _trace_component_dir_extras(baseExtras)
            val runtimeArgs = _with_resolved_subsystem_descriptor_arg(configuration, searchSpecs, rest)
            CncfRuntime.runWithExtraComponents(runtimeArgs, extras)
        }
      } catch {
        case e: CliFailed => e.code
      }

    if (forceExit) {
      sys.exit(code) // CLI adapter may exit only when --force-exit is requested.
    } else if (noExit && code != 0) {
      throw new CliFailed(code)
    } else {
      ()
    }
  }

  private def _take_no_exit(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val noexit =
      args.contains("--no-exit") ||
        _config_truthy(configuration, "cncf.runtime.no-exit")
    val rest = args.filterNot(_ == "--no-exit")
    (noexit, rest)
  }

  private def _take_force_exit(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val forceexit =
      args.contains("--force-exit") ||
        _config_truthy(configuration, "cncf.runtime.force-exit")
    val rest = args.filterNot(_ == "--force-exit")
    (forceexit, rest)
  }

  private val _no_default_components_flag = "--no-default-components"

  private def _take_component_repository(
    configuration: ResolvedConfiguration,
    args: Array[String],
    cwd: Path
  ): (Either[String, Vector[ComponentRepository.Specification]], Array[String], Boolean) = {
    val (specsresult, rest, nodefault) =
      ComponentRepositorySpace.extractArgs(configuration, args)
    specsresult match {
      case Left(message) =>
        (Left(message), rest, nodefault)
      case Right(values) if values.isEmpty =>
        (Right(Vector.empty), rest, nodefault)
      case Right(values) =>
        var error: Option[String] = None
        val parsed = Vector.newBuilder[ComponentRepository.Specification]
        values.foreach { value =>
          ComponentRepository.parseSpecs(value, cwd) match {
            case Left(err) =>
              if (error.isEmpty) {
                error = Some(err)
              }
            case Right(xs) =>
              if (error.isEmpty) {
                parsed ++= xs
              }
          }
        }
        error match {
          case Some(err) => (Left(err), rest, nodefault)
          case None => (Right(parsed.result()), rest, nodefault)
        }
    }
  }

  private def _append_default_component_repository(
    result: Either[String, Vector[ComponentRepository.Specification]],
    cwd: Path,
    noDefault: Boolean
  ): Either[String, Vector[ComponentRepository.Specification]] =
    result match {
      case left @ Left(_) => left
      case Right(specs) if noDefault => Right(specs)
      case Right(specs) =>
        _default_components_dir(cwd) match {
          case Some(dir) if !_has_default_components_spec(specs, dir) =>
            Right(specs :+ ComponentRepository.ComponentDirRepository.Specification(dir))
          case _ => Right(specs)
        }
    }

  private def _default_components_dir(cwd: Path): Option[Path] = {
    val dir = cwd.resolve("component.d").normalize
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

  private def _take_discover_classes(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val enabled =
      args.contains("--discover=classes") ||
        _config_truthy(configuration, "cncf.runtime.discover.classes") ||
        _discover_env_enabled()
    val rest = args.filterNot(_ == "--discover=classes")
    (enabled, rest)
  }

  private def _take_component_factory_classes(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Vector[String], Array[String]) = {
    val classes = Vector.newBuilder[String]
    val rest = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current == "--component-factory-class" && i + 1 < args.length) {
        classes += args(i + 1)
        i = i + 2
      } else if (current.startsWith("--component-factory-class=")) {
        classes += current.drop("--component-factory-class=".length)
        i = i + 1
      } else {
        rest += current
        i = i + 1
      }
    }
    val configClasses =
      _config_string(configuration, "cncf.runtime.component-factory-class")
        .toVector
        .flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
    ((configClasses ++ classes.result()).distinct, rest.result().toArray)
  }

  private def _take_workspace(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Option[Path], Array[String]) = {
    val buffer = Vector.newBuilder[String]
    var workspace: Option[Path] = None
    var i = 0
    while (i < args.length) {
      if (args(i) == "--workspace" && i + 1 < args.length) {
        workspace = Some(Paths.get(args(i + 1)))
        i = i + 2
      } else {
        buffer += args(i)
        i = i + 1
      }
    }
    val resolved =
      workspace.orElse(_config_string(configuration, "cncf.runtime.workspace").map(Paths.get(_)))
    (resolved, buffer.result().toArray)
  }

  private def _discover_env_enabled(): Boolean = {
    sys.env
      .get("CNCF_DISCOVER_CLASSES")
      .exists(v => _truthy_(v))
  }

  private def _discover_components(
    workspace: Option[Path]
  ): Subsystem => Seq[Component] = {
    val classDirs = _class_dirs_(workspace)
    if (classDirs.isEmpty) {
      _ => Nil
    } else {
      (subsystem: Subsystem) => {
        val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
        _discover_from_class_dirs_(params, classDirs, _package_prefixes_())
      }
    }
  }

  private def _discover_from_repositories(
    specs: Seq[ComponentRepository.Specification]
  ): Subsystem => Seq[Component] = {
    (subsystem: Subsystem) => {
      val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
      specs.flatMap { spec =>
        val origin = _origin_for_spec(spec)
        spec.build(params.withOrigin(origin)).discover()
      }
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

  private def _component_extra_function(
    specs: Vector[ComponentRepository.Specification],
    enabled: Boolean,
    workspace: Option[Path],
    factoryClasses: Vector[String]
  ): Subsystem => Seq[Component] = {
    (subsystem: Subsystem) => {
      val components = Vector.newBuilder[Component]
      val seen = mutable.LinkedHashMap.empty[String, Component]
      def addAll(xs: Seq[Component]): Unit =
        xs.foreach { component =>
          val name = component.core.name
          val key = NamingConventions.toComparisonKey(name)
          seen.get(key) match {
            case Some(existing) =>
              val selection = AssemblyReport.selectPreferred(existing, component)
              seen.update(key, selection.selected)
              GlobalRuntimeContext.current.foreach(
                _.assemblyReport.addWarning(
                  AssemblyReport.duplicateComponentWarning(
                    componentName = name,
                    selected = selection.selected,
                    dropped = selection.dropped,
                    reason = selection.reason
                  )
                )
              )
              observe_warn(
                s"duplicate component collapsed name=${name} kept=${selection.selected.origin} dropped=${selection.dropped.map(_.origin).mkString(",")} reason=${selection.reason}"
              )
            case None =>
              seen += key -> component
          }
        }
      if (enabled) {
        addAll(_discover_components(workspace)(subsystem))
      }
      if (specs.nonEmpty) {
        addAll(_discover_from_repositories(specs)(subsystem))
      }
      if (factoryClasses.nonEmpty) {
        addAll(_discover_from_component_factories(factoryClasses)(subsystem))
      }
      components ++= seen.values
      components.result()
    }
  }

  private def _discover_from_component_factories(
    classNames: Seq[String]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val params = ComponentCreate(subsystem, ComponentOrigin.Main)
      classNames.flatMap { name =>
        _load_component_factory(name) match {
          case Left(message) =>
            observe_warn(message)
            Nil
          case Right(factory) =>
            try {
              factory.create(params)
            } catch {
              case NonFatal(e) =>
                observe_warn(s"component factory failed class=${name} message=${e.getMessage}")
                Nil
            }
        }
      }
    }

  private def _load_component_factory(
    className: String
  ): Either[String, Component.Factory] =
    try {
      val loader = Thread.currentThread.getContextClassLoader
      val clazz = Class.forName(className, true, loader)
      if (!classOf[Component.Factory].isAssignableFrom(clazz)) {
        Left(s"component factory class is not a Component.Factory: ${className}")
      } else {
        val ctor = clazz.getDeclaredConstructor()
        ctor.setAccessible(true)
        Right(ctor.newInstance().asInstanceOf[Component.Factory])
      }
    } catch {
      case NonFatal(e) =>
        Left(s"failed to load component factory class=${className} message=${e.getMessage}")
    }

  private def _trace_component_dir_extras(
    extras: Subsystem => Seq[Component]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val components = extras(subsystem)
      if (components.nonEmpty) {
        val modeLabel = GlobalRuntimeContext.current
          .flatMap(ctx => Option(ctx.runtimeMode))
          .map(_.name)
          .getOrElse("unknown")
        observe_trace(
          s"[component-dir] mode=${modeLabel} loaded components=${components.map(_.core.name).mkString(",")}"
        )
      }
      components
    }

  private def _with_resolved_subsystem_descriptor_arg(
    configuration: ResolvedConfiguration,
    specs: Vector[ComponentRepository.Specification],
    args: Array[String]
  ): Array[String] = {
    val alreadySpecified =
      args.exists(_.startsWith(s"--${RuntimeConfig.SubsystemDescriptorKey}=")) ||
        args.exists(_.startsWith(s"--${RuntimeConfig.SubsystemFileKey}=")) ||
        args.sliding(2).exists {
          case Array(k, _) =>
            k == s"--${RuntimeConfig.SubsystemDescriptorKey}" ||
              k == s"--${RuntimeConfig.SubsystemFileKey}"
          case _ =>
            false
        }
    if (alreadySpecified) {
      args
    } else {
      _subsystem_name(configuration, args)
        .flatMap(name => _resolve_subsystem_descriptor_entry(specs, name))
        .map { case (spec, descriptor) =>
          val repoArgs =
            _spec_argument(spec)
              .filterNot(arg => _has_component_repository_config_arg(args, arg))
              .map(arg => Array(s"--${RuntimeConfig.ComponentRepositoryKey}=${arg}"))
              .getOrElse(Array.empty[String])
          args ++ repoArgs ++ Array(s"--${RuntimeConfig.SubsystemFileKey}=${descriptor.path}")
        }
        .getOrElse(args)
    }
  }

  private def _resolve_subsystem_descriptor_entry(
    specs: Vector[ComponentRepository.Specification],
    subsystemName: String
  ): Option[(ComponentRepository.Specification, org.goldenport.cncf.subsystem.GenericSubsystemDescriptor)] =
    specs.iterator.flatMap { spec =>
      spec.resolveSubsystemDescriptor(subsystemName).map(spec -> _)
    }.toSeq.headOption

  private def _subsystem_name(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _subsystem_name_from_args(args).orElse(GenericSubsystemFactory.subsystemName(configuration))

  private def _subsystem_name_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current == s"--${RuntimeConfig.SubsystemNameKey}" && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else if (current.startsWith(s"--${RuntimeConfig.SubsystemNameKey}=")) {
        return Option(current.drop(s"--${RuntimeConfig.SubsystemNameKey}=".length)).map(_.trim).filter(_.nonEmpty)
      }
      i += 1
    }
    None
  }

  private def _spec_argument(
    spec: ComponentRepository.Specification
  ): Option[String] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(baseDir) =>
        Some(s"component-dir:${baseDir}")
      case ComponentRepository.ScalaCliRepository.Specification(baseDir) =>
        Some(s"scala-cli:${baseDir}")
      case _ =>
        None
    }

  private def _has_component_repository_config_arg(
    args: Array[String],
    value: String
  ): Boolean =
    args.contains(s"--${RuntimeConfig.ComponentRepositoryKey}=${value}") ||
      args.sliding(2).exists {
        case Array(currentKey, currentValue) =>
          currentKey == s"--${RuntimeConfig.ComponentRepositoryKey}" && currentValue == value
        case _ => false
      }

  private def _class_dirs_(
    workspace: Option[Path]
  ): Vector[Path] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val scalaCliRoot = workspace.getOrElse(cwd)
    val scalaCli = _scala_cli_classes_dirs_(scalaCliRoot)
    val sbt = _sbt_classes_dirs_(cwd)
    (scalaCli ++ sbt).distinct
  }

  private def _package_prefixes_(): Vector[String] = {
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

  // private def _bootstrap_core(): org.goldenport.cncf.component.Component.Core = {
  //   val name = "bootstrap"
  //   val componentId = ComponentId(name)
  //   val instanceId = ComponentInstanceId.default(componentId)
  //   org.goldenport.cncf.component.Component.Core.create(
  //     name,
  //     componentId,
  //     instanceId,
  //     _empty_protocol()
  //   )
  // }

  private def _scala_cli_classes_dirs_(workspace: Path): Vector[Path] = {
    val root = workspace.resolve(".scala-build")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
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

  private def _sbt_classes_dirs_(baseDir: Path): Vector[Path] = {
    val root = baseDir.resolve("target")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isDirectory(p))
          .filter(p => p.getFileName.toString == "classes")
          .filter(p => _is_scala_target_(p))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _is_scala_target_(classesDir: Path): Boolean = {
    val parent = classesDir.getParent
    if (parent == null) {
      false
    } else {
      val name = parent.getFileName.toString
      name.startsWith("scala-")
    }
  }

  private def _discover_from_class_dirs_(
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String]
  ): Seq[Component] = {
    if (classDirs.isEmpty) {
      Nil
    } else {
      val loader = _class_loader_(classDirs)
      val service = _discover_service_loader_(loader, params)
      if (service.nonEmpty) {
        service
      } else {
        _discover_by_scan_(loader, params, classDirs, packagePrefixes)
      }
    }
  }

  private def _class_loader_(
    classDirs: Seq[Path]
  ): URLClassLoader = {
    val urls = classDirs.map(_.toUri.toURL).toArray
    new URLClassLoader(urls, getClass.getClassLoader)
  }

  private def _discover_service_loader_(
    loader: URLClassLoader,
    params: ComponentCreate
  ): Vector[Component] = {
    val components =
      ServiceLoader.load(classOf[Component], loader).iterator.asScala.toVector
        .filter(_.getClass.getClassLoader eq loader)
    val factories =
      ServiceLoader
        .load(classOf[Component.Factory], loader)
        .iterator
        .asScala
        .toVector
        .filter(_.getClass.getClassLoader eq loader)
    val fromFactories = factories.flatMap(_.create(params))
    val direct = components.map(_initialize_component_(params))
    direct ++ fromFactories
  }

  private def _discover_by_scan_(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String]
  ): Vector[Component] = {
    val seen = mutable.Set.empty[String]
    val results = Vector.newBuilder[Component]
    classDirs.foreach { root =>
      _class_files_(root).foreach { classFile =>
        val className = _class_name_(root, classFile)
        if (_accept_class_(className, packagePrefixes) && !seen.contains(className)) {
          seen += className
          observe_trace(s"[discover:classes] considering $className")
          _load_component_(loader, className, params).foreach { comp =>
            observe_trace(s"[discover:classes] loaded component ${comp.core.name} from $className")
            results += comp
          }
        }
      }
    }
    results.result()
  }

  private def _initialize_component_(
    params: ComponentCreate
  )(
    comp: Component
  ): Component = {
    val core = Component.createScriptCore()
    val init = params.toInit(core)
    comp.initialize(init)
    comp
  }

  private def _class_files_(root: Path): Vector[Path] = {
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

  private def _class_name_(root: Path, classFile: Path): String = {
    val relative = root.relativize(classFile).toString
    val noExt =
      if (relative.endsWith(".class")) {
        relative.substring(0, relative.length - ".class".length)
      } else {
        relative
      }
    noExt.replace('/', '.').replace('\\', '.')
  }

  private def _accept_class_(
    name: String,
    packagePrefixes: Seq[String]
  ): Boolean = {
    if (packagePrefixes.isEmpty) {
      true
    } else {
      packagePrefixes.exists(prefix => name.startsWith(prefix))
    }
  }

  private def _load_component_(
    loader: URLClassLoader,
    className: String,
    params: ComponentCreate
  ): Option[Component] = {
    try {
      val clazz = Class.forName(className, false, loader)
      if (classOf[Component.Factory].isAssignableFrom(clazz)) {
        // Factory classes are resolved through the component class path
        // so preferred impl factories can win during ComponentProvider bootstrap.
        None
      } else if (classOf[Component].isAssignableFrom(clazz)) {
        observe_trace(s"[discover:classes] instantiating class component $className")
        ComponentProvider
          .provide(
            ComponentSource.ClassDef(clazz.asInstanceOf[Class[_ <: Component]], className),
            params.subsystem,
            params.origin
          )
          .toOption
      } else {
        None
      }
    } catch {
      case _: Throwable => None
    }
  }

  private def _truthy_(value: String): Boolean = {
    val v = value.trim.toLowerCase
    v == "1" || v == "true" || v == "yes" || v == "on"
  }

  private def _config_string(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    configuration.get[String](key).toOption.flatten

  private def _config_truthy(
    configuration: ResolvedConfiguration,
    key: String
  ): Boolean =
    _config_string(configuration, key).exists(_truthy_)

  private def _resolve_configuration(
    cwd: Path,
    args: Array[String]
  ): ResolvedConfiguration = {
    val basesources = ConfigurationSources.standard(cwd, applicationname = "cncf", args = Map.empty)
    val explicitconfigs = _explicit_config_sources(cwd, _config_args(args))
    val sources = ConfigurationSources(basesources.sources ++ explicitconfigs)
    ConfigurationResolver.default.resolve(sources) match {
      case Consequence.Success(resolved) =>
        resolved
      case Consequence.Failure(_) =>
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    }
  }

  private def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ) = {
    val files = _split_config_paths(configargs.get("cncf.config.file")) ++
      _split_config_paths(configargs.get("cncf.config.files"))
    files.distinct.map { path =>
      val p = _normalize_config_path(cwd, path)
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Arguments,
        path = p,
        rank = ConfigurationSource.Rank.Arguments,
        loader = new SimpleFileConfigLoader
      )
    }.toVector
  }

  private def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    val p = Paths.get(path)
    if (p.isAbsolute) p.normalize else cwd.resolve(p).normalize
  }

  private def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    val entries = scala.collection.mutable.Map.empty[String, String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--") && current.contains("=")) {
        val raw = current.drop(2)
        val idx = raw.indexOf('=')
        if (idx > 0) {
          entries += raw.substring(0, idx) -> raw.substring(idx + 1)
        }
        i += 1
      } else if (current.startsWith("--") && i + 1 < args.length && !args(i + 1).startsWith("--")) {
        entries += current.drop(2) -> args(i + 1)
        i += 2
      } else {
        i += 1
      }
    }
    entries.toMap
  }
}
