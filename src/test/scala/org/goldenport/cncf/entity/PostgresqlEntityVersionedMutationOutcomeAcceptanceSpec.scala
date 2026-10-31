package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{EntityVersionedMutationResult, PostgresqlAcceptance}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.AcknowledgementMode
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlEntityVersionedMutationOutcomeAcceptanceSpec extends AnyWordSpec
    with EntityVersionedMutationOutcomeFixture.Vocabulary with PostgresqlAcceptance {
  import EntityVersionedMutationOutcomeFixture.*
  import EntityVersionedMutationOutcomeFixture.sampleShrink

  "Native canonical versioned provider outcome" should {
    "capture actual PostgreSQL persistence and both lawful projections per representation" which {
      for { representation <- representations; mode <- Mode.values.toVector } {
        s"VO-N1 retain complete native $representation $mode outcome and physical state" in {
          Given("required PostgreSQL 17.11 with fsync and synchronous_commit plus independent owned provider pools")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true)
            val reader = database.open(normalizeColumns = true)
            val current = session(database.token("vo_native"), representation, writer)
            val sample = Sample("Native 日本 Alpha Ω", "native retained security/audit", 11L, mode, representation)
            When("the native sample scenario interprets one real canonical provider result and both lawful projections")
            val satisfied = checkSample(sample, current, reader, () => database.snapshotAll())
            Then("the complete sample's independent raw, projection and physical contract is satisfied")
            satisfied shouldBe true
          }
        }
      }
      representations.foreach { representation =>
        s"VO-N1 retain native current maximum $representation NoOp without nextC" in {
          Given("a complete native root at Long.MAX_VALUE and a separate live reader")
          with_postgresql { database =>
            val current = session(database.token("vo_maximum"), representation, database.open(normalizeColumns = true))
            val reader = database.open(normalizeColumns = true)
            current.seed(initialRevision = Long.MaxValue)
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            When("one actual canonical versioned provider receives equal business input at the current maximum revision")
            val supplied = success(current.mutate("Before", revision(Long.MaxValue)))
            val outcomes = variants(representation).map(variant => variant -> success(current.outcome(variant, supplied)))
            Then("the native NoOp retains full raw and projected values without requiring a successor or additional write")
            isNoOp(supplied) shouldBe true
            outcomes.foreach { case (variant, outcome) =>
              checkRaw(outcome, supplied, before.selected.get, current.setup, false, Long.MaxValue)
              checkProjection(outcome.projection, variant, before, current.setup, Long.MaxValue)
            }
            current.providerAttempts shouldBe 1
            checkRows(current.rows(reader), before)
            checkPhysical(database.snapshotAll(), beforephysical)
          }
        }
      }
    }

    "retain native raw results through pure postprovider domain faults and legacy parity" which {
      for { representation <- representations; changed <- Vector(false, true) } {
        s"VO-N1/VO-E3/VO-E6 retain typed native $representation faults changed=$changed without replay" in {
          ProjectionMode.values.toVector.foreach { mode =>
            Given(s"one actual acknowledged native result before arming the $mode projection probe")
            with_postgresql { database =>
              val current = session(database.token("vo_projection"), representation, database.open(normalizeColumns = true))
              val reader = database.open(normalizeColumns = true)
              current.seed()
              val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
              val rows = current.rows(reader)
              val physical = database.snapshotAll()
              current.probe.arm(mode)
              When("the typed helper consumes the acknowledged native raw result without another provider attempt")
              val outcome = success(current.outcome(typedVariant(representation), supplied))
              val legacy = if (mode == ProjectionMode.Good || mode == ProjectionMode.StructuredFailure)
                Some(current.legacy(typedVariant(representation), supplied)) else None
              val thrown = if (mode == ProjectionMode.Throw)
                Some(intercept[IllegalStateException] { current.legacy(typedVariant(representation), supplied) }) else None
              Then("the complete raw outcome survives and the independently prescribed legacy success, failure or throw remains intact")
              checkRaw(outcome, supplied, rows.selected.get, current.setup, changed, if (changed) 2L else 1L)
              if (mode == ProjectionMode.Good) {
                checkProjection(outcome.projection, typedVariant(representation), rows, current.setup, if (changed) 2L else 1L)
                checkProjection(legacy.get, typedVariant(representation), rows, current.setup, if (changed) 2L else 1L)
              } else {
                checkProjectionFailure(outcome.projection, changed,
                  if (mode == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
                legacy.foreach(value => checkProjectionFailure(value, changed, Some(structuredFailure.conclusion)))
                thrown.foreach(value => value.getMessage shouldBe "private projection secret")
                conclusion(outcome.projection).show should not include "private projection secret"
              }
              val attempts = if (legacy.isDefined || thrown.isDefined) 2 else 1
              current.probe.admissions shouldBe attempts
              current.probe.decodes shouldBe (if (Set(ProjectionMode.Good, ProjectionMode.NullEntity,
                ProjectionMode.ForeignId, ProjectionMode.WrongRevision).contains(mode)) attempts else 0)
              current.providerAttempts shouldBe 1
              checkRows(current.rows(reader), rows)
              checkPhysical(database.snapshotAll(), physical)
            }
          }
        }
      }
    }

    "distinguish native body absence from genuine failed managed reading" which {
      for { representation <- representations; changed <- Vector(false, true) } {
        s"VO-E4 retain native MissingBody success for $representation changed=$changed" in {
          Given("one actual acknowledged native overflow result followed by deletion of only its selected owned body row")
          with_postgresql { database =>
            val current = session(database.token("vo_missing"), representation, database.open(normalizeColumns = true))
            val reader = database.open(normalizeColumns = true)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            bodyStorage(raw(supplied)) shouldBe Some("overflow")
            val durable = current.rows(reader)
            val durablephysical = database.snapshotAll()
            current.deleteSelectedBody()
            val postfault = current.rows(reader)
            val postfaultphysical = database.snapshotAll()
            When("both lawful new and legacy variants consume the original native raw result after actual body absence")
            val outcomes = variants(representation).map { variant =>
              (variant, success(current.outcome(variant, supplied)), current.legacy(variant, supplied))
            }
            Then("the source-defined unchanged-raw fallback succeeds without changing any postfault physical state")
            postfault.body.isDefined shouldBe false
            checkRows(postfault, durable.copy(body = None))
            checkPhysical(postfaultphysical, withoutSelectedBodyPhysical(durablephysical, current.setup))
            outcomes.foreach { case (variant, outcome, legacy) =>
              checkRaw(outcome, supplied, postfault.selected.get, current.setup, changed, if (changed) 2L else 1L)
              checkProjection(outcome.projection, variant, postfault, current.setup, if (changed) 2L else 1L)
              checkProjection(legacy, variant, postfault, current.setup, if (changed) 2L else 1L)
            }
            current.providerAttempts shouldBe 1
            checkRows(current.rows(reader), postfault)
            checkPhysical(database.snapshotAll(), postfaultphysical)
          }
        }
        s"VO-N3 retain native raw $representation changed=$changed when the owned writer closes after ACK" in {
          Given("separate factory-owned native writer and live independent reader with complete post-ACK state")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true)
            val reader = database.open(normalizeColumns = true)
            val current = session(database.token("vo_closed"), representation, writer)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            val durable = current.rows(reader)
            val durablephysical = database.snapshotAll()
            bodyStorage(raw(supplied)) shouldBe Some("overflow")
            bodyReference(raw(supplied)).isDefined shouldBe true
            success(writer.closeC())
            val postfault = current.rows(reader)
            val postfaultphysical = database.snapshotAll()
            When("each lawful variant hydrates using the same original context whose owned writer has actually closed")
            val outcomes = variants(representation).map { variant =>
              (variant, success(current.outcome(variant, supplied)), current.legacy(variant, supplied))
            }
            Then("the real managed-borrow failure becomes projection Failure while raw persistence and all physical data remain exact")
            checkRows(postfault, durable)
            checkPhysical(postfaultphysical, durablephysical)
            outcomes.foreach { case (_, outcome, legacy) =>
              checkRaw(outcome, supplied, durable.selected.get, current.setup, changed, if (changed) 2L else 1L)
              checkClosedManagedReadFailure(outcome.projection, changed)
              checkClosedManagedReadFailure(legacy, changed)
              conclusion(outcome.projection).observation.taxonomy shouldBe conclusion(legacy).observation.taxonomy
              reasons(outcome.projection) shouldBe reasons(legacy)
              if (!changed) reasons(outcome.projection) should not contain "committed-entity-projection-failure"
            }
            current.probe.admissions shouldBe 0
            current.probe.decodes shouldBe 0
            current.providerAttempts shouldBe 1
            checkRows(current.rows(reader), postfault)
            checkPhysical(database.snapshotAll(), postfaultphysical)
          }
        }
      }
    }

    "respect the real transaction acknowledgment boundary" which {
      for { representation <- representations; mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject,
        AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
        s"VO-N2 preserve actual native $representation $mode acknowledgment without an outcome on unknown ACK" in {
          Given("the unchanged actual acknowledgment fixture and complete independent native root, body and physical baseline")
          with_postgresql { database =>
            val writer = database.openAcknowledgement()
            val reader = database.open()
            val current = session(database.token("vo_ack"), representation, writer)
            current.seed()
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            writer.arm(mode)
            current.probe.arm(ProjectionMode.Good)
            When("one real versioned provider attempt flatMaps its acknowledged success into outcome consumption")
            val result = current.mutate("After", revision(1L)).flatMap { supplied =>
              current.outcome(typedVariant(representation), supplied)
            }
            val after = current.rows(reader)
            val afterphysical = database.snapshotAll()
            Then("known rejection preserves all rows while real committed unknown ACK has durable changes and no projection or outcome")
            current.providerAttempts shouldBe 1
            writer.commitAttempts shouldBe 1
            if (mode == AcknowledgementMode.Normal) {
              val outcome = success(result)
              outcome.applied shouldBe true
              checkRecord(outcome.persisted.record, after.selected.get)
              outcome.persisted.revision.value shouldBe 2L
              checkProjection(outcome.projection, typedVariant(representation), after, current.setup, 2L)
              current.probe.admissions shouldBe 1
              current.probe.decodes shouldBe 1
            } else {
              result shouldBe a[Consequence.Failure[?]]
              reasons(result) should contain(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
              current.probe.admissions shouldBe 0
              current.probe.decodes shouldBe 0
            }
            if (mode == AcknowledgementMode.Reject) {
              checkRows(after, before)
              checkPhysical(afterphysical, beforephysical)
            } else {
              checkChanged(before, after, current.setup, "After", 2L)
              checkPhysical(afterphysical, changedPhysical(beforephysical, current.setup, "After", 2L))
            }
            checkRows(current.rows(reader), after)
            checkPhysical(database.snapshotAll(), afterphysical)
          }
        }
      }
    }

    "satisfy the sequential total native law" which {
      "VO-NP1 retain at least one hundred native outcomes across both representations with zero discards" in {
        Given("bounded nonempty multilingual samples, varied positive revisions and meaningful strictly reducing shrinks")
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true)
          val reader = database.open(normalizeColumns = true)
          val sessions = representations.map(representation => representation ->
            session(database.token("vo_law"), representation, writer)).toMap
          val property = Prop.forAll(samples) { sample =>
            checkSample(sample, sessions(sample.representation), reader, () => database.snapshotAll())
          }
          When("ScalaCheck executes the deferred total law sequentially, reseeding only bounded shared rows and pools")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("actual native execution passes with at least one hundred successes and zero discards")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }
}
