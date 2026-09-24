package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{PayloadCodec, _expect, _fields, _string}

/** A profile-selected Start request on the wire; payload admission stays with the profile. */
object WorkflowStartJsonV1 {
  def encodeC[I](request: WorkflowStartRequest[I], codec: PayloadCodec[I]): Consequence[String] =
    if (request == null || request.input == null || codec == null ||
        codec.typeIdentity == null || codec.typeIdentity != request.input.typeIdentity)
      Consequence.stateConflict("Workflow Start JSON payload codec is incompatible")
    else request.validateC.map { accepted =>
      Json.obj(
        "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
        "kind" -> Json.fromString("START"),
        "startOperation" -> Json.obj(
          "service" -> Json.fromString(accepted.startOperation.service),
          "operation" -> Json.fromString(accepted.startOperation.operation)
        ),
        "workflowIdentity" -> Json.fromString(accepted.workflowIdentity.value),
        "workflowRevision" -> Json.fromString(accepted.workflowRevision.value),
        "input" -> Json.obj(
          "typeIdentity" -> Json.fromString(accepted.input.typeIdentity),
          "value" -> codec.encode(accepted.input.value)
        ),
        "invocationReference" -> Json.fromString(accepted.invocationReference),
        "idempotencyKey" -> Json.fromString(accepted.idempotencyKey)
      ).noSpaces
    }

  def decodeC[I](text: String, codec: PayloadCodec[I]): Consequence[WorkflowStartRequest[I]] =
    if (text == null || codec == null || codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow Start JSON codec is incomplete")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "startOperation", "workflowIdentity",
          "workflowRevision", "input", "invocationReference", "idempotencyKey"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow Start schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "START", "unsupported Workflow Start kind")
        operationFields <- _fields(root("startOperation"), Set("service", "operation"))
        service <- _string(operationFields, "service")
        operation <- _string(operationFields, "operation")
        identity <- _string(root, "workflowIdentity")
        revision <- _string(root, "workflowRevision")
        inputFields <- _fields(root("input"), Set("typeIdentity", "value"))
        inputType <- _string(inputFields, "typeIdentity")
        _ <- _expect(inputType == codec.typeIdentity, "incompatible Workflow Start input type")
        payload <- codec.decode(inputFields("value"))
        _ <- _expect(payload != null, "null Workflow Start input payload")
        invocation <- _string(root, "invocationReference")
        idempotency <- _string(root, "idempotencyKey")
      } yield WorkflowStartRequest(
        StateMachineOperationIdentity(service, operation),
        WorkflowDefinitionIdentity(identity), WorkflowDefinitionRevision(revision),
        TypedValue(inputType, payload), invocation, idempotency
      )
      decoded.fold(Consequence.stateConflict(_), _.validateC)
    }

  /** A selected profile Operation must check the wire identity against its admitted binding. */
  def decodeBoundC[I](
    text: String,
    codec: PayloadCodec[I],
    expectedOperation: StateMachineOperationIdentity,
    expectedWorkflow: WorkflowDefinitionIdentity,
    expectedRevision: WorkflowDefinitionRevision
  ): Consequence[WorkflowStartRequest[I]] =
    if (expectedOperation == null || expectedWorkflow == null || expectedRevision == null)
      Consequence.stateConflict("Workflow Start binding is incomplete")
    else decodeC(text, codec).flatMap { request =>
      if (request.startOperation == expectedOperation &&
          request.workflowIdentity == expectedWorkflow &&
          request.workflowRevision == expectedRevision)
        Consequence.success(request)
      else Consequence.stateConflict("Workflow Start differs from the selected profile Operation")
    }
}
