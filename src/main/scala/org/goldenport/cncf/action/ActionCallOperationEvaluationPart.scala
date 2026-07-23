package org.goldenport.cncf.action

import cats.free.Free
import cats.syntax.flatMap.*
import cats.syntax.functor.*

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.operation.evaluation.{
  CorpusCandidateFact,
  ExperimentObservationFact,
  OperationEvaluationFactId,
  OperationEvaluationIntentId,
  OperationEvaluationLabel,
  OperationEvaluationMeasurement,
  OperationEvaluationSupplementalFact,
  OperationEvaluationSupplementalIntent,
  OperationEvaluationText
}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.schema.DataConfidentiality

/*
 * Protected application DSL for supplemental operation-evaluation evidence.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorOperationEvaluationPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def operation_evaluation_label(
    name: String,
    value: String
  ): ExecUowM[OperationEvaluationLabel] =
    exec_from(OperationEvaluationLabel.createC(name, value))

  protected final def operation_evaluation_measurement(
    name: String,
    value: BigDecimal,
    unit: Option[String] = None
  ): ExecUowM[OperationEvaluationMeasurement] =
    exec_from(OperationEvaluationMeasurement.createC(name, value, unit))

  protected final def corpus_candidate(
    summary: Option[String] = None,
    labels: Vector[OperationEvaluationLabel] = Vector.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Internal
  ): ExecUowM[OperationEvaluationIntentId] =
    exec_from(_corpus_candidate_fact(summary, labels, confidentiality))
      .flatMap(_stage_operation_evaluation_supplemental)

  protected final def experiment_observation(
    measurements: Vector[OperationEvaluationMeasurement],
    labels: Vector[OperationEvaluationLabel] = Vector.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Internal
  ): ExecUowM[OperationEvaluationIntentId] =
    exec_from(_experiment_observation_fact(measurements, labels, confidentiality))
      .flatMap(_stage_operation_evaluation_supplemental)

  private def _corpus_candidate_fact(
    summary: Option[String],
    labels: Vector[OperationEvaluationLabel],
    confidentiality: DataConfidentiality
  ): Consequence[CorpusCandidateFact] =
    for {
      correlation <- _active_operation_evaluation_correlation
      text <- summary.fold[Consequence[Option[OperationEvaluationText]]](Consequence.success(None))(
        OperationEvaluationText.parseC(_).map(Some(_))
      )
      occurredat = current_instant
      fact <- CorpusCandidateFact.createC(
        OperationEvaluationFactId.create("corpus-candidate", occurredat, execution_context.idGeneration),
        correlation,
        occurredat,
        text,
        labels,
        confidentiality
      )
    } yield fact

  private def _experiment_observation_fact(
    measurements: Vector[OperationEvaluationMeasurement],
    labels: Vector[OperationEvaluationLabel],
    confidentiality: DataConfidentiality
  ): Consequence[ExperimentObservationFact] =
    for {
      correlation <- _active_operation_evaluation_correlation
      occurredat = current_instant
      fact <- ExperimentObservationFact.createC(
        OperationEvaluationFactId.create("experiment-observation", occurredat, execution_context.idGeneration),
        correlation,
        occurredat,
        measurements,
        labels,
        confidentiality
      )
    } yield fact

  private def _active_operation_evaluation_correlation =
    Consequence.fromOption(
      execution_context.operationEvaluation.correlation,
      "Operation evaluation supplemental capture requires an active attempt"
    )

  private def _stage_operation_evaluation_supplemental(
    fact: OperationEvaluationSupplementalFact
  ): ExecUowM[OperationEvaluationIntentId] = {
    val stagedat = current_instant
    val intent = OperationEvaluationSupplementalIntent(
      OperationEvaluationIntentId.create(fact.factKind, stagedat, execution_context.idGeneration),
      fact,
      stagedat
    )
    ConsequenceT
      .liftF(Free.liftF[UnitOfWorkOp, Unit](UnitOfWorkOp.StageOperationEvaluationSupplemental(intent)))
      .map(_ => intent.id)
  }
}
