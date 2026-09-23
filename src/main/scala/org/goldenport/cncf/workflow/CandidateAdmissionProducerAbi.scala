package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse

import org.goldenport.{Conclusion, Consequence}

/*
 * @since   Sep. 23, 2026
 * @version Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Immutable CNCF receiver for Cozy's separate Candidate-Admission producer
 * ABI.  It deliberately receives the versioned sidecar only: it neither
 * reparses CML nor adopts a second generated-workflow ABI.
 */
final class CandidateAdmissionProducerAbiException(
  val diagnostic: CandidateAdmissionProducerAbi.Diagnostic
) extends IllegalArgumentException(diagnostic.render)

object CandidateAdmissionProducerAbi {
  final case class ModelIdentity(value: String)
  final case class WorkflowIdentity(value: String)
  final case class WorkflowVersion(value: String)
  final case class JudgmentActionIdentity(value: String)
  final case class AdmissionActionIdentity(value: String)
  final case class RequiredSpiIdentity(value: String)
  final case class AlternativeIdentity(value: String)
  final case class CriterionIdentity(value: String)
  final case class ServiceIdentity(value: String)
  final case class OperationIdentity(value: String)
  final case class TypeIdentity(value: String)
  final case class InputBinding(value: String)
  final case class Rationale(value: String)
  final case class Evidence(value: String)
  final case class EvidenceScope(value: String)
  final case class EvidenceFreshness(value: String)
  final case class EvidenceProvenance(value: String)

  final case class SourceLocation(line: Int)
  final case class SourceReference(value: String, source: SourceLocation)
  final case class Generator(schemaVersion: String, generatorIdentity: String)
  final case class Operation(
    service: ServiceIdentity,
    name: OperationIdentity,
    inputType: Option[TypeIdentity],
    resultType: Option[TypeIdentity]
  )
  final case class Workflow(
    identity: WorkflowIdentity,
    version: WorkflowVersion,
    rootSource: SourceLocation,
    definitionSource: SourceLocation
  )
  final case class Model(
    identity: ModelIdentity,
    name: String,
    source: SourceLocation,
    workflow: Option[Workflow]
  )
  final case class Judgment(
    identity: JudgmentActionIdentity,
    operation: Operation,
    inputBinding: Option[InputBinding],
    goal: SourceReference,
    context: SourceReference,
    candidate: SourceReference,
    alternatives: Vector[SourceReference],
    criteria: Vector[SourceReference],
    expectedResult: SourceReference,
    rationale: SourceReference,
    evidence: SourceReference,
    evidenceScope: SourceReference,
    evidenceFreshness: SourceReference,
    evidenceProvenance: SourceReference,
    actionSource: SourceLocation,
    semanticSource: SourceLocation
  )
  final case class Admission(
    identity: AdmissionActionIdentity,
    candidateJudgmentActionIdentity: JudgmentActionIdentity,
    operation: Operation,
    inputBinding: Option[InputBinding],
    effectClass: String,
    transactionRequirement: String,
    actionSource: SourceLocation,
    semanticSource: SourceLocation,
    candidateActionSource: SourceLocation
  )
  final case class JudgmentAdmission(
    judgmentActionIdentity: JudgmentActionIdentity,
    admissionActionIdentity: AdmissionActionIdentity,
    judgmentSource: SourceLocation,
    admissionSource: SourceLocation,
    candidateActionSource: SourceLocation
  )
  final case class RequiredSpi(
    identity: RequiredSpiIdentity,
    actionIdentity: JudgmentActionIdentity,
    operation: Operation,
    capabilitySource: SourceLocation,
    actionSource: SourceLocation
  )
  final case class ProducerModel(
    model: Model,
    generator: Generator,
    judgments: Vector[Judgment],
    admissions: Vector[Admission],
    judgmentAdmissions: Vector[JudgmentAdmission],
    requiredSpi: Vector[RequiredSpi]
  )
  final case class Artifact(
    schemaVersion: String,
    generator: Generator,
    models: Vector[ProducerModel]
  )

  /** Typed candidate output before receiver admission. */
  final case class JudgmentResult(
    judgment: JudgmentActionIdentity,
    selectedAlternative: AlternativeIdentity,
    rationale: Rationale,
    evidence: Evidence,
    evidenceScope: EvidenceScope,
    evidenceFreshness: EvidenceFreshness,
    evidenceProvenance: EvidenceProvenance
  )

