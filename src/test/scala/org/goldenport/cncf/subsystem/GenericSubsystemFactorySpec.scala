package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.protocol.Protocol
import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  8, 2026
 *  version Apr. 10, 2026
 *  version Apr. 24, 2026
 *  version May. 25, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  override def beforeAll(): Unit = {
    val workarea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workarea))
  }

  "GenericSubsystemFactory" should {
    "materialize multiple named instances from one descriptor component type" in {
      Given("one discovered component and two descriptor instance declarations")
      val subsystem = TestComponentFactory.emptySubsystem("named-instance-materialization")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
      val prototype = _named_instance_factory.createPrimary(params)
      val descriptor = GenericSubsystemDescriptor(
        path = Path.of("named-instance-materialization.yaml"),
        subsystemName = "named-instance-materialization",
        componentBindings = Vector(
          GenericSubsystemComponentBinding(
            "textus-scraper",
            instance = Some("static-default"),
            config = Map("scraper.mode" -> "static"),
            isDefault = Some(true)
          ),
          GenericSubsystemComponentBinding(
            "textus-scraper",
            instance = Some("dynamic-playwright"),
            config = Map("scraper.mode" -> "dynamic")
          )
        )
      )

      When("the descriptor bindings are materialized")
      val instances = GenericSubsystemFactory.materializeComponentInstances(Vector(prototype), descriptor, params)

      Then("one factory produces two independently configured runtime instances")
      instances.map(_.instanceId).toSet shouldBe Set(
        ComponentInstanceId("textus-scraper", "static-default"),
        ComponentInstanceId("textus-scraper", "dynamic-playwright")
      )
      instances.flatMap(_.instanceMetadata).map(_.config("scraper.mode")).toSet shouldBe Set("static", "dynamic")
    }

    "materialize every bundle participant for each named component instance" in {
      Given("one discovered bundle with a primary and componentlet plus two instance declarations")
      val subsystem = TestComponentFactory.emptySubsystem("named-bundle-materialization")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
      val artifact = Component.ArtifactMetadata(
        sourceType = "spec",
        name = "textus-scraper",
        version = "0.1.0",
        component = Some("textus-scraper")
      )
      val discovered = _named_bundle_factory.create(params).participants.map(_.withArtifactMetadata(artifact))
      val descriptor = GenericSubsystemDescriptor(
        path = Path.of("named-bundle-materialization.yaml"),
        subsystemName = "named-bundle-materialization",
        componentBindings = Vector(
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("static")),
          GenericSubsystemComponentBinding("textus-scraper", instance = Some("dynamic"))
        )
      )

      When("the descriptor bindings are materialized from the discovered bundle")
      val participants = GenericSubsystemFactory.materializeComponentInstances(discovered, descriptor, params)

      Then("each instance retains both participant roles with unique participant identities")
      participants.size shouldBe 4
      participants.count(_.isPrimaryParticipant) shouldBe 2
      participants.count(_.isComponentletParticipant) shouldBe 2
      participants.map(_.instanceId).toSet shouldBe Set(
        ComponentInstanceId("textus-scraper", "static"),
        ComponentInstanceId("textus-scraper-admin", "static"),
        ComponentInstanceId("textus-scraper", "dynamic"),
        ComponentInstanceId("textus-scraper-admin", "dynamic")
      )
    }

    "load the descriptor-bound component through the repository runtime path" in {
      Given("a repository containing a descriptor-bound component CAR")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor-car.json")
        Files.writeString(
          componentdescriptor,
          """{"name":"structured-knowledge","version":"0.1.0","component":"textus-mcp-rag"}"""
        )
        _create_car(
          componentdir.resolve("structured-knowledge.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = Files.createTempFile("generic-subsystem-factory", ".yaml")
        Files.writeString(
          descriptorpath,
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

        val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption.get
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.RepositoryDirKey ->
              ConfigurationValue.StringValue(s"component-dir:${componentdir.toString}")
          )),
          ConfigurationTrace.empty
        )

        When("the subsystem is built through the repository runtime path")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)
        val names = subsystem.components.map(_.name).sorted
        val metadata = subsystem.components.flatMap(_.artifactMetadata)

        Then("the component and artifact metadata are visible in the subsystem")
        subsystem.name shouldBe "mcprag"
        subsystem.version shouldBe Some("0.1.0-SNAPSHOT")
        names should contain ("spec")
        metadata.map(_.component) should contain (Some("textus-mcp-rag"))
      }
    }

    "make descriptor-bound component operations visible through the subsystem resolver" in {
      Given("a repository component with generated specification operations")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor-car.json")
        Files.writeString(
          componentdescriptor,
          """{"name":"structured-knowledge","version":"0.1.0","component":"textus-mcp-rag"}"""
        )
        _create_car(
          componentdir.resolve("structured-knowledge.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = Files.createTempFile("generic-subsystem-factory-visibility", ".yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: mcprag
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: textus-mcp-rag
            |    version: 0.1.0-SNAPSHOT
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption.get
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.RepositoryDirKey ->
              ConfigurationValue.StringValue(s"component-dir:${componentdir.toString}")
          )),
          ConfigurationTrace.empty
        )

        When("the subsystem is built")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)

        Then("the subsystem resolver exposes the component operation")
        subsystem.resolver.resolve("spec.export.openapi") shouldBe
          ResolutionResult.Resolved(
            fqn = "spec.export.openapi",
            component = "spec",
            service = "export",
            operation = "openapi"
          )
      }
    }

    "carry descriptor-defined security wiring from the textus-identity journal sample descriptor" in {
      Given("the maintained textus-identity descriptor")
      val descriptorpath = java.nio.file.Path.of("/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")
      val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption.get

      When("the subsystem is constructed")
      val subsystem = GenericSubsystemFactory.default(descriptor)

      val wiring = subsystem.resolvedSecurityWiring.authentication

      Then("the resolved authentication wiring retains descriptor policy")
      subsystem.name shouldBe "textus-identity"
      wiring.conventionEnabled shouldBe true
      wiring.fallbackPrivilegeEnabled shouldBe false
      wiring.providers.map(x => (x.componentName, x.name, x.source.toString)) shouldBe Vector(
        ("textus-user-account", "user-account", "Descriptor")
      )
      wiring.providers.head.kind shouldBe Some("human")
      wiring.providers.head.priority shouldBe 100
      wiring.providers.head.schemes shouldBe Vector("bearer", "refresh-token")
      wiring.providers.head.provider shouldBe empty
    }

    "resolve a subsystem descriptor from component repository using subsystem name only" in {
      Given("a component repository containing a CAR and subsystem SAR")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor.json")
        Files.writeString(
          componentdescriptor,
          """{"name":"textus-user-account","version":"0.1.0-SNAPSHOT","componentName":"textus-user-account"}"""
        )
        _create_car(
          componentdir.resolve("textus-user-account-0.1.0-SNAPSHOT.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val subsystemdescriptor = componentdir.resolve("subsystem-descriptor.yaml")
        Files.writeString(
          subsystemdescriptor,
          """subsystem: textus-identity
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: textus-user-account
            |    version: 0.1.0-SNAPSHOT
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        _create_car(
          componentdir.resolve("textus-identity-0.1.0-SNAPSHOT.sar"),
          Seq(
            "subsystem-descriptor.yaml" -> subsystemdescriptor
          )
        )

        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.SubsystemNameKey ->
              ConfigurationValue.StringValue("textus-identity"),
            RuntimeConfig.RepositoryDirKey ->
              ConfigurationValue.StringValue(s"component-dir:${componentdir.toString}")
          )),
          ConfigurationTrace.empty
        )

        When("the runtime is started using only the subsystem name")
        val subsystem = DefaultSubsystemFactory.defaultWithScope(
          context = ScopeContext(
            kind = ScopeKind.Subsystem,
            name = "textus-identity",
            parent = None,
            observabilityContext = ExecutionContext.create().observability
          ),
          configuration = configuration
        )

        Then("the SAR descriptor and component CAR are resolved")
        subsystem.name shouldBe "textus-identity"
        subsystem.version shouldBe Some("0.1.0-SNAPSHOT")
        subsystem.descriptor.map(_.subsystemName) shouldBe Some("textus-identity")
        subsystem.components.map(_.name) should contain ("spec")
        subsystem.components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
      }
    }

    "resolve descriptor components from the default standard repository using name and version without repository config" in {
      Given("a CAR in the default user repository and no explicit repository setting")
      _with_temp_dir { homedir =>
        val cachedir = homedir.resolve(".cncf").resolve("cache").resolve("car").resolve("textus-user-account").resolve("0.1.0")
        Files.createDirectories(cachedir)
        val fakecomponentjar = _create_fake_component_jar(homedir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = homedir.resolve("component-descriptor.json")
        Files.writeString(
          componentdescriptor,
          """{"name":"textus-user-account","version":"0.1.0","componentName":"textus-user-account"}"""
        )
        _create_car(
          cachedir.resolve("textus-user-account-0.1.0.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = Files.createTempFile("generic-subsystem-factory-default-standard", ".yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: textus-identity
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: textus-user-account
            |    version: 0.1.0
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption.get
        val originalhome = System.getProperty("user.home")
        try {
          System.setProperty("user.home", homedir.toString)

          When("the descriptor-based subsystem is constructed")
          val subsystem = GenericSubsystemFactory.default(descriptor)

          Then("the versioned CAR is discovered from the standard repository")
          subsystem.name shouldBe "textus-identity"
          subsystem.components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
        } finally {
          if (originalhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", originalhome)
        }
      }
    }

  }

  private object _named_instance_factory extends Component.PrimaryComponentFactory {
    protected def create_Component(params: ComponentCreate): Component =
      new Component() {}

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      Component.Core.create(
        "textus-scraper",
        ComponentId("textus_scraper"),
        ComponentInstanceId.default(ComponentId("textus_scraper")),
        Protocol.empty,
        this
      )
  }

  private object _named_bundle_factory extends Component.BundleFactory {
    object Primary extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "textus-scraper",
          ComponentId("textus_scraper"),
          ComponentInstanceId.default(ComponentId("textus_scraper")),
          Protocol.empty,
          this
        )
    }

    object Admin extends Component.ComponentletFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "textus-scraper-admin",
          ComponentId("textus_scraper_admin"),
          ComponentInstanceId.default(ComponentId("textus_scraper_admin")),
          Protocol.empty,
          this
        )
    }

    def primaryFactory: Component.PrimaryComponentFactory = Primary

    override def componentletFactories: Vector[Component.ComponentletFactory] = Vector(Admin)
  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      entries.foreach { case (name, file) =>
        zos.putNextEntry(new ZipEntry(name))
        Files.copy(file, zos)
        zos.closeEntry()
      }
    }

  private def _create_fake_component_jar(target: Path): Path = {
    val factoryclassentry =
      "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      zos.putNextEntry(new ZipEntry(factoryclassentry))
      zos.closeEntry()
    }
    target
  }

  private def _with_temp_dir[T](body: Path => T): T = {
    val base = Files.createTempDirectory("generic-subsystem-factory-spec")
    try body(base)
    finally {
      if (Files.exists(base)) {
        Using.resource(Files.walk(base)) { stream =>
          stream
            .sorted(Comparator.reverseOrder())
            .iterator()
            .asScala
            .foreach(p => Files.deleteIfExists(p))
        }
      }
    }
  }
}
