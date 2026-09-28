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
final class StaticFormAppRendererViewAggregateSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide view, aggregate, and operation-action contracts" which {
    "render component view administration page" in {
      Given("the prerequisites for render component view administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component view administration page is exercised")
      val html = _renderer.renderComponentAdminViews(subsystem, component.name).map(_.body).getOrElse(fail("component view admin is missing"))

      Then("the observable contract for render component view administration page holds")
      html should include (s"${component.name} View Administration")
      html should include ("View read")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("view definitions")
    }

    "render component view read page from a live ViewSpace fixture" in {
      Given("the prerequisites for render component view read page from a live ViewSpace fixture")
      val subsystem = _view_fixture_subsystem()

      When("render component view read page from a live ViewSpace fixture is exercised")
      val html = _renderer.renderComponentAdminViewDetail(subsystem, "notice_board", "notice_view").map(_.body).getOrElse(fail("component view detail admin is missing"))

      Then("the observable contract for render component view read page from a live ViewSpace fixture holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice View View")
      html should include ("Notice View metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_view")
      html should include ("notice")
      html should include ("recent")
      html should include ("Read result")
      html should include ("notice summary")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view/notice%20summary")
      html should include ("Result pages")
      html should not include ("Edit")
      html should not include ("New")
      html should not include ("Create")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views")
    }

    "render component view instance detail page through context-aware read" in {
      Given("the prerequisites for render component view instance detail page through context-aware read")
      val subsystem = _view_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component view instance detail page through context-aware read is exercised")
      val html = _renderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", id).map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      Then("the observable contract for render component view instance detail page through context-aware read holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice View View Detail")
      html should include ("class=\"card admin-card")
      html should include (id)
      html should include ("label")
      html should include ("title")
      html should include ("notice detail notice_1")
      html should not include ("Edit")
      html should not include ("Update")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view")
    }

    "reject a foreign canonical instance locator without rendering Admin reads or aggregate operations" in {
      Given("view and aggregate fixtures with a local short ID colliding with a foreign canonical ID")
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,entropy = "notice_1").value
      val viewsubsystem = _view_fixture_subsystem()
      val aggregatesubsystem = _aggregate_fixture_subsystem()

      When("the foreign canonical locator is rendered through view and aggregate detail routes")
      val viewhtml = _renderer.renderComponentAdminViewInstanceDetail(
        viewsubsystem,
        "notice_board",
        "notice_view",
        foreignid
      ).map(_.body).getOrElse(fail("component view instance detail admin is missing"))
      val aggregatehtml = _renderer.renderComponentAdminAggregateInstanceDetail(
        aggregatesubsystem,
        "notice_board",
        "notice_aggregate",
        foreignid
      ).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("no Admin read result or instance operation is rendered for the foreign locator")
      viewhtml should include (s"No canonical Entity ID is available for ${foreignid}.")
      viewhtml should not include ("notice detail notice_1")
      aggregatehtml should include (s"No canonical Entity ID is available for ${foreignid}.")
      aggregatehtml should include (s"No instance operations are available for unresolved id ${foreignid}.")
      aggregatehtml should not include ("aggregate id prefilled")
      aggregatehtml should not include (s"id=${foreignid}")
    }

    "reject foreign canonical IDs at direct Admin view and aggregate reads" in {
      Given("direct Admin surfaces with a local entropy collision in another collection")
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,entropy = "notice_1").value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem())
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem())

      When("the foreign canonical ID is submitted to direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> foreignid)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> foreignid)
      ))

      Then("both boundaries reject it before view or aggregate resolution")
      viewresponse.code shouldBe 400
      aggregateresponse.code shouldBe 400
    }

    "reject direct Admin ID reads whose declared backing collection is absent" in {
      Given("view and aggregate definitions that name a missing backing entity")
      val id = _notice_entity_id_from_shortid("notice_1").value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem(backingentityname = "missing"))
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem(backingentityname = "missing"))

      When("a canonical local ID is submitted to the direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> id)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> id)
      ))

      Then("neither surface infers a same-base collection from its surface name")
      viewresponse.code shouldBe 400
      aggregateresponse.code shouldBe 400
    }

    "reject direct Admin ID reads whose declared backing collection is ambiguous" in {
      Given("view and aggregate definitions whose entity name has two exact runtime collections")
      val id = _notice_entity_id_from_shortid("notice_1").value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem(ambiguousbacking = true))
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem(ambiguousbacking = true))

      When("a canonical local ID is submitted to the direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> id)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> id)
      ))

      Then("both boundaries expose the ambiguous server configuration rather than choosing a collection")
      viewresponse.code shouldBe 500
      aggregateresponse.code shouldBe 500
    }

    "render component view instance detail with descriptor field schema" in {
      Given("the prerequisites for render component view instance detail with descriptor field schema")
      val subsystem = _view_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "view.notice-view" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("value"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component view instance detail with descriptor field schema is exercised")
      val html = _renderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", id, descriptor).map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      Then("the observable contract for render component view instance detail with descriptor field schema holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
    }

    "render component aggregate administration page" in {
      Given("the prerequisites for render component aggregate administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component aggregate administration page is exercised")
      val html = _renderer.renderComponentAdminAggregates(subsystem, component.name).map(_.body).getOrElse(fail("component aggregate admin is missing"))

      Then("the observable contract for render component aggregate administration page holds")
      html should include (s"${component.name} Aggregate Administration")
      html should include ("Aggregate CRUD")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("aggregate definitions")
    }

    "render component aggregate read page from a live AggregateSpace fixture" in {
      Given("the prerequisites for render component aggregate read page from a live AggregateSpace fixture")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate read page from a live AggregateSpace fixture is exercised")
      val html = _renderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate").map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      Then("the observable contract for render component aggregate read page from a live AggregateSpace fixture holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Aggregate Aggregate")
      html should include ("Notice Aggregate metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_aggregate")
      html should include ("notice")
      html should include ("notice:notice")
      html should include ("Read result")
      html should include ("<th>id</th><th>Short ID</th><th>label</th><th>status</th><th>Actions</th>")
      html should include ("notice_1")
      html should include (s"/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/${id}")
      html should include ("Result pages")
      html should include ("Operations")
      html should include ("create-notice-aggregate")
      html should include ("approve-notice-aggregate")
      html should include ("read-notice-aggregate")
      html should include ("Create operations construct a new aggregate root")
      html should include ("Update and command operations mutate aggregate state")
      html should include ("Create aggregate")
      html should include ("Read aggregate")
      html should include ("Run update command")
      html should include ("btn-warning")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/create-notice-aggregate")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate")
      html should not include ("approve-notice-aggregate__success.html")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/aggregates")
    }

    "render component aggregate list with descriptor field columns" in {
      Given("the prerequisites for render component aggregate list with descriptor field columns")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )

      When("render component aggregate list with descriptor field columns is exercised")
      val html = _renderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate", webDescriptor = descriptor).map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      Then("the observable contract for render component aggregate list with descriptor field columns holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>note</th>")
      html should include (s"/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/${id}")
    }

    "render component aggregate instance detail page through context-aware read" in {
      Given("the prerequisites for render component aggregate instance detail page through context-aware read")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate instance detail page through context-aware read is exercised")
      val html = _renderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", id).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("the observable contract for render component aggregate instance detail page through context-aware read holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Aggregate Aggregate Detail")
      html should include ("class=\"card admin-card")
      html should include (id)
      html should include ("label")
      html should include ("title")
      html should include ("notice aggregate")
      html should include ("Instance operations")
      html should include ("aggregate id prefilled")
      html should include ("Read aggregate")
      html should include ("Run update command")
      html should not include ("Create aggregate")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate?")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/read-notice-aggregate?")
      html should include (s"id=${id}")
      html should include ("crud.success.href=")
      html should not include ("textus.admin.principalId=system")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate")
    }

    "render component aggregate instance detail with descriptor field schema" in {
      Given("the prerequisites for render component aggregate instance detail with descriptor field schema")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("value"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate instance detail with descriptor field schema is exercised")
      val html = _renderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", id, descriptor).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("the observable contract for render component aggregate instance detail with descriptor field schema holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
    }

    "execute admin read/list operations for entity data view and aggregate surfaces" in {
      Given("the prerequisites for execute admin read/list operations for entity data view and aggregate surfaces")
      val entitysubsystem = _management_console_fixture_subsystem()
      val entityengine = new HttpExecutionEngine(entitysubsystem)
      val entitycollection = _notice_fixture_component(entitysubsystem).entitySpace.entity[NoticeEntity]("notice")
      val entityid = entitycollection.storage.storeRealm.values.head.id.value

      val entitylist = entityengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/entity/list", form = Record.data("component" -> _notice_board_component_name, "entity" -> "notice")))
      When("execute admin read/list operations for entity data view and aggregate surfaces is exercised")
      val entityread = entityengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/entity/read", form = Record.data("component" -> _notice_board_component_name, "entity" -> "notice", "id" -> entityid)))

      Then("the observable contract for execute admin read/list operations for entity data view and aggregate surfaces holds")
      entitylist.code shouldBe 200
      entitylist.getString.getOrElse("") should include (entityid)
      entityread.code shouldBe 200
      entityread.getString.getOrElse("") should include ("title=board update")
      val entitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice")
      entitylistrecord.getString("kind") shouldBe Some("entity.list")
      entitylistrecord.getAny("ids").map(_.toString).getOrElse("") should include (entityid)
      entitylistrecord.getAny("items").map(_.toString).getOrElse("") should include (entityid)
      entitylistrecord.getAny("items").map(_.toString).getOrElse("") should include ("board update")
      entitylistrecord.getInt("page") shouldBe Some(1)
      entitylistrecord.getInt("pageSize") shouldBe Some(20)
      entitylistrecord.getBoolean("hasNext") shouldBe Some(false)
      entitylistrecord.getInt("total") shouldBe None
      entitylistrecord.getBoolean("totalAvailable") shouldBe Some(false)
      val ignoredtotalentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true")
      ignoredtotalentitylistrecord.getInt("total") shouldBe None
      ignoredtotalentitylistrecord.getBoolean("totalAvailable") shouldBe Some(false)
      val totalentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalentitylistrecord.getInt("total") shouldBe Some(2)
      totalentitylistrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val requiredentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "required")
      requiredentitylistrecord.getInt("total") shouldBe Some(2)
      val pagedentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "2", "pageSize" -> "5")
      pagedentitylistrecord.getInt("page") shouldBe Some(2)
      pagedentitylistrecord.getInt("pageSize") shouldBe Some(5)
      val firstentitypage = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "pageSize" -> "1")
      val firstentitypageids = firstentitypage.getAny("ids").map(_.toString).getOrElse("")
      firstentitypageids should not be empty
      firstentitypage.getBoolean("hasNext") shouldBe Some(true)
      val secondentitypage = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "2", "pageSize" -> "1")
      val secondentitypageids = secondentitypage.getAny("ids").map(_.toString).getOrElse("")
      secondentitypageids should not be empty
      secondentitypageids should not be firstentitypageids
      secondentitypage.getBoolean("hasNext") shouldBe Some(false)
      val entityreadrecord = _admin_record_response(entitysubsystem, "entity", "read", "component" -> _notice_board_component_name, "entity" -> "notice", "id" -> entityid)
      entityreadrecord.getString("kind") shouldBe Some("entity.read")
      entityreadrecord.getString("label") shouldBe Some("board update")
      entityreadrecord.getAny("item").map(_.toString).getOrElse("") should include (entityid)
      entityreadrecord.getString("fields").getOrElse("") should include ("title=board update")
      _admin_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "0") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"invalid page should fail: ${other}")
      }

      val datafixture = _data_fixture()
      _with_global_runtime(datafixture.runtime) {
        val dataengine = new HttpExecutionEngine(datafixture.subsystem)
        val datalist = dataengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/data/list", form = Record.data("component" -> _notice_board_component_name, "data" -> "audit")))
        val dataread = dataengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/data/read", form = Record.data("component" -> _notice_board_component_name, "data" -> "audit", "id" -> "audit_1")))

        datalist.code shouldBe 200
        datalist.getString.getOrElse("") should include ("audit_1")
        dataread.code shouldBe 200
        dataread.getString.getOrElse("") should include ("actor=alice")
        val datalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit")
        datalistrecord.getString("kind") shouldBe Some("data.list")
        datalistrecord.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        datalistrecord.getAny("items").map(_.toString).getOrElse("") should include ("audit_1")
        datalistrecord.getAny("items").map(_.toString).getOrElse("") should include ("created")
        datalistrecord.getInt("total") shouldBe None
        val totaldatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
        totaldatalistrecord.getInt("total") shouldBe Some(2)
        totaldatalistrecord.getBoolean("totalAvailable") shouldBe Some(true)
        val requireddatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required")
        requireddatalistrecord.getInt("total") shouldBe Some(2)
        val unsupporteddatafixture = _data_fixture(TotalCountCapability.Unsupported)
        _with_global_runtime(unsupporteddatafixture.runtime) {
          val optionalunsupported = _admin_record_response(unsupporteddatafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
          optionalunsupported.getInt("total") shouldBe None
          optionalunsupported.getBoolean("totalAvailable") shouldBe Some(false)
          optionalunsupported.getString("totalUnavailableReason") shouldBe Some("unsupported")
          optionalunsupported.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for data.audit")
          _admin_response(unsupporteddatafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
            case Consequence.Failure(_) => succeed
            case other => fail(s"required total on unsupported datastore should fail: ${other}")
          }
        }
        val datareadrecord = _admin_record_response(datafixture.subsystem, "data", "read", "component" -> _notice_board_component_name, "data" -> "audit", "id" -> "audit_1")
        datareadrecord.getString("kind") shouldBe Some("data.read")
        datareadrecord.getString("label") shouldBe Some("audit_1")
        datareadrecord.getAny("item").map(_.toString).getOrElse("") should include ("audit_1")
        datareadrecord.getString("fields").getOrElse("") should include ("actor=alice")
        val pageddatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "page" -> "3", "pageSize" -> "7")
        pageddatalistrecord.getInt("page") shouldBe Some(3)
        pageddatalistrecord.getInt("pageSize") shouldBe Some(7)
        val firstdatapage = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "pageSize" -> "1")
        firstdatapage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        firstdatapage.getBoolean("hasNext") shouldBe Some(true)
        val seconddatapage = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "page" -> "2", "pageSize" -> "1")
        seconddatapage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_existing")
        seconddatapage.getBoolean("hasNext") shouldBe Some(false)
      }

      val viewsubsystem = _view_fixture_subsystem()
      val viewengine = new HttpExecutionEngine(viewsubsystem)
      val viewid = _notice_entity_id_from_shortid("notice_2").value
      val viewread = viewengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/view/read", form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view")))
      val invalidviewread = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> "legacy-view-id")
      ))

      viewread.code shouldBe 200
      invalidviewread.code shouldBe 400
      viewread.getString.getOrElse("") should include ("notice summary")
      val viewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view")
      viewreadrecord.getString("kind") shouldBe Some("view.read")
      viewreadrecord.getString("fields").getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("values").map(_.toString).getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("items").map(_.toString).getOrElse("") should include ("label")
      viewreadrecord.getInt("page") shouldBe Some(1)
      viewreadrecord.getInt("pageSize") shouldBe Some(20)
      viewreadrecord.getBoolean("hasNext") shouldBe Some(false)
      viewreadrecord.getInt("total") shouldBe None
      val totalviewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalviewreadrecord.getInt("total") shouldBe None
      totalviewreadrecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalviewreadrecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalviewreadrecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for view.notice-view")
      _admin_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on view should fail: ${other}")
      }
      val countedviewsubsystem = _view_fixture_subsystem(totalcountcapability = TotalCountCapability.Supported)
      val countedviewreadrecord = _admin_record_response(countedviewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedviewreadrecord.getInt("total") shouldBe Some(2)
      countedviewreadrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedviewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "page" -> "4", "pageSize" -> "9")
      pagedviewreadrecord.getInt("page") shouldBe Some(4)
      pagedviewreadrecord.getInt("pageSize") shouldBe Some(9)
      val firstviewpage = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "pageSize" -> "1")
      firstviewpage.getString("fields") shouldBe Some("notice summary")
      firstviewpage.getBoolean("hasNext") shouldBe Some(true)
      val secondviewpage = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "page" -> "2", "pageSize" -> "1")
      secondviewpage.getString("fields") shouldBe Some("notice next")
      secondviewpage.getBoolean("hasNext") shouldBe Some(false)
      val viewblobid = _register_external_blob(viewsubsystem, "view-image.png", "https://example.test/view-image.png")
      val viewnoblobrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> viewid)
      viewnoblobrecord.getAny("images") shouldBe Some(Vector.empty)
      viewnoblobrecord.getAny("representativeImage") shouldBe Some(None)
      val viewsourceentityid = viewnoblobrecord.getString("sourceEntityId").getOrElse(fail("view sourceEntityId is missing"))
      _success(viewsubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", viewsourceentityid, None),
        Property("id", viewblobid, None),
        Property("role", "primary", None),
        Property("sortOrder", "1", None)
      )))
      val viewinstancerecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> viewid)
      viewinstancerecord.getString("kind") shouldBe Some("view.read")
      viewinstancerecord.getString("id") shouldBe Some(viewid)
      viewinstancerecord.getString("label").getOrElse("") should include ("notice detail notice_2")
      viewinstancerecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_2")
      viewinstancerecord.getString("fields").getOrElse("") should include ("notice detail notice_2")
      viewinstancerecord.getAny("images").map(_.toString).getOrElse("") should include (viewblobid)
      viewinstancerecord.getAny("images").map(_.toString).getOrElse("") should include ("primary")
      viewinstancerecord.getAny("representativeImage").map(_.toString).getOrElse("") should include (viewblobid)
      viewinstancerecord.getAny("representativeImage").map(_.toString).getOrElse("") should include ("primary")
      _blob_records(viewinstancerecord).map(_.getString("id").getOrElse("")) shouldBe Vector(viewblobid)
      _blob_records(viewinstancerecord).foreach { blob =>
        blob.getAny("payload") shouldBe None
      }

      val aggregatesubsystem = _aggregate_fixture_subsystem()
      val aggregateengine = new HttpExecutionEngine(aggregatesubsystem)
      val aggregateid = _notice_entity_id_from_shortid("notice_1").value
      val aggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-image.png", "https://example.test/aggregate-image.png")
      val earlyaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-early.png", "https://example.test/aggregate-early.png")
      val lateaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-late.png", "https://example.test/aggregate-late.png")
      val unorderedaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-unordered.png", "https://example.test/aggregate-unordered.png")
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", lateaggregateblobid, None),
        Property("role", "lateImage", None),
        Property("sortOrder", "20", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", aggregateblobid, None),
        Property("role", "heroImage", None),
        Property("sortOrder", "2", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", unorderedaggregateblobid, None),
        Property("role", "unorderedImage", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", earlyaggregateblobid, None),
        Property("role", "earlyImage", None),
        Property("sortOrder", "1", None)
      )))
      val aggregateread = aggregateengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/aggregate/read", form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate")))
      val invalidaggregateread = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> "legacy-aggregate-id")
      ))

      aggregateread.code shouldBe 200
      invalidaggregateread.code shouldBe 400
      aggregateread.getString.getOrElse("") should include ("notice aggregate")
      val aggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate")
      aggregatereadrecord.getString("kind") shouldBe Some("aggregate.read")
      aggregatereadrecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("values").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice_1")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include (aggregateblobid)
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("heroImage")
      aggregatereadrecord.getInt("total") shouldBe None
      val totalaggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalaggregatereadrecord.getInt("total") shouldBe None
      totalaggregatereadrecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalaggregatereadrecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalaggregatereadrecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for aggregate.notice-aggregate")
      _admin_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on aggregate should fail: ${other}")
      }
      val countedaggregatesubsystem = _aggregate_fixture_subsystem(totalcountcapability = TotalCountCapability.Supported)
      val countedaggregatereadrecord = _admin_record_response(countedaggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedaggregatereadrecord.getInt("total") shouldBe Some(2)
      countedaggregatereadrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedaggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "page" -> "5", "pageSize" -> "11")
      pagedaggregatereadrecord.getInt("page") shouldBe Some(5)
      pagedaggregatereadrecord.getInt("pageSize") shouldBe Some(11)
      val firstaggregatepage = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "pageSize" -> "1")
      firstaggregatepage.getString("fields").getOrElse("") should include ("notice aggregate")
      firstaggregatepage.getBoolean("hasNext") shouldBe Some(true)
      val secondaggregatepage = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "page" -> "2", "pageSize" -> "1")
      secondaggregatepage.getString("fields").getOrElse("") should include ("notice next")
      secondaggregatepage.getBoolean("hasNext") shouldBe Some(false)
      val aggregateinstancerecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> aggregateid)
      aggregateinstancerecord.getString("kind") shouldBe Some("aggregate.read")
      aggregateinstancerecord.getString("id") shouldBe Some(aggregateid)
      aggregateinstancerecord.getString("label") shouldBe Some("notice aggregate")
      aggregateinstancerecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_1")
      aggregateinstancerecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregateinstancerecord.getAny("record").map(_.toString).getOrElse("") should not include ("blobs")
      aggregateinstancerecord.getAny("record").map(_.toString).getOrElse("") should not include ("images")
      aggregateinstancerecord.getAny("images").map(_.toString).getOrElse("") should include (aggregateblobid)
      aggregateinstancerecord.getAny("images").map(_.toString).getOrElse("") should include ("heroImage")
      val aggregateblobs = _blob_records(aggregateinstancerecord)
      aggregateblobs.map(_.getString("id").getOrElse("")) shouldBe Vector(
        earlyaggregateblobid,
        aggregateblobid,
        lateaggregateblobid,
        unorderedaggregateblobid
      )
      aggregateblobs.map(_.getString("role").getOrElse("")) shouldBe Vector(
        "earlyImage",
        "heroImage",
        "lateImage",
        "unorderedImage"
      )
      aggregateblobs.foreach { blob =>
        blob.getAny("payload") shouldBe None
      }
    }

    "submit aggregate create/update actions through the discovered operation form route" in {
      Given("the prerequisites for submit aggregate create/update actions through the discovered operation form route")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val before = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total

      val createhtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      When("submit aggregate create/update actions through the discovered operation form route is exercised")
      val updatehtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for submit aggregate create/update actions through the discovered operation form route holds")
      createhtml should include ("notice-board.notice-aggregate.create-notice-aggregate")
      createhtml should include ("result.status")
      updatehtml should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      updatehtml should include ("result.status")
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total should be > before
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice-aggregate/create-notice-aggregate")
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice-aggregate/approve-notice-aggregate")
    }

    "keep form control and security values out of operation arguments" in {
      Given("the prerequisites for keep form control and security values out of operation arguments")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&approved=true&crud.success.href=/web/notice-board/admin/aggregates/notice-aggregate/notice_1&textus.admin.principalId=system&textus.admin.privilege=application_content_manager"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      When("keep form control and security values out of operation arguments is exercised")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      Then("the observable contract for keep form control and security values out of operation arguments holds")
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("approved") shouldBe Some("true")
      submitted.getString("crud.success.href") shouldBe None
      submitted.getString("textus.admin.principalId") shouldBe None
      submitted.getString("textus.admin.privilege") shouldBe None
    }

    "stage pasted fileContent as managed job input before operation dispatch" in {
      Given("the prerequisites for stage pasted fileContent as managed job input before operation dispatch")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&fileName=fixture.csv&fileContent=a%2Cb%0A1%2C2%0A"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      When("stage pasted fileContent as managed job input before operation dispatch is exercised")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      Then("the observable contract for stage pasted fileContent as managed job input before operation dispatch holds")
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("fileContent") shouldBe None
      submitted.getString("cncf.job.input.fieldName") shouldBe Some("fileContent")
      submitted.getString("cncf.job.input.filename") shouldBe Some("fixture.csv")
      submitted.getString("cncf.job.input.storage") shouldBe Some("inline")
      submitted.getString("cncf.job.input.inlineBase64").map(java.util.Base64.getDecoder.decode).map(new String(_, StandardCharsets.UTF_8)) shouldBe Some("a,b\n1,2\n")
    }

    "preserve hidden form context for result templates without dispatching it as operation arguments" in {
      Given("the prerequisites for preserve hidden form context for result templates without dispatching it as operation arguments")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(
          selector -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Context Result</h2>
                |  <p>${crud.origin.href}</p>
                |  <p>${crud.success.href}</p>
                |  <p>${crud.error.href}</p>
                |  <p>${paging.page}</p>
                |  <p>${paging.pageSize}</p>
                |  <p>${paging.chunkSize}</p>
                |  <p>${paging.href}</p>
                |  <p>${search.keyword}</p>
                |  <p>${csrf}</p>
                |  <p>form id: ${form.id}</p>
                |  <p>form page: ${form.paging.page}</p>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("preserve hidden form context for result templates without dispatching it as operation arguments is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&crud.success.href=/web/notice-board/admin/aggregates/notice-aggregate/notice_1&crud.error.href=/form/notice-board/notice-aggregate/approve-notice-aggregate&paging.page=2&paging.pageSize=20&paging.chunkSize=1000&paging.href=%2Fform%2Fnotice-board%2Fnotice-aggregate%2Fapprove-notice-aggregate%2Fcontinue%2Ftest-id%3Fpage%3D%7Bpage%7D%26pageSize%3D%7BpageSize%7D&search.keyword=phase12&csrf=token-1&version=7&etag=abc"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve hidden form context for result templates without dispatching it as operation arguments holds")
      html should include ("Context Result")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate")
      html should include ("2")
      html should include ("20")
      html should include ("1000")
      html should include ("phase12")
      html should include (_test_csrf_token)
      html should include ("form id: notice_1")
      html should include ("form page: </p>")
      html should not include ("${crud.origin.href}")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("crud.origin.href") shouldBe None
      submitted.getString("crud.success.href") shouldBe None
      submitted.getString("crud.error.href") shouldBe None
      submitted.getString("paging.page") shouldBe None
      submitted.getString("paging.pageSize") shouldBe None
      submitted.getString("paging.chunkSize") shouldBe None
      submitted.getString("paging.href") shouldBe None
      submitted.getString("search.keyword") shouldBe None
      submitted.getString("csrf") shouldBe None
      submitted.getString("version") shouldBe None
      submitted.getString("etag") shouldBe None
    }

    "await asynchronous command job result through the form job route" in {
      Given("the prerequisites for await asynchronous command job result through the form job route")
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("await asynchronous command job result through the form job route is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-board",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for await asynchronous command job result through the form job route holds")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some("cncf-job-job-1")
      html should include ("created:notice_1")
      html should include ("result.id")
      html should include ("notice_1")
    }

    "preserve componentlet alias when awaiting asynchronous command job result through the form job route" in {
      Given("the prerequisites for preserve componentlet alias when awaiting asynchronous command job result through the form job route")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("preserve componentlet alias when awaiting asynchronous command job result through the form job route is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-admin/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-admin",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve componentlet alias when awaiting asynchronous command job result through the form job route holds")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some("cncf-job-job-1")
      html should include ("created:notice_1")
      html should include ("notice_1")
      html should include ("/form/notice-admin/notice/post-notice")
      html should not include ("/form/notice-board/notice/post-notice")
    }

    "render awaited job result through descriptor result template when static template is absent" in {
      Given("the prerequisites for render awaited job result through descriptor result template when static template is absent")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Descriptor Await Result</h2>
                |  <p>${result.job.id}</p>
                |  <p>${result.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render awaited job result through descriptor result template when static template is absent is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-board",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render awaited job result through descriptor result template when static template is absent holds")
      html should include ("Descriptor Await Result")
      html should include ("cncf-job-job-1")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
    }

    }
  }
}
