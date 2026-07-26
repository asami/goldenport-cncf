package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{
  SqlDataStore,
  SqliteDialectDriver
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 26, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class SqliteEntityMutationStatementTraceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "SQLite Entity mutation statement trace" should {
    "measure direct mutation readback" which {
      "execute direct acknowledgment with one UPDATE and no target SELECT" in {
        Given("a traced SQLite provider containing one revisioned Entity")
        val store = _store("direct-ack")
        given ExecutionContext = _context(store)
        _seed(store)
        store.clearStatements()

        When("a direct mutation requests acknowledgment only")
        val result =
          store.mutateEntityDirect(
            _direct_plan(
              exclusionguards = Vector(
                EntityMutationExclusionGuard.Present("deleted_at")
              )
            )
          )

        Then("the successful target path performs exactly one UPDATE and no SELECT")
        result shouldBe Consequence.success(_applied_omitted)
        _target_updates(store) should have size 1
        _target_selects(store) shouldBe empty
      }

      "add one target SELECT only for authoritative direct readback" in {
        Given("a traced SQLite provider containing one revisioned Entity")
        val store = _store("direct-readback")
        given ExecutionContext = _context(store)
        _seed(store)
        store.clearStatements()

        When("a direct mutation requests authoritative readback")
        val result =
          store.mutateEntityDirect(
            _direct_plan(
              EntityMutationReadbackRequirement.AuthoritativeRecord
            )
          )

        Then("the provider performs one UPDATE followed by one target SELECT")
        result.map(_readback_revision) shouldBe Consequence.success(Some(2L))
        _target_updates(store) should have size 1
        _target_selects(store) should have size 1
      }

    }

    "measure optimistic compare-and-set" which {
      "execute successful compare-and-set with one qualified UPDATE and no target SELECT" in {
        Given("a traced SQLite provider containing revision one")
        val store = _store("cas-success")
        given ExecutionContext = _context(store)
        _seed(store)
        store.clearStatements()

        When("a compare-and-set mutation supplies the current revision")
        val result =
          store.compareAndSetEntity(
            _compare_and_set_plan(EntityRevision.INITIAL)
          )

        Then("the provider uses a revision-qualified UPDATE without success readback")
        result shouldBe Consequence.success(_applied_omitted)
        _target_updates(store) should have size 1
        _target_updates(store).head should include(
          "\"cncf_revision\" = ?"
        )
        _target_updates(store).head should include(
          "\"cncf_revision\" < ?"
        )
        _target_updates(store).head should include(
          "typeof(\"cncf_revision\") = 'integer'"
        )
        _target_selects(store) shouldBe empty
      }

      "diagnose stale compare-and-set with one UPDATE and one SELECT" in {
        Given("a traced SQLite provider containing revision two")
        val store = _store("cas-stale")
        given ExecutionContext = _context(store)
        _seed(store, revision = 2L)
        store.clearStatements()

        When("a compare-and-set mutation supplies revision one")
        val result =
          store.compareAndSetEntity(
            _compare_and_set_plan(EntityRevision.INITIAL)
          )

        Then("the zero-row UPDATE is followed by one diagnostic target SELECT")
        result shouldBe Consequence.success(
          EntityMutationProviderResult.Stale(
            EntityRevision.INITIAL,
            _revision(2L)
          )
        )
        _target_updates(store) should have size 1
        _target_selects(store) should have size 1
      }

    }

    "preserve native mutation admission guards" which {
      "diagnose revision exhaustion without mutating the target" in {
        Given("a traced SQLite provider containing the maximum revision")
        val store = _store("direct-exhaustion")
        given ExecutionContext = _context(store)
        _seed(store, revision = Long.MaxValue)
        store.clearStatements()

        When("a direct mutation attempts to advance the revision")
        val result =
          store.mutateEntityDirect(_direct_plan())
        val targetupdates = _target_updates(store)
        val targetselects = _target_selects(store)

        Then("the guarded UPDATE writes nothing and one diagnostic SELECT classifies exhaustion")
        result shouldBe a[Consequence.Failure[?]]
        targetupdates should have size 1
        targetselects should have size 1
        store
          .load(_collection, _entry)
          .map(_.flatMap(_.getAny(_revision_field))) shouldBe
          Consequence.success(Some(Long.MaxValue))
      }

      "reject an excluded logical state inside the native UPDATE predicate" in {
        Given(
          "a traced SQLite Entity with deletedAt present while aliveness remains alive"
        )
        val store = _store("excluded-logical-state")
        given ExecutionContext = _context(store)
        store.create(
          _collection,
          _entry,
          _record(1L) ++ Record.dataAuto(
            "aliveness" -> "alive",
            "deleted_at" -> "2026-07-26T00:00:00Z"
          )
        ) shouldBe Consequence.unit
        store.clearStatements()

        When("a direct mutation carries the logical-state exclusion guard")
        val result =
          store.mutateEntityDirect(
            _direct_plan(
              exclusionguards = Vector(
                EntityMutationExclusionGuard.Present("deleted_at")
              )
            )
          )

        Then("the guarded UPDATE affects no row and diagnostics preserve the Entity")
        result shouldBe a[Consequence.Failure[?]]
        _target_updates(store) should have size 1
        _target_selects(store) should have size 1
        store
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("before") -> Some(1L)))
      }

      "reject missing managed revision schema without repairing or mutating it" in {
        Given("a traced SQLite Entity whose table has no managed revision column")
        val store = _store("missing-revision-column")
        given ExecutionContext = _context(store)
        store.create(
          _collection,
          _entry,
          Record.dataAuto(
            "id" -> _entry.print,
            "name" -> "before"
          )
        ) shouldBe Consequence.unit
        store.clearStatements()

        When("a direct native mutation is attempted")
        val result = store.mutateEntityDirect(_direct_plan())

        Then("admission fails before UPDATE and the schema remains unmodified")
        result shouldBe a[Consequence.Failure[?]]
        _target_updates(store) shouldBe empty
        store
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("before") -> None))
      }

      "reject invalid persisted revisions without changing business state" in {
        Given("traced SQLite Entities with zero and fractional revisions")
        val zerostore = _store("zero-revision")
        val fractionalstore = _store("fractional-revision")
        given ExecutionContext = _context(zerostore)
        zerostore.create(
          _collection,
          _entry,
          _record(0L)
        ) shouldBe Consequence.unit
        fractionalstore.create(
          _collection,
          _entry,
          _record(1.5d)
        ) shouldBe Consequence.unit
        zerostore.clearStatements()
        fractionalstore.clearStatements()

        When("direct native mutations encounter the invalid persisted values")
        val zeroresult =
          zerostore.mutateEntityDirect(_direct_plan())
        val fractionalresult =
          fractionalstore.mutateEntityDirect(_direct_plan())

        Then("both guarded UPDATEs affect no row and diagnostics preserve the records")
        zeroresult shouldBe a[Consequence.Failure[?]]
        fractionalresult shouldBe a[Consequence.Failure[?]]
        _target_updates(zerostore) should have size 1
        _target_updates(fractionalstore) should have size 1
        zerostore
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("before") -> Some(0L)))
        fractionalstore
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("before") -> Some(1.5d)))
      }

      "preserve the detached managed revision contract under column normalization" in {
        Given("a normalized SQLite schema using the production detached revision field")
        val store =
          _store(
            "normalized-revision-contract",
            SqlDataStore.Config(normalizeColumnNames = true)
          )
        given ExecutionContext = _context(store)
        store.create(
          _collection,
          _entry,
          Record.dataAuto(
            "id" -> _entry.print,
            "name" -> "before",
            _revision_field -> 1L
          )
        ) shouldBe Consequence.unit
        store.clearStatements()

        When("authoritative direct mutation and stale compare-and-set use the storage field")
        val direct =
          store.mutateEntityDirect(
            _direct_plan(
              EntityMutationReadbackRequirement.AuthoritativeRecord
            )
          )
        val stale =
          store.compareAndSetEntity(
            _compare_and_set_plan(EntityRevision.INITIAL)
          )

        Then("readback and diagnostics retain the detached field and canonical stale result")
        direct.map(_readback_revision) shouldBe Consequence.success(Some(2L))
        stale shouldBe Consequence.success(
          EntityMutationProviderResult.Stale(
            EntityRevision.INITIAL,
            _revision(2L)
          )
        )
        store
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("after") -> Some(2L)))
      }

      "reject normalized aliases of the managed revision before SQL execution" in {
        Given("a normalized SQLite schema using the production detached revision field")
        val store =
          _store(
            "normalized-revision-collision",
            SqlDataStore.Config(normalizeColumnNames = true)
          )
        given ExecutionContext = _context(store)
        store.create(
          _collection,
          _entry,
          Record.dataAuto(
            "id" -> _entry.print,
            "name" -> "before",
            _revision_field -> 1L
          )
        ) shouldBe Consequence.unit
        store.clearStatements()

        When("changes use the normalized logical alias of the managed revision")
        val result =
          store.mutateEntityDirect(
            EntityDirectMutationPlan(
              _collection,
              _entry,
              _revision_field,
              Record.dataAuto(
                "name" -> "after",
                "cncfRevision" -> 40L
              )
            )
          )

        Then("framework-managed revision admission rejects the alias before UPDATE")
        result shouldBe a[Consequence.Failure[?]]
        _target_updates(store) shouldBe empty
        store
          .load(_collection, _entry)
          .map(_.map(record =>
            record.getString("name") -> record.getAny(_revision_field)
          )) shouldBe
          Consequence.success(Some(Some("before") -> Some(1L)))
      }

    }

    "retain guarded fallback and representative statement budgets" which {
      "retain the guarded SELECT UPDATE SELECT sequence" in {
        Given("a traced SQLite provider containing one revisioned Entity")
        val store = _store("guarded")
        given ExecutionContext = _context(store)
        _seed(store)
        store.clearStatements()

        When("the existing guarded provider operation executes")
        val result =
          store.mutateVersionedEntity(
            EntityVersionedMutationPlan(
              _collection,
              _entry,
              _revision_field,
              EntityConcurrencyPolicy.None,
              EntityWritePolicy.AlwaysWrite,
              RevisionPreconditionPolicy.Managed,
              None,
              EntityVersionedRootMutation.Patch(
                Record.data("name" -> "after")
              )
            )
          )

        Then("the guarded fallback still reads before and after its UPDATE")
        result shouldBe a[Consequence.Success[?]]
        _target_updates(store) should have size 1
        _target_selects(store) should have size 2
      }

      "record deterministic statement budgets for a representative repeated workload" in {
        Given(
          "separate direct and guarded SQLite roots plus a fixed repeated mutation count"
        )
        val directstore = _store("representative-direct")
        val guardedstore = _store("representative-guarded")
        given ExecutionContext = _context(directstore)
        _seed(directstore)
        _seed(guardedstore)
        val iterations = 64
        directstore.clearStatements()
        guardedstore.clearStatements()

        When("both provider paths apply the same number of revision-advancing writes")
        val directstarted = System.nanoTime()
        val directresults =
          Vector.tabulate(iterations) { index =>
            directstore.mutateEntityDirect(
              _direct_plan_named(s"direct-$index")
            )
          }
        val directelapsed = System.nanoTime() - directstarted
        val guardedstarted = System.nanoTime()
        val guardedresults =
          Vector.tabulate(iterations) { index =>
            guardedstore.mutateVersionedEntity(
              _guarded_plan(s"guarded-$index")
            )
          }
        val guardedelapsed = System.nanoTime() - guardedstarted
        val directupdates = _target_updates(directstore)
        val directselects = _target_selects(directstore)
        val guardedupdates = _target_updates(guardedstore)
        val guardedselects = _target_selects(guardedstore)

        Then(
          "direct acknowledgment retains one statement per write while guarded execution retains three"
        )
        all(directresults) shouldBe Consequence.success(_applied_omitted)
        all(guardedresults) shouldBe a[Consequence.Success[?]]
        directupdates should have size iterations
        directselects shouldBe empty
        guardedupdates should have size iterations
        guardedselects should have size iterations * 2
        info(
          _performance_evidence(
            iterations,
            directelapsed,
            guardedelapsed
          )
        )
      }
    }
  }

  private val _collection =
    DataStore.CollectionId("entity_native_trace")
  private val _entry =
    DataStore.StringEntryId("entity-1")
  private val _revision_field = "cncf_revision"
  private val _table_marker = "\"entity_native_trace\""
  private val _applied_omitted =
    EntityMutationProviderResult.Applied(
      EntityMutationProviderReadback.Omitted
    )

  private def _store(
    name: String,
    sqlconfig: SqlDataStore.Config = SqlDataStore.Config()
  ): TracedSqliteDataStore = {
    val path =
      Files.createTempFile(s"cncf-native-$name-", ".db")
    val sqliteconfig = new SQLiteConfig()
    sqliteconfig.setBusyTimeout(10000)
    sqliteconfig.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    val datasource = new SQLiteDataSource(sqliteconfig)
    datasource.setUrl(s"jdbc:sqlite:${path.toAbsolutePath}")
    new TracedSqliteDataStore(datasource, sqlconfig)
  }

  private def _seed(
    store: SqlDataStore,
    revision: Long = 1L
  )(using
    ctx: ExecutionContext
  ): Unit =
    store.create(
      _collection,
      _entry,
      Record.dataAuto(
        "id" -> _entry.print,
        "name" -> "before",
        _revision_field -> revision
      )
    ) shouldBe Consequence.unit

  private def _direct_plan(
    readbackrequirement: EntityMutationReadbackRequirement =
      EntityMutationReadbackRequirement.None,
    exclusionguards: Vector[EntityMutationExclusionGuard] = Vector.empty
  ): EntityDirectMutationPlan =
    EntityDirectMutationPlan(
      _collection,
      _entry,
      _revision_field,
      Record.data("name" -> "after"),
      readbackrequirement,
      exclusionguards
    )

  private def _direct_plan_named(
    name: String
  ): EntityDirectMutationPlan =
    EntityDirectMutationPlan(
      _collection,
      _entry,
      _revision_field,
      Record.data("name" -> name)
    )

  private def _guarded_plan(
    name: String
  ): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(
      _collection,
      _entry,
      _revision_field,
      EntityConcurrencyPolicy.None,
      EntityWritePolicy.AlwaysWrite,
      RevisionPreconditionPolicy.Managed,
      None,
      EntityVersionedRootMutation.Patch(
        Record.data("name" -> name)
      )
    )

  private def _record(
    revision: Any
  ): Record =
    Record.dataAuto(
      "id" -> _entry.print,
      "name" -> "before",
      _revision_field -> revision
    )

  private def _compare_and_set_plan(
    expectedrevision: EntityRevision
  ): EntityCompareAndSetMutationPlan =
    EntityCompareAndSetMutationPlan(
      _collection,
      _entry,
      _revision_field,
      expectedrevision,
      Record.data("name" -> "after")
    )

  private def _target_updates(
    store: TracedSqliteDataStore
  ): Vector[String] =
    store.statements.filter(statement =>
      statement.startsWith("UPDATE") && statement.contains(_table_marker)
    )

  private def _target_selects(
    store: TracedSqliteDataStore
  ): Vector[String] =
    store.statements.filter(statement =>
      statement.startsWith("SELECT") && statement.contains(_table_marker)
    )

  private def _readback_revision(
    result: EntityMutationProviderResult
  ): Option[Any] =
    result match {
      case EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Authoritative(record)
          ) =>
        record.getAny(_revision_field)
      case other =>
        fail(s"authoritative applied result expected: $other")
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _performance_evidence(
    iterations: Int,
    directelapsed: Long,
    guardedelapsed: Long
  ): String = {
    def _sample_(elapsed: Long): String = {
      val latency = elapsed.toDouble / iterations.toDouble
      val throughput =
        if (elapsed == 0L)
          Double.PositiveInfinity
        else
          iterations.toDouble * 1000000000.0d / elapsed.toDouble
      f"elapsedNanos=$elapsed latencyNanosPerMutation=$latency%.1f throughputPerSecond=$throughput%.1f"
    }
    s"PC-02 representative workload: mutations=$iterations directStatements=$iterations guardedStatements=${iterations * 3} direct(${_sample_(directelapsed)}) guarded(${_sample_(guardedelapsed)})"
  }

  private final class TracedSqliteDataStore(
    datasource: SQLiteDataSource,
    sqlconfig: SqlDataStore.Config
  ) extends SqlDataStore(
        SqliteDialectDriver,
        datasource,
        config = sqlconfig
      ) {
    private var _statements: Vector[String] = Vector.empty

    def statements: Vector[String] = _statements

    def clearStatements(): Unit =
      _statements = Vector.empty

    override protected def sql_statement(
      sql: String
    ): Unit =
      _statements = _statements :+ sql
  }
}
