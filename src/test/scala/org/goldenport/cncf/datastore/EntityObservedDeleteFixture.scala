package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path, Paths}
import java.sql.Connection
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import javax.sql.DataSource
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqlDialectDriver, SqliteDialectDriver}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
private[datastore] object EntityObservedDeleteFixture {
  final case class Collections(
    root: DataStore.CollectionId,
    side: DataStore.CollectionId,
    unrelated: DataStore.CollectionId
  )

  final case class Snapshot(
    root: Consequence[Option[Record]],
    side: Consequence[Option[Record]],
    unrelated: Consequence[Option[Record]]
  )

  final case class RaceEvidence(
    ready: Boolean,
    results: Vector[Consequence[EntityCompareAndDeleteResult]],
    terminated: Boolean
  )

  val rootEntry: DataStore.EntryId = DataStore.StringEntryId("root-1")
  val sideEntry: DataStore.EntryId = DataStore.StringEntryId("side-1")
  val unrelatedEntry: DataStore.EntryId = DataStore.StringEntryId("unrelated-1")
  val revisionField: String = "cncf_revision"
  val logicalChanges: Record = Record.dataAuto(
    "aliveness" -> "dead",
    "deleted_at" -> "2026-10-06T00:00:00Z",
    "updated_at" -> "2026-10-06T00:00:00Z"
  )
  val deleteFeatures: Set[EntityMutationProviderFeature] = Set(
    EntityMutationProviderFeature.OptimisticCompareAndDelete,
    EntityMutationProviderFeature.AuthoritativeDeleteResult,
    EntityMutationProviderFeature.AtomicSideEffects
  )

  def collections(scenario: String): Collections = {
    def _collection_(suffix: String): DataStore.CollectionId =
      DataStore.CollectionId.EntityStore(EntityCollectionId("test", "observed", s"${scenario}_$suffix"))
    Collections(_collection_("root"), _collection_("side"), _collection_("unrelated"))
  }

  def context(store: DataStore): ExecutionContext = {
    val result = ExecutionContext.create()
    result.dataStoreSpace.useDataStore(store)
    result
  }

  def revision(value: Long): EntityRevision =
    success(EntityRevision.createC(value))

  def success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalArgumentException(conclusion.display)
    }

  def rootRecord(value: Any = 1L, title: String = "before"): Record =
    Record.dataAuto(
      "id" -> rootEntry.print,
      "title" -> title,
      "business" -> "untouched",
      "overflow_ref" -> sideEntry.print,
      "aliveness" -> "alive",
      "deleted_at" -> "",
      "updated_at" -> "2026-10-05T00:00:00Z",
      revisionField -> value
    )

  def seed(
    store: DataStore,
    collections: Collections,
    root: Record = rootRecord()
  )(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      _ <- store.create(collections.root, rootEntry, root)
      _ <- store.create(collections.side, sideEntry, Record.dataAuto("id" -> sideEntry.print, "body" -> "overflow"))
      _ <- store.create(collections.unrelated, unrelatedEntry,
        Record.dataAuto("id" -> unrelatedEntry.print, "title" -> "retained", revisionField -> 9L))
    } yield ()

  def plan(
    collections: Collections,
    expectedRevision: EntityRevision = EntityRevision.INITIAL,
    rootMutation: EntityDeleteRootMutation = EntityDeleteRootMutation.Physical,
    includeSide: Boolean = true
  ): EntityCompareAndDeletePlan =
    EntityCompareAndDeletePlan(
      collections.root, rootEntry, revisionField, expectedRevision, rootMutation,
      Vector(EntityMutationExclusionGuard.EqualTo("aliveness", "dead"), EntityMutationExclusionGuard.Present("deleted_at")),
      if (includeSide) Vector(EntityVersionedSideEffect.Delete(collections.side, sideEntry)) else Vector.empty
    )

  def snapshot(store: DataStore, collections: Collections)(using ctx: ExecutionContext): Snapshot =
    Snapshot(
      store.load(collections.root, rootEntry),
      store.load(collections.side, sideEntry),
      store.load(collections.unrelated, unrelatedEntry)
    )

  def invalidPlans(collections: Collections): Vector[(String, EntityCompareAndDeletePlan)] = {
    val physical = plan(collections)
    val logical = plan(collections, rootMutation = EntityDeleteRootMutation.Logical(logicalChanges), includeSide = false)
    val duplicate = Record(logicalChanges.fields ++ logicalChanges.fields)
    Vector(
      "null plan" -> null,
      "null collection" -> physical.copy(collection = null),
      "null nested collection" -> physical.copy(collection = DataStore.CollectionId.EntityStore(null)),
      "null nested universal entry" -> physical.copy(entryId = DataStore.UniversalEntryId(null)),
      "null entry" -> physical.copy(entryId = null),
      "null nested entry" -> physical.copy(entryId = DataStore.StringEntryId(null)),
      "null observation" -> physical.copy(expectedRevision = null),
      "null revision field" -> physical.copy(revisionField = null),
      "blank revision field" -> physical.copy(revisionField = " "),
      "null root policy" -> physical.copy(rootMutation = null),
      "null guards" -> physical.copy(exclusionGuards = null),
      "null guard" -> physical.copy(exclusionGuards = Vector(null)),
      "blank guard field" -> physical.copy(exclusionGuards = Vector(EntityMutationExclusionGuard.Present(" "))),
      "null guard field" -> physical.copy(exclusionGuards = Vector(EntityMutationExclusionGuard.Present(null))),
      "null guard value" -> physical.copy(exclusionGuards = Vector(EntityMutationExclusionGuard.EqualTo("aliveness", null))),
      "invalid guard value" -> physical.copy(exclusionGuards = Vector(EntityMutationExclusionGuard.EqualTo("aliveness", Record.empty))),
      "null effects" -> physical.copy(sideEffects = null),
      "null effect" -> physical.copy(sideEffects = Vector(null)),
      "null effect collection" -> physical.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(null, sideEntry))),
      "null effect entry" -> physical.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(collections.side, null))),
      "blank effect entry" -> physical.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(collections.side, DataStore.StringEntryId(" ")))),
      "duplicate targets" -> physical.copy(sideEffects = physical.sideEffects ++ physical.sideEffects),
      "root as effect" -> physical.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(collections.root, rootEntry))),
      "over limit" -> physical.copy(sideEffects = Vector.tabulate(65)(index => EntityVersionedSideEffect.Delete(collections.side, DataStore.StringEntryId(s"effect-$index")))),
      "logical effects" -> logical.copy(sideEffects = physical.sideEffects),
      "null patch" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(null)),
      "empty patch" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(Record.empty)),
      "duplicate patch" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(duplicate)),
      "blank patch key" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(Record.dataAuto(" " -> "dead"))),
      "managed revision patch" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(Record.dataAuto(revisionField -> 20L))),
      "managed id patch" -> logical.copy(rootMutation = EntityDeleteRootMutation.Logical(Record.dataAuto("id" -> "replacement")))
    )
  }

  def race(stores: Vector[DataStore], plan: EntityCompareAndDeletePlan): RaceEvidence = {
    val executor = Executors.newFixedThreadPool(stores.size)
    val ready = new CountDownLatch(stores.size)
    val start = new CountDownLatch(1)
    var callersready = false
    var results = Vector.empty[Consequence[EntityCompareAndDeleteResult]]
    var terminated = false
    try {
      val calls = stores.map { store =>
        executor.submit(new Callable[Consequence[EntityCompareAndDeleteResult]] {
          def call(): Consequence[EntityCompareAndDeleteResult] = {
            given ExecutionContext = context(store)
            ready.countDown()
            if (start.await(10L, TimeUnit.SECONDS))
              summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(plan)
            else
              EntityVersionedMutationFailure.transactionFailure("Observed delete caller start timed out")
          }
        })
      }
      callersready = ready.await(10L, TimeUnit.SECONDS)
      start.countDown()
      results = calls.map(_.get(30L, TimeUnit.SECONDS))
    } finally {
      start.countDown()
      executor.shutdown()
      terminated = executor.awaitTermination(30L, TimeUnit.SECONDS)
      if (!terminated) {
        executor.shutdownNow()
        terminated = executor.awaitTermination(5L, TimeUnit.SECONDS)
      }
    }
    RaceEvidence(callersready, results, terminated)
  }

  def withSqlite[A](body: SqliteDatabase => A): A = {
    val work = Paths.get("target", "entity-observed-delete", "work")
    Files.createDirectories(work)
    val directory = Files.createTempDirectory(work, "sqlite-")
    val database = new SqliteDatabase(directory.resolve("observed.db"))
    try body(database)
    finally database.close()
  }

  final class SqliteDatabase(val path: Path) {
    private var _stores = Vector.empty[SqlStore]

    def open(
      checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
      config: SqlDataStore.Config = SqlDataStore.Config(),
      failCommit: Boolean = false
    ): SqlStore = {
      val datasource = _data_source()
      val store = new SqlStore(SqliteDialectDriver, datasource, checkpoint, config, failCommit)
      _stores = _stores :+ store
      store
    }

    def execute(sql: String): Unit = {
      val connection = _data_source().getConnection()
      try {
        val statement = connection.createStatement()
        try { statement.execute(sql); () }
        finally statement.close()
      } finally connection.close()
    }

    def close(): Unit = {
      _stores.foreach(store => success(store.closeC()))
      Vector("-journal", "-wal", "-shm", "").foreach(suffix =>
        Files.deleteIfExists(path.resolveSibling(path.getFileName.toString + suffix))
      )
      Files.deleteIfExists(path.getParent)
      ()
    }

    private def _data_source(): SQLiteDataSource = {
      val config = new SQLiteConfig()
      config.setBusyTimeout(10000)
      config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
      val source = new SQLiteDataSource(config)
      source.setUrl(s"jdbc:sqlite:${path.toAbsolutePath}")
      source
    }
  }

  class SqlStore(
    dialect: SqlDialectDriver,
    datasource: DataSource,
    checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
    config: SqlDataStore.Config = SqlDataStore.Config(),
    failCommit: Boolean = false
  ) extends SqlDataStore(dialect, datasource, config = config) {
    private var _statements = Vector.empty[String]
    def statements: Vector[String] = _statements
    def clearStatements(): Unit = { _statements = Vector.empty }

    override protected def sql_statement(sql: String): Unit = {
      _statements = _statements :+ sql
    }

    override protected def versioned_mutation_checkpoint(current: EntityVersionedMutationCheckpoint): Consequence[Unit] =
      if (checkpoint.contains(current))
        EntityVersionedMutationFailure.providerFailure("Injected observed deletion checkpoint")
      else Consequence.unit

    override protected def versioned_mutation_commit(connection: Connection): Consequence[Unit] =
      if (failCommit)
        EntityVersionedMutationFailure.transactionFailure("Injected observed deletion commit rejection")
      else super.versioned_mutation_commit(connection)
  }

  class MemoryStore(
    checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
    acceptedCollection: Option[DataStore.CollectionId] = None
  ) extends DataStore.InMemoryDataStore(CommitRecorder.noop) with SearchableDataStore {
    private var _checkpoint_count = 0
    def checkpointCount: Int = _checkpoint_count
    override def isAccept(cid: DataStore.CollectionId): Boolean = acceptedCollection.forall(_ == cid)
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] =
      synchronized { ensure_collection(collection).flatMap(_.search(directive)) }
    override protected def versioned_mutation_checkpoint(current: EntityVersionedMutationCheckpoint): Consequence[Unit] = {
      _checkpoint_count += 1
      if (checkpoint.contains(current))
        EntityVersionedMutationFailure.providerFailure("Injected observed deletion checkpoint")
      else Consequence.unit
    }
  }

  final class PausingMemoryStore(val entered: CountDownLatch, release: CountDownLatch) extends MemoryStore() {
    override protected def versioned_mutation_checkpoint(current: EntityVersionedMutationCheckpoint): Consequence[Unit] =
      if (current == EntityVersionedMutationCheckpoint.RootPrepared) {
        entered.countDown()
        if (release.await(10L, TimeUnit.SECONDS)) Consequence.unit
        else EntityVersionedMutationFailure.transactionFailure("Observed deletion checkpoint release timed out")
      } else super.versioned_mutation_checkpoint(current)
  }

  class LegacyProvider extends DataStore with EntityVersionedMutationDataStore {
    private var _generic_count = 0
    def genericCount: Int = _generic_count
    def isAccept(cid: DataStore.CollectionId): Boolean = true
    private def _unexpected[A]: Consequence[A] = {
      _generic_count += 1
      Consequence.operationInvalid("unexpected-generic-store-call")
    }
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = _unexpected
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = _unexpected
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = _unexpected
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = _unexpected
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = _unexpected
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = _unexpected
    def prepare(tx: TransactionContext): PrepareResult = PrepareResult.Prepared
    def commit(tx: TransactionContext): Unit = ()
    def abort(tx: TransactionContext): Unit = ()
  }

  final class RecordingProvider(
    capabilities: EntityMutationProviderCapabilities,
    response: Consequence[EntityCompareAndDeleteResult]
  ) extends LegacyProvider {
    private var _invocation_count = 0
    def invocationCount: Int = _invocation_count
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = capabilities
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] = {
      _invocation_count += 1
      response
    }
  }
}
