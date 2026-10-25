package org.goldenport.cncf.event

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.record.Record

/** Opaque generic lookup metadata; it carries no business or access authority.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
final case class EventHistoryCorrelation(namespace: String, key: String) {
  def validateC: Consequence[Unit] =
    if (EventHistoryCorrelation.validText(namespace, 128) &&
        EventHistoryCorrelation.validText(key, 256)) Consequence.unit
    else Consequence.valueInvalid("Event history correlation is invalid")
}

object EventHistoryCorrelation {
  val namespaceAttribute = "history.namespace"
  val keyAttribute = "history.key"
  val namespaceField = "event_history_namespace"
  val keyField = "event_history_key"
  val nameField = "event_history_name"
  val sequenceField = "event_history_sequence"

  private[cncf] def validText(value: String, maximumBytes: Int): Boolean =
    value != null && value.nonEmpty && value.strip == value && !value.isBlank &&
      !value.exists(character => Character.isISOControl(character)) &&
      UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximumBytes

  def fromAttributesC(attributes: Map[String, String]): Consequence[Option[EventHistoryCorrelation]] =
    if (attributes == null) Consequence.valueInvalid("Event attributes are missing")
    else (attributes.get(namespaceAttribute), attributes.get(keyAttribute)) match {
      case (None, None) => Consequence.success(None)
      case (Some(namespace), Some(key)) =>
        val correlation = EventHistoryCorrelation(namespace, key)
        correlation.validateC.map(_ => Some(correlation))
      case _ => Consequence.valueInvalid("Event history requires both correlation attributes")
    }

  /** Pure same-row metadata; canonical encoded event bytes are never changed. */
  def projectC(event: EventRecord): Consequence[Record] =
    if (event == null) Consequence.valueInvalid("Event is missing")
    else fromAttributesC(event.attributes).flatMap {
      case None => Consequence.success(Record.empty)
      case Some(correlation) =>
        if (!event.persistent || event.lane != EventLane.Transactional || event.sequence <= 0L ||
            !validText(event.name, 512) || event.name.length > 128)
          Consequence.valueInvalid("Event history requires a named sequenced persistent transactional record")
        else Consequence.success(Record.dataAuto(
          namespaceField -> correlation.namespace, keyField -> correlation.key,
          nameField -> event.name, sequenceField -> event.sequence))
    }
}
