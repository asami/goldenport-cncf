package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait GuardExpr
object GuardExpr {
  final case class Ref(name: String) extends GuardExpr
  final case class Expression(expr: String) extends GuardExpr
}

final case class ExpressionGuard[S, E](
  expression: String,
  contextFactory: (S, E) => Map[String, Any]
) extends Guard[S, E] {
  def eval(state: S, event: E): Consequence[Boolean] = {
    val ctx = contextFactory(state, event)
    MvelEvaluator.evalBoolean(expression, ctx)
  }
}

final case class RefGuard[S, E](
  name: String,
  resolver: GuardBindingResolver[S, E]
) extends Guard[S, E] {
  def eval(state: S, event: E): Consequence[Boolean] =
    resolver.resolve(name).flatMap(_.eval(state, event))
}

object GuardRuntime {
  /**
   * The Phase 63 normalized path accepts only a named guard binding.  The
   * older build method remains for compatibility until the ABI phase owns its
   * removal; it must not become a way to re-admit raw expressions here.
   */
  def buildNormalized[S, E](
    expr: GuardExpr,
    resolver: GuardBindingResolver[S, E]
  ): Consequence[Guard[S, E]] =
    expr match {
      case GuardExpr.Ref(name) =>
        Consequence.success(RefGuard(name, resolver))
      case GuardExpr.Expression(_) =>
        Consequence.operationInvalid(
          "legacy raw expression is not admitted to the normalized StateMachine guard path"
        )
    }

  def build[S, E](
    expr: GuardExpr,
    resolver: GuardBindingResolver[S, E],
    contextFactory: (S, E) => Map[String, Any]
  ): Guard[S, E] =
    expr match {
      case GuardExpr.Ref(name) => RefGuard(name, resolver)
      case GuardExpr.Expression(code) => ExpressionGuard(code, contextFactory)
    }
}
