package org.goldenport.cncf.component

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, ParsedEventAction, ReceptionDomainEvent, ScopedActionCallDispatcher, SecureActionFactoryDispatcher}
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Mar. 21, 2026
 *  version Mar. 28, 2026
 * @version Apr. 21, 2026
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
      _resolve_selector(component, service, operation).map { case (resolvedComponent, resolvedService, resolvedOperation) =>
        Request.of(
          component = resolvedComponent,
          service = resolvedService,
          operation = resolvedOperation,
          arguments = _build_arguments(event),
          switches = Nil,
          properties = List(
            Property("event_name", event.name, None),
            Property("event_kind", event.kind, None)
          )
        )
      }
    }

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
  ): Consequence[(String, String, String)] = {
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
            Consequence.success((resolvedComponent, serviceDefinition.name, operationDefinition.name))
          case None =>
            Consequence.operationNotFound(s"${serviceDefinition.name}.${operation}")
        }
      case None =>
        Consequence.operationNotFound(s"service:${service}")
    }
  }

  private def _build_arguments(
    event: ReceptionDomainEvent
  ): List[Argument] = {
    val params = event.payload.map { case (k, v) =>
      k -> _to_argument_value(v)
    } ++ event.attributes
    params.toVector
      .sortBy(_._1)
      .map { case (k, v) => Argument(k, v) }
      .toList
  }

  private def _to_argument_value(
    p: Any
  ): String =
    if (p == null)
      ""
    else
      p.toString
}
