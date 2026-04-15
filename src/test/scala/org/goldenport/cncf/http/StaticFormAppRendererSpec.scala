package org.goldenport.cncf.http

import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets
import cats.data.State
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{Method, Request, Uri}
import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.protocol.{Argument, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentDescriptor}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 12, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererSpec extends AnyWordSpec with Matchers {
  "StaticFormAppRenderer" should {
    "render subsystem dashboard state contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))

      val json = _dashboard_state_json(subsystem, None)
      val c = json.hcursor

      c.get[String]("scope") shouldBe Right("subsystem")
      c.downField("cncf").get[String]("version").isRight shouldBe true
      c.downField("subsystem").get[String]("name") shouldBe Right(subsystem.name)
      c.downField("html").downField("requests").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("day").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("hour").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("minute").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("series").downField("minute").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("html").downField("requests").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("html").downField("requests").downField("series").downField("day").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("assembly").downField("warnings").get[Int]("count").isRight shouldBe true
      c.downField("links").get[String]("admin") shouldBe Right("/web/system/admin")
      c.downField("links").get[String]("performance") shouldBe Right("/web/system/performance")
      c.downField("links").get[String]("manual") shouldBe Right("/web/manual")
      c.downField("links").get[String]("console") shouldBe Right("/web/console")
      c.downField("links").get[String]("assemblyWarnings") shouldBe Right("/form/admin/assembly/warnings")
    }

    "render component dashboard state contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val componentName = subsystem.components.headOption.map(_.name).getOrElse(fail("component is missing"))

      val json = _dashboard_state_json(subsystem, Some(componentName))
      val c = json.hcursor

      c.get[String]("scope") shouldBe Right("component")
      c.get[String]("name") shouldBe Right(componentName)
      c.downField("components").focus.flatMap(_.asArray).map(_.size) shouldBe Some(1)
      c.downField("html").downField("requests").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("links").get[String]("admin") shouldBe Right(s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentName)}/admin")
    }

    "render system admin configuration detail page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))

      val html = StaticFormAppRenderer.renderSystemAdmin(subsystem).body

      html should include ("System Admin Configuration")
      html should include ("/web/assets/bootstrap.min.css")
      html should not include ("cdn.jsdelivr")
      html should include ("CNCF version")
      html should include ("Subsystem")
      html should include ("/web/system/dashboard")
      html should include ("/web/system/performance")
      html should include ("/web/manual")
      html should include ("/web/console")
      html should include ("Web Descriptor")
      html should include ("Using built-in Web HTML app defaults.")
      html should include ("/web/system/admin/descriptor")
      html should include ("Runtime Configuration")
      html should include ("Configuration mutation must use a separate admin action surface")
      html should include ("audit logging")
      html should include ("Job Control")
      html should include ("read-only")
      html should include ("cancel, retry, or force-complete")
      html should include ("explicit admin authorization")
      html should include ("Operational Details")
      html should include ("Assembly")
      html should include ("/form/admin/assembly/warnings")
      html should include ("/form/admin/assembly/report")
      html should include ("Execution")
      html should include ("/form/admin/execution/history")
      html should include ("/form/admin/execution/calltree")
    }

    "render resolved runtime configuration with masking rules on system admin page" in {
      val subsystem = new Subsystem(
        name = "masked-system",
        version = Some("1.0.0"),
        configuration = ResolvedConfiguration(
          Configuration(
            Map(
              "textus.runtime.mode" -> ConfigurationValue.StringValue("server"),
              "textus.auth.secret" -> ConfigurationValue.StringValue("open-sesame"),
              "other.value" -> ConfigurationValue.StringValue("hidden-by-scope")
            )
          ),
          ConfigurationTrace.empty
        )
      )

      val html = StaticFormAppRenderer.renderSystemAdmin(subsystem).body

      html should include ("Runtime Configuration")
      html should include ("textus.runtime.mode")
      html should include ("server")
      html should include ("textus.auth.secret")
      html should include ("********")
      html should include ("masked")
      html should not include ("open-sesame")
      html should not include ("other.value")
    }

    "render resolved Web Descriptor summary on system admin page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val descriptor = WebDescriptor(
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(roles = Vector("reader"))),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(enabled = Some(true))),
        apps = Vector(WebDescriptor.App("manual", "/web/manual", "manual")),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      val html = StaticFormAppRenderer.renderSystemAdmin(subsystem, descriptor).body

      html should include ("Web Descriptor")
      html should include ("configured")
      html should include ("notice-board.notice.search-notices")
      html should include ("public")
      html should include ("manual")
      html should include ("/web/manual")
      html should include ("Admin entries")
    }

    "render resolved Web Descriptor drill-down page" in {
      val descriptor = WebDescriptor(
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(roles = Vector("reader"))),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(enabled = Some(true))),
        apps = Vector(WebDescriptor.App("manual", "/web/manual", "manual")),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      val html = StaticFormAppRenderer.renderSystemAdminDescriptor(descriptor).body

      html should include ("System Web Descriptor")
      html should include ("Resolved Descriptor")
      html should include ("/web/system/admin")
      html should include ("&quot;status&quot; : &quot;configured&quot;")
      html should include ("&quot;notice-board.notice.search-notices&quot; : &quot;public&quot;")
      html should include ("&quot;entity.notice&quot;")
      html should include ("&quot;totalCount&quot; : &quot;optional&quot;")
      html should include ("&quot;roles&quot;")
      html should include ("&quot;reader&quot;")
      html should include ("&quot;enabled&quot; : true")
      html should include ("&quot;path&quot; : &quot;/web/manual&quot;")
    }

    "render component admin configuration detail page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdmin(subsystem, component.name).map(_.body).getOrElse(fail("component admin is missing"))

      html should include (s"${component.name} Admin Configuration")
      html should include ("CNCF version")
      html should include (component.name)
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/dashboard")
      html should include ("/web/system/performance")
      html should include ("/web/manual")
      html should include ("/web/console")
      html should include ("Component Operations")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("Managed Data")
      html should include ("Entity CRUD")
      html should include ("Data CRUD")
      html should include ("Aggregate CRUD")
      html should include ("View read")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/entities")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/data")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/aggregates")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/views")
      html should not include ("Operational Details")
      html should not include ("/form/admin/assembly/warnings")
      html should not include ("/form/admin/execution/history")
    }

    "render component entity administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntities(subsystem, component.name).map(_.body).getOrElse(fail("component entity admin is missing"))

      html should include (s"${component.name} Entity Administration")
      html should include ("Entity CRUD")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("entity runtime descriptors")
      component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).headOption match {
        case Some(descriptor) =>
          html should include (s"/web/${componentPath}/admin/entities/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(descriptor.entityName)}")
        case None =>
          html should include ("No entity runtime descriptors")
      }
    }

    "render component entity type list page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityType(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity type admin is missing"))

      html should include (s"${component.name} Sales Order Administration")
      html should include ("Sales Order records")
      html should include ("List with paging")
      html should include (s"/web/${componentPath}/admin/entities")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/new")
      html should include ("No records are currently available")
      html should not include ("sample-id")
      html should include ("Previous")
      html should include ("Next")
    }

    "render component entity detail page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityDetail(subsystem, component.name, "sales-order", "missing-id").map(_.body).getOrElse(fail("component entity detail admin is missing"))

      html should include (s"${component.name} Sales Order Detail")
      html should include ("Sales Order detail")
      html should include (s"/web/${componentPath}/admin/entities/sales-order")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/missing-id/edit")
      html should include ("No record is currently available")
      html should include ("missing-id")
    }

    "render component entity pages from a live EntityCollection fixture" in {
      val subsystem = _management_console_fixture_subsystem()
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val recordId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id.value

      val list = StaticFormAppRenderer.renderComponentAdminEntityType(subsystem, componentName, entityPath).map(_.body).getOrElse(fail("component entity type admin is missing"))
      val firstPage = StaticFormAppRenderer.renderComponentAdminEntityType(
        subsystem,
        componentName,
        entityPath,
        StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1)
      ).map(_.body).getOrElse(fail("component entity first page admin is missing"))
      val secondPage = StaticFormAppRenderer.renderComponentAdminEntityType(
        subsystem,
        componentName,
        entityPath,
        StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1)
      ).map(_.body).getOrElse(fail("component entity second page admin is missing"))
      val totalPage = StaticFormAppRenderer.renderComponentAdminEntityType(
        subsystem,
        componentName,
        entityPath,
        StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
        WebDescriptor(admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
      ).map(_.body).getOrElse(fail("component entity total page admin is missing"))
      val detail = StaticFormAppRenderer.renderComponentAdminEntityDetail(subsystem, componentName, entityPath, recordId).map(_.body).getOrElse(fail("component entity detail admin is missing"))
      val edit = StaticFormAppRenderer.renderComponentAdminEntityEdit(subsystem, componentName, entityPath, recordId).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      list should include ("notice_1")
      list should include (s"/web/${componentPath}/admin/entities/${entityPath}/${recordId}")
      list should include (s"/web/${componentPath}/admin/entities/${entityPath}/${recordId}/edit")
      list should not include ("No records are currently available")
      firstPage should include ("Page 1")
      firstPage should include ("page=2&amp;pageSize=1")
      firstPage should include ("Next")
      secondPage should include ("Page 2")
      secondPage should include ("page=1&amp;pageSize=1")
      secondPage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/notice-board/admin/entities/notice?page=3&amp;pageSize=1\">Next")
      totalPage should include ("total 2")
      totalPage should include ("includeTotal=true")
      detail should include ("board update")
      detail should include ("alice")
      edit should include ("name=\"title\"")
      edit should include ("value=\"board update\"")
      edit should include (s"/form/${componentPath}/admin/entities/${entityPath}/${recordId}/update")
    }

    "apply component entity update form POST into the EntityCollection fixture" in {
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=board+updated&author=bob"
      )

      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      val updated = collection.storage.storeRealm.values.find(_.id.value == recordId).getOrElse(fail("updated entity is missing"))
      updated.title shouldBe "board updated"
      updated.author shouldBe "bob"
      dispatcher.paths should contain ("/admin/entity/update")
    }

    "apply component entity create form POST into the EntityCollection fixture" in {
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val before = collection.storage.storeRealm.values.size
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        "fields=id%3Dnotice_2%0Atitle%3Dnew+notice%0Aauthor%3Dbob"
      )

      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.size shouldBe before + 1
      collection.storage.storeRealm.values.exists(x => x.title == "new notice" && x.author == "bob") shouldBe true
      dispatcher.paths should contain ("/admin/entity/create")
    }

    "render component entity edit page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityEdit(subsystem, component.name, "sales-order", "missing-id").map(_.body).getOrElse(fail("component entity edit admin is missing"))

      html should include (s"${component.name} Sales Order Edit")
      html should include ("Edit Sales Order")
      html should include ("<form method=\"post\"")
      html should include (s"/form/${componentPath}/admin/entities/sales-order/missing-id/update")
      html should include ("name=\"id\"")
      html should include ("value=\"missing-id\"")
      html should include ("Update")
      html should include ("Cancel")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/missing-id")
    }

    "render component entity new page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityNew(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      html should include (s"${component.name} Sales Order New")
      html should include ("New Sales Order")
      html should include ("<form method=\"post\"")
      html should include (s"/form/${componentPath}/admin/entities/sales-order/create")
      html should include ("name=\"fields\"")
      html should include ("Use one name=value pair per line")
      html should include ("Create")
      html should include ("Cancel")
      html should include (s"/web/${componentPath}/admin/entities/sales-order")
    }

    "render component entity update submission result contract" in {
      val html = StaticFormAppRenderer.renderComponentAdminEntityUpdateResult(
        "admin",
        "sales-order",
        "sales-order-1",
        Map("status" -> "confirmed")
      ).body

      html should include ("admin Sales Order Update Result")
      html should include ("Update submitted")
      html should include ("Entity update execution is not enabled in this baseline")
      html should include ("status")
      html should include ("confirmed")
      html should include ("/web/admin/admin/entities/sales-order/sales-order-1")
      html should include ("/web/admin/admin/entities/sales-order/sales-order-1/edit")
    }

    "render component entity create submission result contract" in {
      val html = StaticFormAppRenderer.renderComponentAdminEntityCreateResult(
        "admin",
        "sales-order",
        Map("status" -> "draft")
      ).body

      html should include ("admin Sales Order Create Result")
      html should include ("Create submitted")
      html should include ("Entity create execution is not enabled in this baseline")
      html should include ("status")
      html should include ("draft")
      html should include ("/web/admin/admin/entities/sales-order")
      html should include ("/web/admin/admin/entities/sales-order/new")
    }

    "render component data administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminData(subsystem, component.name).map(_.body).getOrElse(fail("component data admin is missing"))

      html should include (s"${component.name} Data Administration")
      html should include ("Data CRUD")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("not enabled in this baseline")
    }

    "render component data pages from a live DataStore fixture" in {
      val fixture = _data_fixture()
      _with_global_runtime(fixture.runtime) {
        val html = StaticFormAppRenderer.renderComponentAdminDataType(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data type admin is missing"))
        val firstPage = StaticFormAppRenderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data first page admin is missing"))
        val secondPage = StaticFormAppRenderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data second page admin is missing"))
        val totalPage = StaticFormAppRenderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
          WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
        ).map(_.body).getOrElse(fail("component data total page admin is missing"))
        val unsupportedFixture = _data_fixture(TotalCountCapability.Unsupported)
        val unsupportedTotalPage = _with_global_runtime(unsupportedFixture.runtime) {
          StaticFormAppRenderer.renderComponentAdminDataType(
            unsupportedFixture.subsystem,
            "notice_board",
            "audit",
            StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
            WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
          ).map(_.body).getOrElse(fail("component data unsupported total page admin is missing"))
        }
        val detail = StaticFormAppRenderer.renderComponentAdminDataDetail(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data detail admin is missing"))
        val edit = StaticFormAppRenderer.renderComponentAdminDataEdit(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = StaticFormAppRenderer.renderComponentAdminDataNew(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data new admin is missing"))

        html should include ("audit_1")
        html should include ("/web/notice-board/admin/data/audit/audit_1")
        firstPage should include ("Page 1")
        firstPage should include ("page=2&amp;pageSize=1")
        secondPage should include ("Page 2")
        secondPage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/notice-board/admin/data/audit?page=3&amp;pageSize=1\">Next")
        totalPage should include ("total 2")
        totalPage should include ("includeTotal=true")
        unsupportedTotalPage should include ("alert-warning")
        unsupportedTotalPage should include ("total count is not available for data.audit")
        detail should include ("created")
        detail should include ("alice")
        edit should include ("name=\"action\"")
        edit should include ("value=\"created\"")
        newly should include ("/form/notice-board/admin/data/audit/create")
      }
    }

    "apply component data update/create form POST into the DataStore fixture" in {
      val fixture = _data_fixture()
      _with_global_runtime(fixture.runtime) {
        val engine = new HttpExecutionEngine(fixture.subsystem)
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
        val updateReq = _post_form_request(
          "/form/notice-board/admin/data/audit/audit_1/update",
          "action=updated&actor=bob"
        )
        val updateHtml = server
          ._submit_component_admin_data_update(updateReq, "notice-board", "audit", "audit_1")
          .flatMap(_.as[String])
          .unsafeRunSync()

        updateHtml should include ("Data record was applied")
        updateHtml should include ("Applied</th><td>true")
        _load_data_record(fixture.dataStoreSpace, "audit", "audit_1").getString("action") shouldBe Some("updated")
        _load_data_record(fixture.dataStoreSpace, "audit", "audit_1").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/admin/data/update")

        val createReq = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_2%0Aaction%3Dcreated%0Aactor%3Dbob"
        )
        val createHtml = server
          ._submit_component_admin_data_create(createReq, "notice-board", "audit")
          .flatMap(_.as[String])
          .unsafeRunSync()

        createHtml should include ("Data record was applied")
        createHtml should include ("Applied</th><td>true")
        _load_data_record(fixture.dataStoreSpace, "audit", "audit_2").getString("action") shouldBe Some("created")
        _load_data_record(fixture.dataStoreSpace, "audit", "audit_2").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/admin/data/create")
      }
    }

    "render component view administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminViews(subsystem, component.name).map(_.body).getOrElse(fail("component view admin is missing"))

      html should include (s"${component.name} View Administration")
      html should include ("View read")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("view definitions")
    }

    "render component view read page from a live ViewSpace fixture" in {
      val subsystem = _view_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminViewDetail(subsystem, "notice_board", "notice_view").map(_.body).getOrElse(fail("component view detail admin is missing"))

      html should include ("notice_board Notice View View")
      html should include ("Notice View metadata")
      html should include ("notice_view")
      html should include ("notice")
      html should include ("recent")
      html should include ("Read result")
      html should include ("notice summary")
      html should include ("/web/notice-board/admin/views")
    }

    "render component aggregate administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminAggregates(subsystem, component.name).map(_.body).getOrElse(fail("component aggregate admin is missing"))

      html should include (s"${component.name} Aggregate Administration")
      html should include ("Aggregate CRUD")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("aggregate definitions")
    }

    "render component aggregate read page from a live AggregateSpace fixture" in {
      val subsystem = _aggregate_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate").map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      html should include ("notice_board Notice Aggregate Aggregate")
      html should include ("Notice Aggregate metadata")
      html should include ("notice_aggregate")
      html should include ("notice")
      html should include ("notice:notice")
      html should include ("Read result")
      html should include ("notice aggregate")
      html should include ("Operations")
      html should include ("create-notice-aggregate")
      html should include ("approve-notice-aggregate")
      html should include ("read-notice-aggregate")
      html should include ("/form/notice-board/notice-aggregate/create-notice-aggregate")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate")
      html should include ("/web/notice-board/admin/aggregates")
    }

    "execute admin read/list operations for entity data view and aggregate surfaces" in {
      val entitySubsystem = _management_console_fixture_subsystem()
      val entityEngine = new HttpExecutionEngine(entitySubsystem)
      val entityCollection = _notice_fixture_component(entitySubsystem).entitySpace.entity[_NoticeEntity]("notice")
      val entityId = entityCollection.storage.storeRealm.values.head.id.value

      val entityList = entityEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/entity/list", form = Record.data("component" -> "notice-board", "entity" -> "notice")))
      val entityRead = entityEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/entity/read", form = Record.data("component" -> "notice-board", "entity" -> "notice", "id" -> entityId)))

      entityList.code shouldBe 200
      entityList.getString.getOrElse("") should include ("notice_1")
      entityRead.code shouldBe 200
      entityRead.getString.getOrElse("") should include ("title=board update")
      val entityListRecord = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice")
      entityListRecord.getString("kind") shouldBe Some("entity.list")
      entityListRecord.getAny("ids").map(_.toString).getOrElse("") should include ("notice_1")
      entityListRecord.getInt("page") shouldBe Some(1)
      entityListRecord.getInt("pageSize") shouldBe Some(20)
      entityListRecord.getBoolean("hasNext") shouldBe Some(false)
      entityListRecord.getInt("total") shouldBe None
      entityListRecord.getBoolean("totalAvailable") shouldBe Some(false)
      val ignoredTotalEntityListRecord = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "includeTotal" -> "true")
      ignoredTotalEntityListRecord.getInt("total") shouldBe None
      ignoredTotalEntityListRecord.getBoolean("totalAvailable") shouldBe Some(false)
      val totalEntityListRecord = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalEntityListRecord.getInt("total") shouldBe Some(2)
      totalEntityListRecord.getBoolean("totalAvailable") shouldBe Some(true)
      val requiredEntityListRecord = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "required")
      requiredEntityListRecord.getInt("total") shouldBe Some(2)
      val pagedEntityListRecord = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "page" -> "2", "pageSize" -> "5")
      pagedEntityListRecord.getInt("page") shouldBe Some(2)
      pagedEntityListRecord.getInt("pageSize") shouldBe Some(5)
      val firstEntityPage = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "pageSize" -> "1")
      firstEntityPage.getAny("ids").map(_.toString).getOrElse("") should include ("notice_1")
      firstEntityPage.getBoolean("hasNext") shouldBe Some(true)
      val secondEntityPage = _admin_record_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "page" -> "2", "pageSize" -> "1")
      secondEntityPage.getAny("ids").map(_.toString).getOrElse("") should not include (entityId)
      secondEntityPage.getBoolean("hasNext") shouldBe Some(false)
      val entityReadRecord = _admin_record_response(entitySubsystem, "entity", "read", "component" -> "notice-board", "entity" -> "notice", "id" -> entityId)
      entityReadRecord.getString("kind") shouldBe Some("entity.read")
      entityReadRecord.getString("fields").getOrElse("") should include ("title=board update")
      _admin_response(entitySubsystem, "entity", "list", "component" -> "notice-board", "entity" -> "notice", "page" -> "0") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"invalid page should fail: ${other}")
      }

      val dataFixture = _data_fixture()
      _with_global_runtime(dataFixture.runtime) {
        val dataEngine = new HttpExecutionEngine(dataFixture.subsystem)
        val dataList = dataEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/data/list", form = Record.data("component" -> "notice-board", "data" -> "audit")))
        val dataRead = dataEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/data/read", form = Record.data("component" -> "notice-board", "data" -> "audit", "id" -> "audit_1")))

        dataList.code shouldBe 200
        dataList.getString.getOrElse("") should include ("audit_1")
        dataRead.code shouldBe 200
        dataRead.getString.getOrElse("") should include ("actor=alice")
        val dataListRecord = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit")
        dataListRecord.getString("kind") shouldBe Some("data.list")
        dataListRecord.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        dataListRecord.getInt("total") shouldBe None
        val totalDataListRecord = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
        totalDataListRecord.getInt("total") shouldBe Some(2)
        totalDataListRecord.getBoolean("totalAvailable") shouldBe Some(true)
        val requiredDataListRecord = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required")
        requiredDataListRecord.getInt("total") shouldBe Some(2)
        val unsupportedDataFixture = _data_fixture(TotalCountCapability.Unsupported)
        _with_global_runtime(unsupportedDataFixture.runtime) {
          val optionalUnsupported = _admin_record_response(unsupportedDataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
          optionalUnsupported.getInt("total") shouldBe None
          optionalUnsupported.getBoolean("totalAvailable") shouldBe Some(false)
          optionalUnsupported.getString("totalUnavailableReason") shouldBe Some("unsupported")
          optionalUnsupported.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for data.audit")
          _admin_response(unsupportedDataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
            case Consequence.Failure(_) => succeed
            case other => fail(s"required total on unsupported datastore should fail: ${other}")
          }
        }
        val dataReadRecord = _admin_record_response(dataFixture.subsystem, "data", "read", "component" -> "notice-board", "data" -> "audit", "id" -> "audit_1")
        dataReadRecord.getString("kind") shouldBe Some("data.read")
        dataReadRecord.getString("fields").getOrElse("") should include ("actor=alice")
        val pagedDataListRecord = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "page" -> "3", "pageSize" -> "7")
        pagedDataListRecord.getInt("page") shouldBe Some(3)
        pagedDataListRecord.getInt("pageSize") shouldBe Some(7)
        val firstDataPage = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "pageSize" -> "1")
        firstDataPage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        firstDataPage.getBoolean("hasNext") shouldBe Some(true)
        val secondDataPage = _admin_record_response(dataFixture.subsystem, "data", "list", "component" -> "notice-board", "data" -> "audit", "page" -> "2", "pageSize" -> "1")
        secondDataPage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_existing")
        secondDataPage.getBoolean("hasNext") shouldBe Some(false)
      }

      val viewSubsystem = _view_fixture_subsystem()
      val viewEngine = new HttpExecutionEngine(viewSubsystem)
      val viewRead = viewEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/view/read", form = Record.data("component" -> "notice-board", "view" -> "notice-view")))

      viewRead.code shouldBe 200
      viewRead.getString.getOrElse("") should include ("notice summary")
      val viewReadRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view")
      viewReadRecord.getString("kind") shouldBe Some("view.read")
      viewReadRecord.getString("fields").getOrElse("") should include ("notice summary")
      viewReadRecord.getAny("values").map(_.toString).getOrElse("") should include ("notice summary")
      viewReadRecord.getInt("page") shouldBe Some(1)
      viewReadRecord.getInt("pageSize") shouldBe Some(20)
      viewReadRecord.getBoolean("hasNext") shouldBe Some(false)
      viewReadRecord.getInt("total") shouldBe None
      val totalViewReadRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalViewReadRecord.getInt("total") shouldBe Some(2)
      totalViewReadRecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedViewReadRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "page" -> "4", "pageSize" -> "9")
      pagedViewReadRecord.getInt("page") shouldBe Some(4)
      pagedViewReadRecord.getInt("pageSize") shouldBe Some(9)
      val firstViewPage = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "pageSize" -> "1")
      firstViewPage.getString("fields") shouldBe Some("notice summary")
      firstViewPage.getBoolean("hasNext") shouldBe Some(true)
      val secondViewPage = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "page" -> "2", "pageSize" -> "1")
      secondViewPage.getString("fields") shouldBe Some("notice next")
      secondViewPage.getBoolean("hasNext") shouldBe Some(false)

      val aggregateSubsystem = _aggregate_fixture_subsystem()
      val aggregateEngine = new HttpExecutionEngine(aggregateSubsystem)
      val aggregateRead = aggregateEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/aggregate/read", form = Record.data("component" -> "notice-board", "aggregate" -> "notice-aggregate")))

      aggregateRead.code shouldBe 200
      aggregateRead.getString.getOrElse("") should include ("notice aggregate")
      val aggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate")
      aggregateReadRecord.getString("kind") shouldBe Some("aggregate.read")
      aggregateReadRecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregateReadRecord.getAny("values").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregateReadRecord.getInt("total") shouldBe None
      val totalAggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalAggregateReadRecord.getInt("total") shouldBe Some(2)
      totalAggregateReadRecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedAggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "page" -> "5", "pageSize" -> "11")
      pagedAggregateReadRecord.getInt("page") shouldBe Some(5)
      pagedAggregateReadRecord.getInt("pageSize") shouldBe Some(11)
      val firstAggregatePage = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "pageSize" -> "1")
      firstAggregatePage.getString("fields").getOrElse("") should include ("notice aggregate")
      firstAggregatePage.getBoolean("hasNext") shouldBe Some(true)
      val secondAggregatePage = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "page" -> "2", "pageSize" -> "1")
      secondAggregatePage.getString("fields").getOrElse("") should include ("notice next")
      secondAggregatePage.getBoolean("hasNext") shouldBe Some(false)
    }

    "submit aggregate create/update actions through the discovered operation form route" in {
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val before = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total

      val createHtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val updateHtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      createHtml should include ("notice-board.notice-aggregate.create-notice-aggregate")
      createHtml should include ("result.status")
      updateHtml should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      updateHtml should include ("result.status")
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total should be > before
      dispatcher.paths should contain ("/notice-board/notice-aggregate/create-notice-aggregate")
      dispatcher.paths should contain ("/notice-board/notice-aggregate/approve-notice-aggregate")
    }

    "execute aggregate create/update actions through an HTTP ingress-capable component" in {
      val subsystem = _aggregate_http_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val createHtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val updateHtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      createHtml should include ("result.status")
      createHtml should include ("200")
      createHtml should include ("aggregate-created:hello")
      createHtml should not include ("HTTP ingress not configured")
      updateHtml should include ("result.status")
      updateHtml should include ("200")
      updateHtml should include ("aggregate-updated:notice_1")
      updateHtml should not include ("HTTP ingress not configured")
    }

    "build REST operation dispatch requests without executing local operation logic" in {
      val driver = new RecordingRestDriver
      val dispatcher = WebOperationDispatcher.Rest("http://app.example/base", driver)
      val request = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/notice-board/notice-aggregate/create-notice-aggregate",
        query = Record.data("page" -> "1"),
        header = Record.data("X-Trace-Id" -> "trace-1"),
        form = Record.data("title" -> "hello world")
      )

      val response = dispatcher.dispatch(request)

      response.code shouldBe 200
      driver.calls should contain (
        RecordingRestDriver.Call(
          method = "POST",
          path = "http://app.example/base/notice-board/notice-aggregate/create-notice-aggregate?page=1",
          body = Some("title=hello+world"),
          headers = Map("X-Trace-Id" -> "trace-1")
        )
      )
    }

    "render resolved Web Descriptor summary on component admin page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        expose = Map(s"${componentPath}.service.operation" -> WebDescriptor.Exposure.Protected),
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentPath}/dashboard", "dashboard"))
      )

      val html = StaticFormAppRenderer.renderComponentAdmin(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("component admin is missing"))

      html should include ("Web Descriptor")
      html should include ("configured")
      html should include (s"${componentPath}.service.operation")
      html should include ("protected")
      html should include ("component-dashboard")
    }

    "render system performance detail page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/system/dashboard", 200, 12L)
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/missing", 404, 34L)
      RuntimeDashboardMetrics.recordAuthorizationDecision(denied = true)

      val html = StaticFormAppRenderer.renderSystemPerformance(subsystem).body

      html should include ("System Performance")
      html should include ("/web/assets/bootstrap.min.css")
      html should not include ("cdn.jsdelivr")
      html should include ("HTML request")
      html should include ("Latency")
      html should include ("Recent requests")
      html should include ("Recent errors")
      html should include ("ActionCall")
      html should include ("DSL Chokepoints")
      html should include ("Authorization")
      html should include ("Jobs")
      html should include ("Assembly warnings")
      html should include ("/form/admin/assembly/warnings")
      html should include ("/form/admin/assembly/report")
      html should include ("/form/admin/execution/history")
      html should include ("/form/admin/execution/calltree")
      html should include ("/web/system/dashboard")
      html should include ("/missing")
      html should include ("34 ms")
      html should include ("/web/system/dashboard")
      html should include ("/web/system/admin")
      html should include ("/web/manual")
      html should include ("/web/console")
    }

    "render manual and console entry pages without inline operation execution" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))

      val manual = StaticFormAppRenderer.render(subsystem, "manual").map(_.body).getOrElse(fail("manual is missing"))
      val console = StaticFormAppRenderer.render(subsystem, "console").map(_.body).getOrElse(fail("console is missing"))

      manual should include ("System Manual")
      manual should include ("/web/system/dashboard")
      manual should include ("/web/console")
      manual should include ("Console handoff")
      manual should include ("Manual pages remain read-only")
      manual should include ("do not inline operation actions")
      console should include ("System Console")
      console should include ("/web/system/dashboard")
      console should include ("/web/manual")
      console should include ("/form/")
      console should include ("Console links to operation forms")
      console should include ("does not execute operations inline")
      console should not include ("<form method=\"post\"")
    }

    "filter Web HTML app entries by WebDescriptor apps" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("manual", "/web/manual", "manual"))
      )

      val manual = StaticFormAppRenderer.render(subsystem, "manual", webDescriptor = descriptor)
      val console = StaticFormAppRenderer.render(subsystem, "console", webDescriptor = descriptor)

      manual.map(_.body).getOrElse(fail("manual is missing")) should include ("System Manual")
      console shouldBe None
    }

    "allow component dashboard app entries by descriptor path" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentPath}/dashboard", "dashboard"))
      )

      val page = StaticFormAppRenderer.render(subsystem, componentPath, Vector("dashboard"), descriptor)

      page.map(_.body).getOrElse(fail("dashboard is missing")) should include (s"${component.name} Dashboard")
    }

    "render component HTML form operation index" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderFormIndex(subsystem, component.name).map(_.body).getOrElse(fail("form index is missing"))

      html should include (s"${component.name} Forms")
      html should include ("/web/assets/bootstrap.min.css")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/dashboard")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
    }

    "render component HTML operation form" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))

      val html = StaticFormAppRenderer.renderOperationForm(subsystem, component.name, service.name, operation.name).map(_.body).getOrElse(fail("operation form is missing"))

      html should include ("<form method=\"post\"")
      html should include ("name=\"fields\"")
      html should include ("class=\"form-control\"")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)}/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)}")
      html should not include ("cdn.jsdelivr")
    }

    "filter HTML form operations by WebDescriptor form controls" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicePath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentPath, servicePath, operationPath).mkString(".")
      val path = s"/form/${componentPath}/${servicePath}/${operationPath}"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Public),
        form = Map(selector -> WebDescriptor.Form(enabled = Some(false)))
      )

      val index = StaticFormAppRenderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))
      val form = StaticFormAppRenderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      index should not include (path)
      form shouldBe None
    }

    "allow exposed HTML form operations when no explicit form control exists" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicePath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentPath, servicePath, operationPath).mkString(".")
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )

      val form = StaticFormAppRenderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      form.map(_.body).getOrElse(fail("operation form is missing")) should include (s"/form/${componentPath}/${servicePath}/${operationPath}")
    }

    "render textus result widgets with paging links" in {
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

      val html = StaticFormAppRenderer.renderFormResult(properties).body

      html should include ("<table")
      html should include ("Hanako")
      html should not include ("<td>Hello</td>")
      html should include ("result.status")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=1&amp;pageSize=1")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=3&amp;pageSize=1")
      html should not include ("<textus-result-table")
      html should not include ("${result.contentType}")
    }

    "render textus result table without total count" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "10",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/test-id?page={page}&pageSize={pageSize}"
          )
        ),
        200,
        "application/json",
        """[{"title":"Hello"}]"""
      )

      val html = StaticFormAppRenderer.renderFormResult(properties).body

      html should include ("Page 2")
      html should include ("Previous")
      html should include ("Next")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=1&amp;pageSize=10")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=3&amp;pageSize=10")
    }

    "provide local Bootstrap 5 assets" in {
      StaticFormAppAssets.bootstrapVersion shouldBe "5.3.3"
      StaticFormAppAssets.bootstrapCss should include ("Bootstrap")
      StaticFormAppAssets.bootstrapCss should include ("v5.3.3")
      StaticFormAppAssets.bootstrapCss should include (".card")
      StaticFormAppAssets.bootstrapCss should include (".row")
      StaticFormAppAssets.bootstrapCss should include (".form-control")
      StaticFormAppAssets.bootstrapCss should include (".table-responsive")
      StaticFormAppAssets.bootstrapCss should not include ("cdn.jsdelivr")
      StaticFormAppAssets.bootstrapBundleJs should include ("Bootstrap")
      StaticFormAppAssets.bootstrapBundleJs should include ("v5.3.3")
      StaticFormAppAssets.bootstrapBundleJs should include ("bootstrap=e()")
      StaticFormAppAssets.bootstrapBundleJs should include ("Dropdown")
      StaticFormAppAssets.bootstrapBundleJs should not include ("cdn.jsdelivr")
    }

    "provide Bootstrap 5 by default in the Static Form App layout" in {
      val html = StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
        title = "Sample Form App",
        subtitle = "Sample",
        body = """<form><input class="form-control"></form>"""
      ))

      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/bootstrap.bundle.min.js")
      html should include ("<form><input class=\"form-control\"></form>")
      html should not include ("cdn.jsdelivr")
    }
  }

  private def _dashboard_state_json(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    componentName: Option[String]
  ): Json =
    StaticFormAppRenderer.renderDashboardState(subsystem, componentName) match {
      case Some(page) =>
        parse(page.body).fold(
          err => fail(s"dashboard state is not valid JSON: ${err.getMessage}"),
          identity
        )
      case None =>
        fail(s"dashboard state not found: ${componentName.getOrElse("system")}")
    }

  private def _post_form_request(
    path: String,
    body: String
  ): Request[IO] =
    Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(path)
    ).withEntity(body)

  private final case class _DataFixture(
    subsystem: Subsystem,
    runtime: GlobalRuntimeContext,
    dataStoreSpace: DataStoreSpace
  )

  private def _data_fixture(
    totalCountCapability: TotalCountCapability = TotalCountCapability.Supported
  ): _DataFixture = {
    val dataStoreSpace = _data_store_space(totalCountCapability)
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId("audit")
    val _ = dataStoreSpace.inject(cid, Record.create(Vector(
      "id" -> "audit_1",
      "action" -> "created",
      "actor" -> "alice"
    )))
    val _ = dataStoreSpace.inject(cid, Record.create(Vector(
      "id" -> "audit_existing",
      "action" -> "updated",
      "actor" -> "bob"
    )))
    val runtime = GlobalRuntimeContext.create(
      "data-admin-test",
      RuntimeConfig.default.copy(dataStoreSpace = dataStoreSpace),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      org.goldenport.cncf.context.ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val component = TestComponentFactory.create("notice_board", Protocol.empty)
    val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
    _DataFixture(subsystem, runtime, dataStoreSpace)
  }

  private def _data_store_space(
    totalCountCapability: TotalCountCapability
  ): DataStoreSpace =
    totalCountCapability match {
      case TotalCountCapability.Supported =>
        DataStoreSpace.default()
      case other =>
        new DataStoreSpace().addDataStore(
          new _TotalCountCapabilityDataStore(DataStore.inMemorySearchable(), other)
        )
    }

  private final class _TotalCountCapabilityDataStore(
    delegate: SearchableDataStore,
    capability: TotalCountCapability
  ) extends SearchableDataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean =
      delegate.isAccept(cid)

    def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)

    def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)

    def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)

    def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)

    def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      delegate.search(collection, directive)

    override def totalCountCapability(collection: DataStore.CollectionId): TotalCountCapability =
      capability

    def prepare(tx: TransactionContext): PrepareResult =
      delegate.prepare(tx)

    def commit(tx: TransactionContext): Unit =
      delegate.commit(tx)

    def abort(tx: TransactionContext): Unit =
      delegate.abort(tx)
  }

  private def _with_global_runtime[A](
    runtime: GlobalRuntimeContext
  )(body: => A): A = {
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(runtime)
    try {
      body
    } finally {
      GlobalRuntimeContext.current = previous
    }
  }

  private def _load_data_record(
    space: DataStoreSpace,
    collection: String,
    id: String
  ): Record = {
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId(collection)
    (for {
      ds <- space.dataStore(cid)
      entry <- DataStore.EntryId.parse(id)
      record <- ds.load(cid, entry).map(_.getOrElse(Record.empty))
    } yield record).toOption.getOrElse(Record.empty)
  }

  private def _admin_record_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Record = {
    _admin_response(subsystem, service, operation, args*).toOption.collect {
      case OperationResponse.RecordResponse(record) => record
    }.getOrElse(fail(s"admin.${service}.${operation} did not return RecordResponse"))
  }

  private def _admin_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Consequence[OperationResponse] = {
    val request = GRequest.of(
      component = "admin",
      service = service,
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList
    )
    subsystem.executeOperationResponse(request)
  }

  private def _view_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "notice_view",
            entityName = "notice",
            viewNames = Vector("default"),
            queries = Vector(ViewQueryDefinition("recent", Some("notice.updatedAt desc")))
          )
        )
    }
    _initialize_component("notice_board", component)
    val collection = new ViewCollection[String](
      new ViewBuilder[String] {
        def build(id: EntityId): Consequence[String] =
          Consequence.success(s"notice detail ${id.minor}")
      }
    )
    val browser = Browser.from(
      collection,
      _ => Consequence.success(Vector("notice summary", "notice next"))
    )
    component.viewSpace.register("notice_view", collection, browser)
    DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
  }

  private def _aggregate_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = "notice",
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_protocol())
    val aggregate = _NoticeAggregate("notice aggregate")
    val nextAggregate = _NoticeAggregate("notice next")
    component.aggregateSpace.register(
      "notice_aggregate",
      new AggregateCollection[_NoticeAggregate](
        new AggregateBuilder[_NoticeAggregate] {
          def build(id: EntityId): Consequence[_NoticeAggregate] =
            Consequence.success(aggregate)
        },
        q => Consequence.success(org.goldenport.cncf.directive.Query.sliceValues(Vector(aggregate, nextAggregate), q.offset, q.limit))
      )
    )
    DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
  }

  private def _aggregate_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _NoopOperation("read-notice-aggregate"),
                _NoopOperation("create-notice-aggregate"),
                _NoopOperation("approve-notice-aggregate")
              )
            )
          )
        )
      )
    )

  private def _aggregate_http_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = "notice",
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_http_protocol())
    TestComponentFactory.emptySubsystem("sample-web").add(Vector(component))
  }

  private def _aggregate_http_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _SuccessfulAggregateOperation("create-notice-aggregate", "title", "aggregate-created"),
                _SuccessfulAggregateOperation("approve-notice-aggregate", "id", "aggregate-updated")
              )
            )
          )
        )
      ),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector(RestIngress())),
        egresses = EgressCollection(Vector(RestEgress())),
        projections = ProjectionCollection()
      )
    )

  private def _initialize_component(
    name: String,
    component: org.goldenport.cncf.component.Component,
    protocol: Protocol = Protocol.empty
  ): org.goldenport.cncf.component.Component = {
    val componentId = org.goldenport.cncf.component.ComponentId(name)
    val instanceId = org.goldenport.cncf.component.ComponentInstanceId.default(componentId)
    val factory = new org.goldenport.cncf.component.Component.Factory {
      override protected def create_Components(params: org.goldenport.cncf.component.ComponentCreate): Vector[org.goldenport.cncf.component.Component] =
        Vector.empty

      override protected def create_Core(
        params: org.goldenport.cncf.component.ComponentCreate,
        comp: org.goldenport.cncf.component.Component
      ): org.goldenport.cncf.component.Component.Core =
        org.goldenport.cncf.component.Component.Core.create(name, componentId, instanceId, protocol, this)
    }
    val core = org.goldenport.cncf.component.Component.Core.create(name, componentId, instanceId, protocol, factory)
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        TestComponentFactory.emptySubsystem("test"),
        core,
        org.goldenport.cncf.component.ComponentOrigin.Builtin
      )
    )
  }

  private def _management_console_fixture_subsystem(): Subsystem = {
    given EntityPersistent[_NoticeEntity] = _notice_persistent
    val cid = _NoticeEntity.collectionId
    val descriptor = ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = cid,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100
        )
      )
    )
    val component = TestComponentFactory
      .create("notice_board", Protocol.empty)
      .withComponentDescriptors(Vector(descriptor))
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(
        Vector(
          _NoticeEntity(
            EntityId("sample", "notice_1", cid),
            "board update",
            "alice"
          ),
          _NoticeEntity(
            EntityId("sample", "notice_2", cid),
            "board followup",
            "bob"
          )
        )
      )
    )
    DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
  }

  private def _notice_fixture_component(
    subsystem: Subsystem
  ): Component =
    subsystem.components.find(_.name == "notice_board").getOrElse(fail("notice fixture component is missing"))

  private def _notice_collection(
    entities: Vector[_NoticeEntity]
  )(using EntityPersistent[_NoticeEntity]): EntityCollection[_NoticeEntity] = {
    val store = new EntityRealm[_NoticeEntity](
      entityName = "notice",
      loader = EntityLoader[_NoticeEntity](id => entities.find(_.id == id)),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    val memory = new PartitionedMemoryRealm[_NoticeEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _NoticeEntity.collectionId,
      plan = EntityRuntimePlan(
        entityName = "notice",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 100
      ),
      persistent = summon[EntityPersistent[_NoticeEntity]]
    )
    val collection = new EntityCollection[_NoticeEntity](
      descriptor = descriptor,
      storage = EntityStorage(store, Some(memory))
    )
    entities.foreach(collection.put)
    collection
  }

  private def _notice_persistent: EntityPersistent[_NoticeEntity] =
    new EntityPersistent[_NoticeEntity] {
      def id(e: _NoticeEntity): EntityId = e.id
      def toRecord(e: _NoticeEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_NoticeEntity] =
        Consequence.success(
          _NoticeEntity(
            _notice_entity_id(r.getString("id").getOrElse("notice_1")),
            r.getString("title").getOrElse(""),
            r.getString("author").getOrElse("")
          )
        )
    }

  private def _notice_entity_id(value: String): EntityId =
    EntityId.parse(value).toOption.getOrElse(EntityId("sample", value, _NoticeEntity.collectionId))
}

