package org.goldenport.cncf.component

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._
import scala.sys.process._

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.RuntimeConfig

/*
 * @since   May. 16, 2026
 * @version May. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentDependencyManifest(
  provided: Vector[String] = Vector.empty,
  shared: Vector[String] = Vector.empty,
  local: Vector[String] = Vector.empty,
  repositories: Vector[String] = Vector.empty
) {
  def isEmpty: Boolean =
    provided.isEmpty && shared.isEmpty && local.isEmpty && repositories.isEmpty
}

object ComponentDependencyManifest {
  val FILE_NAME: String = "component-dependencies.yaml"

  def load(root: Path): Consequence[ComponentDependencyManifest] = {
    val path = root.resolve(FILE_NAME)
    if (Files.isRegularFile(path))
      parse(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector)
    else
      Consequence.success(ComponentDependencyManifest())
  }

  def parse(lines: Vector[String]): Consequence[ComponentDependencyManifest] = Consequence {
    val parsed = _parse_simple_yaml(lines)
    ComponentDependencyManifest(
      provided = parsed.getOrElse("dependencies.provided", Vector.empty),
      shared = parsed.getOrElse("dependencies.shared", Vector.empty),
      local = parsed.getOrElse("dependencies.local", Vector.empty),
      repositories = parsed.getOrElse("dependencies.repositories", Vector.empty)
    )
  }

  private def _parse_simple_yaml(lines: Vector[String]): Map[String, Vector[String]] = {
    var stack = Vector.empty[(Int, String)]
    var lists = Map.empty[String, Vector[String]]

    def _current_path_ = stack.map(_._2).mkString(".")
    def _drop_to_(indent: Int): Unit =
      stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)

    lines.foreach { raw =>
      val line = _strip_comment(raw)
      if (line.trim.nonEmpty) {
        val indent = line.takeWhile(_ == ' ').length
        val trimmed = line.trim
        if (trimmed.startsWith("- ")) {
          _drop_to_(indent)
          val key = _current_path_
          if (key.nonEmpty)
            lists = lists.updated(key, lists.getOrElse(key, Vector.empty) :+ _unquote(trimmed.substring(2).trim))
        } else {
          val n = trimmed.indexOf(':')
          if (n >= 0) {
            val key = trimmed.substring(0, n).trim
            val rest = trimmed.substring(n + 1).trim
            _drop_to_(indent)
            if (rest.isEmpty)
              stack = stack :+ (indent -> key)
          }
        }
      }
    }
    lists
  }

  private def _strip_comment(s: String): String =
    if (s.trim.startsWith("#")) "" else s

  private def _unquote(s: String): String = {
    val t = s.trim
    if (t.length >= 2 && ((t.head == '"' && t.last == '"') || (t.head == '\'' && t.last == '\'')))
      t.substring(1, t.length - 1)
    else
      t
  }
}

final case class ComponentDependencyConfig(
  resolveEnabled: Boolean = true,
  sharedEnabled: Boolean = true,
  localOverrideEnabled: Boolean = true,
  cacheDir: Option[Path] = None,
  repositories: Vector[String] = Vector.empty,
  coursierCommand: String = ComponentDependencyConfig.DEFAULT_COURSIER_COMMAND
)

object ComponentDependencyConfig {
  val RESOLVE_ENABLED_KEY = RuntimeConfig.ComponentDependenciesResolveEnabledKey
  val CACHE_DIR_KEY = RuntimeConfig.ComponentDependenciesCacheDirKey
  val SHARED_ENABLED_KEY = RuntimeConfig.ComponentDependenciesSharedEnabledKey
  val LOCAL_OVERRIDE_ENABLED_KEY = RuntimeConfig.ComponentDependenciesLocalOverrideEnabledKey
  val REPOSITORIES_KEY = RuntimeConfig.ComponentDependenciesRepositoriesKey
  val DEFAULT_COURSIER_COMMAND = "cs"

  def from(configuration: ResolvedConfiguration): ComponentDependencyConfig =
    ComponentDependencyConfig(
      resolveEnabled = _boolean(configuration, RESOLVE_ENABLED_KEY).getOrElse(true),
      sharedEnabled = _boolean(configuration, SHARED_ENABLED_KEY).getOrElse(true),
      localOverrideEnabled = _boolean(configuration, LOCAL_OVERRIDE_ENABLED_KEY).getOrElse(true),
      cacheDir = RuntimeConfig.getString(configuration, CACHE_DIR_KEY).map(Paths.get(_).normalize),
      repositories = _csv(RuntimeConfig.getString(configuration, REPOSITORIES_KEY)),
      coursierCommand = sys.env.get("CNCF_COURSIER_COMMAND").filter(_.nonEmpty).getOrElse(DEFAULT_COURSIER_COMMAND)
    )

  private def _boolean(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[Boolean] =
    RuntimeConfig.getString(configuration, key).flatMap { value =>
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "true" | "1" | "yes" | "on" => Some(true)
        case "false" | "0" | "no" | "off" => Some(false)
        case _ => None
      }
    }

  private def _csv(value: Option[String]): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
}

final case class ComponentDependencyResolution(
  sharedClasspath: Vector[Path] = Vector.empty,
  localClasspath: Vector[Path] = Vector.empty
) {
  def parentClassLoader(runtimeparent: ClassLoader): ClassLoader =
    ComponentDependencyPool.sharedClassLoader(sharedClasspath, runtimeparent)

  def componentClassLoader(
    componentclasspath: Vector[Path],
    embeddedlocalclasspath: Vector[Path],
    runtimeparent: ClassLoader
  ): URLClassLoader = {
    val parent = parentClassLoader(runtimeparent)
    ComponentLocalFirstClassLoader(componentclasspath ++ embeddedlocalclasspath ++ localClasspath, parent)
  }
}

object ComponentDependencyResolver {
  def resolve(
    root: Path,
    componentname: String,
    configuration: ResolvedConfiguration
  ): Consequence[ComponentDependencyResolution] =
    for {
      manifest <- ComponentDependencyManifest.load(root)
      result <- resolve(manifest, componentname, ComponentDependencyConfig.from(configuration))
    } yield result

  def resolve(
    manifest: ComponentDependencyManifest,
    componentname: String,
    config: ComponentDependencyConfig
  ): Consequence[ComponentDependencyResolution] = {
    if (manifest.isEmpty) {
      Consequence.success(ComponentDependencyResolution())
    } else if (!config.resolveEnabled) {
      Consequence.configurationInvalid(
        s"component dependency resolution is disabled: component=$componentname manifest=${ComponentDependencyManifest.FILE_NAME}"
      )
    } else if (manifest.shared.nonEmpty && !config.sharedEnabled) {
      Consequence.configurationInvalid(
        s"component shared dependency resolution is disabled: component=$componentname dependencies=${manifest.shared.mkString(",")}"
      )
    } else if (manifest.local.nonEmpty && !config.localOverrideEnabled) {
      Consequence.configurationInvalid(
        s"component local dependency override is disabled: component=$componentname dependencies=${manifest.local.mkString(",")}"
      )
    } else {
      val repositories = (manifest.repositories ++ config.repositories).distinct
      for {
        shared <- ComponentDependencyPool.resolveShared(manifest.shared, repositories, config)
        local <- _resolve_local(componentname, manifest.local, repositories, config)
      } yield ComponentDependencyResolution(shared, local)
    }
  }

  private def _resolve_local(
    componentname: String,
    coordinates: Vector[String],
    repositories: Vector[String],
    config: ComponentDependencyConfig
  ): Consequence[Vector[Path]] =
    _reject_protected_local_override(componentname, coordinates).flatMap { _ =>
      CoursierComponentDependencyResolver.resolve(coordinates, repositories, config)
    }

  private def _reject_protected_local_override(
    componentname: String,
    coordinates: Vector[String]
  ): Consequence[Unit] = {
    val rejected = coordinates.filter(_is_protected_coordinate)
    if (rejected.isEmpty) {
      Consequence.success(())
    } else {
      Consequence.configurationInvalid(
        s"component local dependency targets CNCF runtime ABI coordinates: component=$componentname dependencies=${rejected.mkString(",")}"
      )
    }
  }

  private def _is_protected_coordinate(coordinate: String): Boolean = {
    val group = coordinate.split(":").headOption.getOrElse("")
    group == "org.goldenport" || group == "org.simplemodeling" || group == "org.scala-lang" || group == "org.scala-lang.modules"
  }
}

object ComponentDependencyPool {
  private final class SharedDependencyClassLoader(parent: ClassLoader)
      extends URLClassLoader(Array.empty, parent) {
    private var _paths: Set[Path] = Set.empty

    def addPaths(paths: Vector[Path]): Unit = {
      val newpaths = paths.filterNot(_paths.contains)
      newpaths.foreach(path => addURL(path.toUri.toURL))
      _paths = _paths ++ newpaths
    }
  }

  private var _loader: Option[SharedDependencyClassLoader] = None
  private var _requested_coordinates: Vector[String] = Vector.empty
  private var _repositories: Vector[String] = Vector.empty
  private var _resolved_classpath: Vector[Path] = Vector.empty

  def resolveShared(
    requested: Vector[String],
    repositories: Vector[String],
    config: ComponentDependencyConfig
  ): Consequence[Vector[Path]] = synchronized {
    if (requested.isEmpty) {
      Consequence.success(Vector.empty)
    } else {
      val allcoordinates = (_requested_coordinates ++ requested).distinct
      val allrepositories = (_repositories ++ repositories).distinct
      if (allcoordinates == _requested_coordinates && allrepositories == _repositories) {
        Consequence.success(_resolved_classpath)
      } else {
        val conflicts = _request_conflicts(allcoordinates)
        if (conflicts.nonEmpty) {
          val message =
            "component shared dependency version conflict; v1 uses a temporary fail-fast policy and requires component-local dependencies for version-specific use: " +
              conflicts.mkString("; ")
          Consequence.configurationInvalid(message)
        } else {
          for {
            modules <- CoursierComponentDependencyResolver.resolveModules(allcoordinates, allrepositories, config)
            _ <- _reject_module_conflicts(modules)
            classpath <- CoursierComponentDependencyResolver.resolve(allcoordinates, allrepositories, config)
          } yield {
            _requested_coordinates = allcoordinates
            _repositories = allrepositories
            _resolved_classpath = classpath
            _resolved_classpath
          }
        }
      }
    }
  }

  def sharedClassLoader(
    paths: Vector[Path],
    runtimeparent: ClassLoader
  ): ClassLoader = synchronized {
    if (paths.isEmpty) {
      runtimeparent
    } else {
      val l = _loader.getOrElse {
        val created = new SharedDependencyClassLoader(runtimeparent)
        _loader = Some(created)
        created
      }
      l.addPaths(paths)
      l
    }
  }

  private def _request_conflicts(coordinates: Vector[String]): Vector[String] =
    coordinates.flatMap(_module).groupBy { case (group, artifact, _) => (group, artifact) }.toVector.flatMap {
      case ((group, artifact), values) =>
        val versions = values.map(_._3).distinct
        if (versions.size <= 1) Vector.empty
        else Vector(s"$group:$artifact requested=${versions.mkString(",")}")
    }

  private def _reject_module_conflicts(modules: Vector[CoursierComponentDependencyResolver.ResolvedModule]): Consequence[Unit] = {
    val conflicts = moduleConflicts(modules)
    if (conflicts.isEmpty)
      Consequence.success(())
    else
      Consequence.configurationInvalid(
        "component shared dependency transitive version conflict; v1 uses a temporary fail-fast policy and requires component-local dependencies for version-specific use: " +
          conflicts.mkString("; ")
      )
  }

  private[component] def moduleConflicts(
    modules: Vector[CoursierComponentDependencyResolver.ResolvedModule]
  ): Vector[String] =
    modules.groupBy(m => (m.group, m.artifact)).toVector.flatMap {
      case ((group, artifact), values) =>
        val versions = values.map(_.version).distinct.sorted
        if (versions.size <= 1) Vector.empty
        else Vector(s"$group:$artifact resolved=${versions.mkString(",")}")
    }.sorted

  private def _module(coordinate: String): Option[(String, String, String)] = {
    val parts = coordinate.split(":").toVector
    if (parts.size >= 3)
      Some((parts(0), parts(1), parts(2)))
    else
      None
  }
}

object CoursierComponentDependencyResolver {
  final case class ResolvedModule(
    group: String,
    artifact: String,
    version: String
  )

  def resolve(
    coordinates: Vector[String],
    repositories: Vector[String],
    config: ComponentDependencyConfig
  ): Consequence[Vector[Path]] =
    if (coordinates.isEmpty)
      Consequence.success(Vector.empty)
    else
      Consequence {
        val command =
          Vector(config.coursierCommand, "fetch", "--classpath") ++
            config.cacheDir.toVector.flatMap(path => Vector("--cache", path.toString)) ++
            repositories.flatMap(_repository_args) ++
            coordinates
        val output = command.!!
        output
          .trim
          .split(java.io.File.pathSeparator)
          .toVector
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(Paths.get(_).normalize)
          .distinct
      }

  def resolveModules(
    coordinates: Vector[String],
    repositories: Vector[String],
    config: ComponentDependencyConfig
  ): Consequence[Vector[ResolvedModule]] =
    if (coordinates.isEmpty)
      Consequence.success(Vector.empty)
    else
      Consequence {
        val command =
          Vector(config.coursierCommand, "resolve") ++
            config.cacheDir.toVector.flatMap(path => Vector("--cache", path.toString)) ++
            repositories.flatMap(_repository_args) ++
            coordinates
        parseResolvedModules(command.!!)
      }

  private[component] def parseResolvedModules(text: String): Vector[ResolvedModule] =
    text.linesIterator.toVector.flatMap { line =>
      val trimmed = line.trim
      if (trimmed.isEmpty || trimmed.startsWith("#")) {
        None
      } else {
        val parts = trimmed.split(":").toVector
        if (parts.size >= 3)
          Some(ResolvedModule(parts(0), parts(1), parts(2)))
        else
          None
      }
    }.distinct

  private def _repository_args(repository: String): Vector[String] = {
    val value = repository.trim
    if (value.isEmpty || value == "maven-central" || value == "central")
      Vector.empty
    else
      Vector("--repository", value)
  }
}

final class ComponentLocalFirstClassLoader(
  urls: Array[java.net.URL],
  parent: ClassLoader
) extends URLClassLoader(urls, parent) {
  override def loadClass(name: String, resolve: Boolean): Class[?] = synchronized {
    if (ComponentLocalFirstClassLoader.isParentFirst(name)) {
      super.loadClass(name, resolve)
    } else {
      Option(findLoadedClass(name)).getOrElse {
        try {
          val klass = findClass(name)
          if (resolve) resolveClass(klass)
          klass
        } catch {
          case _: ClassNotFoundException =>
            super.loadClass(name, resolve)
        }
      }
    }
  }
}

object ComponentLocalFirstClassLoader {
  private val _parent_first_prefixes = Vector(
    "org.goldenport.",
    "org.simplemodeling.model.",
    "scala.",
    "java.",
    "javax.",
    "sun.",
    "com.sun."
  )

  def apply(
    paths: Seq[Path],
    parent: ClassLoader
  ): ComponentLocalFirstClassLoader =
    new ComponentLocalFirstClassLoader(paths.map(_.toUri.toURL).toArray, parent)

  def isParentFirst(classname: String): Boolean =
    _parent_first_prefixes.exists(classname.startsWith)
}
