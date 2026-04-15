package org.goldenport.cncf.http

import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XString}
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
          BaseContent.Builder("body").label("Body text").build(),
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
      resolved.fields.find(_.name == "body").flatMap(_.label) shouldBe Some("Body text")
      resolved.fields.find(_.name == "body").exists(_.readonly) shouldBe true
    }

    "preserve Schema web metadata when WebDescriptor partially overrides a field" in {
      val schema = Schema(Vector(
        Column(
          BaseContent.Builder("status").label("Publication status").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            controlType = Some("select"),
            values = Vector("draft", "published"),
            required = Some(true),
            help = Some("Schema help")
          )
        )
      ))
      val component = _component_with_schema(schema)
      val descriptor = WebDescriptor(
        admin = Map(
          "entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
            WebDescriptor.AdminField(
              "status",
              WebDescriptor.FormControl(placeholder = Some("Descriptor placeholder"))
            )
          ))
        )
      )

      val resolved = WebSchemaResolver.resolveEntity(component, "notice-board", "notice", descriptor)
      val status = resolved.fields.find(_.name == "status").getOrElse(fail("status field is missing"))

      status.label shouldBe Some("Publication status")
      status.asControl.controlType shouldBe Some("select")
      status.asControl.values shouldBe Vector("draft", "published")
      status.asControl.required shouldBe Some(true)
      status.placeholder shouldBe Some("Descriptor placeholder")
      status.help shouldBe Some("Schema help")
    }

    "merge validation hints conservatively from Schema and WebDescriptor" in {
      val schema = Schema(Vector(
        Column(
          BaseContent.simple("code"),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            validation = WebValidationHints(
              minLength = Some(2),
              maxLength = Some(20),
              pattern = Some("^[A-Z]+$")
            )
          )
        )
      ))
      val component = _component_with_schema(schema)
      val descriptor = WebDescriptor(
        admin = Map(
          "entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
            WebDescriptor.AdminField(
              "code",
              WebDescriptor.FormControl(
                validation = WebValidationHints(
                  minLength = Some(1),
                  maxLength = Some(12),
                  pattern = Some("^[A-Z0-9]+$")
                )
              )
            )
          ))
        )
      )

      val resolved = WebSchemaResolver.resolveEntity(component, "notice-board", "notice", descriptor)
      val code = resolved.fields.find(_.name == "code").getOrElse(fail("code field is missing"))

      code.validation.minLength shouldBe Some(2)
      code.validation.maxLength shouldBe Some(12)
      code.validation.pattern shouldBe Some("^[A-Z0-9]+$")
    }

    "preserve Schema column order when schema-order strategy is selected" in {
      val schema = Schema(Vector(
        Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(BaseContent.simple("body"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
      ))
      val component = _component_with_schema(schema)

      val resolved = WebSchemaResolver.resolveEntity(
        component,
        "notice-board",
        "notice",
        WebDescriptor.empty,
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )

      resolved.fieldNames shouldBe Vector("title", "id", "body")
    }

    "apply id-first as an explicit admin list ordering strategy" in {
      val schema = Schema(Vector(
        Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(BaseContent.simple("body"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
      ))
      val component = _component_with_schema(schema)

      val resolved = WebSchemaResolver.resolveEntity(
        component,
        "notice-board",
        "notice",
        WebDescriptor.empty,
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )

      resolved.fieldNames shouldBe Vector("id", "title", "body")
    }

    "merge operation parameter schema with WebDescriptor controls" in {
      val schema = WebSchemaResolver.resolveOperationControls(
        "notice-board.notice.post-notice",
        Vector(
          ParameterDefinition(
            content = BaseContent.Builder("body").label("Notice body").build(),
            kind = ParameterDefinition.Kind.Argument,
            domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
            web = WebColumn(
              controlType = Some("select"),
              values = Vector("hello", "world"),
              required = Some(true),
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
          "zeta" -> WebDescriptor.FormControl(hidden = true),
          "accessToken" -> WebDescriptor.FormControl(hidden = true),
          "body" -> WebDescriptor.FormControl(placeholder = Some("Descriptor body."))
        )
      )

      schema.fieldNames shouldBe Vector("body", "published", "accessToken", "zeta")
      schema.fields.find(_.name == "body").flatMap(_.label) shouldBe Some("Notice body")
      schema.controls("body").controlType shouldBe Some("select")
      schema.controls("body").values shouldBe Vector("hello", "world")
      schema.controls("body").required shouldBe Some(true)
      schema.fields.find(_.name == "body").flatMap(_.placeholder) shouldBe Some("Descriptor body.")
      schema.fields.find(_.name == "body").flatMap(_.help) shouldBe Some("Notice body.")
      schema.controls("published").controlType shouldBe Some("checkbox")
      schema.controls("published").required shouldBe Some(false)
      schema.controls("accessToken").hidden shouldBe true
      schema.controls("zeta").hidden shouldBe true
    }
  }

  private def _component_with_schema(
    schema: Schema
  ) = {
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
    TestComponentFactory
      .create("notice_board", Protocol.empty)
      .withComponentDescriptors(Vector(descriptor))
  }
}
