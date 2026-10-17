package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/**
 * Retained result bytes, not a command, claim token or progression selector.
 * @since   Sep. 29, 2026
 * @version Oct.  9, 2026
 */
object WorkflowCompletionRecordJsonV1 {
  val schemaVersion = "cncf.workflow-completion-record.v1"
  final case class Record[R](instanceRevision: InstanceRevision, result: ContinuationResult[R],
    closingResult: Option[StateMachineOperationResult] = None)

  def encodeC[R](record: Record[R], codec: PayloadCodec[R]): Consequence[String] = safelyC {
    if (record == null || record.instanceRevision == null || record.instanceRevision.value <= 0L ||
        record.closingResult == null)
      Consequence.stateConflict("Completion record revision is missing or invalid")
    else if (record.closingResult.nonEmpty) WorkflowCompletionRecordJsonV2.encodeC(record, codec)
    else for {
      bytes <- WorkflowResultJsonV1.encodeC(record.result, codec)
      json <- parse(bytes).fold(_ => Consequence.stateConflict("Invalid encoded completion result"), Consequence.success(_))
    } yield Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
      "instanceRevision" -> Json.fromLong(record.instanceRevision.value), "result" -> json).noSpaces
  }

  def decodeC[R](bytes: String, codec: PayloadCodec[R]): Consequence[Record[R]] = safelyC {
    import WorkflowResultJsonV1.{_returned_fields, _string, _expect}
    if (Option(bytes).flatMap(b => parse(b).toOption).flatMap(_.hcursor.get[String]("schemaVersion").toOption)
        .contains(WorkflowCompletionRecordJsonV2.schemaVersion))
      WorkflowCompletionRecordJsonV2.decodeC(bytes, codec)
    else {
    val envelope = for {
      json <- Option(bytes).toRight("Completion bytes are missing").flatMap(x => parse(x).left.map(_ => "Invalid completion JSON"))
      fields <- _returned_fields(json, Set("schemaVersion", "instanceRevision", "result"))
      version <- _string(fields, "schemaVersion")
      _ <- _expect(version == schemaVersion, "Unsupported completion schema")
      revision <- fields("instanceRevision").asNumber.flatMap(_.toLong).filter(_ > 0L).toRight("Invalid completing revision")
    } yield (revision, fields("result").noSpaces)
    envelope.fold(Consequence.stateConflict(_), pair =>
      WorkflowResultJsonV1.decodeC(pair._2, codec).map(result => Record(InstanceRevision(pair._1), result)))
    }
  }

  private def safelyC[A](operation: => Consequence[A]): Consequence[A] =
    try operation catch {
      case NonFatal(_) => Consequence.stateConflict("Completion application codec or envelope failed")
    }
}
