package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.goldenport.cncf.component.repository.fixture.impl._ImplBackedComponent
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 16, 2026
 *  version Apr. 20, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryGeneratedSchemaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ComponentFactory generated schema enrichment" should {
    "include the parent package when resolving generated modules for impl-backed components" in {
      Given("a component implementation class under an impl package")
      val component = new _ImplBackedComponent

      When("ComponentFactory derives generated module package candidates")
      val packages = new ComponentFactory()._generated_module_package_names(component)

      Then("it searches both the impl package and its parent package")
      packages should contain("org.goldenport.cncf.component.repository.fixture.impl")
      packages should contain("org.goldenport.cncf.component.repository.fixture")
    }

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
      schema.columns.map(_.name.value) shouldBe Vector("id", "shortid", "name", "status")

      And("the schema exposes shortid as a system Web identity attribute without replacing canonical id")
      val shortid = schema.columns.find(_.name.value == "shortid").getOrElse(fail("shortid column is missing"))
      shortid.domain.datatype.name shouldBe "string"
      shortid.web.system shouldBe true
      shortid.web.readonly shouldBe true
      shortid.web.required shouldBe Some(false)

      And("the schema preserves generated Web hints for Web form composition")
      val status = schema.columns.find(_.name.value == "status").getOrElse(fail("status column is missing"))
      status.web.controlType shouldBe Some("select")
      status.web.values shouldBe Vector("draft", "submitted", "approved")
      status.web.required shouldBe Some(true)
      status.web.help shouldBe Some("CML generated status hint.")
    }

    "derive logical-to-physical store field mapping from generated companion INPUT_KEYS metadata" in {
      Given("a generated-style companion module exposing PROP and INPUT_KEYS constants")
      val factory = new ComponentFactory()
      val method = classOf[ComponentFactory].getDeclaredMethod("_entity_persistent_store_field_mapping", classOf[AnyRef])
      method.setAccessible(true)

      When("ComponentFactory derives the store field mapping from generated metadata")
      val mapping = method.invoke(factory, _GeneratedStyleEntityModule).asInstanceOf[Map[String, String]]

      Then("the bridge exposes the physical store field name for datastore queries")
      mapping.get("postedAt") shouldBe Some("posted_at")
      mapping.get("body") shouldBe Some("body")
    }
  }
}

private object _GeneratedStyleEntityModule {
  final val PROP_ID = "id"
  final val INPUT_KEYS_ID: List[String] = List("id").distinct
  final val PROP_BODY = "body"
  final val INPUT_KEYS_BODY: List[String] = List("body").distinct
  final val PROP_POSTED_AT = "postedAt"
  final val INPUT_KEYS_POSTED_AT: List[String] = List("postedAt", "posted_at").distinct

  object given_EntityPersistent_GeneratedStyleEntity extends EntityPersistent[_GeneratedStyleEntity] {
    def id(e: _GeneratedStyleEntity): EntityId = e.id
    def toRecord(e: _GeneratedStyleEntity): Record = Record.dataAuto("id" -> e.id, "body" -> e.body, "posted_at" -> e.postedAt)
    def fromRecord(r: Record): Consequence[_GeneratedStyleEntity] =
      Consequence.notImplemented("not used in this spec")
  }
}

private final case class _GeneratedStyleEntity(
  id: EntityId,
  body: String,
  postedAt: String
)
