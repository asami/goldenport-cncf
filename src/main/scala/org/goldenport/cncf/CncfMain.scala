package org.goldenport.cncf

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.ServiceLoader
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{Component, ComponentId, ComponentCreate, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.repository.{ComponentRepository}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  def main(args: Array[String]): Unit = {
    val (reposResult, args1) = _take_component_repository(args)
    val (discover, args2) = _take_discover_classes(args1)
    val (workspace, args3) = _take_workspace(args2)
    val (forceExit, args4) = _take_force_exit(args3)
    val (noExit, rest) = _take_no_exit(args4)
    val enabled = discover || _discover_env_enabled()

    val code: Int =
      try {
        reposResult match {
          case Left(message) =>
            Console.err.println(message)
            2
          case Right(specs) if specs.nonEmpty =>
            val extras = _discover_from_repositories(specs)
            CncfRuntime.runWithExtraComponents(rest, extras)
          case Right(_) if enabled =>
            val extras = _discover_components(workspace)
            CncfRuntime.runWithExtraComponents(rest, extras)
      case Right(_) =>
        CncfRuntime.runExitCode(rest)
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

  private def _take_no_exit(args: Array[String]): (Boolean, Array[String]) = {
    val noexit = args.contains("--no-exit")
    val rest = args.filterNot(_ == "--no-exit")
    (noexit, rest)
  }

  private def _take_force_exit(args: Array[String]): (Boolean, Array[String]) = {
    val forceExit = args.contains("--force-exit")
    val rest = args.filterNot(_ == "--force-exit")
    (forceExit, rest)
  }

  private def _take_component_repository(
    args: Array[String]
  ): (Either[String, Vector[ComponentRepository.Specification]], Array[String]) = {
    val buffer = Vector.newBuilder[String]
    val specs = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg.startsWith("--component-repository=")) {
        specs += arg.stripPrefix("--component-repository=")
        i = i + 1
      } else if (arg == "--component-repository") {
        if (i + 1 >= args.length) {
          return (Left("--component-repository requires a value"), args)
        }
        specs += args(i + 1)
        i = i + 2
      } else {
        buffer += arg
        i = i + 1
      }
    }
    val specValues = specs.result()
    if (specValues.isEmpty) {
      (Right(Vector.empty), buffer.result().toArray)
    } else {
      val cwd = Paths.get("").toAbsolutePath.normalize
      var error: Option[String] = None
      val parsed = Vector.newBuilder[ComponentRepository.Specification]
      specValues.foreach { value =>
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
        case Some(err) => (Left(err), buffer.result().toArray)
        case None => (Right(parsed.result()), buffer.result().toArray)
      }
    }
  }

  private def _take_discover_classes(
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val enabled = args.contains("--discover=classes")
    val rest = args.filterNot(_ == "--discover=classes")
    (enabled, rest)
  }

  private def _take_workspace(
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
    (workspace, buffer.result().toArray)
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
        spec.build(params).discover()
      }
    }
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
        Vector("demo.")
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
    val factories =
      ServiceLoader
        .load(classOf[Component.Factory], loader)
        .iterator
        .asScala
        .toVector
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
          _load_component_(loader, className).foreach { comp =>
            results += _initialize_component_(params)(comp)
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
      false
    } else {
      packagePrefixes.exists(prefix => name.startsWith(prefix))
    }
  }

  private def _load_component_(
    loader: URLClassLoader,
    className: String
  ): Option[Component] = {
    try {
      val clazz = Class.forName(className, false, loader)
      if (!classOf[Component].isAssignableFrom(clazz)) {
        None
      } else if (className.endsWith("$")) {
        _load_module_instance_(clazz)
      } else {
        _load_class_instance_(clazz)
      }
    } catch {
      case _: Throwable => None
    }
  }

  private def _load_module_instance_(
    clazz: Class[_]
  ): Option[Component] = {
    try {
      val field = clazz.getField("MODULE$")
      field.get(null) match {
        case c: Component => Some(c)
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

  private def _load_class_instance_(
    clazz: Class[_]
  ): Option[Component] = {
    try {
      val ctor = clazz.getDeclaredConstructor()
      ctor.setAccessible(true)
      ctor.newInstance() match {
        case c: Component => Some(c)
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

  private def _truthy_(value: String): Boolean = {
    val v = value.trim.toLowerCase
    v == "1" || v == "true" || v == "yes" || v == "on"
  }
}
