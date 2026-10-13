package org.goldenport.cncf.display

import java.time.Instant
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
final class DisplayProtocolCodecPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayProtocolSpecFixture.*

  private val _text = for {
    size <- Gen.choose(0, 20)
    units <- Gen.listOfN(size, Gen.choose(0, 65535))
  } yield units.map(_.toChar).mkString
  private val _coefficient = for {
    size <- Gen.choose(1, 80)
    digits <- Gen.listOfN(size, Gen.choose(0, 9))
    negative <- Gen.oneOf(false, true)
  } yield (if (negative) "-" else "") + digits.mkString
  private val _number = for {
    coefficient <- _coefficient
    scale <- Gen.oneOf(Gen.choose(-40, 40), Gen.oneOf(Int.MinValue, Int.MaxValue, 0, 7, -7))
  } yield decimal(coefficient, scale)
  private val _instant = Gen.oneOf(
    Gen.const(Instant.MIN), Gen.const(Instant.MAX),
    for { seconds <- Gen.choose(-1000000000L, 1000000000L); nanos <- Gen.choose(0, 999999999) }
      yield Instant.ofEpochSecond(seconds, nanos.toLong))
  private val _cases = for {
    text <- Gen.oneOf(_text, Gen.const("\ud83d\ude00\ud800\udfff\u0000\t日本 e\u0301"))
    number <- _number
    instant <- _instant
    size <- Gen.choose(0, 4)
    optional <- Gen.oneOf(false, true)
    revision <- Gen.choose(1L, Long.MaxValue)
    reverse <- Gen.oneOf(false, true)
    empty <- Gen.oneOf(false, false, false, true)
  } yield if (empty) emptySample() else sample(text, number, instant, size, optional, revision, reverse)

  "E1 Valid typed List and Detail law" should afterWord("in spec:display-model-protocol, example:E1, rules:R1,R2,R3,R5, phase:96") {
    "restore all admitted scalar and container arrangements without discarded cases" in {
      Given("docs/spec/display-model-protocol.md R1,R2,R3,R5 property E1: directly constructed valid independent definitions and all five typed values")
      val property = Prop.forAll(_cases) { fixture =>
        Given("docs/spec/display-model-protocol.md R1,R2,R3,R5 property E1: one legal generated snapshot pair with unique opaque resources")
        val definitions = fixture.definitions.definitions
        val models = fixture.displays.map(_.model)
        When("pure encoding and trusted restoration interpret both snapshots")
        val decoded = fixture.displays.map(display => roundTrip(fixture, display))
        Then("exact typed model definition provenance and resource scalar representation survive")
        decoded.zip(fixture.displays).foreach { case (actual, expected) => checkDisplay(actual, expected) }
        (fixture.definitions.definitions eq definitions) shouldBe true
        fixture.displays.map(_.model) shouldBe models
        source.componentId.name shouldBe "org.goldenport.cncf.test.GeneratedLogicalUiConsumer"
        source.resourceType shouldBe "collecting-reference"
        true
      }
      When("ScalaCheck interprets at least one hundred legal generated cases")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("the law passes with at least one hundred successes and zero discarded inputs")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  "E2 Canonical idempotence law" should afterWord("in spec:display-model-protocol, example:E2, rules:R3,R4,R5, phase:96") {
    "retain canonical exact JSON UTF16 units and arbitrary coefficient with signed or extreme scale" in {
      Given("docs/spec/display-model-protocol.md R3,R4,R5 property E2: arbitrary bounded UTF16 vectors precise decimals Instant limits and legal order permutations")
      val property = Prop.forAll(_cases) { fixture =>
        Given("docs/spec/display-model-protocol.md R3,R4,R5 property E2: one finite scoped layout with optional or empty containers")
        val original = fixture.displays
        When("each snapshot encodes decodes and encodes again")
        val results = original.map { display =>
          val first = success(DisplayProtocolCodec.encodeC(source, display))
          val decoded = success(DisplayProtocolCodec.decodeC(source, fixture.definitions, first))
          val second = success(DisplayProtocolCodec.encodeC(source, decoded))
          (display, decoded, first, second)
        }
        Then("the canonical text is identical and every original string unit decimal coefficient and scale remains exact")
        results.foreach { case (display, decoded, first, second) =>
          second shouldBe first
          first.endsWith("\n") shouldBe false
          first.forall(char => char >= ' ' && char <= '~') shouldBe true
          checkDisplay(decoded, display)
        }
        true
      }
      When("ScalaCheck interprets at least one hundred canonical representation cases")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("idempotence passes with at least one hundred successes and zero discarded inputs")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  "E3 Single invalid member law" should afterWord("in spec:display-model-protocol, example:E3, rules:R1,R2,R3,R5,R6, phase:96") {
    "fail closed with stable diagnostics and unchanged trusted objects" in {
      Given("docs/spec/display-model-protocol.md R1,R2,R3,R5,R6 property E3: finite single-defect source version descriptor metadata tag shape resource and scalar cases")
      val fixture = sample()
      val candidates = compatibilityCases(fixture) ++ identityCases(fixture) ++ shapeCases(fixture) ++ resourceCases(fixture) ++ scalarCases(fixture)
      val property = Prop.forAll(Gen.oneOf(candidates)) { candidate =>
        Given(s"docs/spec/display-model-protocol.md R1,R2,R3,R5,R6 property E3: one generated defect at ${candidate.path}")
        val admitted = fixture.definitions
        val definitions = admitted.definitions
        val displays = fixture.displays
        val models = displays.map(_.model)
        val componentid = source.componentId
        val resourcetype = source.resourceType
        When("pure trusted restoration interprets the single invalid member")
        val result = DisplayProtocolCodec.decodeC(source, admitted, candidate.input)
        Then("the stable typed code path fails with no authority object replacement or source effect")
        checkFailure(result, candidate)
        (fixture.definitions eq admitted) shouldBe true
        (admitted.definitions eq definitions) shouldBe true
        (fixture.displays eq displays) shouldBe true
        displays.zip(models).foreach { case (display, model) => (display.model eq model) shouldBe true }
        (source.componentId eq componentid) shouldBe true
        source.resourceType shouldBe resourcetype
        true
      }
      When("ScalaCheck interprets at least one hundred independently selected single-defect candidates")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("closed rejection passes with at least one hundred successes and zero discarded inputs")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }
}
