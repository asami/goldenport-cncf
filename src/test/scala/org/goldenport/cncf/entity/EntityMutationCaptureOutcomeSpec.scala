package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.UnitOfWorkMutationCaptureFixture
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import scala.util.control.ControlThrowable

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
final class EntityMutationCaptureOutcomeSpec extends AnyWordSpec with UnitOfWorkMutationCaptureFixture.Vocabulary {
  import UnitOfWorkMutationCaptureFixture.*
  import UnitOfWorkMutationCaptureFixture.given

  private val _collection = EntityCollectionId("test", "capture", "admission")
  private val _id: EntityId = new CanonicalId(_collection, "selected")
  private val _foreign: EntityId = new CanonicalId(_collection, "foreign")
  private val _invalid_exemplar = conclusion(Consequence.operationInvalid("admission", Cause.Kind.Inconsistency,
    Vector(Descriptor.Facet.Reason("invalid-admission"))))
  private def _raw(representation: EntityRevisionRepresentation, text: String = "secret business"): Record =
    record("id" -> _id, "business" -> text, representation.storageFieldName -> 1L, "retained" -> Vector("日本", 0L, false))
  private def _capture(raw: Record, binding: EntityRevisionBinding)(cohere: () => Consequence[Unit]) =
    EntityCreateMutationOutcome._from_created_c(_collection, Some(_id), CreateResult[Unit](_id, Some(raw)), binding)(cohere)
  private def _fixed_failure(result: Consequence[?], name: String): Unit = {
    val failure = conclusion(result)
    val expected = _invalid_exemplar
    failure.status shouldBe expected.status
    failure.interpretation shouldBe expected.interpretation
    failure.disposition shouldBe expected.disposition
    failure.observation.taxonomy shouldBe expected.observation.taxonomy
    failure.observation.cause.kind shouldBe expected.observation.cause.kind
    failure.observation.cause.descriptor.facets.collect { case Descriptor.Facet.Reason(value) => value } shouldBe Vector(name)
    failure.previous shouldBe None
    failure.display.contains("private-database-credential") shouldBe false
    failure.display.contains("secret business") shouldBe false
  }

