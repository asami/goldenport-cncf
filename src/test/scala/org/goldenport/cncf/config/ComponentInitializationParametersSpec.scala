package org.goldenport.cncf.config

import java.lang.reflect.Modifier
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ComponentParameterBootstrapObservation
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentInitializationParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e8_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E8, rules:R3a,R10, phase:47, slices:CIP-02,CIP-07,CIP-08")
  private val _e11_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E11, rules:R3a,R5,R10, phase:47, slices:CIP-06,CIP-07,CIP-08")

  "Component initialization parameters" should {
    "E8 resolve required typed declarations into an immutable snapshot" must _e8_metadata {
      "when generated string integer and boolean values are admitted" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E8; declared typed keys and a bounded resolver")
        val property = Prop.forAll(
          Gen.alphaStr.suchThat(_.nonEmpty),
          Gen.choose(-100000, 100000),
          Gen.oneOf(true, false)
        ) { (textvalue, intvalue, booleanvalue) =>
          val textkey = ComponentParameterKey.requiredString("provider.name")
          val intkey = ComponentParameterKey.requiredInt("provider.limit")
          val booleankey = ComponentParameterKey.requiredBoolean("provider.enabled")
          val resolver = _resolver(
            "provider.name" -> _candidate(textvalue),
            "provider.limit" -> _candidate(intvalue.toString),
            "provider.enabled" -> _candidate(booleanvalue.toString)
          )

          ComponentInitializationParameters
            .create(Vector(textkey, intkey, booleankey), resolver)
            .toOption
            .exists { parameters =>
              parameters.resolve(textkey).toOption.contains(
                ComponentParameterResolution(
                  Some(textvalue),
                  ComponentParameterProvenance.RuntimeConfiguration
                )
              ) &&
              parameters.resolve(intkey).toOption.contains(
                ComponentParameterResolution(
                  Some(intvalue),
                  ComponentParameterProvenance.RuntimeConfiguration
                )
              ) &&
              parameters.resolve(booleankey).toOption.contains(
                ComponentParameterResolution(
                  Some(booleanvalue),
                  ComponentParameterProvenance.RuntimeConfiguration
                )
              )
            }
        }

        When("the typed snapshot property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("all decoded values retain their declared types and bounded provenance")
        checked.passed shouldBe true
      }
    }

    "E8 represent optional absence while preserving structured required and malformed failures" must _e8_metadata {
      "when values are absent or fail their declared decoder" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E8; required optional and malformed declarations")
        val requiredkey = ComponentParameterKey.requiredString("provider.required")
        val optionalkey = ComponentParameterKey.optionalString("provider.optional")
        val malformedkey = ComponentParameterKey.requiredInt("provider.limit")
        val emptyresolver = _resolver()
        val malformedresolver = _resolver("provider.limit" -> _candidate("not-an-integer"))

        When("CNCF resolves each declaration")
        val required = emptyresolver.resolve(requiredkey)
        val optional = emptyresolver.resolve(optionalkey)
        val malformed = malformedresolver.resolve(malformedkey)

        Then("optional absence is typed and expected failures remain Consequence failures")
        _failure_taxonomy(required) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(required) shouldBe "missing"
        optional.toOption shouldBe Some(
          ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
        )
        _failure_taxonomy(malformed) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(malformed) shouldBe "malformed"
      }
    }

    "E8 reject duplicate declarations and keys outside the validated snapshot" must _e8_metadata {
      "when duplicate names or a reconstructed key are supplied" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E8; declaration identity and duplicate-name constraints")
        val declaredkey = ComponentParameterKey.requiredString("provider.mode")
        val duplicatekey = ComponentParameterKey.optionalString("provider.mode")
        val resolver = _resolver("provider.mode" -> _candidate("strict"))

        When("CNCF builds a snapshot and callers use declared or reconstructed keys")
        val duplicate = ComponentInitializationParameters.create(
          Vector(declaredkey, duplicatekey),
          resolver
        )
        val snapshot = ComponentInitializationParameters.create(Vector(declaredkey), resolver)
        val declared = snapshot.flatMap(_.resolve(declaredkey))
        val reconstructed = snapshot.flatMap(
          _.resolve(ComponentParameterKey.requiredString("provider.mode"))
        )

        Then("duplicate declarations and undeclared identities fail without weakening declared lookup")
        _failure_taxonomy(duplicate) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(duplicate) shouldBe "ambiguous"
        declared.toOption shouldBe Some(
          ComponentParameterResolution(
            Some("strict"),
            ComponentParameterProvenance.RuntimeConfiguration
          )
        )
        _failure_taxonomy(reconstructed) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(reconstructed) shouldBe "rejected"
      }
    }

    "E8 expose only typed lookup and bounded structural metadata" must _e8_metadata {
      "when the public snapshot and provenance surfaces are inspected" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E8; a component-visible snapshot type")
        val disallowed = Set(
          "entries",
          "raw",
          "toMap",
          "configuration",
          "resolvedConfiguration",
          "source"
        )

        When("the declared public methods and provenance tokens are collected")
        val publicmethods = classOf[ComponentInitializationParameters]
          .getDeclaredMethods
          .filter(method => Modifier.isPublic(method.getModifiers))
          .map(_.getName)
          .toSet
        val provenancetokens = ComponentParameterProvenance.values.map(_.token).toVector

        Then("no raw map accessor exists and provenance remains finite and non-physical")
        publicmethods.intersect(disallowed) shouldBe empty
        publicmethods should contain allOf ("resolve", "size", "isEmpty")
        provenancetokens shouldBe Vector(
          "packaged-default",
          "assembly-default",
          "subsystem-instance",
          "runtime-configuration",
          "test-overlay",
          "absent"
        )
      }
    }

    "E11 keep secret-reference and confidential declarations opaque" must _e11_metadata {
      "when initialization sources contain credential locators or confidential material" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R5,R10; Example: E11; dedicated secret-reference and denied confidential declarations")
        val locator = "file:///private/runtime/provider-token"
        val confidentialvalue = "embedded-private-credential"
        val secretkey = ComponentParameterKey.requiredSecretReference("provider.token-ref")
        val optionalsecretkey = ComponentParameterKey.optionalSecretReference("provider.optional-token-ref")
        val confidentialkey = ComponentParameterKey.confidentialRequired("provider.embedded-token")
        val secretresolver = _resolver("provider.token-ref" -> _candidate(locator))
        val confidentialresolver = _resolver(
          "provider.embedded-token" -> _candidate(confidentialvalue)
        )

        When("CNCF resolves the secret reference, optional absence, and confidential denial")
        val secret = secretresolver.resolve(secretkey)
        val absent = secretresolver.resolve(optionalsecretkey)
        val confidential = confidentialresolver.resolve(confidentialkey)

        Then("only the opaque reference crosses the boundary and all public rendering is payload-safe")
        secretkey.confidentiality shouldBe ComponentParameterConfidentiality.Secret
        optionalsecretkey.confidentiality shouldBe ComponentParameterConfidentiality.Secret
        confidentialkey.confidentiality shouldBe ComponentParameterConfidentiality.Confidential
        val resolution = secret.toOption.value
        val reference = resolution.value.value
        reference.toString should not include locator
        resolution.toString should not include locator
        Record.data("reference" -> reference).show should not include locator
        absent.toOption shouldBe Some(
          ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
        )
        val confidentialdisplay = _failure_display(confidential)
        _diagnostic_key(confidential) shouldBe "rejected"
        confidentialdisplay should not include confidentialvalue
        confidentialdisplay should not include "/private/runtime"
      }
    }

    "E11 reject malformed secret references without echoing source values" must _e11_metadata {
      "when a selected initialization source is not a secret-reference string" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R5,R10; Example: E11; a malformed selected secret-reference value")
        val key = ComponentParameterKey.requiredSecretReference("provider.token-ref")
        val resolver = _resolver(
          "provider.token-ref" -> ComponentParameterCandidate(
            ConfigurationValue.NumberValue(BigDecimal(314159)),
            ComponentParameterProvenance.RuntimeConfiguration
          )
        )

        When("the dedicated secret-reference decoder rejects the value")
        val result = resolver.resolve(key)

        Then("the structured failure identifies the declaration contract without exposing the value")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(result) shouldBe "malformed"
        _failure_display(result) should not include "314159"
      }
    }

    "E11 sanitize component-owned decoder failures before diagnostic projection" must _e11_metadata {
      "when a decoder failure message contains the selected payload and a physical source" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R5,R10; Example: E11; an untrusted decoder failure message")
        val payload = "credential-value-from-/private/runtime/provider.conf"
        val key = ComponentParameterKey.required(
          "provider.custom",
          ComponentParameterDecoder[String](_ =>
            Consequence.configurationInvalid(s"custom decoder rejected $payload")
          )
        )
        val resolver = _resolver("provider.custom" -> _candidate(payload))

        When("the CNCF resolver converts the decoder failure at its boundary")
        val result = resolver.resolve(key)
        val diagnostic = _diagnostic(result)

        Then("the standard Conclusion identifies a malformed declaration without retaining decoder payload text")
        diagnostic.diagnosticKey shouldBe "malformed"
        diagnostic.parameter shouldBe Some("provider.custom")
        diagnostic.policy shouldBe Some(ComponentParameterDiagnostics.POLICY)
        diagnostic.reason shouldBe Some("malformed")
        _diagnostic_provenance(result) shouldBe Some(
          ComponentParameterProvenance.RuntimeConfiguration
        )
        diagnostic.toRecord.print should not include payload
        _failure_display(result) should not include payload
        _failure_display(result) should not include "/private/runtime"
      }

      "when a decoder throws an exception containing the selected payload and source" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R5,R10; Example: E11; a throwing untrusted decoder")
        val payload = "credential-value-from-/private/runtime/provider.conf"
        val key = ComponentParameterKey.required(
          "provider.throwing",
          ComponentParameterDecoder[String](_ =>
            throw new IllegalArgumentException(s"custom decoder rejected $payload")
          )
        )
        val resolver = _resolver("provider.throwing" -> _candidate(payload))
        val metricsbefore = RuntimeDashboardMetrics
          .componentInitializationParameterDiagnosticCounts
          .getOrElse("malformed", 0L)

        When("the CNCF resolver and bootstrap observer handle the thrown exception")
        val result = ComponentInitializationParameters.create(Vector(key), resolver)
        ComponentParameterBootstrapObservation.record(
          ComponentId("throwing_decoder_probe"),
          ComponentInstanceId("throwing_decoder_probe", "default"),
          Vector(key),
          result
        )
        val diagnosticrecord = RuntimeDashboardMetrics
          .componentInitializationParameterDiagnosticRecords
          .getOrElse("malformed", fail("expected malformed bootstrap diagnostic"))
          .print

        Then("the exception becomes a payload-safe malformed Conclusion with bounded provenance and metrics")
        _diagnostic_key(result) shouldBe "malformed"
        _diagnostic_provenance(result) shouldBe Some(
          ComponentParameterProvenance.RuntimeConfiguration
        )
        RuntimeDashboardMetrics
          .componentInitializationParameterDiagnosticCounts
          .getOrElse("malformed", 0L) shouldBe metricsbefore + 1L
        diagnosticrecord should include ("runtime-configuration")
        diagnosticrecord should not include payload
        diagnosticrecord should not include "/private/runtime"
        _failure_display(result) should not include payload
      }
    }
  }

  private def _candidate(
    value: String
  ): ComponentParameterCandidate =
    ComponentParameterCandidate(
      ConfigurationValue.StringValue(value),
      ComponentParameterProvenance.RuntimeConfiguration
    )

  private val _configuration_invalid_taxonomy =
    Taxonomy(Taxonomy.Category.Configuration, Taxonomy.Symptom.Invalid)

  private def _failure_taxonomy[A](
    consequence: Consequence[A]
  ): Taxonomy =
    consequence match {
      case Consequence.Failure(conclusion) => conclusion.observation.taxonomy
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }

  private def _failure_display[A](
    consequence: Consequence[A]
  ): String =
    consequence match {
      case Consequence.Failure(conclusion) => conclusion.display
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }

  private def _diagnostic_key[A](
    consequence: Consequence[A]
  ): String =
    _diagnostic(consequence).diagnosticKey

  private def _diagnostic[A](
    consequence: Consequence[A]
  ): ConclusionDiagnostics.Classification =
    consequence match {
      case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion)
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }

  private def _diagnostic_provenance[A](
    consequence: Consequence[A]
  ): Option[ComponentParameterProvenance] =
    consequence match {
      case Consequence.Failure(conclusion) => ComponentParameterDiagnostics.provenance(conclusion)
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }

  private def _resolver(
    entries: (String, ComponentParameterCandidate)*
  ): ComponentParameterResolver =
    new ComponentParameterResolver() {
      private val _entries = entries.toMap

      protected def lookup_parameter(
        name: String
      ): Consequence[Option[ComponentParameterCandidate]] =
        Consequence.success(_entries.get(name))
    }
}
