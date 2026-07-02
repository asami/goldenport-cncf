package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiGenerateResponse, AiRunner, AiRunnerSocket}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  2, 2026
 * @version Jul.  2, 2026
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
  }

  private final case class _ConsumerComponent(
    selection: SpiSelection = SpiSelection()
  ) extends Component with AiRunnerSocket {
    override def spiSelection: SpiSelection = selection
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

  private final case class _AiRunner(
    name: String
  ) extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      Consequence.success(AiGenerateResponse(s"$name:${req.prompt}"))

    def chat(req: org.goldenport.cncf.spi.ai.runner.AiChatRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.spi.ai.runner.AiChatResponse] =
      Consequence.operationInvalid("chat is not used by this spec")
  }
}
