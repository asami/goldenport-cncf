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
    val assignments = directives.directives.values.collect {
      case assignment: TypedAssignment => assignment
    }.toVector
    assignments.foldLeft(request)(_materialize_assignment)
  }

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
