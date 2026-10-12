package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.{PayloadCodec, _required_fields, _string, _expect}

/**
 * Separate closed mechanical receipt schema, never a ContinuationResult.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
object WorkflowMechanicalTransitionJsonV1 {
  val schemaVersion = "cncf.workflow-mechanical-transition.v1"
  private val _max_bytes = 1024 * 1024

  def encodeC[A](receipt: Receipt[A], codec: PayloadCodec[A]): Consequence[String] = _guarded {
    if (receipt == null) Consequence.stateConflict("Mechanical receipt is missing")
    else for {
      current <- WorkflowInstanceRecordJsonV1.encodeC(receipt.current)
      next <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
      text <- _format_c(receipt, codec, current, next)
    } yield text
  }

  /** Precondition: current was consumed by native load and next was constructed
    * by the incremental append in this call chain.
    */
  private[workflow] def _encode_c_unchecked[A](receipt: Receipt[A], codec: PayloadCodec[A]): Consequence[String] =
    _guarded {
      _format_c(receipt, codec, WorkflowInstanceRecordJsonV1._encode_unchecked(receipt.current),
        WorkflowInstanceRecordJsonV1._encode_unchecked(receipt.next))
    }

  private def _format_c[A](receipt: Receipt[A], codec: PayloadCodec[A], current: String,
    next: String): Consequence[String] = {
    if (!_valid_codec(codec) || receipt == null || receipt.reference == null ||
        receipt.component == null || !canonical(receipt.component.name) || receipt.operation == null ||
        !canonical(receipt.operation.service) || !canonical(receipt.operation.operation) || receipt.payload == null ||
        receipt.payload.typeIdentity != codec.typeIdentity)
      Consequence.stateConflict("Mechanical receipt payload codec is incompatible")
    else for {
      _ <- keyC(receipt.reference)
      accepted = receipt
      currentjson <- _json_c(current)
      nextjson <- _json_c(next)
      text = Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
        "reference" -> Json.obj("identity" -> Json.fromString(accepted.reference.identity),
          "revision" -> Json.fromString(accepted.reference.revision)),
        "component" -> Json.fromString(accepted.component.name),
        "operation" -> Json.obj("service" -> Json.fromString(accepted.operation.service),
          "operation" -> Json.fromString(accepted.operation.operation)),
        "current" -> currentjson, "next" -> nextjson,
        "payload" -> Json.obj("typeIdentity" -> Json.fromString(codec.typeIdentity),
          "value" -> codec.encode(accepted.payload.value))).noSpaces
      _ <- _bounded_c(text)
    } yield text
  }

  def decodeC[A](text: String, catalog: WorkflowInstanceRecordJsonV1.Catalog,
    codec: PayloadCodec[A]): Consequence[Receipt[A]] = _guarded {
    if (catalog == null || !_valid_codec(codec))
      Consequence.stateConflict("Mechanical receipt catalog or codec is missing")
    else _bounded_c(text).flatMap { _ =>
      val fields = for {
        json <- parse(text).left.map(_ => "Invalid mechanical receipt JSON")
        root <- _required_fields(json, Set("schemaVersion", "reference", "component", "operation", "current", "next", "payload"))
        version <- _string(root, "schemaVersion")
        _ <- _expect(version == schemaVersion, "Unsupported mechanical receipt schema")
        ref <- _required_fields(root("reference"), Set("identity", "revision"))
        id <- _string(ref, "identity")
        revision <- _string(ref, "revision")
        component <- _string(root, "component")
        op <- _required_fields(root("operation"), Set("service", "operation"))
        service <- _string(op, "service")
        operation <- _string(op, "operation")
        payload <- _required_fields(root("payload"), Set("typeIdentity", "value"))
        kind <- _string(payload, "typeIdentity")
        _ <- _expect(kind == codec.typeIdentity, "Incompatible mechanical receipt payload type")
        value <- codec.decode(payload("value"))
        _ <- _expect(value != null, "Null mechanical receipt payload")
      } yield (root, Reference(id, revision), ComponentId(component),
        StateMachineOperationIdentity(service, operation), TypedValue(kind, value))
      fields.fold(Consequence.stateConflict(_), data => for {
        current <- WorkflowInstanceRecordJsonV1.decodeC(data._1("current").noSpaces, catalog)
        next <- WorkflowInstanceRecordJsonV1.decodeC(data._1("next").noSpaces, catalog)
        _ <- keyC(data._2)
        _ <- if (canonical(data._3.name) && canonical(data._4.service) && canonical(data._4.operation))
          Consequence.unit else Consequence.stateConflict("Mechanical receipt owner or operation is noncanonical")
      } yield Receipt(data._2, data._3, data._4, current, next, data._5))
    }
  }

  private def _valid_codec[A](codec: PayloadCodec[A]): Boolean =
    codec != null && canonical(codec.typeIdentity)
  private def _bounded_c(text: String): Consequence[Unit] = {
    if (text == null || text.getBytes(UTF_8).length > _max_bytes)
      Consequence.stateConflict("Mechanical receipt encoding is absent or exceeds its bound")
    else Consequence.unit
  }
  private def _json_c(text: String): Consequence[Json] =
    parse(text).fold(_ => Consequence.stateConflict("Invalid mechanical instance encoding"), Consequence.success)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = {
     try body catch {
    case e: InterruptedException => Thread.currentThread().interrupt(); throw e
    case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
  }
  }
}
