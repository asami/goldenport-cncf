package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.*

/** A WorkOrder wire projection; the claim token and progression authority stay in CNCF. */
object WorkflowWorkOrderJsonV1 {
  def encodeC[W](
    interaction: WorkflowInteraction[W, Nothing],
    inputCodec: PayloadCodec[W]
  ): Consequence[String] =
    if (interaction == null || interaction.handle == null || inputCodec == null)
      Consequence.stateConflict("Workflow WorkOrder JSON projection is incomplete")
    else interaction.current match {
      case order: WorkflowContinuation.WorkOrder[?] =>
        _encode_order_c(interaction.handle, order.asInstanceOf[WorkflowContinuation.WorkOrder[W]], inputCodec)
      case _ => Consequence.stateConflict("Workflow WorkOrder JSON requires WORK_ORDER continuation")
    }

  def decodeC[W](
    text: String,
    inputCodec: PayloadCodec[W]
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (text == null || inputCodec == null || inputCodec.typeIdentity == null || inputCodec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow WorkOrder JSON codec is incomplete")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "handle", "request", "requirement", "presentation"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported WorkOrder schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "WORK_ORDER", "unsupported Continuation kind")
        handle <- _decodeHandle(root("handle"))
        request <- decodeRequest(root("request"), inputCodec)
        requirement <- _decode_requirement(root("requirement"))
        presentation <- decodePresentation(root("presentation"))
        _ <- _expect(request.context.snapshot.workflowRevision == handle.workflowRevision.value, "WorkOrder snapshot is stale")
      } yield WorkflowInteraction[W, Nothing](
        handle, WorkflowContinuation.WorkOrder(request, requirement, presentation)
      )
      decoded.fold(Consequence.stateConflict(_), x => x.handle.validateC.flatMap(_ =>
        x.current match {
          case order: WorkflowContinuation.WorkOrder[?] =>
            order.requirement.validateC.flatMap(_ => order.presentation.validateC.map(_ => x))
          case _ => Consequence.stateConflict("WorkOrder decoding did not produce WORK_ORDER")
        }
      ))
    }

  private def _encode_order_c[W](
    handle: WorkflowHandle,
    order: WorkflowContinuation.WorkOrder[W],
    codec: PayloadCodec[W]
  ): Consequence[String] =
    if (order.requirement == null || order.presentation == null)
      Consequence.stateConflict("Workflow WorkOrder JSON has an incomplete projection")
    else for {
      requestJson <- encodeRequestC(handle, order.request, codec)
      _ <- order.requirement.validateC
      _ <- order.presentation.validateC
    } yield Json.obj(
      "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
      "kind" -> Json.fromString("WORK_ORDER"),
      "handle" -> _handle(handle),
      "request" -> requestJson,
      "requirement" -> Json.obj(
        "capabilities" -> Json.arr(order.requirement.capabilities.map(x => Json.fromString(x.identity))*),
        "risk" -> Json.fromString(order.requirement.risk.value),
        "reasoning" -> Json.fromString(order.requirement.reasoning.value),
        "reviewRequired" -> Json.fromBoolean(order.requirement.reviewRequired)
      ),
      "presentation" -> presentationJson(order.presentation)
    ).noSpaces

