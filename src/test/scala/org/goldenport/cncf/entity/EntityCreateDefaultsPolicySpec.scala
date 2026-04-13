package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityCreateDefaultsPolicySpec extends AnyWordSpec with Matchers {
  "EntityCreateDefaultsPolicy" should {
    "override create defaults by entity collection" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "target_entity")
      val other = EntityCollectionId("test", "a", "other_entity")
      val policy = EntityCreateDefaultsPolicy.byCollectionName(
        Map("target_entity" -> _custom_policy)
      )

      val targetRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )
      val otherRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "other"),
        EntityId("test", "other", other),
        EntityCreateOptions.default
      )

      targetRecord.getString("customDefault") shouldBe Some("target-default")
      otherRecord.getString("customDefault") shouldBe None
      targetRecord.getString("created_by") shouldBe Some("test_user_principal")
      otherRecord.getString("created_by") shouldBe Some("test_user_principal")
      targetRecord.getString("post_status").map(_.toLowerCase(java.util.Locale.ROOT).contains("published")) shouldBe Some(true)
      val rights = _rights(targetRecord)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("read")) shouldBe Some(true)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("write")) shouldBe Some(true)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("read")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
    }

    "support public-read create defaults for cms-like entities" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "target_entity")

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions(defaultProfiles = Set("public-read"))
      )

      val rights = _rights(targetRecord)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("read")) shouldBe Some(true)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("write")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
    }

    "use business/private owner-group-other permissions by default" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "business_entity")

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )

      val rights = _rights(targetRecord)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("read")) shouldBe Some(true)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("write")) shouldBe Some(true)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      rights.flatMap(_.getRecord("group")).flatMap(_.getBoolean("read")) shouldBe Some(false)
      rights.flatMap(_.getRecord("group")).flatMap(_.getBoolean("write")) shouldBe Some(false)
      rights.flatMap(_.getRecord("group")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("read")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("write")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
    }

    "use CMS/public-content read visibility and publication defaults" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "cms_entity")

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions(defaultProfiles = Set("cms", "publication", "public-content", "public-read"))
      )

      val rights = _rights(targetRecord)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("read")) shouldBe Some(true)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("write")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      targetRecord.getAny("publish_at").orElse(targetRecord.getAny("publishAt")) should not be empty
      targetRecord.getAny("public_at").orElse(targetRecord.getAny("publicAt")) should not be empty
      targetRecord.getString("published_by").orElse(targetRecord.getString("publishedBy")) shouldBe Some("test_user_principal")
    }

    "keep execute false for task-like create defaults" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "task_entity")

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions(defaultProfiles = Set("task"))
      )

      val rights = _rights(targetRecord)
      rights.flatMap(_.getRecord("owner")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      rights.flatMap(_.getRecord("group")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
    }

    "select owner id with an owner id policy" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "sales_order")
      val policy = EntityCreateDefaultsPolicy.withOwnerIdSelector(
        EntityCreateDefaultsPolicy.OwnerIdSelector.constant("seller_organization")
      )

      val targetRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )

      targetRecord.getString("owner_id").orElse(targetRecord.getString("ownerId")) shouldBe Some("seller_organization")
      targetRecord.getString("group_id").orElse(targetRecord.getString("groupId")) shouldBe Some("seller_organization")
    }
  }

  private def _rights(record: Record): Option[Record] =
    record.getRecord("rights")
      .orElse(record.getRecord("security_attributes").flatMap(_.getRecord("rights")))
      .orElse(record.getRecord("securityAttributes").flatMap(_.getRecord("rights")))

  private def _custom_policy: EntityCreateDefaultsPolicy =
    new EntityCreateDefaultsPolicy {
      def complementCreateRecord[T](
        record: Record,
        id: EntityId,
        options: EntityCreateOptions
      )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record =
        EntityCreateDefaultsPolicy.default
          .complementCreateRecord(record, id, options)
          ++ Record.dataAuto("customDefault" -> "target-default")
    }

  private def _persistent_create: EntityPersistentCreate[TestCreate] =
    new EntityPersistentCreate[TestCreate] {
      def id(e: TestCreate): Option[EntityId] = None
      def collection(e: TestCreate): EntityCollectionId = e.collectionId
      def toRecord(e: TestCreate): Record = Record.dataAuto("name" -> e.name)
    }
}

private final case class TestCreate(
  name: String,
  collectionId: EntityCollectionId
)
