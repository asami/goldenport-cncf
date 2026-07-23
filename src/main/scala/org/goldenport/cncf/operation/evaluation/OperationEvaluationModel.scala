package org.goldenport.cncf.operation.evaluation

import java.time.{Duration, Instant}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, IdGenerationContext, TraceId}
import org.goldenport.cncf.job.{JobId, TaskId}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality

/*
 * Provider-neutral, payload-safe values for operation evaluation capture.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationEvaluationExecutionId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "operation_evaluation_execution", timestamp, entropy)

object OperationEvaluationExecutionId {
  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): OperationEvaluationExecutionId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): OperationEvaluationExecutionId =
    OperationEvaluationExecutionId(
      idgeneration.namespace.major,
      idgeneration.namespace.minor,
      Some(timestamp),
      Some(idgeneration.opaqueId(s"operation-evaluation.execution.$purpose"))
    )

  def parse(value: String): Consequence[OperationEvaluationExecutionId] =
    UniversalId.parseParts(value, "operation_evaluation_execution").map(x =>
      OperationEvaluationExecutionId(x.major, x.minor, Some(x.timestamp), Some(x.entropy))
    )
}

final case class OperationEvaluationAttemptId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "operation_evaluation_attempt", timestamp, entropy)

object OperationEvaluationAttemptId {
  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): OperationEvaluationAttemptId =
    OperationEvaluationAttemptId(
      idgeneration.namespace.major,
      idgeneration.namespace.minor,
      Some(timestamp),
      Some(idgeneration.opaqueId(s"operation-evaluation.attempt.$purpose"))
    )

  def parse(value: String): Consequence[OperationEvaluationAttemptId] =
    UniversalId.parseParts(value, "operation_evaluation_attempt").map(x =>
      OperationEvaluationAttemptId(x.major, x.minor, Some(x.timestamp), Some(x.entropy))
    )
}

final case class OperationEvaluationFactId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "operation_evaluation_fact", timestamp, entropy)

object OperationEvaluationFactId {
  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): OperationEvaluationFactId =
    OperationEvaluationFactId(
      idgeneration.namespace.major,
      idgeneration.namespace.minor,
      Some(timestamp),
      Some(idgeneration.opaqueId(s"operation-evaluation.fact.$purpose"))
    )

  def parse(value: String): Consequence[OperationEvaluationFactId] =
    UniversalId.parseParts(value, "operation_evaluation_fact").map(x =>
      OperationEvaluationFactId(x.major, x.minor, Some(x.timestamp), Some(x.entropy))
    )
}

final case class OperationEvaluationIntentId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "operation_evaluation_intent", timestamp, entropy)

object OperationEvaluationIntentId {
  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): OperationEvaluationIntentId =
    OperationEvaluationIntentId(
      idgeneration.namespace.major,
      idgeneration.namespace.minor,
      Some(timestamp),
      Some(idgeneration.opaqueId(s"operation-evaluation.intent.$purpose"))
    )

  def parse(value: String): Consequence[OperationEvaluationIntentId] =
    UniversalId.parseParts(value, "operation_evaluation_intent").map(x =>
      OperationEvaluationIntentId(x.major, x.minor, Some(x.timestamp), Some(x.entropy))
    )
}

final case class OperationEvaluationName private (value: String) {
  def print: String = value
}

object OperationEvaluationName {
  private val _pattern = "[a-z][a-z0-9._-]{0,127}".r

  def option(value: String): Option[OperationEvaluationName] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Some(OperationEvaluationName(text))
      case _ => None
    }
  }

  def parseC(value: String): Consequence[OperationEvaluationName] =
    option(value)
      .map(Consequence.success)
      .getOrElse(Consequence.argumentFormatError("name", "bounded operation-evaluation name", value))

  private[evaluation] def fromResolvedRoute(value: String): OperationEvaluationName =
    option(value).getOrElse {
      val text = Option(value).getOrElse("")
      val digest = MessageDigest
        .getInstance("SHA-256")
        .digest(text.getBytes(StandardCharsets.UTF_8))
        .iterator
        .map(byte => f"${byte & 0xff}%02x")
        .mkString
        .take(16)
      val normalized = text.toLowerCase(Locale.ROOT)
        .map {
          case c if (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-' => c
          case _ => '_'
        }
        .mkString
        .replaceAll("_+", "_")
        .stripPrefix("_")
        .stripSuffix("_")
      val prefixed = normalized.headOption match {
        case Some(c) if c >= 'a' && c <= 'z' => normalized
        case _ => s"route_$normalized"
      }
      OperationEvaluationName(s"${prefixed.take(110)}_$digest")
    }

  // Generated metadata has already passed the CML decoder's validation boundary.
  def unsafe(value: String): OperationEvaluationName =
    option(value).getOrElse(throw new IllegalArgumentException("Invalid operation-evaluation name"))
}

final case class OperationEvaluationText private (value: String) {
  def print: String = value
}

object OperationEvaluationText {
  val MAXIMUM_BYTES: Int = 1024

  def parseC(value: String): Consequence[OperationEvaluationText] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val bytes = text.getBytes(StandardCharsets.UTF_8).length
    if (text.isEmpty)
      Consequence.argumentInvalid("text", "non-empty operation-evaluation text", "empty")
    else if (text.exists(_.isControl))
      Consequence.argumentFormatError("text", "operation-evaluation text without control characters", "invalid")
    else if (bytes > MAXIMUM_BYTES)
      Consequence.argumentLimitExceeded("text", MAXIMUM_BYTES, bytes, "operation-evaluation.text")
    else
      Consequence.success(OperationEvaluationText(text))
  }
}

abstract class OperationEvaluationExternalReference {
  def value: String
  final def print: String = value
}

private object OperationEvaluationExternalReference {
  val MAXIMUM_BYTES: Int = 256

  def parseC(value: String, parameter: String): Consequence[String] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val bytes = text.getBytes(StandardCharsets.UTF_8).length
    if (text.isEmpty || bytes > MAXIMUM_BYTES || text.exists(_.isControl))
      Consequence.argumentFormatError(parameter, "bounded opaque external reference", "invalid")
    else
      Consequence.success(text)
  }
}

final case class CorpusRevisionReference private (value: String)
    extends OperationEvaluationExternalReference
object CorpusRevisionReference {
  def parseC(value: String): Consequence[CorpusRevisionReference] =
    OperationEvaluationExternalReference.parseC(value, "corpusRevision").map(CorpusRevisionReference(_))
}

final case class CorpusCaseReference private (value: String)
    extends OperationEvaluationExternalReference
object CorpusCaseReference {
  def parseC(value: String): Consequence[CorpusCaseReference] =
    OperationEvaluationExternalReference.parseC(value, "corpusCase").map(CorpusCaseReference(_))
}

final case class ExperimentReference private (value: String)
    extends OperationEvaluationExternalReference
object ExperimentReference {
  def parseC(value: String): Consequence[ExperimentReference] =
    OperationEvaluationExternalReference.parseC(value, "experiment").map(ExperimentReference(_))
}

final case class ExperimentArmReference private (value: String)
    extends OperationEvaluationExternalReference
object ExperimentArmReference {
  def parseC(value: String): Consequence[ExperimentArmReference] =
    OperationEvaluationExternalReference.parseC(value, "experimentArm").map(ExperimentArmReference(_))
}

final case class ExperimentRunReference private (value: String)
    extends OperationEvaluationExternalReference
object ExperimentRunReference {
  def parseC(value: String): Consequence[ExperimentRunReference] =
    OperationEvaluationExternalReference.parseC(value, "experimentRun").map(ExperimentRunReference(_))
}

final case class OperationEvaluationOperationIdentity private (
  component: OperationEvaluationName,
  service: OperationEvaluationName,
  operation: OperationEvaluationName
) {
  def print: String = s"${component.print}.${service.print}.${operation.print}"

  def toRecord: Record = Record.data(
    "component" -> component.print,
    "service" -> service.print,
    "operation" -> operation.print
  )
}

object OperationEvaluationOperationIdentity {
  def fromResolvedRoute(
    component: String,
    service: String,
    operation: String
  ): OperationEvaluationOperationIdentity =
    OperationEvaluationOperationIdentity(
      OperationEvaluationName.fromResolvedRoute(component),
      OperationEvaluationName.fromResolvedRoute(service),
      OperationEvaluationName.fromResolvedRoute(operation)
    )

  def createC(
    component: String,
    service: String,
    operation: String
  ): Consequence[OperationEvaluationOperationIdentity] =
    for {
      c <- OperationEvaluationName.parseC(component)
      s <- OperationEvaluationName.parseC(service)
      o <- OperationEvaluationName.parseC(operation)
    } yield OperationEvaluationOperationIdentity(c, s, o)
}

final case class CorpusEvaluationCorrelation private (
  revision: CorpusRevisionReference,
  caseReference: Option[CorpusCaseReference]
) {
  def toRecord: Record = Record.dataAuto(
    "revision" -> revision.print,
    "case" -> caseReference.map(_.print)
  )
}

object CorpusEvaluationCorrelation {
  def create(
    revision: CorpusRevisionReference,
    casereference: Option[CorpusCaseReference] = None
  ): CorpusEvaluationCorrelation = CorpusEvaluationCorrelation(revision, casereference)
}

final case class ExperimentEvaluationCorrelation private (
  experiment: ExperimentReference,
  arm: Option[ExperimentArmReference],
  run: Option[ExperimentRunReference],
  corpusRevision: Option[CorpusRevisionReference]
) {
  def toRecord: Record = Record.dataAuto(
    "experiment" -> experiment.print,
    "arm" -> arm.map(_.print),
    "run" -> run.map(_.print),
    "corpusRevision" -> corpusRevision.map(_.print)
  )
}

object ExperimentEvaluationCorrelation {
  def createC(
    experiment: ExperimentReference,
    arm: Option[ExperimentArmReference] = None,
    run: Option[ExperimentRunReference] = None,
    corpusrevision: Option[CorpusRevisionReference] = None
  ): Consequence[ExperimentEvaluationCorrelation] =
    if (run.nonEmpty && (arm.isEmpty || corpusrevision.isEmpty))
      Consequence.argumentInvalid(
        "experimentRun",
        "experiment arm and corpus revision for a run",
        "incomplete correlation"
      )
    else
      Consequence.success(ExperimentEvaluationCorrelation(experiment, arm, run, corpusrevision))
}

final case class OperationEvaluationCorrelation(
  executionId: OperationEvaluationExecutionId,
  attemptId: OperationEvaluationAttemptId,
  operation: OperationEvaluationOperationIdentity,
  parentExecutionId: Option[OperationEvaluationExecutionId] = None,
  executionContextId: Option[ExecutionContextId] = None,
  jobId: Option[JobId] = None,
  taskId: Option[TaskId] = None,
  traceId: Option[TraceId] = None,
  observabilityCorrelationId: Option[CorrelationId] = None,
  corpus: Option[CorpusEvaluationCorrelation] = None,
  experiment: Option[ExperimentEvaluationCorrelation] = None
) {
  def toRecord: Record = Record.dataAuto(
    "executionId" -> executionId.toString,
    "attemptId" -> attemptId.toString,
    "operation" -> operation.toRecord,
    "parentExecutionId" -> parentExecutionId.map(_.toString),
    "executionContextId" -> executionContextId.map(_.toString),
    "jobId" -> jobId.map(_.toString),
    "taskId" -> taskId.map(_.toString),
    "traceId" -> traceId.map(_.toString),
    "observabilityCorrelationId" -> observabilityCorrelationId.map(_.toString),
    "corpus" -> corpus.map(_.toRecord),
    "experiment" -> experiment.map(_.toRecord)
  )
}

enum OperationEvaluationFactSource(val token: String) {
  case Framework extends OperationEvaluationFactSource("framework")
  case Application extends OperationEvaluationFactSource("application")
  case Provider extends OperationEvaluationFactSource("provider")
}

enum OperationEvaluationOutcome(val token: String) {
  case Success extends OperationEvaluationOutcome("success")
  case Failure extends OperationEvaluationOutcome("failure")
  case Timeout extends OperationEvaluationOutcome("timeout")
  case Cancellation extends OperationEvaluationOutcome("cancellation")
}

abstract class OperationEvaluationFact {
  def id: OperationEvaluationFactId
  def correlation: OperationEvaluationCorrelation
  def source: OperationEvaluationFactSource
  def confidentiality: DataConfidentiality
  def occurredAt: Instant
  def factKind: String
  def toRecord: Record
}

final case class OperationEvaluationStartFact private (
  id: OperationEvaluationFactId,
  correlation: OperationEvaluationCorrelation,
  occurredAt: Instant
) extends OperationEvaluationFact {
  val source: OperationEvaluationFactSource = OperationEvaluationFactSource.Framework
  val confidentiality: DataConfidentiality = DataConfidentiality.Internal
  val factKind: String = "operation-start"

  def toRecord: Record = Record.data(
    "id" -> id.toString,
    "kind" -> factKind,
    "source" -> source.token,
    "confidentiality" -> confidentiality.label,
    "occurredAt" -> occurredAt.toString,
    "correlation" -> correlation.toRecord
  )
}

object OperationEvaluationStartFact {
  def create(
    id: OperationEvaluationFactId,
    correlation: OperationEvaluationCorrelation,
    occurredat: Instant
  ): OperationEvaluationStartFact = OperationEvaluationStartFact(id, correlation, occurredat)
}

final case class OperationEvaluationTerminalFact private (
  id: OperationEvaluationFactId,
  correlation: OperationEvaluationCorrelation,
  occurredAt: Instant,
  outcome: OperationEvaluationOutcome,
  duration: Duration,
  diagnostic: Option[ConclusionDiagnostics.Classification]
) extends OperationEvaluationFact {
  val source: OperationEvaluationFactSource = OperationEvaluationFactSource.Framework
  val confidentiality: DataConfidentiality = DataConfidentiality.Internal
  val factKind: String = "operation-terminal"

  def toRecord: Record = Record.dataAuto(
    "id" -> id.toString,
    "kind" -> factKind,
    "source" -> source.token,
    "confidentiality" -> confidentiality.label,
    "occurredAt" -> occurredAt.toString,
    "outcome" -> outcome.token,
    "durationMillis" -> duration.toMillis,
    "diagnostic" -> diagnostic.map(_.toRecord),
    "correlation" -> correlation.toRecord
  )
}

object OperationEvaluationTerminalFact {
  def createC(
    id: OperationEvaluationFactId,
    correlation: OperationEvaluationCorrelation,
    occurredat: Instant,
    outcome: OperationEvaluationOutcome,
    duration: Duration,
    diagnostic: Option[ConclusionDiagnostics.Classification] = None
  ): Consequence[OperationEvaluationTerminalFact] =
    if (duration.isNegative)
      Consequence.argumentInvalid("duration", "non-negative duration", duration.toString)
    else if (outcome == OperationEvaluationOutcome.Success && diagnostic.nonEmpty)
      Consequence.argumentInvalid("diagnostic", "no failure diagnostic for success", "present")
    else
      Consequence.success(
        OperationEvaluationTerminalFact(id, correlation, occurredat, outcome, duration, diagnostic)
      )
}

final case class OperationEvaluationLabel private (
  name: OperationEvaluationName,
  value: OperationEvaluationText
) {
  def toRecord: Record = Record.data("name" -> name.print, "value" -> value.print)
}

object OperationEvaluationLabel {
  def createC(name: String, value: String): Consequence[OperationEvaluationLabel] =
    for {
      n <- OperationEvaluationName.parseC(name)
      v <- OperationEvaluationText.parseC(value)
    } yield OperationEvaluationLabel(n, v)
}

final case class OperationEvaluationMeasurement private (
  name: OperationEvaluationName,
  value: BigDecimal,
  unit: Option[OperationEvaluationName]
) {
  def toRecord: Record = Record.dataAuto(
    "name" -> name.print,
    "value" -> value,
    "unit" -> unit.map(_.print)
  )
}

object OperationEvaluationMeasurement {
  val MAXIMUM_PRECISION: Int = 34
  val MINIMUM_SCALE: Int = -128
  val MAXIMUM_SCALE: Int = 128

  def createC(
    name: String,
    value: BigDecimal,
    unit: Option[String] = None
  ): Consequence[OperationEvaluationMeasurement] = {
    val precision = value.precision
    val scale = value.scale
    if (precision > MAXIMUM_PRECISION)
      Consequence.argumentLimitExceeded(
        "value.precision",
        MAXIMUM_PRECISION,
        precision,
        "operation-evaluation.measurement"
      )
    else if (scale < MINIMUM_SCALE || scale > MAXIMUM_SCALE)
      Consequence.argumentInvalid(
        "value.scale",
        s"scale from $MINIMUM_SCALE through $MAXIMUM_SCALE",
        scale
      )
    else
      for {
        n <- OperationEvaluationName.parseC(name)
        u <- unit.fold[Consequence[Option[OperationEvaluationName]]](Consequence.success(None))(
          x => OperationEvaluationName.parseC(x).map(Some(_))
        )
      } yield OperationEvaluationMeasurement(n, value, u)
  }
}

abstract class OperationEvaluationSupplementalFact extends OperationEvaluationFact {
  final val source: OperationEvaluationFactSource = OperationEvaluationFactSource.Application
}

final case class CorpusCandidateFact private (
  id: OperationEvaluationFactId,
  correlation: OperationEvaluationCorrelation,
  occurredAt: Instant,
  summary: Option[OperationEvaluationText],
  labels: Vector[OperationEvaluationLabel],
  confidentiality: DataConfidentiality
) extends OperationEvaluationSupplementalFact {
  val factKind: String = "corpus-candidate"

  def toRecord: Record = Record.dataAuto(
    "id" -> id.toString,
    "kind" -> factKind,
    "source" -> source.token,
    "confidentiality" -> confidentiality.label,
    "occurredAt" -> occurredAt.toString,
    "summary" -> summary.map(_.print),
    "labels" -> labels.map(_.toRecord),
    "correlation" -> correlation.toRecord
  )
}

object CorpusCandidateFact {
  val MAXIMUM_LABELS: Int = 32

  def createC(
    id: OperationEvaluationFactId,
    correlation: OperationEvaluationCorrelation,
    occurredat: Instant,
    summary: Option[OperationEvaluationText] = None,
    labels: Vector[OperationEvaluationLabel] = Vector.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Internal
  ): Consequence[CorpusCandidateFact] =
    if (labels.length > MAXIMUM_LABELS)
      Consequence.argumentLimitExceeded("labels", MAXIMUM_LABELS, labels.length, "operation-evaluation.corpus-candidate")
    else
      Consequence.success(CorpusCandidateFact(id, correlation, occurredat, summary, labels, confidentiality))
}

final case class ExperimentObservationFact private (
  id: OperationEvaluationFactId,
  correlation: OperationEvaluationCorrelation,
  occurredAt: Instant,
  measurements: Vector[OperationEvaluationMeasurement],
  labels: Vector[OperationEvaluationLabel],
  confidentiality: DataConfidentiality
) extends OperationEvaluationSupplementalFact {
  val factKind: String = "experiment-observation"

  def toRecord: Record = Record.data(
    "id" -> id.toString,
    "kind" -> factKind,
    "source" -> source.token,
    "confidentiality" -> confidentiality.label,
    "occurredAt" -> occurredAt.toString,
    "measurements" -> measurements.map(_.toRecord),
    "labels" -> labels.map(_.toRecord),
    "correlation" -> correlation.toRecord
  )
}

object ExperimentObservationFact {
  val MAXIMUM_MEASUREMENTS: Int = 64
  val MAXIMUM_LABELS: Int = 32

  def createC(
    id: OperationEvaluationFactId,
    correlation: OperationEvaluationCorrelation,
    occurredat: Instant,
    measurements: Vector[OperationEvaluationMeasurement],
    labels: Vector[OperationEvaluationLabel] = Vector.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Internal
  ): Consequence[ExperimentObservationFact] =
    if (measurements.isEmpty)
      Consequence.argumentInvalid("measurements", "one or more measurements", "empty")
    else if (measurements.length > MAXIMUM_MEASUREMENTS)
      Consequence.argumentLimitExceeded(
        "measurements",
        MAXIMUM_MEASUREMENTS,
        measurements.length,
        "operation-evaluation.experiment-observation"
      )
    else if (labels.length > MAXIMUM_LABELS)
      Consequence.argumentLimitExceeded("labels", MAXIMUM_LABELS, labels.length, "operation-evaluation.experiment-observation")
    else
      Consequence.success(
        ExperimentObservationFact(id, correlation, occurredat, measurements, labels, confidentiality)
      )
}

final case class OperationEvaluationSupplementalIntent(
  id: OperationEvaluationIntentId,
  fact: OperationEvaluationSupplementalFact,
  stagedAt: Instant
) {
  def toRecord: Record = Record.data(
    "id" -> id.toString,
    "stagedAt" -> stagedAt.toString,
    "fact" -> fact.toRecord
  )
}

enum OperationEvaluationLimitationKind(val token: String) {
  case Timeout extends OperationEvaluationLimitationKind("timeout")
  case Saturated extends OperationEvaluationLimitationKind("saturated")
  case Overflow extends OperationEvaluationLimitationKind("overflow")
  case ReentrantSuppressed extends OperationEvaluationLimitationKind("reentrant-suppressed")
  case Unavailable extends OperationEvaluationLimitationKind("unavailable")
  case Unsupported extends OperationEvaluationLimitationKind("unsupported")
  case ConfidentialityRestricted extends OperationEvaluationLimitationKind("confidentiality-restricted")
  case Discarded extends OperationEvaluationLimitationKind("discarded")
  case ProviderFailure extends OperationEvaluationLimitationKind("provider-failure")
}

final case class OperationEvaluationLimitation(
  kind: OperationEvaluationLimitationKind,
  policy: Option[OperationEvaluationName] = None,
  diagnostic: Option[ConclusionDiagnostics.Classification] = None
) {
  def toRecord: Record = Record.dataAuto(
    "kind" -> kind.token,
    "policy" -> policy.map(_.print),
    "diagnostic" -> diagnostic.map(_.toRecord)
  )
}

final case class OperationEvaluationSinkIdentity private (
  contract: OperationEvaluationName,
  socketComponent: OperationEvaluationName,
  providerComponent: OperationEvaluationName,
  providerInstance: Option[OperationEvaluationName]
) {
  def toRecord: Record = Record.dataAuto(
    "contract" -> contract.print,
    "socketComponent" -> socketComponent.print,
    "providerComponent" -> providerComponent.print,
    "providerInstance" -> providerInstance.map(_.print)
  )
}

object OperationEvaluationSinkIdentity {
  def createC(
    contract: String,
    socketcomponent: String,
    providercomponent: String,
    providerinstance: Option[String] = None
  ): Consequence[OperationEvaluationSinkIdentity] =
    for {
      c <- OperationEvaluationName.parseC(contract)
      s <- OperationEvaluationName.parseC(socketcomponent)
      p <- OperationEvaluationName.parseC(providercomponent)
      i <- providerinstance.fold[Consequence[Option[OperationEvaluationName]]](Consequence.success(None))(
        x => OperationEvaluationName.parseC(x).map(Some(_))
      )
    } yield OperationEvaluationSinkIdentity(c, s, p, i)
}

enum OperationEvaluationDeliveryStatus(val token: String) {
  case Delivered extends OperationEvaluationDeliveryStatus("delivered")
  case Discarded extends OperationEvaluationDeliveryStatus("discarded")
  case Limited extends OperationEvaluationDeliveryStatus("limited")
  case Failed extends OperationEvaluationDeliveryStatus("failed")
}

final case class OperationEvaluationDeliveryResult private[evaluation] (
  factId: OperationEvaluationFactId,
  sink: OperationEvaluationSinkIdentity,
  status: OperationEvaluationDeliveryStatus,
  limitations: Vector[OperationEvaluationLimitation],
  confidentiality: DataConfidentiality
) {
  val source: OperationEvaluationFactSource = OperationEvaluationFactSource.Provider

  def toRecord: Record = Record.data(
    "factId" -> factId.toString,
    "source" -> source.token,
    "confidentiality" -> confidentiality.label,
    "sink" -> sink.toRecord,
    "status" -> status.token,
    "limitations" -> limitations.map(_.toRecord)
  )
}

object OperationEvaluationDeliveryResult {
  val MAXIMUM_LIMITATIONS: Int = 16

  def createC(
    factid: OperationEvaluationFactId,
    sink: OperationEvaluationSinkIdentity,
    status: OperationEvaluationDeliveryStatus,
    limitations: Vector[OperationEvaluationLimitation] = Vector.empty,
    confidentiality: DataConfidentiality = DataConfidentiality.Internal
  ): Consequence[OperationEvaluationDeliveryResult] =
    if (limitations.length > MAXIMUM_LIMITATIONS)
      Consequence.argumentLimitExceeded(
        "limitations",
        MAXIMUM_LIMITATIONS,
        limitations.length,
        "operation-evaluation.delivery-result"
      )
    else
      Consequence.success(OperationEvaluationDeliveryResult(factid, sink, status, limitations, confidentiality))
}

enum EvaluationAdmissionRequirement(val token: String) {
  case Optional extends EvaluationAdmissionRequirement("optional")
  case Required extends EvaluationAdmissionRequirement("required")
}

object EvaluationAdmissionRequirement {
  def parseC(value: String): Consequence[EvaluationAdmissionRequirement] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("optional") => Consequence.success(Optional)
      case Some("required") => Consequence.success(Required)
      case _ => Consequence.argumentFormatError("admission", "optional or required", value)
    }
}

enum CorpusCaptureMode(val token: String) {
  case Candidate extends CorpusCaptureMode("candidate")
}

object CorpusCaptureMode {
  def parseC(value: String): Consequence[CorpusCaptureMode] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("candidate") => Consequence.success(Candidate)
      case _ => Consequence.argumentFormatError("capture", "candidate", value)
    }
}

final case class CmlCorpusEvaluationDeclaration(
  capture: CorpusCaptureMode,
  profile: OperationEvaluationName,
  admission: EvaluationAdmissionRequirement = EvaluationAdmissionRequirement.Optional,
  outcomes: Vector[OperationEvaluationOutcome] = Vector.empty,
  sampling: Option[OperationEvaluationName] = None,
  redaction: Option[OperationEvaluationName] = None
) {
  def toRecord: Record = Record.dataAuto(
    "capture" -> capture.token,
    "profile" -> profile.print,
    "admission" -> admission.token,
    "outcomes" -> outcomes.map(_.token),
    "sampling" -> sampling.map(_.print),
    "redaction" -> redaction.map(_.print)
  )
}

final case class CmlExperimentEvaluationDeclaration(
  eligible: Boolean,
  purpose: OperationEvaluationName,
  admission: EvaluationAdmissionRequirement = EvaluationAdmissionRequirement.Optional,
  variantProfile: Option[OperationEvaluationName] = None
) {
  def toRecord: Record = Record.dataAuto(
    "eligible" -> eligible,
    "purpose" -> purpose.print,
    "admission" -> admission.token,
    "variantProfile" -> variantProfile.map(_.print)
  )
}

final case class CmlOperationEvaluationDeclaration(
  corpus: Option[CmlCorpusEvaluationDeclaration] = None,
  experiment: Option[CmlExperimentEvaluationDeclaration] = None
) {
  def isEmpty: Boolean = corpus.isEmpty && experiment.isEmpty

  def toRecord: Record = Record.dataAuto(
    "corpus" -> corpus.map(_.toRecord),
    "experiment" -> experiment.map(_.toRecord)
  )
}
