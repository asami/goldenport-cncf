package org.goldenport.cncf.spi

import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.spi.ai.runner.AiRunner
import org.goldenport.cncf.spi.geo.resolver.GeoResolver
import org.goldenport.cncf.spi.rule.engine.{InferenceEngine, RuleEngine}
import org.goldenport.cncf.spi.toolchain.runner.ToolchainRunner

/*
 * Common tracing support for provider-neutral CNCF SPI calls.
 *
 * @since   Jul.  9, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SpiTraceMetadata(
  contract: String,
  operation: String,
  socketComponent: String,
  providerComponent: String,
  socketName: Option[String] = None,
  providerInstance: Option[String] = None,
  selectorPurpose: Option[String] = None,
  selectorCapabilities: Set[String] = Set.empty,
  selectorTags: Set[String] = Set.empty,
  selectionProvider: Option[String] = None,
  selectionMode: Option[String] = None,
  selectionEngine: Option[String] = None,
  selectionBasis: Option[String] = None
) {
  def label: String = s"spi:$contract.$operation"

  def attributes: Map[String, String] =
    _clean(Map(
      "calltree_kind" -> "spi",
      "contract" -> contract,
      "operation" -> operation,
      "socket_component" -> socketComponent,
      "socket_name" -> socketName.getOrElse(""),
      "provider_component" -> providerComponent,
      "provider_instance" -> providerInstance.getOrElse(""),
      "selector_purpose" -> selectorPurpose.getOrElse(""),
      "selector_capabilities" -> selectorCapabilities.toVector.sorted.mkString(","),
      "selector_tags" -> selectorTags.toVector.sorted.mkString(","),
      "selection_provider" -> selectionProvider.getOrElse(""),
      "selection_mode" -> selectionMode.getOrElse(""),
      "selection_engine" -> selectionEngine.getOrElse(""),
      "selection_basis" -> selectionBasis.getOrElse("")
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
      case m: RuleEngine => RuleEngine.traced(m, metadata)
      case m: InferenceEngine => InferenceEngine.traced(m, metadata)
      case m: ToolchainRunner => ToolchainRunner.traced(m, metadata)
      case _ => service
    }

  private def _record(
    metadata: SpiTraceMetadata,
    error: Boolean,
    conclusion: Option[Conclusion],
    elapsedmillis: Long
  ): Unit = {
    val diagnostic = conclusion.map(ConclusionDiagnostics.classify)
    RuntimeDashboardMetrics.recordSpiInvocation(
      contract = metadata.contract,
      operation = metadata.operation,
      providerComponent = metadata.providerComponent,
      socketComponent = metadata.socketComponent,
      selectionBasis = metadata.selectionBasis,
      error = error,
      diagnosticKey = diagnostic.map(_.diagnosticKey),
      diagnosticRecord = diagnostic.map(_.toRecord),
      elapsedMillis = Some(elapsedmillis)
    )
  }

  private def _elapsed_millis(started: Long): Long =
    (System.nanoTime() - started) / 1000000L
}
