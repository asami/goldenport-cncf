package org.goldenport.cncf.workspace

import java.io.{ByteArrayOutputStream, IOException, OutputStream}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, NoSuchFileException, Path, StandardCopyOption, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, PosixFileAttributeView}
import java.util.zip.{CRC32, GZIPOutputStream, Inflater}
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.resource.{ResourceReadAcquisition, ResourceReadLimits}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FileTarget(workspaceId: WorkspaceId)

enum FileRepresentation {
  case Identity, Gzip
}

enum FilePrepareDirectoryOutcome {
  case Created, AlreadyPresent, Conflict
}

final case class FilePrepareDirectoryResult(target: FileTarget, path: WorkspaceFilePath, outcome: FilePrepareDirectoryOutcome) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.FileWrite, target.workspaceId)
  override def toString: String = s"FilePrepareDirectoryResult($target,$outcome,FileWrite)"
}

final case class FileReadBytesResult(target: FileTarget, path: WorkspaceFilePath, representation: FileRepresentation, bytes: Option[Vector[Byte]]) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.FileRead, target.workspaceId)
  override def toString: String = s"FileReadBytesResult($target,$representation,FileRead)"
}

final case class FileWriteBytesResult(target: FileTarget, path: WorkspaceFilePath, representation: FileRepresentation, outcome: FileWriteOutcome) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.FileWrite, target.workspaceId)
  override def toString: String = s"FileWriteBytesResult($target,$representation,$outcome,FileWrite)"
}

/** Literal POSIX relative filename; interpretation revalidates even copied values. */
final case class WorkspaceFilePath private (value: String) {
  override def toString: String = "WorkspaceFilePath"
}

object WorkspaceFilePath {
  def parseC(value: String): Consequence[WorkspaceFilePath] = {
    val text = Option(value).getOrElse("")
    val segments = text.split("/", -1).toVector
    try {
      val path = Path.of(text)
      if (text.isEmpty || text.indexOf('\u0000') >= 0 || text.startsWith("/") ||
          text.matches("(?s)[A-Za-z]:[/\\\\].*") || text.startsWith("\\\\") ||
          segments.exists(x => x.isEmpty || x == "." || x == "..") ||
          path.isAbsolute || path.normalize().startsWith(".."))
        _invalid_c
      else
        Consequence.success(new WorkspaceFilePath(text))
    } catch {
      case NonFatal(_) => _invalid_c
    }
  }

  private def _invalid_c: Consequence[WorkspaceFilePath] =
    Consequence.argumentFormatError("path", "literal workspace-relative filename", "invalid")
}

final case class FileReadTextResult(target: FileTarget, path: WorkspaceFilePath, text: Option[String]) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.FileRead, target.workspaceId)
  override def toString: String = s"FileReadTextResult($target,FileRead)"
}

enum FileWriteMode {
  case CreateNew
  case UpdateExisting(expectedText: String)

  override def toString: String = this match {
    case CreateNew => "CreateNew"
    case UpdateExisting(_) => "UpdateExisting"
  }
}

enum FileWriteConflict {
  case AlreadyExists, Missing, Changed
}

enum FileWriteOutcome {
  case Created, Updated
  case Conflict(reason: FileWriteConflict)
}

final case class FileWriteTextResult(target: FileTarget, path: WorkspaceFilePath, outcome: FileWriteOutcome) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.FileWrite, target.workspaceId)
  override def toString: String = s"FileWriteTextResult($target,$outcome,FileWrite)"
}

