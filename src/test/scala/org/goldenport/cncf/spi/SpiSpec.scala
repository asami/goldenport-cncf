package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiGenerateResponse, AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerSocket}
import org.goldenport.cncf.spi.geo.resolver.{GeoResolver, GeoResolverSocket}
import org.goldenport.cncf.spi.toolchain.runner.{ConvertSvgPagesToPdfRequest, ToolchainArtifactResponse, ToolchainRunner, ToolchainRunnerSocket}
import org.goldenport.cncf.subsystem.{GenericSubsystemAssemblyDescriptorSource, GenericSubsystemComponentBinding, GenericSubsystemDescriptor}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  2, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class SpiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SpiResolver" should {
    "resolve automatic and compatibility providers" which {
    "inject a provider component into a matching socket component" in {
      Given("a provider component and a consumer component with an AI runner socket")
      given ExecutionContext = ExecutionContext.create()
      val provider = ProviderComponent("provider-a")
      val consumer = ConsumerComponent()

      When("SPI resolution runs across the loaded component set")
      val result = SpiResolver.resolve(Vector(provider, consumer))

      Then("the socket receives the provider implementation")
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
      text should include ("provider_component=trace_provider")
      text should include ("socket_component=trace_consumer")
      text should include ("outcome=success")
      text should not include ("secret prompt")
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total should be > before
      val metrics = RuntimeDashboardMetrics.runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
      metrics.points.exists(point =>
        point.scope == "spi.invocation" &&
          point.labels.get("contract").contains("ai-runner") &&
          point.labels.get("operation").contains("generate")
      ) shouldBe true
    }

    "record canonical AiRunner SPI failures without changing the failure conclusion" in {
      Given("a traced provider that returns a structured failure")
      val subsystem = TestComponentFactory.emptySubsystem("spi_trace_failure")
      val provider = _initialized_component(subsystem, "trace_failing_provider", FailingProviderComponent())
      val consumer = _initialized_component(subsystem, "trace_failing_consumer", ConsumerComponent())
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val beforeErrors = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors

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
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors should be > beforeErrors
    }

    "inject a direct Component.Port SPI service into a matching socket component" in {
      Given("a component port containing the requested SPI service")
      given ExecutionContext = ExecutionContext.create()
      val provider = new Component() {}
        .withPort(Component.Port.of(AiRunnerImplementation("direct")))
      val consumer = ConsumerComponent()

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
      val provider = new Component() {}
        .withPort(Component.Port.of(AiRunnerImplementation("direct")))
      val input = new Component() {}
        .withPort(Component.Port.input(InputRunnerSocket("input-only")))
      val consumer = ConsumerComponent()

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
      val provider1 = ProviderComponent("provider-a")
      val provider2 = ProviderComponent("provider-b")
      val consumer = ConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider1, provider2, consumer))

      Then("resolution fails rather than choosing implicitly")
      result shouldBe a[Consequence.Failure[_]]
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

    "resolve provider variations and standard contracts" which {
    "select a provider by mode and engine" in {
      Given("two providers with different selections")
      given ExecutionContext = ExecutionContext.create()
      val provider1 = ProviderComponent(
        providername = "local",
        selection = SpiSelection(mode = Some("local"), engine = Some("ollama"))
      )
      val provider2 = ProviderComponent(
        providername = "remote",
        selection = SpiSelection(mode = Some("remote"), engine = Some("http"))
      )
      val consumer = ConsumerComponent(
        selection = SpiSelection(mode = Some("remote"), engine = Some("http"))
      )

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider1, provider2, consumer))

      Then("the provider matching the socket selection is injected")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "remote:hello"
    }

    "inject GeoResolver and ToolchainRunner providers through CNCF-owned SPI contracts" in {
      Given("provider components and consumer components for non-AI SPI contracts")
      given ExecutionContext = ExecutionContext.create()
      val geoprovider = GeoResolverProviderComponent()
      val geoconsumer = GeoResolverConsumerComponent()
      val toolprovider = ToolchainRunnerProviderComponent()
      val toolconsumer = ToolchainRunnerConsumerComponent()

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
    val componentid = ComponentId(name.map(ch => if (ch.isLetterOrDigit || ch == '_') ch else '_'))
    val core = Component.Core.create(
      name = name,
      componentid = componentid,
      instanceid = metadata.map(_.instanceId).getOrElse(ComponentInstanceId.default(componentid)),
      protocol = Protocol.empty
    )
    val init = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = metadata
    )
    component.initialize(init)
    component
  }

  private def _initialized_componentlet[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    participantname: String,
    component: A,
    metadata: ComponentInstanceMetadata
  ): A = {
    val componentid = ComponentId(participantname.map(ch => if (ch.isLetterOrDigit || ch == '_') ch else '_'))
    val core = Component.Core.create(
      name = participantname,
      componentid = componentid,
      instanceid = ComponentInstanceId(participantname, metadata.instance),
      protocol = Protocol.empty
    )
    component.initialize(
      ComponentInit(
        subsystem = subsystem,
        core = core,
        origin = ComponentOrigin.Builtin,
        participantRole = Component.ParticipantRole.Componentlet,
        instanceMetadata = Some(metadata)
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
