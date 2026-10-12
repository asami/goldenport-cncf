package org.goldenport.cncf.event

import java.nio.file.{Files, Path}
import java.time.Instant
import io.circe.Json
import io.circe.parser.parse
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
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/**
 * Actual managed transaction composition, not configured append/dispatch.
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
final class ManagedSelectedEventV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {

  private def _temporary_directory(prefix: String): java.nio.file.Path = {
    val directory = java.nio.file.Path.of("target", "phase105", "work", "ManagedSelectedEventV2Spec").toAbsolutePath
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, prefix)
  }
  private given _context: ExecutionContext = ExecutionContext.create()
  private def _take[A](value: Consequence[A]): A = {
     value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private val _owner = ComponentId("test.v2.Workflow")
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "v2", name))
  private val _roots = _collection("instance")
  private val _claims = _collection("claim")
  private val _cursors = _collection("cursor")
  private val _events = _collection("events")
  private val _indexes = _collection("indexes")
  private val _declaration = _take(EventStorePersistenceDeclaration.createC("events", _cursors, _events, _indexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(_owner, "workflow", _roots, _claims)),
    DataStore.StringEntryId("stream")))
  private val _time = Instant.parse("2026-09-30T00:00:00.123Z")
  private val _entity_type = EntityCollectionId("test", "v2", "subject")
  private val _entity_id = _take(EntityId.restore("test", "entity", _entity_type, _time, "issued"))
  private val _machine = CmlStateMachineIdentity("lifecycle")
  private val _binding = CmlTransitionBinding(_owner, _entity_type, _machine, CmlStateMachineVersion(1),
    CmlStateMachineTransitionIdentity(_machine, 0),
    CmlStateMachineStateIdentity(_machine, CmlStateMachineStatePath(Vector("Before"))),
    CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(_machine, "change"))
  private def _event(kind: String, token: String, keyed: Boolean) = {
    val result = EventRecord(EventId("test", "v2", Some(_time), Some(token)), "fact", kind,
      Map("long" -> 9007199254740993L), Map.empty, _time, true, EventRecord.Status.Stored, EventLane.Transactional)
    result._delivery_lookup_key = if (keyed) Some(CommittedDeliveryLookupKey(token, _entity_id, _binding, "change")) else None
    result
  }
  private val _input = Vector(_event("progress", "transient", true), _event("fact", "one", true),
    _event("unknown", "unknown", false), _event("fact", "two", false), _event("fact", "three", true))
  private val _policy = _take(EventPersistencePolicy.createC("v2-policy", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
  private def _selection(records: Vector[EventRecord] = _input) =
    _take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(_policy), records))
  private def _database = _temporary_directory("cncf-managed-v2-").resolve("events.sqlite")
  private def _environment(subsystem: Subsystem) =
    ComponentDataStore.Environment(_context.runtime.resolvedParameters, Some(subsystem.configuration))
  private def _with_host[A](file: Path)(f: Subsystem => A): A = {
    val config = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(file.toString))), ConfigurationTrace.empty)
    EventStorePersistenceConstruction.withDeclaration(_declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = config)(f)
    }
  }
  private def _transition = {
    val component = _take(DataStoreComponentOwner.create(_owner.name))
    def _guard_root_(collection: DataStore.CollectionId, token: String) = _take(DataStoreConditionalRoot.create(
      component, collection, DataStore.StringEntryId(token), "revision", Some(EntityRevision.INITIAL),
      Vector.empty, Record.dataAuto("status" -> "closed"), _take(EntityRevision.createC(2L))))
    _take(DataStoreConditionalTransitionPlan.create(_guard_root_(_roots, "instance"),
      DataStoreConditionalSuccessor.Update(_guard_root_(_claims, "claim")), Vector.empty))
  }
  private def _seed(space: DataStoreSpace, base: DataStoreConditionalTransitionPlan): Unit =
    Vector(base.root, base.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root).foreach { root =>
      _take(_take(space.dataStore(root.collection)).create(root.collection, root.entryId,
        Record.dataAuto("revision" -> 1L, "status" -> "open")))
    }
  private def _stored(space: DataStoreSpace, plan: DataStoreConditionalMultiGuardPlan) = {
    val targets = Vector(plan.transition.root.collection -> plan.transition.root.entryId,
      plan.transition.successor.collection -> plan.transition.successor.entryId) ++
      plan.additionalGuards.map(g => g.collection -> g.entryId) ++
      plan.transition.sideEffects.map(e => e.collection -> e.entryId)
    targets.map { case (collection, id) => _take(_take(space.dataStore(collection)).load(collection, id)) }
  }

  "Managed V2 selected-event composition" should {
    "observe key references and intrinsic indexes in one closure and classify only new facts" in {
      Given("retained indexed events, a key alias, an ID match, a new fact and one transient input")
      _with_host(_database) { subsystem =>
        val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
        val proposal = _take(WorkflowCursorEventProposal.prepareV2C(_selection(), _declaration, cursor))
        val alias = _event("fact", "alias", false).copy(payload = Map("unsupported" -> new Object))
        alias._delivery_lookup_key = _input(1)._delivery_lookup_key
        val retainedid = _event("fact", "three", true)
        retainedid._delivery_lookup_key = _event("fact", "different-key", true)._delivery_lookup_key
        val incoming = _selection(Vector(alias, retainedid, _event("fact", "fresh", false), _event("progress", "progress", true)))
        val snapshot = _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
          (proposal.events.creates ++ proposal.events.indexCreates).foreach { create =>
            _take(_take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
          }
          _take(_take(space.dataStore(_cursors)).save(_cursors, _declaration.cursorEntryId,
            _take(EventCursorRecordV1.createC(2L, 3L)).toRecord))
          When("one native observation follows both actual ID and intrinsic index references")
          space.observeSelectedEventsC(incoming).flatMap { snapshot => snapshot.classificationC.map { classified =>
            Then("only the fresh durable fact consumes capacity and the observation covers actual retained targets")
            classified.newDurableCount shouldBe 1
            classified.dispositions.take(2).foreach(_ shouldBe a[ManagedEventDuplicateClassifier.Disposition.RetainedExisting])
            classified.dispositions(2) shouldBe ManagedEventDuplicateClassifier.Disposition.New(0)
            classified.dispositions(3) shouldBe ManagedEventDuplicateClassifier.Disposition.Transient(3)
            _take(classified.cursorProposalC).get.next.lastSequence shouldBe 4L
            val rows = _take(snapshot.recordsC)
            rows.exists(_._1.entryId == DataStore.StringEntryId(alias.id.value)) shouldBe true
            rows.exists(_._1.entryId == DataStore.StringEntryId(_input(1).id.value)) shouldBe true
            // Neither target below is in the initial manifest: the alias key
            // discovers event one, and event three discovers its intrinsic key.
            val manifest = _take(ManagedEventObservationRequest.createC(incoming,
              _take(DataStoreObservationPolicy.createC(DataStoreObservationTarget(_cursors, _declaration.cursorEntryId), _events, _indexes)))).initialTargets
            manifest.exists(_.entryId == DataStore.StringEntryId(_input(1).id.value)) shouldBe false
            val intrinsicthree = proposal.events.indexCreates.last.entryId
            manifest.exists(_.entryId == intrinsicthree) shouldBe false
            rows.exists(_._1.entryId == intrinsicthree) shouldBe true
            rows.exists(_._1.entryId == DataStore.StringEntryId(incoming.transient.head.id.value)) shouldBe false
            _take(_take(space.dataStore(_events)).load(_events, DataStore.StringEntryId(incoming.events(2).id.value))) shouldBe None
            snapshot
          }}
        })
        When("the observation snapshot is used after its native closure has ended")
        val expired = snapshot.classificationC.toOption
        Then("the closed capability supplies no new classification or cursor allocation")
        expired shouldBe None
        _take(subsystem.loadEventCursorC(_environment(subsystem))).lastSequence shouldBe 3L
      }
    }

    "observe actual normalized retained content despite absent or stale digest annotations" in {
      Given("one keyed retained event whose actual name, payload and JSON formatting differ from the incoming proposal")
      Vector("absent", "stale").foreach { annotation =>
        _with_host(_database) { subsystem =>
          _take(subsystem.initializeEventCursorC(_environment(subsystem)))
          val selected = _selection(Vector(_event("fact", "retained-normalized", true)))
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _declaration, EventCursorRecordV1.initial))
          val actual = proposal.events.persistent.head.copy(name = "actual.retained", payload = Map("long" -> 7L))
          actual._delivery_lookup_key = proposal.events.persistent.head._delivery_lookup_key
          val bytes = "cncf.event-record.v2: \n" + parse(EventRecordJsonV2.encode(actual)).toOption.get.spaces2 + "\n "
          val index = proposal.events.indexCreates.head
          val fields = index.record.fields.filterNot(_.key == "event_body_digest") ++
            (if (annotation == "stale") Record.dataAuto("event_body_digest" -> "stale diagnostic").fields else Vector.empty) ++
            Record.dataAuto("unused_annotation" -> "inert").fields
          _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
            _take(_take(space.dataStore(_events)).create(_events, proposal.events.creates.head.entryId, Record.dataAuto("event_json" -> bytes)))
            _take(_take(space.dataStore(_indexes)).create(_indexes, index.entryId, Record(fields)))
            _take(_take(space.dataStore(_cursors)).save(_cursors, _declaration.cursorEntryId,
              _take(EventCursorRecordV1.createC(3L, 10L)).toRecord))
            When("the native observation follows actual event and index identity")
            val classified = _take(_take(space.observeSelectedEventsC(selected)).classificationC)
            val retained = classified.dispositions.head.asInstanceOf[ManagedEventDuplicateClassifier.Disposition.RetainedExisting]
            val consumed = _take(EventRecordJsonV2.decodeStorageC(retained.canonicalBytes))
            Then("retained classification consumes actual content without allocating a new sequence or interpreting digest metadata")
            classified.newDurableCount shouldBe 0
            consumed.name shouldBe "actual.retained"
            consumed.payload("long") shouldBe 7L
            consumed._delivery_lookup_key shouldBe actual._delivery_lookup_key
            _take(classified.cursorProposalC) shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "reject absent intrinsic indexes, dangling references and mismatched physical events without repair" in {
      Given("native retained inventories with a missing index, dangling reference or foreign physical target")
      Vector("intrinsic", "dangling", "identity").foreach { defect =>
        _with_host(_database) { subsystem =>
          _take(subsystem.initializeEventCursorC(_environment(subsystem)))
          val selected = _selection(Vector(_event("fact", "retained", true)))
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _declaration, EventCursorRecordV1.initial))
          _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
            val create = proposal.events.creates.head
            val index = proposal.events.indexCreates.head
            if (defect != "dangling") _take(_take(space.dataStore(_events)).create(_events, create.entryId, create.record))
            else {
              // Provision the collection without creating the referenced event.
              _take(_take(space.dataStore(_events)).create(_events, DataStore.StringEntryId("other"), create.record))
            }
            if (defect != "intrinsic") _take(_take(space.dataStore(_indexes)).create(_indexes, index.entryId, index.record))
            else _take(_take(space.dataStore(_indexes)).create(_indexes, DataStore.StringEntryId("provision"), index.record))
            _take(_take(space.dataStore(_cursors)).save(_cursors, _declaration.cursorEntryId,
              _take(EventCursorRecordV1.createC(2L, 1L)).toRecord))
            val probe = if (defect == "identity") {
              val different = _event("fact", "wrong-physical", false)
              _take(_take(space.dataStore(_events)).create(_events, DataStore.StringEntryId(different.id.value), create.record))
              _selection(Vector(different))
            } else if (defect == "dangling") {
              val alias = _event("fact", "alias", false)
              alias._delivery_lookup_key = selected.events.head._delivery_lookup_key
              _selection(Vector(alias))
            } else selected
            val before = _take(_take(space.dataStore(_indexes)).load(_indexes, index.entryId))
            When("native observation consumes the actual inconsistent target graph")
            val result = space.observeSelectedEventsC(probe).toOption
            Then("the inventory refuses and the retained index stays untouched")
            result shouldBe None
            _take(_take(space.dataStore(_indexes)).load(_indexes, index.entryId)) shouldBe before
            Consequence.unit
          })
          _take(subsystem.loadEventCursorC(_environment(subsystem))).lastSequence shouldBe 1L
        }
      }
    }

    "retain at exhausted cursor capacity and keep transient-only batches off the observation route" in {
      Given("one indexed retained fact at exhausted cursor capacity")
      _with_host(_database) { subsystem =>
        _take(subsystem.initializeEventCursorC(_environment(subsystem)))
        val selected = _selection(Vector(_event("fact", "retained", true)))
        val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _declaration, EventCursorRecordV1.initial))
        _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
          (proposal.events.creates ++ proposal.events.indexCreates).foreach { create =>
            _take(_take(space.dataStore(create.collection)).create(create.collection, create.entryId, create.record))
          }
          _take(_take(space.dataStore(_cursors)).save(_cursors, _declaration.cursorEntryId,
            _take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue)).toRecord))
          When("retained and transient-only batches request native observation")
          val classified = _take(_take(space.observeSelectedEventsC(selected)).classificationC)
          val transient = space.observeSelectedEventsC(_selection(Vector(_event("progress", "transient-only", true)))).toOption
          Then("retained facts need no new slot and transient-only batches use no observation route")
          classified.newDurableCount shouldBe 0
          _take(classified.cursorProposalC) shouldBe None
          transient shouldBe None
          Consequence.unit
        })
      }
    }

    "freeze durable keys before native request execution without admitting retained payloads" in {
      Given("a durable keyed input with unsupported payload before observation")
      val incoming = _event("fact", "freeze", true).copy(payload = Map("unsupported" -> new Object))
      incoming._delivery_lookup_key = _input(1)._delivery_lookup_key
      val selected = _selection(Vector(incoming))
      val observationpolicy = _take(DataStoreObservationPolicy.createC(DataStoreObservationTarget(_cursors, _declaration.cursorEntryId), _events, _indexes))
      val request = _take(ManagedEventObservationRequest.createC(selected, observationpolicy))
      val original = request.initialTargets
      When("the selected input key changes after the request captures its targets")
      selected.events.head._delivery_lookup_key = _event("fact", "mutated", true)._delivery_lookup_key
      Then("the request keeps its original actual target inventory")
      request.initialTargets shouldBe original
      // Payload need not encode at request capture; a new fact still fails classification.
      val rows = original.map(t => t -> (if (t == observationpolicy.cursor) Some(EventCursorRecordV1.initial.toRecord) else None))
      When("the unsupported new durable payload reaches classification")
      val result = request.classifyC(rows).toOption
      Then("capturing a target grants no durable payload admission")
      result shouldBe None
    }

    "commit cursor, Workflow records, selected V2 events and only their keyed indexes atomically and recover them" in {
      Given("a mixed selected batch and explicit SQLite Workflow/event/index/cursor collections")
      val file = _database
      _with_host(file) { subsystem =>
        val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
        val selected = _selection()
        selected.events.head._delivery_lookup_key shouldBe _input.head._delivery_lookup_key
        WorkflowCursorEventProposal.prepareC(selected, _declaration, cursor).toOption shouldBe None
        val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _declaration, cursor))
        proposal.events.creates.size shouldBe 3
        proposal.events.indexCreates.size shouldBe 2
        val base = _transition
        val plan = _take(proposal.multiGuardPlanC(base))
        plan.additionalGuards.head.entryId shouldBe _declaration.cursorEntryId
        _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
          _seed(space, base)
          When("the one guarded native plan commits the complete selected durable subset")
          val verified = _take(_take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitVerifiedC())
            .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
          val candidates = _take(proposal.deliveryCandidatesFromVerifiedStorageC(verified))
          Then("actual retained facts fill the original mixed delivery order")
          verified.records.size shouldBe 8
          candidates.map(_.sequence) shouldBe Vector(0L, 1L, 0L, 2L, 3L)
          candidates.map(_.id) shouldBe _input.map(_.id)
          candidates.map(_._delivery_lookup_key) shouldBe _input.map(_._delivery_lookup_key)
          candidates.foreach(_.payload("long") shouldBe 9007199254740993L)
          val changed = _selection(_input.updated(1, _event("fact", "one", true).copy(payload = Map("long" -> 7L))))
          When("a different unused proposal payload names the same actual committed targets")
          val consumed = _take(_take(WorkflowCursorEventProposal.prepareV2C(changed, _declaration, cursor))
            .deliveryCandidatesFromVerifiedStorageC(verified))
          Then("candidate projection consumes the retained payload rather than its proposal copy")
          consumed(1).payload("long") shouldBe 9007199254740993L
          val before = _stored(space, plan)
          When("the already consumed native guard is submitted again")
          val replay = _take(space.conditionalMultiGuardVerified(plan))
          Then("the native transaction refuses replay and preserves stored facts")
          replay shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
          _stored(space, plan) shouldBe before
          selected.transient.foreach { event =>
            _take(_take(space.dataStore(_events)).load(_events, DataStore.StringEntryId(event.id.value))) shouldBe None
          }
          _input.foreach(_.sequence shouldBe 0L)
          When("the actual retained envelope and unused index diagnostics are normalized before close")
          proposal.events.creates.foreach { effect =>
            val actual = _take(EventRecordJsonV2.decodeStorageC(effect.record.getString("event_json").get))
            val normalized = actual.copy(name = "actual.retained", payload = Map("long" -> 7L))
            normalized._delivery_lookup_key = actual._delivery_lookup_key
            val bytes = "cncf.event-record.v2: \n" + parse(EventRecordJsonV2.encode(normalized)).toOption.get.spaces2 + "\n "
            _take(_take(space.dataStore(_events)).update(_events, effect.entryId, Record.dataAuto("event_json" -> bytes)))
          }
          proposal.events.indexCreates.foreach { effect =>
            _take(_take(space.dataStore(_indexes)).update(_indexes, effect.entryId,
              Record.dataAuto("event_body_digest" -> "stale diagnostic", "annotation" -> "inert")))
          }
          Consequence.unit
        })
      }
      When("a new managed host physically reopens the explicit file")
      _with_host(file) { subsystem =>
        val cursor = _take(subsystem.loadEventCursorC(_environment(subsystem)))
        Then("the actual native cursor survives physical close and reopen")
        cursor.revision shouldBe 2L
        cursor.lastSequence shouldBe 3L
        val prepared = _take(WorkflowCursorEventProposal.prepareV2C(_selection(), _declaration, EventCursorRecordV1.initial))
        _take(subsystem.withWorkflowMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
          prepared.events.creates.foreach { effect =>
            When("the reopened native event row is decoded at its actual physical target")
            val row = _take(_take(space.dataStore(_events)).load(_events, effect.entryId)).get
            val recovered = _take(EventRecordJsonV2.decodeStorageC(row.getString("event_json").get))
            Then("native stored formatting retains actual changed name, payload and complete delivery key")
            recovered.name shouldBe "actual.retained"
            recovered.payload("long") shouldBe 7L
            recovered._delivery_lookup_key shouldBe prepared.events.persistent.find(_.id == recovered.id).get._delivery_lookup_key
          }
          prepared.events.indexCreates.foreach { effect =>
            When("the reopened index resolves its actual key address and retained event")
            val physical = _take(_take(space.dataStore(_indexes)).load(_indexes, effect.entryId)).get
            val index = _take(EventDeliveryIndexRecordV1.decodeC(Record(physical.fields.filterNot(_.key == "id"))))
            _take(index.validateLookupC(effect.entryId, index.lookupKeyBytes))
            val envelope = _take(_take(space.dataStore(_events)).load(_events, DataStore.StringEntryId(index.eventId.value))).get
            val recovered = _take(index.validateEventC(envelope.getString("event_json").get))
            Then("the index binds actual event identity despite stale body-copy diagnostics")
            recovered.name shouldBe "actual.retained"
            recovered.payload("long") shouldBe 7L
            index.eventBodyDigest shouldBe "stale diagnostic"
          }
          Consequence.unit
        })
      }
    }

    "rollback every event, earlier index, cursor and Workflow update on the last index collision or stale cursor" in {
      Given("a guarded native batch and either a last-index collision or a stale actual cursor")
      Vector(false, true).foreach { collision =>
        _with_host(_database) { subsystem =>
          val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(_selection(), _declaration, cursor))
          val base = _transition
          val plan = _take(proposal.multiGuardPlanC(base))
          _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
            _seed(space, base)
            if (collision) {
              val effect = proposal.events.indexCreates.last
              _take(_take(space.dataStore(_indexes)).create(_indexes, effect.entryId, effect.record))
            } else _take(_take(space.dataStore(_cursors)).save(_cursors, _declaration.cursorEntryId,
              _take(EventCursorRecordV1.createC(2L, 0L)).toRecord))
            val before = _stored(space, plan)
            When("the single native transaction consumes the original guards")
            val rejected = space.conditionalMultiGuardVerified(plan).toOption
            Then("the failure retains no earlier event/index or Workflow update")
            rejected shouldBe None
            _stored(space, plan) shouldBe before
            _stored(space, plan).slice(3, 7).foreach(_ shouldBe None)
            Consequence.unit
          })
        }
      }
    }

    "reject reused delivery keys inside a new-only batch without deduplication or any writes" in {
      Given("two distinct new IDs declaring the same complete delivery key")
      _with_host(_database) { subsystem =>
        val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
        val duplicate = _event("fact", "other-id", true)
        duplicate._delivery_lookup_key = _input(1)._delivery_lookup_key
        val proposal = _take(WorkflowCursorEventProposal.prepareV2C(_selection(Vector(_input(1), duplicate)), _declaration, cursor))
        When("the host composes a new-only native plan")
        val result = proposal.multiGuardPlanC(_transition).toOption
        Then("a duplicate physical index address rejects composition without consuming the cursor")
        proposal.events.indexCreates.map(_.entryId).distinct.size shouldBe 1
        result shouldBe None
        _take(subsystem.loadEventCursorC(_environment(subsystem))).lastSequence shouldBe 0L
      }
    }

    "consume normalized actual native events and inert index metadata with an independently valid cursor" in {
      Given("a real native plan with the same physical targets, valid changed event facts and independently advanced cursor")
      Vector("absent", "stale", "cursor-bound", "cursor-malformed").foreach { mode =>
        _with_host(_database) { subsystem =>
          val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
          val selected = _selection()
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(selected, _declaration, cursor))
          val base = _transition
          val original = _take(proposal.multiGuardPlanC(base))
          val actual = proposal.events.persistent.zipWithIndex.map { case (record, ordinal) =>
            val changed = record.copy(name = "actual.native", payload = Map("long" -> 7L), sequence = 10L + ordinal)
            changed._delivery_lookup_key = record._delivery_lookup_key
            changed
          }
          val eventcreates = proposal.events.creates.zip(actual).map { case (effect, record) =>
            effect.copy(record = Record.dataAuto("event_json" -> ("cncf.event-record.v2: \n" +
              parse(EventRecordJsonV2.encode(record)).toOption.get.spaces2 + "\n ")))
          }
          val indexcreates = proposal.events.indexCreates.map { effect =>
            val reference = _take(EventDeliveryIndexRecordV1.decodeC(effect.record)).eventId
            val sequence = actual.find(_.id == reference).get.sequence
            val fields = effect.record.fields.filterNot(field => Set("event_body_digest", "sequence").contains(field.key)) ++
              Record.dataAuto("sequence" -> sequence.toString, "unused_annotation" -> "inert").fields ++
              (if (mode == "absent") Vector.empty else Record.dataAuto("event_body_digest" -> "stale diagnostic").fields)
            effect.copy(record = Record(fields))
          }
          val oldguard = original.additionalGuards.head
          val changes = Record.dataAuto("cursor_schema" -> EventCursorRecordV1.schema,
            "last_sequence" -> (if (mode == "cursor-bound") "2" else if (mode == "cursor-malformed") "01" else "100"))
          val guard = _take(DataStoreConditionalRoot.create(oldguard.componentOwner, oldguard.collection, oldguard.entryId,
            oldguard.revisionField, Some(oldguard.expectedRevision), oldguard.expectedFields, changes,
            oldguard.nextRevision))
          val actualtransition = _take(DataStoreConditionalTransitionPlan.create(base.root, base.successor, eventcreates ++ indexcreates))
          val submitted = _take(DataStoreConditionalMultiGuardPlan.create(actualtransition, Vector(guard), original.scope))
          _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
            _seed(space, base)
            When("the actual native transaction commits and candidate projection consumes its readback")
            val verified = _take(space.conditionalMultiGuardVerified(submitted)).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            val result = proposal.deliveryCandidatesFromVerifiedStorageC(verified)
            Then("valid actual content is projected in mixed order while invalid actual cursor domains withhold delivery")
            if (mode.startsWith("cursor-")) result.toOption shouldBe None
            else {
              val candidates = _take(result)
              candidates.map(_.sequence) shouldBe Vector(0L, 10L, 0L, 11L, 12L)
              candidates.filter(_.persistent).foreach { record =>
                record.name shouldBe "actual.native"
                record.payload("long") shouldBe 7L
              }
              candidates.filterNot(_.persistent).map(_.payload) shouldBe selected.transient.map(_.payload)
            }
            Consequence.unit
          })
        }
      }
    }

    "withhold candidates for absent or divergent indexes even when storage verifies the submitted plan" in {
      Given("actual native commit plans with missing or malformed consumed index relationships")
      Vector("missing", "sequence", "reference", "key").foreach { defect =>
        _with_host(_database) { subsystem =>
          val cursor = _take(subsystem.initializeEventCursorC(_environment(subsystem)))
          val proposal = _take(WorkflowCursorEventProposal.prepareV2C(_selection(), _declaration, cursor))
          val base = _transition
          val original = _take(proposal.multiGuardPlanC(base))
          val indexcreates = proposal.events.indexCreates
          val changed = if (defect == "missing") Vector.empty else {
            val (field, value) = defect match {
              case "sequence" => "sequence" -> "01"
              case "reference" => "event_reference" -> indexcreates.last.record.getString("event_reference").get
              case "key" => "lookup_key" -> indexcreates.last.record.getString("lookup_key").get
            }
            indexcreates.updated(0, indexcreates.head.copy(record = Record(indexcreates.head.record.fields.filterNot(_.key == field) ++
              Record.dataAuto(field -> value).fields)))
          }
          val transitionwithdefect = _take(DataStoreConditionalTransitionPlan.create(base.root, base.successor,
            proposal.events.creates ++ changed))
          val submitted = _take(DataStoreConditionalMultiGuardPlan.create(transitionwithdefect,
            original.additionalGuards, original.scope))
          _take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(_owner, _environment(subsystem)) { space =>
            _seed(space, base)
            val verified = _take(space.conditionalMultiGuardVerified(submitted))
              .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            val before = _stored(space, submitted)
            When("candidate projection consumes the actual event/index target relationships")
            val rejected = proposal.deliveryCandidatesFromVerifiedStorageC(verified).toOption
            Then("missing or divergent consumed indexes withhold candidates without a retry or repair")
            rejected shouldBe None
            _stored(space, submitted) shouldBe before // no postcommit repair or retry
            Consequence.unit
          })
        }
      }
    }

    "create neither events nor indexes nor a cursor guard for an all-transient keyed batch" in {
      Given("an all-transient keyed batch at exhausted cursor capacity")
      When("the explicit V2 proposal and native plan are prepared")
      val proposal = _take(WorkflowCursorEventProposal.prepareV2C(_selection(Vector(_input.head)), _declaration,
        _take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))))
      val plan = proposal.multiGuardPlanC(_transition).toOption
      Then("no durable storage or cursor guard is required")
      proposal.events.creates shouldBe empty
      proposal.events.indexCreates shouldBe empty
      proposal.cursorGuard shouldBe None
      plan shouldBe None
    }
  }
}
