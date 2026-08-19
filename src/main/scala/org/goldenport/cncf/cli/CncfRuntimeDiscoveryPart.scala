package org.goldenport.cncf.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeProcessExitPolicy
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.subsystem.GenericSubsystemFactory
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] trait CncfRuntimeDiscoveryPart {
  this: GlobalObservable =>
  private[cli] def _subsystem_name(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _subsystem_name_from_args(args)
      .orElse(org.goldenport.cncf.subsystem.GenericSubsystemFactory.subsystemName(configuration))

  private[cli] def _component_name(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _component_name_from_args(args)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.componentNameKey))

  private[cli] def _component_version(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _component_version_from_args(args)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.componentVersionKey))

  private[cli] def _subsystem_name_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (_is_option_name(current, _subsystem_name_keys) && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else {
        _option_value(current, _subsystem_name_keys) match {
          case Some(value) => return Some(value)
          case None => ()
        }
      }
      i += 1
    }
    None
  }

  private[cli] def _component_name_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (_is_option_name(current, _component_name_keys) && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else {
        _option_value(current, _component_name_keys) match {
          case Some(value) => return Some(value)
          case None => ()
        }
      }
      i += 1
    }
    None
  }

  private[cli] def _component_version_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (_is_option_name(current, _component_version_keys) && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else {
        _option_value(current, _component_version_keys) match {
          case Some(value) => return Some(value)
          case None => ()
        }
      }
      i += 1
    }
    None
  }

  private[cli] def _strip_invocation_selection_args(
    args: Array[String]
  ): Array[String] = {
    val buffer = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (
        _is_option_name(current, _subsystem_name_keys ++ _component_name_keys ++ _component_version_keys)
      ) {
        i += (if (i + 1 < args.length) 2 else 1)
      } else if (
        _has_option_value(current, _subsystem_name_keys ++ _component_name_keys ++ _component_version_keys)
      ) {
        i += 1
      } else {
        buffer += current
        i += 1
      }
    }
    buffer.result().toArray
  }

  private[cli] def _subsystem_name_keys: Vector[String] =
    Vector(
      RuntimeConfig.subsystemNameKey,
      RuntimeConfig.runtimeSubsystemNameKey,
      "cncf.subsystem",
      "cncf.runtime.subsystem"
    )

  private[cli] def _component_name_keys: Vector[String] =
    Vector(
      RuntimeConfig.componentNameKey,
      RuntimeConfig.runtimeComponentNameKey,
      "cncf.component",
      "cncf.runtime.component"
    )

  private[cli] def _component_version_keys: Vector[String] =
    Vector(
      RuntimeConfig.componentVersionKey,
      RuntimeConfig.runtimeComponentVersionKey,
      "cncf.component.version",
      "cncf.runtime.component.version"
    )

  private[cli] def _subsystem_descriptor_keys: Vector[String] =
    Vector(
      RuntimeConfig.subsystemDescriptorKey,
      RuntimeConfig.subsystemFileKey,
      RuntimeConfig.subsystemDevDirKey,
      RuntimeConfig.subsystemSarDirKey,
      RuntimeConfig.runtimeSubsystemDescriptorKey,
      RuntimeConfig.runtimeSubsystemFileKey,
      RuntimeConfig.runtimeSubsystemDevDirKey,
      RuntimeConfig.runtimeSubsystemSarDirKey,
      "cncf.subsystem.descriptor",
      "cncf.subsystem.file",
      "cncf.subsystem.dev.dir",
      "cncf.subsystem.sar.dir",
      "cncf.runtime.subsystem.descriptor",
      "cncf.runtime.subsystem.file",
      "cncf.runtime.subsystem.dev.dir",
      "cncf.runtime.subsystem.sar.dir"
    )

  private[cli] def _normalize_source_args(
    args: Array[String]
  ): Array[String] = {
    val buffer = Vector.newBuilder[String]
    var changed = false
    var i = 0
    var aftersentinel = false
    while (i < args.length) {
      val current = args(i)
      if (aftersentinel) {
        buffer += current
        i += 1
      } else if (current == "--") {
        buffer += current
        aftersentinel = true
        i += 1
      } else if (current.startsWith("--subsystem-sar-dir=")) {
        buffer += s"--${RuntimeConfig.subsystemSarDirKey}=${current.stripPrefix("--subsystem-sar-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--subsystem-dev-dir=")) {
        buffer += s"--${RuntimeConfig.subsystemDevDirKey}=${current.stripPrefix("--subsystem-dev-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-car-dir=")) {
        buffer += s"--${RuntimeConfig.componentCarDirKey}=${current.stripPrefix("--component-car-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-dev-dir=")) {
        buffer += s"--${RuntimeConfig.componentDevDirKey}=${current.stripPrefix("--component-dev-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-file=")) {
        buffer += s"--${RuntimeConfig.componentFileKey}=${current.stripPrefix("--component-file=")}"
        changed = true
        i += 1
      } else if (current == "--subsystem-sar-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.subsystemSarDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--subsystem-dev-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.subsystemDevDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-car-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.componentCarDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-dev-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.componentDevDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-file" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.componentFileKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else {
        buffer += current
        i += 1
      }
    }
    val result = buffer.result().toArray
    if (changed) result else args
  }

  private[cli] def _normalize_test_mode_args(
    cwd: Path,
    args: Array[String]
  ): Array[String] =
    args.headOption match {
      case Some("test") =>
        val buffer = Vector.newBuilder[String]
        buffer += s"--${RuntimeConfig.operationModeKey}=test"
        var i = 1
        var done = false
        while (i < args.length && !done) {
          val current = args(i)
          if (current.startsWith("--test-config=")) {
            val value = current.stripPrefix("--test-config=").trim
            if (value.isEmpty)
              throw new IllegalArgumentException("--test-config requires a value")
            buffer += s"--${RuntimeConfig.TEST_DESCRIPTOR_KEY}=${value}"
            i += 1
          } else if (current == "--test-config") {
            if (i + 1 >= args.length)
              throw new IllegalArgumentException("--test-config requires a value")
            buffer += s"--${RuntimeConfig.TEST_DESCRIPTOR_KEY}=${args(i + 1)}"
            i += 2
          } else if (current.startsWith("--home=")) {
            val value = current.stripPrefix("--home=").trim
            if (value.isEmpty)
              throw new IllegalArgumentException("--home requires a value")
            _append_test_home_args(buffer, value)
            i += 1
          } else if (current == "--home") {
            if (i + 1 >= args.length)
              throw new IllegalArgumentException("--home requires a value")
            _append_test_home_args(buffer, args(i + 1))
            i += 2
          } else if (current == "--temporary-home") {
            val temporaryhome = _create_temporary_test_home(cwd)
            _append_test_home_args(buffer, temporaryhome.toString)
            buffer += s"--${RuntimeConfig.TEST_HOME_TEMPORARY_KEY}=true"
            i += 1
          } else {
            done = true
          }
        }
        (buffer.result() ++ args.drop(i)).toArray
      case _ =>
        args
    }

  private[cli] def _append_test_home_args(
    buffer: scala.collection.mutable.Builder[String, Vector[String]],
    path: String
  ): Unit = {
    buffer += s"--${RuntimeConfig.TEST_HOME_MODE_KEY}=isolated"
    buffer += s"--${RuntimeConfig.TEST_HOME_PATH_KEY}=${path}"
    buffer += s"--${RuntimeConfig.TEST_HOME_INHERIT_RUNTIME_KEY}=true"
    buffer += s"--${RuntimeConfig.TEST_HOME_INHERIT_REPOSITORIES_KEY}=true"
    buffer += s"--${RuntimeConfig.TEST_HOME_INHERIT_CREDENTIALS_KEY}=false"
    buffer += s"--${RuntimeConfig.TEST_HOME_INHERIT_LOCAL_DATA_KEY}=false"
  }

  private[cli] def _create_temporary_test_home(
    cwd: Path
  ): Path = {
    val root = cwd.resolve("target").resolve("cncf.d").normalize
    Files.createDirectories(root)
    Files.createTempDirectory(root, "test-home-").normalize
  }

  private[cli] def _is_option_name(
    current: String,
    keys: Vector[String]
  ): Boolean =
    keys.exists(key => current == s"--${key}")

  private[cli] def _has_option_value(
    current: String,
    keys: Vector[String]
  ): Boolean =
    keys.exists(key => current.startsWith(s"--${key}="))

  private[cli] def _option_value(
    current: String,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator.flatMap { key =>
      val prefix = s"--${key}="
      if (current.startsWith(prefix))
        Option(current.drop(prefix.length)).map(_.trim).filter(_.nonEmpty)
      else
        None
    }.toSeq.headOption

  private[cli] def _resolve_subsystem_descriptor_entry(
    specs: Vector[ComponentRepository.Specification],
    subsystemname: String
  ): Option[(ComponentRepository.Specification, GenericSubsystemDescriptor)] =
    specs.iterator.flatMap { spec =>
      spec.resolveSubsystemDescriptor(subsystemname).map(spec -> _)
    }.toSeq.headOption

  private[cli] def _resolve_component_descriptor_entry(
    specs: Vector[ComponentRepository.Specification],
    componentname: String
  ): Option[(ComponentRepository.Specification, org.goldenport.cncf.component.ComponentDescriptor)] =
    specs.iterator.flatMap { spec =>
      spec.resolveComponentDescriptor(componentname).map(spec -> _)
    }.toSeq.headOption

  private[cli] def _resolve_component_archive_entry(
    specs: Vector[ComponentRepository.Specification],
    componentname: String,
    version: Option[String] = None
  ): Option[java.nio.file.Path] =
    specs.iterator.flatMap(_.resolveComponentArchivePath(componentname, version)).toSeq.headOption

  private[cli] def _spec_argument(
    spec: ComponentRepository.Specification
  ): Option[String] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(basedir) =>
        Some(s"component-dir:${basedir}")
      case ComponentRepository.ComponentFileRepository.Specification(file) =>
        Some(s"component-file:${file}")
      case ComponentRepository.ComponentDevDirRepository.Specification(basedir) =>
        Some(s"component-dev-dir:${basedir}")
      case ComponentRepository.SubsystemDevDirRepository.Specification(basedir) =>
        Some(s"subsystem-dev-dir:${basedir}")
      case ComponentRepository.StandardRepository.Specification(_, baseurl, _) =>
        Some(s"standard-repository:${baseurl}")
      case ComponentRepository.ScalaCliRepository.Specification(basedir) =>
        Some(s"scala-cli:${basedir}")
    }

  private[cli] def _has_component_dir_config_arg(
    args: Array[String],
    value: String
  ): Boolean =
    args.contains(s"--${RuntimeConfig.componentDirKey}=${value}") ||
      args.sliding(2).exists {
        case Array(currentkey, currentvalue) =>
          currentkey == s"--${RuntimeConfig.componentDirKey}" && currentvalue == value
        case _ => false
      }

  private[cli] def _component_activation_keys: Vector[String] =
    Vector(
      RuntimeConfig.componentDirKey,
      RuntimeConfig.componentFileKey,
      RuntimeConfig.runtimeComponentFileKey,
      RuntimeConfig.componentDevDirKey,
      RuntimeConfig.repositoryComponentDevDirKey,
      RuntimeConfig.componentCarDirKey,
      RuntimeConfig.subsystemDevDirKey,
      RuntimeConfig.subsystemSarDirKey,
      RuntimeConfig.runtimeSubsystemDevDirKey,
      RuntimeConfig.runtimeSubsystemSarDirKey,
      "cncf.component.dir",
      "cncf.component.file",
      "cncf.runtime.component.file",
      "cncf.component.dev.dir",
      "cncf.repository.component.dev.dir",
      "cncf.component.car.dir",
      "cncf.subsystem.dev.dir",
      "cncf.subsystem.sar.dir",
      "cncf.runtime.subsystem.dev.dir",
      "cncf.runtime.subsystem.sar.dir"
    )

  private[cli] def _has_component_activation_arg(
    args: Array[String]
  ): Boolean =
    args.exists { arg =>
      arg == "--component-file" ||
        arg == "--component-dev-dir" ||
        arg == "--component-car-dir" ||
        arg == "--subsystem-dev-dir" ||
        arg == "--subsystem-sar-dir" ||
        arg.startsWith("--component-dev-dir=") ||
        arg.startsWith("--component-car-dir=") ||
        arg.startsWith("--subsystem-dev-dir=") ||
        arg.startsWith("--subsystem-sar-dir=") ||
        _has_option_value(arg, _component_activation_keys)
    }

  private[cli] def _is_component_activation_arg(
    arg: String
  ): Boolean =
    arg == "--component-file" ||
      arg == "--component-dev-dir" ||
      arg == "--component-car-dir" ||
      arg == "--subsystem-dev-dir" ||
      arg == "--subsystem-sar-dir" ||
      _is_option_name(arg, _component_activation_keys)

  private[cli] def _active_spec_argument(
    spec: ComponentRepository.Specification
  ): Option[(String, String)] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(basedir) =>
        Some((RuntimeConfig.componentDirKey, basedir.toString))
      case ComponentRepository.ComponentFileRepository.Specification(file) =>
        Some((RuntimeConfig.componentFileKey, file.toString))
      case ComponentRepository.ComponentDevDirRepository.Specification(basedir) =>
        Some((RuntimeConfig.componentDevDirKey, basedir.toString))
      case ComponentRepository.SubsystemDevDirRepository.Specification(basedir) =>
        Some((RuntimeConfig.subsystemDevDirKey, basedir.toString))
      case _ =>
        None
    }

  private[cli] def _discover_components(
    workspace: Option[Path]
  ): Subsystem => Seq[Component] = {
    val classdirs = _class_dirs(workspace)
    if (classdirs.isEmpty) {
      _ => Nil
    } else {
      (subsystem: Subsystem) => {
        val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
        _discover_from_class_dirs(params, classdirs, _package_prefixes())
      }
    }
  }

  private[cli] def _discover_from_repositories(
    specs: Seq[ComponentRepository.Specification],
    assemblysearchspecs: Seq[ComponentRepository.Specification]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val declareddescriptors = subsystem.descriptor.map(_.toComponentDescriptors).getOrElse(Vector.empty)
      val descriptors = ComponentRepository.assemblyPreflightDescriptors(specs, declareddescriptors)
      val allspecs = specs ++ assemblysearchspecs
      val developmentclaims = developmentComponentClaims(specs, assemblysearchspecs)
      val repositoryentries = allspecs.zipWithIndex.flatMap { case (spec, index) =>
        val origin = _origin_for_spec(spec)
        val activedescriptors =
          if (index < specs.size)
            ComponentRepository.descriptorsForSpecification(spec, allspecs.take(index), descriptors, developmentclaims)
          else
            ComponentRepository.unresolvedDescriptorsForSearch(allspecs.take(index), descriptors, developmentclaims)
        if (descriptors.nonEmpty && activedescriptors.isEmpty) {
          None
        } else {
          val params = ComponentCreate(subsystem, origin, activedescriptors)
          Some(spec.build(params.withOrigin(origin)) -> (index < specs.size))
        }
      }.toVector
      val repositories = repositoryentries.map(_._1)
      val discoveryrepositories = repositoryentries.collect {
        case (repository, true) => repository
      }
      ComponentRepository.discoverAssembly(repositories, discoveryrepositories)
    }

  private[cncf] def developmentComponentClaims(
    activeSpecifications: Seq[ComponentRepository.Specification],
    searchSpecifications: Seq[ComponentRepository.Specification]
  ): Map[ComponentRepository.Specification, Set[(ComponentId, String)]] =
    ComponentRepository.developmentComponentClaims(activeSpecifications ++ searchSpecifications)

  private[cli] def _origin_for_spec(
    spec: ComponentRepository.Specification
  ): ComponentOrigin =
    spec match {
      case _: ComponentRepository.ComponentDirRepository.Specification =>
        ComponentOrigin.Repository("component-dir")
      case _: ComponentRepository.ComponentFileRepository.Specification =>
        ComponentOrigin.Repository("component-file")
      case _: ComponentRepository.ComponentDevDirRepository.Specification =>
        ComponentOrigin.Repository("component-dev-dir")
      case _: ComponentRepository.SubsystemDevDirRepository.Specification =>
        ComponentOrigin.Repository("subsystem-dev-dir")
      case _: ComponentRepository.StandardRepository.Specification =>
        ComponentOrigin.Repository("standard-repository")
      case _: ComponentRepository.ScalaCliRepository.Specification =>
        ComponentOrigin.Repository("scala-cli")
    }

  private[cli] def _component_extra_function(
    specs: Vector[ComponentRepository.Specification],
    assemblysearchspecs: Vector[ComponentRepository.Specification],
    enabled: Boolean,
    workspace: Option[Path],
    factoryclasses: Vector[String]
  ): Subsystem => Seq[Component] = {
    (subsystem: Subsystem) => {
      val components = Vector.newBuilder[Component]
      val seen = mutable.LinkedHashMap.empty[String, Component]
      def _add_all_(xs: Seq[Component]): Unit =
        xs.foreach { component =>
          val name = component.core.name
          val key = NamingConventions.toComparisonKey(name)
          seen.get(key) match {
            case Some(existing) =>
              val selection = AssemblyReport.selectPreferred(existing, component)
              seen.update(key, selection.selected)
              if (!AssemblyReport.isSameAssemblySource(existing, component)) {
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
              }
            case None =>
              seen += key -> component
          }
        }
      if (enabled) {
        _add_all_(_discover_components(workspace)(subsystem))
      }
      if (specs.nonEmpty) {
        _add_all_(_discover_from_repositories(specs, assemblysearchspecs)(subsystem))
      }
      if (factoryclasses.nonEmpty) {
        _add_all_(_discover_from_component_factories(factoryclasses)(subsystem))
      }
      components ++= seen.values
      components.result()
    }
  }

  private[cli] def _discover_from_component_factories(
    classnames: Seq[String]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val params = ComponentCreate(subsystem, ComponentOrigin.Main)
      classnames.flatMap { name =>
        _load_componentfactory(name) match {
          case Left(message) =>
            observe_warn(message)
            Nil
          case Right(factory) =>
            try {
              factory.create(params).participants
            } catch {
              case NonFatal(e) =>
                observe_warn(s"component factory failed class=${name} message=${e.getMessage}")
                Nil
            }
        }
      }
    }

  private[cli] def _load_componentfactory(
    classname: String
  ): Either[String, Component.BundleFactory] =
    try {
      val loader = Thread.currentThread.getContextClassLoader
      val clazz = Class.forName(classname, true, loader)
      if (!classOf[Component.BundleFactory].isAssignableFrom(clazz)) {
        Left(s"component factory class is not a Component.BundleFactory: ${classname}")
      } else {
        val ctor = clazz.getDeclaredConstructor()
        ctor.setAccessible(true)
        Right(ctor.newInstance().asInstanceOf[Component.BundleFactory])
      }
    } catch {
      case NonFatal(e) =>
        Left(s"failed to load component factory class=${classname} message=${e.getMessage}")
    }

  private[cli] def _trace_component_dir_extras(
    extras: Subsystem => Seq[Component]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val components = extras(subsystem)
      if (components.nonEmpty) {
        val modelabel = GlobalRuntimeContext.current
          .flatMap(ctx => Option(ctx.runtimeMode))
          .map(_.name)
          .getOrElse("unknown")
        observe_trace(
          s"[component-dir] mode=${modelabel} loaded components=${components.map(_.core.name).mkString(",")}"
        )
      }
      components
    }

  private[cli] def _class_dirs(
    workspace: Option[Path]
  ): Vector[Path] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val scalacliroot = workspace.getOrElse(cwd)
    val scalacli = _scala_cli_classes_dirs(scalacliroot)
    val sbt = _sbt_classes_dirs(cwd)
    (scalacli ++ sbt).distinct
  }

  private[cli] def _package_prefixes(): Vector[String] =
    sys.env.get("TEXTUS_DISCOVER_PREFIX").orElse(sys.env.get("CNCF_DISCOVER_PREFIX")) match {
      case Some(value) =>
        value.split(",").map(_.trim).filter(_.nonEmpty).toVector
      case None =>
        Vector.empty
    }

  private[cli] def _scala_cli_classes_dirs(workspace: Path): Vector[Path] = {
    val root = workspace.resolve(".scala-build")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.filter(p => Files.isDirectory(p)).filter(p => p.getFileName.toString == "classes").toVector
      } finally {
        stream.close()
      }
    }
  }

  private[cli] def _sbt_classes_dirs(basedir: Path): Vector[Path] = {
    val root = basedir.resolve("target")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala
          .filter(p => Files.isDirectory(p))
          .filter(p => p.getFileName.toString == "classes")
          .filter(p => _is_scala_target(p))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private[cli] def _is_scala_target(classesdir: Path): Boolean = {
    val parent = classesdir.getParent
    if (parent == null) false
    else parent.getFileName.toString.startsWith("scala-")
  }

  private[cli] def _discover_from_class_dirs(
    params: ComponentCreate,
    classdirs: Seq[Path],
    packageprefixes: Seq[String]
  ): Seq[Component] =
    if (classdirs.isEmpty) {
      Nil
    } else {
      val loader = _class_loader(classdirs)
      val service = _discover_service_loader(loader, params, classdirs)
      if (service.nonEmpty) service
      else _discover_by_scan(loader, params, classdirs, packageprefixes)
    }

  private[cli] def _class_loader(
    classdirs: Seq[Path]
  ): URLClassLoader = {
    val urls = classdirs.map(_.toUri.toURL).toArray
    new URLClassLoader(urls, getClass.getClassLoader)
  }

  private[cli] def _discover_service_loader(
    loader: URLClassLoader,
    params: ComponentCreate,
    classdirs: Seq[Path]
  ): Vector[Component] = {
    def _is_from_class_dirs_(x: AnyRef): Boolean =
      Option(x.getClass.getProtectionDomain)
        .flatMap(pd => Option(pd.getCodeSource))
        .flatMap(cs => Option(cs.getLocation))
        .flatMap(url => scala.util.Try(Paths.get(url.toURI)).toOption)
        .exists(path => classdirs.exists(dir => path.normalize.startsWith(dir.normalize)))

    val components =
      ServiceLoader.load(classOf[Component], loader).iterator.asScala.toVector
        .filter(x => _is_from_class_dirs_(x))
    val factories =
      ServiceLoader
        .load(classOf[Component.BundleFactory], loader)
        .iterator
        .asScala
        .toVector
        .filter(x => _is_from_class_dirs_(x))
    val fromfactories = factories.flatMap(_.create(params).participants)
    val direct = components.map(_initialize_component(params))
    if (fromfactories.nonEmpty)
      fromfactories ++ direct.filterNot(d => fromfactories.exists(f => NamingConventions.equivalentByNormalized(f.name, d.name)))
    else
      direct
  }

  private[cli] def _discover_by_scan(
    loader: URLClassLoader,
    params: ComponentCreate,
    classdirs: Seq[Path],
    packageprefixes: Seq[String]
  ): Vector[Component] = {
    val seen = mutable.Set.empty[String]
    val results = Vector.newBuilder[Component]
    classdirs.foreach { root =>
      _class_files(root).foreach { classfile =>
        val classname = _class_name(root, classfile)
        if (_accept_class(classname, packageprefixes) && !seen.contains(classname)) {
          seen += classname
          observe_trace(s"[discover:classes] considering $classname")
          _load_component(loader, classname, params).foreach { comp =>
            observe_trace(s"[discover:classes] loaded component ${comp.core.name} from $classname")
            results += comp
          }
        }
      }
    }
    results.result()
  }

  private[cli] def _initialize_component(
    params: ComponentCreate
  )(
    comp: Component
  ): Component = {
    val core = Component.createScriptCore()
    val init = params.toInit(core)
    comp.initialize(init)
    comp
  }

  private[cli] def _class_files(root: Path): Vector[Path] =
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala
          .filter(p => Files.isRegularFile(p))
          .filter(p => p.toString.endsWith(".class"))
          .toVector
      } finally {
        stream.close()
      }
    }

  private[cli] def _class_name(root: Path, classfile: Path): String = {
    val relative = root.relativize(classfile).toString
    val noext =
      if (relative.endsWith(".class"))
        relative.substring(0, relative.length - ".class".length)
      else
        relative
    noext.replace('/', '.').replace('\\', '.')
  }

  private[cli] def _accept_class(
    name: String,
    packageprefixes: Seq[String]
  ): Boolean =
    if (packageprefixes.isEmpty) true
    else packageprefixes.exists(prefix => name.startsWith(prefix))

  private[cli] def _load_component(
    loader: URLClassLoader,
    classname: String,
    params: ComponentCreate
  ): Option[Component] =
    try {
      val clazz = Class.forName(classname, false, loader)
      if (classOf[Component.BundleFactory].isAssignableFrom(clazz)) {
        None
      } else if (classOf[Component].isAssignableFrom(clazz)) {
        observe_trace(s"[discover:classes] instantiating class component $classname")
        org.goldenport.cncf.component.repository.ComponentProvider
          .provide(
            org.goldenport.cncf.component.repository.ComponentSource.ClassDef(
              clazz.asInstanceOf[Class[_ <: Component]],
              classname
            ),
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

  private[cli] def _take_process_exit_policy(
    policy: RuntimeProcessExitPolicy,
    args: Array[String]
  ): (RuntimeProcessExitPolicy, Array[String]) =
    RuntimeProcessExitPolicy.admitArguments(policy, args)

  private[cli] def _take_discover_classes(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val enabled =
      args.contains("--discover=classes") ||
        _config_truthy(configuration, RuntimeConfig.discoverClassesKey) ||
        _discover_env_enabled()
    val rest = args.filterNot(_ == "--discover=classes")
    (enabled, rest)
  }

  private[cli] def _take_component_factory_classes(
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
    val configclasses =
      _config_string(configuration, RuntimeConfig.componentFactoryClassKey)
        .toVector
        .flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
    ((configclasses ++ classes.result()).distinct, rest.result().toArray)
  }

  private[cli] def _take_workspace(
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
      workspace.orElse(_config_string(configuration, RuntimeConfig.workspaceKey).map(Paths.get(_)))
    (resolved, buffer.result().toArray)
  }

  private[cli] def _discover_env_enabled(): Boolean =
    sys.env
      .get("TEXTUS_DISCOVER_CLASSES")
      .orElse(sys.env.get("CNCF_DISCOVER_CLASSES"))
      .exists(v => _truthy(v))

  private[cli] def _truthy(p: String): Boolean =
    p.equalsIgnoreCase("true") || p.equalsIgnoreCase("on") || p == "1"

  private[cli] def _config_truthy(
    configuration: ResolvedConfiguration,
    key: String
  ): Boolean =
    _config_string(configuration, key).exists(_truthy)

  private[cli] def _config_string(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    configuration.get[String](key).toOption.flatten.orElse {
      if (key.startsWith("cncf.")) configuration.get[String](key.replaceFirst("^cncf\\.", "textus.")).toOption.flatten
      else None
    }

  private[cli] def _exit_code(c: Consequence[_]): Int =
    c match {
      case Consequence.Success(_) => 0
      case Consequence.Failure(conclusion) =>
        val _ = conclusion
        // TODO When Conclusion/Status supports Long (or an explicit exit/detail code),
        // map it here and return that value.
        1
    }
}
