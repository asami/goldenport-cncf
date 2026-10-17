package org.goldenport.cncf.action

import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workspace.{FileReadTextResult, FileTarget, FileWriteMode, FileWriteTextResult, WorkspaceFilePath}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorFileExecutionPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
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
