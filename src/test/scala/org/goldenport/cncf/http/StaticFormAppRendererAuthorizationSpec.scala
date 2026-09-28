package org.goldenport.cncf.http

import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.{ZipEntry, ZipOutputStream}
import cats.data.State
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyVector
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{MediaType, Method, Request, Uri}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.{Argument, Property, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentFactory, ComponentId, ComponentInstanceId, ComponentletDescriptor}
import org.goldenport.cncf.testutil.DevelopmentRuntimeManifestFixture
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, PrincipalId, RuntimeContext}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.{
  EntityConcurrencyMetadata,
  EntityMutationAdapterDefaults,
  EntityPersistent,
  EntityRevisionBinding,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityRevisionTransport,
  EntityStoreSpace
}
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationDefinition, CmlOperationField, CmlOperationImageBinding, CmlOperationUpdateField}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.information.*
import org.goldenport.cncf.knowledge.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererAuthorizationSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide binding, admin API, and authorization contracts" which {
    "render operation image binding controls and Form API metadata" in {
      Given("the prerequisites for render operation image binding controls and Form API metadata")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice",
            kind = "COMMAND",
            inputType = "RegisterNotice",
            outputType = "RegisterNoticeResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("title", "string", "1")),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("primary", "thumbnail"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("render operation image binding controls and Form API metadata is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for render operation image binding controls and Form API metadata holds")
      html should include ("enctype=\"multipart/form-data\"")
      html should include ("Image Attachments")
      html should include ("name=\"imageAttachments.0.role\"")
      html should include ("name=\"imageAttachments.0.blobId\"")
      html should include ("name=\"imageAttachments.0.file\" type=\"file\"")
      html should include ("<option value=\"primary\">")
      html should include ("<option value=\"thumbnail\">")
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsUpload").as[Boolean].toOption shouldBe Some(true)
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsExistingBlobId").as[Boolean].toOption shouldBe Some(true)
      fieldnames should contain ("imageAttachments.0.role")
      fieldnames should contain ("imageAttachments.0.blobId")
      fieldnames should contain ("imageAttachments.0.file")
    }

    "hide disallowed image binding input modes from operation forms" in {
      Given("the prerequisites for hide disallowed image binding input modes from operation forms")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "attach-notice-image",
            kind = "COMMAND",
            inputType = "AttachNoticeImage",
            outputType = "AttachNoticeImageResult",
            inputValueKind = "COMMAND_VALUE",
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = false,
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("cover"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("attach-notice-image"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      When("hide disallowed image binding input modes from operation forms is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "attach-notice-image"
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for hide disallowed image binding input modes from operation forms holds")
      html should include ("Image Attachments")
      html should include ("name=\"imageAttachments.0.blobId\"")
      html should not include ("name=\"imageAttachments.0.file\"")
      html should not include ("enctype=\"multipart/form-data\"")
    }

    "render operation association binding controls and metadata" in {
      Given("the prerequisites for render operation association binding controls and metadata")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId"),
              sortOrderParameters = Vector("tagSortOrder")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("render operation association binding controls and metadata is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for render operation association binding controls and metadata holds")
      html should include ("Associations")
      html should include ("name=\"tagId\"")
      html should include ("name=\"tagSortOrder\"")
      json.hcursor.downField("bindings").downField("associationBinding").downField("domain").as[String].toOption shouldBe Some("notice_tag")
      json.hcursor.downField("bindings").downField("associationBinding").downField("targetKind").as[String].toOption shouldBe Some("tag")
      fieldnames should contain ("tagId")
      fieldnames should contain ("tagSortOrder")
    }

    "preserve declared binding parameters during operation form validation" in {
      Given("the prerequisites for preserve declared binding parameters during operation form validation")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("tagId", "string", "1")),
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val valid = _renderer.validateOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag",
        Map("tagId" -> "tag-1")
      ).getOrElse(fail("operation validation is missing"))
      When("preserve declared binding parameters during operation form validation is exercised")
      val missing = _renderer.validateOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag",
        Map.empty
      ).getOrElse(fail("operation validation is missing"))

      Then("the observable contract for preserve declared binding parameters during operation form validation holds")
      valid.valid shouldBe true
      missing.valid shouldBe false
      missing.errors.flatMap(_.field) should contain ("tagId")
    }

    "avoid duplicate binding virtual fields for declared operation parameters" in {
      Given("the prerequisites for avoid duplicate binding virtual fields for declared operation parameters")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("tagId", "string", "1")),
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("avoid duplicate binding virtual fields for declared operation parameters is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for avoid duplicate binding virtual fields for declared operation parameters holds")
      fieldnames.count(_ == "tagId") shouldBe 1
      "name=\"tagId\"".r.findAllIn(html).size shouldBe 1
      html should not include ("Tag Id Target id")
    }

    "skip image attachment controls for non-attachment image operations" in {
      Given("the prerequisites for skip image attachment controls for non-attachment image operations")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-blob-like",
            kind = "COMMAND",
            inputType = "RegisterBlobLike",
            outputType = "RegisterBlobLikeResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("payload", "blob", "1")),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              createsAttachment = false,
              parameters = Vector("payload")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "blob",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-blob-like"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "blob",
        "register-blob-like"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "blob",
        "register-blob-like"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("skip image attachment controls for non-attachment image operations is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for skip image attachment controls for non-attachment image operations holds")
      html should not include ("Image Attachments")
      html should not include ("imageAttachments.0.file")
      fieldnames should contain ("payload")
      fieldnames should not contain ("imageAttachments.0.file")
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsUpload").as[Boolean].toOption shouldBe Some(true)
    }

    "serve admin entity form definition API from EntityRuntimeDescriptor schema" in {
      Given("the prerequisites for serve admin entity form definition API from EntityRuntimeDescriptor schema")
      val descriptor = ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(Schema(Vector(
              Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("body").label("Notice body").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(controlType = Some("textarea"), help = Some("Notice body shown on the board."))
              )
            )))
          )
        )
      )
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      When("serve admin entity form definition API from EntityRuntimeDescriptor schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin entity form definition API from EntityRuntimeDescriptor schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("entity")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/create")
      json.hcursor.downField("actions").downN(5).downField("path").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/{id}/update")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("shortid")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("body")
      fields.downN(2).downField("label").as[String].toOption shouldBe Some("Notice body")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("textarea")
      fields.downN(2).downField("help").as[String].toOption shouldBe Some("Notice body shown on the board.")
    }

    "serve admin entity update form definition API from detail view fields" in {
      Given("the prerequisites for serve admin entity update form definition API from detail view fields")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewfields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_update_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice/notice_1/update"),
          "notice-board",
          "notice",
          "notice_1"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity update form definition JSON is invalid"))
      When("serve admin entity update form definition API from detail view fields is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin entity update form definition API from detail view fields holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity-update")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/notice_1/update")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/entities/notice/notice_1/edit")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("title")
      fields.downN(2).downField("name").as[String].toOption shouldBe None
    }

    "allow anonymous admin form API by the develop anonymous admin default" in {
      Given("the prerequisites for allow anonymous admin form API by the develop anonymous admin default")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow anonymous admin form API by the develop anonymous admin default is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow anonymous admin form API by the develop anonymous admin default holds")
      response.status.code shouldBe 200
    }

    "deny anonymous admin form API when develop anonymous admin is disabled" in {
      Given("the prerequisites for deny anonymous admin form API when develop anonymous admin is disabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      When("deny anonymous admin form API when develop anonymous admin is disabled is exercised")
      val json = parse(body).getOrElse(fail(body)).hcursor

      Then("the observable contract for deny anonymous admin form API when develop anonymous admin is disabled holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.downField("error").get[String]("message") shouldBe Right("Forbidden")
      json.downField("error").downField("debug").get[String]("path") shouldBe Right("/form-api/notice-board/admin/entities/notice")
      json.downField("error").downField("debug").get[String]("method") shouldBe Right("GET")
    }

    "include status and detail code in plain-text structured API errors" in {
      Given("the prerequisites for include status and detail code in plain-text structured API errors")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val request = _get_request("/form-api/notice-board/admin/entities/notice")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Accept"), "text/plain"))

      val response = server
        ._component_admin_entity_form_api_definition(
          request,
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("include status and detail code in plain-text structured API errors is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for include status and detail code in plain-text structured API errors holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.plain)
      body should include ("Forbidden")
      body should include ("status: 403")
      body should include ("statusText: Forbidden")
      body should include ("detailCode:")
    }

    "deny anonymous admin form API in production operation mode" in {
      Given("the prerequisites for deny anonymous admin form API in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny anonymous admin form API in production operation mode is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for deny anonymous admin form API in production operation mode holds")
      response.status.code shouldBe 403
    }

    "deny anonymous component admin HTML route in production operation mode" in {
      Given("the prerequisites for deny anonymous component admin HTML route in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice"))
        .unsafeRunSync()
      When("deny anonymous component admin HTML route in production operation mode is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for deny anonymous component admin HTML route in production operation mode holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.html)
      body should include ("Request failed")
      body should include ("<strong>Status:</strong>")
      body should include ("<strong>Status text:</strong>")
      body should include ("<code>403</code>")
      body should include ("<code>Forbidden</code>")
      body should not include ("structured-error-debug")
      body should not include ("\"error\"")
    }

    "deny forged query/header admin identity in production operation mode" in {
      Given("the prerequisites for deny forged query/header admin identity in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny forged query/header admin identity in production operation mode is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice?principalId=admin-test&role=component_operator&privilege=operator"))
        .unsafeRunSync()

      Then("the observable contract for deny forged query/header admin identity in production operation mode holds")
      response.status.code shouldBe 403
    }

    "strip forged authorization fields before resolving the production admin session" in {
      Given("the prerequisites for strip forged authorization fields before resolving the production admin session")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_echoing_auth_session(
        subsystem,
        _session_summary(
          "weak-session-with-forged-query",
          "ordinary-user",
          Map(
            "role" -> "user",
            "privilege" -> "user"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("strip forged authorization fields before resolving the production admin session is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(
          _with_session(
            _get_request("/web/system/admin?role=system_admin&privilege=system"),
            "weak-session-with-forged-query"
          )
        )
        .unsafeRunSync()

      Then("the observable contract for strip forged authorization fields before resolving the production admin session holds")
      response.status.code shouldBe 403
    }

    "allow component operator session to use component admin in production when explicitly enabled" in {
      Given("the prerequisites for allow component operator session to use component admin in production when explicitly enabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "component-admin-session",
          "component-operator",
          Map(
            "role" -> "component_operator",
            "privilege" -> "operator"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow component operator session to use component admin in production when explicitly enabled is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/notice-board/admin/entities/notice"), "component-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for allow component operator session to use component admin in production when explicitly enabled holds")
      response.status.code shouldBe 200
    }

    "deny system admin role when production privilege ceiling is only user" in {
      Given("the prerequisites for deny system admin role when production privilege ceiling is only user")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "weak-system-admin-session",
          "weak-system-admin",
          Map(
            "role" -> "system_admin",
            "privilege" -> "user"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny system admin role when production privilege ceiling is only user is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "weak-system-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for deny system admin role when production privilege ceiling is only user holds")
      response.status.code shouldBe 403
    }

    "allow system admin session to use system admin in production when explicitly enabled" in {
      Given("the prerequisites for allow system admin session to use system admin in production when explicitly enabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "system-admin-session",
          "system-admin",
          Map(
            "role" -> "system_admin",
            "privilege" -> "system"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "system-admin-session"))
        .unsafeRunSync()
      val performance = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/performance"), "system-admin-session"))
        .unsafeRunSync()
      When("allow system admin session to use system admin in production when explicitly enabled is exercised")
      val assembly = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin/assembly/report"), "system-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for allow system admin session to use system admin in production when explicitly enabled holds")
      response.status.code shouldBe 200
      performance.status.code shouldBe 200
      assembly.status.code shouldBe 200
    }

    "allow audit viewer session to read production admin jobs but not system admin home" in {
      Given("the prerequisites for allow audit viewer session to read production admin jobs but not system admin home")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "audit-viewer-session",
          "audit-viewer",
          Map(
            "role" -> "audit_viewer",
            "privilege" -> "system"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val jobs = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin/jobs"), "audit-viewer-session"))
        .unsafeRunSync()
      val home = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "audit-viewer-session"))
        .unsafeRunSync()
      When("allow audit viewer session to read production admin jobs but not system admin home is exercised")
      val performance = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/performance"), "audit-viewer-session"))
        .unsafeRunSync()

      Then("the observable contract for allow audit viewer session to read production admin jobs but not system admin home holds")
      jobs.status.code shouldBe 200
      home.status.code shouldBe 403
      performance.status.code shouldBe 403
    }

    "allow authenticated admin form API when develop anonymous admin is disabled" in {
      Given("the prerequisites for allow authenticated admin form API when develop anonymous admin is disabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow authenticated admin form API when develop anonymous admin is disabled is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice?principalId=admin-test"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow authenticated admin form API when develop anonymous admin is disabled holds")
      response.status.code shouldBe 200
    }

    "deny anonymous admin entity create POST in production operation mode before dispatch" in {
      Given("the prerequisites for deny anonymous admin entity create POST in production operation mode before dispatch")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("deny anonymous admin entity create POST in production operation mode before dispatch is exercised")
      val response = server
        ._submit_component_admin_entity_create(
          _post_form_request(
            "/form/notice-board/admin/entities/notice/create",
            "fields=id%3Dnotice_2%0Atitle%3Dnew+notice"
          ),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for deny anonymous admin entity create POST in production operation mode before dispatch holds")
      response.status.code shouldBe 403
      dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "allow component operator admin entity create POST in production operation mode" in {
      Given("the prerequisites for allow component operator admin entity create POST in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "component-admin-post-session",
          "component-operator",
          Map(
            "role" -> "component_operator",
            "privilege" -> "operator"
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("allow component operator admin entity create POST in production operation mode is exercised")
      val response = server
        ._submit_component_admin_entity_create(
          _with_session(
            _post_form_request(
              "/form/notice-board/admin/entities/notice/create",
            "fields=id%3Dnotice_2%0Atitle%3Dnew+notice%0Aauthor%3Dbob"
            ),
            "component-admin-post-session"
          ),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow component operator admin entity create POST in production operation mode holds")
      response.status.code shouldBe 200
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "serve admin entity form definition API from generated companion schema" in {
      Given("the prerequisites for serve admin entity form definition API from generated companion schema")
      val component = TestComponentFactory
        .create("generated_schema_component", Protocol.empty)
        .withComponentDescriptors(Vector(ComponentDescriptor(
          componentName = Some("generated_schema_component"),
          entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
            entityName = "order",
            collectionId = EntityCollectionId("test", "a", "order"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ))
        )))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(new ComponentFactory().bootstrap(component)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/generated-schema-component/admin/entities/order"),
          "generated-schema-component",
          "order"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      When("serve admin entity form definition API from generated companion schema is exercised")
      val names = fields.flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for serve admin entity form definition API from generated companion schema holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      names shouldBe Vector("id", "shortid", "name", "status")
      fields(1).hcursor.downField("system").as[Boolean].toOption shouldBe Some(true)
      fields(1).hcursor.downField("readonly").as[Boolean].toOption shouldBe Some(true)
      fields(1).hcursor.downField("required").as[Boolean].toOption shouldBe Some(false)
      fields(3).hcursor.downField("label").as[String].toOption shouldBe Some("Order status")
      fields(3).hcursor.downField("type").as[String].toOption shouldBe Some("select")
      fields(3).hcursor.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "submitted", "approved"))
      fields(3).hcursor.downField("required").as[Boolean].toOption shouldBe Some(true)
      fields(3).hcursor.downField("help").as[String].toOption shouldBe Some("CML generated status hint.")
    }

    "serve admin entity form definition API from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for serve admin entity form definition API from merged Schema and WebDescriptor controls")
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = _json_fields(json)
      val names = _json_field_names(fields)
      val body = _json_field(fields, "body")
      When("serve admin entity form definition API from merged Schema and WebDescriptor controls is exercised")
      val status = _json_field(fields, "status")

      Then("the observable contract for serve admin entity form definition API from merged Schema and WebDescriptor controls holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("entity")
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      names shouldBe Vector("id", "body", "status")
      body.downField("label").as[String].toOption shouldBe Some("Notice body")
      body.downField("type").as[String].toOption shouldBe Some("textarea")
      body.downField("placeholder").as[String].toOption shouldBe Some("Descriptor body placeholder.")
      body.downField("help").as[String].toOption shouldBe Some("Descriptor body help.")
      status.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "published", "archived"))
      status.downField("required").as[Boolean].toOption shouldBe Some(false)
    }

    "serve admin data form definition API from inferred data fields" in {
      Given("the prerequisites for serve admin data form definition API from inferred data fields")
      val fixture = _data_fixture()
      When("the observable result for serve admin data form definition API from inferred data fields is inspected")
      locally {
        Then("the observable contract for serve admin data form definition API from inferred data fields holds")
        _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit"),
            "notice-board",
            "audit"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data form definition JSON is invalid"))
        val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/new")
        fieldnames shouldBe Vector("id", "action", "actor")
      }
      }
    }

    "serve admin data update form definition API from inferred data fields" in {
      Given("the prerequisites for serve admin data update form definition API from inferred data fields")
      val fixture = _data_fixture()
      When("the observable result for serve admin data update form definition API from inferred data fields is inspected")
      locally {
        Then("the observable contract for serve admin data update form definition API from inferred data fields holds")
        _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_update_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit/audit_1/update"),
            "notice-board",
            "audit",
            "audit_1"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data update form definition JSON is invalid"))
        val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data-update")
        json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/update")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/edit")
        json.hcursor.downField("actions").downN(3).downField("path").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/update")
        fieldnames shouldBe Vector("id", "action", "actor")
      }
      }
    }

    "serve admin data form definition API from merged inferred data fields and WebDescriptor controls" in {
      Given("the prerequisites for serve admin data form definition API from merged inferred data fields and WebDescriptor controls")
      val fixture = _data_fixture()
      When("serve admin data form definition API from merged inferred data fields and WebDescriptor controls is exercised")
      val descriptor = _data_schema_web_descriptor(includenote = false)
      Then("the observable contract for serve admin data form definition API from merged inferred data fields and WebDescriptor controls holds")
      _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem, Some(descriptor)))

        val response = server
          ._component_admin_data_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit"),
            "notice-board",
            "audit"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data form definition JSON is invalid"))
        val fields = _json_fields(json)
        val names = _json_field_names(fields)
        val action = _json_field(fields, "action")
        val actor = _json_field(fields, "actor")

        response.status.code shouldBe 200
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
        names shouldBe Vector("id", "action", "actor")
        action.downField("type").as[String].toOption shouldBe Some("select")
        action.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("created", "updated"))
        actor.downField("required").as[Boolean].toOption shouldBe Some(true)
        actor.downField("placeholder").as[String].toOption shouldBe Some("Descriptor actor placeholder.")
        actor.downField("help").as[String].toOption shouldBe Some("Descriptor actor help.")
      }
    }

    "serve admin view form definition API from entity schema when the view name carries the view suffix" in {
      Given("the prerequisites for serve admin view form definition API from entity schema when the view name carries the view suffix")
      val subsystem = _view_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      When("serve admin view form definition API from entity schema when the view name carries the view suffix is exercised")
      val fields = _json_fields(json)

      Then("the observable contract for serve admin view form definition API from entity schema when the view name carries the view suffix holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "shortid", "label", "note")
    }

    "serve admin view form definition API from resolved view schema" in {
      Given("the prerequisites for serve admin view form definition API from resolved view schema")
      val subsystem = _view_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "view.notice-view" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("note", WebDescriptor.FormControl(controlType = Some("textarea")))
            )
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      When("serve admin view form definition API from resolved view schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin view form definition API from resolved view schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.view.notice-view")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("view")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-view")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view")
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      json.hcursor.downField("actions").downN(0).downField("name").as[String].toOption shouldBe Some("list")
      json.hcursor.downField("actions").downN(1).downField("name").as[String].toOption shouldBe Some("detail")
      json.hcursor.downField("actions").downN(2).downField("name").as[String].toOption shouldBe None
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("label")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("note")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("textarea")
    }

    "serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix" in {
      Given("the prerequisites for serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix")
      val subsystem = _aggregate_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      When("serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix is exercised")
      val fields = _json_fields(json)

      Then("the observable contract for serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "shortid", "label", "status")
    }

    "serve admin aggregate form definition API from resolved aggregate schema" in {
      Given("the prerequisites for serve admin aggregate form definition API from resolved aggregate schema")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("status", WebDescriptor.FormControl(controlType = Some("select"), values = Vector("draft", "published")))
            )
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      When("serve admin aggregate form definition API from resolved aggregate schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin aggregate form definition API from resolved aggregate schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.aggregate.notice-aggregate")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("aggregate")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-aggregate")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate")
      json.hcursor.downField("actions").downN(0).downField("name").as[String].toOption shouldBe Some("list")
      json.hcursor.downField("actions").downN(1).downField("name").as[String].toOption shouldBe Some("detail")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/{id}")
      json.hcursor.downField("actions").downN(2).downField("name").as[String].toOption shouldBe None
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("label")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("status")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("select")
      fields.downN(2).downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "published"))
    }

    "validate operation form API input without executing the operation" in {
      Given("the prerequisites for validate operation form API input without executing the operation")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice/validate", "accessToken=abc&extra=value"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("validate operation form API input without executing the operation is exercised")
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      Then("the observable contract for validate operation form API input without executing the operation holds")
      invalid.status.code shouldBe 200
      invalid.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      invalidjson.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.notice.post-secret-notice")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      invalidjson.hcursor.downField("errors").downN(0).downField("field").as[String].toOption shouldBe Some("body")
      invalidjson.hcursor.downField("errors").downN(0).downField("code").as[String].toOption shouldBe Some("required")
      invalidjson.hcursor.downField("warnings").downN(0).downField("field").as[String].toOption shouldBe Some("extra")

      val valid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice/validate",
            "body=hello&accessToken=abc&paging.page=2&paging.pageSize=20&crud.origin.href=/web/notice-board&csrf=token-1"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val validjson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      valid.status.code shouldBe 200
      validjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validjson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
      validjson.hcursor.downField("warnings").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "validate operation form API datatype values and multiplicity" in {
      Given("the prerequisites for validate operation form API datatype values and multiplicity")
      val component = new org.goldenport.cncf.component.Component() {}
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = "notice",
              operations = spec.OperationDefinitionGroup(
                operations = NonEmptyVector.of(
                  NoopOperation("validate-fields", Vector("count", "published", "publishedAt", "status", "tags"))
                )
              )
            )
          )
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val selector = "notice-board.notice.validate-fields"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "status" -> WebDescriptor.FormControl(controlType = Some("select"), values = Vector("draft", "published")),
            "tags" -> WebDescriptor.FormControl(controlType = Some("select"), values = Vector("news", "ops"), multiple = true)
          )
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-fields/validate",
            "count=abc&published=maybe&publishedAt=not-date&status=draft,published&tags=news,bad"
          ),
          "notice-board",
          "notice",
          "validate-fields"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))
      val errors = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      val errorfields = errors.flatMap(_.hcursor.downField("field").as[String].toOption)
      When("validate operation form API datatype values and multiplicity is exercised")
      val errorcodes = errors.flatMap(_.hcursor.downField("code").as[String].toOption)

      Then("the observable contract for validate operation form API datatype values and multiplicity holds")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorfields should contain allOf ("count", "published", "publishedAt", "status", "tags")
      errorcodes should contain ("datatype")
      errorcodes should contain ("invalid-value")
      errorcodes should contain ("multiplicity")

      val valid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-fields/validate",
            "count=12&published=true&publishedAt=2026-04-16T10:15:30&status=draft&tags=news,ops"
          ),
          "notice-board",
          "notice",
          "validate-fields"
        )
        .unsafeRunSync()
      val validjson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      validjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validjson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "serve and validate operation form validation hints" in {
      Given("the prerequisites for serve and validate operation form validation hints")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val definition = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/validate-hints"),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val definitionjson = parse(definition.as[String].unsafeRunSync()).getOrElse(fail("hint definition JSON is invalid"))
      val codefield = definitionjson.hcursor.downField("fields").downN(0)
      When("serve and validate operation form validation hints is exercised")
      val countfield = definitionjson.hcursor.downField("fields").downN(1)

      Then("the observable contract for serve and validate operation form validation hints holds")
      codefield.downField("validation").downField("minLength").as[Int].toOption shouldBe Some(2)
      codefield.downField("validation").downField("maxLength").as[Int].toOption shouldBe Some(4)
      codefield.downField("validation").downField("pattern").as[String].toOption shouldBe Some("^[A-Z0-9]+$")
      countfield.downField("validation").downField("min").as[BigDecimal].toOption shouldBe Some(BigDecimal(0))
      countfield.downField("validation").downField("max").as[BigDecimal].toOption shouldBe Some(BigDecimal(100))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice",
        "validate_hints",
        descriptor
      ).map(_.body).getOrElse(fail("hint operation form is missing"))

      html should include ("minlength=\"2\"")
      html should include ("maxlength=\"4\"")
      html should include ("pattern=\"^[A-Z0-9]+$\"")

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-hints/validate",
            "code=toolong&count=101"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("hint validation JSON is invalid"))
      val errorcodes = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("code").as[String].toOption)

      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorcodes should contain ("max-length")
      errorcodes should contain ("pattern")
      errorcodes should contain ("max")
    }

    "keep Schema validation constraints when WebDescriptor attempts to relax them" in {
      Given("the prerequisites for keep Schema validation constraints when WebDescriptor attempts to relax them")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-hints/validate",
            "code=A&count=-1"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("relax validation JSON is invalid"))
      val errors = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      When("keep Schema validation constraints when WebDescriptor attempts to relax them is exercised")
      val errorpairs = errors.flatMap { json =>
        for {
          field <- json.hcursor.downField("field").as[String].toOption
          code <- json.hcursor.downField("code").as[String].toOption
        } yield field -> code
      }

      Then("the observable contract for keep Schema validation constraints when WebDescriptor attempts to relax them holds")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorpairs should contain ("code" -> "min-length")
      errorpairs should contain ("count" -> "min")
    }

    "redisplay operation form validation hint errors before HTML dispatch" in {
      Given("the prerequisites for redisplay operation form validation hint errors before HTML dispatch")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("DISPATCHED", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice/validate-hints",
            "code=toolong&count=101&crud.origin.href=/web/notice-board&paging.page=3&csrf=token-1"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      When("redisplay operation form validation hint errors before HTML dispatch is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay operation form validation hint errors before HTML dispatch holds")
      response.status.code shouldBe 400
      html should include ("Validation failed.")
      html should include ("code must be at most 4 characters.")
      html should include ("code does not match the required pattern.")
      html should include ("count must be less than or equal to 100.")
      html should include ("value=\"toolong\"")
      html should include ("value=\"101\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"3\"")
      html should include (s"type=\"hidden\" name=\"csrf\" value=\"${_test_csrf_token}\"")
      html should not include ("DISPATCHED")
    }

    "render aggregate operation form with admin descriptor field controls" in {
      Given("the prerequisites for render aggregate operation form with admin descriptor field controls")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map("notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected),
        admin = Map(
          "aggregate.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id", WebDescriptor.FormControl(hidden = true)),
              WebDescriptor.AdminField("approved", WebDescriptor.FormControl(controlType = Some("select"), values = Vector("true", "false")))
            )
          )
        )
      )

      When("render aggregate operation form with admin descriptor field controls is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice_aggregate",
        "approve_notice_aggregate",
        descriptor,
        values = Map("id" -> "notice_1", "approved" -> "true")
      ).map(_.body).getOrElse(fail("aggregate operation form is missing"))

      Then("the observable contract for render aggregate operation form with admin descriptor field controls holds")
      html should include ("type=\"hidden\"")
      html should include ("name=\"id\"")
      html should include ("value=\"notice_1\"")
      html should include ("<select")
      html should include ("name=\"approved\"")
      html should include ("<option value=\"true\" selected>true</option>")
      html should include ("<option value=\"false\">false</option>")
    }

    "redirect HTML form submissions by descriptor transition while form-api returns operation response" in {
      Given("the prerequisites for redirect HTML form submissions by descriptor transition while form-api returns operation response")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            successRedirect = Some("/web/${component}/admin/aggregates/${service}/${result.id}"),
            failureRedirect = Some("/form/${component}/${service}/${operation}")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      val redirected = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()
      When("redirect HTML form submissions by descriptor transition while form-api returns operation response is exercised")
      val api = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()

      Then("the observable contract for redirect HTML form submissions by descriptor transition while form-api returns operation response holds")
      redirected.status.code shouldBe 303
      redirected.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      api.status.code shouldBe 200
      api.as[String].unsafeRunSync() should include ("aggregate-updated:notice_1")
    }

    "render operation form result through descriptor result template" in {
      Given("the prerequisites for render operation form result through descriptor result template")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>${operation.label} Custom Result</h2>
                |  <p>Submitted ${form.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |  <textus-property-list source="result"></textus-property-list>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through descriptor result template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through descriptor result template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Custom Result")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "render operation form result route through descriptor result template when static template is absent" in {
      Given("the prerequisites for render operation form result route through descriptor result template when static template is absent")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Descriptor Result Route</h2>
                |  <p>${form.id}</p>
                |  <p>${result.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation form result route through descriptor result template when static template is absent is exercised")
      val html = server
        ._operation_form_result(
          _get_request("/form/notice-board/notice-aggregate/approve-notice-aggregate/result?id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result route through descriptor result template when static template is absent holds")
      html should include ("Descriptor Result Route")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "resolve Static Form template properties across camel, snake, and kebab names" in {
      Given("the prerequisites for resolve Static Form template properties across camel, snake, and kebab names")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "approve-notice",
          Map("pageContext.sessionAuthenticated" -> "true")
        ),
        200,
        "application/json",
        """{"data":{"current":{"workTitle":"源氏物語","textualWorkInformationId":"info-1","rdfUri":"https://example.test/book/1","isbn13":"9784003510179"}}}"""
      )

      When("resolve Static Form template properties across camel, snake, and kebab names is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |<p>${result.body.data.current.work_title}</p>
          |<p>${result.body.data.current.textual-work-information-id}</p>
          |<p>${result.body.data.current.rdf_uri}</p>
          |<p>${result.body.data.current.isbn-13}</p>
          |<p>${pageContext.session-authenticated}</p>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for resolve Static Form template properties across camel, snake, and kebab names holds")
      html should include ("源氏物語")
      html should include ("info-1")
      html should include ("https://example.test/book/1")
      html should include ("9784003510179")
      html should include ("true")
      html should not include ("${result.body.data.current.work_title}")
    }

    "render operation result UX profile metadata" in {
      Given("the prerequisites for render operation result UX profile metadata")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("notice-board", "notice", "approve-notice"),
        200,
        "text/plain",
        "ok",
        uxProfile = WebUxProfile.Material
      )

      When("render operation result UX profile metadata is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render operation result UX profile metadata holds")
      html should include ("data-textus-ux-profile=\"material\"")
    }

    "render static template UX profile metadata" in {
      Given("the prerequisites for render static template UX profile metadata")
      val descriptor = WebDescriptor(
        profile = Some(WebUxProfile.Bootstrap),
        profileRaw = Some("bootstrap"),
        apps = Vector(WebDescriptor.App(
          "console",
          profile = Some(WebUxProfile.Compact),
          profileRaw = Some("compact")
        )),
        pages = Map(
          "console.detail" -> WebDescriptor.PageCustomization(
            profile = Some(WebUxProfile.Material),
            profileRaw = Some("material")
          )
        )
      )

      val apphtml = _renderer.renderStaticTemplate(
        "console",
        Vector("index"),
        """<article data-textus-page="static">${textus.uxProfile}</article>""",
        webdescriptor = descriptor
      ).body
      When("render static template UX profile metadata is exercised")
      val pagehtml = _renderer.renderStaticTemplate(
        "console",
        Vector("detail"),
        """<article data-textus-page="static" data-textus-ux-profile="${page.uxProfile}">${textus.uxProfile}</article>""",
        webdescriptor = descriptor
      ).body

      Then("the observable contract for render static template UX profile metadata holds")
      apphtml should include ("compact")
      pagehtml should include ("data-textus-ux-profile=\"material\"")
      pagehtml should include (">material</article>")
    }

    }
  }
}
