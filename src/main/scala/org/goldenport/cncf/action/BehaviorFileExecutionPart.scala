package org.goldenport.cncf.action

import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workspace.{FileSnapshotLimits, FileReadSnapshotResult, FilePrepareDirectoryResult, FileReadBytesResult, FileReadTextResult, FileRepresentation, FileTarget, FileWriteBytesResult, FileWriteMode, FileWriteTextResult, WorkspaceFilePath}

import org.goldenport.cncf.resource.{ResourceTreeSelection, ResourceTreeQueryLimits}

/*
 * @since   Oct. 15, 2026
 *  version Oct. 21, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorFileExecutionPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def file_read_snapshot(target: FileTarget, selection: ResourceTreeSelection,
    limits: ResourceTreeQueryLimits = FileSnapshotLimits.default): ExecUowM[FileReadSnapshotResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileReadSnapshot(target, selection, limits)))

  protected final def file_prepare_directory(target: FileTarget, path: WorkspaceFilePath): ExecUowM[FilePrepareDirectoryResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FilePrepareDirectory(target, path)))

  protected final def file_write_bytes(
    target: FileTarget,
    path: WorkspaceFilePath,
    bytes: Vector[Byte],
    representation: FileRepresentation
  ): ExecUowM[FileWriteBytesResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileWriteBytes(target, path, bytes, representation)))

  protected final def file_read_bytes(
    target: FileTarget,
    path: WorkspaceFilePath,
    representation: FileRepresentation
  ): ExecUowM[FileReadBytesResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileReadBytes(target, path, representation)))

  protected final def file_read_text(target: FileTarget, path: WorkspaceFilePath): ExecUowM[FileReadTextResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileReadText(target, path)))

  protected final def file_write_text(
    target: FileTarget,
    path: WorkspaceFilePath,
    text: String,
    mode: FileWriteMode
  ): ExecUowM[FileWriteTextResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.FileWriteText(target, path, text, mode)))
}
