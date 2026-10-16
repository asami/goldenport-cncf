package org.goldenport.cncf.datastore

import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.{EntityConcurrencyPolicy, EntityWritePolicy, RevisionPreconditionPolicy}
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
final class DataStoreCreateSideEffectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _roots = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "create_root_v1"))
  private val _successors = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "create_next_v1"))
  private val _effects = DataStore.CollectionId("create_effects_v1")
  private val _owner = _take(DataStoreComponentOwner.create("create-side-record"))
  private val _identity = DataStore.StringEntryId("one")
  private val _early = DataStore.StringEntryId("early")
  private val _saved = DataStore.StringEntryId("saved")
  private val _deleted = DataStore.StringEntryId("deleted")
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def _record(value: String) = Record.dataAuto("payload" -> value)
  private def _create(value: String): EntityVersionedSideEffect =
    EntityVersionedSideEffect.Create(_effects, _identity, _record(value))
  private def _plan(effects: Vector[EntityVersionedSideEffect], identity: DataStore.EntryId = _identity) = {
    val root = _take(DataStoreConditionalRoot.create(_owner, _roots, identity, "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "suspended"),
      _take(EntityRevision.createC(2L))))
    _take(DataStoreConditionalTransitionPlan.create(root,
      DataStoreConditionalSuccessor.Create(_owner, _successors, identity, "revision",
        Record.dataAuto("revision" -> 1L, "work" -> identity.print)), effects))
  }
  private def _mutation(effects: Vector[EntityVersionedSideEffect]) =
    EntityVersionedMutationPlan(_roots, _identity, "revision",
      EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.AlwaysWrite,
      RevisionPreconditionPolicy.Managed, Some(EntityRevision.INITIAL),
      EntityVersionedRootMutation.Patch(Record.dataAuto("status" -> "completed")),
      sideEffects = effects)
  private def _seed(store: DataStore, identity: DataStore.EntryId = _identity): Unit =
    _take(store.create(_roots, identity, Record.dataAuto("revision" -> 1L, "status" -> "initial")))
  private def _pair(store: DataStore, identity: DataStore.EntryId = _identity) =
    Vector(_roots, _successors).map(collection => _take(store.load(collection, identity)))
  private def _database = {
    val directory = Path.of("target", "phase88", "work", "create-side-record")
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "spec-").resolve("records.sqlite")
  }
  private def _with_store(backend: String)(work: (DataStore & EntityConditionalTransitionDataStore & EntityVersionedMutationDataStore) => Unit): Unit = {
    val store: DataStore & EntityConditionalTransitionDataStore & EntityVersionedMutationDataStore =
      if (backend == "memory") new DataStore.InMemoryDataStore(CommitRecorder.noop)
      else SqlDataStore.sqlite(_database.toString)
    try work(store) finally store match {
      case sql: SqlDataStore => _take(sql.closeC())
      case _ => ()
    }
  }

  Vector("memory", "sqlite").foreach { backend =>
    s"Create-only canonical side records ($backend)" should {
      "commit a new immutable identity with root, successor, Save and Delete" in _with_store(backend) { store =>
        Given("one admitted root, one old mutable side record and one removable side record")
        _seed(store)
        _take(store.create(_effects, _saved, _record("before")))
        _take(store.create(_effects, _deleted, _record("remove")))
        When("one canonical plan creates a new identity and applies the old Save/Delete semantics")
        val effects = Vector(_create("exact"),
          EntityVersionedSideEffect.Save(_effects, _saved, _record("after")),
          EntityVersionedSideEffect.Delete(_effects, _deleted))
        _take(store.conditionalTransition(_plan(effects))) shouldBe a[DataStoreConditionalTransitionResult.Transitioned]
        Then("all guarded and local records publish together")
        _pair(store).forall(_.nonEmpty) shouldBe true
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("exact")
        _take(store.load(_effects, _saved)).get.getString("payload") shouldBe Some("after")
        _take(store.load(_effects, _deleted)) shouldBe None
      }
      "reject equal or different duplicate content and discard earlier staged effects" in _with_store(backend) { store =>
        Given("an existing side identity which must never be overwritten")
        _seed(store)
        _take(store.create(_effects, _identity, _record("original")))
        _take(store.create(_effects, _saved, _record("before")))
        _take(store.create(_effects, _deleted, _record("retained")))
        val before = _pair(store)
        When("the same transaction stages effects then encounters an equal or conflicting Create")
        Vector("original", "different").foreach { payload =>
          val effects = Vector(
            EntityVersionedSideEffect.Create(_effects, _early, _record("early")),
            EntityVersionedSideEffect.Save(_effects, _saved, _record("after")),
            EntityVersionedSideEffect.Delete(_effects, _deleted), _create(payload))
          val result = store.conditionalTransition(_plan(effects))
          Then("the duplicate is a structured failure, not NotMatched or idempotent success")
          result.toOption shouldBe None
          (result, Consequence.DataStoreDuplicate(_identity.print)) match {
            case (Consequence.Failure(actual), Consequence.Failure(expected)) =>
              actual.observation.taxonomy shouldBe expected.observation.taxonomy
            case _ => fail("expected structured datastore duplicate failure")
          }
          _pair(store) shouldBe before
          _take(store.load(_effects, _early)) shouldBe None
          _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("original")
          _take(store.load(_effects, _saved)).get.getString("payload") shouldBe Some("before")
          _take(store.load(_effects, _deleted)).get.getString("payload") shouldBe Some("retained")
        }
      }
      "leave side records untouched when a stale root rejects before Create" in _with_store(backend) { store =>
        Given("a plan whose root guard has already been consumed")
        _seed(store)
        val plan = _plan(Vector(_create("winner")))
        _take(store.conditionalTransition(plan))
        When("the same guarded plan is submitted again")
        _take(store.conditionalTransition(plan)) shouldBe a[DataStoreConditionalTransitionResult.NotMatched]
        Then("only the first record remains and no duplicate overwrites it")
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("winner")
      }
      "reject inadmissible Create records and targets without publication" in _with_store(backend) { store =>
        Given("a root and the existing canonical normalization constraints")
        _seed(store)
        val before = _pair(store)
        val base = _plan(Vector.empty)
        val invalid = Vector(
          Vector(EntityVersionedSideEffect.Create(_effects, _identity, null)),
          Vector(EntityVersionedSideEffect.Create(_effects, _identity, Record.dataAuto("object" -> new Object))),
          Vector(EntityVersionedSideEffect.Create(_effects, _identity, Record.dataAuto("clear" -> org.simplemodeling.model.directive.Update.SetNull))),
          Vector(EntityVersionedSideEffect.Create(_roots, _identity, _record("overlap"))),
          Vector(_create("first"), _create("duplicate")),
          Vector.tabulate(65)(i => EntityVersionedSideEffect.Create(_effects, DataStore.StringEntryId(s"over-$i"), _record("over"))))
        When("each malformed plan is normalized")
        invalid.foreach(effects =>
          DataStoreConditionalTransitionPlan.create(base.root, base.successor, effects).toOption shouldBe None)
        Then("no invalid plan is executable and the root remains unchanged")
        _pair(store) shouldBe before
        _take(store.load(_effects, _identity)) shouldBe None
      }
      "preserve create-only atomicity through ordinary versioned mutation" in _with_store(backend) { store =>
        Given("one versioned root and an already retained side identity")
        _seed(store)
        _take(store.create(_effects, _identity, _record("original")))
        val before = _pair(store)
        When("an ordinary versioned plan stages another Create before the duplicate")
        Vector(null, Record.dataAuto("object" -> new Object),
          Record.dataAuto("clear" -> org.simplemodeling.model.directive.Update.SetNull)).foreach { record =>
          store.mutateVersionedEntity(_mutation(Vector(
            EntityVersionedSideEffect.Create(_effects, _early, record)))).toOption shouldBe None
          _pair(store) shouldBe before
          _take(store.load(_effects, _early)) shouldBe None
        }
        store.mutateVersionedEntity(_mutation(Vector(
          EntityVersionedSideEffect.Create(_effects, _early, _record("early")), _create("overwrite")))).toOption shouldBe None
        Then("neither the root nor the staged Create changes")
        _pair(store) shouldBe before
        _take(store.load(_effects, _early)) shouldBe None
        _take(store.load(_effects, _identity)).get.getString("payload") shouldBe Some("original")
        When("the same root creates a distinct absent side identity")
        _take(store.mutateVersionedEntity(_mutation(Vector(
          EntityVersionedSideEffect.Create(_effects, _early, _record("admitted")))))) shouldBe a[EntityVersionedMutationResult.Applied]
        Then("that identity and the root advance together")
        _take(store.load(_effects, _early)).get.getString("payload") shouldBe Some("admitted")
        _pair(store).head.get.getInt("revision") shouldBe Some(2)
      }
    }
  }

  "Create-only shared identity" should {
    "admit one SQLite writer across independent roots and retain rollback after reopen" in {
      Given("independent SQLite providers and independent valid roots sharing one side identity")
      val database = _database
      val left = SqlDataStore.sqlite(database.toString)
      val right = SqlDataStore.sqlite(database.toString)
      val identities = Vector(DataStore.StringEntryId("left"), DataStore.StringEntryId("right"))
      val pool = Executors.newFixedThreadPool(2)
      val gate = new CountDownLatch(1)
      var outcomes = Vector.empty[Boolean]
      try {
        identities.foreach(identity => _seed(left, identity))
        When("independent writers concurrently attempt Create on that same identity")
        val tasks = Vector(left, right).zip(identities).map { (store, identity) =>
          pool.submit(new java.util.concurrent.Callable[Boolean] {
            def call(): Boolean = {
              gate.await()
              store.conditionalTransition(_plan(Vector(_create(identity.print)), identity)).toOption
                .exists(_.isInstanceOf[DataStoreConditionalTransitionResult.Transitioned])
            }
          })
        }
        gate.countDown()
        outcomes = tasks.map(_.get(20L, TimeUnit.SECONDS))
        Then("exactly one wins without overwriting the event or publishing the losing successor")
        outcomes.count(_ == true) shouldBe 1
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        _take(left.closeC())
        _take(right.closeC())
      }
      val reopened = SqlDataStore.sqlite(database.toString)
      try {
        identities.zip(outcomes).foreach { (identity, won) =>
          val pair = _pair(reopened, identity)
          pair.head.get.getInt("revision") shouldBe Some(if (won) 2 else 1)
          pair(1).nonEmpty shouldBe won
        }
        _take(reopened.load(_effects, _identity)).get.getString("payload") shouldBe
          Some(identities(outcomes.indexOf(true)).print)
      } finally _take(reopened.closeC())
    }
    "retain generated normalized payloads without overwriting a created identity" in {
      Given("bounded generated text values and one fresh canonical memory transaction per value")
      val property = Prop.forAll(Gen.alphaNumStr) { payload =>
        val store = new DataStore.InMemoryDataStore(CommitRecorder.noop)
        _seed(store)
        When("the canonical transition creates that payload")
        val result = store.conditionalTransition(_plan(Vector(_create(payload))))
        Then("the committed side payload stays exact")
        result.toOption.exists(_.isInstanceOf[DataStoreConditionalTransitionResult.Transitioned]) &&
          _take(store.load(_effects, _identity)).flatMap(_.getString("payload")).contains(payload)
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property).passed shouldBe true
    }
  }
}
