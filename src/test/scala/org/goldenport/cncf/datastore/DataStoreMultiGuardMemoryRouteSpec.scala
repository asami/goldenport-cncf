package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.cncf.workflow.WorkflowPreparedMultiGuardTransition
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * Real memory staging behind a test-only opt-in. Not production host assembly.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreMultiGuardMemoryRouteSpec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = take(DataStoreComponentOwner.create("workflow"))
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "memory_route", name))
  private val instance = collection("instance")
  private val continuation = collection("continuation")
  private val cursor = collection("cursor")
  private val events = collection("events")
  private val ids = Vector(instance, continuation, cursor).map(_ -> DataStore.StringEntryId("guard"))
  private val effects = Vector("event-1", "event-2").map(id =>
    EntityVersionedSideEffect.Create(events, DataStore.StringEntryId(id),
      Record.dataAuto("event_json" -> id)))
  private def root(cid: DataStore.CollectionId, who: DataStoreComponentOwner = owner) =
    take(DataStoreConditionalRoot.create(who, cid, DataStore.StringEntryId("guard"),
      "revision", Some(EntityRevision.INITIAL), Vector.empty,
      Record.dataAuto("status" -> "next"), take(EntityRevision.createC(2L))))
  private def plan(who: DataStoreComponentOwner = owner) = {
    val transition = take(DataStoreConditionalTransitionPlan.create(root(instance),
      DataStoreConditionalSuccessor.Update(root(continuation)), effects))
    val scope = take(DataStoreConditionalMultiGuardScope.create(owner,
      Vector(DataStoreConditionalGuardBinding(who, cursor))))
    take(DataStoreConditionalMultiGuardPlan.create(transition, Vector(root(cursor, who)), scope))
  }
  private def policy(who: DataStoreComponentOwner = owner) =
    take(DataStoreConditionalMultiGuardHostPolicy.create(owner, instance, continuation,
      Vector(DataStoreConditionalGuardBinding(who, cursor)), Vector(events)))
  private class RoutedMemory extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with EntityConditionalMultiGuardDataStore {
    var invocations = 0
    override protected def commit_multi_guard(input: DataStoreConditionalMultiGuardPlan)(using ExecutionContext):
        Consequence[DataStoreConditionalTransitionResult] = {
      invocations += 1
      conditionalMultiGuardSameOwner(input)
    }
  }
  private def seed(store: RoutedMemory, stale: Boolean = false, collision: Boolean = false): Unit = {
    ids.foreach { case (cid, id) =>
      take(store.create(cid, id, Record.dataAuto("revision" ->
        (if (stale && cid == cursor) 2L else 1L), "status" -> "open")))
    }
    if (collision) take(store.create(events, effects.last.entryId,
      Record.dataAuto("event_json" -> "existing")))
  }
  private def snapshot(store: RoutedMemory) =
    (ids ++ effects.map(effect => events -> effect.entryId)).map { case (cid, id) =>
      take(store.load(cid, id))
    }
  private def run(store: RoutedMemory, who: DataStoreComponentOwner = owner)
      (check: (DataStoreSpace, WorkflowPreparedMultiGuardTransition) => Unit): Unit =
    TestComponentFactory.withEmptySubsystem() { subsystem =>
      take(subsystem._with_managed_datastore_lease_c { lease =>
        val space = take(DataStoreSpace.multiGuardHostViewC(policy(who), store, lease))
        check(space, take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan(who))))
        Consequence.unit
      })
    }

  "Workflow admitted same-owner memory route" should {
    "publish three guarded updates and two Creates, then read the actual stored records" in {
      val store = new RoutedMemory
      seed(store)
      run(store) { (_, prepared) =>
        val result = take(prepared.commitC()).asInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
        // Fixture diagnostics only; not an admitted Workflow readback projection.
        take(store.load(instance, ids.head._2)).get shouldBe result.rootRecord
        take(store.load(continuation, ids(1)._2)).get shouldBe result.successorRecord
        ids.foreach { case (cid, id) =>
          val stored = take(store.load(cid, id)).get
          stored.getInt("revision") shouldBe Some(2)
          stored.getString("status") shouldBe Some("next")
        }
        effects.foreach { effect =>
          take(store.load(events, effect.entryId)).get.getString("event_json") shouldBe
            effect.record.getString("event_json")
        }
        store.invocations shouldBe 1
      }
    }

    "leave every record unchanged on a stale third guard or second Create collision" in {
      Vector(false, true).foreach { collision =>
        val store = new RoutedMemory
        seed(store, stale = !collision, collision = collision)
        val before = snapshot(store)
        run(store) { (_, prepared) => prepared.commitC().toOption shouldBe None }
        snapshot(store) shouldBe before
        store.invocations shouldBe 1
      }
    }

    "discard every staged record at each pre-publication checkpoint failure" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { selected =>
        val store = new RoutedMemory {
          override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] =
            if (stage == selected) Consequence.operationInvalid("fixture-precommit-failure")
            else Consequence.unit
        }
        seed(store)
        val before = snapshot(store)
        run(store) { (_, prepared) => prepared.commitC().toOption shouldBe None }
        snapshot(store) shouldBe before
        store.invocations shouldBe 1
      }
    }

    "preserve the same-owner restriction even when host route policy admits a different owner" in {
      val store = new RoutedMemory
      seed(store)
      val before = snapshot(store)
      run(store, take(DataStoreComponentOwner.create("event-store"))) { (_, prepared) =>
        prepared.commitC().toOption shouldBe None
      }
      snapshot(store) shouldBe before
      store.invocations shouldBe 1
    }

    "not publish again when the same stale proposal is explicitly submitted a second time" in {
      val store = new RoutedMemory
      seed(store)
      run(store) { (_, prepared) =>
        take(prepared.commitC()).isInstanceOf[DataStoreConditionalTransitionResult.Transitioned] shouldBe true
        val after = snapshot(store)
        take(prepared.commitC()).isInstanceOf[DataStoreConditionalTransitionResult.NotMatched] shouldBe true
        snapshot(store) shouldBe after
        store.invocations shouldBe 2
      }
    }
  }
}
