package org.goldenport.cncf.unitofwork

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{PostgresqlAcceptance as RequiredPostgresqlAcceptance}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.AcknowledgementMode
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityVersionedMutationOutcomeFixture.{Variant, Mode, ProjectionMode, success, revision, conclusion}
import org.goldenport.cncf.entity.EntityVersionedMutationPortFixture.{Patch, Sample, SelectedPlan}
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec extends AnyWordSpec
    with UnitOfWorkVersionedOutcomeFixture.Vocabulary with RequiredPostgresqlAcceptance {
  import UnitOfWorkVersionedOutcomeFixture.*
  import UnitOfWorkVersionedOutcomeFixture.given

  "Required PostgreSQL active UnitOfWork versioned capture" should {
    "retain real native persistence independently of projection and coherence" which {
      "UVO-01/UVO-02/UVO-04 retain all default native branches and actual Realm failures" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = Variant.values.toVector.map(variant => variant -> new Session(database.token("uvo_branch"), variant, writer)).toMap
          for { variant <- Variant.values.toVector; mode <- Mode.values.toVector
            if !Set(Mode.FailureCurrent, Mode.FailureChanged).contains(mode) || typed(variant)
            fault <- Fault.values.toVector } {
            Given("required PostgreSQL 17.11 durability and a lawful declared input/coherence dimension")
            val sample = LawSample(Sample("Native 日本 Ω", "native retained business", 11L, mode, variant), fault, DecodeFault.Good)
            When("the complete scenario evaluates one actual node in its active native UnitOfWork")
            val satisfied = checkLaw(sample, sessions(variant), reader, () => database.snapshotAll(), "postgresql")
            Then("complete logical/physical/reference/projection/coherence correspondence retains zero enclosing lifecycle")
            satisfied shouldBe true
          }
        }
      }
      "UVO-02 preserve every current native maximum revision as NoOp" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          Variant.values.toVector.foreach { variant =>
            Given("a complete native root at Long.MAX_VALUE and independently prepared equal input")
            val current = new Session(database.token("uvo_maximum"), variant, writer)
            current.seed(initialRevision = Long.MaxValue)
            val entity = current.source.input("Before", revision(Long.MaxValue))
            val before = current.source.rows(reader); val beforephysical = database.snapshotAll(); val resident = current.resident
            val wanted = expected(current, before, beforephysical, "Before", false, Long.MaxValue, "postgresql")
            current.arm()
            When("one canonical native node interprets the current maximum observation")
            val capture = success(current.evaluateC(current.operation(entity, Patch("Before"), Some(revision(Long.MaxValue)), policy(revision(Long.MaxValue)))))
            Then("NoOp captures the original full persisted Record and projection without a successor or enclosing commit")
            checkPrimitive(current, Primitive.Versioned); checkCapture(capture, current, wanted, false, Long.MaxValue)
            checkProjection(capture.mutation.projection, variant, wanted.rows, current.setup, Long.MaxValue)
            checkCoherence(current, capture, resident, Fault.None, true)
            checkRows(current.source.rows(reader), wanted.rows); checkPhysical(database.snapshotAll(), wanted.physical)
          }
        }
      }
      "UVO-02 reject native inconsistent Embedded input before the provider" in {
        Given("actual native revision two, Embedded input two and supplied observation one")
        with_postgresql { database =>
          val current = new Session(database.token("uvo_mismatch"), Variant.TypedSnapshot, database.open(normalizeColumns = true))
          val reader = database.open(normalizeColumns = true)
          current.seed(initialRevision = 2L)
          val entity = current.source.input("Before", revision(2L))
          val before = current.source.rows(reader); val beforephysical = database.snapshotAll(); val resident = current.resident
          current.arm()
          When("the typed native node enters the existing embedded expected-revision boundary")
          val result = current.evaluateC(current.operation(entity, Patch("Before"), Some(revision(1L)), policy(revision(1L))))
          Then("the complete existing mismatch is retained before all provider and coherence effects")
          checkSourceFailure(conclusion(result), conclusion(Consequence.argumentExpectedActualMismatch(
            current.setup.revisionField, revision(1L), revision(2L))), "StandardEntityRevisionPart.scala")
          checkPrimitive(current, Primitive.Versioned, 0)
          current.space.outcome shouldBe None; current.probe.admissions shouldBe 0
          current.resident shouldBe resident; checkRows(current.source.rows(reader), before); checkPhysical(database.snapshotAll(), beforephysical)
        }
      }
      "UVO-03 retain every native typed projection mode with independent legacy active owners" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          for { variant <- Vector(Variant.TypedSnapshot, Variant.TypedDetached); changed <- Vector(false, true)
            mode <- ProjectionMode.values.toVector } {
            val current = new Session(database.token("uvo_projection"), variant, writer)
            val legacy = new Session(database.token("uvo_legacy"), variant, writer)
            checkTypedPair(current, legacy, mode, changed, reader, () => database.snapshotAll(), "postgresql")
          }
        }
      }
      "UVO-04 exercise all six actual registered native record decoder modes and eviction precedence" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = Vector(Variant.RecordSnapshot, Variant.RecordDetached).map(variant =>
            variant -> new Session(database.token("uvo_decode"), variant, writer)).toMap
          for { variant <- sessions.keys.toVector; mode <- DecodeFault.values.toVector; changed <- Vector(false, true); fault <- Fault.values.toVector } {
            Given("an actual native record route with declared decoder and Realm faults independently of projection")
            val sample = LawSample(Sample("Native record 日本", "retained decoder baseline", 7L,
              if (changed) Mode.Changed else Mode.Current, variant), fault, mode)
            When("the actual active UoW projects its canonical Record then runs the registered residency decoder")
            val satisfied = checkLaw(sample, sessions(variant), reader, () => database.snapshotAll(), "postgresql")
            Then("decoder or earlier Realm failure remains separate from exact acknowledged persistence and successful projection")
            satisfied shouldBe true
          }
        }
      }
      "UVO-03 retain all four native raw outcomes after their owned writer closes following ACK" in {
        for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
            val current = new Session(database.token("uvo_closed"), variant, writer)
            checkClosed(current, reader, () => database.snapshotAll(), "postgresql", changed, () => { success(writer.closeC()); () })
          }
        }
      }
      "UVO-06 follow explicitly declared native Direct and CAS patch policies" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          for { variant <- Vector(Variant.RecordSnapshot, Variant.RecordDetached); optimistic <- Vector(false, true) } {
            val current = new Session(database.token("uvo_primitive"), variant, writer, optimistic = optimistic)
            checkNative(current, reader, () => database.snapshotAll(), "postgresql", optimistic)
          }
        }
      }
      "UVO-05 preserve native raw None and effective Optimistic parity with separate legacy UoWs" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
            Given("separate fresh native current and legacy sessions under registered Optimistic concurrency")
            val current = new Session(database.token("uvo_effective"), variant, writer)
            val legacy = new Session(database.token("uvo_effective_legacy"), variant, writer)
            current.seed(); legacy.seed()
            val title = if (changed) "After" else "Before"; val version = if (changed) 2L else 1L
            val entity = current.source.input(title, revision(1L)); val oldentity = legacy.source.input(title, revision(1L))
            val before = current.source.rows(reader); val oldbefore = legacy.source.rows(reader); val beforephysical = database.snapshotAll()
            val wanted = expected(current, before, beforephysical, title, changed, version, "postgresql")
            val oldwanted = expected(legacy, oldbefore, wanted.physical, title, changed, version, "postgresql")
            val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None)
            current.arm(); legacy.arm()
            When("both actual node owners assemble effective policy before semantic validation")
            val capture = success(current.evaluateC(current.operation(entity, Patch(title), Some(revision(1L)), attempt)))
            val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch(title), Some(revision(1L)), attempt))
            Then("the native plans, complete independent state and canonical projections retain the same effective policy")
            Vector(current, legacy).foreach { session =>
              checkPrimitive(session, Primitive.Versioned)
              session.source.recording.plans.head match {
                case SelectedPlan.Versioned(plan) =>
                  plan.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
                  plan.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
                  plan.expectedRevision shouldBe Some(revision(1L))
                case _ => fail("strict WriteIfChanged requires the canonical versioned primitive")
              }
            }
            checkCapture(capture, current, wanted, changed, version)
            checkProjection(capture.mutation.projection, variant, wanted.rows, current.setup, version)
            checkProjection(oldresult, variant, oldwanted.rows, legacy.setup, version)
            checkRows(current.source.rows(reader), wanted.rows); checkRows(legacy.source.rows(reader), oldwanted.rows)
            checkPhysical(database.snapshotAll(), oldwanted.physical)
          }
        }
      }
      "UVO-07 distinguish Normal, Reject and real committed Indeterminate/Throw/Null acknowledgements" in {
        for { variant <- Variant.values.toVector; mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject,
          AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          with_postgresql { database =>
            val writer = database.openAcknowledgement(); val reader = database.open()
            val current = new Session(database.token("uvo_ack"), variant, writer)
            checkAcknowledgement(current, writer, reader, () => database.snapshotAll(), "postgresql", mode)
          }
        }
      }
    }

    "satisfy the total native law with bounded reused rows and pools" which {
      "UVO-P1 require at least one hundred native UoW samples and zero discards" in {
        Given("lawful typed-failure generation, all four variants, independent finite coherence/decoder faults and meaningful shrinking")
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = Variant.values.toVector.map(variant => variant -> new Session(database.token("uvo_law"), variant, writer)).toMap
          val property = Prop.forAll(samples)(sample => checkLaw(sample, sessions(sample.input.variant), reader, () => database.snapshotAll(), "postgresql"))
          When("the complete real-native-UoW property executes sequentially on bounded roots/pools")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("required execution must pass at least one hundred successful samples without a discarded input")
          withClue(checked.toString) { checked.passed shouldBe true; checked.succeeded should be >= 100; checked.discarded shouldBe 0 }
        }
      }
    }
  }
}
