package org.goldenport.cncf.entity

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Apr. 13, 2026
 *  version Apr. 20, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityCreateDefaultsPolicySpec extends AnyWordSpec with Matchers {
  "EntityCreateDefaultsPolicy" should {
    "store shortid from EntityId entropy as a SimpleEntity identity attribute" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "target_entity")
      val entityId = EntityId("test", "target", target)

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        entityId,
        EntityCreateOptions.default
      )

      targetRecord.getString("shortid") shouldBe Some(entityId.parts.entropy)
    }

    "complement lifecycle audit fields as Instant and non-optional updater fields" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "target_entity")
      val entityId = EntityId("test", "target", target)

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        entityId,
        EntityCreateOptions.default
      )

      _any(targetRecord, "createdAt", "created_at") shouldBe a[Instant]
      _any(targetRecord, "updatedAt", "updated_at") shouldBe a[Instant]
      targetRecord.getString("createdBy").orElse(targetRecord.getString("created_by")) shouldBe Some("test_user_principal")
      targetRecord.getString("updatedBy").orElse(targetRecord.getString("updated_by")) shouldBe Some("test_user_principal")
    }

    "preserve explicitly supplied shortid create value" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "target_entity")

      val targetRecord = EntityCreateDefaultsPolicy.default.complementCreateRecord(
        Record.dataAuto("name" -> "target", "shortid" -> "manual-shortid"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )

      targetRecord.getString("shortid") shouldBe Some("manual-shortid")
    }

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

    "select owner and group ids with selector policies" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "sales_order")
      val policy = EntityCreateDefaultsPolicy.withOwnerAndGroupIdSelectors(
        EntityCreateDefaultsPolicy.OwnerIdSelector.constant("seller_organization"),
        EntityCreateDefaultsPolicy.GroupIdSelector.constant("sales_ops")
      )

      val targetRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )

      targetRecord.getString("owner_id").orElse(targetRecord.getString("ownerId")) shouldBe Some("seller_organization")
      targetRecord.getString("group_id").orElse(targetRecord.getString("groupId")) shouldBe Some("sales_ops")
    }

    "select tenant and organization ids with selector policies" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val target = EntityCollectionId("test", "a", "sales_order")
      val policy = EntityCreateDefaultsPolicy.withSelectors(
        ownerIdSelector = EntityCreateDefaultsPolicy.OwnerIdSelector.constant("seller_organization"),
        groupIdSelector = EntityCreateDefaultsPolicy.GroupIdSelector.constant("sales_ops"),
        tenantIdSelector = EntityCreateDefaultsPolicy.TenantIdSelector.constant("tenant_a"),
        organizationIdSelector = EntityCreateDefaultsPolicy.OrganizationIdSelector.constant("seller_org")
      )

      val targetRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "target"),
        EntityId("test", "target", target),
        EntityCreateOptions.default
      )

      targetRecord.getString("tenant_id").orElse(targetRecord.getString("tenantId")) shouldBe Some("tenant_a")
      targetRecord.getString("organization_id").orElse(targetRecord.getString("organizationId")) shouldBe Some("seller_org")
    }

    "select entity-level create defaults by entity name" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val salesOrder = EntityCollectionId("test", "a", "sales_order")
      val invoice = EntityCollectionId("test", "a", "invoice")
      val salesOrderPolicy = EntityCreateDefaultsPolicy.withSelectors(
        ownerIdSelector = EntityCreateDefaultsPolicy.OwnerIdSelector.constant("seller_organization"),
        groupIdSelector = EntityCreateDefaultsPolicy.GroupIdSelector.constant("sales_ops"),
        tenantIdSelector = EntityCreateDefaultsPolicy.TenantIdSelector.constant("tenant_a"),
        organizationIdSelector = EntityCreateDefaultsPolicy.OrganizationIdSelector.constant("seller_org")
      )
      val policy = EntityCreateDefaultsPolicy.byEntityName(
        Map("sales_order" -> salesOrderPolicy)
      )

      val salesOrderRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "sales-order"),
        EntityId("test", "sales_order", salesOrder),
        EntityCreateOptions.default
      )
      val invoiceRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "invoice"),
        EntityId("test", "invoice", invoice),
        EntityCreateOptions.default
      )

      salesOrderRecord.getString("owner_id").orElse(salesOrderRecord.getString("ownerId")) shouldBe Some("seller_organization")
      salesOrderRecord.getString("group_id").orElse(salesOrderRecord.getString("groupId")) shouldBe Some("sales_ops")
      salesOrderRecord.getString("tenant_id").orElse(salesOrderRecord.getString("tenantId")) shouldBe Some("tenant_a")
      salesOrderRecord.getString("organization_id").orElse(salesOrderRecord.getString("organizationId")) shouldBe Some("seller_org")
      invoiceRecord.getString("owner_id").orElse(invoiceRecord.getString("ownerId")) shouldBe Some("test_user_principal")
      invoiceRecord.getString("tenant_id").orElse(invoiceRecord.getString("tenantId")) shouldBe None
      invoiceRecord.getString("organization_id").orElse(invoiceRecord.getString("organizationId")) shouldBe None
    }

    "use application-level create defaults with entity-level overrides" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistentCreate[TestCreate] = _persistent_create
      val customer = EntityCollectionId("test", "a", "customer")
      val salesOrder = EntityCollectionId("test", "a", "sales_order")
      val applicationDefault = EntityCreateDefaultsPolicy.withSelectors(
        ownerIdSelector = EntityCreateDefaultsPolicy.OwnerIdSelector.constant("application_owner"),
        groupIdSelector = EntityCreateDefaultsPolicy.GroupIdSelector.constant("application_group"),
        tenantIdSelector = EntityCreateDefaultsPolicy.TenantIdSelector.constant("tenant_a")
      )
      val salesOrderPolicy = EntityCreateDefaultsPolicy.withSelectors(
        ownerIdSelector = EntityCreateDefaultsPolicy.OwnerIdSelector.constant("seller_organization"),
        groupIdSelector = EntityCreateDefaultsPolicy.GroupIdSelector.constant("sales_ops"),
        tenantIdSelector = EntityCreateDefaultsPolicy.TenantIdSelector.constant("tenant_b")
      )
      val policy = EntityCreateDefaultsPolicy.withApplicationDefault(
        applicationDefault,
        Map("sales_order" -> salesOrderPolicy)
      )

      val customerRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "customer"),
        EntityId("test", "customer", customer),
        EntityCreateOptions.default
      )
      val salesOrderRecord = policy.complementCreateRecord(
        Record.dataAuto("name" -> "sales-order"),
        EntityId("test", "sales_order", salesOrder),
        EntityCreateOptions.default
      )

      customerRecord.getString("owner_id").orElse(customerRecord.getString("ownerId")) shouldBe Some("application_owner")
      customerRecord.getString("group_id").orElse(customerRecord.getString("groupId")) shouldBe Some("application_group")
      customerRecord.getString("tenant_id").orElse(customerRecord.getString("tenantId")) shouldBe Some("tenant_a")
      salesOrderRecord.getString("owner_id").orElse(salesOrderRecord.getString("ownerId")) shouldBe Some("seller_organization")
      salesOrderRecord.getString("group_id").orElse(salesOrderRecord.getString("groupId")) shouldBe Some("sales_ops")
      salesOrderRecord.getString("tenant_id").orElse(salesOrderRecord.getString("tenantId")) shouldBe Some("tenant_b")
    }
  }

  private def _rights(record: Record): Option[Record] =
    record.getRecord("rights")
      .orElse(record.getRecord("security_attributes").flatMap(_.getRecord("rights")))
      .orElse(record.getRecord("securityAttributes").flatMap(_.getRecord("rights")))

  private def _any(record: Record, primary: String, secondary: String): Any =
    record.getAny(primary).orElse(record.getAny(secondary)).getOrElse(fail(s"missing $primary/$secondary"))

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