/** Direct adapter owned by the current UoW, using the shared trusted workspace binding. */
private[cncf] object WorkspaceFileExecution {
  def prepareDirectoryC(context: ExecutionContext, target: FileTarget, path: WorkspaceFilePath): Consequence[FilePrepareDirectoryResult] =
    _admit_c(context, target, path, WorkspacePermission.Manage).flatMap { case (binding, _, validpath) =>
      binding.synchronized {
        _guard_c {
          _prepare_directory_c(binding, validpath).map(outcome => FilePrepareDirectoryResult(target, validpath, outcome))
        }
      }
    }

  def readBytesC(
    context: ExecutionContext,
    target: FileTarget,
    path: WorkspaceFilePath,
    representation: FileRepresentation
  ): Consequence[FileReadBytesResult] =
    _admit_c(context, target, path, WorkspacePermission.ReadOnly).flatMap { case (binding, limits, validpath) =>
      if (representation == null) _invalid_intent_c
      else _guard_c {
        for {
          resolved <- binding.resolveFileC(validpath)
          stored <- _read_bytes_c(resolved, limits)
          decoded <- stored match {
            case None => Consequence.success(None)
            case Some(bytes) => representation match {
              case FileRepresentation.Identity => Consequence.success(Some(bytes))
              case FileRepresentation.Gzip =>
                _decode_gzip_c(bytes, limits).map(Some(_))
            }
          }
        } yield FileReadBytesResult(target, validpath, representation, decoded)
      }
    }

  def writeBytesC(
    context: ExecutionContext,
    target: FileTarget,
    path: WorkspaceFilePath,
    bytes: Vector[Byte],
    representation: FileRepresentation
  ): Consequence[FileWriteBytesResult] =
    for {
      admitted <- _admit_c(context, target, path, WorkspacePermission.Manage)
      (binding, limits, validpath) = admitted
      _ <- if (bytes == null || representation == null) _invalid_intent_c[Unit]
        else if (bytes.size > limits.maximumBytes) _overflow_c[Unit]
        else if (representation == FileRepresentation.Gzip && limits.maximumBytes < 20) _overflow_c[Unit]
        else Consequence.unit
      outcome <- binding.synchronized {
        _guard_c {
          binding.resolveFileC(validpath).flatMap { destination =>
            _attributes_c(destination).flatMap {
              case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
              case None => _with_staging_c(destination) { channel =>
                val output = new BoundedFileOutput(channel, limits.maximumBytes)
                representation match {
                  case FileRepresentation.Identity => output.write(bytes.toArray)
                  case FileRepresentation.Gzip =>
                    Using.resource(new GZIPOutputStream(output))(_.write(bytes.toArray))
                }
              } { staged => _install_new_c(binding, validpath, staged) }
            }
          }
        }
      }
    } yield FileWriteBytesResult(target, validpath, representation, outcome)

  def readTextC(context: ExecutionContext, target: FileTarget, path: WorkspaceFilePath): Consequence[FileReadTextResult] =
    _admit_c(context, target, path, WorkspacePermission.ReadOnly).flatMap { case (binding, limits, validpath) =>
      _guard_c {
        binding.resolveFileC(validpath).flatMap(_read_c(_, limits))
          .map(text => FileReadTextResult(target, validpath, text))
      }
    }

  def writeTextC(
    context: ExecutionContext,
    target: FileTarget,
    path: WorkspaceFilePath,
    text: String,
    mode: FileWriteMode
  ): Consequence[FileWriteTextResult] =
    for {
      admitted <- _admit_c(context, target, path, WorkspacePermission.Manage)
      (binding, limits, validpath) = admitted
      bytes <- _text_bytes_c(text, limits)
      _ <- mode match {
        case FileWriteMode.CreateNew => Consequence.unit
        case FileWriteMode.UpdateExisting(expected) => _text_bytes_c(expected, limits).map(_ => ())
        case null => _invalid_intent_c
      }
      outcome <- binding.synchronized {
        _guard_c {
          for {
            destination <- binding.resolveFileC(validpath)
            result <- mode match {
              case FileWriteMode.CreateNew => _attributes_c(destination).flatMap {
                case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
                case None => _with_staged_c(destination, bytes) { staged =>
                  binding.resolveFileC(validpath).flatMap { resolved =>
                    _install_new_at_c(resolved, staged)
                  }
                }
              }
              case FileWriteMode.UpdateExisting(expected) => _read_c(destination, limits).flatMap { current => _conflict(current, expected) match {
                case Some(reason) => Consequence.success(FileWriteOutcome.Conflict(reason))
                case None => _with_staged_c(destination, bytes) { staged =>
                  val view = Files.getFileAttributeView(destination, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)
                  if (view != null) Files.setPosixFilePermissions(staged, view.readAttributes().permissions())
                  for {
                    resolved <- binding.resolveFileC(validpath)
                    latest <- _read_c(resolved, limits)
                    result <- _conflict(latest, expected) match {
                      case Some(reason) => Consequence.success(FileWriteOutcome.Conflict(reason))
                      case None =>
                        Files.move(staged, resolved, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        Consequence.success(FileWriteOutcome.Updated)
                    }
                  } yield result
                }
              } }
              case null => _invalid_intent_c
            }
          } yield result
        }
      }
    } yield FileWriteTextResult(target, validpath, outcome)

  private def _admit_c(
    context: ExecutionContext,
    target: FileTarget,
    path: WorkspaceFilePath,
    permission: WorkspacePermission
  ): Consequence[(WorkspaceBinding, ResourceReadLimits, WorkspaceFilePath)] =
    if (target == null || path == null || WorkspaceId.parseC(target.workspaceId.value).isFaillure)
      _invalid_intent_c
    else for {
      validpath <- WorkspaceFilePath.parseC(path.value)
      admission <- Option(context).flatMap(_.workspaceExecutionAdmissionOption)
        .map(Consequence.success).getOrElse(Consequence.serviceUnavailable("Workspace execution service is unavailable"))
      binding <- admission.admitC(context.cncfCore.scope, target.workspaceId, permission)
      limits <- context.resourceReadLimitsC
    } yield (binding, limits, validpath)

  private def _attributes_c(path: Path): Consequence[Option[BasicFileAttributes]] = {
    val parent = Files.readAttributes(path.getParent, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    if (!parent.isDirectory || parent.isSymbolicLink)
      _unavailable_c
    else {
      val attributes = try {
        Some(Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS))
      } catch {
        case _: NoSuchFileException => None
      }
      attributes match {
        case None => Consequence.success(None)
        case Some(value) if value.isRegularFile && !value.isSymbolicLink => Consequence.success(Some(value))
        case _ => _unavailable_c
      }
    }
  }

  private def _prepare_directory_c(binding: WorkspaceBinding, path: WorkspaceFilePath): Consequence[FilePrepareDirectoryOutcome] = {
    val segments = path.value.split("/", -1).toVector
    def _check_(checked: Vector[(WorkspaceFilePath, Option[AnyRef])]): Consequence[Unit] =
      checked.foldLeft(Consequence.unit) { case (acc, (previous, key)) =>
        acc.flatMap { _ => binding.directoryIdentityC(previous).flatMap { current =>
          if (current == key) Consequence.unit else WorkspaceBinding.unavailableC
        } }
      }
    def _prepare_(index: Int, checked: Vector[(WorkspaceFilePath, Option[AnyRef])]): Consequence[FilePrepareDirectoryOutcome] =
      for {
        _ <- _check_(checked)
        prefix <- WorkspaceFilePath.parseC(segments.take(index + 1).mkString("/"))
        resolved <- binding.resolveFileC(prefix)
        result <- {
          val before = _directory_attributes(resolved)
          var created = false
          if (before.isEmpty) {
            try {
              Files.createDirectory(resolved)
              created = true
            }
            catch { case _: FileAlreadyExistsException => () }
          }
          val attributes = _directory_attributes(resolved)
          attributes match {
            case Some(value) if before.exists(previous =>
                Option(previous.fileKey()) != Option(value.fileKey()) ||
                  previous.isDirectory != value.isDirectory || previous.isRegularFile != value.isRegularFile) =>
              _unavailable_c
            case Some(value) if value.isDirectory && !value.isSymbolicLink =>
              for {
                current <- binding.directoryIdentityC(prefix)
                outcome <- if (current != Option(value.fileKey()) ||
                    before.exists(previous => Option(previous.fileKey()) != current)) WorkspaceBinding.unavailableC
                  else if (index + 1 < segments.size) _prepare_(index + 1, checked :+ (prefix -> current))
                  else _check_(checked :+ (prefix -> current)).map { _ =>
                    if (created) FilePrepareDirectoryOutcome.Created
                    else FilePrepareDirectoryOutcome.AlreadyPresent
                  }
              } yield outcome
            case Some(value) if value.isRegularFile && !value.isSymbolicLink =>
              Consequence.success(FilePrepareDirectoryOutcome.Conflict)
            case _ => _unavailable_c
          }
        }
      } yield result
    _prepare_(0, Vector.empty)
  }

  private def _directory_attributes(path: Path): Option[BasicFileAttributes] =
    try Some(Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS))
    catch { case _: NoSuchFileException => None }

  private def _read_bytes_c(path: Path, limits: ResourceReadLimits): Consequence[Option[Vector[Byte]]] =
    _attributes_c(path).flatMap {
      case None => Consequence.success(None)
      case Some(_) => ResourceReadAcquisition.readC(
        Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), limits
      ).map(Some(_))
    }

  /** Validate every member and all stored bytes before exposing the combined payload. */
  private def _decode_gzip_c(bytes: Vector[Byte], limits: ResourceReadLimits): Consequence[Vector[Byte]] = {
    val stored = bytes.toArray
    if (stored.isEmpty) throw new IOException("invalid workspace gzip")
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](math.min(8192, limits.maximumBytes + 1))
    var offset = 0
    while (offset < stored.length) {
      val content = _gzip_header_end(stored, offset)
      val inflater = new Inflater(true)
      val crc = new CRC32()
      var expanded = 0L
      try {
        inflater.setInput(stored, content, stored.length - content)
        while (!inflater.finished()) {
          val before = inflater.getBytesRead()
          val count = inflater.inflate(buffer, 0, math.min(buffer.length, limits.maximumBytes - output.size() + 1))
          if (count > limits.maximumBytes - output.size()) return _overflow_c
          if (count > 0) {
            output.write(buffer, 0, count)
            crc.update(buffer, 0, count)
            expanded += count
          } else if (!inflater.finished() &&
              (inflater.needsInput() || inflater.needsDictionary() || inflater.getBytesRead() == before)) {
            throw new IOException("invalid workspace gzip")
          }
        }
        val trailer = stored.length - inflater.getRemaining()
        _require_gzip_bytes(stored, trailer, 8)
        if (_gzip_unsigned32(stored, trailer) != crc.getValue() ||
            _gzip_unsigned32(stored, trailer + 4) != (expanded & 0xffffffffL))
          throw new IOException("invalid workspace gzip")
        offset = trailer + 8
      } finally inflater.end()
    }
    Consequence.success(output.toByteArray.toVector)
  }

  private def _gzip_header_end(bytes: Array[Byte], start: Int): Int = {
    _require_gzip_bytes(bytes, start, 10)
    val flags = bytes(start + 3) & 0xff
    if ((bytes(start) & 0xff) != 0x1f || (bytes(start + 1) & 0xff) != 0x8b ||
        bytes(start + 2) != 8 || (flags & 0xe0) != 0)
      throw new IOException("invalid workspace gzip")
    var offset = start + 10
    if ((flags & 4) != 0) {
      _require_gzip_bytes(bytes, offset, 2)
      val length = (bytes(offset) & 0xff) | ((bytes(offset + 1) & 0xff) << 8)
      offset += 2
      _require_gzip_bytes(bytes, offset, length)
      offset += length
    }
    if ((flags & 8) != 0) offset = _gzip_string_end(bytes, offset)
    if ((flags & 16) != 0) offset = _gzip_string_end(bytes, offset)
    if ((flags & 2) != 0) {
      _require_gzip_bytes(bytes, offset, 2)
      val crc = new CRC32()
      crc.update(bytes, start, offset - start)
      val expected = (bytes(offset) & 0xff) | ((bytes(offset + 1) & 0xff) << 8)
      if ((crc.getValue() & 0xffffL) != expected) throw new IOException("invalid workspace gzip")
      offset += 2
    }
    offset
  }

  private def _gzip_string_end(bytes: Array[Byte], start: Int): Int = {
    var offset = start
    while (offset < bytes.length && bytes(offset) != 0) offset += 1
    _require_gzip_bytes(bytes, offset, 1)
    offset + 1
  }

  private def _require_gzip_bytes(bytes: Array[Byte], offset: Int, length: Int): Unit =
    if (offset < 0 || length > bytes.length - offset) throw new IOException("invalid workspace gzip")

  private def _gzip_unsigned32(bytes: Array[Byte], offset: Int): Long =
    (bytes(offset).toLong & 0xffL) | ((bytes(offset + 1).toLong & 0xffL) << 8) |
      ((bytes(offset + 2).toLong & 0xffL) << 16) | ((bytes(offset + 3).toLong & 0xffL) << 24)

  private def _install_new_c(binding: WorkspaceBinding, path: WorkspaceFilePath, staged: Path): Consequence[FileWriteOutcome] =
    binding.resolveFileC(path).flatMap(_install_new_at_c(_, staged))

  private def _install_new_at_c(destination: Path, staged: Path): Consequence[FileWriteOutcome] =
    _attributes_c(destination).flatMap {
      case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
      case None =>
        try {
          Files.createLink(destination, staged)
          Consequence.success(FileWriteOutcome.Created)
        } catch {
          case _: FileAlreadyExistsException => _attributes_c(destination).flatMap {
            case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
            case None => _unavailable_c
          }
        }
    }

  private def _read_c(path: Path, limits: ResourceReadLimits): Consequence[Option[String]] =
    _attributes_c(path).flatMap {
      case None => Consequence.success(None)
      case Some(_) =>
          ResourceReadAcquisition.readC(
            Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), limits
          ).flatMap { bytes =>
            val decoder = StandardCharsets.UTF_8.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            try Consequence.success(Some(decoder.decode(ByteBuffer.wrap(bytes.toArray)).toString))
            catch { case NonFatal(_) => Consequence.resourceInvalid("workspace file is not valid UTF-8 text") }
          }
    }

  private def _text_bytes_c(text: String, limits: ResourceReadLimits): Consequence[Vector[Byte]] =
    if (text == null) _invalid_intent_c
    else ResourceReadAcquisition.utf8C(text, limits).flatMap { bytes =>
      if (StandardCharsets.UTF_8.newEncoder().canEncode(text)) Consequence.success(bytes)
      else Consequence.resourceInvalid("workspace file is not valid UTF-8 text")
    }

  private def _conflict(current: Option[String], expected: String): Option[FileWriteConflict] = current match {
    case None => Some(FileWriteConflict.Missing)
    case Some(value) if value != expected => Some(FileWriteConflict.Changed)
    case _ => None
  }

  private def _with_staged_c(
    destination: Path,
    bytes: Vector[Byte]
  )(install: Path => Consequence[FileWriteOutcome]): Consequence[FileWriteOutcome] =
    _with_staging_c(destination) { channel =>
      val buffer = ByteBuffer.wrap(bytes.toArray)
      while (buffer.hasRemaining) channel.write(buffer)
    }(install)

  private def _with_staging_c(
    destination: Path
  )(write: FileChannel => Unit)(install: Path => Consequence[FileWriteOutcome]): Consequence[FileWriteOutcome] = {
    val staged = Files.createTempFile(destination.getParent, ".cncf-file-", ".tmp")
    try {
      Using.resource(FileChannel.open(staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel =>
        write(channel)
        channel.force(true)
      }
      install(staged)
    } finally Files.deleteIfExists(staged)
  }

  /** Bounds every compressed output chunk, including the gzip header and trailer. */
  private final class BoundedFileOutput(channel: FileChannel, maximumbytes: Int) extends OutputStream {
    private var _count = 0L

    override def write(value: Int): Unit = write(Array(value.toByte), 0, 1)

    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
      if (_count + length > maximumbytes) throw new IOException("workspace file byte limit")
      val buffer = ByteBuffer.wrap(bytes, offset, length)
      while (buffer.hasRemaining) channel.write(buffer)
      _count += length
    }
  }

  private def _overflow_c[A]: Consequence[A] =
    Consequence.resourceInvalid("resource exceeds the runtime byte limit")

  private def _guard_c[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => _unavailable_c }

  private def _invalid_intent_c[A]: Consequence[A] =
    Consequence.argumentPolicyViolation("file", "file.workspace.intent", "typed target, path, content and operation", "invalid")

  private def _unavailable_c[A]: Consequence[A] =
    Consequence.resourceInvalid("workspace file operation is unavailable")
}
