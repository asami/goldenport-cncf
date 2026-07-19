package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField, CmlOperationUpdateField}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class OperationUpdateParameterMetadata(
  name: String,
  elementDatatype: String,
  sourceMultiplicity: String,
  collectionValued: Boolean,
  nullAllowed: Boolean
)

private[cncf] object OperationUpdateParameterMetadata {
  private val ENTITY_UPDATE = "ENTITY_UPDATE"
  private val POLICY = "operation-update-metadata"

  def resolve(
    operation: CmlOperationDefinition,
    parametername: String
  ): Consequence[OperationUpdateParameterMetadata] =
    if (!_is_entity_update(operation))
      _failure(parametername, "entity update operation", operation.inputValueKind)
    else
      operation.parameters.find(_.name == parametername) match {
        case None =>
          _failure(parametername, "declared operation parameter", "unknown parameter")
        case Some(field) =>
          field.update match {
            case Some(update) => Consequence.success(_metadata(field, update))
            case None =>
              _failure(
                parametername,
                "generated update metadata",
                "missing source multiplicity and null-assignment metadata"
              )
          }
      }

  private def _is_entity_update(p: CmlOperationDefinition): Boolean =
    Option(p.inputValueKind).exists(_.trim.equalsIgnoreCase(ENTITY_UPDATE))

  private def _metadata(
    field: CmlOperationField,
    update: CmlOperationUpdateField
  ): OperationUpdateParameterMetadata =
    OperationUpdateParameterMetadata(
      name = field.name,
      elementDatatype = field.datatype,
      sourceMultiplicity = update.sourceMultiplicity,
      collectionValued = _is_collection(update.sourceMultiplicity),
      nullAllowed = update.nullAllowed
    )

  private def _is_collection(p: String): Boolean =
    Option(p).map(_.trim.toLowerCase(java.util.Locale.ROOT)).exists {
      case "*" | "+" | "0..*" | "1..*" | "zeromore" | "zero-more" |
          "zero_more" | "onemore" | "one-more" | "one_more" => true
      case _ => false
    }

  private def _failure[A](
    parametername: String,
    expected: String,
    actual: String
  ): Consequence[A] =
    Consequence.argumentPolicyViolation(parametername, POLICY, expected, actual)
}

private[cncf] sealed trait OperationTypedUpdateDirective {
  def parameterName: String
}

private[cncf] object OperationTypedUpdateDirective {
  final case class Existing(directive: OperationUpdateDirective)
      extends OperationTypedUpdateDirective {
    def parameterName: String = directive.parameterName
  }

  final case class TypedAssignment(
    parameterName: String,
    elementDatatype: String,
    update: Update[Any]
  ) extends OperationTypedUpdateDirective
}

private[cncf] final case class OperationTypedUpdateDirectiveSet(
  directives: Map[String, OperationTypedUpdateDirective]
)

private[cncf] object OperationTypedUpdateMapper {
  import OperationTypedUpdateDirective.*
  import OperationUpdateDirective.OperandlessCommand

  private val POLICY = "operation-update-command-compatibility"

  def map(
    operation: CmlOperationDefinition,
    directives: OperationUpdateDirectiveSet
  ): Consequence[OperationTypedUpdateDirectiveSet] =
    Consequence.zipN(directives.directives.toVector.sortBy(_._1).map { case (_, directive) =>
      _map(operation, directive)
    }).map { xs =>
      OperationTypedUpdateDirectiveSet(xs.map(x => x.parameterName -> x).toMap)
    }

  private def _map(
    operation: CmlOperationDefinition,
    directive: OperationUpdateDirective
  ): Consequence[OperationTypedUpdateDirective] =
    directive match {
      case command: OperandlessCommand =>
        OperationUpdateParameterMetadata.resolve(operation, command.parameterName).flatMap { metadata =>
          command.command match {
            case OperandlessCommand.Kind.Clear if metadata.collectionValued =>
              Consequence.success(
                TypedAssignment(
                  metadata.name,
                  metadata.elementDatatype,
                  Update.set(Vector.empty[Any])
                )
              )
            case OperandlessCommand.Kind.Null if metadata.nullAllowed && !metadata.collectionValued =>
              Consequence.success(
                TypedAssignment(
                  metadata.name,
                  metadata.elementDatatype,
                  Update.setNull[Any]
                )
              )
            case OperandlessCommand.Kind.Clear =>
              _failure(metadata.name, "collection-valued update parameter", metadata.sourceMultiplicity)
            case OperandlessCommand.Kind.Null =>
              _failure(metadata.name, "null-assignable update parameter", metadata.sourceMultiplicity)
          }
        }
      case other =>
        _validate_existing_parameter(operation, other.parameterName).map(_ => Existing(other))
    }

  private def _validate_existing_parameter(
    operation: CmlOperationDefinition,
    parametername: String
  ): Consequence[Unit] =
    if (operation.parameters.exists(_.name == parametername))
      Consequence.unit
    else
      _failure(parametername, "declared operation parameter", "unknown parameter")

  private def _failure[A](
    parametername: String,
    expected: String,
    actual: String
  ): Consequence[A] =
    Consequence.argumentPolicyViolation(parametername, POLICY, expected, actual)
}
