package org.goldenport.cncf.workarea

import scala.collection.mutable
import scala.util.control.NonFatal
import java.io.IOException
import java.nio.file.{Files, FileVisitResult, LinkOption, NoSuchFileException, Path, Paths, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.cncf.config.RuntimeConfig

/*
 * @since   Feb.  3, 2026
 *  version Feb.  4, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class WorkArea extends AutoCloseable {
  def id: WorkAreaId
  def limits: WorkArea.Limit

  def dispose(): Consequence[Unit]

  /**
    * Design:
    * - WorkArea guarantees cleanup of all resources it allocated,
    *   even if callers did not delete them manually.
    * - Cleanup is best-effort and idempotent.
    *
    * TODO(metrics):
    * - Emit final usage metrics on close
    */
  def close(): Unit
}
object WorkArea {
  case class Limit(
    maxFiles: Option[Int],
    maxFileSize: Option[Long],
    maxTotalSize: Option[Long]
  )
}

final class FileWorkArea private (
  val id: WorkAreaId,
  val limits: WorkArea.Limit,
  private val _base: Path,
  private val _owns_base: Boolean
) extends WorkArea {
  def this(id: WorkAreaId, limits: WorkArea.Limit, base: Path) =
    this(id, limits, base, false)

  private val _paths = mutable.LinkedHashSet.empty[Path]
  private val _children = mutable.LinkedHashSet.empty[FileWorkArea]
  private var _closed = false

  def partition(): FileWorkArea = synchronized {
    _require_open()
    val child = new FileWorkArea(id, limits, Files.createTempDirectory(_base, "scope-"), true)
    _children += child
    child
  }

  def dispose(): Consequence[Unit] = Consequence {
    synchronized {
      _closed = true
      val failures = Vector.newBuilder[Throwable]
      _children.toVector.foreach { child =>
        child.dispose() match {
          case Consequence.Success(_) => _children -= child
          case Consequence.Failure(conclusion) =>
            failures += conclusion.getException.getOrElse(new IOException(conclusion.show))
        }
      }
      _paths.toVector.foreach { path =>
        try {
          _delete_owned_path(path)
          _paths -= path
        } catch {
          case NonFatal(e) => failures += e
        }
      }
      if (_owns_base && _children.isEmpty && _paths.isEmpty) {
        try Files.deleteIfExists(_base)
        catch {
          case NonFatal(e) => failures += e
        }
      }
      val errors = failures.result()
      if (errors.nonEmpty) {
        val failure = new IOException("WorkArea cleanup failed")
        errors.foreach(failure.addSuppressed)
        throw failure
      }
    }
  }

  private def _require_open(): Unit =
    if (_closed)
      throw new IllegalStateException("WorkArea is closed")

  private def _delete_owned_path(path: Path): Unit = {
    val present = try {
      Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
      true
    } catch {
      case _: NoSuchFileException => false
    }
    if (present) {
      // walkFileTree does not follow symbolic links, including an owned root link.
      val failures = Vector.newBuilder[IOException]
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          try Files.deleteIfExists(file)
          catch {
            case e: IOException => failures += e
          }
          FileVisitResult.CONTINUE
        }

        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
          failures += exc
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          if (exc != null)
            failures += exc
          try Files.deleteIfExists(dir)
          catch {
            case e: IOException => failures += e
          }
          FileVisitResult.CONTINUE
        }
      })
      val errors = failures.result()
      if (errors.nonEmpty) {
        val failure = new IOException("WorkArea owned path cleanup failed")
        errors.foreach(failure.addSuppressed)
        throw failure
      }
    }
  }

 /**
   * Close this WorkArea and release all managed resources.
   *
   * Design:
   * - All files and directories created under this WorkArea
   *   are owned by this WorkArea and MUST be removed on close().
   * - Callers must not delete resources manually.
   *
   * TODO(metrics):
   * - Track number of files/directories created
   * - Track total allocated size
   * - Emit final usage metrics on close
   */
  def close(): Unit = dispose().TAKE

  /**
    * Ownership:
    * - The returned path is owned by this WorkArea.
    * - Callers MAY delete the resource manually if it becomes unnecessary.
    * - Any remaining resources are guaranteed to be removed
    *   when the WorkArea is closed.
    *
    * TODO(metrics):
    * - Increment file count
    * - Track file size
    * - Enforce per-file / total quota
    */
  def createTempFile(prefix: String): Consequence[Path] =
    createTempFile(prefix, ".tmp")

  def createTempFile(prefix: String, suffix: String): Consequence[Path] =
    Consequence {
      synchronized {
        _require_open()
        val path = Files.createTempFile(_base, prefix, suffix)
        _paths += path
        path
      }
    }

  def createTempDir(prefix: String): Consequence[Path] =
    createTempDir(prefix, ".d")

  def createTempDir(prefix: String, suffix: String): Consequence[Path] =
    Consequence {
      synchronized {
        _require_open()
        val path = Files.createTempDirectory(_base, s"${prefix}${suffix}")
        _paths += path
        path
      }
    }

}
object FileWorkArea {
  def create(config: RuntimeConfig): FileWorkArea = {
    // TODO RuntimeConfig
    val date = LocalDate.now()
    val bucket = Paths.get(System.getProperty("java.io.tmpdir", "tmp")).resolve("cncf")
      .resolve(f"${date.getYear}%04d").resolve(f"${date.getMonthValue}%02d")
    Files.createDirectories(bucket)
    new FileWorkArea(
      WorkAreaId("system"),
      WorkArea.Limit(None, None, None),
      Files.createTempDirectory(bucket, "workarea-"),
      true
    )
  }
}

