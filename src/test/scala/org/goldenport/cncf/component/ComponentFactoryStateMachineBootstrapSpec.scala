package org.goldenport.cncf.component

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.statemachine.{CollectionTransitionRule, CollectionTransitionRuleProvider, ExecutionPlan, ResolvedAction, TransitionEvent, TransitionTrigger}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 * @version Jul. 25, 2026
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
      val entity = SpecEntity(EntityId("test", "bootstrap_1", _cid), "taro")

      val transitionresult =
        executioncontext.runtime.transitionValidationHook.beforeUpdate(entity, _entity_persistent)

      Then("bootstrap installs and executes the transition validation hook")
      transitionresult shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition", "entry")
    }
  }

  private def _component_with_transition_rules(
    trace: ArrayBuffer[String]
  ): Component = {
    val component = new Component() with CollectionTransitionRuleProvider {
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
            )
          )
        )
    }

    val core = Component.Core.create(
      name = "state_machine_bootstrap_spec",
      componentid = ComponentId("state_machine_bootstrap_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("state_machine_bootstrap_spec")),
      protocol = Protocol.empty
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
