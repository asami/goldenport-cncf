package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Receipt
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Closed dedicated receipt. Reuses complete bounded native/payload encoding;
  * ordinary mechanical writers still cannot admit a terminal source.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
object WorkflowTerminalClosureContinuationJsonV1 {
  val schemaVersion = "cncf.workflow-terminal-closure-continuation.v1"
  def encodeC[A](receipt: Receipt[A], codec: PayloadCodec[A],
    limits: WorkflowMechanicalReceiptLimits): Consequence[String] = for {
    _ <- WorkflowTerminalClosureContinuation.validateReceiptC(receipt)
    raw <- WorkflowMechanicalTransitionJsonV1.encodeC(receipt, codec, limits)
    json <- parse(raw).fold(e => Consequence.stateConflict(e.message), Consequence.success)
    text = json.mapObject(_.add("schemaVersion", Json.fromString(schemaVersion))).noSpaces
    _ <- if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= limits.maxBytes) Consequence.unit
      else Consequence.stateConflict("Terminal continuation receipt exceeds configured complete-envelope limit")
  } yield text

  def decodeC[A](text: String, catalog: WorkflowInstanceRecordJsonV1.Catalog,
    codec: PayloadCodec[A], limits: WorkflowMechanicalReceiptLimits): Consequence[Receipt[A]] = for {
    _ <- if (text != null && limits != null && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= limits.maxBytes)
      Consequence.unit else Consequence.stateConflict("Terminal continuation receipt is absent or exceeds its complete-envelope limit")
    json <- parse(text).fold(e => Consequence.stateConflict(e.message), Consequence.success)
    _ <- if (json.hcursor.get[String]("schemaVersion").contains(schemaVersion)) Consequence.unit
      else Consequence.stateConflict("Unsupported terminal continuation receipt schema")
    ordinary = json.mapObject(_.add("schemaVersion", Json.fromString(WorkflowMechanicalTransitionJsonV1.schemaVersion))).noSpaces
    receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(ordinary, catalog, codec, limits)
    accepted <- WorkflowTerminalClosureContinuation.validateReceiptC(receipt)
  } yield accepted
}
