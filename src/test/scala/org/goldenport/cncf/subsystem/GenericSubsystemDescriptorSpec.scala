package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  8, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemDescriptorSpec extends AnyWordSpec with Matchers {
  "GenericSubsystemDescriptor" should {
    "load component extension bindings from the formal YAML schema using name and version" in {
      val path = Files.createTempFile("generic-subsystem-descriptor", ".yaml")
      Files.writeString(
        path,
        """subsystem: mcprag
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: textus-mcp-rag
          |    version: 0.1.0-SNAPSHOT
          |    extension_bindings:
          |      knowledge_source_adapters:
          |        - key: view
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val componentDescriptor = descriptor.toComponentDescriptors.head
      val bindings = componentDescriptor.extensionBindings
      val adapterBindings = bindings.getVector("knowledge_source_adapters").toVector.flatten
      val keys = adapterBindings.collect {
        case r: org.goldenport.record.Record => r.getString("key")
        case m: Map[?, ?] => m.iterator.collectFirst { case (k, v) if k.toString == "key" => v.toString }
      }.flatten

      descriptor.subsystemName shouldBe "mcprag"
      descriptor.componentVersion shouldBe Some("0.1.0-SNAPSHOT")
      descriptor.runtimeComponentNames shouldBe Vector("McpRag")
      componentDescriptor.name shouldBe Some("textus-mcp-rag")
      componentDescriptor.version shouldBe Some("0.1.0-SNAPSHOT")
      keys shouldBe Vector("view")
    }

    "load security authentication wiring from the formal YAML schema" in {
      val path = Files.createTempFile("generic-subsystem-security-descriptor", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-identity
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: textus-user-account
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authentication:
          |    convention: enabled
          |    fallback_privilege: disabled
          |    providers:
          |      - name: user-account
          |        component: textus-user-account
          |        kind: human
          |        enabled: true
          |        priority: 100
          |        schemes:
          |          - bearer
          |          - refresh-token
          |        default: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val auth = descriptor.security.flatMap(_.authentication).get
      val provider = auth.providers.head

      auth.convention shouldBe Some("enabled")
      auth.fallbackPrivilege shouldBe Some("disabled")
      provider.name shouldBe "user-account"
      provider.component shouldBe "textus-user-account"
      provider.kind shouldBe Some("human")
      provider.enabled shouldBe Some(true)
      provider.priority shouldBe Some(100)
      provider.schemes shouldBe Vector("bearer", "refresh-token")
      provider.isDefault shouldBe Some(true)
    }

    "load operation authorization rules from the formal YAML schema" in {
      val path = Files.createTempFile("generic-subsystem-operation-authorization", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-sample
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: notice-board
          |    version: 0.1.0-SNAPSHOT
          |operationAuthorization:
          |  notice-board.notice.post-notice:
          |    allowAnonymous: true
          |    anonymousOperationModes:
          |      - develop
          |      - test
          |  notice-board.notice.admin-only:
          |    operationModes: production
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val post = descriptor.operationAuthorizationRule("notice-board.notice.post-notice").get
      val admin = descriptor.operationAuthorizationRule("notice-board.notice.admin-only").get

      post.allowAnonymous shouldBe true
      post.anonymousOperationModes.map(_.name) shouldBe Vector("develop", "test")
      admin.operationModes.map(_.name) shouldBe Vector("production")
    }

    "keep legacy coordinate parsing for backward compatibility" in {
      val path = Files.createTempFile("generic-subsystem-coordinate-descriptor", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-sample
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - component: notice-board
          |    coordinate: org.textus:notice-board:0.1.0-SNAPSHOT
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get

      descriptor.componentBindings.head.componentName shouldBe "notice-board"
      descriptor.componentBindings.head.componentVersion shouldBe Some("0.1.0-SNAPSHOT")
    }


    "load the textus-identity journal sample with security authentication wiring" in {
      val path = java.nio.file.Path.of("/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val auth = descriptor.security.flatMap(_.authentication).get
      val provider = auth.providers.head

      descriptor.subsystemName shouldBe "textus-identity"
      descriptor.runtimeComponentNames shouldBe Vector("UserAccount")
      auth.convention shouldBe Some("enabled")
      auth.fallbackPrivilege shouldBe Some("disabled")
      provider.name shouldBe "user-account"
      provider.component shouldBe "textus-user-account"
      provider.kind shouldBe Some("human")
      provider.priority shouldBe Some(100)
      provider.schemes shouldBe Vector("bearer", "refresh-token")
      provider.isDefault shouldBe Some(true)
    }

  }
}
