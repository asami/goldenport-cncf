package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicBoolean
import com.zaxxer.hikari.HikariDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.blob.{BlobRepository, LocalBlobStore}
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStore}
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}

/*
 * Closed offline layout, bounded policy and original native source ownership.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class DurableJobBackupResourcePolicy(
  maxBytes: Long = 536870912L,
  maxFiles: Int = 16384,
  maxEntries: Int = 32768,
  maxDepth: Int = 16,
  maxRelativePathBytes: Int = 4096,
  maxSegmentBytes: Int = 255
) {
  def isValid: Boolean = maxBytes > 0L && maxBytes <= 536870912L &&
    maxFiles > 0 && maxFiles <= 16384 && maxEntries > 0 && maxEntries <= 32768 &&
    maxDepth > 0 && maxDepth <= 16 && maxRelativePathBytes > 0 && maxRelativePathBytes <= 4096 &&
    maxSegmentBytes > 0 && maxSegmentBytes <= 255
}

private[cncf] object DurableJobBackupResourcePolicy {
  val default: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy()
}

private[cncf] enum DurableJobRestoreState {
  case RestoredAwaitingStartup
}

private[cncf] final case class DurableJobRestoreResult(dataroot: Path) {
  val state: DurableJobRestoreState = DurableJobRestoreState.RestoredAwaitingStartup
  def database: Path = dataroot.resolve("jobs.db")
  def blobs: Path = dataroot.resolve("blobs")
}

private[cncf] final class DurableJobBackupSource private (
  private[job] val _owner: DurableJobRuntimeOwner,
  private[job] val _engine: InMemoryJobEngine,
  private[job] val _subsystem: Subsystem,
  private[job] val _resource: ManagedSqlDataStoreResource,
  private[job] val _database: Path,
  private[job] val _blob_root: Path,
  private[job] val _blob_store: String,
  private[job] val _blob_container: String,
  private[job] val _policy: DurableJobBackupResourcePolicy
) {
  private val _copying = new AtomicBoolean(false)

  private[job] def _enter(): Boolean = _copying.compareAndSet(false, true)
  private[job] def _leave(): Unit = _copying.set(false)

  private[job] def _completed_c(): Consequence[Unit] = {
    val node = _subsystem.systemNode
    if (!_owner._backup_closed || !(_subsystem.jobEngine eq _engine) ||
        !_engine._durable_runtime_owner_option.exists(_ eq _owner) ||
        !_subsystem._shutdown_result_option.exists(_.isSuccess) || node.state != SystemNode.State.Stopped)
      DurableJobBackupOperations._backup_refused
    else node.shutdownC().flatMap { _ =>
      if (_resource.isOpen) DurableJobBackupOperations._backup_refused
      else _resource.closeC()
    }
  }
}

private[cncf] object DurableJobBackupSource {
  // Only the owner calls this under its original lease and original context.
  private[job] def _capture_c(owner: DurableJobRuntimeOwner, engine: InMemoryJobEngine,
    subsystem: Subsystem, inputs: DurableJobRuntimeInputs, policy: DurableJobBackupResourcePolicy)
      : Consequence[DurableJobBackupSource] = {
    val collections = Vector(DurableJobStoreEntity.Collection, DurableJobLifetimeQuotaEntity.Collection,
      DurableJobStorageQuotaEntity.Collection, DurableJobOwnedBlobQuotaEntity.Collection, BlobRepository.CollectionId)
    val routes = collections.foldLeft(Consequence.success(Vector.empty[SqlDataStore])) { (prior, collection) =>
      for {
        selected <- prior
        id <- inputs.context.entityStoreSpace.dataStoreCollection(collection)
        provider <- inputs.context.dataStoreSpace.dataStore(id)
        native <- provider match {
          case value: SqlDataStore => Consequence.success(value)
          case _ => DurableJobBackupOperations._backup_refused
        }
      } yield selected :+ native
    }
    routes.flatMap { providers =>
      val resource = providers.head.managedResourceOption
      (resource, inputs.blobstore) match {
        case (Some(managed), local: LocalBlobStore) if managed.isOpen &&
            providers.forall(_.managedResourceOption.exists(_ eq managed)) => managed.datasource match {
          case hikari: HikariDataSource =>
            for {
              database <- _database_c(hikari.getJdbcUrl)
              status <- local.status()
              source <- (status.location, status.container) match {
                case (Some(location), Some(container)) if status.available && status.backend == "local" &&
                    _valid_name(local.name) && _valid_name(container) =>
                  Consequence.success(new DurableJobBackupSource(owner, engine, subsystem, managed,
                    database, Paths.get(location), local.name, container, policy))
                case _ => DurableJobBackupOperations._backup_refused
              }
            } yield source
          case _ => DurableJobBackupOperations._backup_refused
        }
        case _ => DurableJobBackupOperations._backup_refused
      }
    }
  }

  private def _database_c(url: String): Consequence[Path] = {
    val prefix = "jdbc:sqlite:"
    if (url == null || !url.startsWith(prefix)) DurableJobBackupOperations._backup_refused
    else {
      val name = url.substring(prefix.length)
      if (name.isEmpty || name.startsWith("file:") || name.contains("?") || name.contains("#") ||
          name.contains(":memory:") || name.exists(character => Character.isISOControl(character))) DurableJobBackupOperations._backup_refused
      else {
        val path = Paths.get(name)
        if (path.isAbsolute && path.normalize() == path && path.toString == name)
          Consequence.success(path)
        else DurableJobBackupOperations._backup_refused
      }
    }
  }

  private[job] def _valid_name(value: String): Boolean = value != null &&
    value.nonEmpty && value != "." && value != ".." &&
    value.forall(character => character.isLetterOrDigit || character == '_' || character == '-' || character == '.') &&
    value.getBytes(StandardCharsets.UTF_8).length <= 255
}

private[job] final case class DurableJobBackupEntry(source: Option[Path], relative: Path,
  directory: Boolean, size: Long)
private[job] final case class DurableJobBackupInventory(entries: Vector[DurableJobBackupEntry],
  filecount: Int, bytecount: Long)
private[job] final case class DurableJobBackupMarker(blobstore: String, blobcontainer: String,
  filecount: Int, bytecount: Long) {
  def text: String = s"version=1\nblob.store=$blobstore\nblob.container=$blobcontainer\nfile.count=$filecount\nbyte.count=$bytecount\n"
}
