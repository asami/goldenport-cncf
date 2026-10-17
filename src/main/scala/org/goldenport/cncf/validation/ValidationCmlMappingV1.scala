package org.goldenport.cncf.validation

import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.concurrent.duration.FiniteDuration
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionRequirement
import ValidationMetadataV1.{Declaration, DurationClass, FeatureReference, Metadata, Purpose}

/** Normalized CML properties, independent of CML syntax and generation.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationCmlMappingV1 {
  enum DurationUnit {
    case NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
  }

  final case class DurationValue(amount: Long, unit: DurationUnit)

  final case class Properties(
    abiVersion: Int = 1,
    purposes: Option[Set[String]] = None,
    features: Set[FeatureReference] = Set.empty,
    expectedDuration: Option[DurationValue] = None,
    durationClass: Option[String] = None,
    executionRequirement: Option[ExecutionRequirement] = None,
    requiredSemantics: Set[String] = Set.empty
  ) {
    def declarationC: Consequence[Declaration] = ValidationCmlMappingV1.declarationC(this)
  }

  def declarationC(properties: Properties): Consequence[Declaration] =
    if (properties == null) _invalid_c("properties", "nonnull normalized CML properties")
    else for {
      purposes <- _optional_c(properties.purposes, "purposes")(_purposes_c)
      duration <- _optional_c(properties.expectedDuration, "expectedDuration")(_duration_c)
      durationclass <- _optional_c(properties.durationClass, "durationClass") { value =>
        DurationClass.values.find(_.toString == value).map(Consequence.success).getOrElse(
          _invalid_c("durationClass", "exact NORMAL or LONG_RUNNING label"))
      }
      declaration <- Declaration(properties.abiVersion, purposes, properties.features, duration,
        durationclass, properties.executionRequirement, properties.requiredSemantics).validateC
    } yield declaration

  def resolveC(
    specification: Option[Properties],
    operation: Option[Properties]
  ): Consequence[Metadata] =
    (_optional_c(specification, "specification")(declarationC),
      _optional_c(operation, "operation")(declarationC)).tupled.flatMap { case (defaults, overrides) =>
      ValidationMetadataV1.resolveC(defaults, overrides)
    }

  private def _optional_c[A, B](value: Option[A], field: String)(map: A => Consequence[B]): Consequence[Option[B]] =
    if (value == null || value.exists(_ == null)) _invalid_c(field, "nonnull option and member")
    else value match {
      case None => Consequence.success(None)
      case Some(member) => map(member).map(Some(_))
    }

  private def _purposes_c(values: Set[String]): Consequence[Set[Purpose]] =
    values.foldLeft(Consequence.success(Set.empty[Purpose])) { (acc, value) =>
      acc.flatMap { admitted =>
        Purpose.values.find(x => value != null && x.toString == value).map(x => Consequence.success(admitted + x))
          .getOrElse(_invalid_c("purposes", "exact supported purpose labels"))
      }
    }

  private def _duration_c(value: DurationValue): Consequence[FiniteDuration] =
    if (value.unit == null) _invalid_c("expectedDuration", "nonnull duration unit")
    else {
      val multiplier = value.unit match {
        case DurationUnit.NANOSECONDS => BigInt(1)
        case DurationUnit.MICROSECONDS => BigInt(1000)
        case DurationUnit.MILLISECONDS => BigInt(1000000)
        case DurationUnit.SECONDS => BigInt(1000000000)
        case DurationUnit.MINUTES => BigInt(60000000000L)
        case DurationUnit.HOURS => BigInt(3600000000000L)
        case DurationUnit.DAYS => BigInt(86400000000000L)
      }
      val nanos = BigInt(value.amount) * multiplier
      if (nanos > 0 && nanos <= BigInt(Long.MaxValue))
        Consequence.success(FiniteDuration(nanos.toLong, NANOSECONDS))
      else _invalid_c("expectedDuration", "positive duration representable as Long nanoseconds")
    }

  private def _invalid_c[A](field: String, expected: String): Consequence[A] =
    Consequence.argumentPolicyViolation(field, "validation.cml." + field, expected, "invalid")
}
