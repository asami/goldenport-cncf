package org.goldenport.cncf.event

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.JobOperationalAuditTransport

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EventPersistenceOperationalAudit {
  def observe(expectedName: String, expectedKind: String,
    result: Consequence[Vector[EventRecord]])(using ExecutionContext): Unit =
    try {
      result match {
        case Consequence.Success(records) =>
          val identity = _identity(expectedName, expectedKind, records)
          val atoms: Vector[(String, String | Int | Long | Boolean)] =
            Vector("event-identity-available" -> identity.nonEmpty) ++ identity
          JobOperationalAuditTransport.eventPersistence(true, atoms)
        case Consequence.Failure(_) =>
          JobOperationalAuditTransport.eventPersistence(false, Vector("event-identity-available" -> false))
        case null => ()
      }
    } catch { case NonFatal(_) => () }

  private def _identity(expectedname: String, expectedkind: String,
    records: Vector[EventRecord]): Vector[(String, String | Int | Long | Boolean)] = {
    if (records != null && records.size == 1 && expectedname != null && expectedkind != null) {
      val record = records.head
      if (record != null && record.name == expectedname && record.kind == expectedkind &&
        record.status == EventRecord.Status.Stored && record.lane == EventLane.NonTransactional &&
        record.persistent && record.createdAt != null && record.sequence >= 0 && record.id != null) {
        val id = record.id.value
        if (_safe(id)) Vector("event-id" -> id, "event-sequence" -> record.sequence,
          "event-lane" -> "non-transactional", "occurred-at" -> record.createdAt.toString)
        else Vector.empty
      } else Vector.empty
    } else Vector.empty
  }

  private def _safe(value: String): Boolean =
    value != null && value.nonEmpty && !value.exists(c => Character.isISOControl(c)) &&
      value.getBytes(UTF_8).length <= 512
}
