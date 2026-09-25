package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.*

/** A status projection only; a WAIT document grants no continuation or resume authority. */
object WorkflowWaitJsonV1 {
  def encodeC(interaction: WorkflowInteraction[Nothing, Nothing]): Consequence[String] =
    if (interaction == null || interaction.handle == null)
      Consequence.stateConflict("Workflow Wait JSON projection is incomplete")
    else interaction.current match {
      case wait: WorkflowContinuation.Wait =>
        for {
          handle <- interaction.handle.validateC
          _ <- _validate_wait_c(handle, wait)
        } yield Json.obj(
          "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
          "kind" -> Json.fromString("WAIT"),
          "handle" -> _handle(handle),
          "expectedRevision" -> Json.fromString(wait.expectedRevision.value),
          "contextSnapshot" -> _snapshot(wait.contextSnapshot),
          "presentation" -> _presentation(wait.presentation)
        ).noSpaces
      case _ => Consequence.stateConflict("Workflow Wait JSON requires WAIT continuation")
    }

  def decodeC(text: String): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (text == null)
      Consequence.stateConflict("Workflow Wait JSON is absent")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "handle", "expectedRevision", "contextSnapshot", "presentation"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow Wait schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "WAIT", "unsupported Workflow Wait kind")
        handle <- _decodeHandle(root("handle"))
        revision <- _string(root, "expectedRevision")
        snapshot <- _decodeSnapshot(root("contextSnapshot"))
        presentation <- _decode_presentation(root("presentation"))
      } yield WorkflowInteraction[Nothing, Nothing](
        handle,
        WorkflowContinuation.Wait(StateMachineRevision(revision), snapshot, presentation)
      )
      decoded.fold(Consequence.stateConflict(_), interaction =>
        interaction.handle.validateC.flatMap { handle =>
          interaction.current match {
            case wait: WorkflowContinuation.Wait =>
              _validate_wait_c(handle, wait).map(_ => interaction)
            case _ => Consequence.stateConflict("Workflow Wait decoding did not produce WAIT")
          }
        }
      )
    }

  private def _validate_wait_c(
    handle: WorkflowHandle,
    wait: WorkflowContinuation.Wait
  ): Consequence[Unit] =
    if (wait.expectedRevision == null || !_name(wait.expectedRevision.value) ||
        wait.contextSnapshot == null ||
        wait.contextSnapshot.workflowRevision != handle.workflowRevision.value ||
        !_valid_optional(wait.contextSnapshot.modelRevision) ||
        !_valid_optional(wait.contextSnapshot.workspaceRevision) ||
        !_valid_optional(wait.contextSnapshot.evidenceRevision) ||
        wait.presentation == null)
      Consequence.stateConflict("Workflow Wait JSON state reference is incomplete or incompatible")
    else wait.presentation.validateC.map(_ => ())

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _valid_optional(value: Option[String]): Boolean =
    value != null && value.forall(_name)

  private def _presentation(value: MinimalPresentation): Json = Json.obj(
    "title" -> Json.fromString(value.title),
    "currentSituation" -> Json.fromString(value.currentSituation),
    "summary" -> _optional(value.summary),
    "nextAction" -> _optional(value.nextAction),
    "reason" -> _optional(value.reason),
    "progress" -> _optional(value.progress)
  )

  private def _decode_presentation(json: Json): Either[String, MinimalPresentation] = for {
    fields <- _fields(json, Set("title", "currentSituation", "summary", "nextAction", "reason", "progress"))
    title <- _string(fields, "title")
    situation <- _string(fields, "currentSituation")
    summary <- _optional_string(fields, "summary")
    action <- _optional_string(fields, "nextAction")
    reason <- _optional_string(fields, "reason")
    progress <- _optional_string(fields, "progress")
  } yield MinimalPresentation(title, situation, summary, action, reason, progress)
}
