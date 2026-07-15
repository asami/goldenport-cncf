package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
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
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class CollectionStateMachinePlannerProviderSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "CollectionStateMachinePlannerProvider" should {
    "select plan by event + guard + priority deterministically" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val lowPriorityRule = TransitionRule[_Person](
        eventName = "update",
        priority = 2,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("low", trace)
      )
      val highPriorityRule = TransitionRule[_Person](
        eventName = "update",
        priority = 1,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("high", trace)
      )
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(lowPriorityRule, highPriorityRule))
      )

      val person = _Person(EntityId("test", "p2", _cid), "taro", age = 20)
      val event = TransitionEvent("update", Some(person.id))
      val selected = provider.planForUpdate(person, _person_persistent, event)

      selected shouldBe a[Consequence.Success[_]]
      val plan = selected.TAKE.getOrElse(fail("plan should be selected"))
      ExecutionPlanExecutor.execute(plan, person, event) shouldBe Consequence.unit
      trace.toVector shouldBe Vector("high-exit", "high-transition", "high-entry")
    }

    "return None when no rule matches guard" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val rule = TransitionRule[_Person](
        eventName = "update",
        priority = 1,
        declarationOrder = 0,
        guard = Some(_guard(_ => false)),
        plan = _recording_plan("blocked", scala.collection.mutable.ArrayBuffer.empty[String])
      )
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(rule))
      )

      val person = _Person(EntityId("test", "p3", _cid), "hanako", age = 30)
      val event = TransitionEvent("update", Some(person.id))
      val selected = provider.planForUpdate(person, _person_persistent, event)

      selected shouldBe Consequence.success(None)
    }

    "allow a declared structural state transition on generic update" in {
      Given("a lifecycle rule from Draft to Published and matching record state")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = _Person(EntityId("test", "p4", _cid), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Published"))
      )

      When("the generic update is planned")
      val selected = planner.plan(person, event)

      Then("the declared semantic transition supplies the execution plan")
      selected shouldBe Consequence.success(Some(ExecutionPlan.empty[_Person, TransitionEvent]))
    }

    "reject a state change with no declared structural transition" in {
      Given("a lifecycle that allows Draft to Published only")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = _Person(EntityId("test", "p5", _cid), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Published")),
        proposedRecord = Some(Record.data("status" -> "Draft"))
      )

      When("the reverse update is planned")
      val selected = planner.plan(person, event)

      Then("the planner reports a state conflict")
      selected shouldBe a[Consequence.Failure[_]]
      selected match {
        case Consequence.Failure(conclusion) => conclusion.show should include("Published -> Draft")
        case _ => fail("state conflict should fail")
      }
    }

    "skip transition execution when the state field is unchanged" in {
      Given("a structural lifecycle rule and an update that keeps its state")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = _Person(EntityId("test", "p6", _cid), "taro", age = 21)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft", "age" -> 20)),
        proposedRecord = Some(Record.data("status" -> "Draft", "age" -> 21))
      )

      When("the non-state update is planned")
      val selected = planner.plan(person, event)

      Then("no state-machine plan is required")
      selected shouldBe Consequence.success(None)
    }

    "resolve duplicate semantic event names by structural from and to states" in {
      Given("two cancel transitions that share an event but start in different states")
      val queued = _structural_rule("cancel", "Queued", 1, "Canceled", 4)
      val sending = _structural_rule("cancel", "Sending", 2, "Canceled", 4)
      val planner = new CollectionStateMachinePlanner(Vector(queued, sending))
      val person = _Person(EntityId("test", "p7", _cid), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> 2)),
        proposedRecord = Some(Record.data("status" -> 4))
      )

      When("the update starts from Sending")
      val selected = planner.plan(person, event)

      Then("the Sending transition is selected independently of declaration order")
      selected shouldBe Consequence.success(Some(sending.plan))
    }
  }

  private def _structural_rule(
    eventname: String,
    fromstate: String,
    fromvalue: Int,
    tostate: String,
    tovalue: Int
  ): TransitionRule[_Person] =
    TransitionRule(
      eventName = eventname,
      priority = 0,
      declarationOrder = 0,
      guard = None,
      plan = ExecutionPlan.empty[_Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some(fromstate),
      fromStateValue = Some(fromvalue),
      toState = Some(tostate),
      toStateValue = Some(tovalue)
    )

  private final case class _Person(
    id: EntityId,
    name: String,
    age: Int
  ) {
    def toRecord(): Record = Record.dataAuto("id" -> id, "name" -> name, "age" -> age)
  }

  private val _person_persistent: EntityPersistent[_Person] = new EntityPersistent[_Person] {
    def id(e: _Person): EntityId = e.id
    def toRecord(e: _Person): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_Person] = {
      val m = r.asMap
      (m.get("id"), m.get("name"), m.get("age")) match {
        case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
          Consequence.success(_Person(id, name, age))
        case _ =>
          Consequence.argumentInvalid("invalid person record")
      }
    }
  }

  private def _guard[S](
    f: S => Boolean
  ): Guard[S, TransitionEvent] = new Guard[S, TransitionEvent] {
    def eval(state: S, event: TransitionEvent): Consequence[Boolean] = {
      val _ = event
      Consequence.success(f(state))
    }
  }

  private def _recording_plan[S](
    label: String,
    trace: scala.collection.mutable.ArrayBuffer[String]
  ): ExecutionPlan[S, TransitionEvent] =
    ExecutionPlan(
      exitActions = Vector(_record[S](s"$label-exit", trace)),
      transitionAction = Some(_record[S](s"$label-transition", trace)),
      entryActions = Vector(_record[S](s"$label-entry", trace))
    )

  private def _record[S](
    label: String,
    trace: scala.collection.mutable.ArrayBuffer[String]
  ): ResolvedAction[S, TransitionEvent] =
    new ResolvedAction[S, TransitionEvent] {
      def run(state: S, event: TransitionEvent): Consequence[Unit] = {
        val _ = (state, event)
        trace += label
        Consequence.unit
      }
    }
}
