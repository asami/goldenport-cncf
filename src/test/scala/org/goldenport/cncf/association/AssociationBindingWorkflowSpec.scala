package org.goldenport.cncf.association

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import cats.data.NonEmptyVector
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInit,
  ComponentInstanceId,
  ComponentOrigin
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.operation.{
  CmlOperationAssociationBinding,
  CmlOperationDefinition,
  CmlOperationImageBinding
}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.protocol.{Argument, Protocol, Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Executable specification for BI-04 operation-level Association binding.
 *
 * @since   Apr. 30, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class AssociationBindingWorkflowSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E4, rules:R1,R5, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:association-binding, rules:R1,R5, phase:52")

  "AssociationBindingWorkflow" must _in_phase52_spec {
    "which records EID-01 Association target parsing" which {
      "E4 create an Association from a source parameter and target id parameter" must _in_eid01_spec {
        "retain the exact canonical Association target owner" in {
          Given(
            "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R5; Example: E4; a binding that reads the source and target ids from request parameters"
          )
          given ExecutionContext = ExecutionContext.test()
          val repository         = AssociationRepository.entityStore()
          val workflow           = AssociationBindingWorkflow(repository)
          val sourceid           = _article_id("source_1").value
          val target             = _article_id("target_1")
          _seed_entity(target)
          val binding = CmlOperationAssociationBinding(
            domain = "related_entity",
            targetKind = "article",
            createsAssociation = true,
            roles = Vector("related"),
            sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
            sourceEntityIdParameters = Vector("sourceEntityId"),
            targetIdParameters = Vector("targetEntityId")
          )
          val request = Request.of(
            component = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
            service = "article",
            operation = "attach",
            properties = List(
              Property("sourceEntityId", sourceid, None),
              Property("targetEntityId", target.value, None)
            )
          )

          When("the helper resolves the source and attaches the target")
          val source = _success(AssociationBindingWorkflow.resolveSourceEntityId(
            binding,
            request,
            OperationResponse.RecordResponse(Record.empty)
          ))
          val summary = _success(workflow.attachExistingTargets(source, binding, request))

          Then("the Association is stored in the generic Association collection")
          summary.sourceEntityId shouldBe sourceid
          summary.associations should have size 1
          val listed = _success(repository.list(AssociationFilter(
            domain = AssociationDomain("related_entity"),
            sourceEntityId = Some(sourceid),
            targetEntityId = Some(target.value),
            targetKind = Some("article"),
            role = Some("related")
          )))
          listed should have size 1

          And("the Association request parser retains the exact target owner")
          val exacttarget =
            EntityId(
              _article_collection_id.major,
              _article_collection_id.minor,
              _article_collection_id,
              entropy = Some("target_1")
            )
          val parsedtarget = EntityId.parse(exacttarget.value).toOption.get
          parsedtarget.collection shouldBe _article_collection_id
          EntityId.parse(exacttarget.value).toOption shouldBe Some(exacttarget)
        }
      }
    }

    "reject target ids whose collection does not match targetKind" in {
      Given("a binding declared for articles and a target id from another Entity collection")
      given ExecutionContext = ExecutionContext.test()
      val repository         = AssociationRepository.entityStore()
      val workflow           = AssociationBindingWorkflow(repository)
      val source             = _article_id("source_1").value
      val target             = _comment_id("comment_target_1")
      _seed_entity(target)
      val binding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        roles = Vector("related"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId")
      )
      val request = Request.of(
        component = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", target.value, None)
        )
      )

      When("the helper validates the target")
      val result = workflow.attachExistingTargets(source, binding, request)

      Then("the association is rejected as a kind mismatch")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include("target kind mismatch")
    }

    "not delete pre-existing Associations when a later target fails" in {
      Given("an existing Association followed by a failing target binding")
      given ExecutionContext = ExecutionContext.test()
      val repository         = AssociationRepository.entityStore()
      val source             = _article_id("source_existing").value
      val existingtarget     = _article_id("existing_target")
      val rejectedtarget     = _article_id("rejected_target")
      val firstbinding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        roles = Vector("related"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId")
      )
      val firstrequest = Request.of(
        component = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", existingtarget.value, None)
        )
      )
      val setupworkflow = AssociationBindingWorkflow(
        repository,
        targetValidator = AssociationTargetValidator.unchecked
      )
      _success(setupworkflow.attachExistingTargets(source, firstbinding, firstrequest))
      val failingworkflow = AssociationBindingWorkflow(
        repository,
        targetValidator = new AssociationTargetValidator {
          def validate(
              targetKind: Option[String],
              id: EntityId
          )(using ExecutionContext): Consequence[Unit] =
            if (id == rejectedtarget)
              Consequence.argumentInvalid("target rejected")
            else
              Consequence.unit
        }
      )
      val binding =
        firstbinding.copy(targetIdParameters = Vector("targetEntityId", "rejectedTargetEntityId"))
      val request = Request.of(
        component = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", existingtarget.value, None),
          Property("rejectedTargetEntityId", rejectedtarget.value, None)
        )
      )

      When("the second target fails after the existing Association is reused")
      val result = failingworkflow.attachExistingTargets(source, binding, request)

      Then("the pre-existing Association is preserved")
      result shouldBe a[Consequence.Failure[_]]
      val listed = _success(repository.list(AssociationFilter(
        domain = AssociationDomain("related_entity"),
        sourceEntityId = Some(source),
        targetEntityId = Some(existingtarget.value),
        targetKind = Some("article"),
        role = Some("related")
      )))
      listed should have size 1
    }

    "propagate cleanup failures for newly created Associations" in {
      Given("a newly created Association followed by a failing target and failing cleanup")
      given ExecutionContext = ExecutionContext.test()
      val delegate           = AssociationRepository.entityStore()
        val repository         = FailingDeleteAssociationRepository(delegate)
      val source             = _article_id("source_cleanup_failure").value
      val createdtarget      = _article_id("created_target_cleanup_failure")
      val rejectedtarget     = _article_id("rejected_target_cleanup_failure")
      _seed_entity(createdtarget)
      val workflow = AssociationBindingWorkflow(
        repository,
        targetValidator = new AssociationTargetValidator {
          def validate(
              targetKind: Option[String],
              id: EntityId
          )(using ExecutionContext): Consequence[Unit] =
            if (id == rejectedtarget)
              Consequence.argumentInvalid("target rejected")
            else
              Consequence.unit
        }
      )
      val binding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        roles = Vector("related"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId", "rejectedTargetEntityId")
      )
      val request = Request.of(
        component = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", createdtarget.value, None),
          Property("rejectedTargetEntityId", rejectedtarget.value, None)
        )
      )

      When("the second target fails and cleanup also fails")
      val result = workflow.attachExistingTargets(source, binding, request)

      Then("the cleanup failure is returned instead of being hidden")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include("association cleanup delete failed")
    }

    "resolve entity-create-result from standard entity_id response fields" in {
      Given("a binding whose source entity id is returned by the operation")
      val binding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
        targetIdParameters = Vector("targetEntityId")
      )
      val request  = Request.of("org.goldenport.cncf.test.AssociationBindingAdapterSpec", "article", "create")
      val expected = _article_id("created_1").value
      val response = OperationResponse.RecordResponse(Record.dataAuto("entity_id" -> expected))

      When("resolving the source Entity id")
      val source =
        _success(AssociationBindingWorkflow.resolveSourceEntityId(binding, request, response))

      Then("entity_id is used as the source Entity id")
      source shouldBe expected
    }

    "fail deterministically when an entity-create-result source id is missing" in {
      Given("a binding that expects the operation result to carry an Entity id")
      val binding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
        targetIdParameters = Vector("targetEntityId")
      )
      val request  = Request.of("org.goldenport.cncf.test.AssociationBindingAdapterSpec", "article", "create")
      val response = OperationResponse.RecordResponse(Record.dataAuto("title" -> "missing id"))

      When("resolving the source Entity id")
      val result = AssociationBindingWorkflow.resolveSourceEntityId(binding, request, response)

      Then("the helper returns a validation failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "Subsystem operation association binding adapter" must _in_phase52_spec {
    "attach existing target ids after an operation returns entity_id" in {
      Given("a component operation with associationBinding metadata")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("association_binding_adapter_spec")
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimecomponent =
        subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
      given ExecutionContext = runtimecomponent.logic.executionContext()
      val target             = _article_id("target_2")
      val source             = _article_id("article_1").value
      _seed_entity(target)
      val request = Request.of(
        component = component.componentId.name,
        service = "article",
        operation = "createArticle",
        properties = List(Property("targetEntityId", target.value, None))
      )

      When("the operation is executed through the subsystem")
      val response = _success(subsystem.executeOperationResponse(request))

      Then("the operation result is preserved and the Association is created")
      response match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("entity_id") shouldBe Some(source)
          record.getVector("requestKeys").getOrElse(Vector.empty).map(
            _.toString
          ) should not contain "targetEntityId"
        case other =>
          fail(s"unexpected response: $other")
      }
      val listed = _success(
        AssociationRepository.entityStore().list(AssociationFilter(
          domain = AssociationDomain("related_entity"),
          sourceEntityId = Some(source),
          targetEntityId = Some(target.value),
          targetKind = Some("article"),
          role = Some("related")
        ))
      )
      listed should have size 1
    }

    "reject image uploads when imageBinding only accepts existing Blob ids" in {
      Given("an operation whose imageBinding metadata does not accept uploads")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimecomponent =
        subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
      given ExecutionContext = runtimecomponent.logic.executionContext()
      val request = Request.of(
        component = component.componentId.name,
        service = "article",
        operation = "createArticleBlobOnly",
        properties = List(
          Property(
            "blob.primary",
            MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))),
            None
          )
        )
      )

      When("the operation is executed through the subsystem")
      val result = subsystem.executeOperationResponse(request)

      Then("the upload is rejected before creating a BlobAttachment")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject existing Blob ids when imageBinding only accepts uploads" in {
      Given("an operation whose imageBinding metadata does not accept existing Blob ids")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimecomponent =
        subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
      given ExecutionContext = runtimecomponent.logic.executionContext()
      val request = Request.of(
        component = component.componentId.name,
        service = "article",
        operation = "createArticleUploadOnly",
        properties =
          List(
            Property(
              "blobId.primary",
              EntityId(
                org.goldenport.cncf.blob.BlobRepository.CollectionId.major,
                org.goldenport.cncf.blob.BlobRepository.CollectionId.minor,
                org.goldenport.cncf.blob.BlobRepository.CollectionId,
                entropy = Some("existing_blob")
              ).value,
              None
            )
          )
      )

      When("the operation is executed through the subsystem")
      val result = subsystem.executeOperationResponse(request)

      Then("the existing Blob id is rejected by the metadata contract")
      result shouldBe a[Consequence.Failure[_]]
    }

    "register upload payloads through imageBinding after an operation returns entity_id" in {
      Given("a component operation with imageBinding metadata")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimecomponent =
        subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
      given ExecutionContext = runtimecomponent.logic.executionContext()
      val source = _article_id("article_image_1").value
      val request = Request.of(
        component = component.componentId.name,
        service = "article",
        operation = "createArticleWithImage",
        properties = List(
          Property(
            "blob.primary",
            MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))),
            None
          ),
          Property("blob.primary.filename", "adapter.png", None)
        )
      )

      When("the operation is executed through the subsystem")
      val response = _success(subsystem.executeOperationResponse(request))

      Then("the image binding uses the BlobAttachment Association path")
      response match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("entity_id") shouldBe Some(source)
        case other =>
          fail(s"unexpected response: $other")
      }
      val listed = _success(
        AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault).list(
          AssociationFilter(
            domain = AssociationDomain.BlobAttachment,
            sourceEntityId = Some(source),
            targetKind = Some("blob"),
            role = Some("primary")
          )
        )
      )
      listed should have size 1
    }

    "compensate Association bindings when later image binding fails" in {
      Given("an operation that creates an Association before image binding fails")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimecomponent =
        subsystem.findComponent(component.componentId).getOrElse(fail("component missing"))
      val target = _article_id("target_image_failure_cleanup")
      val source = _article_id("article_association_image_failure").value
      _seed_entity(target)
      given ExecutionContext = runtimecomponent.logic.executionContext()
      val missingblob = EntityId(
        org.goldenport.cncf.blob.BlobRepository.CollectionId.major,
        org.goldenport.cncf.blob.BlobRepository.CollectionId.minor,
        org.goldenport.cncf.blob.BlobRepository.CollectionId,
        entropy = Some("missing_blob_for_association_cleanup")
      )
      val request = Request.of(
        component = component.componentId.name,
        service = "article",
        operation = "createArticleWithAssociationAndBadImage",
        properties = List(
          Property("targetEntityId", target.value, None),
          Property("blobId.primary", missingblob.value, None)
        )
      )

      When("the image binding fails after the Association binding succeeds")
      val result = subsystem.executeOperationResponse(request)

      Then("the operation fails and the earlier Association is compensated")
      result shouldBe a[Consequence.Failure[_]]
      val listed = _success(
        AssociationRepository.entityStore().list(AssociationFilter(
          domain = AssociationDomain("related_entity"),
          sourceEntityId = Some(source),
          targetEntityId = Some(target.value),
          targetKind = Some("article"),
          role = Some("related")
        ))
      )
      listed shouldBe Vector.empty
    }
  }

  "AdminComponent generic Association operations" must _in_phase52_spec {
    "attach, reuse, list, and detach non-image Associations" in {
      Given("existing source and target Entity records")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val admin     = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component missing"))
      given ExecutionContext = admin.logic.executionContext()
      val source             = _article_id("admin_source_1")
      val target             = _article_id("admin_target_1")
      _seed_entity(source)
      _seed_entity(target)
      val attach = Request.of(
        component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name,
        service = "association",
        operation = "admin_attach_association",
        arguments = List(
          Argument("domain", "related_entity"),
          Argument("sourceEntityId", source.value),
          Argument("targetEntityId", target.value),
          Argument("targetKind", "article"),
          Argument("role", "related"),
          Argument("sortOrder", "3")
        )
      )

      When("the same Association is attached twice")
      val first  = _record(_success(subsystem.executeOperationResponse(attach)))
      val second = _record(_success(subsystem.executeOperationResponse(attach)))

      Then("the second call reuses the existing Association")
      first.getBoolean("created") shouldBe Some(true)
      second.getBoolean("created") shouldBe Some(false)
      val listed = _record(_success(subsystem.executeOperationResponse(Request.of(
        component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name,
        service = "association",
        operation = "admin_list_associations",
        arguments = List(
          Argument("domain", "related_entity"),
          Argument("sourceEntityId", source.value),
          Argument("targetKind", "article"),
          Argument("role", "related")
        )
      ))))
      listed.getInt("pageSize") shouldBe Some(20)
      listed.getVector("data").getOrElse(Vector.empty) should have size 1

      When("the Association is detached")
      val detached = _record(_success(subsystem.executeOperationResponse(Request.of(
        component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name,
        service = "association",
        operation = "admin_detach_association",
        arguments = List(
          Argument("domain", "related_entity"),
          Argument("sourceEntityId", source.value),
          Argument("targetEntityId", target.value),
          Argument("targetKind", "article"),
          Argument("role", "related")
        )
      ))))

      Then("only the Association row is removed")
      detached.getInt("detachedCount") shouldBe Some(1)
      _success(AssociationRepository.entityStore().list(AssociationFilter(
        domain = AssociationDomain("related_entity"),
        sourceEntityId = Some(source.value),
        targetEntityId = Some(target.value),
        targetKind = Some("article"),
        role = Some("related")
      ))) shouldBe Vector.empty
      _success(AssociationTargetValidator.entityStoreRecordExists.validate(None, source))
      _success(AssociationTargetValidator.entityStoreRecordExists.validate(None, target))
    }

    "reject missing source, missing target, and targetKind mismatch" in {
      Given("an admin Association attach request")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val admin     = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component missing"))
      given ExecutionContext = admin.logic.executionContext()
      val source             = _article_id("admin_source_2")
      val target             = _article_id("admin_target_2")
      val comment            = _comment_id("admin_comment_2")
      _seed_entity(source)
      _seed_entity(target)
      _seed_entity(comment)
      def _attach_(sourceid: EntityId, targetid: EntityId, targetkind: String) =
        subsystem.executeOperationResponse(Request.of(
          component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name,
          service = "association",
          operation = "admin_attach_association",
          arguments = List(
            Argument("domain", "related_entity"),
            Argument("sourceEntityId", sourceid.value),
            Argument("targetEntityId", targetid.value),
            Argument("targetKind", targetkind),
            Argument("role", "related")
          )
        ))

      _attach_(_article_id("missing_admin_source"), target, "article") shouldBe a[
        Consequence.Failure[_]
      ]
      _attach_(source, _article_id("missing_admin_target"), "article") shouldBe a[
        Consequence.Failure[_]
      ]
      _failure_message(_attach_(source, comment, "article")) should include("target kind mismatch")
    }
  }

  private def _component(subsystem: org.goldenport.cncf.subsystem.Subsystem): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "article",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                CreateArticleOperation("createArticle", _article_id("article_1").value),
                CreateArticleOperation("createArticleWithImage", _article_id("article_image_1").value),
                CreateArticleOperation("createArticleBlobOnly", _article_id("article_blob_only").value),
                CreateArticleOperation("createArticleUploadOnly", _article_id("article_upload_only").value),
                CreateArticleOperation(
                  "createArticleWithAssociationAndBadImage",
                  _article_id("article_association_image_failure").value
                )
              )
            )
          )
        )
      )
    )
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "createArticle",
            kind = "QUERY",
            inputType = "CreateArticle",
            outputType = "CreateArticleResult",
            inputValueKind = "COMMAND_VALUE",
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "related_entity",
              targetKind = "article",
              createsAssociation = true,
              roles = Vector("related"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("targetEntityId")
            ))
          ),
          CmlOperationDefinition(
            name = "createArticleWithImage",
            kind = "QUERY",
            inputType = "CreateArticle",
            outputType = "CreateArticleResult",
            inputValueKind = "COMMAND_VALUE",
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              createsAttachment = true,
              roles = Vector("primary", "cover", "thumbnail", "gallery", "inline"),
              parameters = Vector("blobId.primary"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("blobId.primary")
            ))
          ),
          CmlOperationDefinition(
            name = "createArticleBlobOnly",
            kind = "QUERY",
            inputType = "CreateArticle",
            outputType = "CreateArticleResult",
            inputValueKind = "COMMAND_VALUE",
            imageBinding = Some(CmlOperationImageBinding(
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("primary"),
              parameters = Vector("blobId.primary"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("blobId.primary")
            ))
          ),
          CmlOperationDefinition(
            name = "createArticleUploadOnly",
            kind = "QUERY",
            inputType = "CreateArticle",
            outputType = "CreateArticleResult",
            inputValueKind = "COMMAND_VALUE",
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              createsAttachment = true,
              roles = Vector("primary"),
              parameters = Vector("blob.primary"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
            ))
          ),
          CmlOperationDefinition(
            name = "createArticleWithAssociationAndBadImage",
            kind = "QUERY",
            inputType = "CreateArticle",
            outputType = "CreateArticleResult",
            inputValueKind = "COMMAND_VALUE",
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "related_entity",
              targetKind = "article",
              createsAssociation = true,
              roles = Vector("related"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("targetEntityId")
            )),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("primary"),
              parameters = Vector("blobId.primary"),
              sourceEntityIdMode =
                CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("blobId.primary")
            ))
          )
        )
    }
    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.AssociationBindingAdapterSpec",
      componentId = ComponentId("org.goldenport.cncf.test.AssociationBindingAdapterSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.AssociationBindingAdapterSpec")),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value)      => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other                                    => fail(s"unexpected response: $other")
    }

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show
      case Consequence.Success(value)      => fail(s"unexpected success: $value")
    }

  private lazy val _article_collection_id: EntityCollectionId =
    EntityCollectionId("cncf", "sample", "article")

  private def _article_id(value: String): EntityId =
    EntityId(
      _article_collection_id.major,
      _article_collection_id.minor,
      _article_collection_id,
      timestamp = Some(java.time.Instant.EPOCH),
      entropy = Some(value)
    )

  private lazy val _comment_collection_id: EntityCollectionId =
    EntityCollectionId("cncf", "sample", "comment")

  private def _comment_id(value: String): EntityId =
    EntityId(
      _comment_collection_id.major,
      _comment_collection_id.minor,
      _comment_collection_id,
      timestamp = Some(java.time.Instant.EPOCH),
      entropy = Some(value)
    )

  private def _seed_entity(
      id: EntityId
  )(using ctx: ExecutionContext): Unit = {
    val cid    = DataStore.CollectionId.EntityStore(id.collection)
    val dsid   = DataStore.EntryId(id)
    val ds     = _success(ctx.dataStoreSpace.dataStore(cid))
    val record = Record.dataAuto("id" -> id.value)
    _success(ds.create(cid, dsid, record).recoverWith(_ => ds.save(cid, dsid, record)))
  }
}

private final case class FailingDeleteAssociationRepository(
    delegate: AssociationRepository
) extends AssociationRepository {
  def create(association: AssociationCreate)(using ExecutionContext): Consequence[Association] =
    delegate.create(association)

  def delete(association: Association)(using ExecutionContext): Consequence[Unit] =
    Consequence.stateConflict("association cleanup delete failed")

  def list(
      filter: AssociationFilter,
      offset: Int = 0,
      limit: Option[Int] = None
  )(using ExecutionContext): Consequence[Vector[Association]] =
    delegate.list(filter, offset, limit)
}

private final case class CreateArticleOperation(
    opname: String,
    entityid: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(CreateArticleAction(req, entityid))
}

private final case class CreateArticleAction(
    request: Request,
    entityid: String
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    CreateArticleActionCall(core, request, entityid)
}

private final case class CreateArticleActionCall(
    core: ActionCall.Core,
    operationrequest: Request,
    entityid: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.RecordResponse(Record.dataAuto(
      "entity_id"   -> entityid,
      "requestKeys" -> operationrequest.toRecord.asMap.keys.toVector.sorted
    )))
}
