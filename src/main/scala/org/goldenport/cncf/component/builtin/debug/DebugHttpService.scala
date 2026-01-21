package org.goldenport.cncf.component.builtin.debug

import org.goldenport.Consequence
import org.goldenport.model.value.BaseContent
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.Request
import org.goldenport.cncf.action.{Command, ActionCall}

/*
 * @since   Jan. 21, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
object DebugHttpService extends ServiceDefinition {
  val specification = ServiceDefinition.Specification.Builder("http").
    operation(
      EchoOperation,
      GetOperation,
      PostOperation,
      PutOperation,
      DeleteOperation
    ).build()

  object EchoOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("echo").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoCommand] =
      Consequence.success(EchoCommand(req))
  }

  object GetOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("get").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoCommand] =
      Consequence.success(EchoCommand(req))
  }

  object PostOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("post").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoCommand] =
      Consequence.success(EchoCommand(req))
  }

  object PutOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("put").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoCommand] =
      Consequence.success(EchoCommand(req))
  }

  object DeleteOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("delete").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoCommand] =
      Consequence.success(EchoCommand(req))
  }

  // final class DebugHttpEchoOperation(
  //   request: spec.RequestDefinition,
  //   response: spec.ResponseDefinition,
  //   methodOverride: Option[String]
  // ) extends spec.OperationDefinition {
  //   override val specification: spec.OperationDefinition.Specification =
  //     spec.OperationDefinition.Specification(
  //       name = methodOverride.map(_.toLowerCase).getOrElse("echo"),
  //       request = request,
  //       response = response
  //     )

  //   override def createOperationRequest(
  //     req: Request
  //   ): Consequence[OperationRequest] =
  //     Consequence.success(EchoCommand(req))
  // }

  final case class EchoCommand(
    request: Request
  ) extends Command() {
    override def createCall(core: ActionCall.Core): ActionCall =
      EchoActionCall(core, request)
  }
}
