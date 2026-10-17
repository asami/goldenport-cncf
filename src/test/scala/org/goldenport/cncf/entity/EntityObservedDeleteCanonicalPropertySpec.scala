package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct. 16, 2026
 */
final class EntityObservedDeleteCanonicalPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalPropertySpec.Sample

  private implicit val _sample_shrinker: Shrink[Sample] = Shrink { sample =>
    Vector(1L, sample.actual / 2L).distinct
      .filter(value => value >= 1L && value < sample.actual)
      .map(value => sample.copy(actual = value)).toStream
  }

  "Canonical observed-delete laws" should {
    "COD-P1 retain matched prior revision under every assembled policy" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-P1, rules:R2,R3,R4, phase:96") {
      "advance logical storage once or remove the selected physical root and overflow" in {
        Given("total positive bounded revisions, both representations and both existing lifecycle policies")
        val property = Prop.forAll(_samples) { sample =>
          Given("a fresh canonical fixture with the generated managed revision, representation and policy")
          val setup = fixture(token = "cod_p1", logical = sample.logical, representation = sample.representation,
            initialRevision = sample.actual)
          setup.seed()
          val before = setup.snapshot()
          When("canonical observed deletion receives the current stored revision")
          val result = setup.deleteC(revision(sample.actual))
          val after = setup.snapshot()
          Then("the prior revision is returned and the policy preserves complete unrelated root and overflow records")
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
          Given("a fresh canonical fixture whose managed revision strictly exceeds the generated observation")
          val setup = fixture(token = "cod_p2", logical = sample.logical, representation = sample.representation,
            initialRevision = sample.actual + distance)
          setup.seed()
          val before = setup.snapshot()
          When("canonical observed deletion receives the stale revision")
          val result = setup.deleteC(revision(sample.actual))
          val after = setup.snapshot()
          Then("the structured stale failure preserves the entire canonical snapshot")
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
          Given("distinct canonical fixtures sharing a fresh provider and a generated selected identity")
          val store = new EntityObservedDeleteCanonicalProviderFixture.MemoryStore()
          val setups = Vector.tabulate(count)(index => fixture(token = s"cod_p3_${identity}_$index", provider = store))
          setups.foreach(_.seed())
          val before = setups.map(_.snapshot())
          val target = setups(selected)
          When("canonical observed deletion selects exactly the generated target")
          val result = target.deleteC()
          val after = setups.map(_.snapshot())
          Then("only the selected root and its canonical overflow disappear and every other snapshot is retained")
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

  "COD-SHR-P1 preserve canonical categories while reducing failing revisions" should afterWord(
    "in spec:entity-observed-delete-canonical, example:COD-SHR-P1, rules:R2,R3,R4, phase:96") {
    "produce finite distinct smaller revisions and stop at revision one" in {
      Given("Spec: docs/spec/entity-observed-delete-canonical.md, R2/R3/R4; the total canonical Sample generator and its suite-local implicit shrinker")
      val property = Prop.forAll(_samples) { sample =>
        Given("a generated positive managed revision, representation and lifecycle policy")
        val minimum = sample.actual == 1L
        When("the actual implicit Sample shrinker is consumed completely")
        val candidates = Shrink.shrink(sample).toVector
        Then("candidates retain the canonical domain and categories and strictly reduce the revision rank")
        candidates.size should be <= 2
        candidates.distinct shouldBe candidates
        candidates should not contain sample
        candidates.isEmpty shouldBe minimum
        candidates.foreach { candidate =>
          candidate.actual should be >= 1L
          candidate.actual should be <= 1000000L
          candidate.actual should be < sample.actual
          candidate.logical shouldBe sample.logical
          candidate.representation shouldBe sample.representation
          Set(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached) should contain (candidate.representation)
        }
        true
      }
      When("ScalaCheck checks one hundred total shrink contracts with one worker")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
      Then("every shrink contract succeeds without discarded inputs")
      checked.passed shouldBe true
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
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
