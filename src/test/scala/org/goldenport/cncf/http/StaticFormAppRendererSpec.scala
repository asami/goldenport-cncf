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
import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.{Argument, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentFactory}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStoreSpace}
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField}
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
 * @version Apr. 21, 2026
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
      c.downField("links").get[String]("manual") shouldBe Right("/web/system/manual")
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
      c.downField("links").get[String]("manual") shouldBe Right(s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentName)}/manual")
    }

    "render dashboard pages with Bootstrap health hierarchy without changing links" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))

      val html = StaticFormAppRenderer.renderSubsystemDashboard(subsystem).body

      html should include ("CNCF Health")
      html should include ("class=\"card h-100 shadow-sm border-success\"")
      html should include ("class=\"badge text-bg-success\"")
      html should include ("table table-sm table-hover align-middle")
      html should include ("/web/system/admin")
      html should include ("/web/system/performance")
      html should include ("/web/system/manual")
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
      html should include ("/web/system/manual")
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
      html should include ("Effective Runtime Policy")
      html should include ("textus.operation-mode")
      html should include ("develop")
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
        assets = WebDescriptor.Assets(
          autoComplete = false,
          css = Vector("/web/assets/site.css"),
          js = Vector("/web/assets/site.js")
        ),
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(
          roles = Vector("reader"),
          scopes = Vector("notice:read"),
          capabilities = Vector("notice.search"),
          operationModes = Vector(org.goldenport.cncf.config.OperationMode.Develop),
          anonymousOperationModes = Vector(org.goldenport.cncf.config.OperationMode.Test),
          allowAnonymous = true
        )),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(
          enabled = Some(true),
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/search-notices.css"),
            js = Vector("/web/notice-board/notice-board/assets/search-notices.js")
          )
        )),
        apps = Vector(WebDescriptor.App(
          "notice-board",
          "/web/notice-board",
          "static-form",
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/app.css"),
            js = Vector("/web/notice-board/notice-board/assets/app.js")
          )
        )),
        routes = Vector(WebDescriptor.Route(
          "/web/board",
          WebDescriptor.RouteTarget("notice-board", "notice-board")
        )),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      val html = StaticFormAppRenderer.renderSystemAdmin(subsystem, descriptor).body

      html should include ("Web Descriptor")
      html should include ("configured")
      html should include ("notice-board.notice.search-notices")
      html should include ("public")
      html should include ("manual")
      html should include ("/web/system/manual")
      html should include ("Admin entries")
      html should include ("Management Console Controls")
      html should include ("entity.notice")
      html should include ("optional")
    }

    "render resolved Web Descriptor drill-down page" in {
      val descriptor = WebDescriptor(
        assets = WebDescriptor.Assets(
          autoComplete = false,
          css = Vector("/web/assets/site.css"),
          js = Vector("/web/assets/site.js")
        ),
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(
          roles = Vector("reader"),
          scopes = Vector("notice:read"),
          capabilities = Vector("notice.search"),
          operationModes = Vector(org.goldenport.cncf.config.OperationMode.Develop),
          anonymousOperationModes = Vector(org.goldenport.cncf.config.OperationMode.Test),
          allowAnonymous = true
        )),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(
          enabled = Some(true),
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/search-notices.css"),
            js = Vector("/web/notice-board/notice-board/assets/search-notices.js")
          )
        )),
        apps = Vector(WebDescriptor.App(
          "notice-board",
          "/web/notice-board",
          "static-form",
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/app.css"),
            js = Vector("/web/notice-board/notice-board/assets/app.js")
          )
        )),
        routes = Vector(WebDescriptor.Route(
          "/web/board",
          WebDescriptor.RouteTarget("notice-board", "notice-board")
        )),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      val html = StaticFormAppRenderer.renderSystemAdminDescriptor(descriptor).body

      html should include ("System Web Descriptor")
      html should include ("Descriptor Sections")
      html should include ("href=\"#descriptor-controls\"")
      html should include ("href=\"#asset-composition\"")
      html should include ("href=\"#completed-descriptor\"")
      html should include ("href=\"#configured-descriptor\"")
      html should include ("Descriptor Controls")
      html should include ("Filter descriptor tables")
      html should include ("data-textus-descriptor-filter")
      html should include ("No descriptor rows match the filter.")
      html should include ("Apps")
      html should include ("Routes")
      html should include ("Form Access And Authorization")
      html should include ("Admin Surfaces")
      html should include ("href=\"/web/notice-board\"")
      html should include ("href=\"/web/board\"")
      html should include ("href=\"/form/notice-board/notice/search-notices\"")
      html should include ("notice:read")
      html should include ("notice.search")
      html should include ("develop")
      html should include ("test")
      html should include ("Asset Composition")
      html should include ("Configured Scopes")
      html should include ("Resolved Form Pages")
      html should include ("component form index")
      html should include ("operation input")
      html should include ("operation result")
      html should include ("Completed Descriptor JSON")
      html should include ("Configured Descriptor JSON")
      html should include ("descriptor-json-details")
      html should include ("<details")
      html should include ("/web/system/admin")
      html should include ("&quot;status&quot; : &quot;configured&quot;")
      html should include ("&quot;notice-board.notice.search-notices&quot; : &quot;public&quot;")
      html should include ("&quot;entity.notice&quot;")
      html should include ("&quot;totalCount&quot; : &quot;optional&quot;")
      html should include ("&quot;roles&quot;")
      html should include ("&quot;reader&quot;")
      html should include ("&quot;enabled&quot; : true")
      html should include ("&quot;path&quot; : &quot;/web/notice-board&quot;")
      html should include ("&quot;root&quot; : &quot;/web/notice-board&quot;")
      html should include ("&quot;route&quot; : &quot;/web/{component}/notice-board&quot;")
      html should include ("&quot;assetComposition&quot;")
      html should include ("&quot;global&quot;")
      html should include ("&quot;apps&quot;")
      html should include ("&quot;forms&quot;")
      html should include ("&quot;resolvedForms&quot;")
      html should include ("&quot;componentFormIndex&quot;")
      html should include ("&quot;operationInput&quot;")
      html should include ("&quot;operationResult&quot;")
      html should include ("/web/assets/site.css")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/notice-board/notice-board/assets/search-notices.css")
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
      html should include ("/web/system/manual")
      html should include ("/web/console")
      html should include ("Component Operations")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/descriptor")
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

    "render component-scoped Web Descriptor drill-down page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("notice-board"), WebDescriptor.App("other-board")),
        routes = Vector(
          WebDescriptor.Route("/web/notice", WebDescriptor.RouteTarget(componentPath, "notice-board")),
          WebDescriptor.Route("/web/other", WebDescriptor.RouteTarget("other-board", "other-board"))
        ),
        expose = Map(
          s"${componentPath}.notice.search-notices" -> WebDescriptor.Exposure.Public,
          "other-board.notice.search-notices" -> WebDescriptor.Exposure.Public
        ),
        admin = Map(
          "entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional),
          "other-board.entity.secret" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Required)
        )
      )

      val html = StaticFormAppRenderer.renderComponentAdminDescriptor(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("component descriptor admin is missing"))

      html should include (s"${component.name} Web Descriptor")
      html should include ("Component Management Console descriptor view")
      html should include (s"/web/${componentPath}/admin")
      html should include ("/web/system/admin/descriptor")
      html should include ("Descriptor Sections")
      html should include ("Completed Descriptor JSON")
      html should include ("Configured Descriptor JSON")
      html should include ("descriptor-json-details")
      html should include ("Descriptor Controls")
      html should include ("Filter descriptor tables")
      html should include ("No descriptor rows match the filter.")
      html should include ("Apps")
      html should include ("Routes")
      html should include ("Form Access And Authorization")
      html should include ("Admin Surfaces")
      html should include (s"href=\"/web/${componentPath}/notice-board\"")
      html should include ("href=\"/web/notice\"")
      html should include (s"href=\"/form/${componentPath}/notice/search-notices\"")
      html should include (s"href=\"/web/${componentPath}/admin/entities/notice\"")
      html should not include ("href=\"/web/other\"")
      html should not include ("href=\"/form/other-board/notice/search-notices\"")
      html should not include (s"href=\"/web/${componentPath}/admin/entities/secret\"")
      html should include ("Asset Composition")
      html should include ("Configured Scopes")
      html should include ("Resolved Form Pages")
      html should include ("&quot;root&quot; : &quot;/web/notice-board&quot;")
      html should include (s"&quot;route&quot; : &quot;/web/${componentPath}/notice-board&quot;")
      html should include ("&quot;kind&quot; : &quot;static-form&quot;")
    }

    "render read-only system and component manual pages" in {
      val subsystem = _aggregate_http_fixture_subsystem()

      val systemHtml = StaticFormAppRenderer.renderSystemManual(subsystem).body
      val componentHtml = StaticFormAppRenderer.renderComponentManual(subsystem, "notice-board").map(_.body).getOrElse(fail("component manual is missing"))
      val serviceHtml = StaticFormAppRenderer.renderComponentManualService(subsystem, "notice-board", "notice-aggregate").map(_.body).getOrElse(fail("service manual is missing"))
      val operationHtml = StaticFormAppRenderer.renderComponentManualOperation(subsystem, "notice-board", "notice-aggregate", "approve-notice-aggregate").map(_.body).getOrElse(fail("operation manual is missing"))

      systemHtml should include ("System Manual")
      systemHtml should include ("OpenAPI JSON")
      systemHtml should include ("MCP endpoint")
      systemHtml should include ("/web/notice-board/manual")
      systemHtml should include ("class=\"card manual-card shadow-sm\"")
      componentHtml should include ("notice_board Manual")
      componentHtml should include ("Help")
      componentHtml should include ("Describe")
      componentHtml should include ("Schema")
      componentHtml should include ("/web/notice-board/manual/notice-aggregate")
      componentHtml should include ("class=\"row mb-0\"")
      serviceHtml should include ("Service reference")
      serviceHtml should include ("/web/notice-board/manual/notice-aggregate/approve-notice-aggregate")
      operationHtml should include ("Operation reference")
      operationHtml should include ("approve-notice-aggregate")
      operationHtml should include ("web-empty-state")
      operationHtml should not include ("admin entity")
      operationHtml should not include ("method=\"post\"")
    }

    "serve manual routes and OpenAPI JSON through Web HTML paths" in {
      val subsystem = _aggregate_http_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val manualResponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/manual/notice-aggregate/approve-notice-aggregate"))
        .unsafeRunSync()
      val manualHtml = manualResponse.as[String].unsafeRunSync()
      val openApiResponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/system/manual/openapi.json"))
        .unsafeRunSync()
      val openApiJson = openApiResponse.as[String].unsafeRunSync()

      manualResponse.status.code shouldBe 200
      manualHtml should include ("Operation reference")
      manualHtml should include ("approve-notice-aggregate")
      manualHtml should include ("/mcp")
      openApiResponse.status.code shouldBe 200
      openApiJson should include (""""openapi"""")
      openApiJson should include ("/notice-board/notice-aggregate/approve-notice-aggregate")
    }

    "render component entity administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntities(subsystem, component.name).map(_.body).getOrElse(fail("component entity admin is missing"))

      html should include (s"${component.name} Entity Administration")
      html should include ("Entity CRUD")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("entity runtime descriptors")
      component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).headOption match {
        case Some(descriptor) =>
          html should include ("class=\"table table-sm table-hover align-middle\"")
          html should include (s"/web/${componentPath}/admin/entities/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(descriptor.entityName)}")
        case None =>
          html should include ("No entity runtime descriptors")
          html should include ("admin-empty-state")
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
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("class=\"btn btn-primary\"")
      html should include (s"/web/${componentPath}/admin/entities")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/new")
      html should include ("No records are currently available")
      html should include ("admin-empty-state")
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
      html should include ("class=\"card admin-card")
      html should include ("class=\"btn btn-primary\"")
      html should include (s"/web/${componentPath}/admin/entities/sales-order")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/missing-id/edit")
      html should include ("No record is currently available")
      html should include ("admin-empty-state")
      html should include ("missing-id")
    }

    "render component entity pages from a live EntityCollection fixture" in {
      val subsystem = _management_console_fixture_subsystem()
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val recordEntityId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id
      val recordId = recordEntityId.value
      val recordShortid = recordEntityId.parts.entropy

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
      val detailByShortid = StaticFormAppRenderer.renderComponentAdminEntityDetail(subsystem, componentName, entityPath, recordShortid).map(_.body).getOrElse(fail("component entity detail admin by shortid is missing"))
      val edit = StaticFormAppRenderer.renderComponentAdminEntityEdit(subsystem, componentName, entityPath, recordId).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      list should include ("notice_1")
      list should include ("<th>id</th><th>title</th><th>author</th><th>Actions</th>")
      list should include ("class=\"btn-group btn-group-sm\"")
      list should include ("board update")
      list should include ("alice")
      list should include (s"/web/${componentPath}/admin/entities/${entityPath}/${recordShortid}")
      list should include (s"/web/${componentPath}/admin/entities/${entityPath}/${recordShortid}/edit")
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
      detailByShortid should include ("board update")
      detailByShortid should include ("alice")
      edit should include ("name=\"title\"")
      edit should include ("value=\"board update\"")
      edit should include (s"/form/${componentPath}/admin/entities/${entityPath}/${recordShortid}/update")
    }

    "preserve list paging and search context through entity detail and edit links" in {
      val subsystem = _management_console_fixture_subsystem()
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val recordEntityId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id
      val recordId = recordEntityId.value
      val recordShortid = recordEntityId.parts.entropy
      val context = Map(
        "search.author" -> "alice",
        "paging.page" -> "2",
        "paging.pageSize" -> "1",
        "crud.origin.href" -> s"/web/${componentPath}/admin/entities/${entityPath}?page=2&pageSize=1&search.author=alice"
      )

      val list = StaticFormAppRenderer.renderComponentAdminEntityType(
        subsystem,
        componentName,
        entityPath,
        StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1),
        pageContext = context
      ).map(_.body).getOrElse(fail("component entity list admin is missing"))
      val detail = StaticFormAppRenderer.renderComponentAdminEntityDetail(
        subsystem,
        componentName,
        entityPath,
        recordId,
        values = context
      ).map(_.body).getOrElse(fail("component entity detail admin is missing"))
      val edit = StaticFormAppRenderer.renderComponentAdminEntityEdit(
        subsystem,
        componentName,
        entityPath,
        recordId,
        values = context
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      list should include (s"/web/${componentPath}/admin/entities/${entityPath}/")
      list should include ("?crud.origin.href=")
      list should include ("paging.page=2")
      list should include ("paging.pageSize=1")
      list should include ("search.author=alice")
      list should include ("/edit?crud.origin.href=")
      detail should include (s"/web/${componentPath}/admin/entities/${entityPath}?crud.origin.href=")
      detail should include (s"/web/${componentPath}/admin/entities/${entityPath}/${recordShortid}/edit?crud.origin.href=")
      edit should include ("type=\"hidden\" name=\"crud.origin.href\"")
      edit should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      edit should include ("type=\"hidden\" name=\"paging.pageSize\" value=\"1\"")
      edit should include ("type=\"hidden\" name=\"search.author\" value=\"alice\"")
    }

    "apply component entity update form POST through EntityCollection into EntityStoreSpace" in {
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordEntityId = collection.storage.storeRealm.values.head.id
      val recordId = recordEntityId.value
      val recordShortid = recordEntityId.parts.entropy
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordShortid}/update",
        "title=board+updated&author=bob"
      )

      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordShortid)
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      val updated = collection.storage.storeRealm.values.find(_.id.value == recordId).getOrElse(fail("updated entity is missing"))
      updated.title shouldBe "board updated"
      updated.author shouldBe "bob"
      val stored = _load_notice_store_record(subsystem, updated.id)
      stored.getString("title") shouldBe Some("board updated")
      stored.getString("author") shouldBe Some("bob")
      dispatcher.paths should contain ("/admin/entity/update")
    }

    "redirect component entity update by admin form descriptor transition" in {
      val subsystem = _management_console_fixture_subsystem()
      val descriptor = WebDescriptor(
        form = Map(
          "notice-board.admin.entities.notice.update" -> WebDescriptor.Form(
            successRedirect = Some("/web/${component}/admin/${surface}/${collection}/${id}")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=board+redirected&author=bob"
      )

      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .unsafeRunSync()

      response.status.code shouldBe 303
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some(s"/web/notice-board/admin/entities/notice/${recordId}")
      collection.storage.storeRealm.values.exists(x => x.id.value == recordId && x.title == "board redirected") shouldBe true
      dispatcher.paths should contain ("/admin/entity/update")
    }

    "redisplay component entity update form with submitted values when admin stayOnError is enabled" in {
      val subsystem = _management_console_fixture_subsystem()
      val descriptor = WebDescriptor(
        form = Map(
          "notice-board.admin.entities.notice.update" -> WebDescriptor.Form(stayOnError = true)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid entity update", StandardCharsets.UTF_8)
        )
      )
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=bad+title&author=bob"
      )

      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Edit Notice")
      html should include ("error.status")
      html should include ("400")
      html should include ("invalid entity update")
      html should include ("value=\"bad title\"")
      html should include ("value=\"bob\"")
    }

    "redisplay component entity update form with field validation errors before dispatch" in {
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=&author=bob&crud.origin.href=%2Fweb%2Fnotice-board%2Fadmin%2Fentities%2Fnotice%3Fpage%3D2&paging.page=2&search.author=bob"
      )

      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 400
      html should include ("Edit Notice")
      html should include ("Validation failed.")
      html should include ("admin-feedback")
      html should include ("title is required.")
      html should include ("is-invalid")
      html should include ("value=\"bob\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board/admin/entities/notice?page=2\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"search.author\" value=\"bob\"")
      dispatcher.paths should not contain ("/admin/entity/update")
    }

    "validate component entity update forms by detail view fields before full schema fields" in {
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewFields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val edit = StaticFormAppRenderer
        .renderComponentAdminEntityEdit(subsystem, "notice_board", "notice", recordId)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=detail+only"
      )

      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .flatMap(_.as[String])
        .unsafeRunSync()

      edit should include ("name=\"title\"")
      edit should not include ("name=\"author\"")
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.exists(x => x.id.value == recordId && x.title == "detail only") shouldBe true
      dispatcher.paths should contain ("/admin/entity/update")
    }

    "reject component entity update forms when a required detail view field is empty" in {
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewFields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity]("notice")
      val recordId = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordId}/update",
        "title=&author=ignored"
      )

      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordId)
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 400
      html should include ("Edit Notice")
      html should include ("Validation failed.")
      html should include ("admin-feedback")
      html should include ("title is required.")
      html should include ("is-invalid")
      html should not include ("name=\"author\"")
      dispatcher.paths should not contain ("/admin/entity/update")
    }

    "apply component entity create form POST through EntityCollection into EntityStoreSpace" in {
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
      val created = collection.storage.storeRealm.values.find(x => x.title == "new notice" && x.author == "bob")
        .getOrElse(fail("created entity is missing"))
      val stored = _load_notice_store_record(subsystem, created.id)
      stored.getString("title") shouldBe Some("new notice")
      stored.getString("author") shouldBe Some("bob")
      dispatcher.paths should contain ("/admin/entity/create")
    }

    "render admin entity create and update forms from derived alias schema fields" in {
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val recordEntityId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id
      val recordId = recordEntityId.value
      val recordShortid = recordEntityId.parts.entropy

      val newHtml = StaticFormAppRenderer
        .renderComponentAdminEntityNew(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val editHtml = StaticFormAppRenderer
        .renderComponentAdminEntityEdit(subsystem, componentName, entityPath, recordId)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))

      newHtml should include (s"/form/${componentPath}/admin/entities/${entityPath}/create")
      newHtml should include ("name=\"subject\"")
      newHtml should include ("name=\"body\"")
      newHtml should not include ("name=\"title\"")
      newHtml should not include ("name=\"content\"")
      editHtml should include (s"/form/${componentPath}/admin/entities/${entityPath}/${recordShortid}/update")
      editHtml should include ("name=\"subject\"")
      editHtml should include ("name=\"body\"")
      editHtml should not include ("name=\"title\"")
      editHtml should not include ("name=\"content\"")
    }

    "honor SimpleEntity platform fields when admin schema includes them" in {
      val subsystem = _management_console_fixture_subsystem(schema = _schema(
        "id",
        "nameAttributes",
        "descriptiveAttributes",
        "lifecycleAttributes",
        "publicationAttributes",
        "securityAttributes",
        "resourceAttributes",
        "auditAttributes",
        "mediaAttributes",
        "contextualAttribute",
        "senderName",
        "subject",
        "body"
      ))
      val componentName = "notice_board"
      val entityPath = "notice"
      val recordId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id.value

      val newHtml = StaticFormAppRenderer
        .renderComponentAdminEntityNew(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val editHtml = StaticFormAppRenderer
        .renderComponentAdminEntityEdit(subsystem, componentName, entityPath, recordId)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))

      newHtml should include ("name=\"id\"")
      newHtml should include ("name=\"nameAttributes\"")
      newHtml should include ("name=\"lifecycleAttributes\"")
      newHtml should include ("name=\"securityAttributes\"")
      newHtml should include ("name=\"senderName\"")
      newHtml should include ("name=\"subject\"")
      newHtml should include ("name=\"body\"")
      editHtml should include ("name=\"nameAttributes\"")
      editHtml should include ("name=\"lifecycleAttributes\"")
      editHtml should include ("name=\"securityAttributes\"")
    }

    "render admin entity list and detail with derived alias fields" in {
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val recordId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id.value

      val list = StaticFormAppRenderer
        .renderComponentAdminEntityType(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity type admin is missing"))
      val detail = StaticFormAppRenderer
        .renderComponentAdminEntityDetail(subsystem, componentName, entityPath, recordId)
        .map(_.body)
        .getOrElse(fail("component entity detail admin is missing"))

      list should include ("<th>id</th><th>senderName</th><th>recipientName</th><th>subject</th><th>body</th><th>Actions</th>")
      list should include ("board update")
      list should not include ("<th>title</th>")
      list should not include ("<th>content</th>")
      detail should include ("<th>subject</th><td>board update</td>")
      detail should not include ("<th>title</th>")
      detail should not include ("<th>content</th>")
    }

    "render admin entity list detail edit and new from view fields before full schema fields" in {
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema(
          "id",
          "nameAttributes",
          "lifecycleAttributes",
          "securityAttributes",
          "senderName",
          "recipientName",
          "subject",
          "body"
        ),
        viewFields = Map(
          "summary" -> Vector("id", "subject"),
          "detail" -> Vector("id", "senderName", "recipientName", "subject", "body"),
          "create" -> Vector("senderName", "recipientName", "subject", "body")
        )
      )
      val componentName = "notice_board"
      val entityPath = "notice"
      val recordId = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath).storage.storeRealm.values.head.id.value

      val list = StaticFormAppRenderer
        .renderComponentAdminEntityType(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity type admin is missing"))
      val detail = StaticFormAppRenderer
        .renderComponentAdminEntityDetail(subsystem, componentName, entityPath, recordId)
        .map(_.body)
        .getOrElse(fail("component entity detail admin is missing"))
      val edit = StaticFormAppRenderer
        .renderComponentAdminEntityEdit(subsystem, componentName, entityPath, recordId)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))
      val newly = StaticFormAppRenderer
        .renderComponentAdminEntityNew(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val formDefinition = parse(StaticFormAppRenderer
        .renderComponentAdminEntityFormDefinition(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      val formDefinitionFields = formDefinition.hcursor.downField("fields")

      list should include ("<th>id</th><th>subject</th><th>Actions</th>")
      list should not include ("nameAttributes")
      list should not include ("lifecycleAttributes")
      detail should include ("<th>senderName</th>")
      detail should include ("<th>subject</th>")
      detail should not include ("<th>securityAttributes</th>")
      edit should include ("name=\"senderName\"")
      edit should include ("name=\"subject\"")
      edit should include ("name=\"body\"")
      edit should not include ("name=\"nameAttributes\"")
      edit should not include ("name=\"lifecycleAttributes\"")
      edit should not include ("name=\"securityAttributes\"")
      newly should include ("name=\"senderName\"")
      newly should include ("name=\"subject\"")
      newly should include ("name=\"body\"")
      newly should not include ("name=\"id\"")
      newly should not include ("name=\"nameAttributes\"")
      newly should not include ("name=\"lifecycleAttributes\"")
      newly should not include ("name=\"securityAttributes\"")
      formDefinitionFields.downN(0).downField("name").as[String].toOption shouldBe Some("senderName")
      formDefinitionFields.downN(1).downField("name").as[String].toOption shouldBe Some("recipientName")
      formDefinitionFields.downN(2).downField("name").as[String].toOption shouldBe Some("subject")
      formDefinitionFields.downN(3).downField("name").as[String].toOption shouldBe Some("body")
    }

    "create admin entity records without exposing id when create view fields omit it" in {
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewFields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title", "author"),
          "create" -> Vector("title", "author")
        )
      )
      val componentName = "notice_board"
      val componentPath = "notice-board"
      val entityPath = "notice"
      val newHtml = StaticFormAppRenderer
        .renderComponentAdminEntityNew(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val formDefinition = parse(StaticFormAppRenderer
        .renderComponentAdminEntityFormDefinition(subsystem, componentName, entityPath)
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[_NoticeEntity](entityPath)
      val before = collection.storage.storeRealm.values.size
      val req = _post_form_request(
        s"/form/${componentPath}/admin/entities/${entityPath}/create",
        "title=idless+notice&author=carol"
      )

      val html = server
        ._submit_component_admin_entity_create(req, componentPath, entityPath)
        .flatMap(_.as[String])
        .unsafeRunSync()

      newHtml should include ("name=\"title\"")
      newHtml should include ("name=\"author\"")
      newHtml should not include ("name=\"id\"")
      formDefinition.hcursor.downField("fields").downN(0).downField("name").as[String].toOption shouldBe Some("title")
      formDefinition.hcursor.downField("fields").downN(1).downField("name").as[String].toOption shouldBe Some("author")
      formDefinition.hcursor.downField("fields").downN(2).downField("name").as[String].toOption shouldBe None
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.size shouldBe before + 1
      val created = collection.storage.storeRealm.values.find(_.title == "idless notice").getOrElse(fail("created notice is missing"))
      created.author shouldBe "carol"
      created.id.value should not be "notice_1"
      created.id.collection shouldBe _NoticeEntity.collectionId
      dispatcher.paths should contain ("/admin/entity/create")
    }

    "pass derived alias admin entity create fields through to the dispatcher" in {
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        "id=notice_alias&senderName=alice&recipientName=bob&subject=Phase+12&body=Alias+body"
      )

      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Entity record was applied")
      dispatcher.paths should contain ("/admin/entity/create")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("admin entity create form was not dispatched"))
      submitted.getString("subject") shouldBe Some("Phase 12")
      submitted.getString("body") shouldBe Some("Alias body")
      submitted.getString("title") shouldBe None
      submitted.getString("content") shouldBe None
    }

    "keep static result page convention out of built-in admin entity create flow" in {
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__200.html",
            "<article><h2>Static Operation Result</h2></article>"
          ).resolve("web.yaml").toString)
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        "fields=id%3Dnotice_static%0Atitle%3Dstatic+guard%0Aauthor%3Dbob"
      )

      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Entity record was applied")
      html should include ("Create submitted")
      html should not include ("Static Operation Result")
      dispatcher.paths should contain ("/admin/entity/create")
    }

    "define Static Form Web App template lookup precedence as route-local before common templates" in {
      val subsystem = _management_console_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      server._form_result_template_candidates("notice-board", "notice", "post-notice", 200) shouldBe Vector(
        java.nio.file.Paths.get("notice-board", "notice", "post-notice__200.html"),
        java.nio.file.Paths.get("notice-board", "post-notice__200.html"),
        java.nio.file.Paths.get("notice-board", "notice", "post-notice__success.html"),
        java.nio.file.Paths.get("notice-board", "post-notice__success.html"),
        java.nio.file.Paths.get("notice-board", "notice", "__200.html"),
        java.nio.file.Paths.get("notice-board", "__200.html"),
        java.nio.file.Paths.get("notice-board", "notice", "__success.html"),
        java.nio.file.Paths.get("notice-board", "__success.html"),
        java.nio.file.Paths.get("post-notice__200.html"),
        java.nio.file.Paths.get("__200.html"),
        java.nio.file.Paths.get("post-notice__success.html"),
        java.nio.file.Paths.get("__success.html")
      )
    }

    "load Static Form Web App result templates from the descriptor root with route-local precedence" in {
      val root = Files.createTempDirectory("cncf-web-template-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("notice"))
      Files.writeString(root.resolve("post-notice__200.html"), "ROOT OPERATION", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("__200.html"), "APP COMMON", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("post-notice__200.html"), "APP OPERATION", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("notice").resolve("post-notice__200.html"), "SERVICE OPERATION", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      server._web_resource_roots().map(_.name) shouldBe Vector(root.toString)
      server._form_result_static_template("notice-board", "notice", "post-notice", 200) shouldBe Some("SERVICE OPERATION")
    }

    "serve app-local assets from the canonical component Web app route" in {
      val root = Files.createTempDirectory("cncf-web-asset-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._web_app_asset("notice-board", "notice-board", "app.css")
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      body should include (".notice-board")
      response.contentType.map(_.mediaType) shouldBe Some(MediaType.text.css)
      server._web_app_asset("missing", "notice-board", "app.css").unsafeRunSync().status.code shouldBe 404
    }

    "serve static Web app HTML from the canonical component Web app route" in {
      val root = Files.createTempDirectory("cncf-web-html-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("about.html"), "<h1>About Notice Board</h1>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val index = server._component_web_app("notice-board", "notice-board", Vector.empty).unsafeRunSync()
      val about = server._component_web_app("notice-board", "notice-board", Vector("about")).unsafeRunSync()
      val missingComponent = server._component_web_app("missing", "notice-board", Vector.empty).unsafeRunSync()

      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("Notice Board")
      about.status.code shouldBe 200
      about.as[String].unsafeRunSync() should include ("About Notice Board")
      missingComponent.status.code shouldBe 404
    }

    "serve static Web app HTML and assets through descriptor route aliases" in {
      val root = Files.createTempDirectory("cncf-web-alias-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |    - path: /web
          |      kind: default
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Aliased Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("about.html"), "<h1>Aliased About</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".alias-notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val index = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      val about = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/about"))).unsafeRunSync()
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/assets/app.css"))).unsafeRunSync()
      val default = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()

      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("Aliased Notice Board")
      about.status.code shouldBe 200
      about.as[String].unsafeRunSync() should include ("Aliased About")
      asset.status.code shouldBe 200
      asset.as[String].unsafeRunSync() should include ("alias-notice-board")
      default.status.code shouldBe 200
      default.as[String].unsafeRunSync() should include ("Aliased Notice Board")
    }

    "serve single component Web app through implicit SAR convenience aliases" in {
      val root = Files.createTempDirectory("cncf-web-implicit-alias-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Implicit Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".implicit-notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        )),
        ConfigurationTrace.empty
      )
      val component = TestComponentFactory.create("notice_board", Protocol.empty)
      val subsystem = new Subsystem(
        name = "implicit-web",
        configuration = configuration
      ).add(Vector(component))
      val engine = new HttpExecutionEngine(subsystem)
      val server = new Http4sHttpServer(engine)

      engine.webDescriptor.routes.map(_.path) shouldBe Vector("/web/notice-board", "/web")
      val alias = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board"))).unsafeRunSync()
      val default = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board/assets/app.css"))).unsafeRunSync()

      alias.status.code shouldBe 200
      alias.as[String].unsafeRunSync() should include ("Implicit Notice Board")
      default.status.code shouldBe 200
      default.as[String].unsafeRunSync() should include ("Implicit Notice Board")
      asset.status.code shouldBe 200
      asset.as[String].unsafeRunSync() should include ("implicit-notice-board")
    }

    "load Static Form Web App descriptor, templates, and assets from a CAR archive Web root" in {
      val path = _web_archive_fixture(
        "sample.car",
        Vector(
          "web/web-descriptor.yaml" -> "web:\n  apps:\n    - name: notice-board\n",
          "web/notice-board/index.html" -> "<h1>Archive Notice Board</h1>",
          "web/notice-board/notice/post-notice__200.html" -> "ARCHIVE SERVICE OPERATION",
          "web/notice-board/assets/app.css" -> ".archive-notice-board { color: #14532d; }\n"
        )
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(path.toString)
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val server = new Http4sHttpServer(engine)

      engine.webDescriptor.apps.map(_.name) should contain ("notice-board")
      server._web_resource_roots().map(_.name) shouldBe Vector(path.toString)
      server._component_web_app("notice-board", "notice-board", Vector.empty).unsafeRunSync().as[String].unsafeRunSync() should include ("Archive Notice Board")
      server._form_result_static_template("notice-board", "notice", "post-notice", 200) shouldBe Some("ARCHIVE SERVICE OPERATION")
      server._web_app_asset_content("notice-board", "app.css").map(_._1) shouldBe Some(".archive-notice-board { color: #14532d; }\n")
    }

    "render component entity edit page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityEdit(subsystem, component.name, "sales-order", "missing-id").map(_.body).getOrElse(fail("component entity edit admin is missing"))

      html should include (s"${component.name} Sales Order Edit")
      html should include ("Edit Sales Order")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentPath}/admin/entities/sales-order/missing-id/update")
      html should include ("name=\"id\"")
      html should include ("value=\"missing-id\"")
      html should include ("Update")
      html should include ("Cancel")
      html should include (s"/web/${componentPath}/admin/entities/sales-order/missing-id")
    }

    "render component entity edit page with hidden form context" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityEdit(
        subsystem,
        component.name,
        "sales-order",
        "missing-id",
        values = Map(
          "crud.origin.href" -> s"/web/${componentPath}/admin/entities/sales-order?page=2&pageSize=20",
          "crud.success.href" -> s"/web/${componentPath}/admin/entities/sales-order/missing-id",
          "paging.page" -> "2",
          "paging.pageSize" -> "20",
          "search.status" -> "open",
          "etag" -> "v1"
        )
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      html should include ("type=\"hidden\" name=\"crud.origin.href\"")
      html should include ("type=\"hidden\" name=\"crud.success.href\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"paging.pageSize\" value=\"20\"")
      html should include ("type=\"hidden\" name=\"search.status\" value=\"open\"")
      html should include ("type=\"hidden\" name=\"etag\" value=\"v1\"")
      html should not include ("crud.origin.href=")
      html should not include ("search.status=")
    }

    "render component entity new page contract" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      val html = StaticFormAppRenderer.renderComponentAdminEntityNew(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      html should include (s"${component.name} Sales Order New")
      html should include ("New Sales Order")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentPath}/admin/entities/sales-order/create")
      html should include ("name=\"fields\"")
      html should include ("Use one name=value pair per line")
      html should include ("Create")
      html should include ("Cancel")
      html should include (s"/web/${componentPath}/admin/entities/sales-order")
    }

    "render component entity new page from CML schema descriptor without WebDescriptor" in {
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
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))

      val html = StaticFormAppRenderer.renderComponentAdminEntityNew(subsystem, "notice_board", "notice").map(_.body).getOrElse(fail("component entity new admin is missing"))

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
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(bootstrapped))

      val html = StaticFormAppRenderer.renderComponentAdminEntityNew(subsystem, "generated_schema_component", "order").map(_.body).getOrElse(fail("component entity new admin is missing"))

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
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()

      val html = StaticFormAppRenderer.renderComponentAdminEntityNew(
        subsystem,
        "notice_board",
        "notice",
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity new admin is missing"))

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

      val definition = parse(StaticFormAppRenderer
        .renderComponentAdminEntityFormDefinition(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      val fields = definition.hcursor.downField("fields")
      fields.downN(0).downField("label").as[String].toOption shouldBe Some("Sender")
      fields.downN(0).downField("placeholder").as[String].toOption shouldBe Some("Your name")
      fields.downN(0).downField("validation").downField("minLength").as[Int].toOption shouldBe Some(1)
      fields.downN(1).downField("label").as[String].toOption shouldBe Some("Body")
      fields.downN(1).downField("type").as[String].toOption shouldBe Some("textarea")

      val html = StaticFormAppRenderer
        .renderComponentAdminEntityNew(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      html should include ("""<label class="form-label" for="new-field-sender-name">Sender</label>""")
      html should include ("""<label class="form-label" for="new-field-body">Body</label>""")
      html should include ("""placeholder="Your name"""")
      html should include ("""minlength="1"""")
    }

    "redisplay admin entity create validation errors before dispatching" in {
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
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "id=notice_1&title=hello&status=archived"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 400
      html should include ("New Notice")
      html should include ("Validation failed.")
      html should include ("archived is not an allowed value for Publication status.")
      html should include ("is-invalid")
      html should include ("value=\"notice_1\"")
      html should include ("value=\"hello\"")
    }

    "validate admin entity create and update POST values against Schema hints" in {
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val createResponse = server
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "title=Hi&author="),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val createHtml = createResponse.as[String].unsafeRunSync()

      createResponse.status.code shouldBe 400
      createHtml should include ("Validation failed.")
      createHtml should include ("admin-feedback")
      createHtml should include ("Title must be at least 3 characters.")
      createHtml should include ("Author is required.")
      createHtml should include ("is-invalid")

      val updateResponse = server
        ._submit_component_admin_entity_update(
          _post_form_request("/form/notice-board/admin/entities/notice/notice_1/update", "title=No&author="),
          "notice-board",
          "notice",
          "notice_1"
        )
        .unsafeRunSync()
      val updateHtml = updateResponse.as[String].unsafeRunSync()

      updateResponse.status.code shouldBe 400
      updateHtml should include ("Validation failed.")
      updateHtml should include ("admin-feedback")
      updateHtml should include ("Title must be at least 3 characters.")
      updateHtml should include ("Author is required.")
      updateHtml should include ("is-invalid")
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
      html should include ("result.status")
      html should include ("result.ok")
      html should include ("result.body")
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
      html should include ("result.status")
      html should include ("result.ok")
      html should include ("result.body")
      html should include ("status")
      html should include ("draft")
      html should include ("/web/admin/admin/entities/sales-order")
      html should include ("/web/admin/admin/entities/sales-order/new")
    }

    "render component entity edit page from merged Schema and WebDescriptor controls" in {
      val subsystem = _management_console_fixture_subsystem()
      val recordId = _notice_fixture_component(subsystem).
        entitySpace.
        entity[_NoticeEntity]("notice").
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

      val html = StaticFormAppRenderer.renderComponentAdminEntityEdit(
        subsystem,
        "notice_board",
        "notice",
        recordId,
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      html should include ("id=\"field-id\"")
      html should include ("readonly")
      html should include ("value=\"board update\"")
      html should include ("placeholder=\"Descriptor title placeholder.\"")
      html should include ("Descriptor title help.")
      html should include ("type=\"hidden\" id=\"field-author\" name=\"author\" value=\"alice\"")
    }

    "render component data administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminData(subsystem, component.name).map(_.body).getOrElse(fail("component data admin is missing"))

      html should include (s"${component.name} Data Administration")
      html should include ("Data CRUD")
      html should include ("class=\"card admin-card")
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
        html should include ("<th>id</th><th>action</th><th>actor</th><th>Actions</th>")
        html should include ("class=\"card admin-card")
        html should include ("class=\"table table-sm table-hover align-middle\"")
        html should include ("class=\"btn-group btn-group-sm\"")
        html should include ("created")
        html should include ("alice")
        html should include ("/web/notice-board/admin/data/audit/audit_1")
        firstPage should include ("Page 1")
        firstPage should include ("page=2&amp;pageSize=1")
        secondPage should include ("Page 2")
        secondPage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/notice-board/admin/data/audit?page=3&amp;pageSize=1\">Next")
        totalPage should include ("total 2")
        totalPage should include ("includeTotal=true")
        unsupportedTotalPage should include ("alert-warning")
        unsupportedTotalPage should include ("admin-feedback")
        unsupportedTotalPage should include ("total count is not available for data.audit")
        detail should include ("created")
        detail should include ("alice")
        detail should include ("class=\"card admin-card")
        detail should include ("class=\"btn btn-primary\"")
        edit should include ("name=\"action\"")
        edit should include ("value=\"created\"")
        edit should include ("class=\"admin-form\"")
        newly should include ("/form/notice-board/admin/data/audit/create")
        newly should include ("class=\"admin-form\"")
      }
    }

    "render admin CRUD forms from WebDescriptor field controls" in {
      val fixture = _data_fixture()
      val descriptor = _data_schema_web_descriptor()
      _with_global_runtime(fixture.runtime) {
        val edit = StaticFormAppRenderer.renderComponentAdminDataEdit(
          fixture.subsystem,
          "notice_board",
          "audit",
          "audit_1",
          webDescriptor = descriptor
        ).map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = StaticFormAppRenderer.renderComponentAdminDataNew(
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

    "redirect component data create by admin form descriptor transition" in {
      val fixture = _data_fixture()
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
        val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
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
        _load_data_record(fixture.dataStoreSpace, "audit", "audit_3").getString("action") shouldBe Some("created")
        dispatcher.paths should contain ("/admin/data/create")
      }
    }

    "redisplay component data create form with submitted fields when admin stayOnError is enabled" in {
      val fixture = _data_fixture()
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
        val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
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

    "redisplay component data create form with descriptor validation errors before dispatch" in {
      val fixture = _data_fixture()
      _with_global_runtime(fixture.runtime) {
        val descriptor = _data_schema_web_descriptor()
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))
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
        dispatcher.paths should not contain ("/admin/data/create")
      }
    }

    "extract structured result metadata for form redirect templates" in {
      FormResultMetadata.fromBody("""{"id":"notice_1"}""").toTemplateValues shouldBe Map("result.id" -> "notice_1")
      FormResultMetadata.fromBody("""{"result":{"id":"notice_2"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_2")
      FormResultMetadata.fromBody("""{"item":{"id":"notice_3"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_3")
      FormResultMetadata.fromBody("created:notice_4").toTemplateValues shouldBe Map("result.id" -> "notice_4")
      FormResultMetadata.fromBody("""{"message":"created"}""").toTemplateValues shouldBe Map("result.message" -> "created")
      FormResultMetadata.fromBody("""{"result":{"outcome":"created","message":"Notice created"}}""").toTemplateValues shouldBe Map(
        "result.outcome" -> "created",
        "result.message" -> "Notice created"
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
    }

    "render component view administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminViews(subsystem, component.name).map(_.body).getOrElse(fail("component view admin is missing"))

      html should include (s"${component.name} View Administration")
      html should include ("View read")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("view definitions")
    }

    "render component view read page from a live ViewSpace fixture" in {
      val subsystem = _view_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminViewDetail(subsystem, "notice_board", "notice_view").map(_.body).getOrElse(fail("component view detail admin is missing"))

      html should include ("notice_board Notice View View")
      html should include ("Notice View metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_view")
      html should include ("notice")
      html should include ("recent")
      html should include ("Read result")
      html should include ("notice summary")
      html should include ("/web/notice-board/admin/views/notice-view/notice%20summary")
      html should include ("Result pages")
      html should not include ("Edit")
      html should not include ("New")
      html should not include ("Create")
      html should include ("/web/notice-board/admin/views")
    }

    "render component view instance detail page through context-aware read" in {
      val subsystem = _view_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", "notice_1").map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      html should include ("notice_board Notice View View Detail")
      html should include ("class=\"card admin-card")
      html should include ("notice_1")
      html should include ("label")
      html should include ("value")
      html should include ("notice detail notice_1")
      html should not include ("Edit")
      html should not include ("Update")
      html should include ("/web/notice-board/admin/views/notice-view")
    }

    "render component view instance detail with descriptor field schema" in {
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

      val html = StaticFormAppRenderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", "notice_1", descriptor).map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
    }

    "render component aggregate administration page" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      val html = StaticFormAppRenderer.renderComponentAdminAggregates(subsystem, component.name).map(_.body).getOrElse(fail("component aggregate admin is missing"))

      html should include (s"${component.name} Aggregate Administration")
      html should include ("Aggregate CRUD")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("aggregate definitions")
    }

    "render component aggregate read page from a live AggregateSpace fixture" in {
      val subsystem = _aggregate_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate").map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      html should include ("notice_board Notice Aggregate Aggregate")
      html should include ("Notice Aggregate metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_aggregate")
      html should include ("notice")
      html should include ("notice:notice")
      html should include ("Read result")
      html should include ("<th>id</th><th>label</th><th>status</th><th>Actions</th>")
      html should include ("notice_1")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
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
      html should include ("/form/notice-board/notice-aggregate/create-notice-aggregate")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate")
      html should not include ("approve-notice-aggregate__success.html")
      html should include ("/web/notice-board/admin/aggregates")
    }

    "render component aggregate list with descriptor field columns" in {
      val subsystem = _aggregate_fixture_subsystem()
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

      val html = StaticFormAppRenderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate", webDescriptor = descriptor).map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>note</th>")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
    }

    "render component aggregate instance detail page through context-aware read" in {
      val subsystem = _aggregate_fixture_subsystem()

      val html = StaticFormAppRenderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", "notice_1").map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      html should include ("notice_board Notice Aggregate Aggregate Detail")
      html should include ("class=\"card admin-card")
      html should include ("notice_1")
      html should include ("label")
      html should include ("value")
      html should include ("notice aggregate")
      html should include ("Instance operations")
      html should include ("aggregate id prefilled")
      html should include ("Read aggregate")
      html should include ("Run update command")
      html should not include ("Create aggregate")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate?")
      html should include ("/form/notice-board/notice-aggregate/read-notice-aggregate?")
      html should include ("id=notice_1")
      html should include ("crud.success.href=")
      html should not include ("textus.admin.principalId=system")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
    }

    "render component aggregate instance detail with descriptor field schema" in {
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

      val html = StaticFormAppRenderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", "notice_1", descriptor).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
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
      entityListRecord.getAny("items").map(_.toString).getOrElse("") should include ("notice_1")
      entityListRecord.getAny("items").map(_.toString).getOrElse("") should include ("board update")
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
      entityReadRecord.getString("label") shouldBe Some("board update")
      entityReadRecord.getAny("item").map(_.toString).getOrElse("") should include (entityId)
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
        dataListRecord.getAny("items").map(_.toString).getOrElse("") should include ("audit_1")
        dataListRecord.getAny("items").map(_.toString).getOrElse("") should include ("created")
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
        dataReadRecord.getString("label") shouldBe Some("audit_1")
        dataReadRecord.getAny("item").map(_.toString).getOrElse("") should include ("audit_1")
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
      viewReadRecord.getAny("items").map(_.toString).getOrElse("") should include ("notice summary")
      viewReadRecord.getAny("items").map(_.toString).getOrElse("") should include ("label")
      viewReadRecord.getInt("page") shouldBe Some(1)
      viewReadRecord.getInt("pageSize") shouldBe Some(20)
      viewReadRecord.getBoolean("hasNext") shouldBe Some(false)
      viewReadRecord.getInt("total") shouldBe None
      val totalViewReadRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalViewReadRecord.getInt("total") shouldBe None
      totalViewReadRecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalViewReadRecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalViewReadRecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for view.notice-view")
      _admin_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on view should fail: ${other}")
      }
      val countedViewSubsystem = _view_fixture_subsystem(totalCountCapability = TotalCountCapability.Supported)
      val countedViewReadRecord = _admin_record_response(countedViewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedViewReadRecord.getInt("total") shouldBe Some(2)
      countedViewReadRecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedViewReadRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "page" -> "4", "pageSize" -> "9")
      pagedViewReadRecord.getInt("page") shouldBe Some(4)
      pagedViewReadRecord.getInt("pageSize") shouldBe Some(9)
      val firstViewPage = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "pageSize" -> "1")
      firstViewPage.getString("fields") shouldBe Some("notice summary")
      firstViewPage.getBoolean("hasNext") shouldBe Some(true)
      val secondViewPage = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "page" -> "2", "pageSize" -> "1")
      secondViewPage.getString("fields") shouldBe Some("notice next")
      secondViewPage.getBoolean("hasNext") shouldBe Some(false)
      val viewInstanceRecord = _admin_record_response(viewSubsystem, "view", "read", "component" -> "notice-board", "view" -> "notice-view", "id" -> "notice_1")
      viewInstanceRecord.getString("kind") shouldBe Some("view.read")
      viewInstanceRecord.getString("id") shouldBe Some("notice_1")
      viewInstanceRecord.getString("label").getOrElse("") should include ("notice detail notice_1")
      viewInstanceRecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_1")
      viewInstanceRecord.getString("fields").getOrElse("") should include ("notice detail notice_1")

      val aggregateSubsystem = _aggregate_fixture_subsystem()
      val aggregateEngine = new HttpExecutionEngine(aggregateSubsystem)
      val aggregateRead = aggregateEngine.execute(HttpRequest.fromPath(HttpRequest.POST, "/admin/aggregate/read", form = Record.data("component" -> "notice-board", "aggregate" -> "notice-aggregate")))

      aggregateRead.code shouldBe 200
      aggregateRead.getString.getOrElse("") should include ("notice aggregate")
      val aggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate")
      aggregateReadRecord.getString("kind") shouldBe Some("aggregate.read")
      aggregateReadRecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregateReadRecord.getAny("values").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregateReadRecord.getAny("items").map(_.toString).getOrElse("") should include ("notice_1")
      aggregateReadRecord.getAny("items").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregateReadRecord.getInt("total") shouldBe None
      val totalAggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalAggregateReadRecord.getInt("total") shouldBe None
      totalAggregateReadRecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalAggregateReadRecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalAggregateReadRecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for aggregate.notice-aggregate")
      _admin_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on aggregate should fail: ${other}")
      }
      val countedAggregateSubsystem = _aggregate_fixture_subsystem(totalCountCapability = TotalCountCapability.Supported)
      val countedAggregateReadRecord = _admin_record_response(countedAggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedAggregateReadRecord.getInt("total") shouldBe Some(2)
      countedAggregateReadRecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedAggregateReadRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "page" -> "5", "pageSize" -> "11")
      pagedAggregateReadRecord.getInt("page") shouldBe Some(5)
      pagedAggregateReadRecord.getInt("pageSize") shouldBe Some(11)
      val firstAggregatePage = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "pageSize" -> "1")
      firstAggregatePage.getString("fields").getOrElse("") should include ("notice aggregate")
      firstAggregatePage.getBoolean("hasNext") shouldBe Some(true)
      val secondAggregatePage = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "page" -> "2", "pageSize" -> "1")
      secondAggregatePage.getString("fields").getOrElse("") should include ("notice next")
      secondAggregatePage.getBoolean("hasNext") shouldBe Some(false)
      val aggregateInstanceRecord = _admin_record_response(aggregateSubsystem, "aggregate", "read", "component" -> "notice-board", "aggregate" -> "notice-aggregate", "id" -> "notice_1")
      aggregateInstanceRecord.getString("kind") shouldBe Some("aggregate.read")
      aggregateInstanceRecord.getString("id") shouldBe Some("notice_1")
      aggregateInstanceRecord.getString("label") shouldBe Some("notice aggregate")
      aggregateInstanceRecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_1")
      aggregateInstanceRecord.getString("fields").getOrElse("") should include ("notice aggregate")
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

    "keep form control and security values out of operation arguments" in {
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("approved") shouldBe Some("true")
      submitted.getString("crud.success.href") shouldBe None
      submitted.getString("textus.admin.principalId") shouldBe None
      submitted.getString("textus.admin.privilege") shouldBe None
    }

    "preserve hidden form context for result templates without dispatching it as operation arguments" in {
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      html should include ("Context Result")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate")
      html should include ("2")
      html should include ("20")
      html should include ("1000")
      html should include ("phase12")
      html should include ("token-1")
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
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      dispatcher.paths.lastOption shouldBe Some("/job_control/job/await_job_result")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some("cncf-job-job-1")
      html should include ("created:notice_1")
      html should include ("result.id")
      html should include ("notice_1")
    }

    "render awaited job result through descriptor result template when static template is absent" in {
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      html should include ("Descriptor Await Result")
      html should include ("cncf-job-job-1")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
      dispatcher.paths.lastOption shouldBe Some("/job_control/job/await_job_result")
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

    "specify selector to Web HTML, Form API, and REST operation path mapping" in {
      val subsystem = _form_type_fixture_subsystem()

      val definition = StaticFormAppRenderer.renderOperationFormDefinition(
        subsystem,
        "notice_board",
        "notice",
        "post_secret_notice"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))

      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.notice.post-secret-notice")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form-api/notice-board/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/form/notice-board/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(0).downField("path").as[String].toOption shouldBe Some("/form/notice-board/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some("/form-api/notice-board/notice/post-secret-notice")
      org.goldenport.cncf.naming.NamingConventions.toNormalizedPath(
        "notice_board",
        "notice",
        "post_secret_notice"
      ) shouldBe "/notice-board/notice/post-secret-notice"
    }

    "dispatch Form API POST to the canonical REST operation request" in {
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = new Http4sHttpServer(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice?dryRun=true",
            "body=hello&accessToken=abc&crud.origin.href=/web/notice-board"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
      dispatcher.paths should contain ("/notice-board/notice/post-secret-notice")
      dispatcher.forms.last.getString("body") shouldBe Some("hello")
      dispatcher.forms.last.getString("accessToken") shouldBe Some("abc")
      dispatcher.forms.last.getString("crud.origin.href") shouldBe None
    }

    "keep internal operations invisible from HTML and Form API surfaces" in {
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Internal))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val htmlForm = StaticFormAppRenderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "post-secret-notice",
        descriptor
      )
      val apiResponse = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      descriptor.isFormEnabled(selector) shouldBe false
      htmlForm shouldBe None
      apiResponse.status.code shouldBe 404
    }

    "record minimal runtime hooks when Web dispatch crosses the operation adapter" in {
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = new Http4sHttpServer(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )
      val beforeHtml = RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total
      val beforeDsl = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total
      val beforeAuthorization = RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total

      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice", "body=hello"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
      RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total shouldBe (beforeHtml + 1)
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total shouldBe (beforeDsl + 1)
      RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total shouldBe (beforeAuthorization + 1)
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
      html should include ("/web/system/manual")
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
      console should include ("/web/system/manual")
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

    "apply app-scoped assets to the component HTML form index" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentPath,
          assets = WebDescriptor.Assets(
            css = Vector("/web/component/assets/forms.css"),
            js = Vector("/web/component/assets/forms.js")
          )
        )),
        form = Map(
          s"${componentPath}.service.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/operation.css"),
              js = Vector("/web/component/assets/operation.js")
            )
          )
        )
      )

      val html = StaticFormAppRenderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))

      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should not include ("/web/component/assets/operation.css")
      html should not include ("/web/component/assets/operation.js")
    }

    "apply app and form scoped assets to operation input forms" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicePath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentPath, servicePath, operationPath).mkString(".")
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentPath,
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
          s"${componentPath}.other.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/other.css"),
              js = Vector("/web/component/assets/other.js")
            )
          )
        )
      )

      val html = StaticFormAppRenderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor).map(_.body).getOrElse(fail("operation form is missing"))

      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should include ("/web/component/assets/operation.css")
      html should include ("/web/component/assets/operation.js")
      html should not include ("/web/component/assets/other.css")
      html should not include ("/web/component/assets/other.js")
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

    "render HTML operation form with query-provided initial fields" in {
      val subsystem = _aggregate_fixture_subsystem()

      val html = StaticFormAppRenderer.renderOperationForm(
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

      val html = StaticFormAppRenderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice",
        "post_secret_notice",
        descriptor,
        values = Map("body" -> "hello", "accessToken" -> "abc")
      ).map(_.body).getOrElse(fail("operation form is missing"))

      html should include ("<select")
      html should include ("<option value=\"hello\" selected>")
      html should include ("Notice body")
      html should include ("Descriptor body help.")
      html should include ("name=\"body\"")
      html should include ("type=\"hidden\"")
      html should include ("name=\"accessToken\"")
    }

    "serve operation form definition API from the same resolved Web schema" in {
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some(selector)
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("operation")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("POST")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form-api/notice-board/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/form/notice-board/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(2).downField("path").as[String].toOption shouldBe Some("/form-api/notice-board/notice/post-secret-notice/validate")
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

    "serve operation form definition API from CML operation parameters when protocol parameters are empty" in {
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "search-notices",
            kind = "QUERY",
            inputType = "SearchNotices",
            outputType = "SearchNoticesResult",
            inputValueKind = "QUERY_VALUE",
            parameters = Vector(
              CmlOperationField("recipientName", "name", "1"),
              CmlOperationField("offset", "integer", "?"),
              CmlOperationField("limit", "integer", "?")
            )
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = "notice",
              operations = spec.OperationDefinitionGroup(
                operations = NonEmptyVector.of(_NoopOperation("search-notices"))
              )
            )
          )
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/search-notices"),
          "notice-board",
          "notice",
          "search-notices"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("recipientName")
      fields.downN(0).downField("required").as[Boolean].toOption shouldBe Some(true)
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("offset")
      fields.downN(1).downField("type").as[String].toOption shouldBe Some("number")
      fields.downN(1).downField("required").as[Boolean].toOption shouldBe Some(false)
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("limit")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("number")
      fields.downN(2).downField("required").as[Boolean].toOption shouldBe Some(false)
    }

    "serve admin entity form definition API from EntityRuntimeDescriptor schema" in {
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
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.entity.notice")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("entity")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/notice-board/admin/entities/notice/create")
      json.hcursor.downField("actions").downN(5).downField("path").as[String].toOption shouldBe Some("/form/notice-board/admin/entities/notice/{id}/update")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("body")
      fields.downN(1).downField("label").as[String].toOption shouldBe Some("Notice body")
      fields.downN(1).downField("type").as[String].toOption shouldBe Some("textarea")
      fields.downN(1).downField("help").as[String].toOption shouldBe Some("Notice body shown on the board.")
    }

    "serve admin entity update form definition API from detail view fields" in {
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewFields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_update_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice/notice_1/update"),
          "notice-board",
          "notice",
          "notice_1"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity update form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.entity.notice")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity-update")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/notice-board/admin/entities/notice/notice_1/update")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/notice-board/admin/entities/notice/notice_1/edit")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("title")
      fields.downN(2).downField("name").as[String].toOption shouldBe None
    }

    "allow anonymous admin form API by the develop anonymous admin default" in {
      val subsystem = _management_console_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
    }

    "deny anonymous admin form API when develop anonymous admin is disabled" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.WebDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 403
    }

    "deny anonymous admin form API in production operation mode" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 403
    }

    "deny anonymous component admin HTML route in production operation mode" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice"))
        .unsafeRunSync()

      response.status.code shouldBe 403
    }

    "allow authenticated component admin HTML route in production operation mode" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice?principalId=admin-test"))
        .unsafeRunSync()

      response.status.code shouldBe 200
    }

    "allow authenticated admin form API when develop anonymous admin is disabled" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.WebDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice?principalId=admin-test"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
    }

    "deny anonymous admin entity create POST in production operation mode before dispatch" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      response.status.code shouldBe 403
      dispatcher.paths should not contain ("/admin/entity/create")
    }

    "allow authenticated admin entity create POST in production operation mode" in {
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

      val response = server
        ._submit_component_admin_entity_create(
          _post_form_request(
            "/form/notice-board/admin/entities/notice/create?principalId=admin-test",
            "fields=id%3Dnotice_2%0Atitle%3Dnew+notice%0Aauthor%3Dbob"
          ),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
      dispatcher.paths should contain ("/admin/entity/create")
    }

    "serve admin entity form definition API from generated companion schema" in {
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
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(new ComponentFactory().bootstrap(component)))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/generated-schema-component/admin/entities/order"),
          "generated-schema-component",
          "order"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      val names = fields.flatMap(_.hcursor.downField("name").as[String].toOption)

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
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

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
      val status = _json_field(fields, "status")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.entity.notice")
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
      val fixture = _data_fixture()
      _with_global_runtime(fixture.runtime) {
        val server = new Http4sHttpServer(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit"),
            "notice-board",
            "audit"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data form definition JSON is invalid"))
        val fieldNames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/notice-board/admin/data/audit/new")
        fieldNames shouldBe Vector("id", "action", "actor")
      }
    }

    "serve admin data update form definition API from inferred data fields" in {
      val fixture = _data_fixture()
      _with_global_runtime(fixture.runtime) {
        val server = new Http4sHttpServer(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_update_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit/audit_1/update"),
            "notice-board",
            "audit",
            "audit_1"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data update form definition JSON is invalid"))
        val fieldNames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data-update")
        json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/notice-board/admin/data/audit/audit_1/update")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/notice-board/admin/data/audit/audit_1/edit")
        json.hcursor.downField("actions").downN(3).downField("path").as[String].toOption shouldBe Some("/form/notice-board/admin/data/audit/audit_1/update")
        fieldNames shouldBe Vector("id", "action", "actor")
      }
    }

    "serve admin data form definition API from merged inferred data fields and WebDescriptor controls" in {
      val fixture = _data_fixture()
      val descriptor = _data_schema_web_descriptor(includeNote = false)
      _with_global_runtime(fixture.runtime) {
        val server = new Http4sHttpServer(new HttpExecutionEngine(fixture.subsystem, Some(descriptor)))

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
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.data.audit")
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
      val subsystem = _view_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      val fields = _json_fields(json)

      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "label", "note")
    }

    "serve admin view form definition API from resolved view schema" in {
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.view.notice-view")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("view")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-view")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/notice-board/admin/views/notice-view")
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
      val subsystem = _aggregate_fixture_subsystem()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      val fields = _json_fields(json)

      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "label", "status")
    }

    "serve admin aggregate form definition API from resolved aggregate schema" in {
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")

      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("notice-board.aggregate.notice-aggregate")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("aggregate")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-aggregate")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/notice-board/admin/aggregates/notice-aggregate")
      json.hcursor.downField("actions").downN(0).downField("name").as[String].toOption shouldBe Some("list")
      json.hcursor.downField("actions").downN(1).downField("name").as[String].toOption shouldBe Some("detail")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some("/web/notice-board/admin/aggregates/notice-aggregate/{id}")
      json.hcursor.downField("actions").downN(2).downField("name").as[String].toOption shouldBe None
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("label")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("status")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("select")
      fields.downN(2).downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "published"))
    }

    "validate operation form API input without executing the operation" in {
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice/validate", "accessToken=abc&extra=value"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val invalidJson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      invalid.status.code shouldBe 200
      invalid.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      invalidJson.hcursor.downField("selector").as[String].toOption shouldBe Some(selector)
      invalidJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      invalidJson.hcursor.downField("errors").downN(0).downField("field").as[String].toOption shouldBe Some("body")
      invalidJson.hcursor.downField("errors").downN(0).downField("code").as[String].toOption shouldBe Some("required")
      invalidJson.hcursor.downField("warnings").downN(0).downField("field").as[String].toOption shouldBe Some("extra")

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
      val validJson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      valid.status.code shouldBe 200
      validJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validJson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
      validJson.hcursor.downField("warnings").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "validate operation form API datatype values and multiplicity" in {
      val component = new org.goldenport.cncf.component.Component() {}
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = "notice",
              operations = spec.OperationDefinitionGroup(
                operations = NonEmptyVector.of(
                  _NoopOperation("validate-fields", Vector("count", "published", "publishedAt", "status", "tags"))
                )
              )
            )
          )
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

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
      val invalidJson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))
      val errors = invalidJson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      val errorFields = errors.flatMap(_.hcursor.downField("field").as[String].toOption)
      val errorCodes = errors.flatMap(_.hcursor.downField("code").as[String].toOption)

      invalidJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorFields should contain allOf ("count", "published", "publishedAt", "status", "tags")
      errorCodes should contain ("datatype")
      errorCodes should contain ("invalid-value")
      errorCodes should contain ("multiplicity")

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
      val validJson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      validJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validJson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "serve and validate operation form validation hints" in {
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val definition = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/validate-hints"),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val definitionJson = parse(definition.as[String].unsafeRunSync()).getOrElse(fail("hint definition JSON is invalid"))
      val codeField = definitionJson.hcursor.downField("fields").downN(0)
      val countField = definitionJson.hcursor.downField("fields").downN(1)

      codeField.downField("validation").downField("minLength").as[Int].toOption shouldBe Some(2)
      codeField.downField("validation").downField("maxLength").as[Int].toOption shouldBe Some(4)
      codeField.downField("validation").downField("pattern").as[String].toOption shouldBe Some("^[A-Z0-9]+$")
      countField.downField("validation").downField("min").as[BigDecimal].toOption shouldBe Some(BigDecimal(0))
      countField.downField("validation").downField("max").as[BigDecimal].toOption shouldBe Some(BigDecimal(100))

      val html = StaticFormAppRenderer.renderOperationForm(
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
      val invalidJson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("hint validation JSON is invalid"))
      val errorCodes = invalidJson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("code").as[String].toOption)

      invalidJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorCodes should contain ("max-length")
      errorCodes should contain ("pattern")
      errorCodes should contain ("max")
    }

    "keep Schema validation constraints when WebDescriptor attempts to relax them" in {
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem, Some(descriptor)))

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
      val invalidJson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("relax validation JSON is invalid"))
      val errors = invalidJson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      val errorPairs = errors.flatMap { json =>
        for {
          field <- json.hcursor.downField("field").as[String].toOption
          code <- json.hcursor.downField("code").as[String].toOption
        } yield field -> code
      }

      invalidJson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorPairs should contain ("code" -> "min-length")
      errorPairs should contain ("count" -> "min")
    }

    "redisplay operation form validation hint errors before HTML dispatch" in {
      val (subsystem, descriptor) = _validation_hints_fixture()
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("DISPATCHED", StandardCharsets.UTF_8)
        )
      )
      val server = new Http4sHttpServer(
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
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 400
      html should include ("Validation failed.")
      html should include ("code must be at most 4 characters.")
      html should include ("code does not match the required pattern.")
      html should include ("count must be less than or equal to 100.")
      html should include ("value=\"toolong\"")
      html should include ("value=\"101\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"3\"")
      html should include ("type=\"hidden\" name=\"csrf\" value=\"token-1\"")
      html should not include ("DISPATCHED")
    }

    "render aggregate operation form with admin descriptor field controls" in {
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

      val html = StaticFormAppRenderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice_aggregate",
        "approve_notice_aggregate",
        descriptor,
        values = Map("id" -> "notice_1", "approved" -> "true")
      ).map(_.body).getOrElse(fail("aggregate operation form is missing"))

      html should include ("type=\"hidden\"")
      html should include ("name=\"id\"")
      html should include ("value=\"notice_1\"")
      html should include ("<select")
      html should include ("name=\"approved\"")
      html should include ("<option value=\"true\" selected>true</option>")
      html should include ("<option value=\"false\">false</option>")
    }

    "redirect HTML form submissions by descriptor transition while form-api returns operation response" in {
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
      val server = new Http4sHttpServer(engine)

      val redirected = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()
      val api = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()

      redirected.status.code shouldBe 303
      redirected.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      api.status.code shouldBe 200
      api.as[String].unsafeRunSync() should include ("aggregate-updated:notice_1")
    }

    "render operation form result through descriptor result template" in {
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
      val server = new Http4sHttpServer(engine)

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Custom Result")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "render operation form result route through descriptor result template when static template is absent" in {
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

      val html = server
        ._operation_form_result(
          _get_request("/form/notice-board/notice-aggregate/approve-notice-aggregate/result?id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Descriptor Result Route")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "render operation form result through static success template convention before descriptor template" in {
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
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
      val server = new Http4sHttpServer(engine)

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static Success")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Descriptor Result")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "prefer exact static status result template over static success template" in {
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
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = new Http4sHttpServer(engine)

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200 Exact")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Static Success Alias")
      html should not include ("<textus-result-view")
    }

    "render operation form result through static status template convention" in {
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
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
      val server = new Http4sHttpServer(engine)

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("<textus-property-list")
    }

    "render operation form result through common static status template convention" in {
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
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
      val server = new Http4sHttpServer(engine)

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("Common Static 200")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
    }

    "render form continuation through static status template convention" in {
      val rows = (1 to 21).map { i =>
        f"""{"title":"Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responseBody = s"""{"data":${rows},"fetched_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" href="paging.href"></textus-result-table>
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
          Bag.text(responseBody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continueHref = """href="([^"]*page=2&amp;pageSize=20)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("continuation link is missing"))
      val page2 = server
        ._operation_form_continue(
          _get_request(continueHref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continueHref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      page1 should include ("Matching notices")
      page1 should include ("Paging Notice 01")
      page1 should include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Paging Notice 21")
      page2 should include ("Page 2")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus-result-table")
    }

    "render form continuation with explicit total-count paging" in {
      val rows = (1 to 21).map { i =>
        f"""{"title":"Total Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responseBody = s"""{"data":${rows},"total_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
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
          Bag.text(responseBody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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
      val continueHref = """href="([^"]*page=2&amp;pageSize=20&amp;includeTotal=true)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("total-count continuation link is missing"))
      val page2 = server
        ._operation_form_continue(
          _get_request(continueHref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continueHref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      page1 should include ("Matching notices")
      page1 should include ("Total Paging Notice 01")
      page1 should include ("includeTotal=true")
      page1 should not include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Total Paging Notice 21")
      page2 should include ("includeTotal=true")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus-result-table")
    }

    "render operation failure through exact static status template before error template" in {
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
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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

      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 400 Exact")
      html should include ("invalid approval")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("form id: notice_1")
      html should include ("form page: </p>")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"csrf\" value=\"token-1\"")
      html should not include ("Static Error Alias")
      html should not include ("${crud.origin.href}")
      html should not include ("<textus:hidden-context")
      html should not include ("Submitted Values")
    }

    "render operation failure through common static error template when status template is absent" in {
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

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
      val root = Files.createTempDirectory("cncf-web-error-template-")
      val appRoot = root.resolve("notice-board")
      Files.createDirectories(appRoot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        appRoot.resolve("__404.html"),
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
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 404
      html should include ("Notice Board Missing")
      html should include ("404")
      html should include ("/web/notice-board/missing")
      html should include ("Static Form App not found")
    }

    "render Web HTML errors through global static error template convention" in {
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

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

    "redisplay the operation form with submitted values when stayOnError is enabled" in {
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
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      html should include ("HTML form operation")
      html should include ("error.status")
      html should include ("400")
      html should include ("invalid approval")
      html should include ("value=\"notice_1\"")
    }

    "redisplay operation form validation errors before dispatching HTML submit" in {
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
      val server = new Http4sHttpServer(
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
      val html = response.as[String].unsafeRunSync()

      response.status.code shouldBe 400
      html should include ("HTML form operation")
      html should include ("Validation failed.")
      html should include ("Notice body is required.")
      html should include ("is-invalid")
      html should include ("value=\"abc\"")
      html should not include ("DISPATCHED")
    }

    "merge schema-driven form fields with additional fields on submit" in {
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = new Http4sHttpServer(engine, operationDispatcherOption = Some(dispatcher))

      val html = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1&fields=approved%3Dtrue"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).flatMap(_.as[String]).unsafeRunSync()

      html should include ("aggregate-updated:notice_1")
      dispatcher.forms.lastOption.map(_.getString("approved")) shouldBe Some(Some("true"))
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

    "render form result properties from operation response and submitted values" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "body" -> "hello",
            "recipient" -> "taro"
          )
        ),
        201,
        "application/json",
        """{"id":"notice_1","outcome":"created","message":"created","actions":[{"name":"detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(properties).body

      html should include ("notice-board.notice.post-notice Result")
      html should include ("result.id")
      html should include ("notice_1")
      html should include ("result.outcome")
      html should include ("created")
      html should include ("result.message")
      html should include ("result.action.primary.href")
      html should include ("/web/notice-board/admin/entities/notice/notice_1")
      html should include ("form.body")
      html should include ("hello")
      html should include ("form.recipient")
      html should include ("taro")
      html should not include ("${operation.label}")
    }

    "render textus action link from operation result actions" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        201,
        "application/json",
        """{"outcome":"created","actions":[{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"},{"name":"approve","label":"Approve","href":"/form/notice-board/notice/approve","method":"POST"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.approve" class="btn btn-warning"></textus-action-link>
          |  <textus:action-link source="result.action.missing"></textus:action-link>
          |</article>""".stripMargin
      ).body

      html should include ("""<a class="btn btn-primary" href="/web/notice-board/admin/entities/notice/notice_1">Open detail</a>""")
      html should include ("""<form method="post" action="/form/notice-board/notice/approve" class="d-inline">""")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"1\"")
      html should include ("""<button type="submit" class="btn btn-warning">Approve</button>""")
      html should not include ("<textus-action-link")
      html should not include ("<textus:action-link")
      html should not include ("result.action.missing")
    }

    "render action widgets with hidden page context for post actions" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "crud.origin.href" -> "/web/notice-board/admin/entities/notice?page=2",
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "csrf" -> "token-1",
            "recipientName" -> "bob"
          )
        ),
        201,
        "application/json",
        """{"actions":[{"name":"approve","label":"Approve","href":"/form/notice-board/notice/approve","method":"POST"},{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.approve" class="btn btn-warning"></textus:action-link>
          |  <textus:action-form source="result.action.approve" class="btn btn-danger" label="Approve again"></textus:action-form>
          |  <textus-action-form source="result.action.detail" class="btn btn-outline-primary" method="POST" context="false"></textus-action-form>
          |</article>""".stripMargin
      ).body

      html should include ("""<form method="post" action="/form/notice-board/notice/approve" class="d-inline"><input type="hidden" name="crud.origin.href" value="/web/notice-board/admin/entities/notice?page=2">""")
      html should include ("""<input type="hidden" name="paging.page" value="2">""")
      html should include ("""<input type="hidden" name="paging.pageSize" value="20">""")
      html should include ("""<input type="hidden" name="csrf" value="token-1">""")
      html should include ("""<button type="submit" class="btn btn-warning">Approve</button>""")
      html should include ("""<button type="submit" class="btn btn-danger">Approve again</button>""")
      html should include ("""<form method="post" action="/web/notice-board/admin/entities/notice/notice_1" class="d-inline"><button type="submit" class="btn btn-outline-primary">Open detail</button></form>""")
      html should not include ("""name="recipientName"""")
      html should not include ("<textus:action-link")
      html should not include ("<textus:action-form")
      html should not include ("<textus-action-form")
    }

    "render textus hidden context inputs from page context without operation values" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "recipientName" -> "bob",
            "crud.origin.href" -> "/web/notice-board/admin/entities/notice?page=2",
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "search.recipientName" -> "bob",
            "csrf" -> "token-1",
            "version" -> "7",
            "ui.tab" -> "summary",
            "empty.context" -> ""
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<form method="post" action="/form/notice-board/notice/search-notices">
          |  <textus:hidden-context keys="ui.tab,empty.context,missing"></textus:hidden-context>
          |  <textus-hidden-context></textus-hidden-context>
          |</form>""".stripMargin
      ).body

      html should include ("""<input type="hidden" name="crud.origin.href" value="/web/notice-board/admin/entities/notice?page=2">""")
      html should include ("""<input type="hidden" name="paging.page" value="2">""")
      html should include ("""<input type="hidden" name="paging.pageSize" value="20">""")
      html should include ("""<input type="hidden" name="search.recipientName" value="bob">""")
      html should include ("""<input type="hidden" name="csrf" value="token-1">""")
      html should include ("""<input type="hidden" name="version" value="7">""")
      html should include ("""<input type="hidden" name="ui.tab" value="summary">""")
      html should not include ("""name="recipientName"""")
      html should not include ("""name="empty.context"""")
      html should not include ("<textus:hidden-context")
      html should not include ("<textus-hidden-context")
    }

    "render await action link from asynchronous command job result" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "text/plain",
        "cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa"
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <p>${result.job.id}</p>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.await" class="btn btn-outline-primary"></textus-action-link>
          |</article>""".stripMargin
      ).body

      html should include ("cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa")
      html should include ("""<form method="post" action="/form/notice-board/notice/post-notice/jobs/cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa/await" class="d-inline">""")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"1\"")
      html should include ("""<button type="submit" class="btn btn-primary">Check result</button>""")
      html should include ("""<button type="submit" class="btn btn-outline-primary">Check result</button>""")
      html should not include ("<textus:action-link")
      html should not include ("<textus-action-link")
    }

    "render job ticket and job actions for application job result UX" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"jobId":"cncf-job-job-1","jobStatus":"running","message":"Queued"}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:job-ticket></textus:job-ticket>
          |  <textus-job-actions actions="await"></textus-job-actions>
          |</article>""".stripMargin
      ).body

      html should include ("textus-job-ticket")
      html should include ("cncf-job-job-1")
      html should include ("running")
      html should include ("Queued")
      html should include ("textus-job-actions")
      _count_occurrences(html, "/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await") shouldBe 2
      html should not include ("<textus:job-ticket")
      html should not include ("<textus-job-actions")
    }

    "render system job ticket page with fixed system await link" in {
      val html = StaticFormAppRenderer.renderSystemJobTicket("cncf-job-job-1").body

      html should include ("textus-job-ticket")
      html should include ("cncf-job-job-1")
      html should include ("/web/system/jobs/cncf-job-job-1/await")
      html should include ("Check result")
      html should not include ("<textus:job-ticket")
    }

    "render detail action link from command result id" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1"}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.detail" class="btn btn-outline-primary"></textus-action-link>
          |</article>""".stripMargin
      ).body

      html should include ("""<a class="btn btn-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should include ("""<a class="btn btn-outline-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should not include ("<textus:action-link")
      html should not include ("<textus-action-link")
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

    "render textus result table from operation response body fields" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.pageSize" -> "1"
          )
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello","sender_name":"alice"},{"subject":"World","sender_name":"bob"}],"total_count":2}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <p>${result.totalCount}</p>
          |  <p>${paging.total}</p>
          |  <textus-result-table source="result.body.data"></textus-result-table>
          |</article>""".stripMargin
      ).body

      html should include ("<table")
      html should include ("subject")
      html should include ("sender_name")
      html should include ("Hello")
      html should not include ("bob")
      html should include ("<p>2</p>")
      html should not include ("result.totalCount")
      html should not include ("paging.total")
      html should include ("/form/notice-board/notice/search-notices/result?page=2&amp;pageSize=1")
      html should not include ("Page 1")
      html should not include ("<textus-result-table")
    }

    "render textus result table from result body shorthand" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data"></textus-result-table></article>"""
      ).body

      html should include ("<td>Hello</td>")
      html should not include ("<textus-result-table")
    }

    "render standalone textus pagination from shared paging metadata" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "paging.total" -> "45",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/result-1?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:pagination></textus:pagination>
          |  <textus-pagination page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href" has-next="paging.hasNext"></textus-pagination>
          |</article>""".stripMargin
      ).body

      html should include ("""<nav aria-label="Result pages">""")
      html should include ("""page=1&amp;pageSize=20""")
      html should include ("""page=3&amp;pageSize=20""")
      html should include ("""<li class="page-item active"><a class="page-link" href="/form/notice-board/notice/search-notices/continue/result-1?page=2&amp;pageSize=20">2</a></li>""")
      html should not include ("<textus:pagination")
      html should not include ("<textus-pagination")
    }

    "render standalone textus pagination without total count" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.href" -> "/form/notice-board/notice/search-notices/result?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "false"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus:pagination></textus:pagination></article>"""
      ).body

      html should include ("Page 1")
      html should include ("""<li class="page-item disabled"><a class="page-link" href="/form/notice-board/notice/search-notices/result?page=2&amp;pageSize=20">Next</a></li>""")
      html should not include ("<textus:pagination")
    }

    "render textus record card with CML summary columns" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "get-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body", "notice", "summary") -> columns)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus:record-card source="result.body" entity="notice" view="summary"></textus:record-card></article>"""
      ).body

      html should include ("class=\"card h-100 textus-record-card\"")
      html should include ("<h3 class=\"h5 card-title\">Phase12</h3>")
      html should include ("<dt class=\"col-sm-4\">Title</dt><dd class=\"col-sm-8\">Phase12</dd>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should not include ("Static form validation")
      html should not include ("<textus:record-card")
    }

    "render textus card list with shared paging metadata" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "1",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/result-1?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"},{"id":"notice_2","title":"Hidden","content":"Second","recipient_name":"Alice"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body.data", "notice", "summary") -> columns)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus:card-list source="result.body" entity="notice" view="summary"></textus:card-list></article>"""
      ).body

      html should include ("row row-cols-1 row-cols-md-2 g-3 mt-3")
      html should include ("<h3 class=\"h5 card-title\">Phase12</h3>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should include ("Page 1")
      html should include ("""page=2&amp;pageSize=1""")
      html should not include ("Hidden")
      html should not include ("Static form validation")
      html should not include ("<textus:card-list")
    }

    "render summary card and feedback widgets" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "result.count" -> "42",
            "result.message" -> "Notices loaded",
            "error.message" -> "Search failed"
          )
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus:summary-card title="Matches" value="${result.count}" subtitle="result.message" variant="success"></textus:summary-card>
          |  <textus-alert title="Status" message="result.message" variant="info"></textus-alert>
          |  <textus:alert source="error.message" variant="error"></textus:alert>
          |  <textus:empty-state source="result.body" message="No notices"></textus:empty-state>
          |  <textus-empty-state source="result.missing" message="No missing records"></textus-empty-state>
          |</article>""".stripMargin
      ).body

      html should include ("class=\"card h-100 textus-summary-card border-success\"")
      html should include ("<strong class=\"display-6 text-success\">42</strong>")
      html should include ("Notices loaded")
      html should include ("class=\"alert alert-info textus-alert\"")
      html should include ("class=\"alert alert-danger textus-alert\"")
      html should include ("Search failed")
      html should include ("No missing records")
      html should not include ("No notices")
      html should not include ("<textus:summary-card")
      html should not include ("<textus-alert")
      html should not include ("<textus:empty-state")
    }

    "render namespace and HTML-compatible widget notation through the same contract" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.href" -> "/form/notice-board/notice/search-notices/result?page={page}&pageSize={pageSize}",
            "result.count" -> "1",
            "result.message" -> "ready"
          )
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <textus-record-card source="result.body" columns="title,recipient_name"></textus-record-card>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |  <textus:card title="Widget Card" subtitle="result.message"><p>${result.count} record</p></textus:card>
          |  <textus-summary-card title="Matches" value="result.count"></textus-summary-card>
          |  <textus:alert message="result.message"></textus:alert>
          |  <textus-pagination></textus-pagination>
          |</article>""".stripMargin
      ).body

      html should include ("textus-record-card")
      html should include ("row row-cols-1 row-cols-md-2")
      html should include ("textus-card")
      html should include ("Widget Card")
      html should include ("textus-summary-card")
      html should include ("textus-alert")
      html should include ("Result pages")
      html should not include ("<textus-record-card")
      html should not include ("<textus:card-list")
      html should not include ("<textus:card")
      html should not include ("<textus-summary-card")
      html should not include ("<textus:alert")
      html should not include ("<textus-pagination")
    }

    "complete local widget assets for HTML document templates that use Textus widgets" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head>
          |  <meta charset="utf-8">
          |  <title>Search result</title>
          |</head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/bootstrap.bundle.min.js")
      html should include ("/web/assets/textus-widgets.css")
      html should include ("/web/assets/textus-widgets.js")
      html should include ("row row-cols-1 row-cols-md-2")
      html should not include ("<textus:card-list")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("</head>")
      html.indexOf("/web/assets/textus-widgets.css") should be < html.indexOf("</head>")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("/web/assets/textus-widgets.css")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("</body>")
      html.indexOf("/web/assets/textus-widgets.js") should be < html.indexOf("</body>")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("/web/assets/textus-widgets.js")
    }

    "not duplicate existing local widget assets in HTML document templates" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head>
          |  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
          |  <link href="/web/assets/textus-widgets.css" rel="stylesheet">
          |</head>
          |<body>
          |  <textus-result-table source="result.body"></textus-result-table>
          |  <script src="/web/assets/bootstrap.bundle.min.js"></script>
          |  <script src="/web/assets/textus-widgets.js"></script>
          |</body>
          |</html>""".stripMargin
      ).body

      _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
      _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
      html should not include ("<textus-result-table")
    }

    "leave HTML document templates without Textus widgets unchanged by asset completion" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "plain"
        ),
        200,
        "text/plain",
        "ok"
      )
      val template =
        """<!doctype html>
          |<html lang="en">
          |<head><title>Plain</title></head>
          |<body><p>No widgets</p></body>
          |</html>""".stripMargin

      val html = StaticFormAppRenderer.renderFormResult(properties, template).body

      html shouldBe template
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "honor descriptor-disabled result asset auto-completion" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(autoComplete = false)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      html should include ("row row-cols-1 row-cols-md-2")
      html should not include ("<textus:card-list")
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "insert descriptor-declared result assets without duplicating framework assets" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector(
            "/web/assets/bootstrap.min.css",
            "/web/assets/textus-widgets.css"
          ),
          declaredJs = Vector(
            "/web/assets/bootstrap.bundle.min.js",
            "/web/assets/textus-widgets.js"
          )
        )
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus-result-table source="result.body"></textus-result-table>
          |</body>
          |</html>""".stripMargin
      ).body

      html should include ("<table")
      html should not include ("<textus-result-table")
      _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
      _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
    }

    "insert descriptor app assets after framework assets in full HTML result pages" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      html should include ("row row-cols-1 row-cols-md-2")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("/web/assets/textus-widgets.css")
      html.indexOf("/web/assets/textus-widgets.css") should be < html.indexOf("/web/notice-board/notice-board/assets/app.css")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("/web/assets/textus-widgets.js")
      html.indexOf("/web/assets/textus-widgets.js") should be < html.indexOf("/web/notice-board/notice-board/assets/app.js")
    }

    "insert descriptor app assets even when framework auto-completion is disabled" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          autoComplete = false,
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus-result-table source="result.body"></textus-result-table>
          |</body>
          |</html>""".stripMargin
      ).body

      html should include ("<table")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/notice-board/notice-board/assets/app.js")
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "insert descriptor app assets into fragment result pages" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article>
          |  <h2>Fragment result</h2>
          |  <textus-result-table source="result.body"></textus-result-table>
          |</article>""".stripMargin
      ).body

      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/textus-widgets.css")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/assets/bootstrap.bundle.min.js")
      html should include ("/web/assets/textus-widgets.js")
      html should include ("/web/notice-board/notice-board/assets/app.js")
      html should not include ("<textus-result-table")
    }

    "insert descriptor-declared widget assets once during completion" in {
      val html = StaticFormAppLayout.completeWidgetAssets(
        """<!doctype html>
          |<html lang="en">
          |<head><title>Declared assets</title></head>
          |<body><main class="card">Body</main></body>
          |</html>""".stripMargin,
        StaticFormAppLayout.AssetCompletionOptions(
          requiresBootstrap = true,
          requiresTextusWidgets = true,
          declaredCss = Vector(
            "/web/assets/bootstrap.min.css",
            "/web/assets/textus-widgets.css"
          ),
          declaredJs = Vector(
            "/web/assets/bootstrap.bundle.min.js",
            "/web/assets/textus-widgets.js"
          )
        )
      )

      _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
      _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
    }

    "load packaged Textus widget assets" in {
      StaticFormAppAssets.textusWidgetsCss should include ("textus-widget")
      StaticFormAppAssets.textusWidgetsJs should include ("textusWidgets")
    }

    "render textus result table from result body object data" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"query":{"condition":{"recipient_name":"Bob"},"include_total":false},"data":[{"title":"Phase12","content":"Static form validation","recipient_name":"Bob"}],"fetched_count":1}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.body"></textus-result-table></article>"""
      ).body

      html should include ("<table")
      html should include ("<th>title</th>")
      html should include ("<th>content</th>")
      html should include ("<td>Phase12</td>")
      html should include ("<td>Static form validation</td>")
      html should include ("Page 1")
      html should not include ("<textus-result-table")
    }

    "render textus result table with explicit columns" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","rights":{"owner":{"read":true}}}]}"""
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data" columns="sender_name,subject"></textus-result-table></article>"""
      ).body

      html should include ("<th>sender_name</th><th>subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("rights")
      html should not include ("<textus-result-table")
    }

    "render textus result table from result body with CML summary columns" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body.data", "notice", "summary") -> columns)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.body" entity="notice" view="summary"></textus-result-table></article>"""
      ).body

      html should include ("<th>Title</th><th>Recipient</th>")
      html should include ("<td>Phase12</td>")
      html should include ("<td>Bob</td>")
      html should not include ("notice_1")
      html should not include ("Static form validation")
      html should not include ("<textus-result-table")
    }

    "render textus result table with resolved CML table columns" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender"),
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","rights":{"owner":{"read":true}}}]}""",
        Map("result.data" -> columns)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data"></textus-result-table></article>"""
      ).body

      html should include ("<th>Sender</th><th>Subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("rights")
      html should not include ("<textus-result-table")
    }

    "render textus result table with explicit entity and view columns" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender"),
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","body":"hidden"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.data", "notice", "summary") -> columns)
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data" entity="notice" view="summary"></textus-result-table></article>"""
      ).body

      html should include ("<th>Sender</th><th>Subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("hidden")
      html should not include ("<textus-result-table")
    }

    "render textus result table with descriptor default view columns" in {
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.data", "notice", "card") -> columns),
        defaultTableView = "card"
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data" entity="notice"></textus-result-table></article>"""
      ).body

      html should include ("<th>Subject</th>")
      html should include ("<td>Hello</td>")
      html should not include ("alice")
      html should not include ("<textus-result-table")
    }

    "let textus result table view attribute override descriptor default view" in {
      val summaryColumns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender")
      )
      val cardColumns = Vector(
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice"}]}""",
        Map(
          StaticFormAppRenderer.tableColumnKey("result.data", "notice", "summary") -> summaryColumns,
          StaticFormAppRenderer.tableColumnKey("result.data", "notice", "card") -> cardColumns
        ),
        defaultTableView = "card"
      )

      val html = StaticFormAppRenderer.renderFormResult(
        properties,
        """<article><textus-result-table source="result.data" entity="notice" view="summary"></textus-result-table></article>"""
      ).body

      html should include ("<th>Sender</th>")
      html should include ("<td>alice</td>")
      html should not include ("Hello")
      html should not include ("<textus-result-table")
    }

    "render form result properties with error panel" in {
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        500,
        "text/plain",
        "boom"
      )

      val html = StaticFormAppRenderer.renderFormResult(properties).body

      html should include ("error.status")
      html should include ("500")
      html should include ("boom")
      html should include ("result.ok")
      html should include ("false")
      html should not include ("<textus-error-panel")
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

    "keep WEB-10 built-in pages offline-ready and responsive" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val pages = Vector(
        StaticFormAppRenderer.renderSubsystemDashboard(subsystem).body,
        StaticFormAppRenderer.renderSystemAdmin(subsystem).body,
        StaticFormAppRenderer.renderSystemPerformance(subsystem).body,
        StaticFormAppRenderer.renderSystemManual(subsystem).body,
        StaticFormAppRenderer.renderComponentManual(subsystem, componentPath).map(_.body).getOrElse(fail("component manual is missing")),
        StaticFormAppRenderer.renderComponentAdmin(subsystem, componentPath).map(_.body).getOrElse(fail("component admin is missing"))
      )

      pages.foreach { html =>
        html should include ("""<meta name="viewport" content="width=device-width, initial-scale=1">""")
        html should include ("/web/assets/bootstrap.min.css")
        html should include ("/web/assets/bootstrap.bundle.min.js")
        html should not include ("cdn.jsdelivr")
      }
      pages.mkString("\n") should include ("table-responsive")
      pages.mkString("\n") should include ("d-flex flex-wrap")
      pages.mkString("\n") should include ("card")
      pages.mkString("\n") should include ("shadow-sm")
    }
  }

  private def _count_occurrences(
    text: String,
    needle: String
  ): Int =
    if (needle.isEmpty)
      0
    else
      text.sliding(needle.length).count(_ == needle)

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

  private def _get_request(
    path: String
  ): Request[IO] =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(path)
    )

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

  private def _json_fields(
    json: Json
  ): Vector[Json] =
    json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)

  private def _json_field_names(
    fields: Vector[Json]
  ): Vector[String] =
    fields.flatMap(_.hcursor.downField("name").as[String].toOption)

  private def _json_field(
    fields: Vector[Json],
    name: String
  ): HCursor =
    fields.
      find(_.hcursor.downField("name").as[String].toOption.contains(name)).
      map(_.hcursor).
      getOrElse(fail(s"$name field is missing"))

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

  private def _view_fixture_subsystem(
    totalCountCapability: TotalCountCapability = TotalCountCapability.Unsupported
  ): Subsystem = {
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
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = _NoticeEntity.collectionId,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "note"))
          )
        )
      )
    ))
    val collection = new ViewCollection[String](
      new ViewBuilder[String] {
        def build(id: EntityId): Consequence[String] =
          Consequence.success(s"notice detail ${id.minor}")
      }
    )
    val browser = Browser.from(
      collection,
      _ => Consequence.success(Vector("notice summary", "notice next")),
      countfn = if (totalCountCapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
      totalCountCapabilityValue = totalCountCapability
    )
    component.viewSpace.register("notice_view", collection, browser)
    DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
  }

  private def _aggregate_fixture_subsystem(
    totalCountCapability: TotalCountCapability = TotalCountCapability.Unsupported
  ): Subsystem = {
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
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = _NoticeEntity.collectionId,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "status"))
          )
        )
      )
    ))
    val aggregate = _NoticeAggregate("notice_1", "notice aggregate")
    val nextAggregate = _NoticeAggregate("notice_2", "notice next")
    component.aggregateSpace.register(
      "notice_aggregate",
      new AggregateCollection[_NoticeAggregate](
        new AggregateBuilder[_NoticeAggregate] {
          def build(id: EntityId): Consequence[_NoticeAggregate] =
            Consequence.success(aggregate)
        },
        q => Consequence.success(org.goldenport.cncf.directive.Query.sliceValues(Vector(aggregate, nextAggregate), q.offset, q.limit)),
        countfn = if (totalCountCapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
        totalCountCapabilityValue = totalCountCapability
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
                _NoopOperation("approve-notice-aggregate", Vector("id"))
              )
            )
          )
        )
      )
    )

  private def _form_type_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {}
    _initialize_component("notice_board", component, _form_type_protocol())
    DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
  }

  private def _validation_hints_fixture(): (Subsystem, WebDescriptor) = {
    val component = new org.goldenport.cncf.component.Component() {}
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _NoopOperation("validate-hints", Vector("code", "count"))
              )
            )
          )
        )
      )
    )
    _initialize_component("notice_board", component, protocol)
    val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
    val selector = "notice-board.notice.validate-hints"
    val descriptor = WebDescriptor(
      expose = Map(selector -> WebDescriptor.Exposure.Protected),
      form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "code" -> WebDescriptor.FormControl(
            validation = WebValidationHints(minLength = Some(1), maxLength = Some(4))
          ),
          "count" -> WebDescriptor.FormControl(
            validation = WebValidationHints(min = Some(BigDecimal(-10)), max = Some(BigDecimal(200)))
          )
        )
      ))
    )
    subsystem -> descriptor
  }

  private def _form_type_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _NoopOperation("post-secret-notice", Vector("body", "accessToken"))
              )
            )
          )
        )
      )
    )

  private def _aggregate_http_fixture_subsystem(
    configuration: Configuration = Configuration.empty
  ): Subsystem = {
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
    new Subsystem(
      name = "sample-web",
      configuration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    ).add(Vector(component))
  }

  private def _web_template_fixture_root(
    filename: String,
    content: String
  ): java.nio.file.Path = {
    val root = Files.createTempDirectory("cncf-web-template-")
    Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
    Files.writeString(root.resolve(filename), content, StandardCharsets.UTF_8)
    root
  }

  private def _web_archive_fixture(
    filename: String,
    entries: Vector[(String, String)]
  ): Path = {
    val root = Files.createTempDirectory("cncf-web-archive-")
    val path = root.resolve(filename)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach {
        case (name, content) =>
          out.putNextEntry(new ZipEntry(name))
          out.write(content.getBytes(StandardCharsets.UTF_8))
          out.closeEntry()
      }
    } finally {
      out.close()
    }
    path
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

  private def _management_console_fixture_subsystem(
    configuration: Configuration = Configuration.empty,
    schema: Schema = _schema("id", "title", "author"),
    viewFields: Map[String, Vector[String]] = Map.empty
  ): Subsystem = {
    val resolvedConfiguration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    val runtimeConfig = RuntimeConfig.default.copy(
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = EntityStoreSpace.create(resolvedConfiguration)
    )
    val runtime = GlobalRuntimeContext.create(
      "static-form-app-renderer-spec",
      runtimeConfig,
      resolvedConfiguration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
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
          maxEntitiesPerPartition = 100,
          schema = Some(schema)
        )
      )
    )
    val component =
      if (viewFields.isEmpty) {
        TestComponentFactory.create("notice_board", Protocol.empty)
      } else {
        val c = new org.goldenport.cncf.component.Component() {
          override def viewDefinitions: Vector[ViewDefinition] =
            Vector(
              ViewDefinition(
                name = "notice_view",
                entityName = "notice",
                viewNames = viewFields.keys.toVector,
                viewFields = viewFields
              )
            )
        }
        _initialize_component("notice_board", c, Protocol.empty)
      }
    component.withComponentDescriptors(Vector(descriptor))
    val notices = Vector(
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
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(notices)
    )
    val subsystem = DefaultSubsystemFactory.defaultWithScope(
      runtime,
      Some(org.goldenport.cncf.cli.RunMode.Server),
      resolvedConfiguration
    ).add(Vector(component))
    given ExecutionContext = component.logic.executionContext()
    notices.foreach { notice =>
      summon[ExecutionContext].entityStoreSpace.save(
        org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreSave(notice, summon[EntityPersistent[_NoticeEntity]])
      ).getOrElse(fail(s"notice fixture seed failed: ${notice.id.print}"))
    }
    subsystem
  }

  private def _entity_schema_web_descriptor_fixture(): (Subsystem, WebDescriptor) = {
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
              web = WebColumn(
                controlType = Some("textarea"),
                placeholder = Some("Schema body placeholder."),
                help = Some("Schema body help."),
                required = Some(true)
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
    val subsystem = DefaultSubsystemFactory.default(Some("server")).add(Vector(component))
    val webDescriptor = WebDescriptor(admin = Map(
      "notice-board.entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
        WebDescriptor.AdminField("id"),
        WebDescriptor.AdminField(
          "body",
          WebDescriptor.FormControl(
            placeholder = Some("Descriptor body placeholder."),
            help = Some("Descriptor body help.")
          )
        ),
        WebDescriptor.AdminField(
          "status",
          WebDescriptor.FormControl(
            values = Vector("draft", "published", "archived"),
            required = Some(false)
          )
        )
      ))
    ))
    subsystem -> webDescriptor
  }

  private def _data_schema_web_descriptor(
    includeNote: Boolean = true
  ): WebDescriptor = {
    val fields = Vector(
      WebDescriptor.AdminField("id"),
      WebDescriptor.AdminField(
        "action",
        WebDescriptor.FormControl(
          controlType = Some("select"),
          values = Vector("created", "updated")
        )
      ),
      WebDescriptor.AdminField(
        "actor",
        WebDescriptor.FormControl(
          required = Some(true),
          placeholder = Some("Descriptor actor placeholder."),
          help = Some("Descriptor actor help.")
        )
      )
    ) ++ (
      if (includeNote)
        Vector(WebDescriptor.AdminField("note", WebDescriptor.FormControl(controlType = Some("textarea"))))
      else
        Vector.empty
    )
    WebDescriptor(
      admin = Map(
        "data.audit" -> WebDescriptor.AdminSurface(fields = fields)
      )
    )
  }

  private def _notice_fixture_component(
    subsystem: Subsystem
  ): Component =
    subsystem.components.find(_.name == "notice_board").getOrElse(fail("notice fixture component is missing"))

  private def _schema(names: String*): Schema =
    Schema(names.toVector.map { name =>
      Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
    })

  private def _schema(fields: Vector[(String, WebColumn)]): Schema =
    Schema(fields.toVector.map {
      case (name, web) =>
        Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One), web = web)
    })

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

  private def _load_notice_store_record(
    subsystem: Subsystem,
    id: EntityId
  ): Record = {
    given ExecutionContext = _notice_fixture_component(subsystem).logic.executionContext()
    val collectionId = DataStore.CollectionId.EntityStore(id.collection)
    val entryId = DataStore.EntryId(id)
    val loaded = for {
      ds <- summon[ExecutionContext].dataStoreSpace.dataStore(collectionId)
      record <- ds.load(collectionId, entryId)
    } yield record
    loaded.toOption.flatten.getOrElse(fail(s"notice store record is missing: ${id.print}"))
  }
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

