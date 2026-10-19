package org.goldenport.cncf.component

import domain.logicalui.DomainLogicalUiDefinitions
import logicalui.{DisplayServiceReadFixture, GeneratedDisplayConsumerFixture, TypedDisplayRuntimeFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.display.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class ComponentFactoryLogicalUiActivationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ComponentFactoryLogicalUiActivationSpec.*

  "Selected Logical UI activation" should {
    "default-compatible bootstrap" which {
      "retain an unchanged actual generated consumer with the concrete default enabled" in {
        Given("an existing generated provider which implements only the definition capability")
        _with_subsystem { subsystem =>
          val component = new GeneratedDisplayConsumerFixture.ActualComponent(Vector.empty)
          DisplayServiceReadFixture.initialize(component, subsystem)
          When("ordinary bootstrap reads its existing definitions and bindings")
          val result = new ComponentFactory().bootstrapC(component)
          Then("the concrete default publishes the complete generated registry and ordinary service")
          result.isSuccess shouldBe true
          component.generatedLogicalUiEnabled shouldBe true
          component._admitted_logical_ui_definitions.get.definitions shouldBe DomainLogicalUiDefinitions.definitions
          component.displayRuntime.get.screenIds shouldBe DomainLogicalUiDefinitions.definitions.map(_.screenId)
          component.protocol.services.services.count(_.name == "display") shouldBe 1
          component.metadataReads shouldBe 1
          component.bindingReads shouldBe 1
          component.installed.get.browser.findCalls shouldBe empty
          component.installed.get.browser.queryCalls shouldBe empty
        }
      }

      "skip every Display getter when the selected component explicitly disables publication" in {
        Given("a disabled provider whose metadata runtime service mutation and Business getters would throw")
        _with_subsystem { subsystem =>
          val component = new ActivationComponent(() => false, () => throw new IllegalStateException("unused metadata"), true)
          DisplayServiceReadFixture.initialize(component, subsystem)
          When("ordinary component bootstrap evaluates its server-side activation once")
          val result = new ComponentFactory().bootstrapC(component)
          Then("legacy bootstrap succeeds with no Display publication or unused getter evaluation")
          result.isSuccess shouldBe true
          component.collectionsBootstrapped shouldBe true
          component.activationReads shouldBe 1
          _unread(component)
          _unpublished(component)
        }
      }

      "keep a disabled component authoritative over an enabled factory" in {
        Given("an explicitly disabled component and a factory with valid enabled metadata and bindings")
        _with_subsystem { subsystem =>
          val fallback = new TrackingFactory
          val component = new ActivationComponent(() => false, () => DomainLogicalUiDefinitions.definitions, true)
          DisplayServiceReadFixture.initialize(component, subsystem, factory = Some(fallback))
          When("ordinary bootstrap selects the component capability before its activation decision")
          val result = new ComponentFactory().bootstrapC(component)
          Then("the factory activation metadata and runtime capabilities are never considered")
          result.isSuccess shouldBe true
          component.activationReads shouldBe 1
          _unread(component)
          fallback.reads shouldBe 0
          _unpublished(component)
        }
      }
    }

    "enabled-provider failure and lifetime" which {
      "preserve definitions-missing for enabled empty metadata without fallback or partial publication" in {
        Given("an enabled empty component provider and an otherwise usable factory")
        _with_subsystem { subsystem =>
          val fallback = new TrackingFactory
          val component = new ActivationComponent(() => true, () => Vector.empty, true)
          DisplayServiceReadFixture.initialize(component, subsystem, factory = Some(fallback))
          When("ordinary bootstrap admits the whole selected vector")
          val result = new ComponentFactory().bootstrapC(component)
          Then("the unchanged structured ABI diagnostic rejects the component before any binding getter")
          _diagnostic(result).code shouldBe "LUI96_DEFINITIONS_MISSING"
          _diagnostic(result).path shouldBe "definitions"
          component.activationReads shouldBe 1
          component.metadataReads shouldBe 1
          component.runtimeReads shouldBe 0
          component.serviceReads shouldBe 0
          component.mutationReads shouldBe 0
          component.businessReads shouldBe 0
          fallback.reads shouldBe 0
          component.collectionsBootstrapped shouldBe false
          _unpublished(component)
        }
      }

      "contain an activation getter exception as the existing structured component failure" in {
        Given("a selected activation getter which throws and a valid shadowed factory")
        _with_subsystem { subsystem =>
          val error = new IllegalStateException("activation capability failed")
          val component = new ActivationComponent(() => throw error, () => DomainLogicalUiDefinitions.definitions, true)
          val fallback = new TrackingFactory
          DisplayServiceReadFixture.initialize(component, subsystem, factory = Some(fallback))
          When("ordinary bootstrap evaluates selected activation")
          val result = new ComponentFactory().bootstrapC(component)
          Then("the component-invalid taxonomy and original boundary cause are retained without fallback")
          val failure = _failure(result)
          failure.conclusion.observation.taxonomy shouldBe _failure(Consequence.componentInvalid(error)).conclusion.observation.taxonomy
          failure.conclusion.observation.cause.getException shouldBe Some(error)
          component.activationReads shouldBe 1
          _unread(component)
          fallback.reads shouldBe 0
          component.collectionsBootstrapped shouldBe false
          _unpublished(component)
        }
      }

      "retain successful activation and complete registration across repeated bootstrap" in {
        Given("an enabled generated consumer with counted metadata runtime service mutation and Business providers")
        _with_subsystem { subsystem =>
          val component = new ActivationComponent(() => true, () => DomainLogicalUiDefinitions.definitions, false)
          DisplayServiceReadFixture.initialize(component, subsystem)
          val factory = new ComponentFactory()
          When("ordinary bootstrap completes and is requested again on the same component")
          val first = factory.bootstrapC(component)
          val metadata = component._admitted_logical_ui_definitions
          val runtime = component.displayRuntime
          val protocol = component.protocol
          val services = component.services
          val second = factory.bootstrapC(component)
          Then("activation and every getter run exactly once while all published objects retain identity")
          first.isSuccess shouldBe true
          second.isSuccess shouldBe true
          component.activationReads shouldBe 1
          component.metadataReads shouldBe 1
          component.runtimeReads shouldBe 1
          component.serviceReads shouldBe 1
          component.mutationReads shouldBe 1
          component.businessReads shouldBe 1
          (component._admitted_logical_ui_definitions eq metadata) shouldBe true
          (component.displayRuntime eq runtime) shouldBe true
          (component.protocol eq protocol) shouldBe true
          (component.services eq services) shouldBe true
          component.collectionsBootstrapped shouldBe true
        }
      }
    }

    "activation and whole-vector admission laws" which {
      "preserve disabled absence and enabled valid empty or malformed semantics for at least 100 cases" in {
        Given("Boolean activation and valid empty or malformed complete generated definition vectors")
        val cases = for { enabled <- Gen.oneOf(true, false); shape <- Gen.choose(0, 2) } yield (enabled, shape)
        val property = Prop.forAll(cases) { case (enabled, shape) =>
          val subsystem = TestComponentFactory.emptySubsystem("logical-ui-activation-property")
          try {
            val definitions = shape match {
              case 0 => DomainLogicalUiDefinitions.definitions
              case 1 => Vector.empty
              case _ => DomainLogicalUiDefinitions.definitions.updated(1,
                DomainLogicalUiDefinitions.definitions(1).copy(definitionRevision = ""))
            }
            val component = new ActivationComponent(() => enabled, () => definitions, false)
            DisplayServiceReadFixture.initialize(component, subsystem)
            When("ordinary bootstrap selects activation and conditionally admits the complete vector")
            val result = new ComponentFactory().bootstrapC(component)
            Then("disabled providers remain unread while enabled vectors retain authoritative admission semantics")
            component.activationReads shouldBe 1
            if (!enabled) {
              result.isSuccess shouldBe true
              _unread(component)
              _unpublished(component)
            } else if (shape == 0) {
              result.isSuccess shouldBe true
              component._admitted_logical_ui_definitions.get.definitions shouldBe definitions
              component.runtimeReads shouldBe 1
              component.serviceReads shouldBe 1
            } else {
              _diagnostic(result) shouldBe _diagnostic(GeneratedLogicalUiAbi.admitC(definitions))
              component.metadataReads shouldBe 1
              component.runtimeReads shouldBe 0
              component.serviceReads shouldBe 0
              _unpublished(component)
            }
            true
          } finally subsystem.shutdown()
        }
        When("ScalaCheck checks at least 100 undiscarded activation and definition cases")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("all generated cases satisfy the activation and unchanged admission laws")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }
    }
  }

  private def _with_subsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("logical-ui-activation")
    try body(subsystem) finally subsystem.shutdown()
  }
  private def _failure(result: Consequence[?]): Consequence.Failure[?] = result match {
    case failure: Consequence.Failure[?] => failure
    case _ => fail("expected structured bootstrap rejection")
  }
  private def _diagnostic(result: Consequence[?]): GeneratedLogicalUiAbi.Diagnostic = {
    val failure = _failure(result)
    failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    failure.conclusion.observation.cause.getException match {
      case Some(carrier: GeneratedLogicalUiAbi.AdmissionException) => carrier.diagnostic
      case _ => fail("expected structured Logical UI admission diagnostic")
    }
  }
  private def _unpublished(component: Component): Unit = {
    component._admitted_logical_ui_definitions shouldBe None
    component.displayRuntime shouldBe None
    component.protocol.services.services.exists(_.name == "display") shouldBe false
    component.services.services.exists(_.serviceDefinition.name == "display") shouldBe false
  }
  private def _unread(component: ActivationComponent): Unit = {
    component.metadataReads shouldBe 0
    component.runtimeReads shouldBe 0
    component.serviceReads shouldBe 0
    component.mutationReads shouldBe 0
    component.businessReads shouldBe 0
    component.installed shouldBe None
  }
}

