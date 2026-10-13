package org.goldenport.cncf.component

import cats.data.State
import cats.effect.Ref
import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture, TypedDisplayRuntimeFixture}
import logicalui.TypedDisplayRuntimeFixture.Installed
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.display.{Display, DisplayBindingProvider, DisplayModel, DisplayRuntime, DisplayService, DisplayServiceBindingProvider, GeneratedLogicalUiAbi, GeneratedLogicalUiMetadataProvider}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.ViewDefinition
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{GeneratedWorkflowAbi, GeneratedWorkflowAbiAdmissionException, GeneratedWorkflowMetadataProvider}
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  5, 2026
 */
final class ComponentFactoryDisplayRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*

  private val _fixture = TypedDisplayProjectionFixture
  private val _runtime_fixture = TypedDisplayRuntimeFixture
  private val _definitions = _fixture.definitions
  private val _entity_name = "view_bootstrap_person"

  private class FixtureFactory extends Component.Factory {
    override protected def create_Component(params: ComponentCreate): Component = {
      throw new UnsupportedOperationException("fixture factory does not create components")
    }
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
      throw new UnsupportedOperationException("fixture factory does not create cores")
    }
  }
  private final class RuntimeFactory(readBindings: Component => Vector[DisplayRuntime.Binding])
    extends FixtureFactory with DisplayBindingProvider with DisplayServiceBindingProvider {
    private val _read_bindings = readBindings
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = _read_bindings(component)
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
      DisplayServiceReadFixture.resourceBindings
  }
  private final class CombinedFactory(readBindings: Component => Vector[DisplayRuntime.Binding])
    extends FixtureFactory with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
    private val _read_bindings = readBindings
    def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = _read_bindings(component)
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
      DisplayServiceReadFixture.resourceBindings
  }

  private def _with_subsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("display-runtime-bootstrap")
    try body(subsystem) finally subsystem.shutdown()
  }
  private def _initialize(subsystem: Subsystem, component: Component,
    factory: Option[Component.Factory] = None): Component = {
    val componentid = ComponentId("org.goldenport.cncf.component.DisplayRuntimeBootstrapSpec")
    val core = factory match {
      case Some(selected) => Component.Core.create(name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty, factory = selected)
      case None => Component.Core.create(name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty)
    }
    component.initialize(ComponentInit(subsystem = subsystem, core = core, origin = ComponentOrigin.Builtin))
  }
  private def _metadata_component: Component = {
    new Component() with GeneratedLogicalUiMetadataProvider {
      def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
    }
  }
  private def _provider_component(read: Component => Vector[DisplayRuntime.Binding]): Component = {
    new Component() with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
      def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
      def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = read(component)
      def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
        DisplayServiceReadFixture.resourceBindings
    }
  }
  private def _success[A](result: Consequence[A]): A = {
    result match {
      case Consequence.Success(value) => value
      case _ => fail("fixture bootstrap or contextual request must succeed")
    }
  }
  private def _runtime(component: Component): DisplayRuntime.Registry = {
    component.displayRuntime.getOrElse(fail("successful metadata bootstrap must publish complete runtime"))
  }
  private def _definitions_of(component: Component): AdmittedDefinitions = {
    component._admitted_logical_ui_definitions.getOrElse(fail("successful bootstrap must retain admitted definitions"))
  }
  private def _detail(display: Display): DisplayModel.DetailModel = {
    display.model match {
      case model: DisplayModel.DetailModel => model
      case _ => fail("expected contextual Detail")
    }
  }
  private def _list(display: Display): DisplayModel.ListModel = {
    display.model match {
      case model: DisplayModel.ListModel => model
      case _ => fail("expected contextual List")
    }
  }
  private def _unpublished(component: Component): Unit = {
    component.displayRuntime shouldBe None
    component._admitted_logical_ui_definitions shouldBe None
    component.collectionsBootstrapped shouldBe false
  }
  private def _failure(result: Consequence[?]): Consequence.Failure[?] = {
    result match {
      case rejected: Consequence.Failure[?] => rejected
      case _ => fail("expected structured bootstrap failure")
    }
  }
  private def _expect(result: Consequence[?], code: String, path: String): Unit = {
    val rejected = _failure(result)
    rejected.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    rejected.conclusion.observation.cause.getException match {
      case Some(carrier: DisplayRuntime.BindingException) =>
        carrier.diagnostic.code shouldBe "DSP96_RUNTIME_" + code
        carrier.diagnostic.path shouldBe path
        carrier.diagnostic.reason should not be empty
      case _ => fail("expected typed runtime diagnostic")
    }
  }
  private def _installed(value: Option[Installed]): Installed = {
    value.getOrElse(fail("selected provider must have installed its typed source"))
  }

  "ComponentFactory contextual Display publication" should {
    "late semantic binding" which {
      "B1 run the provider after actual factory-created Views and publish only its complete contextual runtime" in {
        Given("declared ViewDefinition and an existing typed ViewBootstrapPerson EntityCollection plus late ESS registrations")
        _with_subsystem { subsystem =>
          given EntityPersistent[PersonEntity] = entity.ViewBootstrapPerson.entityPersistent
          given ExecutionContext = ExecutionContext.create()
          val source = _success(_fixture.captureC(1, 7))
          val personid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("m", "a",
            entity.ViewBootstrapPerson.collectionId, entropy = "late_view_person")
          val person = PersonEntity(personid, "taro", "Tokyo")
          val collection = _person_collection(Vector(person))
          var reads = 0
          var duplicateblocked = false
          var beforepublication = false
          var installed: Option[Installed] = None
          val component = _initialize(subsystem, new Component() with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
            def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
            def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
              DisplayServiceReadFixture.resourceBindings
            override def viewDefinitions: Vector[ViewDefinition] =
              Vector(ViewDefinition("person-view", _entity_name))
            def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
              reads += 1
              beforepublication = component.displayRuntime.isEmpty &&
                component._admitted_logical_ui_definitions.isEmpty && !component.collectionsBootstrapped
              val probe = _runtime_fixture.captureBrowser(Vector(source))
              try {
                component.viewSpace.register("person-view", _runtime_fixture.collection(probe), probe)
              } catch {
                case _: IllegalStateException => duplicateblocked = true
              }
              val selected = _runtime_fixture.install(component, Vector(source), "ess-after-semantic-view", named = true)
              installed = Some(selected)
              selected.bindings
            }
          })
          component.entitySpace.registerEntity(_entity_name, collection)
          When("normal bootstrap creates the semantic View then reads the binding provider")
          val result = new ComponentFactory().bootstrapC(component)
          Then("duplicate registration proves the declared default View already exists before any Display state is published")
          (_success(result) eq component) shouldBe true
          reads shouldBe 1
          duplicateblocked shouldBe true
          beforepublication shouldBe true
          component.entitySpace.entityCollectionsByName(_entity_name).exists(_ eq collection) shouldBe true
          _runtime(component).screenIds shouldBe _definitions.map(_.screenId)
          (_definitions_of(component).definitions eq _definitions) shouldBe true
          component.collectionsBootstrapped shouldBe true
          val selected = _installed(installed)
          selected.browser.findCalls shouldBe empty
          selected.browser.queryCalls shouldBe empty
          selected.browser.contextFreeCalls shouldBe 0
          selected.browser.countCalls shouldBe 0
          When("the published runtime is queried and its resource is separately read with a caller context")
          val list = _list(_success(_runtime(component).queryC("collecting-resources", Query("all"))))
          val detail = _detail(_success(_runtime(component).readC("capture-detail", source.resource.entityId)))
          Then("both contextual semantic paths preserve the ESS resource and values without count or eager Detail")
          list.items.map(_.resource) shouldBe Vector(source.resource)
          list.snapshot shouldBe None
          detail.resource shouldBe source.resource
          detail.sections.head.fields.head.value shouldBe org.goldenport.cncf.display.DisplayValue.Text(source.title)
          selected.browser.queryCalls.size shouldBe 1
          selected.browser.findCalls.size shouldBe 1
          selected.browser.queryCalls.head._2 shouldBe summon[ExecutionContext]
          selected.browser.contextFreeCalls shouldBe 0
          selected.browser.countCalls shouldBe 0
        }
      }

      "B2 ignore throwing runtime capabilities when metadata is absent" in {
        Given("legacy components with a throwing runtime provider at either component or factory location but no metadata")
        _with_subsystem { subsystem =>
          Vector(true, false).foreach { componentowned =>
            var reads = 0
            val selected: Component => Vector[DisplayRuntime.Binding] = _ => {
              reads += 1
              throw new IllegalStateException("unused runtime getter")
            }
            val component = if (componentowned) {
              _initialize(subsystem, new Component() with DisplayBindingProvider {
                def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = selected(component)
              })
            } else _initialize(subsystem, new Component() {}, Some(new RuntimeFactory(selected)))
            val services = component.services
            When("normal bootstrap interprets a component without static Logical UI definitions")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the getter is never read and ordinary bootstrap retains services with no Display runtime")
            (_success(result) eq component) shouldBe true
            reads shouldBe 0
            component.displayRuntime shouldBe None
            component._admitted_logical_ui_definitions shouldBe None
            component.collectionsBootstrapped shouldBe true
            (component.services eq services) shouldBe true
          }
        }
      }

      "B2 fail closed when valid metadata lacks a runtime provider or complete bindings" in {
        Given("valid ESS metadata with either no binding capability or an empty selected runtime vector")
        _with_subsystem { subsystem =>
          val missing = _initialize(subsystem, _metadata_component)
          var reads = 0
          val emptybindings = _initialize(subsystem, _provider_component(_ => { reads += 1; Vector.empty }))
          When("normal bootstrap reaches the late typed binding gate")
          val missingresult = new ComponentFactory().bootstrapC(missing)
          val emptyresult = new ComponentFactory().bootstrapC(emptybindings)
          Then("missing provider and first missing screen diagnostics expose no definitions runtime or flag")
          _expect(missingresult, "PROVIDER_MISSING", "bindings")
          _expect(emptyresult, "SCREEN_MISSING", "definitions[0].screenId")
          reads shouldBe 1
          _unpublished(missing)
          _unpublished(emptybindings)
        }
      }
    }

    "single runtime provider selection" which {
      "B3 prefer the component runtime provider and use the factory only when that capability is absent" in {
        Given("independent static and runtime providers with selected and shadowed getter counters")
        _with_subsystem { subsystem =>
          var componentreads = 0
          var shadowedreads = 0
          var factoryreads = 0
          val component = _initialize(subsystem, _provider_component(selected => {
            componentreads += 1
            _runtime_fixture.installBindings(selected)
          }), Some(new CombinedFactory(selected => {
            shadowedreads += 1
            _runtime_fixture.installBindings(selected)
          })))
          val factoryonly = _initialize(subsystem, _metadata_component, Some(new RuntimeFactory(selected => {
            factoryreads += 1
            _runtime_fixture.installBindings(selected)
          })))
          val staticfactory = _initialize(subsystem, new Component() with DisplayBindingProvider {
            def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
              componentreads += 1
              _runtime_fixture.installBindings(component)
            }
          }, Some(new CombinedFactory(selected => { shadowedreads += 1; _runtime_fixture.installBindings(selected) })))
          When("normal bootstrap selects each component's independent runtime capability")
          val first = new ComponentFactory().bootstrapC(component)
          val second = new ComponentFactory().bootstrapC(factoryonly)
          val third = new ComponentFactory().bootstrapC(staticfactory)
          Then("each chosen getter runs exactly once and a shadowed valid factory cannot concatenate registrations")
          Vector(first, second, third).foreach(_.isSuccess shouldBe true)
          componentreads shouldBe 2
          factoryreads shouldBe 1
          shadowedreads shouldBe 0
          Vector(component, factoryonly, staticfactory).foreach { selected =>
            _runtime(selected).screenIds shouldBe _definitions.map(_.screenId)
            (_definitions_of(selected).definitions eq _definitions) shouldBe true
            selected.collectionsBootstrapped shouldBe true
          }
        }
      }

      "B3 never rescue an invalid null or throwing selected runtime provider with a valid factory" in {
        Given("component-first runtime providers returning incomplete null or throwing results plus valid shadowed factories")
        _with_subsystem { subsystem =>
          Vector("empty", "null", "throwing").foreach { kind =>
            Given(s"the selected $kind runtime getter and a different valid factory getter")
            var selectedreads = 0
            var fallbackreads = 0
            val component = _initialize(subsystem, _provider_component(_ => {
              selectedreads += 1
              kind match {
                case "empty" => Vector.empty
                case "null" => null
                case _ => throw new IllegalStateException("private provider source payload")
              }
            }), Some(new CombinedFactory(selected => { fallbackreads += 1; _runtime_fixture.installBindings(selected) })))
            When("normal bootstrap reads the selected runtime provider after legacy semantic setup")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the selected failure is structured and redacted without fallback or partial publication")
            val expected = kind match {
              case "empty" => ("SCREEN_MISSING", "definitions[0].screenId")
              case "null" => ("ELEMENT_INVALID", "bindings")
              case _ => ("PROVIDER_FAILED", "bindings")
            }
            _expect(result, expected._1, expected._2)
            _failure(result).conclusion.observation.cause.getException.foreach { exception =>
              exception.getMessage should not include "private provider source payload"
            }
            selectedreads shouldBe 1
            fallbackreads shouldBe 0
            _unpublished(component)
          }
        }
      }

      "B3 retain factory-only null invalid and throwing provider failures after one read" in {
        Given("static component metadata and failing selected factory-only runtime providers")
        _with_subsystem { subsystem =>
          Vector("empty", "null", "throwing").foreach { kind =>
            var reads = 0
            val component = _initialize(subsystem, _metadata_component, Some(new RuntimeFactory(_ => {
              reads += 1
              kind match {
                case "empty" => Vector.empty
                case "null" => null
                case _ => throw new IllegalArgumentException("private factory failure")
              }
            })))
            When("normal bootstrap selects the only runtime provider")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the factory getter is evaluated once and its failure cannot publish a complete runtime")
            val expected = kind match {
              case "empty" => ("SCREEN_MISSING", "definitions[0].screenId")
              case "null" => ("ELEMENT_INVALID", "bindings")
              case _ => ("PROVIDER_FAILED", "bindings")
            }
            _expect(result, expected._1, expected._2)
            reads shouldBe 1
            _unpublished(component)
          }
        }
      }
    }

    "publication failure and instance lifetime" which {
      "B4 reject an invalid last binding without publishing its valid prefix or executing sources" in {
        Given("a provider registering its typed handle but returning a valid List followed by an incompatible Detail key")
        _with_subsystem { subsystem =>
          val source = _success(_fixture.captureC(1, 7))
          var installed: Option[Installed] = None
          var reads = 0
          val component = _initialize(subsystem, _provider_component(selected => {
            reads += 1
            val value = _runtime_fixture.install(selected, Vector(source))
            installed = Some(value)
            Vector(value.bindings.head, DisplayRuntime.detail("capture-detail", "wrong-key", value.browser, _fixture.detailRow))
          }))
          When("normal bootstrap compiles the full runtime after successful ordinary setup")
          val result = new ComponentFactory().bootstrapC(component)
          Then("B1's incompatible key diagnostic survives with no Display publication while legacy registrations may remain")
          _failure(result).conclusion.observation.cause.getException match {
            case Some(carrier: org.goldenport.cncf.display.DisplayProjection.ProjectionException) =>
              carrier.diagnostic.code shouldBe "DSP96_PROJECTION_VIEW_BINDING_MISMATCH"
              carrier.diagnostic.path shouldBe "viewBindingId"
            case _ => fail("expected preserved B1 binding diagnostic")
          }
          _unpublished(component)
          reads shouldBe 1
          val value = _installed(installed)
          component.viewSpace.containsBrowser(value.browser) shouldBe true
          value.browser.findCalls shouldBe empty
          value.browser.queryCalls shouldBe empty
          value.browser.contextFreeCalls shouldBe 0
          value.browser.countCalls shouldBe 0
        }
      }

      "B4 retain the earlier Workflow gate without reading the late binding provider" in {
        Given("valid ESS metadata preceding an empty invalid generated Workflow vector and a counted runtime getter")
        _with_subsystem { subsystem =>
          var reads = 0
          val component = _initialize(subsystem, new Component() with GeneratedLogicalUiMetadataProvider
            with GeneratedWorkflowMetadataProvider with DisplayBindingProvider {
            def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
            override def generatedWorkflowDefinitions: Vector[GeneratedWorkflowAbi.Definition] = Vector.empty
            def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
              reads += 1
              _runtime_fixture.installBindings(component)
            }
          })
          When("normal bootstrap interprets the unchanged earlier Workflow admission gate")
          val result = new ComponentFactory().bootstrapC(component)
          val direct = GeneratedWorkflowAbi.admitC(Vector.empty)
          Then("the earlier diagnostic prevents runtime provider evaluation and all Display publication")
          _failure(result).conclusion.observation.taxonomy shouldBe _failure(direct).conclusion.observation.taxonomy
          val actualdiagnostic = _failure(result).conclusion.observation.cause.getException.collect {
            case carrier: GeneratedWorkflowAbiAdmissionException => carrier.diagnostic
          }
          val expecteddiagnostic = _failure(direct).conclusion.observation.cause.getException.collect {
            case carrier: GeneratedWorkflowAbiAdmissionException => carrier.diagnostic
          }
          actualdiagnostic shouldBe defined
          actualdiagnostic shouldBe expecteddiagnostic
          reads shouldBe 0
          _unpublished(component)
        }
      }

      "B5 repeat bootstrap with the same objects and isolate identical screen IDs and foreign handles across components" in {
        Given("two component-owned contextual sources with equal screen IDs distinct values and revisions plus a foreign-handle candidate")
        _with_subsystem { subsystem =>
          given ExecutionContext = ExecutionContext.create()
          val firstsource = _success(_fixture.captureC(1, 7)).copy(title = "first component")
          val secondsource = _success(_fixture.captureC(1, 19)).copy(title = "second component")
          var firstreads = 0
          var secondreads = 0
          var firstinstalled: Option[Installed] = None
          var secondinstalled: Option[Installed] = None
          val first = _initialize(subsystem, _provider_component(selected => {
            firstreads += 1
            val value = _runtime_fixture.install(selected, Vector(firstsource))
            firstinstalled = Some(value)
            value.bindings
          }))
          val second = _initialize(subsystem, _provider_component(selected => {
            secondreads += 1
            val value = _runtime_fixture.install(selected, Vector(secondsource))
            secondinstalled = Some(value)
            value.bindings
          }))
          val factory = new ComponentFactory()
          When("normal bootstrap first admits both complete component-local registries")
          val firstresult = factory.bootstrapC(first)
          val secondresult = factory.bootstrapC(second)
          Then("both instances publish separate complete objects without acquiring their sources")
          (_success(firstresult) eq first) shouldBe true
          (_success(secondresult) eq second) shouldBe true
          val firstruntime = _runtime(first)
          val secondruntime = _runtime(second)
          val firstdefinitions = _definitions_of(first)
          val storedruntime = first.displayRuntime
          val storeddefinitions = first._admitted_logical_ui_definitions
          (firstruntime eq secondruntime) shouldBe false
          _installed(firstinstalled).browser.findCalls shouldBe empty
          _installed(secondinstalled).browser.findCalls shouldBe empty
          Given("a third component supplies the first component's handle without registering it in its own ViewSpace")
          val foreign = _initialize(subsystem, _provider_component(_ => _installed(firstinstalled).bindings))
          When("bootstrap is repeated and a third component tries to borrow the first component's unregistered handle")
          val repeated = factory.bootstrapC(first)
          val rejected = factory.bootstrapC(foreign)
          val firstmodel = _detail(_success(firstruntime.readC("capture-detail", firstsource.resource.entityId)))
          val secondmodel = _detail(_success(secondruntime.readC("capture-detail", secondsource.resource.entityId)))
          Then("repeat retains Option registry and definition identity with no getter reread and failure cannot alter prior components")
          (_success(repeated) eq first) shouldBe true
          (first.displayRuntime eq storedruntime) shouldBe true
          (first._admitted_logical_ui_definitions eq storeddefinitions) shouldBe true
          (_runtime(first) eq firstruntime) shouldBe true
          (_definitions_of(first) eq firstdefinitions) shouldBe true
          firstreads shouldBe 1
          secondreads shouldBe 1
          _expect(rejected, "SOURCE_NOT_REGISTERED", "bindings[0].browser")
          _unpublished(foreign)
          firstmodel.resource shouldBe firstsource.resource
          secondmodel.resource shouldBe secondsource.resource
          firstmodel.sections.head.fields.head.value shouldBe org.goldenport.cncf.display.DisplayValue.Text(firstsource.title)
          secondmodel.sections.head.fields.head.value shouldBe org.goldenport.cncf.display.DisplayValue.Text(secondsource.title)
          _installed(firstinstalled).browser.findCalls.size shouldBe 1
          _installed(secondinstalled).browser.findCalls.size shouldBe 1
          (_runtime(second) eq secondruntime) shouldBe true
        }
      }
    }
  }

  private def _person_collection(entities: Vector[PersonEntity])
    (using EntityPersistent[PersonEntity]): EntityCollection[PersonEntity] = {
    val storerealm = new EntityRealm[PersonEntity](entityName = _entity_name,
      loader = EntityLoader[PersonEntity](id => entities.find(_.id == id)),
      state = new RuntimeRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty)))
    val memoryrealm = new PartitionedMemoryRealm[PersonEntity](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id)
    val descriptor = EntityDescriptor(collectionId = entity.ViewBootstrapPerson.collectionId,
      plan = EntityRuntimePlan(entityName = _entity_name, memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None, partitionStrategy = PartitionStrategy.byOrganizationMonthUTC, maxPartitions = 4, maxEntitiesPerPartition = 16),
      persistent = summon[EntityPersistent[PersonEntity]])
    val collection = new EntityCollection[PersonEntity](descriptor, EntityStorage(storerealm, Some(memoryrealm)))
    entities.foreach { value =>
      collection.storage.storeRealm.put(value)
      collection.storage.memoryRealm.foreach(_.put(value))
    }
    collection
  }

  private final class RuntimeRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = _value
    def set(a: A): Unit = { _value = a }
    override def getAndSet(a: A): A = {
      val previous = _value
      _value = a
      previous
    }
    def access: (A, A => Boolean) = {
      val snapshot = _value
      (snapshot, next => {
        if (_value == snapshot) { _value = next; true } else false
      })
    }
    override def tryUpdate(f: A => A): Boolean = { _value = f(_value); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = Some(modify(f))
    def update(f: A => A): Unit = { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = {
      val (next, result) = f(_value)
      _value = next
      result
    }
    override def modifyState[B](state: State[A, B]): B = {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }
    override def tryModifyState[B](state: State[A, B]): Option[B] = Some(modifyState(state))
  }
}
