package org.goldenport.cncf.rule

import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ConclusionDiagnostics

/*
 * Payload-safe observability for explicit Rule action firing. Evaluation
 * through a RuleEngine socket is traced at the consumer SPI boundary.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object RuleTraceSupport {
  def fireC(
    evaluation: RuleEvaluationResult
  )(
    body: => Consequence[RuleFireResult]
  )(using ExecutionContext): Consequence[RuleFireResult] = {
    val calltree = summon[ExecutionContext].observability.callTreeContext
    val started = System.nanoTime()
    if (calltree.isEnabled)
      calltree.enter("rule:fire", _attributes(evaluation))
    try {
      val result = body
      val elapsed = _elapsed_millis(started)
      result match {
        case Consequence.Success(value) =>
          _record(evaluation, error = false, None, elapsed)
          if (calltree.isEnabled)
            calltree.leave(_success_attributes(value, elapsed))
        case Consequence.Failure(conclusion) =>
          _record(evaluation, error = true, Some(conclusion), elapsed)
          if (calltree.isEnabled)
            calltree.leave(_failure_attributes(conclusion, elapsed))
      }
      result
    } catch {
      case NonFatal(e) =>
        val conclusion = Conclusion.from(e)
        val elapsed = _elapsed_millis(started)
        _record(evaluation, error = true, Some(conclusion), elapsed)
        if (calltree.isEnabled)
          calltree.leave(_failure_attributes(conclusion, elapsed))
        throw e
    }
  }

  private def _attributes(evaluation: RuleEvaluationResult): Map[String, String] =
    Map(
      "calltree_kind" -> "rule",
      "operation" -> "fire",
      "rule_set" -> evaluation.ruleSet.id.value,
      "rule_version" -> evaluation.ruleSet.version.value,
      "planned_action_count" -> evaluation.actionPlans.size.toString
    )

  private def _success_attributes(result: RuleFireResult, elapsedmillis: Long): Map[String, String] = {
    val jobids = result.outcomes.collect { case RuleActionOutcome.JobSubmitted(_, id) => id.value }
    Map(
      "outcome" -> "success",
      "duration_ms" -> elapsedmillis.toString,
      "outcome_count" -> result.outcomes.size.toString,
      "job_count" -> jobids.size.toString,
      "job_ids" -> jobids.sorted.mkString(",")
    ).filter(_._2.nonEmpty)
  }

  private def _failure_attributes(conclusion: Conclusion, elapsedmillis: Long): Map[String, String] = {
    val diagnostic = ConclusionDiagnostics.classify(conclusion)
    Map(
      "outcome" -> "failure",
      "duration_ms" -> elapsedmillis.toString,
      "status" -> conclusion.status.webCode.code.toString,
      "diagnostic_key" -> diagnostic.diagnosticKey
    )
  }

  private def _record(
    evaluation: RuleEvaluationResult,
    error: Boolean,
    conclusion: Option[Conclusion],
    elapsedmillis: Long
  ): Unit = {
    val diagnostic = conclusion.map(ConclusionDiagnostics.classify)
    RuntimeDashboardMetrics.recordRuleExecution(
      operation = "fire",
      ruleset = evaluation.ruleSet.id.value,
      error = error,
      diagnostickey = diagnostic.map(_.diagnosticKey),
      diagnosticrecord = diagnostic.map(_.toRecord),
      elapsedmillis = Some(elapsedmillis)
    )
  }

  private def _elapsed_millis(started: Long): Long =
    (System.nanoTime() - started) / 1000000L
}
