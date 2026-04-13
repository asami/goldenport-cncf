package org.goldenport.cncf.http

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
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
      console should include ("System Console")
      console should include ("/web/system/dashboard")
      console should include ("/web/manual")
      console should include ("/form/")
      console should not include ("<form method=\"post\"")
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
