package org.goldenport.cncf.backend.collaborator

import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.jar.JarFile

import scala.jdk.CollectionConverters._
import scala.util.Using
import scala.util.control.NonFatal

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.api.Collaborator as ApiCollaborator

/*
 * @since   Jan. 30, 2026
 *  version Feb.  5, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorFactory(
  private val _repository_space: CollaboratorRepositorySpace = CollaboratorRepositorySpace.empty
) {

  private val _entries: Vector[CollaboratorRepository.CollaboratorEntry] =
    _repository_space.discover().toVector

  def entries: Vector[CollaboratorRepository.CollaboratorEntry] = _entries

  private[collaborator] def _repository_directories: Vector[Path] =
    _repository_space._repository_directories

  def resolve(name: String): Option[CollaboratorRepository.CollaboratorEntry] =
    _entries.find(_.name == name)
}

object CollaboratorFactory {
  val empty = CollaboratorFactory()

  def create(configuration: ResolvedConfiguration): CollaboratorFactory = {
    val space = CollaboratorRepositorySpace.create(configuration)
    CollaboratorFactory(space)
  }

  /**
   * Runtime bootstrap entry point. Its caller supplies only normalized paths
   * from the typed Global repository policy.
   */
  def create(repositoryPaths: Vector[Path]): CollaboratorFactory =
    CollaboratorFactory(CollaboratorRepositorySpace.create(repositoryPaths))

  def create(loader: ClassLoader): Consequence[Collaborator] =
    create(loader, Seq.empty)

  def create(
    loader: ClassLoader,
    classpaths: Seq[java.nio.file.Path]
  ): Consequence[Collaborator] = {
    _read_collaborator_classname_from_meta_inf(loader) match {
      case Some(classname) => _instantiate_collaborator(classname, loader)
      case None => _scan_and_instantiate(loader, classpaths)
    }
  }

  private def _instantiate_collaborator(
    classname: String,
    loader: ClassLoader
  ): Consequence[Collaborator] = {
    try {
      val cls = Class.forName(classname, true, loader)
      _validate_and_wrap(cls)
    } catch {
      case NonFatal(e) =>
        Consequence.componentInvalid(e)
    }
  }

  private def _validate_and_wrap(
    cls: Class[_]
  ): Consequence[Collaborator] = {
    if (
      classOf[ApiCollaborator].isAssignableFrom(cls) &&
      !cls.isInterface &&
      !Modifier.isAbstract(cls.getModifiers) &&
      !cls.getName.endsWith("$")
    ) {
      try {
        val apiinstance = cls.getDeclaredConstructor().newInstance().asInstanceOf[ApiCollaborator]
        Consequence.success(Collaborator(apiinstance))
      } catch {
        case NonFatal(e) =>
          Consequence.componentInvalid(e)
      }
    } else {
      Consequence.componentInvalid(s"invalid collaborator class: ${cls.getName}")
    }
  }

  private def _scan_and_instantiate(
    loader: ClassLoader,
    classpaths: Seq[java.nio.file.Path]
  ): Consequence[Collaborator] = {
    if (classpaths.isEmpty) {
      Consequence.resourceNotFound("collaborator classpath is empty")
    } else {
      val classnames = _jar_class_names_from_paths(classpaths)
      val candidates = classnames.flatMap(_load_api_collaborator_class(_, loader))
      candidates.distinct match {
        case Vector(cls) => _validate_and_wrap(cls)
        case Vector() => Consequence.resourceNotFound("no collaborator implementations found by scanning")
        case _ => Consequence.componentInvalid("multiple collaborator implementations found by scanning")
      }
    }
  }

  private def _load_api_collaborator_class(
    classname: String,
    loader: ClassLoader
  ): Option[Class[_]] = {
    _load_class(classname, loader).filter { cls =>
      classOf[ApiCollaborator].isAssignableFrom(cls) &&
      !cls.isInterface &&
      !Modifier.isAbstract(cls.getModifiers) &&
      !cls.getName.endsWith("$")
    }
  }

  private def _load_class(
    classname: String,
    loader: ClassLoader
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(classname, false, loader))
    } catch {
      case NonFatal(_) => None
    }
  }

  private def _read_collaborator_classname_from_meta_inf(
    loader: ClassLoader
  ): Option[String] = {
    Option(loader.getResourceAsStream("META-INF/cncf/collaborator")).flatMap { stream =>
      Using.resource(stream) { in =>
        val text = new String(in.readAllBytes(), StandardCharsets.UTF_8)
        _parse_first_classname(text)
      }
    }
  }

  private def _parse_first_classname(text: String): Option[String] = {
    text
      .linesIterator
      .map(_.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .take(1)
      .toVector
      .headOption
  }

  private def _jar_class_names_from_paths(paths: Seq[java.nio.file.Path]): Vector[String] =
    paths.flatMap(_jar_class_names).toVector

  private def _jar_class_names(path: java.nio.file.Path): Vector[String] = {
    Using.resource(new JarFile(path.toFile)) { jar =>
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
    withoutextension.replace('/', '.').replace('\\', '.')
  }
}
