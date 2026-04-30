package org.goldenport.cncf.component

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder

/*
 * Loader for component descriptors.
 *
 * Canonical direction:
 * - ComponentDescriptor is packaged in CAR
 * - local override uses CAR-style layout or top-level descriptor files
 * - explicit path may point at a descriptor file or a CAR-style directory
 *
 * @since   Mar. 27, 2026
 *  version Apr.  8, 2026
 *  version Apr. 14, 2026
 *  version Apr. 25, 2026
 * @version May.  1, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentDescriptorLoader {
  private val _canonical_descriptor_files = Vector(
    "descriptor.json",
    "descriptor.yaml",
    "descriptor.yml",
    "descriptor.conf",
    "descriptor.hocon",
    "descriptor.xml",
    "component-descriptor.json",
    "component-descriptor.yaml",
    "component-descriptor.yml",
    "component-descriptor.conf",
    "component-descriptor.hocon",
    "component-descriptor.xml",
    "meta/component-descriptor.json",
    "meta/component-descriptor.yaml",
    "meta/component-descriptor.yml",
    "meta/component-descriptor.conf",
    "meta/component-descriptor.hocon",
    "meta/component-descriptor.xml"
  )

  def load(path: Path): Consequence[Vector[ComponentDescriptor]] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"component descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path))
      _load_directory(path)
    else if (Files.isRegularFile(path))
      _load_file(path)
    else
      Consequence.resourceInvalid(s"component descriptor path is not a file or directory: ${path}")

  def loadArchive(path: Path): Consequence[ComponentDescriptor] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"component archive descriptor path does not exist: ${path}")
    else if (_is_archive_file(path))
      _load_archive_file(path)
    else if (_is_non_component_archive_file(path))
      Consequence.resourceInvalid(s"component archive must be a CAR file: ${path}")
    else {
      val descriptorPath =
        if (Files.isDirectory(path)) _resolve_canonical_descriptor_files(path).headOption
        else Some(path)
      descriptorPath match {
        case Some(file) =>
          _load_file(file).flatMap(_.headOption.map(Consequence.success).getOrElse(Consequence.resourceInvalid(s"component archive descriptor is empty: ${file}")))
        case None =>
          Consequence.resourceNotFound(s"component descriptor not found under canonical CAR layout: ${path}")
      }
    }

  def looksLikeArchiveDirectory(path: Path): Boolean = {
    val componentdir = path.resolve("component")
    Files.isDirectory(path) && Files.isDirectory(componentdir) &&
      _resolve_canonical_descriptor_files(path).nonEmpty &&
      _contains_component_jar(componentdir)
  }

  private def _contains_component_jar(componentdir: Path): Boolean = {
    val stream = Files.list(componentdir)
    try {
      stream.iterator().asScala.exists { p =>
        Files.isRegularFile(p) && p.getFileName.toString.toLowerCase.endsWith(".jar")
      }
    } finally {
      stream.close()
    }
  }

  private def _load_directory(path: Path): Consequence[Vector[ComponentDescriptor]] = {
    val canonical = _resolve_canonical_descriptor_files(path)
    if (canonical.nonEmpty)
      _load_files(canonical)
    else
      Consequence.resourceNotFound(s"component descriptor not found under canonical CAR layout: ${path}")
  }

  private def _resolve_canonical_descriptor_files(path: Path): Vector[Path] =
    _canonical_descriptor_files
      .map(path.resolve(_).normalize)
      .filter(Files.isRegularFile(_))
      .distinct

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".car")
  }

  private def _is_non_component_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".zip")
  }

  private def _load_archive_file(path: Path): Consequence[ComponentDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      val root = fs.getPath("/")
      _resolve_canonical_descriptor_files(root).headOption match {
        case Some(file) =>
          _load_file(file).flatMap(_.headOption.map(Consequence.success).getOrElse(Consequence.resourceInvalid(s"component archive descriptor is empty: ${path}")))
        case None =>
          Consequence.resourceNotFound(s"component descriptor not found in archive: ${path}")
      }
    }
  }

  private def _load_files(files: Vector[Path]): Consequence[Vector[ComponentDescriptor]] =
    files.foldLeft(Consequence.success(Vector.empty[ComponentDescriptor])) { (z, file) =>
      for {
        xs <- z
        ys <- _load_file(file)
      } yield xs ++ ys
    }

  private def _load_file(path: Path): Consequence[Vector[ComponentDescriptor]] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.foldLeft(Consequence.success(Vector.empty[ComponentDescriptor])) { (z, rec) =>
        for {
          xs <- z
          x <- _to_descriptor(path.toString, rec)
        } yield xs :+ x
      }
    }

  private def _to_descriptor(origin: String, rec: Record): Consequence[ComponentDescriptor] = {
    summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).flatMap { descriptor =>
      if (descriptor.name.orElse(descriptor.componentName).exists(_.trim.nonEmpty))
        Consequence.success(descriptor)
      else
        Consequence.argumentMissing("component descriptor name/component")
    }.leftMap { c =>
      c.copy(observation = c.observation.copy(cause = c.observation.cause.withMessage(s"${c.displayMessage} in ${origin}")))
    }
  }
}
