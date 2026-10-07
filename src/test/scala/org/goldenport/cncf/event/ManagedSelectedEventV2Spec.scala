package org.goldenport.cncf.event

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.statemachine.*
import org.goldenport.cncf.subsystem.{EventStorePersistenceConstruction, Subsystem}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{WorkflowCursorEventProposal, WorkflowEventSelection, WorkflowPreparedMultiGuardTransition}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/**
 * Actual managed transaction composition, not configured append/dispatch.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class ManagedSelectedEventV2Spec extends AnyWordSpec with Matchers {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = ComponentId("test.v2.Workflow")
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "v2", name))
  private val roots = collection("instance")
  private val claims = collection("claim")
  private val cursors = collection("cursor")
  private val events = collection("events")
  private val indexes = collection("indexes")
  private val declaration = take(EventStorePersistenceDeclaration.createC("events", cursors, events, indexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow", roots, claims)),
    DataStore.StringEntryId("stream")))
  private val time = Instant.parse("2026-09-30T00:00:00.123Z")
  private val entityType = EntityCollectionId("test", "v2", "subject")
  private val entityId = take(EntityId.restore("test", "entity", entityType, time, "issued"))
  private val machine = CmlStateMachineIdentity("lifecycle")
  private val binding = CmlTransitionBinding(owner, entityType, machine, CmlStateMachineVersion(1),
    CmlStateMachineTransitionIdentity(machine, 0),
    CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Before"))),
    CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(machine, "change"))
  private def event(kind: String, token: String, keyed: Boolean) = {
    val result = EventRecord(EventId("test", "v2", Some(time), Some(token)), "fact", kind,
      Map("long" -> 9007199254740993L), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional)
    result._delivery_lookup_key = if (keyed) Some(CommittedDeliveryLookupKey(token, entityId, binding, "change")) else None
    result
  }
  private val input = Vector(event("progress", "transient", true), event("fact", "one", true),
    event("unknown", "unknown", false), event("fact", "two", false), event("fact", "three", true))
  private val policy = take(EventPersistencePolicy.createC("v2-policy", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
  private def selection(records: Vector[EventRecord] = input) =
    take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(policy), records))
  private def database = Files.createTempDirectory("cncf-managed-v2-").resolve("events.sqlite")
  private def environment(subsystem: Subsystem) =
    ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
  private def withHost[A](file: Path)(f: Subsystem => A): A = {
    val config = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
    EventStorePersistenceConstruction.withDeclaration(declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = config)(f)
    }
  }
  private def transition = {
    val component = take(DataStoreComponentOwner.create(owner.name))
    def root(collection: DataStore.CollectionId, token: String) = take(DataStoreConditionalRoot.create(
      component, collection, DataStore.StringEntryId(token), "revision", Some(EntityRevision.INITIAL),
      Vector.empty, Record.dataAuto("status" -> "closed"), take(EntityRevision.createC(2L))))
    take(DataStoreConditionalTransitionPlan.create(root(roots, "instance"),
      DataStoreConditionalSuccessor.Update(root(claims, "claim")), Vector.empty))
  }
  private def seed(space: DataStoreSpace, base: DataStoreConditionalTransitionPlan): Unit =
    Vector(base.root, base.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root).foreach { root =>
      take(take(space.dataStore(root.collection)).create(root.collection, root.entryId,
        Record.dataAuto("revision" -> 1L, "status" -> "open")))
    }
  private def stored(space: DataStoreSpace, plan: DataStoreConditionalMultiGuardPlan) = {
    val targets = Vector(plan.transition.root.collection -> plan.transition.root.entryId,
      plan.transition.successor.collection -> plan.transition.successor.entryId) ++
      plan.additionalGuards.map(g => g.collection -> g.entryId) ++
      plan.transition.sideEffects.map(e => e.collection -> e.entryId)
    targets.map { case (collection, id) => take(take(space.dataStore(collection)).load(collection, id)) }
  }

  "Managed V2 selected-event composition" should {
    "observe key references and intrinsic indexes in one closure and classify only new facts" in {
      withHost(database) { subsystem =>
        val cursor = take(subsystem.initializeEventCursorC(environment(subsystem)))
        val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(), declaration, cursor))
        val alias = event("fact", "alias", false).copy(payload = Map("unsupported" -> new Object))
        alias._delivery_lookup_key = input(1)._delivery_lookup_key
        val retainedId = event("fact", "three", true)
        retainedId._delivery_lookup_key = event("fact", "different-key", true)._delivery_lookup_key
        val incoming = selection(Vector(alias, retainedId, event("fact", "fresh", false), event("progress", "progress", true)))
        val snapshot = take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
          (proposal.events.creates ++ proposal.events.indexCreates).foreach { create =>
            take(take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
          }
          take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
            take(EventCursorRecordV1.createC(2L, 3L)).toRecord))
          space.observeSelectedEventsC(incoming).flatMap { snapshot => snapshot.classificationC.map { classified =>
            classified.newDurableCount shouldBe 1
            classified.dispositions.take(2).foreach(_ shouldBe a[ManagedEventDuplicateClassifier.Disposition.RetainedExisting])
            classified.dispositions(2) shouldBe ManagedEventDuplicateClassifier.Disposition.New(0)
            classified.dispositions(3) shouldBe ManagedEventDuplicateClassifier.Disposition.Transient(3)
            take(classified.cursorProposalC).get.next.lastSequence shouldBe 4L
            val rows = take(snapshot.recordsC)
            rows.exists(_._1.entryId == DataStore.StringEntryId(alias.id.value)) shouldBe true
            rows.exists(_._1.entryId == DataStore.StringEntryId(input(1).id.value)) shouldBe true
            // Neither target below is in the initial manifest: the alias key
            // discovers event one, and event three discovers its intrinsic key.
            val manifest = take(ManagedEventObservationRequest.createC(incoming,
              take(DataStoreObservationPolicy.createC(DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes)))).initialTargets
            manifest.exists(_.entryId == DataStore.StringEntryId(input(1).id.value)) shouldBe false
            val intrinsicThree = proposal.events.indexCreates.last.entryId
            manifest.exists(_.entryId == intrinsicThree) shouldBe false
            rows.exists(_._1.entryId == intrinsicThree) shouldBe true
            rows.exists(_._1.entryId == DataStore.StringEntryId(incoming.transient.head.id.value)) shouldBe false
            take(take(space.dataStore(events)).load(events, DataStore.StringEntryId(incoming.events(2).id.value))) shouldBe None
            snapshot
          }}
        })
        snapshot.classificationC.toOption shouldBe None
        take(subsystem.loadEventCursorC(environment(subsystem))).lastSequence shouldBe 3L
      }
    }

    "reject absent intrinsic indexes, dangling references, mismatched physical events and wrong digests without repair" in {
      Vector("intrinsic", "dangling", "identity", "digest").foreach { defect =>
        withHost(database) { subsystem =>
          take(subsystem.initializeEventCursorC(environment(subsystem)))
          val selected = selection(Vector(event("fact", "retained", true)))
          val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, EventCursorRecordV1.initial))
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            val create = proposal.events.creates.head
            val index = proposal.events.indexCreates.head
            if (defect != "dangling") take(take(space.dataStore(events)).create(events, create.entryId, create.record))
            else {
              // Provision the collection without creating the referenced event.
              take(take(space.dataStore(events)).create(events, DataStore.StringEntryId("other"), create.record))
            }
            if (defect != "intrinsic") take(take(space.dataStore(indexes)).create(indexes, index.entryId,
              if (defect == "digest") Record(index.record.fields.filterNot(_.key == "event_body_digest") ++
                Record.dataAuto("event_body_digest" -> ("0" * 64)).fields) else index.record))
            else take(take(space.dataStore(indexes)).create(indexes, DataStore.StringEntryId("provision"), index.record))
            take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(2L, 1L)).toRecord))
            val probe = if (defect == "identity") {
              val different = event("fact", "wrong-physical", false)
              take(take(space.dataStore(events)).create(events, DataStore.StringEntryId(different.id.value), create.record))
              selection(Vector(different))
            } else if (defect == "dangling") {
              val alias = event("fact", "alias", false)
              alias._delivery_lookup_key = selected.events.head._delivery_lookup_key
              selection(Vector(alias))
            } else selected
            val before = take(take(space.dataStore(indexes)).load(indexes, index.entryId))
            space.observeSelectedEventsC(probe).toOption shouldBe None
            take(take(space.dataStore(indexes)).load(indexes, index.entryId)) shouldBe before
            Consequence.unit
          })
          take(subsystem.loadEventCursorC(environment(subsystem))).lastSequence shouldBe 1L
        }
      }
    }

    "retain at exhausted cursor capacity and keep transient-only batches off the observation route" in {
      withHost(database) { subsystem =>
        take(subsystem.initializeEventCursorC(environment(subsystem)))
        val selected = selection(Vector(event("fact", "retained", true)))
        val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, EventCursorRecordV1.initial))
        take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
          (proposal.events.creates ++ proposal.events.indexCreates).foreach { create =>
            take(take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
          }
          take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
            take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue)).toRecord))
          val classified = take(take(space.observeSelectedEventsC(selected)).classificationC)
          classified.newDurableCount shouldBe 0
          take(classified.cursorProposalC) shouldBe None
          space.observeSelectedEventsC(selection(Vector(event("progress", "transient-only", true)))).toOption shouldBe None
          Consequence.unit
        })
      }
    }

    "freeze durable keys before native request execution without admitting retained payloads" in {
      val incoming = event("fact", "freeze", true).copy(payload = Map("unsupported" -> new Object))
      incoming._delivery_lookup_key = input(1)._delivery_lookup_key
      val selected = selection(Vector(incoming))
      val policy = take(DataStoreObservationPolicy.createC(DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
      val request = take(ManagedEventObservationRequest.createC(selected, policy))
      val original = request.initialTargets
      selected.events.head._delivery_lookup_key = event("fact", "mutated", true)._delivery_lookup_key
      request.initialTargets shouldBe original
      // Payload need not encode at request capture; a new fact still fails classification.
      val rows = original.map(t => t -> (if (t == policy.cursor) Some(EventCursorRecordV1.initial.toRecord) else None))
      request.classifyC(rows).toOption shouldBe None
    }

    "commit cursor, Workflow records, selected V2 events and only their keyed indexes atomically and recover them" in {
      val file = database
      withHost(file) { subsystem =>
        val cursor = take(subsystem.initializeEventCursorC(environment(subsystem)))
        val selected = selection()
        selected.events.head._delivery_lookup_key shouldBe input.head._delivery_lookup_key
        WorkflowCursorEventProposal.prepareC(selected, declaration, cursor).toOption shouldBe None
        val proposal = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, cursor))
        proposal.events.creates.size shouldBe 3
        proposal.events.indexCreates.size shouldBe 2
        val base = transition
        val plan = take(proposal.multiGuardPlanC(base))
        plan.additionalGuards.head.entryId shouldBe declaration.cursorEntryId
        take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
          seed(space, base)
          val verified = take(take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitVerifiedC())
            .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
          verified.records.size shouldBe 8
          val candidates = take(proposal.deliveryCandidatesFromVerifiedStorageC(verified))
          candidates.map(_.sequence) shouldBe Vector(0L, 1L, 0L, 2L, 3L)
          candidates.map(_.id) shouldBe input.map(_.id)
          candidates.map(_._delivery_lookup_key) shouldBe input.map(_._delivery_lookup_key)
          candidates.foreach(_.payload("long") shouldBe 9007199254740993L)
          val changed = selection(input.updated(1, event("fact", "one", true).copy(payload = Map("long" -> 7L))))
          take(WorkflowCursorEventProposal.prepareV2C(changed, declaration, cursor))
            .deliveryCandidatesFromVerifiedStorageC(verified).toOption shouldBe None
          val before = stored(space, plan)
          take(space.conditionalMultiGuardVerified(plan)) shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
          stored(space, plan) shouldBe before
          selected.transient.foreach { event =>
            take(take(space.dataStore(events)).load(events, DataStore.StringEntryId(event.id.value))) shouldBe None
          }
          input.foreach(_.sequence shouldBe 0L)
          Consequence.unit
        })
      }
      withHost(file) { subsystem =>
        val cursor = take(subsystem.loadEventCursorC(environment(subsystem)))
        cursor.revision shouldBe 2L
        cursor.lastSequence shouldBe 3L
        val prepared = take(WorkflowCursorEventProposal.prepareV2C(selection(), declaration, EventCursorRecordV1.initial))
        take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
          prepared.events.creates.foreach { effect =>
            val row = take(take(space.dataStore(events)).load(events, effect.entryId)).get
            val recovered = take(EventRecordJsonV2.decodeStorageC(row.getString("event_json").get))
            recovered._delivery_lookup_key shouldBe prepared.events.persistent.find(_.id == recovered.id).get._delivery_lookup_key
          }
          prepared.events.indexCreates.foreach { effect =>
            val physical = take(take(space.dataStore(indexes)).load(indexes, effect.entryId)).get
            val index = take(EventDeliveryIndexRecordV1.decodeC(Record(physical.fields.filterNot(_.key == "id"))))
            take(index.validateLookupC(effect.entryId, index.lookupKeyBytes))
            val envelope = take(take(space.dataStore(events)).load(events, DataStore.StringEntryId(index.eventId.value))).get
            take(index.validateEventC(envelope.getString("event_json").get))
          }
          Consequence.unit
        })
      }
    }

    "rollback every event, earlier index, cursor and Workflow update on the last index collision or stale cursor" in {
      Vector(false, true).foreach { collision =>
        withHost(database) { subsystem =>
          val cursor = take(subsystem.initializeEventCursorC(environment(subsystem)))
          val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(), declaration, cursor))
          val base = transition
          val plan = take(proposal.multiGuardPlanC(base))
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seed(space, base)
            if (collision) {
              val effect = proposal.events.indexCreates.last
              take(take(space.dataStore(indexes)).create(indexes, effect.entryId, effect.record))
            } else take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(2L, 0L)).toRecord))
            val before = stored(space, plan)
            space.conditionalMultiGuardVerified(plan).toOption shouldBe None
            stored(space, plan) shouldBe before
            stored(space, plan).slice(3, 7).foreach(_ shouldBe None)
            Consequence.unit
          })
        }
      }
    }

    "reject reused delivery keys inside a new-only batch without deduplication or any writes" in {
      withHost(database) { subsystem =>
        val cursor = take(subsystem.initializeEventCursorC(environment(subsystem)))
        val duplicate = event("fact", "other-id", true)
        duplicate._delivery_lookup_key = input(1)._delivery_lookup_key
        val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(Vector(input(1), duplicate)), declaration, cursor))
        proposal.events.indexCreates.map(_.entryId).distinct.size shouldBe 1
        proposal.multiGuardPlanC(transition).toOption shouldBe None
        take(subsystem.loadEventCursorC(environment(subsystem))).lastSequence shouldBe 0L
      }
    }

    "withhold candidates for absent or divergent indexes even when storage verifies the submitted plan" in {
      Vector("missing", "digest", "sequence", "reference", "key").foreach { defect =>
        withHost(database) { subsystem =>
          val cursor = take(subsystem.initializeEventCursorC(environment(subsystem)))
          val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(), declaration, cursor))
          val base = transition
          val original = take(proposal.multiGuardPlanC(base))
          val indexes = proposal.events.indexCreates
          val changed = if (defect == "missing") Vector.empty else {
            val (field, value) = defect match {
              case "digest" => "event_body_digest" -> ("0" * 64)
              case "sequence" => "sequence" -> "01"
              case "reference" => "event_reference" -> indexes.last.record.getString("event_reference").get
              case "key" => "lookup_key" -> indexes.last.record.getString("lookup_key").get
            }
            indexes.updated(0, indexes.head.copy(record = Record(indexes.head.record.fields.filterNot(_.key == field) ++
              Record.dataAuto(field -> value).fields)))
          }
          val transitionWithDefect = take(DataStoreConditionalTransitionPlan.create(base.root, base.successor,
            proposal.events.creates ++ changed))
          val submitted = take(DataStoreConditionalMultiGuardPlan.create(transitionWithDefect,
            original.additionalGuards, original.scope))
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seed(space, base)
            val verified = take(space.conditionalMultiGuardVerified(submitted))
              .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            val before = stored(space, submitted)
            proposal.deliveryCandidatesFromVerifiedStorageC(verified).toOption shouldBe None
            stored(space, submitted) shouldBe before // no postcommit repair or retry
            Consequence.unit
          })
        }
      }
    }

    "create neither events nor indexes nor a cursor guard for an all-transient keyed batch" in {
      val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(Vector(input.head)), declaration,
        take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))))
      proposal.events.creates shouldBe empty
      proposal.events.indexCreates shouldBe empty
      proposal.cursorGuard shouldBe None
      proposal.multiGuardPlanC(transition).toOption shouldBe None
    }
  }
}
