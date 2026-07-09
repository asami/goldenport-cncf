package org.goldenport.cncf.spi

import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.spi.ai.runner.AiRunner
import org.goldenport.cncf.spi.geo.resolver.GeoResolver
import org.goldenport.cncf.spi.toolchain.runner.ToolchainRunner

/*
 * Common tracing support for provider-neutral CNCF SPI calls.
 *
 * @since   Jul.  9, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SpiTraceMetadata(
  contract: String,
  operation: String,
  socketComponent: String,
  providerComponent: String,
  selectionProvider: Option[String] = None,
  selectionMode: Option[String] = None,
  selectionEngine: Option[String] = None
) {
  def label: String = s"spi:$contract.$operation"

  def attributes: Map[String, String] =
    _clean(Map(
      "calltree_kind" -> "spi",
      "contract" -> contract,
      "operation" -> operation,
      "socket_component" -> socketComponent,
      "provider_component" -> providerComponent,
      "selection_provider" -> selectionProvider.getOrElse(""),
      "selection_mode" -> selectionMode.getOrElse(""),
      "selection_engine" -> selectionEngine.getOrElse("")
    ))

  def withOperation(name: String): SpiTraceMetadata =
    copy(operation = name)

  private def _clean(values: Map[String, String]): Map[String, String] =
    values.filter(_._2.nonEmpty)
}

object SpiTraceSupport {
  def trace[A](
    metadata: SpiTraceMetadata,
    resultAttributes: A => Map[String, String] = (_: A) => Map.empty
  )(body: => Consequence[A])(using ExecutionContext): Consequence[A] = {
    val calltree = summon[ExecutionContext].observability.callTreeContext
    val started = System.nanoTime()
    if (calltree.isEnabled)
      calltree.enter(metadata.label, metadata.attributes)
    try {
      val result = body
      val elapsed = _elapsed_millis(started)
      result match {
        case Consequence.Success(value) =>
          _record(metadata, error = false, None, elapsed)
          if (calltree.isEnabled)
            calltree.leave(resultAttributes(value) ++ Map(
              "outcome" -> "success",
              "duration_ms" -> elapsed.toString
            ))
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          _record(metadata, error = true, Some(conclusion), elapsed)
          if (calltree.isEnabled)
            calltree.leave(Map(
              "outcome" -> "failure",
              "duration_ms" -> elapsed.toString,
              "status" -> conclusion.status.webCode.code.toString,
              "diagnostic_key" -> diagnostic.diagnosticKey,
              "error" -> conclusion.display
            ))
      }
      result
    } catch {
      case NonFatal(e) =>
        val conclusion = Conclusion.from(e)
        val diagnostic = ConclusionDiagnostics.classify(conclusion)
        val elapsed = _elapsed_millis(started)
        _record(metadata, error = true, Some(conclusion), elapsed)
        if (calltree.isEnabled)
          calltree.leave(Map(
            "outcome" -> "failure",
            "duration_ms" -> elapsed.toString,
            "diagnostic_key" -> diagnostic.diagnosticKey,
            "error" -> conclusion.display
          ))
        throw e
    }
  }

  def wrapInstalled(
    service: Any,
    metadata: SpiTraceMetadata
  ): Any =
    service match {
      case m: AiRunner => AiRunner.traced(m, metadata)
      case m: GeoResolver => GeoResolver.traced(m, metadata)
      case m: ToolchainRunner => ToolchainRunner.traced(m, metadata)
      case _ => service
    }

  private def _record(
    metadata: SpiTraceMetadata,
    error: Boolean,
    conclusion: Option[Conclusion],
    elapsedMillis: Long
  ): Unit = {
    val diagnostic = conclusion.map(ConclusionDiagnostics.classify)
    RuntimeDashboardMetrics.recordSpiInvocation(
      contract = metadata.contract,
      operation = metadata.operation,
      providerComponent = metadata.providerComponent,
      socketComponent = metadata.socketComponent,
      error = error,
      diagnosticKey = diagnostic.map(_.diagnosticKey),
      diagnosticRecord = diagnostic.map(_.toRecord),
      elapsedMillis = Some(elapsedMillis)
    )
  }

  private def _elapsed_millis(started: Long): Long =
    (System.nanoTime() - started) / 1000000L
}
