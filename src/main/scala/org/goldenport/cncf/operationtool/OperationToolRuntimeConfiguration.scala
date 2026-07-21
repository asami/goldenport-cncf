package org.goldenport.cncf.operationtool

import java.nio.file.Path
import scala.util.Try

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.io.RecordSourceLoader

/*
 * Runtime-owned activation policy for in-process Operation tool sets.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class OperationToolRuntimeConfiguration private (
  admissions: Vector[OperationToolAdmission]
)

private[cncf] object OperationToolRuntimeConfiguration {
  private val _default_maximum_calls = 8
  private val _default_maximum_input_bytes = 16384L
  private val _default_maximum_result_bytes = 16384L
  private val _default_maximum_concurrency = 1

  def loadC(path: Path): Consequence[OperationToolRuntimeConfiguration] =
    RecordSourceLoader.load(path).flatMap(decodeC)

  def decodeC(record: Record): Consequence[OperationToolRuntimeConfiguration] =
    _required_records_c(record, "toolSets").flatMap { records =>
      records.foldLeft(Consequence.success(Vector.empty[OperationToolAdmission])) {
        case (z, entry) =>
          for {
            admissions <- z
            admission <- _admission_c(entry)
          } yield admissions :+ admission
      }.flatMap { admissions =>
        val identities = admissions.map(_.toolSetId)
        if (identities.distinct.size == identities.size)
          Consequence.success(OperationToolRuntimeConfiguration(admissions.sortBy(_.toolSetId.print)))
        else
          Consequence.argumentPolicyViolation(
            "toolSets",
            "operation-tool.runtime-configuration",
            "unique tool-set identities",
            "duplicate"
          )
      }
    }

  private def _admission_c(record: Record): Consequence[OperationToolAdmission] =
    for {
      id <- _required_string_c(record, "id").flatMap(OperationToolSetId.parseC)
      names <- _required_strings_c(record, "operations")
      _ <- if (names.nonEmpty) Consequence.unit else Consequence.argumentMissing("operations")
      identities <- names.foldLeft(Consequence.success(Vector.empty[OperationToolIdentity])) {
        case (z, name) =>
          for {
            values <- z
            identity <- OperationToolIdentity.parseC(name)
          } yield values :+ identity
      }
      limits <- _limits_c(record.getRecord("limits"))
      admission <- OperationToolAdmission.createC(id, identities, limits)
    } yield admission

  private def _limits_c(record: Option[Record]): Consequence[OperationToolLimits] = {
    val value = record.getOrElse(Record.empty)
    for {
      calls <- _int_c(value, "maximumCalls", _default_maximum_calls)
      inputbytes <- _long_c(value, "maximumInputBytes", _default_maximum_input_bytes)
      resultbytes <- _long_c(value, "maximumResultBytes", _default_maximum_result_bytes)
      concurrency <- _int_c(value, "maximumConcurrency", _default_maximum_concurrency)
      limits <- OperationToolLimits.createC(calls, inputbytes, resultbytes, concurrency)
    } yield limits
  }

  private def _long_c(record: Record, name: String, default: Long): Consequence[Long] =
    record.getAny(name) match {
      case None => Consequence.success(default)
      case Some(value) => _integral_c(name, value).flatMap { number =>
        if (number.isValidLong) Consequence.success(number.longValue)
        else Consequence.argumentLimitExceeded(name, Long.MaxValue, number, "operation-tool.limits")
      }
    }

  private def _int_c(record: Record, name: String, default: Int): Consequence[Int] =
    record.getAny(name) match {
      case None => Consequence.success(default)
      case Some(value) => _integral_c(name, value).flatMap { number =>
        if (number.isValidInt) Consequence.success(number.intValue)
        else Consequence.argumentLimitExceeded(name, Int.MaxValue, number, "operation-tool.limits")
      }
    }

  private def _integral_c(name: String, value: Any): Consequence[BigInt] =
    Try(BigDecimal(value.toString)).toOption.flatMap(_.toBigIntExact) match {
      case Some(number) => Consequence.success(number)
      case None => Consequence.argumentFormatError(name, "integral number", value)
    }

  private def _required_records_c(record: Record, name: String): Consequence[Vector[Record]] =
    record.getVector(name) match {
      case Some(values) => values.foldLeft(Consequence.success(Vector.empty[Record])) {
        case (z, value) =>
          for {
            records <- z
            entry <- value match {
              case rec: Record => Consequence.success(rec)
              case _ => Consequence.argumentFormatError(name, "record list", "invalid")
            }
          } yield records :+ entry
      }
      case None => Consequence.argumentMissing(name)
    }

  private def _required_strings_c(record: Record, name: String): Consequence[Vector[String]] =
    record.getVector(name) match {
      case Some(values) =>
        val strings = values.map(_.toString.trim)
        if (strings.forall(_.nonEmpty)) Consequence.success(strings)
        else Consequence.argumentFormatError(name, "non-empty string list", "blank")
      case None => Consequence.argumentMissing(name)
    }

  private def _required_string_c(record: Record, name: String): Consequence[String] =
    record.getString(name).map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(name)
    }
}
