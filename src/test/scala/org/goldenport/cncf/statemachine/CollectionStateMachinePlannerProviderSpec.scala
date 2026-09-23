package org.goldenport.cncf.statemachine

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.{ExecutionContext, ExecutionInvocationIdentity}
import org.goldenport.cncf.event.TransitionLifecycleFailureOutcome
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, ContextReference, StateMachineOperationResult, StateMachineResultTypeReference}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 *  version Aug. 14, 2026
 * @version Sep. 19, 2026
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

      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p2", _cid, entropy = "p2"), "taro", age = 20)
      val event = TransitionEvent("update", Some(person.id))

      When("the update event is planned")
      val selected = provider.planForUpdate(person, _person_persistent, event)

      Then("the higher-priority plan is selected and runs in lifecycle order")
      selected shouldBe a[Consequence.Success[_]]
      val plan = selected.TAKE.getOrElse(fail("plan should be selected"))
      ExecutionPlanExecutor.execute(
        plan,
        person,
        event,
        summon[ExecutionContext].runtime.unitOfWorkInterpreter
      ) shouldBe Consequence.unit
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

      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p3", _cid, entropy = "p3"), "hanako", age = 30)
      val event = TransitionEvent("update", Some(person.id))

      When("the update event is planned")
      val selected = provider.planForUpdate(person, _person_persistent, event)

      Then("no transition plan is selected")
      selected shouldBe Consequence.success(None)
    }

    "classify a source rejection while preserving the legacy failure" in {
      Given("a structural Draft-to-Published rule and an update from Published to Draft")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "source_rejection", _cid, entropy = "source_rejection"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Published")),
        proposedRecord = Some(Record.data("status" -> "Draft"))
      )

      When("the typed planner result is requested")
      val result = planner.planWithOutcome(person, event)

      Then("the source classification carries the same caller-visible failure")
      result match {
        case TransitionPlanningResult.Rejected(outcome, conclusion, None) =>
          outcome shouldBe TransitionLifecycleFailureOutcome.Source
          conclusion.show should include ("Published -> Draft")
          planner.plan(person, event) match {
            case Consequence.Failure(legacyconclusion) =>
              legacyconclusion.show shouldBe conclusion.show
            case _ => fail("legacy planning should retain the source failure")
          }
        case _ => fail("the planner should classify the source rejection")
      }
      result.toConsequence shouldBe a[Consequence.Failure[_]]
    }

    "classify an invalid proposed target as Target" in {
      Given("a structural Draft-to-Published rule and an invalid proposed target")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "target_rejection", _cid, entropy = "target_rejection"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Suspended"))
      )

      When("the typed planner result is requested")
      val result = planner.planWithOutcome(person, event)

      Then("the rejected target is typed and still fails for direct planner callers")
      result match {
        case TransitionPlanningResult.Rejected(outcome, _, None) =>
          outcome shouldBe TransitionLifecycleFailureOutcome.Target
        case _ => fail("the planner should classify the target rejection")
      }
      result.toConsequence shouldBe a[Consequence.Failure[_]]
    }

    "classify a target-valid guard rejection as Guard" in {
      Given("a structural Draft-to-Published rule whose guard rejects the candidate")
      val rule = _structural_rule("publish", "Draft", 1, "Published", 2).copy(
        guard = Some(_guard[Person](_ => false))
      )
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "guard_rejection", _cid, entropy = "guard_rejection"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Published"))
      )

      When("the typed planner result is requested")
      val result = planner.planWithOutcome(person, event)

      Then("the guard classification retains the planner failure behavior")
      result match {
        case TransitionPlanningResult.Rejected(outcome, _, None) =>
          outcome shouldBe TransitionLifecycleFailureOutcome.Guard
        case _ => fail("the planner should classify the guard rejection")
      }
      result.toConsequence shouldBe a[Consequence.Failure[_]]
    }

    "map an unannotated legacy provider failure to NoMatch" in {
      Given("a third-party provider that only implements the compatibility planner methods")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[Person] = _person_persistent
      val provider = new LegacyFailureProvider
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "legacy_failure", _cid, entropy = "legacy_failure"), "taro", age = 20)
      val event = TransitionEvent("update", Some(person.id))

      When("the outcome-aware default method adapts the legacy failure")
      val result = provider.planForUpdateOutcome(person, _person_persistent, event)

      Then("the original failure is retained under the conservative NoMatch outcome")
      result match {
        case TransitionPlanningResult.Rejected(outcome, conclusion, None) =>
          outcome shouldBe TransitionLifecycleFailureOutcome.NoMatch
          conclusion.show should include ("legacy planner failure")
        case _ => fail("the legacy failure should be adapted to NoMatch")
      }
    }

    "allow a declared structural state transition on generic update" in {
      Given("a lifecycle rule from Draft to Published and matching record state")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p4", _cid, entropy = "p4"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Published"))
      )

      When("the generic update is planned")
      val selected = planner.plan(person, event)

      Then("the declared semantic transition supplies the execution plan")
      selected shouldBe Consequence.success(Some(ExecutionPlan.empty[Person, TransitionEvent].copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      )))
    }

    "select an explicit operation transition only for its actual invocation identity" in {
      Given("a structural Draft-to-Published rule bound to entity.updateSalesOrder")
      val rule = _explicit_operation_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "operation_gate", _cid, entropy = "operation_gate"), "taro", age = 20)
      val base = TransitionEvent(
        name = "update",
        targetId = Some(person.id),
        currentRecord = Some(Record.data("status" -> "Draft")),
        proposedRecord = Some(Record.data("status" -> "Published"))
      )
      val matching = base.copy(invocation = Some(_invocation("org.example.Person.entity.updateSalesOrder")))
      val absent = base.copy(name = "operation:entity.updateSalesOrder")
      val wrongcomponent = base.copy(name = "operation:entity.updateSalesOrder", invocation = Some(_invocation("org.example.Other.entity.updateSalesOrder")))
      val wrongservice = base.copy(name = "operation:entity.updateSalesOrder", invocation = Some(_invocation("org.example.Person.other.updateSalesOrder")))
      val wrongoperation = base.copy(name = "operation:entity.updateSalesOrder", invocation = Some(_invocation("org.example.Person.entity.updateSalesOrderRecord")))

      When("the same state change is planned under matching, absent, and mismatched invocations")
      val accepted = planner.plan(person, matching)
      val rejected = Vector(absent, wrongcomponent, wrongservice, wrongoperation).map(planner.plan(person, _))

      Then("only the exact component, service, and operation selector selects the bound transition")
      accepted.toOption.flatten.flatMap(_.selectedTransitionBinding) shouldBe Some(_explicit_operation_binding)
      rejected.foreach(_ shouldBe a[Consequence.Failure[_]])
    }

    "reject a directly constructed operation rule without its typed CML binding" in {
      Given("an otherwise valid direct operation rule declaration with no binding")

      When("the runtime rule is constructed")
      val failure = intercept[IllegalArgumentException] {
        CollectionTransitionRule[Person](
          collectionName = _cid.name,
          trigger = TransitionTrigger.Operation,
          eventName = "operation:entity.updateSalesOrder",
          priority = 0,
          declarationOrder = 0,
          guard = None,
          plan = ExecutionPlan.empty[Person, TransitionEvent]
        )
      }

      Then("the declaration fails before it can enter legacy name-based planning")
      failure.getMessage should include ("Operation transition rules require")
    }

    "ignore an explicit operation transition outside its selector before requiring state records" in {
      Given("a structural rule bound only to entity.updateSalesOrder")
      val planner = new CollectionStateMachinePlanner(Vector(_explicit_operation_rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "operation_save", _cid, entropy = "operation_save"), "taro", age = 20)
      val save = TransitionEvent(
        name = "save",
        targetId = Some(person.id),
        invocation = Some(_invocation("org.example.Person.entity.saveSalesOrder"))
      )

      When("a create/save operation has no current record")
      val selected = planner.plan(person, save)

      Then("the unrelated update binding does not claim the operation")
      selected shouldBe Consequence.success(None)
    }

    "select a terminal explicit operation transition without changing its generated numeric state" in {
      Given("a terminal Suspended transition bound to entity.saveSalesOrder")
      val rule = _final_operation_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "operation_final", _cid, entropy = "operation_final"), "taro", age = 20)
      val base = TransitionEvent(
        name = "save",
        targetId = Some(person.id),
        currentRecord = Some(Record.data("status" -> 4)),
        proposedRecord = Some(Record.data("status" -> 4))
      )
      val matching = base.copy(invocation = Some(_invocation("org.example.Person.entity.saveSalesOrder")))
      val mutated = matching.copy(proposedRecord = Some(Record.data("status" -> 5)))
      val wrong = base.copy(invocation = Some(_invocation("org.example.Person.entity.updateSalesOrder")))
      val invalidsource = matching.copy(
        currentRecord = Some(Record.data("status" -> 5)),
        proposedRecord = Some(Record.data("status" -> 5))
      )

      When("the unchanged, mutating, wrong, and invalid-source explicit invocations are planned")
      val selected = planner.plan(person, matching)
      val mutationrejected = planner.plan(person, mutated)
      val wrongrejected = planner.plan(person, wrong)
      val sourcefailure = planner.planWithOutcome(person, invalidsource)

      Then("only the unchanged matching source selects the typed final binding and operation trigger")
      selected shouldBe Consequence.success(Some(rule.plan.copy(
        selectedTransitionBinding = Some(_final_operation_binding),
        selectedTransitionTrigger = Some(TransitionTrigger.Operation)
      )))
      mutationrejected shouldBe a[Consequence.Failure[_]]
      wrongrejected shouldBe Consequence.success(None)
      sourcefailure match {
        case TransitionPlanningResult.Rejected(outcome, _, _) =>
          outcome shouldBe TransitionLifecycleFailureOutcome.Source
        case _ => fail("the matching terminal invocation should reject an invalid source")
      }
    }

    "ignore a terminal explicit operation transition when creation has no current record" in {
      Given("a terminal Suspended transition bound to entity.saveSalesOrder")
      val planner = new CollectionStateMachinePlanner(Vector(_final_operation_rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "operation_final_create", _cid, entropy = "operation_final_create"), "taro", age = 20)
      val event = TransitionEvent(
        name = "save",
        targetId = Some(person.id),
        proposedRecord = Some(Record.data("status" -> 4)),
        invocation = Some(_invocation("org.example.Person.entity.saveSalesOrder"))
      )

      When("the matching generated create operation is planned without a current record")
      val selected = planner.plan(person, event)

      Then("the terminal transition is not selected during entity creation")
      selected shouldBe Consequence.success(None)
    }

    "require a proposed record for a terminal explicit operation transition after creation" in {
      Given("a terminal Suspended transition and an existing generated entity record")
      val planner = new CollectionStateMachinePlanner(Vector(_final_operation_rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "operation_final_missing_proposed", _cid, entropy = "operation_final_missing_proposed"), "taro", age = 20)
      val event = TransitionEvent(
        name = "save",
        targetId = Some(person.id),
        currentRecord = Some(Record.data("status" -> 4)),
        invocation = Some(_invocation("org.example.Person.entity.saveSalesOrder"))
      )

      When("the matching terminal operation is planned without a proposed record")
      val selected = planner.plan(person, event)

      Then("structural terminal validation rejects the malformed existing-record update")
      selected shouldBe a[Consequence.Failure[_]]
    }

    "reject a state change with no declared structural transition" in {
      Given("a lifecycle that allows Draft to Published only")
      val planner = new CollectionStateMachinePlanner(Vector(
        _structural_rule("publish", "Draft", 1, "Published", 2)
      ))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p5", _cid, entropy = "p5"), "taro", age = 20)
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
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p6", _cid, entropy = "p6"), "taro", age = 21)
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
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p7", _cid, entropy = "p7"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> 2)),
        proposedRecord = Some(Record.data("status" -> 4))
      )

      When("the update starts from Sending")
      val selected = planner.plan(person, event)

      Then("the Sending transition is selected independently of declaration order")
      selected shouldBe Consequence.success(Some(sending.plan.copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      )))
    }

    "recover named shallow history and require its proposed record write" in {
      Given("a named Review history transition with an existing persistent history record")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p8", _cid, entropy = "p8"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.data("Review" -> "Approved"))),
        proposedRecord = Some(Record.data("status" -> "Approved", "lifecycleHistory" -> Record.data("Review" -> "Approved")))
      )

      When("the proposed state and history write agree with the stored Review leaf")
      val selected = planner.plan(person, event)

      Then("the planner accepts the history transition without mutating either record")
      selected shouldBe Consequence.success(Some(rule.plan.copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      )))
    }

    "recover named shallow history when the generated state carrier stores numeric values" in {
      Given("a named Review history transition whose persistent leaf is Approved")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p8numeric", _cid, entropy = "p8numeric"), "taro", age = 20)
      val event = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> 4, "lifecycleHistory" -> Record.data("Review" -> "Approved"))),
        proposedRecord = Some(Record.data("status" -> 3, "lifecycleHistory" -> Record.data("Review" -> "Approved")))
      )

      When("the PowerType-backed numeric state resumes the persisted shallow-history leaf")
      val selected = planner.plan(person, event)

      Then("the generated leaf-value binding admits the matching numeric state")
      selected shouldBe Consequence.success(Some(rule.plan.copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      )))
    }

    "fall back to the declared direct leaf for absent history" in {
      Given("a named Review history transition with no stored Review entry")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p9", _cid, entropy = "p9"), "taro", age = 20)
      val fallback = TransitionEvent(
        "update",
        Some(person.id),
        currentRecord = Some(Record.data("status" -> "Suspended", "lifecycleHistory" -> Record.empty)),
        proposedRecord = Some(Record.data("status" -> "Pending", "lifecycleHistory" -> Record.data("Review" -> "Pending")))
      )

      When("the transition uses the declared fallback leaf")
      val selected = planner.plan(person, fallback)

      Then("the caller-proposed fallback leaf is accepted")
      selected shouldBe Consequence.success(Some(rule.plan.copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      )))
    }

    "reject malformed proposed history records after an absent history fallback" in {
      Given("a named Review history transition with no stored Review entry")
      val rule = _history_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p9", _cid, entropy = "p9"), "taro", age = 20)
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
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p10", _cid, entropy = "p10"), "taro", age = 20)
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
      selected shouldBe Vector.fill(3)(Consequence.success(Some(enter.plan.copy(
        selectedTransitionTrigger = Some(TransitionTrigger.Update)
      ))))
    }

    "reject a stale normal composite history write" in {
      Given("a normal transition that enters Review.Pending")
      val rule = _normal_composite_rule
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val person = Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p10", _cid, entropy = "p10"), "taro", age = 20)
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

  private val _explicit_operation_binding: CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("lifecycle")
    val trigger = CmlStateMachineTriggerIdentity(machine, "operation:entity.updateSalesOrder")
    val source = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Draft")))
    val target = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Published")))
    val contextidentity = CmlStateMachineTriggerContextIdentity(trigger)
    val context = CmlStateMachineTriggerContext(
      identity = contextidentity,
      version = CmlStateMachineVersion(1),
      fields = Vector("eventName", "targetIdentifier", "currentState", "candidateState").map { name =>
        CmlStateMachineTriggerContextField(
          CmlStateMachineTriggerContextFieldIdentity(contextidentity, name),
          CmlStateMachineScalarType.StringValue
        )
      }
    )
    CmlTransitionBinding(
      componentId = org.goldenport.cncf.component.ComponentId("org.example.Person"),
      entityType = _cid,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = CmlStateMachineTransitionTarget.State(target),
      trigger = trigger,
      operation = Some(CmlStateMachineOperationIdentity("entity", "updateSalesOrder")),
      triggerContext = Some(context)
    )
  }

  private def _explicit_operation_rule: TransitionRule[Person] =
    TransitionRule(
      eventName = "operation:entity.updateSalesOrder",
      priority = 0,
      declarationOrder = 0,
      guard = None,
      plan = ExecutionPlan.empty[Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some("Draft"),
      toState = Some("Published"),
      binding = Some(_explicit_operation_binding),
      trigger = TransitionTrigger.Operation
    )

  private val _final_operation_binding: CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("lifecycle")
    val trigger = CmlStateMachineTriggerIdentity(machine, "operation:entity.saveSalesOrder")
    val source = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Suspended")))
    val contextidentity = CmlStateMachineTriggerContextIdentity(trigger)
    val context = CmlStateMachineTriggerContext(
      identity = contextidentity,
      version = CmlStateMachineVersion(1),
      fields = Vector("eventName", "targetIdentifier", "currentState", "candidateState").map { name =>
        CmlStateMachineTriggerContextField(
          CmlStateMachineTriggerContextFieldIdentity(contextidentity, name),
          CmlStateMachineScalarType.StringValue
        )
      }
    )
    CmlTransitionBinding(
      componentId = org.goldenport.cncf.component.ComponentId("org.example.Person"),
      entityType = _cid,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 1),
      source = source,
      target = CmlStateMachineTransitionTarget.Final,
      trigger = trigger,
      operation = Some(CmlStateMachineOperationIdentity("entity", "saveSalesOrder")),
      triggerContext = Some(context)
    )
  }

  private def _final_operation_rule: TransitionRule[Person] =
    TransitionRule(
      eventName = "operation:entity.saveSalesOrder",
      priority = 0,
      declarationOrder = 1,
      guard = None,
      plan = ExecutionPlan.empty[Person, TransitionEvent],
      machineName = Some("lifecycle"),
      stateFieldName = Some("status"),
      fromState = Some("Suspended"),
      fromStateValue = Some(4),
      binding = Some(_final_operation_binding),
      trigger = TransitionTrigger.Operation
    )

  private def _invocation(selector: String): ExecutionInvocationIdentity =
    ExecutionInvocationIdentity(
      key = s"spec-$selector",
      ordinal = 1L,
      operationSelector = selector,
      explicit = true
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
      fromStateValue = Some(4),
      historyCompositeName = Some("Review"),
      historyFieldName = Some("lifecycleHistory"),
      historyDirectLeaves = Vector("Pending", "Approved"),
      historyDirectLeafValues = Map("Pending" -> 2, "Approved" -> 3),
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
      transitionActions = Vector(_record[S](s"$label-transition", trace)),
      entryActions = Vector(_record[S](s"$label-entry", trace))
    )

  private def _record[S](
    label: String,
    trace: scala.collection.mutable.ArrayBuffer[String]
  ): ResolvedAction[S, TransitionEvent] =
    new ResolvedAction[S, TransitionEvent] {
      def program(state: S, event: TransitionEvent): ExecUowM[ActionExecution] = {
        val _ = (state, event)
        trace += label
        _completed_program
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

  private final class LegacyFailureProvider extends StateMachinePlannerProvider {
    def planForSave[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      Consequence.success(None)
    }

    def planForUpdate[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      Consequence.stateConflict("legacy planner failure")
    }

    def planForUpdateById[P](
      id: EntityId,
      patch: P,
      tc: EntityPersistentUpdate[P],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]] = {
      val _ = (id, patch, tc, event)
      Consequence.success(None)
    }
  }
}
