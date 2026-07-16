package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DecisionTableId(value: String) {
  def print: String = value
}

final case class DecisionTableRowId(value: String) {
  def print: String = value
}

final case class DecisionColumn(name: String) {
  def print: String = name
}

sealed abstract class DecisionCondition {
  def specificity: Int
}

object DecisionCondition {
  case object Any extends DecisionCondition {
    val specificity: Int = 0
  }

  final case class Equals(expected: RuleFactValue) extends DecisionCondition {
    val specificity: Int = 1
  }

  final case class NumberRange(
    minimumInclusive: Option[BigDecimal] = None,
    maximumExclusive: Option[BigDecimal] = None
  ) extends DecisionCondition {
    val specificity: Int = 1
  }
}

final case class DecisionTableRow(
  id: DecisionTableRowId,
  priority: RulePriority = RulePriority.Default,
  conditions: Map[String, DecisionCondition] = Map.empty,
  output: Record = Record.empty,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def specificity: Int = conditions.values.map(_.specificity).sum
}

final case class DecisionTable private (
  id: DecisionTableId,
  inputColumns: Vector[DecisionColumn],
  rows: Vector[DecisionTableRow],
  metadata: Record
)

object DecisionTable {
  def createC(
    id: DecisionTableId,
    inputcolumns: Vector[DecisionColumn],
    rows: Vector[DecisionTableRow],
    metadata: Record = Record.empty
  ): Consequence[DecisionTable] = {
    val columnnames = inputcolumns.map(_.name)
    val rowids = rows.map(_.id.value)
    val unknowncolumns = rows.flatMap(_.conditions.keys).filterNot(columnnames.contains)
    if (id.value.trim.isEmpty)
      Consequence.argumentInvalid("DecisionTable must have a non-empty id")
    else if (inputcolumns.isEmpty)
      Consequence.argumentInvalid("DecisionTable must declare at least one input column")
    else if (columnnames.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("DecisionTable input column names must be non-empty")
    else if (_has_duplicates(columnnames))
      Consequence.argumentInvalid("DecisionTable input column names must be unique")
    else if (rows.isEmpty)
      Consequence.argumentInvalid("DecisionTable must contain at least one row")
    else if (rowids.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("DecisionTable rows must have non-empty ids")
    else if (_has_duplicates(rowids))
      Consequence.argumentInvalid("DecisionTable row ids must be unique")
    else if (unknowncolumns.nonEmpty)
      Consequence.argumentInvalid("DecisionTable row conditions must reference declared input columns")
    else if (rows.exists(row => row.conditions.values.exists(condition => !_is_valid_condition(condition))))
      Consequence.argumentInvalid("DecisionTable numeric ranges must have at least one bound and a non-empty interval")
    else
      Consequence.success(
        new DecisionTable(
          id,
          inputcolumns.sortBy(_.name),
          rows.sortBy(_.id.value),
          metadata
        )
      )
  }

  private def _has_duplicates(values: Vector[String]): Boolean =
    values.distinct.size != values.size

  private def _is_valid_condition(condition: DecisionCondition): Boolean =
    condition match {
      case DecisionCondition.NumberRange(minimum, maximum) =>
        (minimum.nonEmpty || maximum.nonEmpty) && (
          (minimum, maximum) match {
            case (Some(minimum), Some(maximum)) => minimum < maximum
            case _ => true
          }
        )
      case _ => true
    }
}

final case class DecisionTableInput(
  name: String,
  value: RuleFactValue
)

final case class DecisionTableInputs private (values: Vector[DecisionTableInput]) {
  def get(name: String): Option[RuleFactValue] =
    values.find(_.name == name).map(_.value)
}

object DecisionTableInputs {
  val empty: DecisionTableInputs = new DecisionTableInputs(Vector.empty)

  def apply(): DecisionTableInputs = empty

  def fromInputsC(inputs: Iterable[DecisionTableInput]): Consequence[DecisionTableInputs] = {
    val values = inputs.toVector
    val names = values.map(_.name)
    if (names.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("DecisionTable input names must be non-empty")
    else if (names.distinct.size != names.size)
      Consequence.argumentInvalid("DecisionTable input names must be unique")
    else
      Consequence.success(new DecisionTableInputs(values.sortBy(_.name)))
  }
}

final case class DecisionTableMatch(
  row: DecisionTableRow,
  matchedColumns: Vector[String]
) {
  def specificity: Int = row.specificity
}

final case class DecisionTableEvaluation(
  tableId: DecisionTableId,
  candidates: Vector[DecisionTableMatch],
  selected: Option[DecisionTableMatch]
)

object DecisionTableEvaluator {
  def evaluateC(
    table: DecisionTable,
    inputs: DecisionTableInputs
  ): Consequence[DecisionTableEvaluation] = {
    val result = table.rows.foldLeft(Consequence.success(Vector.empty[DecisionTableMatch])) { (z, row) =>
      z.flatMap { values =>
        _match_row_c(table, row, inputs).map { matched =>
          matched.map(value => values :+ value).getOrElse(values)
        }
      }
    }
    result.map { values =>
      val candidates = _order_matches(values)
      DecisionTableEvaluation(table.id, candidates, candidates.headOption)
    }
  }

  private def _match_row_c(
    table: DecisionTable,
    row: DecisionTableRow,
    inputs: DecisionTableInputs
  ): Consequence[Option[DecisionTableMatch]] = {
    val result = table.inputColumns.foldLeft(Consequence.success(Option(Vector.empty[String]))) { (z, column) =>
      z.flatMap {
        case None => Consequence.success(None)
        case Some(columns) =>
          row.conditions.get(column.name) match {
            case None | Some(DecisionCondition.Any) => Consequence.success(Some(columns))
            case Some(condition) =>
              inputs.get(column.name) match {
                case None => Consequence.success(None)
                case Some(value) => _matches_c(condition, value).map { matches =>
                  if (matches)
                    Some(columns :+ column.name)
                  else
                    None
                }
              }
          }
      }
    }
    result.map(_.map(columns => DecisionTableMatch(row, columns)))
  }

  private def _matches_c(
    condition: DecisionCondition,
    value: RuleFactValue
  ): Consequence[Boolean] =
    condition match {
      case DecisionCondition.Any => Consequence.success(true)
      case DecisionCondition.Equals(expected) => Consequence.success(expected == value)
      case DecisionCondition.NumberRange(minimum, maximum) =>
        _number_c(value).map { actual =>
          minimum.forall(_ <= actual) && maximum.forall(actual < _)
        }
    }

  private def _number_c(value: RuleFactValue): Consequence[BigDecimal] =
    value match {
      case RuleFactValue.Scalar(value) =>
        value match {
          case value: BigDecimal => Consequence.success(value)
          case value: java.math.BigDecimal => Consequence.success(BigDecimal(value))
          case value: BigInt => Consequence.success(BigDecimal(value))
          case value: java.math.BigInteger => Consequence.success(BigDecimal(new java.math.BigDecimal(value)))
          case value: Byte => Consequence.success(BigDecimal(value))
          case value: Short => Consequence.success(BigDecimal(value))
          case value: Int => Consequence.success(BigDecimal(value))
          case value: Long => Consequence.success(BigDecimal(value))
          case _ => Consequence.argumentInvalid("DecisionTable numeric conditions require BigDecimal or an integral value")
        }
      case RuleFactValue.Structured(_) =>
        Consequence.argumentInvalid("DecisionTable numeric conditions require a scalar value")
    }

  private def _order_matches(values: Vector[DecisionTableMatch]): Vector[DecisionTableMatch] =
    values.sortWith { (left, right) =>
      left.row.priority.value > right.row.priority.value ||
        (left.row.priority.value == right.row.priority.value &&
          (left.specificity > right.specificity ||
            (left.specificity == right.specificity && left.row.id.value < right.row.id.value)))
    }
}
