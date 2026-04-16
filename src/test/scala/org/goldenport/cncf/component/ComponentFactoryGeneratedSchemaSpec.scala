package org.goldenport.cncf.component

import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 16, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryGeneratedSchemaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ComponentFactory generated schema enrichment" should {
    "copy generated companion schema into EntityRuntimeDescriptor during bootstrap" in {
      Given("a descriptor without schema and a generated entity companion with schema")
      val component = TestComponentFactory
        .create("generated_schema_component", Protocol.empty)
        .withComponentDescriptors(Vector(ComponentDescriptor(
          componentName = Some("generated_schema_component"),
          entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
            entityName = "order",
            collectionId = EntityCollectionId("test", "a", "order"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ))
        )))

      When("ComponentFactory bootstraps the component")
      new ComponentFactory().bootstrap(component)

      Then("the runtime descriptor exposes the companion schema")
      val schema = component.entityRuntimeDescriptor("order").flatMap(_.schema).getOrElse(fail("schema is missing"))
      schema.columns.map(_.name.value) shouldBe Vector("id", "name", "status")

      And("the schema preserves generated Web hints for Web form composition")
      val status = schema.columns.find(_.name.value == "status").getOrElse(fail("status column is missing"))
      status.web.controlType shouldBe Some("select")
      status.web.values shouldBe Vector("draft", "submitted", "approved")
      status.web.required shouldBe Some(true)
      status.web.help shouldBe Some("CML generated status hint.")
    }
  }
}
