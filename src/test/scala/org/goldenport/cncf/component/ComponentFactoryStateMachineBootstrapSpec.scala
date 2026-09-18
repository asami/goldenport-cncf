package org.goldenport.cncf.component

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.statemachine.{CmlNormalizedStateMachine, CmlStateMachineDefinition, CmlStateMachineDefinitionProvider, CmlStateMachineIdentity, CmlStateMachineStateDefinition, CmlStateMachineStateIdentity, CmlStateMachineStateKind, CmlStateMachineStatePath, CmlStateMachineTransitionIdentity, CmlStateMachineTransitionTarget, CmlStateMachineTriggerIdentity, CmlStateMachineVersion, CmlTransitionBinding, CollectionTransitionRule, CollectionTransitionRuleProvider, ExecutionPlan, ResolvedAction, TransitionEvent, TransitionTrigger}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 *  version Aug. 14, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryStateMachineBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("sys", "sys", "default")

  "ComponentFactory bootstrap" should {
    "register component transition rules into runtime planner provider" in {
      Given("a component with one update transition rule")
      val trace = ArrayBuffer.empty[String]
      val component = _component_with_transition_rules(trace)
      val factory = new ComponentFactory()

      When("the component is bootstrapped through the public consequence boundary")
      val bootstrapped =
        factory
          .bootstrapC(component)
          .toOption
          .getOrElse(fail("component bootstrap should succeed"))
      val executioncontext = bootstrapped.logic.executionContext()
      given org.goldenport.cncf.context.ExecutionContext = executioncontext
      given EntityPersistent[SpecEntity] = _entity_persistent
      val entity = SpecEntity(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "bootstrap_1", _cid, entropy = "bootstrap_1"), "taro")
      val selectedplan = component.stateMachinePlannerProvider
        .planForUpdate(entity, _entity_persistent, TransitionEvent("update", Some(entity.id)))
        .TAKE
        .getOrElse(fail("the component rule must be registered for update planning"))

      val transitionresult =
        executioncontext.runtime.transitionValidationHook.beforeUpdate(entity, _entity_persistent)

      Then("bootstrap installs and executes the transition validation hook")
      selectedplan.selectedTransitionBinding shouldBe Some(_binding)
      transitionresult shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition", "entry")
      component match {
        case provider: CollectionTransitionRuleProvider =>
          provider.stateMachineTransitionRules.head.historyFieldName shouldBe Some("lifecycleHistory")
        case _ =>
          fail("component should expose the transition rule provider contract")
      }
      bootstrapped.stateMachineDefinitions shouldBe Vector(_normalized_definition)
    }

    "copy factory-provided state machine definitions when the component does not provide them" in {
      Given("a component without definitions and a factory providing one typed definition")
      val factorydefinition = _normalized_definition.copy(
        name = "factory-owned",
        normalized = None
      )
      val componentfactory = new StateMachineDefinitionFactory(Vector(factorydefinition))
      val component = _initialized_component(new Component() {}, componentfactory)
      val factory = new ComponentFactory()

      When("the component is bootstrapped through the public consequence boundary")
      val bootstrapped =
        factory
          .bootstrapC(component)
          .toOption
          .getOrElse(fail("component bootstrap should succeed"))

      Then("bootstrap copies the factory definition into the component")
      bootstrapped.stateMachineDefinitions shouldBe Vector(factorydefinition)
    }

    "prefer component-provided state machine definitions over factory definitions" in {
      Given("a component and its factory providing different typed definition vectors")
      val componentdefinition = _normalized_definition.copy(
        name = "component-owned",
        normalized = None
      )
      val factorydefinition = _normalized_definition.copy(
        name = "factory-owned",
        normalized = None
      )
      val componentfactory = new StateMachineDefinitionFactory(Vector(factorydefinition))
      val component = new Component() with CmlStateMachineDefinitionProvider {
        override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
          Vector(componentdefinition)
      }
      val initialized = _initialized_component(component, componentfactory)
      val factory = new ComponentFactory()

      When("the component is bootstrapped through the public consequence boundary")
      val bootstrapped =
        factory
          .bootstrapC(initialized)
          .toOption
          .getOrElse(fail("component bootstrap should succeed"))

      Then("bootstrap keeps the component definition")
      bootstrapped.stateMachineDefinitions shouldBe Vector(componentdefinition)
    }
  }

  private def _component_with_transition_rules(
    trace: ArrayBuffer[String]
  ): Component = {
    val component = new Component() with CollectionTransitionRuleProvider with CmlStateMachineDefinitionProvider {
      override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
        Vector(
          CollectionTransitionRule[Any](
            collectionName = "default",
            trigger = TransitionTrigger.Update,
            eventName = "update",
            priority = 1,
            declarationOrder = 0,
            guard = None,
            plan = ExecutionPlan[Any, TransitionEvent](
              exitActions = Vector(_record_action("exit", trace)),
              transitionAction = Some(_record_action("transition", trace)),
              entryActions = Vector(_record_action("entry", trace))
            ),
            historyFieldName = Some("lifecycleHistory"),
            expectedHistoryRecordWrites = Vector(org.goldenport.cncf.statemachine.HistoryRecordWrite("Review", "Draft")),
            binding = Some(_binding)
          )
        )

      override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
        Vector(_normalized_definition)
    }

    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.StateMachineBootstrapSpec",
      componentId = ComponentId("org.goldenport.cncf.test.StateMachineBootstrapSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.StateMachineBootstrapSpec")),
      protocol = Protocol.empty
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("state_machine_bootstrap_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private final class StateMachineDefinitionFactory(
    definitions: Vector[CmlStateMachineDefinition]
  ) extends Component.Factory
    with CmlStateMachineDefinitionProvider {
    override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] = definitions

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("test fixture factory does not create components")

    override protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      throw new UnsupportedOperationException("test fixture factory does not create component cores")
  }

  private def _normalized_definition: CmlStateMachineDefinition = {
    val machine = CmlStateMachineIdentity("lifecycle")
    val state = CmlStateMachineStateIdentity(
      machine,
      CmlStateMachineStatePath(Vector("Draft"))
    )
    CmlStateMachineDefinition(
      name = "lifecycle",
      normalized = Some(CmlNormalizedStateMachine(
        identity = machine,
        version = CmlStateMachineVersion(1),
        initialState = state,
        states = Vector(CmlStateMachineStateDefinition(
          identity = state,
          kind = CmlStateMachineStateKind.Leaf
        )),
        transitions = Vector.empty,
        terminalTransitions = Vector.empty
      ))
    )
  }

  private val _binding: CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("lifecycle")
    val source = CmlStateMachineStateIdentity(
      machine,
      CmlStateMachineStatePath(Vector("Draft"))
    )
    val target = CmlStateMachineStateIdentity(
      machine,
      CmlStateMachineStatePath(Vector("Approved"))
    )
    CmlTransitionBinding(
      componentId = ComponentId("org.goldenport.cncf.test.StateMachineBootstrapSpec"),
      entityType = _cid,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = CmlStateMachineTransitionTarget.State(target),
      trigger = CmlStateMachineTriggerIdentity(machine, "update")
    )
  }

  private def _initialized_component(
    component: Component,
    componentFactory: Component.Factory
  ): Component = {
    val core = Component.Core.create(
      name = "org.goldenport.cncf.test.StateMachineBootstrapSpec",
      componentId = ComponentId("org.goldenport.cncf.test.StateMachineBootstrapSpec"),
      instanceId = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.StateMachineBootstrapSpec")),
      protocol = Protocol.empty,
      factory = componentFactory
    )
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("state_machine_bootstrap_spec"),
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _record_action(
    label: String,
    trace: ArrayBuffer[String]
  ): ResolvedAction[Any, TransitionEvent] =
    new ResolvedAction[Any, TransitionEvent] {
      override def run(state: Any, event: TransitionEvent): Consequence[Unit] = {
        val _ = (state, event)
        trace += label
        Consequence.unit
      }
    }

  private final case class SpecEntity(
    id: EntityId,
    name: String
  ) {
    def toRecord(): Record =
      Record.dataAuto("id" -> id, "name" -> name)
  }

  private val _entity_persistent: EntityPersistent[SpecEntity] = new EntityPersistent[SpecEntity] {
    override def id(e: SpecEntity): EntityId = e.id
    override def toRecord(e: SpecEntity): Record = e.toRecord()
    override def fromRecord(r: Record): Consequence[SpecEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(SpecEntity(id, name))
        case _ =>
          Consequence.argumentInvalid("invalid entity record")
      }
    }
  }
}
