package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.repository.fixture.spi.{
  ArtSceneComponent,
  ArtSceneComponentFactory,
  ComponentFactory as PlainAiRunnerComponentFactory,
  PlainAiRunner,
  PlainAiRunnerProviderComponent
}
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.component.testutil.CarArchiveFixture
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
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  override def beforeAll(): Unit = {
    val workarea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workarea))
  }

  "GenericSubsystemFactory" should {
    "descriptor materialization and development selection" which {
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

    "preserve descriptor runtime config across repository duplicate selection" in {
      Given("one repository component and a descriptor binding that disables MCP publication")
      val subsystem = TestComponentFactory.emptySubsystem("descriptor-runtime-config")
      val origin = ComponentOrigin.Repository("component-file:car:textus-scraper:0.1.0-SNAPSHOT")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("subsystem-descriptor"))
      val prototype = _named_instance_factory.createPrimary(params.withOrigin(origin))
      val descriptor = GenericSubsystemDescriptor(
        path = Path.of("descriptor-runtime-config.yaml"),
        subsystemName = "descriptor-runtime-config",
        componentBindings = Vector(
          GenericSubsystemComponentBinding(
            "textus-scraper",
            config = Map("cncf.mcp.enabled" -> "false")
          )
        )
      )

      When("the configured instance competes with its repository prototype")
      val materialized = GenericSubsystemFactory
        .materializeComponentInstances(Vector(prototype), descriptor, params)
        .head
      val selection = org.goldenport.cncf.assembly.AssemblyReport.selectPreferred(materialized, prototype)

      Then("the configured instance retains repository priority and MCP stays disabled")
      selection.selected should be theSameInstanceAs materialized
      materialized.origin shouldBe origin
      materialized.applicationConfig.config.flatMap(_.string("cncf.mcp.enabled")) shouldBe Some("false")
      prototype.isMcpReady("Search", "query") shouldBe true
      materialized.isMcpReady("Search", "query") shouldBe false
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
          """{"name":"structured-knowledge","version":"0.1.0-SNAPSHOT","component":"textus-mcp-rag"}"""
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
            RuntimeConfig.repositoryDirKey ->
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

    "E10 prefer a complete sibling development set over an older packaged CAR" in {
      Given("two prepared prefixed development directories and an older same-name packaged CAR")
      _with_temp_dir { root =>
        val primarydir = root.resolve("devdirsample")
        val primaryclassdir = primarydir.resolve("target").resolve("scala-3.3.8").resolve("classes")
        _copy_test_package_classes(classOf[devdirsample.DevDirSampleComponent], primaryclassdir)
        _write_runtime_classpath(primarydir, primaryclassdir, "devdirsample", "0.1.0-SNAPSHOT", "devdirsample")
        val secondarydir = root.resolve("secondarydevdirsample")
        val secondaryclassdir = secondarydir.resolve("target").resolve("scala-3.3.8").resolve("classes")
        _copy_test_package_classes(classOf[secondarydevdirsample.SecondaryDevDirSampleComponent], secondaryclassdir)
        _write_runtime_classpath(secondarydir, secondaryclassdir, "secondarydevdirsample", "0.1.0-SNAPSHOT", "secondarydevdirsample")
        val packagedir = Files.createDirectories(root.resolve("packaged"))
        val packagedjar = _create_fake_component_jar(root.resolve("assets").resolve("packaged-main.jar"))
        val packageddescriptor = root.resolve("packaged-descriptor.json")
        Files.writeString(
          packageddescriptor,
          """{"name":"devdirsample","version":"0.0.1","component":"devdirsample"}"""
        )
        _create_unadmitted_car(
          packagedir.resolve("devdirsample-0.0.1.car"),
          Seq(
            "component/main.jar" -> packagedjar,
            "component-descriptor.json" -> packageddescriptor
          )
        )
        val descriptor = GenericSubsystemDescriptor(
          path = root.resolve("assembly-descriptor.yaml"),
          subsystemName = "complete-development-set",
          version = Some("0.0.1"),
          componentBindings = Vector(
            GenericSubsystemComponentBinding("devdirsample", version = Some("0.0.1")),
            GenericSubsystemComponentBinding("secondarydevdirsample", version = Some("0.1.0-SNAPSHOT"))
          )
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentCarDirKey -> ConfigurationValue.StringValue(packagedir.toString),
            RuntimeConfig.repositoryComponentDevDirKey -> ConfigurationValue.StringValue(
              s"component-dev-dir:$primarydir,component-dev-dir:$secondarydir"
            )
          )),
          ConfigurationTrace.empty
        )

        When("GenericSubsystemFactory resolves the complete explicit development repository set")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)
        val components = subsystem.components.filter(component =>
          Set("devdirsample", "secondarydevdirsample").contains(component.name)
        )

        Then("both descriptor claims use development classes and no packaged provider is admitted")
        components should have size 2
        components.map(_.getClass.getName).toSet shouldBe Set(
          classOf[devdirsample.DevDirSamplePrimaryComponent].getName,
          classOf[secondarydevdirsample.SecondaryDevDirSampleComponent].getName
        )
      }
    }

    "isolate assembly descriptors across explicit component CARs" in {
      Given("two explicit CARs and one assembly descriptor binding each component")
      _with_temp_dir { root =>
        val appjar = _create_class_component_jar(
          root.resolve("assets").resolve("component-file-app.jar"),
          Seq(classOf[ArtSceneComponentFactory], classOf[ArtSceneComponent])
        )
        val providerjar = _create_class_component_jar(
          root.resolve("assets").resolve("plain-ai-runner-provider.jar"),
          Seq(
            classOf[PlainAiRunnerComponentFactory],
            classOf[PlainAiRunnerProviderComponent],
            classOf[PlainAiRunner]
          )
        )
        val appdescriptor = root.resolve("component-file-app-descriptor.json")
        val providerdescriptor = root.resolve("plain-ai-runner-provider-descriptor.json")
        Files.writeString(
          appdescriptor,
          """{"name":"component-file-app","version":"0.1.0","component":"component-file-app"}"""
        )
        Files.writeString(
          providerdescriptor,
          """{"name":"plain-ai-runner-provider","version":"0.1.0","component":"plain-ai-runner-provider"}"""
        )
        val appcar = root.resolve("component-file-app.car")
        val providercar = root.resolve("plain-ai-runner-provider.car")
        _create_car(
          appcar,
          Seq(
            "component/main.jar" -> appjar,
            "component-descriptor.json" -> appdescriptor
          )
        )
        _create_car(
          providercar,
          Seq(
            "component/main.jar" -> providerjar,
            "component-descriptor.json" -> providerdescriptor
          )
        )
        val descriptor = GenericSubsystemDescriptor(
          path = root.resolve("assembly-descriptor.yaml"),
          subsystemName = "explicit-component-files",
          componentBindings = Vector(
            GenericSubsystemComponentBinding("component-file-app", version = Some("0.1.0")),
            GenericSubsystemComponentBinding("plain-ai-runner-provider", version = Some("0.1.0"))
          )
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(
                s"component-file:${appcar},component-file:${providercar}"
              )
          )),
          ConfigurationTrace.empty
        )

        When("the subsystem factory assembles both explicit CAR repositories")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)

        Then("each factory receives only the descriptor owned by its CAR")
        val expectednames = Set("component-file-app", "plain-ai-runner-provider")
        val explicitcomponents = subsystem.components.filter(component =>
          expectednames.contains(component.name)
        )
        explicitcomponents.map(_.name).sorted shouldBe Vector(
          "component-file-app",
          "plain-ai-runner-provider"
        )
        explicitcomponents.flatMap(_.artifactMetadata.flatMap(_.component)).sorted shouldBe Vector(
          "component-file-app",
          "plain-ai-runner-provider"
        )
      }
    }

    }

    "runtime resolution and security wiring" which {
    "make descriptor-bound component operations visible through the subsystem resolver" in {
      Given("a repository component with generated specification operations")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor-car.json")
        Files.writeString(
          componentdescriptor,
          """{"name":"structured-knowledge","version":"0.1.0-SNAPSHOT","component":"textus-mcp-rag"}"""
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
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(s"component-dir:${componentdir.toString}")
          )),
          ConfigurationTrace.empty
        )

        When("the subsystem is built")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)

        Then("the explicitly versioned CAR is loaded")
        subsystem.components.flatMap(_.artifactMetadata.map(_.component)) should contain (Some("textus-mcp-rag"))

        And("the subsystem resolver exposes the component operation")
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
            RuntimeConfig.subsystemNameKey ->
              ConfigurationValue.StringValue("textus-identity"),
            RuntimeConfig.repositoryDirKey ->
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
  }

  private object _named_instance_factory extends Component.PrimaryComponentFactory {
    protected def create_Component(params: ComponentCreate): Component =
      new Component() {
        override def mcpReadyServices: Set[String] = Set("Search")
      }

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
    CarArchiveFixture.write(target, entries)

  private def _create_unadmitted_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      entries.foreach { case (name, file) =>
        zip.putNextEntry(new ZipEntry(name))
        Files.copy(file, zip)
        zip.closeEntry()
      }
    }

  private def _write_runtime_classpath(
    componentdir: Path,
    classdir: Path,
    name: String,
    version: String,
    component: String
  ): Unit = {
    val file = componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    val cardir = componentdir.resolve("src").resolve("main").resolve("car")
    Files.createDirectories(file.getParent)
    Files.createDirectories(cardir)
    Files.writeString(file, classdir.toString, StandardCharsets.UTF_8)
    Files.writeString(
      cardir.resolve("component-descriptor.json"),
      s"""{"name":"$name","version":"$version","component":"$component"}""",
      StandardCharsets.UTF_8
    )
    Files.writeString(
      cardir.resolve("abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v1","car":{"name":"$name","version":"$version"},"abi":{"exports":{"components":[{"name":"$component"}]}}}""",
      StandardCharsets.UTF_8
    )
    val classpathidentity = s"project:${componentdir.relativize(classdir).toString.replace('\\', '/')}"
    val evidence = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(file), Some(_sha256(classpathidentity.getBytes(StandardCharsets.UTF_8)))),
      ("src/main/car/component-descriptor.json", _sha256(cardir.resolve("component-descriptor.json")), None),
      ("src/main/car/abi-manifest.json", _sha256(cardir.resolve("abi-manifest.json")), None)
    )
    val entries = evidence.map { case (path, digest, logical) =>
      val logicalfield = logical.map(value => s""""logicalSha256":"$value",""").getOrElse("")
      s"""{$logicalfield"path":"$path","sha256":"$digest"}"""
    }.mkString("[", ",", "]")
    val evidencedigest = _sha256(evidence.map { case (path, digest, logical) =>
      s"$path\t$digest\t${logical.getOrElse("")}"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(
      file.getParent.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v1","sourceKind":"development-directory","car":{"name":"$name","version":"$version","component":"$component"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$evidencedigest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _copy_test_package_classes(source: Class[?], target: Path): Unit = {
    val testclasses = Path.of(
      source
        .getProtectionDomain
        .getCodeSource
        .getLocation
        .toURI
    )
    val packagepath = source.getPackageName.replace('.', '/')
    val sourcepackage = testclasses.resolve(packagepath)
    val targetpackage = target.resolve(packagepath)
    Files.createDirectories(targetpackage)
    Using.resource(Files.list(sourcepackage)) { stream =>
      stream.iterator().asScala
        .filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".class"))
        .foreach { path =>
          Files.copy(
            path,
            targetpackage.resolve(path.getFileName),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
          )
        }
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

  private def _create_class_component_jar(
    target: Path,
    classes: Seq[Class[?]]
  ): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      classes.foreach { cls =>
        val entry = s"${cls.getName.replace('.', '/')}.class"
        val resource = Option(getClass.getClassLoader.getResource(entry))
          .getOrElse(fail(s"missing test class resource: $entry"))
        zos.putNextEntry(new ZipEntry(entry))
        Using.resource(resource.openStream()) { in =>
          in.transferTo(zos)
        }
        zos.closeEntry()
      }
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
