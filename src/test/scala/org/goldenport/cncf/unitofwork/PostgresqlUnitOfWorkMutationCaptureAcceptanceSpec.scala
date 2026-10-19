package org.goldenport.cncf.unitofwork

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.{BodyMode, FaultMode}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlUnitOfWorkMutationCaptureAcceptanceSpec extends AnyWordSpec
    with UnitOfWorkMutationCaptureFixture.Vocabulary with PostgresqlAcceptance {
  import UnitOfWorkMutationCaptureFixture.*
  import UnitOfWorkMutationCaptureFixture.given

  private def _session(database: PostgresqlPersistenceFixture.Database, environment: Environment, value: Sample = sample): Session = {
    val session = new Session(database.token("capture"), value, environment)
    session.seed(); session
  }

  "Required PostgreSQL 17.11 canonical UoW capture with fsync and synchronous_commit" should {
    "retain complete native receipt and lifecycle correspondence" which {
      for (normalized <- Vector(false, true)) {
        s"PG-CMC-01 capture every create body/identity/representation with normalization $normalized" in {
          for { representation <- representations; mode <- BodyMode.values.toVector; supplied <- Vector(false, true) } {
            Given("mandatory native PostgreSQL, registered persistent binding, explicit full schemas and independently prescribed input/default/body fields")
            with_postgresql { database =>
              val environment = postgresql(database, normalized)
              val session = _session(database, environment, sample.copy(representation = representation, body = mode, supplied = supplied))
              val before = session.rows(); val exactbefore = session.rows(environment.exact); val physical = environment.physical()
              val residentbefore = session.resident
              val expected = expectedCreate(session, before, physical, normalized)
              val exactexpected = expectedCreate(session, exactbefore, physical, false)
              When("the actual registered active UoW calls canonical create and separate native readers observe the durable result")
              val outcome = success(session.evaluateC(session.createOperation)); val after = session.rows(); val exactafter = session.rows(environment.exact)
              val physicalafter = environment.physical(); val residentafter = session.resident
              val views = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("the exact raw references, every logical field and physical column/type/NULL/row are preserved under one native attempt")
              checkSchema(session, physical); checkCreated(outcome, session, expected.raw); outcome.coherence shouldBe Consequence.unit
              checkRows(after, expected.rows); checkRows(exactafter, exactexpected.rows); checkPhysical(physicalafter, expected.physical)
              residentafter.selected shouldBe residentbefore.selected; residentafter.unrelated shouldBe residentbefore.unrelated
              residentafter.memoryselected shouldBe residentbefore.memoryselected; residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              val domain = if (representation == EntityRevisionRepresentation.Detached) Record(expected.raw.fields.filterNot(_.key == session.revisionField)) else expected.raw
              residentafter.fresh shouldBe Some(Entity(session.freshId, domain)); residentafter.memoryfresh shouldBe residentafter.fresh
              views shouldBe Vector(1, 1); session.coherence shouldBe Vector("evict-view-1", "decode")
            }
          }
        }
        s"PG-CMC-01 retain complete native creation through all registered decode modes with normalization $normalized" in {
          for { representation <- representations; mode <- DecodeMode.values.toVector } {
            Given("an actual registered decoder whose fault is armed only by a real native acknowledgement")
            with_postgresql { database =>
              val environment = postgresql(database, normalized)
              val session = _session(database, environment, sample.copy(representation = representation, mode = mode))
              val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              val expected = expectedCreate(session, before, physical, normalized)
              When("canonical creation succeeds before the selected registered optional decode behavior")
              val outcome = success(session.evaluateC(session.createOperation)); val after = session.rows(); val physicalafter = environment.physical()
              val residentafter = session.resident; val view = success(session.view.resolve(session.unrelatedId))
              Then("capture retains the raw receipt, full durable state and redacted optional failure without mutation replay")
              checkCreated(outcome, session, expected.raw); checkRows(after, expected.rows); checkPhysical(physicalafter, expected.physical)
              session.decodeCalls shouldBe 1; session.coherence shouldBe Vector("evict-view-1", "decode"); view shouldBe 1
              residentafter.selected shouldBe residentbefore.selected; residentafter.unrelated shouldBe residentbefore.unrelated
              residentafter.memoryselected shouldBe residentbefore.memoryselected; residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              if (mode == DecodeMode.Good) {
                outcome.coherence shouldBe Consequence.unit
                val domain = if (representation == EntityRevisionRepresentation.Detached) Record(expected.raw.fields.filterNot(_.key == session.revisionField)) else expected.raw
                residentafter.fresh shouldBe Some(Entity(session.freshId, domain)); residentafter.memoryfresh shouldBe residentafter.fresh
              } else {
                residentafter.fresh shouldBe None; residentafter.memoryfresh shouldBe None
                if (mode == DecodeMode.Structured) (conclusion(outcome.coherence) eq conclusion(session.decodeFailure)) shouldBe true
                else reason(outcome.coherence) shouldBe Some("entity-mutation-coherence-invalid")
                outcome.coherence.toString.contains("private-database-credential") shouldBe false
              }
            }
          }
        }
        s"PG-CMC-01 retain physical/logical deletion and Realm failure with normalization $normalized" in {
          for { representation <- representations; logical <- Vector(false, true); thrown <- Vector(false, true) } {
            Given("explicit TEXT state/guard columns, independent old/unrelated bodies and an actual registered Realm")
            with_postgresql { database =>
              val environment = postgresql(database, normalized)
              val session = _session(database, environment, sample.copy(representation = representation, logical = logical))
              val before = session.rows(); val exactbefore = session.rows(environment.exact); val physical = environment.physical(); val residentbefore = session.resident
              val expected = expectedDelete(session, before, physical, normalized)
              val exactexpected = expectedDelete(session, exactbefore, physical, false)
              session.arm(throwEvict = thrown)
              When("one actual observed delete acknowledges its matched prior revision before optional registered Realm removal")
              val outcome = success(session.evaluateC(session.deleteOperation())); val after = session.rows(); val exactafter = session.rows(environment.exact)
              val physicalafter = environment.physical(); val residentafter = session.resident
              val views = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
              Then("the exact deletion result and independently prescribed full tombstone/absence remain valid through optional eviction failure")
              checkSchema(session, physical); checkDeleted(outcome, session); checkRows(after, expected.rows); checkRows(exactafter, exactexpected.rows)
              checkPhysical(physicalafter, expected.physical); views shouldBe Vector(1, 1); session.coherence shouldBe Vector("evict-view-1")
              if (thrown) { reason(outcome.coherence) shouldBe Some("entity-mutation-coherence-invalid"); residentafter shouldBe residentbefore }
              else {
                outcome.coherence shouldBe Consequence.unit; residentafter.selected shouldBe None; residentafter.memoryselected shouldBe None
                residentafter.fresh shouldBe residentbefore.fresh; residentafter.memoryfresh shouldBe residentbefore.memoryfresh
                residentafter.unrelated shouldBe residentbefore.unrelated; residentafter.memoryunrelated shouldBe residentbefore.memoryunrelated
              }
            }
          }
        }
      }
      "PG-CMC-01 retain literal physical JSON object and array text through canonical UoW capture" in {
        for { normalized <- Vector(false, true); business <- Vector("{\"n\":7,\"ok\":false}", "[7,false,\"label\"]") } {
          Given("independently declared JSON-looking business Strings and exact original permission text")
          with_postgresql { database =>
            val environment = postgresql(database, normalized)
            val session = _session(database, environment, sample.copy(business = business)); val before = session.rows(); val physical = environment.physical()
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
    "distinguish real rollback and unknown acknowledgement without a receipt" which {
      "PG-CMC-02 preserve native physical/logical deletion rollback at the real root/side/publication checkpoints" in {
        for { representation <- representations; logical <- Vector(false, true)
          checkpoint <- Vector(EntityVersionedMutationCheckpoint.RootPrepared, EntityVersionedMutationCheckpoint.SideEffectsPrepared, EntityVersionedMutationCheckpoint.BeforePublish) } {
          Given("the pinned native observed-delete checkpoint writer with actual registered binding and complete explicit schema")
          with_postgresql { database =>
            val writer = new EntityObservedDeleteCanonicalProviderFixture.SqlStore(org.goldenport.cncf.datastore.sql.PostgreSqlDialectDriver,
              database.source(), checkpoint = Some(checkpoint))
            val environment = postgresql(database, false).copy(writer = writer)
            try {
              val session = _session(database, environment, sample.copy(representation = representation, logical = logical))
              val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
              When("the actual canonical delete rejects after real root/side preparation and before native publication")
              val result = session.evaluateC(session.deleteOperation()); val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
              Then("full native rollback preserves every selected/unrelated cell and invokes no optional coherence or replay")
              reason(result) shouldBe Some("provider-failure"); checkRows(after, before); checkPhysical(physicalafter, physical); residentafter shouldBe residentbefore
              checkCounters(session, 0, 1); session.coherence shouldBe Vector.empty; session.space.deleted shouldBe None; session.recording.deleted shouldBe None
            } finally success(writer.closeC())
          }
        }
      }
      "PG-CMC-02 preserve complete native duplicate/stale rejection under both read conventions and representations" in {
        for { normalized <- Vector(false, true); representation <- representations; duplicate <- Vector(false, true) } {
          Given("the actual native provider, full declared schemas and complete selected/unrelated baseline")
          with_postgresql { database =>
            val environment = postgresql(database, normalized)
            val session = _session(database, environment, sample.copy(representation = representation)); val before = session.rows(); val physical = environment.physical()
            val candidate = session.input.copy(identity = Some(session.id), record = session.input.record.upsertSingle("id", session.id))
            When("one canonical create duplicates an existing identity or observed delete supplies a stale revision")
            val result = if (duplicate) session.evaluateC(session.createOperation.copy(entity = candidate))
              else session.evaluateC(session.deleteOperation(revision(sample.version - 1)))
            val after = session.rows(); val physicalafter = environment.physical()
            Then("canonical structured rejection supplies no outcome, preserves all data and never retries")
            result shouldBe a[Consequence.Failure[?]]; checkRows(after, before); checkPhysical(physicalafter, physical)
            checkCounters(session, if (duplicate) 1 else 0, if (duplicate) 0 else 1)
            session.decodeCalls shouldBe 0
            if (duplicate) {
              conclusion(result).observation.taxonomy shouldBe conclusion(Consequence.DataStoreDuplicate(session.id.print)).observation.taxonomy
              session.coherence shouldBe Vector.empty
            } else { reason(result) shouldBe Some("stale-entity-revision"); session.coherence shouldBe Vector("evict-view-0") }
          }
        }
      }
      "PG-CMC-02 retain native root/side/publication rollback without substituting provider results" in {
        for { representation <- representations; mode <- Vector(FaultMode.RootCreate, FaultMode.SideSave, FaultMode.BeforePublish,
          FaultMode.ThrowRoot, FaultMode.ThrowSide, FaultMode.ThrowPublish) } {
          Given("the pinned native checkpoint writer with independently declared root/overflow addresses and full prior tables")
          with_postgresql { database =>
            val environment = postgresql(database, false)
            val session = _session(database, environment, sample.copy(representation = representation)); val before = session.rows(); val physical = environment.physical(); val resident = session.resident
            val writer = environment.writer.asInstanceOf[EntityCreateFailureSafetyFixture.SqlStore]
            writer.arm(session.fault(mode))
            When("one canonical native create reaches the selected actual rejecting or throwing checkpoint")
            val result = session.evaluateC(session.createOperation); val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
            Then("atomic rollback preserves every physical cell and logical field with no receipt or optional work")
            reason(result) shouldBe Some("provider-failure"); checkRows(after, before); checkPhysical(physicalafter, physical)
            checkCounters(session, 1, 0); session.coherence shouldBe Vector.empty; residentafter shouldBe resident
            writer.sideSaves shouldBe 1; writer.rootCreates shouldBe (if (mode == FaultMode.SideSave || mode == FaultMode.ThrowSide) 0 else 1)
            writer.commitAttempts shouldBe 0; session.space.created shouldBe None; session.recording.created shouldBe None
          }
        }
      }
      "PG-CMC-02 distinguish acknowledged, rejected, indeterminate, escaped and null commit for both deletion modes and creation" in {
        for { representation <- representations; action <- Vector("create", "physical-delete", "logical-delete")
          mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          Given("a pinned real native acknowledgement writer using its unchanged exact-reader convention and complete independently expected states")
          with_postgresql { database =>
            val environment = postgresql(database, false, acknowledgement = true)
            val session = _session(database, environment, sample.copy(representation = representation, logical = action == "logical-delete"))
            val before = session.rows(); val physical = environment.physical(); val resident = session.resident
            val expected = if (action == "create") expectedCreate(session, before, physical, false) else expectedDelete(session, before, physical, false)
            val writer = environment.writer.asInstanceOf[AcknowledgementStore]; writer.arm(mode)
            When("one actual canonical attempt reaches rejection or real connection.commit and its selected acknowledgement")
            val created = if (action == "create") Some(session.evaluateC(session.createOperation)) else None
            val deleted = if (action == "create") None else Some(session.evaluateC(session.deleteOperation()))
            val result: Consequence[?] = created.getOrElse(deleted.get)
            val after = session.rows(); val physicalafter = environment.physical(); val residentafter = session.resident
            Then("known success alone yields capture; unknown acknowledgement retains correct durable data without replay or fabricated success")
            checkRows(after, if (mode == AcknowledgementMode.Reject) before else expected.rows)
            checkPhysical(physicalafter, if (mode == AcknowledgementMode.Reject) physical else expected.physical)
            checkCounters(session, if (action == "create") 1 else 0, if (action == "create") 0 else 1); writer.commitAttempts shouldBe 1
            if (mode == AcknowledgementMode.Normal) {
              if (action == "create") checkCreated(success(created.get), session, expected.raw) else checkDeleted(success(deleted.get), session)
            } else {
              reason(result) shouldBe Some(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
              session.coherence shouldBe Vector.empty; residentafter shouldBe resident
              session.space.created shouldBe None; session.space.deleted shouldBe None
            }
          }
        }
      }
    }
    "satisfy the total native capture law" which {
      "PG-CMC-P1 execute at least 100 sequential shrinkable native samples with zero discards" in {
        Given("mandatory live PostgreSQL and total bounded Unicode/revision/body/identity/representation/decode samples")
        with_postgresql { database =>
          val environment = postgresql(database, true)
          val law = Prop.forAll(samples) { value =>
            Given("a fresh actual registered session, explicit fixed schema and complete independent creation oracle")
            val session = _session(database, environment, value); val before = session.rows(); val physical = environment.physical(); val residentbefore = session.resident
            val createexpected = expectedCreate(session, before, physical, true)
            When("the real new UoW create persists once and independent pools read the entire root/body state")
            val outcome = success(session.evaluateC(session.createOperation)); val aftercreate = session.rows(); val physicalcreate = environment.physical()
            val createresident = session.resident; val createviews = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
            Then("the complete logical/physical result and exact native/Space references satisfy the declared creation law")
            checkSchema(session, physical); checkCreated(outcome, session, createexpected.raw)
            checkRows(aftercreate, createexpected.rows); checkPhysical(physicalcreate, createexpected.physical)
            checkCreateResident(createresident, residentbefore, createexpected.raw, session)
            session.coherence shouldBe Vector("evict-view-1", "decode"); createviews shouldBe Vector(1, 1)
            if (value.mode == DecodeMode.Good) outcome.coherence shouldBe Consequence.unit
            else (conclusion(outcome.coherence) eq conclusion(session.decodeFailure)) shouldBe true
            Given("the finished creation and a separately prescribed old selected target deletion state")
            val expected = expectedDelete(session, createexpected.rows, createexpected.physical, true); session.arm(DecodeMode.Good)
            When("one actual new observed-delete operation acknowledges the old matched revision")
            val deleted = success(session.evaluateC(session.deleteOperation())); val after = session.rows(); val physicalafter = environment.physical()
            val deleteresident = session.resident; val deleteviews = Vector(success(session.view.resolve(session.id)), success(session.view.resolve(session.unrelatedId)))
            Then("complete selected/unrelated state and the exact deletion receipt satisfy the lifecycle law without enclosing commit")
            checkDeleted(deleted, session); checkRows(after, expected.rows); checkPhysical(physicalafter, expected.physical)
            checkDeleteResident(deleteresident, createresident); session.coherence shouldBe Vector("evict-view-1"); deleteviews shouldBe Vector(1, 1)
            true
          }
          When("the sequential native law runs without filters, implications, cancellation or skipping")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
          Then("native execution requires at least 100 successes and no discarded samples")
          result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
        }
      }
    }
  }
}
