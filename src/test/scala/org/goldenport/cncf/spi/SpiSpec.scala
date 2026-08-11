package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiGenerateResponse, AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerApplicationPurpose, AiRunnerApplicationPurposeRegistration, AiRunnerApplicationPurposeRegistrationSocketSet, AiRunnerSocket, AiRunnerSocketSet}
import org.goldenport.cncf.spi.geo.resolver.{GeoResolver, GeoResolverSocket, GeoResolverSocketSet}
import org.goldenport.cncf.spi.toolchain.runner.{ConvertSvgPagesToPdfRequest, ToolchainArtifactResponse, ToolchainRunner, ToolchainRunnerSocket, ToolchainRunnerSocketSet}
import org.goldenport.cncf.subsystem.{GenericSubsystemAssemblyDescriptorSource, GenericSubsystemComponentBinding, GenericSubsystemDescriptor}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  2, 2026
 *  version Jul. 29, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class SpiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "SpiResolver" should {
    "publish paired socket forms for CNCF-owned standard SPI contracts" in {
      Given("the AI, geographic, and toolchain standard SPI contracts")
      val aiset = new AiRunnerSocketSet {}
      val geoset = new GeoResolverSocketSet {}
      val toolset = new ToolchainRunnerSocketSet {}
      val registrationset = new AiRunnerApplicationPurposeRegistrationSocketSet {}

      When("their socket-set forms are constructed without providers")
      val contracts = Vector(
        aiset.spiContract.name,
        geoset.spiContract.name,
        toolset.spiContract.name,
        registrationset.spiContract.name
      )

      Then("each standard contract exposes an optional empty set alongside its existing single socket")
      contracts shouldBe Vector(
        "ai-runner",
        "geo-resolver",
        "toolchain-runner",
        "ai-runner-application-purpose-registration"
      )
      Vector(aiset, geoset, toolset, registrationset).forall(_.spiMembers.isEmpty) shouldBe true
      Vector(aiset, geoset, toolset, registrationset).forall(!_.spiRequired) shouldBe true
    }

    "collect application-purpose registrations through Component.Port at bootstrap" in {
      Given("an application registration output and a Textus AI registration input socket")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_application_purpose_registration")
      val registrationset = new AiRunnerApplicationPurposeRegistrationSocketSet {}
      val application = _initialized_component(
        subsystem,
        "application",
        new Component() {}.withPort(Component.Port.of(AiRunnerApplicationPurposeRegistration(Vector(
          AiRunnerApplicationPurpose("sanpomap-scenario-generation", "structured-extraction")
        ))))
      )
      val runtime = _initialized_component(
        subsystem,
        "runtime",
        new Component() {}.withPort(Component.Port.input(registrationset))
      )

      When("SPI resolution runs across the bootstrap component set")
      val result = SpiResolver.resolve(Vector(application, runtime))

      Then("the AI runtime input receives the application-owned registration")
      result shouldBe a[Consequence.Success[_]]
      registrationset.registrations.flatMap(_.purposes.map(_.name)) shouldBe
        Vector("sanpomap-scenario-generation")
    }

    "reject an uninitialized component before resolving a canonical SPI selector" in {
      Given("an uninitialized SPI consumer with an explicit canonical selector")
      given ExecutionContext = ExecutionContext.create()
      val consumer = ConsumerComponent()
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("provider"))
      )

      When("SPI resolution validates the selector candidates")
      val result = SpiResolver.resolve(Vector(consumer), Vector(binding))

      Then("resolution returns a structured identity failure before alias resolution")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
        "SPI component identity is not initialized"
      )
    }

    "resolve automatic and compatibility providers" which {
    "inject a provider component into a matching socket component" in {
      Given("a provider component and a consumer component with an AI runner socket")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_automatic_provider")
      val provider = _initialized_component(subsystem, "provider", ProviderComponent("provider-a"))
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())

      When("SPI resolution runs across the loaded component set")
      val result = SpiResolver.resolve(Vector(provider, consumer))

      Then("the socket receives the provider implementation")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "provider-a:hello"
    }

    "resolve one provider once when it is exposed through both the component and its port" in {
      Given("one provider instance exposed through the component SPI and its output port")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_port_backed_provider")
      val provider = _initialized_component(subsystem, "provider", PortBackedProviderComponent("provider-a"))
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())

      When("SPI resolution collects the component's providers")
      val result = SpiResolver.resolve(Vector(provider, consumer))

      Then("the repeated exposure is treated as one provider, not an ambiguity")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "provider-a:hello"
    }

    "trace canonical AiRunner SPI invocation in CallTree and metrics" in {
      Given("an initialized provider component and consumer socket with calltree enabled")
      val subsystem = TestComponentFactory.emptySubsystem("spi_trace")
      val provider = _initialized_component(subsystem, "trace_provider", ProviderComponent("provider-a"))
      val consumer = _initialized_component(subsystem, "trace_consumer", ConsumerComponent())
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val before = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total

      When("SPI resolution installs the provider and the consumer calls the SPI")
      SpiResolver.resolve(Vector(provider, consumer)) shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("secret prompt")) shouldBe Consequence.success(AiGenerateResponse("provider-a:secret prompt"))

      Then("the SPI call is visible without leaking request payload text")
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include ("spi:ai-runner.generate")
      text should include ("calltree_kind=spi")
      text should include ("contract=ai-runner")
      text should include (s"provider_component=${provider.componentId.name}")
      text should include (s"socket_component=${consumer.componentId.name}")
      text should include ("selection_basis=assembly-binding")
      text should include ("outcome=success")
      text should not include ("secret prompt")
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total should be > before
      val metrics = RuntimeDashboardMetrics.runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
      metrics.points.exists(point =>
        point.scope == "spi.invocation" &&
          point.labels.get("contract").contains("ai-runner") &&
          point.labels.get("operation").contains("generate") &&
          point.labels.get("selection_basis").contains("assembly-binding") &&
          !point.labels.contains("provider_instance") &&
          !point.labels.contains("selector_purpose")
      ) shouldBe true
    }

    "record canonical AiRunner SPI failures without changing the failure conclusion" in {
      Given("a traced provider that returns a structured failure")
      val subsystem = TestComponentFactory.emptySubsystem("spi_trace_failure")
      val provider = _initialized_component(subsystem, "trace_failing_provider", FailingProviderComponent())
      val consumer = _initialized_component(subsystem, "trace_failing_consumer", ConsumerComponent())
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val beforeerrors = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors

      When("the consumer calls the failing SPI")
      SpiResolver.resolve(Vector(provider, consumer)) shouldBe a[Consequence.Success[_]]
      val result = consumer.aiRunner.generate(AiGenerateRequest("hidden failure prompt"))

      Then("the original failure is preserved and traced safely")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("planned spi failure")
      val text = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      text should include ("spi:ai-runner.generate")
      text should include ("outcome=failure")
      text should include ("diagnostic_key")
      text should not include ("hidden failure prompt")
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors should be > beforeerrors
    }

    "inject a direct Component.Port SPI service into a matching socket component" in {
      Given("a component port containing the requested SPI service")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_direct_port_provider")
      val provider = _initialized_component(
        subsystem,
        "provider",
        new Component() {}.withPort(Component.Port.of(AiRunnerImplementation("direct")))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider, consumer))

      Then("the direct service is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("ping")).toOption.get.text shouldBe "direct:ping"
    }

    "not treat an AiRunnerSocket port entry as a direct provider candidate" in {
      Given("a port entry that is both an input socket and assignable to the SPI runtime class")
      given ExecutionContext = ExecutionContext.create()
      val input = new Component() {}
        .withPort(Component.Port.input(InputRunnerSocket("input-only")))
      val consumer = ConsumerComponent()

      When("SPI resolution runs without a real output provider")
      val result = SpiResolver.resolve(Vector(input, consumer))

      Then("the input socket is ignored as a provider")
      result shouldBe a[Consequence.Failure[_]]
    }

    "install an output provider without ambiguity when another port entry is an input socket" in {
      Given("one direct output provider and one input socket-looking port entry")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_output_and_input_port")
      val provider = _initialized_component(
        subsystem,
        "provider",
        new Component() {}.withPort(Component.Port.of(AiRunnerImplementation("direct")))
      )
      val input = _initialized_component(
        subsystem,
        "input",
        new Component() {}.withPort(Component.Port.input(InputRunnerSocket("input-only")))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider, input, consumer))

      Then("only the output provider is used")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("ping")).toOption.get.text shouldBe "direct:ping"
    }

    "return deterministic failure when no provider is available" in {
      Given("a consumer component without a matching provider")
      given ExecutionContext = ExecutionContext.create()
      val consumer = ConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(consumer))

      Then("resolution fails explicitly")
      result shouldBe a[Consequence.Failure[_]]
    }

    "return deterministic failure when providers are ambiguous" in {
      Given("two equivalent providers for one socket")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_ambiguous_providers")
      val provider1 = _initialized_component(subsystem, "provider-a", ProviderComponent("provider-a"))
      val provider2 = _initialized_component(subsystem, "provider-b", ProviderComponent("provider-b"))
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider1, provider2, consumer))

      Then("resolution fails rather than choosing implicitly")
      result shouldBe a[Consequence.Failure[_]]
      val failure = result.asInstanceOf[Consequence.Failure[_]].conclusion.display
      failure should include ("ambiguous SPI providers")
    }
    }

    "resolve explicit component instance bindings" which {
    "select a test provider component from an explicit SPI binding" in {
      Given("two equivalent providers and a consumer socket")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_explicit_binding")
      val prod = _initialized_component(subsystem, "prodprovider", ProviderComponent("prod"))
      val test = _initialized_component(subsystem, "testprovider", ProviderComponent("test"))
      val consumer = _initialized_component(
        subsystem,
        "art-scene",
        ConsumerComponent(),
        Some(ComponentInstanceMetadata("art-scene", "main"))
      )
      val binding = SpiRuntimeBinding(
        socket = SpiSocketSelector(
          component = Some("art-scene"),
          contract = "ai-runner",
          instance = Some("main")
        ),
        provider = SpiProviderSelector(component = Some("testprovider")),
        selection = SpiSelection()
      )

      When("SPI resolution runs with an explicit binding")
      val result = SpiResolver.resolve(Vector(prod, test, consumer), Vector(binding))

      Then("the provider from the bound component is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "test:hello"
      SpiResolver.resolve(Vector(prod, test, consumer), Vector(binding)) shouldBe a[Consequence.Success[_]]
    }

    "select an exact named provider instance from one component type" in {
      Given("two provider instances of one component type and an exact assembly binding")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_exact_instance")
      val static = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("static"),
        Some(ComponentInstanceMetadata("textus-scraper", "static-default", isDefault = true))
      )
      val dynamic = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("dynamic"),
        Some(ComponentInstanceMetadata("textus-scraper", "dynamic-playwright"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        socket = SpiSocketSelector(Some("consumer"), "ai-runner"),
        provider = SpiProviderSelector(
          component = Some("textus-scraper"),
          instance = Some("dynamic-playwright")
        )
      )

      When("SPI resolution applies the exact instance selector")
      val result = SpiResolver.resolve(Vector(static, dynamic, consumer), Vector(binding))

      Then("only the requested provider instance is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "dynamic:hello"
    }

    "select an exact provider instance published by a componentlet participant" in {
      Given("a componentlet provider whose participant id differs from its owning component instance")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_componentlet_provider_instance")
      val provider = _initialized_componentlet(
        subsystem,
        "textus-scraper-ai",
        ProviderComponent("componentlet"),
        ComponentInstanceMetadata("textus-scraper", "static-default", isDefault = true)
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"), instance = Some("static-default"))
      )

      When("SPI resolution applies the owning component instance selector")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("the componentlet provider is resolved through the logical assembly instance")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "componentlet:hello"
    }

    "resolve exact provider instance from an effective assembly descriptor" in {
      Given("named provider instances and an assembly descriptor selecting one instance")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_descriptor_instance")
      val static = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("static"),
        Some(ComponentInstanceMetadata("textus-scraper", "static-default", isDefault = true))
      )
      val dynamic = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("dynamic"),
        Some(ComponentInstanceMetadata("textus-scraper", "dynamic-playwright"))
      )
      val consumer = _initialized_component(subsystem, "art-scene", ConsumerComponent())
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.sar"),
        subsystemName = "art-scene",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("art-scene"),
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("static-default")),
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("dynamic-playwright"))
        ),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(Record.data(
                "socket" -> Record.data(
                  "component" -> "art-scene",
                  "contract" -> "ai-runner"
                ),
                "provider" -> Record.data(
                  "component" -> "textus-scraper",
                  "instance" -> "dynamic-playwright"
                )
              ))
            )
          ),
          source = "spec"
        ))
      )

      When("the effective assembly binding is decoded and resolved")
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get
      val result = SpiResolver.resolve(Vector(static, dynamic, consumer), bindings)

      Then("the descriptor-selected instance is installed through the runtime resolver")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "dynamic:hello"
    }

    "select the declared default provider instance when a binding omits instance" in {
      Given("two provider instances with one declared default")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_declared_default")
      val selected = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("static"),
        Some(ComponentInstanceMetadata("textus-scraper", "static-default", isDefault = true))
      )
      val other = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("dynamic"),
        Some(ComponentInstanceMetadata("textus-scraper", "dynamic-playwright"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"))
      )

      When("SPI resolution applies the component-only provider selector")
      val result = SpiResolver.resolve(Vector(selected, other, consumer), Vector(binding))

      Then("the declared default instance is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "static:hello"
    }

    "select the literal default provider instance when no instance is declared default" in {
      Given("two provider instances including the literal default instance")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_literal_default")
      val selected = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("default"),
        Some(ComponentInstanceMetadata("textus-scraper", "default"))
      )
      val other = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("dynamic"),
        Some(ComponentInstanceMetadata("textus-scraper", "dynamic-playwright"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"))
      )

      When("SPI resolution applies the component-only provider selector")
      val result = SpiResolver.resolve(Vector(selected, other, consumer), Vector(binding))

      Then("the literal default instance is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "default:hello"
    }

    "fail when an exact provider instance is missing" in {
      Given("one provider instance and a binding requesting another instance")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_missing_instance")
      val provider = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("static"),
        Some(ComponentInstanceMetadata("textus-scraper", "static-default"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"), instance = Some("dynamic-playwright"))
      )

      When("SPI resolution applies the missing exact selector")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("resolution fails without falling back to another instance")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("dynamic-playwright")
    }

    "fail when an exact provider instance does not expose the requested contract" in {
      Given("an exact provider instance that exposes GeoResolver instead of AiRunner")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_incompatible_instance")
      val provider = _initialized_component(
        subsystem,
        "textus-scraper",
        GeoResolverProviderComponent(),
        Some(ComponentInstanceMetadata("textus-scraper", "static-default"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"), instance = Some("static-default"))
      )

      When("SPI resolution checks the exact instance against the socket contract")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("the incompatible provider fails without selecting another component")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("SPI provider not found")
    }

    "fail when multiple provider instances have no default" in {
      Given("two provider instances without declared or literal default identity")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_ambiguous_instance")
      val first = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("first"),
        Some(ComponentInstanceMetadata("textus-scraper", "first"))
      )
      val second = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("second"),
        Some(ComponentInstanceMetadata("textus-scraper", "second"))
      )
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("textus-scraper"))
      )

      When("SPI resolution cannot derive a default instance")
      val result = SpiResolver.resolve(Vector(first, second, consumer), Vector(binding))

      Then("resolution reports provider ambiguity")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("ambiguous SPI providers")
    }

    "select only the exact namespace-qualified provider component" in {
      Given("two admitted providers sharing a local ID in different namespaces")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi-qualified-selector")
      val alphaid = ComponentId("org.alpha.Catalog")
      val betaid = ComponentId("org.beta.Catalog")
      val consumerid = ComponentId("org.example.Consumer")
      val alpha = _initialized_component_with_id(subsystem, alphaid, ProviderComponent("alpha"))
      val beta = _initialized_component_with_id(subsystem, betaid, ProviderComponent("beta"))
      val consumer = _initialized_component_with_id(subsystem, consumerid, ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some(consumerid.name), "ai-runner"),
        SpiProviderSelector(component = Some(alphaid.name))
      )

      When("an SPI binding selects the qualified canonical provider ID")
      val result = SpiResolver.resolve(Vector(alpha, beta, consumer), Vector(binding))

      Then("only the matching namespace provider is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "alpha:hello"
    }

    "reject an ambiguous bare provider alias across admitted canonical components" in {
      Given("two admitted canonical providers with the same presentation alias")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi-ambiguous-alias")
      val alpha = _initialized_component_with_id(
        subsystem,
        ComponentId("org.alpha.Catalog"),
        ProviderComponent("alpha")
      )
      val beta = _initialized_component_with_id(
        subsystem,
        ComponentId("org.beta.Catalog"),
        ProviderComponent("beta")
      )
      val consumerid = ComponentId("org.example.Consumer")
      val consumer = _initialized_component_with_id(subsystem, consumerid, ConsumerComponent())
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some(consumerid.name), "ai-runner"),
        SpiProviderSelector(component = Some("catalog"))
      )

      When("the shared bare alias is used as an SPI provider selector")
      val result = SpiResolver.resolve(Vector(alpha, beta, consumer), Vector(binding))

      Then("SPI resolution fails closed instead of selecting either provider")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("component.identity.compatibility.ambiguous")
    }

    "retain the canonical component ID in fallback SPI member metadata" in {
      Given("a provider exposing only its canonical Component.Core identity")
      given ExecutionContext = ExecutionContext.create()
      val componentid = ComponentId("org.example.FallbackProvider")
      val provider = FallbackMetadataProviderComponent(componentid)

      When("SPI assembly creates its programmatic provider metadata")
      val resolution = SpiResolver.resolveAssembly(Vector(provider)).toOption.get
      val metadata = resolution.componentApiResolver._providers.head.metadata

      Then("the fallback retains the canonical ComponentId and default instance label")
      metadata.instanceId.componentId shouldBe componentid
      metadata.instanceId shouldBe ComponentInstanceId(componentid, "default")
    }
    }

    "resolve named socket bindings" which {
    "bind multiple named input sockets of one contract independently" in {
      Given("one consumer component exposing two named input sockets and two providers")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_named_sockets")
      val staticprovider = _initialized_component(subsystem, "static-provider", ProviderComponent("static"))
      val dynamicprovider = _initialized_component(subsystem, "dynamic-provider", ProviderComponent("dynamic"))
      val staticsocket = NamedRunnerSocket("static-scraper")
      val dynamicsocket = NamedRunnerSocket("dynamic-scraper")
      val consumer = _initialized_component(
        subsystem,
        "consumer",
        new Component() {}.withPort(Component.Port.input(staticsocket, dynamicsocket))
      )
      val bindings = Vector(
        SpiRuntimeBinding(
          SpiSocketSelector(Some("consumer"), "ai-runner", name = Some("static-scraper")),
          SpiProviderSelector(component = Some("static-provider"))
        ),
        SpiRuntimeBinding(
          SpiSocketSelector(Some("consumer"), "ai-runner", name = Some("dynamic-scraper")),
          SpiProviderSelector(component = Some("dynamic-provider"))
        )
      )

      When("SPI resolution applies each named socket binding")
      val result = SpiResolver.resolve(Vector(staticprovider, dynamicprovider, consumer), bindings)

      Then("each input socket receives its independently selected provider")
      result shouldBe a[Consequence.Success[_]]
      staticsocket.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "static:hello"
      dynamicsocket.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "dynamic:hello"
    }

    "reject an unnamed binding when multiple sockets share one component and contract" in {
      Given("one consumer exposing two named sockets and an unnamed assembly binding")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_ambiguous_socket")
      val provider = _initialized_component(subsystem, "provider", ProviderComponent("provider"))
      val consumer = _initialized_component(
        subsystem,
        "consumer",
        new Component() {}.withPort(Component.Port.input(
          NamedRunnerSocket("first"),
          NamedRunnerSocket("second")
        ))
      )
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner"),
        SpiProviderSelector(component = Some("provider"))
      )

      When("SPI resolution validates the unnamed socket selector")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("resolution reports socket ambiguity")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("ambiguous SPI sockets")
    }

    "select an exact named socket published by a componentlet participant" in {
      Given("a componentlet consumer whose participant id differs from its owning component instance")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_componentlet_socket_instance")
      val provider = _initialized_component(subsystem, "provider", ProviderComponent("provider"))
      val socket = NamedRunnerSocket("assistant")
      val consumer = _initialized_componentlet(
        subsystem,
        "art-scene-ai",
        new Component() {}.withPort(Component.Port.input(socket)),
        ComponentInstanceMetadata("art-scene", "main")
      )
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(
          component = Some("art-scene"),
          contract = "ai-runner",
          instance = Some("main"),
          name = Some("assistant")
        ),
        SpiProviderSelector(component = Some("provider"))
      )

      When("SPI resolution applies the owning component instance and socket name")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("the componentlet socket receives the selected provider")
      result shouldBe a[Consequence.Success[_]]
      socket.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "provider:hello"
    }

    "reject provider service bindings until service-level matching is supported" in {
      Given("a binding that names an unsupported provider service selector")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_provider_service_binding")
      val provider = _initialized_component(subsystem, "testprovider", ProviderComponent("test"))
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent())
      val binding = SpiRuntimeBinding(
        socket = SpiSocketSelector(Some("consumer"), "ai-runner"),
        provider = SpiProviderSelector(component = Some("testprovider"), service = Some("ai-runner-test")),
        selection = SpiSelection()
      )

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider, consumer), Vector(binding))

      Then("resolution fails instead of ignoring the service selector")
      result shouldBe a[Consequence.Failure[_]]
    }
    }

    "resolve socket sets and abstract component selectors" which {
    "resolve an assembly-admitted provider without a consumer socket" in {
      Given("one loaded provider component and no socket binding")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_programmatic_provider")
      val provider = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("programmatic"),
        Some(ComponentInstanceMetadata(
          "textus-scraper",
          "static",
          purposes = Vector("official-site"),
          capabilities = Vector("html")
        ))
      )

      When("assembly resolution builds the public component API catalog")
      val resolution = SpiResolver.resolveAssembly(Vector(provider)).toOption.get
      val result = resolution.componentApiResolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some("textus-scraper"), purpose = Some("official-site"))
      )

      Then("the admitted provider is materialized lazily through the typed resolver")
      result.flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "programmatic:hello"
    }

    "install explicitly bound provider instances into one socket set" in {
      Given("two named provider instances and one required socket set with exact bindings")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_socket_set_exact")
      val static = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("static"),
        Some(ComponentInstanceMetadata(
          "textus-scraper",
          "static-default",
          purposes = Vector("official-site"),
          tags = Vector("static"),
          priority = 100,
          isDefault = true,
          capabilities = Vector("html")
        ))
      )
      val dynamic = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("dynamic"),
        Some(ComponentInstanceMetadata(
          "textus-scraper",
          "dynamic-playwright",
          purposes = Vector("javascript-heavy-site"),
          tags = Vector("dynamic"),
          priority = 100,
          capabilities = Vector("javascript")
        ))
      )
      val socket = RunnerSocketSet("scrapers", required = true)
      val consumer = _initialized_component(
        subsystem,
        "art-scene",
        new Component() {}.withPort(Component.Port.input(socket))
      )
      val bindings = Vector("static-default", "dynamic-playwright").map { instance =>
        SpiRuntimeBinding(
          SpiSocketSelector(Some("art-scene"), "ai-runner", name = Some("scrapers"), cardinality = SpiCardinality.OneOrMore),
          SpiProviderSelector(component = Some("textus-scraper"), instance = Some(instance))
        )
      }

      When("SPI resolution installs the assembly-bounded provider set")
      val result = SpiResolver.resolveAssembly(Vector(static, dynamic, consumer), bindings)

      Then("both members are installed and abstract selectors resolve the expected typed API")
      result shouldBe a[Consequence.Success[_]]
      subsystem.withComponentApiResolver(result.toOption.get.componentApiResolver)
      socket.spiMembers.map(_.metadata.instanceId.instance).toSet shouldBe Set("static-default", "dynamic-playwright")
      socket.resolve(ComponentSelector(purpose = Some("javascript-heavy-site")))
        .flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "dynamic:hello"
      socket.resolve(ComponentSelector(capabilities = Set("html"), tags = Set("static")))
        .flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "static:hello"
      subsystem.componentApiResolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some("textus-scraper"), instance = Some("dynamic-playwright"))
      ).flatMap(_.generate(AiGenerateRequest("runtime"))).toOption.get.text shouldBe "dynamic:runtime"
    }

    "expand a component-only set binding to all compatible assembly instances" in {
      Given("two compatible instances and one component-only many binding")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_socket_set_component")
      val first = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("first"),
        Some(ComponentInstanceMetadata("textus-scraper", "first"))
      )
      val second = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("second"),
        Some(ComponentInstanceMetadata("textus-scraper", "second"))
      )
      val socket = RunnerSocketSet("scrapers")
      val consumer = _initialized_component(
        subsystem,
        "consumer",
        new Component() {}.withPort(Component.Port.input(socket))
      )
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner", name = Some("scrapers"), cardinality = SpiCardinality.Many),
        SpiProviderSelector(component = Some("textus-scraper"))
      )

      When("SPI resolution expands the component selector")
      val result = SpiResolver.resolve(Vector(first, second, consumer), Vector(binding))

      Then("every compatible named instance is installed without default collapsing")
      result shouldBe a[Consequence.Success[_]]
      socket.spiMembers.map(_.metadata.instanceId.instance).toSet shouldBe Set("first", "second")
      socket.resolve(ComponentSelector()) shouldBe a[Consequence.Failure[_]]
    }

    "exclude unhealthy members and apply declared default selection deterministically" in {
      Given("a healthy default provider and a higher-priority unhealthy provider")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_socket_set_health")
      val healthy = _initialized_component(
        subsystem,
        "textus-scraper",
        ProviderComponent("healthy"),
        Some(ComponentInstanceMetadata("textus-scraper", "default", priority = 10, isDefault = true))
      )
      val unhealthy = _initialized_component(
        subsystem,
        "textus-scraper",
        UnavailableProviderComponent(),
        Some(ComponentInstanceMetadata("textus-scraper", "unhealthy", priority = 100))
      )
      unhealthy.registerHealthContributor(new Component.HealthContributor {
        def name: String = "provider"
        def check(component: Component): Component.HealthCheck = Component.HealthCheck(name, "error", Some("offline"))
      })
      val socket = RunnerSocketSet("scrapers", required = true)
      val consumer = _initialized_component(subsystem, "consumer", new Component() {}.withPort(Component.Port.input(socket)))
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(Some("consumer"), "ai-runner", name = Some("scrapers"), cardinality = SpiCardinality.OneOrMore),
        SpiProviderSelector(component = Some("textus-scraper"))
      )

      When("the socket set resolves its default member")
      val result = SpiResolver.resolve(Vector(healthy, unhealthy, consumer), Vector(binding))

      Then("the error provider is excluded before service materialization")
      result shouldBe a[Consequence.Success[_]]
      socket.spiMembers.map(_.metadata.healthStatus).toSet shouldBe Set("ok")
      socket.resolve().flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "healthy:hello"
    }

    "respect optional single and required set cardinalities" in {
      Given("an optional single socket plus empty optional and required socket sets")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_cardinalities")
      val optional = OptionalRunnerSocket()
      val optionalset = RunnerSocketSet("optional-set")
      val required = RunnerSocketSet("required", required = true)
      val optionalconsumer = _initialized_component(subsystem, "optional-consumer", new Component() {}.withPort(Component.Port.input(optional)))
      val optionalsetconsumer = _initialized_component(subsystem, "optional-set-consumer", new Component() {}.withPort(Component.Port.input(optionalset)))
      val requiredconsumer = _initialized_component(subsystem, "required-consumer", new Component() {}.withPort(Component.Port.input(required)))
      val optionalbinding = SpiRuntimeBinding(
        SpiSocketSelector(Some("optional-consumer"), "ai-runner", cardinality = SpiCardinality.Optional),
        SpiProviderSelector(component = Some("missing-provider"))
      )
      val optionalsetbinding = SpiRuntimeBinding(
        SpiSocketSelector(Some("optional-set-consumer"), "ai-runner", name = Some("optional-set"), cardinality = SpiCardinality.Many),
        SpiProviderSelector(component = Some("missing-provider"))
      )
      val requiredbinding = SpiRuntimeBinding(
        SpiSocketSelector(Some("required-consumer"), "ai-runner", name = Some("required"), cardinality = SpiCardinality.OneOrMore),
        SpiProviderSelector(component = Some("missing-provider"))
      )

      When("SPI resolution evaluates each cardinality")
      val optionalresult = SpiResolver.resolve(Vector(optionalconsumer), Vector(optionalbinding))
      val optionalsetresult = SpiResolver.resolve(Vector(optionalsetconsumer), Vector(optionalsetbinding))
      val requiredresult = SpiResolver.resolve(Vector(requiredconsumer), Vector(requiredbinding))

      Then("unknown provider aliases fail closed for every cardinality")
      optionalresult shouldBe a[Consequence.Failure[_]]
      optional.isSpiInstalled shouldBe false
      optionalsetresult shouldBe a[Consequence.Failure[_]]
      optionalset.spiMembers shouldBe empty
      requiredresult shouldBe a[Consequence.Failure[_]]
      Vector(optionalresult, optionalsetresult, requiredresult).foreach { result =>
        result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
          "component.identity.compatibility.unsupported"
        )
      }
    }

    "apply a typed runtime policy before priority and default selection" in {
      Given("two resolved members and a policy that rejects the default member")
      val defaultmember = ResolvedSpiMember[AiRunner](
        AiRunnerImplementation("default"),
        SpiMemberMetadata("ai-runner", "textus-scraper", ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-scraper"), "default"), priority = 100, isDefault = true)
      )
      val allowedmember = ResolvedSpiMember[AiRunner](
        AiRunnerImplementation("allowed"),
        SpiMemberMetadata("ai-runner", "textus-scraper", ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-scraper"), "allowed"), priority = 10)
      )
      val othercontract = ResolvedSpiMember[AiRunner](
        AiRunnerImplementation("other-contract"),
        SpiMemberMetadata("other-runner", "textus-scraper", ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus-scraper"), "other"), priority = 1000)
      )
      val policy = new ComponentSelectionPolicy {
        def accept(member: SpiMemberMetadata, selector: ComponentSelector): Consequence[Boolean] =
          Consequence.success(member.instanceId.instance != "default")
      }
      val resolver = ComponentApiResolver(Vector(defaultmember, allowedmember, othercontract), policy)
      val socket = RunnerSocketSet("policy", selectionpolicy = policy)
      socket.installSpiMembers(Vector(defaultmember, allowedmember, othercontract))
      val subsystem = TestComponentFactory.emptySubsystem("spi_policy_merge")
      subsystem.withComponentApiResolver(resolver)
      given ExecutionContext = ExecutionContext.create()

      When("the public component API resolver applies an abstract selector")
      val result = resolver.resolve(SpiContract("ai-runner", classOf[AiRunner]), ComponentSelector(component = Some("textus-scraper")))

      Then("contract and policy rejection happen before deterministic ranking")
      result.flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "allowed:hello"
      socket.resolve(ComponentSelector(component = Some("textus-scraper")))
        .flatMap(_.generate(AiGenerateRequest("socket"))).toOption.get.text shouldBe "allowed:socket"
      subsystem.componentApiResolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some("textus-scraper"))
      ).flatMap(_.generate(AiGenerateRequest("subsystem"))).toOption.get.text shouldBe "allowed:subsystem"
    }

    "select a Component API by its exact qualified canonical component ID" in {
      Given("two resolved component APIs with the same local ID in different namespaces")
      given ExecutionContext = ExecutionContext.create()
      val alphaid = ComponentId("org.alpha.Catalog")
      val betaid = ComponentId("org.beta.Catalog")
      val resolver = ComponentApiResolver(Vector(
        _component_api_member(alphaid, "catalog", "alpha"),
        _component_api_member(betaid, "catalog", "beta")
      ))

      When("the Component API selector uses the exact canonical alpha ID")
      val result = resolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some(alphaid.name))
      )

      Then("only the matching canonical provider is selected")
      result.flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "alpha:hello"
    }

    "adapt a unique legacy Component API presentation alias to its canonical component ID" in {
      Given("one resolved component API with a non-authoritative legacy presentation alias")
      given ExecutionContext = ExecutionContext.create()
      val componentid = ComponentId("org.example.Catalog")
      val resolver = ComponentApiResolver(Vector(
        _component_api_member(componentid, "legacy-catalog", "catalog")
      ))

      When("the Component API selector uses the unique legacy alias")
      val result = resolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some("legacy catalog"))
      )

      Then("the admitted canonical provider remains the selected identity authority")
      result.flatMap(_.generate(AiGenerateRequest("hello"))).toOption.get.text shouldBe "catalog:hello"
    }

    "reject an ambiguous shared Component API presentation alias" in {
      Given("two resolved component APIs sharing one legacy presentation alias")
      given ExecutionContext = ExecutionContext.create()
      val resolver = ComponentApiResolver(Vector(
        _component_api_member(ComponentId("org.alpha.Catalog"), "shared-catalog", "alpha"),
        _component_api_member(ComponentId("org.beta.Search"), "shared-catalog", "beta")
      ))

      When("the Component API selector uses the shared alias")
      val result = resolver.resolve(
        SpiContract("ai-runner", classOf[AiRunner]),
        ComponentSelector(component = Some("shared catalog"))
      )

      Then("selection fails closed rather than choosing either canonical provider")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("component.identity.compatibility.ambiguous")
    }
    }

    "resolve provider variations and standard contracts" which {
    "select a provider by mode and engine" in {
      Given("two providers with different selections")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_provider_selection")
      val provider1 = _initialized_component(subsystem, "local-provider", ProviderComponent(
        providername = "local",
        selection = SpiSelection(mode = Some("local"), engine = Some("ollama"))
      ))
      val provider2 = _initialized_component(subsystem, "remote-provider", ProviderComponent(
        providername = "remote",
        selection = SpiSelection(mode = Some("remote"), engine = Some("http"))
      ))
      val consumer = _initialized_component(subsystem, "consumer", ConsumerComponent(
        selection = SpiSelection(mode = Some("remote"), engine = Some("http"))
      ))

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider1, provider2, consumer))

      Then("the provider matching the socket selection is injected")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "remote:hello"
    }

    "inject GeoResolver and ToolchainRunner providers through CNCF-owned SPI contracts" in {
      Given("provider components and consumer components for non-AI SPI contracts")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_standard_contracts")
      val geoprovider = _initialized_component(subsystem, "geo-provider", GeoResolverProviderComponent())
      val geoconsumer = _initialized_component(subsystem, "geo-consumer", GeoResolverConsumerComponent())
      val toolprovider = _initialized_component(subsystem, "toolchain-provider", ToolchainRunnerProviderComponent())
      val toolconsumer = _initialized_component(subsystem, "toolchain-consumer", ToolchainRunnerConsumerComponent())

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(geoprovider, geoconsumer, toolprovider, toolconsumer))

      Then("each socket receives its matching provider implementation")
      result shouldBe a[Consequence.Success[_]]
      geoconsumer.geoResolver.getClass.getName should include ("TracedGeoResolver")
      toolconsumer.toolchainRunner.convertSvgPagesToPdf(ConvertSvgPagesToPdfRequest(Vector("a.svg"))).toOption.get.pageCount shouldBe 1
    }
    }
  }

  private final case class ConsumerComponent(
    selection: SpiSelection = SpiSelection()
  ) extends Component with AiRunnerSocket {
    override def spiSelection: SpiSelection = selection
  }

  private def _initialized_component[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: A,
    metadata: Option[ComponentInstanceMetadata] = None
  ): A = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    val canonicalmetadata = metadata.map(_.copy(componentId = Some(componentid)))
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = canonicalmetadata.map(_.instanceId).getOrElse(ComponentInstanceId.default(componentid)),
      protocol = Protocol.empty
    )
    val init = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = canonicalmetadata
    )
    component.initialize(init)
    component
  }

  private def _component_api_member(
    componentid: ComponentId,
    presentationalias: String,
    providername: String
  ): ResolvedSpiMember[AiRunner] =
    ResolvedSpiMember(
      AiRunnerImplementation(providername),
      SpiMemberMetadata(
        contract = "ai-runner",
        component = presentationalias,
        instanceId = ComponentInstanceId(componentid, "default")
      )
    )

  private def _initialized_component_with_id[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    componentid: ComponentId,
    component: A,
    metadata: Option[ComponentInstanceMetadata] = None
  ): A = {
    val canonicalmetadata = metadata.map(_.copy(componentId = Some(componentid)))
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = canonicalmetadata.map(_.instanceId).getOrElse(ComponentInstanceId.default(componentid)),
      protocol = Protocol.empty
    )
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = canonicalmetadata
    ))
    component
  }

  private def _initialized_componentlet[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    participantname: String,
    component: A,
    metadata: ComponentInstanceMetadata
  ): A = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(participantname)
    val canonicalmetadata = metadata.copy(componentId = Some(componentid))
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId(componentid.name, metadata.instance),
      protocol = Protocol.empty
    )
    component.initialize(
      ComponentInit(
        subsystem = subsystem,
        core = core,
        origin = ComponentOrigin.Builtin,
        participantRole = Component.ParticipantRole.Componentlet,
        instanceMetadata = Some(canonicalmetadata)
      )
    )
    component
  }

  private final case class ProviderComponent(
    providername: String,
    selection: SpiSelection = SpiSelection()
  ) extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(AiRunnerProvider(providername, selection))
  }

  private final case class FallbackMetadataProviderComponent(
    componentid: ComponentId
  ) extends Component with SpiProviderComponent {
    private val _canonical_core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )

    override def core: Component.Core = _canonical_core
    override def coreOption: Option[Component.Core] = None

    def spiProviders: Vector[SpiProvider[?]] =
      Vector(AiRunnerProvider("fallback", SpiSelection()))
  }

  private final case class PortBackedProviderComponent(providername: String)
    extends Component
    with SpiProviderComponent {
    private val _provider = AiRunnerProvider(providername, SpiSelection())
    withPort(Component.Port.of(_provider))

    def spiProviders: Vector[SpiProvider[?]] = Vector(_provider)
  }

  private final case class AiRunnerProvider(
    name: String,
    selection: SpiSelection
  ) extends SpiProvider[AiRunner] {
    def supports(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "ai-runner" &&
        contract.runtimeClass == classOf[AiRunner] &&
        _matches(selection.provider, requested.provider) &&
        _matches(selection.mode, requested.mode) &&
        _matches(selection.engine, requested.engine)

    private def _matches(
      provider: Option[String],
      requested: Option[String]
    ): Boolean =
      requested.forall(v => provider.contains(v))

    def provide(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Consequence[AiRunner] =
      Consequence.success(AiRunnerImplementation(name))
  }

  private final case class FailingProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(FailingAiRunnerProvider())
  }

  private final case class FailingAiRunnerProvider() extends SpiProvider[AiRunner] {
    def supports(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "ai-runner" &&
        contract.runtimeClass == classOf[AiRunner]

    def provide(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Consequence[AiRunner] =
      Consequence.success(FailingAiRunnerImplementation())
  }

  private final case class UnavailableProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] = Vector(UnavailableAiRunnerProvider())
  }

  private final case class UnavailableAiRunnerProvider() extends SpiProvider[AiRunner] {
    def supports(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "ai-runner" && contract.runtimeClass == classOf[AiRunner]

    def provide(
      contract: SpiContract[AiRunner],
      requested: SpiSelection
    )(using ExecutionContext): Consequence[AiRunner] =
      Consequence.serviceUnavailable("unhealthy provider must not be materialized")
  }

  private final case class AiRunnerImplementation(
    name: String
  ) extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class FailingAiRunnerImplementation() extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.operationInvalid("planned spi failure")

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class InputRunnerSocket(
    name: String
  ) extends AiRunnerSocket with AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class NamedRunnerSocket(
    name: String
  ) extends AiRunnerSocket {
    override def spiSocketName: String = name
  }

  private final case class OptionalRunnerSocket() extends AiRunnerSocket {
    override def spiRequired: Boolean = false
  }

  private final case class RunnerSocketSet(
    name: String,
    required: Boolean = false,
    selectionpolicy: ComponentSelectionPolicy = ComponentSelectionPolicy.allowAll
  ) extends AiRunnerSocketSet {
    override def spiSocketName: String = name
    override def spiRequired: Boolean = required
    override def spiSelectionPolicy: ComponentSelectionPolicy = selectionpolicy
  }

  private final case class GeoResolverConsumerComponent() extends Component with GeoResolverSocket

  private final case class GeoResolverProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(GeoResolverProvider())
  }

  private final case class GeoResolverProvider() extends SpiProvider[GeoResolver] {
    def supports(
      contract: SpiContract[GeoResolver],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "geo-resolver" &&
        contract.runtimeClass == classOf[GeoResolver]

    def provide(
      contract: SpiContract[GeoResolver],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[GeoResolver] =
      Consequence.success(GeoResolverImplementation())
  }

  private final case class GeoResolverImplementation() extends GeoResolver {
    def resolveRoute(req: org.goldenport.cncf.spi.geo.resolver.GeoResolveRouteRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoResolveRouteResponse] =
      Consequence.operationInvalid("resolveRoute is not used by this spec")

    def buildMapContext(req: org.goldenport.cncf.spi.geo.resolver.GeoBuildMapContextRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoBuildMapContextResponse] =
      Consequence.operationInvalid("buildMapContext is not used by this spec")

    def investigateLocation(req: org.goldenport.cncf.spi.geo.resolver.GeoInvestigateLocationRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoInvestigateLocationResponse] =
      Consequence.operationInvalid("investigateLocation is not used by this spec")

    def researchLinearFeatureRoute(req: org.goldenport.cncf.spi.geo.resolver.GeoResearchLinearFeatureRouteRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoResearchLinearFeatureRouteResponse] =
      Consequence.operationInvalid("researchLinearFeatureRoute is not used by this spec")
  }

  private final case class ToolchainRunnerConsumerComponent() extends Component with ToolchainRunnerSocket

  private final case class ToolchainRunnerProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(ToolchainRunnerProvider())
  }

  private final case class ToolchainRunnerProvider() extends SpiProvider[ToolchainRunner] {
    def supports(
      contract: SpiContract[ToolchainRunner],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == "toolchain-runner" &&
        contract.runtimeClass == classOf[ToolchainRunner]

    def provide(
      contract: SpiContract[ToolchainRunner],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[ToolchainRunner] =
      Consequence.success(ToolchainRunnerImplementation())
  }

  private final case class ToolchainRunnerImplementation() extends ToolchainRunner {
    def convertSvgToPdf(req: org.goldenport.cncf.spi.toolchain.runner.ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.operationInvalid("convertSvgToPdf is not used by this spec")

    def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.success(ToolchainArtifactResponse(true, 0, 0, "ok", req.out, req.svgFiles.length, Some("test")))
  }
}
