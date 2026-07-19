package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.protocol.{Argument, Property, Request}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object OperationUpdateRequestNormalizer {
  import OperationTypedUpdateDirective.TypedAssignment
  import OperationUpdateDirectiveNormalizer.FieldOccurrence

  def normalize(
    operation: CmlOperationDefinition,
    request: Request
  ): Consequence[Request] = {
    val parameters = request.arguments ++ request.properties
    val carriers = parameters.filter(x => OperationUpdateDirectiveNormalizer.isUpdateCarrier(x.name))
    if (carriers.isEmpty)
      Consequence.success(request)
    else {
      val declarednames = operation.parameters.map(_.name).toSet
      val occurrences = parameters.collect {
        case parameter if
            OperationUpdateDirectiveNormalizer.isUpdateCarrier(parameter.name) ||
              declarednames.contains(parameter.name) =>
          FieldOccurrence(parameter.name, parameter.value)
      }.toVector
      for {
        directives <- OperationUpdateDirectiveNormalizer.normalize(occurrences)
        typed <- OperationTypedUpdateMapper.map(operation, directives)
      } yield _materialize(request, typed)
    }
  }

  private def _materialize(
    request: Request,
    directives: OperationTypedUpdateDirectiveSet
  ): Request = {
    directives.directives.values.toVector.foldLeft(request) {
      case (z, assignment: TypedAssignment) => _materialize_assignment(z, assignment)
      case (z, OperationTypedUpdateDirective.Existing(
            assignment: OperationUpdateDirective.ExplicitValueAssignment
          )) => _materialize_explicit_value_assignment(z, assignment)
      case (z, _) => z
    }
  }

  private def _materialize_explicit_value_assignment(
    request: Request,
    assignment: OperationUpdateDirective.ExplicitValueAssignment
  ): Request = {
    val parametername = assignment.parameterName
    request.copy(
      arguments = request.arguments.map { argument =>
        if (_is_carrier_for(argument.name, parametername))
          Argument(parametername, argument.value, argument.spec)
        else
          argument
      },
      properties = request.properties.map { property =>
        if (_is_carrier_for(property.name, parametername))
          Property(parametername, property.value, property.spec)
        else
          property
      }
    )
  }

  private def _is_carrier_for(name: String, parametername: String): Boolean =
    OperationUpdateDirectiveNormalizer.isUpdateCarrier(name) &&
      OperationUpdateDirectiveNormalizer.parameterName(name) == parametername

  private def _materialize_assignment(
    request: Request,
    assignment: TypedAssignment
  ): Request = {
    val parametername = assignment.parameterName
    val value = _materialized_value(assignment.update)
    val argumentcarrier = request.arguments.find(x =>
      OperationUpdateDirectiveNormalizer.isUpdateCarrier(x.name) &&
        OperationUpdateDirectiveNormalizer.parameterName(x.name) == parametername
    )
    val propertycarrier = request.properties.find(x =>
      OperationUpdateDirectiveNormalizer.isUpdateCarrier(x.name) &&
        OperationUpdateDirectiveNormalizer.parameterName(x.name) == parametername
    )
    val arguments = request.arguments.filterNot(x =>
      OperationUpdateDirectiveNormalizer.isUpdateCarrier(x.name) &&
        OperationUpdateDirectiveNormalizer.parameterName(x.name) == parametername
    )
    val properties = request.properties.filterNot(x =>
      OperationUpdateDirectiveNormalizer.isUpdateCarrier(x.name) &&
        OperationUpdateDirectiveNormalizer.parameterName(x.name) == parametername
    )
    argumentcarrier match {
      case Some(carrier) =>
        request.copy(
          arguments = arguments :+ Argument(parametername, value, carrier.spec),
          properties = properties
        )
      case None =>
        val spec = propertycarrier.flatMap(_.spec)
        request.copy(
          arguments = arguments,
          properties = properties :+ Property(parametername, value, spec)
        )
    }
  }

  private def _materialized_value(update: Update[Any]): Any =
    update.fold(
      Update.Noop,
      identity,
      Update.SetNull
    )
}
