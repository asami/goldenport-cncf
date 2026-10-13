package org.goldenport.cncf.component.capability

import java.io.{ByteArrayOutputStream, InputStream, RandomAccessFile}
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.nio.file.{AccessDeniedException, Files, LinkOption, Path}
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentDescriptorLoader}
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityPackageSource.*

/*
 * Local read-only metadata acquisition; never extracts or activates a CAR.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityPackageArchivePart {
  final case class ReadPackage(descriptor: ComponentDescriptor, index: Vector[String], resources: Vector[(String, String)]) {}
  private val _index_limit = 1024L * 1024
  private val _resource_limit = 16L * 1024 * 1024
  private val _total_limit = 64L * 1024 * 1024
  private val _descriptor_names = Vector("descriptor", "component-descriptor").flatMap { name =>
    Vector("json", "yaml", "yml", "conf", "hocon", "xml").map(extension => s"$name.$extension")
  } ++ Vector("json", "yaml", "yml", "conf", "hocon", "xml").map(extension => s"meta/component-descriptor.$extension")

  def read(location: Location): Either[Diagnostic, ReadPackage] = location match {
    case Location.Directory(root) => _nonnull(root, "/location/root").flatMap(_read_directory)
    case Location.Car(path) => _nonnull(path, "/location/path").flatMap(_read_car)
    case null => Left(_malformed("/location", "An explicit local package location is required."))
  }

  private def _io[A](location: String)(body: => Either[Diagnostic, A]): Either[Diagnostic, A] = {
    try body catch {
      case NonFatal(error) if _access_denied(error) => Left(_diagnostic(DiagnosticKind.Unauthorized, "Selected metadata access was denied.", location))
      case NonFatal(_) => Left(_diagnostic(DiagnosticKind.Unavailable, "Selected metadata could not be read.", location))
    }
  }

  private def _access_denied(error: Throwable): Boolean = {
    error.isInstanceOf[AccessDeniedException] || Option(error.getCause).exists(_access_denied)
  }

  private def _kind(path: Path, directory: Boolean, location: String): Either[Diagnostic, Unit] = {
    _io(location) {
      val attributes = Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
      Either.cond(!attributes.isSymbolicLink && (if (directory) attributes.isDirectory else attributes.isRegularFile), (),
        _malformed(location, "A selected package path must have its exact regular file or directory kind, without symlinks."))
    }
  }

  private def _read_directory(root: Path): Either[Diagnostic, ReadPackage] = {
    _io("/location/root") {
      for {
        _ <- _kind(root, true, "/location/root")
        _ <- _kind(root.resolve("meta"), true, "/meta")
        _ <- _kind(root.resolve(metadataDirectory), true, "/" + metadataDirectory)
        indextext <- _read_file(root.resolve(indexPath), _index_limit, "/index")
        index <- ComponentCapabilityPackageIndexPart.decode(indextext._1)
        inventory <- _directory_inventory(root)
        _ <- ComponentCapabilityPackageIndexPart.validateInventory(index, inventory)
        descriptor <- _directory_descriptor(root)
        resources <- _resources(index) { (name, limit) =>
          _read_file(root.resolve(metadataDirectory).resolve(name), limit, "/" + metadataDirectory + "/" + name)
        }
      } yield ReadPackage(descriptor, index, resources)
    }
  }

  private def _directory_inventory(root: Path): Either[Diagnostic, Vector[String]] = {
    _io("/" + metadataDirectory) {
      val stream = Files.walk(root.resolve(metadataDirectory))
      try {
        val paths = stream.iterator().asScala.toVector
        Right(paths.filterNot(Files.isDirectory(_, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/')))
      } finally stream.close()
    }
  }

  private def _directory_descriptor(root: Path): Either[Diagnostic, ComponentDescriptor] = {
    _io("/descriptor") {
      val candidates = _descriptor_names.map(root.resolve(_)).filter(Files.exists(_, LinkOption.NOFOLLOW_LINKS))
      for {
        _ <- _sequence(candidates.map(path => _kind(path, false, "/descriptor"))).map(_ => ())
        _ <- candidates.headOption match {
          case Some(path) =>
            // Force ordinary file access failures before the descriptor loader's
            // existing syntax/identity failure wrapper.
            val stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)
            try { stream.read(); Right(()) } finally stream.close()
          case None => Left(_malformed("/descriptor", "Canonical descriptor evidence is required."))
        }
        descriptor <- _descriptor(root)
      } yield descriptor
    }
  }

  private def _descriptor(path: Path): Either[Diagnostic, ComponentDescriptor] = {
    ComponentDescriptorLoader.loadArchive(path) match {
      case Consequence.Success(value) => value.requireCanonicalIdentityC match {
        case Consequence.Success(_) => Right(value)
        case Consequence.Failure(_) => Left(_malformed("/descriptor", "Complete canonical schema-3 identity and release are required."))
      }
      case Consequence.Failure(_) => Left(_malformed("/descriptor", "Canonical descriptor syntax and identity are required."))
    }
  }

  private def _read_car(path: Path): Either[Diagnostic, ReadPackage] = {
    _io("/location/path") {
      for {
        _ <- _kind(path, false, "/location/path")
        _ <- Either.cond(path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".car"), (), _malformed("/location/path", "A regular CAR file is required."))
        result <- {
          val zip = new ZipFile(path.toFile)
          try {
            val entries = zip.entries().asScala.toVector
            val names = entries.map(_.getName)
            for {
              _ <- _archive_inventory(names)
              _ <- _archive_symlinks(path, names)
              indexbytes <- _zip_read(zip, indexPath, _index_limit, "/index")
              index <- ComponentCapabilityPackageIndexPart.decode(indexbytes._1)
              _ <- ComponentCapabilityPackageIndexPart.validateInventory(index, names)
              descriptor <- _descriptor(path)
              resources <- _resources(index) { (name, limit) =>
                _zip_read(zip, metadataDirectory + "/" + name, limit, "/" + metadataDirectory + "/" + name)
              }
            } yield ReadPackage(descriptor, index, resources)
          } finally zip.close()
        }
      } yield result
    }
  }

  private def _archive_inventory(names: Vector[String]): Either[Diagnostic, Unit] = {
    val prefix = metadataDirectory + "/"
    val metadata = names.filter(_metadata_name)
    metadata.find { name =>
      val parts = name.stripSuffix("/").split("/", -1)
      name.startsWith("/") || name.contains("\\") || parts.exists(part => part.isEmpty || part == "." || part == "..") || !name.startsWith(prefix)
    } match {
      case Some(name) => Left(_malformed("/" + name, "Unsafe archive metadata resource alias."))
      case None =>
        val resources = metadata.filter(_.endsWith(".json"))
        resources.indices.find(index => resources.take(index).contains(resources(index))) match {
          case Some(index) => Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated exact archive metadata entry.", "/" + resources(index)))
          case None => Right(())
        }
    }
  }

  private def _metadata_name(name: String): Boolean = {
    val parts = name.replace('\\', '/').split("/", -1).foldLeft(Vector.empty[String]) { (values, part) =>
      part match {
        case "" | "." => values
        case ".." => values.dropRight(1)
        case _ => values :+ part
      }
    }
    name.startsWith(metadataDirectory + "/") || parts.take(2) == Vector("meta", "component-capability")
  }

  private def _unsigned_short(bytes: Array[Byte], offset: Int): Int = {
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(offset).toInt & 0xffff
  }

  private def _unsigned_int(bytes: Array[Byte], offset: Int): Long = {
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(offset).toLong & 0xffffffffL
  }

  private def _archive_symlinks(path: Path, names: Vector[String]): Either[Diagnostic, Unit] = {
    // ZipFile reads entry bytes without interpreting Unix link attributes.
    // Inspect central-directory mode bits only for canonical metadata roles.
    _io("/location/path") {
      val file = new RandomAccessFile(path.toFile, "r")
      try {
        val tailsize = math.min(file.length(), 65557L).toInt
        val tail = new Array[Byte](tailsize)
        file.seek(file.length() - tailsize)
        file.readFully(tail)
        val end = (tailsize - 22 to 0 by -1).find { index =>
          _unsigned_int(tail, index) == 0x06054b50L && index + 22 + _unsigned_short(tail, index + 20) == tailsize
        }
        end match {
          case None => Left(_malformed("/location/path", "A complete CAR central directory is required."))
          case Some(index) =>
            val offset = _unsigned_int(tail, index + 16)
            val central = if (offset != 0xffffffffL) offset else {
              val locator = new Array[Byte](20)
              file.seek(file.length() - tailsize + index - 20)
              file.readFully(locator)
              val record = ByteBuffer.wrap(locator).order(ByteOrder.LITTLE_ENDIAN).getLong(8)
              val header = new Array[Byte](56)
              file.seek(record)
              file.readFully(header)
              ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getLong(48)
            }
            file.seek(central)
            var remaining = names.size
            var error: Option[Diagnostic] = None
            while (remaining > 0 && error.isEmpty) {
              val header = new Array[Byte](46)
              file.readFully(header)
              if (_unsigned_int(header, 0) != 0x02014b50L) {
                error = Some(_malformed("/location/path", "A canonical ZIP central entry is required."))
              } else {
                val namebytes = new Array[Byte](_unsigned_short(header, 28))
                file.readFully(namebytes)
                val name = new String(namebytes, StandardCharsets.UTF_8)
                val mode = (_unsigned_int(header, 38) >>> 16) & 0xf000L
                if (mode == 0xa000L && (name.stripSuffix("/") == "meta" || name.stripSuffix("/") == metadataDirectory ||
                    (_metadata_name(name) && name.endsWith(".json")) || _descriptor_names.contains(name))) {
                  error = Some(_malformed("/" + name, "Archive metadata symlinks are forbidden."))
                }
                file.seek(file.getFilePointer + _unsigned_short(header, 30) + _unsigned_short(header, 32))
              }
              remaining -= 1
            }
            error.toLeft(())
        }
      } finally file.close()
    }
  }

  private def _zip_read(zip: ZipFile, name: String, limit: Long, location: String): Either[Diagnostic, (String, Long)] = {
    _io(location) {
      Option(zip.getEntry(name)) match {
        case None => Left(_diagnostic(DiagnosticKind.Unavailable, "Selected metadata resource is missing.", location))
        case Some(entry) if entry.isDirectory => Left(_malformed(location, "A metadata resource must be a file."))
        case Some(entry) =>
          if (entry.getSize > limit) Left(_malformed(location, "Metadata byte limit exceeded."))
          else {
            val stream = zip.getInputStream(entry)
            try _read_stream(stream, limit, location) finally stream.close()
          }
      }
    }
  }

  private def _read_file(path: Path, limit: Long, location: String): Either[Diagnostic, (String, Long)] = {
    _io(location) {
      for {
        _ <- _kind(path, false, location)
        _ <- Either.cond(Files.size(path) <= limit, (), _malformed(location, "Metadata byte limit exceeded."))
        value <- {
          val stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)
          try _read_stream(stream, limit, location) finally stream.close()
        }
      } yield value
    }
  }

  private def _read_stream(stream: InputStream, limit: Long, location: String): Either[Diagnostic, (String, Long)] = {
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](8192)
    var total = 0L
    var count = stream.read(buffer)
    while (count != -1 && total <= limit) {
      total += count
      if (total <= limit) output.write(buffer, 0, count)
      if (total <= limit) count = stream.read(buffer)
    }
    if (total > limit) Left(_malformed(location, "Metadata byte limit exceeded."))
    else {
      try {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        Right(decoder.decode(ByteBuffer.wrap(output.toByteArray)).toString -> total)
      } catch {
        case _: CharacterCodingException => Left(_malformed(location, "Strict UTF-8 metadata is required."))
      }
    }
  }

  private def _resources(index: Vector[String])(read: (String, Long) => Either[Diagnostic, (String, Long)]): Either[Diagnostic, Vector[(String, String)]] = {
    index.foldLeft[Either[Diagnostic, (Vector[(String, String)], Long)]](Right(Vector.empty -> 0L)) { (result, name) =>
      result.flatMap { (values, total) =>
        read(name, math.min(_resource_limit, _total_limit - total)).map { (text, size) =>
          (values :+ (name -> text), total + size)
        }
      }
    }.map(_._1)
  }
}
