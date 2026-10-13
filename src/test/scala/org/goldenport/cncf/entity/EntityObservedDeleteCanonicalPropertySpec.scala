package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct. 14, 2026
 */
final class EntityObservedDeleteCanonicalPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalPropertySpec.Sample

  "Canonical observed-delete laws" should {
    "COD-P1 retain matched prior revision under every assembled policy" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-P1, rules:R2,R3,R4, phase:96") {
      "advance logical storage once or remove the selected physical root and overflow" in {
        Given("total positive bounded revisions, both representations and both existing lifecycle policies")
        val property = Prop.forAll(_samples) { sample =>
          val setup = fixture(token = "cod_p1", logical = sample.logical, representation = sample.representation,
            initialRevision = sample.actual)
          setup.seed()
          val before = setup.snapshot()
          val result = setup.deleteC(revision(sample.actual))
          val after = setup.snapshot()
          val rootlaw = if (sample.logical) {
            after.root.exists(row =>
              org.simplemodeling.model.datatype.EntityRevision.createC(row(setup.revisionField)).toOption.contains(revision(sample.actual + 1L)) &&
              Vector("title", "business", "content_ref", "owner_id", "permission").forall(key => row.get(key) == before.root.get.get(key))) &&
            after.overflow == before.overflow
          } else after.root.isEmpty && after.overflow.isEmpty
          result == Consequence.success(EntityDeletionResult(setup.id, revision(sample.actual))) && rootlaw &&
            after.unrelatedRoot == before.unrelatedRoot && after.unrelatedOverflow == before.unrelatedOverflow
        }

        When("ScalaCheck executes at least one hundred canonical comparisons")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("the matched-prior and exact-scope law succeeds without discarded input")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "COD-P2 preserve complete state for every stale observation" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-P2, rules:R2,R4,R5, phase:96") {
      "never weaken an explicit observation even with concurrency None" in {
        Given("total observations below independently stored revisions and every assembled policy")
        val property = Prop.forAll(_samples, Gen.chooseNum(1L, 1000L)) { (sample, distance) =>
          val setup = fixture(token = "cod_p2", logical = sample.logical, representation = sample.representation,
            initialRevision = sample.actual + distance)
          setup.seed()
          val before = setup.snapshot()
          val result = setup.deleteC(revision(sample.actual))
          val after = setup.snapshot()
          reason(result).contains("stale-entity-revision") && result.isFaillure && after == before
        }

        When("ScalaCheck executes at least one hundred actual stale deletions")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("every complete root and overflow record is retained with zero discard")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "COD-P3 retain correspondence between typed root identities and canonical overflow" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-P3, rules:R1,R3,R4, phase:96") {
      "delete exactly one generated selected root and preserve every unselected root and overflow" in {
        Given("total bounded distinct collection identities and a bounded selected ordinal")
        val generator = for {
          count <- Gen.chooseNum(1, 8)
          selected <- Gen.chooseNum(0, count - 1)
          identity <- Gen.chooseNum(1, 1000000)
        } yield (count, selected, identity)
        val property = Prop.forAll(generator) { case (count, selected, identity) =>
          val store = new EntityObservedDeleteCanonicalProviderFixture.MemoryStore()
          val setups = Vector.tabulate(count)(index => fixture(token = s"cod_p3_${identity}_$index", provider = store))
          setups.foreach(_.seed())
          val before = setups.map(_.snapshot())
          val target = setups(selected)
          val result = target.deleteC()
          val after = setups.map(_.snapshot())
          val scope = after.zip(before).zipWithIndex.forall { case ((actual, previous), index) =>
            if (index == selected) actual.root.isEmpty && actual.overflow.isEmpty &&
              actual.unrelatedRoot == previous.unrelatedRoot && actual.unrelatedOverflow == previous.unrelatedOverflow
            else actual == previous
          }
          result == Consequence.success(EntityDeletionResult(target.id, org.simplemodeling.model.datatype.EntityRevision.INITIAL)) &&
            setups.map(_.id).distinct.size == count && scope
        }

        When("ScalaCheck executes at least one hundred selected-identity publications")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("only the canonical overflow belonging to the selected typed identity disappears without discard")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }

  private val _samples: Gen[Sample] = for {
    actual <- Gen.chooseNum(1L, 1000000L)
    logical <- Gen.oneOf(false, true)
    representation <- Gen.oneOf(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
  } yield Sample(actual, logical, representation)
}

object EntityObservedDeleteCanonicalPropertySpec {
  private final case class Sample(actual: Long, logical: Boolean, representation: EntityRevisionRepresentation)
}
