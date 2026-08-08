package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{RepositoryBootstrapPolicy, RuntimeConfig}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin, SubsystemCapabilityId}
import org.goldenport.cncf.path.AliasResolver
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
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  private val _e1 = afterWord("in spec:generic-subsystem-factory, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:generic-subsystem-factory, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:generic-subsystem-factory, example:E3, rules:CID05C-R9, phase:56, slice:CID-05C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:generic-subsystem-factory, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  private def _cid06c_metadata(exampleid: String) =
    afterWord(s"in spec:generic-subsystem-factory, example:$exampleid, rules:CID06C-R5,R8, phase:56, slice:CID-06C")
  override def beforeAll(): Unit = {
    val workarea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workarea))
  }

  "GenericSubsystemFactory" should {
    "compatibility notice ownership" which {
      "E56-CID06C retain an assembly-binding notice in the owning runtime report" must _cid06c_metadata("E56-CID06C-assembly") {
        "when a bare binding is admitted through the scoped factory boundary" in {
          Given("a canonical descriptor override, an owning runtime scope, and a foreign ambient runtime")
          val componentid = ComponentId("org.goldenport.cncf.Specification")
          val descriptor = GenericSubsystemDescriptor(
            path = Path.of("cid06c-factory-assembly.yaml"),
            subsystemName = "cid06c-factory-assembly",
            componentBindings = Vector(GenericSubsystemComponentBinding("Specification")),
            componentDescriptorOverrides = Vector(_canonical_descriptor(componentid))
          )
          _with_temp_dir { repositorydir =>
            _install_specification_car(repositorydir)
            val configuration = _repository_configuration(repositorydir)
            val execution = ExecutionContext.create()
            val owner = GlobalRuntimeContext.create(
              "cid06c-factory-assembly",
              RuntimeConfig.default,
              configuration,
              execution.observability,
              AliasResolver.empty
            )
            val foreign = GlobalRuntimeContext.create(
              "cid06c-factory-foreign",
              RuntimeConfig.default,
              configuration,
              execution.observability,
              AliasResolver.empty
            )
            val previous = GlobalRuntimeContext.current
            GlobalRuntimeContext.current = Some(foreign)
            try {
              When("the factory constructs the subsystem with the owner supplied as its scope")
              GenericSubsystemFactory.defaultWithScope(
                descriptor = descriptor,
                context = owner,
                configuration = configuration,
                aliasResolver = AliasResolver.empty
              )

              Then("the assembly-binding notice reaches only the owning report")
              owner.assemblyReport.warnings.map(_.reason).flatten should contain (
                "surface=assembly-binding; alias-kind=bare; alias=Specification; canonical=org.goldenport.cncf.Specification"
              )
              foreign.assemblyReport.warnings.filter(_.kind == "component-identity-compatibility") shouldBe empty
            } finally {
              GlobalRuntimeContext.current = previous
            }
          }
        }
      }

      "E56-CID06C retain a descriptor-field notice in the owning runtime report" must _cid06c_metadata("E56-CID06C-descriptor") {
        "when a typed binding projects legacy descriptor fields through the scoped factory boundary" in {
          Given("a canonical typed binding, a legacy descriptor override, and an owning runtime scope")
          val componentid = ComponentId("org.goldenport.cncf.Specification")
          val descriptor = GenericSubsystemDescriptor(
            path = Path.of("cid06c-factory-descriptor.yaml"),
            subsystemName = "cid06c-factory-descriptor",
            componentBindings = Vector(
              GenericSubsystemComponentBinding(componentid.name, componentId = Some(componentid))
            ),
            componentDescriptorOverrides = Vector(
              ComponentDescriptor(
                name = Some("Specification"),
                componentName = Some("Specification"),
                version = Some("0.1.0")
              )
            )
          )
          _with_temp_dir { repositorydir =>
            _install_specification_car(repositorydir)
            val configuration = _repository_configuration(repositorydir)
            val execution = ExecutionContext.create()
            val owner = GlobalRuntimeContext.create(
              "cid06c-factory-descriptor",
              RuntimeConfig.default,
              configuration,
              execution.observability,
              AliasResolver.empty
            )
            val previous = GlobalRuntimeContext.current
            GlobalRuntimeContext.current = None
            try {
              When("the factory constructs the subsystem with the owner supplied as its scope")
              GenericSubsystemFactory.defaultWithScope(
                descriptor = descriptor,
                context = owner,
                configuration = configuration,
                aliasResolver = AliasResolver.empty
              )

              Then("the descriptor-field notice reaches the owning report without reconstructing it")
              owner.assemblyReport.warnings.map(_.reason).flatten should contain (
                "surface=descriptor-field; alias-kind=bare; alias=Specification; canonical=org.goldenport.cncf.Specification"
              )
            } finally {
              GlobalRuntimeContext.current = previous
            }
          }
        }
      }

      "E56-CID06C leave notices unowned when only a foreign ambient runtime exists" must _cid06c_metadata("E56-CID06C-unowned") {
        "when the supplied scope has no GlobalRuntimeContext owner" in {
          Given("a bare binding, its canonical override, an unowned scope, and a foreign ambient runtime")
          val componentid = ComponentId("org.goldenport.cncf.Specification")
          val descriptor = GenericSubsystemDescriptor(
            path = Path.of("cid06c-factory-unowned.yaml"),
            subsystemName = "cid06c-factory-unowned",
            componentBindings = Vector(GenericSubsystemComponentBinding("Specification")),
            componentDescriptorOverrides = Vector(_canonical_descriptor(componentid))
          )
          _with_temp_dir { repositorydir =>
            _install_specification_car(repositorydir)
            val configuration = _repository_configuration(repositorydir)
            val execution = ExecutionContext.create()
            val foreign = GlobalRuntimeContext.create(
              "cid06c-factory-foreign-only",
              RuntimeConfig.default,
              configuration,
              execution.observability,
              AliasResolver.empty
            )
            val unowned = ScopeContext(
              kind = ScopeKind.Subsystem,
              name = "cid06c-factory-unowned",
              parent = None,
              observabilityContext = execution.observability
            )
            val previous = GlobalRuntimeContext.current
            GlobalRuntimeContext.current = Some(foreign)
            try {
              When("the factory constructs the subsystem with the unowned scope")
              GenericSubsystemFactory.defaultWithScope(
                descriptor = descriptor,
                context = unowned,
                configuration = configuration,
                aliasResolver = AliasResolver.empty
              )

              Then("no compatibility notice is attributed to the foreign ambient runtime")
              foreign.assemblyReport.warnings.filter(_.kind == "component-identity-compatibility") shouldBe empty
            } finally {
              GlobalRuntimeContext.current = previous
            }
          }
        }
      }
    }

    "descriptor materialization and development selection" which {
    "E1 materialize multiple named instances from one descriptor component type" must _e1 {
      "when exercising: materialize multiple named instances from one descriptor component type" in {
        Given("one discovered component and two descriptor instance declarations")
        val subsystem = TestComponentFactory.emptySubsystem("named-instance-materialization")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
        val prototype = NamedInstanceFactory.createPrimary(params)
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
          ComponentInstanceId("org.goldenport.cncf.spec.Scraper", "static-default"),
          ComponentInstanceId("org.goldenport.cncf.spec.Scraper", "dynamic-playwright")
        )
        instances.flatMap(_.instanceMetadata).map(_.config("scraper.mode")).toSet shouldBe Set("static", "dynamic")
      }
    }

    "E4 preserve descriptor runtime config across repository duplicate selection" must _metadata("E4") {
      "when exercising: preserve descriptor runtime config across repository duplicate selection" in {
      Given("one repository component and a descriptor binding that disables MCP publication")
      val subsystem = TestComponentFactory.emptySubsystem("descriptor-runtime-config")
      val origin = ComponentOrigin.Repository("component-file:car:textus-scraper:0.1.0-SNAPSHOT")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("subsystem-descriptor"))
      val prototype = NamedInstanceFactory.createPrimary(params.withOrigin(origin))
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
    }

    "E5 materialize every bundle participant for each named component instance" must _metadata("E5") {
      "when exercising: materialize every bundle participant for each named component instance" in {
      Given("one discovered bundle with a primary and componentlet plus two instance declarations")
      val subsystem = TestComponentFactory.emptySubsystem("named-bundle-materialization")
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
      val artifact = Component.ArtifactMetadata(
        sourceType = "spec",
        name = "textus-scraper",
        version = "0.1.0",
        component = Some("textus-scraper")
      )
      val discovered = NamedBundleFactory.create(params).participants.map(_.withArtifactMetadata(artifact))
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
        ComponentInstanceId("org.goldenport.cncf.spec.Scraper", "static"),
        ComponentInstanceId("org.goldenport.cncf.spec.ScraperAdmin", "static"),
        ComponentInstanceId("org.goldenport.cncf.spec.Scraper", "dynamic"),
        ComponentInstanceId("org.goldenport.cncf.spec.ScraperAdmin", "dynamic")
      )
      }
    }

    "E2 reject multiple exact canonical component candidates" must _e2 {
      "when exercising: reject multiple exact canonical component candidates" in {
        Given("one canonical binding and two discovered primaries with the same exact Core and artifact identity")
        val componentid = ComponentId("org.example.Cwitter")
        val subsystem = TestComponentFactory.emptySubsystem("canonical-candidate-ambiguity")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("spec"))
        val descriptor = GenericSubsystemDescriptor(
          path = Path.of("canonical-candidate-ambiguity.yaml"),
          subsystemName = "canonical-candidate-ambiguity",
          componentBindings = Vector(
            GenericSubsystemComponentBinding(componentid.name, componentId = Some(componentid))
          )
        )
        val artifact = Component.ArtifactMetadata(
          sourceType = "spec",
          name = "cwitter.car",
          version = "0.1.0",
          componentId = Some(componentid)
        )
        def _candidate_(): Component = {
          val candidate = new Component() {}
          candidate.initialize(
            ComponentInit(
              subsystem,
              Component.Core.create(
                componentid.name,
                componentid,
                ComponentInstanceId.default(componentid),
                Protocol.empty
              ),
              ComponentOrigin.Repository("spec")
            )
          )
          candidate.withArtifactMetadata(artifact)
        }
        val candidates = Vector.fill(2)(_candidate_())

        When("canonical factory materialization selects exact candidates")
        val result = GenericSubsystemFactory.materializeComponentInstancesC(candidates, descriptor, params)

        Then("the ambiguous exact identity is rejected instead of materializing both primaries")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.display shouldBe
              "canonical component binding has multiple exact Core/artifact identity matches: org.example.Cwitter"
          case Consequence.Success(value) =>
            fail(s"expected canonical candidate ambiguity rejection but got $value")
        }
      }
    }

    "E6 load the descriptor-bound component through the repository runtime path" must _metadata("E6") {
      "when exercising: load the descriptor-bound component through the repository runtime path" in {
      Given("a repository containing a descriptor-bound component CAR")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor-car.json")
        Files.writeString(
          componentdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0-SNAPSHOT")
        )
        _create_car(
          componentdir.resolve("structured-knowledge.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = _create_temp_file("generic-subsystem-factory", ".yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: mcprag
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: org.goldenport.cncf.Specification
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
        val names = subsystem.components.map(_.displayName).sorted
        val metadata = subsystem.components.flatMap(_.artifactMetadata)

        Then("the component and artifact metadata are visible in the subsystem")
        subsystem.name shouldBe "mcprag"
        subsystem.version shouldBe Some("0.1.0-SNAPSHOT")
        names should contain ("spec")
        metadata.map(_.component) should contain (Some("org.goldenport.cncf.Specification"))
      }
      }
    }

    "E10 prefer a complete sibling development set over an older packaged CAR" must _e3 {
      "when exercising: E10 prefer a complete sibling development set over an older packaged CAR" in {
        Given("two prepared prefixed development directories and an older same-name packaged CAR")
        _with_temp_dir { root =>
          val primarydir = root.resolve("devdirsample")
          val primaryclassdir = primarydir.resolve("target").resolve("scala-3.3.8").resolve("classes")
          _copy_test_package_classes(classOf[devdirsample.DevDirSampleComponent], primaryclassdir)
          _write_runtime_classpath(primarydir, primaryclassdir, "devdirsample", "0.1.0-SNAPSHOT", "devdirsample", "org.goldenport.fixture.DevDirSample")
          val secondarydir = root.resolve("secondarydevdirsample")
          val secondaryclassdir = secondarydir.resolve("target").resolve("scala-3.3.8").resolve("classes")
          _copy_test_package_classes(classOf[secondarydevdirsample.SecondaryDevDirSampleComponent], secondaryclassdir)
          _write_runtime_classpath(secondarydir, secondaryclassdir, "secondarydevdirsample", "0.1.0-SNAPSHOT", "secondarydevdirsample", "org.goldenport.fixture.SecondaryDevDirSample")
          val packagedir = Files.createDirectories(root.resolve("packaged"))
          val packagedjar = _create_fake_component_jar(root.resolve("assets").resolve("packaged-main.jar"))
          val packageddescriptor = root.resolve("packaged-descriptor.json")
          Files.writeString(
            packageddescriptor,
            _canonical_descriptor_json("org.goldenport.fixture.DevDirSample", "0.0.1")
          )
          _create_car(
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
              GenericSubsystemComponentBinding("org.goldenport.fixture.DevDirSample", version = Some("0.0.1")),
              GenericSubsystemComponentBinding("org.goldenport.fixture.SecondaryDevDirSample", version = Some("0.1.0-SNAPSHOT"))
            ),
            subsystemCapabilityProviders = Vector(
              GenericSubsystemCapabilityProviderBinding(
                "runtime-facilities",
                "org.goldenport.fixture.DevDirSample",
                Vector(
                  SubsystemCapabilityId.parseC("datastore.optimistic-concurrency@1").toOption.get,
                  SubsystemCapabilityId.parseC("datastore.persistent@1").toOption.get,
                  SubsystemCapabilityId.parseC("datastore.transactional@1").toOption.get,
                  SubsystemCapabilityId.parseC("user-context.current@1").toOption.get
                )
              )
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
            Set(
              "org.goldenport.fixture.DevDirSample",
              "org.goldenport.fixture.SecondaryDevDirSample"
            ).contains(component.name)
          )

          Then("both descriptor claims use development classes and no packaged provider is admitted")
          components should have size 2
          components.map(_.displayName).toSet shouldBe Set("devdirsample", "secondarydevdirsample")
          components.map(_.getClass.getName).toSet shouldBe Set(
            classOf[devdirsample.DevDirSamplePrimaryComponent].getName,
            classOf[secondarydevdirsample.SecondaryDevDirSampleComponent].getName
          )
          components.foreach { component =>
            component.artifactMetadata.flatMap(_.componentId) shouldBe Some(component.core.componentId)
          }
        }
      }
    }

    "E7 resolve an equivalent stable Subsystem identity through direct development and packaged inputs" must _metadata("E7") {
      "when exercising: resolve an equivalent stable Subsystem identity through direct development and packaged inputs" in {
      Given("equivalent direct component descriptors in prepared development and packaged CAR forms")
      _with_temp_dir { root =>
        val developmentdir = root.resolve("identity-development")
        val classdir = developmentdir.resolve("target").resolve("scala-3.3.8").resolve("classes")
        _copy_test_package_classes(classOf[devdirsample.DevDirSampleComponent], classdir)
        _write_runtime_classpath(
          developmentdir,
          classdir,
          "identity-component",
          "0.1.0-SNAPSHOT",
          "identity-component",
          "org.goldenport.fixture.DevDirSample"
        )
        val packageddescriptor = root.resolve("identity-component-descriptor.json")
        Files.writeString(
          packageddescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.DevDirSample", "0.1.0-SNAPSHOT"),
          StandardCharsets.UTF_8
        )
        val packagedcar = root.resolve("identity-component.car")
        _create_car(
          packagedcar,
          Seq("component-descriptor.json" -> packageddescriptor)
        )
        val developmentconfiguration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentDevDirKey -> ConfigurationValue.StringValue(developmentdir.toString)
          )),
          ConfigurationTrace.empty
        )
        val packagedconfiguration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentCarDirKey -> ConfigurationValue.StringValue(packagedcar.toString)
          )),
          ConfigurationTrace.empty
        )

        When("both direct runtime inputs resolve their implicit Subsystem descriptor")
        val development = GenericSubsystemFactory.resolveDescriptorC(developmentconfiguration).toOption.flatten.get
        val packaged = GenericSubsystemFactory.resolveDescriptorC(packagedconfiguration).toOption.flatten.get

        Then("the descriptor-owned Component identity is stable across the two paths")
        development.subsystemName shouldBe "org.goldenport.fixture.DevDirSample"
        packaged.subsystemName shouldBe "org.goldenport.fixture.DevDirSample"
      }
      }
    }

    "E8 retain a configured component binding when an assembly overlay declares empty components" must _metadata("E8") {
      "when exercising: retain a configured component binding when an assembly overlay declares empty components" in {
      Given("a configured component name and an assembly descriptor with empty components plus config")
      _with_temp_dir { root =>
        val componentname = "configured-component"
        val assembly = root.resolve("empty-components-assembly.yaml")
        Files.writeString(
          assembly,
          """subsystem: configured-component
            |components: []
            |config:
            |  assembly.overlay: retained
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentNameKey -> ConfigurationValue.StringValue(componentname),
            RuntimeConfig.assemblyDescriptorKey -> ConfigurationValue.StringValue(assembly.toString)
          )),
          ConfigurationTrace.empty
        )

        When("static and runtime descriptor resolution apply the configured component before the assembly overlay")
        val staticresult = GenericSubsystemFactory.resolveDescriptorC(configuration)
        val runtimeresult = GenericSubsystemFactory.runtimeResolveDescriptorC(
          configuration,
          Some(RepositoryBootstrapPolicy())
        )

        Then("both descriptors retain the configured binding and overlay config")
        staticresult shouldBe a[org.goldenport.Consequence.Success[_]]
        runtimeresult shouldBe a[org.goldenport.Consequence.Success[_]]
        val staticdescriptor = staticresult.toOption.flatten.getOrElse(fail("static descriptor"))
        val runtimedescriptor = runtimeresult.toOption.flatten.getOrElse(fail("runtime descriptor"))
        staticdescriptor.componentBindings shouldBe Vector(GenericSubsystemComponentBinding(componentname))
        runtimedescriptor.componentBindings shouldBe Vector(GenericSubsystemComponentBinding(componentname))
        staticdescriptor.config should contain ("assembly.overlay" -> "retained")
        runtimedescriptor.config should contain ("assembly.overlay" -> "retained")
      }
      }
    }

    "E9 reject an empty configured assembly when no component name or binding is available" must _metadata("E9") {
      "when exercising: reject an empty configured assembly when no component name or binding is available" in {
      Given("an assembly descriptor with empty components and no configured component name")
      _with_temp_dir { root =>
        val assembly = root.resolve("unbound-empty-components-assembly.yaml")
        Files.writeString(
          assembly,
          """subsystem: unbound-empty-components
            |components: []
            |config:
            |  assembly.overlay: retained
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.assemblyDescriptorKey -> ConfigurationValue.StringValue(assembly.toString)
          )),
          ConfigurationTrace.empty
        )

        When("static and runtime descriptor resolution reach the unbound assembly boundary")
        val staticresult = GenericSubsystemFactory.resolveDescriptorC(configuration)
        val runtimeresult = GenericSubsystemFactory.runtimeResolveDescriptorC(
          configuration,
          Some(RepositoryBootstrapPolicy())
        )

        Then("both boundaries return structured configuration failure")
        staticresult shouldBe a[org.goldenport.Consequence.Failure[_]]
        runtimeresult shouldBe a[org.goldenport.Consequence.Failure[_]]
      }
      }
    }

    "E11 accept an empty configured assembly with a valid explicit test descriptor" must _metadata("E11") {
      "when exercising: accept an empty configured assembly with a valid explicit test descriptor" in {
      Given("an assembly descriptor with empty components and a controlled-test descriptor")
      _with_temp_dir { root =>
        val assembly = root.resolve("controlled-test-empty-components-assembly.yaml")
        val testdescriptor = root.resolve("test.yaml")
        Files.writeString(
          assembly,
          """subsystem: controlled-test-assembly
            |components: []
            |config:
            |  assembly.overlay: retained
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        Files.writeString(
          testdescriptor,
          """kind: test-descriptor
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.assemblyDescriptorKey -> ConfigurationValue.StringValue(assembly.toString),
            RuntimeConfig.TEST_DESCRIPTOR_KEY -> ConfigurationValue.StringValue(testdescriptor.toString)
          )),
          ConfigurationTrace.empty
        )

        When("static and runtime descriptor resolution admit the validated controlled-test assembly")
        val staticresult = GenericSubsystemFactory.resolveDescriptorC(configuration)
        val runtimeresult = GenericSubsystemFactory.runtimeResolveDescriptorC(
          configuration,
          Some(RepositoryBootstrapPolicy())
        )

        Then("both descriptors retain empty bindings and assembly config")
        staticresult shouldBe a[org.goldenport.Consequence.Success[_]]
        runtimeresult shouldBe a[org.goldenport.Consequence.Success[_]]
        val staticdescriptor = staticresult.toOption.flatten.getOrElse(fail("static descriptor"))
        val runtimedescriptor = runtimeresult.toOption.flatten.getOrElse(fail("runtime descriptor"))
        staticdescriptor.componentBindings shouldBe Vector.empty
        runtimedescriptor.componentBindings shouldBe Vector.empty
        staticdescriptor.config should contain ("assembly.overlay" -> "retained")
        runtimedescriptor.config should contain ("assembly.overlay" -> "retained")
      }
      }
    }

    "E12 isolate assembly descriptors across explicit component CARs" must _metadata("E12") {
      "when exercising: isolate assembly descriptors across explicit component CARs" in {
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
          _canonical_descriptor_json("org.goldenport.fixture.ComponentFileApp", "0.1.0")
        )
        Files.writeString(
          providerdescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.PlainAiRunnerProvider", "0.1.0")
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
            GenericSubsystemComponentBinding("org.goldenport.fixture.ComponentFileApp", version = Some("0.1.0")),
            GenericSubsystemComponentBinding("org.goldenport.fixture.PlainAiRunnerProvider", version = Some("0.1.0"))
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
          expectednames.contains(component.displayName)
        )
        explicitcomponents.map(_.name).sorted shouldBe Vector(
          "org.goldenport.fixture.ComponentFileApp",
          "org.goldenport.fixture.PlainAiRunnerProvider"
        )
        explicitcomponents.flatMap(_.artifactMetadata.flatMap(_.component)).sorted shouldBe Vector(
          "org.goldenport.fixture.ComponentFileApp",
          "org.goldenport.fixture.PlainAiRunnerProvider"
        )
      }
      }
    }

    }

    "runtime resolution and security wiring" which {
    "E13 make descriptor-bound component operations visible through the subsystem resolver" must _metadata("E13") {
      "when exercising: make descriptor-bound component operations visible through the subsystem resolver" in {
      Given("a repository component with generated specification operations")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor-car.json")
        Files.writeString(
          componentdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0-SNAPSHOT")
        )
        _create_car(
          componentdir.resolve("structured-knowledge.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = _create_temp_file("generic-subsystem-factory-visibility", ".yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: mcprag
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: org.goldenport.cncf.Specification
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
        subsystem.components.flatMap(_.artifactMetadata.map(_.component)) should contain (Some("org.goldenport.cncf.Specification"))

        And("the subsystem resolver exposes the component operation")
        subsystem.resolver.resolve("org.goldenport.cncf.Specification.export.openapi") shouldBe
          ResolutionResult.Resolved(
            fqn = "org.goldenport.cncf.Specification.export.openapi",
            component = "org.goldenport.cncf.Specification",
            service = "export",
            operation = "openapi"
          )
      }
      }
    }

    "E14 carry descriptor-defined security wiring from the textus-identity journal sample descriptor" must _metadata("E14") {
      "when exercising: carry descriptor-defined security wiring from the textus-identity journal sample descriptor" in {
      Given("the maintained textus-identity descriptor")
      val descriptorpath = Path.of(
        "docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml"
      ).toAbsolutePath.normalize
      val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption.get

      _with_temp_dir { repositorydir =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(s"component-dir:$repositorydir")
          )),
          ConfigurationTrace.empty
        )

        When("the subsystem is constructed with an isolated empty repository")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)
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
      }
    }

    "E15 resolve a subsystem descriptor from component repository using subsystem name only" must _metadata("E15") {
      "when exercising: resolve a subsystem descriptor from component repository using subsystem name only" in {
      Given("a component repository containing a CAR and subsystem SAR")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = componentdir.resolve("component-descriptor.json")
        Files.writeString(
          componentdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0-SNAPSHOT")
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
            |  - name: org.goldenport.cncf.Specification
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
        subsystem.components.map(_.displayName) should contain ("spec")
        subsystem.components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("org.goldenport.cncf.Specification")
      }
      }
    }

    "E16 resolve descriptor components from the default standard repository using name and version without repository config" must _metadata("E16") {
      "when exercising: resolve descriptor components from the default standard repository using name and version without repository config" in {
      Given("a CAR in the default user repository and no explicit repository setting")
      _with_temp_dir { homedir =>
        val cachedir = homedir.resolve(".cncf").resolve("cache").resolve("car").resolve("org.goldenport.cncf.Specification").resolve("0.1.0")
        Files.createDirectories(cachedir)
        val fakecomponentjar = _create_fake_component_jar(homedir.resolve("assets").resolve("component-main.jar"))
        val componentdescriptor = homedir.resolve("component-descriptor.json")
        Files.writeString(
          componentdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0")
        )
        _create_car(
          cachedir.resolve("org.goldenport.cncf.Specification-0.1.0.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )

        val descriptorpath = _create_temp_file("generic-subsystem-factory-default-standard", ".yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: textus-identity
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - name: org.goldenport.cncf.Specification
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
          subsystem.components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("org.goldenport.cncf.Specification")
        } finally {
          if (originalhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", originalhome)
        }
      }
      }
    }

    }
  }

  private def _repository_configuration(repositorydir: Path): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.repositoryDirKey -> ConfigurationValue.StringValue(s"component-dir:$repositorydir")
      )),
      ConfigurationTrace.empty
    )

  private def _install_specification_car(repositorydir: Path): Unit = {
    val assets = repositorydir.resolve("cid06c-specification-assets")
    val componentjar = _create_fake_component_jar(assets.resolve("component-main.jar"))
    val componentdescriptor = assets.resolve("component-descriptor.json")
    Files.writeString(
      componentdescriptor,
      _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0"),
      StandardCharsets.UTF_8
    )
    _create_car(
      repositorydir.resolve("org.goldenport.cncf.Specification-0.1.0.car"),
      Seq(
        "component/main.jar" -> componentjar,
        "component-descriptor.json" -> componentdescriptor
      )
    )
  }

  private def _canonical_descriptor(componentid: ComponentId): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some("0.1.0"),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )

  private object NamedInstanceFactory extends Component.PrimaryComponentFactory {
    protected def create_Component(params: ComponentCreate): Component =
      new Component() {
        override def mcpReadyServices: Set[String] = Set("Search")
      }

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      Component.Core.create(
        "org.goldenport.cncf.spec.Scraper",
        ComponentId("org.goldenport.cncf.spec.Scraper"),
        ComponentInstanceId.default(ComponentId("org.goldenport.cncf.spec.Scraper")),
        Protocol.empty,
        this
      )
  }

  private object NamedBundleFactory extends Component.BundleFactory {
    object Primary extends Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

      protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          "org.goldenport.cncf.spec.Scraper",
          ComponentId("org.goldenport.cncf.spec.Scraper"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.spec.Scraper")),
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
          "org.goldenport.cncf.spec.ScraperAdmin",
          ComponentId("org.goldenport.cncf.spec.ScraperAdmin"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.spec.ScraperAdmin")),
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
    component: String,
    componentid: String
  ): Unit = {
    val file = componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    val cardir = componentdir.resolve("src").resolve("main").resolve("car")
    val descriptor = file.getParent.resolve("component-descriptor.json")
    Files.createDirectories(file.getParent)
    Files.createDirectories(cardir)
    Files.writeString(file, classdir.toString, StandardCharsets.UTF_8)
    Files.writeString(
      descriptor,
      _canonical_development_descriptor_json(componentid, version),
      StandardCharsets.UTF_8
    )
    Files.writeString(cardir.resolve("component-descriptor.json"), Files.readString(descriptor, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    Files.writeString(
      cardir.resolve("abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v1","car":{"name":"$componentid","version":"$version"},"abi":{"exports":{"components":[{"name":"$componentid"}]}}}""",
      StandardCharsets.UTF_8
    )
    val classpathidentity = s"project:${componentdir.relativize(classdir).toString.replace('\\', '/')}"
    val evidence = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(file), Some(_sha256(classpathidentity.getBytes(StandardCharsets.UTF_8)))),
      ("target/cncf.d/component-descriptor.json", _sha256(descriptor), None),
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
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v2","sourceKind":"development-directory","car":{"name":"$componentid","version":"$version","component":"$componentid"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$evidencedigest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _canonical_development_descriptor_json(
    componentid: String,
    release: String
  ): String =
    s"""{"schemaVersion":2,"name":"$componentid","version":"$release","component":{"name":"$componentid"},"componentStyle":{"apiVersion":"cncf.textus/v1","provider":"cncf","id":"full-fledged-with-standalone@1","version":1,"parameterSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false},"parameters":{},"provides":{"bundles":["domain.full@1"],"capabilities":["user.fixed-context-compatible@1","user.multi-user@1"],"effective":["domain.aggregate@1","domain.command@1","domain.domain-event@1","domain.entity@1","domain.optimistic-concurrency@1","domain.persistence@1","domain.projection@1","domain.query@1","domain.transaction@1","user.fixed-context-compatible@1","user.multi-user@1"]},"requires":{"subsystemCapabilities":["datastore.optimistic-concurrency@1","datastore.persistent@1","datastore.transactional@1","user-context.current@1"]}}}"""

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _canonical_descriptor_json(
    componentid: String,
    release: String
  ): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }

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
    val workdir = Files.createDirectories(
      Path.of("target", "generic-subsystem-factory-spec", "work").toAbsolutePath.normalize
    )
    val base = Files.createTempDirectory(workdir, "generic-subsystem-factory-spec")
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

  private def _create_temp_file(prefix: String, suffix: String): Path = {
    val workdir = Files.createDirectories(
      Path.of("target", "generic-subsystem-factory-spec", "work").toAbsolutePath.normalize
    )
    Files.createTempFile(workdir, prefix, suffix)
  }
}
