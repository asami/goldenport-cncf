package org.goldenport.cncf.entity

import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
import javax.sql.DataSource
import com.mysql.cj.jdbc.MysqlDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{MySqlDialectDriver, SqlDataStore, SqlDialectDriver, SqliteDialectDriver}
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
private[cncf] object EntityObservedDeleteCanonicalProviderFixture {
  class MemoryStore(
    checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
    acceptedCollection: Option[DataStore.CollectionId] = None,
    missingFeature: Option[EntityMutationProviderFeature] = None,
    returned: Option[Consequence[EntityCompareAndDeleteResult]] = None
  ) extends DataStore.InMemoryDataStore(CommitRecorder.noop) with SearchableDataStore {
    private var _delete_calls = 0
    private var _generic_deletes = 0
    private var _loads = 0
    private var _last_plan: Option[EntityCompareAndDeletePlan] = None
    def deleteCalls: Int = _delete_calls
    def genericDeletes: Int = _generic_deletes
    def loads: Int = _loads
    def lastPlan: Option[EntityCompareAndDeletePlan] = _last_plan
    def resetObservation(): Unit = {
      _delete_calls = 0
      _generic_deletes = 0
      _loads = 0
      _last_plan = None
    }
    override def isAccept(cid: DataStore.CollectionId): Boolean = acceptedCollection.forall(_ == cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = {
      val supported = super.entityMutationProviderCapabilities
      supported.copy(features = supported.features -- missingFeature)
    }
    override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[org.goldenport.record.Record]] = {
      _loads += 1
      super.load(collection, id)
    }
    override def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_deletes += 1
      super.delete(collection, id)
    }
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] = {
      _delete_calls += 1
      _last_plan = Some(plan)
      returned.getOrElse(super.compareAndDeleteEntity(plan))
    }
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] =
      synchronized { ensure_collection(collection).flatMap(_.search(directive)) }
    override protected def versioned_mutation_checkpoint(current: EntityVersionedMutationCheckpoint): Consequence[Unit] =
      if (checkpoint.contains(current))
        EntityVersionedMutationFailure.providerFailure("Injected canonical deletion checkpoint")
      else Consequence.unit
  }

  class SqlStore(
    dialect: SqlDialectDriver,
    dataSource: DataSource,
    checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
    failCommit: Boolean = false
  ) extends SqlDataStore(dialect, dataSource) {
    override protected def versioned_mutation_checkpoint(current: EntityVersionedMutationCheckpoint): Consequence[Unit] =
      if (checkpoint.contains(current))
        EntityVersionedMutationFailure.providerFailure("Injected canonical deletion checkpoint")
      else Consequence.unit
    override protected def versioned_mutation_commit(connection: Connection): Consequence[Unit] =
      if (failCommit)
        EntityVersionedMutationFailure.transactionFailure("Injected canonical deletion commit rejection")
      else super.versioned_mutation_commit(connection)
  }

  def withSqlite[A](body: SqliteDatabase => A): A = {
    val work = Paths.get("target", "entity-observed-delete-canonical", "work")
    Files.createDirectories(work)
    val directory = Files.createTempDirectory(work, "sqlite-")
    val database = new SqliteDatabase(directory.resolve("canonical.db"))
    try body(database)
    finally database.close()
  }

  final class SqliteDatabase(val path: Path) {
    private var _stores = Vector.empty[SqlDataStore]
    def open(
      checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
      failCommit: Boolean = false
    ): SqlDataStore = {
      val store = if (checkpoint.nonEmpty || failCommit) {
        val config = new SQLiteConfig()
        config.setBusyTimeout(10000)
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        val source = new SQLiteDataSource(config)
        source.setUrl(s"jdbc:sqlite:${path.toAbsolutePath}")
        new SqlStore(SqliteDialectDriver, source, checkpoint, failCommit)
      } else SqlDataStore.jdbc(jdbcUrl = s"jdbc:sqlite:${path.toAbsolutePath}",
        dialect = SqlDataStore.Sqlite, driverClassName = Some("org.sqlite.JDBC"))
      _stores = _stores :+ store
      store
    }
    def close(): Unit = {
      _stores.foreach(store => EntityObservedDeleteCanonicalFixture.success(store.closeC()))
      Vector("-journal", "-wal", "-shm", "").foreach(suffix =>
        Files.deleteIfExists(path.resolveSibling(path.getFileName.toString + suffix)))
      Files.deleteIfExists(path.getParent)
      ()
    }
  }

  final class MysqlDatabase(jdbcUrl: String, username: String, password: String) {
    private var _stores = Vector.empty[SqlDataStore]
    def open(
      checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
      failCommit: Boolean = false
    ): SqlDataStore = {
      val store = if (checkpoint.nonEmpty || failCommit) {
        val source = new MysqlDataSource()
        source.setURL(jdbcUrl)
        source.setUser(username)
        source.setPassword(password)
        new SqlStore(MySqlDialectDriver, source, checkpoint, failCommit)
      } else SqlDataStore.jdbc(jdbcUrl = jdbcUrl, dialect = SqlDataStore.Mysql,
        username = Some(username), password = Some(password), driverClassName = Some("com.mysql.cj.jdbc.Driver"))
      _stores = _stores :+ store
      store
    }
    def close(): Unit =
      _stores.foreach(store => EntityObservedDeleteCanonicalFixture.success(store.closeC()))
  }
}
