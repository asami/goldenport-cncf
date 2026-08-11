package org.goldenport.cncf.component

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
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
 * @version Aug. 11, 2026
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
    _load_archive(path, validatecanonical = true)

  /**
   * Decodes packaged descriptor evidence before CID-06D admission
   * classification. Public archive loading remains canonical and strict.
   */
  private[component] def loadArchiveRaw(path: Path): Consequence[ComponentDescriptor] =
    _load_archive(path, validatecanonical = false)

  /**
   * Reads only canonical identity evidence from a CAR archive without
   * constructing or activating a ComponentDescriptor.
   */
  private[cncf] def probeArchiveComponentIdentityC(
    path: Path
  ): Consequence[(ComponentId, Option[String])] =
    _load_archive_descriptor_record(path).flatMap(_archive_component_identity_c)

  /** Repository metadata view after exact deferred-release classification. */
  private[cncf] def loadArchiveEffective(path: Path): Consequence[ComponentDescriptor] =
    for {
      raw <- loadArchiveRaw(path)
      registry <- ComponentIdentityDeferredReleaseRegistry.loadC()
      admission <- registry.admitDescriptorC(raw)
    } yield admission.effective

  private def _load_archive(
    path: Path,
    validatecanonical: Boolean
  ): Consequence[ComponentDescriptor] =
    try {
      if (!Files.exists(path))
        Consequence.resourceNotFound(s"component archive descriptor path does not exist: ${path}")
      else if (_is_archive_file(path))
        _load_archive_file(path, validatecanonical)
      else if (_is_non_component_archive_file(path))
        Consequence.resourceInvalid(s"component archive must be a CAR file: ${path}")
      else {
        val descriptorpath =
          if (Files.isDirectory(path)) _resolve_canonical_descriptor_files(path).headOption
          else Some(path)
        descriptorpath match {
          case Some(file) =>
            _load_archive_descriptor_file(file, path, validatecanonical)
          case None =>
            Consequence.resourceNotFound(s"component descriptor not found under canonical CAR layout: ${path}")
        }
      }
    } catch {
      case NonFatal(e) => _archive_io_failure(path, e)
    }

  private def _load_archive_descriptor_record(path: Path): Consequence[Record] = {
    try {
      val result =
        if (!Files.exists(path))
          Consequence.resourceNotFound(s"component archive descriptor path does not exist: ${path}")
        else if (_is_archive_file(path)) {
          val uri = URI.create(s"jar:${path.toUri}")
          Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
            val root = fs.getPath("/")
            _resolve_canonical_descriptor_files(root).headOption match {
              case Some(file) => _load_archive_descriptor_record(file, path)
              case None => Consequence.resourceNotFound(s"component descriptor not found in archive: ${path}")
            }
          }
        } else if (_is_non_component_archive_file(path))
          Consequence.resourceInvalid(s"component archive must be a CAR file: ${path}")
        else if (Files.isDirectory(path)) {
          _resolve_canonical_descriptor_files(path).headOption match {
            case Some(file) => _load_archive_descriptor_record(file, path)
            case None => Consequence.resourceNotFound(s"component descriptor not found under canonical CAR layout: ${path}")
          }
        } else
          Consequence.resourceInvalid(s"component archive must be a CAR file: ${path}")
      _archive_admission_result_c(path, result)
    } catch {
      case NonFatal(e) => _archive_io_failure(path, e)
    }
  }

  private def _load_archive_descriptor_record(
    descriptorpath: Path,
    archivepath: Path
  ): Consequence[Record] =
    DescriptorRecordLoader.load(descriptorpath).flatMap { records =>
      records.headOption
        .map(Consequence.success)
        .getOrElse(Consequence.resourceInvalid(s"component archive descriptor is empty: ${archivepath}"))
    }

  private def _archive_component_identity_c(
    record: Record
  ): Consequence[(ComponentId, Option[String])] =
    _schema_version_three_c(record).flatMap { _ =>
      _record_value(record, "component") match {
        case Some(component) =>
          for {
            namespace <- _canonical_identity_string_c(component, "namespace")
            localid <- _canonical_identity_string_c(component, "id")
            qualifiedid = s"${namespace}.${localid}"
            componentid <- ComponentId.parseC(qualifiedid)
            _ <- if (componentid.name == qualifiedid)
              Consequence.unit
            else
              Consequence.resourceInvalid("canonical component identity was not preserved exactly")
            version <- _optional_canonical_identity_string_c(component, "version")
          } yield componentid -> version
        case None =>
          Consequence.resourceInvalid("canonical component identity must be an object")
      }
    }

  private def _schema_version_three_c(record: Record): Consequence[Unit] =
    if (record.getAny("schemaVersion").exists(_schema_version_three))
      Consequence.unit
    else
      Consequence.resourceInvalid("archive component identity requires schemaVersion 3")

  private def _schema_version_three(value: Any): Boolean =
    value match {
      case value: Int => value == 3
      case value: Long => value == 3L
      case value: BigInt => value == 3
      case value: BigDecimal => value == 3
      case _ => false
    }

  private def _record_value(record: Record, field: String): Option[Record] =
    record.getAny(field) match {
      case Some(value: Record) => Some(value)
      case Some(value: Map[?, ?]) => Some(Record.create(value.iterator.map { case (k, v) => k.toString -> v }.toMap))
      case _ => None
    }

  private def _canonical_identity_string_c(record: Record, field: String): Consequence[String] =
    record.getAny(field) match {
      case Some(value: String) if value.nonEmpty && value == value.trim => Consequence.success(value)
      case _ => Consequence.resourceInvalid(s"canonical component identity ${field} must be a non-empty string")
    }

  private def _optional_canonical_identity_string_c(
    record: Record,
    field: String
  ): Consequence[Option[String]] =
    record.getAny(field) match {
      case None => Consequence.success(None)
      case Some(value: String) if value.nonEmpty && value == value.trim => Consequence.success(Some(value))
      case _ => Consequence.resourceInvalid(s"canonical component identity ${field} must be a non-empty string")
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
    Files.isRegularFile(path) && name.endsWith(".car")
  }

  private def _is_non_component_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    Files.isRegularFile(path) && (name.endsWith(".sar") || name.endsWith(".zip"))
  }

  private def _load_archive_file(
    path: Path,
    validatecanonical: Boolean
  ): Consequence[ComponentDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      val root = fs.getPath("/")
      _resolve_canonical_descriptor_files(root).headOption match {
        case Some(file) =>
          _load_archive_descriptor_file(file, path, validatecanonical)
        case None =>
          Consequence.resourceNotFound(s"component descriptor not found in archive: ${path}")
      }
    }
  }

  private def _archive_io_failure(
    archivepath: Path,
    cause: Throwable
  ): Consequence[Nothing] = {
    val message = Option(cause.getMessage).filter(_.nonEmpty).getOrElse(cause.getClass.getName)
    Consequence.resourceInvalid(s"CAR component archive I/O failed: archive=${archivepath}; cause=${message}")
  }

  private def _load_archive_descriptor_file(
    descriptorpath: Path,
    archivepath: Path,
    validatecanonical: Boolean
  ): Consequence[ComponentDescriptor] = {
    val result = _load_file(descriptorpath).flatMap { descriptors =>
      descriptors.headOption
        .map(Consequence.success)
        .getOrElse(Consequence.resourceInvalid(s"component archive descriptor is empty: ${archivepath}"))
    }.flatMap { descriptor =>
      if (validatecanonical)
        _validate_archive_descriptor_contract(descriptor).map(_ => descriptor)
      else
        Consequence.success(descriptor)
    }
    _archive_admission_result_c(archivepath, result)
  }

  private def _archive_admission_result_c[A](
    archivepath: Path,
    result: Consequence[A]
  ): Consequence[A] =
    result match {
      case Consequence.Success(value) => Consequence.success(value)
      case Consequence.Failure(conclusion) =>
        Consequence.resourceInvalid(
          s"CAR component descriptor admission failed: archive=${archivepath}; reason=${conclusion.display}"
        )
    }

  private def _validate_archive_descriptor_contract(
    descriptor: ComponentDescriptor
  ): Consequence[Unit] =
    descriptor.requireCanonicalIdentityC.map(_ => ())

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
      if (descriptor.isCanonicalIdentity || descriptor.name.orElse(descriptor.componentName).exists(_.trim.nonEmpty))
        Consequence.success(descriptor)
      else
        Consequence.argumentMissing("component descriptor name/component")
    }.leftMap { c =>
      c.copy(observation = c.observation.copy(cause = c.observation.cause.withMessage(s"${c.displayMessage} in ${origin}")))
    }
  }
}
