package org.goldenport.cncf.component.protocol

import org.goldenport.provisional.observation._
import org.goldenport.observation.Descriptor.Facet

import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.bag.TextBag
import org.goldenport.http._
import org.goldenport.protocol._
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.cncf.action._
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.Program
import org.goldenport.ConsequenceT
import org.goldenport.cncf.component.protocol.ComponentOperationDefinition.OperationAttribute
import cats.Functor
import cats.syntax.all.*

/*
 * @since   Feb.  6, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class RestOperationDefinition()
    extends ComponentOperationDefinition() {
  def restSpecification: RestSpecification =
    RestSpecification(_to_base_url(name))

  def restOperationSpecification: RestOperationSpecification =
    RestOperationSpecification()

  protected[protocol] def operationAttribute: OperationAttribute =
    OperationAttribute.Command

  def createOperationRequest(req: Request): Consequence[Action] =
    Consequence.success(RestCommand(req, this))
}

final case class RestSpecification(
  baseUrl: String,
  mappingRule: RestParameterMappingRule =
    RestParameterMappingRule.Default
)
object RestSpecification {
  def apply(name: String): RestSpecification =
    RestSpecification(_to_base_url(name), RestParameterMappingRule.Default)
}

final case class RestOperationSpecification(
  method: Option[HttpRequest.Method] = None,
  baseUrl: Option[String] = None,
  mappingRule: Option[RestParameterMappingRule] = None
)

trait RestParameterMappingRule {
  def parameters(
    operation: OperationDefinition,
    request: Request
  ): RestParameterMappingRule.Parameters
}
object RestParameterMappingRule {
  case class Parameters(
    path: Vector[String],
    body: Option[Bag]
  )

  object Default extends RestParameterMappingRule {
    override def parameters(
      operation: OperationDefinition,
      request: Request
    ): Parameters = {
      val path = request.arguments.map(_.value.toString).toVector
      val body = _body_from_properties(request.properties)
      Parameters(path, body)
    }

    private def _body_from_properties(properties: List[Property]): Option[Bag] =
      properties.find(_.name == "-d").flatMap(_body_from_property)

    private def _body_from_property(property: Property): Option[Bag] =
      property.value match {
        case bag: Bag => Some(bag)
        case text: String => Some(Bag.text(text))
        case _ => None
      }
  }
}

case class RestCommand(
  request: Request,
  operation: RestOperationDefinition
) extends Command() {
  def createCall(core: ActionCall.Core): RestCall =
    RestCall(core, operation)
}

final case class RestCall(
  core: ActionCall.Core,
  operation: RestOperationDefinition
) extends FunctionalActionCall with ActionCallHttpPart {
  protected final def build_Program: ExecUowM[OperationResponse] = {
    val spec = operation.restSpecification
    val opSpec = operation.restOperationSpecification
    val resolvedRule =
      opSpec.mappingRule.orElse(operation.restOperationSpecification.mappingRule)
        .getOrElse(spec.mappingRule)
    // Actually, we want: opSpec.mappingRule.getOrElse(spec.mappingRule), but spec.mappingRule is always present.
    // To match the override hierarchy: operation.restOperationSpecification.mappingRule, fallback to operation.restSpecification.mappingRule.
    // So, opSpec.mappingRule.getOrElse(spec.mappingRule)
    val params = resolvedRule.parameters(operation, request)
    val baseUrl = opSpec.baseUrl.getOrElse(spec.baseUrl)
    val method = opSpec.method.getOrElse {
      operation.operationAttribute match {
        case OperationAttribute.Query => HttpRequest.GET
        case OperationAttribute.Command => HttpRequest.POST
        case _ => HttpRequest.POST
      }
    }
    val httprequest = _build_http_request(baseUrl, method, params)
    val url = httprequest.urlStringWithQuery
    val exec = method match {
      case HttpRequest.POST =>
        for {
          body <- _body_string_c(httprequest)
          response <- http_post(url, body, _headers_map(httprequest))
        } yield response
      case HttpRequest.PUT =>
        for {
          body <- _body_string_c(httprequest)
          response <- http_put(url, body, _headers_map(httprequest))
        } yield response
      case _ =>
        http_get(url)
    }
    exec.map(response => OperationResponse.Scalar(_response_text(response)))
  }

  private def _build_http_request(
    baseUrl: String,
    method: HttpRequest.Method,
    params: RestParameterMappingRule.Parameters
  ): HttpRequest = {
    val path = _build_path(baseUrl, params.path)
    HttpRequest.fromPath(
      method = method,
      path = path,
      body = params.body,
      context = HttpContext.empty
    )
  }

  private def _build_path(baseUrl: String, segments: Vector[String]): String = {
    val prefix = _trim_trailing_slash(baseUrl)
    val suffix =
      if (segments.isEmpty) ""
      else segments.map(_clean_segment).mkString("/", "/", "")
    val combined = s"$prefix$suffix"
    if (combined.isEmpty) "/" else combined
  }

  private def _trim_trailing_slash(path: String): String =
    if (path != "/" && path.endsWith("/")) path.dropRight(1) else path

  private def _clean_segment(segment: String): String =
    if (segment.startsWith("/")) segment.dropWhile(_ == '/') else segment

  private def _body_string_c(req: HttpRequest): ExecUowM[Option[String]] =
    req.body match {
      case None => _exec_pure(None)
      case Some(t: TextBag) =>
        _exec_from(t.toText.map(Some(_)))
      case Some(bag) =>
        _exec_from(Consequence.failure(s"unsupported request body bag type: ${bag.getClass.getName}"))
    }

  private def _headers_map(req: HttpRequest): Map[String, String] =
    req.header.asMap.map { case (k, v) => k -> v.toString }

  private def _response_text(resp: HttpResponse): String =
    resp.getString.getOrElse(resp.show)

  private def _exec_pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  private def _exec_from[A](c: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](c)
}

private def _to_base_url(name: String): String = {
  val prefix = if (name.startsWith("/")) name else s"/$name"
  if (prefix != "/" && prefix.endsWith("/")) prefix.dropRight(1) else prefix
}
