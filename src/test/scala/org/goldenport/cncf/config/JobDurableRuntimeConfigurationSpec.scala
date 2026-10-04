package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingTrace, ConfigurationValue}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.job.JobDurableRuntimeFixtures
import org.goldenport.cncf.job.JobDurableRuntimeFixtures.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableRuntimeConfigurationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-runtime-owner, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1B")

  "Q1 Absent and provider-only typed values" must _scenario("Q1", "R2") {
    "project unconfigured or the exact maintenance defaults" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2 Q1: admitted empty and provider-only bindings")
      val empty = ConfigurationBindingCollection.empty[CncfConfigurationTarget]
      val provided = success(bindings(JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(providerId)))
      When("the value-only policies are projected")
      val absent = success(JobDurableRuntimeConfiguration.from(empty))
      val enabled = success(JobDurableRuntimeConfiguration.from(provided))
      Then("absence is unconfigured and a provider receives exactly 60000 milliseconds and 100 candidates")
      absent shouldBe JobDurableRuntimeConfiguration(None, 60000L, 100)
      enabled shouldBe JobDurableRuntimeConfiguration(Some(providerId), 60000L, 100)
    }
  }
  "Q2 Inclusive exact ASCII and numeric bounds" must _scenario("Q2", "R2") {
    "retain every admitted whole value without normalization" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2 Q2: one and 64 character provider identities and inclusive numeric limits")
      val names = Vector("A", "z_9", "A" + "_" * 63)
      When("each endpoint combination crosses typed document admission")
      val results = for { name <- names; period <- Vector(1000L, 86400000L); limit <- Vector(1, 1024) } yield {
        val admitted = success(bindings(JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(name),
          JobDurableRuntimeConfiguration.periodKey -> ConfigurationValue.NumberValue(BigDecimal(period)),
          JobDurableRuntimeConfiguration.candidatesKey -> ConfigurationValue.NumberValue(BigDecimal(limit))))
        success(JobDurableRuntimeConfiguration.from(admitted)) -> JobDurableRuntimeConfiguration(Some(name), period, limit)
      }
      Then("all admitted values remain exact")
      results.foreach { case (actual, expected) => actual shouldBe expected }
    }
  }
  "Q3 Confidential provider grammar refusal" must _scenario("Q3", "R2") {
    "reject whitespace class names null empty and overlong identities without reflecting input" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2 Q3: inadmissible opaque provider strings")
      val values = Vector(" leading", "trailing ", "org.example.Provider", "credential/path", "", null, "A" * 65, "9provider", "秘密")
      When("the dedicated provider codec receives each whole input")
      val results = values.map(value => CncfConfigurationParameterCatalog.durableJobProvider.codec.decode(ConfigurationValue.StringValue(value)))
      Then("every refusal carries only its fixed parameter label")
      results.foreach { result =>
        result.isSuccess shouldBe false
        JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe Some(JobDurableRuntimeConfiguration.providerKey)
      }
    }
  }
  "Q4 Bounded whole decimal admission" must _scenario("Q4", "R2") {
    "refuse malformed numbers and otherwise valid numeric values without a provider" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2 Q4: negative fractional overflow long nondecimal and out-of-bounds values")
      val badperiod = Vector("-1", "1000.5", "9223372036854775808", "0" * 100, "1e3", "999", "86400001", " 1000")
      val badbound = Vector("0", "1025", "-1", "2.5", "2147483648", "0" * 100)
      When("dedicated codecs and the provider dependency project these inputs")
      val periods = badperiod.map(x => JobDurableRuntimeConfiguration.period.codec.decode(ConfigurationValue.StringValue(x)))
      val limits = badbound.map(x => JobDurableRuntimeConfiguration.candidates.codec.decode(ConfigurationValue.StringValue(x)))
      val fractional = JobDurableRuntimeConfiguration.period.codec.decode(ConfigurationValue.NumberValue(BigDecimal("1000.5")))
      val orphan = JobDurableRuntimeConfiguration.from(success(bindings(JobDurableRuntimeConfiguration.periodKey -> ConfigurationValue.StringValue("1000"))))
      Then("numeric conversion never admits partial values and maintenance cannot implicitly enable a provider")
      (periods ++ limits ++ Vector(fractional, orphan)).foreach(_.isSuccess shouldBe false)
      periods.foreach(result => JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe Some(JobDurableRuntimeConfiguration.periodKey))
      limits.foreach(result => JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe Some(JobDurableRuntimeConfiguration.candidatesKey))
      JobDurableRuntimeFixtures.failureConclusion(orphan).observation.getEffectiveMessage shouldBe Some(JobDurableRuntimeConfiguration.providerKey)
    }
  }
  "Q5 Closed catalog aliases targets and confidential provenance" must _scenario("Q5", "R2,R18") {
    "resolve only the selected Subsystem and redact provider values in diagnostics" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2,R18 Q5: registered document aliases canonical argv and foreign target scopes")
      val identity = success(SubsystemInstanceId.create("platform", "default"))
      val target = success(CncfConfigurationTarget.SubsystemInstance.create(identity))
      val spellings = Vector("textus.job.durable.provider", "textus.runtime.job.durable.provider", "cncf.job.durable.provider", "cncf.runtime.job.durable.provider")
      val codec = success(CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
      When("documents resolve their exact target and the canonical argv crosses its envelope codec")
      val resolved = spellings.map(spelling => success(decodeBindings(Vector(spelling -> ConfigurationValue.StringValue(providerId)), target, identity)))
      val assignment = success(codec.decode(s"--textus.binding=@s/platform/default:${JobDurableRuntimeConfiguration.providerKey}=$providerId"))
      val global = codec.decode(s"--textus.binding=${JobDurableRuntimeConfiguration.providerKey}=$providerId")
      val component = success(CncfConfigurationTarget.ComponentClass.create(ComponentId("org.goldenport.cncf.test.Provider")))
      val forbidden = decodeBindings(Vector(JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(providerId)), component, identity)
      val foreignidentity = success(SubsystemInstanceId.create("foreign", "default"))
      val foreign = success(decodeBindings(Vector(JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(providerId)), target, foreignidentity))
      val diagnostics = resolved.map(value => success(CncfConfigurationBindingDiagnosticCodec.encode(success(ConfigurationBindingTrace.from(value)))))
      Then("aliases share the witness canonical argv retains the exact identity and confidential trace values are redacted")
      resolved.foreach(value => success(JobDurableRuntimeConfiguration.from(value)).providerid shouldBe Some(providerId))
      assignment.rawValue shouldBe providerId
      success(codec.encode(assignment)) shouldBe s"--textus.binding=@s/platform/default:${JobDurableRuntimeConfiguration.providerKey}=$providerId"
      global.isSuccess shouldBe false
      forbidden.isSuccess shouldBe false
      success(JobDurableRuntimeConfiguration.from(foreign)).providerid shouldBe None
      diagnostics.foreach { text => text should include("redacted"); text should not include providerId }
    }
  }
  "Q6 Seeded shrinking whole-value bounds" must _scenario("Q6", "R2,R19") {
    "decode exact valid values and refuse invalid whole values with redacted provenance" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md R2,R19 Q6: generated bounds with seed 69709702 and ordinary shrinking")
      val property = Prop.forAll(Gen.chooseNum(-2048, 2048), Gen.chooseNum(0L, 86401000L)) { (limit, period) =>
        val decodedlimit = JobDurableRuntimeConfiguration.candidates.codec.decode(ConfigurationValue.NumberValue(BigDecimal(limit)))
        val decodedperiod = JobDurableRuntimeConfiguration.period.codec.decode(ConfigurationValue.NumberValue(BigDecimal(period)))
        val resolved = success(bindings(JobDurableRuntimeConfiguration.providerKey -> ConfigurationValue.StringValue(providerId)))
        val diagnostic = success(CncfConfigurationBindingDiagnosticCodec.encode(success(ConfigurationBindingTrace.from(resolved))))
        decodedlimit.toOption == Option.when(limit >= 1 && limit <= 1024)(limit) &&
          decodedperiod.toOption == Option.when(period >= 1000L && period <= 86400000L)(period) &&
          !diagnostic.contains(providerId) && diagnostic.contains("redacted") &&
          (decodedlimit.isSuccess || JobDurableRuntimeFixtures.failureConclusion(decodedlimit).observation.getEffectiveMessage.contains(JobDurableRuntimeConfiguration.candidatesKey)) &&
          (decodedperiod.isSuccess || JobDurableRuntimeFixtures.failureConclusion(decodedperiod).observation.getEffectiveMessage.contains(JobDurableRuntimeConfiguration.periodKey))
      }
      When("the seeded whole-value property runs with at least 24 successful cases")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709702L)), property)
      Then("all whole values preserve the inclusive grammar and confidential projection")
      checked.passed shouldBe true
    }
  }
}
