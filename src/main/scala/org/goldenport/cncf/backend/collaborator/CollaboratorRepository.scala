package org.goldenport.cncf.backend.collaborator

import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import java.util.jar.JarFile
import java.util.ServiceLoader

import scala.util.Using
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

import org.goldenport.cncf.collaborator.api

/*
 * @since   Jan. 30, 2026
 *  version Feb.  5, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait CollaboratorRepository {
  def discover(): Vector[CollaboratorRepository.CollaboratorEntry]
}

object CollaboratorRepository {
  final case class CollaboratorEntry(
    name: String,
    collaborator: api.Collaborator,
    loader: ClassLoader,
    origin: String
  )

  final class CollaboratorDirRepository(
    baseDir: Path,
    collaboratorApiUrl: Option[URL]
  ) extends CollaboratorRepository {

    override def discover(): Vector[CollaboratorEntry] = {
      if (!Files.exists(baseDir)) {
        Vector.empty
      } else {
        val stream = Files.list(baseDir)
        try {
          stream
            .iterator()
            .asScala
            .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar"))
            .flatMap(_discover_from_jar(_, collaboratorApiUrl))
            .toVector
        } finally {
          stream.close()
        }
      }
    }

    private def _discover_from_jar(
      jarpath: Path,
      collaboratorapiurl: Option[URL]
    ): Vector[CollaboratorEntry] = {
      val origin = jarpath.getFileName.toString
      val urls = collaboratorapiurl match {
        case Some(apiurl) => Array(jarpath.toUri.toURL, apiurl)
        case None => Array(jarpath.toUri.toURL)
      }
      val loader = new URLClassLoader(urls, null)
      val classnames = Using.resource(new JarFile(jarpath.toFile)) { jar =>
        jar
          .entries()
          .asScala
          .filter(e => !e.isDirectory && e.getName.endsWith(".class"))
          .map(e => _class_name_from_entry(e.getName))
          .toVector
      }
      val services = _load_service_collaborators(loader, origin)
      val candidates = classnames.flatMap(name => _load_class_collaborator(name, loader, origin))
      _deduplicate(services ++ candidates)
    }

    private def _load_service_collaborators(
      loader: ClassLoader,
      origin: String
    ): Vector[CollaboratorEntry] =
      ServiceLoader
        .load(classOf[api.Collaborator], loader)
        .iterator()
        .asScala
        .map { inst =>
          CollaboratorEntry(inst.getClass.getName, inst, loader, origin)
        }
        .toVector

    private def _load_class_collaborator(
      classname: String,
      loader: ClassLoader,
      origin: String
    ): Option[CollaboratorEntry] = {
      try {
        val cls = Class.forName(classname, false, loader)
        if (
          classOf[api.Collaborator].isAssignableFrom(cls) &&
          !cls.isInterface &&
          !Modifier.isAbstract(cls.getModifiers) &&
          !classname.endsWith("$")
        ) {
          val instance = cls.getDeclaredConstructor().newInstance().asInstanceOf[api.Collaborator]
          Some(CollaboratorEntry(classname, instance, loader, origin))
        } else {
          None
        }
      } catch {
        case NonFatal(_) => None
      }
    }

    private def _deduplicate(
      entries: Seq[CollaboratorEntry]
    ): Vector[CollaboratorEntry] =
      entries
        .groupBy(_.name)
        .view
        .mapValues(_.head)
        .values
        .toVector

    private def _class_name_from_entry(entryname: String): String = {
      val withoutextension = entryname.substring(0, entryname.length - ".class".length)
      withoutextension.replace('/', '.').replace('\\', '.')
    }
  }
}
