package org.goldenport.cncf.component

import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture, TypedDisplayRuntimeFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.display.*
import org.goldenport.cncf.service.Service
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.service.{Service as ProtocolService}
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition, ServiceDefinition, ServiceDefinitionGroup}
import org.goldenport.schema.DataType
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class ComponentFactoryDisplayServiceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*

  private val _fixture = DisplayServiceReadFixture
  private val _typed = TypedDisplayProjectionFixture
  private val _bindings = DisplayServiceReadFixture.resourceBindings

  private class SourceComponent(definitions: Vector[Definition] = TypedDisplayProjectionFixture.definitions)
      extends Component() with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider {
    private val _definitions = definitions
    private var _metadata_reads = 0
    private var _runtime_reads = 0
    private var _installed: Option[TypedDisplayRuntimeFixture.Installed] = None
    def metadataReads: Int = _metadata_reads
    def runtimeReads: Int = _runtime_reads
    def installed: Option[TypedDisplayRuntimeFixture.Installed] = _installed
    def generatedLogicalUiDefinitions: Vector[Definition] = { _metadata_reads += 1; _definitions }
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _runtime_reads += 1
      if (_definitions.isEmpty) Vector.empty else {
        val installed = TypedDisplayRuntimeFixture.install(component, Vector(_typed.captureC(1, 7).TAKE))
        _installed = Some(installed)
        installed.bindings
      }
    }
  }
  private final class ServiceComponent(read: Component => Vector[DisplayService.ResourceBinding])
      extends SourceComponent() with DisplayServiceBindingProvider {
    private val _read = read
    private var _service_reads = 0
    def serviceReads: Int = _service_reads
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
      _service_reads += 1
      _read(component)
    }
  }
  private class FixtureFactory extends Component.Factory {
    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("fixture uses ordinary initialized components")
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      throw new UnsupportedOperationException("fixture uses ordinary initialized component cores")
  }
  private final class CapabilityFactory(read: Component => Vector[DisplayService.ResourceBinding])
      extends FixtureFactory with DisplayServiceBindingProvider {
    private val _read = read
    private var _reads = 0
    def reads: Int = _reads
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
      _reads += 1
      _read(component)
    }
  }

  "E1 Late complete service admission" should afterWord("in spec:display-model-runtime, example:E1, rules:SR1,SR2,SR4,DSP96-R03, phase:96, slice:P96-05A") {
    "select the component first or the absent-capability factory once after View/runtime preparation" in {
      Given("component-owned and factory-fallback bindings with a competing fallback and no published Display state")
      _with_subsystem { subsystem =>
        var componentlate = false
        var factorylate = false
        val fallback = new CapabilityFactory(_ => throw new IllegalStateException("unused fallback"))
        val selected = new ServiceComponent(component => {
          componentlate = _before_publication(component) && component.viewSpace.containsBrowser(_selected_browser(component))
          _bindings
        })
        _fixture.initialize(selected, subsystem, factory = Some(fallback))
        val factory = new CapabilityFactory(component => {
          factorylate = _before_publication(component) && component.viewSpace.containsBrowser(_selected_browser(component))
          _bindings
        })
        val absentcapability = new SourceComponent()
        _fixture.initialize(absentcapability, subsystem, name = "FactoryDisplayRead", factory = Some(factory))
        When("ordinary bootstrap prepares both complete component registrations")
        val results = Vector(selected, absentcapability).map(new ComponentFactory().bootstrapC)
        Then("only selected getters run and complete effective protocols/services become visible without evaluating their Browsers")
        results.foreach(_ shouldBe a[Consequence.Success[?]])
        componentlate shouldBe true
        factorylate shouldBe true
        selected.serviceReads shouldBe 1
        fallback.reads shouldBe 0
        factory.reads shouldBe 1
        Vector(selected, absentcapability).foreach { component =>
          component.metadataReads shouldBe 1
          component.runtimeReads shouldBe 1
          component.collectionsBootstrapped shouldBe true
          component.displayRuntime.get.screenIds shouldBe _typed.definitions.map(_.screenId)
          _display_definition(component).operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
          _display_definition(component).operations.operations.toVector.foreach { operation =>
            operation.response.isSpecified shouldBe true
            operation.response.isVoid shouldBe false
            operation.response.result shouldBe List(DataType.Named("OperationResponse"))
          }
          _display_definition(component).defaultOperation shouldBe None
          component.services.services.count(_.serviceDefinition.name == "display") shouldBe 1
          _no_source_calls(component)
        }
      }
    }
  }

  "E2 No service without admitted declarations" should afterWord("in spec:display-model-runtime, example:E2, rules:SR1,SR4, phase:96, slice:P96-05A") {
    "preserve legacy absence success and reject present-empty metadata before reading unused capabilities" in {
      _with_subsystem { subsystem =>
        Given("a legacy component without metadata whose service getter would throw")
        var legacyreads = 0
        val legacy = new Component() with DisplayServiceBindingProvider {
          def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
            legacyreads += 1
            throw new IllegalStateException("unused capability")
          }
        }
        _fixture.initialize(legacy, subsystem, name = "NoDisplayLegacy", protocol = _unrelated_protocol)
        val legacyprotocol = legacy.protocol
        val legacyservices = legacy.services
        val legacycore = legacy.core
        When("normal bootstrap sees no presentation metadata provider")
        val legacyresult = new ComponentFactory().bootstrapC(legacy)
        Then("legacy bootstrap succeeds without a display service or capability read and retains original references")
        legacyresult shouldBe a[Consequence.Success[?]]
        (legacy.protocol eq legacyprotocol) shouldBe true
        (legacy.protocol.handler eq legacyprotocol.handler) shouldBe true
        (legacy.services eq legacyservices) shouldBe true
        (legacy.core eq legacycore) shouldBe true
        legacy.displayRuntime shouldBe None
        legacy.protocol.services.services.exists(_.name == "display") shouldBe false
        legacy.collectionsBootstrapped shouldBe true
        legacyreads shouldBe 0
        Given("a present metadata provider with an empty definition vector whose service getter would throw")
        var emptyreads = 0
        val empty = new SourceComponent(Vector.empty) with DisplayServiceBindingProvider {
          def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
            emptyreads += 1
            throw new IllegalStateException("unused capability")
          }
        }
        _fixture.initialize(empty, subsystem, name = "NoDisplayEmpty", protocol = _unrelated_protocol)
        val emptyprotocol = empty.protocol
        val emptyservices = empty.services
        val emptycore = empty.core
        When("normal bootstrap statically admits the selected empty metadata vector")
        val emptyresult = new ComponentFactory().bootstrapC(empty)
        Then("typed definitions-missing failure precedes runtime/service getters and leaves publication and original references intact")
        emptyresult shouldBe a[Consequence.Failure[?]]
        emptyresult match {
          case failure: Consequence.Failure[?] =>
            failure.conclusion.observation.cause.getException match {
              case Some(carrier: GeneratedLogicalUiAbi.AdmissionException) =>
                carrier.diagnostic.code shouldBe "LUI96_DEFINITIONS_MISSING"
                carrier.diagnostic.path shouldBe "definitions"
              case _ => fail("expected typed LogicalUI admission failure")
            }
          case _ => fail("expected typed LogicalUI admission failure")
        }
        _unpublished(empty, emptyprotocol, emptyservices)
        (empty.core eq emptycore) shouldBe true
        (empty.protocol.handler eq emptyprotocol.handler) shouldBe true
        empty.metadataReads shouldBe 1
        empty.runtimeReads shouldBe 0
        emptyreads shouldBe 0
        empty.installed shouldBe None
        empty.protocol.services.services.exists(_.name == "display") shouldBe false
      }
    }
  }

  "E3 Fail-closed selected binding matrix" should afterWord("in spec:display-model-runtime, example:E3, rules:SR1,SR4, phase:96, slice:P96-05A") {
    "reject absent throwing null duplicate extra incomplete or incompatible mappings with deterministic redacted diagnostics" in {
      Given("complete admitted metadata/runtime and invalid selected service capabilities plus a valid competing fallback")
      _with_subsystem { subsystem =>
        val cases: Vector[(Component => Vector[DisplayService.ResourceBinding], String, String)] = Vector(
          (_ => throw new IllegalStateException("private provider detail"), "PROVIDER_FAILED", "bindings"),
          (_ => null, "ELEMENT_INVALID", "bindings"),
          (_ => Vector(null), "ELEMENT_INVALID", "bindings[0]"),
          (_ => Vector(_bindings.head.copy(screenId = null)), "ID_INVALID", "bindings[0].screenId"),
          (_ => Vector(_bindings.head.copy(resourceType = " type ")), "ID_INVALID", "bindings[0].resourceType"),
          (_ => Vector(_bindings.head.copy(resourceType = "")), "ID_INVALID", "bindings[0].resourceType"),
          (_ => Vector(_bindings.head, _bindings.head), "SCREEN_DUPLICATE", "bindings[1].screenId"),
          (_ => _bindings :+ DisplayService.ResourceBinding("type", "extra"), "SCREEN_UNDECLARED", "bindings[2].screenId"),
          (_ => Vector.empty, "SCREEN_MISSING", "definitions[0].screenId"),
          (_ => Vector(_bindings.head), "SCREEN_MISSING", "definitions[1].screenId"),
          (_ => Vector(_bindings.head, _bindings(1).copy(resourceType = "other")), "RESOURCE_MISMATCH", "definitions[0].screen.detailTarget.screenId"))
        cases.zipWithIndex.foreach { case ((read, code, path), index) =>
          val component = new ServiceComponent(read)
          val fallback = new CapabilityFactory(_ => _bindings)
          _fixture.initialize(component, subsystem, name = "InvalidService" + index, factory = Some(fallback))
          val protocol = component.protocol
          val services = component.services
          When("ordinary bootstrap admits the selected invalid mapping")
          val result = new ComponentFactory().bootstrapC(component)
          Then("its exact defect fails with no fallback source evaluation or presentation/protocol publication")
          _expect(result, code, path)
          component.serviceReads shouldBe 1
          fallback.reads shouldBe 0
          _unpublished(component, protocol, services)
          _no_source_calls(component)
          result.display should not include "private provider detail"
        }
        Given("the service capability is absent at both selection locations")
        val missing = new SourceComponent()
        _fixture.initialize(missing, subsystem, name = "MissingService")
        val protocol = missing.protocol
        val services = missing.services
        When("the normal runtime join succeeds but service capability selection has no candidate")
        val result = new ComponentFactory().bootstrapC(missing)
        Then("the structured missing-provider diagnostic leaves all Display publication absent")
        _expect(result, "PROVIDER_MISSING", "bindings")
        _unpublished(missing, protocol, services)
        _no_source_calls(missing)
        Given("ordinary service construction throws only while preparing the new display service")
        val brokenfactory = new FixtureFactory {
          override val serviceFactory: Component.ServiceFactory = new Component.ServiceFactory {
            def create(core: ProtocolService.Core, ccore: Service.CCore): Service = {
              if (core.serviceDefinition.name == "display") throw new IllegalStateException("private factory detail")
              else Service(core, ccore)
            }
          }
        }
        val broken = new ServiceComponent(_ => _bindings)
        _fixture.initialize(broken, subsystem, name = "BrokenServiceConstruction", factory = Some(brokenfactory))
        val originalprotocol = broken.protocol
        val originalservices = broken.services
        When("normal bootstrap prepares the new service instance before final publication")
        val rejected = new ComponentFactory().bootstrapC(broken)
        Then("a structured component failure retains original references and exposes no partial registration")
        rejected shouldBe a[Consequence.Failure[?]]
        _unpublished(broken, originalprotocol, originalservices)
        rejected.display should not include "private factory detail"
        rejected match {
          case failure: Consequence.Failure[?] =>
            failure.conclusion.observation.taxonomy shouldBe Taxonomy.componentInvalid
            failure.conclusion.observation.cause.getException shouldBe None
          case _ => fail("expected failed service construction")
        }
        rejected.display should include ("Display service construction failed")
        _no_source_calls(broken)
      }
    }
  }

  "E4 Existing display collision" should afterWord("in spec:display-model-runtime, example:E4, rules:SR1,SR4, phase:96, slice:P96-05A") {
    "reject normalized service identities and duplicate original operations rather than merging or replacing them" in {
      Given("original installed display services under canonical and normalized names, including duplicate operation definitions")
      _with_subsystem { subsystem =>
        Vector("display", " DISPLAY ").foreach { name => Vector(false, true).foreach { duplicate =>
          val service = _legacy_service(name)
          val original = if (duplicate) ServiceDefinition(name, service.operations.head, service.operations.head) else service
          val component = new ServiceComponent(_ => _bindings)
          _fixture.initialize(component, subsystem, name = "CollidingDisplay" + duplicate,
            protocol = Protocol(ServiceDefinitionGroup(Vector(original))))
          val protocol = component.protocol
          val services = component.services
          val logic = component.protocolLogic
          When("normal bootstrap reaches the effective registration collision gate")
          val result = new ComponentFactory().bootstrapC(component)
          val resolver = OperationResolver.build(Vector(component))
          Then("a fixed collision diagnostic leaves original definitions/instances/logic and no newly resolved read operation")
          _expect(result, "SERVICE_COLLISION", "service")
          _unpublished(component, protocol, services)
          (component.protocolLogic eq logic) shouldBe true
          protocol.services.services.exists(_ eq original) shouldBe true
          component.services.services.zip(services.services).foreach { case (actual, prior) => (actual eq prior) shouldBe true }
          resolver.resolveOperationDefinition(component.name + ".display.read") shouldBe None
          _no_source_calls(component)
        } }
      }
    }
  }

  "E5 Fixed Core and effective protocol" should afterWord("in spec:display-model-runtime, example:E5, rules:SR4,DSP96-R03, phase:96, slice:P96-05A") {
    "keep all fixed Core engines identity and unrelated service instances while every ordinary consumer sees display" in {
      Given("an initialized component with a fixed overridden Core and an unrelated original service/handler")
      _with_subsystem { subsystem =>
        val componentid = ComponentId("org.goldenport.cncf.test.FixedCoreDisplay")
        val core = Component.Core.create(componentid.name, componentid, ComponentInstanceId.default(componentid), _unrelated_protocol)
        var installed: Option[TypedDisplayRuntimeFixture.Installed] = None
        val component = new Component.Instance(core) with GeneratedLogicalUiMetadataProvider
            with DisplayBindingProvider with DisplayServiceBindingProvider {
          def generatedLogicalUiDefinitions: Vector[Definition] = _typed.definitions
          def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
            val value = TypedDisplayRuntimeFixture.install(component, Vector(_typed.captureC(1, 7).TAKE))
            installed = Some(value)
            value.bindings
          }
          def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = _bindings
        }
        component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
        val services = component.services.services
        val originalprotocol = component.protocol
        When("the normal factory appends a prepared immutable display registration without copying Core")
        val result = new ComponentFactory().bootstrapC(component)
        val resolver = OperationResolver.build(Vector(component))
        Then("Core/name/ids/factory/engines/subsystem remain exact while effective protocol logic services and resolver agree")
        result shouldBe a[Consequence.Success[?]]
        (component.core eq core) shouldBe true
        component.name shouldBe core.name
        component.componentId shouldBe core.componentId
        component.instanceId shouldBe core.instanceId
        component.factory shouldBe core.factory
        (component.actionEngine eq core.actionEngine) shouldBe true
        (component.jobEngine eq core.jobEngine) shouldBe true
        component.subsystem.exists(_ eq subsystem) shouldBe true
        (core.protocol eq originalprotocol) shouldBe true
        (component.protocol.handler eq originalprotocol.handler) shouldBe true
        component.protocol.services.services.dropRight(1) shouldBe originalprotocol.services.services
        component.services.services.dropRight(1).zip(services).foreach { case (actual, prior) => (actual eq prior) shouldBe true }
        Vector("query", "read").foreach { name =>
          val operation = resolver.resolveOperationDefinition(component.name + ".display." + name).get
          (_display_definition(component).operations.operations.find(_.name == name).get eq operation) shouldBe true
        }
        component.protocolLogic.makeOperationRequest(_fixture.request(component, "query", "collecting-resources")) shouldBe a[Consequence.Success[?]]
        installed.get.browser.findCalls shouldBe empty
        installed.get.browser.queryCalls shouldBe empty
      }
    }
  }

  "E6 Stable repeat and component-local ownership" should afterWord("in spec:display-model-runtime, example:E6, rules:SR1,SR3,SR4,DSP96-R03, phase:96, slice:P96-05A") {
    "retain published references without rereading providers or borrowing a foreign registered Browser" in {
      Given("a fully installed component and a second metadata owner presenting the first component's source bindings")
      _with_subsystem { subsystem =>
        val component = new ServiceComponent(_ => _bindings)
        _fixture.initialize(component, subsystem)
        val factory = new ComponentFactory()
        factory.bootstrapC(component).TAKE
        val protocol = component.protocol
        val logic = component.protocolLogic
        val services = component.services
        val runtime = component.displayRuntime
        val definitions = component._admitted_logical_ui_definitions
        val foreign = new Component() with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
          def generatedLogicalUiDefinitions: Vector[Definition] = _typed.definitions
          def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = _foreign_bindings
          def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = _bindings
          private val _foreign_bindings = component.installed.get.bindings
        }
        _fixture.initialize(foreign, subsystem, name = "ForeignRegistration")
        When("normal bootstrap repeats the accepted owner and attempts the foreign runtime join")
        val repeated = factory.bootstrapC(component)
        val rejected = factory.bootstrapC(foreign)
        Then("the repeat preserves every reference/provider count and the unregistered foreign Browser cannot be published")
        repeated shouldBe a[Consequence.Success[?]]
        rejected shouldBe a[Consequence.Failure[?]]
        (component.protocol eq protocol) shouldBe true
        (component.protocolLogic eq logic) shouldBe true
        (component.services eq services) shouldBe true
        (component.displayRuntime eq runtime) shouldBe true
        (component._admitted_logical_ui_definitions eq definitions) shouldBe true
        component.metadataReads shouldBe 1
        component.runtimeReads shouldBe 1
        component.serviceReads shouldBe 1
        foreign.displayRuntime shouldBe None
        foreign.collectionsBootstrapped shouldBe false
        component.entitySpace.entityCollectionsByName("display") shouldBe empty
        foreign.entitySpace.entityCollectionsByName("display") shouldBe empty
        _no_source_calls(component)
      }
    }
  }

  private def _with_subsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("display-service-registration")
    try body(subsystem) finally subsystem.shutdown()
  }
  private def _legacy_service(name: String): ServiceDefinition =
    ServiceDefinition(name, "echo", RequestDefinition(), ResponseDefinition(result = List(DataType.Named("OperationResponse"))))
  private def _unrelated_protocol: Protocol =
    Protocol(ServiceDefinitionGroup(Vector(_legacy_service("legacy"))), ProtocolHandler.default)
  private def _display_definition(component: Component): ServiceDefinition =
    component.protocol.services.services.find(_.name == "display").get
  private def _before_publication(component: Component): Boolean =
    component.displayRuntime.isEmpty && component._admitted_logical_ui_definitions.isEmpty &&
      !component.collectionsBootstrapped && !component.protocol.services.services.exists(_.name == "display")
  private def _selected_browser(component: Component): org.goldenport.cncf.entity.view.Browser[TypedDisplayProjectionFixture.Capture] =
    component.viewSpace.browserOption[TypedDisplayProjectionFixture.Capture]("ess-runtime-source").get
  private def _no_source_calls(component: SourceComponent): Unit = {
    val browser = component.installed.get.browser
    browser.findCalls shouldBe empty
    browser.queryCalls shouldBe empty
    browser.contextFreeCalls shouldBe 0
    browser.countCalls shouldBe 0
  }
  private def _unpublished(component: Component, protocol: Protocol, services: org.goldenport.cncf.service.ServiceGroup): Unit = {
    component.displayRuntime shouldBe None
    component._admitted_logical_ui_definitions shouldBe None
    component.collectionsBootstrapped shouldBe false
    (component.protocol eq protocol) shouldBe true
    (component.services eq services) shouldBe true
  }
  private def _expect(result: Consequence[?], code: String, path: String): Unit = result match {
    case failure: Consequence.Failure[?] =>
      failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
      failure.conclusion.observation.cause.getException match {
        case Some(carrier: DisplayService.ServiceException) =>
          carrier.diagnostic.code shouldBe "DSP96_SERVICE_" + code
          carrier.diagnostic.path shouldBe path
          carrier.diagnostic.reason should not be empty
        case _ => fail("expected typed service admission failure")
      }
    case _ => fail("expected failed service admission")
  }
}
