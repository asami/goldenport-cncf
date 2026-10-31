package org.goldenport.cncf.datastore

import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.*
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlEntityCreatePropertySpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  import PostgresqlEntityCreatePropertySpec.*

  "Native PostgreSQL canonical create laws" should {
    "PG-E3 prove nonvacuous positive root/body correspondence" which {
      "retain authoritative INITIAL, hydration and unrelated rows across at least one hundred bounded samples" in {
        Given("a total generator, meaningful bounded shrinker and two suite-owned actual native pools")
        with_postgresql { database =>
          val writer = database.open()
          val reader = database.open()
          val property = Prop.forAll(_samples) { sample =>
            Given("a uniquely addressed real canonical collection with complete old root/body pairs")
            val token = database.token("pg_property_positive")
            val setup = fixture(token, representation = sample.representation, provider = writer, oldRevision = sample.oldrevision)
            seedCanonical(setup)
            val before = setup.base.snapshot(reader)
            val candidate = setup.input(sample.mode, Some(setup.freshId), sample.content)
            When("one real canonical create commits its admitted native body mode")
            val result = setup.createC(candidate)
            val after = setup.snapshot(setup.freshId, reader)
            val hydrated = success(fixture(token, representation = sample.representation, provider = reader).hydratedC(setup.freshId))
            val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidate.record))
            Then("the full authoritative pair corresponds to the candidate and retains every unrelated field")
            val correspondence = result.toOption.exists { created =>
              created.id == setup.freshId && created.record.map(_.asMap) == after.root &&
                created.record.flatMap(_.getAny(setup.base.revisionField)).exists(value => success(EntityRevision.createC(value)) == EntityRevision.INITIAL)
            }
            val bodypair = if (sample.mode == BodyMode.Overflow)
              after.overflow.contains(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap) &&
                hydrated.flatMap(_.record.getString("content")) == candidate.record.getString("content")
            else after.overflow.isEmpty && hydrated.flatMap(_.record.getString("content")) ==
              (if (sample.mode == BodyMode.Inline) Some("small candidate") else None)
            Prop.all("actual canonical success and authoritative initialization" |: Prop(correspondence),
              "actual complete native body/hydration pair" |: Prop(bodypair),
              "complete independently read unrelated root/body preservation" |: Prop(after.preexisting == before))
          }
          When("ScalaCheck executes the deferred native positive creation law")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("positive native cases pass at least one hundred successes without discarded scenarios")
          _require_total(checked)
        }
      }
    }
    "PG-E4/CFS-P1 preserve every duplicate body candidate" which {
      "retain complete selected and unrelated rows across both representations and all body modes" in {
        Given("a total bounded generator with meaningful body and revision shrinking and shared actual native pools")
        with_postgresql { database =>
          val writer = database.open()
          val reader = database.open()
          val property = Prop.forAll(_samples) { sample =>
            Given("a unique actual canonical native collection seeded with complete selected and unrelated pairs")
            val setup = fixture(database.token("pg_property_duplicate"), representation = sample.representation,
              provider = writer, oldRevision = sample.oldrevision)
            seedCanonical(setup)
            val before = setup.snapshot(setup.base.id, reader)
            val candidate = setup.input(sample.mode, Some(setup.base.id), sample.content)
            When("strict canonical create attempts a duplicate through the real native primary-id boundary")
            val result = setup.createC(candidate)
            val after = setup.snapshot(setup.base.id, reader)
            Then("the actual duplicate taxonomy and every independently read selected/unrelated field are retained")
            Prop.all("real native duplicate" |: Prop(result.isFaillure && taxonomy(result) == duplicateTaxonomy(setup.base.id)),
              "full selected and unrelated root/body equality" |: Prop(after == before))
          }
          When("ScalaCheck executes the deferred native duplicate preservation law")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the native preservation law succeeds at least one hundred times with zero discards")
          _require_total(checked)
        }
      }
    }
    "PG-E9/CFS-P2 preserve fresh absence after real side work and rejection" which {
      "never publish an orphan for bounded nonempty overflow and actual root/publish/commit faults" in {
        Given("a total bounded fault/body generator and a real reusable checkpoint writer plus independent reader")
        with_postgresql { database =>
          val writer = database.openFault()
          val reader = database.open()
          val property = Prop.forAll(_fault_samples) { sample =>
            Given("a unique native canonical collection and full selected/unrelated snapshots before the actual fault is armed")
            val setup = fixture(database.token("pg_property_rejected"), representation = sample.representation,
              provider = writer, oldRevision = sample.oldrevision)
            seedCanonical(setup)
            val before = setup.snapshot(setup.freshId, reader)
            val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId), sample.content)
            writer.arm(setup.fault(sample.fault))
            When("the actual native provider reaches side work and rejects the selected root/publication boundary")
            val result = setup.createC(candidate)
            val after = setup.snapshot(setup.freshId, reader)
            Then("the exact fault counts and complete independent snapshots prove no orphan or unrelated change")
            Prop.all("actual structured known failure" |: Prop(result.isFaillure && reason(result).contains(
              if (sample.fault == FaultMode.CommitReject) "transaction-failure" else "provider-failure")),
              "actual side and root checkpoints reached once" |: Prop(writer.sideSaves == 1 && writer.rootCreates == 1),
              "full native selected and unrelated retention" |: Prop(after == before),
              "fresh pair remains absent" |: Prop(before.root.isEmpty && before.overflow.isEmpty && after.root.isEmpty && after.overflow.isEmpty))
          }
          When("ScalaCheck executes the deferred native no-orphan law")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the actual native preservation law passes at least one hundred successes with zero discarded cases")
          _require_total(checked)
        }
      }
    }
  }

  private val _samples: Gen[Sample] = for {
    representation <- Gen.oneOf(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
    oldrevision <- Gen.chooseNum(1L, 1000000L)
    mode <- Gen.oneOf(BodyMode.Overflow, BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull)
    size <- Gen.chooseNum(4200, 9000)
    character <- Gen.oneOf("x", "日", "λ")
  } yield Sample(representation, oldrevision, mode, character * size, FaultMode.RootCreate)

  private val _fault_samples: Gen[Sample] = for {
    sample <- _samples
    fault <- Gen.oneOf(FaultMode.RootCreate, FaultMode.ThrowRoot, FaultMode.BeforePublish, FaultMode.ThrowPublish, FaultMode.CommitReject)
  } yield sample.copy(mode = BodyMode.Overflow, fault = fault)

  private def _require_total(checked: Test.Result): Unit = withClue(checked.toString) {
    checked.passed shouldBe true
    checked.succeeded should be >= 100
    checked.discarded shouldBe 0
  }
}

private object PostgresqlEntityCreatePropertySpec {
  final case class Sample(representation: EntityRevisionRepresentation, oldrevision: Long, mode: BodyMode,
    content: String, fault: FaultMode)
  given Shrink[Sample] = Shrink { sample =>
    Stream(sample.copy(oldrevision = math.max(1L, sample.oldrevision / 2)),
      sample.copy(content = sample.content.take(4200)),
      sample.copy(representation = EntityRevisionRepresentation.Detached))
      .filter(_ != sample)
  }
}
