package org.goldenport.cncf.entity.runtime

import org.goldenport.cncf.component.{Component, ComponentDescriptor}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.ViewDefinition
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityQueryFieldResolverSpec extends AnyWordSpec with Matchers {
  "EntityQueryFieldResolver" should {
    "resolve query field names to CML schema names by normalized comparison" in {
      val resolver = EntityQueryFieldResolver(_component_with_schema(), "notice")

      resolver.resolve("recipientName") shouldBe "recipientName"
      resolver.resolve("recipient_name") shouldBe "recipientName"
      resolver.resolve("recipient-name") shouldBe "recipientName"
      resolver.resolve("unknown_field") shouldBe "unknown_field"
    }

    "rewrite structured Query paths without changing query values" in {
      val resolver = EntityQueryFieldResolver(_component_with_schema(), "notice")
      val query = Query.plan(
        condition = org.goldenport.record.Record.data("recipient_name" -> "bob"),
        where = Query.And(Vector(
          Query.Eq("recipient-name", "bob"),
          Query.Contains("subject", "hello", caseInsensitive = true)
        )),
        sort = Vector(Query.SortKey("sender_name"))
      )

      resolver.rewrite(query).toRecord().asMap("where").toString should include ("recipientName")
      resolver.rewrite(query).toRecord().asMap("where").toString should include ("hello")
      resolver.rewrite(query).sort.map(_.path) shouldBe Vector("senderName")
    }

    "resolve view fields from component view metadata and keep schema names" in {
      val resolver = EntityQueryFieldResolver(_component_with_schema_and_view(), "notice")

      resolver.viewFields("summary") shouldBe Vector("id", "recipientName", "subject")
      resolver.defaultSearchFields("summary") shouldBe Vector("recipientName", "subject")
    }

    "fall back to the requested name when schema metadata is not available" in {
      val resolver = EntityQueryFieldResolver(new Component {}, "notice")

      resolver.resolve("recipient_name") shouldBe "recipient_name"
      resolver.viewFields("summary") shouldBe Vector.empty
      resolver.defaultSearchFields("summary") shouldBe Vector.empty
    }
  }

  private def _component_with_schema(): Component =
    (new Component {})
      .withComponentDescriptors(Vector(_descriptor(_notice_schema)))

  private def _component_with_schema_and_view(): Component = {
    val component = new Component {
      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "notice_view",
            entityName = "notice",
            viewFields = Map("summary" -> Vector("id", "recipient_name", "subject"))
          )
        )
    }
    component.withComponentDescriptors(Vector(_descriptor(_notice_schema)))
  }

  private def _descriptor(schema: Schema): ComponentDescriptor =
    ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = EntityCollectionId("sys", "sys", "notice"),
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(schema)
        )
      )
    )

  private def _notice_schema: Schema =
    Schema(Vector(
      Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
      Column(BaseContent.simple("senderName"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
      Column(BaseContent.simple("recipientName"), ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)),
      Column(BaseContent.simple("subject"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
      Column(BaseContent.simple("body"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
    ))
}
