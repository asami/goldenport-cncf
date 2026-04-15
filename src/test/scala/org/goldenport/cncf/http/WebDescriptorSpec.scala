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
 * @version Apr. 16, 2026
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
          |
          |  form:
          |    notice-board.notice.search-notices:
          |      enabled: true
          |      successRedirect: /web/${component}/admin/aggregates/${service}/${result.id}
          |      failureRedirect: /form/${component}/${service}/${operation}
          |      stayOnError: true
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
      descriptor.form("notice-board.notice.search-notices").enabled shouldBe Some(true)
      descriptor.form("notice-board.notice.search-notices").successRedirect shouldBe Some("/web/${component}/admin/aggregates/${service}/${result.id}")
      descriptor.form("notice-board.notice.search-notices").failureRedirect shouldBe Some("/form/${component}/${service}/${operation}")
      descriptor.form("notice-board.notice.search-notices").stayOnError shouldBe true
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
    }

    "load /web/web.yaml from a directory descriptor root" in {
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

    "load /web/web.yaml from an archive descriptor root" in {
      val path = Files.createTempFile("cncf-web-descriptor-archive", ".sar")
      val yaml =
        """web:
          |  expose:
          |    notice-board.notice.search-notices: public
          |""".stripMargin
      val zip = new ZipOutputStream(Files.newOutputStream(path))
      try {
        zip.putNextEntry(new ZipEntry("web/web.yaml"))
        zip.write(yaml.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      } finally {
        zip.close()
      }

      val descriptor = WebDescriptor.load(path).toOption.get

      descriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
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
  }
}