sealed trait WorkAreaGroupOperations extends AutoCloseable {
  def dispose(): Consequence[Unit]

  def close(): Unit = dispose().TAKE

  def createTempFile(prefix: String): Consequence[Path]

  def createTempFile(prefix: String, suffix: String): Consequence[Path]

  def createTempDir(prefix: String): Consequence[Path]

  def createTempDir(prefix: String, suffix: String): Consequence[Path]
}

case class WorkAreaGroup(
  file: FileWorkArea
) extends WorkAreaGroupOperations {
  def allocate(): Consequence[WorkAreaHandle] = Consequence {
    val wag = WorkAreaGroup(file.partition())
    WorkAreaHandle(wag)
  }

  def dispose(): Consequence[Unit] = file.dispose()

  def createTempFile(prefix: String): Consequence[Path] =
    file.createTempFile(prefix)

  def createTempFile(prefix: String, suffix: String): Consequence[Path] =
    file.createTempFile(prefix, suffix)

  def createTempDir(prefix: String): Consequence[Path] =
    file.createTempDir(prefix)

  def createTempDir(prefix: String, suffix: String): Consequence[Path] =
    file.createTempDir(prefix, suffix)
}
object WorkAreaGroup {
  trait Holder extends WorkAreaGroupOperations {
    def areas: WorkAreaGroup

    def createTempFile(prefix: String): Consequence[Path] =
      areas.createTempFile(prefix)

    def createTempFile(prefix: String, suffix: String): Consequence[Path] =
      areas.createTempFile(prefix, suffix)

    def createTempDir(prefix: String): Consequence[Path] =
      areas.createTempDir(prefix)

    def createTempDir(prefix: String, suffix: String): Consequence[Path] =
      areas.createTempDir(prefix, suffix)

    def dispose(): Consequence[Unit] = areas.dispose()
  }
}

final class WorkAreaSpace(
  val areas: WorkAreaGroup
) extends WorkAreaGroup.Holder {
  def allocate(): Consequence[WorkAreaHandle] = areas.allocate()

  def execute[T](f: WorkAreaHandle => Consequence[T]): Consequence[T] =
    allocate().flatMap { handle =>
      _release_scope(handle, _invoke(handle)(f(handle)))
    }

  private[cncf] def executeRetained[T](
    register: AutoCloseable => Consequence[Unit]
  )(
    f: WorkAreaHandle => Consequence[T]
  ): Consequence[T] =
    allocate().flatMap { handle =>
      val result = _invoke(handle) {
        Consequence.run(f(handle)).flatMap { value =>
          Consequence.run(register(handle)).map(_ => value)
        }
      }
      result match {
        case Consequence.Success(_) => result
        case Consequence.Failure(_) => _release_scope(handle, result)
      }
    }

  private def _invoke[T](handle: WorkAreaHandle)(body: => Consequence[T]): Consequence[T] =
    try Consequence.run(body)
    catch {
      case failure: Throwable =>
        try handle.close()
        catch {
          case cleanup: Throwable => failure.addSuppressed(cleanup)
        }
        throw failure
    }

  private def _release_scope[T](handle: WorkAreaHandle, result: Consequence[T]): Consequence[T] =
    (result, handle.dispose()) match {
      case (_, Consequence.Success(_)) => result
      case (Consequence.Success(_), Consequence.Failure(cleanup)) => Consequence.Failure(cleanup)
      case (Consequence.Failure(primary), Consequence.Failure(cleanup)) =>
        Consequence.Failure(cleanup ++ primary)
    }
}
object WorkAreaSpace {
  def create(config: RuntimeConfig): WorkAreaSpace = WorkAreaSpace(
    WorkAreaGroup(
      FileWorkArea.create(config)
    )
  )
}

final class WorkAreaHandle(
  val areas: WorkAreaGroup
) extends WorkAreaGroup.Holder with AutoCloseable {
}

final case class WorkAreaId(
  name: String
) extends UniversalId("cncf", name, "workarea")
