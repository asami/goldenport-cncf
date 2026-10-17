package org.goldenport.cncf.event

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record

/**
 * Pure cursor facts. No storage identity, initialization, reset or authority.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class EventCursorRecordV1 private (
  val revision: Long,
  val lastSequence: Long
) {
  def toRecord: Record = Record.dataAuto(
    "cursor_schema" -> EventCursorRecordV1.schema,
    "revision" -> revision,
    "last_sequence" -> lastSequence.toString)

  /** A bounded proposal only; zero new durable facts produce no cursor work. */
  def proposeC(newDurableCount: Int): Consequence[Option[EventCursorRecordV1.Proposal]] =
    if (newDurableCount < 0 || newDurableCount > EventPersistencePolicy.MAX_BATCH_SIZE)
      Consequence.argumentInvalid("newDurableCount", "0..256", newDurableCount.toString)
    else if (newDurableCount == 0) Consequence.success(None)
    else if (revision == Long.MaxValue || lastSequence > Long.MaxValue - newDurableCount.toLong)
      Consequence.stateConflict("Event cursor revision or sequence overflows")
    else EventCursorRecordV1.createC(revision + 1L, lastSequence + newDurableCount.toLong).map { next =>
      Some(new EventCursorRecordV1.Proposal(this, next,
        Vector.tabulate(newDurableCount)(index => lastSequence + index.toLong + 1L)))
    }
}

object EventCursorRecordV1 {
  val schema = "cncf.event-cursor.v1"
  private val fields = Set("cursor_schema", "revision", "last_sequence")
  val initial: EventCursorRecordV1 = new EventCursorRecordV1(1L, 0L)

  /** Exact before/after facts; not a reserved range or commit receipt. */
  final class Proposal private[EventCursorRecordV1] (
    val expected: EventCursorRecordV1,
    val next: EventCursorRecordV1,
    val sequences: Vector[Long]
  )

  def createC(revision: Long, lastSequence: Long): Consequence[EventCursorRecordV1] =
    if (revision <= 0L || lastSequence < 0L)
      Consequence.stateConflict("Event cursor requires a positive revision and nonnegative sequence")
    else Consequence.success(new EventCursorRecordV1(revision, lastSequence))

  /** Logical fields only. Host/provider must separately validate physical id. */
  def decodeC(record: Record): Consequence[EventCursorRecordV1] =
    try {
      if (record == null || record.fields.size != fields.size ||
          record.fields.map(_.key).toSet != fields)
        Consequence.stateConflict("Event cursor fields are missing, duplicate or unexpected")
      else {
        def scalar(key: String): Any = record.fields.find(_.key == key).get.value.single match {
          case Some(value) => value
          case value => value
        }
        val revision = scalar("revision") match {
          case value: Long if value > 0L => Some(value)
          case value: Int if value > 0 => Some(value.toLong)
          case _ => None
        }
        val sequence = scalar("last_sequence") match {
          case value: String if value.length <= 19 && value.matches("0|[1-9][0-9]*") =>
            value.toLongOption
          case _ => None
        }
        (scalar("cursor_schema"), revision, sequence) match {
          case (value: String, Some(r), Some(s)) if value == schema => createC(r, s)
          case _ => Consequence.stateConflict("Event cursor schema, revision or canonical sequence is invalid")
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Event cursor record is invalid")
    }
}
