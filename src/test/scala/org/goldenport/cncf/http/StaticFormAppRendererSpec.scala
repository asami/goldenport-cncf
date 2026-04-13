package org.goldenport.cncf.http

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 12, 2026
 * @version Apr. 14, 2026
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
        apps = Vector(WebDescriptor.App("manual", "/web/manual", "manual"))
      )

      val html = StaticFormAppRenderer.renderSystemAdmin(subsystem, descriptor).body

      html should include ("Web Descriptor")
      html should include ("configured")
      html should include ("notice-board.notice.search-notices")
      html should include ("public")
      html should include ("manual")
      html should include ("/web/manual")
    }

    "render resolved Web Descriptor drill-down page" in {
      val descriptor = WebDescriptor(
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(roles = Vector("reader"))),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(enabled = Some(true))),
        apps = Vector(WebDescriptor.App("manual", "/web/manual", "manual"))
      )

      val html = StaticFormAppRenderer.renderSystemAdminDescriptor(descriptor).body

      html should include ("System Web Descriptor")
      html should include ("Resolved Descriptor")
      html should include ("/web/system/admin")
      html should include ("&quot;status&quot; : &quot;configured&quot;")
      html should include ("&quot;notice-board.notice.search-notices&quot; : &quot;public&quot;")
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

      val html = StaticFormAppRenderer.renderComponentAdminEntities(subsystem, component.name).map(_.body).getOrElse(fail("component entity admin is missing"))

      html should include (s"${component.name} Entity Administration")
      html should include ("Entity CRUD")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("entity runtime descriptors")
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
}
