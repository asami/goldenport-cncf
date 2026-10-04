package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import io.circe.Json
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * @since   Oct.  3, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object JobOperationalAuditTransport {
  def experience(operation: String, outcome: String, recordKind: String,
    atoms: Vector[(String, String | Int | Long | Boolean)])(using ExecutionContext): Unit =
    _emit("job.audit.v1", "job-experience", operation, outcome, recordKind, atoms)

  def lifecycle(outcome: String, recordKind: String,
    atoms: Vector[(String, String | Int | Long | Boolean)])(using ExecutionContext): Unit =
    _emit("job.lifecycle.audit.v1", "job-lifecycle", "emit", outcome, recordKind, atoms)

  def producer(atoms: Vector[(String, String | Int | Long | Boolean)])(using ExecutionContext): Unit =
    _emit("job.producer.audit.v1", "job-producer", "correlate", "submitted", "association", atoms)

  def eventPersistence(succeeded: Boolean,
    atoms: Vector[(String, String | Int | Long | Boolean)])(using ExecutionContext): Unit =
    _emit("event.persistence.audit.v1", "event-persistence", "persist",
      if (succeeded) "succeeded" else "failed", "acknowledgement", atoms)

  private def _emit(prefix: String, kind: String, operation: String, outcome: String,
    recordkind: String, atoms: Vector[(String, String | Int | Long | Boolean)])(using ctx: ExecutionContext): Unit =
    try {
      val context = ctx.observability
      val scope = ctx.cncfCore.scope
      val traceid = _reference(context.traceId.value)
      val correlationid = context.correlationId.map(_.value)
      if (traceid.nonEmpty && correlationid.forall(_safe(_, 512)) && _safe(scope.name, 128)) {
        val spanid = context.spanId.map(_.value).flatMap(_reference).map("span-id" -> _).toVector
        val fields: Vector[(String, String | Int | Long | Boolean)] = Vector(
          "audit.kind" -> kind, "operation" -> operation, "outcome" -> outcome,
          "record-kind" -> recordkind, "trace-id" -> traceid.get
        ) ++ spanid ++ correlationid.map("correlation-id" -> _).toVector ++ atoms
        val json = Json.fromFields(fields.map { case (key, atom) => key -> _json(atom) })
        val body = json.noSpaces
        val bytes = body.getBytes(UTF_8).length
        if (bytes <= 4096) {
          val name = s"$prefix bytes=$bytes $body"
          val suffix = (s"traceId=${traceid.get}" +: correlationid.map(id => s"correlationId=$id").toVector).mkString("[", " ", "]")
          val line = s"event=info scope=${scope.kind} name=${scope.name} $name $suffix"
          if (line.getBytes(UTF_8).length <= 8192) {
            val record = Record.dataAuto(fields.map { case (key, atom) => key -> (atom: Any) }*)
            context.emitInfo(scope, name, record)
          }
        }
      }
    } catch { case NonFatal(_) => () }

  private def _json(atom: String | Int | Long | Boolean): Json = atom match {
    case value: String => Json.fromString(value)
    case value: Int => Json.fromInt(value)
    case value: Long => Json.fromLong(value)
    case value: Boolean => Json.fromBoolean(value)
  }

  private def _safe(value: String, bound: Int): Boolean =
    value != null && value.nonEmpty && !value.exists(c => Character.isISOControl(c)) && value.getBytes(UTF_8).length <= bound

  private def _reference(value: String): Option[String] = Option(value).filter(_safe(_, 512))
}
