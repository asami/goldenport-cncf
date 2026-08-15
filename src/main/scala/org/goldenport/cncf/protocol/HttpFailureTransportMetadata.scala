package org.goldenport.cncf.protocol

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.util.Base64

import scala.util.Try

import org.goldenport.Conclusion
import org.goldenport.http.HttpResponse
import org.goldenport.record.Record

/*
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object HttpFailureTransportMetadata {
  val DETAIL_CODE_HEADER = "X-Cncf-Internal-Detail-Code"
  val APP_CODE_HEADER = "X-Cncf-Internal-App-Code"
  val APP_STATUS_HEADER = "X-Cncf-Internal-App-Status"

  final case class Metadata(
    detailCode: Option[Long],
    appCode: Option[Long],
    appStatus: Option[String]
  ) {
    def isEmpty: Boolean =
      detailCode.isEmpty && appCode.isEmpty && appStatus.isEmpty
  }

  object Metadata {
    val empty: Metadata = Metadata(None, None, None)
  }

  def attach(
    response: HttpResponse,
    conclusion: Conclusion
  ): HttpResponse =
    _attach(
      response,
      Metadata(
        conclusion.status.detailCode.map(_.code),
        conclusion.status.appCode,
        conclusion.status.appStatus
      )
    )

  def fromHttpResponse(response: HttpResponse): Metadata =
    _metadata(response.header).getOrElse(Metadata.empty)

  def isInternalHeader(name: String): Boolean =
    _internal_headers.exists(_.equalsIgnoreCase(Option(name).getOrElse("")))

  private val _internal_headers = Set(
    DETAIL_CODE_HEADER,
    APP_CODE_HEADER,
    APP_STATUS_HEADER
  )

  private def _metadata(header: Record): Option[Metadata] =
    for {
      detailcode <- _optional_long(header, DETAIL_CODE_HEADER)
      appcode <- _optional_long(header, APP_CODE_HEADER)
      appstatus <- _optional_app_status(header, APP_STATUS_HEADER)
    } yield Metadata(detailcode, appcode, appstatus)

  private def _optional_long(
    header: Record,
    name: String
  ): Option[Option[Long]] =
    _single_header_value(header, name).flatMap {
      case None => Some(None)
      case Some(value) => _long(value).map(Some.apply)
    }

  private def _optional_app_status(
    header: Record,
    name: String
  ): Option[Option[String]] =
    _single_header_value(header, name).flatMap {
      case None => Some(None)
      case Some(value) => _decode_app_status(value).map(Some.apply)
    }

  private def _single_header_value(
    header: Record,
    name: String
  ): Option[Option[String]] =
    header.fields.filter(field => field.key.equalsIgnoreCase(name)).toList match {
      case Nil => Some(None)
      case field :: Nil => Option(field.value.single).map(x => Some(x.toString))
      case _ => None
    }

  private def _long(value: String): Option[Long] =
    Option(value)
      .filter(_.matches("-?(0|[1-9][0-9]*)"))
      .flatMap(_.toLongOption)

  private def _decode_app_status(value: String): Option[String] =
    Option(value)
      .filter(_.matches("[A-Za-z0-9_-]*"))
      .flatMap { encoded =>
        Try {
          val bytes = Base64.getUrlDecoder.decode(encoded)
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          decoder.decode(ByteBuffer.wrap(bytes)).toString
        }.toOption
      }
      .filter(status => !status.contains('\r') && !status.contains('\n'))

  private def _attach(
    response: HttpResponse,
    metadata: Metadata
  ): HttpResponse = {
    val header = Record(response.header.fields.filterNot(field => isInternalHeader(field.key)))
    val fields = Vector(
      metadata.detailCode.map(value => DETAIL_CODE_HEADER -> value.toString),
      metadata.appCode.map(value => APP_CODE_HEADER -> value.toString),
      metadata.appStatus.map(value => APP_STATUS_HEADER -> _encode_app_status(value))
    ).flatten
    response.withHeader(header ++ Record.data(fields*))
  }

  private def _encode_app_status(status: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(
      Option(status).getOrElse("").getBytes(StandardCharsets.UTF_8)
    )
}
