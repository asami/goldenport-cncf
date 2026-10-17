package org.goldenport.cncf.display

import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
object DisplayEditingStudioReferencePropertySpec {
  import DisplayEditingStudioReferenceFixture.*
  import DisplayMutationServiceFixture.{Target, representations}

  val parameters: Test.Parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  private val _text = for {
    length <- Gen.choose(0, 18)
    units <- Gen.listOfN(length, Gen.choose(0, 65535).map(_.toChar))
  } yield units.mkString
  val samples: Gen[Sample] = for {
    payload <- Gen.oneOf(_text, Gen.const(witnessText), Gen.const("\n\u0000"))
    description <- Gen.oneOf(_text, Gen.const(""), Gen.const(witnessText))
    version <- Gen.choose(1L, 16L)
    target <- Gen.oneOf(Target.values.toVector)
    representation <- Gen.oneOf(representations)
    profile <- Gen.oneOf(Profile.values.toVector)
  } yield Sample(payload, description, version, target, representation, profile)
  val rejected: Gen[Rejected] = for {
    sample <- samples
    mode <- Gen.oneOf(Rejection.values.toVector)
  } yield Rejected(sample, mode)
  given Shrink[Sample] = Shrink { sample =>
    val payload = if (sample.payload.isEmpty) Stream.empty else Stream(sample.copy(payload = sample.payload.take(sample.payload.length / 2)))
    val description = if (sample.description.isEmpty) Stream.empty else
      Stream(sample.copy(description = sample.description.take(sample.description.length / 2)))
    val version = if (sample.initialrevision == 1L) Stream.empty else
      Stream(sample.copy(initialrevision = math.max(1L, sample.initialrevision / 2L)))
    val target = if (sample.target == Target.Entity) Stream.empty else Stream(sample.copy(target = Target.Entity))
    val representation = if (sample.representation == EntityRevisionRepresentation.Embedded) Stream.empty else
      Stream(sample.copy(representation = EntityRevisionRepresentation.Embedded))
    val profile = if (sample.profile == Profile.UpdateOnly) Stream.empty else Stream(sample.copy(profile = Profile.UpdateOnly))
    payload #::: description #::: version #::: target #::: representation #::: profile
  }
  given Shrink[Rejected] = Shrink { rejected =>
    summon[Shrink[Sample]].shrink(rejected.sample).map(sample => rejected.copy(sample = sample)) #:::
      (if (rejected.mode == Rejection.CurrentNoop) Stream.empty else Stream(rejected.copy(mode = Rejection.CurrentNoop)))
  }

  trait Laws extends DisplayEditingStudioReferenceFixture.Vocabulary { self: org.scalatest.Informing =>
    def connectedLaw(pool: Pool, sample: Sample): Boolean = {
      Given("the total generated lawful prefix/raw UTF16 payload, description, revision and closed target/representation/profile choices")
      val session = pool.session(sample)
      When("the same resource traverses ordinary read/select/update/no-op/stale/direct Business and explicit Detail/List")
      val result = connected(session, sample)
      Then("independently supplied text and all full-state oracles correspond without filtering or more than four owned sessions")
      result.entry.values(0) shouldBe DisplayValue.Text(sample.title)
      result.entry.values(1) shouldBe DisplayValue.Text(sample.description)
      result.entry.version shouldBe sample.initialrevision + 2L
      pool.liveSessionCount should be <= 4
      true
    }
    def rejectionLaw(pool: Pool, rejected: Rejected): Boolean = {
      Given("the total predeclared rejection case, independently current/stale revision and complete canonical pre-action state")
      val sample = rejected.sample
      val session = pool.session(sample)
      val declaration = prepare(session, sample.initialTitle, sample.initialrevision)
      val selected = select(session, declaration)
      val inputs = Vector(DisplayMutationServiceFixture.field("title", DisplayValue.Text(sample.initialTitle)))
      When("the ordinary update receives the selected current-noop/stale-equal/readonly/ASCII-blank case")
      rejectMode(session, selected, declaration.initial, rejected.mode, inputs, sample.initialrevision + 1L)
      Then("the separately specified no-op or structured failure retains every durable field and the independently current revision")
      checkSemantic(session.lifecycleSnapshot(), declaration.initial)
      pool.liveSessionCount should be <= 4
      true
    }
    def checkLaw(result: Test.Result): Unit = {
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }
}

final class DisplayEditingStudioReferencePropertySpec extends AnyWordSpec with DisplayEditingStudioReferencePropertySpec.Laws {
  import DisplayEditingStudioReferenceFixture.*
  import DisplayEditingStudioReferencePropertySpec.*
  import DisplayEditingStudioReferencePropertySpec.given
  import DisplayMutationServiceFixture.{Target, representations}

  "REF-P1 Total connected correspondence" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-P1, rules:R1,R2,R3,R6,R8, phase:96") {
    "preserve independent exact raw text through real canonical sequences with lawful reducing shrinkers" in {
      Given("docs/spec/display-editing-studio-reference.md R1/R2/R3/R6/R8 REF-P1: a four-owner SQLite pool and total unsanitized UTF16 generators")
      val pool = new Pool
      try {
        When("the initial newline/NUL revision-sixteen witness traverses all six target/representation combinations and both profiles")
        for (target <- Target.values.toVector; representation <- representations; profile <- Profile.values.toVector) {
          connectedLaw(pool, Sample("\n\u0000", witnessText, 16L, target, representation, profile)) shouldBe true
        }
        Then("every deterministic control establishes exact initial and later semantic/display text before generated selection")
        pool.liveSessionCount should be <= 4
        When("one worker executes at least one hundred total generated connected sequences with payload/revision/mode shrinking")
        val result = Test.check(parameters, Prop.forAll(samples)(sample => connectedLaw(pool, sample)))
        Then("the actual separate law result has at least one hundred successes and zero discards")
        checkLaw(result)
      } finally pool.close()
      Then("all owned runtime sessions are closed after the law")
      pool.liveSessionCount shouldBe 0
    }
  }

  "REF-P2 Total no-op and rejection preservation" should afterWord(
    "in spec:display-editing-studio-reference, example:REF-P2, rules:R3,R4,R8, phase:96") {
    "retain complete current state for every independently chosen no-op or rejection case" in {
      Given("docs/spec/display-editing-studio-reference.md R3/R4/R8 REF-P2: total case ADT and independent lawful values/current/stale observations")
      val pool = new Pool
      try {
        When("every branch has a deterministic newline/NUL revision-sixteen control before the generated law")
        Rejection.values.toVector.foreach { mode =>
          rejectionLaw(pool, Rejected(Sample("\n\u0000", "", 16L, Target.Entity,
            EntityRevisionRepresentation.Embedded, Profile.UpdateOnly), mode)) shouldBe true
        }
        Then("no branch can be vacuous or selected from a production response")
        pool.liveSessionCount should be <= 4
        When("one worker interprets at least one hundred total cases with shorter values, revisions and still-real case shrinkers")
        val result = Test.check(parameters, Prop.forAll(rejected)(sample => rejectionLaw(pool, sample)))
        Then("the separate law passes at least one hundred times without discards, sanitization or catching failures into success")
        checkLaw(result)
      } finally pool.close()
      Then("the finite pool owns no runtime after final cleanup")
      pool.liveSessionCount shouldBe 0
    }
  }
}
