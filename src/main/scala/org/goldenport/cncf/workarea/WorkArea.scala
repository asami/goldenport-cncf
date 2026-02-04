package org.goldenport.cncf.workarea

import scala.util.Using
import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.cncf.config.RuntimeConfig
import java.util.UUID

/*
 * @since   Feb.  3, 2026
 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class WorkArea {
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

final class FileWorkArea(
  val id: WorkAreaId,
  val limits: WorkArea.Limit,
  private val base: Path
) extends WorkArea {
  def partition(): FileWorkArea = FileWorkArea(
    id,
    limits,
    base
  ) // TODO

  def dispose(): Consequence[Unit] = Consequence.success(()) // TODO

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
  def close(): Unit = {
    // Best-effort cleanup; detailed handling is done via dispose(): Consequence
    dispose()
    ()
  }

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
      Files.createTempFile(base, prefix, suffix)
    }

  def createTempDir(prefix: String): Consequence[Path] =
    createTempDir(prefix, ".d")

  def createTempDir(prefix: String, suffix: String): Consequence[Path] =
    Consequence {
      Files.createTempDirectory(base, s"${prefix}${suffix}")
    }

  // def execute[T](f: Path => Consequence[T]): Consequence[T] =
  //   for {
  //     path <- createTempFile("work")
  //     r <- Consequence.run(f(path))
  //   } yield {
  //     try {
  //       Files.deleteIfExists(path)
  //     } catch {
  //       case _: Throwable => ()
  //     }
  //     r
  //   }
}
object FileWorkArea {
  def create(config: RuntimeConfig): FileWorkArea = {
    // TODO RuntimeConfig
    val tmpRoot = Paths.get(System.getProperty("java.io.tmpdir", "tmp")).resolve("cncf")
    Files.createDirectories(tmpRoot)
    FileWorkArea(
      id = WorkAreaId("system"),
      limits = WorkArea.Limit(None, None, None),
      base = tmpRoot
    )
  }
}

sealed trait WorkAreaGroupOperations {
  def dispose(): Consequence[Unit]

  def close(): Unit = {
    // Best-effort cleanup: dispose failures are intentionally ignored here.
    dispose()
    ()
  }

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
    Consequence.using(allocate())(f)
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
