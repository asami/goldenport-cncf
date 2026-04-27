package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  8, 2026
 * @version Apr. 28, 2026
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
      descriptor.runtimeComponentNames shouldBe Vector("textus-mcp-rag")
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

    "load security authorization role definitions from the formal YAML schema" in {
      val path = Files.createTempFile("generic-subsystem-security-authorization", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: blob
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authorization:
          |    roles:
          |      blob_user:
          |        capabilities:
          |          - collection:blob:create
          |          - collection:blob:read
          |      blob_operator:
          |        includes:
          |          - blob_user
          |        capabilities:
          |          - association:blob_attachment:delete
          |          - store:blobstore:status
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val roles = descriptor.security.flatMap(_.authorization).map(_.roles).get

      roles("blob_user").capabilities should contain allOf ("collection:blob:create", "collection:blob:read")
      roles("blob_operator").includes shouldBe Vector("blob_user")
      roles("blob_operator").capabilities should contain allOf ("association:blob_attachment:delete", "store:blobstore:status")
    }

    "reject invalid security authorization role definitions" in {
      val path = Files.createTempFile("generic-subsystem-security-authorization-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: blob
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authorization:
          |    roles:
          |      blob_user: invalid-scalar-role-definition
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      GenericSubsystemDescriptor.load(path) shouldBe a[Consequence.Failure[_]]
    }

    "let SAR security role definitions override inherited CAR role definitions by role name" in {
      val car = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<car>"),
        subsystemName = "blob-car",
        componentBindings = Vector(GenericSubsystemComponentBinding("blob")),
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(
            roles = Map(
              "blob_user" -> org.goldenport.cncf.security.SecurityRoleDefinition(
                name = "blob_user",
                capabilities = Vector("collection:blob:read")
              )
            )
          ))
        ))
      )
      val sar = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<sar>"),
        subsystemName = "blob-sar",
        componentBindings = Vector(GenericSubsystemComponentBinding("blob")),
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(
            roles = Map(
              "blob-user" -> org.goldenport.cncf.security.SecurityRoleDefinition(
                name = "blob-user",
                capabilities = Vector("collection:blob:create")
              )
            )
          ))
        ))
      )

      val effective = GenericSubsystemDescriptor.mergeComponentDefaults(car, sar)
      val roles = effective.security.flatMap(_.authorization).map(_.roles).get

      roles.values.map(_.name).toSet shouldBe Set("blob-user")
      roles.values.flatMap(_.capabilities).toSet shouldBe Set("collection:blob:create")
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

    "create a synthetic subsystem descriptor from component CAR assembly metadata" in {
      val car = Files.createTempFile("component-with-assembly", ".car")
      val descriptor =
        """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}"""
      val assembly =
        """subsystem: cwitter
          |version: 0.0.1-SNAPSHOT
          |components:
          |  - name: cwitter
          |    version: 0.0.1-SNAPSHOT
          |  - name: textus-user-account
          |    version: 0.1.1-SNAPSHOT
          |security:
          |  authentication:
          |    convention: enabled
          |    fallback_privilege: disabled
          |""".stripMargin
      _write_zip(
        car,
        Map(
          "component-descriptor.json" -> descriptor,
          "assembly-descriptor.yaml" -> assembly,
          "web/web.yaml" -> "apps: []\n"
        )
      )

      val loaded = GenericSubsystemDescriptor.loadComponentArchive(car).toOption.get

      loaded.subsystemName shouldBe "cwitter"
      loaded.componentBindings.map(_.componentName) shouldBe Vector("cwitter", "textus-user-account")
      loaded.security.flatMap(_.authentication).flatMap(_.convention) shouldBe Some("enabled")
      loaded.assemblyDescriptor.map(_.source) shouldBe Some("component-car")
    }

    "reject a component archive with an invalid assembly descriptor" in {
      val car = Files.createTempFile("invalid-component-assembly", ".car")
      val descriptor =
        """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}"""
      val assembly =
        """version: 0.0.1-SNAPSHOT
          |components:
          |  - name: cwitter
          |""".stripMargin
      _write_zip(
        car,
        Map(
          "component-descriptor.json" -> descriptor,
          "assembly-descriptor.yaml" -> assembly
        )
      )

      GenericSubsystemDescriptor.loadComponentArchive(car) match {
        case Consequence.Failure(_) => succeed
        case Consequence.Success(value) => fail(s"expected invalid assembly descriptor failure but got ${value}")
      }
    }

    "let a SAR descriptor inherit authentication provider defaults from a component CAR assembly descriptor" in {
      val car = Files.createTempFile("cwitter-component-defaults", ".car")
      val descriptor =
        """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}"""
      val assembly =
        """subsystem: cwitter
          |version: 0.0.1-SNAPSHOT
          |components:
          |  - name: cwitter
          |    version: 0.0.1-SNAPSHOT
          |  - name: textus-user-account
          |    version: 0.1.1-SNAPSHOT
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
          |        default: true
          |""".stripMargin
      _write_zip(
        car,
        Map(
          "component-descriptor.json" -> descriptor,
          "assembly-descriptor.yaml" -> assembly
        )
      )
      val carDefaults = GenericSubsystemDescriptor.loadComponentArchive(car).toOption.get
      val sar = Files.createTempFile("cwitter-sar-no-security", ".yaml")
      Files.writeString(
        sar,
        """subsystem: cwitter
          |version: 0.0.1-SNAPSHOT
          |components:
          |  - name: cwitter
          |    version: 0.0.1-SNAPSHOT
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val sarDescriptor = GenericSubsystemDescriptor.load(sar).toOption.get

      val effective = GenericSubsystemDescriptor.mergeComponentDefaults(carDefaults, sarDescriptor)
      val provider = effective.security.flatMap(_.authentication).toVector.flatMap(_.providers).headOption.get

      provider.name shouldBe "user-account"
      provider.component shouldBe "textus-user-account"
      effective.componentBindings.map(_.componentName) shouldBe Vector("cwitter", "textus-user-account")
    }

    "let a SAR assembly descriptor override a provider inherited from component CAR assembly defaults" in {
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("cwitter.car"),
        subsystemName = "cwitter",
        componentBindings = Vector(GenericSubsystemComponentBinding("cwitter")),
        security = Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(
            providers = Vector(GenericSubsystemAuthenticationProviderBinding(
              name = "user-account",
              component = "textus-user-account",
              kind = Some("human")
            ))
          ))
        ))
      )
      val overrideSource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "security" -> Record.data(
            "authentication" -> Record.data(
              "providers" -> Vector(Record.data(
                "name" -> "user-account",
                "component" -> "custom-user-account",
                "kind" -> "human",
                "enabled" -> true,
                "priority" -> 200,
                "default" -> true
              ))
            )
          )
        ),
        source = "sar",
        path = Some(java.nio.file.Path.of("cwitter.sar"))
      )

      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overrideSource)
      val provider = effective.security.flatMap(_.authentication).toVector.flatMap(_.providers).headOption.get

      provider.name shouldBe "user-account"
      provider.component shouldBe "custom-user-account"
      provider.priority shouldBe Some(200)
    }

    "merge partial SAR assembly wiring overrides with inherited component CAR wiring" in {
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("cwitter.car"),
        subsystemName = "cwitter",
        componentBindings = Vector(GenericSubsystemComponentBinding("cwitter")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "wiring" -> Vector(
              _wiring_record("cwitter", "account", "signin", "textus-user-account", "account", "signin"),
              _wiring_record("cwitter", "message", "deliver", "textus-message-delivery-stub", "message", "deliver")
            )
          ),
          source = "component-car",
          path = Some(java.nio.file.Path.of("cwitter.car"))
        ))
      )
      val overrideSource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "wiring" -> Vector(
            _wiring_record("cwitter", "account", "signin", "enterprise-user-account", "account", "signin")
          )
        ),
        source = "sar",
        path = Some(java.nio.file.Path.of("cwitter.sar"))
      )

      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overrideSource)
      val bindings = effective.resolvedWiring

      bindings.map(_.toComponent).toSet shouldBe Set("enterprise-user-account", "textus-message-delivery-stub")
      bindings.count(_.fromService == "account") shouldBe 1
    }

    "load the textus-identity journal sample with security authentication wiring" in {
      val path = java.nio.file.Path.of("/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")

      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val auth = descriptor.security.flatMap(_.authentication).get
      val provider = auth.providers.head

      descriptor.subsystemName shouldBe "textus-identity"
      descriptor.runtimeComponentNames shouldBe Vector("textus-user-account")
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

  private def _write_zip(path: java.nio.file.Path, entries: Map[String, String]): Unit = {
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach { case (name, content) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(content.getBytes(StandardCharsets.UTF_8))
        out.closeEntry()
      }
    } finally {
      out.close()
    }
  }

  private def _wiring_record(
    fromComponent: String,
    fromService: String,
    fromOperation: String,
    toComponent: String,
    toService: String,
    toOperation: String
  ): Record =
    Record.data(
      "from" -> Record.data(
        "component" -> fromComponent,
        "service" -> fromService,
        "operation" -> fromOperation
      ),
      "to" -> Record.data(
        "component" -> toComponent,
        "service" -> toService,
        "operation" -> toOperation
      )
    )
}
