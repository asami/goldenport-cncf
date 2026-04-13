package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 *  version Apr.  7, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class PortBindingSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "PortApi" should {
    "resolve a service contract from a named requirement" in {
      Given("a requirement and a resolution-side port api")
      val requirement = _Requirement("generate")
      val api = _port_api()

      When("the requirement is resolved")
      val result = api.resolve(requirement)

      Then("the expected service contract is returned")
      result.toOption.get.name shouldBe "generate-service"
      result.toOption.get.runtimeClass shouldBe classOf[_GenerateService]
    }
  }

  "VariationPoint" should {
    "expose the current normalized variation state" in {
      Given("a requirement with configured variation fields")
      given ExecutionContext = ExecutionContext.create()
      val requirement = _Requirement(
        capability = "generate",
        provider = Some("gemma"),
        mode = Some("local"),
        engine = Some("ollama")
      )
      val variation = _variation_point()

      When("the current variation is requested")
      val result = variation.current(requirement)

      Then("the normalized variation is returned")
      result.toOption.get shouldBe VariationSelection(
        provider = Some("gemma"),
        mode = Some("local"),
        engine = Some("ollama")
      )
    }

    "inject a new variation selection into the requirement" in {
      Given("a requirement and a variation selection")
      given ExecutionContext = ExecutionContext.create()
      val requirement = _Requirement("generate")
      val variation = _variation_point()
      val selection = VariationSelection(
        provider = Some("remote-gemma"),
        mode = Some("remote"),
        engine = Some("http")
      )

      When("the variation is injected")
      val result = variation.inject(requirement, selection)

      Then("the resulting requirement reflects the injected variation")
      result.toOption.get shouldBe requirement.copy(
        provider = Some("remote-gemma"),
        mode = Some("remote"),
        engine = Some("http")
      )
    }
  }

  "Component.Binding" should {
    "bind the first compatible extension point as an injected service" in {
      Given("a port definition with multiple extension points")
      given ExecutionContext = ExecutionContext.create()
      val binding = _binding()
      val requirement = _Requirement(
        capability = "generate",
        provider = Some("gemma"),
        mode = Some("local"),
        engine = Some("ollama")
      )

      When("binding is performed")
      val result = binding.bind(requirement)

      Then("the first compatible extension point provides the service")
      result.toOption.get.generate("hello") shouldBe "generated:local-gemma"
    }

    "return failure when no compatible extension point exists" in {
      Given("a port definition and an unsupported variation")
      given ExecutionContext = ExecutionContext.create()
      val binding = _binding()
      val requirement = _Requirement(
        capability = "generate",
        provider = Some("unknown"),
        mode = Some("remote"),
        engine = Some("none")
      )

      When("binding is performed")
      val result = binding.bind(requirement)

      Then("an explicit failure is returned")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "Binding result and Component.Port registry" should {
    "remain separate from runtime invocation semantics" in {
      Given("an injected service stored in the component service registry")
      val service = _GenerateServiceImpl("local-gemma")
      val registry = Component.Port.of(service)

      When("the service is loaded from the registry")
      val result = registry.get[_GenerateService]

      Then("the injected service is retrieved directly without a runtime port invocation")
      result.map(_.generate("hello")) shouldBe Some("generated:local-gemma")
    }
  }

  "Component binding registry" should {
    "store named bindings and keep legacy binding accessor as first entry" in {
      Given("a component with multiple named bindings")
      val component = new Component() {}
      val first = _binding()
      val second = _binding()

      When("named bindings are registered")
      component
        .withBinding("generate", first)
        .withBinding("chat", second)

      Then("all named bindings are available and the legacy accessor remains populated")
      component.binding("generate") shouldBe Some(first)
      component.binding("chat") shouldBe Some(second)
      component.bindings.keySet shouldBe Set("generate", "chat")
      component.binding should not be empty
    }

    "replace an existing binding when the same name is used" in {
      Given("a component with a named binding")
      val component = new Component() {}
      val first = _binding()
      val second = _binding()

      When("the same binding name is registered twice")
      component
        .withBinding("generate", first)
        .withBinding("generate", second)

      Then("the later binding replaces the earlier one")
      component.binding("generate") shouldBe Some(second)
      component.bindings.size shouldBe 1
    }

    "install a named binding result into Component.Port registry" in {
      Given("a component with a named binding and an empty service registry")
      given ExecutionContext = ExecutionContext.create()
      val component = new Component() {}
      val requirement = _Requirement(
        capability = "generate",
        provider = Some("gemma"),
        mode = Some("local"),
        engine = Some("ollama")
      )
      component.withBinding("generate", _binding())

      When("the named binding is installed")
      val result = component.install_binding[_Requirement, _GenerateService](
        name = "generate",
        req = requirement
      )

      Then("the resolved service is available from Component.Port")
      result.toOption.get.port.get[_GenerateService].map(_.generate("hello")) shouldBe Some("generated:local-gemma")
    }

    "return failure when a requested named binding is missing" in {
      Given("a component without the requested named binding")
      given ExecutionContext = ExecutionContext.create()
      val component = new Component() {}
      component.withBinding("chat", _binding())

      When("a different binding name is installed")
      val result = component.install_binding[_Requirement, _GenerateService](
        name = "generate",
        req = _Requirement("generate")
      )

      Then("an explicit failure is returned")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _binding(): Component.Binding[_Requirement, _GenerateService] =
    Component.Binding(
      org.goldenport.cncf.component.Port(
        api = _port_api(),
        spi = Vector(_remote_extension_point(), _local_extension_point()),
        variation = _variation_point()
      )
    )

  private def _port_api(): PortApi[_Requirement, _GenerateService] =
    new PortApi[_Requirement, _GenerateService] {
      def resolve(req: _Requirement): Consequence[ServiceContract[_GenerateService]] =
        req.capability match {
          case "generate" =>
            Consequence.success(
              ServiceContract(
                name = "generate-service",
                runtimeClass = classOf[_GenerateService]
              )
            )
          case m =>
            Consequence.operationInvalid(s"unsupported capability: $m")
        }
    }

  private def _variation_point(): VariationPoint[_Requirement] =
    new VariationPoint[_Requirement] {
      def current(req: _Requirement)(using ExecutionContext): Consequence[VariationSelection] =
        Consequence.success(
          VariationSelection(
            provider = req.provider,
            mode = req.mode,
            engine = req.engine
          )
        )

      def inject(
        req: _Requirement,
        selection: VariationSelection
      )(using ExecutionContext): Consequence[_Requirement] =
        Consequence.success(
          req.copy(
            provider = selection.provider,
            mode = selection.mode,
            engine = selection.engine
          )
        )
    }

  private def _local_extension_point(): ExtensionPoint[_GenerateService] =
    new ExtensionPoint[_GenerateService] {
      def supports(
        contract: ServiceContract[_GenerateService],
        variation: VariationSelection
      )(using ExecutionContext): Boolean =
        contract.name == "generate-service" &&
          variation.provider.contains("gemma") &&
          variation.mode.contains("local") &&
          variation.engine.contains("ollama")

      def provide(
        contract: ServiceContract[_GenerateService],
        variation: VariationSelection
      )(using ExecutionContext): Consequence[_GenerateService] =
        Consequence.success(_GenerateServiceImpl("local-gemma"))
    }

  private def _remote_extension_point(): ExtensionPoint[_GenerateService] =
    new ExtensionPoint[_GenerateService] {
      def supports(
        contract: ServiceContract[_GenerateService],
        variation: VariationSelection
      )(using ExecutionContext): Boolean =
        contract.name == "generate-service" &&
          variation.provider.contains("gemma") &&
          variation.mode.contains("remote")

      def provide(
        contract: ServiceContract[_GenerateService],
        variation: VariationSelection
      )(using ExecutionContext): Consequence[_GenerateService] =
        Consequence.success(_GenerateServiceImpl("remote-gemma"))
    }

  private final case class _Requirement(
    capability: String,
    provider: Option[String] = None,
    mode: Option[String] = None,
    engine: Option[String] = None
  )

  private trait _GenerateService {
    def generate(prompt: String): String
  }

  private final case class _GenerateServiceImpl(
    name: String
  ) extends _GenerateService {
    def generate(prompt: String): String =
      s"generated:$name"
  }
}