  "Canonical mutation receipt admission only" should {
    "retain constructor-controlled complete create receipts" which {
      "CMC-01 admit exact opaque identities, initial revisions and ordered raw references" in {
        for { representation <- representations; supplied <- Vector(false, true); identity <- Vector[Any](_id, _id.print, DataStore.EntryId(_id)) } {
          Given("an independently constructed admission-only receipt and selected representation; no persistence is implied")
          val raw = Record(_raw(representation).fields.updated(0, field("id", identity)))
          val created = CreateResult[String](_id, Some(raw))
          val binding = EntityRevisionBinding(representation)
          var callbacks = 0
          When("the companion captures the valid receipt before optional coherence")
          val outcome = success(EntityCreateMutationOutcome._from_created_c(_collection,
            if (supplied) Some(_id) else None, created, binding)(() => { callbacks += 1; Consequence.unit }))
          Then("the exact canonical objects and every ordered field survive one callback without a committed flag")
          (outcome.created eq created) shouldBe true
          (outcome.persisted.record eq raw) shouldBe true
          checkRecord(outcome.persisted.record, raw)
          outcome.persisted.revision shouldBe EntityRevision.INITIAL
          outcome.coherence shouldBe Consequence.unit
          callbacks shouldBe 1
          outcome.toString shouldBe "EntityCreateMutationOutcome"
        }
      }
      "CMC-02 reject malformed admission-only create receipts before coherence" in {
        representations.foreach { representation =>
          Given("a valid raw receipt and a finite independent set of missing, duplicate and mismatched boundary shapes")
          val raw = _raw(representation)
          val binding = EntityRevisionBinding(representation)
          val valid = CreateResult[Unit](_id, Some(raw))
          val records = Vector(null, Record(null), record("business" -> "secret business", representation.storageFieldName -> 1L),
            Record(raw.fields :+ field("id", _id)), Record(raw.fields :+ field(representation.storageFieldName, 1L)),
            Record(raw.fields.filterNot(_.key == representation.storageFieldName)),
            Record(raw.fields.map(value => if (value.key == representation.storageFieldName) field(value.key, "wrong") else value)),
            Record(raw.fields.map(value => if (value.key == representation.storageFieldName) field(value.key, 2L) else value)),
            Record(raw.fields.updated(0, field("id", _foreign))))
          val attempts: Vector[() => Consequence[EntityCreateMutationOutcome[Unit]]] = records.map { value =>
            () => _capture(value, binding)(() => throw new IllegalStateException("callback must not run"))
          } ++ Vector(
            () => EntityCreateMutationOutcome._from_created_c(null, Some(_id), valid, binding)(() => fail("callback")),
            () => EntityCreateMutationOutcome._from_created_c(_collection, null, valid, binding)(() => fail("callback")),
            () => EntityCreateMutationOutcome._from_created_c(_collection, Some(null), valid, binding)(() => fail("callback")),
            () => EntityCreateMutationOutcome._from_created_c(_collection, Some(_foreign), valid, binding)(() => fail("callback")),
            () => EntityCreateMutationOutcome._from_created_c[Unit](_collection, Some(_id), null, binding)(() => fail("callback")),
            () => _capture(raw, null)(() => fail("callback")),
            () => _capture(raw, EntityRevisionBinding(null))(() => fail("callback"))) ++
            Vector(CreateResult[Unit](null, Some(raw)), CreateResult[Unit](_foreign, Some(raw)),
              CreateResult[Unit](_id, null), CreateResult[Unit](_id, None)).map { value =>
                () => EntityCreateMutationOutcome._from_created_c(_collection, Some(_id), value, binding)(() => fail("callback"))
              }
          When("each invalid receipt is admitted without invoking a coherence callback")
          val results = attempts.map(_())
          Then("every attempt is the fixed redacted structured invalid-create receipt")
          results.foreach(_fixed_failure(_, "entity-create-mutation-outcome-invalid"))
        }
      }
    }
    "retain only the matched prior deletion receipt" which {
      "CMC-03 preserve the exact deletion object and reject invalid selections with no invented successor" in {
        Given("an actual canonical EntityDeletionResult object used solely as an admission input")
        val prior = revision(17L)
        val deleted = EntityDeletionResult(_id, prior)
        var calls = 0
        When("the companion admits its exact selection and separately rejects malformed selections")
        val outcome = success(EntityDeleteMutationOutcome._from_deleted_c(_id, prior, deleted)(() => { calls += 1; Consequence.unit }))
        val invalid = Vector(EntityDeletionResult(null, prior), EntityDeletionResult(_foreign, prior),
          EntityDeletionResult(_id, null), EntityDeletionResult(_id, revision(18L)), null).map { value =>
          EntityDeleteMutationOutcome._from_deleted_c(_id, prior, value)(() => { calls += 1; Consequence.unit })
        } ++ Vector(EntityDeleteMutationOutcome._from_deleted_c(null, prior, deleted)(() => { calls += 1; Consequence.unit }),
          EntityDeleteMutationOutcome._from_deleted_c(_id, null, deleted)(() => { calls += 1; Consequence.unit }))
        Then("the admitted receipt retains its prior revision and all invalid receipts invoke zero callbacks")
        (outcome.deleted eq deleted) shouldBe true
        (outcome.deleted.matchedRevision eq prior) shouldBe true
        outcome.coherence shouldBe Consequence.unit
        outcome.toString shouldBe "EntityDeleteMutationOutcome"
        calls shouldBe 1
        invalid.foreach(_fixed_failure(_, "entity-delete-mutation-outcome-invalid"))
      }
    }
    "contain optional coherence without erasing capture" which {
      "CMC-04 preserve structured Conclusions and redact malformed or escaped callback failures" in {
        Given("valid admission-only create and delete receipts and independently constructed callback behaviors")
        val binding = EntityRevisionBinding(EntityRevisionRepresentation.Detached)
        val raw = _raw(binding.representation)
        val failed = Consequence.securityPermissionDenied("coherence-denied")
        val callbacks: Vector[() => Consequence[Unit]] = Vector(null, () => null,
          () => Consequence.Failure(null), () => throw new IllegalStateException("private-database-credential"))
        When("valid receipts run their optional callbacks once under NonFatal containment")
        val structured = success(_capture(raw, binding)(() => failed))
        val structureddelete = success(EntityDeleteMutationOutcome._from_deleted_c(_id, revision(7), EntityDeletionResult(_id, revision(7)))(() => failed))
        val creates = callbacks.map(callback => success(_capture(raw, binding)(callback)))
        val deletes = callbacks.map(callback => success(EntityDeleteMutationOutcome._from_deleted_c(_id, revision(7), EntityDeletionResult(_id, revision(7)))(callback)))
        Then("outer capture remains successful and original structured Conclusions retain reference identity")
        (conclusion(structured.coherence) eq conclusion(failed)) shouldBe true
        (conclusion(structureddelete.coherence) eq conclusion(failed)) shouldBe true
        creates.foreach { outcome => (outcome.persisted.record eq raw) shouldBe true; _fixed_failure(outcome.coherence, "entity-mutation-coherence-invalid") }
        deletes.foreach(outcome => _fixed_failure(outcome.coherence, "entity-mutation-coherence-invalid"))
      }
      "CMC-04 propagate control, interruption and fatal signals from optional coherence" in {
        Given("admission-only valid receipts and exact non-NonFatal signal instances")
        val binding = EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
        val signals = Vector[Throwable](new ControlThrowable {}, new InterruptedException("control"), new OutOfMemoryError("fatal"))
        signals.foreach { signal =>
          When("a callback raises a signal excluded from NonFatal")
          val caughtcreate = intercept[Throwable] { _capture(_raw(binding.representation), binding)(() => throw signal) }
          val caughtdelete = intercept[Throwable] { EntityDeleteMutationOutcome._from_deleted_c(_id, revision(1), EntityDeletionResult(_id, revision(1)))(() => throw signal) }
          Then("the original signal propagates without becoming an optional failure")
          (caughtcreate eq signal) shouldBe true; (caughtdelete eq signal) shouldBe true
        }
      }
    }
    "obey total admission laws" which {
      "CMC-P1 retain complete ordered raw values or reject a foreign selection over 100 shrinkable samples" in {
        Given("total bounded samples with meaningful lawful shrinking and no persistence claim")
        val law = Prop.forAll(samples) { sample =>
          Given("one independent raw record with all generated domain values and selected revision")
          val binding = EntityRevisionBinding(sample.representation)
          val raw = record("id" -> _id, "title" -> sample.title, "business" -> sample.business,
            sample.representation.storageFieldName -> 1L, "other" -> Vector(sample.version, false, sample.supplied))
          var calls = 0
          When("a matching receipt is captured and its foreign expected selection is rejected")
          val outcome = success(_capture(raw, binding)(() => { calls += 1; Consequence.unit }))
          val invalid = EntityCreateMutationOutcome._from_created_c(_collection, Some(_foreign), outcome.created, binding)(() => { calls += 1; Consequence.unit })
          Then("the entire ordered record and object identity survive while only one callback ran")
          checkRecord(outcome.persisted.record, raw); (outcome.persisted.record eq raw) shouldBe true
          calls shouldBe 1; _fixed_failure(invalid, "entity-create-mutation-outcome-invalid")
          true
        }
        When("the sequential admission law is checked without filtering or implications")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
        Then("at least 100 successful admission examples and zero discards are required")
        result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
      }
    }
  }
}
