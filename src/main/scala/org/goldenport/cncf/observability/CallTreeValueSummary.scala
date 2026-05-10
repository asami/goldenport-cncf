package org.goldenport.cncf.observability

import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality

/*
 * @since   May. 10, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object CallTreeValueSummary {
  def resultAttributes(
    value: Any,
    key: String = "result"
  ): Map[String, String] =
    Map(key -> json(summary(value, includeInline = false)))

  def responseAttributes(
    response: OperationResponse
  ): Map[String, String] =
    Map(
      "response_type" -> response.getClass.getSimpleName.stripSuffix("$"),
      "response" -> json(operationResponseSummary(response))
    )

  def operationResponseSummary(
    response: OperationResponse
  ): Record =
    operationResponseSummary(response, Map.empty)

  def operationResponseSummary(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality]
  ): Record =
    DiagnosticPayloadSummary.operationResponse(response, confidentiality).toRecord

  def summary(
    value: Any
  ): Record =
    summary(value, includeInline = true)

  def summary(
    value: Any,
    includeInline: Boolean
  ): Record =
    DiagnosticPayloadSummary.summarize(value, includeInline).toRecord

  def recordSummary(
    record: Record
  ): Record =
    recordSummary(record, includeInline = true, Map.empty)

  def recordSummary(
    record: Record,
    includeInline: Boolean
  ): Record =
    recordSummary(record, includeInline, Map.empty)

  def recordSummary(
    record: Record,
    includeInline: Boolean,
    confidentiality: Map[String, DataConfidentiality]
  ): Record =
    DiagnosticPayloadSummary.recordSummary(record, includeInline, confidentiality).toRecord

  def textSummary(
    kind: String,
    text: String
  ): Record =
    textSummary(kind, text, includeInline = true)

  def textSummary(
    kind: String,
    text: String,
    includeInline: Boolean
  ): Record = {
    DiagnosticPayloadSummary.textSummary(kind, text, includeInline).toRecord
  }

  private def json(record: Record): String =
    RecordEncoder.json(record)
}
