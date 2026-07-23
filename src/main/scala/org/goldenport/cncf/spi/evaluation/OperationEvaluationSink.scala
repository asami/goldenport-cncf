package org.goldenport.cncf.spi.evaluation

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.{CorpusCandidateFact, ExperimentObservationFact, OperationEvaluationDeliveryResult, OperationEvaluationDeliveryStatus, OperationEvaluationFact, OperationEvaluationLimitation, OperationEvaluationLimitationKind, OperationEvaluationSinkIdentity, OperationEvaluationStartFact, OperationEvaluationTerminalFact}
import org.goldenport.cncf.spi.{SpiContract, SpiSelection, SpiSocket, SpiTraceFailureDetail, SpiTraceMetadata, SpiTraceSupport}

/*
 * Provider-neutral Corpus and Experiment sinks for operation evaluation.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
trait CorpusEvaluationSink {
  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
  def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
}

object CorpusEvaluationSink {
  val CONTRACT_NAME = "corpus-evaluation-sink"

  val disabled: CorpusEvaluationSink = DisabledCorpusEvaluationSink

  def traced(
    underlying: CorpusEvaluationSink,
    metadata: SpiTraceMetadata
  ): CorpusEvaluationSink =
    TracedCorpusEvaluationSink(underlying, metadata)

  private object DisabledCorpusEvaluationSink extends CorpusEvaluationSink {
    def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)

    def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)
  }

  private final case class TracedCorpusEvaluationSink(
    underlying: CorpusEvaluationSink,
    base: SpiTraceMetadata
  ) extends CorpusEvaluationSink {
    def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "recordStart", fact)(ctx => underlying.recordStart(fact)(using ctx))

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "recordTerminal", fact)(ctx => underlying.recordTerminal(fact)(using ctx))

    def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "submitCandidate", fact)(ctx => underlying.submitCandidate(fact)(using ctx))
  }
}

trait ExperimentEvaluationSink {
  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
  def submitObservation(fact: ExperimentObservationFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult]
}

object ExperimentEvaluationSink {
  val CONTRACT_NAME = "experiment-evaluation-sink"

  val disabled: ExperimentEvaluationSink = DisabledExperimentEvaluationSink

  def traced(
    underlying: ExperimentEvaluationSink,
    metadata: SpiTraceMetadata
  ): ExperimentEvaluationSink =
    TracedExperimentEvaluationSink(underlying, metadata)

  private object DisabledExperimentEvaluationSink extends ExperimentEvaluationSink {
    def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)

    def submitObservation(fact: ExperimentObservationFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _discarded(CONTRACT_NAME, fact)
  }

  private final case class TracedExperimentEvaluationSink(
    underlying: ExperimentEvaluationSink,
    base: SpiTraceMetadata
  ) extends ExperimentEvaluationSink {
    def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "recordStart", fact)(ctx => underlying.recordStart(fact)(using ctx))

    def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "recordTerminal", fact)(ctx => underlying.recordTerminal(fact)(using ctx))

    def submitObservation(fact: ExperimentObservationFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
      _trace(base, "submitObservation", fact)(ctx => underlying.submitObservation(fact)(using ctx))
  }
}

trait CorpusEvaluationSinkSocket extends SpiSocket[CorpusEvaluationSink] {
  private var _corpus_evaluation_sink: Option[CorpusEvaluationSink] = None

  final def corpusEvaluationSink: CorpusEvaluationSink =
    _corpus_evaluation_sink.getOrElse(CorpusEvaluationSink.disabled)

  override final def spiContract: SpiContract[CorpusEvaluationSink] =
    SpiContract(CorpusEvaluationSink.CONTRACT_NAME, classOf[CorpusEvaluationSink])

  override def spiSelection: SpiSelection = SpiSelection()
  override final def spiRequired: Boolean = false
  override final def isSpiInstalled: Boolean = _corpus_evaluation_sink.nonEmpty

  override final def installSpi(spi: CorpusEvaluationSink): Unit =
    _corpus_evaluation_sink = Some(spi)
}

trait ExperimentEvaluationSinkSocket extends SpiSocket[ExperimentEvaluationSink] {
  private var _experiment_evaluation_sink: Option[ExperimentEvaluationSink] = None

  final def experimentEvaluationSink: ExperimentEvaluationSink =
    _experiment_evaluation_sink.getOrElse(ExperimentEvaluationSink.disabled)

  override final def spiContract: SpiContract[ExperimentEvaluationSink] =
    SpiContract(ExperimentEvaluationSink.CONTRACT_NAME, classOf[ExperimentEvaluationSink])

  override def spiSelection: SpiSelection = SpiSelection()
  override final def spiRequired: Boolean = false
  override final def isSpiInstalled: Boolean = _experiment_evaluation_sink.nonEmpty

  override final def installSpi(spi: ExperimentEvaluationSink): Unit =
    _experiment_evaluation_sink = Some(spi)
}

final class DeterministicCorpusEvaluationSink private (
  val sinkIdentity: OperationEvaluationSinkIdentity
) extends CorpusEvaluationSink {
  private val _facts = new ConcurrentLinkedQueue[OperationEvaluationFact]()

  def facts: Vector[OperationEvaluationFact] = _facts.iterator.asScala.toVector

  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  private def _record(fact: OperationEvaluationFact): Consequence[OperationEvaluationDeliveryResult] = {
    _facts.add(fact)
    OperationEvaluationDeliveryResult.createC(
      fact.id,
      sinkIdentity,
      OperationEvaluationDeliveryStatus.Delivered
    )
  }
}

object DeterministicCorpusEvaluationSink {
  def createC(
    socketcomponent: String,
    providercomponent: String,
    providerinstance: Option[String] = None
  ): Consequence[DeterministicCorpusEvaluationSink] =
    OperationEvaluationSinkIdentity
      .createC(CorpusEvaluationSink.CONTRACT_NAME, socketcomponent, providercomponent, providerinstance)
      .map(new DeterministicCorpusEvaluationSink(_))
}

final class DeterministicExperimentEvaluationSink private (
  val sinkIdentity: OperationEvaluationSinkIdentity
) extends ExperimentEvaluationSink {
  private val _facts = new ConcurrentLinkedQueue[OperationEvaluationFact]()

  def facts: Vector[OperationEvaluationFact] = _facts.iterator.asScala.toVector

  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  def submitObservation(fact: ExperimentObservationFact)(using ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
    _record(fact)

  private def _record(fact: OperationEvaluationFact): Consequence[OperationEvaluationDeliveryResult] = {
    _facts.add(fact)
    OperationEvaluationDeliveryResult.createC(
      fact.id,
      sinkIdentity,
      OperationEvaluationDeliveryStatus.Delivered
    )
  }
}

object DeterministicExperimentEvaluationSink {
  def createC(
    socketcomponent: String,
    providercomponent: String,
    providerinstance: Option[String] = None
  ): Consequence[DeterministicExperimentEvaluationSink] =
    OperationEvaluationSinkIdentity
      .createC(ExperimentEvaluationSink.CONTRACT_NAME, socketcomponent, providercomponent, providerinstance)
      .map(new DeterministicExperimentEvaluationSink(_))
}

private def _trace[A <: OperationEvaluationFact](
  base: SpiTraceMetadata,
  operation: String,
  fact: A
)(body: ExecutionContext => Consequence[OperationEvaluationDeliveryResult])(using ctx: ExecutionContext): Consequence[OperationEvaluationDeliveryResult] =
  SpiTraceSupport.trace(base.withOperation(operation), (result: OperationEvaluationDeliveryResult) => Map(
    "fact_kind" -> fact.factKind,
    "delivery_status" -> result.status.token,
    "limitation_count" -> result.limitations.size.toString
  ), SpiTraceFailureDetail.Structural) {
    for {
      sink <- OperationEvaluationSinkIdentity.createC(
        base.contract,
        base.socketComponent,
        base.providerComponent,
        base.providerInstance
      )
      delivered <-
        if (ctx.operationEvaluation.isSinkActive(sink))
          OperationEvaluationDeliveryResult.createC(
            fact.id,
            sink,
            OperationEvaluationDeliveryStatus.Discarded,
            Vector(OperationEvaluationLimitation(OperationEvaluationLimitationKind.ReentrantSuppressed))
          )
        else
          ExecutionContext.withActiveOperationEvaluationSink(ctx, sink).flatMap(body)
      normalized <- OperationEvaluationDeliveryResult.createC(
        fact.id,
        sink,
        delivered.status,
        delivered.limitations,
        delivered.confidentiality
      )
    } yield normalized
  }

private def _discarded(
  contract: String,
  fact: OperationEvaluationFact
): Consequence[OperationEvaluationDeliveryResult] =
  for {
    sink <- OperationEvaluationSinkIdentity.createC(contract, "disabled", "disabled")
    result <- OperationEvaluationDeliveryResult.createC(
      fact.id,
      sink,
      OperationEvaluationDeliveryStatus.Discarded,
      Vector(OperationEvaluationLimitation(OperationEvaluationLimitationKind.Unavailable))
    )
  } yield result