private final case class _NoticeEntity(
  id: EntityId,
  title: String,
  author: String
) {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "title" -> title,
      "author" -> author
    )
}

private object _NoticeEntity {
  val collectionId: EntityCollectionId =
    EntityCollectionId("sample", "web", "notice")
}

private final case class _NoticeAggregate(summary: String)

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.notImplemented("not used")
}

private final case class _SuccessfulAggregateOperation(
  opname: String,
  argumentName: String,
  resultPrefix: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.success(_SuccessfulAggregateAction(OperationRequest.Core(req), argumentName, resultPrefix))
}

private final case class _SuccessfulAggregateAction(
  core: OperationRequest.Core,
  argumentName: String,
  resultPrefix: String
) extends QueryAction with OperationRequest.Core.Holder {
  override def createCall(core: ActionCall.Core): ActionCall =
    _SuccessfulAggregateActionCall(core, argumentName, resultPrefix)
}

private final case class _SuccessfulAggregateActionCall(
  core: ActionCall.Core,
  argumentName: String,
  resultPrefix: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    val value = core.action.arguments.find(_.name == argumentName).map(_.value).getOrElse("")
    Consequence.success(OperationResponse.Scalar(s"${resultPrefix}:${value}"))
  }
}

private final class RecordingWebOperationDispatcher(
  delegate: WebOperationDispatcher
) extends WebOperationDispatcher {
  private val _paths = ListBuffer.empty[String]

  def targetName: String = "recording"

  def paths: Vector[String] = _paths.synchronized {
    _paths.toVector
  }

  def dispatch(request: HttpRequest): HttpResponse = {
    _paths.synchronized {
      _paths += request.path.asString
    }
    delegate.dispatch(request)
  }
}

