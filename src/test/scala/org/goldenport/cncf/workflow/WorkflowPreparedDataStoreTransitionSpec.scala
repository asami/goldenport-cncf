package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord, EventRecordJsonV1}
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Oct. 12, 2026
 */
final class WorkflowPreparedDataStoreTransitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _roots = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "prepared_root_v1"))
  private val _successors = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "prepared_next_v1"))
  private val _effects = DataStore.CollectionId("prepared_effects_v1")
  private val _events = DataStore.CollectionId("prepared_events_v1")
  private val _identity = DataStore.StringEntryId("one")
  private val _owner = _take(DataStoreComponentOwner.create("prepared-workflow"))
  private def _take[A](result: Consequence[A]): A = {
     result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private def _plan = {
    val root = _take(DataStoreConditionalRoot.create(_owner, _roots, _identity, "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "suspended"),
      _take(EntityRevision.createC(2L))))
    _take(DataStoreConditionalTransitionPlan.create(root,
      DataStoreConditionalSuccessor.Create(_owner, _successors, _identity, "revision",
        Record.dataAuto("revision" -> 1L, "work" -> "exact issued payload"))))
  }
  private def _seed(store: DataStore): Unit =
    _take(store.create(_roots, _identity, Record.dataAuto("revision" -> 1L, "status" -> "initial")))
  private def _project_c(result: DataStoreConditionalTransitionResult): Consequence[String] = {
     result match {
    case DataStoreConditionalTransitionResult.Transitioned(root, successor) =>
      if (root.getString("status").contains("suspended") && successor.getString("work").contains("exact issued payload"))
        Consequence.success("committed")
      else Consequence.stateConflict("invalid readback")
    case _: DataStoreConditionalTransitionResult.NotMatched => Consequence.stateConflict("stale prepared plan")
  }
  }
  private def _prepared(store: DataStore & EntityConditionalTransitionDataStore) =
    WorkflowPreparedDataStoreTransition.create(store, _plan, _project_c)
  private def _effect(value: String) =
    EntityVersionedSideEffect.Save(_effects, _identity, Record.dataAuto("payload" -> value))
  private def _event(identity: String, sequence: Long): EventRecord = {
    val instant = Instant.parse("2026-09-29T01:02:03Z")
    EventRecord(EventId("test", "prepared", Some(instant), Some(identity)),
      "workflow.fact", "transition", Map("identity" -> identity), Map.empty,
      instant, true, EventRecord.Status.Stored, EventLane.Transactional, sequence)
  }
  private def _pair(store: DataStore) = Vector(_roots, _successors).map(collection =>
    _take(store.load(collection, _identity)))
  private def _sqlite = {
    val directory = Path.of("target", "phase88", "work", "prepared")
    Files.createDirectories(directory)
    SqlDataStore.sqlite(Files.createTempDirectory(directory, "spec-").resolve("workflow.sqlite").toString)
  }
  private def _reason[A](result: Consequence[A], expected: String): Unit = {
     result match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
      } should contain (expected)
    case _ => fail("expected structured failure")
  }
  }
  private def _with_store(backend: String)(work: (DataStore & EntityConditionalTransitionDataStore) => Unit): Unit = {
    val store: DataStore & EntityConditionalTransitionDataStore =
      if (backend == "memory") new DataStore.InMemoryDataStore(CommitRecorder.noop) else _sqlite
    try work(store) finally store match {
      case sql: SqlDataStore => _take(sql.closeC())
      case _ => ()
    }
  }

  Vector("memory", "sqlite").foreach { backend =>
    s"Prepared canonical Workflow commit ($backend)" should {
      "publish no record during preparation or composition and commit all admitted records together" in _with_store(backend) { store =>
        Given("one guarded root and a not-yet-published successor")
        _seed(store)
        val before = _pair(store)
        When("a prepared transition is composed with one explicit local record effect")
        val prepared = _prepared(store)
        val composed = _take(prepared.withSideEffectsC(Vector(_effect("retained local value"))))
        Then("preparation and composition publish nothing and change no guard")
        _pair(store) shouldBe before
        _take(store.load(_effects, _identity)) shouldBe None
        composed.plan.root shouldBe prepared.plan.root
        composed.plan.successor shouldBe prepared.plan.successor
        When("the host performs the single canonical commit")
        _take(composed.commitC()) shouldBe "committed"
        Then("all three records are retained and the original guarded plan cannot replay")
        _pair(store).forall(_.nonEmpty) shouldBe true
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("retained local value")
        prepared.commitC().toOption shouldBe None
      }
      "compose an issued event batch without publishing before the single guarded commit" in _with_store(backend) { store =>
        Given("one guarded root and two fully issued, ordered transactional events")
        _seed(store)
        val first = _event("first", 10L)
        val second = _event("second", 11L)
        val original = _prepared(store)
        When("the host attaches both EventRecords to its prepared record plan")
        val composed = _take(original.withEventRecordsC(_events, Vector(first, second)))
        Then("the event collection and Workflow records remain unpublished")
        Vector(first, second).foreach(event =>
          _take(store.load(_events, DataStore.StringEntryId(event.id.value))) shouldBe None)
        _take(store.load(_successors, _identity)) shouldBe None
        When("the host commits once")
        _take(composed.commitC()) shouldBe "committed"
        Then("the same transaction retains both exact event facts and the guarded successor")
        Vector(first, second).foreach { event =>
          val stored = _take(store.load(_events, DataStore.StringEntryId(event.id.value))).get
          val bytes = stored.getString("event_json").get
          _take(EventRecordJsonV1.decodeStorageC(bytes)) shouldBe event
        }
        _take(store.load(_successors, _identity)).nonEmpty shouldBe true
      }
      "reject duplicate IDs, sequence ambiguity, and unsupported event facts before publication" in _with_store(backend) { store =>
        Given("a prepared guarded root and an issued event batch")
        _seed(store)
        val prepared = _prepared(store)
        val first = _event("first", 10L)
        val second = _event("second", 11L)
        val invalid = Vector(
          Vector.empty[EventRecord],
          Vector(first, first.copy(sequence = 11L)),
          Vector(first, second.copy(sequence = 10L)),
          Vector(second, first),
          Vector(first, second.copy(lane = EventLane.NonTransactional)),
          Vector(first, second.copy(sequence = 0L)),
          Vector(first, second.copy(id = second.id.copy(timestamp = None))),
          Vector(first, second.copy(id = second.id.copy(entropy = None))))
        When("each ambiguous or unsupported batch is attached")
        invalid.foreach(records => prepared.withEventRecordsC(_events, records).toOption shouldBe None)
        prepared.withEventRecordsC(null, Vector(first)).toOption shouldBe None
        prepared.withEventRecordsC(_events, null).toOption shouldBe None
        _take(prepared.withEventRecordsC(_events, Vector(first)))
          .withEventRecordsC(_events, Vector(second)).toOption shouldBe None
        Then("no event or guarded transition is published")
        _take(store.load(_events, DataStore.StringEntryId(first.id.value))) shouldBe None
        _take(store.load(_successors, _identity)) shouldBe None
        _take(store.load(_roots, _identity)).get.getString("status") shouldBe Some("initial")
      }
      "roll back the entire prepared event batch on a late identity collision" in _with_store(backend) { store =>
        Given("one prepared batch before another writer uses its second identity")
        _seed(store)
        val first = _event("first", 10L)
        val second = _event("second", 11L)
        val prepared = _take(_prepared(store).withEventRecordsC(_events, Vector(first, second)))
        val existing = _event("second", 20L)
        val effect = _take(EventRecordJsonV1.createSideEffectC(_events, existing))
        When("the other writer creates that identity before commit")
        _take(store.create(_events, effect.entryId, effect.record))
        prepared.commitC().toOption shouldBe None
        Then("neither the earlier batch event nor the guarded records advance")
        _take(store.load(_events, DataStore.StringEntryId(first.id.value))) shouldBe None
        _take(store.load(_events, effect.entryId)).get.getString("event_json") shouldBe
          effect.record.getString("event_json")
        _take(store.load(_successors, _identity)) shouldBe None
        _take(store.load(_roots, _identity)).get.getString("status") shouldBe Some("initial")
      }
      "reject overlapping, duplicated, invalid and excessive effects without calling the provider" in _with_store(backend) { store =>
        Given("one prepared transition and canonical effect admission constraints")
        _seed(store)
        val before = _pair(store)
        val prepared = _prepared(store)
        val invalid = Vector(
          Vector(EntityVersionedSideEffect.Save(_roots, _identity, Record.dataAuto("overwrite" -> true))),
          Vector(EntityVersionedSideEffect.Save(_successors, _identity, Record.dataAuto("overwrite" -> true))),
          Vector(_effect("first"), _effect("duplicate")),
          Vector.fill(65)(_effect("excess")),
          Vector(EntityVersionedSideEffect.Save(_effects, _identity, null)),
          null)
        When("each invalid effect set is attached")
        val results = invalid.map(prepared.withSideEffectsC)
        Then("no invalid composition is available and every stored fact stays unchanged")
        results.foreach(_.toOption shouldBe None)
        _pair(store) shouldBe before
        _take(store.load(_effects, _identity)) shouldBe None
      }
      "roll back a Create collision introduced after Workflow preparation" in _with_store(backend) { store =>
        Given("one prepared Workflow and an absent side-record identity")
        _seed(store)
        val before = _pair(store)
        val prepared = _take(_prepared(store).withSideEffectsC(Vector(
          EntityVersionedSideEffect.Create(_effects, _identity, Record.dataAuto("payload" -> "prepared")))))
        When("another writer retains that identity before the canonical commit")
        _take(store.create(_effects, _identity, Record.dataAuto("payload" -> "other writer")))
        Then("commit rejects the collision and leaves both Workflow records unchanged")
        prepared.commitC().toOption shouldBe None
        _pair(store) shouldBe before
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("other writer")
      }
      "recheck expected revision at commit after another prepared writer wins" in _with_store(backend) { store =>
        Given("two independently prepared transitions from the same stored root")
        _seed(store)
        val delayed = _take(_prepared(store).withSideEffectsC(Vector(_effect("late effect"))))
        val winner = _prepared(store)
        When("one writer commits before the delayed plan")
        _take(winner.commitC())
        val before = _pair(store)
        val stale = delayed.commitC()
        Then("the delayed plan has no publication authority and its side effect stays absent")
        stale.toOption shouldBe None
        _pair(store) shouldBe before
        _take(store.load(_effects, _identity)) shouldBe None
      }
    }
  }

  "Prepared publication failure and recovery" should {
    "consume normalized actual event readback and withhold success for missing or malformed retained events" in {
      Vector("normalized", "missing", "malformed", "foreign").foreach { distortion =>
        Given(s"a canonical provider whose committed event readback is $distortion")
        var committed = false
        var eventreads = 0
        var projections = 0
        val store = new DataStore.InMemoryDataStore(CommitRecorder.noop) {
          override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
              Consequence[DataStoreConditionalTransitionResult] =
            super.conditionalTransition(plan).map { result =>
              committed = result.isInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
              result
            }

          override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext):
              Consequence[Option[Record]] = {
            if (committed && collection == _events) {
              eventreads += 1
              if (distortion == "missing") Consequence.success(None)
              else if (distortion == "malformed") Consequence.success(Some(Record.dataAuto("event_json" -> "not-native")))
              else {
                val original = _event(if (distortion == "foreign") "foreign" else "readback", 20L)
                  .copy(name = "actual.retained", payload = Map("actual" -> "changed"))
                val json = io.circe.parser.parse(EventRecordJsonV1.encode(original)).toOption.get
                Consequence.success(Some(Record.dataAuto("event_json" ->
                  ("cncf.event-record.v1: \n" + json.spaces2 + "\n "))))
              }
            } else super.load(collection, id)
          }
        }
        _seed(store)
        val event = _event("readback", 10L)
        val base = WorkflowPreparedDataStoreTransition.create[String](store, _plan, result => {
          projections += 1
          _project_c(result)
        })
        val prepared = _take(base.withEventRecordsC(_events, Vector(event)))
        eventreads shouldBe 0
        When("the single guarded transaction commits")
        val result = prepared.commitC()
        Then("actual valid content is consumed while missing or malformed targets remain committed but indeterminate")
        if (distortion == "normalized") _take(result) shouldBe "committed"
        else _reason(result, "transaction-indeterminate")
        eventreads shouldBe 1
        projections shouldBe (if (distortion == "normalized") 1 else 0)
        _pair(store).forall(_.nonEmpty) shouldBe true
      }
    }
    "reject effects outside the same accepting borrowed provider before publication" in {
      Given("a provider accepting only the guarded Workflow collections")
      val store = new DataStore.InMemoryDataStore(CommitRecorder.noop) {
        override def isAccept(collection: DataStore.CollectionId): Boolean = collection != _effects
      }
      _seed(store)
      val before = _pair(store)
      When("an explicit effect is not accepted by that exact provider")
      val rejected = _prepared(store).withSideEffectsC(Vector(_effect("foreign provider")))
      Then("no prepared composition or record publication is available")
      rejected.toOption shouldBe None
      _pair(store) shouldBe before
    }
    "classify a rejected applied projection as indeterminate rather than a safe rollback" in {
      Given("an acknowledged canonical commit and rejected typed readback admission")
      val store = new DataStore.InMemoryDataStore(CommitRecorder.noop)
      _seed(store)
      val prepared = WorkflowPreparedDataStoreTransition.create[String](store, _plan,
        _ => Consequence.stateConflict("readback unavailable"))
      When("the host attempts one commit")
      val result = prepared.commitC()
      Then("committed records remain authoritative and automatic retry is not inferred")
      _reason(result, "transaction-indeterminate")
      _pair(store).forall(_.nonEmpty) shouldBe true
    }
    "roll back all three records on SQLite pre-publication rejection and preserve them on reopen" in {
      Given("a canonical SQLite provider rejecting the compound record commit")
      val directory = Path.of("target", "phase88", "work", "prepared-rollback")
      Files.createDirectories(directory)
      val path = Files.createTempDirectory(directory, "spec-").resolve("workflow.sqlite")
      val datasource = new org.sqlite.SQLiteDataSource()
      datasource.setUrl(s"jdbc:sqlite:$path")
      val store = new SqlDataStore(org.goldenport.cncf.datastore.sql.SqliteDialectDriver, datasource) {
        override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] = {
          if (stage == DataStoreConditionalTransitionCheckpoint.BeforePublish)
            Consequence.operationInvalid("injected prepared commit rejection")
          else Consequence.unit
        }
      }
      _seed(store)
      val before = _pair(store)
      When("the prepared transition commits with a local effect")
      val result = try _take(_prepared(store).withSideEffectsC(Vector(_effect("rolled back")))).commitC()
        finally _take(store.closeC())
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        Then("the failure leaves root, successor and side record unchanged after reopen")
        result.toOption shouldBe None
        _pair(reopened) shouldBe before
        _take(reopened.load(_effects, _identity)) shouldBe None
      } finally _take(reopened.closeC())
    }
    "contain projection failure as transaction-indeterminate without undoing acknowledged records" in {
      Given("a prepared transition whose authoritative projection throws after provider commit")
      val store = new DataStore.InMemoryDataStore(CommitRecorder.noop)
      _seed(store)
      val prepared = WorkflowPreparedDataStoreTransition.create[String](store, _plan,
        _ => throw new IllegalStateException("projection unavailable"))
      When("the host commits once")
      val result = prepared.commitC()
      Then("the acknowledged records remain stored and no rollback or retry is fabricated")
      _reason(result, "transaction-indeterminate")
      _pair(store).forall(_.nonEmpty) shouldBe true
    }
    "contain a throwing provider as an uncertain attempt without retry" in {
      Given("a provider whose exception does not prove whether a commit happened")
      var calls = 0
      val store = new DataStore.InMemoryDataStore(CommitRecorder.noop) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
            Consequence[DataStoreConditionalTransitionResult] = {
          calls += 1
          throw new IllegalStateException("provider outcome unavailable")
        }
      }
      _seed(store)
      When("one prepared commit is requested")
      val result = _prepared(store).commitC()
      Then("the unknown provider outcome stays typed and there is exactly one invocation")
      _reason(result, "transaction-indeterminate")
      calls shouldBe 1
    }
    "preserve generated local values without publishing during preparation" in {
      Given("generated normalized payload values for the explicitly declared local effect")
      val property = Prop.forAll(Gen.alphaNumStr) { text =>
        val store = new DataStore.InMemoryDataStore(CommitRecorder.noop)
        _seed(store)
        When("a side record is prepared and then committed")
        val prepared = _take(_prepared(store).withSideEffectsC(Vector(_effect(text))))
        _take(store.load(_effects, _identity)) shouldBe None
        _take(prepared.commitC())
        Then("the exact value is retained through the canonical provider")
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some(text)
        true
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property).passed shouldBe true
    }
  }
}
