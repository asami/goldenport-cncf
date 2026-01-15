package org.goldenport.cncf.service

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.http.{ContentType, HttpRequest, HttpResponse, HttpStatus, MimeType, StringResponse}
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.service.{Service as ProtocolService}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.{Action, Command, Query}
import org.goldenport.cncf.action.ActionEngine
import org.goldenport.cncf.component.{Component, ComponentLogic}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeKind}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobContext}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 31, 2025
 *  version Jan.  3, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Service extends ProtocolService with Service.CCore.Holder {
  private def invoke(
    name: String,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse] = {
    val _ = name
    logic.makeOperationRequest(request).flatMap {
      case action: Command =>
        val actionid = ActionId.generate()
        val task = ActionTask(actionid, action, logic.component.actionEngine)
        val jobid = logic.submitJob(List(task), executionContext)
        Consequence.success(OperationResponse.Scalar(jobid.value))
      case action: Query =>
        val actionid = ActionId.generate()
        val jobcontext = JobContext(None, None, Some(actionid))
        val ctx = ExecutionContext.withJobContext(executionContext, jobcontext)
        val task = ActionTask(actionid, action, logic.component.actionEngine)
        task.run(ctx).result
      case _ =>
        Consequence.failure("OperationRequest must be Action")
    }
  }

  def invokeCli(args: Array[String]): Consequence[String] =
    for {
      req <- logic.component.protocolLogic.makeRequest(args)
      res <- invokeRequest(req)
      r <- logic.component.protocolLogic.makeStringResponse(res)
    } yield r

  def invokeHttp(req: HttpRequest): Consequence[HttpResponse] =
    for {
      request <- _to_request(req)
      response <- invokeRequest(request)
      http <- _to_http_response(response)
    } yield http

  def invokeRequest(
    request: Request
  ): Consequence[Response] = {
    val servicescope =
      logic.component.scopeContext.createChildScope(
        ScopeKind.Service,
        serviceDefinition.name
      )
    val _ = servicescope
    val ctx = _execution_context_from_request(request)
    val cid = ctx.observability.correlationId
    for {
      opres <- invoke(request.operation, request, ctx, cid)
      res <- Consequence.success(_to_response(opres))
    } yield res
  }

  private def _to_response(
    op: OperationResponse
  ): Response =
    op.toResponse

  private def _execute(p: OperationRequest) = p match {
    case action: Action =>
      val ac = logic.createActionCall(action)
      logic.execute(ac)
    case m => ???
  }

  private def _execution_context_from_request(
    request: Request
  ): ExecutionContext = {
    val _ = request
    ExecutionContext.createWithSystem(logic.component.systemContext)
  }

  private def _to_request(
    req: HttpRequest
  ): Consequence[Request] = {
    val _ = req
    Consequence.failure("invokeHttp: request conversion not implemented")
  }

  private def _to_http_response(
    res: Response
  ): Consequence[HttpResponse] = {
    Consequence.success(_to_http_response_body(res))
  }

  private def _to_http_response_body(
    res: Response
  ): HttpResponse = {
    res match {
      case Response.Json(json) =>
        _string_response(
          HttpStatus.Ok,
          _content_type_json(),
          json
        )
      case Response.Scalar(value) =>
        _string_response(
          HttpStatus.Ok,
          _content_type_text(),
          value.toString
        )
      case Response.Void() =>
        _string_response(
          HttpStatus.Ok,
          _content_type_text(),
          ""
        )
      case _ =>
        _string_response(
          HttpStatus.InternalServerError,
          _content_type_text(),
          res.toString
        )
    }
  }

  private def _string_response(
    status: HttpStatus,
    contentType: ContentType,
    body: String
  ): HttpResponse = {
    val bag = Bag.text(body, StandardCharsets.UTF_8)
    StringResponse(status, contentType, bag)
  }

  private def _content_type_json(): ContentType =
    ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8))

  private def _content_type_text(): ContentType =
    ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8))
}

object Service {
  final case class CCore(
    logic: ComponentLogic
  )
  object CCore {
    trait Holder {
      def ccore: CCore

      def logic: ComponentLogic = ccore.logic
    }
  }

  case class Instance(core: ProtocolService.Core, ccore: CCore)
      extends Service with CCore.Holder {
  }

  def apply(core: ProtocolService.Core, ccore: CCore): Service =
    Instance(core, ccore)
}

case class ServiceGroup(services: Vector[Service] = Vector.empty)

object ServiceGroup {
  val empty = new ServiceGroup()

  def apply(): ServiceGroup = empty
}

// class InteractionEngine(
//   val protocol: Protocol,
//   val engine: ActionEngine
// ) {
//   def executeCli(args: Array[String]): Consequence[Response] = {
//     ???
//   }

//   def executeHttp(req: HttpRequest): Consequence[HttpResponse] = {
//     ???
//   }

//   def execute(req: Request): Consequence[Response] = {
//     ???
//   }

//   def execute(req: OperationRequest): Consequence[OperationResponse] = {
//     ???
//   }
// }
