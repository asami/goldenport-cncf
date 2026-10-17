package org.goldenport.cncf.workspace

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, NoSuchFileException, Path, StandardCopyOption, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, PosixFileAttributeView}
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.resource.{ResourceReadAcquisition, ResourceReadLimits}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FileTarget(workspaceId: WorkspaceId)

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
                    // Inspect a race winner too: links and nonregular occupants are policy failures.
                    _attributes_c(resolved).flatMap {
                      case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
                      case None =>
                        try {
                          Files.createLink(resolved, staged)
                          Consequence.success(FileWriteOutcome.Created)
                        } catch {
                          case _: FileAlreadyExistsException => _attributes_c(resolved).flatMap {
                            case Some(_) => Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
                            case None => _unavailable_c
                          }
                        }
                    }
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
  )(install: Path => Consequence[FileWriteOutcome]): Consequence[FileWriteOutcome] = {
    val staged = Files.createTempFile(destination.getParent, ".cncf-file-", ".tmp")
    try {
      Using.resource(FileChannel.open(staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel =>
        val buffer = ByteBuffer.wrap(bytes.toArray)
        while (buffer.hasRemaining) channel.write(buffer)
        channel.force(true)
      }
      install(staged)
    } finally Files.deleteIfExists(staged)
  }

  private def _guard_c[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => _unavailable_c }

  private def _invalid_intent_c[A]: Consequence[A] =
    Consequence.argumentPolicyViolation("file", "file.workspace.intent", "typed target, path, text and mode", "invalid")

  private def _unavailable_c[A]: Consequence[A] =
    Consequence.resourceInvalid("workspace file operation is unavailable")
}
