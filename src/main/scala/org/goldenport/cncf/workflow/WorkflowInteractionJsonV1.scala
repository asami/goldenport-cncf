package org.goldenport.cncf.workflow

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** One closed wire entry point for a current Continuation; variant codecs remain authoritative. */
object WorkflowInteractionJsonV1 {
  def encodeC[W, O](
    interaction: WorkflowInteraction[W, O],
    workCodec: PayloadCodec[W],
    terminalCodec: PayloadCodec[O]
  ): Consequence[String] =
    if (interaction == null || interaction.handle == null || interaction.current == null)
      Consequence.stateConflict("Workflow Interaction JSON is incomplete")
    else interaction.current match {
      case value: WorkflowContinuation.WorkOrder[?] =>
        WorkflowWorkOrderJsonV1.encodeC(
          WorkflowInteraction(interaction.handle, value.asInstanceOf[WorkflowContinuation.WorkOrder[W]]), workCodec
        )
      case value: WorkflowContinuation.Decision[?] =>
        WorkflowDecisionJsonV1.encodeC(
          WorkflowInteraction(interaction.handle, value.asInstanceOf[WorkflowContinuation.Decision[W]]), workCodec
        )
      case value: WorkflowContinuation.Wait =>
        WorkflowWaitJsonV1.encodeC(WorkflowInteraction(interaction.handle, value))
      case value: WorkflowContinuation.Terminal[?] =>
        WorkflowTerminalJsonV1.encodeC(
          WorkflowInteraction(interaction.handle, value.asInstanceOf[WorkflowContinuation.Terminal[O]]), terminalCodec
        )
    }

  def decodeC[W, O](
    text: String,
    workCodec: PayloadCodec[W],
    terminalCodec: PayloadCodec[O]
  ): Consequence[WorkflowInteraction[W, O]] =
    if (text == null || workCodec == null || terminalCodec == null)
      Consequence.stateConflict("Workflow Interaction JSON codecs are incomplete")
    else {
      val kind = for {
        json <- parse(text).left.map(_ => "invalid Workflow Interaction JSON")
        fields <- json.asObject.toRight("Workflow Interaction JSON must be an object")
        version <- fields("schemaVersion").flatMap(_.asString)
          .toRight("Workflow Interaction schema is missing")
        _ <- Either.cond(version == WorkflowProtocolV1.schemaVersion, (),
          "unsupported Workflow Interaction schema")
        value <- fields("kind").flatMap(_.asString)
          .toRight("Workflow Interaction kind is missing")
      } yield value
      kind.fold(Consequence.stateConflict(_), {
        case "WORK_ORDER" => WorkflowWorkOrderJsonV1.decodeC(text, workCodec)
          .map(x => WorkflowInteraction[W, O](x.handle, x.current))
        case "DECISION" => WorkflowDecisionJsonV1.decodeC(text, workCodec)
          .map(x => WorkflowInteraction[W, O](x.handle, x.current))
        case "WAIT" => WorkflowWaitJsonV1.decodeC(text)
          .map(x => WorkflowInteraction[W, O](x.handle, x.current))
        case "TERMINAL" => WorkflowTerminalJsonV1.decodeC(text, terminalCodec)
          .map(x => WorkflowInteraction[W, O](x.handle, x.current))
        case _ => Consequence.stateConflict("unsupported Workflow Interaction kind")
      })
    }
}
