package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Fail-closed wire encoding for the separate-turn WorkOrder result boundary. */
object WorkflowResultJsonV1 {
  trait PayloadCodec[A] {
    def typeIdentity: String
    def encode(value: A): Json
    def decode(value: Json): Either[String, A]
  }

  def encodeC[A](value: ContinuationResult[A], codec: PayloadCodec[A]): Consequence[String] =
    if (value == null || value.result == null || codec == null || codec.typeIdentity != value.result.typeIdentity)
      Consequence.stateConflict("Workflow result JSON payload codec is incompatible")
    else _validate_envelope_c(value).map { _ =>
      Json.obj(
        "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
        "kind" -> Json.fromString("CONTINUATION_RESULT"),
        "handle" -> _handle(value.handle),
        "runId" -> Json.fromString(value.runId.value),
        "continuationId" -> Json.fromString(value.continuationId.value),
        "expectedRevision" -> Json.fromString(value.expectedRevision.value),
        "contextSnapshot" -> _snapshot(value.contextSnapshot),
        "result" -> Json.obj(
          "typeIdentity" -> Json.fromString(value.result.typeIdentity),
          "value" -> codec.encode(value.result.value),
          "reference" -> _reference(value.resultReference)
        ),
        "completionFacts" -> Json.arr(value.completionFacts.map(_reference)*),
        "executionEvidence" -> Json.obj(
          "references" -> Json.arr(value.evidence.references.map(_reference)*),
          "workerIdentity" -> _optional(value.evidence.workerIdentity),
          "modelIdentity" -> _optional(value.evidence.modelIdentity),
          "skillDispatch" -> value.evidence.skillDispatch.map(_skill_dispatch).getOrElse(Json.Null)
        )
      ).noSpaces
    }

