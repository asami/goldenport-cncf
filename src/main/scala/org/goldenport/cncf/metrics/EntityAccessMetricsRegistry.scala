package org.goldenport.cncf.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import scala.jdk.CollectionConverters.*

import org.goldenport.record.Record

/*
 * @since   Mar. 29, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityAccessMetricEntry(
  name: String,
  entity: Option[String],
  source: Option[String],
  outcome: Option[String],
  reason: Option[String],
  workingSetState: Option[String],
  count: Long
) {
  def toRecord: Record =
    Record.dataAuto(
      "name" -> name,
      "entity" -> entity,
      "source" -> source,
      "outcome" -> outcome,
      "reason" -> reason,
      "workingSetState" -> workingSetState,
      "count" -> count
    )
}

final class EntityAccessMetricsRegistry private () {
  import EntityAccessMetricsRegistry.*

  private val _counters = new ConcurrentHashMap[MetricKey, LongAdder]()

  def record(
    name: String,
    attributes: Record
  ): Unit = {
    val key = MetricKey(
      name = name,
      entity = _string(attributes, "entity"),
      source = _string(attributes, "source"),
      outcome = _string(attributes, "outcome"),
      reason = _string(attributes, "reason"),
      workingSetState = _string(attributes, "workingSetState")
    )
    val counter = _counters.computeIfAbsent(key, _ => new LongAdder())
    counter.increment()
  }

  def snapshot(): Vector[EntityAccessMetricEntry] =
    _counters.entrySet().asScala.toVector.map { entry =>
      EntityAccessMetricEntry(
        name = entry.getKey.name,
        entity = entry.getKey.entity,
        source = entry.getKey.source,
        outcome = entry.getKey.outcome,
        reason = entry.getKey.reason,
        workingSetState = entry.getKey.workingSetState,
        count = entry.getValue.sum()
      )
    }.sortBy(x => (x.name, x.entity.getOrElse(""), x.source.getOrElse(""), x.outcome.getOrElse(""), x.reason.getOrElse(""), x.workingSetState.getOrElse("")))

  def toRecord: Record =
    Record.data(
      "entries" -> snapshot().map(_.toRecord)
    )

  def clear(): Unit =
    _counters.clear()
}

object EntityAccessMetricsRegistry {
  private final case class MetricKey(
    name: String,
    entity: Option[String],
    source: Option[String],
    outcome: Option[String],
    reason: Option[String],
    workingSetState: Option[String]
  )

  val shared: EntityAccessMetricsRegistry = new EntityAccessMetricsRegistry()

  private def _string(record: Record, key: String): Option[String] =
    record.asMap.get(key).map(_.toString).map(_.trim).filter(_.nonEmpty)
}
