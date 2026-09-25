package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.*
import org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.{decodePresentation, decodeRequest, encodeRequestC, presentationJson}

/** A typed DECISION wire projection; the wire document itself grants no claim or resume authority. */
object WorkflowDecisionJsonV1 {
  def encodeC[W](
    interaction: WorkflowInteraction[W, Nothing],
    inputCodec: PayloadCodec[W]
  ): Consequence[String] =
    if (interaction == null || interaction.handle == null || inputCodec == null)
      Consequence.stateConflict("Workflow Decision JSON projection is incomplete")
    else interaction.current match {
      case decision: WorkflowContinuation.Decision[?] =>
        val typed = decision.asInstanceOf[WorkflowContinuation.Decision[W]]
        if (typed.presentation == null)
          Consequence.stateConflict("Workflow Decision presentation is incomplete")
        else for {
          request <- encodeRequestC(interaction.handle, typed.request, inputCodec)
          _ <- typed.presentation.validateC
        } yield Json.obj(
          "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion),
          "kind" -> Json.fromString("DECISION"),
          "handle" -> _handle(interaction.handle),
          "request" -> request,
          "presentation" -> presentationJson(typed.presentation)
        ).noSpaces
      case _ => Consequence.stateConflict("Workflow Decision JSON requires DECISION continuation")
    }

  def decodeC[W](
    text: String,
    inputCodec: PayloadCodec[W]
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (text == null || inputCodec == null || inputCodec.typeIdentity == null ||
        inputCodec.typeIdentity.trim.isEmpty)
      Consequence.stateConflict("Workflow Decision JSON codec is incomplete")
    else {
      val decoded = for {
        json <- parse(text).left.map(_ => "invalid JSON")
        root <- _fields(json, Set("schemaVersion", "kind", "handle", "request", "presentation"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == WorkflowProtocolV1.schemaVersion, "unsupported Workflow Decision schema")
        kind <- _string(root, "kind")
        _ <- _expect(kind == "DECISION", "unsupported Workflow Decision kind")
        handle <- _decodeHandle(root("handle"))
        request <- decodeRequest(root("request"), inputCodec)
        presentation <- decodePresentation(root("presentation"))
        _ <- _expect(request.context.snapshot.workflowRevision == handle.workflowRevision.value,
          "Workflow Decision snapshot is stale")
      } yield WorkflowInteraction[W, Nothing](
        handle, WorkflowContinuation.Decision(request, presentation)
      )
      decoded.fold(Consequence.stateConflict(_), interaction =>
        interaction.handle.validateC.flatMap(_ => interaction.current match {
          case decision: WorkflowContinuation.Decision[?] =>
            decision.presentation.validateC.map(_ => interaction)
          case _ => Consequence.stateConflict("Workflow Decision decoding did not produce DECISION")
        })
      )
    }
}
