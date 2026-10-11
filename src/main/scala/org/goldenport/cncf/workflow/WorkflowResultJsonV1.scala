package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Result conversion only; transition authority belongs to the consuming Operation.
  * @since   Sep. 25, 2026
  * @version Oct.  9, 2026
  */
object WorkflowResultJsonV1 {
  trait PayloadCodec[A] {
    def typeIdentity: String
    def encode(value: A): Json
    def decode(value: Json): Either[String, A]
  }

  def encodeC[A](value: ContinuationResult[A], codec: PayloadCodec[A]): Consequence[String] =
    if (value == null || codec == null)
      Consequence.stateConflict("Workflow result JSON conversion requires a value and codec")
    else try {
      Consequence.success(Json.obj(
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
        "executionEvidence" -> Json.obj((Vector(
          "references" -> Json.arr(value.evidence.references.map(_reference)*),
          "workerIdentity" -> _optional(value.evidence.workerIdentity),
          "modelIdentity" -> _optional(value.evidence.modelIdentity),
          "skillDispatch" -> value.evidence.skillDispatch.map(_skill_dispatch).getOrElse(Json.Null)
        ) ++ value.evidence.execution.toVector.map(x => "execution" -> WorkflowExecutionJsonV1.observation(x)))*)
      ).noSpaces)
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Workflow result cannot be encoded")
    }

  def decodeC[A](text: String, codec: PayloadCodec[A]): Consequence[ContinuationResult[A]] =
    if (text == null || codec == null)
      Consequence.stateConflict("Workflow result JSON codec is incomplete")
    else try {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _returned_fields(json, Set("schemaVersion", "kind", "handle", "runId", "continuationId", "expectedRevision", "contextSnapshot", "result", "completionFacts", "executionEvidence"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow result schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "CONTINUATION_RESULT", "unsupported Workflow result kind")
        handle <- _decode_returned_handle(root("handle"))
        run <- _text(root, "runId")
        continuation <- _text(root, "continuationId")
        revision <- _text(root, "expectedRevision")
        snapshot <- _decode_returned_snapshot(root("contextSnapshot"))
        result <- _returned_fields(root("result"), Set("typeIdentity", "value", "reference"))
        resultType <- _text(result, "typeIdentity")
        payload <- codec.decode(result("value"))
        _ <- _expect(payload != null, "null Workflow result payload")
        reference <- _decode_returned_reference(result("reference"))
        facts <- _decode_returned_references(root("completionFacts"))
        evidence <- _returned_fields(root("executionEvidence"),
          Set("references", "workerIdentity", "modelIdentity", "skillDispatch"))
        references <- _decode_returned_references(evidence("references"))
        worker <- _optional_text(evidence, "workerIdentity")
        model <- _optional_text(evidence, "modelIdentity")
        dispatch <- _decode_skill_dispatch(evidence("skillDispatch"))
        execution <- evidence.get("execution") match {
          case None => Right(None)
          case Some(value) => WorkflowExecutionJsonV1.decodeReturnedObservation(value).map(Some(_))
        }
      } yield ContinuationResult(
        handle,
        StateMachineRunIdentity(run),
        ContinuationIdentity(continuation),
        StateMachineRevision(revision),
        snapshot,
        TypedValue(resultType, payload),
        reference,
        facts,
        ExecutionEvidence(references, worker, model, dispatch, execution)
      )
      decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Workflow result cannot be decoded")
    }

  /** Returned data may contain annotations and blank diagnostics. These readers
    * convert the required representation; shared issued-work readers stay unchanged.
    */
  private[workflow] def _returned_fields(json: Json, required: Set[String]): Either[String, Map[String, Json]] =
    json.asObject.toRight("returned result must be an object").flatMap { obj =>
      _expect(required.subsetOf(obj.keys.toSet), "returned result fields are missing").map(_ => obj.toMap)
    }

  private[workflow] def _text(fields: Map[String, Json], key: String): Either[String, String] =
    fields.get(key).flatMap(_.asString).toRight(s"returned result field $key must be a string")

  private[workflow] def _optional_text(fields: Map[String, Json], key: String): Either[String, Option[String]] =
    fields.get(key) match {
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.map(Some(_)).toRight(s"returned result field $key must be null or a string")
      case None => Left(s"returned result field $key is missing")
    }

  private def _decode_returned_handle(json: Json): Either[String, WorkflowHandle] = for {
    fields <- _returned_fields(json, Set("componentIdentity", "workflowIdentity", "workflowRevision", "instanceIdentity", "protocolVersion"))
    component <- _text(fields, "componentIdentity")
    identity <- _text(fields, "workflowIdentity")
    revision <- _text(fields, "workflowRevision")
    instance <- _text(fields, "instanceIdentity")
    version <- _text(fields, "protocolVersion")
  } yield WorkflowHandle(ComponentId(component), WorkflowDefinitionIdentity(identity),
    WorkflowDefinitionRevision(revision), InstanceIdentity(instance), version)

  private def _decode_returned_snapshot(json: Json): Either[String, ContextSnapshot] = for {
    fields <- _returned_fields(json, Set("workflowRevision", "modelRevision", "workspaceRevision", "evidenceRevision"))
    workflow <- _text(fields, "workflowRevision")
    model <- _optional_text(fields, "modelRevision")
    workspace <- _optional_text(fields, "workspaceRevision")
    evidence <- _optional_text(fields, "evidenceRevision")
  } yield ContextSnapshot(workflow, model, workspace, evidence)

  private def _decode_returned_reference(json: Json): Either[String, ContextReference] = for {
    fields <- _returned_fields(json, Set("identity", "revision"))
    identity <- _text(fields, "identity")
    revision <- _text(fields, "revision")
  } yield ContextReference(identity, revision)

  private def _decode_returned_references(json: Json): Either[String, Vector[ContextReference]] =
    json.asArray.toRight("expected references array").flatMap { values =>
      values.foldLeft[Either[String, Vector[ContextReference]]](Right(Vector.empty)) {
        case (acc, value) => for {
          prior <- acc
          decoded <- _decode_returned_reference(value)
        } yield prior :+ decoded
      }
    }

  private def _skill_dispatch(value: SkillDispatchEvidence): Json = Json.obj(
    "requestedRequirement" -> WorkflowExecutionJsonV1.requirement(value.requestedRequirement),
    "selectedWorkerProfile" -> Json.fromString(value.selectedWorkerProfile),
    "mappingPolicyVersion" -> Json.fromString(value.mappingPolicyVersion)
  )

  private def _decode_skill_dispatch(json: Json): Either[String, Option[SkillDispatchEvidence]] =
    if (json.isNull) Right(None)
    else for {
      fields <- _returned_fields(json, Set("requestedRequirement", "selectedWorkerProfile", "mappingPolicyVersion"))
      requirement <- WorkflowExecutionJsonV1.decodeReturnedRequirement(fields("requestedRequirement"))
      profile <- _text(fields, "selectedWorkerProfile")
      policy <- _text(fields, "mappingPolicyVersion")
    } yield Some(SkillDispatchEvidence(
      requirement, profile, policy
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
