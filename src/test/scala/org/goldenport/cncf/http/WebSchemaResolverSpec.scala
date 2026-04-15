package org.goldenport.cncf.http

import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, XBoolean, XString}
import org.goldenport.value.BaseContent
import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 16, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebSchemaResolverSpec extends AnyWordSpec with Matchers {
  "WebSchemaResolver" should {
    "use EntityRuntimeDescriptor Schema as the entity operation metadata source" in {
      val schema = Schema(Vector(
        Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(BaseContent.simple("published"), ValueDomain(datatype = XBoolean, multiplicity = Multiplicity.ZeroOne)),
        Column(
          BaseContent.simple("body"),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            controlType = Some("textarea"),
            required = Some(true),
            readonly = true,
            placeholder = Some("Write the notice body."),
            help = Some("Notice body shown on the board.")
          )
        )
      ))
      val descriptor = ComponentDescriptor(
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
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))

      val resolved = WebSchemaResolver.resolveEntity(component, "notice-board", "notice", WebDescriptor.empty)

      resolved.source shouldBe WebSchemaResolver.Source.Schema
      resolved.fieldNames shouldBe Vector("id", "title", "published", "body")
      resolved.controls("published").controlType shouldBe Some("checkbox")
      resolved.controls("published").required shouldBe Some(false)
      resolved.controls("body").controlType shouldBe Some("textarea")
      resolved.controls("body").required shouldBe Some(true)
      resolved.fields.find(_.name == "body").flatMap(_.placeholder) shouldBe Some("Write the notice body.")
      resolved.fields.find(_.name == "body").flatMap(_.help) shouldBe Some("Notice body shown on the board.")
      resolved.fields.find(_.name == "body").exists(_.readonly) shouldBe true
    }

    "merge operation parameter schema with WebDescriptor controls" in {
      val schema = WebSchemaResolver.resolveOperationControls(
        "notice-board.notice.post-notice",
        Vector(
          ParameterDefinition(
            content = BaseContent.simple("body"),
            kind = ParameterDefinition.Kind.Argument,
            domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
            web = WebColumn(
              controlType = Some("textarea"),
              placeholder = Some("Write the notice body."),
              help = Some("Notice body.")
            )
          ),
          ParameterDefinition(
            content = BaseContent.simple("published"),
            kind = ParameterDefinition.Kind.Argument,
            domain = ValueDomain(datatype = XBoolean, multiplicity = Multiplicity.ZeroOne)
          )
        ),
        Map(
          "accessToken" -> WebDescriptor.FormControl(hidden = true)
        )
      )

      schema.fieldNames shouldBe Vector("body", "published", "accessToken")
      schema.controls("body").controlType shouldBe Some("textarea")
      schema.controls("body").required shouldBe Some(true)
      schema.fields.find(_.name == "body").flatMap(_.placeholder) shouldBe Some("Write the notice body.")
      schema.fields.find(_.name == "body").flatMap(_.help) shouldBe Some("Notice body.")
      schema.controls("published").controlType shouldBe Some("checkbox")
      schema.controls("published").required shouldBe Some(false)
      schema.controls("accessToken").hidden shouldBe true
    }
  }
}
