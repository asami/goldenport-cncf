package org.goldenport.cncf.entity

import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 16, 2026
 */
final class EntityCreateFailureSafetyPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*
  import EntityObservedDeleteCanonicalFixture.reason

  private implicit val _sample_shrinker: Shrink[Sample] = Shrink { sample =>
    val revisions = Vector(1L, sample.oldrevision / 2L).distinct
      .filter(value => value >= 1L && value < sample.oldrevision)
      .map(value => sample.copy(oldrevision = value))
    val lengths = Vector(4200, 4200 + (sample.content.length - 4200) / 2).distinct
      .filter(value => value >= 4200 && value < sample.content.length)
      .map(value => sample.copy(content = sample.content.take(value)))
    (revisions ++ lengths).distinct.toStream
  }

  "Canonical Entity create preservation laws" should {
    "CFS-P1 preserve every duplicate candidate" which {
      "retain the full selected and unselected root and body maps across both representations and all body modes" in {
        Given("a total bounded generator of managed representations, old revisions, body modes and large text")
        val property = Prop.forAll(_duplicate_samples) { sample =>
          // This closure's setup and operation run during Test.check, not during property construction.
          Given(s"a fresh real memory provider seeded at ${sample.oldrevision} with ${sample.representation}")
          val setup = fixture("cfs_p1", representation = sample.representation, oldRevision = sample.oldrevision)
          setup.seed()
          val before = setup.snapshot(setup.base.id)
          val candidate = setup.input(sample.mode, Some(setup.base.id), sample.content)

          When(s"canonical EntityStoreSpace create attempts the duplicate ${sample.mode} candidate")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.base.id)

          Then("native duplicate taxonomy and the entire authoritative provider snapshot must be retained")
          Prop.all(
            "actual native duplicate failure" |: Prop(result.isFaillure && taxonomy(result) == duplicateTaxonomy(setup.base.id)),
            "complete selected and preexisting record retention" |: Prop(after == before))
        }

        When("ScalaCheck executes the deferred real-provider duplicate scenarios")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("the desired preservation law passes at least one hundred samples with zero discarded cases")
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }

    "CFS-P2 preserve fresh absence after root rejection" which {
      "never leave orphan overflow for bounded large text and either managed representation" in {
        Given("a total bounded generator of large text, old managed revisions and both representations")
        val property = Prop.forAll(_root_rejection_samples) { sample =>
          // Every sample owns its provider and seeds before enabling exactly one fault.
          Given(s"a fresh real provider with ${sample.representation} and two unchanged preexisting overflow entities")
          val store = new MemoryStore()
          val setup = fixture("cfs_p2", representation = sample.representation, provider = store,
            oldRevision = sample.oldrevision)
          setup.seed()
          val before = setup.snapshot(setup.freshId)
          val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId), sample.content)
          store.arm(setup.fault(FaultMode.RootCreate))

          When("canonical create stages the large side body and rejects only the fresh root at BeforeRootWrite")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.freshId)

          Then("the provider failure retains fresh absence and all complete preexisting records")
          Prop.all(
            "structured provider marker" |: Prop(result.isFaillure && reason(result).contains("provider-failure")),
            "real side-staging and root-create checkpoints reached" |: Prop(store.sideSaves == 1 && store.rootCreates == 1),
            "fresh root and overflow absent before and after" |:
              Prop(before.root.isEmpty && before.overflow.isEmpty && after.root.isEmpty && after.overflow.isEmpty),
            "complete authoritative provider retention" |: Prop(after == before))
        }

        When("ScalaCheck executes the deferred seeded create attempts with keyed root rejection")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("the no-orphan preservation law passes at least one hundred samples with zero discard")
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  "CFS-SHR-P1 preserve create failure categories while reducing counterexamples" should afterWord(
    "in spec:entity-create-atomic-provider, example:CFS-SHR-P1, rules:CA12, phase:96") {
    "produce finite distinct smaller samples and stop at revision one with 4200 characters" in {
      Given("Spec: docs/spec/entity-create-atomic-provider.md, CA12; the total duplicate Sample generator and its suite-local implicit shrinker")
      val property = Prop.forAll(_duplicate_samples) { sample =>
        Given("a generated managed representation, positive old revision, body mode and homogeneous large text")
        val minimum = sample.oldrevision == 1L && sample.content.length == 4200
        When("the actual implicit Sample shrinker is consumed completely")
        val candidates = Shrink.shrink(sample).toVector
        Then("candidates retain the create domain and categories and strictly reduce the independent numeric rank")
        candidates.size should be <= 4
        candidates.distinct shouldBe candidates
        candidates should not contain sample
        candidates.isEmpty shouldBe minimum
        candidates.foreach { candidate =>
          candidate.representation shouldBe sample.representation
          Set(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached) should contain (candidate.representation)
          candidate.mode shouldBe sample.mode
          Set(BodyMode.Overflow, BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull) should contain (candidate.mode)
          candidate.oldrevision should be >= 1L
          candidate.oldrevision should be <= 1000000L
          candidate.content.length should be >= 4200
          candidate.content.length should be <= 9000
          Set('x', '日', 'λ') should contain (candidate.content.head)
          candidate.content.forall(_ == sample.content.head) shouldBe true
          sample.content.startsWith(candidate.content) shouldBe true
          candidate.oldrevision + candidate.content.length.toLong should be < (sample.oldrevision + sample.content.length.toLong)
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

  private val _large_text: Gen[String] = for {
    size <- Gen.chooseNum(4200, 9000)
    character <- Gen.oneOf("x", "日", "λ")
  } yield character * size

  private val _duplicate_samples: Gen[Sample] = for {
    representation <- Gen.oneOf(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
    oldrevision <- Gen.chooseNum(1L, 1000000L)
    mode <- Gen.oneOf(BodyMode.Overflow, BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull)
    content <- _large_text
  } yield Sample(representation, oldrevision, mode, content)

  private val _root_rejection_samples: Gen[Sample] = for {
    representation <- Gen.oneOf(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
    oldrevision <- Gen.chooseNum(1L, 1000000L)
    content <- _large_text
  } yield Sample(representation, oldrevision, BodyMode.Overflow, content)
}
