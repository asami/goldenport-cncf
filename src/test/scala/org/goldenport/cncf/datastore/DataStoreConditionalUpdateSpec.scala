package org.goldenport.cncf.datastore

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 */
final class DataStoreConditionalUpdateSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val roots = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "update_root_v1"))
  private val seconds = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "update_second_v1"))
  private val entryId = DataStore.StringEntryId("one")
  private val owner = take(DataStoreComponentOwner.create("phase88"))
  private val foreign = take(DataStoreComponentOwner.create("foreign"))
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private def root(collection: DataStore.CollectionId, revision: Long = 1L, token: String = "claim",
      host: DataStoreComponentOwner = owner): DataStoreConditionalRoot =
    take(DataStoreConditionalRoot.create(host, collection, entryId, "revision",
      Some(take(EntityRevision.createC(revision))),
      Vector(DataStoreConditionalExpectedField("token", take(DataStoreConditionalValue.from(token)))),
      Record.dataAuto("status" -> "completed"), take(EntityRevision.createC(revision + 1L))))
  private def plan(second: DataStoreConditionalRoot = root(seconds)) =
    take(DataStoreConditionalTransitionPlan.create(root(roots), DataStoreConditionalSuccessor.Update(second)))
  private def seed(store: DataStore): Unit = Vector(roots, seconds).foreach(collection =>
    take(store.create(collection, entryId, Record.dataAuto("revision" -> 1L, "token" -> "claim", "status" -> "open", "retained" -> "exact"))))
  private def pair(store: DataStore) = Vector(roots, seconds).map(collection => take(store.load(collection, entryId)).get)
  private def sqlite = SqlDataStore.sqlite(Files.createTempDirectory("cncf-update-").resolve("store.sqlite").toString)

  Vector("memory", "sqlite").foreach { backend =>
    def withStore(f: (DataStore & EntityConditionalUpdateDataStore) => Unit): Unit = {
      val store: DataStore & EntityConditionalUpdateDataStore =
        if (backend == "memory") new DataStore.InMemoryDataStore(org.goldenport.cncf.unitofwork.CommitRecorder.noop)
        else sqlite
      try f(store) finally store match {
        case sql: SqlDataStore => take(sql.closeC())
        case _ => ()
      }
    }
    s"Canonical guarded existing successor ($backend)" should {
      "publish two authoritative updates and preserve unchanged fields" in withStore { store =>
        seed(store)
        val result = take(store.conditionalTransition(plan())).asInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
        Vector(result.rootRecord, result.successorRecord) shouldBe pair(store)
        pair(store).foreach { record =>
          record.getInt("revision") shouldBe Some(2)
          record.getString("status") shouldBe Some("completed")
          record.getString("retained") shouldBe Some("exact")
        }
      }
      "reject a stale second revision and exact token mismatch without publishing the first update" in withStore { store =>
        seed(store)
        val before = pair(store)
        Vector(root(seconds, 2L), root(seconds, token = "foreign")).foreach { second =>
          store.conditionalTransition(plan(second)).toOption shouldBe None
          pair(store) shouldBe before
        }
        take(store.conditionalTransition(plan()))
        store.conditionalTransition(plan()).toOption.get shouldBe a[DataStoreConditionalTransitionResult.NotMatched]
      }
      "reject malformed, foreign and overlapping second guards before mutation" in withStore { store =>
        seed(store)
        val before = pair(store)
        Vector(DataStoreConditionalSuccessor.Update(null),
          DataStoreConditionalSuccessor.Update(root(seconds, host = foreign)),
          DataStoreConditionalSuccessor.Update(root(roots))).foreach { second =>
          DataStoreConditionalTransitionPlan.create(root(roots), second).toOption shouldBe None
        }
        DataStoreConditionalRoot.create(owner, seconds, entryId, "revision",
          Some(EntityRevision.INITIAL), Vector.empty, null, take(EntityRevision.createC(2L))).toOption shouldBe None
        DataStoreConditionalTransitionPlan.create(root(roots), DataStoreConditionalSuccessor.Update(root(seconds)),
          Vector(EntityVersionedSideEffect.Save(seconds, entryId, Record.dataAuto("ignored" -> "overwrite")))).toOption shouldBe None
        pair(store) shouldBe before
      }
    }
  }

  "Canonical two-row update" should {
    "discard both staged updates on an in-memory publication failure" in {
      Given("two seeded authoritative rows and a provider that fails before publication")
      val store = new DataStore.InMemoryDataStore(org.goldenport.cncf.unitofwork.CommitRecorder.noop) {
        override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] =
          if (stage == DataStoreConditionalTransitionCheckpoint.BeforePublish) Consequence.operationInvalid("injected") else Consequence.unit
      }
      seed(store)
      val before = pair(store)
      When("the guarded two-row transition reaches the failing publication checkpoint")
      val result = store.conditionalTransition(plan())
      Then("the transition is rejected and neither staged row is published")
      result.toOption shouldBe None
      pair(store) shouldBe before
    }
    "keep a single pair winner across two independently bound SQLite writers" in {
      Given("two SQLite writers sharing seeded rows and one guarded transition")
      val db = Files.createTempDirectory("cncf-update-race-").resolve("store.sqlite")
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      val pool = Executors.newFixedThreadPool(2)
      val gate = new CountDownLatch(1)
      try {
        seed(left)
        val attempt = plan()
        val tasks = Vector(left, right).map(store => pool.submit(new java.util.concurrent.Callable[Boolean] {
          def call(): Boolean = {
            gate.await()
            store.conditionalTransition(attempt).toOption.exists(_.isInstanceOf[DataStoreConditionalTransitionResult.Transitioned])
          }
        }))
        When("both writers race after the common start gate opens")
        gate.countDown()
        val results = tasks.map(_.get(20L, TimeUnit.SECONDS))
        Then("exactly one writer publishes the pair and both rows reach revision two")
        results.count(_ == true) shouldBe 1
        pair(left).foreach(_.getInt("revision") shouldBe Some(2))
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        take(left.closeC())
        take(right.closeC())
      }
    }
  }
}