  /** Shared request wire body for WORK_ORDER and DECISION, without claim authority. */
  private[workflow] def encodeRequestC[W](
    handle: WorkflowHandle,
    request: ContinuationRequest[W],
    codec: PayloadCodec[W]
  ): Consequence[Json] =
    if (handle == null || codec == null || handle.workflowRevision == null ||
        codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty ||
        request == null || request.runId == null || !_name(request.runId.value) ||
        request.continuationId == null || !_name(request.continuationId.value) ||
        request.expectedRevision == null || !_name(request.expectedRevision.value) ||
        request.requiredOperation == null || !_name(request.requiredOperation.capability) ||
        !_valid_operation(request.operation) || !_valid_operation(request.completionOperation) ||
        request.context == null || !_name(request.context.summary) || request.context.snapshot == null ||
        request.context.requiredFacts == null || request.context.references == null ||
        request.context.requiredFacts.exists(x => !_valid_reference(x)) ||
        request.context.references.exists(x => !_valid_reference(x)) ||
        request.completion == null || !_name(request.completion.identity) ||
        request.completion.requiredFacts == null ||
        request.completion.requiredFacts.exists(x => !_valid_reference(x)) ||
        request.evidence == null || !_name(request.evidence.identity) ||
        request.evidence.requiredEvidence == null ||
        request.evidence.requiredEvidence.exists(x => !_valid_reference(x)) ||
        request.input == null || request.resultType == null ||
        request.input.exists(x => x == null || x.typeIdentity != codec.typeIdentity) ||
        request.resultType.exists(x => x == null || !_name(x.value)) ||
        !_valid_snapshot(request.context.snapshot) ||
        request.context.snapshot.workflowRevision != handle.workflowRevision.value)
      Consequence.stateConflict("Workflow Continuation JSON has an incomplete or incompatible request")
    else for {
      _ <- handle.validateC
      _ <- request.input match {
        case Some(value) => value.validateC.map(_ => ())
        case None => Consequence.unit
      }
    } yield Json.obj(
        "runId" -> Json.fromString(request.runId.value),
        "continuationId" -> Json.fromString(request.continuationId.value),
        "expectedRevision" -> Json.fromString(request.expectedRevision.value),
        "requiredOperation" -> Json.fromString(request.requiredOperation.capability),
        "operation" -> _operation(request.operation),
        "input" -> request.input.map(x => Json.obj(
          "typeIdentity" -> Json.fromString(x.typeIdentity), "value" -> codec.encode(x.value)
        )).getOrElse(Json.Null),
        "resultType" -> request.resultType.map(x => Json.fromString(x.value)).getOrElse(Json.Null),
        "context" -> _context(request.context),
        "completion" -> Json.obj(
          "identity" -> Json.fromString(request.completion.identity),
          "requiredFacts" -> Json.arr(request.completion.requiredFacts.map(_reference)*)
        ),
        "evidence" -> Json.obj(
          "identity" -> Json.fromString(request.evidence.identity),
          "requiredEvidence" -> Json.arr(request.evidence.requiredEvidence.map(_reference)*)
        ),
        "completionOperation" -> _operation(request.completionOperation)
    )

  private[workflow] def decodeRequest[W](json: Json, codec: PayloadCodec[W]): Either[String, ContinuationRequest[W]] = for {
    fields <- _fields(json, Set("runId", "continuationId", "expectedRevision", "requiredOperation", "operation", "input", "resultType", "context", "completion", "evidence", "completionOperation"))
    run <- _string(fields, "runId")
    continuation <- _string(fields, "continuationId")
    revision <- _string(fields, "expectedRevision")
    required <- _string(fields, "requiredOperation")
    operation <- _decode_operation(fields("operation"))
    input <- _decode_input(fields("input"), codec)
    resultType <- _optional_string(fields, "resultType")
    context <- _decode_context(fields("context"))
    completionFields <- _fields(fields("completion"), Set("identity", "requiredFacts"))
    completionIdentity <- _string(completionFields, "identity")
    completionFacts <- _decodeReferences(completionFields("requiredFacts"))
    evidenceFields <- _fields(fields("evidence"), Set("identity", "requiredEvidence"))
    evidenceIdentity <- _string(evidenceFields, "identity")
    requiredEvidence <- _decodeReferences(evidenceFields("requiredEvidence"))
    completionOperation <- _decode_operation(fields("completionOperation"))
  } yield ContinuationRequest(
    StateMachineRunIdentity(run), ContinuationIdentity(continuation), StateMachineRevision(revision),
    StateMachineRequiredOperationIdentity(required), operation, input,
    resultType.map(StateMachineResultTypeReference.apply), context,
    CompletionContract(completionIdentity, completionFacts),
    EvidenceContract(evidenceIdentity, requiredEvidence), completionOperation
  )

