package org.goldenport.cncf.statemachine

import scala.collection.mutable.ArrayBuffer
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, ContextReference, StateMachineOperationResult, StateMachineResultTypeReference}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineRuleBuilderSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "StateMachineRuleBuilder" should {
    "build update rule with ref guard and execute plan" in {
      Given("a state-machine update rule with a reference guard and execution plan")
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
          _completed_program
        }),
        transitionActions = Vector(StateMachineRuleBuilder.action { (_, _) =>
          trace += "transition"
          _completed_program
        }),
        entry = Vector(StateMachineRuleBuilder.action { (_, _) =>
          trace += "entry"
          _completed_program
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

      val entity = _Entity(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "b1", _cid, entropy = "b1"), "taro")
      val event = TransitionEvent("update", Some(entity.id))
      When("the provider selects and executes the update plan")
      val selected = provider.planForUpdate(entity, _entityPersistent, event)
      val selectedPlan = selected.TAKE.getOrElse(fail("plan should be selected"))
      Then("the plan executes its exit, transition, and entry actions in order")
      ExecutionPlanExecutor.execute(
        selectedPlan,
        entity,
        event,
        summon[ExecutionContext].runtime.unitOfWorkInterpreter
      ) shouldBe Consequence.unit
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
          Consequence.argumentInvalid("invalid record")
      }
    }
  }

  private def _completed_program: ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](
      ActionExecution.Completed(
        StateMachineOperationResult(
          StateMachineResultTypeReference("test.result"),
          ContextReference("result", "1")
        )
      )
    )
}
