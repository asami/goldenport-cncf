package org.goldenport.cncf.component

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.statemachine.{CollectionTransitionRule, CollectionTransitionRuleProvider, ExecutionPlan, ResolvedAction, TransitionEvent, TransitionTrigger}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryStateMachineBootstrapSpec
  extends AnyWordSpec
  with Matchers {

  private val _cid = EntityCollectionId("sys", "sys", "default")

  "ComponentFactory bootstrap" should {
    "register component transition rules into runtime planner provider" in {
      val trace = ArrayBuffer.empty[String]
      val component = _component_with_transition_rules(trace)
      val factory = new ComponentFactory()
      val bootstrapped = _bootstrap_collections(factory, component)
      val executioncontext = bootstrapped.logic.executionContext()
      given org.goldenport.cncf.context.ExecutionContext = executioncontext
      given EntityPersistent[_Entity] = _entity_persistent
      val entity = _Entity(EntityId("test", "bootstrap_1", _cid), "taro")

      val result =
        executioncontext.runtime.transitionValidationHook.beforeUpdate(entity, _entity_persistent)

      result shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition", "entry")
    }
  }

  private def _bootstrap_collections(
    factory: ComponentFactory,
    component: Component
  ): Component = {
    val method = classOf[ComponentFactory].getDeclaredMethod("_bootstrap_collections", classOf[Component])
    method.setAccessible(true)
    method.invoke(factory, component).asInstanceOf[Component]
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
      def run(state: Any, event: TransitionEvent): Consequence[Unit] = {
        val _ = (state, event)
        trace += label
        Consequence.unit
      }
    }

  private final case class _Entity(
    id: EntityId,
    name: String
  ) {
    def toRecord(): Record =
      Record.dataAuto("id" -> id, "name" -> name)
  }

  private val _entity_persistent: EntityPersistent[_Entity] = new EntityPersistent[_Entity] {
    def id(e: _Entity): EntityId = e.id
    def toRecord(e: _Entity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_Entity] = {
      val m = r.asMap
      (m.get("id"), m.get("name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(_Entity(id, name))
        case _ =>
          Consequence.argumentInvalid("invalid entity record")
      }
    }
  }
}
