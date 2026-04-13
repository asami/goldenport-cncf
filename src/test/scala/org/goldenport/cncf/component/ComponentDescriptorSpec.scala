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
 * @version Apr. 13, 2026
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
  }
}
