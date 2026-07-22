package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  ComponentInstanceMetadata,
  ComponentletDescriptor
}
import org.goldenport.cncf.subsystem.GenericSubsystemComponentBinding
import org.goldenport.cncf.observability.ConclusionDiagnostics
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
final class ComponentParameterContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E1, rules:R3a, phase:47, slice:CIP-04")
  private val _e2_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E2, rules:R3a, phase:47, slice:CIP-04")
  private val _e3_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E3, rules:R3a, phase:47, slices:CIP-04,CIP-07")
  private val _e4_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E4, rules:R3a, phase:47, slices:CIP-04,CIP-07")
  private val _e5_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E5, rules:R3a, phase:47, slice:CIP-04")
  private val _e6_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E6, rules:R3a, phase:47, slice:CIP-04")

  "A component initialization parameter context" should {
    "E1 select packaged descriptor ownership for primary and componentlet identities" must _e1_metadata {
      "when one admitted assembly instance owns both participants" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; one descriptor with a primary and componentlet")
        val descriptor = ComponentDescriptor(
          name = Some("provider-artifact"),
          componentName = Some("provider"),
          componentlets = Vector(ComponentletDescriptor("provider_admin"))
        )
        val metadata = ComponentInstanceMetadata("provider", "tenant_a")

        When("CNCF selects contexts for the primary and componentlet")
        val primary = ComponentParameterContext.select(
          ComponentId("provider"),
          ComponentInstanceId("provider", "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )
        val componentlet = ComponentParameterContext.select(
          ComponentId("provider_admin"),
          ComponentInstanceId("provider_admin", "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )

        Then("both contexts retain their exact runtime participant identity and one owning descriptor")
        primary.toOption.map(_.componentInstanceId) shouldBe Some(
          ComponentInstanceId("provider", "tenant_a")
        )
        componentlet.toOption.map(_.componentInstanceId) shouldBe Some(
          ComponentInstanceId("provider_admin", "tenant_a")
        )
        componentlet.toOption.map(_.descriptor) shouldBe Some(descriptor)
      }
    }

    "E2 isolate identical parameter keys for every generated pair of named instances" must _e2_metadata {
      "when SAR bindings carry distinct values" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; two named SAR bindings of one component type")
        val property = Prop.forAll(
          Gen.alphaStr.suchThat(_.nonEmpty),
          Gen.alphaStr.suchThat(_.nonEmpty)
        ) { (firstvalue, secondvalue) =>
          val descriptor = _descriptor
          val bindings = Vector(
            GenericSubsystemComponentBinding(
              "provider",
              instance = Some("first"),
              config = Map("provider.mode" -> firstvalue)
            ),
            GenericSubsystemComponentBinding(
              "provider",
              instance = Some("second"),
              config = Map("provider.mode" -> secondvalue)
            )
          )
          val metadata = bindings.map(_.instanceMetadata)
          val first = _context("first", Vector(descriptor), metadata).flatMap(_resolve_mode)
          val second = _context("second", Vector(descriptor), metadata).flatMap(_resolve_mode)
          first.toOption.flatMap(_.value).contains(firstvalue) &&
            second.toOption.flatMap(_.value).contains(secondvalue)
        }

        When("the component-instance isolation property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("each resolver reads only the settings selected for its own ComponentInstanceId")
        checked.passed shouldBe true
      }
    }

    "E3 reject missing and ambiguous packaged descriptor ownership" must _e3_metadata {
      "when no descriptor or two descriptors claim the target component" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; missing and duplicate descriptor candidates")
        val componentid = ComponentId("provider")
        val instanceid = ComponentInstanceId("provider", "default")
        val metadata = Vector(ComponentInstanceMetadata("provider"))

        When("CNCF selects each invalid descriptor context")
        val missing = ComponentParameterContext.select(
          componentid,
          instanceid,
          Vector.empty,
          metadata
        )
        val ambiguous = ComponentParameterContext.select(
          componentid,
          instanceid,
          Vector(_descriptor, _descriptor.copy(version = Some("2"))),
          metadata
        )

        Then("both invalid contexts fail structurally before any layer can resolve")
        _failure_taxonomy(missing) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(ambiguous) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(missing) shouldBe "missing"
        _diagnostic_key(ambiguous) shouldBe "ambiguous"
      }
    }

    "E4 reject missing ambiguous and mismatched component-instance metadata" must _e4_metadata {
      "when assembly metadata cannot identify exactly one requested instance" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; one descriptor and invalid instance selections")
        val descriptor = Vector(_descriptor)
        val componentid = ComponentId("provider")
        val instanceid = ComponentInstanceId("provider", "first")
        val first = ComponentInstanceMetadata("provider", "first")

        When("CNCF selects missing duplicate and inconsistent identities")
        val missing = ComponentParameterContext.select(
          componentid,
          instanceid,
          descriptor,
          Vector(ComponentInstanceMetadata("provider", "second"))
        )
        val ambiguous = ComponentParameterContext.select(
          componentid,
          instanceid,
          descriptor,
          Vector(first, first.copy(config = Map("provider.mode" -> "other")))
        )
        val mismatched = ComponentParameterContext.select(
          componentid,
          ComponentInstanceId("other", "first"),
          descriptor,
          Vector(first)
        )

        Then("all invalid selections remain structured configuration failures")
        Vector(missing, ambiguous, mismatched).foreach { result =>
          _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
        }
        _diagnostic_key(missing) shouldBe "missing"
        _diagnostic_key(ambiguous) shouldBe "ambiguous"
        _diagnostic_key(mismatched) shouldBe "rejected"
      }
    }

    "E5 prevent one SAR component instance from supplying another instance's settings" must _e5_metadata {
      "when only the non-target instance defines the requested key" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; isolated SAR metadata with one configured instance")
        val metadata = Vector(
          GenericSubsystemComponentBinding(
            "provider",
            instance = Some("first"),
            config = Map("provider.mode" -> "first-only")
          ).instanceMetadata,
          GenericSubsystemComponentBinding(
            "provider",
            instance = Some("second")
          ).instanceMetadata
        )

        When("the second component instance resolves the optional key")
        val resolution = _context("second", Vector(_descriptor), metadata)
          .flatMap(context => _layers(context).resolve(ComponentParameterKey.optionalString("provider.mode")))

        Then("the first instance value is absent rather than leaking across the instance boundary")
        resolution.toOption shouldBe Some(
          ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
        )
      }
    }

    "E6 keep CAR artifact identity separate from runtime component ownership" must _e6_metadata {
      "when descriptor name and componentName identify different concepts" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rule: R3a; a CAR artifact name distinct from its runtime component name")
        val descriptor = ComponentDescriptor(
          name = Some("provider-artifact"),
          componentName = Some("provider")
        )
        val metadata = ComponentInstanceMetadata(
          "provider",
          config = Map("provider.mode" -> "private-to-provider")
        )

        When("a component context is requested using only the artifact name")
        val result = ComponentParameterContext.select(
          ComponentId("provider_artifact"),
          ComponentInstanceId("provider_artifact", "default"),
          Vector(descriptor),
          Vector(metadata)
        )

        Then("the artifact name cannot claim the runtime component descriptor or settings")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
      }
    }
  }

  private val _descriptor = ComponentDescriptor(
    name = Some("provider-artifact"),
    componentName = Some("provider")
  )

  private val _configuration_invalid_taxonomy =
    Taxonomy(Taxonomy.Category.Configuration, Taxonomy.Symptom.Invalid)

  private def _context(
    instance: String,
    descriptors: Vector[ComponentDescriptor],
    metadata: Vector[ComponentInstanceMetadata]
  ): Consequence[ComponentParameterContext] =
    ComponentParameterContext.select(
      ComponentId("provider"),
      ComponentInstanceId("provider", instance),
      descriptors,
      metadata
    )

  private def _resolve_mode(
    context: ComponentParameterContext
  ): Consequence[ComponentParameterResolution[String]] =
    _layers(context).resolve(ComponentParameterKey.requiredString("provider.mode"))

  private def _layers(
    context: ComponentParameterContext
  ): ComponentParameterResolutionLayers =
    ComponentParameterResolutionLayers.create(
      ComponentPackagedParameterDefaults.fromConfiguration(Configuration.empty),
      ComponentAssemblyParameterDefaults.fromConfiguration(Configuration.empty),
      context,
      ComponentRuntimeParameterProjection.create(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        None
      )
    )

  private def _failure_taxonomy[A](
    consequence: Consequence[A]
  ): Taxonomy =
    consequence match {
      case Consequence.Failure(conclusion) => conclusion.observation.taxonomy
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }

  private def _diagnostic_key[A](
    consequence: Consequence[A]
  ): String =
    consequence match {
      case Consequence.Failure(conclusion) =>
        ConclusionDiagnostics.classify(conclusion).diagnosticKey
      case Consequence.Success(value) =>
        fail(s"expected structured failure, got success: $value")
    }
}
