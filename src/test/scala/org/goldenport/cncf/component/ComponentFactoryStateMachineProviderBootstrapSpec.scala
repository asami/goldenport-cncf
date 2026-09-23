package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{ActionExecution, ProviderExecutionRequest, ProviderIdentity, StateMachineProvider, StateMachineProviderBinding, StateMachineProviderSource, StateMachineRequiredOperationIdentity}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryStateMachineProviderBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory provider bootstrap" should {
    "prefer an explicit Component Provider source over its Factory hooks" in {
      Given("a Component and Factory with different explicit Provider bindings")
      val required = StateMachineRequiredOperationIdentity("required.component")
      val componentprovider = new ProbeProvider("provider.component")
      val factoryprovider = new ProbeProvider("provider.factory")
      val factorysource = new ProviderSourceFactory(
        Vector(StateMachineProviderBinding(required, factoryprovider.identity)),
        Vector(factoryprovider)
      )
      val component = _initialize(
        new ProviderSourceComponent(
          Vector(StateMachineProviderBinding(required, componentprovider.identity)),
          Vector(componentprovider)
        ),
        factorysource
      )

      When("the Component is bootstrapped")
      val bootstrapped = new ComponentFactory().bootstrapC(component).toOption.getOrElse(
        fail("component bootstrap should succeed")
      )

      Then("the Component binding is installed without Provider invocation")
      bootstrapped.stateMachineProviderResolver.resolve(required).toOption shouldBe Some(componentprovider)
      componentprovider.executionCalls shouldBe 0
      factoryprovider.executionCalls shouldBe 0
    }

    "fall back to Factory Provider hooks when the Component has no Provider source" in {
      Given("a Component without a Provider source and a Factory with one explicit binding")
      val required = StateMachineRequiredOperationIdentity("required.factory")
      val provider = new ProbeProvider("provider.factory")
      val component = _initialize(
        new Component() {},
        new ProviderSourceFactory(
          Vector(StateMachineProviderBinding(required, provider.identity)),
          Vector(provider)
        )
      )

      When("the Component is bootstrapped")
      val bootstrapped = new ComponentFactory().bootstrapC(component).toOption.getOrElse(
        fail("component bootstrap should succeed")
      )

      Then("the Factory binding is installed without Provider invocation")
      bootstrapped.stateMachineProviderResolver.resolve(required).toOption shouldBe Some(provider)
      provider.executionCalls shouldBe 0
    }

    "keep a Component with no Provider source valid with the empty resolver" in {
      Given("a Component without a Provider source or Factory")
      val required = StateMachineRequiredOperationIdentity("required.unbound")
      val component = _initialize(new Component() {})

      When("the Component is bootstrapped")
      val bootstrapped = new ComponentFactory().bootstrapC(component).toOption.getOrElse(
        fail("component bootstrap should succeed")
      )

      Then("the empty resolver fails closed for the unbound Required SPI identity")
      _expect_failure(bootstrapped.stateMachineProviderResolver.resolve(required))
    }

    "reject invalid bindings before the Component is marked bootstrapped" in {
      Given("a Component Provider source binding to an absent Provider")
      val required = StateMachineRequiredOperationIdentity("required.invalid")
      val component = _initialize(
        new ProviderSourceComponent(
          Vector(StateMachineProviderBinding(required, ProviderIdentity("provider.absent"))),
          Vector.empty
        )
      )

      When("the Component is bootstrapped")
      val result = new ComponentFactory().bootstrapC(component)

      Then("bootstrap fails closed and leaves the Component unbootstrapped")
      _expect_failure(result)
      component.collectionsBootstrapped shouldBe false
    }
  }

  private def _initialize(
    component: Component,
    factory: Component.Factory = null
  ): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test.StateMachineProviderBootstrapSpec")
    val core =
      if (factory == null)
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = Protocol.empty
        )
      else
        Component.Core.create(
          name = componentid.name,
          componentId = componentid,
          instanceId = ComponentInstanceId.default(componentid),
          protocol = Protocol.empty,
          factory = factory
        )
    component.initialize(ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("state_machine_provider_bootstrap_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    ))
  }

  private def _expect_failure[A](result: Consequence[A]): Unit =
    result match {
      case Consequence.Failure(_) => succeed
      case Consequence.Success(_) => fail("a typed Consequence failure was required")
    }

  private final class ProviderSourceComponent(
    bindings: Vector[StateMachineProviderBinding],
    providers: Vector[StateMachineProvider]
  ) extends Component with StateMachineProviderSource {
    override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] = bindings
    override def stateMachineProviders: Vector[StateMachineProvider] = providers
  }

  private final class ProviderSourceFactory(
    bindings: Vector[StateMachineProviderBinding],
    providers: Vector[StateMachineProvider]
  ) extends Component.Factory {
    override def stateMachineProviderBindings: Vector[StateMachineProviderBinding] = bindings
    override def stateMachineProviders: Vector[StateMachineProvider] = providers

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("test fixture factory does not create components")

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("test fixture factory does not create component cores")
  }

  private final class ProbeProvider(value: String) extends StateMachineProvider {
    private var _execution_calls = 0

    def executionCalls: Int = _execution_calls

    override val identity: ProviderIdentity = ProviderIdentity(value)

    override def execute(request: ProviderExecutionRequest): ActionExecution = {
      val _ = request
      _execution_calls += 1
      throw new AssertionError("Provider execution is outside provider bootstrap")
    }
  }
}
