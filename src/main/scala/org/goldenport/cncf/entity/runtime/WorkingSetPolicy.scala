package org.goldenport.cncf.entity.runtime

import java.time.{Duration, Instant}
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Apr. 24, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait WorkingSetPolicy {
  def label: String

  def isResident(
    record: Record,
    now: Instant = Instant.now()
  ): Boolean
}

object WorkingSetPolicy {
  case object Disabled extends WorkingSetPolicy {
    def label: String = "disabled"
    def isResident(
      record: Record,
      now: Instant
    ): Boolean = false
  }

  case object ResidentAll extends WorkingSetPolicy {
    def label: String = "resident-all"
    def isResident(
      record: Record,
      now: Instant
    ): Boolean = true
  }

  final case class Recent(
    duration: Duration,
    timestampField: String
  ) extends WorkingSetPolicy {
    def label: String = s"recent(${_format_duration(duration)} on $timestampField)"

    def isResident(
      record: Record,
      now: Instant
    ): Boolean =
      _instant_value(record, timestampField).exists { ts =>
        !ts.isBefore(now.minus(duration))
      }
  }

  final case class Custom(
    policyName: String,
    evaluator: WorkingSetPolicyEvaluator
  ) extends WorkingSetPolicy {
    def label: String = policyName

    def isResident(
      record: Record,
      now: Instant
    ): Boolean =
      evaluator.isResident(record, now)
  }

  def parse(
    kind: String,
    duration: Option[String],
    timestampfield: Option[String]
  ): Consequence[WorkingSetPolicy] =
    kind.trim.toLowerCase match {
      case "" | "disabled" | "none" =>
        Consequence.success(Disabled)
      case "resident-all" | "resident_all" | "residentall" | "all" =>
        Consequence.success(ResidentAll)
      case "recent" =>
        for {
          window <- duration
            .map(_parse_duration)
            .getOrElse(Consequence.argumentMissing("workingSetPolicy.duration"))
          tsfield <- timestampfield
            .filter(_.trim.nonEmpty)
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("workingSetPolicy.timestampField"))
        } yield Recent(window, tsfield)
      case other =>
        Consequence.argumentInvalid(s"unknown working-set policy: $other")
    }

  private def _instant_value(
    record: Record,
    field: String
  ): Option[Instant] =
    _instant_any(record.getAny(field)).orElse {
      val alt = _alternate_field_names(field)
      alt.iterator.flatMap(name =>
        _instant_any(record.getAny(name))
      ).toSeq.headOption
    }

  private def _instant_any(
    value: Option[Any]
  ): Option[Instant] =
    value.flatMap {
      case m: Instant => Some(m)
      case m: java.sql.Timestamp => Some(m.toInstant)
      case m: java.util.Date => Some(m.toInstant)
      case m: String => scala.util.Try(Instant.parse(m.trim)).toOption
      case _ => None
    }

  private def _alternate_field_names(
    field: String
  ): Vector[String] = {
    val flat = field.replace("-", "").replace("_", "").trim
    val snake = flat.foldLeft(new StringBuilder) { (z, c) =>
      if (c.isUpper) {
        if (z.nonEmpty) z.append('_')
        z.append(c.toLower)
      } else {
        z.append(c)
      }
    }.toString
    val kebab = snake.replace('_', '-')
    Vector(field, flat, snake, kebab).distinct.filterNot(_ == field)
  }

  private def _parse_duration(
    text: String
  ): Consequence[Duration] = {
    val normalized = text.trim.toLowerCase
    if (normalized.isEmpty)
      Consequence.argumentMissing("workingSetPolicy.duration")
    else if (normalized.startsWith("pt"))
      scala.util.Try(Duration.parse(normalized.toUpperCase)).toOption
        .map(Consequence.success)
        .getOrElse(Consequence.argumentInvalid(s"invalid working-set duration: $text"))
    else {
      val pattern = """(\d+)([smhd])""".r
      normalized match {
        case pattern(num, unit) =>
          val n = num.toLong
          val duration = unit match {
            case "s" => Duration.ofSeconds(n)
            case "m" => Duration.ofMinutes(n)
            case "h" => Duration.ofHours(n)
            case "d" => Duration.ofDays(n)
          }
          Consequence.success(duration)
        case _ =>
          Consequence.argumentInvalid(s"invalid working-set duration: $text")
      }
    }
  }

  private def _format_duration(
    duration: Duration
  ): String =
    if (duration.toDays > 0 && duration.toDays * 24 == duration.toHours)
      s"${duration.toDays}d"
    else if (duration.toHours > 0 && duration.toHours * 60 == duration.toMinutes)
      s"${duration.toHours}h"
    else if (duration.toMinutes > 0 && duration.toMinutes * 60 == duration.getSeconds)
      s"${duration.toMinutes}m"
    else
      s"${duration.getSeconds}s"
}

trait WorkingSetPolicyEvaluator {
  def isResident(
    record: Record,
    now: Instant = Instant.now()
  ): Boolean
}

enum WorkingSetPolicySource {
  case Cml, Config, Code
}
