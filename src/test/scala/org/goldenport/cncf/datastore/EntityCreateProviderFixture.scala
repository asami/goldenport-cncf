package org.goldenport.cncf.datastore

import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityCreateFailureSafetyFixture, EntityObservedDeleteCanonicalFixture}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalProviderFixture.SqliteDatabase
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import com.mysql.cj.jdbc.MysqlDataSource
import org.goldenport.cncf.datastore.sql.MySqlDialectDriver
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] object EntityCreateProviderFixture {
  import EntityObservedDeleteCanonicalFixture.success

  def storageScalar(value: Any): String = EntityCreateSupport.scalar(value)

  enum ReturnedMode {
    case Delegate, NullConsequence, NullResult, NullRecord, WrongIdentity, WrongRevision, MalformedRecord,
      CommitThenThrow, CommitThenIndeterminate
  }
  final case class Snapshot(root: Option[Map[String, Any]], side: Option[Map[String, Any]],
    oldroot: Option[Map[String, Any]], oldside: Option[Map[String, Any]])

  final class PlainMemoryStore extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    private var _target: Option[EntityCreateFailureSafetyFixture.FaultTarget] = None
    def arm(target: EntityCreateFailureSafetyFixture.FaultTarget): Unit = _target = Some(target)
    override protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit] = {
      import EntityCreateFailureSafetyFixture.FaultMode
      val selected = _target.filter(target => target.rootcollection == plan.collection && target.rootentry == plan.entryId)
      val rejected = checkpoint match {
        case EntityCreateCheckpoint.BeforeRootWrite => selected.exists(_.mode == FaultMode.RootCreate)
        case EntityCreateCheckpoint.BeforePublish => selected.exists(_.mode == FaultMode.BeforePublish)
        case _ => false
      }
      if (rejected) EntityVersionedMutationFailure.providerFailure("Actual plain memory create checkpoint rejection") else Consequence.unit
    }
  }

  final class LiveMysqlContainer extends MySQLContainer(DockerImageName.parse("mysql:8.4")) {
    withDatabaseName("cncf_phase96_atomic_create")
    withUsername("cncf")
    withPassword("cncf-atomic-create-fixture")
  }

  final class MysqlDatabase(jdbcUrl: String, username: String, password: String) {
    private var _stores = Vector.empty[SqlDataStore]
    private def _source: MysqlDataSource = {
      val source = new MysqlDataSource()
      source.setURL(jdbcUrl)
      source.setUser(username)
      source.setPassword(password)
      source
    }
    def open(): SqlDataStore = {
      val store = SqlDataStore.jdbc(jdbcUrl = jdbcUrl, dialect = SqlDataStore.Mysql,
        username = Some(username), password = Some(password), driverClassName = Some("com.mysql.cj.jdbc.Driver"))
      _stores = _stores :+ store
      store
    }
    def openFault(): EntityCreateFailureSafetyFixture.SqlStore = {
      val store = new EntityCreateFailureSafetyFixture.SqlStore(MySqlDialectDriver, _source)
      _stores = _stores :+ store
      store
    }
    def statement(sql: String): Unit = {
      val connection = _source.getConnection()
      try {
        val statement = connection.createStatement()
        try { statement.execute(sql); () } finally statement.close()
      } finally connection.close()
    }
    def lowerCaseTableNames: Int = {
      val connection = _source.getConnection()
      try {
        val statement = connection.createStatement()
        try {
          val rows = statement.executeQuery("SELECT @@lower_case_table_names")
          try { rows.next(); rows.getInt(1) } finally rows.close()
        } finally statement.close()
      } finally connection.close()
    }
    def close(): Unit = _stores.foreach(store => success(store.closeC()))
  }

  class LegacyStore(val underlying: DataStore, acceptedName: Option[String] = None) extends DataStore {
    private var _generic_writes = 0
    def genericWrites: Int = _generic_writes
    def isAccept(cid: DataStore.CollectionId): Boolean = acceptedName.forall(_ == cid.collectionName)
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1; underlying.create(collection, id, record)
    }
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1; underlying.save(collection, id, record)
    }
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1; underlying.update(collection, id, changes)
    }
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1; underlying.delete(collection, id)
    }
    def prepare(tx: TransactionContext): PrepareResult = underlying.prepare(tx)
    def commit(tx: TransactionContext): Unit = underlying.commit(tx)
    def abort(tx: TransactionContext): Unit = underlying.abort(tx)
  }

  class DefaultPortStore(underlying: DataStore) extends LegacyStore(underlying) with EntityCreateDataStore {
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      underlying.asInstanceOf[EntityCreateDataStore].entityMutationProviderCapabilities
  }

  class QualifiedStore(
    underlying: DataStore,
    missingFeature: Option[EntityMutationProviderFeature] = None,
    nullCapabilities: Boolean = false,
    returned: ReturnedMode = ReturnedMode.Delegate,
    acceptedName: Option[String] = None
  ) extends LegacyStore(underlying, acceptedName) with EntityCreateDataStore {
    private var _create_attempts = 0
    def createAttempts: Int = _create_attempts
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      if (nullCapabilities) null
      else {
        val capabilities = underlying.asInstanceOf[EntityCreateDataStore].entityMutationProviderCapabilities
        capabilities.copy(features = capabilities.features -- missingFeature)
      }
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      _create_attempts += 1
      returned match {
        case ReturnedMode.NullConsequence => null
        case ReturnedMode.NullResult => Consequence.success(null)
        case ReturnedMode.NullRecord => Consequence.success(EntityCreateResult(null))
        case ReturnedMode.WrongIdentity => Consequence.success(EntityCreateResult(plan.record.upsertSingle("id", "wrong")))
        case ReturnedMode.WrongRevision => Consequence.success(EntityCreateResult(plan.record.upsertSingle("cncf_revision", 9L)))
        case ReturnedMode.MalformedRecord => Consequence.success(EntityCreateResult(Record(plan.record.fields ++ plan.record.fields)))
        case ReturnedMode.CommitThenThrow =>
          success(underlying.asInstanceOf[EntityCreateDataStore].createEntity(plan))
          throw new IllegalStateException("Foreign create published and escaped")
        case ReturnedMode.CommitThenIndeterminate =>
          success(underlying.asInstanceOf[EntityCreateDataStore].createEntity(plan))
          EntityVersionedMutationFailure.transactionIndeterminate("Foreign create published without acknowledged result")
        case ReturnedMode.Delegate => underlying.asInstanceOf[EntityCreateDataStore].createEntity(plan)
      }
    }
  }

  final class DirectSession(token: String, val store: DataStore) {
    val rootCollection: DataStore.CollectionId = DataStore.CollectionId(s"${token}_root")
    val sideCollection: DataStore.CollectionId = DataStore.CollectionId(s"${token}_side")
    val rootEntry: DataStore.EntryId = DataStore.StringEntryId("fresh")
    val sideEntry: DataStore.EntryId = DataStore.StringEntryId("freshbody")
    val oldEntry: DataStore.EntryId = DataStore.StringEntryId("old")
    val context: ExecutionContext = EntityCreateFailureSafetyFixture.fixture(s"${token}_context", provider = store).base.context
    def plan(title: String = "Candidate", content: String = "large body " * 600): EntityCreatePlan = EntityCreatePlan(
      rootCollection, rootEntry,
      Record.dataAuto("id" -> rootEntry.print, "title" -> title, "cncf_revision" -> 1L, "body_id" -> sideEntry.print),
      Vector(EntityVersionedSideEffect.Save(sideCollection, sideEntry,
        Record.dataAuto("id" -> sideEntry.print, "content" -> content, "title" -> title))))
    def seed(): Unit = {
      given ExecutionContext = context
      success(store.create(rootCollection, oldEntry, Record.dataAuto("id" -> oldEntry.print, "title" -> "Old", "cncf_revision" -> 7L)))
      success(store.create(sideCollection, oldEntry, Record.dataAuto("id" -> oldEntry.print, "content" -> "Retained complete old body")))
    }
    def createC(plan: EntityCreatePlan, provider: DataStore = store): Consequence[EntityCreateResult] =
      new DataStoreSpace().useDataStore(provider).createEntity(plan)(using context)
    def snapshot(reader: DataStore = store): Snapshot = {
      def _row_(collection: DataStore.CollectionId, entry: DataStore.EntryId): Option[Map[String, Any]] =
        success(reader.load(collection, entry)(using context)).map(_.asMap)
      Snapshot(_row_(rootCollection, rootEntry), _row_(sideCollection, sideEntry),
        _row_(rootCollection, oldEntry), _row_(sideCollection, oldEntry))
    }
  }

  def sqliteSource(database: SqliteDatabase): SQLiteDataSource = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(10000)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    val source = new SQLiteDataSource(config)
    source.setUrl(s"jdbc:sqlite:${database.path.toAbsolutePath}")
    source
  }

  def sqliteFaultStore(database: SqliteDatabase, normalizeColumns: Boolean = false): EntityCreateFailureSafetyFixture.SqlStore =
    new EntityCreateFailureSafetyFixture.SqlStore(SqliteDialectDriver, sqliteSource(database),
      SqlDataStore.Config(normalizeColumnNames = normalizeColumns))

  def sqliteStatement(database: SqliteDatabase, sql: String): Unit = {
    val connection = sqliteSource(database).getConnection()
    try {
      val statement = connection.createStatement()
      try { statement.execute(sql); () } finally statement.close()
    } finally connection.close()
  }

  def race[A](first: () => Consequence[A], second: () => Consequence[A]): Vector[Consequence[A]] = {
    val executor = Executors.newFixedThreadPool(2)
    val ready = new CountDownLatch(2)
    val start = new CountDownLatch(1)
    try {
      val futures = Vector(first, second).map { action => executor.submit(new Callable[Consequence[A]] {
        def call(): Consequence[A] = {
          ready.countDown()
          if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Create start barrier timed out")
          action()
        }
      }) }
      if (!ready.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Create workers did not reach start barrier")
      start.countDown()
      futures.map(_.get(30, TimeUnit.SECONDS))
    } finally {
      start.countDown()
      executor.shutdownNow()
      executor.awaitTermination(10, TimeUnit.SECONDS)
    }
  }
}
