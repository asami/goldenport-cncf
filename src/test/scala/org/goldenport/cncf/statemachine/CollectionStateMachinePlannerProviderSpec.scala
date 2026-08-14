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
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CollectionStateMachinePlannerProviderSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "CollectionStateMachinePlannerProvider" should {
    "select plan by event + guard + priority deterministically" in {
      Given("two matching legacy transition plans with distinct priorities")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val lowpriorityrule = TransitionRule[Person](
        eventName = "update",
        priority = 2,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("low", trace)
      )
      val highpriorityrule = TransitionRule[Person](
        eventName = "update",
        priority = 1,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("high", trace)
      )
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(lowpriorityrule, highpriorityrule))
      )

      val person = Person(EntityId("test", "p2", _cid), "taro", age = 20)
      val event = TransitionEvent("update", Some(person.id))

      When("the update event is planned")
      val selected = provider.planForUpdate(person, _person_persistent, event)

      Then("the higher-priority plan is selected and runs in lifecycle order")
      selected shouldBe a[Consequence.Success[_]]
      val plan = selected.TAKE.getOrElse(fail("plan should be selected"))
      ExecutionPlanExecutor.execute(plan, person, event) shouldBe Consequence.unit
      trace.toVector shouldBe Vector("high-exit", "high-transition", "high-entry")
    }

    "return None when no rule matches guard" in {
      Given("a legacy transition whose guard rejects the update")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val rule = TransitionRule[Person](
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

      val person = Person(EntityId("test", "p3", _cid), "hanako", age = 30)
      val event = TransitionEvent("update", Some(person.id))

      When("the update event is planned")
      val selected = provider.planForUpdate(person, _person_persistent, event)

      Then("no transition plan is selected")
      selected shouldBe Consequence.success(None)
    }

    "allow a declared structural state transition on generic update" in {
      Given("a lifecycle rule from Draft to Published and matching record state")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(EntityId("test", "p4", _cid), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Published"))
      )

      When("the generic update is planned")
      val selected = planner.plan(person, event)

      Then("the declared semantic transition supplies the execution plan")
      selected shouldBe Consequence.success(Some(ExecutionPlan.empty[Person, TransitionEvent]))
    }

    "reject a state change with no declared structural transition" in {
      Given("a lifecycle that allows Draft to Published only")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(EntityId("test", "p5", _cid), "taro", age = 20)
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
      val person = Person(EntityId("test", "p6", _cid), "taro", age = 21)
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
      val person = Person(EntityId("test", "p7", _cid), "taro", age = 20)
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

    "recover named shallow history and require its proposed record write" in {
      Given("a named Review history transition with an existing persistent history record")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(EntityId("test", "p8", _cid), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.data("Review" -> "Approved"))),
        proposedRecord = Some(Record.data("status" -> "Approved", "lifecycleHistory" -> Record.data("Review" -> "Approved")))
      )

      When("the proposed state and history write agree with the stored Review leaf")
      val selected = planner.plan(person, event)

      Then("the planner accepts the history transition without mutating either record")
      selected shouldBe Consequence.success(Some(rule.plan))
    }

    "fall back to the declared direct leaf for absent history" in {
      Given("a named Review history transition with no stored Review entry")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(EntityId("test", "p9", _cid), "taro", age = 20)
      val fallback = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.empty)),
        proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending")))
      )

      When("the transition uses the declared fallback leaf")
      val selected = planner.plan(person, fallback)

      Then("the caller-proposed fallback leaf is accepted")
      selected shouldBe Consequence.success(Some(rule.plan))
    }

    "reject malformed proposed history records after an absent history fallback" in {
      Given("a named Review history transition with no stored Review entry")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(EntityId("test", "p9", _cid), "taro", age = 20)
      val fallback = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.empty)),
        proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending")))
      )

      When("mismatched, invalid-leaf, and invalid-shape history records are planned")
      val malformed = fallback.copy(proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Approved"))))
      val invalidstored = fallback.copy(currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.data("Review" -> 99))))
      val invalidshape = fallback.copy(currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> "not-a-record")))
      val results = Vector(malformed, invalidstored, invalidshape).map(planner.plan(person, _))

      Then("each malformed proposal is a state conflict")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
    }

    "require normal composite enter, direct-leaf move, and leave transitions to carry their proposed history writes" in {
      Given("normal transitions that enter Review.Pending, move to Approved, and leave to Suspended")
      val enter = _normal_composite_rule
      val move = enter.copy(
        fromState = Some("Pending"),
        toState = Some("Approved"),
        expectedHistoryRecordWrites = Vector(HistoryRecordWrite("Review", "Approved"))
      )
      val leave = enter.copy(
        fromState = Some("Approved"),
        toState = Some("Suspended"),
        expectedHistoryRecordWrites = Vector(HistoryRecordWrite("Review", "Approved"))
      )
      val planner = new CollectionStateMachinePlanner(Vector(enter, move, leave))
      val person = Person(EntityId("test", "p10", _cid), "taro", age = 20)
      val enteraccepted = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained"))),
        proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending", "Other" -> "Retained")))
      )
      val moveaccepted = enteraccepted.copy(
        currentRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending", "Other" -> "Retained"))),
        proposedRecord = Some(Record.data("status" -> "Approved", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained")))
      )
      val leaveaccepted = enteraccepted.copy(
        currentRecord = Some(Record.data("status" -> "Approved", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained"))),
        proposedRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained")))
      )

      When("the caller proposes each required Review leaf while retaining unrelated entries")
      val selected = Vector(enteraccepted, moveaccepted, leaveaccepted).map(planner.plan(person, _))

      Then("each required write is accepted without runtime mutation")
      selected shouldBe Vector.fill(3)(Consequence.success(Some(enter.plan)))
    }

    "reject a stale normal composite history write" in {
      Given("a normal transition that enters Review.Pending")
      val rule = _normal_composite_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(EntityId("test", "p10", _cid), "taro", age = 20)
      val accepted = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained"))),
        proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending", "Other" -> "Retained")))
      )

      When("the caller proposes a stale Review leaf")
      val stale = accepted.copy(proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Approved", "Other" -> "Retained"))))
      val selected = planner.plan(person, stale)

      Then("the stale required write is rejected without runtime mutation")
      selected shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _structural_rule(
    eventname: String,
    fromstate: String,
    fromvalue: Int,
    tostate: String,
    tovalue: Int
  ): TransitionRule[Person] =
    TransitionRule(
      eventName = eventname,
      priority = 0,
      declarationOrder = 0,
      guard = None,
      plan = ExecutionPlan.empty[Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some(fromstate),
      fromStateValue = Some(fromvalue),
      toState = Some(tostate),
      toStateValue = Some(tovalue)
    )

  private def _history_rule: TransitionRule[Person] =
    TransitionRule(
      eventName = "resume",
      priority = 0,
      declarationOrder = 0,
      guard = None,
      plan = ExecutionPlan.empty[Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some("Suspended"),
      historyCompositeName = Some("Review"),
      historyFieldName = Some("lifecycleHistory"),
      historyDirectLeaves = Vector("Pending", "Approved"),
      historyFallbackLeaf = Some("Pending")
    )

  private def _normal_composite_rule: TransitionRule[Person] =
    TransitionRule(
      eventName = "submit",
      priority = 0,
      declarationOrder = 0,
      guard = None,
      plan = ExecutionPlan.empty[Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some("Draft"),
      toState = Some("Pending"),
      historyFieldName = Some("lifecycleHistory"),
      expectedHistoryRecordWrites = Vector(HistoryRecordWrite("Review", "Pending"))
    )

  private final case class Person(
    id: EntityId,
    name: String,
    age: Int
  ) {
    def toRecord(): Record = Record.dataAuto("id" -> id, "name" -> name, "age" -> age)
  }

  private val _person_persistent: EntityPersistent[Person] = new EntityPersistent[Person] {
    def id(e: Person): EntityId = e.id
    def toRecord(e: Person): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Person] = {
      val m = r.asMap
      (m.get("id"), m.get("name"), m.get("age")) match {
        case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
          Consequence.success(Person(id, name, age))
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
