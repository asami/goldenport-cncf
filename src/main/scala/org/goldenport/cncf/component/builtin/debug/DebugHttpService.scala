package org.goldenport.cncf.component.builtin.debug

import org.goldenport.Consequence
import org.goldenport.value.BaseContent
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.Request
import org.goldenport.cncf.action.{QueryAction, ActionCall}

/*
 * @since   Jan. 21, 2026
 *  version Jan. 22, 2026
 *  version Feb. 19, 2026
 * @version Mar. 29, 2026
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
    ): Consequence[EchoQuery] =
      Consequence.success(EchoQuery(req))
  }

  object GetOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("get").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoQuery] =
      Consequence.success(EchoQuery(req))
  }

  object PostOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("post").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoQuery] =
      Consequence.success(EchoQuery(req))
  }

  object PutOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("put").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoQuery] =
      Consequence.success(EchoQuery(req))
  }

  object DeleteOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("delete").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[EchoQuery] =
      Consequence.success(EchoQuery(req))
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

  final case class EchoQuery(
    request: Request
  ) extends QueryAction() {
    override def createCall(core: ActionCall.Core): ActionCall =
      EchoActionCall(core)
  }
}