private final case class _NoticeAggregate(id: String, summary: String)

private final case class _NoopOperation(
  opname: String,
  parameters: Vector[String] = Vector.empty
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(
        parameters = parameters.map { name =>
          val content =
            if (name == "body") BaseContent.Builder("body").label("Notice body").build()
            else BaseContent.simple(name)
          val web =
            if (name == "body") WebColumn(help = Some("Body parameter."))
            else if (name == "code") WebColumn(validation = WebValidationHints(minLength = Some(2), maxLength = Some(8), pattern = Some("^[A-Z0-9]+$")))
            else if (name == "count") WebColumn(validation = WebValidationHints(min = Some(BigDecimal(0)), max = Some(BigDecimal(100))))
            else WebColumn.empty
          spec.ParameterDefinition(
            content = content,
            kind = spec.ParameterDefinition.Kind.Argument,
            domain = _noop_parameter_domain(name),
            web = web
          )
        }.toList
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.notImplemented("not used")

  private def _noop_parameter_domain(
    name: String
  ): ValueDomain =
    name match {
      case "count" => ValueDomain(datatype = XInt, multiplicity = Multiplicity.One)
      case "published" => ValueDomain(datatype = XBoolean, multiplicity = Multiplicity.One)
      case "publishedAt" => ValueDomain(datatype = XDateTime, multiplicity = Multiplicity.One)
      case _ => ValueDomain(datatype = XString, multiplicity = Multiplicity.One)
    }
}

private final case class _SuccessfulAggregateOperation(
  opname: String,
  argumentName: String,
  resultPrefix: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple(argumentName),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
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
  private val _forms = ListBuffer.empty[Record]

  def targetName: String = "recording"

  def paths: Vector[String] = _paths.synchronized {
    _paths.toVector
  }

  def forms: Vector[Record] = _forms.synchronized {
    _forms.toVector
  }

  def dispatch(request: HttpRequest): HttpResponse = {
    _paths.synchronized {
      _paths += request.path.asString
    }
    _forms.synchronized {
      _forms += request.form
    }
    delegate.dispatch(request)
  }
}

private final class StaticWebOperationDispatcher(
  response: HttpResponse
) extends WebOperationDispatcher {
  def targetName: String = "static"

  def dispatch(request: HttpRequest): HttpResponse = {
    val _ = request
    response
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
