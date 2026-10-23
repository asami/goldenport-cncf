package org.goldenport.cncf.processexecution

import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.config.ResolvedParameters
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 22, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtTestOutputParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _parameters(values: Map[String, ConfigurationValue]): ResolvedParameters =
    ResolvedParameters.fromComponentConfiguration(Configuration(values), "sbt-output-spec", None)

  "E1 SBT test capture defaults" must afterWord(
    "in spec:typed-sbt-execution, example:E1, rules:TEST-OUTPUT-01, phase:116") {
    "resolve independent 64 MiB stdout and 8 MiB stderr defaults" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01 E1: absent ordinary test properties")
      When("the shared test output policy resolves the current parameters")
      val result = SbtTestOutputParameters.resolveC(ResolvedParameters.empty()).toOption.get
      Then("both finite defaults use their canonical numeric byte counts")
      SbtTestOutputParameters.stdoutKey shouldBe "cncf.sbt.test.stdout.maximum-bytes"
      SbtTestOutputParameters.stderrKey shouldBe "cncf.sbt.test.stderr.maximum-bytes"
      result shouldBe ProcessExecutionLimits(stdoutBytes = Some(67108864L), stderrBytes = Some(8388608L))
    }
  }

  "E2 Independent inherited stream properties" must afterWord(
    "in spec:typed-sbt-execution, example:E2, rules:TEST-OUTPUT-01, phase:116") {
    "preserve positive integral numeric values and independent inherited defaults" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01 E2: generated positive Long properties and inherited stdout")
      val property = Prop.forAll(Gen.choose(1L, Long.MaxValue), Gen.choose(1L, Long.MaxValue)) { (stdout, stderr) =>
        val values = Map(SbtTestOutputParameters.stdoutKey -> ConfigurationValue.NumberValue(BigDecimal(stdout)),
          SbtTestOutputParameters.stderrKey -> ConfigurationValue.NumberValue(BigDecimal(stderr)))
        SbtTestOutputParameters.resolveC(_parameters(values)).toOption.contains(
          ProcessExecutionLimits(stdoutBytes = Some(stdout), stderrBytes = Some(stderr)))
      }
      val parent = _parameters(Map(SbtTestOutputParameters.stdoutKey -> ConfigurationValue.NumberValue(BigDecimal("134217728.0"))))
      val child = ResolvedParameters.empty(Some(parent))
      When("ordinary configuration resolves both streams without text coercion")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      val inherited = SbtTestOutputParameters.resolveC(child).toOption.get
      Then("exact numeric values survive, and one absent stream retains its own default")
      withClue(checked.toString) { checked.passed shouldBe true }
      inherited.stdoutBytes shouldBe Some(134217728L)
      inherited.stderrBytes shouldBe Some(8388608L)
      child.usedEntries.map(_.key).toSet should contain(SbtTestOutputParameters.stdoutKey)
    }
  }

  "E3 Invalid present properties" must afterWord(
    "in spec:typed-sbt-execution, example:E3, rules:TEST-OUTPUT-01, phase:116") {
    "fail structurally for either invalid stream without echoing its supplied value" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-01 E3: malformed values in each stream")
      val secret = "private-invalid-output-setting"
      val invalid = Vector(ConfigurationValue.NullValue, ConfigurationValue.StringValue(secret),
        ConfigurationValue.StringValue("67108864"), ConfigurationValue.BooleanValue(true),
        ConfigurationValue.NumberValue(BigDecimal("1.5")), ConfigurationValue.NumberValue(BigDecimal(0)),
        ConfigurationValue.NumberValue(BigDecimal(-1)),
        ConfigurationValue.NumberValue(BigDecimal("9223372036854775808")),
        ConfigurationValue.NumberValue(null), ConfigurationValue.ListValue(List.empty),
        ConfigurationValue.ObjectValue(Map.empty))
      When("the resolver validates each present property")
      val results = for {
        key <- Vector(SbtTestOutputParameters.stdoutKey, SbtTestOutputParameters.stderrKey)
        value <- invalid
      } yield key -> SbtTestOutputParameters.resolveC(_parameters(Map(key -> value)))
      Then("every invalid property fails with its canonical key and no supplied secret")
      results.foreach { case (key, result) =>
        result.isFaillure shouldBe true
        result.toString should include(key)
        result.toString should not include secret
      }
      SbtTestOutputParameters.resolveC(null).isFaillure shouldBe true
    }
  }

  "E4 Host ceiling and operation isolation" must afterWord(
    "in spec:typed-sbt-execution, example:E4, rules:TEST-OUTPUT-02, phase:116") {
    "only narrow admitted Test and TestOnly captures and ignore test properties for Compile" in {
      Given("docs/spec/typed-sbt-execution.md TEST-OUTPUT-02 E4: explicit host ceilings and generated stream selections")
      val property = Prop.forAll(Gen.choose(1L, 134217728L), Gen.choose(1L, 134217728L)) { (hostbytes, selectedbytes) =>
        val host = ProcessExecutionLimits(stdoutBytes = Some(hostbytes), stderrBytes = Some(hostbytes),
          executionTimeoutMillis = Some(7000L))
        val parameters = _parameters(Map(SbtTestOutputParameters.stdoutKey -> ConfigurationValue.NumberValue(BigDecimal(selectedbytes)),
          SbtTestOutputParameters.stderrKey -> ConfigurationValue.NumberValue(BigDecimal(selectedbytes))))
        Vector(SbtOperation.Test, SbtOperation.TestOnly).forall { operation =>
          SbtTestOutputParameters.applyC(parameters, operation, host).toOption.contains(
            host.copy(stdoutBytes = Some(hostbytes.min(selectedbytes)), stderrBytes = Some(hostbytes.min(selectedbytes))))
        }
      }
      val host = ProcessExecutionLimits(stdoutBytes = Some(17L), stderrBytes = Some(19L))
      val invalid = _parameters(Map(SbtTestOutputParameters.stdoutKey -> ConfigurationValue.StringValue("invalid")))
      When("the existing operation-specific policy derives effective ceilings")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      val compile = SbtTestOutputParameters.applyC(invalid, SbtOperation.Compile, host)
      Then("host authority is never enlarged and Compile retains its exact limits")
      withClue(checked.toString) { checked.passed shouldBe true }
      compile.toOption shouldBe Some(host)
      invalid.usedEntries shouldBe empty
    }
  }
}
