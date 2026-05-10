package org.goldenport.cncf.http

import org.goldenport.Conclusion
import org.goldenport.cncf.config.OperationMode
import org.goldenport.error.DetailCode
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Apr. 24, 2026
 * @version May. 11, 2026
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

  def publicRecord: Record =
    Record.data(
      "status" -> status,
      "statusText" -> statusText,
      "message" -> message
    ) ++ Record.dataOption(
      "detailCode" -> detailCode,
      "appCode" -> appCode,
      "appStatus" -> appStatus
    )

  def diagnosticRecord: Record =
    publicRecord ++
      Record.data(
        "mode" -> operationMode.name,
        "path" -> path,
        "method" -> method
      ) ++
      Record.dataOption(
        "component" -> component,
        "service" -> service,
        "operation" -> operation,
        "detailCodePath" -> conclusion.map(DetailCode.path),
        "conclusion" -> conclusion.map(_.toRecord),
        "diagnostic" -> conclusion.map(_.show)
      )

  def envelopeRecord: Record =
    if (debugEnabled)
      Record.data("error" -> (publicRecord ++ Record.data("debug" -> diagnosticRecord)))
    else
      Record.data("error" -> publicRecord)

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
    operationmode: OperationMode,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): StructuredHttpError = {
    val resolvedstatus = conclusion.status.webCode.code
    StructuredHttpError(
      status = resolvedstatus,
      statusText = conclusion.status.webCode.statusText,
      message = conclusion.displayMessage,
      detailCode = conclusion.status.detailCode.map(_.code),
      appCode = conclusion.status.appCode,
      appStatus = conclusion.status.appStatus,
      path = path,
      method = method,
      operationMode = operationmode,
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
    operationmode: OperationMode,
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
      operationMode = operationmode,
      component = component,
      service = service,
      operation = operation
    )
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
