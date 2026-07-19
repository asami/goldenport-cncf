package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] sealed trait OperationUpdateDirective {
  def parameterName: String
}

private[cncf] object OperationUpdateDirective {
  final case class NoDirective(parameterName: String) extends OperationUpdateDirective

  final case class PlainAssignment(
    parameterName: String,
    values: Vector[Any]
  ) extends OperationUpdateDirective

  final case class ExplicitValueAssignment(
    parameterName: String,
    carrier: ExplicitValueAssignment.Kind,
    values: Vector[Any]
  ) extends OperationUpdateDirective

  object ExplicitValueAssignment {
    enum Kind(val suffix: String, val emptycommand: Option[OperandlessCommand.Kind]) {
      case Value extends Kind("__value", None)
      case ValueOrClear extends Kind("__value_or_clear", Some(OperandlessCommand.Kind.Clear))
      case ValueOrNull extends Kind("__value_or_null", Some(OperandlessCommand.Kind.Null))
    }

    val kinds: Vector[Kind] = Kind.values.toVector
  }

  final case class ValueOperation(
    parameterName: String,
    operation: ValueOperation.Kind,
    values: Vector[Any]
  ) extends OperationUpdateDirective

  object ValueOperation {
    enum Kind(val suffix: String) {
      case Overwrite extends Kind("__overwrite")
      case Prepend extends Kind("__prepend")
      case Append extends Kind("__append")
      case Remove extends Kind("__remove")
    }

    val kinds: Vector[Kind] = Kind.values.toVector
  }

  final case class OperandlessCommand(
    parameterName: String,
    command: OperandlessCommand.Kind
  ) extends OperationUpdateDirective

  object OperandlessCommand {
    enum Kind(val token: String) {
      case Clear extends Kind("clear")
      case Null extends Kind("null")
    }

    def parse(p: Any): Option[Kind] = {
      val token = Option(p).map(_.toString.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
      Kind.values.find(_.token == token)
    }
  }
}

private[cncf] final case class OperationUpdateDirectiveSet(
  directives: Map[String, OperationUpdateDirective]
) {
  def directive(parametername: String): OperationUpdateDirective =
    directives.getOrElse(parametername, OperationUpdateDirective.NoDirective(parametername))
}

private[cncf] object OperationUpdateDirectiveNormalizer {
  import OperationUpdateDirective.*

  private val COMMAND_SUFFIX = "__update_command"
  private val POLICY = "operation-update-directive"

  final case class FieldOccurrence(
    name: String,
    value: Any
  )

  def normalize(record: Record): Consequence[OperationUpdateDirectiveSet] =
    normalize(record.fields.map(field => FieldOccurrence(field.key, field.value.single)))

  def isUpdateCarrier(name: String): Boolean =
    _split(FieldOccurrence(name, ()))._2 != PlainOccurrence

  def parameterName(name: String): String =
    _split(FieldOccurrence(name, ()))._1

  def normalize(
    occurrences: Vector[FieldOccurrence]
  ): Consequence[OperationUpdateDirectiveSet] = {
    val grouped = occurrences
      .map { occurrence =>
        val (parametername, kind) = _split(occurrence)
        parametername -> (kind -> occurrence)
      }
      .groupMap(_._1)(_._2)
      .toVector
      .sortBy(_._1)
    Consequence.zipN(grouped.map { case (parametername, entries) =>
      _normalize_parameter(parametername, entries)
    }).map { directives =>
      OperationUpdateDirectiveSet(directives.map(x => x.parameterName -> x).toMap)
    }
  }

  private sealed trait OccurrenceKind
  private case object PlainOccurrence extends OccurrenceKind
  private final case class ValueOccurrence(kind: ValueOperation.Kind) extends OccurrenceKind
  private final case class ExplicitValueOccurrence(kind: ExplicitValueAssignment.Kind) extends OccurrenceKind
  private case object CommandOccurrence extends OccurrenceKind

  private def _split(
    occurrence: FieldOccurrence
  ): (String, OccurrenceKind) =
    if (occurrence.name.endsWith(COMMAND_SUFFIX)) {
      val basename = occurrence.name.dropRight(COMMAND_SUFFIX.length)
      basename -> CommandOccurrence
    } else {
      ExplicitValueAssignment.kinds.find(kind => occurrence.name.endsWith(kind.suffix)) match {
        case Some(kind) =>
          val basename = occurrence.name.dropRight(kind.suffix.length)
          basename -> ExplicitValueOccurrence(kind)
        case None =>
          ValueOperation.kinds.find(kind => occurrence.name.endsWith(kind.suffix)) match {
            case Some(kind) =>
              val basename = occurrence.name.dropRight(kind.suffix.length)
              basename -> ValueOccurrence(kind)
            case None =>
              occurrence.name -> PlainOccurrence
          }
      }
    }

  private def _normalize_parameter(
    parametername: String,
    entries: Vector[(OccurrenceKind, FieldOccurrence)]
  ): Consequence[OperationUpdateDirective] = {
    val plain = entries.collect { case (PlainOccurrence, occurrence) => occurrence.value }
    val commands = entries.collect { case (CommandOccurrence, occurrence) => occurrence.value }
    val valueoperations = entries.collect { case (ValueOccurrence(kind), occurrence) => kind -> occurrence.value }
    val explicitvalues = entries.collect { case (ExplicitValueOccurrence(kind), occurrence) => kind -> occurrence.value }
    val valuekinds = valueoperations.map(_._1).distinct
    val explicitvaluekinds = explicitvalues.map(_._1).distinct

    if (parametername.isEmpty)
      _failure(parametername, "non-empty parameter name", "empty parameter name")
    else if (commands.size > 1)
      _failure(parametername, "one update command", s"${commands.size} update commands")
    else if (commands.nonEmpty && (plain.nonEmpty || valueoperations.nonEmpty || explicitvalues.nonEmpty))
      _failure(parametername, "one update directive", _directive_summary(plain, commands, valuekinds, explicitvaluekinds))
    else if (plain.nonEmpty && (valueoperations.nonEmpty || explicitvalues.nonEmpty))
      _failure(parametername, "one assignment carrier", _directive_summary(plain, commands, valuekinds, explicitvaluekinds))
    else if (valueoperations.nonEmpty && explicitvalues.nonEmpty)
      _failure(parametername, "one value carrier", _directive_summary(plain, commands, valuekinds, explicitvaluekinds))
    else if (valuekinds.size > 1)
      _failure(parametername, "one value operation", valuekinds.map(_.toString).sorted.mkString(","))
    else if (explicitvaluekinds.size > 1)
      _failure(parametername, "one explicit value carrier", explicitvaluekinds.map(_.toString).sorted.mkString(","))
    else if (commands.nonEmpty)
      OperandlessCommand.parse(commands.head) match {
        case Some(command) => Consequence.success(OperandlessCommand(parametername, command))
        case None => _failure(parametername, "clear or null", Option(commands.head).map(_.toString).getOrElse("null"))
      }
    else if (explicitvalues.nonEmpty)
      _normalize_explicit_values(parametername, explicitvalues.head._1, explicitvalues.map(_._2))
    else if (valueoperations.nonEmpty) {
      val kind = valueoperations.head._1
      Consequence.success(ValueOperation(parametername, kind, valueoperations.map(_._2)))
    } else {
      Consequence.success(PlainAssignment(parametername, plain))
    }
  }

  private def _directive_summary(
    plain: Vector[Any],
    commands: Vector[Any],
    valuekinds: Vector[ValueOperation.Kind],
    explicitvaluekinds: Vector[ExplicitValueAssignment.Kind]
  ): String =
    Vector(
      Option.when(plain.nonEmpty)("plain"),
      Option.when(commands.nonEmpty)("command"),
      Option.when(valuekinds.nonEmpty)(valuekinds.map(_.toString).sorted.mkString(",")),
      Option.when(explicitvaluekinds.nonEmpty)(explicitvaluekinds.map(_.toString).sorted.mkString(","))
    ).flatten.mkString("+")

  private def _normalize_explicit_values(
    parametername: String,
    kind: ExplicitValueAssignment.Kind,
    values: Vector[Any]
  ): Consequence[OperationUpdateDirective] =
    kind.emptycommand match {
      case None =>
        Consequence.success(ExplicitValueAssignment(parametername, kind, values))
      case Some(command) =>
        val (emptyvalues, nonemptyvalues) = values.partition(_is_empty_value)
        if (emptyvalues.nonEmpty && nonemptyvalues.nonEmpty)
          _failure(parametername, "empty command or non-empty values", "empty and non-empty values")
        else if (emptyvalues.size > 1)
          _failure(parametername, "one empty command carrier", s"${emptyvalues.size} empty command carriers")
        else if (emptyvalues.nonEmpty)
          Consequence.success(OperandlessCommand(parametername, command))
        else
          Consequence.success(ExplicitValueAssignment(parametername, kind, nonemptyvalues))
    }

  private def _is_empty_value(p: Any): Boolean =
    p match {
      case value: String => value.isEmpty
      case _ => false
    }

  private def _failure[A](
    parametername: String,
    expected: String,
    actual: String
  ): Consequence[A] =
    Consequence.argumentPolicyViolation(parametername, POLICY, expected, actual)
}
