package org.goldenport.cncf.component

import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.security.{EntityApplicationDomain, EntityOperationKind, EntityUsageKind}
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 *  version Apr. 13, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentDescriptorSpec extends AnyWordSpec with Matchers {
  "ComponentDescriptor" should {
    "decode entity classification fields in camelCase" in {
      val rec = Record.data(
        "entity" -> "Notice",
        "usageKind" -> "public-content",
        "operationKind" -> "resource",
        "applicationDomain" -> "cms"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.entityName shouldBe "Notice"
      descriptor.usageKind shouldBe EntityUsageKind.PublicContent
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Cms
    }

    "decode entity classification fields in snake_case" in {
      val rec = Record.data(
        "entity" -> "SalesOrder",
        "usage_kind" -> "business-object",
        "operation_kind" -> "resource",
        "application_domain" -> "business"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.entityName shouldBe "SalesOrder"
      descriptor.usageKind shouldBe EntityUsageKind.BusinessRecord
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Business
    }

    "decode descriptor-first component root with componentlets" in {
      val rec = Record.data(
        "component" -> Record.data(
          "name" -> "sample-component",
          "kind" -> "component",
          "isPrimary" -> "true",
          "archiveScope" -> "car-root",
          "boundedContext" -> "default"
        ),
        "componentlets" -> Vector(
          Record.data(
            "name" -> "notice-admin",
            "kind" -> "componentlet",
            "isPrimary" -> "false",
            "archiveScope" -> "car-bundled",
            "implementationClass" -> "domain.impl.NoticeAdminComponent",
            "factoryObject" -> "domain.impl.NoticeAdminComponent"
          ),
          Record.data(
            "name" -> "public-notice",
            "kind" -> "componentlet",
            "isPrimary" -> "false",
            "archiveScope" -> "car-bundled",
            "implementationClass" -> "domain.impl.PublicNoticeComponent",
            "factoryObject" -> "domain.impl.PublicNoticeComponent"
          )
        )
      )

      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      descriptor.componentName shouldBe Some("sample-component")
      descriptor.name shouldBe Some("sample-component")
      descriptor.extensions.get("kind") shouldBe Some("component")
      descriptor.extensions.get("archiveScope") shouldBe Some("car-root")
      descriptor.componentlets.map(_.name) shouldBe Vector("notice-admin", "public-notice")
      descriptor.componentlets.map(_.kind) shouldBe Vector(Some("componentlet"), Some("componentlet"))
      descriptor.componentlets.flatMap(_.implementationClass) shouldBe Vector(
        "domain.impl.NoticeAdminComponent",
        "domain.impl.PublicNoticeComponent"
      )
    }

    "decode componentlet names from simple string list" in {
      val rec = Record.data(
        "component" -> Record.data(
          "name" -> "sample-component"
        ),
        "componentlets" -> Vector("notice-admin", "public-notice")
      )

      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      descriptor.componentlets.map(_.name) shouldBe Vector("notice-admin", "public-notice")
      descriptor.componentlets.forall(_.kind.isEmpty) shouldBe true
    }
  }
}
