package org.goldenport.cncf.operation.evaluation

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * Provider-neutral admission boundary for declared operation evaluation.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationEvaluationAssignment(
    variant: OperationEvaluationName,
    executionPlanReference: Option[OperationEvaluationText] = None
) {
  def toRecord: Record = Record.dataAuto(
    "variant"                -> variant.print,
    "executionPlanReference" -> executionPlanReference.map(_.print)
  )
}

final case class OperationEvaluationAdmissionRequest(
    operation: OperationEvaluationOperationIdentity,
    declaration: CmlOperationEvaluationDeclaration
) {
  def toRecord: Record = Record.dataAuto(
    "operation"   -> operation.toRecord,
    "declaration" -> declaration.toRecord
  )
}

sealed abstract class OperationEvaluationAdmission {
  def toRecord: Record
}

object OperationEvaluationAdmission {
  final case class Admitted private[evaluation] (
      corpus: Option[CorpusEvaluationCorrelation] = None,
      experiment: Option[ExperimentEvaluationCorrelation] = None,
      assignment: Option[OperationEvaluationAssignment] = None
  ) extends OperationEvaluationAdmission {
    def toRecord: Record = Record.dataAuto(
      "status"     -> "admitted",
      "corpus"     -> corpus.map(_.toRecord),
      "experiment" -> experiment.map(_.toRecord),
      "assignment" -> assignment.map(_.toRecord)
    )
  }

  final case class Unavailable private[evaluation] (
      limitations: Vector[OperationEvaluationLimitation]
  ) extends OperationEvaluationAdmission {
    def toRecord: Record = Record.dataAuto(
      "status" -> "unavailable",
      "limitations" -> limitations.map(
        OperationEvaluationAdmissionLimitationDiagnostic.from(_).toRecord
      )
    )
  }

  def unavailable(
      kind: OperationEvaluationLimitationKind = OperationEvaluationLimitationKind.Unavailable
  ): OperationEvaluationAdmission =
    new Unavailable(Vector(OperationEvaluationLimitation(kind)))

  def admittedC(
      corpus: Option[CorpusEvaluationCorrelation] = None,
      experiment: Option[ExperimentEvaluationCorrelation] = None,
      assignment: Option[OperationEvaluationAssignment] = None
  ): Consequence[Admitted] =
    if (corpus.isEmpty && experiment.isEmpty && assignment.isEmpty)
      Consequence.argumentInvalid(
        "admission",
        "Corpus membership or complete Experiment assignment",
        "empty"
      )
    else if (experiment.nonEmpty != assignment.nonEmpty)
      Consequence.argumentInvalid(
        "admission",
        "complete Experiment correlation and assignment",
        "incomplete"
      )
    else
      Consequence.success(new Admitted(corpus, experiment, assignment))

  def unavailableC(
      limitations: Vector[OperationEvaluationLimitation]
  ): Consequence[OperationEvaluationAdmission] =
    OperationEvaluationAdmissionLimitationDiagnostic
      .validateC(limitations)
      .map(_ => new Unavailable(limitations))
}

trait OperationEvaluationResolver {
  def resolve(
      request: OperationEvaluationAdmissionRequest
  )(using ExecutionContext): Consequence[OperationEvaluationAdmission]
}

object OperationEvaluationResolver {
  val disabled: OperationEvaluationResolver = new OperationEvaluationResolver {
    def resolve(
        request: OperationEvaluationAdmissionRequest
    )(using ExecutionContext): Consequence[OperationEvaluationAdmission] =
      Consequence.success(OperationEvaluationAdmission.unavailable())
  }
}

final class DeterministicOperationEvaluationResolver private (
    resolvefn: OperationEvaluationAdmissionRequest => Consequence[OperationEvaluationAdmission]
) extends OperationEvaluationResolver {
  private val _requests = new ConcurrentLinkedQueue[OperationEvaluationAdmissionRequest]()

  def requests: Vector[OperationEvaluationAdmissionRequest] =
    _requests.iterator.asScala.toVector

  def resolve(
      request: OperationEvaluationAdmissionRequest
  )(using ExecutionContext): Consequence[OperationEvaluationAdmission] = {
    _requests.add(request)
    resolvefn(request)
  }
}

object DeterministicOperationEvaluationResolver {
  def fixed(
      admission: OperationEvaluationAdmission
  ): DeterministicOperationEvaluationResolver =
    new DeterministicOperationEvaluationResolver(_ => Consequence.success(admission))

  def failing(
      conclusion: Conclusion
  ): DeterministicOperationEvaluationResolver =
    new DeterministicOperationEvaluationResolver(_ => Consequence.Failure(conclusion))

