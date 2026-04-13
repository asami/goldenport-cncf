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
 * @version Apr. 14, 2026
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
