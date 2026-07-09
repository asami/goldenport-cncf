package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiGenerateResponse, AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerSocket}
import org.goldenport.cncf.spi.geo.resolver.{GeoResolver, GeoResolverSocket}
import org.goldenport.cncf.spi.toolchain.runner.{ConvertSvgPagesToPdfRequest, ToolchainArtifactResponse, ToolchainRunner, ToolchainRunnerSocket}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  2, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class SpiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SpiResolver" should {
    "inject a provider component into a matching socket component" in {
      Given("a provider component and a consumer component with an AI runner socket")
      given ExecutionContext = ExecutionContext.create()
      val provider = _ProviderComponent("provider-a")
      val consumer = _ConsumerComponent()

      When("SPI resolution runs across the loaded component set")
      val result = SpiResolver.resolve(Vector(provider, consumer))

      Then("the socket receives the provider implementation")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "provider-a:hello"
    }

    "trace canonical AiRunner SPI invocation in CallTree and metrics" in {
      Given("an initialized provider component and consumer socket with calltree enabled")
      val subsystem = TestComponentFactory.emptySubsystem("spi_trace")
      val provider = _initialized_component(subsystem, "trace_provider", _ProviderComponent("provider-a"))
      val consumer = _initialized_component(subsystem, "trace_consumer", _ConsumerComponent())
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
      val provider = _initialized_component(subsystem, "trace_failing_provider", _FailingProviderComponent())
      val consumer = _initialized_component(subsystem, "trace_failing_consumer", _ConsumerComponent())
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
        .withPort(Component.Port.of(_AiRunner("direct")))
      val consumer = _ConsumerComponent()

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
        .withPort(Component.Port.of(_InputRunnerSocket("input-only")))
      val consumer = _ConsumerComponent()

      When("SPI resolution runs without a real output provider")
      val result = SpiResolver.resolve(Vector(input, consumer))

      Then("the input socket is ignored as a provider")
      result shouldBe a[Consequence.Failure[_]]
    }

    "install an output provider without ambiguity when another port entry is an input socket" in {
      Given("one direct output provider and one input socket-looking port entry")
      given ExecutionContext = ExecutionContext.create()
      val provider = new Component() {}
        .withPort(Component.Port.of(_AiRunner("direct")))
      val input = new Component() {}
        .withPort(Component.Port.of(_InputRunnerSocket("input-only")))
      val consumer = _ConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider, input, consumer))

      Then("only the output provider is used")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("ping")).toOption.get.text shouldBe "direct:ping"
    }

    "return deterministic failure when no provider is available" in {
      Given("a consumer component without a matching provider")
      given ExecutionContext = ExecutionContext.create()
      val consumer = _ConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(consumer))

      Then("resolution fails explicitly")
      result shouldBe a[Consequence.Failure[_]]
    }

    "return deterministic failure when providers are ambiguous" in {
      Given("two equivalent providers for one socket")
      given ExecutionContext = ExecutionContext.create()
      val provider1 = _ProviderComponent("provider-a")
      val provider2 = _ProviderComponent("provider-b")
      val consumer = _ConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(provider1, provider2, consumer))

      Then("resolution fails rather than choosing implicitly")
      result shouldBe a[Consequence.Failure[_]]
    }

    "select a test provider component from an explicit SPI binding" in {
      Given("two equivalent providers and a consumer socket")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_explicit_binding")
      val prod = _initialized_component(subsystem, "prodprovider", _ProviderComponent("prod"))
      val test = _initialized_component(subsystem, "testprovider", _ProviderComponent("test"))
      val consumer = _initialized_component(subsystem, "consumer", _ConsumerComponent())
      val binding = SpiRuntimeBinding(
        socket = SpiSocketSelector(Some("consumer"), "ai-runner"),
        provider = SpiProviderSelector(component = Some("testprovider")),
        selection = SpiSelection()
      )

      When("SPI resolution runs with an explicit binding")
      val result = SpiResolver.resolve(Vector(prod, test, consumer), Vector(binding))

      Then("the provider from the bound component is installed")
      result shouldBe a[Consequence.Success[_]]
      consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "test:hello"
    }

    "reject provider service bindings until service-level matching is supported" in {
      Given("a binding that names an unsupported provider service selector")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("spi_provider_service_binding")
      val provider = _initialized_component(subsystem, "testprovider", _ProviderComponent("test"))
      val consumer = _initialized_component(subsystem, "consumer", _ConsumerComponent())
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

    "select a provider by mode and engine" in {
      Given("two providers with different selections")
      given ExecutionContext = ExecutionContext.create()
      val provider1 = _ProviderComponent(
        providername = "local",
        selection = SpiSelection(mode = Some("local"), engine = Some("ollama"))
      )
      val provider2 = _ProviderComponent(
        providername = "remote",
        selection = SpiSelection(mode = Some("remote"), engine = Some("http"))
      )
      val consumer = _ConsumerComponent(
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
      val geoprovider = _GeoResolverProviderComponent()
      val geoconsumer = _GeoResolverConsumerComponent()
      val toolprovider = _ToolchainRunnerProviderComponent()
      val toolconsumer = _ToolchainRunnerConsumerComponent()

      When("SPI resolution runs")
      val result = SpiResolver.resolve(Vector(geoprovider, geoconsumer, toolprovider, toolconsumer))

      Then("each socket receives its matching provider implementation")
      result shouldBe a[Consequence.Success[_]]
      geoconsumer.geoResolver.getClass.getName should include ("TracedGeoResolver")
      toolconsumer.toolchainRunner.convertSvgPagesToPdf(ConvertSvgPagesToPdfRequest(Vector("a.svg"))).toOption.get.pageCount shouldBe 1
    }
  }

  private final case class _ConsumerComponent(
    selection: SpiSelection = SpiSelection()
  ) extends Component with AiRunnerSocket {
    override def spiSelection: SpiSelection = selection
  }

  private def _initialized_component[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: A
  ): A = {
    val componentid = ComponentId(name)
    val core = Component.Core.create(
      name = name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = Protocol.empty
    )
    val init = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(init)
    component
  }

  private final case class _ProviderComponent(
    providername: String,
    selection: SpiSelection = SpiSelection()
  ) extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(_AiRunnerProvider(providername, selection))
  }

  private final case class _AiRunnerProvider(
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
      Consequence.success(_AiRunner(name))
  }

  private final case class _FailingProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(_FailingAiRunnerProvider())
  }

  private final case class _FailingAiRunnerProvider() extends SpiProvider[AiRunner] {
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
      Consequence.success(_FailingAiRunner())
  }

  private final case class _AiRunner(
    name: String
  ) extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class _FailingAiRunner() extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.operationInvalid("planned spi failure")

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class _InputRunnerSocket(
    name: String
  ) extends AiRunnerSocket with AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      Consequence.operationInvalid("generateRecord is not used by this spec")

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }

  private final case class _GeoResolverConsumerComponent() extends Component with GeoResolverSocket

  private final case class _GeoResolverProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(_GeoResolverProvider())
  }

  private final case class _GeoResolverProvider() extends SpiProvider[GeoResolver] {
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
      Consequence.success(_GeoResolver())
  }

  private final case class _GeoResolver() extends GeoResolver {
    def resolveRoute(req: org.goldenport.cncf.spi.geo.resolver.GeoResolveRouteRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoResolveRouteResponse] =
      Consequence.operationInvalid("resolveRoute is not used by this spec")

    def buildMapContext(req: org.goldenport.cncf.spi.geo.resolver.GeoBuildMapContextRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoBuildMapContextResponse] =
      Consequence.operationInvalid("buildMapContext is not used by this spec")

    def investigateLocation(req: org.goldenport.cncf.spi.geo.resolver.GeoInvestigateLocationRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoInvestigateLocationResponse] =
      Consequence.operationInvalid("investigateLocation is not used by this spec")

    def researchLinearFeatureRoute(req: org.goldenport.cncf.spi.geo.resolver.GeoResearchLinearFeatureRouteRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.geo.resolver.GeoResearchLinearFeatureRouteResponse] =
      Consequence.operationInvalid("researchLinearFeatureRoute is not used by this spec")
  }

  private final case class _ToolchainRunnerConsumerComponent() extends Component with ToolchainRunnerSocket

  private final case class _ToolchainRunnerProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(_ToolchainRunnerProvider())
  }

  private final case class _ToolchainRunnerProvider() extends SpiProvider[ToolchainRunner] {
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
      Consequence.success(_ToolchainRunner())
  }

  private final case class _ToolchainRunner() extends ToolchainRunner {
    def convertSvgToPdf(req: org.goldenport.cncf.spi.toolchain.runner.ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.operationInvalid("convertSvgToPdf is not used by this spec")

    def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.success(ToolchainArtifactResponse(true, 0, 0, "ok", req.out, req.svgFiles.length, Some("test")))
  }
}
