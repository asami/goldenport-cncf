package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.{EventStoreConstruction, EventStorePersistenceConstruction, ObservationLeaseTestSupport, Subsystem}
import org.goldenport.cncf.workflow.{WorkflowCursorEventProposal, WorkflowEventSelection}
import org.goldenport.cncf.statemachine.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreObservationSnapshotSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](c: Consequence[A]): A = c match {
    case Consequence.Success(a) => a
    case other => fail(s"Unexpected rejection: $other")
  }
  private def collection(n: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "observe", n))
  private val owner = ComponentId("test.observe.Workflow")
  private val roots = collection("root")
  private val claims = collection("claim")
  private val cursors = collection("cursor")
  private val events = collection("events")
  private val indexes = collection("indexes")
  private val declaration = take(EventStorePersistenceDeclaration.createC("events", cursors, events, indexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow", roots, claims)),
    DataStore.StringEntryId("stream")))
  private val cursor = DataStoreObservationTarget(cursors, declaration.cursorEntryId)
  private def target(c: DataStore.CollectionId, id: String) = DataStoreObservationTarget(c, DataStore.StringEntryId(id))
  private def env(s: Subsystem) = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(s.configuration))
  private def withHost(f: Subsystem => Unit): Unit = {
    val path = Files.createTempDirectory("cncf-observation-").resolve("events.sqlite")
    val conf = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    EventStorePersistenceConstruction.withDeclaration(declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = conf)(f)
    }
  }
  private def provision(space: DataStoreSpace): Unit =
    Vector(events, indexes).foreach { c =>
      take(take(space.dataStore(c)).create(c, DataStore.StringEntryId("seed"), Record.dataAuto("text" -> "retained")))
    }
  private val time = java.time.Instant.parse("2026-09-30T00:00:00Z")
  private def ordinary(token: String, kind: String = "durable", payload: Map[String, Any] = Map("value" -> "original")) =
    EventRecord(EventId("test", "prepare", Some(time), Some(token)), "fact", kind, payload, Map.empty,
      time, false, EventRecord.Status.Stored, EventLane.Transactional)
  private def keyed(token: String): EventRecord = {
    val event = ordinary(token)
    val entityType = EntityCollectionId("test", "observe", "subject")
    val entity = take(EntityId.restore("test", "entity", entityType, time, "issued"))
    val machine = CmlStateMachineIdentity("lifecycle")
    val binding = CmlTransitionBinding(owner, entityType, machine, CmlStateMachineVersion(1),
      CmlStateMachineTransitionIdentity(machine, 0),
      CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Before"))),
      CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(machine, "change"))
    ObservationEventTestSupport.key(event, token, entity, binding)
    event
  }
  private def installedStore = EventStore.inMemory(take(EventPersistencePolicy.createC("preparation-policy", Vector(
    EventPersistencePolicy.Rule(EventTypeId("durable"), EventPersistencePolicy.Mode.Persist)))))
  private def workflowTransition(space: DataStoreSpace): DataStoreConditionalTransitionPlan = {
    val component = take(DataStoreComponentOwner.create(owner.name))
    def root(collection: DataStore.CollectionId, id: String) = {
      take(take(space.dataStore(collection)).create(collection, DataStore.StringEntryId(id),
        Record.dataAuto("revision" -> 1L, "status" -> "open")))
      take(DataStoreConditionalRoot.create(component, collection, DataStore.StringEntryId(id), "revision",
        Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "closed"), take(EntityRevision.createC(2L))))
    }
    take(DataStoreConditionalTransitionPlan.create(root(roots, "instance"),
      DataStoreConditionalSuccessor.Update(root(claims, "continuation")), Vector.empty))
  }

  "Host-admitted fixed-target observation" should {
    "complete captured-policy transient and empty input without consulting or modifying event storage" in {
      Vector("empty", "transient", "corrupt-cursor", "durable", "mixed").foreach { mode =>
        Given(s"a captured-policy host with $mode input and no initialized event store")
        EventStoreConstruction.withService(installedStore) { withHost { s =>
          take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            val transition = workflowTransition(space)
            def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
            if (mode == "corrupt-cursor") take(take(space.dataStore(cursors)).create(cursors,
              declaration.cursorEntryId, Record.dataAuto("invalid-cursor" -> "untouched")))
            val beforeCursor = read(cursors, declaration.cursorEntryId).map(_.fields)
            val transient = ordinary("memory-only", "ephemeral", Map("unsupported" -> new Object)).copy(persistent = true)
            val durable = ordinary("durable-input")
            val input = mode match {
              case "empty" => Vector.empty
              case "durable" => Vector(durable)
              case "mixed" => Vector(transient, durable)
              case _ => Vector(transient)
            }
            When("the ordinary-input transient completion entry asks its captured service to select persistence")
            val result = space.conditionalTransientEventsVerified(transition, input)
            Then("only zero-durable input verifies the two Workflow records without event cursor authority")
            val accepted = mode != "durable" && mode != "mixed"
            if (accepted) {
              val verified = take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
              verified.records.map(_.collection) shouldBe Vector(roots, claims)
              take(space.conditionalTransientEventsVerified(transition, input)) shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
            } else result.toOption shouldBe None
            read(roots, transition.root.entryId).get.getString("status") shouldBe Some(if (accepted) "closed" else "open")
            read(claims, transition.successor.entryId).get.getString("status") shouldBe Some(if (accepted) "closed" else "open")
            read(cursors, declaration.cursorEntryId).map(_.fields) shouldBe beforeCursor
            input.foreach(e => read(events, DataStore.StringEntryId(e.id.value)) shouldBe None)
            transient.persistent shouldBe true // Sender flag is ignored, not rewritten on the source.
            transient.sequence shouldBe 0L
            Consequence.unit
          })
        }}
      }
    }

    "retain key-free V1 and V2 facts and revalidate incoming-key index absence without creating an alias index" in {
      Vector("v1-key-free", "v2-key-free", "absent-index", "stale-absent-index").foreach { mode =>
        Given(s"a key-free stored event and $mode retained input on a captured-policy managed host")
        val installed = installedStore
        EventStoreConstruction.withService(installed) { withHost { s =>
          take(s.initializeEventCursorC(env(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            val transition = workflowTransition(space)
            val original = ordinary("key-free-retained")
            val selected = take(WorkflowEventSelection.prepareFromStoreC(installed, Vector(original)))
            val stored = if (mode == "v2-key-free")
              take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, EventCursorRecordV1.initial))
            else take(WorkflowCursorEventProposal.prepareC(selected, declaration, EventCursorRecordV1.initial))
            stored.events.indexCreates shouldBe empty
            val retained = stored.events.creates.head
            take(take(space.dataStore(events)).create(events, retained.entryId, retained.record))
            take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(2L, 1L)).toRecord))
            val incoming = ordinary("key-free-retained", payload = Map("unsupported" -> new Object))
            val lookup = keyed("key-free-retained")
            val proposedIndex = take(WorkflowCursorEventProposal.prepareV2C(
              take(WorkflowEventSelection.prepareFromStoreC(installed, Vector(lookup))), declaration,
              EventCursorRecordV1.initial)).events.indexCreates.head
            if (mode.endsWith("absent-index")) {
              // Provision the declared index schema before observing absence. A missing table is not absence authority.
              val provider = take(space.dataStore(indexes))
              take(provider.create(indexes, proposedIndex.entryId, proposedIndex.record))
              take(provider.delete(indexes, proposedIndex.entryId))
              ObservationEventTestSupport.copyKey(lookup, incoming)
            }
            val snapshot = take(space.observeHostEventsC(Vector(incoming)))
            val prepared = take(snapshot.preparationC)
            take(prepared.classificationC).newDurableCount shouldBe 0
            val observed = take(prepared.recordsC)
            observed.filter(_._1.collection == indexes).size shouldBe (if (mode.endsWith("absent-index")) 1 else 0)
            observed.filter(_._1.collection == indexes).foreach(_._2 shouldBe None)
            if (mode == "stale-absent-index")
              take(take(space.dataStore(indexes)).create(indexes, proposedIndex.entryId, proposedIndex.record))
            def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
            val beforeCursor = read(cursors, declaration.cursorEntryId).get.fields
            val beforeEvent = read(events, retained.entryId).get.fields
            val beforeIndex = read(indexes, proposedIndex.entryId).map(_.fields)
            When("retained-only completion revalidates every present and absent observation")
            val result = space.conditionalRetainedEventsVerified(transition, prepared)
            Then("only unchanged observations admit the Workflow pair without allocating a sequence or creating an index")
            if (mode == "stale-absent-index") result.toOption shouldBe None
            else {
              val verified = take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
              verified.records.size shouldBe 4 // Workflow pair, unchanged cursor, key-free retained event
              verified.records.exists(_.collection == indexes) shouldBe false
            }
            val expected = if (mode == "stale-absent-index") "open" else "closed"
            read(roots, transition.root.entryId).get.getString("status") shouldBe Some(expected)
            read(claims, transition.successor.entryId).get.getString("status") shouldBe Some(expected)
            read(cursors, declaration.cursorEntryId).get.fields shouldBe beforeCursor
            read(events, retained.entryId).get.fields shouldBe beforeEvent
            read(indexes, proposedIndex.entryId).map(_.fields) shouldBe beforeIndex
            Consequence.unit
          })
        }}
      }
    }

    "complete all-retained work with validation-only cursor facts even at exhausted capacity and reject stale facts" in {
      Vector("unchanged", "exhausted", "stale-cursor", "stale-index", "new-durable").foreach { mode =>
        Given(s"a captured-service all-retained alias with $mode storage and an unsupported incoming payload")
        val installed = installedStore
        EventStoreConstruction.withService(installed) { withHost { s =>
          take(s.initializeEventCursorC(env(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            val transition = workflowTransition(space)
            val retained = keyed("retained-only")
            val stored = take(WorkflowCursorEventProposal.prepareV2C(
              take(WorkflowEventSelection.prepareFromStoreC(installed, Vector(retained))), declaration, EventCursorRecordV1.initial))
            (stored.events.creates ++ stored.events.indexCreates).foreach { create =>
              take(take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
            }
            val loaded = if (mode == "exhausted") take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))
              else take(EventCursorRecordV1.createC(2L, 1L))
            take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId, loaded.toRecord))
            val alias = ordinary("retained-alias", payload = Map("unsupported" -> new Object))
            ObservationEventTestSupport.copyKey(retained, alias)
            val input = if (mode == "new-durable") Vector(ordinary("genuinely-new")) else Vector(alias)
            val prepared = take(take(space.observeHostEventsC(input)).preparationC)
            if (mode == "stale-cursor") take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(3L, 9L)).toRecord))
            if (mode == "stale-index") take(take(space.dataStore(indexes)).delete(indexes, stored.events.indexCreates.head.entryId))
            def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
            val beforeCursor = read(cursors, declaration.cursorEntryId).get.fields
            When("the host submits an explicit retained-only Workflow completion")
            val result = space.conditionalRetainedEventsVerified(transition, prepared)
            Then("only valid all-retained facts allow Workflow mutation while the cursor remains byte-for-field unchanged")
            if (mode == "unchanged" || mode == "exhausted") {
              val verified = take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
              verified.records.size shouldBe 5
              verified.records.find(_.collection == cursors).get.record.getString("last_sequence") shouldBe Some(loaded.lastSequence.toString)
              read(roots, transition.root.entryId).get.getString("status") shouldBe Some("closed")
              read(claims, transition.successor.entryId).get.getString("status") shouldBe Some("closed")
              take(space.conditionalRetainedEventsVerified(transition, prepared)) shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
            } else {
              result.toOption shouldBe None
              read(roots, transition.root.entryId).get.getString("status") shouldBe Some("open")
              read(claims, transition.successor.entryId).get.getString("status") shouldBe Some("open")
            }
            read(cursors, declaration.cursorEntryId).get.fields shouldBe beforeCursor
            read(events, DataStore.StringEntryId(alias.id.value)) shouldBe None
            read(events, DataStore.StringEntryId(ordinary("genuinely-new").id.value)) shouldBe None
            Consequence.unit
          })
        }}
      }
    }
    "reject foreign views, wrong threads, revoked or escaped publication and invalid Workflow transitions without effects" in {
      Vector("foreign-view", "thread", "revoked", "successor-conflict", "reserved-column", "injected-effect", "escaped").foreach { misuse =>
        Given(s"an original captured-service host preparation and a $misuse publication attempt")
        val installed = installedStore
        EventStoreConstruction.withService(installed) { withHost { s =>
          take(s.initializeEventCursorC(env(s)))
          val fresh = keyed("authority")
          val escaped = take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            provision(space)
            val transition = workflowTransition(space)
            val preparation = take(take(space.observeHostEventsC(Vector(fresh))).preparationC)
            When("the publication entry receives the invalid scope or transition")
            misuse match {
              case "foreign-view" => take(s._with_managed_datastore_lease_c { lease =>
                val policy = take(declaration.policyC(take(declaration.grantC(owner))))
                val readPolicy = take(DataStoreObservationPolicy.createC(cursor, events, indexes))
                val provider = take(space.dataStore(events))
                val other = take(DataStoreSpace.multiGuardHostViewC(policy, provider, lease, Some(readPolicy), Some(installed)))
                // Identical service, provider, grant and live lease still do not replace the issuing view.
                other.conditionalPreparedEventsVerified(transition, preparation).toOption shouldBe None
                Consequence.unit
              })
              case "thread" =>
                val result = new java.util.concurrent.atomic.AtomicReference[Boolean]()
                val thread = new Thread(() => result.set(space.conditionalPreparedEventsVerified(transition, preparation).toOption.nonEmpty))
                thread.start()
                thread.join(5000L)
                result.get() shouldBe false
              case "revoked" =>
                take(s._with_managed_datastore_lease_c { lease =>
                  ObservationLeaseTestSupport.revoke(lease)
                  Consequence.unit
                })
                space.conditionalPreparedEventsVerified(transition, preparation).toOption shouldBe None
              case "successor-conflict" =>
                take(take(space.dataStore(claims)).save(claims, transition.successor.entryId,
                  Record.dataAuto("revision" -> 9L, "status" -> "external")))
                space.conditionalPreparedEventsVerified(transition, preparation).toOption shouldBe None
              case "reserved-column" =>
                val original = transition.root
                val root = take(DataStoreConditionalRoot.create(original.componentOwner, original.collection,
                  original.entryId, original.revisionField, Some(original.expectedRevision), original.expectedFields,
                  Record.dataAuto("id" -> "foreign", "status" -> "closed"), original.nextRevision))
                val invalid = take(DataStoreConditionalTransitionPlan.create(root, transition.successor, Vector.empty))
                space.conditionalPreparedEventsVerified(invalid, preparation).toOption shouldBe None
              case "injected-effect" =>
                val effect = EntityVersionedSideEffect.Create(events, DataStore.StringEntryId("injected"), Record.dataAuto("text" -> "foreign"))
                val invalid = take(DataStoreConditionalTransitionPlan.create(transition.root, transition.successor, Vector(effect)))
                space.conditionalPreparedEventsVerified(invalid, preparation).toOption shouldBe None
              case "escaped" => ()
            }
            Then("no successful publication receipt is created for this attempt")
            Consequence.success(transition -> preparation)
          })
          if (misuse == "escaped") {
            When("a newly admitted view is asked to use the released original preparation")
            take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
              space.conditionalPreparedEventsVerified(escaped._1, escaped._2).toOption shouldBe None
              Consequence.unit
            })
          }
          Then("a fresh inspection shows unchanged Workflow root, cursor and absent new event/index")
          take(s.loadEventCursorC(env(s))).lastSequence shouldBe 0L
          take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
            read(roots, escaped._1.root.entryId).get.getString("status") shouldBe Some("open")
            read(claims, escaped._1.successor.entryId).get.getString("status") shouldBe
              Some(if (misuse == "successor-conflict") "external" else "open")
            read(events, DataStore.StringEntryId(fresh.id.value)) shouldBe None
            val creates = take(take(space.observeHostEventsC(Vector(fresh))).preparationC).planC(escaped._1)
            val index = take(creates).transition.sideEffects.find(_.collection == indexes).get
            read(indexes, index.entryId) shouldBe None
            read(events, DataStore.StringEntryId("injected")) shouldBe None
            Consequence.unit
          })
        }}
      }
    }
    "revalidate all frozen observations before staging and read back retained facts with new Creates" in {
      Vector("unchanged", "cursor", "retained-event", "retained-index", "absent-event", "absent-index").foreach { change =>
        Given(s"a captured-service preparation and an independently changed $change observation")
        val installed = installedStore
        EventStoreConstruction.withService(installed) { withHost { s =>
          take(s.initializeEventCursorC(env(s)))
          take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
            val transition = workflowTransition(space)
            val retained = keyed("retained")
            val stored = take(WorkflowCursorEventProposal.prepareV2C(
              take(WorkflowEventSelection.prepareFromStoreC(installed, Vector(retained))), declaration, EventCursorRecordV1.initial))
            (stored.events.creates ++ stored.events.indexCreates).foreach { create =>
              take(take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
            }
            take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(2L, 1L)).toRecord))
            val alias = ordinary("alias", payload = Map("unsupported" -> new Object))
            ObservationEventTestSupport.copyKey(retained, alias)
            val fresh = keyed("fresh")
            val prepared = take(take(space.observeHostEventsC(Vector(alias, fresh))).preparationC)
            val plan = take(prepared.planC(transition))
            change match {
              case "cursor" => take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
                take(EventCursorRecordV1.createC(3L, 9L)).toRecord))
              case "retained-event" => take(take(space.dataStore(events)).delete(events, stored.events.creates.head.entryId))
              case "retained-index" => take(take(space.dataStore(indexes)).delete(indexes, stored.events.indexCreates.head.entryId))
              case "absent-event" =>
                val create = plan.transition.sideEffects.find(_.collection == events).get.asInstanceOf[EntityVersionedSideEffect.Create]
                take(take(space.dataStore(events)).create(events, create.entryId, create.record))
              case "absent-index" =>
                val create = plan.transition.sideEffects.find(_.collection == indexes).get.asInstanceOf[EntityVersionedSideEffect.Create]
                take(take(space.dataStore(indexes)).create(indexes, create.entryId, create.record))
              case _ => ()
            }
            def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
            val beforeCursor = read(cursors, declaration.cursorEntryId).get.fields
            When("the original host publishes through the prepared-event verified route")
            val result = space.conditionalPreparedEventsVerified(transition, prepared)
            Then("only exact observations allow the atomic Workflow/cursor/new-event update")
            if (change == "unchanged") {
              val verified = take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
              verified.records.size shouldBe 7 // Workflow pair, cursor, new event/index, retained event/index
              verified.records.exists(r => r.collection == events && r.entryId == stored.events.creates.head.entryId) shouldBe true
              verified.records.exists(r => r.collection == indexes && r.entryId == stored.events.indexCreates.head.entryId) shouldBe true
              take(s.loadEventCursorC(env(s))).lastSequence shouldBe 2L
              read(roots, transition.root.entryId).get.getString("status") shouldBe Some("closed")
              // Explicit resubmission is stale; the Workflow/cursor cannot advance twice.
              space.conditionalPreparedEventsVerified(transition, prepared).toOption shouldBe None
            } else {
              result.toOption shouldBe None
              read(roots, transition.root.entryId).get.getString("status") shouldBe Some("open")
              read(claims, transition.successor.entryId).get.getString("status") shouldBe Some("open")
              read(cursors, declaration.cursorEntryId).get.fields shouldBe beforeCursor
              if (change != "absent-event") read(events, DataStore.StringEntryId(fresh.id.value)) shouldBe None
              if (change != "absent-index") read(indexes, plan.transition.sideEffects.find(_.collection == indexes).get.entryId) shouldBe None
            }
            Consequence.unit
          })
        }}
      }
    }
    "freeze retained envelopes and indexes independently of source keys, projections and later storage changes" in {
      Given("a captured policy store with one durable keyed fact and a retained alias whose incoming payload cannot encode")
      val installed = installedStore
      EventStoreConstruction.withService(installed) { withHost { s =>
        take(s.initializeEventCursorC(env(s)))
        val retained = keyed("retained")
        val selected = take(WorkflowEventSelection.prepareFromStoreC(installed, Vector(retained)))
        val stored = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, EventCursorRecordV1.initial))
        val alias = ordinary("alias", payload = Map("unsupported" -> new Object))
        ObservationEventTestSupport.copyKey(retained, alias)
        val fresh = keyed("fresh")
        val escaped = take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
          (stored.events.creates ++ stored.events.indexCreates).foreach { create =>
            take(take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
          }
          take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
            take(EventCursorRecordV1.createC(2L, 1L)).toRecord))
          When("the host captures closure and the caller changes source keys and decoded new-event projections")
          val snapshot = take(space.observeHostEventsC(Vector(alias, fresh, ordinary("progress", "transient"))))
          val preparation = take(snapshot.preparationC)
          val before = take(preparation.recordsC)
          val classification = take(preparation.classificationC)
          val projectedNew = take(classification.newEventsC).head
          val assignedNew = projectedNew.copy(sequence = 1L)
          ObservationEventTestSupport.copyKey(projectedNew, assignedNew)
          val template = EventRecordJsonV2.encodeStorage(assignedNew)
          ObservationEventTestSupport.clearKey(alias)
          ObservationEventTestSupport.clearKey(fresh)
          ObservationEventTestSupport.clearKey(projectedNew)
          Then("frozen cursor, ordered absences, retained bytes and index content and new templates remain exact")
          val after = take(preparation.recordsC)
          after.map { case (t, r) => t -> r.map(_.fields) } shouldBe before.map { case (t, r) => t -> r.map(_.fields) }
          before.zip(after).foreach { case ((_, a), (_, b)) => a.zip(b).foreach { case (x, y) =>
            x should not be theSameInstanceAs(y)
          }}
          after.find(_._1 == target(events, alias.id.value)).get._2 shouldBe None
          after.find(_._1 == target(events, fresh.id.value)).get._2 shouldBe None
          after.find(_._1 == target(events, retained.id.value)).get._2.get.fields shouldBe stored.events.creates.head.record.fields
          after.find(_._1.entryId == stored.events.indexCreates.head.entryId).get._2.get.fields.toSet shouldBe
            stored.events.indexCreates.head.record.fields.toSet
          classification.dispositions.head shouldBe ManagedEventDuplicateClassifier.Disposition.RetainedExisting(
            stored.events.creates.head.record.getString("event_json").get)
          classification.newDurableCount shouldBe 1
          val again = take(classification.newEventsC).head
          val assigned = again.copy(sequence = 1L)
          ObservationEventTestSupport.copyKey(again, assigned)
          EventRecordJsonV2.encodeStorage(assigned) shouldBe template
          take(classification.cursorProposalC).get.sequences shouldBe Vector(2L)
          take(take(space.dataStore(cursors)).save(cursors, declaration.cursorEntryId,
            take(EventCursorRecordV1.createC(3L, 9L)).toRecord))
          take(take(space.dataStore(indexes)).delete(indexes, stored.events.indexCreates.head.entryId))
          take(preparation.recordsC).map { case (t, r) => t -> r.map(_.fields) } shouldBe
            before.map { case (t, r) => t -> r.map(_.fields) }
          // Stale immutable evidence is not a write/dispatch authorization.
          Consequence.success(preparation)
        })
        When("the issuing scope releases its lease")
        Then("the retained preparation itself exposes neither records nor classification")
        escaped.recordsC.toOption shouldBe None
        escaped.classificationC.toOption shouldBe None
      }}
    }

    "reject direct preparation access on another thread and after actual lease revocation" in {
      Given("a preparation issued inside a live captured-service host")
      EventStoreConstruction.withService(installedStore) { withHost { s =>
        take(s.initializeEventCursorC(env(s)))
        take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
          provision(space)
          val preparation = take(take(space.observeHostEventsC(Vector(ordinary("new")))).preparationC)
          When("another thread accesses the preparation while the original lease is live")
          val result = new java.util.concurrent.atomic.AtomicReference[(Boolean, Boolean)]()
          val thread = new Thread(() => result.set((preparation.recordsC.toOption.nonEmpty,
            preparation.classificationC.toOption.nonEmpty)))
          thread.start()
          thread.join(5000L)
          Then("both accesses reject while the issuing thread still has access")
          result.get() shouldBe ((false, false))
          preparation.recordsC.toOption.nonEmpty shouldBe true
          preparation.classificationC.toOption.nonEmpty shouldBe true
          When("SystemNode's actual lease is revoked")
          take(s._with_managed_datastore_lease_c { lease =>
            ObservationLeaseTestSupport.revoke(lease)
            Consequence.unit
          })
          Then("the issuing thread also loses records and classification access without retry")
          preparation.recordsC.toOption shouldBe None
          preparation.classificationC.toOption shouldBe None
          Consequence.unit
        })
      }}
    }
    "select ordinary events through the captured host service and reject revision-equivalent substitutes" in {
      Given("a host constructed with a fixed EventStore policy and ordinary sender flags")
      val policy = take(EventPersistencePolicy.createC("host-policy", Vector(
        EventPersistencePolicy.Rule(EventTypeId("durable"), EventPersistencePolicy.Mode.Persist))))
      val installed = EventStore.inMemory(policy)
      val substitute = EventStore.inMemory(policy)
      val time = java.time.Instant.parse("2026-09-30T00:00:00Z")
      def event(token: String, kind: String, persistent: Boolean) = EventRecord(
        EventId("test", "host", Some(time), Some(token)), "fact", kind, Map("value" -> token), Map.empty,
        time, persistent, EventRecord.Status.Stored, EventLane.Transactional)
      val ordinary = Vector(event("durable", "durable", false), event("transient", "other", true))
      EventStoreConstruction.withService(installed) { withHost { s =>
        s.eventStore should be theSameInstanceAs installed
        take(s.initializeEventCursorC(env(s)))
        When("the live managed host selects and observes the batch")
        val snapshot = take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
          provision(space)
          EventStoreConstruction.withService(substitute) {
            space.observeHostEventsC(ordinary).flatMap { snapshot =>
              for {
                _ <- snapshot.validateHostSelectionC(installed)
                classified <- snapshot.classificationC
              } yield {
                Then("only the captured service grants a fresh immutable preparation projection")
                classified.newDurableCount shouldBe 1
                classified.dispositions shouldBe Vector(ManagedEventDuplicateClassifier.Disposition.New(0),
                  ManagedEventDuplicateClassifier.Disposition.Transient(1))
                take(classified.newEventsC).head.persistent shouldBe true
                snapshot.validateHostSelectionC(substitute).toOption shouldBe None
                val preparation = take(snapshot.preparationC)
                preparation.policyRevision shouldBe "host-policy"
                val first = take(preparation.recordsC)
                val second = take(preparation.recordsC)
                first.map(_._1) shouldBe second.map(_._1)
                first.head._2.get should not be theSameInstanceAs(second.head._2.get)
                first.head._2.get.getString("last_sequence") shouldBe Some("0")
                first.tail.map(_._2) shouldBe Vector(None)
                take(preparation.classificationC).newDurableCount shouldBe 1
                val supplied = take(WorkflowEventSelection.prepareFromStoreC(installed, ordinary))
                val diagnostic = take(space.observeSelectedEventsC(supplied))
                diagnostic.validateHostSelectionC(installed).toOption shouldBe None
                diagnostic.preparationC.toOption shouldBe None
                snapshot
              }
            }
          }
        })
        snapshot.validateHostSelectionC(installed).toOption shouldBe None
        snapshot.preparationC.toOption shouldBe None
        take(s.loadEventCursorC(env(s))).lastSequence shouldBe 0L
      }}
      EventStoreConstruction.current shouldBe None
    }

    "keep legacy no-policy construction unchanged and reject the host-selected managed route" in withHost { s =>
      take(s.initializeEventCursorC(env(s)))
      take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
        space.observeHostEventsC(Vector.empty).toOption shouldBe None
        Consequence.unit
      })
    }

    "bound each target class and reject noncanonical identities before provider reads" in {
      val policy = take(DataStoreObservationPolicy.createC(cursor, events, indexes))
      val maximum = Vector(cursor) ++ Vector.tabulate(512)(i => target(events, s"e$i")) ++
        Vector.tabulate(768)(i => target(indexes, s"i$i"))
      policy.validateC(maximum).toOption.nonEmpty shouldBe true
      policy.validateC(Vector(cursor) ++ Vector.tabulate(513)(i => target(events, s"e$i"))).toOption shouldBe None
      policy.validateC(Vector(cursor) ++ Vector.tabulate(769)(i => target(indexes, s"i$i"))).toOption shouldBe None
      Vector("", " padded", "x" * 65537).foreach { id =>
        policy.validateC(Vector(cursor, target(events, id))).toOption shouldBe None
      }
    }
    "return explicit presence and absence without physical ids or writes and reject escaped scope" in withHost { s =>
      take(s.initializeEventCursorC(env(s)))
      val snapshot = take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
        provision(space)
        val request = Vector(cursor, target(events, "seed"), target(events, "absent"), target(indexes, "absent"))
        space.observeDeclaredTargetsC(request).flatMap { snapshot => snapshot.recordsC.map { rows =>
          rows.map(_._1) shouldBe request
          rows.head._2.get.getString("last_sequence") shouldBe Some("0")
          rows(1)._2.get.getString("text") shouldBe Some("retained")
          rows.flatMap(_._2).foreach(_.fields.exists(_.key == "id") shouldBe false)
          rows.drop(2).map(_._2) shouldBe Vector(None, None)
          take(take(space.dataStore(events)).load(events, DataStore.StringEntryId("absent"))) shouldBe None
          snapshot
        }}
      })
      snapshot.recordsC.toOption shouldBe None
      take(s.loadEventCursorC(env(s))).lastSequence shouldBe 0L
    }

    "reject missing collections and missing cursor rather than treating them as absence" in withHost { s =>
      take(s.initializeEventCursorC(env(s)))
      take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
        space.observeDeclaredTargetsC(Vector(cursor, target(events, "absent"))).toOption shouldBe None
        provision(space)
        take(take(space.dataStore(cursors)).delete(cursors, declaration.cursorEntryId))
        space.observeDeclaredTargetsC(Vector(cursor, target(events, "absent"))).toOption shouldBe None
        Consequence.unit
      })
      s.loadEventCursorC(env(s)).toOption shouldBe None
    }

    "reject undeclared targets, duplicates, misplaced cursor and wrong thread" in withHost { s =>
      take(s.initializeEventCursorC(env(s)))
      take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
        provision(space)
        Vector(Vector(cursor, target(roots, "root")), Vector(cursor, cursor),
          Vector(target(events, "seed"), cursor), Vector(target(cursors, "other"))).foreach { request =>
          space.observeDeclaredTargetsC(request).toOption shouldBe None
        }
        val snapshot = take(space.observeDeclaredTargetsC(Vector(cursor)))
        val result = new java.util.concurrent.atomic.AtomicBoolean(true)
        val thread = new Thread(() => result.set(snapshot.recordsC.toOption.nonEmpty))
        thread.start()
        thread.join()
        result.get() shouldBe false
        snapshot.recordsC.toOption.nonEmpty shouldBe true
        Consequence.unit
      })
    }

    "leave ordinary and raw multi-guard spaces without read admission" in withHost { s =>
      new DataStoreSpace().observeDeclaredTargetsC(Vector(cursor)).toOption shouldBe None
      take(s.withWorkflowMultiGuardSpaceC(owner, env(s)) { space =>
        space.observeDeclaredTargetsC(Vector(cursor)).toOption shouldBe None
        Consequence.unit
      })
    }
  }
}
