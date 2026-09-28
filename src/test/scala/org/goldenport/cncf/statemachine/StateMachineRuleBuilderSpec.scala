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
 * @version Sep. 28, 2026
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
      given EntityPersistent[TestEntity] = _entity_persistent

      val trace = ArrayBuffer.empty[String]
      val guardresolver = new GuardBindingResolver[TestEntity, TransitionEvent] {
        def resolve(name: String): Consequence[Guard[TestEntity, TransitionEvent]] =
          Consequence.success(new Guard[TestEntity, TransitionEvent] {
            def eval(state: TestEntity, event: TransitionEvent): Consequence[Boolean] = {
              val _ = state
              Consequence.success(name == "isUpdate" && event.name == "update")
            }
          })
      }
      val guard = StateMachineRuleBuilder.guardRef("isUpdate", guardresolver)
      val plan = StateMachineRuleBuilder.plan[TestEntity](
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
            guard = rule.guard.map(_.asInstanceOf[Guard[TestEntity, TransitionEvent]]),
            plan = rule.plan.asInstanceOf[ExecutionPlan[TestEntity, TransitionEvent]]
          )
        ))
      )

      val entity = TestEntity(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "b1", _cid, entropy = "b1"), "taro")
      val event = TransitionEvent("update", Some(entity.id))
      When("the provider selects and executes the update plan")
      val selected = provider.planForUpdate(entity, _entity_persistent, event)
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
      Given("an update-event expression with an explicit state, event and context binding")
      val expression = "event.name == 'update'"

      When("the rule builder constructs the expression guard")
      val guard = StateMachineRuleBuilder.guardExpression[TestEntity](expression) {
        (state, event) => Map(
          "state" -> state,
          "event" -> event,
          "ctx" -> Map.empty[String, Any]
        )
      }

      Then("the helper returns an expression guard")
      guard shouldBe a[ExpressionGuard[?, ?]]
    }
  }

  private final case class TestEntity(id: EntityId, name: String) {
    def toRecord: Record = Record.dataAuto("id" -> id, "name" -> name)
  }

  private val _entity_persistent: EntityPersistent[TestEntity] = new EntityPersistent[TestEntity] {
    def id(e: TestEntity): EntityId = e.id
    def toRecord(e: TestEntity): Record = e.toRecord
    def fromRecord(r: Record): Consequence[TestEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(TestEntity(id, name))
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
