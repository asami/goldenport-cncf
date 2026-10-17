package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import java.sql.{Connection, SQLException}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.WorkflowPreparedMultiGuardTransition
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/**
 * Real SQLite, test-only opt-in via Workflow/DataStoreSpace; no production activation.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreMultiGuardSqliteRouteSpec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = take(DataStoreComponentOwner.create("workflow"))
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "sqlite_multi", name))
  private val instance = collection("instance")
  private val continuation = collection("continuation")
  private val cursor = collection("cursor")
  private val events = collection("events")
  private val id = DataStore.StringEntryId("guard")
  private def root(cid: DataStore.CollectionId) = take(DataStoreConditionalRoot.create(owner,
    cid, id, "revision", Some(EntityRevision.INITIAL), Vector.empty,
    Record.dataAuto("status" -> "next"), take(EntityRevision.createC(2L))))
  private val guards = Vector(instance, continuation, cursor).map(root)
  private val effects = Vector("event-1", "event-2").map(name =>
    EntityVersionedSideEffect.Create(events, DataStore.StringEntryId(name), Record.dataAuto("event_json" -> name)))
  private val plan = take(DataStoreConditionalMultiGuardPlan.create(
    take(DataStoreConditionalTransitionPlan.create(guards.head,
      DataStoreConditionalSuccessor.Update(guards(1)), effects)), Vector(guards(2)),
    take(DataStoreConditionalMultiGuardScope.create(owner,
      Vector(DataStoreConditionalGuardBinding(owner, cursor))))))
  private val policy = take(DataStoreConditionalMultiGuardHostPolicy.create(owner,
    instance, continuation, Vector(DataStoreConditionalGuardBinding(owner, cursor)), Vector(events)))
  private val targets = DataStoreMultiGuardReadback.targets(plan)
  private def path = Files.createTempDirectory("cncf-sqlite-multi-guard-").resolve("store.sqlite")
  private def source(file: Path, busyTimeout: Int = 10000): SQLiteDataSource = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(busyTimeout)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    val result = new SQLiteDataSource(config)
    result.setUrl(s"jdbc:sqlite:$file")
    result
  }
  private class RoutedSqlite(file: Path) extends SqlDataStore(SqliteDialectDriver, source(file))
      with EntityVerifiedMultiGuardDataStore {
    var calls = 0
    var nativeCommits = 0
    var snapshots = 0
    override protected def commit_multi_guard_for_readback(input: DataStoreConditionalMultiGuardPlan)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = {
      calls += 1
      conditionalMultiGuardAcknowledgedSameOwner(input)
    }
    override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] = {
      nativeCommits += 1
      super.conditional_transition_commit(connection)
    }
    override protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
        (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
      snapshots += 1
      readbackMultiGuardSnapshotC(invocation)
    }
  }
  private def seed(store: DataStore): Unit = guards.foreach { guard =>
    take(store.create(guard.collection, id,
      Record.dataAuto("revision" -> 1L, "status" -> "open", "retained" -> guard.collection.print)))
  }
  private def snapshot(store: DataStore) =
    targets.map { case (cid, entry) => take(store.load(cid, entry)) }
  private def run(store: DataStore, selectedPlan: DataStoreConditionalMultiGuardPlan = plan,
      selectedPolicy: DataStoreConditionalMultiGuardHostPolicy = policy): Consequence[DataStoreMultiGuardReadbackResult] =
    TestComponentFactory.withEmptySubsystem() { subsystem =>
      subsystem._with_managed_datastore_lease_c { lease => for {
        space <- DataStoreSpace.multiGuardHostViewC(selectedPolicy, store, lease)
        prepared <- WorkflowPreparedMultiGuardTransition.prepareC(space, selectedPlan)
        result <- prepared.commitVerifiedC()
      } yield result }
    }
  private def uncertain[A](value: Consequence[A]): Unit = value match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Reason(name) => name
      } should contain("transaction-indeterminate")
    case _ => fail("uncertain commit projected as success")
  }

  "Workflow admitted SQLite multi-guard candidate" should {
    "commit three guards and two Creates once and verify all native post-images on reopen" in {
      val file = path
      val store = new RoutedSqlite(file)
      seed(store)
      val verified = take(run(store)).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
      verified.records.map(item => item.collection -> item.entryId) shouldBe targets
      verified.records.take(3).foreach { item =>
        item.record.getInt("revision") shouldBe Some(2)
        item.record.getString("retained").isDefined shouldBe true
      }
      snapshot(new RoutedSqlite(file)).flatten.map(record =>
        Record(record.fields.filterNot(_.key == "id").sortBy(_.key))) shouldBe verified.records.map(_.record)
      store.calls shouldBe 1
      store.nativeCommits shouldBe 1
      store.snapshots shouldBe 1
    }

    "rollback every earlier update and Create on a stale third guard or second Create collision" in {
      Vector(false, true).foreach { collision =>
        val file = path
        val store = new RoutedSqlite(file)
        seed(store)
        if (collision) take(store.create(events, effects.last.entryId, Record.dataAuto("event_json" -> "existing")))
        else take(store.save(cursor, id, Record.dataAuto("revision" -> 2L, "status" -> "stale")))
        val before = snapshot(store)
        run(store).toOption shouldBe None
        snapshot(new RoutedSqlite(file)) shouldBe before
        store.calls shouldBe 1
        store.nativeCommits shouldBe 0
        store.snapshots shouldBe 0
      }
    }

    "rollback all staged records at every pre-publication checkpoint" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { selected =>
        val file = path
        val store = new RoutedSqlite(file) {
          override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] = if (stage == selected) Consequence.operationInvalid("fixture-precommit-failure")
                else Consequence.unit
        }
        seed(store)
        val before = snapshot(store)
        run(store).toOption shouldBe None
        snapshot(new RoutedSqlite(file)) shouldBe before
        store.nativeCommits shouldBe 0
        store.snapshots shouldBe 0
      }
    }

    "return NotMatched without snapshot or record publication for a stale root" in {
      val store = new RoutedSqlite(path)
      seed(store)
      take(store.save(instance, id, Record.dataAuto("revision" -> 2L, "status" -> "already")))
      val before = snapshot(store)
      take(run(store)).isInstanceOf[DataStoreMultiGuardReadbackResult.NotMatched] shouldBe true
      snapshot(store) shouldBe before
      store.snapshots shouldBe 0
    }

    "rollback schema expansion with record writes and avoid it entirely on a stale root" in {
      Vector(false, true).foreach { stale =>
        val file = path
        val store = new RoutedSqlite(file) {
          override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] = if (stage == DataStoreConditionalTransitionCheckpoint.BeforePublish)
                Consequence.operationInvalid("fixture-precommit-failure") else Consequence.unit
        }
        seed(store)
        if (stale) take(store.save(instance, id, Record.dataAuto("revision" -> 2L, "status" -> "already")))
        def expanded(guard: DataStoreConditionalRoot, value: String) =
          take(DataStoreConditionalRoot.create(guard.componentOwner, guard.collection, guard.entryId,
            guard.revisionField, Some(guard.expectedRevision), guard.expectedFields,
            guard.changes ++ Record.dataAuto("fresh" -> value), guard.nextRevision))
        val expandedRoot = expanded(guards.head, "value")
        val expandedCursor = expanded(guards(2), "cursor")
        val transition = take(DataStoreConditionalTransitionPlan.create(expandedRoot,
          DataStoreConditionalSuccessor.Update(guards(1)), effects))
        val expandedPlan = take(DataStoreConditionalMultiGuardPlan.create(transition,
          Vector(expandedCursor), plan.scope))
        val before = snapshot(store)
        val result = run(store, expandedPlan)
        if (stale) take(result).isInstanceOf[DataStoreMultiGuardReadbackResult.NotMatched] shouldBe true
        else result.toOption shouldBe None
        snapshot(new RoutedSqlite(file)) shouldBe before
        store.snapshots shouldBe 0
      }
    }

    "admit only one winner from two independent SQLite writers through their own host leases" in {
      val file = path
      seed(new RoutedSqlite(file))
      val writers = Vector.fill(2)(new RoutedSqlite(file))
      val ready = new CountDownLatch(2)
      val start = new CountDownLatch(1)
      val pool = Executors.newFixedThreadPool(2)
      given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
      try {
        val pending = writers.map(store => Future {
          ready.countDown()
          if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout")
          run(store)
        })
        ready.await(10, TimeUnit.SECONDS) shouldBe true
        start.countDown()
        val results = Await.result(Future.sequence(pending), 30.seconds).map(take)
        results.count(_.isInstanceOf[DataStoreMultiGuardReadbackResult.Verified]) shouldBe 1
        results.count(_.isInstanceOf[DataStoreMultiGuardReadbackResult.NotMatched]) shouldBe 1
        snapshot(new RoutedSqlite(file)).flatten.size shouldBe 5
        writers.map(_.calls).sum shouldBe 2
        writers.map(_.snapshots).sum shouldBe 1
      } finally {
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
      }
    }

    "keep one coherent snapshot when an independent writer attempts an update between target reads" in {
      val file = path
      val start = new CountDownLatch(1)
      val completed = new CountDownLatch(1)
      val writerSource = source(file, busyTimeout = 0)
      var snapshotPaused = false
      val store = new RoutedSqlite(file) {
        var reading = false
        override protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
            (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
          reading = true
          try super.readback_multi_guard(invocation)
          finally reading = false
        }
        override protected def sql_statement(sql: String): Unit = {
          // The root has already been read when the continuation query is prepared.
          if (reading && !snapshotPaused && sql == SqliteDialectDriver.selectByIdSql(continuation.collectionName)) {
            snapshotPaused = true
            start.countDown()
            completed.await(10, TimeUnit.SECONDS) shouldBe true
          }
        }
      }
      seed(store)
      def updateCursor(): Int = {
        val connection = writerSource.getConnection()
        try {
          val statement = connection.prepareStatement(
            s"UPDATE ${SqliteDialectDriver.quoteIdentifier(cursor.collectionName)} SET revision = 3, status = 'later' WHERE id = ?")
          try {
            statement.setString(1, id.print)
            statement.executeUpdate()
          } finally statement.close()
        } finally connection.close()
      }
      val pool = Executors.newSingleThreadExecutor()
      given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
      try {
        val attempted = Future {
          try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("snapshot pause timeout")
            try Right(updateCursor())
            catch { case error: SQLException => Left(error.getErrorCode) }
          } finally completed.countDown()
        }
        val verified = take(run(store)).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
        snapshotPaused shouldBe true
        // Exact native SQLITE_BUSY, not a scheduling timeout or generic failure.
        Await.result(attempted, 10.seconds) shouldBe Left(5)
        verified.records.map(item => item.collection -> item.entryId) shouldBe targets
        verified.records.take(3).foreach(_.record.getInt("revision") shouldBe Some(2))
        store.calls shouldBe 1
        store.nativeCommits shouldBe 1
        store.snapshots shouldBe 1
        // A separate explicit later write proves that the snapshot lock is released.
        updateCursor() shouldBe 1
        take(store.load(cursor, id)).get.getInt("revision") shouldBe Some(3)
        verified.records(2).record.getInt("revision") shouldBe Some(2)
      } finally {
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
      }
    }

    "classify missing actual post-commit storage as indeterminate without another commit" in {
      val file = path
      val store = new RoutedSqlite(file) {
        override protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
            (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
          take(delete(events, effects.last.entryId))
          super.readback_multi_guard(invocation)
        }
      }
      seed(store)
      uncertain(run(store))
      guards.foreach(guard => take(store.load(guard.collection, id)).get.getInt("revision") shouldBe Some(2))
      take(store.load(events, effects.head.entryId)).isDefined shouldBe true
      store.calls shouldBe 1
      store.nativeCommits shouldBe 1
      store.snapshots shouldBe 1
    }

    "retain actually committed records when the commit acknowledgement is indeterminate" in {
      val file = path
      val store = new RoutedSqlite(file) {
        override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] = {
          connection.commit()
          DataStoreConditionalTransitionFailure.transactionIndeterminate("fixture acknowledged commit uncertainty")
        }
      }
      seed(store)
      uncertain(run(store))
      snapshot(new RoutedSqlite(file)).flatten.size shouldBe 5
      guards.foreach(guard => take(store.load(guard.collection, id)).get.getInt("revision") shouldBe Some(2))
      store.calls shouldBe 1
      store.snapshots shouldBe 0
    }

    "deny an unadmitted provider route before native staging" in {
      val store = new RoutedSqlite(path)
      seed(store)
      take(WorkflowPreparedMultiGuardTransition.prepareC(new DataStoreSpace().useDataStore(store), plan))
        .commitVerifiedC().toOption shouldBe None
      store.calls shouldBe 0
      store.nativeCommits shouldBe 0
      store.snapshots shouldBe 0
    }

    "keep cross-owner native staging denied even when the host policy admits its labels" in {
      val store = new RoutedSqlite(path)
      seed(store)
      val other = take(DataStoreComponentOwner.create("events"))
      val guard = take(DataStoreConditionalRoot.create(other, cursor, id, "revision",
        Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "next"),
        take(EntityRevision.createC(2L))))
      val bindings = Vector(DataStoreConditionalGuardBinding(other, cursor))
      val crossPlan = take(DataStoreConditionalMultiGuardPlan.create(plan.transition,
        Vector(guard), take(DataStoreConditionalMultiGuardScope.create(owner, bindings))))
      val crossPolicy = take(DataStoreConditionalMultiGuardHostPolicy.create(owner,
        instance, continuation, bindings, Vector(events)))
      val before = snapshot(store)
      run(store, crossPlan, crossPolicy).toOption shouldBe None
      snapshot(store) shouldBe before
      store.calls shouldBe 1
      store.nativeCommits shouldBe 0
      store.snapshots shouldBe 0
    }

    "reject business writes to the reserved physical id before staging" in {
      val store = new RoutedSqlite(path)
      seed(store)
      val invalidEffects = effects.updated(0, effects.head.copy(record =
        effects.head.record ++ Record.dataAuto("id" -> "forged")))
      val transition = take(DataStoreConditionalTransitionPlan.create(guards.head,
        DataStoreConditionalSuccessor.Update(guards(1)), invalidEffects))
      val invalidPlan = take(DataStoreConditionalMultiGuardPlan.create(transition,
        plan.additionalGuards, plan.scope))
      val before = snapshot(store)
      run(store, invalidPlan).toOption shouldBe None
      snapshot(store) shouldBe before
      store.calls shouldBe 1
      store.nativeCommits shouldBe 0
      store.snapshots shouldBe 0
    }
  }
}