  private def _decode_input[W](json: Json, codec: PayloadCodec[W]): Either[String, Option[TypedValue[W]]] =
    if (json.isNull) Right(None)
    else for {
      fields <- _fields(json, Set("typeIdentity", "value"))
      identity <- _string(fields, "typeIdentity")
      _ <- _expect(identity == codec.typeIdentity, "incompatible WorkOrder input type")
      value <- codec.decode(fields("value"))
      _ <- _expect(value != null, "null WorkOrder input")
    } yield Some(TypedValue(identity, value))

  private def _context(value: ContextBundle): Json = Json.obj(
    "summary" -> Json.fromString(value.summary),
    "requiredFacts" -> Json.arr(value.requiredFacts.map(_reference)*),
    "references" -> Json.arr(value.references.map(_reference)*),
    "snapshot" -> _snapshot(value.snapshot)
  )

  private def _decode_context(json: Json): Either[String, ContextBundle] = for {
    fields <- _fields(json, Set("summary", "requiredFacts", "references", "snapshot"))
    summary <- _string(fields, "summary")
    facts <- _decodeReferences(fields("requiredFacts"))
    references <- _decodeReferences(fields("references"))
    snapshot <- _decodeSnapshot(fields("snapshot"))
  } yield ContextBundle(summary, facts, references, snapshot)

  private def _operation(value: StateMachineOperationIdentity): Json = Json.obj(
    "service" -> Json.fromString(value.service), "operation" -> Json.fromString(value.operation)
  )

  private def _decode_operation(json: Json): Either[String, StateMachineOperationIdentity] = for {
    fields <- _fields(json, Set("service", "operation"))
    service <- _string(fields, "service")
    operation <- _string(fields, "operation")
  } yield StateMachineOperationIdentity(service, operation)

  private def _decode_requirement(json: Json): Either[String, ExecutionRequirement] = for {
    fields <- _fields(json, Set("capabilities", "risk", "reasoning", "reviewRequired"))
    capabilitiesJson <- fields("capabilities").asArray.toRight("capabilities must be an array")
    capabilities <- capabilitiesJson.foldLeft[Either[String, Vector[CapabilityRequirement]]](Right(Vector.empty)) {
      case (acc, item) => for {
        prior <- acc
        identity <- item.asString.filter(_.trim.nonEmpty).toRight("invalid capability")
      } yield prior :+ CapabilityRequirement(identity)
    }
    risk <- _string(fields, "risk")
    reasoning <- _string(fields, "reasoning")
    level <- ReasoningLevel.values.find(_.value == reasoning).toRight("unsupported reasoning level")
    review <- fields("reviewRequired").asBoolean.toRight("reviewRequired must be boolean")
  } yield ExecutionRequirement(capabilities, RiskLevel(risk), level, review)

  private[workflow] def presentationJson(value: MinimalPresentation): Json = Json.obj(
    "title" -> Json.fromString(value.title),
    "currentSituation" -> Json.fromString(value.currentSituation),
    "summary" -> _optional(value.summary),
    "nextAction" -> _optional(value.nextAction),
    "reason" -> _optional(value.reason),
    "progress" -> _optional(value.progress)
  )

  private[workflow] def decodePresentation(json: Json): Either[String, MinimalPresentation] = for {
    fields <- _fields(json, Set("title", "currentSituation", "summary", "nextAction", "reason", "progress"))
    title <- _string(fields, "title")
    situation <- _string(fields, "currentSituation")
    summary <- _optional_string(fields, "summary")
    action <- _optional_string(fields, "nextAction")
    reason <- _optional_string(fields, "reason")
    progress <- _optional_string(fields, "progress")
  } yield MinimalPresentation(title, situation, summary, action, reason, progress)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _valid_snapshot(value: ContextSnapshot): Boolean =
    _name(value.workflowRevision) &&
      _valid_optional(value.modelRevision) &&
      _valid_optional(value.workspaceRevision) &&
      _valid_optional(value.evidenceRevision)

  private def _valid_optional(value: Option[String]): Boolean =
    value != null && value.forall(_name)

  private def _valid_reference(value: ContextReference): Boolean =
    value != null && _name(value.identity) && _name(value.revision)

  private def _valid_operation(value: StateMachineOperationIdentity): Boolean =
    value != null && _name(value.service) && _name(value.operation)
}
