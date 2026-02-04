package org.goldenport.cncf.collaborator

import java.lang.reflect.Modifier
import java.util.ServiceLoader
import java.util.jar.JarFile

import scala.jdk.CollectionConverters._
import scala.util.Using
import scala.util.control.NonFatal

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.Collaborator
import org.goldenport.cncf.collaborator.api.Collaborator as ApiCollaborator

/*
 * @since   Jan. 30, 2026
 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorFactory(
  private val _repository_space: CollaboratorRepositorySpace = CollaboratorRepositorySpace.empty
) {

  private val _entries: Vector[CollaboratorRepository.CollaboratorEntry] =
    _repository_space.discover().toVector

  def entries: Vector[CollaboratorRepository.CollaboratorEntry] = _entries

  def resolve(name: String): Option[CollaboratorRepository.CollaboratorEntry] =
    _entries.find(_.name == name)
}

object CollaboratorFactory {
  val empty = CollaboratorFactory()

  def create(c: ResolvedConfiguration): CollaboratorFactory = {
    val space = CollaboratorRepositorySpace.create(c)
    CollaboratorFactory(space)
  }

  def create(loader: ClassLoader): Consequence[Collaborator] =
    create(loader, Seq.empty)

  def create(
    loader: ClassLoader,
    classpaths: Seq[java.nio.file.Path]
  ): Consequence[Collaborator] = {
    _readCollaboratorClassnameFromMetaInf(loader) match {
      case Some(className) => _instantiateCollaborator(className, loader)
      case None => _scanAndInstantiate(loader, classpaths)
    }
  }

  private def _instantiateCollaborator(
    className: String,
    loader: ClassLoader
  ): Consequence[Collaborator] = {
    try {
      val cls = Class.forName(className, true, loader)
      _validateAndWrap(cls)
    } catch {
      case NonFatal(e) =>
        Consequence.failure(e)
    }
  }

  private def _validateAndWrap(
    cls: Class[_]
  ): Consequence[Collaborator] = {
    if (
      classOf[ApiCollaborator].isAssignableFrom(cls) &&
      !cls.isInterface &&
      !Modifier.isAbstract(cls.getModifiers) &&
      !cls.getName.endsWith("$")
    ) {
      try {
        val apiInstance = cls.getDeclaredConstructor().newInstance().asInstanceOf[ApiCollaborator]
        Consequence.success(Collaborator(apiInstance))
      } catch {
        case NonFatal(e) =>
          Consequence.failure(e)
      }
    } else {
      Consequence.failure(s"invalid collaborator class: ${cls.getName}")
    }
  }

  private def _scanAndInstantiate(
    loader: ClassLoader,
    classpaths: Seq[java.nio.file.Path]
  ): Consequence[Collaborator] = {
    if (classpaths.isEmpty) {
      Consequence.failure("collaborator classpath is empty")
    } else {
      val classNames = _jar_class_names_from_paths(classpaths)
      val candidates = classNames.flatMap(_loadApiCollaboratorClass(_, loader))
      candidates.distinct match {
        case Vector(cls) => _validateAndWrap(cls)
        case Vector() => Consequence.failure("no collaborator implementations found by scanning")
        case _ => Consequence.failure("multiple collaborator implementations found by scanning")
      }
    }
  }

  private def _loadApiCollaboratorClass(
    className: String,
    loader: ClassLoader
  ): Option[Class[_]] = {
    _loadClass(className, loader).filter { cls =>
      classOf[ApiCollaborator].isAssignableFrom(cls) &&
      !cls.isInterface &&
      !Modifier.isAbstract(cls.getModifiers) &&
      !cls.getName.endsWith("$")
    }
  }

  private def _loadClass(
    className: String,
    loader: ClassLoader
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(className, false, loader))
    } catch {
      case NonFatal(_) => None
    }
  }

  private def _readCollaboratorClassnameFromMetaInf(
    loader: ClassLoader
  ): Option[String] = {
    Option(loader.getResourceAsStream("META-INF/cncf/collaborator")).flatMap { stream =>
      Using.resource(stream) { in =>
        val text = new String(in.readAllBytes(), StandardCharsets.UTF_8)
        _parseFirstClassname(text)
      }
    }
  }

  private def _parseFirstClassname(text: String): Option[String] = {
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
    val withoutExtension = entryname.substring(0, entryname.length - ".class".length)
    withoutExtension.replace('/', '.').replace('\\', '.')
  }
}
