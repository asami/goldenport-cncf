package org.goldenport.cncf.statemachine

import scala.collection.mutable.ArrayBuffer
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, ContextReference, StateMachineOperationResult, StateMachineResultTypeReference}
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineActionPlannerSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "StateMachineActionPlanner" should {
    "lower every declared action into phase and declaration-order partitions without invocation" in {
      Given("a CML transition with reverse-alphabetical binding names and multiple ordered transition actions")
      val programcalls = ArrayBuffer.empty[String]
      val exit = _action("exit", programcalls)
      val transitionzero = _action("transition-zero", programcalls)
      val transitionone = _action("transition-one", programcalls)
      val entry = _action("entry", programcalls)
      val transition = _transition(Vector(
        _binding(CmlStateMachineActionPhase.Exit, 0, "z-exit"),
        _binding(CmlStateMachineActionPhase.Transition, 0, "z-transition"),
        _binding(CmlStateMachineActionPhase.Transition, 1, "a-transition"),
        _binding(CmlStateMachineActionPhase.Entry, 0, "z-entry")
      ))
      val resolver = new Resolver(Map(
        "z-exit" -> Vector(exit),
        "z-transition" -> Vector(transitionzero),
        "a-transition" -> Vector(transitionone),
        "z-entry" -> Vector(entry)
      ))

      When("the admitted CML declarations are lowered through the explicit resolver")
      val result = StateMachineActionPlanner.plan(transition, resolver)

      Then("every declaration is represented exactly once in its CML phase/order partition without name selection or program invocation")
      val plan = result.toOption.getOrElse(fail("action plan should be admitted"))
      plan.exitActions shouldBe Vector(exit)
      plan.transitionActions shouldBe Vector(transitionzero, transitionone)
      plan.entryActions shouldBe Vector(entry)
      programcalls shouldBe empty
    }

    "reject a missing action binding before it can return a partial plan" in {
      Given("a CML transition whose declared action has no resolver binding")
      val transition = _transition(Vector(
        _binding(CmlStateMachineActionPhase.Transition, 0, "missing-action")
      ))

      When("the action plan is admitted")
      val result = StateMachineActionPlanner.plan(transition, new Resolver(Map.empty))

      Then("the missing binding remains a typed not-found Consequence failure")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          conclusion.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.NotFound
          _has_operation_facet(conclusion, "missing-action") shouldBe true
        case _ => fail("a missing binding must not produce a partial plan")
      }
    }

    "reject an ambiguous action binding before it can return a partial plan" in {
      Given("a CML transition whose declared action resolves to multiple canonical programs")
      val programcalls = ArrayBuffer.empty[String]
      val transition = _transition(Vector(
        _binding(CmlStateMachineActionPhase.Transition, 0, "duplicate-action")
      ))
      val resolver = new Resolver(Map(
        "duplicate-action" -> Vector(_action("first", programcalls), _action("second", programcalls))
      ))

      When("the action plan is admitted")
      val result = StateMachineActionPlanner.plan(transition, resolver)

      Then("the ambiguous binding remains a typed conflict Consequence failure without program invocation")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          conclusion.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
          _has_operation_facet(conclusion, "duplicate-action") shouldBe true
          _has_value_facet(conclusion, "2") shouldBe true
          programcalls shouldBe empty
        case _ => fail("an ambiguous binding must not produce a partial plan")
      }
    }
  }

  private val _machine = CmlStateMachineIdentity("action-planner")
  private val _transition_identity = CmlStateMachineTransitionIdentity(_machine, 0)

  private def _binding(
    phase: CmlStateMachineActionPhase,
    order: Int,
    name: String
  ): CmlStateMachineActionBinding =
    CmlStateMachineActionBinding(
      CmlStateMachineActionIdentity(_transition_identity, phase, order),
      name
    )

  private def _transition(
    actions: Vector[CmlStateMachineActionBinding]
  ): CmlStateMachineTransition = {
    val source = CmlStateMachineStateIdentity(
      _machine,
      CmlStateMachineStatePath(Vector("Draft"))
    )
    val triggeridentity = CmlStateMachineTriggerIdentity(_machine, "advance")
    CmlStateMachineTransition(
      identity = _transition_identity,
      source = source,
      target = CmlStateMachineTransitionTarget.State(source),
      trigger = CmlStateMachineTrigger(
        triggeridentity,
        CmlStateMachineTriggerContext(
          CmlStateMachineTriggerContextIdentity(triggeridentity),
          CmlStateMachineVersion(1),
          Vector.empty
        )
      ),
      priority = 0,
      guard = CmlStateMachineGuardProgram.Named(
        CmlStateMachineGuardIdentity(_transition_identity, "allowed"),
        "allowed"
      ),
      actions = actions,
      sourceLocation = CmlStateMachineSourceLocation(
        _machine,
        Vector("states", "Draft", "on", "advance")
      )
    )
  }

  private def _action(
    label: String,
    programcalls: ArrayBuffer[String]
  ): ResolvedAction[String, TransitionEvent] =
    new ResolvedAction[String, TransitionEvent] {
      def program(state: String, event: TransitionEvent): ExecUowM[ActionExecution] = {
        val _ = (state, event)
        programcalls += label
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

  private final class Resolver(
    values: Map[String, Vector[ResolvedAction[String, TransitionEvent]]]
  ) extends ActionBindingResolver[String, TransitionEvent] {
    def resolve(name: String): Consequence[ResolvedAction[String, TransitionEvent]] =
      values.get(name).map(_.toVector).getOrElse(Vector.empty) match {
        case Vector(single) => Consequence.success(single)
        case Vector() =>
          Consequence.operationNotFound(
            name,
            Seq(Facet.Message("action-binding-not-found"))
          )
        case many =>
          Consequence.operationConflict(
            name,
            Seq(
              Facet.Value(many.size),
              Facet.Message("action-binding-ambiguous")
            )
          )
      }
  }

  private def _has_operation_facet(c: Conclusion, expected: String): Boolean =
    c.observation.cause.descriptor.facets.exists {
      case Facet.Operation(name) => name == expected
      case _ => false
    }

  private def _has_value_facet(c: Conclusion, expected: String): Boolean =
    c.observation.cause.descriptor.facets.exists {
      case Facet.Value(value) => value.toString == expected
      case _ => false
    }
}
