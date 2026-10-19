package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.Root
import org.goldenport.cncf.datastore.PostgresqlAcceptance
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, StoredTable}
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlEntityVersionedMutationPortAcceptanceSpec extends AnyWordSpec
    with EntityVersionedMutationPortFixture.Vocabulary with PostgresqlAcceptance {
  import EntityVersionedMutationPortFixture.*
  import EntityVersionedMutationPortFixture.sampleShrink
  import EntityVersionedMutationOutcomeFixture.{Variant, Mode, ProjectionMode, Rows, success, revision,
    conclusion, reasons, structuredFailure, typedVariant, bodyStorage, bodyReference}

  "Native canonical versioned mutation producer ports" should {
    "retain actual raw persistence through all four Space ports" which {
      for { variant <- Variant.values.toVector;
        mode <- Vector(Mode.Current, Mode.Changed, Mode.StaleCurrent, Mode.StaleChanged) } {
        s"VP-E1/VP-E2 retain native $variant $mode with complete physical correspondence" in {
          Given("required PostgreSQL 17.11 with fsync and synchronous_commit and independent normalized owned pools")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true)
            val reader = database.open(normalizeColumns = true)
            val current = new Session(database.token("vp_native"), variant, writer)
            val sample = Sample("Native 日本 Alpha Ω", "native retained business", 11L, mode, variant)
            When("the typed sample invokes exactly one selected new Space producer port")
            val satisfied = checkSample(sample, current, reader, () => database.snapshotAll())
            Then("the real branch, raw reference, revision, projection and complete independent state correspond")
            satisfied shouldBe true
          }
        }
      }
      Variant.values.toVector.foreach { variant =>
        s"VP-E2 preserve native $variant current maximum NoOp without a successor" in {
          Given("a complete overflow-backed native root at Long.MAX_VALUE and an independent normalized reader")
          with_postgresql { database =>
            val current = new Session(database.token("vp_maximum"), variant, database.open(normalizeColumns = true))
            val reader = database.open(normalizeColumns = true)
            current.seed(initialRevision = Long.MaxValue)
            val observed = revision(Long.MaxValue)
            val entity = current.input("Before", observed)
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            current.arm()
            When("one new Space port receives current equal business values at the maximum revision")
            val outcome = success(current.invokeC(Route.Space, entity, Patch("Before"), Some(observed), policy(observed)))
            Then("the actual NoOp preserves every Field, body and physical row without requiring nextC")
            checkCounters(current)
            checkCaptured(outcome, current, before, false, Long.MaxValue)
            checkProjection(outcome.projection, variant, before, current.setup, Long.MaxValue)
            current.probe.admissions shouldBe (if (isTyped(variant)) 1 else 0)
            current.probe.decodes shouldBe (if (isTyped(variant)) 1 else 0)
            checkRows(current.rows(reader), before)
            checkPhysical(database.snapshotAll(), beforephysical)
          }
        }
      }
      "VP-E2 reject native embedded input mismatch before persistence" in {
        Given("complete native revision 2 and typed Embedded input 2 with supplied observation 1")
        with_postgresql { database =>
          val current = new Session(database.token("vp_mismatch"), Variant.TypedSnapshot,
            database.open(normalizeColumns = true))
          val reader = database.open(normalizeColumns = true)
          current.seed(initialRevision = 2L)
          val entity = current.input("Before", revision(2L))
          val before = current.rows(reader)
          val beforephysical = database.snapshotAll()
          current.arm()
          When("one new typed Space port interprets inconsistent embedded and expected revisions")
          val result = current.invokeC(Route.Space, entity, Patch("Before"), Some(revision(1L)), policy(revision(1L)))
          Then("the existing exact mismatch precedes the provider and all projection while complete state remains")
          checkConstructedSourceFailure(conclusion(result), conclusion(Consequence.argumentExpectedActualMismatch(
            current.setup.revisionField, revision(1L), revision(2L))), "StandardEntityRevisionPart.scala")
          checkCounters(current, 0)
          current.probe.admissions shouldBe 0
          current.probe.decodes shouldBe 0
          checkRows(current.rows(reader), before)
          checkPhysical(database.snapshotAll(), beforephysical)
        }
      }
      for { variant <- Vector(Variant.RecordSnapshot, Variant.RecordDetached); optimistic <- Vector(false, true) } {
        s"VP-E1 retain native actual $variant direct/CAS selection optimistic=$optimistic" in {
          Given("the real native capabilities and an AlwaysWrite typed title patch at the registered concurrency policy")
          with_postgresql { database =>
            val current = new Session(database.token("vp_primitive"), variant,
              database.open(normalizeColumns = true), optimistic = optimistic)
            val reader = database.open(normalizeColumns = true)
            current.seed()
            val entity = current.input("After", revision(1L))
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            checkNativeSqlSchemaAbsent(current, before, beforephysical)
            val attempt = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.None, EntityWritePolicy.AlwaysWrite,
              RevisionPreconditionPolicy.Managed, None)
            current.arm()
            When("one new Record Space port selects the existing native direct or CAS primitive")
            val outcome = success(current.invokeC(Route.Space, entity, Patch("After"), Some(revision(1L)), attempt))
            val after = current.rows(reader)
            Then("actual authoritative native readback is retained with one primitive and no generic fallback")
            checkCounters(current)
            current.recording.captured.get shouldBe a[Captured.Native]
            current.recording.plans.head match {
              case SelectedPlan.Direct(_) => optimistic shouldBe false
              case SelectedPlan.CompareAndSet(plan) =>
                optimistic shouldBe true
                plan.expectedRevision shouldBe revision(1L)
              case _ => fail("native-capable patch must select direct or CAS")
            }
            checkCaptured(outcome, current, after, true, 2L)
            checkProjection(outcome.projection, variant, after, current.setup, 2L)
            checkState(current, before, after, beforephysical, database.snapshotAll(), "After", true, 2L,
              nativeSqlSchema = Some(NativeSqlSchema.Postgresql))
            current.probe.admissions shouldBe 0
            current.probe.decodes shouldBe 0
          }
        }
      }
    }

    "capture native persistence before typed projection with separate legacy sessions" which {
      for { representation <- representations; changed <- Vector(false, true) } {
        s"VP-E3 retain native $representation projection and legacy semantics changed=$changed" in {
          ProjectionMode.values.toVector.foreach { mode =>
            Given(s"independently seeded new and legacy normalized native sessions with the same typed input and $mode probe")
            with_postgresql { database =>
              val writer = database.open(normalizeColumns = true)
              val reader = database.open(normalizeColumns = true)
              val variant = typedVariant(representation)
              val current = new Session(database.token("vp_projection"), variant, writer)
              val legacy = new Session(database.token("vp_legacy"), variant, writer)
              current.seed()
              legacy.seed()
              val title = if (changed) "After" else "Before"
              val entity = current.input(title, revision(1L))
              val oldentity = legacy.input(title, revision(1L))
              val before = current.rows(reader)
              val oldbefore = legacy.rows(reader)
              val beforephysical = database.snapshotAll()
              var capturedrows = Option.empty[Rows]
              var capturedphysical = Option.empty[Map[(String, String), StoredTable]]
              current.arm(mode, () => {
                capturedrows = Some(current.rows(reader))
                capturedphysical = Some(database.snapshotAll())
              })
              legacy.arm(mode)
              When("one new Space port captures its actual result before projection and the legacy mirror makes its separate attempt")
              val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), policy(revision(1L))))
              val after = current.rows(reader)
              val afterphysical = database.snapshotAll()
              val thrown = if (mode == ProjectionMode.Throw)
                Some(intercept[IllegalStateException] {
                  legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L)))
                })
              else if (mode == ProjectionMode.NullConsequence || mode == ProjectionMode.NullEntity)
                Some(intercept[NullPointerException] {
                  legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L)))
                })
              else None
              val oldresult = if (thrown.isEmpty)
                Some(legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L)))) else None
              val oldafter = legacy.rows(reader)
              Then("the original raw publication precedes the probe, contained failure retains it, and legacy throws or Conclusions stay exact")
              val version = if (changed) 2L else 1L
              checkRows(capturedrows.get, after)
              checkPhysical(capturedphysical.get, afterphysical)
              checkCaptured(outcome, current, after, changed, version)
              checkCounters(current)
              checkCounters(legacy)
              if (mode == ProjectionMode.Good) {
                checkProjection(outcome.projection, variant, after, current.setup, version)
                checkProjection(oldresult.get, variant, oldafter, legacy.setup, version)
              } else {
                checkProjectionFailure(outcome.projection, changed,
                  if (mode == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
                if (mode == ProjectionMode.StructuredFailure) {
                  checkProjectionFailure(oldresult.get, changed, Some(structuredFailure.conclusion))
                  checkConstructedFailure(conclusion(outcome.projection), conclusion(oldresult.get))
                  if (!changed) (conclusion(outcome.projection) eq structuredFailure.conclusion) shouldBe true
                }
                if (mode == ProjectionMode.Throw) thrown.get.getMessage shouldBe "private projection secret"
                if (mode == ProjectionMode.ForeignId) {
                  val projected = success(oldresult.get)
                  val root = if (variant == Variant.TypedSnapshot)
                    projected.asInstanceOf[EntitySnapshot[Root]].entity
                  else projected.asInstanceOf[EntityRevisionCarrier[Root]].entity
                  root.id shouldBe legacy.setup.unrelatedId
                }
                if (mode == ProjectionMode.WrongRevision) {
                  val wrong = if (version == 1L) 2L else 1L
                  checkLegacyWrongRevisionProjection(oldresult.get, variant, oldafter, legacy.setup, wrong)
                }
                conclusion(outcome.projection).show should not include "private projection secret"
              }
              val decodes = if (Set(ProjectionMode.Good, ProjectionMode.NullEntity,
                ProjectionMode.ForeignId, ProjectionMode.WrongRevision).contains(mode)) 1 else 0
              Vector(current, legacy).foreach { session =>
                session.probe.admissions shouldBe 1
                session.probe.decodes shouldBe decodes
              }
              checkState(current, before, after, beforephysical, afterphysical, title, changed, version)
              checkState(legacy, oldbefore, oldafter, Map.empty, Map.empty, title, changed, version)
              val first = if (changed) expectedChangedPhysical(beforephysical, current, title, version) else beforephysical
              val expected = if (changed) expectedChangedPhysical(first, legacy, title, version) else first
              checkPhysical(database.snapshotAll(), expected)
              checkRows(current.rows(reader), after)
              checkRows(legacy.rows(reader), oldafter)
            }
          }
        }
      }
    }

    "retain actual raw persistence after the owned native writer closes following ACK" which {
      for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
        s"VP-E4 retain native $variant changed=$changed after actual post-ACK close" in {
          Given("separate normalized factory-owned native writer and live reader with full overflow-backed seed")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true)
            val reader = database.open(normalizeColumns = true)
            val current = new Session(database.token("vp_closed"), variant, writer)
            current.seed()
            val title = if (changed) "After" else "Before"
            val entity = current.input(title, revision(1L))
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            var durable = Option.empty[Rows]
            var durablephysical = Option.empty[Map[(String, String), StoredTable]]
            current.arm(afterAck = () => {
              durable = Some(current.rows(reader))
              durablephysical = Some(database.snapshotAll())
              success(writer.closeC())
            })
            When("one new Space port receives actual known ACK and its observer closes the real writer before same-context hydration")
            val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), policy(revision(1L))))
            val after = current.rows(reader)
            val afterphysical = database.snapshotAll()
            Then("the exact System/Corrupted closed-resource failure preserves raw reference, one attempt and all durable data")
            bodyStorage(outcome.persisted.record) shouldBe Some("overflow")
            bodyReference(outcome.persisted.record).isDefined shouldBe true
            checkCaptured(outcome, current, after, changed, if (changed) 2L else 1L)
            checkClosedManagedReadFailure(outcome.projection, changed)
            checkCounters(current)
            current.probe.admissions shouldBe 0
            current.probe.decodes shouldBe 0
            checkRows(after, durable.get)
            checkPhysical(afterphysical, durablephysical.get)
            checkState(current, before, after, beforephysical, afterphysical, title, changed, if (changed) 2L else 1L)
            checkRows(current.rows(reader), after)
            checkPhysical(database.snapshotAll(), afterphysical)
          }
        }
      }
    }

    "respect real native commit acknowledgment before outcome construction" which {
      for { variant <- Variant.values.toVector;
        mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject,
          AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
        s"VP-E5 retain native $variant $mode actual acknowledgment without replay" in {
          Given("the unchanged native ACK writer and independent reader both using their default false normalization")
          with_postgresql { database =>
            val writer = database.openAcknowledgement()
            val reader = database.open()
            val current = new Session(database.token("vp_ack"), variant, writer)
            current.seed()
            val entity = current.input("After", revision(1L))
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            current.arm()
            writer.arm(mode)
            When("one new Space producer port receives the real provider's selected commit acknowledgment")
            val result = current.invokeC(Route.Space, entity, Patch("After"), Some(revision(1L)), policy(revision(1L)))
            val after = current.rows(reader)
            val afterphysical = database.snapshotAll()
            Then("known rejection preserves all state and real committed unknown ACK yields no outcome or projection")
            checkCounters(current)
            writer.commitAttempts shouldBe 1
            if (mode == AcknowledgementMode.Normal) {
              val outcome = success(result)
              checkCaptured(outcome, current, after, true, 2L)
              checkProjection(outcome.projection, variant, after, current.setup, 2L)
              current.probe.admissions shouldBe (if (isTyped(variant)) 1 else 0)
              current.probe.decodes shouldBe (if (isTyped(variant)) 1 else 0)
            } else {
              result shouldBe a[Consequence.Failure[?]]
              current.recording.captured shouldBe None
              reasons(result) should contain(
                if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
              current.probe.admissions shouldBe 0
              current.probe.decodes shouldBe 0
            }
            checkState(current, before, after, beforephysical, afterphysical, "After",
              mode != AcknowledgementMode.Reject, if (mode == AcknowledgementMode.Reject) 1L else 2L)
            checkRows(current.rows(reader), after)
            checkPhysical(database.snapshotAll(), afterphysical)
          }
        }
      }
    }

    "retain canonical effective policy selection and separate legacy parity" which {
      for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
        s"VP-E7 retain native $variant raw None attempt under assembled Optimistic changed=$changed" in {
          Given("separate native new/legacy Optimistic collections with a raw None WriteIfChanged ObservedRequired attempt")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = true)
            val reader = database.open(normalizeColumns = true)
            val current = new Session(database.token("vp_effective"), variant, writer)
            val legacy = new Session(database.token("vp_effective_legacy"), variant, writer)
            current.seed()
            legacy.seed()
            val title = if (changed) "After" else "Before"
            val entity = current.input(title, revision(1L))
            val oldentity = legacy.input(title, revision(1L))
            val before = current.rows(reader)
            val oldbefore = legacy.rows(reader)
            val beforephysical = database.snapshotAll()
            val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None)
            current.arm()
            legacy.arm()
            When("one new Space port and its independently seeded legacy mirror select effective Optimistic before validation")
            val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), attempt))
            val oldresult = legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), attempt)
            val after = current.rows(reader)
            val oldafter = legacy.rows(reader)
            Then("the real guarded plans, revision and full raw/projected/storage state retain canonical parity")
            val version = if (changed) 2L else 1L
            Vector(current, legacy).foreach { session =>
              checkCounters(session)
              session.recording.plans.head match {
                case SelectedPlan.Versioned(plan) =>
                  plan.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
                  plan.writePolicy shouldBe EntityWritePolicy.WriteIfChanged
                  plan.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
                  plan.expectedRevision shouldBe Some(revision(1L))
                case _ => fail("WriteIfChanged must retain the guarded canonical primitive")
              }
              session.probe.admissions shouldBe (if (isTyped(variant)) 1 else 0)
              session.probe.decodes shouldBe (if (isTyped(variant)) 1 else 0)
            }
            checkCaptured(outcome, current, after, changed, version)
            checkProjection(outcome.projection, variant, after, current.setup, version)
            checkProjection(oldresult, variant, oldafter, legacy.setup, version)
            checkState(current, before, after, Map.empty, Map.empty, title, changed, version)
            checkState(legacy, oldbefore, oldafter, Map.empty, Map.empty, title, changed, version)
            val first = if (changed) expectedChangedPhysical(beforephysical, current, title, version) else beforephysical
            val expected = if (changed) expectedChangedPhysical(first, legacy, title, version) else first
            checkPhysical(database.snapshotAll(), expected)
            checkRows(current.rows(reader), after)
            checkRows(legacy.rows(reader), oldafter)
          }
        }
      }
      for { variant <- Variant.values.toVector; missing <- Vector(false, true) } {
        s"VP-E7 reject native $variant invalid effective policy missingObservation=$missing" in {
          Given("a native typed input with either effective None+ObservedRequired or absent required observation")
          with_postgresql { database =>
            val current = new Session(database.token("vp_policy_reject"), variant,
              database.open(normalizeColumns = true), optimistic = missing)
            val reader = database.open(normalizeColumns = true)
            current.seed()
            val entity = current.input("After", revision(1L))
            val before = current.rows(reader)
            val beforephysical = database.snapshotAll()
            val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None,
              observedRevision = if (missing) None else Some(revision(1L)))
            current.arm()
            When("one new Space port reaches the existing effective semantic validation")
            val result = current.invokeC(Route.Space, entity, Patch("After"), Some(revision(1L)), attempt)
            Then("the exact canonical policy failure precedes writes and projection and preserves every physical row")
            val expected = if (missing) Consequence.argumentMissing("observedRevision")
              else Consequence.configurationInvalid("Entity concurrency policy None cannot require an observed revision")
            checkConstructedSourceFailure(conclusion(result), conclusion(expected), "EntityMutationPolicy.scala")
            checkCounters(current, 0)
            current.probe.admissions shouldBe 0
            current.probe.decodes shouldBe 0
            checkRows(current.rows(reader), before)
            checkPhysical(database.snapshotAll(), beforephysical)
          }
        }
      }
    }

    "satisfy the sequential total native law over bounded reused rows and pools" which {
      "VP-P1 retain at least one hundred actual native Space outcomes with zero discards" in {
        Given("all four ports, nonempty ASCII/Japanese/Greek values, revisions 2..50 and reducing text/revision/mode shrinking")
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true)
          val reader = database.open(normalizeColumns = true)
          val sessions = Variant.values.toVector.map(variant =>
            variant -> new Session(database.token("vp_law"), variant, writer)).toMap
          val property = Prop.forAll(samples) { sample =>
            checkSample(sample, sessions(sample.variant), reader, () => database.snapshotAll())
          }
          When("the total property executes sequentially with one actual new Space producer action per sample")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("required native execution passes at least one hundred samples and discards none")
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