  def apply(
      resolvefn: OperationEvaluationAdmissionRequest => Consequence[OperationEvaluationAdmission]
  ): DeterministicOperationEvaluationResolver =
    new DeterministicOperationEvaluationResolver(resolvefn)
}

enum OperationEvaluationAdmissionStatus(val token: String) {
  case Admitted    extends OperationEvaluationAdmissionStatus("admitted")
  case Unavailable extends OperationEvaluationAdmissionStatus("unavailable")
  case Rejected    extends OperationEvaluationAdmissionStatus("rejected")
  case Failed      extends OperationEvaluationAdmissionStatus("failed")
}

final case class OperationEvaluationAdmissionLimitationDiagnostic private (
    kind: OperationEvaluationLimitationKind,
    policy: Option[OperationEvaluationName],
    diagnostic: Option[OperationEvaluationDiagnosticKey]
) {
  def toRecord: Record = Record.dataAuto(
    "kind"       -> kind.token,
    "policy"     -> policy.map(_.print),
    "diagnostic" -> diagnostic.map(_.token)
  )
}

object OperationEvaluationAdmissionLimitationDiagnostic {
  def from(
      limitation: OperationEvaluationLimitation
  ): OperationEvaluationAdmissionLimitationDiagnostic =
    OperationEvaluationAdmissionLimitationDiagnostic(
      limitation.kind,
      limitation.policy,
      limitation.diagnostic.map(OperationEvaluationDiagnosticKey.fromClassification)
    )

  private[evaluation] def validateC(
      limitations: Vector[OperationEvaluationLimitation]
  ): Consequence[Unit] =
    if (limitations.isEmpty)
      Consequence.argumentInvalid("limitations", "one or more bounded limitations", "empty")
    else if (limitations.length > OperationEvaluationDeliveryResult.MAXIMUM_LIMITATIONS)
      Consequence.argumentLimitExceeded(
        "limitations",
        OperationEvaluationDeliveryResult.MAXIMUM_LIMITATIONS,
        limitations.length,
        "operation-evaluation.admission"
      )
    else
      Consequence.unit
}

final case class OperationEvaluationAdmissionDiagnostic private (
    operation: OperationEvaluationOperationIdentity,
    status: OperationEvaluationAdmissionStatus,
    limitations: Vector[OperationEvaluationAdmissionLimitationDiagnostic],
    diagnostic: Option[OperationEvaluationDiagnosticKey]
) {
  def toRecord: Record = Record.dataAuto(
    "operation"   -> operation.toRecord,
    "status"      -> status.token,
    "limitations" -> limitations.map(_.toRecord),
    "diagnostic"  -> diagnostic.map(_.token)
  )
}

object OperationEvaluationAdmissionDiagnostic {
  def admitted(
      operation: OperationEvaluationOperationIdentity
  ): OperationEvaluationAdmissionDiagnostic =
    OperationEvaluationAdmissionDiagnostic(
      operation,
      OperationEvaluationAdmissionStatus.Admitted,
      Vector.empty,
      None
    )

  def unavailableC(
      operation: OperationEvaluationOperationIdentity,
      limitations: Vector[OperationEvaluationLimitation]
  ): Consequence[OperationEvaluationAdmissionDiagnostic] =
    _from_c(operation, OperationEvaluationAdmissionStatus.Unavailable, limitations, None)

  def rejectedC(
      operation: OperationEvaluationOperationIdentity,
      limitations: Vector[OperationEvaluationLimitation]
  ): Consequence[OperationEvaluationAdmissionDiagnostic] =
    _from_c(operation, OperationEvaluationAdmissionStatus.Rejected, limitations, None)

  def failed(
      operation: OperationEvaluationOperationIdentity,
      conclusion: Conclusion
  ): OperationEvaluationAdmissionDiagnostic =
    OperationEvaluationAdmissionDiagnostic(
      operation,
      OperationEvaluationAdmissionStatus.Failed,
      Vector.empty,
      Some(OperationEvaluationDiagnosticKey.fromClassification(
        org.goldenport.cncf.observability.ConclusionDiagnostics.classify(conclusion)
      ))
    )

  private def _from_c(
      operation: OperationEvaluationOperationIdentity,
      status: OperationEvaluationAdmissionStatus,
      limitations: Vector[OperationEvaluationLimitation],
      diagnostic: Option[OperationEvaluationDiagnosticKey]
  ): Consequence[OperationEvaluationAdmissionDiagnostic] =
    OperationEvaluationAdmissionLimitationDiagnostic
      .validateC(limitations)
      .map { _ =>
        OperationEvaluationAdmissionDiagnostic(
          operation,
          status,
          limitations.map(OperationEvaluationAdmissionLimitationDiagnostic.from),
          diagnostic
        )
      }
}
