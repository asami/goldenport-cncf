package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class SimpleEntityStorageShapePolicySpec extends AnyWordSpec with Matchers {
  "SimpleEntityStorageShapePolicy" should {
    "prefer target storage security identity over stale legacy security attributes" in {
      val targetRights = SecurityAttributes.privateOwnedBy("target_owner").rights
      val record = Record.dataAuto(
        "owner_id" -> "target_owner",
        "group_id" -> "target_group",
        "privilege_id" -> "target_privilege",
        "permission" -> SimpleEntityStorageShapePolicy.permissionJson(targetRights),
        "securityAttributes" -> SecurityAttributes.publicOwnedBy("legacy_owner").toRecord
      )

      val attributes = SimpleEntityStorageShapePolicy.securityAttributesFromRecord(record).get

      attributes.ownerId.id.value shouldBe "target_owner"
      attributes.groupId.id.value shouldBe "target_group"
      attributes.privilegeId.id.value shouldBe "target_privilege"
      attributes.rights.other.read shouldBe false
    }

    "remove target and legacy security fields before typed authorization overlay" in {
      val record = Record.dataAuto(
        "name" -> "target",
        "owner_id" -> "stale_target_owner",
        "ownerId" -> "stale_legacy_owner",
        "group_id" -> "stale_group",
        "privilege_id" -> "stale_privilege",
        "permission" -> SimpleEntityStorageShapePolicy.permissionJson(SecurityAttributes.publicOwnedBy("stale").rights),
        "rights" -> SecurityAttributes.publicOwnedBy("stale").rights.toRecord,
        "securityAttributes" -> SecurityAttributes.publicOwnedBy("legacy").toRecord,
        "security_attributes" -> SecurityAttributes.publicOwnedBy("legacy_snake").toRecord
      )

      val sanitized = SimpleEntityStorageShapePolicy.withoutSecurityFields(record)

      sanitized.getString("name") shouldBe Some("target")
      sanitized.getAny("owner_id") shouldBe None
      sanitized.getAny("ownerId") shouldBe None
      sanitized.getAny("group_id") shouldBe None
      sanitized.getAny("privilege_id") shouldBe None
      sanitized.getAny("permission") shouldBe None
      sanitized.getAny("rights") shouldBe None
      sanitized.getAny("securityAttributes") shouldBe None
      sanitized.getAny("security_attributes") shouldBe None
    }
  }
}
