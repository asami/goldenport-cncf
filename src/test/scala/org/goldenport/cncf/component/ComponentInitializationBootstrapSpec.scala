package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.backend.collaborator.{
  CollaboratorFactory,
  CollaboratorRepository
}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.config.{
  ComponentInitializationParameters,
  ComponentParameterKey,
  ComponentParameterProvenance,
  RuntimeConfig
}
import org.goldenport.cncf.subsystem.{
  GenericSubsystemComponentBinding,
  GenericSubsystemDescriptor,
  GenericSubsystemFactory
}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentInitializationBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Component initialization bootstrap" should {
    "deliver context-bound snapshots" which {
      "resolve required typed parameters before component initialization" in {
        Given("a factory declaration and packaged component parameter default")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-bootstrap")
        val descriptor = ComponentDescriptor(
          componentName = Some("parameter_probe"),
          config = Map("provider.limit" -> "12")
        )

        When("the consequence-aware factory path creates the component")
        val result = ParameterProbeFactory.createPrimaryC(
          ComponentCreate(
            subsystem,
            ComponentOrigin.Repository("phase-47"),
            componentDescriptors = Vector(descriptor)
          )
        )

        Then("component-specific initialization receives only the resolved typed snapshot")
        val component = withClue(result) {
          result.toOption.value.asInstanceOf[ParameterProbeComponent]
        }
        component.limit shouldBe Some(12)
        component.provenance shouldBe Some(ComponentParameterProvenance.PackagedDefault)
        component.initializationParameters.size shouldBe 1
      }

      "isolate named component instance settings" in {
        Given("one factory and two assembly instances with different values")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-instances")
        val descriptor = ComponentDescriptor(componentName = Some("parameter_probe"))
        val base = ComponentCreate(
          subsystem,
          ComponentOrigin.Repository("phase-47"),
          componentDescriptors = Vector(descriptor)
        )

        When("each named instance is materialized independently")
        val first = ParameterProbeFactory.createPrimaryC(
          base.withInstanceMetadata(
            ComponentInstanceMetadata("parameter_probe", "first", Map("provider.limit" -> "3"))
          )
        ).toOption.value.asInstanceOf[ParameterProbeComponent]
        val second = ParameterProbeFactory.createPrimaryC(
          base.withInstanceMetadata(
            ComponentInstanceMetadata("parameter_probe", "second", Map("provider.limit" -> "8"))
          )
        ).toOption.value.asInstanceOf[ParameterProbeComponent]

        Then("each immutable snapshot retains only its selected instance value")
        first.instanceId shouldBe ComponentInstanceId("parameter_probe", "first")
        second.instanceId shouldBe ComponentInstanceId("parameter_probe", "second")
        first.limit shouldBe Some(3)
        second.limit shouldBe Some(8)
        first.provenance shouldBe Some(ComponentParameterProvenance.SubsystemInstance)
        second.provenance shouldBe Some(ComponentParameterProvenance.SubsystemInstance)
        first.applicationConfig.config.flatMap(_.get("provider.limit")) should not be empty

        And("generated valid integer values preserve the same instance isolation contract")
        val property = Prop.forAll(Gen.choose(0, 100000)) { value =>
          ParameterProbeFactory.createPrimaryC(
            base.withInstanceMetadata(
              ComponentInstanceMetadata(
                "parameter_probe",
                s"generated$value",
                Map("provider.limit" -> value.toString)
              )
            )
          ).toOption.exists { component =>
            component.asInstanceOf[ParameterProbeComponent].limit.contains(value)
          }
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property).passed shouldBe true
      }

      "connect the fixed bootstrap layers to the selected runtime context" in {
        Given("packaged, assembly, instance, runtime, and explicit test values for one declaration")
        val testpath = Files.createTempFile("component-initialization-bootstrap", ".json")
        Files.writeString(
          testpath,
          """{"config":{"provider.limit":"55"}}""",
          StandardCharsets.UTF_8
        )
        val runtime = ResolvedConfiguration(
          Configuration(Map(
            "provider.limit" -> ConfigurationValue.StringValue("44"),
            RuntimeConfig.TEST_DESCRIPTOR_KEY -> ConfigurationValue.StringValue(testpath.toString)
          )),
          ConfigurationTrace.empty
        )
        val subsystem = TestComponentFactory
          .emptySubsystem("initialization-layers", configuration = runtime)
          .withDescriptor(
            GenericSubsystemDescriptor(
              path = testpath,
              subsystemName = "initialization-layers",
              config = Map("provider.limit" -> "33")
            )
          )
        val descriptor = ComponentDescriptor(
          componentName = Some("parameter_probe"),
          config = Map("provider.limit" -> "22")
        )

        try {
          When("the runtime resolves the declared value during factory bootstrap")
          val component = ParameterProbeFactory.createPrimaryC(
            ComponentCreate(
              subsystem,
              ComponentOrigin.Repository("phase-47"),
              Vector(descriptor),
              Some(ComponentInstanceMetadata(
                "parameter_probe",
                "layered",
                Map("provider.limit" -> "11")
              ))
            )
          ).toOption.value.asInstanceOf[ParameterProbeComponent]

          Then("the explicit test descriptor remains the highest fixed source")
          component.limit shouldBe Some(55)
          component.provenance shouldBe Some(ComponentParameterProvenance.TestOverlay)
        } finally {
          Files.deleteIfExists(testpath)
        }
      }

      "use descriptor and instance values through the repository subsystem path" in {
        Given("an isolated development repository whose factory requires one initialization parameter")
        _with_parameter_repository { repositorydir =>
          val configuration = ResolvedConfiguration(
            Configuration(Map(
              RuntimeConfig.ComponentDevDirKey -> ConfigurationValue.StringValue(repositorydir.toString)
            )),
            ConfigurationTrace.empty
          )
          val assemblydescriptor = GenericSubsystemDescriptor(
            path = repositorydir,
            subsystemName = "repository-assembly-parameter",
            componentBindings = Vector(
              GenericSubsystemComponentBinding("repository_parameter_probe")
            ),
            config = Map("provider.limit" -> "33")
          )
          val instancedescriptor = assemblydescriptor.copy(
            subsystemName = "repository-instance-parameter",
            componentBindings = Vector(
              GenericSubsystemComponentBinding(
                "repository_parameter_probe",
                instance = Some("configured"),
                config = Map("provider.limit" -> "44")
              )
            )
          )

          When("each descriptor is bootstrapped through GenericSubsystemFactory and its repository")
          val assemblycomponent = GenericSubsystemFactory
            .default(assemblydescriptor, configuration = configuration)
            .components
            .find(_.name == "repository_parameter_probe")
            .value
          val instancecomponent = GenericSubsystemFactory
            .default(instancedescriptor, configuration = configuration)
            .components
            .find(_.name == "repository_parameter_probe")
            .value

          Then("assembly defaults are available before discovery and instance settings override them")
          assemblycomponent.initializationParameters
            .resolve(RepositoryParameterProbeFactory.limitKey)
            .toOption
            .flatMap(_.value) shouldBe Some(33)
          instancecomponent.initializationParameters
            .resolve(RepositoryParameterProbeFactory.limitKey)
            .toOption
            .flatMap(_.value) shouldBe Some(44)
          instancecomponent.instanceMetadata.map(_.instance) shouldBe Some("configured")
        }
      }

    }

    "preserve structured failure" which {
      "reject missing and malformed declarations before installation" in {
        Given("a required integer declaration without a valid selected value")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-invalid")
        val missingdescriptor = ComponentDescriptor(componentName = Some("parameter_probe"))
        val malformeddescriptor = missingdescriptor.copy(config = Map("provider.limit" -> "invalid"))

        When("the factory resolves both invalid bootstrap contexts")
        val missing = ParameterProbeFactory.createPrimaryC(
          ComponentCreate(subsystem, ComponentOrigin.Repository("phase-47"), Vector(missingdescriptor))
        )
        val malformed = ParameterProbeFactory.createPrimaryC(
          ComponentCreate(subsystem, ComponentOrigin.Repository("phase-47"), Vector(malformeddescriptor))
        )

        Then("both remain structured failures and no component can be admitted")
        missing.isFaillure shouldBe true
        malformed.isFaillure shouldBe true
      }

      "preserve repository factory parameter failures" in {
        Given("a development repository and a malformed required instance setting")
        _with_parameter_repository { repositorydir =>
          val binding = GenericSubsystemComponentBinding(
            "repository_parameter_probe",
            config = Map("provider.limit" -> "invalid")
          )
          val descriptor = GenericSubsystemDescriptor(
            path = repositorydir,
            subsystemName = "initialization-repository-failure",
            componentBindings = Vector(binding)
          )
          val subsystem = TestComponentFactory
            .emptySubsystem("initialization-repository-failure")
            .withDescriptor(descriptor)
          val params = ComponentCreate(
            subsystem,
            ComponentOrigin.Repository("phase-47"),
            descriptor.toComponentDescriptors,
            Some(binding.instanceMetadata)
          )
          val repository = ComponentRepository.ComponentDevDirRepository
            .Specification(repositorydir)
            .build(params)

          val repositoryspace = ComponentRepositorySpace(Vector(
            ComponentRepositorySpace.Slot(repository, ComponentOrigin.Repository("phase-47"))
          ))

          When("consequence-aware component discovery invokes the repository factory")
          val result = new ComponentFactory(repositoryspace).discoverC()

          Then("the original structured initialization failure is not converted to absence")
          result.isFaillure shouldBe true
        }
      }

    }

    "support participant variants" which {
      "resolve componentlet parameters through the owning descriptor context" in {
        Given("a componentlet declaration owned by one packaged component descriptor")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-componentlet")
        val descriptor = ComponentDescriptor(
          componentName = Some("parameter_probe"),
          componentlets = Vector(ComponentletDescriptor("parameter_probe_admin"))
        )
        val metadata = ComponentInstanceMetadata(
          "parameter_probe",
          "operator",
          Map("provider.limit" -> "21")
        )

        When("the componentlet participant is initialized")
        val component = ParameterComponentletFactory.createComponentletC(
          ComponentCreate(
            subsystem,
            ComponentOrigin.Repository("phase-47"),
            Vector(descriptor),
            Some(metadata)
          )
        ).toOption.value.asInstanceOf[ParameterProbeComponent]

        Then("the participant keeps its own identity and the owner's instance settings")
        component.instanceId shouldBe ComponentInstanceId("parameter_probe_admin", "operator")
        component.limit shouldBe Some(21)
      }

      "avoid descriptor context for factories without declarations" in {
        Given("a factory that declares no initialization parameters")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-empty")

        When("the component is created without descriptors or instance metadata")
        val result = EmptyParameterFactory.createPrimaryC(
          ComponentCreate(subsystem, ComponentOrigin.Builtin)
        )
        val component = withClue(result) { result.toOption.value }

        Then("the runtime supplies the canonical empty typed snapshot")
        component.initializationParameters should be theSameInstanceAs ComponentInitializationParameters.empty
      }

      "deliver the same typed snapshot to special component initialization" in {
        Given("a collaborator component with one declared initialization parameter")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-special")
        val descriptor = ComponentDescriptor(
          componentName = Some("special_probe"),
          config = Map("provider.limit" -> "34")
        )
        val component = SpecialProbeFactory.createPrimaryC(
          ComponentCreate(
            subsystem,
            ComponentOrigin.Repository("phase-47"),
            Vector(descriptor)
          )
        ).toOption.value.asInstanceOf[SpecialProbeComponent]
        val entry = CollaboratorRepository.CollaboratorEntry(
          "special_probe",
          new api.Collaborator {
            def invoke(call: api.ActionCall): api.Consequence =
              new api.SuccessConsequence("unused")
          },
          getClass.getClassLoader,
          "spec"
        )
        val collaborators = new CollaboratorFactory() {
          override def entries: Vector[CollaboratorRepository.CollaboratorEntry] = Vector(entry)
          override def resolve(name: String): Option[CollaboratorRepository.CollaboratorEntry] =
            entries.find(_.name == name)
        }
        val factory = new ComponentFactory(
          ComponentRepositorySpace(),
          collaborators
        )

        When("special component bootstrap installs its collaborator core")
        val initialized = factory.bootstrapC(component).toOption.value

        Then("the special initializer receives the exact common bootstrap snapshot")
        initialized shouldBe component
        component.specialParameters should be theSameInstanceAs component.initializationParameters
        component.specialParameters.resolve(SpecialProbeFactory.limitKey).toOption
          .flatMap(_.value) shouldBe Some(34)
      }

      "preserve special component initialization exceptions as structured failures" in {
        Given("a collaborator component whose special initializer fails")
        val subsystem = TestComponentFactory.emptySubsystem("initialization-special-failure")
        val component = FailingSpecialProbeFactory.createPrimaryC(
          ComponentCreate(subsystem, ComponentOrigin.Repository("phase-47"))
        ).toOption.value
        val entry = CollaboratorRepository.CollaboratorEntry(
          "failing_special_probe",
          new api.Collaborator {
            def invoke(call: api.ActionCall): api.Consequence =
              new api.SuccessConsequence("unused")
          },
          getClass.getClassLoader,
          "spec"
        )
        val collaborators = new CollaboratorFactory() {
          override def entries: Vector[CollaboratorRepository.CollaboratorEntry] = Vector(entry)
          override def resolve(name: String): Option[CollaboratorRepository.CollaboratorEntry] =
            entries.find(_.name == name)
        }

        When("the consequence-aware component bootstrap invokes the special initializer")
        val result = new ComponentFactory(ComponentRepositorySpace(), collaborators).bootstrapC(component)

        Then("the exception is represented by the normal structured failure channel")
        result.isFaillure shouldBe true
      }
    }

    "protect the public snapshot boundary" which {
      "keep physical runtime configuration outside the public snapshot API" in {
        Given("the public component initialization snapshot type")
        val forbidden: Set[Class[?]] = Set(
          classOf[org.goldenport.configuration.Configuration],
          classOf[org.goldenport.configuration.ResolvedConfiguration]
        )

        When("its public method signatures are inspected")
        val exposed = classOf[ComponentInitializationParameters]
          .getMethods
          .flatMap(method => method.getParameterTypes.toVector :+ method.getReturnType)
          .toSet

        Then("neither raw configuration representation crosses the component boundary")
        exposed.intersect(forbidden) shouldBe empty
      }
    }
  }

  private def _with_parameter_repository[T](body: Path => T): T = {
    val repositorydir = Files.createTempDirectory("component-parameter-repository")
    val classdir = repositorydir.resolve("classes")
    val runtimeclasspath = repositorydir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    val factoryresource = classOf[RepositoryParameterProbeFactory].getName.replace('.', '/') + ".class"
    val factoryclass = classdir.resolve(factoryresource)
    Files.createDirectories(factoryclass.getParent)
    Using.resource(classOf[RepositoryParameterProbeFactory].getClassLoader.getResourceAsStream(factoryresource)) { in =>
      Files.copy(in, factoryclass)
    }
    Files.createDirectories(runtimeclasspath.getParent)
    Files.writeString(runtimeclasspath, classdir.toString, StandardCharsets.UTF_8)
    try {
      body(repositorydir)
    } finally {
      Using.resource(Files.walk(repositorydir)) { stream =>
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(Files.deleteIfExists(_))
      }
    }
  }

  private final class ParameterProbeComponent extends Component {
    var limit: Option[Int] = None
    var provenance: Option[ComponentParameterProvenance] = None

    override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
      params.initializationParameters.resolve(ParameterProbeFactory.limitKey).map { resolution =>
        limit = resolution.value
        provenance = Some(resolution.provenance)
      }
  }

  private object ParameterProbeFactory extends Component.Factory {
    val limitKey: ComponentParameterKey[Int] = ComponentParameterKey.requiredInt("provider.limit")

    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector(limitKey)

    protected def create_Component(params: ComponentCreate): Component =
      new ParameterProbeComponent

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      spec_create(
        "parameter_probe",
        ComponentId("parameter_probe"),
        Vector.empty[spec.ServiceDefinition]
      )
  }

  private object ParameterComponentletFactory extends Component.Factory {
    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector(ParameterProbeFactory.limitKey)

    protected def create_Component(params: ComponentCreate): Component =
      new ParameterProbeComponent

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      spec_create(
        "parameter_probe_admin",
        ComponentId("parameter_probe_admin"),
        Vector.empty[spec.ServiceDefinition]
      )
  }

  private object EmptyParameterFactory extends Component.Factory {
    protected def create_Component(params: ComponentCreate): Component =
      new Component {}

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      spec_create(
        "empty_parameter_probe",
        ComponentId("empty_parameter_probe"),
        Vector.empty[spec.ServiceDefinition]
      )
  }

  private final class SpecialProbeComponent extends CollaboratorComponent {
    var specialParameters: ComponentInitializationParameters = ComponentInitializationParameters.empty

    override def initialize(params: CollaboratorComponentInit): CollaboratorComponent = {
      specialParameters = params.initializationParameters
      super.initialize(params)
    }
  }

  private object SpecialProbeFactory extends Component.Factory {
    val limitKey: ComponentParameterKey[Int] = ComponentParameterKey.requiredInt("provider.limit")

    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector(limitKey)

    protected def create_Component(params: ComponentCreate): Component =
      new SpecialProbeComponent

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      spec_create(
        "special_probe",
        ComponentId("special_probe"),
        Vector.empty[spec.ServiceDefinition]
      )
  }

  private final class FailingSpecialProbeComponent extends CollaboratorComponent {
    override def initialize(params: CollaboratorComponentInit): CollaboratorComponent =
      throw new IllegalStateException("special initialization failed")
  }

  private object FailingSpecialProbeFactory extends Component.Factory {
    protected def create_Component(params: ComponentCreate): Component =
      new FailingSpecialProbeComponent

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      spec_create(
        "failing_special_probe",
        ComponentId("failing_special_probe"),
        Vector.empty[spec.ServiceDefinition]
      )
  }
}

final class RepositoryParameterProbeComponent extends Component

final class RepositoryParameterProbeFactory extends Component.PrimaryComponentFactory {
  override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
    Vector(RepositoryParameterProbeFactory.limitKey)

  protected def create_Component(params: ComponentCreate): Component =
    {
      RepositoryParameterProbeFactory.recordCreation()
      new RepositoryParameterProbeComponent
    }

  protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
    spec_create(
      "repository_parameter_probe",
      ComponentId("repository_parameter_probe"),
      Vector.empty[spec.ServiceDefinition]
    )
}

object RepositoryParameterProbeFactory {
  private val _creation_count = new AtomicInteger(0)

  val limitKey: ComponentParameterKey[Int] = ComponentParameterKey.requiredInt("provider.limit")

  def resetCreationCount(): Unit =
    _creation_count.set(0)

  def creationCount: Int =
    _creation_count.get()

  private[component] def recordCreation(): Unit =
    _creation_count.incrementAndGet()
}
