package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 14, 2026
 * @version Apr. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebDescriptorSpec extends AnyWordSpec with Matchers {
  "WebDescriptor" should {
    "load the minimum Phase 12 schema from an explicit descriptor path" in {
      val path = Files.createTempFile("cncf-web-descriptor", ".yaml")
      Files.writeString(
        path,
        """web:
          |  expose:
          |    notice-board.notice.search-notices: public
          |    notice-board.notice.post-notice: protected
          |
          |  auth:
          |    mode: session
          |
          |  authorization:
          |    notice-board.notice.post-notice:
          |      roles: ["moderator"]
          |      scopes: ["notice:write"]
          |      capabilities: ["notice.post"]
          |      operationModes: ["develop", "test"]
          |      anonymousOperationModes: ["develop"]
          |      allowAnonymous: true
          |
          |  form:
          |    notice-board.notice.search-notices:
          |      enabled: true
          |      successRedirect: /web/${component}/admin/aggregates/${service}/${result.id}
          |      failureRedirect: /form/${component}/${service}/${operation}
          |      stayOnError: true
          |      assets:
          |        css:
          |          - /web/notice-board/notice-board/assets/search.css
          |        js:
          |          - /web/notice-board/notice-board/assets/search.js
          |      resultTemplate: |
          |        <article>
          |          <h2>${operation.label}</h2>
          |          <textus-property-list source="result"></textus-property-list>
          |        </article>
          |      controls:
          |        body:
          |          type: textarea
          |          required: true
          |          placeholder: Write a notice.
          |          help: Notice body.
          |        status:
          |          type: select
          |          values: [Draft, Published]
          |          multiple: true
          |        accessToken:
          |          hidden: true
          |          system: true
          |          readonly: true
          |
          |  admin:
          |    entity.notice:
          |      totalCount: optional
          |      fields:
          |        - id
          |        - title
          |        - name: body
          |          type: textarea
          |          required: true
          |    data.audit:
          |      totalCount: required
          |      fields: id action actor
          |      controls:
          |        action:
          |          type: select
          |          values: [created, updated]
          |
          |  apps:
          |    - name: manual
          |      path: /web/manual
          |      kind: manual
          |    - name: console
          |      path: /web/console
          |      kind: console
          |      assets:
          |        css:
          |          - /web/console/assets/console.css
          |        js:
          |          - /web/console/assets/console.js
          |
          |  routes:
          |    - path: /web/notice-board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |    - path: /web
          |      kind: default
          |      target:
          |        component: notice-board
          |        app: notice-board
          |
          |  assets:
          |    autoComplete: false
          |    css:
          |      - /web/assets/bootstrap.min.css
          |      - /web/notice-board/assets/app.css
          |    js:
          |      - /web/assets/bootstrap.bundle.min.js
          |      - /web/notice-board/assets/app.js
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(path).toOption.get

      descriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
      descriptor.expose("notice-board.notice.post-notice") shouldBe WebDescriptor.Exposure.Protected
      descriptor.auth.mode shouldBe "session"
      descriptor.authorization("notice-board.notice.post-notice").roles shouldBe Vector("moderator")
      descriptor.authorization("notice-board.notice.post-notice").scopes shouldBe Vector("notice:write")
      descriptor.authorization("notice-board.notice.post-notice").capabilities shouldBe Vector("notice.post")
      descriptor.authorization("notice-board.notice.post-notice").operationModes shouldBe
        Vector(org.goldenport.cncf.config.OperationMode.Develop, org.goldenport.cncf.config.OperationMode.Test)
      descriptor.authorization("notice-board.notice.post-notice").anonymousOperationModes shouldBe
        Vector(org.goldenport.cncf.config.OperationMode.Develop)
      descriptor.authorization("notice-board.notice.post-notice").allowAnonymous shouldBe true
      descriptor.form("notice-board.notice.search-notices").enabled shouldBe Some(true)
      descriptor.form("notice-board.notice.search-notices").successRedirect shouldBe Some("/web/${component}/admin/aggregates/${service}/${result.id}")
      descriptor.form("notice-board.notice.search-notices").failureRedirect shouldBe Some("/form/${component}/${service}/${operation}")
      descriptor.form("notice-board.notice.search-notices").stayOnError shouldBe true
      descriptor.form("notice-board.notice.search-notices").assets.css shouldBe Vector("/web/notice-board/notice-board/assets/search.css")
      descriptor.form("notice-board.notice.search-notices").assets.js shouldBe Vector("/web/notice-board/notice-board/assets/search.js")
      descriptor.form("notice-board.notice.search-notices").resultTemplate.getOrElse(fail("result template is missing")) should include ("<textus-property-list source=\"result\"></textus-property-list>")
      descriptor.form("notice-board.notice.search-notices").controls("body").controlType shouldBe Some("textarea")
      descriptor.form("notice-board.notice.search-notices").controls("body").required shouldBe Some(true)
      descriptor.form("notice-board.notice.search-notices").controls("body").placeholder shouldBe Some("Write a notice.")
      descriptor.form("notice-board.notice.search-notices").controls("body").help shouldBe Some("Notice body.")
      descriptor.form("notice-board.notice.search-notices").controls("status").controlType shouldBe Some("select")
      descriptor.form("notice-board.notice.search-notices").controls("status").values shouldBe Vector("Draft", "Published")
      descriptor.form("notice-board.notice.search-notices").controls("status").multiple shouldBe true
      descriptor.form("notice-board.notice.search-notices").controls("accessToken").hidden shouldBe true
      descriptor.form("notice-board.notice.search-notices").controls("accessToken").system shouldBe true
      descriptor.form("notice-board.notice.search-notices").controls("accessToken").readonly shouldBe true
      descriptor.admin("entity.notice").totalCount shouldBe WebDescriptor.TotalCountPolicy.Optional
      descriptor.admin("data.audit").totalCount shouldBe WebDescriptor.TotalCountPolicy.Required
      descriptor.admin("entity.notice").fields.map(_.name) shouldBe Vector("id", "title", "body")
      descriptor.admin("entity.notice").fields(2).control.controlType shouldBe Some("textarea")
      descriptor.admin("entity.notice").fields(2).control.required shouldBe Some(true)
      descriptor.admin("data.audit").fields.map(_.name) shouldBe Vector("id", "action", "actor")
      descriptor.admin("data.audit").fields(1).control.controlType shouldBe Some("select")
      descriptor.admin("data.audit").fields(1).control.values shouldBe Vector("created", "updated")
      descriptor.adminTotalCountPolicy("notice_board", "entity", "notice") shouldBe WebDescriptor.TotalCountPolicy.Optional
      descriptor.adminTotalCountPolicy("notice_board", "data", "audit") shouldBe WebDescriptor.TotalCountPolicy.Required
      descriptor.adminFields("notice_board", "data", "audit").map(_.name) shouldBe Vector("id", "action", "actor")
      descriptor.apps.map(_.name) shouldBe Vector("manual", "console")
      descriptor.apps.map(_.path) shouldBe Vector("/web/manual", "/web/console")
      descriptor.apps(1).assets.css shouldBe Vector("/web/console/assets/console.css")
      descriptor.apps(1).assets.js shouldBe Vector("/web/console/assets/console.js")
      descriptor.routes.map(_.path) shouldBe Vector("/web/notice-board", "/web")
      descriptor.routes.map(_.kind) shouldBe Vector(WebDescriptor.RouteKind.Alias, WebDescriptor.RouteKind.Default)
      descriptor.routes.head.target.component shouldBe "notice-board"
      descriptor.routes.head.target.app shouldBe "notice-board"
      descriptor.webRouteFor(Vector("web", "notice-board")).map(_.target.app) shouldBe Some("notice-board")
      descriptor.webRouteFor(Vector("web", "notice-board", "about")).map(_.remainingPath) shouldBe Some(Vector("about"))
      descriptor.webRouteFor(Vector("web", "notice-board", "about")).map(_.kind) shouldBe Some(WebDescriptor.RouteKind.Alias)
      descriptor.webRouteFor(Vector("web", "other")).map(_.kind) shouldBe Some(WebDescriptor.RouteKind.Default)
      descriptor.assets.autoComplete shouldBe false
      descriptor.assets.css shouldBe Vector("/web/assets/bootstrap.min.css", "/web/notice-board/assets/app.css")
      descriptor.assets.js shouldBe Vector("/web/assets/bootstrap.bundle.min.js", "/web/notice-board/assets/app.js")
      descriptor.resultAssets("notice-board", "notice", "search-notices").css shouldBe Vector(
        "/web/assets/bootstrap.min.css",
        "/web/notice-board/assets/app.css",
        "/web/notice-board/notice-board/assets/search.css"
      )
      descriptor.resultAssets("console", "notice", "search-notices").css shouldBe Vector(
        "/web/assets/bootstrap.min.css",
        "/web/notice-board/assets/app.css",
        "/web/console/assets/console.css"
      )
    }

    "complete obvious Static Form Web app defaults from a minimal app entry" in {
      val path = Files.createTempFile("cncf-web-descriptor-minimal-app", ".yaml")
      Files.writeString(
        path,
        """web:
          |  apps:
          |    - name: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(path).toOption.get
      val app = descriptor.apps.headOption.getOrElse(fail("app is missing"))

      app.name shouldBe "notice-board"
      app.kind shouldBe "static-form"
      app.effectivePath shouldBe "/web/notice-board"
      app.completed.root shouldBe Some("/web/notice-board")
      app.completed.route shouldBe Some("/web/{component}/notice-board")
    }

    "merge scoped assets in global app and form order without duplicates" in {
      val path = Files.createTempFile("cncf-web-descriptor-scoped-assets", ".yaml")
      Files.writeString(
        path,
        """web:
          |  assets:
          |    css:
          |      - /web/assets/site.css
          |    js:
          |      - /web/assets/site.js
          |
          |  apps:
          |    - name: notice-board
          |      assets:
          |        css:
          |          - /web/assets/site.css
          |          - /web/notice-board/notice-board/assets/app.css
          |        js:
          |          - /web/notice-board/notice-board/assets/app.js
          |
          |  form:
          |    notice-board.notice.search-notices:
          |      assets:
          |        autoComplete: false
          |        css:
          |          - /web/notice-board/notice-board/assets/app.css
          |          - /web/notice-board/notice-board/assets/search.css
          |        js:
          |          - /web/notice-board/notice-board/assets/search.js
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(path).toOption.get
      val assets = descriptor.resultAssets("notice-board", "notice", "search-notices")
      val indexAssets = descriptor.formIndexAssets("notice-board")

      assets.autoComplete shouldBe false
      indexAssets.css shouldBe Vector(
        "/web/assets/site.css",
        "/web/notice-board/notice-board/assets/app.css"
      )
      indexAssets.js shouldBe Vector(
        "/web/assets/site.js",
        "/web/notice-board/notice-board/assets/app.js"
      )
      assets.css shouldBe Vector(
        "/web/assets/site.css",
        "/web/notice-board/notice-board/assets/app.css",
        "/web/notice-board/notice-board/assets/search.css"
      )
      assets.js shouldBe Vector(
        "/web/assets/site.js",
        "/web/notice-board/notice-board/assets/app.js",
        "/web/notice-board/notice-board/assets/search.js"
      )
    }

    "discover /web/web-descriptor.yaml from a directory descriptor root" in {
      val root = Files.createTempDirectory("cncf-web-descriptor-root")
      val web = Files.createDirectories(root.resolve("web"))
      Files.writeString(
        web.resolve("web-descriptor.yaml"),
        """web:
          |  expose:
          |    notice-board.notice.search-notices: public
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(root).toOption.get

      descriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
    }

    "keep /web/web.yaml as the secondary directory descriptor name" in {
      val root = Files.createTempDirectory("cncf-web-descriptor-root")
      val web = Files.createDirectories(root.resolve("web"))
      Files.writeString(
        web.resolve("web.yaml"),
        """web:
          |  expose:
          |    admin.system.ping: protected
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(root).toOption.get

      descriptor.expose("admin.system.ping") shouldBe WebDescriptor.Exposure.Protected
    }

    "discover /web/web-descriptor.yaml from an archive descriptor root" in {
      val path = Files.createTempFile("cncf-web-descriptor-archive", ".sar")
      val yaml =
        """web:
          |  expose:
          |    notice-board.notice.search-notices: public
          |""".stripMargin
      val zip = new ZipOutputStream(Files.newOutputStream(path))
      try {
        zip.putNextEntry(new ZipEntry("web/web-descriptor.yaml"))
        zip.write(yaml.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      } finally {
        zip.close()
      }

      val descriptor = WebDescriptor.load(path).toOption.get

      descriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
    }

    "complete CAR Web app package routes from descriptor app entries" in {
      val app = WebDescriptor.App("notice-board").completedFor(Some("notice-board"))

      app.effectiveRoot shouldBe "/web/notice-board"
      app.route shouldBe Some("/web/notice-board/notice-board")
      app.effectiveKind shouldBe "static-form"
      app.matches("notice-board", Vector.empty) shouldBe true
    }

    "reject conflicting Web route aliases during descriptor load" in {
      val path = Files.createTempFile("cncf-web-descriptor-route-conflict", ".yaml")
      Files.writeString(
        path,
        """web:
          |  routes:
          |    - path: /web/board
          |      target:
          |        component: notice-board
          |        app: notice-board
          |    - path: /web/board
          |      target:
          |        component: inventory
          |        app: catalog
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val result = WebDescriptor.load(path)

      result shouldBe a[org.goldenport.Consequence.Failure[_]]
      result match {
        case org.goldenport.Consequence.Failure(conclusion) =>
          conclusion.show should include ("web route conflict")
          conclusion.show should include ("/web/board")
        case _ =>
          fail("expected descriptor conflict failure")
      }
    }

    "deduplicate identical Web route aliases during descriptor load" in {
      val path = Files.createTempFile("cncf-web-descriptor-route-duplicate", ".yaml")
      Files.writeString(
        path,
        """web:
          |  routes:
          |    - path: /web/board
          |      target:
          |        component: notice-board
          |        app: notice-board
          |    - path: /web/board
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = WebDescriptor.load(path).toOption.get

      descriptor.routes.size shouldBe 1
      descriptor.routes.head.normalizedPathText shouldBe "/web/board"
    }

    "derive implicit SAR routes when a Web app matches one component among several candidates" in {
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("notice-board"))
      ).withImplicitSarRoutes(Vector("admin", "NoticeBoard", "metrics"))

      descriptor.routes.map(_.path) shouldBe Vector("/web/notice-board", "/web")
      descriptor.routes.map(_.target.component).distinct shouldBe Vector("notice-board")
    }

    "resolve the runtime descriptor path from RuntimeConfig" in {
      val path = Files.createTempFile("cncf-web-descriptor-runtime", ".yaml")
      Files.writeString(
        path,
        """web:
          |  expose:
          |    notice-board.notice.search-notices: public
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(path.toString)
          )
        ),
        ConfigurationTrace.empty
      )

      val descriptor = WebDescriptorResolver.resolve(configuration).toOption.get

      descriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
    }

    "resolve an empty descriptor when no runtime descriptor path is configured" in {
      val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)

      val descriptor = WebDescriptorResolver.resolve(configuration).toOption.get

      descriptor shouldBe WebDescriptor.empty
    }

    "treat exposure as the contract gate for public Web form surfaces" in {
      val publicSelector = "notice-board.notice.search-notices"
      val protectedSelector = "notice-board.notice.post-notice"
      val internalSelector = "notice-board.notice.rebuild-index"
      val descriptor = WebDescriptor(
        expose = Map(
          publicSelector -> WebDescriptor.Exposure.Public,
          protectedSelector -> WebDescriptor.Exposure.Protected,
          internalSelector -> WebDescriptor.Exposure.Internal
        )
      )

      descriptor.exposureOf(publicSelector) shouldBe WebDescriptor.Exposure.Public
      descriptor.exposureOf(protectedSelector) shouldBe WebDescriptor.Exposure.Protected
      descriptor.exposureOf(internalSelector) shouldBe WebDescriptor.Exposure.Internal
      descriptor.exposureOf("notice-board.notice.unlisted") shouldBe WebDescriptor.Exposure.Internal
      descriptor.isFormEnabled(publicSelector) shouldBe true
      descriptor.isFormEnabled(protectedSelector) shouldBe true
      descriptor.isFormEnabled(internalSelector) shouldBe false
      descriptor.isFormEnabled("notice-board.notice.unlisted") shouldBe false
    }

    "allow Web Tier authorization when every configured category matches at least one subject value" in {
      val descriptor = WebDescriptor(
        authorization = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Authorization(
            roles = Vector("moderator", "admin"),
            scopes = Vector("notice:write"),
            capabilities = Vector("notice.post")
          )
        )
      )
      val subject = WebDescriptorAuthorization.Subject(
        roles = Set("Moderator"),
        scopes = Set("notice:write"),
        capabilities = Set("notice-post", "notice.post")
      )

      WebDescriptorAuthorization.isAllowed(descriptor, "notice-board.notice.post-notice", subject) shouldBe true
    }

    "deny Web Tier authorization when a configured category has no matching subject value" in {
      val descriptor = WebDescriptor(
        authorization = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Authorization(
            roles = Vector("moderator"),
            scopes = Vector("notice:write")
          )
        )
      )
      val subject = WebDescriptorAuthorization.Subject(
        roles = Set("moderator"),
        scopes = Set("notice:read")
      )

      WebDescriptorAuthorization.isAllowed(descriptor, "notice-board.notice.post-notice", subject) shouldBe false
    }

    "apply operation-mode and anonymous policy from Web Tier authorization rules" in {
      val descriptor = WebDescriptor(
        authorization = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Authorization(
            allowAnonymous = true,
            anonymousOperationModes = Vector(org.goldenport.cncf.config.OperationMode.Develop)
          )
        )
      )
      val subject = WebDescriptorAuthorization.Subject()

      WebDescriptorAuthorization.isAllowed(
        descriptor,
        "notice-board.notice.post-notice",
        subject,
        org.goldenport.cncf.config.OperationMode.Develop
      ) shouldBe true
      WebDescriptorAuthorization.isAllowed(
        descriptor,
        "notice-board.notice.post-notice",
        subject,
        org.goldenport.cncf.config.OperationMode.Production
      ) shouldBe false
    }

    "allow Web Tier authorization when no rule exists for the selector" in {
      val descriptor = WebDescriptor(
        authorization = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Authorization(
            roles = Vector("moderator")
          )
        )
      )

      WebDescriptorAuthorization.isAllowed(descriptor, "notice-board.notice.search-notices", WebDescriptorAuthorization.Subject()) shouldBe true
    }

    "derive a Web authorization subject from an anonymous security context" in {
      val security = org.goldenport.cncf.context.ExecutionContext
        .create(org.goldenport.cncf.context.SecurityContext.Privilege.Anonymous)
        .security

      val subject = WebDescriptorAuthorization.Subject.from(security)

      subject.isAnonymous shouldBe true
      subject.roles should contain ("anonymous")
      subject.capabilities should contain ("anonymous")
    }

    "derive a Web authorization subject from HTTP query and header values" in {
      val request = org.http4s.Request[cats.effect.IO](
        method = org.http4s.Method.GET,
        uri = org.http4s.Uri.unsafeFromString("/web/notice-board/admin?role=operator&capability=notice.admin&principalId=admin-test")
      ).putHeaders(
        org.http4s.Header.Raw(
          org.typelevel.ci.CIString("x-textus-scope"),
          "notice:write"
        )
      )

      val subject = WebDescriptorAuthorization.Subject.fromHttp(request)

      subject.isAnonymous shouldBe false
      subject.roles should contain ("operator")
      subject.scopes should contain ("notice:write")
      subject.capabilities should contain ("notice.admin")
    }
  }
}
