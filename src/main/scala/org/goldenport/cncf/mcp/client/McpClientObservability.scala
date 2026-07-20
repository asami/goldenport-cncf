package org.goldenport.cncf.mcp.client

import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.record.Record

/*
 * Payload-safe observability at the consumer-side MCP client boundary.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[client] object McpClientObservability {
  def catalog[A](
    serversetid: McpServerSetId
  )(body: => Consequence[A])(using ExecutionContext): Consequence[A] =
    _trace("catalog", serversetid, None)(body)

  def invoke[A](
    serversetid: McpServerSetId,
    toolidentity: McpToolIdentity
  )(body: => Consequence[A])(using ExecutionContext): Consequence[A] =
    _trace("invoke", serversetid, Some(toolidentity))(body)

  private def _trace[A](
    operation: String,
    serversetid: McpServerSetId,
    toolidentity: Option[McpToolIdentity]
  )(body: => Consequence[A])(using ExecutionContext): Consequence[A] = {
    val calltree = summon[ExecutionContext].observability.callTreeContext
    val startedat = System.nanoTime()
    try {
      val result = body
      val elapsedmillis = _elapsed_millis(startedat)
      result match {
        case success: Consequence.Success[A] =>
          _record(operation, serversetid, toolidentity, error = false, None, elapsedmillis)
          _mark_calltree(calltree, operation, serversetid, toolidentity, Map(
              "outcome" -> "success",
              "duration_ms" -> elapsedmillis.toString
            ))
          success
        case failure: Consequence.Failure[A] =>
          val diagnostic = ConclusionDiagnostics.classify(failure.conclusion)
          _record(operation, serversetid, toolidentity, error = true, Some(failure.conclusion), elapsedmillis)
          _mark_calltree(calltree, operation, serversetid, toolidentity, Map(
              "outcome" -> "failure",
              "duration_ms" -> elapsedmillis.toString,
              "status" -> failure.conclusion.status.webCode.code.toString,
              "diagnostic_key" -> diagnostic.diagnosticKey
            ))
          failure
      }
    } catch {
      case NonFatal(e) =>
        val conclusion = Conclusion.from(e)
        val diagnostic = ConclusionDiagnostics.classify(conclusion)
        val elapsedmillis = _elapsed_millis(startedat)
        _record(operation, serversetid, toolidentity, error = true, Some(conclusion), elapsedmillis)
        _mark_calltree(calltree, operation, serversetid, toolidentity, Map(
            "outcome" -> "failure",
            "duration_ms" -> elapsedmillis.toString,
            "status" -> conclusion.status.webCode.code.toString,
            "diagnostic_key" -> diagnostic.diagnosticKey
          ))
        throw e
    }
  }

  private def _attributes(
    operation: String,
    serversetid: McpServerSetId,
    toolidentity: Option[McpToolIdentity]
  ): Map[String, String] =
    Map(
      "calltree_kind" -> "mcp-client",
      "operation" -> operation,
      "server_set" -> serversetid.print
    ) ++ toolidentity.map { identity =>
      Map(
        "server" -> identity.serverId.print,
        "tool" -> identity.toolName.print
      )
    }.getOrElse(Map.empty)

  private def _mark_calltree(
    calltree: org.goldenport.cncf.observability.CallTreeContext,
    operation: String,
    serversetid: McpServerSetId,
    toolidentity: Option[McpToolIdentity],
    outcomeattributes: Map[String, String]
  ): Unit =
    if (calltree.isEnabled)
      calltree.synchronized {
        calltree.mark(
          s"mcp-client:$operation",
          _attributes(operation, serversetid, toolidentity) ++ outcomeattributes
        )
      }

  private def _record(
    operation: String,
    serversetid: McpServerSetId,
    toolidentity: Option[McpToolIdentity],
    error: Boolean,
    conclusion: Option[Conclusion],
    elapsedmillis: Long
  ): Unit = {
    val diagnostic = conclusion.map(ConclusionDiagnostics.classify)
    RuntimeDashboardMetrics.recordMcpClientInvocation(
      operation = operation,
      serverSet = serversetid.print,
      server = toolidentity.map(_.serverId.print),
      tool = toolidentity.map(_.toolName.print),
      error = error,
      diagnosticKey = diagnostic.map(_.diagnosticKey),
      diagnosticRecord = diagnostic.map(_diagnostic_record),
      elapsedMillis = Some(elapsedmillis)
    )
  }

  private def _diagnostic_record(
    diagnostic: ConclusionDiagnostics.Classification
  ): Record =
    Record.dataAuto(
      "diagnosticKey" -> diagnostic.diagnosticKey,
      "taxonomyCategory" -> diagnostic.taxonomyCategory,
      "taxonomySymptom" -> diagnostic.taxonomySymptom,
      "causeKind" -> diagnostic.causeKind,
      "webStatus" -> diagnostic.webStatus,
      "policy" -> diagnostic.policy.filter(_.startsWith("mcp-client."))
    )

  private def _elapsed_millis(startedat: Long): Long =
    (System.nanoTime() - startedat) / 1000000L
}
