package org.goldenport.cncf.component

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, ParsedEventAction, ReceptionDomainEvent, SecureActionFactoryDispatcher}

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationRequestActionDispatcher(
  logic: ComponentLogic
) extends SecureActionFactoryDispatcher {
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
            Consequence.failure(s"OperationRequest must be Action: $actionName")
        }
      case _ =>
        Consequence.failure(s"unsupported event for action dispatch: ${event.getClass.getSimpleName}")
    }

  def dispatchParsedAction(
    p: ParsedEventAction
  ): Consequence[Unit] = {
    val call = logic.createActionCall(p.action)
    logic.execute(call).map(_ => ())
  }

  def dispatchParsedActionAuthorized(
    p: ParsedEventAction
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val call = logic.createActionCall(p.action, ctx)
    logic.execute(call).map(_ => ())
  }

  private def _to_request(
    actionName: String,
    event: ReceptionDomainEvent
  ): Consequence[Request] =
    _parse_action_name(actionName).map { case (component, service, operation) =>
      Request.of(
        component = component,
        service = service,
        operation = operation,
        arguments = _build_arguments(event),
        switches = Nil,
        properties = List(
          Property("event_name", event.name, None),
          Property("event_kind", event.kind, None)
        )
      )
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
        Consequence.failure(s"action name must be component.service.operation or service.operation: $p")
    }

  private def _default_component_name: String =
    Try(logic.component.name).toOption.filter(_.nonEmpty).getOrElse("domain")

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
