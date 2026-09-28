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
final class StaticFormAppRendererEntityEditSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide entity edit, data, and form-definition contracts" which {
    "render component entity edit page contract" in {
      Given("a live entity collection and its canonical stored Entity ID")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      When("the edit page is rendered with its canonical route locator")
      val html = _renderer.renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the canonical route is used in form, value, and navigation contracts")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Edit")
      html should include ("Edit Notice")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentpath}/admin/entities/${entitypath}/${recordid}/update")
      html should include ("name=\"id\"")
      html should include (s"value=\"${recordid}\"")
      html should include ("Update")
      html should include ("Cancel")
      html should include (s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}")
    }

    "render component entity edit page with hidden form context" in {
      Given("a live entity collection, a canonical stored Entity ID, and hidden navigation context")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      When("the edit page is rendered")
      val html = _renderer.renderComponentAdminEntityEdit(
        subsystem,
        componentname,
        entitypath,
        recordid,
        values = Map(
          "crud.origin.href" -> s"/web/${componentpath}/admin/entities/${entitypath}?page=2&pageSize=20",
          "crud.success.href" -> s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}",
          "paging.page" -> "2",
          "paging.pageSize" -> "20",
          "search.status" -> "open",
          "etag" -> "v1",
          "id" -> "missing-id"
        )
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the hidden context remains in form fields and never leaks into visible links")
      html should include ("type=\"hidden\" name=\"crud.origin.href\"")
      html should include ("type=\"hidden\" name=\"crud.success.href\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"paging.pageSize\" value=\"20\"")
      html should include ("type=\"hidden\" name=\"search.status\" value=\"open\"")
      html should include ("type=\"hidden\" name=\"etag\" value=\"v1\"")
      html should include (s"value=\"${recordid}\"")
      html should not include ("value=\"missing-id\"")
      html should not include ("crud.origin.href=")
      html should not include ("search.status=")
    }

    "render component entity new page contract" in {
      Given("the prerequisites for render component entity new page contract")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      When("render component entity new page contract is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page contract holds")
      html should include (s"${component.name} Sales Order New")
      html should include ("New Sales Order")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentpath}/admin/entities/sales-order/create")
      html should include ("name=\"fields\"")
      html should include ("Use one name=value pair per line")
      html should include ("Create")
      html should include ("Cancel")
      html should include (s"/web/${componentpath}/admin/entities/sales-order")
    }

    "render component entity new page from CML schema descriptor without WebDescriptor" in {
      Given("the prerequisites for render component entity new page from CML schema descriptor without WebDescriptor")
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
              Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("body").label("Notice body").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("textarea"),
                  readonly = true,
                  placeholder = Some("Write the notice body."),
                  help = Some("Notice body shown on the board.")
                )
              ),
              Column(
                BaseContent.Builder("status").label("Publication status").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("select"),
                  values = Vector("draft", "published"),
                  required = Some(true)
                )
              )
            )))
          )
        )
      )
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      When("render component entity new page from CML schema descriptor without WebDescriptor is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, "notice_board", "notice").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from CML schema descriptor without WebDescriptor holds")
      html should include ("name=\"id\"")
      html should include ("name=\"title\"")
      html should include ("name=\"body\"")
      html should include ("name=\"status\"")
      html should include ("Notice body")
      html should include ("Publication status")
      html should include ("<textarea")
      html should include ("<select")
      html should include ("<option value=\"draft\"")
      html should include ("<option value=\"published\"")
      html should include ("readonly")
      html should include ("required")
      html should include ("placeholder=\"Write the notice body.\"")
      html should include ("Notice body shown on the board.")
    }

    "render component entity new page from generated companion schema" in {
      Given("the prerequisites for render component entity new page from generated companion schema")
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
      val bootstrapped = new ComponentFactory().bootstrap(component)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(bootstrapped))

      When("render component entity new page from generated companion schema is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, "generated_schema_component", "order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from generated companion schema holds")
      html should include ("name=\"id\"")
      html should include ("name=\"name\"")
      html should include ("name=\"status\"")
      html should include ("Order status")
      html should include ("<select")
      html should include ("<option value=\"submitted\"")
      html should include ("CML generated status hint.")
      html should include ("Use one name=value pair per line")
    }

    "render component entity new page from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for render component entity new page from merged Schema and WebDescriptor controls")
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()

      When("render component entity new page from merged Schema and WebDescriptor controls is exercised")
      val html = _renderer.renderComponentAdminEntityNew(
        subsystem,
        "notice_board",
        "notice",
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from merged Schema and WebDescriptor controls holds")
      html should include ("name=\"id\"")
      html should include ("name=\"body\"")
      html should include ("name=\"status\"")
      html should include ("Notice body")
      html should include ("<textarea")
      html should include ("placeholder=\"Descriptor body placeholder.\"")
      html should include ("Descriptor body help.")
      html should include ("<select")
      html should include ("<option value=\"archived\"")
    }

    "expose Schema labels in admin entity Form API and HTML" in {
      Given("the prerequisites for expose Schema labels in admin entity Form API and HTML")
      val schema = Schema(Vector(
        Column(
          BaseContent.Builder("senderName").label("Sender").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            placeholder = Some("Your name"),
            help = Some("Name displayed as the poster."),
            validation = WebValidationHints(minLength = Some(1))
          )
        ),
        Column(
          BaseContent.Builder("body").label("Body").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            controlType = Some("textarea"),
            placeholder = Some("Notice body"),
            help = Some("Main notice text."),
            validation = WebValidationHints(minLength = Some(1))
          )
        )
      ))
      val subsystem = _management_console_fixture_subsystem(schema = schema)

      val definition = parse(_renderer
        .renderComponentAdminEntityFormDefinition(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      When("expose Schema labels in admin entity Form API and HTML is exercised")
      val fields = definition.hcursor.downField("fields")
      Then("the observable contract for expose Schema labels in admin entity Form API and HTML holds")
      fields.downN(0).downField("label").as[String].toOption shouldBe Some("Sender")
      fields.downN(0).downField("placeholder").as[String].toOption shouldBe Some("Your name")
      fields.downN(0).downField("validation").downField("minLength").as[Int].toOption shouldBe Some(1)
      fields.downN(1).downField("label").as[String].toOption shouldBe Some("Body")
      fields.downN(1).downField("type").as[String].toOption shouldBe Some("textarea")

      val html = _renderer
        .renderComponentAdminEntityNew(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      html should include ("""<label class="form-label" for="new-field-sender-name">Sender</label>""")
      html should include ("""<label class="form-label" for="new-field-body">Body</label>""")
      html should include ("""placeholder="Your name"""")
      html should include ("""minlength="1"""")
    }

    "redisplay admin entity create validation errors before dispatching" in {
      Given("the prerequisites for redisplay admin entity create validation errors before dispatching")
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
              Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("status").label("Publication status").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("select"),
                  values = Vector("draft", "published"),
                  required = Some(true)
                )
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
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "id=notice_1&title=hello&status=archived"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("redisplay admin entity create validation errors before dispatching is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay admin entity create validation errors before dispatching holds")
      response.status.code shouldBe 400
      html should include ("New Notice")
      html should include ("Validation failed.")
      html should include ("archived is not an allowed value for Publication status.")
      html should include ("is-invalid")
      html should include ("value=\"notice_1\"")
      html should include ("value=\"hello\"")
    }

    "validate admin entity create and update POST values against Schema hints" in {
      Given("the prerequisites for validate admin entity create and update POST values against Schema hints")
      val schema = Schema(Vector(
        Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(
          BaseContent.Builder("title").label("Title").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(validation = WebValidationHints(minLength = Some(3)))
        ),
        Column(
          BaseContent.Builder("author").label("Author").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(required = Some(true))
        )
      ))
      val subsystem = _management_console_fixture_subsystem(schema = schema)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head.id.value

      val createresponse = server
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "title=Hi&author="),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("validate admin entity create and update POST values against Schema hints is exercised")
      val createhtml = createresponse.as[String].unsafeRunSync()

      Then("the observable contract for validate admin entity create and update POST values against Schema hints holds")
      createresponse.status.code shouldBe 400
      createhtml should include ("Validation failed.")
      createhtml should include ("admin-feedback")
      createhtml should include ("Title must be at least 3 characters.")
      createhtml should include ("Author is required.")
      createhtml should include ("is-invalid")

      val updateresponse = server
        ._submit_component_admin_entity_update(
          _post_form_request(s"/form/notice-board/admin/entities/notice/${recordid}/update", "title=No&author="),
          "notice-board",
          "notice",
          recordid
        )
        .unsafeRunSync()
      val updatehtml = updateresponse.as[String].unsafeRunSync()

      updateresponse.status.code shouldBe 400
      updatehtml should include ("Validation failed.")
      updatehtml should include ("admin-feedback")
      updatehtml should include ("Title must be at least 3 characters.")
      updatehtml should include ("Author is required.")
      updatehtml should include ("is-invalid")
    }

    "render component entity update submission result contract" in {
      Given("the prerequisites for render component entity update submission result contract")
      val html = _renderer.renderComponentAdminEntityUpdateResult(
        "admin",
        "sales-order",
        "sales-order-1",
        Map("status" -> "confirmed")
      ).body

      When("the observable result for render component entity update submission result contract is inspected")
      locally {
        Then("the observable contract for render component entity update submission result contract holds")
        html should include ("admin Sales Order Update Result")
        html should include ("Update submitted")
        html should include ("nav nav-pills")
        html should include ("class=\"card admin-card")
        html should include ("table table-sm table-hover align-middle mb-0")
        html should include ("Entity update execution is not enabled in this baseline")
        html should include ("result.status")
        html should include ("result.ok")
        html should include ("result.body")
        html should include ("status")
        html should include ("confirmed")
        html should include ("/web/admin/admin/entities/sales-order/sales-order-1")
        html should include ("/web/admin/admin/entities/sales-order/sales-order-1/edit")
      }
    }

    "render component entity create submission result contract" in {
      Given("the prerequisites for render component entity create submission result contract")
      val html = _renderer.renderComponentAdminEntityCreateResult(
        "admin",
        "sales-order",
        Map("status" -> "draft")
      ).body

      When("the observable result for render component entity create submission result contract is inspected")
      locally {
        Then("the observable contract for render component entity create submission result contract holds")
        html should include ("admin Sales Order Create Result")
        html should include ("Create submitted")
        html should include ("nav nav-pills")
        html should include ("class=\"card admin-card")
        html should include ("table table-sm table-hover align-middle mb-0")
        html should include ("Entity create execution is not enabled in this baseline")
        html should include ("result.status")
        html should include ("result.ok")
        html should include ("result.body")
        html should include ("status")
        html should include ("draft")
        html should include ("/web/admin/admin/entities/sales-order")
        html should include ("/web/admin/admin/entities/sales-order/new")
      }
    }

    "render component entity edit page from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for render component entity edit page from merged Schema and WebDescriptor controls")
      val subsystem = _management_console_fixture_subsystem()
      val recordid = _notice_fixture_component(subsystem).
        entitySpace.
        entity[NoticeEntity]("notice").
        storage.
        storeRealm.
        values.
        head.
        id.
        value
      val descriptor = WebDescriptor(admin = Map(
        "notice-board.entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
          WebDescriptor.AdminField("id", WebDescriptor.FormControl(readonly = true)),
          WebDescriptor.AdminField(
            "title",
            WebDescriptor.FormControl(
              placeholder = Some("Descriptor title placeholder."),
              help = Some("Descriptor title help.")
            )
          ),
          WebDescriptor.AdminField("author", WebDescriptor.FormControl(hidden = true))
        ))
      ))

      When("render component entity edit page from merged Schema and WebDescriptor controls is exercised")
      val html = _renderer.renderComponentAdminEntityEdit(
        subsystem,
        "notice_board",
        "notice",
        recordid,
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for render component entity edit page from merged Schema and WebDescriptor controls holds")
      html should include ("id=\"field-id\"")
      html should include ("readonly")
      html should include ("value=\"board update\"")
      html should include ("placeholder=\"Descriptor title placeholder.\"")
      html should include ("Descriptor title help.")
      html should include ("type=\"hidden\" id=\"field-author\" name=\"author\" value=\"alice\"")
    }

    "render component data administration page" in {
      Given("the prerequisites for render component data administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component data administration page is exercised")
      val html = _renderer.renderComponentAdminData(subsystem, component.name).map(_.body).getOrElse(fail("component data admin is missing"))

      Then("the observable contract for render component data administration page holds")
      html should include (s"${component.name} Data Administration")
      html should include ("Data record management")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/descriptor")
      html should include ("Data record management")
      html should include ("Implemented baseline")
      html should include ("Delete flows")
    }

    "render component data pages from a live DataStore fixture" in {
      Given("the prerequisites for render component data pages from a live DataStore fixture")
      val fixture = _data_fixture()
      When("the observable result for render component data pages from a live DataStore fixture is inspected")
      locally {
        Then("the observable contract for render component data pages from a live DataStore fixture holds")
        _with_global_runtime(fixture.runtime) {
        val html = _renderer.renderComponentAdminDataType(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data type admin is missing"))
        val firstpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data first page admin is missing"))
        val secondpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data second page admin is missing"))
        val totalpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
          WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
        ).map(_.body).getOrElse(fail("component data total page admin is missing"))
        val unsupportedfixture = _data_fixture(TotalCountCapability.Unsupported)
        val unsupportedtotalpage = _with_global_runtime(unsupportedfixture.runtime) {
          _renderer.renderComponentAdminDataType(
            unsupportedfixture.subsystem,
            "notice_board",
            "audit",
            StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
            WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
          ).map(_.body).getOrElse(fail("component data unsupported total page admin is missing"))
        }
        val detail = _renderer.renderComponentAdminDataDetail(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data detail admin is missing"))
        val edit = _renderer.renderComponentAdminDataEdit(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = _renderer.renderComponentAdminDataNew(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data new admin is missing"))

        html should include ("audit_1")
        html should include ("<th>id</th><th>action</th><th>actor</th><th>Actions</th>")
        html should include ("class=\"card admin-card")
        html should include ("class=\"table table-sm table-hover align-middle\"")
        html should include ("class=\"btn-group btn-group-sm\"")
        html should include ("created")
        html should include ("alice")
        html should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1")
        firstpage should include ("Page 1")
        firstpage should include ("page=2&amp;pageSize=1")
        secondpage should include ("Page 2")
        secondpage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/org-goldenport-cncf-test-notice-board/admin/data/audit?page=3&amp;pageSize=1\">Next")
        totalpage should include ("total 2")
        totalpage should include ("includeTotal=true")
        unsupportedtotalpage should include ("alert-warning")
        unsupportedtotalpage should include ("admin-feedback")
        unsupportedtotalpage should include ("total count is not available for data.audit")
        detail should include ("created")
        detail should include ("alice")
        detail should include ("class=\"card admin-card")
        detail should include ("class=\"btn btn-primary\"")
        detail should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        detail should include ("/web/org-goldenport-cncf-test-notice-board/admin")
        edit should include ("name=\"action\"")
        edit should include ("value=\"created\"")
        edit should include ("class=\"admin-form\"")
        edit should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1")
        edit should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        newly should include ("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/create")
        newly should include ("class=\"admin-form\"")
        newly should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        newly should include ("/web/org-goldenport-cncf-test-notice-board/admin/data")
      }
      }
    }

    "render admin CRUD forms from WebDescriptor field controls" in {
      Given("the prerequisites for render admin CRUD forms from WebDescriptor field controls")
      val fixture = _data_fixture()
      When("render admin CRUD forms from WebDescriptor field controls is exercised")
      val descriptor = _data_schema_web_descriptor()
      Then("the observable contract for render admin CRUD forms from WebDescriptor field controls holds")
      _with_global_runtime(fixture.runtime) {
        val edit = _renderer.renderComponentAdminDataEdit(
          fixture.subsystem,
          "notice_board",
          "audit",
          "audit_1",
          webDescriptor = descriptor
        ).map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = _renderer.renderComponentAdminDataNew(
          fixture.subsystem,
          "notice_board",
          "audit",
          webDescriptor = descriptor
        ).map(_.body).getOrElse(fail("component data new admin is missing"))

        edit should include ("name=\"action\"")
        edit should include ("<select")
        edit should include ("<option value=\"created\" selected>")
        edit should include ("name=\"actor\"")
        edit should include ("required")
        edit should include ("placeholder=\"Descriptor actor placeholder.\"")
        edit should include ("Descriptor actor help.")
        edit should include ("name=\"note\"")
        edit should include ("<textarea")
        newly should include ("name=\"note\"")
        newly should include ("<textarea")
      }
    }

    "apply component data update/create form POST into the DataStore fixture" in {
      Given("the prerequisites for apply component data update/create form POST into the DataStore fixture")
      val fixture = _data_fixture()
      When("the observable result for apply component data update/create form POST into the DataStore fixture is inspected")
      locally {
        Then("the observable contract for apply component data update/create form POST into the DataStore fixture holds")
        _with_global_runtime(fixture.runtime) {
        val engine = new HttpExecutionEngine(fixture.subsystem)
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val updatereq = _post_form_request(
          "/form/notice-board/admin/data/audit/audit_1/update",
          "action=updated&actor=bob"
        )
        val updatehtml = server
          ._submit_component_admin_data_update(updatereq, "notice-board", "audit", "audit_1")
          .flatMap(_.as[String])
          .unsafeRunSync()

        updatehtml should include ("Data record was applied")
        updatehtml should include ("Applied</th><td>true")
        _load_data_record(fixture.datastorespace, "audit", "audit_1").getString("action") shouldBe Some("updated")
        _load_data_record(fixture.datastorespace, "audit", "audit_1").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/update")

        val createreq = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_2%0Aaction%3Dcreated%0Aactor%3Dbob"
        )
        val createhtml = server
          ._submit_component_admin_data_create(createreq, "notice-board", "audit")
          .flatMap(_.as[String])
          .unsafeRunSync()

        createhtml should include ("Data record was applied")
        createhtml should include ("Applied</th><td>true")
        _load_data_record(fixture.datastorespace, "audit", "audit_2").getString("action") shouldBe Some("created")
        _load_data_record(fixture.datastorespace, "audit", "audit_2").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "redirect component data create by admin form descriptor transition" in {
      Given("the prerequisites for redirect component data create by admin form descriptor transition")
      val fixture = _data_fixture()
      When("the observable result for redirect component data create by admin form descriptor transition is inspected")
      locally {
        Then("the observable contract for redirect component data create by admin form descriptor transition holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = WebDescriptor(
          form = Map(
            "notice-board.admin.data.audit.create" -> WebDescriptor.Form(
              successRedirect = Some("/web/${component}/admin/${surface}/${collection}/${result.id}")
            )
          )
        )
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_3%0Aaction%3Dcreated%0Aactor%3Dbob"
        )

        val response = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .unsafeRunSync()

        response.status.code shouldBe 303
        response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
          Some("/web/notice-board/admin/data/audit/audit_3")
        _load_data_record(fixture.datastorespace, "audit", "audit_3").getString("action") shouldBe Some("created")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "redisplay component data create form with submitted fields when admin stayOnError is enabled" in {
      Given("the prerequisites for redisplay component data create form with submitted fields when admin stayOnError is enabled")
      val fixture = _data_fixture()
      When("the observable result for redisplay component data create form with submitted fields when admin stayOnError is enabled is inspected")
      locally {
        Then("the observable contract for redisplay component data create form with submitted fields when admin stayOnError is enabled holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = WebDescriptor(
          form = Map(
            "notice-board.admin.data.audit.create" -> WebDescriptor.Form(stayOnError = true)
          )
        )
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.BadRequest,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("invalid data create", StandardCharsets.UTF_8)
          )
        )
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_bad%0Aaction%3Dcreated%0Aactor%3Dbob"
        )

        val html = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .flatMap(_.as[String])
          .unsafeRunSync()

        html should include ("New Audit")
        html should include ("error.status")
        html should include ("400")
        html should include ("invalid data create")
        html should include ("name=\"id\"")
        html should include ("value=\"audit_bad\"")
        html should include ("name=\"action\"")
        html should include ("value=\"created\"")
        html should include ("name=\"actor\"")
        html should include ("value=\"bob\"")
      }
      }
    }

    "redisplay component data create form with descriptor validation errors before dispatch" in {
      Given("the prerequisites for redisplay component data create form with descriptor validation errors before dispatch")
      val fixture = _data_fixture()
      When("the observable result for redisplay component data create form with descriptor validation errors before dispatch is inspected")
      locally {
        Then("the observable contract for redisplay component data create form with descriptor validation errors before dispatch holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = _data_schema_web_descriptor()
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "id=audit_invalid&action=created&actor="
        )

        val response = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .unsafeRunSync()
        val html = response.as[String].unsafeRunSync()

        response.status.code shouldBe 400
        html should include ("New Audit")
        html should include ("Validation failed.")
        html should include ("admin-feedback")
        html should include ("actor is required.")
        html should include ("Descriptor actor help.")
        html should include ("is-invalid")
        html should include ("audit_invalid")
        dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "extract structured result metadata for form redirect templates" in {
      Given("supported structured and scalar result body representations")
      val structuredidbody = """{"id":"notice_1"}"""

      When("template metadata is extracted from the result body")
      val structuredidvalues =
        FormResultMetadata.fromBody(structuredidbody).toTemplateValues

      Then("the supported representations produce canonical template values")
      structuredidvalues shouldBe Map("result.id" -> "notice_1")
      FormResultMetadata.fromBody("""{"id":"urn:textus:image:abc"}""").toTemplateValues shouldBe Map("result.id" -> "urn:textus:image:abc")
      FormResultMetadata.fromBody("""{"result":{"id":"notice_2"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_2")
      FormResultMetadata.fromBody("""{"item":{"id":"notice_3"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_3")
      FormResultMetadata.fromBody("created:notice_4").toTemplateValues shouldBe Map("result.id" -> "notice_4")
      FormResultMetadata.fromBody("created:notice_5\ndebug:\n  calltree: ...").toTemplateValues shouldBe Map("result.id" -> "notice_5")
      FormResultMetadata.fromBody("""{"message":"created"}""").toTemplateValues shouldBe Map("result.message" -> "created")
      FormResultMetadata.fromBody("""{"result":{"outcome":"created","message":"Notice created"}}""").toTemplateValues shouldBe Map(
        "result.outcome" -> "created",
        "result.message" -> "Notice created"
      )
      FormResultMetadata.fromBody(
        """{"data":{"action_status":"seeded","resolver_status":"resolved","added_candidate_count":2,"suggested_field_count":4,"current":{"information_id":"single-global-entity-information-1"}}}"""
      ).toTemplateValues should contain allOf (
        "result.id" -> "single-global-entity-information-1",
        "result.outcome" -> "seeded",
        "result.resolverStatus" -> "resolved",
        "result.candidateCount" -> "2",
        "result.suggestedFieldCount" -> "4"
      )
      FormResultMetadata.fromBody("cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa"
      )
      FormResultMetadata.fromBody("""{"jobId":"cncf-job-job-1"}""").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-1"
      )
      FormResultMetadata.fromBody("""{"jobId":"cncf-job-job-2","jobStatus":"running"}""").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-2",
        "result.job.status" -> "running"
      )
      FormResultMetadata.fromBody(
        """{"actions":[{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      ).toTemplateValues should contain allOf (
        "result.actions.count" -> "1",
        "result.action.primary.name" -> "detail",
        "result.action.primary.label" -> "Open detail",
        "result.action.primary.href" -> "/web/notice-board/admin/entities/notice/notice_1",
        "result.action.primary.method" -> "GET",
        "result.action.detail.href" -> "/web/notice-board/admin/entities/notice/notice_1",
        "result.action.0.href" -> "/web/notice-board/admin/entities/notice/notice_1"
      )
      val jsonaction = parse("""{"name":"approve","label":"Approve","href":"/form/approve","method":"POST"}""")
        .toOption
        .flatMap(FormResultMetadata.Action.fromJson)
        .getOrElse(fail("action JSON should parse"))
      jsonaction.toTemplateValues("result.action.approve") should contain allOf (
        "result.action.approve.name" -> "approve",
        "result.action.approve.label" -> "Approve",
        "result.action.approve.href" -> "/form/approve",
        "result.action.approve.method" -> "POST"
      )
    }

    "project form metadata from explicit execution transport state" must _aes05b {
      Given("responses carrying direct, accepted-job, job-result, or no execution-result headers")
      val directjsonresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"running","message":"direct"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "direct",
        "X-Textus-Job-Id" -> "stale-header-job"
      ))
      val directscalarresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-body-job"
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "direct",
        "X-Textus-Job-Id" -> "stale-header-job"
      ))
      val acceptedresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "accepted-job",
        "X-Textus-Job-Id" -> "cncf-job-authoritative"
      ))
      val jobresultresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"completed"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "job-result",
        "X-Textus-Job-Id" -> "cncf-job-authoritative-result"
      ))
      val acceptedwithoutidresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-without-header","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "AcCePtEd-JoB"
      ))
      val jobresultwithoutidresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-without-header","jobStatus":"completed"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "JoB-ReSuLt"
      ))
      val duplicateheaderresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-duplicate","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "accepted-job",
        "X-Textus-Execution-Result" -> "direct",
        "x-textus-job-id" -> "cncf-job-first",
        "X-Textus-Job-Id" -> "cncf-job-second"
      ))
      val legacyresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-legacy"
      )
      val unknownresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-unknown"
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "future-result"
      ))

      When("form metadata is extracted from each response")
      val directjsonmetadata = FormResultMetadata.fromHttpResponse(directjsonresponse)
      val directscalarmetadata = FormResultMetadata.fromHttpResponse(directscalarresponse)
      val acceptedmetadata = FormResultMetadata.fromHttpResponse(acceptedresponse)
      val jobresultmetadata = FormResultMetadata.fromHttpResponse(jobresultresponse)
      val acceptedwithoutidmetadata = FormResultMetadata.fromHttpResponse(acceptedwithoutidresponse)
      val jobresultwithoutidmetadata = FormResultMetadata.fromHttpResponse(jobresultwithoutidresponse)
      val duplicateheadermetadata = FormResultMetadata.fromHttpResponse(duplicateheaderresponse)
      val legacymetadata = FormResultMetadata.fromHttpResponse(legacyresponse)
      val unknownmetadata = FormResultMetadata.fromHttpResponse(unknownresponse)

      Then("direct responses suppress body and stale-header Job IDs")
      directjsonmetadata.jobId shouldBe None
      directjsonmetadata.jobStatus shouldBe None
      directscalarmetadata.jobId shouldBe None
      directscalarmetadata.jobStatus shouldBe None

      And("accepted-job responses use the authoritative header Job ID and status")
      acceptedmetadata.jobId shouldBe Some("cncf-job-authoritative")
      acceptedmetadata.jobStatus shouldBe Some("accepted")

      And("job-result responses use the authoritative header Job ID and retain its result status")
      jobresultmetadata.jobId shouldBe Some("cncf-job-authoritative-result")
      jobresultmetadata.jobStatus shouldBe Some("completed")

      And("recognized results use case-insensitive headers and values without body fallback")
      acceptedwithoutidmetadata.jobId shouldBe None
      acceptedwithoutidmetadata.jobStatus shouldBe None
      jobresultwithoutidmetadata.jobId shouldBe None
      jobresultwithoutidmetadata.jobStatus shouldBe Some("completed")

      And("duplicate headers use the first matching field exposed by HttpResponse")
      duplicateheadermetadata.jobId shouldBe Some("cncf-job-first")
      duplicateheadermetadata.jobStatus shouldBe Some("accepted")

      And("responses without execution-result state preserve legacy body inference")
      legacymetadata.jobId shouldBe Some("cncf-job-legacy")

      And("unknown execution-result values preserve legacy body inference")
      unknownmetadata.jobId shouldBe Some("cncf-job-unknown")
    }

    }
  }
}
