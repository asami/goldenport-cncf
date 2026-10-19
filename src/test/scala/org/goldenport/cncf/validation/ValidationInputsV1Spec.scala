package org.goldenport.cncf.validation

import io.circe.Json
import org.goldenport.cncf.processexecution.SbtScenarioParameters
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Executable exact input schema and transfer contract.
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class ValidationInputsV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ValidationInputsV1.*

  private val _schema = Vector(
    Parameter("label", Kind.Text, defaultValue = Some(Value.Text("default case"))),
    Parameter("enabled", Kind.BooleanValue, defaultValue = Some(Value.BooleanValue(true))),
    Parameter("count", Kind.Integer, defaultValue = Some(Value.Integer(1L))),
    Parameter("optional", Kind.Text, required = false)
  )
  private val _defaults = Map("count" -> Value.Integer(1L), "enabled" -> Value.BooleanValue(true),
    "label" -> Value.Text("default case"))

  "E2 Explicit inputs, defaults and absence" must afterWord(
    "in spec:validation-test-metadata, example:E2, rules:R1,R2, phase:6.2"
  ) {
    "retain exact supplied types and apply defaults only to absent names" in {
      Given("docs/spec/validation-test-metadata.md R1,R2 E2: three defaults and one optional input")
      val supplied = Map("count" -> Json.fromLong(Long.MinValue), "enabled" -> Json.False,
        "label" -> Json.fromString("日本語 ' $() literal"))
      When("supplied and absent inputs are bound")
      val explicit = bindC(_schema, supplied)
      val defaulted = bindC(_schema, Map.empty)
      Then("values retain their kinds, defaults fill absence and optional absence remains absent")
      explicit.toOption shouldBe Some(Map("count" -> Value.Integer(Long.MinValue),
        "enabled" -> Value.BooleanValue(false), "label" -> Value.Text("日本語 ' $() literal")))
      defaulted.toOption shouldBe Some(_defaults)
      validateSchemaC(_schema).toOption.get.map(_.name) shouldBe Vector("count", "enabled", "label", "optional")
    }

    "reject missing required, unknown, null and wrong kinds including nonintegral or oversized numbers" in {
      Given("docs/spec/validation-test-metadata.md R2 E2: exact required input kinds")
      val malformed = Vector(
        Map("unknown" -> Json.True), Map("count" -> Json.Null), Map("count" -> null.asInstanceOf[Json]),
        Map("count" -> Json.fromString("2")), Map("count" -> Json.fromBigDecimal(BigDecimal("1.5"))),
        Map("count" -> Json.fromBigInt(BigInt(Long.MaxValue) + 1)),
        Map("enabled" -> Json.fromString("true")), Map("enabled" -> Json.fromInt(1)),
        Map("label" -> Json.True), Map(null.asInstanceOf[String] -> Json.True)
      )
      When("invalid supplied data are admitted even though defaults exist")
      val rejected = malformed.map(bindC(_schema, _)) ++ Vector(bindC(_schema, null),
        bindC(Vector(Parameter("required", Kind.Text)), Map.empty))
      Then("defaults never mask invalid supplied data or missing required values")
      rejected.foreach(_.isFaillure shouldBe true)
    }
  }

  "E3 Schema and literal boundaries" must afterWord(
    "in spec:validation-test-metadata, example:E3, rules:R1, phase:6.2"
  ) {
    "reject malformed schemas and defaults with exact kind and literal restrictions" in {
      Given("docs/spec/validation-test-metadata.md R1 E3: invalid schema structure and default values")
      val schemas = Vector[Vector[Parameter]](null, Vector(null),
        Vector(Parameter("same", Kind.Text), Parameter("same", Kind.Integer)),
        Vector(Parameter("Bad", Kind.Text)), Vector(Parameter(null, Kind.Text)),
        Vector(Parameter("a", null)), Vector(Parameter("a", Kind.Text, defaultValue = null)),
        Vector(Parameter("a", Kind.Text, defaultValue = Some(null))),
        Vector(Parameter("a", Kind.Text, defaultValue = Some(Value.Integer(1L)))),
        Vector(Parameter("a", Kind.Text, defaultValue = Some(Value.Text(null)))),
        (1 to 17).map(x => Parameter(s"p$x", Kind.Integer)).toVector)
      val texts = Vector("", "x" * 513, "a\n", "a\u0000", "a\"", "a\\", "a;")
      When("schema and literal typed value admission are applied")
      val rejected = schemas.map(validateSchemaC) ++ texts.map { text =>
        validateSchemaC(Vector(Parameter("label", Kind.Text, defaultValue = Some(Value.Text(text)))))
      }
      val values = Vector[Map[String, Value]](null, Map("a" -> null), Map(null.asInstanceOf[String] -> Value.Integer(1)),
        Map("A" -> Value.Integer(1)), (1 to 17).map(x => s"p$x" -> Value.Integer(x.toLong)).toMap)
      Then("invalid declarations and parameter data yield domain failures")
      rejected.foreach(_.isFaillure shouldBe true)
      values.map(SbtScenarioParameters.validateC).foreach(_.isFaillure shouldBe true)
      SbtScenarioParameters.validateC(Map("a" -> Value.Text("x" * 512))).isFaillure shouldBe false
      SbtScenarioParameters.validateC(Map(("a" * 64) -> Value.Integer(Long.MaxValue))).isFaillure shouldBe false
      SbtScenarioParameters.validateC(Map(("a" * 65) -> Value.Integer(1))).isFaillure shouldBe true
    }
  }

  "E4 Ordinary ScalaTest ConfigMap transfer" must afterWord(
    "in spec:validation-test-metadata, example:E4, rules:R2,R3, phase:6.2"
  ) {
    "recover exact types from canonical rendered tokens and ignore unrelated ScalaTest keys" in {
      Given("docs/spec/validation-test-metadata.md R2,R3 E4: generated Long and Boolean values with literal text")
      val property = Prop.forAll(Gen.choose(Long.MinValue, Long.MaxValue), Gen.oneOf(true, false)) { (count, enabled) =>
        val expected = Map("count" -> Value.Integer(count), "enabled" -> Value.BooleanValue(enabled),
          "label" -> Value.Text("日本語 ' $() case"))
        val tokens = SbtScenarioParameters.renderArguments(expected)
        val config = tokens.map { token =>
          val pair = token.substring(3, token.length - 1).split("=", 2)
          pair(0) -> pair(1)
        }.toMap[String, Any] + ("unrelated.scalatest" -> 42)
        fromConfigMapC(_schema, config).toOption.contains(expected) &&
          tokens == Vector(s"\"-Dcncf.validation.input.count=$count\"",
            s"\"-Dcncf.validation.input.enabled=$enabled\"", "\"-Dcncf.validation.input.label=日本語 ' $() case\"")
      }
      When("the ordinary ConfigMap bridge consumes the generated canonical transfer")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)
      Then("typed values and literal spelling survive the transfer")
      checked.passed shouldBe true
      fromConfigMapC(_schema, Map("unrelated" -> null)).toOption shouldBe Some(_defaults)
    }

    "reject malformed namespace values and missing required ConfigMap entries" in {
      Given("docs/spec/validation-test-metadata.md R3 E4: unknown names and noncanonical ConfigMap spellings")
      val malformed = Vector[Map[String, Any]](null, Map(null.asInstanceOf[String] -> "x"),
        Map("cncf.validation.input.unknown" -> "x"), Map("cncf.validation.input." -> "x"),
        Map("cncf.validation.input.count" -> null), Map("cncf.validation.input.count" -> 2),
        Map("cncf.validation.input.count" -> "01"), Map("cncf.validation.input.count" -> "+1"),
        Map("cncf.validation.input.count" -> "-0"), Map("cncf.validation.input.count" -> "1.0"),
        Map("cncf.validation.input.count" -> "9223372036854775808"),
        Map("cncf.validation.input.enabled" -> "True"), Map("cncf.validation.input.enabled" -> "1"),
        Map("cncf.validation.input.label" -> "a;"))
      When("the ConfigMap bridge admits these supplied values")
      val rejected = malformed.map(fromConfigMapC(_schema, _)) ++ Vector(
        fromConfigMapC(Vector(Parameter("required", Kind.Integer)), Map.empty))
      Then("malformed values fail rather than falling back to defaults")
      rejected.foreach(_.isFaillure shouldBe true)
    }
  }
}
