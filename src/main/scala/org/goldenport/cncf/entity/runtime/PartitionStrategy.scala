package org.goldenport.cncf.entity.runtime

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.goldenport.model.datatype.EntityId

/*
 * @since   Mar. 14, 2026
 * @version Mar. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait PartitionStrategy {
  def partitionKey(id: EntityId): String
  def partitionsForRange(
    major: String,
    minor: String,
    from: Instant,
    to: Instant
  ): Vector[String]
}

object PartitionStrategy {
  /**
   * Default strategy.
   *
   * Uses the whole EntityId string which produces
   * stable but evenly distributed partitions.
   */
  val byEntityId: PartitionStrategy =
    new PartitionStrategy {
      def partitionKey(id: EntityId): String =
        id.toString

      def partitionsForRange(
        major: String,
        minor: String,
        from: Instant,
        to: Instant
      ): Vector[String] =
        Vector.empty
    }

  val byOrganizationMonthUTC: PartitionStrategy =
    new PartitionStrategy {
      def partitionKey(id: EntityId): String = {
        val parts = id.parts
        val yyyymm = utcYearMonth(parts.timestamp)
        val org = organizationPrefix(parts.major, parts.minor)
        s"$org-$yyyymm"
      }

      def partitionsForRange(
        major: String,
        minor: String,
        from: Instant,
        to: Instant
      ): Vector[String] = {
        val org = organizationPrefix(major, minor)
        monthsBetween(from, to).map(ym => s"$org-$ym")
      }
    }

  private val _yyyymm = DateTimeFormatter.ofPattern("yyyyMM")

  def utcYearMonth(timestamp: Instant): String =
    _yyyymm.format(
      timestamp.atZone(ZoneOffset.UTC)
    )

  def utcYearMonth(epochSeconds: Long): String =
    utcYearMonth(Instant.ofEpochSecond(epochSeconds))

  def monthsBetween(from: Instant, to: Instant): Vector[String] = {
    val fromMonth = from.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
    val toMonth = to.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
    val (start, end) =
      if (fromMonth.isAfter(toMonth)) (toMonth, fromMonth) else (fromMonth, toMonth)
    val b = Vector.newBuilder[String]
    var current = start
    while (!current.isAfter(end)) {
      b += _yyyymm.format(current)
      current = current.plusMonths(1)
    }
    b.result()
  }

  def organizationPrefix(major: String, minor: String): String =
    if (minor.isEmpty) major else s"$major-$minor"
}
