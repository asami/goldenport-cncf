package org.goldenport.cncf.unitofwork

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.Root
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityVersionedMutationOutcomeFixture.{Variant, Mode, ProjectionMode, success, revision, conclusion, reasons, structuredFailure, withBackend}
import org.goldenport.cncf.entity.EntityVersionedMutationPortFixture.{Patch, Sample, SelectedPlan, nullCollectionId}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.{EntityAccessMode, EntityAccessRelation, EntityAbacCondition, ServiceOperationModel}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
final class UnitOfWorkVersionedOutcomeSpec extends AnyWordSpec with UnitOfWorkVersionedOutcomeFixture.Vocabulary {
  import UnitOfWorkVersionedOutcomeFixture.*
  import UnitOfWorkVersionedOutcomeFixture.given
  private val _tokens = new AtomicInteger()
  private def _token(): String = s"uvo_${_tokens.incrementAndGet()}"

  "Active UnitOfWork versioned capture" should {
    "keep canonical acknowledgements, projections and coherence distinct" which {
      Vector("memory", "sqlite").foreach { backend =>
        s"UVO-01/UVO-02/UVO-04 retain all four $backend branches and post-ACK eviction faults" in {
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = Variant.values.toVector.map(variant => variant -> new Session(_token(), variant, writer)).toMap
            for { variant <- Variant.values.toVector; mode <- Mode.values.toVector
              if !Set(Mode.FailureCurrent, Mode.FailureChanged).contains(mode) || typed(variant)
              fault <- Fault.values.toVector } {
              Given("a declared lawful variant, current/changed/stale input and independent eviction dimension")
              val sample = LawSample(Sample("日本 Alpha Ω", "retained business", 7L, mode, variant), fault, DecodeFault.Good)
              When("the complete real-UoW scenario is interpreted with pre-action expected state")
              val satisfied = checkLaw(sample, sessions(variant), reader, physical, backend)
              Then("the branch, complete state, exact references and separate cache result satisfy the declared correspondence")
              satisfied shouldBe true
            }
          }
        }
        s"UVO-02 preserve all four $backend current maximum revisions without successors" in {
          withBackend(backend) { (writer, reader, physical) =>
            Variant.values.toVector.foreach { variant =>
              Given("a complete maximum-revision root and equal current input")
              val current = new Session(_token(), variant, writer)
              current.seed(initialRevision = Long.MaxValue)
              val entity = current.source.input("Before", revision(Long.MaxValue))
              val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
              val wanted = expected(current, before, beforephysical, "Before", false, Long.MaxValue, backend)
              current.arm()
              When("the selected node evaluates in its active UnitOfWork at the current maximum revision")
              val capture = success(current.evaluateC(current.operation(entity, Patch("Before"), Some(revision(Long.MaxValue)), policy(revision(Long.MaxValue)))))
              Then("NoOp retains its actual raw receipt and exact projection without successor materialization or enclosing lifecycle")
              checkPrimitive(current, Primitive.Versioned)
              checkCapture(capture, current, wanted, false, Long.MaxValue)
              checkProjection(capture.mutation.projection, variant, wanted.rows, current.setup, Long.MaxValue)
              checkCoherence(current, capture, resident, Fault.None, true)
              checkRows(current.source.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
            }
          }
        }
        s"UVO-02 reject $backend inconsistent embedded input before any writer" in {
          Given("a complete Embedded revision two with typed input two and supplied observation one")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), Variant.TypedSnapshot, writer)
            current.seed(initialRevision = 2L)
            val entity = current.source.input("Before", revision(2L))
            val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
            current.arm()
            When("the canonical typed save receives conflicting embedded and supplied revisions")
            val result = current.evaluateC(current.operation(entity, Patch("Before"), Some(revision(1L)), policy(revision(1L))))
            Then("the existing exact mismatch precedes provider/projection/coherence and preserves complete state")
            checkSourceFailure(conclusion(result), conclusion(Consequence.argumentExpectedActualMismatch(
              current.setup.revisionField, revision(1L), revision(2L))), "StandardEntityRevisionPart.scala")
            checkPrimitive(current, Primitive.Versioned, 0)
            current.space.outcome shouldBe None; current.probe.admissions shouldBe 0
            current.resident shouldBe resident; current.evictions shouldBe Vector.empty
            checkRows(current.source.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
        s"UVO-03 retain every typed $backend projection mode and separate legacy active-evaluation semantics" in {
          withBackend(backend) { (writer, reader, physical) =>
            for { variant <- Vector(Variant.TypedSnapshot, Variant.TypedDetached); changed <- Vector(false, true)
              mode <- ProjectionMode.values.toVector } {
              val current = new Session(_token(), variant, writer)
              val legacy = new Session(_token(), variant, writer)
              checkTypedPair(current, legacy, mode, changed, reader, physical, backend)
            }
          }
        }
        s"UVO-04 exercise all six real registered $backend record decoders after projection" in {
          withBackend(backend) { (writer, reader, physical) =>
            for { variant <- Vector(Variant.RecordSnapshot, Variant.RecordDetached); mode <- DecodeFault.values.toVector
              changed <- Vector(false, true); fault <- Fault.values.toVector } {
              Given("a record variant with an independently declared residency decoder and Realm fault")
              val current = new Session(_token(), variant, writer)
              val sample = LawSample(Sample("Record 日本", "retained decoder baseline", 5L,
                if (changed) Mode.Changed else Mode.Current, variant), fault, mode)
              When("the actual canonical record projection and selected registered decoder are interpreted")
              val satisfied = checkLaw(sample, current, reader, physical, backend)
              Then("projection stays successful while decoder failure or earlier eviction failure remains separate")
              satisfied shouldBe true
            }
          }
        }
        s"UVO-06 preserve explicit native $backend Direct and CAS patch policies" in {
          withBackend(backend) { (writer, reader, physical) =>
            for { variant <- Vector(Variant.RecordSnapshot, Variant.RecordDetached); optimistic <- Vector(false, true) } {
              val current = new Session(_token(), variant, writer, optimistic = optimistic)
              checkNative(current, reader, physical, backend, optimistic)
            }
          }
        }
        s"UVO-05 retain raw None and effective Optimistic $backend legacy parity" in {
          withBackend(backend) { (writer, reader, physical) =>
            for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
              Given("separate fresh new/legacy active UnitOfWork sessions under assembled Optimistic")
              val current = new Session(_token(), variant, writer); val legacy = new Session(_token(), variant, writer)
              current.seed(); legacy.seed()
              val title = if (changed) "After" else "Before"; val version = if (changed) 2L else 1L
              val entity = current.source.input(title, revision(1L)); val oldentity = legacy.source.input(title, revision(1L))
              val before = current.source.rows(reader); val oldbefore = legacy.source.rows(reader); val beforephysical = physical()
              val wanted = expected(current, before, beforephysical, title, changed, version, backend)
              val oldwanted = expected(legacy, oldbefore, wanted.physical, title, changed, version, backend)
              val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None)
              current.arm(); legacy.arm()
              When("both owners assemble effective concurrency before semantic precondition validation")
              val capture = success(current.evaluateC(current.operation(entity, Patch(title), Some(revision(1L)), attempt)))
              val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch(title), Some(revision(1L)), attempt))
              Then("both actual guarded plans retain the existing Optimistic policy and complete independent projections/state")
              Vector(current, legacy).foreach { session =>
                checkPrimitive(session, Primitive.Versioned)
                session.source.recording.plans.head match {
                  case SelectedPlan.Versioned(plan) =>
                    plan.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
                    plan.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
                    plan.expectedRevision shouldBe Some(revision(1L))
                  case _ => fail("the declared strict policy requires the versioned primitive")
                }
              }
              checkCapture(capture, current, wanted, changed, version)
              checkProjection(capture.mutation.projection, variant, wanted.rows, current.setup, version)
              checkProjection(oldresult, variant, oldwanted.rows, legacy.setup, version)
              checkRows(current.source.rows(reader), wanted.rows); checkRows(legacy.source.rows(reader), oldwanted.rows)
              checkPhysical(physical(), oldwanted.physical)
            }
          }
        }
      }
      "UVO-03 preserve all four SQLite raw outcomes after the actual owned writer closes" in {
        for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
          withBackend("sqlite") { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            checkClosed(current, reader, physical, "sqlite", changed, () => { success(writer.asInstanceOf[SqlDataStore].closeC()); () })
          }
        }
      }
      "UVO-07 retain known SQLite rejection without a captured result or replay" in {
        Variant.values.toVector.foreach { variant =>
          EntityObservedDeleteCanonicalProviderFixture.withSqlite { database =>
            val source = EntityCreateProviderFixture.sqliteSource(database)
            val writer = new AcknowledgementStore(SqliteDialectDriver, source)
            val reader = new SqlDataStore(SqliteDialectDriver, source)
            try {
              val current = new Session(_token(), variant, writer)
              checkAcknowledgement(current, writer, reader, () => UnitOfWorkMutationCaptureFixture.snapshotSqlite(source), "sqlite", AcknowledgementMode.Reject)
            } finally { success(writer.closeC()); success(reader.closeC()) }
          }
        }
      }
    }

    "admit authority and canonical shapes before effects" which {
      "UVO-05 deny actual access-policy and factory requests before every primitive" in {
        for { variant <- Variant.values.toVector; factory <- Vector(false, true) } {
          Given("an owned complete seed and either an unrelated principal or an actual denying Component factory")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer, principal = if (factory) None else Some("intruder"), denyFactory = factory)
          current.seed()
          if (!factory) {
            val unrelated = current.source.rows(writer).unrelated.get.upsertSingle("owner_id", "intruder")
              .upsertSingle("group_id", "intruder").upsertSingle("privilege_id", "intruder")
            success(writer.save(current.setup.dataCollection, DataStore.EntryId(current.setup.unrelatedId), unrelated)(using current.context))
          }
          val entity = current.source.input("After", revision(1L)); val before = current.source.rows(writer); val resident = current.resident
          current.arm()
          When("one new node reaches the actual authorization chain")
          val supplied = current.authorization.copy(targetId = Some(current.setup.unrelatedId), collectionName = Some("foreign_collection"))
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), Some(supplied)))
          Then("permission rejection occurs before the selected writer and retains every row/cache and lifecycle state")
          conclusion(result).observation.taxonomy shouldBe conclusion(Consequence.securityPermissionDenied("expected permission denial")).observation.taxonomy
          current.factoryCalls should have size (if (factory) 1 else 0)
          current.space.routes shouldBe Vector.empty; current.space.outcome shouldBe None
          checkPrimitive(current, Primitive.Versioned, 0)
          current.resident shouldBe resident; checkRows(current.source.rows(writer), before)
        }
      }
      "UVO-05 rebind only selected identity and collection while retaining Aggregate command authority" in {
        Variant.values.toVector.foreach { variant =>
          Given("a full Aggregate command authorization with a supplied foreign target and collection")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer)
          current.seed()
          val entity = current.source.input("After", revision(1L))
          val supplied = current.authorization.copy(resourceFamily = "aggregate", resourceType = Some("RootAggregate"),
            targetId = Some(current.setup.unrelatedId), collectionName = Some("foreign_collection"), accessKind = "command:changeTitle",
            access = Some(CmlOperationAccess("authenticated_only")), operationModel = Some(ServiceOperationModel.BusinessService),
            relationRules = Vector(EntityAccessRelation("owner_id", "subject.id", Set("update"))),
            naturalConditions = EntityAbacCondition.parseList("owner_id=subject.id:update"))
          val wanted = supplied.copy(targetId = Some(current.setup.id), collectionName = Some(current.setup.rootName))
          val before = current.source.rows(writer)
          val expectedstate = expected(current, before, Map.empty, "After", true, 2L, "memory")
          current.arm()
          When("the real authorization chain receives the node's selected identity")
          val capture = success(current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), Some(supplied))))
          Then("factory evidence equals the entire rebound authorization and only selected persistence changes")
          current.factoryCalls shouldBe Vector(wanted)
          checkPrimitive(current, Primitive.Versioned); checkCapture(capture, current, expectedstate, true, 2L)
          checkRows(current.source.rows(writer), expectedstate.rows)
        }
      }
      "UVO-05 leave absent authorization absent without fabricating a grant" in {
        Variant.values.toVector.foreach { variant =>
          Given("an explicitly absent authorization with the existing canonical mutation policy")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer)
          current.seed(); val entity = current.source.input("After", revision(1L))
          val wanted = expected(current, current.source.rows(writer), Map.empty, "After", true, 2L, "memory")
          current.arm()
          When("the new node interprets an absent authorization")
          val capture = success(current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), None)))
          Then("the canonical writer still runs once and the factory sees no synthesized authorization")
          current.factoryCalls shouldBe Vector.empty
          checkPrimitive(current, Primitive.Versioned); checkCapture(capture, current, wanted, true, 2L)
          checkRows(current.source.rows(writer), wanted.rows)
        }
      }
      "UVO-05 reject every invalid Option, policy, entity/patch, persistent and selected identity shape" in {
        Variant.values.toVector.foreach { variant =>
          Given("a valid canonical memory seed and separately declared malformed node shapes")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer)
          current.seed(); val entity = current.source.input("After", revision(1L))
          val before = current.source.rows(writer); val resident = current.resident; val good = policy(revision(1L))
          val malformedpolicies = Vector[EntityMutationExecutionPolicy](null, good.copy(concurrencyPolicy = null), good.copy(writePolicy = null),
            good.copy(preconditionPolicy = null), good.copy(observedRevision = null), good.copy(observedRevision = Some(null)))
          val malformed = malformedpolicies.map(attempt => current.operation(entity, Patch("After"), Some(revision(1L)), attempt)) ++
            Vector(null, Some(null)).map(observed => current.operation(entity, Patch("After"), observed, good)) ++
            Vector[EntityId](null, nullCollectionId(current.setup.collectionId)).map(id =>
              current.operation(entity.copy(id = id), Patch("After"), Some(revision(1L)), good, targetId = id)) ++
            Vector(current.operation(null, null, Some(revision(1L)), good),
              current.operation(entity, Patch("After"), Some(revision(1L)), good, persistent = null, patchPersistent = null),
              current.operation(entity, Patch("After"), Some(revision(1L)), good, authorization = null),
              current.operation(entity, Patch("After"), Some(revision(1L)), good, authorization = Some(null)))
          val wantedshapes: Vector[(Consequence[?], String)] = Vector(
            Consequence.argumentMissing("executionPolicy") -> "EntityStore.scala",
            Consequence.argumentMissing("executionPolicy.concurrencyPolicy") -> "EntityStore.scala",
            Consequence.argumentMissing("executionPolicy.writePolicy") -> "EntityStore.scala",
            Consequence.argumentMissing("executionPolicy.preconditionPolicy") -> "EntityStore.scala",
            Consequence.argumentMissing("executionPolicy.observedRevision") -> "EntityStore.scala",
            Consequence.argumentInvalid("executionPolicy.observedRevision", "nonnull revision", "null revision") -> "EntityStore.scala",
            Consequence.argumentMissing("expectedRevision") -> "EntityStore.scala",
            Consequence.argumentInvalid("expectedRevision", "nonnull revision", "null revision") -> "EntityStore.scala",
            Consequence.argumentMissing("id") -> "EntityStore.scala",
            Consequence.argumentMissing("id.collection") -> "EntityStore.scala",
            Consequence.argumentMissing(if (typed(variant)) "entity" else "patch") -> "UnitOfWorkInterpreter.scala",
            Consequence.argumentMissing("persistent") -> "UnitOfWorkInterpreter.scala",
            Consequence.argumentMissing("authorization") -> "UnitOfWorkInterpreter.scala",
            Consequence.argumentInvalid("authorization", "nonnull authorization", "null authorization") -> "UnitOfWorkInterpreter.scala")
          current.arm()
          When("each invalid shape enters pre-effect admission independently")
          val results = malformed.map(current.evaluateC(_))
          Then("each shape is rejected before collection/auth/provider effects with exact selected collection failure correspondence")
          results.size shouldBe wantedshapes.size
          results.zip(wantedshapes).foreach { case (result, (wanted, source)) =>
            result shouldBe a[Consequence.Failure[?]]
            checkSourceFailure(conclusion(result), conclusion(wanted), source)
          }
          checkSourceFailure(conclusion(results(malformedpolicies.size + 3)), conclusion(Consequence.argumentMissing("id.collection")), "EntityStore.scala")
          current.factoryCalls shouldBe Vector.empty; current.space.routes shouldBe Vector.empty
          checkPrimitive(current, Primitive.Versioned, 0)
          current.resident shouldBe resident; checkRows(current.source.rows(writer), before)
        }
      }
      "UVO-05 retain unsupported defaults, missing providers and managed representation rejection" in {
        for { variant <- Variant.values.toVector; problem <- Vector("unmanaged", "wrong-binding", "missing-store", "unsupported", "capability") } {
          Given("an actual unsupported or incomplete registration boundary with complete independent data")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val other = if (EntityVersionedMutationPortFixture.representation(variant) == EntityRevisionRepresentation.Embedded)
            EntityRevisionRepresentation.Detached else EntityRevisionRepresentation.Embedded
          val current = new Session(_token(), variant, writer, managed = problem != "unmanaged",
            bindingRepresentation = if (problem == "wrong-binding") Some(other) else None,
            entityStore = if (problem == "missing-store") None else if (problem == "unsupported") Some(new NoopEntityStore()) else Some(EntityStore.standard()),
            runtimeDataStore = if (problem == "capability") Some(new NonVersionedStore(writer)) else None)
          current.seed(); val entity = current.source.input("After", revision(1L))
          val before = current.source.rows(writer); val resident = current.resident
          current.arm()
          When("one selected new node reaches the actual canonical binding/provider/capability boundary")
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), None))
          Then("a structured rejection precedes all writer effects and no legacy fallback or capture appears")
          result shouldBe a[Consequence.Failure[?]]
          if (problem == "unsupported") {
            reasons(result) shouldBe Vector("unsupported-capability")
            conclusion(result).observation.cause.descriptor.facets should contain(Descriptor.Facet.Capability("entitystore.versioned-mutation-outcome"))
          }
          checkPrimitive(current, Primitive.Versioned, 0)
          current.space.outcome shouldBe None; current.probe.admissions shouldBe 0
          current.resident shouldBe resident; checkRows(current.source.rows(writer), before)
        }
      }
      "UVO-05 reject effective None or missing observed preconditions without raw-policy prevalidation" in {
        for { variant <- Variant.values.toVector; missing <- Vector(false, true) } {
          Given("a valid node with either effective None plus ObservedRequired or a missing required observation")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer, optimistic = missing)
          current.seed(); val entity = current.source.input("After", revision(1L)); val before = current.source.rows(writer)
          val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None, observedRevision = if (missing) None else Some(revision(1L)))
          current.arm()
          When("the canonical producer selects effective policy and validates it")
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), attempt))
          Then("the exact existing policy failure precedes persistence and projection")
          val wanted = if (missing) Consequence.argumentMissing("observedRevision")
            else Consequence.configurationInvalid("Entity concurrency policy None cannot require an observed revision")
          checkSourceFailure(conclusion(result), conclusion(wanted), "EntityMutationPolicy.scala")
          checkPrimitive(current, Primitive.Versioned, 0)
          current.space.outcome shouldBe None; checkRows(current.source.rows(writer), before)
        }
      }
      "UVO-05 retain exact collection selection when a name is ambiguous and reject absent registration" in {
        for { variant <- Variant.values.toVector; registered <- Vector(false, true) } {
          Given("an actual fresh EntitySpace with either absent registration or two different collection identities sharing a name")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer, registered = registered)
          current.seed(); val entity = current.source.input("After", revision(1L))
          val before = current.source.rows(writer); val resident = current.resident
          if (registered) {
            val otherid = org.simplemodeling.model.datatype.EntityCollectionId("other", "identity", current.setup.rootName)
            val other = new org.goldenport.cncf.entity.runtime.EntityCollection(
              current.collection.descriptor.copy(collectionId = otherid), current.collection.storage)
            current.component.entitySpace.registerEntity(current.setup.rootName, other)
          }
          val wanted = expected(current, before, Map.empty, "After", registered, if (registered) 2L else 1L, "memory")
          current.arm()
          When("name lookup and the new node's exact selected collection are resolved through their actual owners")
          val byname = current.component.entitySpace.entityByNameC[Root](current.setup.rootName)
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), None))
          Then("ambiguous names are rejected by name lookup while exact-id nodes retain canonical selection, and absent binding cannot write")
          byname shouldBe a[Consequence.Failure[?]]
          if (registered) {
            reasons(byname) shouldBe Vector("entity-collection-name-ambiguous")
            checkCapture(success(result), current, wanted, true, 2L)
          } else { result shouldBe a[Consequence.Failure[?]]; current.space.outcome shouldBe None; current.resident shouldBe resident }
          checkPrimitive(current, Primitive.Versioned, if (registered) 1 else 0)
          checkRows(current.source.rows(writer), wanted.rows)
        }
      }
    }

    "retain enclosing-owner decisions" which {
      "UVO-05 select the simple missing-current hook without inventing a current record" in {
        Variant.values.toVector.foreach { variant =>
          Given("fresh active new/legacy sessions whose selected persisted rows are absent but whose declared input remains valid")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val event = new DomainEvent {}
          var calls = Vector.empty[String]
          val hook = new TransitionValidationHook {
            private def _stage(label: String)(using ctx: ExecutionContext): Consequence[Unit] = {
              calls :+= label; ctx.unitOfWork.stageEvent(event); Consequence.unit
            }
            def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage("save-missing")
            override def beforeSave[T](entity: T, tc: EntityPersistent[T], current: Record, proposed: Record)(using ExecutionContext): Consequence[Unit] = _stage("unexpected-current")
            def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage("unexpected-update")
            def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] = _stage("patch-missing")
            override def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P], current: Record, proposed: Record)(using ExecutionContext): Consequence[Unit] = _stage("unexpected-current")
          }
          val current = new Session(_token(), variant, writer, hook = hook); val legacy = new Session(_token(), variant, writer, hook = hook)
          current.seed(); legacy.seed()
          val entity = current.source.input("After", revision(1L)); val oldentity = legacy.source.input("After", revision(1L))
          Vector(current, legacy).foreach { session =>
            success(writer.delete(session.setup.dataCollection, DataStore.EntryId(session.setup.id))(using session.context))
            session.arm()
          }
          val before = current.source.rows(writer); val oldbefore = legacy.source.rows(writer)
          When("both actual transition owners encounter no current selected record before canonical persistence admission")
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)), None))
          val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch("After"), Some(revision(1L)), policy(revision(1L)), None))
          Then("only the simple overload stages its event and actual missing-target failure leaves full storage unchanged")
          calls shouldBe Vector.fill(2)(if (typed(variant)) "save-missing" else "patch-missing")
          Vector(current -> result, legacy -> oldresult).foreach { case (session, value) =>
            val missing = Consequence.DataStoreNotFound(DataStore.EntryId(session.setup.id).print)
            val wanted = if (typed(variant)) missing else EntityConcurrencyMetadata.mutationTargetFailure(conclusion(missing))
            checkConstructedFailure(conclusion(value), conclusion(wanted))
            session.uow.pendingEvents shouldBe Vector(event)
            checkPrimitive(session, Primitive.Versioned, 0)
          }
          current.space.outcome shouldBe None
          checkRows(current.source.rows(writer), before); checkRows(legacy.source.rows(writer), oldbefore)
        }
      }
      "UVO-05 mirror current-aware and simple hooks and pre-provider event retention" in {
        for { variant <- Variant.values.toVector; aware <- Vector(false, true); rejected <- Vector(false, true) } {
          Given("separate new/legacy UoWs with actual runtime hooks and an explicitly staged event")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val event = new DomainEvent {}
          var calls = Vector.empty[String]
          var currents = Vector.empty[Record]
          var proposals = Vector.empty[Record]
          def _stage_(label: String)(using ctx: ExecutionContext): Consequence[Unit] = {
            calls :+= label; ctx.unitOfWork.stageEvent(event)
            if (rejected) structuredFailure else Consequence.unit
          }
          val hook = new TransitionValidationHook {
            def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage_("save-simple")
            override def beforeSave[T](entity: T, tc: EntityPersistent[T], current: Record, proposed: Record)(using ExecutionContext): Consequence[Unit] =
              if (aware) { currents :+= current; proposals :+= proposed; _stage_("save-current") } else super.beforeSave(entity, tc, current, proposed)
            def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage_("update-simple")
            def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] = _stage_("patch-simple")
            override def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P], current: Record, proposed: Record)(using ExecutionContext): Consequence[Unit] =
              if (aware) { currents :+= current; proposals :+= proposed; _stage_("patch-current") } else super.beforeUpdateById(id, patch, tc, current, proposed)
          }
          val current = new Session(_token(), variant, writer, hook = hook)
          val legacy = new Session(_token(), variant, writer, hook = hook)
          current.seed(); legacy.seed()
          val entity = current.source.input("After", revision(1L)); val oldentity = legacy.source.input("After", revision(1L))
          val before = current.source.rows(writer); val oldbefore = legacy.source.rows(writer)
          val prior = new DomainEvent {}
          current.uow.stageEvent(prior); legacy.uow.stageEvent(prior)
          val proposed = if (typed(variant)) entity.record.upsertSingle("id", entity.id.value) else before.selected.get.upsertSingle("title", "After")
          val oldproposed = if (typed(variant)) oldentity.record.upsertSingle("id", oldentity.id.value) else oldbefore.selected.get.upsertSingle("title", "After")
          val wanted = expected(current, before, Map.empty, "After", !rejected, if (rejected) 1L else 2L, "memory")
          val oldwanted = expected(legacy, oldbefore, Map.empty, "After", !rejected, if (rejected) 1L else 2L, "memory")
          current.arm(); legacy.arm()
          When("both active owners authorize and invoke the same actual hook overload before their canonical writer")
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L))))
          val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch("After"), Some(revision(1L)), policy(revision(1L))))
          Then("hook selection, event retention and complete state match without an invented local event restore or auto-abort")
          val label = (if (typed(variant)) "save" else "patch") + (if (aware) "-current" else "-simple")
          calls shouldBe Vector(label, label)
          if (aware) {
            checkRecord(currents.head, before.selected.get); checkRecord(currents(1), oldbefore.selected.get)
            checkRecord(proposals.head, proposed); checkRecord(proposals(1), oldproposed)
          }
          current.uow.pendingEvents shouldBe Vector(prior, event); legacy.uow.pendingEvents shouldBe Vector(prior, event)
          Vector(current, legacy).foreach(checkPrimitive(_, Primitive.Versioned, if (rejected) 0 else 1))
          if (rejected) { (conclusion(result) eq structuredFailure.conclusion) shouldBe true; (conclusion(oldresult) eq structuredFailure.conclusion) shouldBe true }
          else checkCapture(success(result), current, wanted, true, 2L)
          checkRows(current.source.rows(writer), wanted.rows); checkRows(legacy.source.rows(writer), oldwanted.rows)
        }
      }
      "UVO-05 retain hook events after a real stale persistence failure in fresh legacy mirrors" in {
        Variant.values.toVector.foreach { variant =>
          Given("separate active new/legacy sessions with current revision two and a hook that stages an event")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val event = new DomainEvent {}
          val hook = new TransitionValidationHook {
            private def _stage(using ctx: ExecutionContext): Consequence[Unit] = { ctx.unitOfWork.stageEvent(event); Consequence.unit }
            def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage
            def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _stage
            def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] = _stage
          }
          val current = new Session(_token(), variant, writer, hook = hook); val legacy = new Session(_token(), variant, writer, hook = hook)
          current.seed(initialRevision = 2L); legacy.seed(initialRevision = 2L)
          val prior = new DomainEvent {}
          current.uow.stageEvent(prior); legacy.uow.stageEvent(prior)
          val entity = current.source.input("After", revision(1L)); val oldentity = legacy.source.input("After", revision(1L))
          val before = current.source.rows(writer); val oldbefore = legacy.source.rows(writer)
          current.arm(); legacy.arm()
          When("both hook owners stage events and their actual canonical providers reject a stale observation")
          val result = current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L))))
          val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch("After"), Some(revision(1L)), policy(revision(1L))))
          Then("Save/SaveDetached and observed/detached patch retain their events for the enclosing owner")
          checkConstructedFailure(conclusion(result), conclusion(oldresult))
          current.uow.pendingEvents shouldBe Vector(prior, event); legacy.uow.pendingEvents shouldBe Vector(prior, event)
          Vector(current, legacy).foreach(checkPrimitive(_, Primitive.Versioned))
          checkRows(current.source.rows(writer), before); checkRows(legacy.source.rows(writer), oldbefore)
        }
      }
    }

    "capture only admitted raw outcomes and keep programs pure" which {
      "UVO-04 contain malformed/NonFatal callbacks, retain structured identity and propagate control signals" in {
        Given("an actual canonical acknowledged outcome before any independent companion callback experiment")
        val writer = new AggregateObservedSnapshotFixture.MemoryStore()
        val current = new Session(_token(), Variant.TypedSnapshot, writer)
        current.seed(); val entity = current.source.input("After", revision(1L))
        val capture = success(current.evaluateC(current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)))))
        val raw = capture.mutation
        val before = current.source.rows(writer)
        var calls = 0
        val callbacks: Vector[() => Consequence[Unit]] = Vector(null, () => { calls += 1; null },
          () => { calls += 1; Consequence.Failure(null) }, () => { calls += 1; throw new IllegalStateException("private callback credential") })
        When("the companion receives valid raw evidence with malformed callbacks or an original structured coherence failure")
        val malformed = callbacks.map(callback => success(EntityVersionedMutationCaptureOutcome._from_outcome_c(current.setup.id, raw)(callback)))
        val structured = success(EntityVersionedMutationCaptureOutcome._from_outcome_c(current.setup.id, raw)(() => { calls += 1; structuredFailure }))
        Then("callback failures stay redacted and separate while the exact raw and structured Conclusion identities survive")
        calls shouldBe 4
        malformed.foreach { value =>
          (value.mutation eq raw) shouldBe true
          reasons(value.coherence) shouldBe Vector("entity-mutation-coherence-invalid")
          conclusion(value.coherence).show should not include "private callback credential"
          checkSourceFailure(conclusion(value.coherence), conclusion(Consequence.operationInvalid(
            "entity-mutation-coherence", Cause.Kind.Inconsistency,
            Vector(Descriptor.Facet.Reason("entity-mutation-coherence-invalid")))), "EntityCreateMutationOutcome.scala")
        }
        (structured.mutation eq raw) shouldBe true; (conclusion(structured.coherence) eq structuredFailure.conclusion) shouldBe true
        Vector[Throwable](new InterruptedException("owned interrupt"), new scala.util.control.ControlThrowable {}, new LinkageError("owned fatal")).foreach { signal =>
          Given("a valid actual raw outcome and one exact fatal/control/interruption object")
          When("the admitted companion callback raises that object")
          val propagated = intercept[Throwable] { EntityVersionedMutationCaptureOutcome._from_outcome_c(current.setup.id, raw)(() => throw signal) }
          Then("the exact excluded control object propagates without any writer replay")
          (propagated eq signal) shouldBe true
        }
        Given("null or foreign selected capture identities and a callback with an observable counter")
        var invalidcalls = 0
        val callback = () => { invalidcalls += 1; Consequence.unit }
        When("capture admission receives invalid selected identities or a missing raw outcome")
        val invalid = Vector(EntityVersionedMutationCaptureOutcome._from_outcome_c(null, raw)(callback),
          EntityVersionedMutationCaptureOutcome._from_outcome_c(nullCollectionId(current.setup.collectionId), raw)(callback),
          EntityVersionedMutationCaptureOutcome._from_outcome_c(current.setup.unrelatedId, raw)(callback),
          EntityVersionedMutationCaptureOutcome._from_outcome_c(current.setup.id, null)(callback))
        Then("the fixed inconsistency failure precedes every optional callback and retains complete storage")
        invalidcalls shouldBe 0
        invalid.foreach { result =>
          reasons(result) shouldBe Vector("entity-versioned-mutation-capture-invalid")
          conclusion(result).observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
          checkSourceFailure(conclusion(result), conclusion(Consequence.operationInvalid(
            "entity-versioned-mutation-capture", Cause.Kind.Inconsistency,
            Vector(Descriptor.Facet.Reason("entity-versioned-mutation-capture-invalid")))), "EntityVersionedMutationCaptureOutcome.scala")
        }
        checkRows(current.source.rows(writer), before); checkPrimitive(current, Primitive.Versioned)
      }
      "UVO-06 construct Local Free programs and record supplied typed outcomes without effects" in {
        Variant.values.toVector.foreach { variant =>
          Given("one actual prior capture and a fresh pure program over the corresponding internal node")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), variant, writer)
          current.seed(); val entity = current.source.input("After", revision(1L))
          val operation = current.operation(entity, Patch("After"), Some(revision(1L)), policy(revision(1L)))
          val supplied = current.evaluateC(operation)
          val actual = success(supplied)
          current.arm()
          val before = current.source.rows(writer); val pending = current.uow.pendingEvents
          When("the Free program is constructed, planned and interpreted only by the typed recording interpreter")
          val planned = UnitOfWorkProgramPlanner.plan(Vector(operation))
          val recorded = UnitOfWorkProgramRecorder.record(program(operation), new UnitOfWorkProgramStubProvider {
            def resultFor[A](ordinal: Int, selected: UnitOfWorkOp[A]): Consequence[A] = {
              ordinal shouldBe 0; (selected eq operation) shouldBe true
              supplied.asInstanceOf[Consequence[A]]
            }
          })
          Then("Local planning and typed recording preserve the supplied capture reference without touching a provider or lifecycle")
          planned.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local)
          planned.segments should have size 1
          (success(recorded.result) eq actual) shouldBe true
          recorded.occurrences.map(_.operation) shouldBe Vector(operation)
          checkPrimitive(current, Primitive.Versioned, 0); current.space.routes shouldBe Vector.empty
          current.uow.pendingEvents shouldBe pending; checkRows(current.source.rows(writer), before)
        }
      }
    }

    "satisfy sequential total laws with bounded reused rows" which {
      Vector("memory", "sqlite").foreach { backend =>
        s"UVO-P1 pass at least one hundred $backend UoW samples with no discards" in {
          Given("all four variants, lawful typed-failure input generation, finite independent coherence faults and reducing shrinking")
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = Variant.values.toVector.map(variant => variant -> new Session(_token(), variant, writer)).toMap
            val property = Prop.forAll(samples)(sample => checkLaw(sample, sessions(sample.input.variant), reader, physical, backend))
            When("the total real-UoW law runs sequentially over bounded reusable roots and pools")
            val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
            Then("the law requires passed, at least one hundred successes and zero discarded inputs")
            withClue(checked.toString) { checked.passed shouldBe true; checked.succeeded should be >= 100; checked.discarded shouldBe 0 }
          }
        }
      }
    }
  }
}
