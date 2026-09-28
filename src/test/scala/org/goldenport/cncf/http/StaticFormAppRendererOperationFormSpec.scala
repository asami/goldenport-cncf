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
final class StaticFormAppRendererOperationFormSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide HTML operation form and typed-update contracts" which {
    "render component HTML form operation index" in {
      Given("the prerequisites for render component HTML form operation index")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component HTML form operation index is exercised")
      val html = _renderer.renderFormIndex(subsystem, component.name).map(_.body).getOrElse(fail("form index is missing"))

      Then("the observable contract for render component HTML form operation index holds")
      html should include (s"${component.name} Forms")
      html should include ("/web/assets/bootstrap.min.css")
      html should include ("card admin-card")
      html should include ("list-group")
      html should include ("row g-3")
      html should include ("nav nav-pills")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/dashboard")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
    }

    "render component HTML operation form" in {
      Given("the prerequisites for render component HTML operation form")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)

      When("render component HTML operation form is exercised")
      val html = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render component HTML operation form holds")
      html should include ("<form method=\"post\"")
      html should include ("data-textus-page=\"static-form-operation\"")
      html should include ("data-textus-section=\"operation-form\"")
      html should include ("data-textus-section=\"form-errors\"")
      html should include ("data-textus-section=\"form-controls\"")
      html should include ("data-textus-section=\"form-actions\"")
      html should include ("data-textus-ux-profile=\"bootstrap\"")
      html should include (s"""data-textus-form="${componentpath}.${servicepath}.${operationpath}"""")
      html should include ("data-textus-field=\"fields\"")
      html should include ("data-textus-action=\"submit\"")
      html should include ("data-textus-action=\"operations\"")
      html should include ("card admin-card")
      html should include ("row g-3")
      html should include ("admin-action-row")
      html should include ("name=\"fields\"")
      html should include ("class=\"form-control\"")
      html should include (s"/form/${componentpath}/${servicepath}/${operationpath}")
      html should not include ("cdn.jsdelivr")
    }

    "render operation form UX profile metadata" in {
      Given("the prerequisites for render operation form UX profile metadata")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val selector = WebDescriptor.formSelector(component.name, service.name, operation.name)
      val globaldescriptor = WebDescriptor(
        profile = Some(WebUxProfile.Compact),
        profileRaw = Some("compact"),
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val appdescriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        apps = Vector(WebDescriptor.App(componentpath, profile = Some(WebUxProfile.Material), profileRaw = Some("material")))
      )
      val formdescriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(profile = Some(WebUxProfile.Admin), profileRaw = Some("admin")))
      )

      val globalhtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = globaldescriptor).map(_.body).getOrElse(fail("operation form is missing"))
      val apphtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = appdescriptor).map(_.body).getOrElse(fail("operation form is missing"))
      When("render operation form UX profile metadata is exercised")
      val formhtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = formdescriptor).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render operation form UX profile metadata holds")
      globalhtml should include ("data-textus-ux-profile=\"compact\"")
      apphtml should include ("data-textus-ux-profile=\"material\"")
      formhtml should include ("data-textus-ux-profile=\"admin\"")
    }

    "append development debug panel to operation form error redisplay" in {
      Given("the prerequisites for append development debug panel to operation form error redisplay")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))

      When("append development debug panel to operation form error redisplay is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        component.name,
        service.name,
        operation.name,
        values = Map(
          "error.status" -> "500",
          "error.body" -> "openlibrary.org",
          "error.diagnostic.trace.id" -> "test-runtime-trace-1",
          "error.diagnostic.id" -> "test-runtime-correlation-1",
          "error.diagnostic.failure" -> "openlibrary.org",
          "error.diagnostic.providers" -> "provider:openbd.book.isbn.lookup,provider:openlibrary.book.isbn.lookup",
          "error.diagnostic.calltree.json" -> RecordEncoder.json(Record.data(
            "calltree" -> Vector(
              Record.data(
                "label" -> "action:TextusKnowledgeEditor.BookEditor.seedBook",
                "kind" -> "action",
                "flow" -> Vector(
                  Record.data(
                    "label" -> "provider:openlibrary.book.isbn.lookup",
                    "kind" -> "provider"
                  )
                )
              )
            )
          )),
          "error.diagnostic.calltree.href" -> "/rest/v1/admin/execution/calltree",
          "error.diagnostic.history.href" -> "/rest/v1/admin/execution/history",
          "textus.debug.executionPanel" -> "true"
        ),
        operationMode = OperationMode.Develop,
        showExecutionDebugPanel = true
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for append development debug panel to operation form error redisplay holds")
      html should include ("Form submission failed.")
      html should include ("textus-execution-debug-panel")
      html should not include ("error.diagnostic.trace.id")
      html should not include ("error.diagnostic.providers")
      html should include ("Execution path")
      html should include ("test-runtime-trace-1")
      html should include ("test-runtime-correlation-1")
      html should include ("provider:openlibrary.book.isbn.lookup")
      html should include ("data-textus-calltree")
      html should include ("action:TextusKnowledgeEditor.BookEditor.seedBook")
      html should include ("openlibrary.org")
    }

    "ignore external debug panel flags on operation form input pages" in {
      Given("the prerequisites for ignore external debug panel flags on operation form input pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))

      When("ignore external debug panel flags on operation form input pages is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        component.name,
        service.name,
        operation.name,
        values = Map("textus.debug.executionPanel" -> "true"),
        operationMode = OperationMode.Develop
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for ignore external debug panel flags on operation form input pages holds")
      html should not include ("textus-execution-debug-panel")
    }

    "apply app-scoped assets to the component HTML form index" in {
      Given("the prerequisites for apply app-scoped assets to the component HTML form index")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentpath,
          assets = WebDescriptor.Assets(
            css = Vector("/web/component/assets/forms.css"),
            js = Vector("/web/component/assets/forms.js")
          )
        )),
        form = Map(
          s"${componentpath}.service.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/operation.css"),
              js = Vector("/web/component/assets/operation.js")
            )
          )
        )
      )

      When("apply app-scoped assets to the component HTML form index is exercised")
      val html = _renderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))

      Then("the observable contract for apply app-scoped assets to the component HTML form index holds")
      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should not include ("/web/component/assets/operation.css")
      html should not include ("/web/component/assets/operation.js")
    }

    "apply app and form scoped assets to operation input forms" in {
      Given("the prerequisites for apply app and form scoped assets to operation input forms")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentpath,
          assets = WebDescriptor.Assets(
            css = Vector("/web/component/assets/forms.css"),
            js = Vector("/web/component/assets/forms.js")
          )
        )),
        form = Map(
          selector -> WebDescriptor.Form(
            enabled = Some(true),
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/operation.css"),
              js = Vector("/web/component/assets/operation.js")
            )
          ),
          s"${componentpath}.other.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/other.css"),
              js = Vector("/web/component/assets/other.js")
            )
          )
        )
      )

      When("apply app and form scoped assets to operation input forms is exercised")
      val html = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for apply app and form scoped assets to operation input forms holds")
      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should include ("/web/component/assets/operation.css")
      html should include ("/web/component/assets/operation.js")
      html should not include ("/web/component/assets/other.css")
      html should not include ("/web/component/assets/other.js")
    }

    "filter HTML form operations by WebDescriptor form controls" in {
      Given("the prerequisites for filter HTML form operations by WebDescriptor form controls")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val path = s"/form/${componentpath}/${servicepath}/${operationpath}"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Public),
        form = Map(selector -> WebDescriptor.Form(enabled = Some(false)))
      )

      val index = _renderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))
      When("filter HTML form operations by WebDescriptor form controls is exercised")
      val form = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      Then("the observable contract for filter HTML form operations by WebDescriptor form controls holds")
      index should not include (path)
      form shouldBe None
    }

    "allow exposed HTML form operations when no explicit form control exists" in {
      Given("the prerequisites for allow exposed HTML form operations when no explicit form control exists")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )

      When("allow exposed HTML form operations when no explicit form control exists is exercised")
      val form = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      Then("the observable contract for allow exposed HTML form operations when no explicit form control exists holds")
      form.map(_.body).getOrElse(fail("operation form is missing")) should include (s"/form/${componentpath}/${servicepath}/${operationpath}")
    }

    "render HTML operation form with query-provided initial fields" in {
      Given("the prerequisites for render HTML operation form with query-provided initial fields")
      val subsystem = _aggregate_fixture_subsystem()

      When("render HTML operation form with query-provided initial fields is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice_aggregate",
        "approve_notice_aggregate",
        values = Map(
          "id" -> "notice_1",
          "crud.origin.href" -> "/web/notice-board/admin/aggregates/notice-aggregate?page=2",
          "paging.page" -> "2",
          "search.keyword" -> "notice"
        )
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render HTML operation form with query-provided initial fields holds")
      html should include ("name=\"id\"")
      html should include ("type=\"text\"")
      html should include ("required")
      html should include ("value=\"notice_1\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board/admin/aggregates/notice-aggregate?page=2\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"search.keyword\" value=\"notice\"")
      html should include ("Additional fields")
      html should not include ("crud.origin.href=")
      html should not include ("search.keyword=")
    }

    "render descriptor-defined select and hidden controls for operation parameters" in {
      Given("the prerequisites for render descriptor-defined select and hidden controls for operation parameters")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "accessToken" -> WebDescriptor.FormControl(hidden = true),
            "body" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("hello", "world"),
              placeholder = Some("Descriptor body placeholder."),
              help = Some("Descriptor body help.")
            )
          )
        ))
      )

      When("render descriptor-defined select and hidden controls for operation parameters is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice",
        "post_secret_notice",
        descriptor,
        values = Map("body" -> "hello", "accessToken" -> "abc")
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render descriptor-defined select and hidden controls for operation parameters holds")
      html should include ("<select")
      html should include ("<option value=\"hello\" selected>")
      html should include ("Notice body")
      html should include ("Descriptor body help.")
      html should include ("name=\"body\"")
      html should include ("type=\"hidden\"")
      html should include ("name=\"accessToken\"")
    }

    "serve operation form definition API from the same resolved Web schema" in {
      Given("the prerequisites for serve operation form definition API from the same resolved Web schema")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "accessToken" -> WebDescriptor.FormControl(hidden = true),
            "body" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("hello", "world"),
              placeholder = Some("Descriptor body placeholder."),
              help = Some("Descriptor body help.")
            )
          )
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("form definition JSON is invalid"))
      When("serve operation form definition API from the same resolved Web schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve operation form definition API from the same resolved Web schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.notice.post-secret-notice")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("operation")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("POST")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form-api/org-goldenport-cncf-test-notice-board/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(2).downField("path").as[String].toOption shouldBe Some("/form-api/org-goldenport-cncf-test-notice-board/notice/post-secret-notice/validate")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("body")
      fields.downN(0).downField("label").as[String].toOption shouldBe Some("Notice body")
      fields.downN(0).downField("type").as[String].toOption shouldBe Some("select")
      fields.downN(0).downField("required").as[Boolean].toOption shouldBe Some(true)
      fields.downN(0).downField("values").as[Vector[String]].toOption shouldBe Some(Vector("hello", "world"))
      fields.downN(0).downField("placeholder").as[String].toOption shouldBe Some("Descriptor body placeholder.")
      fields.downN(0).downField("help").as[String].toOption shouldBe Some("Descriptor body help.")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("accessToken")
      fields.downN(1).downField("hidden").as[Boolean].toOption shouldBe Some(true)
    }



    }

    "provide Static Web form-result and continuation contracts" which {
    "render a schema-backed operation form inside a Static Web page" in {
      Given("an aggregate command exposed to a Static Web template")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          successRedirect = Some("/web/notice-board/detail?outcome=approved"),
          controls = Map(
            "id" -> WebDescriptor.FormControl(hidden = true),
            "approved" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("true", "false")
            )
          )
        ))
      )
      val template =
        """<main>
          |  <textus:operation-form service="notice-aggregate"
          |                         operation="approve-notice-aggregate"
          |                         value-id="${notice.id}"
          |                         value-approved="${notice.approved}"
          |                         submit-label="${message.action.approve}"></textus:operation-form>
          |</main>""".stripMargin

      When("the framework renders the page without a browser Form API request")
      val html = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(
          values = Map(
            "notice.id" -> "notice_1",
            "notice.approved" -> "true",
            "id" -> "unrelated-page-id",
            "csrf" -> "token-1"
          ),
          messages = Map("action.approve" -> "Approve")
        ),
        descriptor
      ).body

      Then("the operation schema, values, hidden context, and canonical HTML Form ingress are present")
      html should include ("action=\"/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate\"")
      html should include ("data-textus-widget=\"textus:operation-form\"")
      html should include ("type=\"hidden\"")
      html should include ("name=\"id\"")
      html should include ("value=\"notice_1\"")
      html should include ("<option value=\"true\" selected>true</option>")
      html should include ("name=\"csrf\" value=\"token-1\"")
      html should include (">Approve</button>")
      html should not include ("/form-api/")
      html should not include ("<textus:operation-form")

      And("dynamic boolean values select a schema-backed checkbox before widget rendering")
      val checkboxdescriptor = descriptor.copy(form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "id" -> WebDescriptor.FormControl(hidden = true),
          "approved" -> WebDescriptor.FormControl(controlType = Some("checkbox"))
        )
      )))
      val checkboxhtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> "true"
        )),
        checkboxdescriptor
      ).body
      checkboxhtml should include ("type=\"checkbox\" value=\"true\" checked")

      And("multiple select prefill marks every schema-backed value")
      val multipledescriptor = descriptor.copy(form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "id" -> WebDescriptor.FormControl(hidden = true),
          "approved" -> WebDescriptor.FormControl(
            controlType = Some("select"),
            values = Vector("true", "false"),
            multiple = true
          )
        )
      )))
      val multiplehtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> "true,false"
        )),
        multipledescriptor
      ).body
      multiplehtml should include ("<input type=\"hidden\" name=\"approved\" value=\"\">")
      multiplehtml should include ("<select class=\"form-select\" id=\"field-approved\" name=\"approved\" required multiple>")
      multiplehtml should include ("<option value=\"true\" selected>true</option>")
      multiplehtml should include ("<option value=\"false\" selected>false</option>")

      And("an empty multiple-select prefill carries an explicit clear value")
      val clearmultiplehtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> ""
        )),
        multipledescriptor
      ).body
      clearmultiplehtml should include ("<input type=\"hidden\" name=\"approved\" value=\"\">")
      clearmultiplehtml should not include ("<option value=\"true\" selected>true</option>")
      clearmultiplehtml should not include ("<option value=\"false\" selected>false</option>")

      val unboundhtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        """<textus:operation-form service="notice-aggregate" operation="approve-notice-aggregate"></textus:operation-form>""",
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map("id" -> "unrelated-page-id")),
        descriptor
      ).body
      unboundhtml should not include ("unrelated-page-id")

      val unresolvedhtml = _renderer.renderStaticTemplate(
        "planning-app",
        Vector("detail"),
        template,
        pageContext = WebPageContext(values = Map("notice.id" -> "notice_1")),
        webdescriptor = descriptor
      ).body
      unresolvedhtml should not include ("<textus:operation-form")
      unresolvedhtml should not include ("data-textus-widget=\"textus:operation-form\"")

      And("submitting the rendered form dispatches the aggregate command and redirects with GET")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))
      val response = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1&approved=false&approved=true&csrf=token-1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      response.status.code shouldBe 303
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some("/web/notice-board/detail?outcome=approved")
    }

    "render a localized one-time flash after operation-form Post Redirect Get" in {
      Given("an aggregate command with an explicit redirect message key")
      val root = Files.createTempDirectory("cncf-static-web-flash-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("detail.html"),
        """<!doctype html><html><head><title>Detail</title></head><body><textus:flash></textus:flash><main>Committed view</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
        WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("ja-JP")
      ))
      val subsystem = _aggregate_http_fixture_subsystem(
        configuration,
        Vector(WebMessageCatalog(
          "planning-app",
          java.util.Locale.JAPAN,
          Map(
            "notice.approved" -> "承認しました",
            "other.message" -> "Forged outcome"
          )
        ))
      )
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(
          selector -> WebDescriptor.Form(
            successRedirect = Some("/web/notice-board/planning-app/detail"),
            successMessageKey = Some("notice.approved")
          ),
          "other-component.notice.approve" -> WebDescriptor.Form(
            successMessageKey = Some("other.message")
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      When("the command succeeds and the browser follows the redirect")
      val redirect = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      val setcookie = redirect.headers.headers
        .find(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .getOrElse(fail("flash cookie is missing"))
      val cookie = setcookie.takeWhile(_ != ';')
      val request = _get_request("/web/notice-board/planning-app/detail")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie))
      val response = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(request)
      ).unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      Then("the redirect carries only a bounded key and the next HTML response localizes and consumes it")
      redirect.status.code shouldBe 303
      setcookie should include ("HttpOnly")
      setcookie should include ("SameSite=Lax")
      setcookie should include ("Max-Age=120")
      setcookie should not include ("承認しました")
      html should include ("承認しました")
      html should include ("alert-success")
      html should include ("data-textus-widget=\"textus:flash\"")
      html should include ("Committed view")
      html should not include ("<textus:flash")
      response.headers.headers
        .filter(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value).mkString("\n") should include ("Max-Age=0")

      And("an unconfigured cross-component key cannot select catalog text")
      val rejectedcookie = WebFlash.encode(WebFlash.Value("success", "other.message"))
        .getOrElse(fail("test flash value was not encoded"))
      val rejectedrequest = _get_request("/web/notice-board/planning-app/detail")
        .putHeaders(org.http4s.Header.Raw(
          org.typelevel.ci.CIString("Cookie"),
          s"${WebFlash.cookieName("notice-board")}=${rejectedcookie}"
        ))
      val rejected = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(rejectedrequest)
      ).unsafeRunSync().as[String].unsafeRunSync()
      rejected should not include ("other.message")
      rejected should not include ("Forged outcome")
      rejected should not include ("data-textus-widget=\"textus:flash\"")
    }

    "issue and enforce a session-associated CSRF token for Static Web operation forms" in {
      Given("a Static Web page with one aggregate command form")
      val root = Files.createTempDirectory("cncf-static-web-csrf-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("detail.html"),
        """<!doctype html><html><head><title>Detail</title></head><body><textus:operation-form component="notice-admin" service="notice-aggregate" operation="approve-notice-aggregate" value-id="notice_1"></textus:operation-form></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
      ))
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets(configuration)
      val selector = "notice-admin.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          successRedirect = Some("/web/notice-board/planning-app/detail")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("the browser loads the page")
      val getrequest = _get_request("/web/notice-board/planning-app/detail")
      val getresponse = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(getrequest)
      ).unsafeRunSync()
      val html = getresponse.as[String].unsafeRunSync()
      val cookiename = WebCsrf.cookieName
      val setcookie = getresponse.headers.headers
        .filter(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .find(_.startsWith(s"${cookiename}="))
        .getOrElse(fail("CSRF cookie is missing"))
      val cookie = setcookie.takeWhile(_ != ';')
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(html)
        .map(_.group(1))
        .getOrElse(fail("CSRF hidden field is missing"))

      Then("the response synchronizes a strong hidden token and HttpOnly cookie")
      cookie shouldBe s"${cookiename}=${token}"
      setcookie should include ("HttpOnly")
      setcookie should include ("SameSite=Lax")
      getresponse.headers.get(org.typelevel.ci.CIString("Cache-Control")).map(_.head.value) shouldBe
        Some("private, no-store")
      html should include ("action=\"/form/org-goldenport-cncf-test-notice-admin/notice-aggregate/approve-notice-aggregate\"")
      WebCsrf.isValid(None, token) shouldBe true

      And("only a matching browser submission reaches operation dispatch")
      val validrequest = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
      ).withEntity(s"id=notice_1&csrf=${token}")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie))
      val validresponse = server._submit_operation_form(
        validrequest,
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      validresponse.status.code shouldBe 303
      dispatcher.forms.size shouldBe 1
      dispatcher.forms.last.getString("csrf") shouldBe None

      val missingresponse = server._submit_operation_form(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
        ).withEntity("id=notice_1"),
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      missingresponse.status.code shouldBe 403
      dispatcher.forms.size shouldBe 1

      val othertoken = WebCsrf.issue(None)
      val mismatchresponse = server._submit_operation_form(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
        ).withEntity(s"id=notice_1&csrf=${othertoken}")
          .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie)),
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      mismatchresponse.status.code shouldBe 403
      dispatcher.forms.size shouldBe 1

      And("an authenticated token is invalid after the session changes")
      val sessiontoken = WebCsrf.issue(Some("session-one"))
      WebCsrf.verify(
        Some("session-one"),
        Some(sessiontoken),
        Some(sessiontoken)
      ) shouldBe true
      WebCsrf.verify(
        Some("session-two"),
        Some(sessiontoken),
        Some(sessiontoken)
      ) shouldBe false
    }

    "apply subject-safe cache policy to Static Web documents" in {
      Given("a read-only Static Web page in a wired multi-user Subsystem")
      val root = Files.createTempDirectory("cncf-static-web-cache-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("index.html"),
        "<!doctype html><html><head><title>Planning</title></head><body><main>Public planning page</main></body></html>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        approot.resolve("form.html"),
        "<!doctype html><html><body><form method=\"post\" action=\"/form/notice-board/notice-aggregate/approve-notice-aggregate\"><input name=\"title\"></form></body></html>",
        StandardCharsets.UTF_8
      )
      val multiconfiguration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
        org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")
      ))
      val multisubsystem = _with_multi_user_authentication(_aggregate_http_fixture_subsystem(multiconfiguration))
      multisubsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe
        Some(org.goldenport.cncf.subsystem.SubsystemUserMode.MultiUser)
      multisubsystem.resolvedSecurityWiring.authentication.enabledProviders.map(_.name) shouldBe
        Vector("static-web-authentication")
      val multiserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(multisubsystem))
      def _header_(response: org.http4s.Response[IO], name: String): Option[String] =
        response.headers.get(org.typelevel.ci.CIString(name)).map(_.head.value)

      When("an anonymous browser without a session loads the public document")
      val publicresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app"))
      ).unsafeRunSync()

      Then("shared storage is allowed only with revalidation and language variance")
      publicresponse.status.code shouldBe 200
      _header_(publicresponse, "Cache-Control") shouldBe Some("public, max-age=0, must-revalidate")
      _header_(publicresponse, "Vary") shouldBe
        Some("Accept-Language, Cookie, Authorization, X-Textus-Session, X-CNCF-Session")

      When("the same multi-user document is requested with a session")
      val sessionresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_with_session(_get_request("/web/notice-board/planning-app"), "subject-session"))
      ).unsafeRunSync()

      Then("the subject-associated document cannot enter a shared cache")
      sessionresponse.status.code shouldBe 200
      _header_(sessionresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(sessionresponse, "Vary") shouldBe None

      And("an authorization-bearing request also remains private without a session cookie")
      val authorizationresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app").putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("Authorization"), "Bearer subject-token")
        ))
      ).unsafeRunSync()
      authorizationresponse.status.code shouldBe 200
      _header_(authorizationresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(authorizationresponse, "Vary") shouldBe None

      And("an alternate session ingress remains private")
      val alternatesessionresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app").putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("X-CNCF-Session"), "alternate-session")
        ))
      ).unsafeRunSync()
      alternatesessionresponse.status.code shouldBe 200
      _header_(alternatesessionresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(alternatesessionresponse, "Vary") shouldBe None

      And("an anonymous form retains its CSRF cookie and stays private")
      val csrfresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector("form"),
        Some(_get_request("/web/notice-board/planning-app/form"))
      ).unsafeRunSync()
      csrfresponse.status.code shouldBe 200
      _header_(csrfresponse, "Cache-Control") shouldBe Some("private, no-store")
      csrfresponse.headers.get(org.typelevel.ci.CIString("Set-Cookie")) should not be empty

      And("standalone documents remain private even without an authentication session")
      val standaloneconfiguration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
      ))
      val standalonesubsystem = _aggregate_http_fixture_subsystem(standaloneconfiguration)
      val standaloneserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(standalonesubsystem))
      val standaloneresponse = standaloneserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app"))
      ).unsafeRunSync()
      _header_(standaloneresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(standaloneresponse, "Vary") shouldBe None
    }

    "carry a declared failure flash without exposing the operation response" in {
      Given("the prerequisites for carry a declared failure flash without exposing the operation response")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          failureRedirect = Some("/web/notice-board/planning-app/detail"),
          failureMessageKey = Some("notice.approval-failed")
        ))
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("sensitive operation failure detail", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      val setcookie = response.headers.headers
        .find(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .getOrElse(fail("failure flash cookie is missing"))
      When("carry a declared failure flash without exposing the operation response is exercised")
      val encoded = setcookie.takeWhile(_ != ';').split("=", 2).lift(1)
        .getOrElse(fail("failure flash cookie value is missing"))

      Then("the observable contract for carry a declared failure flash without exposing the operation response holds")
      response.status.code shouldBe 303
      WebFlash.decode(encoded) shouldBe Some(WebFlash.Value("danger", "notice.approval-failed"))
      setcookie should not include ("sensitive operation failure detail")
    }

    "resolve legacy result.body.data paths against unwrapped JSON response bodies" in {
      Given("the prerequisites for resolve legacy result.body.data paths against unwrapped JSON response bodies")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "book-editor",
          "book",
          "get-book"
        ),
        200,
        "application/json",
        """{"current":{"title":"源氏物語"},"counts":{"information_count":2},"information":[{"title":"Book A"}]}"""
      )

      When("resolve legacy result.body.data paths against unwrapped JSON response bodies is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |<p>${result.body.data.current.title}</p>
          |<textus:summary-card title="Information" source="result.body.data.counts.information_count"></textus:summary-card>
          |<textus:table source="result.body.data.information" pagination="false"></textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for resolve legacy result.body.data paths against unwrapped JSON response bodies holds")
      html should include ("源氏物語")
      html should include ("<strong class=\"display-6 text-primary\">2</strong>")
      html should include ("Book A")
      html should not include ("${result.body.data.current.title}")
    }

    "render operation form result through static success template convention before descriptor template" in {
      Given("the prerequisites for render operation form result through static success template convention before descriptor template")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__success.html",
            """<!doctype html>
              |<html>
              |<body>
              |  <h1>${operation.label} Static Success</h1>
              |  <p>Submitted ${form.id}</p>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</body>
              |</html>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some("<article><h2>Descriptor Result</h2></article>")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through static success template convention before descriptor template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through static success template convention before descriptor template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static Success")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Descriptor Result")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "prefer page-local static result template when textus form page is submitted" in {
      Given("the prerequisites for prefer page-local static result template when textus form page is submitted")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "new__success.html",
            """<!doctype html>
              |<html>
              |<body>
              |  <h1>New Page Success</h1>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</body>
              |</html>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some("<article><h2>Descriptor Result</h2></article>")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("prefer page-local static result template when textus form page is submitted is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&textus.form.page=new"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for prefer page-local static result template when textus form page is submitted holds")
      html should include ("New Page Success")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Descriptor Result")
      dispatcher.forms.lastOption.flatMap(_.getString("textus.form.page")) shouldBe None

      val pagecontexthtml = server
        ._prepared_form_result_template(
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          200,
          Map("pageContext.page" -> "new")
        )
        .toOption
        .flatten
        .getOrElse(fail("page context result template is missing"))
      pagecontexthtml should include ("New Page Success")
      pagecontexthtml should not include ("Descriptor Result")
    }

    "expand result body JSON paths in static result templates" in {
      Given("the prerequisites for expand result body JSON paths in static result templates")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("blog", "blog", "get-my-post"),
        200,
        "application/json",
        """{"entity_id":"major-post-1","title":"Hello <Blog>","content":"<article><p>Body</p></article>"}"""
      )

      When("expand result body JSON paths in static result templates is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<form action="/form/blog-component/blog/save-editor-post" method="post">
          |  <input name="id" value="${result.body.entity_id}">
          |  <input name="title" value="${result.body.title}">
          |  <textarea name="content">${result.body.content}</textarea>
          |</form>""".stripMargin
      ).body

      Then("the observable contract for expand result body JSON paths in static result templates holds")
      html should include ("value=\"major-post-1\"")
      html should include ("value=\"Hello &lt;Blog&gt;\"")
      html should include ("&lt;article&gt;&lt;p&gt;Body&lt;/p&gt;&lt;/article&gt;")
    }

    "prefer exact static status result template over static success template" in {
      Given("the prerequisites for prefer exact static status result template over static success template")
      val root = Files.createTempDirectory("cncf-web-template-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("approve-notice-aggregate__success.html"),
        "<article><h2>Static Success Alias</h2></article>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("approve-notice-aggregate__200.html"),
        """<article>
          |  <h2>${operation.label} Static 200 Exact</h2>
          |  <textus-result-view source="result.body"></textus-result-view>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("prefer exact static status result template over static success template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for prefer exact static status result template over static success template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200 Exact")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Static Success Alias")
      html should not include ("<textus-result-view")
    }

    "render operation form result through static status template convention" in {
      Given("the prerequisites for render operation form result through static status template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>${operation.label} Static 200</h2>
              |  <textus-property-list source="result"></textus-property-list>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through static status template convention is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through static status template convention holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("<textus-property-list")
    }

    "render operation form result through common static status template convention" in {
      Given("the prerequisites for render operation form result through common static status template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__200.html",
            """<article>
              |  <h2>Common Static 200</h2>
              |  <p>${operation.label}</p>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through common static status template convention is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through common static status template convention holds")
      html should include ("Common Static 200")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
    }

    "render form continuation through static status template convention" in {
      Given("the prerequisites for render form continuation through static status template convention")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"fetched_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" href="paging.href"></textus:table>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("continuation link is missing"))
      When("render form continuation through static status template convention is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render form continuation through static status template convention holds")
      page1 should include ("Matching notices")
      page1 should include ("Paging Notice 01")
      page1 should include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Paging Notice 21")
      page2 should include ("Page 2")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus:table")
    }

    "preserve page-local result template on form continuation" in {
      Given("the prerequisites for preserve page-local result template on form continuation")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Page Local Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"fetched_count":21}"""
      val root = Files.createTempDirectory("cncf-web-page-local-continuation-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("publicblogs__success.html"),
        """<article>
          |  <h2>Page Local Search</h2>
          |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" href="paging.href"></textus:table>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&textus.form.page=publicblogs"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("page-local continuation link is missing"))
      When("preserve page-local result template on form continuation is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve page-local result template on form continuation holds")
      page1 should include ("Page Local Search")
      page1 should include ("Page Local Notice 01")
      page2 should include ("Page Local Search")
      page2 should include ("Page Local Notice 21")
      page2 should not include ("Content-Type application/json")
    }

    "render form continuation with explicit total-count paging" in {
      Given("the prerequisites for render form continuation with explicit total-count paging")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Total Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"total_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus:table>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&paging.includeTotal=true"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20&amp;includeTotal=true)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("total-count continuation link is missing"))
      When("render form continuation with explicit total-count paging is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render form continuation with explicit total-count paging holds")
      page1 should include ("Matching notices")
      page1 should include ("Total Paging Notice 01")
      page1 should include ("includeTotal=true")
      page1 should not include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Total Paging Notice 21")
      page2 should include ("includeTotal=true")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus:table")
    }

    "render operation failure through exact static status template before error template" in {
      Given("the prerequisites for render operation failure through exact static status template before error template")
      val root = Files.createTempDirectory("cncf-web-template-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("approve-notice-aggregate__error.html"),
        "<article><h2>Static Error Alias</h2></article>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("approve-notice-aggregate__400.html"),
        """<article>
          |  <h2>${operation.label} Static 400 Exact</h2>
          |  <p>${error.body}</p>
          |  <p>${crud.origin.href}</p>
          |  <p>form id: ${form.id}</p>
          |  <p>form page: ${form.paging.page}</p>
          |  <form method="post" action="/form/notice-board/notice-aggregate/approve-notice-aggregate">
          |    <textus:hidden-context></textus:hidden-context>
          |    <input type="hidden" name="id" value="${form.id}">
          |  </form>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid approval", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation failure through exact static status template before error template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&paging.page=2&paging.pageSize=20&csrf=token-1"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation failure through exact static status template before error template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 400 Exact")
      html should include ("invalid approval")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("form id: notice_1")
      html should include ("form page: </p>")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include (s"type=\"hidden\" name=\"csrf\" value=\"${_test_csrf_token}\"")
      html should not include ("Static Error Alias")
      html should not include ("${crud.origin.href}")
      html should not include ("<textus:hidden-context")
      html should not include ("Submitted Values")
    }

    "render operation failure through common static error template when status template is absent" in {
      Given("the prerequisites for render operation failure through common static error template when status template is absent")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__error.html",
            """<article>
              |  <h2>Common Static Error</h2>
              |  <p>${crud.origin.href}</p>
              |  <p>${form.id}</p>
              |  <p>${form.paging.page}</p>
              |  <textus-error-panel source="result"></textus-error-panel>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.InternalServerError,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("aggregate service failed", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation failure through common static error template when status template is absent is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&paging.page=2"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation failure through common static error template when status template is absent holds")
      html should include ("Common Static Error")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("notice_1")
      html should include ("500")
      html should include ("aggregate service failed")
      html should include ("result.status")
      html should include ("result.body")
      html should not include ("${form.paging.page}")
      html should not include ("<textus-error-panel")
      html should not include ("Submitted Values")
    }

    "render Web HTML errors through app-specific static status template convention" in {
      Given("the prerequisites for render Web HTML errors through app-specific static status template convention")
      val root = Files.createTempDirectory("cncf-web-error-template-")
      val approot = root.resolve("notice-board")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("__404.html"),
        """<article>
          |  <h2>Notice Board Missing</h2>
          |  <p>${error.status}</p>
          |  <p>${error.path}</p>
          |  <p>${error.message}</p>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      When("render Web HTML errors through app-specific static status template convention is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render Web HTML errors through app-specific static status template convention holds")
      response.status.code shouldBe 404
      html should include ("Notice Board Missing")
      html should include ("404")
      html should include ("/web/notice-board/missing")
      html should include ("Static Form App not found")
    }

    "render Web HTML errors through global static error template convention" in {
      Given("the prerequisites for render Web HTML errors through global static error template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__error.html",
            """<article>
              |  <h2>Global Web Error</h2>
              |  <p>${component}</p>
              |  <textus-error-panel source="result"></textus-error-panel>
              |  <textus-property-list source="error"></textus-property-list>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      When("render Web HTML errors through global static error template convention is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render Web HTML errors through global static error template convention holds")
      response.status.code shouldBe 404
      html should include ("Global Web Error")
      html should include ("notice-board")
      html should include ("404")
      html should include ("/web/notice-board/missing")
      html should include ("result.status")
      html should include ("result.body")
      html should include ("error.path")
      html should not include ("<textus-error-panel")
    }

    "render non-production structured error debug YAML at the bottom of Web error pages" in {
      Given("the prerequisites for render non-production structured error debug YAML at the bottom of Web error pages")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Develop,
        component = Some("notice-board")
      )

      When("render non-production structured error debug YAML at the bottom of Web error pages is exercised")
      val html = _renderer.renderStructuredErrorPage(Some("notice-board"), error).body

      Then("the observable contract for render non-production structured error debug YAML at the bottom of Web error pages holds")
      html should include ("Request failed")
      html should include (detailcode.toString)
      html should include ("structured-error-debug")
      html should include ("Debug error details")
      html should include ("mode: develop")
      html should include ("path: /web/notice-board/missing")
    }

    "hide structured debug YAML in production Web error pages while keeping the Conclusion detail code" in {
      Given("the prerequisites for hide structured debug YAML in production Web error pages while keeping the Conclusion detail code")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Production,
        component = Some("notice-board")
      )

      When("hide structured debug YAML in production Web error pages while keeping the Conclusion detail code is exercised")
      val html = _renderer.renderStructuredErrorPage(Some("notice-board"), error).body

      Then("the observable contract for hide structured debug YAML in production Web error pages while keeping the Conclusion detail code holds")
      html should include (detailcode.toString)
      html should not include ("structured-error-debug")
      html should not include ("Debug error details")
      html should not include ("mode: production")
    }

    "include structured Conclusion detail code in response error records" in {
      Given("the prerequisites for include structured Conclusion detail code in response error records")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      When("include structured Conclusion detail code in response error records is exercised")
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Production,
        component = Some("notice-board")
      )

      Then("the observable contract for include structured Conclusion detail code in response error records holds")
      error.publicRecord.asMap.get("detailCode") shouldBe Some(detailcode)
      error.envelopeJson should include (s""""detailCode":${detailcode}""")
      error.envelopeJson should not include ("codeSource")
      error.envelopeJson should not include ("http.404")
    }

    "redisplay the operation form with submitted values when stayOnError is enabled" in {
      Given("the prerequisites for redisplay the operation form with submitted values when stayOnError is enabled")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            stayOnError = true
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid approval", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("redisplay the operation form with submitted values when stayOnError is enabled is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for redisplay the operation form with submitted values when stayOnError is enabled holds")
      html should include ("HTML form operation")
      html should include ("error.status")
      html should include ("400")
      html should include ("invalid approval")
      html should include ("value=\"notice_1\"")
    }

    "redisplay operation form validation errors before dispatching HTML submit" in {
      Given("the prerequisites for redisplay operation form validation errors before dispatching HTML submit")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
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
          _post_form_request("/form/notice-board/notice/post-secret-notice", "accessToken=abc"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("redisplay operation form validation errors before dispatching HTML submit is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay operation form validation errors before dispatching HTML submit holds")
      response.status.code shouldBe 400
      html should include ("HTML form operation")
      html should include ("Validation failed.")
      html should include ("data-textus-validation-summary=\"form\"")
      html should include ("data-textus-validation-message=\"error\"")
      html should include ("data-textus-validation-field=\"body\"")
      html should include ("data-textus-validation-code=\"required\"")
      html should include ("data-textus-issue-scope=\"field\"")
      html should include ("Notice body is required.")
      html should include ("is-invalid")
      html should include ("value=\"abc\"")
      html should not include ("DISPATCHED")
    }

    "merge schema-driven form fields with additional fields on submit" in {
      Given("the prerequisites for merge schema-driven form fields with additional fields on submit")
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("merge schema-driven form fields with additional fields on submit is exercised")
      val html = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1&fields=approved%3Dtrue"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).flatMap(_.as[String]).unsafeRunSync()

      Then("the observable contract for merge schema-driven form fields with additional fields on submit holds")
      html should include ("aggregate-updated:notice_1")
      dispatcher.forms.lastOption.map(_.getString("approved")) shouldBe Some(Some("true"))
    }

    "render textus result widgets with paging links" in {
      Given("the prerequisites for render textus result widgets with paging links")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "1",
            "paging.total" -> "25",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/test-id?page={page}&pageSize={pageSize}"
          )
        ),
        200,
        "application/json",
        """[{"title":"Hello","author":"Taro"},{"title":"World","author":"Hanako"}]"""
      )

      When("render textus result widgets with paging links is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render textus result widgets with paging links holds")
      html should include ("<table")
      html should include ("Hanako")
      html should not include ("<td>Hello</td>")
      html should include ("result.status")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=1&amp;pageSize=1")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=3&amp;pageSize=1")
      html should not include ("<textus:table")
      html should not include ("${result.contentType}")
    }

    }
  }
}
