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
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentParameterContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e10_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E10, rules:R3a,R10, phase:47, slices:CIP-04,CIP-07,CIP-08")

  "A component initialization parameter context" should {
    "E10 select packaged descriptor ownership for primary and componentlet identities" must _e10_metadata {
      "when one admitted assembly instance owns both participants" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; one descriptor with a primary and componentlet")
        val descriptor = ComponentDescriptor(
          name = Some("provider-artifact"),
          componentName = Some("provider"),
          componentlets = Vector(ComponentletDescriptor("provider_admin"))
        )
        val metadata = ComponentInstanceMetadata("provider", "tenant_a")

        When("CNCF selects contexts for the primary and componentlet")
        val primary = ComponentParameterContext.select(
          ComponentId("org.goldenport.cncf.test.Provider"),
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider"), "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )
        val componentlet = ComponentParameterContext.select(
          ComponentId("org.goldenport.cncf.test.ProviderAdmin"),
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider_admin"), "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )

        Then("both contexts retain their exact runtime participant identity and one owning descriptor")
        primary.toOption.map(_.componentInstanceId) shouldBe Some(
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider"), "tenant_a")
        )
        componentlet.toOption.map(_.componentInstanceId) shouldBe Some(
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider_admin"), "tenant_a")
        )
        componentlet.toOption.map(_.descriptor) shouldBe Some(descriptor)
      }
    }

    "E10 isolate identical parameter keys for every generated pair of named instances" must _e10_metadata {
      "when SAR bindings carry distinct values" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; two named SAR bindings of one component type")
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

    "E10 reject missing and ambiguous packaged descriptor ownership" must _e10_metadata {
      "when no descriptor or two descriptors claim the target component" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; missing and duplicate descriptor candidates")
        val componentid = ComponentId("org.goldenport.cncf.test.Provider")
        val instanceid = ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider"), "default")
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

    "E10 reject missing ambiguous and mismatched component-instance metadata" must _e10_metadata {
      "when assembly metadata cannot identify exactly one requested instance" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; one descriptor and invalid instance selections")
        val descriptor = Vector(_descriptor)
        val componentid = ComponentId("org.goldenport.cncf.test.Provider")
        val instanceid = ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider"), "first")
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
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("other"), "first"),
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

    "E10 prevent one SAR component instance from supplying another instance's settings" must _e10_metadata {
      "when only the non-target instance defines the requested key" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; isolated SAR metadata with one configured instance")
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

    "E10 keep CAR artifact identity separate from runtime component ownership" must _e10_metadata {
      "when descriptor name and componentName identify different concepts" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; a CAR artifact name distinct from its runtime component name")
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
          ComponentId("org.goldenport.cncf.test.ProviderArtifact"),
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider_artifact"), "default"),
          Vector(descriptor),
          Vector(metadata)
        )

        Then("the artifact name cannot claim the runtime component descriptor or settings")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E10 resolve a primary runtime component through its distinct CAR artifact identity" must _e10_metadata {
      "when the requested instance and assembly metadata use the artifact name" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; a primary runtime component with a distinct CAR artifact name")
        val descriptor = ComponentDescriptor(
          name = Some("textus-user-account"),
          componentName = Some("UserAccount")
        )
        val metadata = ComponentInstanceMetadata(
          "textus-user-account",
          "tenant_a",
          config = Map("user.account.mode" -> "artifact-bound")
        )

        When("CNCF selects the primary component context through its artifact-named instance")
        val result = ComponentParameterContext.select(
          ComponentId("org.goldenport.cncf.test.UserAccount"),
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-user-account"), "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )
        val resolved = result.flatMap { context =>
          _layers(context).resolve(
            ComponentParameterKey.requiredString("user.account.mode")
          )
        }

        Then("the context retains the requested artifact-named instance and resolves only its assembly configuration")
        result.toOption.map(_.componentInstanceId) shouldBe Some(
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-user-account"), "tenant_a")
        )
        result.toOption.map(_.descriptor) shouldBe Some(descriptor)
        resolved.toOption.map(_.value) shouldBe Some(Some("artifact-bound"))
      }
    }

    "E10 reject an artifact alias when it replaces a componentlet runtime identity" must _e10_metadata {
      "when the componentlet requests the owning artifact name as its instance identity" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E10; a descriptor primary and componentlet with a shared assembly artifact")
        val descriptor = ComponentDescriptor(
          name = Some("textus-user-account"),
          componentName = Some("UserAccount"),
          componentlets = Vector(ComponentletDescriptor("UserAccountAdmin"))
        )
        val metadata = ComponentInstanceMetadata(
          "textus-user-account",
          "tenant_a"
        )

        When("CNCF validates the componentlet context using the artifact-named instance")
        val result = ComponentParameterContext.select(
          ComponentId("org.goldenport.cncf.test.UserAccountAdmin"),
          ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-user-account"), "tenant_a"),
          Vector(descriptor),
          Vector(metadata)
        )

        Then("the artifact alias is rejected instead of replacing the componentlet identity")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
        _diagnostic_key(result) shouldBe "rejected"
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
      ComponentId("org.goldenport.cncf.test.Provider"),
      ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("provider"), instance),
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
