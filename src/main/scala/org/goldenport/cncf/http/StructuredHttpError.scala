package org.goldenport.cncf.http

import org.goldenport.Conclusion
import org.goldenport.cncf.config.OperationMode
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Apr. 24, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class StructuredHttpError(
  status: Int,
  message: String,
  code: String,
  codeSource: String,
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
      "message" -> message,
      "code" -> code,
      "detailCode" -> code,
      "codeSource" -> codeSource
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
    status: Int,
    path: String,
    method: String,
    operationmode: OperationMode,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): StructuredHttpError = {
    val code = _code(conclusion, status)
    StructuredHttpError(
      status = status,
      message = conclusion.displayMessage,
      code = code._1,
      codeSource = code._2,
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
      message = message,
      code = s"http.${status}",
      codeSource = "fallback",
      path = path,
      method = method,
      operationMode = operationmode,
      component = component,
      service = service,
      operation = operation
    )

  private def _code(
    conclusion: Conclusion,
    status: Int
  ): (String, String) =
    conclusion.status.detailCodes.headOption match {
      case Some(code) => code.id -> "conclusion"
      case None => s"http.${status}" -> "fallback"
    }
}
