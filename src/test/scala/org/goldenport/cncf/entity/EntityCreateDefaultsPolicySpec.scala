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
      targetRecord.getString("created_by") shouldBe Some("test-user-principal")
      otherRecord.getString("created_by") shouldBe Some("test-user-principal")
      targetRecord.getString("post_status").map(_.toLowerCase(java.util.Locale.ROOT).contains("published")) shouldBe Some(true)
      val rights = targetRecord.getRecord("security_attributes").flatMap(_.getRecord("rights"))
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

      val rights = targetRecord.getRecord("security_attributes").flatMap(_.getRecord("rights"))
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("read")) shouldBe Some(true)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("write")) shouldBe Some(false)
      rights.flatMap(_.getRecord("other")).flatMap(_.getBoolean("execute")) shouldBe Some(false)
    }
  }

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
