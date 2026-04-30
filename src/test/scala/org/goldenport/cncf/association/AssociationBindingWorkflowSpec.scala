package org.goldenport.cncf.association

import cats.data.NonEmptyVector
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.operation.{CmlOperationAssociationBinding, CmlOperationDefinition, CmlOperationImageBinding}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.protocol.{Protocol, Property, Request}
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
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class AssociationBindingWorkflowSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "AssociationBindingWorkflow" should {
    "create an Association from a source parameter and target id parameter" in {
      Given("a binding that reads the source and target ids from request parameters")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore()
      val workflow = AssociationBindingWorkflow(repository)
      val target = _article_id("target_1")
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
        component = "sample",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", "source-1", None),
          Property("targetEntityId", target.value, None)
        )
      )

      When("the helper resolves the source and attaches the target")
      val source = _success(AssociationBindingWorkflow.resolveSourceEntityId(binding, request, OperationResponse.RecordResponse(Record.empty)))
      val summary = _success(workflow.attachExistingTargets(source, binding, request))

      Then("the Association is stored in the generic Association collection")
      summary.sourceEntityId shouldBe "source-1"
      summary.associations should have size 1
      val listed = _success(repository.list(AssociationFilter(
        domain = AssociationDomain("related_entity"),
        sourceEntityId = Some("source-1"),
        targetEntityId = Some(target.value),
        targetKind = Some("article"),
        role = Some("related")
      )))
      listed should have size 1
    }

    "reject target ids whose collection does not match targetKind" in {
      Given("a binding declared for articles and a target id from another Entity collection")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore()
      val workflow = AssociationBindingWorkflow(repository)
      val target = _comment_id("comment_target_1")
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
        component = "sample",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", "source-1", None),
          Property("targetEntityId", target.value, None)
        )
      )

      When("the helper validates the target")
      val result = workflow.attachExistingTargets("source-1", binding, request)

      Then("the association is rejected as a kind mismatch")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include ("target kind mismatch")
    }

    "not delete pre-existing Associations when a later target fails" in {
      Given("an existing Association followed by a failing target binding")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore()
      val source = "source-existing"
      val existingTarget = _article_id("existing_target")
      val rejectedTarget = _article_id("rejected_target")
      val firstBinding = CmlOperationAssociationBinding(
        domain = "related_entity",
        targetKind = "article",
        createsAssociation = true,
        roles = Vector("related"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId")
      )
      val firstRequest = Request.of(
        component = "sample",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", existingTarget.value, None)
        )
      )
      val setupWorkflow = AssociationBindingWorkflow(repository, targetValidator = AssociationTargetValidator.unchecked)
      _success(setupWorkflow.attachExistingTargets(source, firstBinding, firstRequest))
      val failingWorkflow = AssociationBindingWorkflow(
        repository,
        targetValidator = new AssociationTargetValidator {
          def validate(
            targetKind: Option[String],
            id: EntityId
          )(using ExecutionContext): Consequence[Unit] =
            if (id == rejectedTarget)
              Consequence.argumentInvalid("target rejected")
            else
              Consequence.unit
        }
      )
      val binding = firstBinding.copy(targetIdParameters = Vector("targetEntityId", "rejectedTargetEntityId"))
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", existingTarget.value, None),
          Property("rejectedTargetEntityId", rejectedTarget.value, None)
        )
      )

      When("the second target fails after the existing Association is reused")
      val result = failingWorkflow.attachExistingTargets(source, binding, request)

      Then("the pre-existing Association is preserved")
      result shouldBe a[Consequence.Failure[_]]
      val listed = _success(repository.list(AssociationFilter(
        domain = AssociationDomain("related_entity"),
        sourceEntityId = Some(source),
        targetEntityId = Some(existingTarget.value),
        targetKind = Some("article"),
        role = Some("related")
      )))
      listed should have size 1
    }

    "propagate cleanup failures for newly created Associations" in {
      Given("a newly created Association followed by a failing target and failing cleanup")
      given ExecutionContext = ExecutionContext.test()
      val delegate = AssociationRepository.entityStore()
      val repository = _FailingDeleteAssociationRepository(delegate)
      val source = "source-cleanup-failure"
      val createdTarget = _article_id("created_target_cleanup_failure")
      val rejectedTarget = _article_id("rejected_target_cleanup_failure")
      _seed_entity(createdTarget)
      val workflow = AssociationBindingWorkflow(
        repository,
        targetValidator = new AssociationTargetValidator {
          def validate(
            targetKind: Option[String],
            id: EntityId
          )(using ExecutionContext): Consequence[Unit] =
            if (id == rejectedTarget)
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
        component = "sample",
        service = "article",
        operation = "attach",
        properties = List(
          Property("sourceEntityId", source, None),
          Property("targetEntityId", createdTarget.value, None),
          Property("rejectedTargetEntityId", rejectedTarget.value, None)
        )
      )

      When("the second target fails and cleanup also fails")
      val result = workflow.attachExistingTargets(source, binding, request)

      Then("the cleanup failure is returned instead of being hidden")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include ("association cleanup delete failed")
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
      val request = Request.of("sample", "article", "create")
      val response = OperationResponse.RecordResponse(Record.dataAuto("entity_id" -> "created-1"))

      When("resolving the source Entity id")
      val source = _success(AssociationBindingWorkflow.resolveSourceEntityId(binding, request, response))

      Then("entity_id is used as the source Entity id")
      source shouldBe "created-1"
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
      val request = Request.of("sample", "article", "create")
      val response = OperationResponse.RecordResponse(Record.dataAuto("title" -> "missing id"))

      When("resolving the source Entity id")
      val result = AssociationBindingWorkflow.resolveSourceEntityId(binding, request, response)

      Then("the helper returns a validation failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "Subsystem operation association binding adapter" should {
    "attach existing target ids after an operation returns entity_id" in {
      Given("a component operation with associationBinding metadata")
      val subsystem = TestComponentFactory.emptySubsystem("association_binding_adapter_spec")
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimeComponent =
        subsystem.findComponent(component.name).getOrElse(fail("component missing"))
      given ExecutionContext = runtimeComponent.logic.executionContext()
      val target = _article_id("target_2")
      _seed_entity(target)
      val request = Request.of(
        component = component.name,
        service = "article",
        operation = "createArticle",
        properties = List(Property("targetEntityId", target.value, None))
      )

      When("the operation is executed through the subsystem")
      val response = _success(subsystem.executeOperationResponse(request))

      Then("the operation result is preserved and the Association is created")
      response match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("entity_id") shouldBe Some("article-1")
        case other =>
          fail(s"unexpected response: $other")
      }
      val listed = _success(
        AssociationRepository.entityStore().list(AssociationFilter(
          domain = AssociationDomain("related_entity"),
          sourceEntityId = Some("article-1"),
          targetEntityId = Some(target.value),
          targetKind = Some("article"),
          role = Some("related")
        ))
      )
      listed should have size 1
    }

    "reject image uploads when imageBinding only accepts existing Blob ids" in {
      Given("an operation whose imageBinding metadata does not accept uploads")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimeComponent =
        subsystem.findComponent(component.name).getOrElse(fail("component missing"))
      given ExecutionContext = runtimeComponent.logic.executionContext()
      val request = Request.of(
        component = component.name,
        service = "article",
        operation = "createArticleBlobOnly",
        properties = List(
          Property("blob.primary", MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))), None)
        )
      )

      When("the operation is executed through the subsystem")
      val result = subsystem.executeOperationResponse(request)

      Then("the upload is rejected before creating a BlobAttachment")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject existing Blob ids when imageBinding only accepts uploads" in {
      Given("an operation whose imageBinding metadata does not accept existing Blob ids")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimeComponent =
        subsystem.findComponent(component.name).getOrElse(fail("component missing"))
      given ExecutionContext = runtimeComponent.logic.executionContext()
      val request = Request.of(
        component = component.name,
        service = "article",
        operation = "createArticleUploadOnly",
        properties = List(
          Property("blobId.primary", EntityId("cncf", "existing_blob", org.goldenport.cncf.blob.BlobRepository.CollectionId).value, None)
        )
      )

      When("the operation is executed through the subsystem")
      val result = subsystem.executeOperationResponse(request)

      Then("the existing Blob id is rejected by the metadata contract")
      result shouldBe a[Consequence.Failure[_]]
    }

    "register upload payloads through imageBinding after an operation returns entity_id" in {
      Given("a component operation with imageBinding metadata")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimeComponent =
        subsystem.findComponent(component.name).getOrElse(fail("component missing"))
      given ExecutionContext = runtimeComponent.logic.executionContext()
      val request = Request.of(
        component = component.name,
        service = "article",
        operation = "createArticleWithImage",
        properties = List(
          Property("blob.primary", MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))), None),
          Property("blob.primary.filename", "adapter.png", None)
        )
      )

      When("the operation is executed through the subsystem")
      val response = _success(subsystem.executeOperationResponse(request))

      Then("the image binding uses the BlobAttachment Association path")
      response match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("entity_id") shouldBe Some("article-image-1")
        case other =>
          fail(s"unexpected response: $other")
      }
      val listed = _success(
        AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault).list(AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some("article-image-1"),
          targetKind = Some("blob"),
          role = Some("primary")
        ))
      )
      listed should have size 1
    }

    "compensate Association bindings when later image binding fails" in {
      Given("an operation that creates an Association before image binding fails")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val component = _component(subsystem)
      subsystem.add(component)
      val runtimeComponent =
        subsystem.findComponent(component.name).getOrElse(fail("component missing"))
      given ExecutionContext = runtimeComponent.logic.executionContext()
      val target = _article_id("target_image_failure_cleanup")
      _seed_entity(target)
      val missingBlob = EntityId("cncf", "missing_blob_for_association_cleanup", org.goldenport.cncf.blob.BlobRepository.CollectionId)
      val request = Request.of(
        component = component.name,
        service = "article",
        operation = "createArticleWithAssociationAndBadImage",
        properties = List(
          Property("targetEntityId", target.value, None),
          Property("blobId.primary", missingBlob.value, None)
        )
      )

      When("the image binding fails after the Association binding succeeds")
      val result = subsystem.executeOperationResponse(request)

      Then("the operation fails and the earlier Association is compensated")
      result shouldBe a[Consequence.Failure[_]]
      val listed = _success(
        AssociationRepository.entityStore().list(AssociationFilter(
          domain = AssociationDomain("related_entity"),
          sourceEntityId = Some("article-association-image-failure"),
          targetEntityId = Some(target.value),
          targetKind = Some("article"),
          role = Some("related")
        ))
      )
      listed shouldBe Vector.empty
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
                _CreateArticleOperation("createArticle", "article-1"),
                _CreateArticleOperation("createArticleWithImage", "article-image-1"),
                _CreateArticleOperation("createArticleBlobOnly", "article-blob-only"),
                _CreateArticleOperation("createArticleUploadOnly", "article-upload-only"),
                _CreateArticleOperation("createArticleWithAssociationAndBadImage", "article-association-image-failure")
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
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
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
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
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
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
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
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
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
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("targetEntityId")
            )),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("primary"),
              parameters = Vector("blobId.primary"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("blobId.primary")
            ))
          )
        )
    }
    val core = Component.Core.create(
      name = "association_binding_adapter_spec",
      componentid = ComponentId("association_binding_adapter_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("association_binding_adapter_spec")),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show
      case Consequence.Success(value) => fail(s"unexpected success: $value")
    }

  private val ArticleCollectionId: EntityCollectionId =
    EntityCollectionId("cncf", "sample", "article")

  private def _article_id(value: String): EntityId =
    {
      val id = EntityId("cncf", value, ArticleCollectionId)
      EntityId.parse(id.value).toOption.getOrElse(id)
    }

  private val CommentCollectionId: EntityCollectionId =
    EntityCollectionId("cncf", "sample", "comment")

  private def _comment_id(value: String): EntityId =
    {
      val id = EntityId("cncf", value, CommentCollectionId)
      EntityId.parse(id.value).toOption.getOrElse(id)
    }

  private def _seed_entity(
    id: EntityId
  )(using ctx: ExecutionContext): Unit = {
    val cid = DataStore.CollectionId.EntityStore(id.collection)
    val dsid = DataStore.EntryId(id)
    val ds = _success(ctx.dataStoreSpace.dataStore(cid))
    val record = Record.dataAuto("id" -> id.value)
    _success(ds.create(cid, dsid, record).recoverWith(_ => ds.save(cid, dsid, record)))
  }
}

private final case class _FailingDeleteAssociationRepository(
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

private final case class _CreateArticleOperation(
  opname: String,
  entityId: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_CreateArticleAction(req, entityId))
}

private final case class _CreateArticleAction(
  request: Request,
  entityId: String
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _CreateArticleActionCall(core, entityId)
}

private final case class _CreateArticleActionCall(
  core: ActionCall.Core,
  entityId: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.RecordResponse(Record.dataAuto("entity_id" -> entityId)))
}
