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

    "resolve an impl factory when the accepted component class already lives under impl" in {
      Given("component class under impl package with sibling component factory")
      val subsystem = TestComponentFactory.emptySubsystem("provider-impl-factory")

      When("provider resolves the impl-packaged class source")
      val provided = ComponentProvider.provide(
        ComponentSource.ClassDef(classOf[org.goldenport.cncf.component.repository.fixture.impl._ImplBackedComponent], "test"),
        subsystem,
        ComponentOrigin.Repository("provider-test")
      )

      Then("the sibling impl factory is used instead of no-arg component instantiation")
      val component = provided.getOrElse(fail("component was not provided"))
      component.name shouldBe "impl-backed-primary"
      component.isPrimaryParticipant shouldBe true
      component.factoryOption shouldBe Some(org.goldenport.cncf.component.repository.fixture.impl.ComponentFactory.PrimaryFactory)
    }

    "resolve a plain component factory when the accepted component class already lives under impl" in {
      Given("component class under impl package with sibling plain component factory")
      val subsystem = TestComponentFactory.emptySubsystem("provider-plain-factory")

      When("provider resolves the impl-packaged class source")
      val provided = ComponentProvider.provide(
        ComponentSource.ClassDef(classOf[org.goldenport.cncf.component.repository.fixture.plain._PlainFactoryBackedComponent], "test"),
        subsystem,
        ComponentOrigin.Repository("provider-test")
      )

      Then("the plain factory is used instead of no-arg component instantiation")
      val component = provided.getOrElse(fail("component was not provided"))
      component.name shouldBe "plain-factory-primary"
      component.isPrimaryParticipant shouldBe true
      component.factoryOption.getOrElse(fail("factory was not attached")).getClass.getName shouldBe
        "org.goldenport.cncf.component.repository.fixture.plain.ComponentFactory"
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
