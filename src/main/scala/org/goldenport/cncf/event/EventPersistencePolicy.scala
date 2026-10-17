package org.goldenport.cncf.event

import org.goldenport.Consequence

/** EventStore-owned versioned selection data, independent of storage permissions
  * and delivery authorization. Unmatched kinds are transient, not persist-all.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
final class EventPersistencePolicy private (
  val revision: String,
  val rules: Vector[EventPersistencePolicy.Rule]
) {
  private val decisions = rules.map(rule => rule.eventType.value -> rule.mode).toMap

  private[cncf] def selectC(records: Vector[EventRecord]):
      Consequence[Vector[EventRecord]] =
    if (records != null && records.exists(record => record != null && record.lane != EventLane.Transactional))
      Consequence.configurationInvalid("Workflow event selection requires the transactional lane")
    else selectOrdinaryC(records)

  private[event] def selectOrdinaryC(records: Vector[EventRecord]):
      Consequence[Vector[EventRecord]] = {
    if (records == null || records.size > EventPersistencePolicy.MAX_BATCH_SIZE ||
        records.exists(record => record == null || record.id == null ||
          !_canonicalKind(record.kind) || record.lane == null || record.sequence != 0L) ||
        records.map(_.id).distinct.size != records.size)
      Consequence.configurationInvalid("Event selection requires a bounded unissued batch with canonical kinds and unique identities")
    else records.foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) {
      (acc, record) => acc.flatMap { selected =>
        val mode = decisions.getOrElse(record.kind, EventPersistencePolicy.Mode.Transient)
        val normalized = record.copy(persistent = mode == EventPersistencePolicy.Mode.Persist)
        normalized._delivery_lookup_key = record._delivery_lookup_key
        Consequence.success(selected :+ normalized)
      }
    }
  }

  private def _canonicalKind(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isWhitespace)
}

object EventPersistencePolicy {
  val MAX_RULES = 256
  val MAX_BATCH_SIZE = 256

  enum Mode {
    case Persist, Transient
  }

  final case class Rule(eventType: EventTypeId, mode: Mode)

  def createC(revision: String, rules: Vector[Rule]): Consequence[EventPersistencePolicy] =
    if (!_canonical(revision) || rules == null || rules.isEmpty || rules.size > MAX_RULES ||
        rules.exists(rule => rule == null || rule.eventType == null ||
          !_canonical(rule.eventType.value) || rule.mode == null) ||
        rules.map(_.eventType).distinct.size != rules.size)
      Consequence.configurationInvalid("Event persistence policy requires a revision and unique explicit event-type rules")
    else Consequence.success(new EventPersistencePolicy(revision, rules))

  private def _canonical(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value &&
      !value.exists(_.isWhitespace) && !value.contains('*')
}