  /** A result that is known to this admitted producer artifact. */
  final case class AdmittedJudgmentResult private[workflow] (value: JudgmentResult) {
    def judgment: JudgmentActionIdentity = value.judgment
    def selectedAlternative: AlternativeIdentity = value.selectedAlternative
  }

  enum DiagnosticCode(val value: String) {
    case InvalidJson extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-001")
    case InvalidShape extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-002")
    case UnsupportedSchema extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-003")
    case UnsupportedGenerator extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-004")
    case MissingRequiredFact extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-005")
    case MissingRequiredProvenance extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-006")
    case DuplicateIdentity extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-007")
    case MissingAlternatives extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-008")
    case DuplicateAlternative extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-009")
    case MissingCriteria extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-010")
    case DuplicateCriterion extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-011")
    case JudgmentAdmissionPairing extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-012")
    case JudgmentAdmissionIdentityMismatch extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-013")
    case OperationMismatch extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-014")
    case InputBindingMismatch extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-015")
    case InvalidAdmissionEffectClass extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-016")
    case InvalidAdmissionTransactionRequirement extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-017")
    case UnknownJudgmentResult extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-018")
    case UnknownJudgmentAlternative extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-019")
    case IncompleteJudgmentResult extends DiagnosticCode("CWF77-CANDIDATE-ADMISSION-RECEIVER-020")
  }

  final case class Diagnostic(code: DiagnosticCode, data: Map[String, String]) {
    def render: String = {
      val details = data.toVector.sortBy(_._1).map { case (key, value) =>
        s"$key=$value"
      }.mkString(", ")
      s"${code.value}: $details"
    }
  }

  val acceptedSchemaVersion: String = "cozy.cml.candidate-admission-producer-abi.v1"
  val acceptedGeneratorIdentity: String =
    "cozy.modeler.CandidateAdmissionProducerAbiGenerator"

  /** Parses and admits exactly the frozen Cozy producer sidecar schema. */
  def parseC(text: String): Consequence[Artifact] =
    parse(text) match {
      case Left(_) =>
        _failure(Diagnostic(DiagnosticCode.InvalidJson, Map("kind" -> "candidate-admission-producer-abi")))
      case Right(json) =>
        _decode_artifact(json).fold(_failure, admitC)
    }

  /** Rechecks an in-memory representation before it crosses the receiver boundary. */
  def admitC(value: Artifact): Consequence[Artifact] =
    _artifact_diagnostic(value).fold(Consequence.success(value))(_failure)

  /** Admits a fully populated result against a previously admitted artifact. */
  def admitJudgmentResultC(
    artifact: Artifact,
    result: JudgmentResult
  ): Consequence[AdmittedJudgmentResult] =
    admitC(artifact) match {
      case Consequence.Success(admitted) =>
        _result_diagnostic(admitted, result).fold(
          Consequence.success(AdmittedJudgmentResult(result))
        )(_failure)
      case Consequence.Failure(conclusion) =>
        Consequence.Failure(conclusion)
    }

