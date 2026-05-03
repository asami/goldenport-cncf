package org.goldenport.cncf.component

import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimePlan, PartitionStrategy, WorkingSetDefinition}
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 *  version Apr. 24, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryRuntimePlanActivationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory discover bootstrap" should {
    "activate runtime plans supplied by component provider" in {
      Given("a component that provides a non-empty EntityRuntimePlan")
      val component = _component_with_runtime_plan()
      val space = new ComponentRepositorySpace() {
        override def discover(): Vector[Component] = Vector(component)
      }
      val factory = new ComponentFactory(space)

      When("discover bootstrap is executed")
      val discovered = factory.discover()
      val bootstrapped = discovered.head
      val collection = bootstrapped.entity[Any]("person")
      val memory = collection.storage.memoryRealm
        .getOrElse(fail("memory realm should be enabled by plan"))

      Then("plan branch is applied and plan-driven settings are active")
      discovered.size shouldBe 1
      bootstrapped.workingSetEntityNames should contain("person")
      collection.descriptor.plan.maxPartitions shouldBe 2
      collection.descriptor.plan.maxEntitiesPerPartition shouldBe 1
      collection.storage.storeRealm.values.size shouldBe 2
      _eventually_int(memory.cachedEntityCount, 1)
    }

    "bootstrap direct-added components when a subsystem receives an unbootstrapped instance" in {
      Given("a subsystem and a component created directly from a bundle factory")
      val subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_direct_add")
      val component = _component_factory_bundle().createPrimary(
        ComponentCreate(subsystem, ComponentOrigin.Builtin)
      )

      When("the raw component is added to the subsystem")
      subsystem.add(component)
      val resolved = subsystem.findComponent("runtime_plan_activation_direct_add")
        .getOrElse(fail("missing bootstrapped component"))
      val collection = resolved.entity[Any]("person")

      Then("subsystem add bootstraps collections and runtime plans")
      resolved.collectionsBootstrapped shouldBe true
      collection.descriptor.collectionId shouldBe EntityCollectionId("sys", "sys", "person")
      collection.storage.storeRealm.values.size shouldBe 2
      resolved.workingSetEntityNames should contain("person")
    }
  }

  private def _eventually_int(value: => Int, expected: Int): Unit = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
    while (value != expected && System.nanoTime() < deadline)
      Thread.sleep(10)
    value shouldBe expected
  }

  private def _component_with_runtime_plan(): Component = {
    val component = new Component() with EntityRuntimePlanProvider {
      private val cid = EntityCollectionId("sys", "sys", "person")
      private val first = _Entity(EntityId("tokyo", "sales", cid), "taro")
      private val second = _Entity(EntityId("tokyo", "sales", cid), "jiro")

      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "person",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = Some(WorkingSetDefinition[Any]("person", Vector(first, second))),
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 2,
            maxEntitiesPerPartition = 1
          )
        )
    }
    val core = Component.Core.create(
      name = "runtime_plan_activation_spec",
      componentid = ComponentId("runtime_plan_activation_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("runtime_plan_activation_spec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("runtime_plan_activation_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _component_factory_bundle(): Component.SinglePrimaryBundleFactory =
    new Component.SinglePrimaryBundleFactory with EntityRuntimePlanProvider {
      private val cid = EntityCollectionId("sys", "sys", "person")
      private val first = _Entity(EntityId("tokyo", "sales", cid), "taro")
      private val second = _Entity(EntityId("tokyo", "sales", cid), "jiro")

      override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
        Vector(
          EntityRuntimePlan[Any](
            entityName = "person",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = Some(WorkingSetDefinition[Any]("person", Vector(first, second))),
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 2,
            maxEntitiesPerPartition = 1
          )
        )

      override protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          name = "runtime_plan_activation_direct_add",
          componentid = ComponentId("runtime_plan_activation_direct_add"),
          instanceid = ComponentInstanceId.default(ComponentId("runtime_plan_activation_direct_add")),
          protocol = Protocol.empty,
          factory = this
        )
    }

  private final case class _Entity(
    id: EntityId,
    name: String
  ) extends EntityPersistable {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name
      )
  }
}
