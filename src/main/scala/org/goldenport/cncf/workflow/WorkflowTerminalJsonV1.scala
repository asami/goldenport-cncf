package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.*

/** Strict wire projection of a completed WorkflowInteraction; JSON is not progression authority. */
object WorkflowTerminalJsonV1 {
  def encodeC[O](
    interaction: WorkflowInteraction[Nothing, O],
    codec: PayloadCodec[O]
  ): Consequence[String] =
    if (interaction == null || interaction.handle == null || codec == null ||
        codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow Terminal JSON projection is incomplete")
    else interaction.current match {
      case terminal: WorkflowContinuation.Terminal[?] =>
        val typed = terminal.asInstanceOf[WorkflowContinuation.Terminal[O]]
        if (typed.result == null || typed.result.typeIdentity != codec.typeIdentity ||
            typed.presentation == null)
          Consequence.stateConflict("Workflow Terminal JSON payload codec is incompatible")
        else for {
          handle <- interaction.handle.validateC
          result <- typed.result.validateC
          presentation <- typed.presentation.validateC
        } yield Json.obj(
          "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
          "kind" -> Json.fromString("TERMINAL"),
          "handle" -> _handle(handle),
          "result" -> Json.obj(
            "typeIdentity" -> Json.fromString(result.typeIdentity),
            "value" -> codec.encode(result.value)
          ),
          "presentation" -> _presentation(presentation)
        ).noSpaces
      case _ => Consequence.stateConflict("Workflow Terminal JSON requires TERMINAL continuation")
    }

  def decodeC[O](
    text: String,
    codec: PayloadCodec[O]
  ): Consequence[WorkflowInteraction[Nothing, O]] =
    if (text == null || codec == null || codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow Terminal JSON codec is incomplete")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "handle", "result", "presentation"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow Terminal schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "TERMINAL", "unsupported Workflow Terminal kind")
        handle <- _decodeHandle(root("handle"))
        resultFields <- _fields(root("result"), Set("typeIdentity", "value"))
        resultType <- _string(resultFields, "typeIdentity")
        _ <- _expect(resultType == codec.typeIdentity, "incompatible Workflow Terminal result type")
        payload <- codec.decode(resultFields("value"))
        _ <- _expect(payload != null, "null Workflow Terminal result payload")
        presentation <- _decode_presentation(root("presentation"))
      } yield WorkflowInteraction[Nothing, O](
        handle, WorkflowContinuation.Terminal(TypedValue(resultType, payload), presentation)
      )
      decoded.fold(Consequence.stateConflict(_), interaction =>
        interaction.handle.validateC.flatMap(_ => interaction.current match {
          case terminal: WorkflowContinuation.Terminal[?] =>
            terminal.result.validateC.flatMap(_ => terminal.presentation.validateC.map(_ => interaction))
          case _ => Consequence.stateConflict("Workflow Terminal decoding did not produce TERMINAL")
        })
      )
    }

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
