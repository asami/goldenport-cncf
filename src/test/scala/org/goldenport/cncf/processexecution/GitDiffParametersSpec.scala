package org.goldenport.cncf.processexecution

import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 22, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitDiffParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _parameters(value: ConfigurationValue): ResolvedParameters =
    ResolvedParameters.fromComponentConfiguration(Configuration(Map(GitDiffParameters.key -> value)), "git-diff-spec", None)

  "Common Git diff parameters" should {
    "E1 CAND-BUDGET-01 Ordinary parameter resolution" should afterWord(
      "in spec:typed-git-execution, example:E1, rules:CAND-BUDGET-01, phase:CC-04") {
      "default an absent canonical property to one MiB" in {
        Given("an ordinary parameter snapshot without a diff property")
        val parameters = ResolvedParameters.empty()
        When("the shared diff policy resolves the snapshot")
        val resolved = GitDiffParameters.resolveC(parameters)
        Then("the canonical key retains a one MiB default and permits an explicit maximum of sixteen MiB")
        GitDiffParameters.key shouldBe "cncf.git.diff.maximum-bytes"
        GitDiffParameters.defaultBytes shouldBe 1048576L
        GitDiffParameters.maximumBytes shouldBe 16777216L
        resolved.toOption shouldBe Some(1048576L)
      }

      "accept exactly integral numeric values throughout the inclusive finite range" in {
        Given("generated ordinary NumberValue properties and explicit boundary values")
        val property = Prop.forAll(Gen.choose(1L, GitDiffParameters.maximumBytes)) { bytes =>
          GitDiffParameters.resolveC(_parameters(ConfigurationValue.NumberValue(BigDecimal(bytes)))).toOption.contains(bytes)
        }
        val boundaries = Vector(BigDecimal(1), BigDecimal("1.0"), BigDecimal(131072), BigDecimal(1048576),
          BigDecimal(1048577), BigDecimal(16777216))
        When("the common resolver decodes the property values")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        val resolved = boundaries.map(number => GitDiffParameters.resolveC(_parameters(ConfigurationValue.NumberValue(number))))
        Then("integral numeric values retain their exact byte counts including both endpoints")
        checked.passed shouldBe true
        resolved.map(_.toOption) shouldBe Vector(Some(1L), Some(1L), Some(131072L), Some(1048576L),
          Some(1048577L), Some(16777216L))
      }

      "reject present null, textual, fractional and out-of-range properties without coercion" in {
        Given("present values that do not denote an admitted positive integral NumberValue")
        val secret = "secret-diff-configuration"
        val invalid = Vector(
          ConfigurationValue.NullValue, ConfigurationValue.StringValue("1048576"), ConfigurationValue.StringValue(secret),
          ConfigurationValue.NumberValue(BigDecimal("1.5")), ConfigurationValue.NumberValue(BigDecimal(0)),
          ConfigurationValue.NumberValue(BigDecimal(-1)), ConfigurationValue.NumberValue(BigDecimal(16777217)),
          ConfigurationValue.NumberValue(BigDecimal("9223372036854775808")), ConfigurationValue.NumberValue(null),
          ConfigurationValue.BooleanValue(true), ConfigurationValue.ListValue(List.empty),
          ConfigurationValue.ObjectValue(Map.empty))
        When("the common resolver sees each explicit invalid value or a missing snapshot")
        val resolved = invalid.map(value => GitDiffParameters.resolveC(_parameters(value))) ++ Vector(
          GitDiffParameters.resolveC(null: ResolvedParameters), GitDiffParameters.resolveC(null: ExecutionContext))
        Then("each is a structured configuration failure whose diagnostic excludes the supplied value")
        resolved.foreach { rejected =>
          rejected.isFaillure shouldBe true
          rejected.toString should include(GitDiffParameters.key)
          rejected.toString should not include secret
        }
      }

      "consume the canonical inherited ordinary parameter without inventing another source" in {
        Given("a child snapshot inheriting an explicitly selected numeric ceiling")
        val parent = _parameters(ConfigurationValue.NumberValue(BigDecimal(262144)))
        val child = ResolvedParameters.empty(Some(parent))
        When("the shared policy resolves the ordinary inherited snapshot")
        val resolved = GitDiffParameters.resolveC(child)
        Then("the selected value and its normal parameter-use evidence belong to the canonical key")
        resolved.toOption shouldBe Some(262144L)
        child.usedEntries.map(_.key) shouldBe Vector(GitDiffParameters.key)
      }
    }
  }
}
