package org.goldenport.cncf.metrics

import scala.collection.mutable

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentMetricDefinition(
  name: String,
  labelKeys: Vector[String],
  maxSeries: Int = 64
) {
  require(ComponentMetricDefinition.isValidName(name), s"invalid component metric name: $name")
  require(labelKeys.nonEmpty, "component metric labels are required")
  require(labelKeys.contains("component"), "component metric labels must include component")
  require(labelKeys.distinct.size == labelKeys.size, s"duplicate component metric label keys: $labelKeys")
  require(labelKeys.forall(ComponentMetricDefinition.isValidLabelKey), s"invalid component metric label keys: $labelKeys")
  require(maxSeries > 0 && maxSeries <= 256, s"component metric maxSeries must be in 1..256: $maxSeries")
}

object ComponentMetricDefinition {
  private val NAME_PATTERN = "[a-z][a-z0-9_.-]*".r
  private val LABEL_KEY_PATTERN = "[a-z][a-z0-9_]*".r

  private[metrics] def isValidName(value: String): Boolean =
    NAME_PATTERN.matches(Option(value).getOrElse(""))

  private[metrics] def isValidLabelKey(value: String): Boolean =
    LABEL_KEY_PATTERN.matches(Option(value).getOrElse(""))
}

final case class ComponentMetricEntry(
  name: String,
  labels: Map[String, String],
  count: Long,
  errorCount: Long,
  durationCount: Long,
  durationTotalMillis: Long,
  durationMinMillis: Option[Long],
  durationMaxMillis: Option[Long]
) {
  def toRuntimeMetricPoint: RuntimeMetricPoint =
    RuntimeMetricPoint(
      scope = "component",
      name = name,
      labels = labels,
      count = count,
      errorCount = errorCount,
      durationCount = durationCount,
      durationTotalMillis = durationTotalMillis,
      durationMinMillis = durationMinMillis,
      durationMaxMillis = durationMaxMillis
    )
}

final class ComponentMetricsRegistry private () {
  import ComponentMetricsRegistry.*

  private val _series = mutable.LinkedHashMap.empty[MetricSeriesKey, MetricAccumulator]

  // Returns false when an unapproved label or a new over-limit series is rejected.
  def record(
    definition: ComponentMetricDefinition,
    labels: Map[String, String],
    error: Boolean = false,
    durationmillis: Option[Long] = None
  ): Boolean = synchronized {
    _normalize_labels(definition, labels) match {
      case None => false
      case Some(normalized) =>
        val key = MetricSeriesKey(definition, normalized)
        _series.get(key) match {
          case Some(accumulator) =>
            accumulator.record(error, durationmillis)
            true
          case None if _series.keysIterator.count(_.definition == definition) >= definition.maxSeries =>
            false
          case None =>
            val accumulator = new MetricAccumulator
            accumulator.record(error, durationmillis)
            _series += key -> accumulator
            true
        }
    }
  }

  def snapshot(): Vector[ComponentMetricEntry] = synchronized {
    _series.iterator.map { case (key, value) =>
      ComponentMetricEntry(
        key.definition.name,
        key.labels,
        value.count,
        value.errorCount,
        value.durationCount,
        value.durationTotalMillis,
        value.durationMinMillis,
        value.durationMaxMillis
      )
    }.toVector.sortBy(x => (x.name, x.labels.toVector.sortBy(_._1).mkString("|")))
  }

  def clear(): Unit = synchronized {
    _series.clear()
  }

  private def _normalize_labels(
    definition: ComponentMetricDefinition,
    labels: Map[String, String]
  ): Option[Map[String, String]] = {
    val normalized = labels.iterator.map { case (key, value) =>
      key -> Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    }.toMap
    Option.when(
      normalized.keySet.subsetOf(definition.labelKeys.toSet) &&
        normalized.nonEmpty &&
        normalized.forall { case (key, value) =>
          ComponentMetricDefinition.isValidLabelKey(key) && VALUE_PATTERN.matches(value)
        }
    )(normalized)
  }
}

object ComponentMetricsRegistry {
  private val VALUE_PATTERN = "[a-z0-9][a-z0-9_.:-]{0,63}".r

  private final case class MetricSeriesKey(
    definition: ComponentMetricDefinition,
    labels: Map[String, String]
  )

  private final class MetricAccumulator {
    private var _count = 0L
    private var _error_count = 0L
    private var _duration_count = 0L
    private var _duration_total_millis = 0L
    private var _duration_min_millis: Option[Long] = None
    private var _duration_max_millis: Option[Long] = None

    def count: Long = _count
    def errorCount: Long = _error_count
    def durationCount: Long = _duration_count
    def durationTotalMillis: Long = _duration_total_millis
    def durationMinMillis: Option[Long] = _duration_min_millis
    def durationMaxMillis: Option[Long] = _duration_max_millis

    def record(error: Boolean, durationmillis: Option[Long]): Unit = {
      _count += 1
      if (error)
        _error_count += 1
      durationmillis.foreach { value =>
        val duration = Math.max(0L, value)
        _duration_count += 1
        _duration_total_millis += duration
        _duration_min_millis = Some(_duration_min_millis.fold(duration)(Math.min(_, duration)))
        _duration_max_millis = Some(_duration_max_millis.fold(duration)(Math.max(_, duration)))
      }
    }
  }

  val shared: ComponentMetricsRegistry = new ComponentMetricsRegistry()

  def create(): ComponentMetricsRegistry = new ComponentMetricsRegistry()
}
