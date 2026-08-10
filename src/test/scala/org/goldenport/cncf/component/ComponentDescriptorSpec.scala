package org.goldenport.cncf.component

import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.entity.runtime.{EntityKind, EntityKindRuntimePolicy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{EntityApplicationDomain, EntityOperationKind, EntityUsageKind}
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 *  version Apr. 16, 2026
 *  version Apr. 24, 2026
 *  version May.  7, 2026
 *  version Jul. 31, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ComponentDescriptor" should {
    "decode legacy records and validate the versioned component-style projection" which {
    "decode entity classification fields in camelCase" in {
      Given("a camelCase entity runtime descriptor record")
      val rec = Record.data(
        "entity" -> "Notice",
        "entityKind" -> "document",
        "usageKind" -> "public-content",
        "applicationDomain" -> "cms"
      )

      When("the record is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the normalized entity classification is retained")
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
      Given("a snake_case entity runtime descriptor record")
      val rec = Record.data(
        "entity" -> "SalesOrder",
        "entity_kind" -> "workflow",
        "usage_kind" -> "business-object",
        "application_domain" -> "business"
      )

      When("the record is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the normalized entity classification is retained")
      descriptor.entityName shouldBe "SalesOrder"
      descriptor.entityKind shouldBe EntityKind.Workflow
      descriptor.usageKind shouldBe EntityUsageKind.BusinessRecord
      descriptor.operationKind shouldBe EntityOperationKind.Task
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Business
    }

    "decode shared business record usage" in {
      Given("a shared business record descriptor")
      val rec = Record.data(
        "entity" -> "SharedCatalog",
        "usageKind" -> "shared-record",
        "applicationDomain" -> "business"
      )

      When("the record is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("its shared-record usage is retained")
      descriptor.usageKind shouldBe EntityUsageKind.SharedRecord
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.applicationDomain shouldBe EntityApplicationDomain.Business
    }

    "keep legacy operationKind descriptors compatible" in {
      Given("a legacy operationKind-only descriptor")
      val rec = Record.data(
        "entity" -> "LegacyJob",
        "operationKind" -> "task"
      )

      When("the record is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the compatible entity classification is derived")
      descriptor.entityKind shouldBe EntityKind.Task
      descriptor.operationKind shouldBe EntityOperationKind.Task
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Task
      descriptor.workingSetPolicy shouldBe None
      descriptor.entityKindExplicit shouldBe false
      descriptor.operationKindExplicit shouldBe true
    }

    "parse all canonical entity kinds" in {
      Given("the canonical entity-kind spellings")
      When("each spelling is parsed")
      val master = EntityKind.parse("master")
      val document = EntityKind.parse("document")
      val workflow = EntityKind.parse("workflow")
      val task = EntityKind.parse("task")
      val actor = EntityKind.parse("actor")
      val asset = EntityKind.parse("asset")
      val system = EntityKind.parse("system")

      Then("the corresponding entity kind is returned")
      master shouldBe EntityKind.Master
      document shouldBe EntityKind.Document
      workflow shouldBe EntityKind.Workflow
      task shouldBe EntityKind.Task
      actor shouldBe EntityKind.Actor
      asset shouldBe EntityKind.Asset
      system shouldBe EntityKind.System
    }

    "centralize entity kind runtime policy defaults" in {
      Given("the canonical entity kinds")
      When("their runtime policies are resolved")
      val master = EntityKindRuntimePolicy.forKind(EntityKind.Master)
      val document = EntityKindRuntimePolicy.forKind(EntityKind.Document)
      val workflow = EntityKindRuntimePolicy.forKind(EntityKind.Workflow)
      val task = EntityKindRuntimePolicy.forKind(EntityKind.Task)
      val actor = EntityKindRuntimePolicy.forKind(EntityKind.Actor)
      val asset = EntityKindRuntimePolicy.forKind(EntityKind.Asset)
      val system = EntityKindRuntimePolicy.forKind(EntityKind.System)

      Then("each policy exposes its defined defaults")
      master.legacyOperationKind shouldBe EntityOperationKind.Resource
      master.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      document.legacyOperationKind shouldBe EntityOperationKind.Resource
      document.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      workflow.legacyOperationKind shouldBe EntityOperationKind.Task
      workflow.defaultWorkingSetPolicy shouldBe None
      workflow.workingSetDefaultNote should contain("active-only candidate; requires explicit state-field policy")
      task.legacyOperationKind shouldBe EntityOperationKind.Task
      task.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      actor.legacyOperationKind shouldBe EntityOperationKind.Resource
      actor.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      asset.legacyOperationKind shouldBe EntityOperationKind.Resource
      asset.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      system.legacyOperationKind shouldBe EntityOperationKind.Resource
      system.defaultWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
    }

    "reject unknown explicit entityKind" in {
      Given("a descriptor with an unknown explicit entity kind")
      val rec = Record.data(
        "entity" -> "Bad",
        "entityKind" -> "documnt"
      )

      When("the record is decoded")
      val decoded = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption

      Then("the public decoder rejects it")
      decoded shouldBe None
    }

    "apply entity kind working-set defaults only when entityKind is explicit" in {
      Given("descriptors with explicit entity kinds")
      val productrecord = Record.data(
        "entity" -> "Product",
        "entityKind" -> "master"
      )
      val blogpostrecord = Record.data(
        "entity" -> "BlogPost",
        "entityKind" -> "document",
        "applicationDomain" -> "cms",
        "usageKind" -> "public-content"
      )
      val salesorderrecord = Record.data(
        "entity" -> "SalesOrder",
        "entityKind" -> "workflow"
      )
      val taskrecord = Record.data(
        "entity" -> "ImportTask",
        "entityKind" -> "task"
      )
      val actorrecord = Record.data(
        "entity" -> "ExternalAccount",
        "entityKind" -> "actor"
      )
      val assetrecord = Record.data(
        "entity" -> "Image",
        "entityKind" -> "asset"
      )
      val systemrecord = Record.data(
        "entity" -> "Job",
        "entityKind" -> "system"
      )

      When("the records are decoded and the descriptors are converted to runtime plans")
      val product = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(productrecord).toOption.get
      val blogpost = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(blogpostrecord).toOption.get
      val salesorder = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(salesorderrecord).toOption.get
      val task = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(taskrecord).toOption.get
      val actor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(actorrecord).toOption.get
      val asset = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(assetrecord).toOption.get
      val system = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(systemrecord).toOption.get

      Then("each explicit kind contributes its documented default")
      product.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      blogpost.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      salesorder.toPlan.workingSetPolicy shouldBe None
      task.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      actor.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      asset.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      system.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      salesorder.effectiveOperationKind shouldBe EntityOperationKind.Task
    }

    "prefer explicit working-set policy over entity kind default" in {
      Given("an explicit working-set policy and an entity-kind default")
      val rec = Record.data(
        "entity" -> "Product",
        "entityKind" -> "master",
        "workingSetPolicyKind" -> "disabled"
      )

      When("the record is decoded and the descriptor is converted to a runtime plan")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the explicit policy has precedence")
      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.toPlan.workingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.effectiveWorkingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "allow explicit operationKind only as a legacy compatibility override" in {
      Given("an explicit legacy operation override")
      val rec = Record.data(
        "entity" -> "WorkflowProjection",
        "entityKind" -> "workflow",
        "operationKind" -> "resource"
      )

      When("the descriptor is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the explicit legacy operation remains observable")
      descriptor.entityKind shouldBe EntityKind.Workflow
      descriptor.operationKind shouldBe EntityOperationKind.Resource
      descriptor.operationKindExplicit shouldBe true
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Resource
      descriptor.toPlan.workingSetPolicy shouldBe None
    }

    "preserve EK-01 constructor ABI without operationKindExplicit" in {
      Given("the EK-01 constructor arguments without an explicit operation kind")
      When("the descriptor is constructed")
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

      Then("the compatibility flags retain their ABI semantics")
      descriptor.entityKindExplicit shouldBe true
      descriptor.operationKindExplicit shouldBe false
      descriptor.effectiveOperationKind shouldBe EntityOperationKind.Task
      descriptor.toPlan.workingSetPolicy shouldBe None
    }

    "derive entityKind from legacy constructor operation kind" in {
      Given("the legacy constructor arguments with only an operation kind")
      When("the descriptor is constructed")
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

      Then("the entity kind is derived compatibly")
      descriptor.entityKind shouldBe EntityKind.Task
      descriptor.entityKindExplicit shouldBe false
      descriptor.operationKindExplicit shouldBe true
      descriptor.toPlan.workingSetPolicy shouldBe None
    }

    "decode descriptor-first component root with componentlets" in {
      Given("a descriptor-first root with bundled componentlets")
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

      When("the root is decoded")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      Then("the component and componentlet metadata are retained")
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
      Given("a component descriptor with simple componentlet names")
      val rec = Record.data(
        "component" -> Record.data(
          "name" -> "sample-component"
        ),
        "componentlets" -> Vector("notice-admin", "public-notice")
      )

      When("the root is decoded")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      Then("each name becomes a componentlet without optional metadata")
      descriptor.componentlets.map(_.name) shouldBe Vector("notice-admin", "public-notice")
      descriptor.componentlets.forall(_.kind.isEmpty) shouldBe true
    }

    "decode nested recent working-set policy from descriptor" in {
      Given("a descriptor with a nested recent working-set policy")
      val rec = Record.data(
        "entity" -> "Post",
        "workingSetPolicy" -> Record.data(
          "kind" -> "recent",
          "duration" -> "24h",
          "timestampField" -> "postedAt"
        )
      )

      When("the descriptor is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the duration and timestamp field are retained")
      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt"))
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "decode flat resident-all working-set policy keys" in {
      Given("a descriptor with flat snake_case working-set keys")
      val rec = Record.data(
        "entity" -> "Post",
        "working_set_policy_kind" -> "resident-all"
      )

      When("the descriptor is decoded")
      val descriptor = summon[RecordDecoder[EntityRuntimeDescriptor]].fromRecord(rec).toOption.get

      Then("the resident-all policy is retained")
      descriptor.workingSetPolicy shouldBe Some(WorkingSetPolicy.ResidentAll)
      descriptor.workingSetPolicySource shouldBe Some(WorkingSetPolicySource.Cml)
    }

    "require a catalog-matching component style snapshot for schema v2" in {
      Given("a numeric schema-v2 descriptor with the complete canonical style snapshot")
      val rec = Record.data(
        "schemaVersion" -> 2,
        "name" -> "sample-car-artifact",
        "version" -> "2.0.0",
        "component" -> Record.data("name" -> "sample-component"),
        "entities" -> Vector(Record.data(
          "entity" -> "SampleEntity",
          "usageKind" -> "business-object",
          "applicationDomain" -> "business"
        )),
        "extensions" -> Record.data("descriptor.extension" -> "enabled"),
        "extensionBindings" -> Record.data("descriptor.binding" -> "bound"),
        "config" -> Record.data("descriptor.config" -> "configured"),
        "componentStyle" -> Record.createFull(Vector(
          "apiVersion" -> "cncf.textus/v1",
          "provider" -> "cncf",
          "id" -> "full-fledged-with-standalone@1",
          "version" -> 1,
          "parameterSchema" -> Record.createFull(Vector(
            "type" -> "object",
            "properties" -> Record.empty,
            "required" -> Vector.empty[String],
            "additionalProperties" -> false
          )),
          "parameters" -> Record.empty,
          "provides" -> Record.data(
            "bundles" -> Vector("domain.full@1"),
            "capabilities" -> Vector("user.fixed-context-compatible@1", "user.multi-user@1"),
            "effective" -> Vector(
              "domain.aggregate@1", "domain.command@1", "domain.domain-event@1", "domain.entity@1",
              "domain.optimistic-concurrency@1", "domain.persistence@1", "domain.projection@1",
              "domain.query@1", "domain.transaction@1", "user.fixed-context-compatible@1", "user.multi-user@1"
            )
          ),
          "requires" -> Record.data(
            "subsystemCapabilities" -> Vector(
              "datastore.optimistic-concurrency@1", "datastore.persistent@1",
              "datastore.transactional@1", "user-context.current@1"
            )
          )
        ))
      )

      When("the descriptor is decoded through the public RecordDecoder boundary")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      Then("the catalog-matching snapshot and root descriptor fields are retained")
      descriptor.schemaVersion shouldBe Some(2)
      descriptor.name shouldBe Some("sample-car-artifact")
      descriptor.version shouldBe Some("2.0.0")
      descriptor.componentName shouldBe Some("sample-component")
      descriptor.entityRuntimeDescriptors.map(_.entityName) shouldBe Vector("SampleEntity")
      descriptor.extensions shouldBe Map("descriptor.extension" -> "enabled")
      descriptor.extensionBindings.asMap shouldBe Map("descriptor.binding" -> "bound")
      descriptor.config shouldBe Map("descriptor.config" -> "configured")
      descriptor.requireComponentStyleSnapshotC.toOption.map(_.id.canonical) shouldBe Some("full-fledged-with-standalone@1")
      ComponentDescriptor.componentStyleProjectionJson(descriptor.requireComponentStyleSnapshotC.toOption.get).hcursor.get[String]("id").toOption shouldBe Some("full-fledged-with-standalone@1")
    }

    "decode a canonical schema-v3 descriptor with a catalog-matching component style snapshot" in {
      Given("a canonical schema-v3 namespace, id, and version with the complete canonical style snapshot")
      val rec = Record.data(
        "schemaVersion" -> 3,
        "component" -> Record.data(
          "namespace" -> "org.simplemodeling.textus",
          "id" -> "TextusArtScene",
          "version" -> "0.1.0-SNAPSHOT"
        ),
        "componentStyle" -> Record.createFull(Vector(
          "apiVersion" -> "cncf.textus/v1",
          "provider" -> "cncf",
          "id" -> "full-fledged-with-standalone@1",
          "version" -> 1,
          "parameterSchema" -> Record.createFull(Vector(
            "type" -> "object",
            "properties" -> Record.empty,
            "required" -> Vector.empty[String],
            "additionalProperties" -> false
          )),
          "parameters" -> Record.empty,
          "provides" -> Record.data(
            "bundles" -> Vector("domain.full@1"),
            "capabilities" -> Vector("user.fixed-context-compatible@1", "user.multi-user@1"),
            "effective" -> Vector(
              "domain.aggregate@1", "domain.command@1", "domain.domain-event@1", "domain.entity@1",
              "domain.optimistic-concurrency@1", "domain.persistence@1", "domain.projection@1",
              "domain.query@1", "domain.transaction@1", "user.fixed-context-compatible@1", "user.multi-user@1"
            )
          ),
          "requires" -> Record.data(
            "subsystemCapabilities" -> Vector(
              "datastore.optimistic-concurrency@1", "datastore.persistent@1",
              "datastore.transactional@1", "user-context.current@1"
            )
          )
        ))
      )

      When("the descriptor is decoded through the public RecordDecoder boundary")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      Then("the canonical identity and catalog-matching snapshot are retained")
      descriptor.schemaVersion shouldBe Some(3)
      descriptor.componentId.map(_.name) shouldBe Some("org.simplemodeling.textus.TextusArtScene")
      descriptor.componentName shouldBe Some("org.simplemodeling.textus.TextusArtScene")
      descriptor.version shouldBe Some("0.1.0-SNAPSHOT")
      descriptor.requireComponentStyleSnapshotC.toOption.map(_.id.canonical) shouldBe Some("full-fledged-with-standalone@1")
      ComponentDescriptor.componentStyleProjectionJson(descriptor.requireComponentStyleSnapshotC.toOption.get).hcursor.get[String]("id").toOption shouldBe Some("full-fledged-with-standalone@1")
    }

    "keep a legacy descriptor readable but reject it for schema-v2 style admission" in {
      Given("a style-less legacy descriptor and invalid schema-v2 producer forms")
      val legacyrecord = Record.data(
        "component" -> Record.data("name" -> "legacy-component")
      )
      val missingstylerecord = Record.data(
        "schemaVersion" -> 2,
        "component" -> Record.data("name" -> "missing-style")
      )
      val stringversionrecord = Record.data(
        "schemaVersion" -> "2",
        "component" -> Record.data("name" -> "string-version")
      )
      val zeroversionrecord = Record.data(
        "schemaVersion" -> 0,
        "component" -> Record.data("name" -> "zero-version")
      )
      val aliasstylerecord = Record.data(
        "schemaVersion" -> 2,
        "component_style" -> Record.data("id" -> "full-fledged-with-standalone@1"),
        "component" -> Record.data("name" -> "alias-style")
      )
      val legacystylerecord = Record.data(
        "schemaVersion" -> 1,
        "componentStyle" -> Record.data("id" -> "full-fledged-with-standalone@1"),
        "component" -> Record.data("name" -> "legacy-style")
      )
      def _style(
        schemafields: Vector[(String, Any)],
        parameters: Option[Record]
      ): Record =
        Record.createFull(Vector[(String, Any)](
          "apiVersion" -> "cncf.textus/v1",
          "provider" -> "cncf",
          "id" -> "full-fledged-with-standalone@1",
          "version" -> 1,
          "parameterSchema" -> Record.createFull(schemafields),
          "provides" -> Record.data(
            "bundles" -> Vector("domain.full@1"),
            "capabilities" -> Vector("user.fixed-context-compatible@1", "user.multi-user@1"),
            "effective" -> Vector(
              "domain.aggregate@1", "domain.command@1", "domain.domain-event@1", "domain.entity@1",
              "domain.optimistic-concurrency@1", "domain.persistence@1", "domain.projection@1",
              "domain.query@1", "domain.transaction@1", "user.fixed-context-compatible@1", "user.multi-user@1"
            )
          ),
          "requires" -> Record.data(
            "subsystemCapabilities" -> Vector(
              "datastore.optimistic-concurrency@1", "datastore.persistent@1",
              "datastore.transactional@1", "user-context.current@1"
            )
          )
        ) ++ parameters.toVector.map("parameters" -> _))
      def _v2_record(name: String, style: Record): Record = Record.data(
        "schemaVersion" -> 2,
        "component" -> Record.data("name" -> name),
        "componentStyle" -> style
      )
      val missingproperties = _v2_record(
        "missing-parameter-schema-properties",
        _style(Vector("type" -> "object", "required" -> Vector.empty[String], "additionalProperties" -> false), Some(Record.empty))
      )
      val missingrequired = _v2_record(
        "missing-parameter-schema-required",
        _style(Vector("type" -> "object", "properties" -> Record.empty, "additionalProperties" -> false), Some(Record.empty))
      )
      val missingparameters = _v2_record(
        "missing-parameters",
        _style(Vector("type" -> "object", "properties" -> Record.empty, "required" -> Vector.empty[String], "additionalProperties" -> false), None)
      )

      When("the descriptors are decoded")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(legacyrecord).toOption.get
      val missingstyle = summon[RecordDecoder[ComponentDescriptor]].fromRecord(missingstylerecord).toOption
      val stringversion = summon[RecordDecoder[ComponentDescriptor]].fromRecord(stringversionrecord).toOption
      val zeroversion = summon[RecordDecoder[ComponentDescriptor]].fromRecord(zeroversionrecord).toOption
      val aliasstyle = summon[RecordDecoder[ComponentDescriptor]].fromRecord(aliasstylerecord).toOption
      val legacystyle = summon[RecordDecoder[ComponentDescriptor]].fromRecord(legacystylerecord).toOption
      val missingpropertiesresult = summon[RecordDecoder[ComponentDescriptor]].fromRecord(missingproperties).toOption
      val missingrequiredresult = summon[RecordDecoder[ComponentDescriptor]].fromRecord(missingrequired).toOption
      val missingparametersresult = summon[RecordDecoder[ComponentDescriptor]].fromRecord(missingparameters).toOption

      Then("legacy reads remain compatible while malformed or alias v2 records fail")
      descriptor.schemaVersion shouldBe None
      descriptor.requireComponentStyleSnapshotC.toOption shouldBe None
      missingstyle shouldBe None
      stringversion shouldBe None
      zeroversion shouldBe None
      aliasstyle shouldBe None
      legacystyle shouldBe None
      missingpropertiesresult shouldBe None
      missingrequiredresult shouldBe None
      missingparametersresult shouldBe None
    }

    "retain entities from a legacy nested component when the root has none" in {
      Given("a legacy descriptor whose entity declarations remain inside component")
      val rec = Record.data(
        "component" -> Record.data(
          "name" -> "legacy-component",
          "entities" -> Vector(Record.data(
            "entity" -> "LegacyEntity",
            "usageKind" -> "business-object",
            "applicationDomain" -> "business"
          ))
        )
      )

      When("the legacy nested descriptor is decoded")
      val descriptor = summon[RecordDecoder[ComponentDescriptor]].fromRecord(rec).toOption.get

      Then("its nested entity remains observable without overriding root schema-v2 fields")
      descriptor.entityRuntimeDescriptors.map(_.entityName) shouldBe Vector("LegacyEntity")
    }
    }
  }
}
