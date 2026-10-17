package org.goldenport.cncf.entity

import java.util.concurrent.atomic.AtomicInteger
import scala.compiletime.testing.typeCheckErrors
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedMutationResult}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class EntityVersionedMutationOutcomeSpec extends AnyWordSpec with EntityVersionedMutationOutcomeFixture.Vocabulary {
  import EntityVersionedMutationOutcomeFixture.*
  import EntityVersionedMutationOutcomeFixture.sampleShrink

  private val _tokens = new AtomicInteger()
  private def _token(): String = s"vo_${_tokens.incrementAndGet()}"

  "Canonical versioned provider outcome" should {
    "retain complete raw persistence independently of the four lawful projections" which {
      for { backend <- backends; representation <- representations; mode <- Mode.values.toVector } {
        s"VO-E1/VO-E2 retain $representation $mode on actual $backend" in {
          Given("the existing canonical fixture and actual selected backend with an independent reader")
          withBackend(backend) { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            val sample = Sample("日本 Alpha Ω", "full retained business", 7L, mode, representation)
            When("the complete sample scenario interprets one actual canonical provider result and its lawful projections")
            val satisfied = checkSample(sample, current, reader, physical)
            Then("the sample's independently prescribed full outcome and persistence contract is satisfied")
            satisfied shouldBe true
          }
        }
      }
      for { backend <- backends; representation <- representations } {
        s"VO-E2 retain current maximum $representation revision without a successor on $backend" in {
          Given("a current Long.MAX_VALUE root and full selected, body, NULL, audit and unrelated state")
          withBackend(backend) { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            current.seed(initialRevision = Long.MaxValue)
            val before = current.rows(reader)
            val beforephysical = physical()
            When("one actual provider receives equal business data at the exact maximum observation")
            val supplied = success(current.mutate("Before", revision(Long.MaxValue)))
            val outcomes = variants(representation).map(variant => variant -> success(current.outcome(variant, supplied)))
            Then("NoOp preserves the complete raw root and each lawful projection without advancing revision or replay")
            isNoOp(supplied) shouldBe true
            current.providerAttempts shouldBe 1
            outcomes.foreach { case (variant, outcome) =>
              checkRaw(outcome, supplied, before.selected.get, current.setup, false, Long.MaxValue)
              checkProjection(outcome.projection, variant, before, current.setup, Long.MaxValue)
            }
            checkRows(current.rows(reader), before)
            checkPhysical(physical(), beforephysical)
          }
        }
      }
    }

    "retain valid provider data through postprovider domain faults" which {
      for { backend <- backends; representation <- representations; changed <- Vector(false, true) } {
        s"VO-E3 contain every typed $representation projection fault after changed=$changed on $backend" in {
          ProjectionMode.values.toVector.filterNot(_ == ProjectionMode.Good).foreach { mode =>
            Given(s"one actual acknowledged provider result before the pure $mode probe is armed")
            withBackend(backend) { (writer, reader, physical) =>
              val current = session(_token(), representation, writer)
              current.seed()
              val before = current.rows(reader)
              val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
              val after = current.rows(reader)
              val afterphysical = physical()
              current.probe.arm(mode)
              When("the typed projection consumes the same raw result once without another mutation")
              val outcome = success(current.outcome(typedVariant(representation), supplied))
              Then("projection failure retains every raw Field and the exact revision and branch without further storage effects")
              checkRaw(outcome, supplied, after.selected.get, current.setup, changed, if (changed) 2L else 1L)
              checkProjectionFailure(outcome.projection, changed,
                if (mode == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
              current.probe.admissions shouldBe 1
              current.probe.decodes shouldBe (if (Set(ProjectionMode.NullEntity, ProjectionMode.ForeignId,
                ProjectionMode.WrongRevision).contains(mode)) 1 else 0)
              current.providerAttempts shouldBe 1
              if (mode != ProjectionMode.StructuredFailure) {
                if (!changed) reasons(outcome.projection) should contain("entity-mutation-projection-invalid")
                conclusion(outcome.projection).show should not include "private projection secret"
              }
              if (changed) checkChanged(before, after, current.setup, "After", 2L)
              else checkRows(after, before)
              checkRows(current.rows(reader), after)
              checkPhysical(physical(), afterphysical)
            }
          }
        }
      }
    }

    "distinguish an absent body from a failed managed SQL read" which {
      for { backend <- backends; representation <- representations; changed <- Vector(false, true) } {
        s"VO-E4 keep successful MissingBody raw fallback for $representation changed=$changed on $backend" in {
          Given("an actual acknowledged Applied or NoOp with an overflow marker before deletion of only the owned selected body")
          withBackend(backend) { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            raw(supplied).getString("content_storage") shouldBe Some("overflow")
            val durable = current.rows(reader)
            val durablephysical = physical()
            current.deleteSelectedBody()
            val postfault = current.rows(reader)
            val postfaultphysical = physical()
            When("all lawful helpers and their legacy counterparts consume the original raw result after body absence")
            val outcomes = variants(representation).map { variant =>
              (variant, success(current.outcome(variant, supplied)), current.legacy(variant, supplied))
            }
            Then("the existing hydration fallback succeeds with the unchanged raw data and no mutation replay")
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
            checkPhysical(physical(), postfaultphysical)
          }
        }
      }
      for { representation <- representations; changed <- Vector(false, true) } {
        s"VO-N3 retain raw $representation changed=$changed after closing the owned SQLite writer" in {
          Given("separate factory-owned live SQLite writer and reader over only one owned database file")
          withBackend("sqlite") { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            val durable = current.rows(reader)
            val durablephysical = physical()
            raw(supplied).getString("content_storage") shouldBe Some("overflow")
            raw(supplied).getString("content_ref").isDefined shouldBe true
            success(writer.asInstanceOf[SqlDataStore].closeC())
            val postfault = current.rows(reader)
            val postfaultphysical = physical()
            When("all lawful helpers read the selected overflow through the unchanged now-closed writer context")
            val outcomes = variants(representation).map { variant =>
              (variant, success(current.outcome(variant, supplied)), current.legacy(variant, supplied))
            }
            Then("a genuine structured hydration Failure retains the durable raw result without replay or data change")
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
            checkPhysical(physical(), postfaultphysical)
          }
        }
      }
    }

    "admit metadata and contain only the optional projection boundary" which {
      "VO-E5 reject tampered raw identities and revisions before any callback or effect" in {
        representations.foreach { representation =>
          Given("a complete independently read Record obtained from one actual memory provider Applied result")
          val current = session(_token(), representation, new org.goldenport.cncf.action.AggregateObservedSnapshotFixture.MemoryStore())
          current.seed()
          val supplied = success(current.mutate("After", revision(1L)))
          val observed = current.rows(current.setup.provider)
          val record = observed.selected.get
          val idfield = record.fields.find(_.key == "id").get
          val invalidrecords = Vector[Record](null, Record(null), Record(Vector(null)),
            Record(record.fields.filterNot(_.key == "id")),
            Record(record.fields :+ idfield),
            record.upsertSingle("id", current.setup.unrelatedId.value),
            Record(record.fields.map(field => if (field.key == "id") Field("id", Field.Value.Single(null)) else field)),
            Record(record.fields.map(field => if (field.key == "id") Field("id", null) else field)),
            record.upsertSingle("id", Vector(current.setup.id.value)),
            Record(record.fields.filterNot(_.key == current.setup.revisionField)),
            record.upsertSingle(current.setup.revisionField, "private malformed revision"),
            record.upsertSingle(current.setup.revisionField, 0L),
            Record(record.fields.map(field => if (field.key == current.setup.revisionField)
              Field(field.key, Field.Value.Single(null)) else field)))
          var callbacks = 0
          When("pure negative admission consumes tampered copies without claiming provider acceptance")
          val results = invalidrecords.map { invalid =>
            EntityVersionedMutationOutcome._from_provider_c(current.setup.id,
              EntityVersionedMutationResult.Applied(invalid), current.binding) { (_, _) =>
              callbacks += 1; Consequence.success("unreachable")
            }
          }
          Then("each invalid input yields a fixed redacted failure with no outcome, callback or storage effect")
          results.foreach { value =>
            reasons(value) shouldBe Vector("entity-mutation-outcome-invalid")
            conclusion(value).observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
            conclusion(value).show should not include "private malformed revision"
          }
          callbacks shouldBe 0
          current.providerAttempts shouldBe 1
          checkRows(current.rows(current.setup.provider), observed)
        }
      }
      "VO-E5 reject nullable input shapes without projection" in {
        Given("one actual current NoOp and a callback counting pure factory invocations")
        val current = session(_token(), EntityRevisionRepresentation.Embedded,
          new org.goldenport.cncf.action.AggregateObservedSnapshotFixture.MemoryStore())
        current.seed()
        val supplied = success(current.mutate("Before", revision(1L)))
        val before = current.rows(current.setup.provider)
        var callbacks = 0
        val projector: (Record, org.simplemodeling.model.datatype.EntityRevision) => Consequence[String] = (_, _) => {
          callbacks += 1; Consequence.success("unreachable")
        }
        When("the negative boundary receives null id, result, binding, representation, callback or stale revision")
        val results = Vector(
          EntityVersionedMutationOutcome._from_provider_c(null, supplied, current.binding)(projector),
          EntityVersionedMutationOutcome._from_provider_c(current.setup.id, null, current.binding)(projector),
          EntityVersionedMutationOutcome._from_provider_c(current.setup.id, supplied, null)(projector),
          EntityVersionedMutationOutcome._from_provider_c(current.setup.id, supplied, EntityRevisionBinding(null))(projector),
          EntityVersionedMutationOutcome._from_provider_c[String](current.setup.id, supplied, current.binding)(null),
          EntityVersionedMutationOutcome._from_provider_c(current.setup.id,
            EntityVersionedMutationResult.Stale(null, revision(1L)), current.binding)(projector),
          EntityVersionedMutationOutcome._from_provider_c(current.setup.id,
            EntityVersionedMutationResult.Stale(revision(1L), null), current.binding)(projector))
        Then("all shapes reject with no callback or outcome and preserve every full row")
        results.foreach(value => reasons(value) shouldBe Vector("entity-mutation-outcome-invalid"))
        callbacks shouldBe 0
        checkRows(current.rows(current.setup.provider), before)
      }
      "VO-E5 accept only exact String, typed EntityId and matching EntryId without altering raw Fields" in {
        Given("an independent actual raw NoOp whose only pure-negative-test variation is the identity carrier")
        val current = session(_token(), EntityRevisionRepresentation.Embedded,
          new org.goldenport.cncf.action.AggregateObservedSnapshotFixture.MemoryStore())
        current.seed()
        val supplied = success(current.mutate("Before", revision(1L)))
        val before = current.rows(current.setup.provider)
        val identities = Vector[Any](DataStore.EntryId(current.setup.id).print, current.setup.id, DataStore.EntryId(current.setup.id))
        var callbacks = 0
        When("the pure factory consumes the three admitted exact identity carriers")
        val records = identities.map(identity => Record(raw(supplied).fields.map(field =>
          if (field.key == "id") Field("id", Field.Value.Single(identity)) else field)))
        val outcomes = records.map(record => success(EntityVersionedMutationOutcome._from_provider_c(
          current.setup.id, EntityVersionedMutationResult.NoOp(record), current.binding) { (_, _) =>
          callbacks += 1; Consequence.success("projected")
        }))
        Then("each complete supplied raw vector is retained exactly without ID parsing or effects")
        outcomes.zip(records).foreach { case (outcome, record) =>
          checkRecord(outcome.persisted.record, record)
          (outcome.persisted.record eq record) shouldBe true
          outcome.projection shouldBe Consequence.success("projected")
        }
        callbacks shouldBe 3
        checkRows(current.rows(current.setup.provider), before)
      }
      "VO-E3 contain pure null and NonFatal callbacks while propagating control exceptions" in {
        Given("a genuine positive raw provider result and a separate count for each pure projection callback")
        val current = session(_token(), EntityRevisionRepresentation.Embedded,
          new org.goldenport.cncf.action.AggregateObservedSnapshotFixture.MemoryStore())
        current.seed()
        val supplied = success(current.mutate("Before", revision(1L)))
        val before = current.rows(current.setup.provider)
        var callbacks = 0
        val projectors: Vector[(Record, org.simplemodeling.model.datatype.EntityRevision) => Consequence[String]] = Vector(
          (_, _) => { callbacks += 1; null },
          (_, _) => { callbacks += 1; Consequence.success(null) },
          (_, _) => { callbacks += 1; throw new IllegalStateException("private factory secret") },
          (_, _) => { callbacks += 1; structuredFailure })
        val control = new scala.util.control.ControlThrowable {}
        val fatal = new LinkageError("owned fatal")
        When("the pure optional projection factory consumes null, NonFatal, structured and control callback branches")
        val outcomes = projectors.map(projector => success(EntityVersionedMutationOutcome._from_provider_c(
          current.setup.id, supplied, current.binding)(projector)))
        val interrupted = intercept[InterruptedException] {
          EntityVersionedMutationOutcome._from_provider_c[String](current.setup.id, supplied, current.binding) { (_, _) =>
            throw new InterruptedException("owned interrupt")
          }
        }
        val propagated = intercept[scala.util.control.ControlThrowable] {
          EntityVersionedMutationOutcome._from_provider_c[String](current.setup.id, supplied, current.binding) { (_, _) => throw control }
        }
        val propagatedfatal = intercept[LinkageError] {
          EntityVersionedMutationOutcome._from_provider_c[String](current.setup.id, supplied, current.binding) { (_, _) => throw fatal }
        }
        Then("each ordinary callback runs once, structured failure is exact, redacted defects retain raw data and control signals propagate")
        callbacks shouldBe 4
        outcomes.take(3).foreach { outcome =>
          reasons(outcome.projection) shouldBe Vector("entity-mutation-projection-invalid")
          conclusion(outcome.projection).show should not include "private factory secret"
          checkRecord(outcome.persisted.record, before.selected.get)
        }
        conclusion(outcomes.last.projection) shouldBe structuredFailure.conclusion
        interrupted.getMessage shouldBe "owned interrupt"
        (propagated eq control) shouldBe true
        (propagatedfatal eq fatal) shouldBe true
        checkRows(current.rows(current.setup.provider), before)
      }
      "VO-E5 forbid constructor and copy bypasses" in {
        Given("the internal non-case carrier with companion-only construction")
        When("a caller attempts direct construction or case-class copying")
        val constructor = typeCheckErrors("new org.goldenport.cncf.entity.EntityVersionedMutationOutcome[String](null, null, false, null)")
        val copyerrors = typeCheckErrors("(null: org.goldenport.cncf.entity.EntityVersionedMutationOutcome[String]).copy()")
        Then("neither bypass has a type-correct source API")
        constructor.nonEmpty shouldBe true
        copyerrors.nonEmpty shouldBe true
      }
    }

    "preserve the legacy projection contract" which {
      for { backend <- backends; representation <- representations; changed <- Vector(false, true) } {
        s"VO-E6 retain complete legacy $representation success and exact structured failure changed=$changed on $backend" in {
          Given("one actual raw provider result shared by legacy and new helper consumption")
          withBackend(backend) { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            val rows = current.rows(reader)
            val beforephysical = physical()
            When("both helper families project the same raw result without a second write")
            val positive = variants(representation).map { variant =>
              (variant, current.legacy(variant, supplied), success(current.outcome(variant, supplied)))
            }
            current.probe.arm(ProjectionMode.StructuredFailure)
            val oldfailure = current.legacy(typedVariant(representation), supplied)
            val newfailure = success(current.outcome(typedVariant(representation), supplied))
            Then("complete independent values and the original structured Conclusion match the prescribed legacy semantics")
            positive.foreach { case (variant, legacy, outcome) =>
              checkProjection(legacy, variant, rows, current.setup, if (changed) 2L else 1L)
              checkProjection(outcome.projection, variant, rows, current.setup, if (changed) 2L else 1L)
            }
            current.probe.admissions shouldBe 2
            current.probe.decodes shouldBe 0
            checkProjectionFailure(oldfailure, changed, Some(structuredFailure.conclusion))
            checkProjectionFailure(newfailure.projection, changed, Some(structuredFailure.conclusion))
            if (changed) checkConstructedFailure(conclusion(oldfailure), conclusion(newfailure.projection))
            else conclusion(oldfailure) shouldBe conclusion(newfailure.projection)
            current.providerAttempts shouldBe 1
            checkRows(current.rows(reader), rows)
            checkPhysical(physical(), beforephysical)
          }
        }
        s"VO-E6 retain legacy direct throws while the new $representation helper retains raw changed=$changed on $backend" in {
          Given("one real provider outcome before a pure throw is armed separately for each projection attempt")
          withBackend(backend) { (writer, reader, physical) =>
            val current = session(_token(), representation, writer)
            current.seed()
            val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
            val rows = current.rows(reader)
            val beforephysical = physical()
            current.probe.arm(ProjectionMode.Throw)
            When("the legacy helper evaluates its direct expression and the new helper separately contains that same expression")
            val thrown = intercept[IllegalStateException] { current.legacy(typedVariant(representation), supplied) }
            val outcome = success(current.outcome(typedVariant(representation), supplied))
            Then("the legacy throwable escapes unchanged and the new raw outcome survives its redacted projection failure")
            thrown.getMessage shouldBe "private projection secret"
            current.probe.admissions shouldBe 2
            current.probe.decodes shouldBe 0
            checkRaw(outcome, supplied, rows.selected.get, current.setup, changed, if (changed) 2L else 1L)
            checkProjectionFailure(outcome.projection, changed)
            conclusion(outcome.projection).show should not include "private projection secret"
            current.providerAttempts shouldBe 1
            checkRows(current.rows(reader), rows)
            checkPhysical(physical(), beforephysical)
          }
        }
        s"VO-E6 retain legacy null-boundary exceptions for $representation changed=$changed on $backend" in {
          Vector(ProjectionMode.NullConsequence, ProjectionMode.NullEntity).foreach { mode =>
            Given(s"one actual provider result before arming the pure $mode boundary")
            withBackend(backend) { (writer, reader, physical) =>
              val current = session(_token(), representation, writer)
              current.seed()
              val supplied = success(current.mutate(if (changed) "After" else "Before", revision(1L)))
              val rows = current.rows(reader)
              val beforephysical = physical()
              current.probe.arm(mode)
              When("the legacy expression encounters its original null boundary and the new factory contains a separate attempt")
              val thrown = intercept[NullPointerException] { current.legacy(typedVariant(representation), supplied) }
              val outcome = success(current.outcome(typedVariant(representation), supplied))
              Then("legacy exception semantics remain observable while the new outcome preserves all raw data with failure")
              thrown shouldBe a[NullPointerException]
              current.probe.admissions shouldBe 2
              current.probe.decodes shouldBe (if (mode == ProjectionMode.NullEntity) 2 else 0)
              checkRaw(outcome, supplied, rows.selected.get, current.setup, changed, if (changed) 2L else 1L)
              checkProjectionFailure(outcome.projection, changed)
              current.providerAttempts shouldBe 1
              checkRows(current.rows(reader), rows)
              checkPhysical(physical(), beforephysical)
            }
          }
        }
      }
    }

    "satisfy total portable provider laws" which {
      backends.foreach { backend =>
        s"VO-P1 retain at least one hundred complete $backend outcomes across both representations without discards" in {
          Given("nonempty ASCII, Japanese and Greek values, positive revisions and strictly reducing lawful shrinking")
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = representations.map(representation => representation -> session(_token(), representation, writer)).toMap
            val property = Prop.forAll(samples) { sample => checkSample(sample, sessions(sample.representation), reader, physical) }
            When("ScalaCheck evaluates the deferred total property sequentially against bounded reused rows and pools")
            val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
            Then("the actual law passes with at least one hundred successes and zero discarded cases")
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
}
