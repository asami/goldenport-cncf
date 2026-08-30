package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, ComponentStyleCatalog, ComponentStyleId, SubsystemCapabilityId}
import org.goldenport.cncf.config.RuntimeTestDescriptor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemDescriptorSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  private val _e1 = afterWord("in spec:generic-subsystem-descriptor, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:generic-subsystem-descriptor, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:generic-subsystem-descriptor, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _temporary_paths = scala.collection.mutable.ArrayBuffer.empty[Path]

  override def afterAll(): Unit = {
    try _temporary_paths.reverse.foreach(_delete_tree)
    finally super.afterAll()
  }

  "GenericSubsystemDescriptor" should {
    "E3 preserve a component style snapshot when it becomes an implicit subsystem binding" must _metadata("E3") {
      "when exercising: preserve a component style snapshot when it becomes an implicit subsystem binding" in {
      Given("a schema-3 canonical component descriptor with a resolved style")
      val snapshot = ComponentStyleCatalog.default
        .resolveC(ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get)
        .flatMap(ComponentStyleCatalog.default.expandC)
        .toOption
        .get
      val source = ComponentDescriptor(
        name = Some("org.example.Application"),
        componentName = Some("org.example.Application"),
        schemaVersion = Some(3),
        componentId = Some(org.goldenport.cncf.component.ComponentId("org.example.Application")),
        componentStyleSnapshot = Some(snapshot)
      )
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("implicit-subsystem"),
        subsystemName = "application",
        componentBindings = Vector(_binding("Application")),
        componentDescriptorOverrides = Vector(source)
      )

      When("the implicit subsystem projects its component descriptors")
      val projected = descriptor.toComponentDescriptors

      Then("the typed component-style requirement remains available before component materialization")
      projected shouldBe Vector(source)
      projected.head.componentStyleSnapshot.map(_.subsystemCapabilities) shouldBe Some(snapshot.subsystemCapabilities)
      }
    }

    "E4 decode typed subsystem capability provider authority separately from component instance metadata" must _metadata("E4") {
      "when exercising: decode typed subsystem capability provider authority separately from component instance metadata" in {
      Given("an assembly descriptor with explicit subsystem capability providers")
      val path = _create_temp_file("generic-subsystem-capability-providers", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: ArtScene
          |    version: 1.0.0
          |    capabilities: [html, same-origin]
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
    }

    "E5 reject a subsystem capability provider without typed capabilities" must _metadata("E5") {
      "when exercising: reject a subsystem capability provider without typed capabilities" in {
      Given("an incomplete provider declaration")
      val path = _create_temp_file("generic-subsystem-capability-provider-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: ArtScene
          |    version: 1.0.0
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
    }

    "manage component instance declarations" which {
    "E6 load named component instance metadata without collapsing duplicate component types" must _metadata("E6") {
      "when exercising: load named component instance metadata without collapsing duplicate component types" in {
      Given("an assembly descriptor with two configured instances of one component type")
      val path = _create_temp_file("generic-subsystem-named-instances", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
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
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
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
      descriptor.componentBindings.map(_.componentName) shouldBe Vector(
        "org.example.TextusScraper",
        "org.example.TextusScraper"
      )
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
    }

    "E7 reject duplicate named component instance ids" must _metadata("E7") {
      "when exercising: reject duplicate named component instance ids" in {
      Given("an assembly descriptor that repeats one component and instance pair")
      val path = _create_temp_file("generic-subsystem-duplicate-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
          |    instance: static-default
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
          |    instance: static-default
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the duplicate runtime identity is rejected")
      result shouldBe a[Consequence.Failure[_]]
      }
    }

    "E8 reject malformed component instance names" must _metadata("E8") {
      "when exercising: reject malformed component instance names" in {
      Given("an assembly descriptor with a path-like instance name")
      val path = _create_temp_file("generic-subsystem-invalid-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
          |    instance: ../dynamic
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the malformed instance declaration is rejected")
      result shouldBe a[Consequence.Failure[_]]
      }
    }

    "E9 reject duplicate component instance names" must _metadata("E9") {
      "when exercising: reject duplicate component instance names" in {
      Given("two identical valid instance names for one canonical ComponentId")
      val path = _create_temp_file("generic-subsystem-canonical-instance", ".yaml")
      Files.writeString(
        path,
        """subsystem: art-scene
          |components:
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
          |    instance: dynamic-playwright
          |  - namespace: org.example
          |    id: TextusScraper
          |    version: 1.0.0
          |    instance: dynamic-playwright
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the descriptor admits the canonical component bindings")
      val result = GenericSubsystemDescriptor.load(path)

      Then("the duplicate canonical instance identity is rejected")
      result shouldBe a[Consequence.Failure[_]]
      }
    }

    "E10 merge component defaults by component type and instance id" must _metadata("E10") {
      "when exercising: merge component defaults by component type and instance id" in {
      Given("two default instance declarations and one matching override")
      val defaults = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "art-scene",
        componentBindings = Vector(
          _binding("TextusScraper", instance = Some("static"), config = Map("mode" -> "static")),
          GenericSubsystemComponentBinding(
            ComponentId("org.example.TextusScraper").name,
            version = Some("1.0.0"),
            instance = Some("dynamic"),
            config = Map("mode" -> "dynamic", "timeout" -> "30s"),
            rules = Record.data("retry" -> 2),
            tags = Vector("browser"),
            componentId = Some(ComponentId("org.example.TextusScraper"))
          )
        )
      )
      val overrides = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.sar"),
        subsystemName = "art-scene",
        componentBindings = Vector(
          _binding("TextusScraper", instance = Some("dynamic"), config = Map("mode" -> "browser"))
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
    }

    "E11 reject malformed component declarations in assembly overrides" must _metadata("E11") {
      "when exercising: reject malformed component declarations in assembly overrides" in {
      Given("a valid subsystem and an override containing duplicate canonical instance ids")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("base.yaml"),
        subsystemName = "art-scene",
        componentBindings = Vector(_binding("TextusScraper"))
      )
      val overridesource = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "components" -> Vector(
            Record.data(
              "namespace" -> "org.example",
              "id" -> "TextusScraper",
              "version" -> "1.0.0",
              "instance" -> "static-driver"
            ),
            Record.data(
              "namespace" -> "org.example",
              "id" -> "TextusScraper",
              "version" -> "1.0.0",
              "instance" -> "static-driver"
            )
          )
        ),
        "spec"
      )

      When("the assembly override is applied through the consequence boundary")
      val result = GenericSubsystemDescriptor.applyAssemblyOverrideC(base, overridesource)

      Then("the invalid override fails instead of retaining the base bindings silently")
      result shouldBe a[Consequence.Failure[_]]
      }
    }

    "E12 apply typed subsystem capability providers from an assembly override" must _metadata("E12") {
      "when exercising: apply typed subsystem capability providers from an assembly override" in {
      Given("a component-CAR default and a SAR provider-authority override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.car"),
        subsystemName = "art-scene",
        componentBindings = Vector(_binding("ArtScene")),
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


    "decode canonical namespace/id/version assembly records" in {
      Given("one assembly descriptor with canonical component fields")
      val yaml =
        """subsystem: catalog
          |components:
          |  - namespace: org.example
          |    id: Catalog
          |    version: 1.0.0
          |""".stripMargin

      When("the descriptor is decoded")
      val result = _load(yaml)

      Then("the binding carries one exact qualified ComponentId and release")
      val binding = result.toOption.get.componentBindings.head
      binding.componentName shouldBe "org.example.Catalog"
      binding.componentId.map(_.name) shouldBe Some("org.example.Catalog")
      binding.version shouldBe Some("1.0.0")
    }

    "reject legacy identity records at assembly admission" in {
      Given("an assembly record that supplies a legacy name declaration")
      val yaml =
        """subsystem: catalog
          |components:
          |  - name: catalog
          |    version: 1.0.0
          |""".stripMargin

      When("the assembly descriptor is decoded")
      val result = _load(yaml)

      Then("no local, artifact, or qualified-name fallback supplies Component identity")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
        "component assembly binding rejects legacy identity fields: name"
      )
    }

    "reject a canonical record that also declares a legacy identity field" in {
      Given("an assembly record with canonical fields and an agreeing legacy name")
      val yaml =
        """subsystem: catalog
          |components:
          |  - namespace: org.example
          |    id: Catalog
          |    version: 1.0.0
          |    name: org.example.Catalog
          |""".stripMargin

      When("the assembly descriptor is decoded")
      val result = _load(yaml)

      Then("the legacy declaration does not become an alternative identity authority")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include (
        "component assembly binding rejects legacy identity fields"
      )
    }
    "load extension and security descriptors" which {
    "E13 load component extension bindings from the formal YAML schema using name and version" must _metadata("E13") {
      "when exercising: load component extension bindings from the formal YAML schema using name and version" in {
      Given("a formal descriptor with component extension bindings")
      val path = _create_temp_file("generic-subsystem-descriptor", ".yaml")
      Files.writeString(
        path,
        """subsystem: mcprag
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: TextusMcpRag
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
      descriptor.runtimeComponentNames shouldBe Vector("org.example.TextusMcpRag")
      componentdescriptor.name shouldBe Some("org.example.TextusMcpRag")
      componentdescriptor.version shouldBe Some("0.1.0-SNAPSHOT")
      keys shouldBe Vector("view")
      }
    }

    "E14 load security authentication wiring from the formal YAML schema" must _metadata("E14") {
      "when exercising: load security authentication wiring from the formal YAML schema" in {
      Given("a descriptor with authentication provider wiring")
      val path = _create_temp_file("generic-subsystem-security-descriptor", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-identity
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: TextusUserAccount
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
    }

    "strict nested assembly decoding" which {
    "E15 reject semantically invalid nested assembly defaults instead of dropping them" must _metadata("E15") {
      "when exercising: reject semantically invalid nested assembly defaults instead of dropping them" in {
      Given("invalid local-subject, authentication-provider, message-delivery-provider, and builtin declarations")
      val localsubject = _create_temp_file("invalid-local-subject", ".yaml")
      Files.writeString(
        localsubject,
        """subsystem: invalid-local-subject
          |components:
          |  - namespace: org.example
          |    id: InvalidLocalSubject
          |    version: 1.0.0
          |security:
          |  authentication:
          |    local_subject:
          |      roles: [user]
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val authenticationprovider = _create_temp_file("invalid-authentication-provider", ".yaml")
      Files.writeString(
        authenticationprovider,
        """subsystem: invalid-authentication-provider
          |components:
          |  - namespace: org.example
          |    id: InvalidAuthenticationProvider
          |    version: 1.0.0
          |security:
          |  authentication:
          |    providers:
          |      - name: incomplete
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val messagedelivery = _create_temp_file("invalid-message-delivery-provider", ".yaml")
      Files.writeString(
        messagedelivery,
        """subsystem: invalid-message-delivery-provider
          |components:
          |  - namespace: org.example
          |    id: InvalidMessageDeliveryProvider
          |    version: 1.0.0
          |security:
          |  message_delivery:
          |    providers:
          |      - name: incomplete
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val builtincar = _create_temp_file("invalid-builtin-default", ".car")
      _write_zip(
        builtincar,
        Map(
          "component-descriptor.json" ->
            """{"schemaVersion":3,"component":{"namespace":"org.example","id":"InvalidBuiltinDefault","version":"1.0.0"}}""",
          "assembly-descriptor.yaml" ->
            """subsystem: invalid-builtin-default
              |components:
              |  - namespace: org.example
              |    id: InvalidBuiltinDefault
              |    version: 1.0.0
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
        componentBindings = Vector(_binding("InvalidAssemblyOverride"))
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
    }

    "E16 load operation authorization rules from the formal YAML schema" must _metadata("E16") {
      "when exercising: load operation authorization rules from the formal YAML schema" in {
      Given("a descriptor with anonymous and production authorization rules")
      val path = _create_temp_file("generic-subsystem-operation-authorization", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-sample
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: NoticeBoard
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
    }

    "E17 load security authorization role definitions from the formal YAML schema" must _metadata("E17") {
      "when exercising: load security authorization role definitions from the formal YAML schema" in {
      Given("a descriptor with composable authorization roles")
      val path = _create_temp_file("generic-subsystem-security-authorization", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Blob
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
    }

    "E18 load security authorization resource policies from the formal YAML schema" must _metadata("E18") {
      "when exercising: load security authorization resource policies from the formal YAML schema" in {
      Given("a descriptor with collection, association, and store policies")
      val path = _create_temp_file("generic-subsystem-security-resource-policy", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Blob
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
    }

    "E19 reject invalid security authorization resource policies" must _metadata("E19") {
      "when exercising: reject invalid security authorization resource policies" in {
      Given("a descriptor with a scalar resource policy")
      val path = _create_temp_file("generic-subsystem-security-resource-policy-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Blob
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
    }

    "E20 reject invalid security authorization resource permission values" must _metadata("E20") {
      "when exercising: reject invalid security authorization resource permission values" in {
      Given("a descriptor with an unknown permission value")
      val path = _create_temp_file("generic-subsystem-security-resource-permission-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Blob
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
    }

    "E21 reject invalid security authorization role definitions" must _metadata("E21") {
      "when exercising: reject invalid security authorization role definitions" in {
      Given("a descriptor with a scalar role definition")
      val path = _create_temp_file("generic-subsystem-security-authorization-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-blob
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Blob
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
    }

    "E22 reject invalid user notification event forwarding rules" must _metadata("E22") {
      "when exercising: reject invalid user notification event forwarding rules" in {
      Given("an event forwarding declaration without an event selector")
      val path = _create_temp_file("generic-subsystem-user-notification-event-forwarding-invalid", ".yaml")
      Files.writeString(
        path,
        """subsystem: textus-notify
          |version: 0.1.0-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: TextusUserNotification
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
    }

    "E23 let SAR security role definitions override inherited CAR role definitions by role name" must _metadata("E23") {
      "when exercising: let SAR security role definitions override inherited CAR role definitions by role name" in {
      Given("CAR role defaults and a SAR role override with an equivalent normalized name")
      val car = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<car>"),
        subsystemName = "blob-car",
        componentBindings = Vector(_binding("Blob")),
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
        componentBindings = Vector(_binding("Blob")),
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
    }

    "E24 keep legacy coordinate parsing for backward compatibility" must _metadata("E24") {
      "when exercising: keep legacy coordinate parsing for backward compatibility" in {
      Given("a legacy component coordinate declaration")
      val path = _create_temp_file("generic-subsystem-coordinate-descriptor", ".yaml")
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
      val result = GenericSubsystemDescriptor.load(path)

      Then("legacy coordinate identity cannot derive a canonical Component binding")
      result shouldBe a[Consequence.Failure[_]]
      }
    }
    }

    "compose component and subsystem archives" which {
    "E1 create a synthetic subsystem descriptor from component CAR assembly metadata" must _e1 {
      "when exercising: create a synthetic subsystem descriptor from component CAR assembly metadata" in {
        Given("a component CAR containing component and assembly descriptors")
        val car = _create_temp_file("component-with-assembly", ".car")
        val descriptor =
          """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Cwitter","version":"0.0.1-SNAPSHOT"}}"""
        val assembly =
          """subsystem: cwitter
            |version: 0.0.1-SNAPSHOT
            |components:
            |  - namespace: org.example
            |    id: Cwitter
            |    version: 0.0.1-SNAPSHOT
            |  - namespace: org.example
            |    id: TextusUserAccount
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
        loaded.componentBindings.map(_.componentName) shouldBe Vector(
          "org.example.Cwitter",
          "org.example.TextusUserAccount"
        )
        loaded.componentBindings.head.componentId.map(_.name) shouldBe Some("org.example.Cwitter")
        loaded.security.flatMap(_.authentication).flatMap(_.convention) shouldBe Some("enabled")
        loaded.assemblyDescriptor.map(_.source) shouldBe Some("component-car")
      }
    }

    "E2 reject a legacy component CAR assembly root declaration" must _e2 {
      "when exercising: reject a legacy component CAR assembly root declaration" in {
        Given("a canonical component CAR whose adjacent assembly uses a legacy name declaration")
        val car = _create_temp_file("canonical-component-root-alias", ".car")
        _write_zip(
          car,
          Map(
            "component-descriptor.json" ->
              """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Cwitter","version":"0.0.1-SNAPSHOT"}}""",
            "assembly-descriptor.yaml" ->
              """subsystem: cwitter
                |components:
                |  - name: Cwitter
                |""".stripMargin
          )
        )

        When("the canonical component archive is translated into a subsystem descriptor")
        val result = GenericSubsystemDescriptor.loadComponentArchive(car)

        Then("the legacy declaration is rejected before it can enter factory selection")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.display shouldBe
              "component assembly binding rejects legacy identity fields: name"
          case Consequence.Success(value) =>
            fail(s"expected canonical root alias rejection but got $value")
        }
      }
    }

    "E25 reject a component archive with an invalid assembly descriptor" must _metadata("E25") {
      "when exercising: reject a component archive with an invalid assembly descriptor" in {
      Given("a component CAR whose assembly descriptor has no subsystem name")
      val car = _create_temp_file("invalid-component-assembly", ".car")
      val descriptor =
        """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Cwitter","version":"0.0.1-SNAPSHOT"}}"""
      val assembly =
        """version: 0.0.1-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Cwitter
          |    version: 0.0.1-SNAPSHOT
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
    }

    "E26 reject missing and malformed explicitly configured assembly descriptors" must _metadata("E26") {
      "when exercising: reject missing and malformed explicitly configured assembly descriptors" in {
      Given("one absent assembly path and one malformed configured assembly descriptor")
      val missing = _create_temp_directory("missing-configured-assembly").resolve("assembly.yaml")
      val malformed = _create_temp_file("malformed-configured-assembly", ".yaml")
      Files.writeString(malformed, "subsystem: [unterminated", StandardCharsets.UTF_8)

      When("the explicit assembly descriptor loader reads each configured path")
      val missingresult = GenericSubsystemDescriptor.loadAssemblyDescriptorC(missing)
      val malformedresult = GenericSubsystemDescriptor.loadAssemblyDescriptorC(malformed)

      Then("both configured-source failures remain structured instead of becoming absent defaults")
      missingresult shouldBe a[Consequence.Failure[_]]
      malformedresult shouldBe a[Consequence.Failure[_]]
      }
    }

    "E27 let a SAR descriptor inherit authentication provider defaults from a component CAR assembly descriptor" must _metadata("E27") {
      "when exercising: let a SAR descriptor inherit authentication provider defaults from a component CAR assembly descriptor" in {
      Given("component CAR authentication defaults and a SAR without security overrides")
      val car = _create_temp_file("cwitter-component-defaults", ".car")
      val descriptor =
        """{"schemaVersion":3,"component":{"namespace":"org.example","id":"Cwitter","version":"0.0.1-SNAPSHOT"}}"""
      val assembly =
        """subsystem: cwitter
          |version: 0.0.1-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Cwitter
          |    version: 0.0.1-SNAPSHOT
          |  - namespace: org.example
          |    id: TextusUserAccount
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
      val sar = _create_temp_file("cwitter-sar-no-security", ".yaml")
      Files.writeString(
        sar,
        """subsystem: cwitter
          |version: 0.0.1-SNAPSHOT
          |components:
          |  - namespace: org.example
          |    id: Cwitter
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
      effective.componentBindings.map(_.componentName) shouldBe Vector(
        "org.example.Cwitter",
        "org.example.TextusUserAccount"
      )
      }
    }

    "E28 let a SAR assembly descriptor override a provider inherited from component CAR assembly defaults" must _metadata("E28") {
      "when exercising: let a SAR assembly descriptor override a provider inherited from component CAR assembly defaults" in {
      Given("an inherited authentication provider and a SAR assembly override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("cwitter.car"),
        subsystemName = "cwitter",
        componentBindings = Vector(_binding("Cwitter")),
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
    }

    "E29 merge partial SAR assembly wiring overrides with inherited component CAR wiring" must _metadata("E29") {
      "when exercising: merge partial SAR assembly wiring overrides with inherited component CAR wiring" in {
      Given("two inherited wiring entries and one matching SAR override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("cwitter.car"),
        subsystemName = "cwitter",
        componentBindings = Vector(_binding("Cwitter")),
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
    }

    "load and validate SPI assembly bindings" which {
    "E30 load test descriptor config and assembly SPI bindings" must _metadata("E30") {
      "when exercising: load test descriptor config and assembly SPI bindings" in {
      Given("a test descriptor with runtime config and an SPI binding")
      val path = _create_temp_file("cncf-test-descriptor", ".yaml")
      Files.writeString(
        path,
        """kind: test-descriptor
          |config:
          |  textus.web.demo-assist.enabled: true
          |assembly:
          |  components:
          |    - namespace: org.example
          |      id: TargetComponent
          |      version: 1.0.0
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
        componentBindings = Vector(_binding("TargetComponent"))
      )
      val effective = descriptor.assembly
        .map(GenericSubsystemDescriptor.applyAssemblyOverride(subsystem, _))
        .getOrElse(subsystem)
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(effective).toOption.get

      Then("runtime config, declared component configuration, and typed SPI selectors remain available")
      descriptor.config shouldBe Map("textus.web.demo-assist.enabled" -> "true")
      effective.componentBindings shouldBe Vector(
        _binding("TargetComponent", config = Map("provider.mode" -> "deterministic-test"))
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
    }

    "E31 load multiple providers for one required socket set" must _metadata("E31") {
      "when exercising: load multiple providers for one required socket set" in {
      Given("an assembly descriptor with two exact many-cardinality bindings")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E32 merge socket set bindings by provider member identity" must _metadata("E32") {
      "when exercising: merge socket set bindings by provider member identity" in {
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
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E33 reject an unknown socket cardinality" must _metadata("E33") {
      "when exercising: reject an unknown socket cardinality" in {
      Given("an assembly binding with an unsupported cardinality")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E34 reject contradictory socket cardinality and required declarations" must _metadata("E34") {
      "when exercising: reject contradictory socket cardinality and required declarations" in {
      Given("optional-required and non-empty-optional assembly bindings")
      def _descriptor_(cardinality: String, required: Boolean): GenericSubsystemDescriptor =
        GenericSubsystemDescriptor(
          path = java.nio.file.Path.of("component.car"),
          subsystemName = "target",
          componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E35 reject an SPI provider instance without a provider component" must _metadata("E35") {
      "when exercising: reject an SPI provider instance without a provider component" in {
      Given("an assembly binding with an instance-only provider selector")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E36 reject duplicate assembly bindings for one named socket identity" must _metadata("E36") {
      "when exercising: reject duplicate assembly bindings for one named socket identity" in {
      Given("two bindings targeting canonical aliases of the same socket instance and name")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E37 merge assembly SPI bindings by socket selector" must _metadata("E37") {
      "when exercising: merge assembly SPI bindings by socket selector" in {
      Given("inherited SPI bindings and a matching test override")
      val base = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E38 reject invalid assembly SPI bindings instead of dropping them" must _metadata("E38") {
      "when exercising: reject invalid assembly SPI bindings instead of dropping them" in {
      Given("an assembly SPI binding without a socket contract")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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
    }

    "E39 reject provider service matching until it is supported" must _metadata("E39") {
      "when exercising: reject provider service matching until it is supported" in {
      Given("an SPI provider selector that requests service-level matching")
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("component.car"),
        subsystemName = "target",
        componentBindings = Vector(_binding("TargetComponent")),
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

    "E40 retain an assembly component replacement when the effective assembly is replayed" must _metadata("E40") {
      "when exercising: retain an assembly component replacement when the effective assembly is replayed" in {
      Given("a component CAR assembly and a multi-user overlay with a replacement component list")
      val staticassembly = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "components" -> Vector(
            Record.data("namespace" -> "org.example", "id" -> "ArtScene", "version" -> "1.0.0"),
            Record.data("namespace" -> "org.example", "id" -> "UserNotification", "version" -> "1.0.0")
          )
        ),
        source = "component-car"
      )
      val multiuserassembly = GenericSubsystemAssemblyDescriptorSource(
        Record.data(
          "components" -> Vector(
            Record.data("namespace" -> "org.example", "id" -> "ArtScene", "version" -> "1.0.0"),
            Record.data("namespace" -> "org.example", "id" -> "UserAccount", "version" -> "1.0.0"),
            Record.data("namespace" -> "org.example", "id" -> "UserNotification", "version" -> "1.0.0")
          )
        ),
        source = "multi-user"
      )
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("art-scene.car"),
        subsystemName = "art-scene",
        componentBindings = Vector(_binding("ArtScene"), _binding("UserNotification")),
        assemblyDescriptor = Some(staticassembly)
      )

      When("the overlay and then its merged assembly source are applied")
      val overlaid = GenericSubsystemDescriptor.applyAssemblyOverride(descriptor, multiuserassembly)
      val replayed = GenericSubsystemDescriptor.applyAssemblyOverride(overlaid, overlaid.assemblyDescriptor.get)

      Then("the replay preserves the overlay component set rather than restoring the CAR defaults")
      replayed.componentBindings.map(_.componentId.map(_.name).get) shouldBe Vector(
        "org.example.ArtScene",
        "org.example.UserAccount",
        "org.example.UserNotification"
      )
      }
    }
    }

  }

  }

  private def _load(yaml: String): Consequence[GenericSubsystemDescriptor] = {
    val path = Files.createTempFile("generic-subsystem-descriptor-", ".yaml")
    try {
      Files.writeString(path, yaml, StandardCharsets.UTF_8)
      GenericSubsystemDescriptor.load(path)
    } finally {
      Files.deleteIfExists(path)
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

  private def _create_temp_file(prefix: String, suffix: String): Path = {
    val workdir = _test_work_directory
    val path = Files.createTempFile(workdir, prefix, suffix)
    _temporary_paths += path
    path
  }

  private def _create_temp_directory(prefix: String): Path = {
    val path = Files.createTempDirectory(_test_work_directory, prefix)
    _temporary_paths += path
    path
  }

  private def _test_work_directory: Path =
    Files.createDirectories(
      Path.of("target", "cncf-test", "work", "generic-subsystem-descriptor-spec").toAbsolutePath.normalize
    )

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      Using.resource(Files.walk(path)) { stream =>
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(Files.deleteIfExists)
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

  private def _binding(
    localid: String,
    version: String = "1.0.0",
    instance: Option[String] = None,
    config: Map[String, String] = Map.empty
  ): GenericSubsystemComponentBinding = {
    val componentid = ComponentId(s"org.example.$localid")
    GenericSubsystemComponentBinding(
      componentName = componentid.name,
      version = Some(version),
      instance = instance,
      config = config,
      componentId = Some(componentid)
    )
  }
}
