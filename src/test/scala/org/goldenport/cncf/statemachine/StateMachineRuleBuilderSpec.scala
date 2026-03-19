package org.goldenport.cncf.statemachine

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineRuleBuilderSpec
  extends AnyWordSpec
  with Matchers {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "StateMachineRuleBuilder" should {
    "build update rule with ref guard and execute plan" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Entity] = _entityPersistent

      val trace = ArrayBuffer.empty[String]
      val guardresolver = new GuardBindingResolver[_Entity, TransitionEvent] {
        def resolve(name: String): Consequence[Guard[_Entity, TransitionEvent]] =
          Consequence.success(new Guard[_Entity, TransitionEvent] {
            def eval(state: _Entity, event: TransitionEvent): Consequence[Boolean] = {
              val _ = state
              Consequence.success(name == "isUpdate" && event.name == "update")
            }
          })
      }
      val guard = StateMachineRuleBuilder.guardRef("isUpdate", guardresolver)
      val plan = StateMachineRuleBuilder.plan[_Entity](
        exit = Vector(StateMachineRuleBuilder.action { (_, _) =>
          trace += "exit"
          Consequence.unit
        }),
        transition = Some(StateMachineRuleBuilder.action { (_, _) =>
          trace += "transition"
          Consequence.unit
        }),
        entry = Vector(StateMachineRuleBuilder.action { (_, _) =>
          trace += "entry"
          Consequence.unit
        })
      )
      val rule = StateMachineRuleBuilder.updateRule(
        collectionName = "person",
        eventName = "update",
        priority = 1,
        plan = plan,
        guard = Some(guard)
      )
      val provider = new CollectionStateMachinePlannerProvider()
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(
          TransitionRule(
            eventName = rule.eventName,
            priority = rule.priority,
            declarationOrder = rule.declarationOrder,
            guard = rule.guard.map(_.asInstanceOf[Guard[_Entity, TransitionEvent]]),
            plan = rule.plan.asInstanceOf[ExecutionPlan[_Entity, TransitionEvent]]
          )
        ))
      )

      val entity = _Entity(EntityId("test", "b1", _cid), "taro")
      val event = TransitionEvent("update", Some(entity.id))
      val selected = provider.planForUpdate(entity, _entityPersistent, event)
      val selectedPlan = selected.TAKE.getOrElse(fail("plan should be selected"))
      ExecutionPlanExecutor.execute(selectedPlan, entity, event) shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition", "entry")
    }

    "create expression guard helper instance" in {
      val guard = StateMachineRuleBuilder.guardExpression[_Entity]("event.name == 'update'") {
        (state, event) => Map(
          "state" -> state,
          "event" -> event,
          "ctx" -> Map.empty[String, Any]
        )
      }

      guard shouldBe a[ExpressionGuard[?, ?]]
    }
  }

  private final case class _Entity(id: EntityId, name: String) {
    def toRecord: Record = Record.dataAuto("id" -> id, "name" -> name)
  }

  private val _entityPersistent: EntityPersistent[_Entity] = new EntityPersistent[_Entity] {
    def id(e: _Entity): EntityId = e.id
    def toRecord(e: _Entity): Record = e.toRecord
    def fromRecord(r: Record): Consequence[_Entity] = {
      val m = r.asMap
      (m.get("id"), m.get("name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(_Entity(id, name))
        case _ =>
          Consequence.failure("invalid record")
      }
    }
  }
}
