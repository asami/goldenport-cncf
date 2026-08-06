package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, ComponentInstanceId, ComponentInstanceMetadata}
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentParameterPathRouteSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e14_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E14, rules:R3a,R10, phase:12, slice:P12-S12C-1C-DYNAMIC-PATH")

  "Dynamic component initialization parameter paths" should {
    "E14 expand flat and nested dynamic names into typed immutable parameters" must _e14_metadata {
      "when one route discovers two user-defined execution classes" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E14; a bounded route with required model and optional tools leaves")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val tools = ComponentParameterPathLeaf.optionalString("tools").toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model, tools)
        ).toOption.get
        val runtime = ResolvedConfiguration(
          Configuration(Map(
            "textus.ai.execution-classes.standard-work.model" -> ConfigurationValue.StringValue("gpt-standard"),
            "textus" -> ConfigurationValue.ObjectValue(Map(
              "ai" -> ConfigurationValue.ObjectValue(Map(
                "execution-classes" -> ConfigurationValue.ObjectValue(Map(
                  "deep-thinking" -> ConfigurationValue.ObjectValue(Map(
                    "model" -> ConfigurationValue.StringValue("gpt-deep"),
                    "tools" -> ConfigurationValue.StringValue("web-search")
                  ))
                ))
              ))
            ))
          )),
          ConfigurationTrace.empty
        )

        When("CNCF discovers the dynamic names before resolving the declared leaves")
        val registered = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = runtime)
        ).toOption.get
        val parameters = registered.parameters.head
        val byname = parameters.segments.map(x => x.value -> x).toMap

        Then("both names are sorted and every leaf retains typed value and bounded provenance")
        parameters.segments.map(_.value) shouldBe Vector("deep-thinking", "standard-work")
        parameters.resolve(byname("standard-work"), model).toOption shouldBe Some(
          ComponentParameterResolution(Some("gpt-standard"), ComponentParameterProvenance.RuntimeConfiguration)
        )
        parameters.resolve(byname("standard-work"), tools).toOption shouldBe Some(
          ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
        )
        parameters.resolve(byname("deep-thinking"), model).toOption shouldBe Some(
          ComponentParameterResolution(Some("gpt-deep"), ComponentParameterProvenance.RuntimeConfiguration)
        )
        parameters.resolve(byname("deep-thinking"), tools).toOption shouldBe Some(
          ComponentParameterResolution(Some("web-search"), ComponentParameterProvenance.RuntimeConfiguration)
        )
      }
    }

    "E14 preserve fixed-layer precedence before compatibility alias preference" must _e14_metadata {
      "when a high-layer alias shadows a lower canonical leaf" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E14; canonical and compatibility route spellings across admitted layers")
        val model = ComponentParameterPathLeaf.requiredString(
          "model-name",
          Vector("modelName")
        ).toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model),
          prefixAliases = Vector("textus.runtime.ai.execution-classes")
        ).toOption.get
        val layers = _layers(
          packageddefaults = _configuration(
            "textus.ai.execution-classes.standard-work.model-name" -> "packaged-canonical"
          ),
          runtimeconfiguration = _resolved(
            "textus.runtime.ai.execution-classes.standard-work.modelName" -> "runtime-alias"
          )
        )

        When("the dynamic route is registered and resolved")
        val registered = ComponentParameterPathSchemaRegistration.registerC(Vector(route), layers).toOption.get
        val parameters = registered.parameters.head
        val segment = parameters.segments.head

        Then("the higher admitted layer wins before canonical spelling preference")
        parameters.resolve(segment, model).toOption shouldBe Some(
          ComponentParameterResolution(Some("runtime-alias"), ComponentParameterProvenance.RuntimeConfiguration)
        )
      }
    }

    "E14 reject unknown leaves invalid segments and reconstructed identities" must _e14_metadata {
      "when inputs escape the declared route schema or snapshot identity" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E14; one bounded route and invalid runtime paths")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model)
        ).toOption.get
        val reconstructed = ComponentParameterPathLeaf.requiredString("model").toOption.get

        When("CNCF validates route ownership and callers resolve by declared identity")
        val unknown = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard-work.command" -> "unsafe"
          ))
        )
        val invalid = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.bad name.model" -> "unsafe"
          ))
        )
        val valid = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard-work.model" -> "safe"
          ))
        ).toOption.get.parameters.head
        val identityresult = valid.resolve(valid.segments.head, reconstructed)

        Then("schema escapes and reconstructed identities are structured configuration failures")
        _failure_taxonomy(unknown) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(invalid) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(identityresult) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 reject duplicate and prefix-overlapping routes before discovery" must _e14_metadata {
      "when route ownership could interpret one path in more than one way" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E14; duplicate and nested-prefix route declarations")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val parent = ComponentParameterPathRoute.createC(
          "textus.ai",
          "profile",
          Vector(model)
        ).toOption.get
        val child = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model)
        ).toOption.get
        val layers = _layers(runtimeconfiguration = _resolved(
          "textus.ai.execution-classes.standard.model" -> "model"
        ))

        When("CNCF registers repeated identities or nested owned prefixes")
        val duplicate = ComponentParameterPathSchemaRegistration.registerC(Vector(parent, parent), layers)
        val nested = ComponentParameterPathSchemaRegistration.registerC(Vector(parent, child), layers)

        Then("both ambiguous schemas fail before any value is decoded")
        _failure_taxonomy(duplicate) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(nested) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 reject static and dynamic declarations that resolve to one canonical identity" must _e14_metadata {
      "when dynamic expansion produces a name already declared statically" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; a static declaration collides with an expanded canonical dynamic declaration")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model)
        ).toOption.get
        val static = ComponentParameterKey.requiredString(
          "textus.ai.execution-classes.standard.model"
        )
        val layers = _layers(runtimeconfiguration = _resolved(
          "textus.ai.execution-classes.standard.model" -> "standard"
        ))

        When("registration validates the complete static and expanded declaration family")
        val result = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          layers,
          Vector(static)
        )

        Then("the ambiguous canonical declaration fails before the dynamic snapshot is created")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 reject route-prefix scalars and malformed dynamic shapes" must _e14_metadata {
      "when a configuration value names the route itself or adds a third dynamic path segment" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; one route and values that cannot denote a segment and declared leaf")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model)
        ).toOption.get

        When("CNCF discovers scalar and extra-segment values under the route")
        val scalar = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes" -> "invalid"
          ))
        )
        val extra = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard.model.extra" -> "invalid"
          ))
        )

        Then("both values are rejected as invalid route shapes")
        _failure_taxonomy(scalar) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(extra) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 require every required dynamic sibling and preserve sensitivity semantics" must _e14_metadata {
      "when a discovered segment omits a required sibling or declares confidential and secret leaves" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R5,R10; required, secret, and confidential leaf declarations")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val tools = ComponentParameterPathLeaf.requiredString("tools").toOption.get
        val requiredRoute = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model, tools)
        ).toOption.get
        val secret = ComponentParameterPathLeaf.requiredSecretReference("credential").toOption.get
        val secretRoute = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(secret)
        ).toOption.get
        val confidential = ComponentParameterPathLeaf.confidentialRequired("token").toOption.get
        val confidentialRoute = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(confidential)
        ).toOption.get

        When("a segment is incomplete and the sensitive leaves are resolved")
        val incomplete = ComponentParameterPathSchemaRegistration.registerC(
          Vector(requiredRoute),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard.model" -> "standard"
          ))
        )
        val secrets = ComponentParameterPathSchemaRegistration.registerC(
          Vector(secretRoute),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard.credential" -> "vault://private/credential"
          ))
        ).toOption.get.parameters.head
        val rejected = ComponentParameterPathSchemaRegistration.registerC(
          Vector(confidentialRoute),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard.token" -> "private"
          ))
        )

        Then("required siblings fail together, secret values remain typed, and confidential values stay unavailable")
        _failure_taxonomy(incomplete) shouldBe _configuration_invalid_taxonomy
        secrets.resolve(secrets.segments.head, secret).toOption.get.value should not be empty
        _failure_taxonomy(rejected) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 prefer canonical and direct values within one selected layer" must _e14_metadata {
      "when canonical aliases and direct nested configuration spellings coexist" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; one layer contains canonical aliases and a direct value overriding its nested counterpart")
        val model = ComponentParameterPathLeaf.requiredString(
          "model-name",
          Vector("modelName")
        ).toOption.get
        val route = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model)
        ).toOption.get

        When("the route resolves every candidate from the selected runtime layer")
        val canonical = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = _resolved(
            "textus.ai.execution-classes.standard.model-name" -> "canonical",
            "textus.ai.execution-classes.standard.modelName" -> "alias"
          ))
        ).toOption.get.parameters.head
        val direct = ComponentParameterPathSchemaRegistration.registerC(
          Vector(route),
          _layers(runtimeconfiguration = ResolvedConfiguration(
            Configuration(Map(
              "textus.ai.execution-classes.standard.model-name" -> ConfigurationValue.StringValue("direct"),
              "textus" -> ConfigurationValue.ObjectValue(Map(
                "ai" -> ConfigurationValue.ObjectValue(Map(
                  "execution-classes" -> ConfigurationValue.ObjectValue(Map(
                    "standard" -> ConfigurationValue.ObjectValue(Map(
                      "model-name" -> ConfigurationValue.StringValue("nested")
                    ))
                  ))
                ))
              ))
            )),
            ConfigurationTrace.empty
          ))
        ).toOption.get.parameters.head
        val canonicalResult = canonical.resolve(canonical.segments.head, model)
        val directResult = direct.resolve(direct.segments.head, model)

        Then("canonical spelling wins over its alias and direct configuration wins over nested configuration")
        canonicalResult.toOption.get.value shouldBe Some("canonical")
        directResult.toOption.get.value shouldBe Some("direct")
      }
    }

    "E14 enforce route segment leaf alias and expanded-key bounds" must _e14_metadata {
      "when declarations or discovered names exceed their bounded schema limits" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; bounded limits for aliases, leaves, segments, and expanded keys")
        val leaf = ComponentParameterPathLeaf.requiredString("model").toOption.get
        val aliases = Vector.tabulate(9)(index => s"alias-$index")
        val leaves = Vector.tabulate(65)(index =>
          ComponentParameterPathLeaf.requiredString(s"leaf-$index").toOption.get
        )
        val limits = ComponentParameterPathLimits.createC(1).toOption.get
        val expandedLeaves = Vector.tabulate(64)(index =>
          ComponentParameterPathLeaf.requiredString(s"leaf-$index").toOption.get
        )
        val segmentEntries = Vector.tabulate(65) { index =>
          s"textus.ai.execution-classes.segment-$index.model" -> ConfigurationValue.StringValue("model")
        }.toMap

        When("CNCF creates routes and expands discovered dynamic segments")
        val overAliases = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(leaf),
          prefixAliases = aliases
        )
        val overLeafAliases = ComponentParameterPathLeaf.requiredString(
          "model",
          aliases
        )
        val overLeaves = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          leaves
        )
        val limitedRoute = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(leaf),
          limits = limits
        ).toOption.get
        val expandedRoute = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          expandedLeaves
        ).toOption.get
        val overSegments = ComponentParameterPathSchemaRegistration.registerC(
          Vector(limitedRoute),
          _layers(runtimeconfiguration = ResolvedConfiguration(Configuration(segmentEntries), ConfigurationTrace.empty))
        )
        val overExpanded = ComponentParameterPathSchemaRegistration.registerC(
          Vector(expandedRoute),
          _layers(runtimeconfiguration = ResolvedConfiguration(Configuration(segmentEntries), ConfigurationTrace.empty))
        )

        Then("each bounded schema dimension rejects excessive input")
        _failure_taxonomy(overAliases) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(overLeafAliases) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(overLeaves) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(overSegments) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(overExpanded) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 reject canonical and alias prefixes that overlap within one route" must _e14_metadata {
      "when one route would own both an ancestor and descendant prefix" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; a canonical prefix and a segment-boundary alias descendant")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get

        When("CNCF validates the route declaration")
        val result = ComponentParameterPathRoute.createC(
          "textus.ai",
          "execution-class",
          Vector(model),
          prefixAliases = Vector("textus.ai.execution-classes")
        )

        Then("the ambiguous same-route prefix ownership is rejected")
        _failure_taxonomy(result) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 reject trailing-dot canonical and alias prefixes" must _e14_metadata {
      "when either route ownership spelling has an empty final path segment" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; one leaf and prefixes with trailing empty segments")
        val model = ComponentParameterPathLeaf.requiredString("model").toOption.get

        When("CNCF validates canonical and alias route prefixes")
        val canonical = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes.",
          "execution-class",
          Vector(model)
        )
        val alias = ComponentParameterPathRoute.createC(
          "textus.ai.execution-classes",
          "execution-class",
          Vector(model),
          prefixAliases = Vector("textus.runtime.ai.execution-classes.")
        )

        Then("both invalid ownership spellings fail with structured configuration-invalid taxonomy")
        _failure_taxonomy(canonical) shouldBe _configuration_invalid_taxonomy
        _failure_taxonomy(alias) shouldBe _configuration_invalid_taxonomy
      }
    }

    "E14 register generated valid dynamic names in stable lexical order" must _e14_metadata {
      "when unique segment sets are generated" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3a,R10; Example: E14; generated bounded dynamic names")
        val character = Gen.oneOf(('a' to 'z') ++ ('0' to '9'))
        val segment = Gen.choose(1, 24).flatMap(size => Gen.listOfN(size, character)).map(_.mkString)
        val segments = Gen.choose(1, 32).flatMap(size => Gen.listOfN(size, segment)).map(_.distinct)
        val property = Prop.forAll(segments) { names =>
          val model = ComponentParameterPathLeaf.requiredString("model").toOption.get
          val route = ComponentParameterPathRoute.createC(
            "textus.ai.execution-classes",
            "execution-class",
            Vector(model)
          ).toOption.get
          val runtime = ResolvedConfiguration(
            Configuration(names.map { name =>
              s"textus.ai.execution-classes.$name.model" -> ConfigurationValue.StringValue(s"model-$name")
            }.toMap),
            ConfigurationTrace.empty
          )
          ComponentParameterPathSchemaRegistration
            .registerC(Vector(route), _layers(runtimeconfiguration = runtime))
            .toOption
            .exists(_.parameters.head.segments.map(_.value) == names.sorted)
        }

        When("the registration property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("registration is deterministic for every generated valid name set")
        checked.passed shouldBe true
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
