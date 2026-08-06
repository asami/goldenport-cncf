package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentStyleCatalog, ComponentStyleId, SubsystemCapabilityId}
import org.goldenport.cncf.config.RuntimeTestDescriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  8, 2026
 *  version Apr. 28, 2026
 *  version May.  7, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "GenericSubsystemDescriptor" should {
    "preserve a component style snapshot when it becomes an implicit subsystem binding" in {
      Given("a schema-v2 component descriptor with a resolved style")
      val snapshot = ComponentStyleCatalog.default
        .resolveC(ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get)
        .flatMap(ComponentStyleCatalog.default.expandC)
        .toOption
        .get
      val source = ComponentDescriptor(
        name = Some("application"),
        componentName = Some("application"),
        schemaVersion = Some(2),
        componentStyleSnapshot = Some(snapshot)
      )
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("implicit-subsystem"),
        subsystemName = "application",
        componentBindings = Vector(GenericSubsystemComponentBinding("application")),
        componentDescriptorOverrides = Vector(source)
      )

      When("the implicit subsystem projects its component descriptors")
      val projected = descriptor.toComponentDescriptors

      Then("the typed component-style requirement remains available before component materialization")
      projected shouldBe Vector(source)
      projected.head.componentStyleSnapshot.map(_.subsystemCapabilities) shouldBe Some(snapshot.subsystemCapabilities)
    }

    "decode typed subsystem capability provider authority separately from component instance metadata" in {
      Given("an assembly descriptor with explicit subsystem capability providers")
      val path = Files.createTempFile("generic-subsystem-capability-providers", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |component: art-scene
          |capabilities: [html, same-origin]
          |subsystemCapabilities:
          |  providers:
          |    - name: persistent-store
          |      component: art-scene
          |      provides: [datastore.persistent@1]
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get

      Then("only the dedicated declaration becomes typed subsystem authority")
      descriptor.componentBindings.head.capabilities shouldBe Vector("html", "same-origin")
      descriptor.subsystemCapabilityProviders shouldBe Vector(
        GenericSubsystemCapabilityProviderBinding(
          "persistent-store",
          "art-scene",
          Vector(SubsystemCapabilityId.parseC("datastore.persistent@1").toOption.get)
        )
      )
    }

    "reject a subsystem capability provider without typed capabilities" in {
      Given("an incomplete provider declaration")
      val path = Files.createTempFile("generic-subsystem-capability-provider-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |component: art-scene
          |subsystemCapabilities:
          |  providers:
          |    - name: incomplete
          |      component: art-scene
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the failure identifies the missing authority declaration")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("subsystemCapabilities.providers[0].provides")
    }

    "manage component instance declarations" which {
    "load named component instance metadata without collapsing duplicate component types" in {
      Given("an assembly descriptor with two configured instances of one component type")
      val path = Files.createTempFile("generic-subsystem-named-instances", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - name: textus-scraper
          |    instance: static-default
          |    purposes: [official-site, lightweight-navigation]
          |    capabilities: [html, same-origin]
          |    tags: [static, jsoup]
          |    priority: 100
          |    default: true
          |    config:
          |      scraper.mode: static
          |    rules:
          |      navigation:
          |        max-pages: 8
          |  - name: textus-scraper
          |    instance: dynamic-playwright
          |    purposes: [javascript-heavy-site]
          |    tags: [dynamic, browser]
          |    config:
          |      scraper.mode: dynamic
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val static = descriptor.componentBindings.head
      val dynamic = descriptor.componentBindings(1)

      Then("both component instances retain isolated identity and metadata")
      descriptor.componentBindings.map(_.componentName) shouldBe Vector("textus-scraper", "textus-scraper")
      descriptor.componentBindings.map(_.instanceName) shouldBe Vector("static-default", "dynamic-playwright")
      static.config shouldBe Map("scraper.mode" -> "static")
      static.rules.getRecord("navigation").flatMap(_.getInt("max-pages")) shouldBe Some(8)
      static.purposes should contain allOf ("official-site", "lightweight-navigation")
      static.capabilities should contain allOf ("html", "same-origin")
      static.tags should contain allOf ("static", "jsoup")
      static.priority shouldBe Some(100)
      static.isDefault shouldBe Some(true)
      dynamic.config shouldBe Map("scraper.mode" -> "dynamic")
    }

    "reject duplicate named component instance ids" in {
      Given("an assembly descriptor that repeats one component and instance pair")
      val path = Files.createTempFile("generic-subsystem-duplicate-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - name: textus-scraper
          |    instance: static-default
          |  - name: textus-scraper
          |    instance: static-default
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the duplicate runtime identity is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject malformed component instance names" in {
      Given("an assembly descriptor with a path-like instance name")
      val path = Files.createTempFile("generic-subsystem-invalid-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - name: textus-scraper
          |    instance: ../dynamic
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the malformed instance declaration is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject component instance names that collide after canonicalization" in {
      Given("two instance names with the same stable ComponentInstanceId value")
      val path = Files.createTempFile("generic-subsystem-canonical-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - name: textus-scraper
          |    instance: dynamic-playwright
          |  - name: textus-scraper
          |    instance: dynamic_playwright
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor constructs stable component instance identities")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the canonical identity collision is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "merge component defaults by component type and instance id" in {
      Given("two default instance declarations and one matching override")
      val defaults = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "art-scene",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("static"), config = Map("mode" -> "static")),
          GenericSubsystemComponentBinding(
            "textus-scraper",
            instance = Some("dynamic"),
            config = Map("mode" -> "dynamic", "timeout" -> "30s"),
            rules = Record.data("retry" -> 2),
            tags = Vector("browser")
          )
        )
      )
      val overrides = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.sar"),
        subsystemName = "art-scene",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("dynamic"), config = Map("mode" -> "browser"))
        )
      )

      When("component defaults are merged")
      val effective = GenericSubsystemDescriptor.mergeComponentDefaults(defaults, overrides)

      Then("the matching instance is field-merged without collapsing inherited settings")
      effective.componentBindings.map(_.instanceName) shouldBe Vector("static", "dynamic")
      effective.componentBindings.map(_.config("mode")) shouldBe Vector("static", "browser")
      effective.componentBindings(1).config("timeout") shouldBe "30s"
      effective.componentBindings(1).rules.getInt("retry") shouldBe Some(2)
      effective.componentBindings(1).tags shouldBe Vector("browser")
    }

    "reject malformed component declarations in assembly overrides" in {
      Given("a valid subsystem and an override containing duplicate canonical instance ids")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("base.yaml"),
        subsystemName = "art-scene",
        componentBindings = Vector(GenericSubsystemComponentBinding("textus-scraper"))
      )
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "components" -> Vector(
            Record.data("name" -> "textus-scraper", "instance" -> "static-driver"),
            Record.data("name" -> "textus-scraper", "instance" -> "static_driver")
          )
        ),
        "spec"
      )

      When("the assembly override is applied through the consequence boundary")
      val result = GenericSubsystemDescriptor.applyAssemblyOverrideC(base, overridesource)

      Then("the invalid override fails instead of retaining the base bindings silently")
      result shouldBe a[Consequence.Failure[_]]
    }

    "apply typed subsystem capability providers from an assembly override" in {
      Given("a component-CAR default and a SAR provider-authority override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.car"),
        subsystemName = "art-scene",
        componentBindings = Vector(GenericSubsystemComponentBinding("art-scene")),
        subsystemCapabilityProviders = Vector(GenericSubsystemCapabilityProviderBinding(
          "default-store",
          "art-scene",
          Vector(SubsystemCapabilityId.parseC("datastore.memory@1").toOption.get)
        ))
      )
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "subsystemCapabilities" -> Record.data(
            "providers" -> Vector(Record.data(
              "name" -> "persistent-store",
              "component" -> "art-scene",
              "provides" -> Vector("datastore.persistent@1")
            ))
          )
        ),
        source = "sar",
        path = Some(java.nio.file.Path.of("art-scene.sar"))
      )

      When("the assembly override is applied")
      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overridesource)

      Then("the override becomes the capability authority consumed by assembly admission")
      effective.subsystemCapabilityProviders shouldBe Vector(GenericSubsystemCapabilityProviderBinding(
        "persistent-store",
        "art-scene",
        Vector(SubsystemCapabilityId.parseC("datastore.persistent@1").toOption.get)
      ))
    }
    }

    "load extension and security descriptors" which {
    "load component extension bindings from the formal YAML schema using name and version" in {
      Given("a formal descriptor with component extension bindings")
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

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val componentdescriptor = descriptor.toComponentDescriptors.head
      val bindings = componentdescriptor.extensionBindings
      val adapterbindings = bindings.getVector("knowledge_source_adapters").toVector.flatten
      val keys = adapterbindings.collect {
        case r: org.goldenport.record.Record => r.getString("key")
        case m: Map[?, ?] => m.iterator.collectFirst { case (k, v) if k.toString == "key" => v.toString }
      }.flatten

      Then("the extension binding and component coordinate metadata are retained")
      descriptor.subsystemName shouldBe "mcprag"
      descriptor.componentVersion shouldBe Some("0.1.0-SNAPSHOT")
      descriptor.runtimeComponentNames shouldBe Vector("textus-mcp-rag")
      componentdescriptor.name shouldBe Some("textus-mcp-rag")
      componentdescriptor.version shouldBe Some("0.1.0-SNAPSHOT")
      keys shouldBe Vector("view")
    }

    "load security authentication wiring from the formal YAML schema" in {
      Given("a descriptor with authentication provider wiring")
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
          |    local_subject:
          |      id: standalone-local
          |      roles: [user]
          |      capabilities: [user, notification:read]
          |      security_level: user
          |      attributes:
          |        installation: standalone
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

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val auth = descriptor.security.flatMap(_.authentication).get
      val provider = auth.providers.head

      Then("the authentication policy and provider metadata are available")
      auth.convention shouldBe Some("enabled")
      auth.fallbackPrivilege shouldBe Some("disabled")
      auth.localSubject.map(_.id) shouldBe Some("standalone-local")
      auth.localSubject.map(_.roles) shouldBe Some(Vector("user"))
      auth.localSubject.map(_.capabilities) shouldBe Some(Vector("user", "notification:read"))
      auth.localSubject.flatMap(_.securityLevel) shouldBe Some("user")
      auth.localSubject.map(_.attributes) shouldBe Some(Map("installation" -> "standalone"))
      provider.name shouldBe "user-account"
      provider.component shouldBe "textus-user-account"
      provider.kind shouldBe Some("human")
      provider.enabled shouldBe Some(true)
      provider.priority shouldBe Some(100)
      provider.schemes shouldBe Vector("bearer", "refresh-token")
      provider.isDefault shouldBe Some(true)
    }

    "strict nested assembly decoding" which {
    "reject semantically invalid nested assembly defaults instead of dropping them" in {
      Given("invalid local-subject, authentication-provider, message-delivery-provider, and builtin declarations")
      val localsubject = Files.createTempFile("invalid-local-subject", ".yaml")
      Files.writeString(
        localsubject,
        """subsystem: invalid-local-subject
          |components:
          |  - name: invalid-local-subject
          |security:
          |  authentication:
          |    local_subject:
          |      roles: [user]
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val authenticationprovider = Files.createTempFile("invalid-authentication-provider", ".yaml")
      Files.writeString(
        authenticationprovider,
        """subsystem: invalid-authentication-provider
          |components:
          |  - name: invalid-authentication-provider
          |security:
          |  authentication:
          |    providers:
          |      - name: incomplete
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val messagedelivery = Files.createTempFile("invalid-message-delivery-provider", ".yaml")
      Files.writeString(
        messagedelivery,
        """subsystem: invalid-message-delivery-provider
          |components:
          |  - name: invalid-message-delivery-provider
          |security:
          |  message_delivery:
          |    providers:
          |      - name: incomplete
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val builtincar = Files.createTempFile("invalid-builtin-default", ".car")
      _write_zip(
        builtincar,
        Map(
          "component-descriptor.json" -> """{"name":"invalid-builtin-default","version":"1.0.0","component":"invalid-builtin-default"}""",
          "assembly-descriptor.yaml" ->
            """subsystem: invalid-builtin-default
              |components:
              |  - name: invalid-builtin-default
              |builtin: invalid
              |""".stripMargin
        )
      )

      When("strict descriptor and component-CAR loading decodes each nested declaration")
      val results = Vector(
        GenericSubsystemDescriptor.load(localsubject),
        GenericSubsystemDescriptor.load(authenticationprovider),
        GenericSubsystemDescriptor.load(messagedelivery),
        GenericSubsystemDescriptor.loadComponentArchive(builtincar)
      )
      val overridebase = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("invalid-assembly-override"),
        subsystemName = "invalid-assembly-override",
        componentBindings = Vector(GenericSubsystemComponentBinding("invalid-assembly-override"))
      )
      val overrideresults = Vector(
        Record.data("runtime" -> "invalid"),
        Record.data("runtime" -> Record.data("user_notification" -> "invalid")),
        Record.data("runtime" -> Record.data("user_notification" -> Record.data(
          "providers" -> Vector(Record.data("name" -> "incomplete"))
        ))),
        Record.data("builtin" -> "invalid")
      ).map(record => GenericSubsystemDescriptor.applyAssemblyOverrideC(
        overridebase,
        GenericSubsystemAssemblyDescriptorSource(record, "invalid-override")
      ))

      Then("each present malformed semantic declaration remains a structured failure")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
      overrideresults.foreach(_ shouldBe a[Consequence.Failure[_]])
    }
    }

    "load operation authorization rules from the formal YAML schema" in {
      Given("a descriptor with anonymous and production authorization rules")
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

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val post = descriptor.operationAuthorizationRule("notice-board.notice.post-notice").get
      val admin = descriptor.operationAuthorizationRule("notice-board.notice.admin-only").get

      Then("each operation exposes its resolved authorization modes")
      post.allowAnonymous shouldBe true
      post.anonymousOperationModes.map(_.name) shouldBe Vector("develop", "test")
      admin.operationModes.map(_.name) shouldBe Vector("production")
    }

    "load security authorization role definitions from the formal YAML schema" in {
      Given("a descriptor with composable authorization roles")
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

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val roles = descriptor.security.flatMap(_.authorization).map(_.roles).get

      Then("role capabilities and inheritance are retained")
      roles("blob_user").capabilities should contain allOf ("collection:blob:create", "collection:blob:read")
      roles("blob_operator").includes shouldBe Vector("blob_user")
      roles("blob_operator").capabilities should contain allOf ("association:blob_attachment:delete", "store:blobstore:status")
    }

    "load security authorization resource policies from the formal YAML schema" in {
      Given("a descriptor with collection, association, and store policies")
      val path = Files.createTempFile("generic-subsystem-security-resource-policy", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: blob
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authorization:
          |    resources:
          |      collections:
          |        blob:
          |          create:
          |            capability: collection:blob:create
          |          delete:
          |            permission: execute
          |      associations:
          |        blob_attachment:
          |          create:
          |            capability: association:blob_attachment:create
          |      stores:
          |        blobstore:
          |          status:
          |            capability: store:blobstore:status
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val resources = descriptor.security.flatMap(_.authorization).map(_.resources).get

      Then("resource policies remain addressable by resource type and operation")
      resources.collection(Some("blob"), "create").get.capabilities shouldBe Vector("collection:blob:create")
      resources.collection(Some("blob"), "delete").get.permission shouldBe Some("execute")
      resources.association(Some("blob_attachment"), "create").get.capabilities shouldBe Vector("association:blob_attachment:create")
      resources.store(Some("blobstore"), "status").get.capabilities shouldBe Vector("store:blobstore:status")
    }

    "reject invalid security authorization resource policies" in {
      Given("a descriptor with a scalar resource policy")
      val path = Files.createTempFile("generic-subsystem-security-resource-policy-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: blob
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authorization:
          |    resources:
          |      collections:
          |        blob: invalid-scalar-resource-policy
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the malformed resource policy is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject invalid security authorization resource permission values" in {
      Given("a descriptor with an unknown permission value")
      val path = Files.createTempFile("generic-subsystem-security-resource-permission-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: blob
          |    version: 0.1.0-SNAPSHOT
          |security:
          |  authorization:
          |    resources:
          |      collections:
          |        blob:
          |          delete:
          |            permission: exectue
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the unknown permission is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject invalid security authorization role definitions" in {
      Given("a descriptor with a scalar role definition")
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

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the malformed role is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject invalid user notification event forwarding rules" in {
      Given("an event forwarding declaration without an event selector")
      val path = Files.createTempFile("generic-subsystem-user-notification-event-forwarding-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-notify
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - name: textus-user-notification
          |    version: 0.1.0-SNAPSHOT
          |runtime:
          |  userNotification:
          |    providers:
          |      - name: textus-user-notification
          |        component: textus-user-notification
          |        enabled: true
          |    eventForwarding:
          |      - provider: textus-user-notification
          |        appVisibleOnly: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the incomplete forwarding rule is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "let SAR security role definitions override inherited CAR role definitions by role name" in {
      Given("CAR role defaults and a SAR role override with an equivalent normalized name")
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
            ),
            resources = org.goldenport.cncf.security.AuthorizationResourcePolicies(
              collections = Map(
                "blob" -> Map(
                  "create" -> org.goldenport.cncf.security.AuthorizationResourcePolicy(
                    capabilities = Vector("collection:blob:read")
                  )
                )
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
            ),
            resources = org.goldenport.cncf.security.AuthorizationResourcePolicies(
              collections = Map(
                "blob" -> Map(
                  "create" -> org.goldenport.cncf.security.AuthorizationResourcePolicy(
                    capabilities = Vector("collection:blob:create")
                  )
                )
              )
            )
          ))
        ))
      )

      When("the component defaults and subsystem descriptor are merged")
      val effective = GenericSubsystemDescriptor.mergeComponentDefaults(car, sar)
      val roles = effective.security.flatMap(_.authorization).map(_.roles).get

      Then("the SAR role and resource policy replace the inherited definitions")
      roles.values.map(_.name).toSet shouldBe Set("blob-user")
      roles.values.flatMap(_.capabilities).toSet shouldBe Set("collection:blob:create")
      effective.security.flatMap(_.authorization).flatMap(_.resources.collection(Some("blob"), "create"))
        .map(_.capabilities) shouldBe Some(Vector("collection:blob:create"))
    }

    "keep legacy coordinate parsing for backward compatibility" in {
      Given("a legacy component coordinate declaration")
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

      When("the descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get

      Then("component name and version are derived from the coordinate")
      descriptor.componentBindings.head.componentName shouldBe "notice-board"
      descriptor.componentBindings.head.componentVersion shouldBe Some("0.1.0-SNAPSHOT")
    }
    }

    "compose component and subsystem archives" which {
    "create a synthetic subsystem descriptor from component CAR assembly metadata" in {
      Given("a component CAR containing component and assembly descriptors")
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

      When("the component archive is loaded as a subsystem")
      val loaded = GenericSubsystemDescriptor.loadComponentArchive(car).toOption.get

      Then("the synthetic subsystem includes assembly components and security defaults")
      loaded.subsystemName shouldBe "cwitter"
      loaded.componentBindings.map(_.componentName) shouldBe Vector("cwitter", "textus-user-account")
      loaded.security.flatMap(_.authentication).flatMap(_.convention) shouldBe Some("enabled")
      loaded.assemblyDescriptor.map(_.source) shouldBe Some("component-car")
    }

    "reject a component archive with an invalid assembly descriptor" in {
      Given("a component CAR whose assembly descriptor has no subsystem name")
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

      When("the component archive is loaded")
      val result = GenericSubsystemDescriptor.loadComponentArchive(car)

      Then("the invalid embedded assembly is rejected")
      result match {
        case Consequence.Failure(_) => succeed
        case Consequence.Success(value) => fail(s"expected invalid assembly descriptor failure but got ${value}")
      }
    }

    "reject missing and malformed explicitly configured assembly descriptors" in {
      Given("one absent assembly path and one malformed configured assembly descriptor")
      val missing = Files.createTempDirectory("missing-configured-assembly").resolve("assembly.yaml")
      val malformed = Files.createTempFile("malformed-configured-assembly", ".yaml")
      Files.writeString(malformed, "subsystem: [unterminated", StandardCharsets.UTF_8)

      When("the explicit assembly descriptor loader reads each configured path")
      val missingresult = GenericSubsystemDescriptor.loadAssemblyDescriptorC(missing)
      val malformedresult = GenericSubsystemDescriptor.loadAssemblyDescriptorC(malformed)

      Then("both configured-source failures remain structured instead of becoming absent defaults")
      missingresult shouldBe a[Consequence.Failure[_]]
      malformedresult shouldBe a[Consequence.Failure[_]]
    }

    "let a SAR descriptor inherit authentication provider defaults from a component CAR assembly descriptor" in {
      Given("component CAR authentication defaults and a SAR without security overrides")
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
      val cardefaults = GenericSubsystemDescriptor.loadComponentArchive(car).toOption.get
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
      val sardescriptor = GenericSubsystemDescriptor.load(sar).toOption.get

      When("the CAR defaults and SAR descriptor are merged")
      val effective = GenericSubsystemDescriptor.mergeComponentDefaults(cardefaults, sardescriptor)
      val provider = effective.security.flatMap(_.authentication).toVector.flatMap(_.providers).headOption.get

      Then("the SAR inherits the provider and component dependency")
      provider.name shouldBe "user-account"
      provider.component shouldBe "textus-user-account"
      effective.componentBindings.map(_.componentName) shouldBe Vector("cwitter", "textus-user-account")
    }

    "let a SAR assembly descriptor override a provider inherited from component CAR assembly defaults" in {
      Given("an inherited authentication provider and a SAR assembly override")
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
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
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

      When("the assembly override is applied")
      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overridesource)
      val provider = effective.security.flatMap(_.authentication).toVector.flatMap(_.providers).headOption.get

      Then("the provider implementation and priority come from the SAR")
      provider.name shouldBe "user-account"
      provider.component shouldBe "custom-user-account"
      provider.priority shouldBe Some(200)
    }

    "merge partial SAR assembly wiring overrides with inherited component CAR wiring" in {
      Given("two inherited wiring entries and one matching SAR override")
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
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "wiring" -> Vector(
            _wiring_record("cwitter", "account", "signin", "enterprise-user-account", "account", "signin")
          )
        ),
        source = "sar",
        path = Some(java.nio.file.Path.of("cwitter.sar"))
      )

      When("the partial wiring override is applied")
      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overridesource)
      val bindings = effective.resolvedWiring

      Then("the matching route is replaced while unrelated wiring is inherited")
      bindings.map(_.toComponent).toSet shouldBe Set("enterprise-user-account", "textus-message-delivery-stub")
      bindings.count(_.fromService == "account") shouldBe 1
    }
    }

    "load and validate SPI assembly bindings" which {
    "load test descriptor config and assembly SPI bindings" in {
      Given("a test descriptor with runtime config and an SPI binding")
      val path = Files.createTempFile("cncf-test-descriptor", ".yaml")
      Files.writeString(
        path,
        """kind: test-descriptor
          |config:
          |  textus.web.demo-assist.enabled: true
          |assembly:
          |  components:
          |    - name: target-component
          |      config:
          |        provider.mode: deterministic-test
          |  spi:
          |    bindings:
          |      - socket:
          |          component: target-component
          |          instance: consumer-default
          |          name: ai
          |          contract: ai-runner
          |        provider:
          |          component: target-component
          |          instance: provider-default
          |        selection:
          |          mode: test
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the test descriptor is loaded and applied")
      val descriptor = RuntimeTestDescriptor.load(path).toOption.get
      val subsystem = GenericSubsystemDescriptor(
        path = path,
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component"))
      )
      val effective = descriptor.assembly
        .map(GenericSubsystemDescriptor.applyAssemblyOverride(subsystem, _))
        .getOrElse(subsystem)
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(effective).toOption.get

      Then("runtime config, declared component configuration, and typed SPI selectors remain available")
      descriptor.config shouldBe Map("textus.web.demo-assist.enabled" -> "true")
      effective.componentBindings shouldBe Vector(
        GenericSubsystemComponentBinding(
          componentName = "target-component",
          config = Map("provider.mode" -> "deterministic-test")
        )
      )
      bindings.size shouldBe 1
      bindings.head.socket.component shouldBe Some("target-component")
      bindings.head.socket.instance shouldBe Some("consumer-default")
      bindings.head.socket.name shouldBe Some("ai")
      bindings.head.socket.contract shouldBe "ai-runner"
      bindings.head.provider.component shouldBe Some("target-component")
      bindings.head.provider.instance shouldBe Some("provider-default")
      bindings.head.selection.mode shouldBe Some("test")
    }

    "load multiple providers for one required socket set" in {
      Given("an assembly descriptor with two exact many-cardinality bindings")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector("static", "dynamic").map { instance =>
                Record.data(
                  "socket" -> Record.data(
                    "component" -> "target-component",
                    "name" -> "scrapers",
                    "contract" -> "ai-runner",
                    "cardinality" -> "many",
                    "required" -> true
                  ),
                  "provider" -> Record.data(
                    "component" -> "textus-scraper",
                    "instance" -> instance
                  )
                )
              }
            )
          ),
          source = "spec"
        ))
      )

      When("assembly SPI bindings are decoded")
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get

      Then("both providers retain required set cardinality and exact identity")
      bindings.size shouldBe 2
      bindings.map(_.socket.cardinality).toSet shouldBe Set(org.goldenport.cncf.spi.SpiCardinality.OneOrMore)
      bindings.flatMap(_.provider.instance).toSet shouldBe Set("static", "dynamic")
    }

    "merge socket set bindings by provider member identity" in {
      Given("two inherited socket-set members and one provider-specific override")
      def _binding_(instance: String, mode: String): Record =
        Record.data(
          "socket" -> Record.data(
            "component" -> "target-component",
            "name" -> "scrapers",
            "contract" -> "ai-runner",
            "cardinality" -> "many"
          ),
          "provider" -> Record.data(
            "component" -> "textus-scraper",
            "instance" -> instance
          ),
          "selection" -> Record.data("mode" -> mode)
        )
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data("spi" -> Record.data("bindings" -> Vector(
            _binding_("static", "base-static"),
            _binding_("dynamic", "base-dynamic")
          ))),
          source = "car"
        ))
      )
      val overridevalue = GenericSubsystemAssemblyDescriptorSource(
        Record.data("spi" -> Record.data("bindings" -> Vector(
          _binding_("dynamic", "override-dynamic")
        ))),
        source = "sar"
      )

      When("the subsystem assembly override is applied")
      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overridevalue)
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(effective).toOption.get

      Then("the matching provider is replaced while the other set member remains")
      bindings.size shouldBe 2
      bindings.map(x => x.provider.instance.get -> x.selection.mode.get).toMap shouldBe Map(
        "static" -> "base-static",
        "dynamic" -> "override-dynamic"
      )
    }

    "reject an unknown socket cardinality" in {
      Given("an assembly binding with an unsupported cardinality")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(Record.data(
                "socket" -> Record.data(
                  "component" -> "target-component",
                  "contract" -> "ai-runner",
                  "cardinality" -> "several"
                ),
                "provider" -> Record.data("component" -> "textus-ai")
              ))
            )
          ),
          source = "spec"
        ))
      )

      When("assembly SPI bindings are decoded")
      val result = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor)

      Then("the malformed cardinality fails deterministically")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("unknown SPI socket cardinality")
    }

    "reject contradictory socket cardinality and required declarations" in {
      Given("optional-required and non-empty-optional assembly bindings")
      def _descriptor_(cardinality: String, required: Boolean): GenericSubsystemDescriptor =
        GenericSubsystemDescriptor(
          path = java.nio.file.Path.of("component.car"),
          subsystemName = "target",
          componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
          assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
            Record.data(
              "spi" -> Record.data(
                "bindings" -> Vector(Record.data(
                  "socket" -> Record.data(
                    "component" -> "target-component",
                    "contract" -> "ai-runner",
                    "cardinality" -> cardinality,
                    "required" -> required
                  ),
                  "provider" -> Record.data("component" -> "textus-ai")
                ))
              )
            ),
            source = "spec"
          ))
        )

      When("assembly SPI bindings are decoded")
      val optionalrequired = GenericSubsystemDescriptor.resolveAssemblySpiBindings(_descriptor_("optional", true))
      val nonemptyoptional = GenericSubsystemDescriptor.resolveAssemblySpiBindings(_descriptor_("one-or-more", false))

      Then("both contradictory declarations fail explicitly")
      optionalrequired shouldBe a[Consequence.Failure[_]]
      optionalrequired.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("cannot be required")
      nonemptyoptional shouldBe a[Consequence.Failure[_]]
      nonemptyoptional.asInstanceOf[Consequence.Failure[_]].conclusion.display should include ("cannot be optional")
    }

    "reject an SPI provider instance without a provider component" in {
      Given("an assembly binding with an instance-only provider selector")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(Record.data(
                "socket" -> Record.data(
                  "component" -> "target-component",
                  "contract" -> "ai-runner"
                ),
                "provider" -> Record.data(
                  "instance" -> "provider-default"
                )
              ))
            )
          ),
          source = "spec"
        ))
      )

      When("assembly SPI bindings are decoded")
      val result = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor)

      Then("the incomplete exact selector is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject duplicate assembly bindings for one named socket identity" in {
      Given("two bindings targeting canonical aliases of the same socket instance and name")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(
                Record.data(
                  "socket" -> Record.data(
                    "component" -> "target-component",
                    "instance" -> "consumer-default",
                    "name" -> "ai",
                    "contract" -> "ai-runner"
                  ),
                  "provider" -> Record.data("component" -> "provider-a")
                ),
                Record.data(
                  "socket" -> Record.data(
                    "component" -> "target_component",
                    "instance" -> "consumer_default",
                    "name" -> "ai",
                    "contract" -> "ai-runner"
                  ),
                  "provider" -> Record.data("component" -> "provider-b")
                )
              )
            )
          ),
          source = "spec"
        ))
      )

      When("assembly SPI bindings are decoded")
      val result = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor)

      Then("the canonical socket identity collision is rejected")
      result shouldBe a[Consequence.Failure[_]]
    }

    "merge assembly SPI bindings by socket selector" in {
      Given("inherited SPI bindings and a matching test override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(
                _spi_binding_record("target-component", "ai-runner", "prod-provider", "prod"),
                _spi_binding_record("target-component", "geo-resolver", "geo-provider", "prod")
              )
            )
          ),
          source = "component-car",
          path = Some(java.nio.file.Path.of("component.car"))
        ))
      )
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "spi" -> Record.data(
            "bindings" -> Vector(
              _spi_binding_record("target-component", "ai-runner", "test-provider", "test")
            )
          )
        ),
        source = "test",
        path = Some(java.nio.file.Path.of("test.yaml"))
      )

      When("the assembly override is applied")
      val effective = GenericSubsystemDescriptor.applyAssemblyOverride(base, overridesource)
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(effective).toOption.get

      Then("the matching socket provider is replaced and other sockets remain")
      bindings.map(_.socket.contract).toSet shouldBe Set("ai-runner", "geo-resolver")
      bindings.find(_.socket.contract == "ai-runner").flatMap(_.provider.component) shouldBe Some("test-provider")
      bindings.find(_.socket.contract == "geo-resolver").flatMap(_.provider.component) shouldBe Some("geo-provider")
    }

    "reject invalid assembly SPI bindings instead of dropping them" in {
      Given("an assembly SPI binding without a socket contract")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(
                Record.data(
                  "socket" -> Record.data(
                    "component" -> "target-component"
                  ),
                  "provider" -> Record.data(
                    "component" -> "test-provider"
                  )
                )
              )
            )
          ),
          source = "test",
          path = Some(java.nio.file.Path.of("test.yaml"))
        ))
      )

      When("SPI bindings are resolved")
      val result = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor)

      Then("the malformed binding fails deterministically")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject provider service matching until it is supported" in {
      Given("an SPI provider selector that requests service-level matching")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(GenericSubsystemComponentBinding("target-component")),
        assemblyDescriptor = Some(GenericSubsystemAssemblyDescriptorSource(
          Record.data(
            "spi" -> Record.data(
              "bindings" -> Vector(
                Record.data(
                  "socket" -> Record.data(
                    "component" -> "target-component",
                    "contract" -> "ai-runner"
                  ),
                  "provider" -> Record.data(
                    "component" -> "test-provider",
                    "service" -> "ai-runner-test"
                  )
                )
              )
            )
          ),
          source = "test",
          path = Some(java.nio.file.Path.of("test.yaml"))
        ))
      )

      When("SPI bindings are resolved")
      val result = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor)

      Then("the unsupported selector fails explicitly")
      result shouldBe a[Consequence.Failure[_]]
    }
    }

    "preserve journal sample compatibility" which {
    "load the textus-identity journal sample with security authentication wiring" in {
      Given("the maintained textus-identity descriptor sample")
      val path = java.nio.file.Path.of("/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")

      When("the sample descriptor is loaded")
      val descriptor = GenericSubsystemDescriptor.load(path).toOption.get
      val auth = descriptor.security.flatMap(_.authentication).get
      val provider = auth.providers.head

      Then("its authentication wiring remains executable documentation")
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

  private def _spi_binding_record(
    socketcomponent: String,
    contract: String,
    providercomponent: String,
    mode: String
  ): Record =
    Record.data(
      "socket" -> Record.data(
        "component" -> socketcomponent,
        "contract" -> contract
      ),
      "provider" -> Record.data(
        "component" -> providercomponent
      ),
      "selection" -> Record.data(
        "mode" -> mode
      )
    )
}
