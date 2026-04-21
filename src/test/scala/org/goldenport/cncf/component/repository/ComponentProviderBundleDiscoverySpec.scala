package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.protocol.Protocol

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentProviderBundleDiscoverySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "ComponentProvider" should {
    "resolve the primary participant through companion bundle factory discovery" in {
      Given("component class with companion bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("provider-bundle")

      When("provider resolves the class source")
      val provided = ComponentProvider.provide(
        ComponentSource.ClassDef(classOf[_ProvidedComponent], "test"),
        subsystem,
        ComponentOrigin.Repository("provider-test")
      )

      Then("the primary runtime participant is returned")
      val component = provided.getOrElse(fail("component was not provided"))
      component.name shouldBe "provided-primary"
      component.isPrimaryParticipant shouldBe true
      component.factoryOption shouldBe Some(_ProvidedComponent.Factory.PrimaryFactory)
    }
  }
}

final class _ProvidedComponent extends Component

object _ProvidedComponent {
  object Factory extends Component.BundleFactory {
    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new _ProvidedComponent

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "provided-primary",
          ComponentId("provided_primary"),
          ComponentInstanceId.default(ComponentId("provided_primary")),
          Protocol.empty,
          this
        )
    }

    object AuxiliaryFactory extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "provided-componentlet",
          ComponentId("provided_componentlet"),
          ComponentInstanceId.default(ComponentId("provided_componentlet")),
          Protocol.empty,
          this
        )
    }

    def primaryFactory: Component.PrimaryComponentFactory =
      PrimaryFactory

    override def componentletFactories: Vector[Component.ComponentletFactory] =
      Vector(AuxiliaryFactory)
  }
}