private final class RecordingRestDriver extends HttpDriver {
  import RecordingRestDriver.Call

  private val _calls = ListBuffer.empty[Call]
  private val _response =
    HttpResponse.Text(
      HttpStatus.Ok,
      ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
      Bag.text("ok", StandardCharsets.UTF_8)
    )

  def calls: Vector[Call] = _calls.synchronized {
    _calls.toVector
  }

  def get(path: String): HttpResponse = {
    _record(Call("GET", path, None, Map.empty))
    _response
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    _record(Call("POST", path, body, headers))
    _response
  }

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    _record(Call("PUT", path, body, headers))
    _response
  }

  private def _record(call: Call): Unit = _calls.synchronized {
    _calls += call
  }
}

private object RecordingRestDriver {
  final case class Call(
    method: String,
    path: String,
    body: Option[String],
    headers: Map[String, String]
  )
}

private final class _IdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value: A = initial

  def get: A = synchronized {
    _value
  }

  def set(a: A): Unit = synchronized {
    _value = a
  }

  override def getAndSet(a: A): A = synchronized {
    val prev = _value
    _value = a
    prev
  }

  def access: (A, A => Boolean) = synchronized {
    val snapshot = _value
    val setter: A => Boolean = (next: A) => synchronized {
      if (_value == snapshot) {
        _value = next
        true
      } else {
        false
      }
    }
    (snapshot, setter)
  }

  override def tryUpdate(f: A => A): Boolean = synchronized {
    _value = f(_value)
    true
  }

  override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
    val (next, out) = f(_value)
    _value = next
    Some(out)
  }

  def update(f: A => A): Unit = synchronized {
    _value = f(_value)
  }

  def modify[B](f: A => (A, B)): B = synchronized {
    val (next, out) = f(_value)
    _value = next
    out
  }

  override def modifyState[B](state: State[A, B]): B = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    out
  }

  override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    Some(out)
  }
}