object ComponentFactoryLogicalUiActivationSpec {
  final class ActivationComponent(activation: () => Boolean,
    definitions: () => Vector[GeneratedLogicalUiAbi.Definition], throwBindings: Boolean)
      extends GeneratedDisplayConsumerFixture.ConsumerComponent(Vector.empty, false)
      with GeneratedLogicalUiMetadataProvider with DisplayMutationServiceBindingProvider
      with DisplayBusinessOperationBindingProvider {
    private val _activation = activation
    private val _definitions = definitions
    private val _throw_bindings = throwBindings
    private var _activation_reads = 0
    private var _runtime_reads = 0
    private var _service_reads = 0
    private var _mutation_reads = 0
    private var _business_reads = 0
    def activationReads: Int = _activation_reads
    def runtimeReads: Int = _runtime_reads
    def serviceReads: Int = _service_reads
    def mutationReads: Int = _mutation_reads
    def businessReads: Int = _business_reads
    override def generatedLogicalUiEnabled: Boolean = { _activation_reads += 1; _activation() }
    def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition] = {
      recordMetadataRead()
      _definitions()
    }
    private def _guard(): Unit = if (_throw_bindings) throw new IllegalStateException("unused Display binding")
    override def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _runtime_reads += 1
      _guard()
      super.displayBindings(component)
    }
    override def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
      _service_reads += 1
      _guard()
      super.displayServiceBindings(component)
    }
    def displayMutationBindings(component: Component): Vector[DisplayMutationRuntime.Binding] = {
      _mutation_reads += 1
      _guard()
      Vector.empty
    }
    def displayBusinessOperationBindings(component: Component): Vector[DisplayBusinessOperation.Binding] = {
      _business_reads += 1
      _guard()
      Vector.empty
    }
  }

  final class TrackingFactory extends Component.Factory with GeneratedLogicalUiMetadataProvider
      with DisplayBindingProvider with DisplayServiceBindingProvider {
    private var _reads = 0
    def reads: Int = _reads
    override def generatedLogicalUiEnabled: Boolean = { _reads += 1; true }
    def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition] = {
      _reads += 1
      DomainLogicalUiDefinitions.definitions
    }
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _reads += 1
      TypedDisplayRuntimeFixture.installBindings(component)
    }
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
      _reads += 1
      DisplayServiceReadFixture.resourceBindings
    }
    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("fixture uses initialized components")
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      throw new UnsupportedOperationException("fixture uses initialized component cores")
  }
}
