package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.RuntimeContext

/*
 * @since   Apr. 15, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
trait WebOperationDispatcher {
  def targetName: String

  def dispatch(request: HttpRequest): HttpResponse

  def dispatchWithMetadata(request: HttpRequest): HttpExecutionResult =
    HttpExecutionResult(dispatch(request), RuntimeContext.ExecutionMetadata.empty)
}

object WebOperationDispatcher {
  def create(
    engine: HttpExecutionEngine
  ): WebOperationDispatcher = {
    val config = RuntimeConfig.from(engine.runtimeSubsystem.configuration)
    config.webOperationDispatcher match {
      case "rest" =>
        config.webOperationDispatcherRestBaseUrl match {
          case Some(baseUrl) =>
            Rest(baseUrl, new UrlConnectionHttpDriver(baseUrl))
          case None =>
            RestNotConfigured
        }
      case _ =>
        Local(engine)
    }
  }

  final case class Local(
    engine: HttpExecutionEngine
  ) extends WebOperationDispatcher {
    val targetName: String = "local"

    def dispatch(request: HttpRequest): HttpResponse =
      engine.execute(request)

    override def dispatchWithMetadata(request: HttpRequest): HttpExecutionResult =
      engine.executeWithMetadata(request)
  }

  final case class Rest(
    baseUrl: String,
    driver: HttpDriver
  ) extends WebOperationDispatcher {
    val targetName: String = "rest"

    def dispatch(request: HttpRequest): HttpResponse =
      request.method match {
        case HttpRequest.GET =>
          driver.get(_path_with_query(request))
        case HttpRequest.POST =>
          driver.post(_path_with_query(request), _body(request), _headers(request))
        case HttpRequest.PUT =>
          driver.put(_path_with_query(request), _body(request), _headers(request))
        case _ =>
          _not_implemented("REST WebOperationDispatcher currently supports GET, POST, and PUT")
      }

    private def _path_with_query(request: HttpRequest): String = {
      val path = request.path.asString
      val query = _encode_record(request.query)
      val relative = if (query.isEmpty) path else s"${path}?${query}"
      val base = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
      s"${base}${relative}"
    }

    private def _body(request: HttpRequest): Option[String] =
      request.body.map(_.asStringUnsafe()).orElse {
        val form = _encode_record(request.form)
        if (form.isEmpty) None else Some(form)
      }

    private def _headers(request: HttpRequest): Map[String, String] =
      request.header.asMap.view.mapValues(_.toString).toMap
  }

  case object RestNotConfigured extends WebOperationDispatcher {
    val targetName: String = "rest-not-configured"

    def dispatch(request: HttpRequest): HttpResponse = {
      val _ = request
      _not_implemented("REST WebOperationDispatcher requires textus.web.operation.dispatcher.rest.base-url")
    }
  }

  private def _encode_record(record: org.goldenport.record.Record): String =
    record.asMap.toVector.map {
      case (key, value) =>
        s"${_url_encode(key)}=${_url_encode(Option(value).map(_.toString).getOrElse(""))}"
    }.mkString("&")

  private def _url_encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def _not_implemented(message: String): HttpResponse =
    HttpResponse.Text(
      HttpStatus.InternalServerError,
      ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
      Bag.text(message, StandardCharsets.UTF_8)
    )
}
