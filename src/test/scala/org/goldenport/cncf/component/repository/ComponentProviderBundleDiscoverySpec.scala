package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.protocol.Protocol

/*
 * @since   Apr. 22, 2026
 * @version Aug. 11, 2026
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
        ComponentSource.ClassDef(classOf[ProvidedComponent], "test"),
        subsystem,
        ComponentOrigin.Repository("provider-test")
      )

      Then("the primary runtime participant is returned")
      val component = provided.getOrElse(fail("component was not provided"))
      component.name shouldBe "org.goldenport.cncf.test.ProvidedPrimary"
      component.isPrimaryParticipant shouldBe true
      component.factoryOption shouldBe Some(ProvidedComponent.Factory.PrimaryFactory)
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
      component.name shouldBe "org.goldenport.cncf.test.ImplBackedPrimary"
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
      component.name shouldBe "org.goldenport.cncf.test.PlainFactoryPrimary"
      component.isPrimaryParticipant shouldBe true
      component.factoryOption.getOrElse(fail("factory was not attached")).getClass.getName shouldBe
        "org.goldenport.cncf.component.repository.fixture.plain.ComponentFactory"
    }
  }
}

final class ProvidedComponent extends Component

object ProvidedComponent {
  object Factory extends Component.BundleFactory {
    object PrimaryFactory extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new ProvidedComponent

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "org.goldenport.cncf.test.ProvidedPrimary",
          ComponentId("org.goldenport.cncf.test.ProvidedPrimary"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ProvidedPrimary")),
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
          "org.goldenport.cncf.test.ProvidedComponentlet",
          ComponentId("org.goldenport.cncf.test.ProvidedComponentlet"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.ProvidedComponentlet")),
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