  private def _artifact_diagnostic(value: Artifact): Option[Diagnostic] =
    if (value.schemaVersion != acceptedSchemaVersion)
      Some(_diagnostic(
        DiagnosticCode.UnsupportedSchema,
        "actual" -> value.schemaVersion,
        "expected" -> acceptedSchemaVersion
      ))
    else if (!_is_generator(value.generator))
      Some(_generator_diagnostic(value.generator, "artifact"))
    else if (value.models.isEmpty)
      Some(_diagnostic(DiagnosticCode.MissingRequiredFact, "kind" -> "models"))
    else
      _duplicate(value.models.map(_.model.identity.value), "model")
        .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "model"))
        .orElse(_duplicate(value.models.flatMap(_.judgments.map(_.identity.value)), "judgment")
          .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "judgment")))
        .orElse(_duplicate(value.models.flatMap(_.admissions.map(_.identity.value)), "admission")
          .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "admission")))
        .orElse(_duplicate(value.models.flatMap(_.requiredSpi.map(_.identity.value)), "required-spi")
          .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "required-spi")))
        .orElse(value.models.iterator.map(_model_diagnostic).collectFirst { case Some(x) => x })

  private def _model_diagnostic(value: ProducerModel): Option[Diagnostic] =
    _generator_diagnostic_if_needed(value.generator, value.model.identity.value)
      .orElse(_model_fact_diagnostic(value.model))
      .orElse(_duplicate(value.judgments.map(_.identity.value), "judgment")
        .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "judgment")))
      .orElse(_duplicate(value.admissions.map(_.identity.value), "admission")
        .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "admission")))
      .orElse(_duplicate(value.requiredSpi.map(_.identity.value), "required-spi")
        .map(identity => _diagnostic(DiagnosticCode.DuplicateIdentity, "identity" -> identity, "kind" -> "required-spi")))
      .orElse(value.judgments.iterator.map(_judgment_diagnostic).collectFirst { case Some(x) => x })
      .orElse(value.admissions.iterator.map(_admission_diagnostic).collectFirst { case Some(x) => x })
      .orElse(value.requiredSpi.iterator.map(_required_spi_diagnostic).collectFirst { case Some(x) => x })
      .orElse(_pair_diagnostic(value))
      .orElse(_compatibility_diagnostic(value))

  private def _model_fact_diagnostic(value: Model): Option[Diagnostic] =
    _required_diagnostic("model-identity", value.identity.value)
      .orElse(_required_diagnostic("model-name", value.name))
      .orElse(_source_diagnostic("model-source", value.source))
      .orElse(value.workflow.flatMap { workflow =>
        _required_diagnostic("workflow-identity", workflow.identity.value)
          .orElse(_required_diagnostic("workflow-version", workflow.version.value))
          .orElse(_source_diagnostic("workflow-root-source", workflow.rootSource))
          .orElse(_source_diagnostic("workflow-definition-source", workflow.definitionSource))
      })

  private def _judgment_diagnostic(value: Judgment): Option[Diagnostic] =
    _required_diagnostic("judgment-identity", value.identity.value)
      .orElse(_operation_diagnostic("judgment-operation", value.operation))
      .orElse(_input_binding_diagnostic("judgment-input-binding", value.inputBinding))
      .orElse(_references_diagnostic(
        Vector(
          "judgment-goal" -> value.goal,
          "judgment-context" -> value.context,
          "judgment-candidate" -> value.candidate,
          "judgment-expected-result" -> value.expectedResult,
          "judgment-rationale" -> value.rationale,
          "judgment-evidence" -> value.evidence,
          "judgment-evidence-scope" -> value.evidenceScope,
          "judgment-evidence-freshness" -> value.evidenceFreshness,
          "judgment-evidence-provenance" -> value.evidenceProvenance
        )
      ))
      .orElse(_nonempty_references(
        "judgment-alternative",
        value.alternatives,
        DiagnosticCode.MissingAlternatives,
        DiagnosticCode.DuplicateAlternative
      ))
      .orElse(_nonempty_references(
        "judgment-criterion",
        value.criteria,
        DiagnosticCode.MissingCriteria,
        DiagnosticCode.DuplicateCriterion
      ))
      .orElse(_source_diagnostic("judgment-action-source", value.actionSource))
      .orElse(_source_diagnostic("judgment-semantic-source", value.semanticSource))

  private def _admission_diagnostic(value: Admission): Option[Diagnostic] =
    _required_diagnostic("admission-identity", value.identity.value)
      .orElse(_required_diagnostic("admission-candidate-judgment", value.candidateJudgmentActionIdentity.value))
      .orElse(_operation_diagnostic("admission-operation", value.operation))
      .orElse(_input_binding_diagnostic("admission-input-binding", value.inputBinding))
      .orElse(_required_diagnostic("admission-effect-class", value.effectClass))
      .orElse(_required_diagnostic("admission-transaction-requirement", value.transactionRequirement))
      .orElse(_source_diagnostic("admission-action-source", value.actionSource))
      .orElse(_source_diagnostic("admission-semantic-source", value.semanticSource))
      .orElse(_source_diagnostic("admission-candidate-action-source", value.candidateActionSource))
      .orElse(
        if (value.effectClass == "LOCAL") None
        else Some(_diagnostic(DiagnosticCode.InvalidAdmissionEffectClass, "actual" -> value.effectClass, "expected" -> "LOCAL"))
      )
      .orElse(
        if (value.transactionRequirement == "REQUIRED") None
        else Some(_diagnostic(DiagnosticCode.InvalidAdmissionTransactionRequirement, "actual" -> value.transactionRequirement, "expected" -> "REQUIRED"))
      )

  private def _required_spi_diagnostic(value: RequiredSpi): Option[Diagnostic] =
    _required_diagnostic("required-spi-identity", value.identity.value)
      .orElse(_required_diagnostic("required-spi-action", value.actionIdentity.value))
      .orElse(_operation_diagnostic("required-spi-operation", value.operation))
      .orElse(_source_diagnostic("required-spi-capability-source", value.capabilitySource))
      .orElse(_source_diagnostic("required-spi-action-source", value.actionSource))

  private def _pair_diagnostic(value: ProducerModel): Option[Diagnostic] = {
    val judgments = value.judgments.map(x => x.identity.value -> x).toMap
    val admissions = value.admissions.map(x => x.identity.value -> x).toMap
    if (value.judgments.isEmpty || value.admissions.isEmpty)
      Some(_diagnostic(DiagnosticCode.JudgmentAdmissionPairing, "model" -> value.model.identity.value, "reason" -> "missing-action"))
    else if (value.judgmentAdmissions.size != value.judgments.size ||
        value.judgmentAdmissions.size != value.admissions.size)
      Some(_diagnostic(DiagnosticCode.JudgmentAdmissionPairing, "model" -> value.model.identity.value, "reason" -> "cardinality"))
    else if (_duplicate(value.judgmentAdmissions.map(_.judgmentActionIdentity.value), "pair-judgment").nonEmpty ||
        _duplicate(value.judgmentAdmissions.map(_.admissionActionIdentity.value), "pair-admission").nonEmpty)
      Some(_diagnostic(DiagnosticCode.JudgmentAdmissionPairing, "model" -> value.model.identity.value, "reason" -> "duplicate"))
    else
      value.judgmentAdmissions.iterator.map { pair =>
        for {
          judgment <- judgments.get(pair.judgmentActionIdentity.value)
          admission <- admissions.get(pair.admissionActionIdentity.value)
        } yield (pair, judgment, admission)
      }.collectFirst { case None =>
        _diagnostic(DiagnosticCode.JudgmentAdmissionIdentityMismatch, "model" -> value.model.identity.value, "reason" -> "unknown-action")
      }.orElse(
        value.judgmentAdmissions.iterator.flatMap { pair =>
          for {
            judgment <- judgments.get(pair.judgmentActionIdentity.value)
            admission <- admissions.get(pair.admissionActionIdentity.value)
          } yield (pair, judgment, admission)
        }.map { case (pair, _, admission) =>
          _source_diagnostic("judgment-admission-judgment-source", pair.judgmentSource)
            .orElse(_source_diagnostic("judgment-admission-admission-source", pair.admissionSource))
            .orElse(_source_diagnostic("judgment-admission-candidate-action-source", pair.candidateActionSource))
            .orElse(
              if (admission.candidateJudgmentActionIdentity == pair.judgmentActionIdentity) None
              else Some(_diagnostic(
                DiagnosticCode.JudgmentAdmissionIdentityMismatch,
                "admission" -> admission.identity.value,
                "expected-judgment" -> pair.judgmentActionIdentity.value,
                "actual-judgment" -> admission.candidateJudgmentActionIdentity.value
              ))
            )
        }.collectFirst { case Some(x) => x }
      )
  }

  private def _compatibility_diagnostic(value: ProducerModel): Option[Diagnostic] = {
    val admissions = value.admissions.map(x => x.identity.value -> x).toMap
    val judgments = value.judgments.map(x => x.identity.value -> x).toMap
    value.judgmentAdmissions.iterator.flatMap { pair =>
      for {
        judgment <- judgments.get(pair.judgmentActionIdentity.value)
        admission <- admissions.get(pair.admissionActionIdentity.value)
      } yield (judgment, admission)
    }.map { case (judgment, admission) =>
      if (judgment.operation != admission.operation)
        Some(_diagnostic(DiagnosticCode.OperationMismatch, "admission" -> admission.identity.value, "judgment" -> judgment.identity.value))
      else if (judgment.inputBinding != admission.inputBinding)
        Some(_diagnostic(DiagnosticCode.InputBindingMismatch, "admission" -> admission.identity.value, "judgment" -> judgment.identity.value))
      else
        value.requiredSpi.iterator
          .filter(_.actionIdentity == judgment.identity)
          .map { required =>
            if (required.operation == judgment.operation) None
            else Some(_diagnostic(DiagnosticCode.OperationMismatch, "judgment" -> judgment.identity.value, "required-spi" -> required.identity.value))
          }.collectFirst { case Some(x) => x }
    }.collectFirst { case Some(x) => x }
  }

  private def _result_diagnostic(
    artifact: Artifact,
    value: JudgmentResult
  ): Option[Diagnostic] =
    _result_required_diagnostic("judgment", value.judgment.value)
      .orElse(_result_required_diagnostic("selected-alternative", value.selectedAlternative.value))
      .orElse(_result_required_diagnostic("rationale", value.rationale.value))
      .orElse(_result_required_diagnostic("evidence", value.evidence.value))
      .orElse(_result_required_diagnostic("evidence-scope", value.evidenceScope.value))
      .orElse(_result_required_diagnostic("evidence-freshness", value.evidenceFreshness.value))
      .orElse(_result_required_diagnostic("evidence-provenance", value.evidenceProvenance.value))
      .orElse {
        artifact.models.iterator.flatMap(_.judgments.iterator)
          .find(_.identity == value.judgment) match {
          case None => Some(_diagnostic(DiagnosticCode.UnknownJudgmentResult, "judgment" -> value.judgment.value))
          case Some(judgment) if !judgment.alternatives.exists(_.value == value.selectedAlternative.value) =>
            Some(_diagnostic(
              DiagnosticCode.UnknownJudgmentAlternative,
              "alternative" -> value.selectedAlternative.value,
              "judgment" -> value.judgment.value
            ))
          case Some(_) => None
        }
      }

  private def _result_required_diagnostic(kind: String, value: String): Option[Diagnostic] =
    if (_is_blank(value)) Some(_diagnostic(DiagnosticCode.IncompleteJudgmentResult, "kind" -> kind))
    else None

  private def _generator_diagnostic_if_needed(value: Generator, model: String): Option[Diagnostic] =
    if (_is_generator(value)) None else Some(_generator_diagnostic(value, model))

  private def _generator_diagnostic(value: Generator, location: String): Diagnostic =
    _diagnostic(
      DiagnosticCode.UnsupportedGenerator,
      "actual-generator" -> value.generatorIdentity,
      "actual-schema" -> value.schemaVersion,
      "expected-generator" -> acceptedGeneratorIdentity,
      "expected-schema" -> acceptedSchemaVersion,
      "location" -> location
    )

  private def _is_generator(value: Generator): Boolean =
    value.schemaVersion == acceptedSchemaVersion &&
      value.generatorIdentity == acceptedGeneratorIdentity

  private def _operation_diagnostic(kind: String, value: Operation): Option[Diagnostic] =
    _required_diagnostic(s"$kind-service", value.service.value)
      .orElse(_required_diagnostic(s"$kind-name", value.name.value))
      .orElse(value.inputType.flatMap(x => _required_diagnostic(s"$kind-input-type", x.value)))
      .orElse(value.resultType.flatMap(x => _required_diagnostic(s"$kind-result-type", x.value)))

  private def _input_binding_diagnostic(kind: String, value: Option[InputBinding]): Option[Diagnostic] =
    value.flatMap(x => _required_diagnostic(kind, x.value))

  private def _references_diagnostic(values: Vector[(String, SourceReference)]): Option[Diagnostic] =
    values.iterator.map { case (kind, value) =>
      _required_diagnostic(kind, value.value).orElse(_source_diagnostic(s"$kind-source", value.source))
    }.collectFirst { case Some(x) => x }

  private def _nonempty_references(
    kind: String,
    values: Vector[SourceReference],
    missing: DiagnosticCode,
    duplicate: DiagnosticCode
  ): Option[Diagnostic] =
    if (values.isEmpty) Some(_diagnostic(missing, "kind" -> kind))
    else _references_diagnostic(values.map(value => kind -> value))
      .orElse(_duplicate(values.map(_.value), kind).map(identity => _diagnostic(duplicate, "identity" -> identity, "kind" -> kind)))

  private def _required_diagnostic(kind: String, value: String): Option[Diagnostic] =
    if (_is_blank(value)) Some(_diagnostic(DiagnosticCode.MissingRequiredFact, "kind" -> kind))
    else None

  private def _source_diagnostic(kind: String, value: SourceLocation): Option[Diagnostic] =
    if (value.line <= 0) Some(_diagnostic(DiagnosticCode.MissingRequiredProvenance, "kind" -> kind))
    else None

  private def _duplicate(values: Vector[String], kind: String): Option[String] =
    values.groupBy(identity).collect {
      case (value, entries) if entries.size > 1 => value
    }.toVector.sorted.headOption

  private def _is_blank(value: String): Boolean = value == null || value.trim.isEmpty

  private def _diagnostic(code: DiagnosticCode, values: (String, String)*): Diagnostic =
    Diagnostic(code, Map.from(values))

  private def _failure[A](diagnostic: Diagnostic): Consequence[A] =
    Consequence.Failure(Conclusion.from(new CandidateAdmissionProducerAbiException(diagnostic)))

  private def _decode_artifact(json: Json): Either[Diagnostic, Artifact] =
    for {
      fields <- _fields(json, Set("schemaVersion", "generator", "models"), "artifact")
      schema <- _string(fields, "schemaVersion", "artifact")
      generator <- _decode_generator(fields("generator"), "artifact.generator")
      models <- _array(fields("models"), "artifact.models").flatMap(values =>
        _decode_vector(values, _decode_model(_, "artifact.models"))
      )
    } yield Artifact(schema, generator, models)

  private def _decode_model(json: Json, context: String): Either[Diagnostic, ProducerModel] =
    for {
      fields <- _fields(json, Set("model", "generator", "judgments", "admissions", "judgmentAdmissions", "requiredSpi"), context)
      model <- _decode_model_identity(fields("model"), s"$context.model")
      generator <- _decode_generator(fields("generator"), s"$context.generator")
      judgments <- _array(fields("judgments"), s"$context.judgments").flatMap(values =>
        _decode_vector(values, _decode_judgment(_, s"$context.judgments"))
      )
      admissions <- _array(fields("admissions"), s"$context.admissions").flatMap(values =>
        _decode_vector(values, _decode_admission(_, s"$context.admissions"))
      )
      pairs <- _array(fields("judgmentAdmissions"), s"$context.judgmentAdmissions").flatMap(values =>
        _decode_vector(values, _decode_pair(_, s"$context.judgmentAdmissions"))
      )
      required <- _array(fields("requiredSpi"), s"$context.requiredSpi").flatMap(values =>
        _decode_vector(values, _decode_required_spi(_, s"$context.requiredSpi"))
      )
    } yield ProducerModel(model, generator, judgments, admissions, pairs, required)

  private def _decode_generator(json: Json, context: String): Either[Diagnostic, Generator] =
    for {
      fields <- _fields(json, Set("schemaVersion", "generatorIdentity"), context)
      schema <- _string(fields, "schemaVersion", context)
      identity <- _string(fields, "generatorIdentity", context)
    } yield Generator(schema, identity)

  private def _decode_model_identity(json: Json, context: String): Either[Diagnostic, Model] =
    for {
      fields <- _fields(json, Set("compositeStateMachineIdentity", "compositeStateMachineName", "compositeStateMachineSource", "workflow"), context)
      identity <- _string(fields, "compositeStateMachineIdentity", context)
      name <- _string(fields, "compositeStateMachineName", context)
      source <- _decode_source(fields("compositeStateMachineSource"), s"$context.compositeStateMachineSource")
      workflow <- _optional_object(fields("workflow"), s"$context.workflow")(_decode_workflow)
    } yield Model(ModelIdentity(identity), name, source, workflow)

  private def _decode_workflow(json: Json, context: String): Either[Diagnostic, Workflow] =
    for {
      fields <- _fields(json, Set("identity", "version", "rootSource", "definitionSource"), context)
      identity <- _string(fields, "identity", context)
      version <- _string(fields, "version", context)
      root <- _decode_source(fields("rootSource"), s"$context.rootSource")
      definition <- _decode_source(fields("definitionSource"), s"$context.definitionSource")
    } yield Workflow(WorkflowIdentity(identity), WorkflowVersion(version), root, definition)

  private def _decode_judgment(json: Json, context: String): Either[Diagnostic, Judgment] =
    for {
      fields <- _fields(json, Set("actionIdentity", "operation", "inputBinding", "goal", "context", "candidate", "alternatives", "criteria", "expectedResult", "rationale", "evidence", "evidenceScope", "evidenceFreshness", "evidenceProvenance", "actionSource", "semanticSource"), context)
      identity <- _string(fields, "actionIdentity", context)
      operation <- _decode_operation(fields("operation"), s"$context.operation")
      binding <- _optional_string(fields("inputBinding"), s"$context.inputBinding")
      goal <- _decode_reference(fields("goal"), s"$context.goal")
      contextref <- _decode_reference(fields("context"), s"$context.context")
      candidate <- _decode_reference(fields("candidate"), s"$context.candidate")
      alternatives <- _array(fields("alternatives"), s"$context.alternatives").flatMap(values => _decode_vector(values, _decode_reference(_, s"$context.alternatives")))
      criteria <- _array(fields("criteria"), s"$context.criteria").flatMap(values => _decode_vector(values, _decode_reference(_, s"$context.criteria")))
      expected <- _decode_reference(fields("expectedResult"), s"$context.expectedResult")
      rationale <- _decode_reference(fields("rationale"), s"$context.rationale")
      evidence <- _decode_reference(fields("evidence"), s"$context.evidence")
      scope <- _decode_reference(fields("evidenceScope"), s"$context.evidenceScope")
      freshness <- _decode_reference(fields("evidenceFreshness"), s"$context.evidenceFreshness")
      provenance <- _decode_reference(fields("evidenceProvenance"), s"$context.evidenceProvenance")
      actionsource <- _decode_source(fields("actionSource"), s"$context.actionSource")
      semanticsource <- _decode_source(fields("semanticSource"), s"$context.semanticSource")
    } yield Judgment(JudgmentActionIdentity(identity), operation, binding.map(InputBinding.apply), goal, contextref, candidate, alternatives, criteria, expected, rationale, evidence, scope, freshness, provenance, actionsource, semanticsource)

  private def _decode_admission(json: Json, context: String): Either[Diagnostic, Admission] =
    for {
      fields <- _fields(json, Set("actionIdentity", "candidateJudgmentActionIdentity", "operation", "inputBinding", "effectClass", "transactionRequirement", "actionSource", "semanticSource", "candidateActionSource"), context)
      identity <- _string(fields, "actionIdentity", context)
      candidate <- _string(fields, "candidateJudgmentActionIdentity", context)
      operation <- _decode_operation(fields("operation"), s"$context.operation")
      binding <- _optional_string(fields("inputBinding"), s"$context.inputBinding")
      effect <- _string(fields, "effectClass", context)
      transaction <- _string(fields, "transactionRequirement", context)
      actionsource <- _decode_source(fields("actionSource"), s"$context.actionSource")
      semanticsource <- _decode_source(fields("semanticSource"), s"$context.semanticSource")
      candidatesource <- _decode_source(fields("candidateActionSource"), s"$context.candidateActionSource")
    } yield Admission(AdmissionActionIdentity(identity), JudgmentActionIdentity(candidate), operation, binding.map(InputBinding.apply), effect, transaction, actionsource, semanticsource, candidatesource)

  private def _decode_pair(json: Json, context: String): Either[Diagnostic, JudgmentAdmission] =
    for {
      fields <- _fields(json, Set("judgmentActionIdentity", "admissionActionIdentity", "judgmentSource", "admissionSource", "candidateActionSource"), context)
      judgment <- _string(fields, "judgmentActionIdentity", context)
      admission <- _string(fields, "admissionActionIdentity", context)
      judgmentsource <- _decode_source(fields("judgmentSource"), s"$context.judgmentSource")
      admissionsource <- _decode_source(fields("admissionSource"), s"$context.admissionSource")
      candidatesource <- _decode_source(fields("candidateActionSource"), s"$context.candidateActionSource")
    } yield JudgmentAdmission(JudgmentActionIdentity(judgment), AdmissionActionIdentity(admission), judgmentsource, admissionsource, candidatesource)

  private def _decode_required_spi(json: Json, context: String): Either[Diagnostic, RequiredSpi] =
    for {
      fields <- _fields(json, Set("capability", "actionIdentity", "operation", "capabilitySource", "actionSource"), context)
      capability <- _string(fields, "capability", context)
      action <- _string(fields, "actionIdentity", context)
      operation <- _decode_operation(fields("operation"), s"$context.operation")
      capabilitysource <- _decode_source(fields("capabilitySource"), s"$context.capabilitySource")
      actionsource <- _decode_source(fields("actionSource"), s"$context.actionSource")
    } yield RequiredSpi(RequiredSpiIdentity(capability), JudgmentActionIdentity(action), operation, capabilitysource, actionsource)

  private def _decode_operation(json: Json, context: String): Either[Diagnostic, Operation] =
    for {
      fields <- _fields(json, Set("service", "name", "inputType", "resultType"), context)
      service <- _string(fields, "service", context)
      name <- _string(fields, "name", context)
      input <- _optional_string(fields("inputType"), s"$context.inputType")
      result <- _optional_string(fields("resultType"), s"$context.resultType")
    } yield Operation(ServiceIdentity(service), OperationIdentity(name), input.map(TypeIdentity.apply), result.map(TypeIdentity.apply))

  private def _decode_reference(json: Json, context: String): Either[Diagnostic, SourceReference] =
    for {
      fields <- _fields(json, Set("value", "source"), context)
      value <- _string(fields, "value", context)
      source <- _decode_source(fields("source"), s"$context.source")
    } yield SourceReference(value, source)

  private def _decode_source(json: Json, context: String): Either[Diagnostic, SourceLocation] =
    for {
      fields <- _fields(json, Set("line"), context)
      line <- fields("line").asNumber.flatMap(_.toInt).toRight(_shape_diagnostic(context, "line-must-be-integer"))
    } yield SourceLocation(line)

  private def _fields(
    json: Json,
    expected: Set[String],
    context: String
  ): Either[Diagnostic, Map[String, Json]] =
    json.asObject match {
      case None => Left(_shape_diagnostic(context, "object-required"))
      case Some(value) =>
        val actual = value.keys.toSet
        val missing = expected.diff(actual).toVector.sorted
        val unexpected = actual.diff(expected).toVector.sorted
        if (missing.nonEmpty) Left(_shape_diagnostic(context, s"missing=${missing.mkString("|")}"))
        else if (unexpected.nonEmpty) Left(_shape_diagnostic(context, s"unexpected=${unexpected.mkString("|")}"))
        else Right(value.toMap)
    }

  private def _string(
    fields: Map[String, Json],
    name: String,
    context: String
  ): Either[Diagnostic, String] =
    fields(name).asString.toRight(_shape_diagnostic(s"$context.$name", "string-required"))

  private def _optional_string(json: Json, context: String): Either[Diagnostic, Option[String]] =
    if (json.isNull) Right(None)
    else json.asString.map(Some(_)).toRight(_shape_diagnostic(context, "string-or-null-required"))

  private def _optional_object[A](json: Json, context: String)(f: (Json, String) => Either[Diagnostic, A]): Either[Diagnostic, Option[A]] =
    if (json.isNull) Right(None) else f(json, context).map(Some(_))

  private def _array(json: Json, context: String): Either[Diagnostic, Vector[Json]] =
    json.asArray.toRight(_shape_diagnostic(context, "array-required"))

  private def _decode_vector[A](
    values: Vector[Json],
    f: Json => Either[Diagnostic, A]
  ): Either[Diagnostic, Vector[A]] =
    values.zipWithIndex.foldLeft(Right(Vector.empty[A]): Either[Diagnostic, Vector[A]]) {
      case (acc, (value, index)) =>
        for {
          accumulated <- acc
          decoded <- f(value)
        } yield accumulated :+ decoded
    }

  private def _shape_diagnostic(context: String, reason: String): Diagnostic =
    _diagnostic(DiagnosticCode.InvalidShape, "context" -> context, "reason" -> reason)
}
