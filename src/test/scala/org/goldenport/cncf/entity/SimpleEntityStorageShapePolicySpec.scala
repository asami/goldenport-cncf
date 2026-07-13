package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr. 26, 2026
 * @version Jul. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class SimpleEntityStorageShapePolicySpec extends AnyWordSpec with Matchers {
  "SimpleEntityStorageShapePolicy" should {
    "roundtrip compact permission JSON without legacy rights fields" in {
      val source = SecurityAttributes.publicOwnedBy("owner").copy(
        rights = SecurityAttributes.Rights(
          owner = SecurityAttributes.Rights.Permissions(read = true, write = true, execute = false),
          group = SecurityAttributes.Rights.Permissions(read = true, write = false, execute = false),
          other = SecurityAttributes.Rights.Permissions(read = false, write = false, execute = false)
        )
      )

      val json = SimpleEntityStorageShapePolicy.permissionJson(source.rights)
      val decoded = SimpleEntityStorageShapePolicy.permissionRightsFromJson(json)

      decoded shouldBe Some(source.rights)
      json.contains("securityAttributes") shouldBe false
      json.contains("security_attributes") shouldBe false
    }

    "use target names for framework management fields and leave domain scalar names unchanged" in {
      SimpleEntityStorageShapePolicy.targetName("shortId") shouldBe "short_id"
      SimpleEntityStorageShapePolicy.targetName("createdAt") shouldBe "created_at"
      SimpleEntityStorageShapePolicy.targetName("updatedBy") shouldBe "updated_by"
      SimpleEntityStorageShapePolicy.targetName("postStatus") shouldBe "post_status"
      SimpleEntityStorageShapePolicy.targetName("ownerId") shouldBe "owner_id"
      SimpleEntityStorageShapePolicy.targetName("groupId") shouldBe "group_id"
      SimpleEntityStorageShapePolicy.targetName("privilegeId") shouldBe "privilege_id"
      SimpleEntityStorageShapePolicy.targetName("name") shouldBe "name"
      SimpleEntityStorageShapePolicy.targetName("age") shouldBe "age"
      SimpleEntityStorageShapePolicy.targetName("body") shouldBe "body"
    }

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

    "preserve private permissions when a datastore decodes permission JSON as a Record" in {
      val privaterights = SecurityAttributes.privateOwnedBy("alice").rights
      val permission = Record.dataAuto(
        "owner" -> privaterights.owner.toRecord,
        "group" -> privaterights.group.toRecord,
        "other" -> privaterights.other.toRecord
      )
      val record = Record.dataAuto(
        "owner_id" -> "alice",
        "group_id" -> "alice",
        "privilege_id" -> "alice",
        "permission" -> permission
      )

      val attributes = SimpleEntityStorageShapePolicy.securityAttributesFromRecord(record).get

      attributes.rights.owner.read shouldBe true
      attributes.rights.group.read shouldBe false
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
