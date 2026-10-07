package org.goldenport.cncf.datastore

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreConditionalMultiGuardPlanSpec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private val workflowOwner = take(DataStoreComponentOwner.create("workflow"))
  private val eventOwner = take(DataStoreComponentOwner.create("event-store"))
  private val instanceCollection =
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "multi_instance_v1"))
  private val continuationCollection =
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "multi_continuation_v1"))
  private val cursorCollection =
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "event", "multi_cursor_v1"))
  private val eventCollection =
    DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "event", "multi_event_v1"))
  private val instanceId = DataStore.StringEntryId("run-1")
  private val continuationId = DataStore.StringEntryId("claim-1")
  private val cursorId = DataStore.StringEntryId("global")

  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }

  private def root(
    owner: DataStoreComponentOwner,
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId
  ): DataStoreConditionalRoot =
    take(DataStoreConditionalRoot.create(
      owner,
      collection,
      entryId,
      "revision",
      Some(EntityRevision.INITIAL),
      Vector.empty,
      Record.dataAuto("status" -> "next"),
      take(EntityRevision.createC(2L))
    ))

  private val instance = root(workflowOwner, instanceCollection, instanceId)
  private val continuation = root(workflowOwner, continuationCollection, continuationId)
  private val cursor = root(eventOwner, cursorCollection, cursorId)

  private def transition(
    effects: Vector[EntityVersionedSideEffect] = Vector.empty
  ): DataStoreConditionalTransitionPlan =
    take(DataStoreConditionalTransitionPlan.create(
      instance,
      DataStoreConditionalSuccessor.Update(continuation),
      effects
    ))

  private def scope(guards: Vector[DataStoreConditionalRoot]) =
    take(DataStoreConditionalMultiGuardScope.create(
      workflowOwner,
      guards.map(guard => DataStoreConditionalGuardBinding(
        guard.componentOwner, guard.collection))
    ))

  private def memory =
    new DataStore.InMemoryDataStore(org.goldenport.cncf.unitofwork.CommitRecorder.noop)

  private def seed(store: DataStore.InMemoryDataStore, cursorRevision: Long = 1L): Unit = {
    take(store.create(instanceCollection, instanceId,
      Record.dataAuto("revision" -> 1L, "status" -> "open")))
    take(store.create(continuationCollection, continuationId,
      Record.dataAuto("revision" -> 1L, "status" -> "open")))
    take(store.create(cursorCollection, cursorId,
      Record.dataAuto("revision" -> cursorRevision, "status" -> "open")))
  }

  private def loaded(
    store: DataStore.InMemoryDataStore,
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId
  ): Option[Record] = take(store.load(collection, entryId))

  "S12 pure multi-guard plan" should {
    "admit an exact cross-component third guard and Create-only events" in {
      val effects = Vector(EntityVersionedSideEffect.Create(
        eventCollection, DataStore.StringEntryId("event-1"),
        Record.dataAuto("event_json" -> "canonical")
      ))
      val admitted = take(DataStoreConditionalMultiGuardPlan.create(
        transition(effects), Vector(cursor), scope(Vector(cursor))
      ))
      admitted.additionalGuards shouldBe Vector(cursor)
      admitted.scope.additionalBindings shouldBe Vector(
        DataStoreConditionalGuardBinding(eventOwner, cursorCollection))
    }

    "reject a missing, mismatched or forged scope" in {
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(cursor), null).toOption shouldBe None
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(cursor), scope(Vector(continuation))).toOption shouldBe None
      val forged = new DataStoreConditionalMultiGuardScope(
        eventOwner, Vector(DataStoreConditionalGuardBinding(eventOwner, cursorCollection)))
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(cursor), forged).toOption shouldBe None
    }

    "reject overlapping guards and side records" in {
      val duplicate = root(eventOwner, cursorCollection, cursorId)
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(cursor, duplicate), scope(Vector(cursor, duplicate))
      ).toOption shouldBe None
      val effects = Vector(EntityVersionedSideEffect.Create(
        cursorCollection, cursorId, Record.dataAuto("value" -> "collision")))
      DataStoreConditionalMultiGuardPlan.create(
        transition(effects), Vector(cursor), scope(Vector(cursor))
      ).toOption shouldBe None
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(null), scope(Vector(cursor))
      ).toOption shouldBe None
    }

    "reject unguarded side-effect Save and empty or excessive guards" in {
      val save = Vector(EntityVersionedSideEffect.Save(
        eventCollection, DataStore.StringEntryId("event-1"),
        Record.dataAuto("event_json" -> "overwrite")))
      DataStoreConditionalMultiGuardPlan.create(
        transition(save), Vector(cursor), scope(Vector(cursor))
      ).toOption shouldBe None
      DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector.empty, scope(Vector.empty)
      ).toOption shouldBe None
      val many = (1 to 9).toVector.map(index => root(
        eventOwner, cursorCollection, DataStore.StringEntryId(s"cursor-$index")))
      DataStoreConditionalMultiGuardPlan.create(
        transition(), many, scope(many)
      ).toOption shouldBe None
    }
  }

  "S12 internal same-owner memory staging" should {
    val sameOwnerCursor = root(workflowOwner, cursorCollection, cursorId)
    val effect = EntityVersionedSideEffect.Create(
      eventCollection, DataStore.StringEntryId("event-1"),
      Record.dataAuto("event_json" -> "canonical"))
    val admitted = take(DataStoreConditionalMultiGuardPlan.create(
      transition(Vector(effect)), Vector(sameOwnerCursor), scope(Vector(sameOwnerCursor))))

    "publish all three guarded updates and a Create together" in {
      val store = memory
      seed(store)
      val result = take(store.conditionalMultiGuardSameOwner(admitted))
        .asInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
      result.rootRecord shouldBe loaded(store, instanceCollection, instanceId).get
      result.successorRecord shouldBe loaded(store, continuationCollection, continuationId).get
      Vector(instanceCollection -> instanceId, continuationCollection -> continuationId,
        cursorCollection -> cursorId).foreach { case (collection, entryId) =>
        loaded(store, collection, entryId).get.getInt("revision") shouldBe Some(2)
      }
      loaded(store, eventCollection, DataStore.StringEntryId("event-1"))
        .flatMap(_.getString("event_json")) shouldBe Some("canonical")
    }

    "leave every record unchanged on a stale third guard or Create collision" in {
      Vector(false, true).foreach { collision =>
        val store = memory
        seed(store, if (collision) 1L else 2L)
        if (collision)
          take(store.create(eventCollection, DataStore.StringEntryId("event-1"),
            Record.dataAuto("event_json" -> "existing")))
        val before = Vector(instanceCollection -> instanceId,
          continuationCollection -> continuationId, cursorCollection -> cursorId)
          .map { case (collection, entryId) => loaded(store, collection, entryId) }
        store.conditionalMultiGuardSameOwner(admitted).toOption shouldBe None
        Vector(instanceCollection -> instanceId,
          continuationCollection -> continuationId, cursorCollection -> cursorId)
          .map { case (collection, entryId) => loaded(store, collection, entryId) } shouldBe before
        loaded(store, eventCollection, DataStore.StringEntryId("event-1"))
          .flatMap(_.getString("event_json")) shouldBe
          (if (collision) Some("existing") else None)
      }
    }

    "reject a cross-owner guard without publishing any record" in {
      val store = memory
      seed(store)
      val cross = take(DataStoreConditionalMultiGuardPlan.create(
        transition(Vector(effect)), Vector(cursor), scope(Vector(cursor))))
      store.conditionalMultiGuardSameOwner(cross).toOption shouldBe None
      loaded(store, instanceCollection, instanceId).get.getInt("revision") shouldBe Some(1)
      loaded(store, cursorCollection, cursorId).get.getInt("revision") shouldBe Some(1)
      loaded(store, eventCollection, DataStore.StringEntryId("event-1")) shouldBe None
    }

    "discard the staged state when publication is interrupted" in {
      val store = new DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop) {
        override protected def conditional_transition_checkpoint(
          stage: DataStoreConditionalTransitionCheckpoint
        ): Consequence[Unit] =
          if (stage == DataStoreConditionalTransitionCheckpoint.BeforePublish)
            Consequence.operationInvalid("injected")
          else Consequence.unit
      }
      seed(store)
      store.conditionalMultiGuardSameOwner(admitted).toOption shouldBe None
      loaded(store, instanceCollection, instanceId).get.getInt("revision") shouldBe Some(1)
      loaded(store, continuationCollection, continuationId).get.getInt("revision") shouldBe Some(1)
      loaded(store, cursorCollection, cursorId).get.getInt("revision") shouldBe Some(1)
      loaded(store, eventCollection, DataStore.StringEntryId("event-1")) shouldBe None
    }
  }

  "S12 DataStoreSpace entry" should {
    "deny a valid cross-owner plan before any provider lookup without host admission" in {
      val lookups = new AtomicInteger(0)
      val space = new DataStoreSpace {
        override def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] = {
          lookups.incrementAndGet()
          super.dataStore(cid)
        }
      }.addDataStore(memory)
      val admitted = take(DataStoreConditionalMultiGuardPlan.create(
        transition(), Vector(cursor), scope(Vector(cursor))))

      space.conditionalMultiGuard(admitted).toOption shouldBe None
      lookups.get() shouldBe 0
    }

    "reject a missing plan before any provider lookup" in {
      val lookups = new AtomicInteger(0)
      val space = new DataStoreSpace {
        override def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] = {
          lookups.incrementAndGet()
          super.dataStore(cid)
        }
      }.addDataStore(memory)

      space.conditionalMultiGuard(null).toOption shouldBe None
      lookups.get() shouldBe 0
    }
  }

  "S12 host policy comparison" should {
    val effect = EntityVersionedSideEffect.Create(
      eventCollection, DataStore.StringEntryId("event-1"),
      Record.dataAuto("event_json" -> "canonical"))
    val admitted = take(DataStoreConditionalMultiGuardPlan.create(
      transition(Vector(effect)), Vector(cursor), scope(Vector(cursor))))

    def policy(
      owner: DataStoreComponentOwner = workflowOwner,
      rootCollectionId: DataStore.CollectionId = instanceCollection,
      guardPermissions: Vector[DataStoreConditionalGuardBinding] =
        Vector(DataStoreConditionalGuardBinding(eventOwner, cursorCollection)),
      createPermissions: Vector[DataStore.CollectionId] = Vector(eventCollection)
    ) = take(DataStoreConditionalMultiGuardHostPolicy.create(
      owner, rootCollectionId, continuationCollection,
      guardPermissions, createPermissions))

    "accept exact owner and collection permissions as data, not an invocation grant" in {
      policy().validate(admitted).toOption shouldBe Some(())
      new DataStoreSpace().conditionalMultiGuard(admitted).toOption shouldBe None
    }

    "reject wrong Workflow owner/root, guard owner and side-Create collection" in {
      val wrongGuard = DataStoreConditionalGuardBinding(workflowOwner, cursorCollection)
      Vector(
        policy(owner = eventOwner),
        policy(rootCollectionId = cursorCollection),
        policy(guardPermissions = Vector(wrongGuard)),
        policy(createPermissions = Vector.empty)
      ).forall(_.validate(admitted).toOption.isEmpty) shouldBe true
    }
  }
}
