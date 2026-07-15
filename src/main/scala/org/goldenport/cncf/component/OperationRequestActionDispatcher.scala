package org.goldenport.cncf.component

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request, Switch}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, ParsedEventAction, ReceptionDomainEvent, ScopedActionCallDispatcher, SecureActionFactoryDispatcher}
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Mar. 21, 2026
 *  version Mar. 28, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationRequestActionDispatcher(
  logic: ComponentLogic
) extends SecureActionFactoryDispatcher with ScopedActionCallDispatcher {
  def dispatchBaseExecutionContext(): ExecutionContext =
    logic.executionContext()

  def parseValidateAction(
    actionName: String,
    event: DomainEvent
  ): Consequence[ParsedEventAction] =
    event match {
      case e: ReceptionDomainEvent =>
        _to_request(actionName, e).flatMap(logic.makeOperationRequest).flatMap {
          case action: Action =>
            Consequence.success(ParsedEventAction(actionName, action, event))
          case _ =>
            Consequence.argumentInvalid(s"OperationRequest must be Action: $actionName")
        }
      case _ =>
        Consequence.argumentInvalid(s"unsupported event for action dispatch: ${event.getClass.getSimpleName}")
    }

  def dispatchParsedAction(
    p: ParsedEventAction
  ): Consequence[Unit] = {
    val ec = logic.executionContext()
    logic.executeEventContinuationAction(p.action, ec).map(_ => ())
  }

  def dispatchParsedActionAuthorized(
    p: ParsedEventAction
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    logic.executeEventContinuationAction(p.action, ctx).map(_ => ())
  }

  private def _to_request(
    actionName: String,
    event: ReceptionDomainEvent
  ): Consequence[Request] =
    _parse_action_name(actionName).flatMap { case (component, service, operation) =>
      _resolve_selector(component, service, operation).map { resolved =>
        val parameters = _build_parameters(event, resolved.parameterKinds)
        Request.of(
          component = resolved.component,
          service = resolved.service,
          operation = resolved.operation,
          arguments = parameters.arguments,
          switches = parameters.switches,
          properties = parameters.properties
        )
      }
    }

  private final case class _ResolvedSelector(
    component: String,
    service: String,
    operation: String,
    parameterKinds: Map[String, ParameterDefinition.Kind]
  )

  private final case class _RequestParameters(
    arguments: List[Argument],
    switches: List[Switch],
    properties: List[Property]
  )

  private def _parse_action_name(
    p: String
  ): Consequence[(String, String, String)] =
    p.split("\\.").toVector.filter(_.nonEmpty) match {
      case Vector(component, service, operation) =>
        Consequence.success((component, service, operation))
      case Vector(service, operation) =>
        Consequence.success((_default_component_name, service, operation))
      case _ =>
        Consequence.argumentInvalid(s"action name must be component.service.operation or service.operation: $p")
    }

  private def _default_component_name: String =
    Try(logic.component.name).toOption.filter(_.nonEmpty).getOrElse("domain")

  private def _resolve_selector(
    component: String,
    service: String,
    operation: String
  ): Consequence[_ResolvedSelector] = {
    val resolvedComponent =
      if (NamingConventions.equivalentByNormalized(component, logic.component.name))
        logic.component.name
      else
        component
    logic.component.core.protocol.services.services.find(s =>
      NamingConventions.equivalentByNormalized(service, s.name)
    ) match {
      case Some(serviceDefinition) =>
        serviceDefinition.operations.operations.find(op =>
          NamingConventions.equivalentByNormalized(operation, op.name)
        ) match {
          case Some(operationDefinition) =>
            Consequence.success(_ResolvedSelector(
              resolvedComponent,
              serviceDefinition.name,
              operationDefinition.name,
              operationDefinition.specification.request.parameters.map(x => x.name -> x.kind).toMap
            ))
          case None =>
            Consequence.operationNotFound(s"${serviceDefinition.name}.${operation}")
        }
      case None =>
        Consequence.operationNotFound(s"service:${service}")
    }
  }

  private def _build_parameters(
    event: ReceptionDomainEvent,
    parameterKinds: Map[String, ParameterDefinition.Kind]
  ): _RequestParameters = {
    val params = event.payload ++ event.attributes ++ Map(
      "event_name" -> event.name,
      "event_kind" -> event.kind
    )
    params.toVector.sortBy(_._1).foldLeft(_RequestParameters(Nil, Nil, Nil)) {
      case (z, (name, rawvalue)) =>
        val value = _to_argument_value(rawvalue)
        parameterKinds.get(name) match {
          case Some(ParameterDefinition.Kind.Property) =>
            z.copy(properties = z.properties :+ Property(name, value, None))
          case Some(ParameterDefinition.Kind.Switch) =>
            z.copy(switches = z.switches :+ Switch(name, _to_boolean(value), None))
          case _ =>
            z.copy(arguments = z.arguments :+ Argument(name, value))
        }
    }
  }

  private def _to_boolean(p: String): Boolean =
    p.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => true
      case _ => false
    }

  private def _to_argument_value(
    p: Any
  ): String =
    if (p == null)
      ""
    else
      p.toString
}
