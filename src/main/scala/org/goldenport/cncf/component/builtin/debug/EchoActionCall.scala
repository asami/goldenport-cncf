package org.goldenport.cncf.component.builtin.debug

import java.nio.charset.StandardCharsets
import java.util.Base64

import io.circe.Json
import io.circe.Printer
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.ActionCall
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.http.{HttpStatus, StringResponse}
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.text.Presentable

/*
 * @since   Jan. 21, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EchoActionCall(
  core: ActionCall.Core,
  request: Request
) extends ActionCall {
  override def execute(): Consequence[OperationResponse] =
    response_json(_build_json())

  //   Consequence.success(OperationResponse.Http(buildHttpResponse()))

  // private def buildHttpResponse(): StringResponse = {
  //   val json = buildJson()
  //   val payload = Printer.spaces2.print(json)
  //   val bag = Bag.text(payload, StandardCharsets.UTF_8)
  //   StringResponse(
  //     status = HttpStatus.Ok,
  //     contentType = ContentType.APPLICATION_JSON_UTF8,
  //     bag = bag
  //   )
  // }

  private def _build_json(): Json = {
    Json.obj(
      "component" -> request.component.map(Json.fromString).getOrElse(Json.Null),
      "service" -> request.service.map(Json.fromString).getOrElse(Json.Null),
      "operation" -> Json.fromString(request.operation),
      "method" -> Json.fromString(resolveMethod()),
      "path" -> Json.fromString(resolvePath()),
      "query" -> buildQueryJson(),
      "headers" -> Json.obj(),
      "arguments" -> buildArgumentsJson(),
      "properties" -> buildPropertiesJson(),
      "body" -> buildBodyJson()
    )
  }

  private def resolveMethod(): String =
    request.source match {
      case Some(Request.Source.Args(_)) => _default
      case Some(Request.Source.Http(http)) => Presentable.print(http.method)
      case None => _default
    }

  private def _default =
    findPropertyValue("http.method").getOrElse(request.operation)

  private def resolvePath(): String =
    findPropertyValue("http.path").getOrElse(buildDerivedPath())

  private def buildDerivedPath(): String = {
    val parts = Vector(
      request.component,
      request.service,
      Some(request.operation)
    ).flatten
    s"/${parts.mkString("/")}"
  }

  private def findPropertyValue(name: String): Option[String] =
    request.properties
      .find(_.name == name)
      .map(p => Presentable.print(p.value))

  private def buildQueryJson(): Json = {
    val grouped = request.arguments.groupBy(_.name)
    val fields = grouped.map { case (name, entries) =>
      val values = entries.map(arg => Json.fromString(renderValue(arg.value)))
      name -> Json.fromValues(values)
    }.toSeq
    Json.obj(fields*)
  }

  private def buildArgumentsJson(): Json =
    Json.fromValues(
      request.arguments.map(arg =>
        Json.obj(
          "name" -> Json.fromString(arg.name),
          "value" -> Json.fromString(renderValue(arg.value))
        )
      )
    )

  private def buildPropertiesJson(): Json =
    Json.fromValues(
      request.properties.map(prop =>
        Json.obj(
          "name" -> Json.fromString(prop.name),
          "value" -> Json.fromString(renderValue(prop.value))
        )
      )
    )

  private def renderValue(value: Any): String =
    value match {
      case mb: MimeBody => renderMimeBody(mb)
      case other => Presentable.print(other)
    }

  private def renderMimeBody(mime: MimeBody): String =
    mime.contentType.toString

  private def buildBodyJson(): Json = {
    bodyDetails match {
      case Some((contentType, size, preview)) =>
        Json.obj(
          "present" -> Json.fromBoolean(true),
          "contentType" -> Json.fromString(contentType),
          "size" -> Json.fromLong(size),
          "preview" -> Json.fromString(preview)
        )
      case None =>
        Json.obj(
          "present" -> Json.fromBoolean(false),
          "contentType" -> Json.Null,
          "size" -> Json.Null,
          "preview" -> Json.Null
        )
    }
  }

  private def bodyDetails: Option[(String, Long, String)] =
    findMimeBody().map { mime =>
      val bytes = readBag(mime.value)
      val size = bytes.length.toLong
      val preview = Base64.getEncoder.encodeToString(bytes.take(256))
      (mime.contentType.toString, size, preview)
    }

  private def readBag(bag: Bag): Array[Byte] = {
    val stream = bag.openInputStream()
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  private def findMimeBody(): Option[MimeBody] =
    request.properties.collectFirst { case Property(_, mb: MimeBody, _) => mb }
      .orElse(request.arguments.collectFirst { case Argument(_, mb: MimeBody, _) => mb })
}
