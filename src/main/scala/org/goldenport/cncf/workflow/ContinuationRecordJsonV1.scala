package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContinuationRuntimePersistence.{Record, Status}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{_decodeReferences, _decodeSnapshot, _expect, _fields, _optional, _optional_string, _reference, _snapshot, _string}

/** Strict local persistence encoding for one Continuation claim state. */
object ContinuationRecordJsonV1 {
  val schemaVersion = "cncf.continuation-record.v1"

  def encodeC(record: Record): Consequence[String] =
    _validate_c(record).map { admitted =>
      val c = admitted.continuation
      val required = c.requiredOperation
      val metadata = required.metadata
      Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "status" -> Json.fromString(admitted.status.toString),
        "claimId" -> _optional(admitted.claimId),
        "continuation" -> Json.obj(
          "runId" -> Json.fromString(c.runId.value),
          "continuationId" -> Json.fromString(c.continuationId.value),
          "expectedRevision" -> Json.fromString(c.expectedRevision.value),
          "requiredOperation" -> Json.obj(
            "capability" -> Json.fromString(required.identity.capability),
            "actionIdentity" -> Json.fromString(required.actionIdentity),
            "service" -> Json.fromString(required.operation.service),
            "operation" -> Json.fromString(required.operation.operation),
            "inputType" -> _optional(required.inputType.map(_.value)),
            "resultType" -> _optional(required.resultType.map(_.value)),
            "metadata" -> Json.obj(
              "contextIdentity" -> Json.fromString(metadata.contextContract.identity),
              "contextFacts" -> Json.arr(metadata.contextContract.requiredFacts.map(_reference)*),
              "contextReferences" -> Json.arr(metadata.contextContract.requiredReferences.map(_reference)*),
              "completionIdentity" -> Json.fromString(metadata.completionContract.identity),
              "completionFacts" -> Json.arr(metadata.completionContract.requiredFacts.map(_reference)*),
              "evidenceIdentity" -> Json.fromString(metadata.evidenceContract.identity),
              "evidenceReferences" -> Json.arr(metadata.evidenceContract.requiredEvidence.map(_reference)*),
              "constraints" -> Json.arr(metadata.constraints.map(x => Json.obj(
                "identity" -> Json.fromString(x.identity), "value" -> Json.fromString(x.value)
              ))*)
            )
          ),
          "context" -> Json.obj(
            "summary" -> Json.fromString(c.context.summary),
            "requiredFacts" -> Json.arr(c.context.requiredFacts.map(_reference)*),
            "references" -> Json.arr(c.context.references.map(_reference)*),
            "snapshot" -> _snapshot(c.context.snapshot)
          )
        )
      ).noSpaces
    }

  def decodeC(text: String): Consequence[Record] =
    if (text == null) Consequence.stateConflict("Continuation record JSON is missing")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid Continuation record JSON")
        root <- _fields(json, Set("schemaVersion", "status", "claimId", "continuation"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == schemaVersion, "unsupported Continuation record schema")
        statusText <- _string(root, "status")
        status <- Status.values.find(_.toString == statusText).toRight("unsupported Continuation status")
        claimId <- _optional_string(root, "claimId")
        c <- _fields(root("continuation"), Set("runId", "continuationId", "expectedRevision", "requiredOperation", "context"))
        run <- _string(c, "runId")
        identity <- _string(c, "continuationId")
        revision <- _string(c, "expectedRevision")
        required <- _decode_required(c("requiredOperation"))
        context <- _decode_context(c("context"))
      } yield Record(
        Continuation(StateMachineRunIdentity(run), ContinuationIdentity(identity),
          StateMachineRevision(revision), required, context), status, claimId
      )
      decoded.fold(Consequence.stateConflict(_), _validate_c)
    }

  private def _decode_required(json: Json): Either[String, StateMachineRequiredOperation] = for {
    fields <- _fields(json, Set("capability", "actionIdentity", "service", "operation", "inputType", "resultType", "metadata"))
    capability <- _string(fields, "capability")
    action <- _string(fields, "actionIdentity")
    service <- _string(fields, "service")
    operation <- _string(fields, "operation")
    input <- _optional_string(fields, "inputType")
    result <- _optional_string(fields, "resultType")
    metadata <- _decode_metadata(fields("metadata"))
  } yield StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(capability), action,
    StateMachineOperationIdentity(service, operation),
    input.map(StateMachineInputTypeReference.apply),
    result.map(StateMachineResultTypeReference.apply), metadata
  )

  private def _decode_metadata(json: Json): Either[String, StateMachineRequiredOperationMetadata] = for {
    fields <- _fields(json, Set("contextIdentity", "contextFacts", "contextReferences", "completionIdentity", "completionFacts", "evidenceIdentity", "evidenceReferences", "constraints"))
    context <- _string(fields, "contextIdentity")
    contextFacts <- _decodeReferences(fields("contextFacts"))
    contextReferences <- _decodeReferences(fields("contextReferences"))
    completion <- _string(fields, "completionIdentity")
    completionFacts <- _decodeReferences(fields("completionFacts"))
    evidence <- _string(fields, "evidenceIdentity")
    evidenceReferences <- _decodeReferences(fields("evidenceReferences"))
    constraints <- _decode_constraints(fields("constraints"))
  } yield StateMachineRequiredOperationMetadata(
    ContextContract(context, contextFacts, contextReferences),
    CompletionContract(completion, completionFacts),
    EvidenceContract(evidence, evidenceReferences), constraints
  )

  private def _decode_constraints(json: Json): Either[String, Vector[StateMachineConstraint]] =
    json.asArray.toRight("constraints must be an array").flatMap(_.foldLeft[Either[String, Vector[StateMachineConstraint]]](Right(Vector.empty)) {
      case (acc, value) => for {
        prior <- acc
        fields <- _fields(value, Set("identity", "value"))
        identity <- _string(fields, "identity")
        text <- _string(fields, "value")
      } yield prior :+ StateMachineConstraint(identity, text)
    })

  private def _decode_context(json: Json): Either[String, ContextBundle] = for {
    fields <- _fields(json, Set("summary", "requiredFacts", "references", "snapshot"))
    summary <- _string(fields, "summary")
    facts <- _decodeReferences(fields("requiredFacts"))
    references <- _decodeReferences(fields("references"))
    snapshot <- _decodeSnapshot(fields("snapshot"))
  } yield ContextBundle(summary, facts, references, snapshot)

  private def _validate_c(record: Record): Consequence[Record] = {
    val c = Option(record).map(_.continuation).orNull
    val required = Option(c).map(_.requiredOperation).orNull
    val metadata = Option(required).map(_.metadata).orNull
    val context = Option(c).map(_.context).orNull
    if (record == null || record.status == null || c == null ||
        c.runId == null || !_name(c.runId.value) ||
        c.continuationId == null || !_name(c.continuationId.value) ||
        c.expectedRevision == null || !_name(c.expectedRevision.value) ||
        required == null || required.identity == null || !_name(required.identity.capability) ||
        !_name(required.actionIdentity) || required.operation == null ||
        !_name(required.operation.service) || !_name(required.operation.operation) ||
        required.inputType == null || required.inputType.exists(x => x == null || !_name(x.value)) ||
        required.resultType == null || required.resultType.exists(x => x == null || !_name(x.value)) ||
        metadata == null || metadata.contextContract == null || metadata.completionContract == null ||
        metadata.evidenceContract == null || !_name(metadata.contextContract.identity) ||
        !_name(metadata.completionContract.identity) || !_name(metadata.evidenceContract.identity) ||
        !_references(metadata.contextContract.requiredFacts) ||
        !_references(metadata.contextContract.requiredReferences) ||
        !_references(metadata.completionContract.requiredFacts) ||
        !_references(metadata.evidenceContract.requiredEvidence) ||
        metadata.constraints == null || metadata.constraints.exists(x => x == null || !_name(x.identity) || !_name(x.value)) ||
        context == null || !_name(context.summary) || !_references(context.requiredFacts) ||
        !_references(context.references) || context.snapshot == null ||
        !_name(context.snapshot.workflowRevision) ||
        record.claimId == null ||
        (record.status == Status.Available && record.claimId.nonEmpty) ||
        (record.status != Status.Available && !record.claimId.exists(_name)))
      Consequence.stateConflict("Continuation record is incomplete or inconsistent")
    else Consequence.success(record)
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _references(values: Vector[ContextReference]): Boolean =
    values != null && values.forall(x => x != null && _name(x.identity) && _name(x.revision))
}
