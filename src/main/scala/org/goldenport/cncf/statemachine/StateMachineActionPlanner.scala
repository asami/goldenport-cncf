package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * Lowers already-admitted CML action declarations into the canonical runtime
 * action programs.  It deliberately resolves declarations only; invocation is
 * owned by ExecutionPlanExecutor and its supplied UnitOfWork interpreter.
 *
 * @since   Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
object StateMachineActionPlanner {
  def plan[S, E](
    transition: CmlStateMachineTransition,
    resolver: ActionBindingResolver[S, E]
  ): Consequence[ExecutionPlan[S, E]] =
    transition.actions.foldLeft(
      Consequence.success(Vector.empty[(CmlStateMachineActionBinding, ResolvedAction[S, E])])
    ) { (z, binding) =>
      z.flatMap { actions =>
        resolver.resolve(binding.bindingName).map(action => actions :+ (binding -> action))
      }
    }.map { actions =>
      ExecutionPlan(
        exitActions = _actions(actions, CmlStateMachineActionPhase.Exit),
        transitionActions = _actions(actions, CmlStateMachineActionPhase.Transition),
        entryActions = _actions(actions, CmlStateMachineActionPhase.Entry)
      )
    }

  private def _actions[S, E](
    actions: Vector[(CmlStateMachineActionBinding, ResolvedAction[S, E])],
    phase: CmlStateMachineActionPhase
  ): Vector[ResolvedAction[S, E]] =
    actions.collect {
      case (binding, action) if binding.identity.phase == phase => action
    }
}
