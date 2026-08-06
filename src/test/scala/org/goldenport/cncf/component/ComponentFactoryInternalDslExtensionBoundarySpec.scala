package org.goldenport.cncf.component

import java.lang.reflect.Modifier
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  1, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryInternalDslExtensionBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final class ExtensionHookProbeFactory extends Component.Factory {
    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      ???

    override protected def create_Component(params: ComponentCreate): Component =
      ???

    override protected def initialize_component_c(
      component: Component,
      params: ComponentInit
    ) =
      super.initialize_component_c(component, params)
  }

  "Component.Factory internal DSL extension boundary" should {
    "expose final internal operations and reviewed authorization extensions as camelCase methods" in {
      Given("the exact internal-DSL factory operation names")
      val names = Vector(
        "aggregateCollectionBindings",
        "aggregateBehaviorBindings",
        "createAggregateFromRecord",
        "createAggregateBehavior",
        "entityUsageKind",
        "entityOperationKind",
        "entityApplicationDomain",
        "serviceOperationModel",
        "entityAccessMode",
        "entityAccessRelations"
      )

      When("the public Factory surface is reflected")
      val methods = classOf[Component.Factory].getMethods.toVector

      Then("each fixed internal operation is public and final")
      names.foreach { name =>
        val method = methods.find(_.getName == name).getOrElse(fail(s"missing Factory method: $name"))
        Modifier.isPublic(method.getModifiers) shouldBe true
        Modifier.isFinal(method.getModifiers) shouldBe true
      }
    }

    "remove the legacy snake_case factory operation names" in {
      Given("the former public internal-DSL method names")
      val legacy = Set(
        "aggregate_collection_bindings",
        "aggregate_behavior_bindings",
        "create_aggregate_from_record",
        "create_aggregate_behavior",
        "authorize_operation_access",
        "authorize_operation_entity",
        "authorize_unit_of_work",
        "entity_usage_kind",
        "entity_operation_kind",
        "entity_application_domain",
        "service_operation_model",
        "entity_access_mode",
        "entity_access_relations"
      )

      When("the Factory surface is reflected")
      val names = classOf[Component.Factory].getMethods.map(_.getName).toSet

      Then("no removed compatibility alias remains")
      names.intersect(legacy) shouldBe empty
    }

    "retain reviewed authorization and construction Factory extension points" in {
      Given("the factory extension and construction hook surface")
      val nonfinalmethods = classOf[Component.Factory].getDeclaredMethods
        .filter(method => Modifier.isPublic(method.getModifiers))
        .filterNot(_.isSynthetic)
        .filterNot(method => Modifier.isFinal(method.getModifiers))
        .map(_.getName)
        .toSet

      When("the Scala source compiles a Factory subclass overriding each protected hook")
      classOf[ExtensionHookProbeFactory] should not be null

      Then("only the public service, authorization, and construction hooks remain non-final")
      nonfinalmethods shouldBe Set(
        "serviceFactory",
        "initializationParameterDeclarations",
        "initializationParameterPathRoutes",
        "authorizeOperationAccess",
        "authorizeOperationEntity",
        "authorizeUnitOfWork",
        "create_Core",
        "create_Component",
        "initialize_component_c"
      )
    }
  }
}
