package org.goldenport.cncf.statemachine

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.{AdmittedJudgmentResult, AlternativeIdentity, JudgmentActionIdentity}

/*
 * @since   Sep. 23, 2026
 * @version Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure StateMachine-owned selection of a transition target for an admitted result. */
final class CandidateAdmissionRouterException(
  val diagnostic: CandidateAdmissionRouter.Diagnostic
) extends IllegalArgumentException(diagnostic.render)

object CandidateAdmissionRouter {
  final case class Route(
    judgment: JudgmentActionIdentity,
    alternative: AlternativeIdentity,
    target: CmlStateMachineTransitionTarget
  )

  enum DiagnosticCode(val value: String) {
    case MissingRoute extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-001")
    case AmbiguousRoute extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-002")
    case MissingTarget extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-ROUTER-003")
  }

  final case class Diagnostic(code: DiagnosticCode, data: Map[String, String]) {
    def render: String = {
      val details = data.toVector.sortBy(_._1).map { case (key, value) =>
        s"$key=$value"
      }.mkString(", ")
      s"${code.value}: $details"
    }
  }

  /**
   * Selects only an explicit StateMachine route.  No Provider, callback, or
   * effect is accepted by this API, so a worker cannot choose progression.
   */
  def routeC(
    result: AdmittedJudgmentResult,
    routes: Vector[Route]
  ): Consequence[CmlStateMachineTransitionTarget] = {
    val selected = routes.filter(route =>
      route.judgment == result.judgment &&
        route.alternative == result.selectedAlternative
    )
    selected match {
      case Vector(Route(_, _, null)) =>
        _failure(Diagnostic(DiagnosticCode.MissingTarget, _route_data(result)))
      case Vector(Route(_, _, target)) => Consequence.success(target)
      case Vector() => _failure(Diagnostic(DiagnosticCode.MissingRoute, _route_data(result)))
      case _ => _failure(Diagnostic(DiagnosticCode.AmbiguousRoute, _route_data(result)))
    }
  }

  private def _route_data(result: AdmittedJudgmentResult): Map[String, String] =
    Map(
      "alternative" -> result.selectedAlternative.value,
      "judgment" -> result.judgment.value
    )

  private def _failure[A](diagnostic: Diagnostic): Consequence[A] =
    Consequence.Failure(Conclusion.from(new CandidateAdmissionRouterException(diagnostic)))
}
