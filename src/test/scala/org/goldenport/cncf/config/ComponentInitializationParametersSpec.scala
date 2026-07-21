package org.goldenport.cncf.config

import java.lang.reflect.Modifier
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentInitializationParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _r3a_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, rule:R3a, phase:47, slice:CIP-02")

  "Component initialization parameters" should {
    "resolve required typed declarations into an immutable snapshot" must _r3a_metadata {
      "when generated string integer and boolean values are admitted" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; declared typed keys and a bounded resolver")
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

    "represent optional absence while preserving structured required and malformed failures" must _r3a_metadata {
      "when values are absent or fail their declared decoder" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; required optional and malformed declarations")
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
        optional.toOption shouldBe Some(
          ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
        )
        _failure_taxonomy(malformed) shouldBe _configuration_invalid_taxonomy
      }
    }

    "reject duplicate declarations and keys outside the validated snapshot" must _r3a_metadata {
      "when duplicate names or a reconstructed key are supplied" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; declaration identity and duplicate-name constraints")
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
        declared.toOption shouldBe Some(
          ComponentParameterResolution(
            Some("strict"),
            ComponentParameterProvenance.RuntimeConfiguration
          )
        )
        _failure_taxonomy(reconstructed) shouldBe _configuration_invalid_taxonomy
      }
    }

    "expose only typed lookup and bounded structural metadata" must _r3a_metadata {
      "when the public snapshot and provenance surfaces are inspected" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; a component-visible snapshot type")
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
