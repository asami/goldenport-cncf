package org.goldenport.cncf.config

import java.nio.file.Paths
import org.goldenport.Consequence
import org.goldenport.configuration.{
  Configuration,
  ConfigurationOrigin,
  ConfigurationResolution,
  ConfigurationTrace,
  ConfigurationValue,
  ResolvedConfiguration
}
import org.goldenport.cncf.component.{
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  ComponentInstanceMetadata
}
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 22, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentParameterResolutionLayersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e9_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E9, rules:R3a,R10, phase:47, slices:CIP-03,CIP-08")

  "Component initialization parameter resolution layers" should {
    "E9 preserve the bounded provenance of every admitted layer" must _e9_metadata {
      "when each layer is the only source of a declared value" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; one declared key and every admitted layer")
        val key = ComponentParameterKey.requiredString("provider.mode")
        val cases = Vector(
          _layers(packageddefaults = _configuration("provider.mode" -> "packaged")) ->
            ("packaged" -> ComponentParameterProvenance.PackagedDefault),
          _layers(assemblydefaults = _configuration("provider.mode" -> "assembly")) ->
            ("assembly" -> ComponentParameterProvenance.AssemblyDefault),
          _layers(subsysteminstance = Map("provider.mode" -> "subsystem")) ->
            ("subsystem" -> ComponentParameterProvenance.SubsystemInstance),
          _layers(runtimeconfiguration = _resolved("provider.mode" -> "runtime")) ->
            ("runtime" -> ComponentParameterProvenance.RuntimeConfiguration),
          _layers(testdescriptor = Some(_test_descriptor("provider.mode" -> "test"))) ->
            ("test" -> ComponentParameterProvenance.TestOverlay)
        )

        When("each fixed layer resolves the same typed key")
        val resolutions = cases.map { case (layers, expected) =>
          layers.resolve(key).toOption -> expected
        }

        Then("the selected value reports only its bounded logical provenance")
        resolutions.foreach { case (resolution, (value, provenance)) =>
          resolution shouldBe Some(ComponentParameterResolution(Some(value), provenance))
        }
      }
    }

    "E9 select the highest admitted layer for every generated overlap" must _e9_metadata {
      "when every layer defines the same key" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; generated distinct values in all five layers")
        val property = Prop.forAll(Gen.alphaStr.suchThat(_.nonEmpty)) { base =>
          val values = Vector.tabulate(5)(index => s"$base-$index")
          val layers = _layers(
            packageddefaults = _configuration("provider.mode" -> values(0)),
            assemblydefaults = _configuration("provider.mode" -> values(1)),
            subsysteminstance = Map("provider.mode" -> values(2)),
            runtimeconfiguration = _resolved("provider.mode" -> values(3)),
            testdescriptor = Some(_test_descriptor("provider.mode" -> values(4)))
          )
          layers.resolve(ComponentParameterKey.requiredString("provider.mode")).toOption.contains(
            ComponentParameterResolution(Some(values(4)), ComponentParameterProvenance.TestOverlay)
          )
        }

        When("the precedence property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("the explicit test overlay always wins without caller-controlled ordering")
        checked.passed shouldBe true
      }
    }

    "E9 fall back through the fixed precedence order" must _e9_metadata {
      "when higher layers are removed one at a time" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; cumulative layer prefixes")
        val key = ComponentParameterKey.requiredString("provider.mode")
        val cases = Vector(
          _layers(packageddefaults = _configuration("provider.mode" -> "packaged")) -> "packaged",
          _layers(
            packageddefaults = _configuration("provider.mode" -> "packaged"),
            assemblydefaults = _configuration("provider.mode" -> "assembly")
          ) -> "assembly",
          _layers(
            packageddefaults = _configuration("provider.mode" -> "packaged"),
            assemblydefaults = _configuration("provider.mode" -> "assembly"),
            subsysteminstance = Map("provider.mode" -> "subsystem")
          ) -> "subsystem",
          _layers(
            packageddefaults = _configuration("provider.mode" -> "packaged"),
            assemblydefaults = _configuration("provider.mode" -> "assembly"),
            subsysteminstance = Map("provider.mode" -> "subsystem"),
            runtimeconfiguration = _resolved("provider.mode" -> "runtime")
          ) -> "runtime"
        )

        When("each cumulative prefix resolves the declaration")
        val values = cases.map { case (layers, expected) =>
          layers.resolve(key).toOption.flatMap(_.value) -> expected
        }

        Then("the last present fixed layer wins deterministically")
        values.foreach { case (actual, expected) => actual shouldBe Some(expected) }
      }
    }

    "E9 resolve a declared runtime key from nested configuration" must _e9_metadata {
      "when the typed parameter name is represented by nested objects" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; a nested runtime configuration value")
        val key = ComponentParameterKey.requiredBoolean("textus.debug.auth.enabled")
        val runtimeconfiguration = ResolvedConfiguration(
          Configuration(Map(
            "textus" -> ConfigurationValue.ObjectValue(Map(
              "debug" -> ConfigurationValue.ObjectValue(Map(
                "auth" -> ConfigurationValue.ObjectValue(Map(
                  "enabled" -> ConfigurationValue.BooleanValue(true)
                ))
              ))
            ))
          )),
          ConfigurationTrace.empty
        )

        When("the fixed runtime layer resolves the declared typed key")
        val resolution = _layers(runtimeconfiguration = runtimeconfiguration).resolve(key)

        Then("the nested value retains runtime-configuration provenance")
        resolution.toOption shouldBe Some(
          ComponentParameterResolution(Some(true), ComponentParameterProvenance.RuntimeConfiguration)
        )
      }
    }

    "E9 prefer a direct runtime key over its nested representation" must _e9_metadata {
      "when one runtime layer contains conflicting direct and nested values" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; conflicting direct and nested runtime values")
        val name = "textus.debug.auth.enabled"
        val key = ComponentParameterKey.requiredBoolean(name)
        val runtimeconfiguration = ResolvedConfiguration(
          Configuration(Map(
            name -> ConfigurationValue.BooleanValue(false),
            "textus" -> ConfigurationValue.ObjectValue(Map(
              "debug" -> ConfigurationValue.ObjectValue(Map(
                "auth" -> ConfigurationValue.ObjectValue(Map(
                  "enabled" -> ConfigurationValue.BooleanValue(true)
                ))
              ))
            ))
          )),
          ConfigurationTrace.empty
        )

        When("the fixed runtime layer resolves the declared typed key")
        val resolution = _layers(runtimeconfiguration = runtimeconfiguration).resolve(key)

        Then("the direct exact-key value wins without changing its provenance")
        resolution.toOption shouldBe Some(
          ComponentParameterResolution(Some(false), ComponentParameterProvenance.RuntimeConfiguration)
        )
      }
    }

    "E9 reject a malformed or null higher layer instead of falling through" must _e9_metadata {
      "when a valid default is shadowed by an invalid runtime value" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; a valid packaged integer and invalid runtime values")
        val malformedlayers = _layers(
          packageddefaults = _configuration("provider.limit" -> "10"),
          runtimeconfiguration = _resolved("provider.limit" -> "invalid")
        )
        val nulllayers = _layers(
          packageddefaults = _configuration("provider.limit" -> "10"),
          runtimeconfiguration = ResolvedConfiguration(
            Configuration(Map("provider.limit" -> ConfigurationValue.NullValue)),
            ConfigurationTrace.empty
          )
        )

        When("the required integer declaration is resolved against malformed and explicit-null values")
        val malformed = malformedlayers.resolve(ComponentParameterKey.requiredInt("provider.limit"))
        val explicitnull = nulllayers.resolve(ComponentParameterKey.requiredInt("provider.limit"))

        Then("both present higher-precedence values remain structured configuration failures")
        _failure_taxonomy(malformed) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(explicitnull) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E9 project runtime values without retaining physical trace details" must _e9_metadata {
      "when resolved runtime configuration carries a physical source trace" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; runtime configuration with sensitive trace metadata")
        val key = "provider.mode"
        val trace = ConfigurationTrace(Map(
          key -> ConfigurationResolution(
            key,
            ConfigurationValue.StringValue("trace-value"),
            ConfigurationOrigin.Resource,
            Nil,
            sourceType = Some("file"),
            sourceId = Some("/private/runtime/config.yaml")
          )
        ))
        val runtime = ResolvedConfiguration(_configuration(key -> "runtime-value"), trace)

        When("the runtime layer is projected and resolved")
        val layers = _layers(runtimeconfiguration = runtime)
        val resolution = layers.resolve(ComponentParameterKey.requiredString(key))
        val fieldtypes = classOf[ComponentRuntimeParameterConfiguration].getDeclaredFields.map(_.getType).toSet

        Then("only the resolved value and bounded runtime provenance remain")
        resolution.toOption shouldBe Some(
          ComponentParameterResolution(Some("runtime-value"), ComponentParameterProvenance.RuntimeConfiguration)
        )
        fieldtypes should not contain classOf[ResolvedConfiguration]
        fieldtypes should not contain classOf[ConfigurationTrace]
      }
    }

    "E9 admit a test overlay only through an explicit test descriptor" must _e9_metadata {
      "when the same layers are resolved without and with a descriptor" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; runtime configuration and an optional explicit test descriptor")
        val key = ComponentParameterKey.requiredString("provider.mode")
        val without = _layers(runtimeconfiguration = _resolved("provider.mode" -> "runtime"))
        val descriptor = _test_descriptor("provider.mode" -> "test")
        val mergedruntime = _resolved("provider.mode" -> "test")
        val projection = ComponentRuntimeParameterProjection.create(mergedruntime, Some(descriptor))
        val withdescriptor = _layers(runtimeconfiguration = mergedruntime, testdescriptor = Some(descriptor))

        When("both fixed source sets resolve the declaration")
        val runtimevalue = without.resolve(key)
        val testvalue = withdescriptor.resolve(key)

        Then("descriptor-owned values are removed from runtime and retain only test-overlay provenance")
        runtimevalue.toOption shouldBe Some(
          ComponentParameterResolution(Some("runtime"), ComponentParameterProvenance.RuntimeConfiguration)
        )
        projection.runtimeConfiguration._configuration.get("provider.mode") shouldBe None
        projection.testOverlay._configuration.get("provider.mode") shouldBe Some(
          ConfigurationValue.StringValue("test")
        )
        testvalue.toOption shouldBe Some(
          ComponentParameterResolution(Some("test"), ComponentParameterProvenance.TestOverlay)
        )
      }
    }

    "E9 ignore ambient and unsupported sources" must _e9_metadata {
      "when an optional key exists only as an ambient system property" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E9; empty admitted layers and an ambient property")
        val name = "cncf.phase47.unsupported.ambient"
        val previous = Option(System.getProperty(name))
        val layertypes = classOf[ComponentParameterResolutionLayers]
          .getDeclaredConstructors
          .flatMap(_.getParameterTypes)
          .toVector
        val subsystemfactorytypes = SubsystemComponentInstanceParameterSettings
          .getClass
          .getDeclaredMethods
          .flatMap(_.getParameterTypes)
          .toVector

        try {
          System.setProperty(name, "ambient-value")

          When("the fixed-layer resolver resolves the optional declaration")
          val resolution = _layers().resolve(ComponentParameterKey.optionalString(name))

          Then("ambient state does not become an initialization parameter layer")
          resolution.toOption shouldBe Some(
            ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
          )
          layertypes should contain theSameElementsInOrderAs Vector(
            classOf[ComponentPackagedParameterDefaults],
            classOf[ComponentAssemblyParameterDefaults],
            classOf[ComponentParameterContext],
            classOf[ComponentRuntimeParameterConfiguration],
            classOf[ComponentTestParameterOverlay]
          )
          subsystemfactorytypes should contain (classOf[ComponentParameterContext])
          subsystemfactorytypes should not contain classOf[Configuration]
        } finally {
          previous.fold(System.clearProperty(name))(System.setProperty(name, _))
        }
      }
    }
  }

  private val _configuration_invalid_taxonomy =
    Taxonomy(Taxonomy.Category.Configuration, Taxonomy.Symptom.Invalid)

  private def _configuration(
    entries: (String, String)*
  ): Configuration =
    Configuration(entries.map { case (key, value) =>
      key -> ConfigurationValue.StringValue(value)
    }.toMap)

  private def _resolved(
    entries: (String, String)*
  ): ResolvedConfiguration =
    ResolvedConfiguration(_configuration(entries*), ConfigurationTrace.empty)

  private def _test_descriptor(
    entries: (String, String)*
  ): RuntimeTestDescriptor =
    RuntimeTestDescriptor(
      Paths.get("explicit-test.yaml"),
      Record.empty,
      entries.toMap,
      None
    )

  private def _layers(
    packageddefaults: Configuration = Configuration.empty,
    assemblydefaults: Configuration = Configuration.empty,
    subsysteminstance: Map[String, String] = Map.empty,
    runtimeconfiguration: ResolvedConfiguration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    testdescriptor: Option[RuntimeTestDescriptor] = None
  ): ComponentParameterResolutionLayers =
    ComponentParameterResolutionLayers.create(
      ComponentPackagedParameterDefaults.fromConfiguration(packageddefaults),
      ComponentAssemblyParameterDefaults.fromConfiguration(assemblydefaults),
      _context(subsysteminstance),
      ComponentRuntimeParameterProjection.create(runtimeconfiguration, testdescriptor)
    )

  private def _context(
    subsysteminstance: Map[String, String]
  ): ComponentParameterContext =
    ComponentParameterContext.select(
      ComponentId("provider"),
      ComponentInstanceId("provider", "default"),
      Vector(ComponentDescriptor(name = Some("provider"), componentName = Some("provider"))),
      Vector(ComponentInstanceMetadata("provider", config = subsysteminstance))
    ).toOption.get

  private def _failure_taxonomy[A](
    consequence: Consequence[A]
  ): Taxonomy =
    consequence match {
      case Consequence.Failure(conclusion) => conclusion.observation.taxonomy
      case Consequence.Success(value) => fail(s"expected structured failure, got success: $value")
    }
}
