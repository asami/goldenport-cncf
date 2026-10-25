package org.goldenport.cncf.http

import org.goldenport.Conclusion
import org.goldenport.http.HttpResponse
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.observability.ConclusionDisclosure
import org.goldenport.cncf.protocol.HttpFailureTransportMetadata
import org.goldenport.error.DetailCode
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality

/*
 * @since   Apr. 24, 2026
 *  version May. 11, 2026
 *  version Aug. 15, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class StructuredHttpError(
  status: Int,
  statusText: String,
  message: String,
  detailCode: Option[Long],
  appCode: Option[Long],
  appStatus: Option[String],
  path: String,
  method: String,
  operationMode: OperationMode,
  conclusion: Option[Conclusion] = None,
  component: Option[String] = None,
  service: Option[String] = None,
  operation: Option[String] = None
) {
  def isProduction: Boolean =
    operationMode == OperationMode.Production

  def debugEnabled: Boolean =
    !isProduction

  def publicRecord: Record = _public_record(Map.empty, Vector.empty)

  private def _public_record(confidentiality: Map[String, DataConfidentiality], credentials: Vector[String]): Record =
    Record.data(
      "status" -> status,
      "statusText" -> ConclusionDisclosure.sanitizeText(statusText, credentials),
      "message" -> conclusion.map(ConclusionDisclosure.message(_, credentials, confidentiality))
        .getOrElse(ConclusionDisclosure.sanitizeText(message, credentials))
    ) ++ Record.dataOption(
      "detailCode" -> detailCode,
      "appCode" -> appCode,
      "appStatus" -> appStatus.map(ConclusionDisclosure.sanitizeText(_, credentials))
    )

  def diagnosticRecord: Record =
    _diagnostic_record(ConclusionDisclosure.Detail.Standard, Map.empty, Vector.empty)

  private def _diagnostic_record(detail: ConclusionDisclosure.Detail,
    confidentiality: Map[String, DataConfidentiality], credentials: Vector[String]): Record =
    _public_record(confidentiality, credentials) ++
      Record.data(
        "mode" -> operationMode.name,
        "path" -> ConclusionDisclosure.sanitizeText(path, credentials),
        "method" -> ConclusionDisclosure.sanitizeText(method, credentials)
      ) ++
      Record.dataOption(
        "component" -> component.map(ConclusionDisclosure.sanitizeText(_, credentials)),
        "service" -> service.map(ConclusionDisclosure.sanitizeText(_, credentials)),
        "operation" -> operation.map(ConclusionDisclosure.sanitizeText(_, credentials)),
        "detailCodePath" -> conclusion.map(DetailCode.path),
        "conclusion" -> conclusion.map(ConclusionDisclosure.http(_, detail, confidentiality, credentials))
      )

  def envelopeRecord: Record =
    _envelope_record(ConclusionDisclosure.Detail.Standard)

  private[cncf] def _envelope_record(
    detail: ConclusionDisclosure.Detail,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    credentials: Vector[String] = Vector.empty
  ): Record = {
    val semantic = conclusion.map { original =>
      Record.data("conclusion" -> ConclusionDisclosure.http(original, detail, confidentiality, credentials),
        "conclusionDetail" -> detail.name) ++ Record.dataOption("conclusionOmitted" ->
          Option.when(detail == ConclusionDisclosure.Detail.Standard && original.previous.nonEmpty)(Vector("previous")))
    }.getOrElse(Record.empty)
    val error = _public_record(confidentiality, credentials) ++ semantic
    Record.data("error" -> (if (debugEnabled) error ++
      Record.data("debug" -> _diagnostic_record(detail, confidentiality, credentials)) else error))
  }

  def envelopeJson: String =
    RecordEncoder.json(envelopeRecord)

  def envelopeYaml: String =
    RecordEncoder.yaml(envelopeRecord)

  def diagnosticYaml: String =
    RecordEncoder.yaml(diagnosticRecord)
}

object StructuredHttpError {
  def fromConclusion(
    conclusion: Conclusion,
    path: String,
    method: String,
    operationMode: OperationMode,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): StructuredHttpError = {
    val resolvedstatus = HttpFailureTransportMetadata.httpStatus(conclusion).code
    StructuredHttpError(
      status = resolvedstatus,
      statusText = statusText(resolvedstatus),
      message = ConclusionDisclosure.message(conclusion),
      detailCode = conclusion.status.detailCode.map(_.code),
      appCode = conclusion.status.appCode,
      appStatus = conclusion.status.appStatus,
      path = path,
      method = method,
      operationMode = operationMode,
      conclusion = Some(conclusion),
      component = component,
      service = service,
      operation = operation
    )
  }

  def fromMessage(
    message: String,
    status: Int,
    path: String,
    method: String,
    operationMode: OperationMode,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): StructuredHttpError =
    StructuredHttpError(
      status = status,
      statusText = _status_text(status),
      message = message,
      detailCode = None,
      appCode = None,
      appStatus = None,
      path = path,
      method = method,
      operationMode = operationMode,
      component = component,
      service = service,
      operation = operation
    )

  private[http] def fromFallbackHttpResponse(
    response: HttpResponse,
    path: String,
    method: String,
    operationmode: OperationMode,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): StructuredHttpError = {
    val metadata = HttpFailureTransportMetadata.fromHttpResponse(response)
    fromMessage(
      response.getString.filter(_.trim.nonEmpty).getOrElse(s"HTTP ${response.code}"),
      response.code,
      path,
      method,
      operationmode,
      component,
      service,
      operation
    ).copy(
      detailCode = metadata.detailCode,
      appCode = metadata.appCode,
      appStatus = metadata.appStatus
    )
  }

  def statusText(status: Int): String =
    status match {
      case 200 => "OK"
      case 201 => "Created"
      case 204 => "No Content"
      case 303 => "See Other"
      case 307 => "Temporary Redirect"
      case 400 => "Bad Request"
      case 401 => "Unauthorized"
      case 403 => "Forbidden"
      case 404 => "Not Found"
      case 409 => "Conflict"
      case 500 => "Internal Server Error"
      case 501 => "Not Implemented"
      case 503 => "Service Unavailable"
      case _ => ""
    }

  private def _status_text(status: Int): String =
    statusText(status)

}
