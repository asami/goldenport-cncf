package org.goldenport.cncf.component

import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{ContinuationRuntime, ContinuationRuntimeSource}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ComponentFactoryContinuationRuntimeBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory continuation runtime bootstrap" should {
    "prefer an explicit Component runtime source over its Factory source" in {
      Given("a Component and its Factory with distinct continuation runtimes")
      val componentRuntime = new ContinuationRuntime.InMemory
      val factoryRuntime = new ContinuationRuntime.InMemory
      val component = _initialize(
        new RuntimeSourceComponent(componentRuntime),
        new RuntimeSourceFactory(factoryRuntime)
      )

      When("the Component is bootstrapped")
      val bootstrapped = new ComponentFactory().bootstrapC(component).toOption.getOrElse(
        fail("component bootstrap should succeed")
      )

      Then("the explicit Component runtime is retained")
      bootstrapped.continuationRuntime shouldBe componentRuntime
    }

    "fall back to the Factory runtime source when the Component has none" in {
      Given("a Component without a runtime source and a Factory with one")
      val factoryRuntime = new ContinuationRuntime.InMemory
      val component = _initialize(new Component() {}, new RuntimeSourceFactory(factoryRuntime))

      When("the Component is bootstrapped")
      val bootstrapped = new ComponentFactory().bootstrapC(component).toOption.getOrElse(
        fail("component bootstrap should succeed")
      )

      Then("the Factory runtime is installed")
      bootstrapped.continuationRuntime shouldBe factoryRuntime
    }
  }

  private def _initialize(component: Component, factory: Component.Factory): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test.ContinuationRuntimeBootstrapSpec")
    component.initialize(ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("continuation_runtime_bootstrap_spec"),
      core = Component.Core.create(
        name = componentid.name,
        componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid),
        protocol = Protocol.empty,
        factory = factory
      ),
      origin = ComponentOrigin.Builtin
    ))
  }

  private final class RuntimeSourceComponent(
    runtime: ContinuationRuntime
  ) extends Component with ContinuationRuntimeSource {
    override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(runtime)
  }

  private final class RuntimeSourceFactory(
    runtime: ContinuationRuntime
  ) extends Component.Factory {
    override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(runtime)

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("test fixture factory does not create components")

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("test fixture factory does not create component cores")
  }
}
