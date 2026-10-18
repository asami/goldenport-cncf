package org.goldenport.cncf.unitofwork

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.{BodyMode, FaultMode}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 *  version Oct. 15, 2026
 * @version Oct. 18, 2026
 */
final class UnitOfWorkMutationCaptureSpec extends AnyWordSpec with UnitOfWorkMutationCaptureFixture.Vocabulary {
  import UnitOfWorkMutationCaptureFixture.*
  import UnitOfWorkMutationCaptureFixture.given
  private var _sequence = 0
  private def _session(environment: Environment, value: Sample = sample): Session = {
    _sequence += 1
    val session = new Session(s"portable_capture_$_sequence", value, environment)
    session.seed(); session
  }
  private def _with_backend(backend: String, normalize: Boolean = false)(body: Environment => Unit): Unit =
    if (backend == "memory") body(memory()) else withSqlite(normalize)(body)
  private def _resident_create(expected: Record, session: Session): Entity = Entity(session.freshId,
    if (session.sample.representation == EntityRevisionRepresentation.Detached)
      Record(expected.fields.filterNot(_.key == session.revisionField)) else expected)

  "Active UnitOfWork mutation capture" should {
    "defer effects until real canonical interpretation" which {
      for (backend <- Vector("memory", "sqlite")) {
        s"UMC-01 construct pure typed programs and retain every $backend create field" in {
          for { representation <- representations; body <- BodyMode.values.toVector; supplied <- Vector(false, true)
            normalize <- (if (backend == "sqlite") Vector(false, true) else Vector(false)) } {
            Given("a real registered managed collection, fixed identity context and complete independent prior rows/schema")
            _with_backend(backend, normalize) { environment =>
              val session = _session(environment, sample.copy(representation = representation, body = body, supplied = supplied))
              val before = session.rows(); val exactbefore = session.rows(environment.exact); val physical = environment.physical()
              val residentbefore = session.resident
              val expected = expectedCreate(session, before, physical, normalize)
              val exactexpected = expectedCreate(session, exactbefore, physical, false)
              val operation = session.createOperation
              When("both typed Free programs are constructed without interpretation")
              val createprogram = session.program(operation)
              val deleteprogram = session.program(session.deleteOperation())
              Then("construction performs no native writer, ID generation, optional coherence or enclosing commit")
              checkCounters(session, 0, 0); session.coherence shouldBe Vector.empty; session.idCalls shouldBe 0
              session.space.creates shouldBe 0; session.space.deletes shouldBe 0
              createprogram should not be null; deleteprogram should not be null
              checkSchema(session, physical)
              When("the real active interpreter executes the create once and independent readers observe the complete result")
              val outcome = success(session.evaluateC(operation))
              val after = session.rows(); val exactafter = session.rows(environment.exact); val physicalafter = environment.physical()
              val residentafter = session.resident
              val views = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("actual native and Space references, INITIAL metadata, whole rows/schema and selected residency match independent expectations")
              checkCreated(outcome, session, expected.raw); outcome.coherence shouldBe Consequence.unit
              checkRows(after, expected.rows); checkRows(exactafter, exactexpected.rows); checkPhysical(physicalafter, expected.physical)
              residentafter.selected shouldBe residentbefore.selected; residentafter.unrelated shouldBe residentbefore.unrelated
              residentafter.memoryselected shouldBe residentbefore.memoryselected; residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              residentafter.fresh shouldBe Some(_resident_create(expected.raw, session))
              residentafter.memoryfresh shouldBe residentafter.fresh
              session.coherence shouldBe Vector("evict-view-1", "decode"); views shouldBe Vector(1, 1)
            }
          }
        }
        s"UMC-02 retain actual $backend persistence through every registered decode fault and legacy comparison" in {
          for { representation <- representations; mode <- DecodeMode.values.toVector; legacy <- Vector(false, true) } {
            Given("a real registered decoder armed only after an actual canonical create acknowledgement")
            _with_backend(backend) { environment =>
              val session = _session(environment, sample.copy(representation = representation, mode = mode))
              val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              val expected = expectedCreate(session, before, physical, false)
              session.arm(mode)
              When("the active new or unchanged legacy create encounters the selected optional decode behavior after persistence")
              val captured = if (legacy) None else Some(session.evaluateC(session.createOperation))
              val old = if (legacy) Some(session.evaluateC(UnitOfWorkOp.EntityStoreCreate(session.input, session.createPersistent))) else None
              val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
              val views = Vector(success(session.view.resolve(session.freshId)), success(session.view.resolve(session.unrelatedId)))
              Then("full durable rows survive exactly one writer attempt while each route retains its own result boundary")
              checkRows(after, expected.rows); checkPhysical(physicalafter, expected.physical); checkCounters(session, 1, 0)
              session.decodeCalls shouldBe 1; session.space.creates shouldBe 1
              residentafter.selected shouldBe residentbefore.selected; residentafter.unrelated shouldBe residentbefore.unrelated
              residentafter.memoryselected shouldBe residentbefore.memoryselected; residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              if (!legacy) {
                val outcome = success(captured.get); checkCreated(outcome, session, expected.raw)
                session.coherence shouldBe Vector("evict-view-1", "decode"); views shouldBe Vector(1, 1)
                if (mode == DecodeMode.Good) {
                  outcome.coherence shouldBe Consequence.unit
                  residentafter.fresh shouldBe Some(_resident_create(expected.raw, session)); residentafter.memoryfresh shouldBe residentafter.fresh
                } else {
                  residentafter.fresh shouldBe None; residentafter.memoryfresh shouldBe None
                  if (mode == DecodeMode.Structured) (conclusion(outcome.coherence) eq conclusion(session.decodeFailure)) shouldBe true
                  else reason(outcome.coherence) shouldBe Some("entity-mutation-coherence-invalid")
                  outcome.coherence.toString.contains("private-database-credential") shouldBe false
                }
              } else if (mode == DecodeMode.Good) {
                val created = success(old.get); (created eq session.space.created.get) shouldBe true
                checkRecord(created.record.get, expected.raw); views shouldBe Vector(1, 1)
                residentafter.fresh shouldBe Some(_resident_create(expected.raw, session)); residentafter.memoryfresh shouldBe residentafter.fresh
              } else {
                old.get shouldBe a[Consequence.Failure[?]]
                if (mode == DecodeMode.Structured) (conclusion(old.get) eq conclusion(session.decodeFailure)) shouldBe true
                views shouldBe Vector(0, 0); residentafter shouldBe residentbefore
              }
            }
          }
        }
        s"UMC-03 retain actual physical/logical $backend deletion before optional Realm eviction" in {
          for { representation <- representations; logical <- Vector(false, true); thrown <- Vector(false, true); legacy <- Vector(false, true) } {
            Given("complete selected/unrelated root/body and resident/View state under a real registered Realm")
            _with_backend(backend) { environment =>
              val session = _session(environment, sample.copy(representation = representation, logical = logical))
              val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              val expected = expectedDelete(session, before, physical, false)
              session.arm(throwEvict = thrown)
              When("one actual observed deletion acknowledges its matched prior revision before optional eviction")
              val outcome = if (legacy) None else Some(session.evaluateC(session.deleteOperation()))
              val old = if (legacy) Some(session.evaluateC(UnitOfWorkOp.EntityStoreDeleteObserved(session.id, revision(session.sample.version)))) else None
              val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
              val views = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("only the selected durable mutation occurs and the captured object survives an eviction defect")
              checkRows(after, expected.rows); checkPhysical(physicalafter, expected.physical); checkCounters(session, 0, 1)
              session.decodeCalls shouldBe 0; residentafter.unrelated shouldBe residentbefore.unrelated
              residentafter.fresh shouldBe residentbefore.fresh; residentafter.memoryfresh shouldBe residentbefore.memoryfresh
              residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              if (thrown) {
                residentafter shouldBe residentbefore
                if (!legacy) {
                  checkDeleted(success(outcome.get), session); reason(success(outcome.get).coherence) shouldBe Some("entity-mutation-coherence-invalid")
                  views shouldBe Vector(1, 1); session.coherence shouldBe Vector("evict-view-1")
                } else { old.get shouldBe a[Consequence.Failure[?]]; views shouldBe Vector(0, 0); session.coherence shouldBe Vector("evict-view-0") }
              } else {
                residentafter.selected shouldBe None; residentafter.memoryselected shouldBe None
                views shouldBe Vector(1, 1)
                if (!legacy) { checkDeleted(success(outcome.get), session); success(outcome.get).coherence shouldBe Consequence.unit }
                else old.get shouldBe Consequence.success(EntityDeletionResult(session.id, revision(session.sample.version)))
              }
            }
          }
        }
      }
    }
    "admit canonical authority and shapes before effects" which {
      "UMC-04 deny actual user/factory authorization for both operations before native dispatch" in {
        for { kind <- Vector("create", "delete"); factory <- Vector(false, true) } {
          Given("an owner context with an actual manager-only user policy or a denying Component factory")
          val session = new Session(s"denied_${kind}_$factory", sample, memory(), denyFactory = factory)
          session.seed()
          val before = session.rows(); val residentbefore = session.resident
          val grant = session.authorization(kind, if (kind == "delete") Some(session.id) else None)
          val denied = if (factory) grant else grant.copy(access = Some(CmlOperationAccess(policy = "manager-only")))
          val operation: UnitOfWorkOp[?] = if (kind == "create") session.createOperation.copy(authorization = Some(denied))
            else session.deleteOperation().copy(authorization = Some(denied))
          When("the real active interpreter applies canonical user authorization and then factory authority")
          val result = session.evaluateC(operation)
          val after = session.rows(); val residentafter = session.resident
          Then("structured denial precedes writer dispatch and all durable/resident/View state remains complete")
          result shouldBe a[Consequence.Failure[?]]; checkRows(after, before); residentafter shouldBe residentbefore
          checkCounters(session, 0, 0); session.coherence shouldBe Vector.empty; session.viewBuilds shouldBe 3
          session.space.creates shouldBe 0; session.space.deletes shouldBe 0
          session.factoryCalls shouldBe (if (factory) Vector(denied) else Vector.empty)
        }
      }
      "UMC-04 rebind only the three delete target fields and retain all supplied authorization evidence" in {
        Given("a real authorized selected target and unrelated caller evidence on the new delete node")
        val session = _session(memory())
        val before = session.rows(); val expected = expectedDelete(session, before, Map.empty, false)
        val grant = session.authorization("read", Some(session.unrelatedId)).copy(collectionName = Some("incorrect"),
          resourceType = Some("RetainedResource"), entityNames = Vector("CaptureEntity", "RetainedEntity"))
        When("the actual active interpreter binds canonical delete identity and calls the normal loader/authorization chain")
        val outcome = success(session.evaluateC(session.deleteOperation().copy(authorization = Some(grant))))
        val after = session.rows()
        Then("only targetId, collectionName and accessKind change while the actual canonical deletion is captured")
        checkDeleted(outcome, session); checkRows(after, expected.rows)
        session.factoryCalls shouldBe Vector(grant.copy(targetId = Some(session.id), collectionName = Some(session.collectionId.name), accessKind = "delete"))
      }
      "UMC-04 reject null shapes, foreign identity and absent/unmanaged binding without a receipt" in {
        Given("a registered collection and independently retained complete baseline before any malformed input")
        val session = _session(memory())
        val before = session.rows(); val residentbefore = session.resident
        val foreign = new CanonicalId(EntityCollectionId("foreign", "capture", "same"), "foreign")
        val badpersistent = new EntityPersistentCreate[Input] {
          def id(entity: Input): Option[EntityId] = null
          def collection(entity: Input): EntityCollectionId = session.collectionId
          def toRecord(entity: Input): Record = entity.record
        }
        val operations: Vector[UnitOfWorkOp[?]] = Vector(session.createOperation.copy(entity = null), session.createOperation.copy(tc = null),
          session.createOperation.copy(options = null), session.createOperation.copy(authorization = null), session.createOperation.copy(authorization = Some(null)),
          session.createOperation.copy(options = EntityCreateOptions(defaultProfiles = null)),
          session.createOperation.copy(options = EntityCreateOptions(defaultValues = null)),
          session.createOperation.copy(entity = session.input.copy(identity = Some(null))), session.createOperation.copy(tc = badpersistent),
          session.createOperation.copy(entity = session.input.copy(identity = Some(foreign))),
          session.deleteOperation().copy(id = null), session.deleteOperation().copy(expectedrevision = null),
          session.deleteOperation().copy(authorization = null), session.deleteOperation().copy(authorization = Some(null)))
        When("each malformed new operation is interpreted through its real pre-effect admission")
        val results = operations.map(operation => session.evaluateC(operation))
        val after = session.rows(); val residentafter = session.resident
        Then("all attempts fail structurally without native/Space writes, coherence, identity generation or state loss")
        results.foreach(_ shouldBe a[Consequence.Failure[?]]); checkRows(after, before); residentafter shouldBe residentbefore
        checkCounters(session, 0, 0); session.coherence shouldBe Vector.empty
        Vector(false -> true, true -> false).foreach { case (managed, registered) =>
          Given("a separately owned unmanaged or unregistered collection with its full durable baseline")
          val unbound = new Session(s"unbound_${managed}_$registered", sample, memory(), managed, registered)
          unbound.seed(); val before = unbound.rows()
          When("the new create requires an exact registered managed binding before invoking Space")
          val result = unbound.evaluateC(unbound.createOperation); val after = unbound.rows()
          Then("the canonical representation policy fails closed and every prior row remains exact")
          val facets = conclusion(result).observation.cause.descriptor.facets
          facets should contain(Descriptor.Facet.Policy("entity.revision.representation"))
          facets should contain(Descriptor.Facet.Expected("embedded-or-detached")); facets should contain(Descriptor.Facet.Actual("unmanaged"))
          checkRows(after, before); checkCounters(unbound, 0, 0); unbound.space.creates shouldBe 0
        }
      }
      "UMC-04 retain stale, missing, duplicate and unavailable-capability state with canonical reconciliation" in {
        for (mode <- Vector("stale", "missing", "duplicate", "capability", "delete-capability")) {
          Given("the real canonical provider and complete selected/unrelated rows before an inadmissible mutation")
          val provider = if (mode == "capability" || mode == "delete-capability") new EntityObservedDeleteCanonicalProviderFixture.MemoryStore(
            missingFeature = Some(if (mode == "capability") EntityMutationProviderFeature.AtomicCreate else EntityMutationProviderFeature.OptimisticCompareAndDelete))
            else new EntityCreateFailureSafetyFixture.MemoryStore()
          val session = _session(memory(provider)); val before = session.rows(); val residentbefore = session.resident
          val operation: UnitOfWorkOp[?] = mode match {
            case "stale" => session.deleteOperation(revision(sample.version - 1))
            case "missing" => session.deleteOperation().copy(id = session.freshId, authorization = None)
            case "duplicate" => session.createOperation.copy(entity = session.input.copy(identity = Some(session.id),
              record = session.input.record.upsertSingle("id", session.id)))
            case "delete-capability" => session.deleteOperation()
            case _ => session.createOperation
          }
          When("one active attempt is rejected at the actual canonical admission/provider boundary")
          val result = session.evaluateC(operation); val after = session.rows(); val residentafter = session.resident
          Then("no outcome or optional decode is manufactured and whole persistence is preserved without replay")
          result shouldBe a[Consequence.Failure[?]]; checkRows(after, before); session.decodeCalls shouldBe 0
          checkCounters(session, if (mode == "duplicate") 1 else 0, if (mode == "stale") 1 else 0)
          if (mode == "stale") {
            reason(result) shouldBe Some("stale-entity-revision"); residentafter.selected shouldBe None
            session.coherence shouldBe Vector("evict-view-0")
          } else if (mode == "missing") { residentafter.fresh shouldBe None; session.coherence shouldBe Vector("evict-view-0") }
          else { residentafter shouldBe residentbefore; session.coherence shouldBe Vector.empty }
          session.viewBuilds shouldBe 3
        }
      }
    }
    "respect actual provider atomicity and typed recording" which {
      "UMC-04 retain full SQLite logical/physical state on real authorization and shape rejection" in {
        for { normalized <- Vector(false, true); kind <- Vector("create", "delete"); malformed <- Vector(false, true) } {
          Given("an explicit SQLite schema, independent full tables and real manager-only or malformed input admission")
          withSqlite(normalized) { environment =>
            val session = _session(environment); val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
            val grant = session.authorization(kind, if (kind == "delete") Some(session.id) else None)
              .copy(access = Some(CmlOperationAccess(policy = "manager-only")))
            val operation: UnitOfWorkOp[?] = if (kind == "create") {
              if (malformed) session.createOperation.copy(authorization = Some(null)) else session.createOperation.copy(authorization = Some(grant))
            } else if (malformed) session.deleteOperation().copy(expectedrevision = null) else session.deleteOperation().copy(authorization = Some(grant))
            When("one real active attempt is rejected before any canonical writer is invoked")
            val result = session.evaluateC(operation); val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
            Then("the structured failure supplies no receipt and every logical/physical/resident value remains baseline-exact")
            result shouldBe a[Consequence.Failure[?]]; checkRows(after, before); checkPhysical(physicalafter, physical); residentafter shouldBe residentbefore
            checkCounters(session, 0, 0); session.coherence shouldBe Vector.empty; session.factoryCalls shouldBe Vector.empty
            session.space.creates shouldBe 0; session.space.deletes shouldBe 0; session.viewBuilds shouldBe 3
          }
        }
      }
      "UMC-05 preserve real memory/SQLite physical and logical deletion rollback at each pinned checkpoint" in {
        for { backend <- Vector("memory", "sqlite"); logical <- Vector(false, true)
          checkpoint <- Vector(EntityVersionedMutationCheckpoint.RootPrepared, EntityVersionedMutationCheckpoint.SideEffectsPrepared, EntityVersionedMutationCheckpoint.BeforePublish) } {
          Given("a pinned observed-delete checkpoint writer and complete selected/unrelated persistence before action")
          _with_backend(backend) { initial =>
            val writer: DataStore = if (backend == "memory") new EntityObservedDeleteCanonicalProviderFixture.MemoryStore(checkpoint = Some(checkpoint))
              else new EntityObservedDeleteCanonicalProviderFixture.SqlStore(org.goldenport.cncf.datastore.sql.SqliteDialectDriver,
                initial.source.get, checkpoint = Some(checkpoint))
            val environment = if (backend == "memory") memory(writer) else initial.copy(writer = writer)
            try {
              val session = _session(environment, sample.copy(logical = logical)); val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              When("one actual observed delete reaches the selected native rejecting checkpoint")
              val result = session.evaluateC(session.deleteOperation()); val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
              Then("atomic rollback preserves complete state and no deletion receipt or optional coherence is manufactured")
              reason(result) shouldBe Some("provider-failure"); checkRows(after, before); checkPhysical(physicalafter, physical); residentafter shouldBe residentbefore
              checkCounters(session, 0, 1); session.coherence shouldBe Vector.empty; session.space.deleted shouldBe None; session.recording.deleted shouldBe None
            } finally writer match {
              case store: org.goldenport.cncf.datastore.sql.SqlDataStore => success(store.closeC())
              case _ => ()
            }
          }
        }
      }
      "UMC-05 retain complete rollback for actual memory and SQLite create checkpoints" in {
        for { backend <- Vector("memory", "sqlite"); mode <- Vector(FaultMode.RootCreate, FaultMode.SideSave, FaultMode.BeforePublish,
          FaultMode.ThrowRoot, FaultMode.ThrowSide, FaultMode.ThrowPublish) } {
          Given("an existing pinned checkpoint provider and full independent selected/unrelated baseline")
          _with_backend(backend) { environment =>
            val session = _session(environment); val before = session.rows(); val physical = environment.physical(); val resident = session.resident
            environment.writer match {
              case writer: EntityCreateFailureSafetyFixture.MemoryStore => writer.arm(session.fault(mode))
              case writer: EntityCreateFailureSafetyFixture.SqlStore => writer.arm(session.fault(mode))
              case _ => fail("A pinned real checkpoint writer is required")
            }
            When("one real canonical create reaches its selected rejecting or throwing checkpoint")
            val result = session.evaluateC(session.createOperation); val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
            Then("provider failure retains the full pre-action storage and performs no optional work, replay or compensation")
            reason(result) shouldBe Some("provider-failure"); checkRows(after, before); checkPhysical(physicalafter, physical)
            checkCounters(session, 1, 0); session.coherence shouldBe Vector.empty; residentafter shouldBe resident
            session.space.created shouldBe None; session.recording.created shouldBe None
          }
        }
      }
      "UMC-05 distinguish SQLite real commit rejection from lost, thrown or null acknowledgement for both routes" in {
        for { action <- Vector("create", "delete"); mode <- Vector(AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          Given("a pinned actual SQLite acknowledgement writer and complete independent expected committed and rolled-back states")
          withSqlite(acknowledgement = true) { environment =>
            val session = _session(environment); val before = session.rows(); val physical = environment.physical(); val resident = session.resident
            val expected = if (action == "create") expectedCreate(session, before, physical, false) else expectedDelete(session, before, physical, false)
            val writer = environment.writer.asInstanceOf[AcknowledgementStore]; writer.arm(mode)
            When("the canonical route encounters rejection or uncertainty after the actual connection.commit")
            val result = if (action == "create") session.evaluateC(session.createOperation) else session.evaluateC(session.deleteOperation())
            val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
            Then("unknown acknowledgement never yields a receipt while complete independent state distinguishes rollback and committed data")
            reason(result) shouldBe Some(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
            val committed = mode != AcknowledgementMode.Reject
            checkRows(after, if (committed) expected.rows else before); checkPhysical(physicalafter, if (committed) expected.physical else physical)
            checkCounters(session, if (action == "create") 1 else 0, if (action == "delete") 1 else 0)
            writer.commitAttempts shouldBe 1; session.coherence shouldBe Vector.empty; residentafter shouldBe resident
            session.space.created shouldBe None; session.space.deleted shouldBe None
          }
        }
      }
      "UMC-06 record each exact typed program using its actual interpreted result without another physical effect" in {
        Given("actual create and delete operations, real persistence and independently prepared full expected states")
        val session = _session(memory()); val before = session.rows(); val createexpected = expectedCreate(session, before, Map.empty, false)
        val create = session.createOperation
        When("the active interpreter performs the create before the separate typed recorder receives its real outcome")
        val created = session.evaluateC(create)
        val recordedcreate = _record(session, create, created)
        val aftercreate = session.rows()
        Then("recording preserves exact occurrence, outcome and Local planner correspondence without replay")
        checkCreated(success(created), session, createexpected.raw); checkRows(aftercreate, createexpected.rows)
        _check_recording(create, created, recordedcreate)
        Given("the completed create snapshot and a separate actual observed-delete operation")
        val delete = session.deleteOperation(); val expected = expectedDelete(session, createexpected.rows, Map.empty, false)
        session.arm()
        When("the real interpreter deletes once and its actual outcome is supplied to the separate recorder")
        val deleted = session.evaluateC(delete); val recordeddelete = _record(session, delete, deleted); val after = session.rows()
        Then("the actual deletion object and complete persistence coexist with metadata-only recording")
        checkDeleted(success(deleted), session); checkRows(after, expected.rows); _check_recording(delete, deleted, recordeddelete)
      }
    }
    "satisfy total sequential persistence laws" which {
      for (backend <- Vector("memory", "sqlite")) {
        s"UMC-P1 execute at least 100 shrinkable $backend create/delete samples with zero discards" in {
          Given("bounded Unicode input, revision, body, representation, identity and decode modes with lawful shrinking")
          _with_backend(backend) { environment =>
            val law = Prop.forAll(samples) { value =>
              Given("a fresh registered session and complete independently constructed creation state")
              val session = _session(environment, value); val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              val createexpected = expectedCreate(session, before, physical, false)
              When("the actual create runs once with the known identity followed by independent full observation")
              val created = success(session.evaluateC(session.createOperation)); val aftercreate = session.rows(); val createphysical = environment.physical()
              val createresident = session.resident; val createviews = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("every raw field, native reference, body and unrelated physical row matches its independent oracle")
              checkCreated(created, session, createexpected.raw); checkRows(aftercreate, createexpected.rows); checkPhysical(createphysical, createexpected.physical)
              checkCreateResident(createresident, residentbefore, createexpected.raw, session)
              session.coherence shouldBe Vector("evict-view-1", "decode"); createviews shouldBe Vector(1, 1)
              if (value.mode == DecodeMode.Good) created.coherence shouldBe Consequence.unit
              else (conclusion(created.coherence) eq conclusion(session.decodeFailure)) shouldBe true
              Given("the finished creation and independently prescribed physical or logical deletion at the old matched revision")
              val expected = expectedDelete(session, createexpected.rows, createexpected.physical, false); session.arm(DecodeMode.Good)
              When("the actual observed delete executes without replay or an enclosing commit")
              val deleted = success(session.evaluateC(session.deleteOperation())); val after = session.rows(); val afterphysical = environment.physical()
              val deleteresident = session.resident; val deleteviews = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("the exact canonical deletion receipt and complete durable state follow the selected lifecycle law")
              checkDeleted(deleted, session); checkRows(after, expected.rows); checkPhysical(afterphysical, expected.physical)
              checkDeleteResident(deleteresident, createresident); session.coherence shouldBe Vector("evict-view-1"); deleteviews shouldBe Vector(1, 1)
              true
            }
            When("the sequential total property runs with 100 required successes and no input filtering")
            val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
            Then("all generated cases must pass and none may be discarded")
            result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
          }
        }
      }
      "UMC-01 retain literal physical JSON object and array text through SQLite canonical capture" in {
        for { normalized <- Vector(false, true); business <- Vector("{\"n\":7,\"ok\":false}", "[7,false,\"label\"]") } {
          Given("independently declared JSON-looking business Strings and exact original permission text")
          withSqlite(normalized) { environment =>
            val session = _session(environment, sample.copy(business = business)); val before = session.rows(); val physical = environment.physical()
            val expected = expectedCreate(session, before, physical, normalized)
            When("native canonical creation returns its physical record without payload inference")
            val outcome = success(session.evaluateC(session.createOperation)); val after = session.rows(); val physicalafter = environment.physical()
            Then("every raw captured field and physical cell retains the independently prescribed String values")
            outcome.persisted.record.getAny("business") shouldBe Some(business)
            outcome.persisted.record.getAny("permission") shouldBe Some(permission)
            checkCreated(outcome, session, expected.raw); checkRows(after, expected.rows); checkPhysical(physicalafter, expected.physical)
          }
        }
      }
    }
  }

  private def _record[A](session: Session, operation: UnitOfWorkOp[A], outcome: Consequence[A]): UnitOfWorkProgramRecording[A] =
    UnitOfWorkProgramRecorder.record(session.program(operation), new UnitOfWorkProgramStubProvider {
      def resultFor[B](ordinal: Int, selected: UnitOfWorkOp[B]): Consequence[B] = {
        outcome.asInstanceOf[Consequence[B]]
      }
    })
  private def _check_recording[A](operation: UnitOfWorkOp[A], result: Consequence[A], recorded: UnitOfWorkProgramRecording[A]): Unit = {
    UnitOfWorkOperationClassifier.classify(operation) shouldBe UnitOfWorkEffectClass.Local
    recorded.result shouldBe result; recorded.occurrences.map(_.operation) shouldBe Vector(operation)
    recorded.occurrences.map(_.ordinal) shouldBe Vector(0); recorded.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local)
    recorded.plan shouldBe UnitOfWorkProgramPlanner.plan(Vector(operation))
    recorded.plan.segments shouldBe Vector(UnitOfWorkPlanSegment.LocalAtomic(recorded.plan.occurrences))
    (success(recorded.result).asInstanceOf[AnyRef] eq success(result).asInstanceOf[AnyRef]) shouldBe true
  }
}
