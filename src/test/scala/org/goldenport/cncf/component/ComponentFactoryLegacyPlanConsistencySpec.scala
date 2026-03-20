package org.goldenport.cncf.component

import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryLegacyPlanConsistencySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentFactory legacy bootstrap" should {
    "keep descriptor plan values consistent with actual memory realm settings" in {
      Given("a component bootstrapped without runtime plan provider")
      val factory = new ComponentFactory()
      val component = _component()

      When("legacy bootstrap runs")
      val bootstrapped = _bootstrap_collections(factory, component)
      val collection = bootstrapped.entity[Any]("default")
      val memory = collection.storage.memoryRealm
        .getOrElse(fail("legacy bootstrap should create memory realm"))
      val cid = EntityCollectionId("sys", "sys", "default")

      memory.put(_Entity(EntityId("tokyo", "sales", cid), "taro"))
      memory.put(_Entity(EntityId("tokyo", "sales", cid), "jiro"))

      Then("descriptor plan and runtime defaults are aligned")
      collection.descriptor.plan.maxPartitions shouldBe 64
      collection.descriptor.plan.maxEntitiesPerPartition shouldBe 10000
      memory.cachedEntityCount shouldBe 2
    }
  }

  private def _bootstrap_collections(
    factory: ComponentFactory,
    component: Component
  ): Component = {
    val method = classOf[ComponentFactory].getDeclaredMethod("_bootstrap_collections", classOf[Component])
    method.setAccessible(true)
    method.invoke(factory, component).asInstanceOf[Component]
  }

  private def _component(): Component = {
    val component = new Component() {}
    val core = Component.Core.create(
      name = "legacy_plan_consistency_spec",
      componentid = ComponentId("legacy_plan_consistency_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("legacy_plan_consistency_spec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("legacy_plan_consistency_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
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