  def decodeC[A](text: String, codec: PayloadCodec[A]): Consequence[ContinuationResult[A]] =
    if (text == null || codec == null || codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow result JSON codec is incomplete")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "handle", "runId", "continuationId", "expectedRevision", "contextSnapshot", "result", "completionFacts", "executionEvidence"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow result schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "CONTINUATION_RESULT", "unsupported Workflow result kind")
        handle <- _decodeHandle(root("handle"))
        run <- _string(root, "runId")
        continuation <- _string(root, "continuationId")
        revision <- _string(root, "expectedRevision")
        snapshot <- _decodeSnapshot(root("contextSnapshot"))
        result <- _fields(root("result"), Set("typeIdentity", "value", "reference"))
        resultType <- _string(result, "typeIdentity")
        _ <- _expect(resultType == codec.typeIdentity, "incompatible Workflow result type")
        payload <- codec.decode(result("value"))
        _ <- _expect(payload != null, "null Workflow result payload")
        reference <- _decodeReference(result("reference"))
        facts <- _decodeReferences(root("completionFacts"))
        evidence <- _fields(root("executionEvidence"), Set("references", "workerIdentity", "modelIdentity", "skillDispatch"))
        references <- _decodeReferences(evidence("references"))
        worker <- _optional_string(evidence, "workerIdentity")
        model <- _optional_string(evidence, "modelIdentity")
        dispatch <- _decode_skill_dispatch(evidence("skillDispatch"))
      } yield ContinuationResult(
        handle,
        StateMachineRunIdentity(run),
        ContinuationIdentity(continuation),
        StateMachineRevision(revision),
        snapshot,
        TypedValue(resultType, payload),
        reference,
        facts,
        ExecutionEvidence(references, worker, model, dispatch)
      )
      decoded.fold(Consequence.stateConflict(_), _validate_envelope_c)
    }

  private def _validate_envelope_c[A](value: ContinuationResult[A]): Consequence[ContinuationResult[A]] =
    if (value.handle == null || value.handle.workflowRevision == null ||
        value.runId == null || value.runId.value == null || value.runId.value.trim.isEmpty ||
        value.continuationId == null || value.continuationId.value == null || value.continuationId.value.trim.isEmpty ||
        value.expectedRevision == null || value.expectedRevision.value == null || value.expectedRevision.value.trim.isEmpty ||
        value.contextSnapshot == null || value.contextSnapshot.workflowRevision != value.handle.workflowRevision.value ||
        value.resultReference == null || !_valid_reference(value.resultReference) ||
        value.completionFacts == null || value.completionFacts.exists(x => !_valid_reference(x)) ||
        value.completionFacts.distinct.size != value.completionFacts.size ||
        value.evidence == null || value.evidence.references == null ||
        value.evidence.references.exists(x => !_valid_reference(x)) ||
        value.evidence.references.distinct.size != value.evidence.references.size ||
        value.evidence.workerIdentity == null || value.evidence.modelIdentity == null ||
        value.evidence.skillDispatch == null ||
        value.evidence.workerIdentity.exists(x => x == null || x.trim.isEmpty) ||
        value.evidence.modelIdentity.exists(x => x == null || x.trim.isEmpty) ||
        value.evidence.skillDispatch.exists(x => x == null || x.validateC.toOption.isEmpty))
      Consequence.stateConflict("Workflow result JSON envelope is incomplete or inconsistent")
    else value.handle.validateC.flatMap(_ => value.result.validateC.map(_ => value))

  private def _valid_reference(value: ContextReference): Boolean =
    value != null && value.identity != null && value.identity.trim.nonEmpty &&
      value.revision != null && value.revision.trim.nonEmpty

  private def _skill_dispatch(value: SkillDispatchEvidence): Json = Json.obj(
    "requestedRequirement" -> Json.obj(
      "capabilities" -> Json.arr(value.requestedRequirement.capabilities.map(x => Json.fromString(x.identity))*),
      "risk" -> Json.fromString(value.requestedRequirement.risk.value),
      "reasoning" -> Json.fromString(value.requestedRequirement.reasoning.value),
      "reviewRequired" -> Json.fromBoolean(value.requestedRequirement.reviewRequired)
    ),
    "selectedWorkerProfile" -> Json.fromString(value.selectedWorkerProfile),
    "mappingPolicyVersion" -> Json.fromString(value.mappingPolicyVersion)
  )

  private def _decode_skill_dispatch(json: Json): Either[String, Option[SkillDispatchEvidence]] =
    if (json.isNull) Right(None)
    else for {
      fields <- _fields(json, Set("requestedRequirement", "selectedWorkerProfile", "mappingPolicyVersion"))
      requirementFields <- _fields(fields("requestedRequirement"), Set("capabilities", "risk", "reasoning", "reviewRequired"))
      capabilitiesJson <- requirementFields("capabilities").asArray.toRight("Skill capabilities must be an array")
      capabilities <- capabilitiesJson.foldLeft[Either[String, Vector[CapabilityRequirement]]](Right(Vector.empty)) {
        case (acc, item) => for {
          prior <- acc
          identity <- item.asString.filter(_.trim.nonEmpty).toRight("invalid Skill capability")
        } yield prior :+ CapabilityRequirement(identity)
      }
      risk <- _string(requirementFields, "risk")
      reasoning <- _string(requirementFields, "reasoning")
      level <- ReasoningLevel.values.find(_.value == reasoning).toRight("unsupported Skill reasoning level")
      review <- requirementFields("reviewRequired").asBoolean.toRight("Skill reviewRequired must be boolean")
      profile <- _string(fields, "selectedWorkerProfile")
      policy <- _string(fields, "mappingPolicyVersion")
    } yield Some(SkillDispatchEvidence(
      ExecutionRequirement(capabilities, RiskLevel(risk), level, review), profile, policy
    ))

  private[workflow] def _handle(value: WorkflowHandle): Json = Json.obj(
    "componentIdentity" -> Json.fromString(value.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(value.workflowIdentity.value),
    "workflowRevision" -> Json.fromString(value.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(value.instanceIdentity.value),
    "protocolVersion" -> Json.fromString(value.protocolVersion)
  )

  private[workflow] def _decodeHandle(json: Json): Either[String, WorkflowHandle] = for {
    fields <- _fields(json, Set("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"))
    component <- _string(fields, "componentIdentity")
    identity <- _string(fields, "workflowIdentity")
    revision <- _string(fields, "workflowRevision")
    instance <- _string(fields, "instanceIdentity")
    version <- _string(fields, "protocolVersion")
    _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow handle version")
  } yield WorkflowHandle(
    ComponentId(component), WorkflowDefinitionIdentity(identity),
    WorkflowDefinitionRevision(revision), InstanceIdentity(instance), version
  )

  private[workflow] def _snapshot(value: ContextSnapshot): Json = Json.obj(
    "workflowRevision" -> Json.fromString(value.workflowRevision),
    "modelRevision" -> _optional(value.modelRevision),
    "workspaceRevision" -> _optional(value.workspaceRevision),
    "evidenceRevision" -> _optional(value.evidenceRevision)
  )

  private[workflow] def _decodeSnapshot(json: Json): Either[String, ContextSnapshot] = for {
    fields <- _fields(json, Set("workflowRevision", "modelRevision", "workspaceRevision", "evidenceRevision"))
    workflow <- _string(fields, "workflowRevision")
    model <- _optional_string(fields, "modelRevision")
    workspace <- _optional_string(fields, "workspaceRevision")
    evidence <- _optional_string(fields, "evidenceRevision")
  } yield ContextSnapshot(workflow, model, workspace, evidence)

  private[workflow] def _reference(value: ContextReference): Json = Json.obj(
    "identity" -> Json.fromString(value.identity),
    "revision" -> Json.fromString(value.revision)
  )

  private[workflow] def _decodeReference(json: Json): Either[String, ContextReference] = for {
    fields <- _fields(json, Set("identity", "revision"))
    identity <- _string(fields, "identity")
    revision <- _string(fields, "revision")
  } yield ContextReference(identity, revision)

  private[workflow] def _decodeReferences(json: Json): Either[String, Vector[ContextReference]] =
    json.asArray.toRight("expected references array").flatMap { values =>
      values.foldLeft[Either[String, Vector[ContextReference]]](Right(Vector.empty)) {
        case (acc, value) => for {
          prior <- acc
          decoded <- _decodeReference(value)
        } yield prior :+ decoded
      }
    }

  private[workflow] def _optional(value: Option[String]): Json = value match {
    case Some(x) => Json.fromString(x)
    case None => Json.Null
  }

  private[workflow] def _fields(json: Json, expected: Set[String]): Either[String, Map[String, Json]] =
    json.asObject match {
      case Some(obj) if obj.keys.toSet == expected => Right(obj.toMap)
      case _ => Left("Workflow result JSON fields are missing or unknown")
    }

  private[workflow] def _string(fields: Map[String, Json], key: String): Either[String, String] =
    fields.get(key).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"Workflow result JSON field $key must be a nonempty string")

  private[workflow] def _optional_string(fields: Map[String, Json], key: String): Either[String, Option[String]] =
    fields.get(key) match {
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.filter(_.trim.nonEmpty).map(Some(_))
        .toRight(s"Workflow result JSON field $key must be null or a nonempty string")
      case None => Left(s"Workflow result JSON field $key is missing")
    }

  private[workflow] def _expect(condition: Boolean, message: String): Either[String, Unit] =
    if (condition) Right(()) else Left(message)
}
