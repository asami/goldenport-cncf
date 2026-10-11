package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowCompletionRecordJsonV1.Record
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Joint retained semantic and actual closing Action results. V1 bytes remain
  * unchanged for completions without a closing Action. References are facts,
  * never a grant to resolve context or execute another Action.
  * @since   Oct.  1, 2026
  * @version Oct.  9, 2026
  */
private[workflow] object WorkflowCompletionRecordJsonV2 {
  val schemaVersion = "cncf.workflow-completion-record.v2"

  def encodeC[R](record: Record[R], codec: PayloadCodec[R]): Consequence[String] = safelyC {
    if (record == null || record.closingResult == null || record.closingResult.isEmpty ||
        !valid(record.closingResult.get))
      Consequence.stateConflict("Joint completion requires the actual typed closing result")
    else for {
      semantic <- WorkflowCompletionRecordJsonV1.encodeC(record.copy(closingResult = None), codec)
      json <- parse(semantic).fold(_ => Consequence.stateConflict("Invalid semantic completion JSON"), Consequence.success(_))
    } yield {
      val closing = record.closingResult.get
      Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
        "semanticCompletion" -> json,
        "closingResult" -> Json.obj("typeIdentity" -> Json.fromString(closing.typeReference.value),
          "contextIdentity" -> Json.fromString(closing.contextReference.identity),
          "contextRevision" -> Json.fromString(closing.contextReference.revision))).noSpaces
    }
  }

  def decodeC[R](bytes: String, codec: PayloadCodec[R]): Consequence[Record[R]] = safelyC {
    import WorkflowResultJsonV1.{_returned_fields, _string, _expect}
    val decoded = for {
      json <- Option(bytes).toRight("Joint completion bytes are missing").flatMap(b => parse(b).left.map(_ => "Invalid joint completion JSON"))
      fields <- _returned_fields(json, Set("schemaVersion", "semanticCompletion", "closingResult"))
      version <- _string(fields, "schemaVersion")
      _ <- _expect(version == schemaVersion, "Unsupported joint completion schema")
      semantic <- _returned_fields(fields("semanticCompletion"), Set("schemaVersion", "instanceRevision", "result"))
      semanticVersion <- _string(semantic, "schemaVersion")
      _ <- _expect(semanticVersion == WorkflowCompletionRecordJsonV1.schemaVersion, "Joint completion requires a V1 semantic envelope")
      closing <- _returned_fields(fields("closingResult"), Set("typeIdentity", "contextIdentity", "contextRevision"))
      typeIdentity <- _string(closing, "typeIdentity")
      contextIdentity <- _string(closing, "contextIdentity")
      contextRevision <- _string(closing, "contextRevision")
      result = StateMachineOperationResult(StateMachineResultTypeReference(typeIdentity), ContextReference(contextIdentity, contextRevision))
      _ <- _expect(valid(result), "Malformed closing result")
    } yield (fields("semanticCompletion").noSpaces, result)
    decoded.fold(Consequence.stateConflict(_), pair =>
      WorkflowCompletionRecordJsonV1.decodeC(pair._1, codec).map(_.copy(closingResult = Some(pair._2))))
  }

  private def valid(result: StateMachineOperationResult): Boolean =
    result != null && result.typeReference != null && result.contextReference != null &&
      Vector(result.typeReference.value, result.contextReference.identity, result.contextReference.revision)
        .forall(v => v != null && v.trim.nonEmpty)

  private def safelyC[A](operation: => Consequence[A]): Consequence[A] =
    try operation catch { case NonFatal(_) => Consequence.stateConflict("Joint completion codec failed") }
}
