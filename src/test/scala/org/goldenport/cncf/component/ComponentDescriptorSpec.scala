package org.goldenport.cncf.component

import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.entity.runtime.{EntityKind, EntityKindRuntimePolicy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{EntityApplicationDomain, EntityOperationKind, EntityUsageKind}
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 *  version Apr. 16, 2026
 *  version Apr. 24, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentDescriptorSpec extends AnyWordSpec with Matchers {
  "ComponentDescriptor" should {
    "decode entity classification fields in camelCase" in {
      val rec = Record.data(
        "entity" -> "Notice",
        "entityKind" -> "document",
        "usageKind" -> "public-content",
        "applicationDomain" -> "cms"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.entityName shouldBe "Notice"
      descriptor.entityKind shouldBe EntityKind.Document
      descriptor.usageKind shouldBe EntityUsageKind.PublicContent
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Cms
      descriptor.workingSetPolicy shouldBe None
      descriptor.workingSetPolicySource shouldBe None
      descriptor.effectiveWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.effectiveWorkingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "decode entity classification fields in snake_case" in {
      val rec = Record.data(
        "entity" -> "SalesOrder",
        "entity_kind" -> "workflow",
        "usage_kind" -> "business-object",
        "application_domain" -> "business"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.entityName shouldBe "SalesOrder"
      descriptor.entityKind shouldBe EntityKind.Workflow
      descriptor.usageKind shouldBe EntityUsageKind.BusinessRecord
      descriptor.operationKind shouldBe EntityOperationKind.Task
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Business
    }

    "keep legacy operationKind descriptors compatible" in {
      val rec = Record.data(
        "entity" -> "LegacyJob",
        "operationKind" -> "task"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.entityKind shouldBe EntityKind.Task
      descriptor.operationKind shouldBe EntityOperationKind.Task
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Task
      descriptor.workingSetPolicy shouldBe None
      descriptor.entityKindExplicit shouldBe false
      descriptor.operationKindExplicit shouldBe true
    }

    "parse all canonical entity kinds" in {
      EntityKind.parse("master") shouldBe EntityKind.Master
      EntityKind.parse("document") shouldBe EntityKind.Document
      EntityKind.parse("workflow") shouldBe EntityKind.Workflow
      EntityKind.parse("task") shouldBe EntityKind.Task
      EntityKind.parse("actor") shouldBe EntityKind.Actor
      EntityKind.parse("asset") shouldBe EntityKind.Asset
    }

    "centralize entity kind runtime policy defaults" in {
      EntityKindRuntimePolicy.forKind(EntityKind.Master).legacyOperationKind shouldBe EntityOperationKind.Resource
      EntityKindRuntimePolicy.forKind(EntityKind.Master).defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      EntityKindRuntimePolicy.forKind(EntityKind.Document).legacyOperationKind shouldBe EntityOperationKind.Resource
      EntityKindRuntimePolicy.forKind(EntityKind.Document).defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      EntityKindRuntimePolicy.forKind(EntityKind.Workflow).legacyOperationKind shouldBe EntityOperationKind.Task
      EntityKindRuntimePolicy.forKind(EntityKind.Workflow).defaultWorkingSetPolicy shouldBe None
      EntityKindRuntimePolicy.forKind(EntityKind.Workflow).workingSetDefaultNote should contain("active-only candidate; requires explicit state-field policy")
      EntityKindRuntimePolicy.forKind(EntityKind.Task).legacyOperationKind shouldBe EntityOperationKind.Task
      EntityKindRuntimePolicy.forKind(EntityKind.Task).defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      EntityKindRuntimePolicy.forKind(EntityKind.Actor).legacyOperationKind shouldBe EntityOperationKind.Resource
      EntityKindRuntimePolicy.forKind(EntityKind.Actor).defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      EntityKindRuntimePolicy.forKind(EntityKind.Asset).legacyOperationKind shouldBe EntityOperationKind.Resource
      EntityKindRuntimePolicy.forKind(EntityKind.Asset).defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
    }

    "reject unknown explicit entityKind" in {
      val rec = Record.data(
        "entity" -> "Bad",
        "entityKind" -> "documnt"
      )

      summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption shouldBe None
    }

    "apply entity kind working-set defaults only when entityKind is explicit" in {
      val product = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "Product",
        "entityKind" -> "master"
      )).toOption.get
      val blogPost = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "BlogPost",
        "entityKind" -> "document",
        "applicationDomain" -> "cms",
        "usageKind" -> "public-content"
      )).toOption.get
      val salesOrder = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "SalesOrder",
        "entityKind" -> "workflow"
      )).toOption.get
      val task = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "ImportTask",
        "entityKind" -> "task"
      )).toOption.get
      val actor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "ExternalAccount",
        "entityKind" -> "actor"
      )).toOption.get
      val asset = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "Image",
        "entityKind" -> "asset"
      )).toOption.get

      product.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      blogPost.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      salesOrder.toPlan.workingSetPolicy shouldBe None
      task.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      actor.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      asset.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      salesOrder.effectiveOperationKind shouldBe EntityOperationKind.Task
    }

    "prefer explicit working-set policy over entity kind default" in {
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "Product",
        "entityKind" -> "master",
        "workingSetPolicyKind" -> "disabled"
      )).toOption.get

      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.effectiveWorkingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "allow explicit operationKind only as a legacy compatibility override" in {
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(Record.data(
        "entity" -> "WorkflowProjection",
        "entityKind" -> "workflow",
        "operationKind" -> "resource"
      )).toOption.get

      descriptor.entityKind shouldBe EntityKind.Workflow
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.operationKindExplicit shouldBe true
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Resource
      descriptor.toPlan.workingSetPolicy shouldBe None
    }

    "preserve EK-01 constructor ABI without operationKindExplicit" in {
      val descriptor = EntityRuntimeDescriptor(
        entityName = "GeneratedWorkflow",
        collectionId = org.simplemodeling.model.datatype.EntityCollectionId("sys", "sys", "GeneratedWorkflow"),
        memoryPolicy = org.goldenport.cncf.entity.runtime.EntityMemoryPolicy.LoadToMemory,
        partitionStrategy = org.goldenport.cncf.entity.runtime.PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 64,
        maxEntitiesPerPartition = 10000,
        workingSet = None,
        workingSetPolicy = None,
        workingSetPolicySource = None,
        schema = None,
        aggregateNames = Vector.empty,
        viewNames = Vector.empty,
        entityKind = EntityKind.Workflow,
        usageKind = EntityUsageKind.default,
        operationKind = EntityKind.Workflow.legacyOperationKind,
        applicationDomain = EntityApplicationDomain.default,
        entityKindExplicit = true
      )

      descriptor.entityKindExplicit shouldBe true
      descriptor.operationKindExplicit shouldBe false
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Task
      descriptor.toPlan.workingSetPolicy shouldBe None
    }

    "derive entityKind from legacy constructor operation kind" in {
      val descriptor = EntityRuntimeDescriptor(
        entityName = "LegacyTask",
        collectionId = org.simplemodeling.model.datatype.EntityCollectionId("sys", "sys", "LegacyTask"),
        memoryPolicy = org.goldenport.cncf.entity.runtime.EntityMemoryPolicy.LoadToMemory,
        partitionStrategy = org.goldenport.cncf.entity.runtime.PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 64,
        maxEntitiesPerPartition = 10000,
        workingSet = None,
        workingSetPolicy = None,
        workingSetPolicySource = None,
        schema = None,
        aggregateNames = Vector.empty,
        viewNames = Vector.empty,
        usageKind = EntityUsageKind.default,
        operationKind = EntityOperationKind.Task,
        applicationDomain = EntityApplicationDomain.default
      )

      descriptor.entityKind shouldBe EntityKind.Task
      descriptor.entityKindExplicit shouldBe false
      descriptor.operationKindExplicit shouldBe true
      descriptor.toPlan.workingSetPolicy shouldBe None
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

    "decode nested recent working-set policy from descriptor" in {
      val rec = Record.data(
        "entity" -> "Post",
        "workingSetPolicy" -> Record.data(
          "kind" -> "recent",
          "duration" -> "24h",
          "timestampField" -> "postedAt"
        )
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt"))
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "decode flat resident-all working-set policy keys" in {
      val rec = Record.data(
        "entity" -> "Post",
        "working_set_policy_kind" -> "resident-all"
      )

      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }
  }
}
