package org.goldenport.cncf.projection

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.goldenport.cncf.component._
import org.goldenport.cncf.statemachine._
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineProjectionSpec
  extends AnyWordSpec
  with Matchers {

  "StateMachineProjection" should {
    "project deterministic transitions and guard shape for component target" in {
      val component = _component_with_rules()
      val rec = StateMachineProjection.project(component, Some(component.name))
      val map = rec.asMap

      map.get("type") shouldBe Some("statemachine")
      map.get("targetType") shouldBe Some("component")
      map.get("name") shouldBe Some(component.name)

      val transitions = _records(map("transitions"))
      transitions.size shouldBe 2

      val first = transitions(0).asMap
      val second = transitions(1).asMap

      first.get("event") shouldBe Some("save")
      first.get("priority") shouldBe Some(1)
      _record(first("guard")).asMap.get("kind") shouldBe Some("ref")

      second.get("event") shouldBe Some("update")
      second.get("priority") shouldBe Some(2)
      _record(second("guard")).asMap.get("kind") shouldBe Some("expression")
    }

    "project state machine definitions into states/events" in {
      val component = _component_with_definitions()
      val rec = StateMachineProjection.project(component, Some(component.name))
      val map = rec.asMap

      map.get("states") shouldBe Some(Vector("Draft", "Published"))
      map.get("events") shouldBe Some(Vector("publish"))

      val definitions = _records(map("definitions"))
      definitions.size shouldBe 1
      definitions.head.asMap.get("name") shouldBe Some("lifecycle")
      definitions.head.asMap.get("states") shouldBe Some(Vector("Draft", "Published"))
      definitions.head.asMap.get("events") shouldBe Some(Vector("publish"))
    }
  }

  private def _component_with_rules(): Component = {
    val component = new Component() with CollectionTransitionRuleProvider {
      override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
        Vector(
          CollectionTransitionRule[Any](
            collectionName = "person",
            trigger = TransitionTrigger.Update,
            eventName = "update",
            priority = 2,
            declarationOrder = 1,
            guard = Some(ExpressionGuard("event.name == 'update'", (_, _) => Map("event" -> Map("name" -> "update")))),
            plan = ExecutionPlan.empty[Any, TransitionEvent]
          ),
          CollectionTransitionRule[Any](
            collectionName = "person",
            trigger = TransitionTrigger.Save,
            eventName = "save",
            priority = 1,
            declarationOrder = 0,
            guard = Some(RefGuard("canSave", new GuardBindingResolver[Any, TransitionEvent] {
              def resolve(name: String): Consequence[Guard[Any, TransitionEvent]] = {
                val _ = name
                Consequence.success(new Guard[Any, TransitionEvent] {
                  def eval(state: Any, event: TransitionEvent): Consequence[Boolean] = {
                    val _ = (state, event)
                    Consequence.success(true)
                  }
                })
              }
            })),
            plan = ExecutionPlan.empty[Any, TransitionEvent]
          )
        )
    }

    val core = Component.Core.create(
      name = "projection_state_machine_spec",
      componentid = ComponentId("projection_state_machine_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("projection_state_machine_spec")),
      protocol = Protocol.empty
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_state_machine_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _component_with_definitions(): Component = {
    val component = new Component() {
      override def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
        Vector(
          CmlStateMachineDefinition(
            name = "lifecycle",
            states = Vector("Draft", "Published"),
            events = Vector("publish")
          )
        )
    }

    val core = Component.Core.create(
      name = "projection_state_machine_definition_spec",
      componentid = ComponentId("projection_state_machine_definition_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("projection_state_machine_definition_spec")),
      protocol = Protocol.empty
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_state_machine_definition_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Vector[?] =>
        xs.collect { case r: Record => r }
      case xs: Seq[?] =>
        xs.toVector.collect { case r: Record => r }
      case _ =>
        Vector.empty
    }

  private def _record(value: Any): Record =
    value.asInstanceOf[Record]
}
