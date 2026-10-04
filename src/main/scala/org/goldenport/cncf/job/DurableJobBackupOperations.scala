package org.goldenport.cncf.job

import java.nio.file.Path
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Operator-authorized offline operations; no runtime startup or shutdown here.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class DurableJobBackupOperations private[job] (reader: DurableJobBackupCopyReader) {
  private val _files = new DurableJobBackupFiles(reader)

  def prepare(owner: DurableJobRuntimeOwner, policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default)
      (using ctx: ExecutionContext): Consequence[DurableJobBackupSource] = _protect(false) {
    _authorize.flatMap { _ =>
      if (owner == null || policy == null || !policy.isValid) DurableJobBackupOperations._backup_refused
      else owner._prepare_backup_c(policy)
    }
  }

  def backup(source: DurableJobBackupSource, destination: Path)
      (using ctx: ExecutionContext): Consequence[Unit] = _protect(false) {
    _authorize.flatMap { _ =>
      if (source == null || !source._enter()) DurableJobBackupOperations._backup_refused
      else try source._completed_c().map { _ =>
        val inventory = _files.backupInventory(source)
        _files.destination(destination, Vector(source._database, source._blob_root))
        _files.copy(inventory, destination)
        _files.publishMarker(destination, DurableJobBackupMarker(source._blob_store, source._blob_container,
          inventory.filecount, inventory.bytecount))
      } finally source._leave()
    }
  }

  def restore(artifactRoot: Path, destination: Path, expectedBlobStore: String, expectedContainer: String,
    policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default)
      (using ctx: ExecutionContext): Consequence[DurableJobRestoreResult] = _protect(true) {
    _authorize.flatMap { _ =>
      if (policy == null || !policy.isValid || !DurableJobBackupSource._valid_name(expectedBlobStore) ||
          !DurableJobBackupSource._valid_name(expectedContainer)) DurableJobBackupOperations._restore_refused
      else {
        val (marker, inventory) = _files.artifactInventory(artifactRoot, policy)
        if (marker.blobstore != expectedBlobStore || marker.blobcontainer != expectedContainer)
          DurableJobBackupOperations._restore_refused
        else {
          _files.destination(destination, Vector(artifactRoot))
          Consequence.success(DurableJobRestoreResult(_files.copy(inventory, destination)))
        }
      }
    }
  }

  private def _authorize(using ctx: ExecutionContext): Consequence[Unit] =
    new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope

  private def _protect[A](restore: Boolean)(body: => Consequence[A]): Consequence[A] = {
    def _refused_(): Consequence[A] =
      if (restore) DurableJobBackupOperations._restore_refused else DurableJobBackupOperations._backup_refused
    try {
      if (Thread.currentThread().isInterrupted) _refused_()
      else body.recoverWith(_ => _refused_())
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); _refused_()
      case NonFatal(_) => _refused_()
    }
  }
}

private[cncf] object DurableJobBackupOperations {
  private val _native = new DurableJobBackupOperations(DurableJobBackupCopyReader.native)
  def prepare(owner: DurableJobRuntimeOwner, policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default)
      (using ctx: ExecutionContext): Consequence[DurableJobBackupSource] = _native.prepare(owner, policy)
  def backup(source: DurableJobBackupSource, destination: Path)
      (using ctx: ExecutionContext): Consequence[Unit] = _native.backup(source, destination)
  def restore(artifactRoot: Path, destination: Path, expectedBlobStore: String, expectedContainer: String,
    policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default)
      (using ctx: ExecutionContext): Consequence[DurableJobRestoreResult] =
    _native.restore(artifactRoot, destination, expectedBlobStore, expectedContainer, policy)
  private[job] def _backup_refused[A]: Consequence[A] = Consequence.stateInvalid("durable job backup refused")
  private[job] def _restore_refused[A]: Consequence[A] = Consequence.stateInvalid("durable job restore refused")
}
