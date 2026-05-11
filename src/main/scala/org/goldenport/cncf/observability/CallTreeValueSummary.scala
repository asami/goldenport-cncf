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
    Map(key -> json(summary(value, includeInline = false, payloadKind = key)))

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
  ): Record = {
    val base = DiagnosticPayloadSummary.operationResponse(response, confidentiality)
    val operation = DiagnosticPayloadExternalizer.currentOperation.getOrElse("")
    response match {
      case OperationResponse.RecordResponse(record) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeRecordSummary(operation, "response", record, base, confidentiality)
          .toRecord
      case OperationResponse.Json(json) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(operation, "response", "application/json", "json", json.spaces2, base)
          .toRecord
      case OperationResponse.Yaml(yaml) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(operation, "response", "application/yaml", "yaml", yaml, base)
          .toRecord
      case OperationResponse.Scalar(value) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(operation, "response", "text/plain", "txt", String.valueOf(value), base)
          .toRecord
      case OperationResponse.Opaque(value) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(operation, "response", "text/plain", "txt", String.valueOf(value), base)
          .toRecord
      case _ =>
        base.toRecord
    }
  }

  def summary(
    value: Any
  ): Record =
    summary(value, includeInline = true)

  def summary(
    value: Any,
    includeInline: Boolean
  ): Record =
    summary(value, includeInline, payloadKind = "result")

  def summary(
    value: Any,
    includeInline: Boolean,
    payloadKind: String
  ): Record = {
    val base = DiagnosticPayloadSummary.summarize(value, includeInline)
    val operation = DiagnosticPayloadExternalizer.currentOperation.getOrElse("")
    value match {
      case r: Record =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeRecordSummary(operation, payloadKind, r, base)
          .toRecord
      case _ =>
        base.toRecord
    }
  }

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
  ): Record = {
    val base = DiagnosticPayloadSummary.recordSummary(record, includeInline, confidentiality)
    val operation = DiagnosticPayloadExternalizer.currentOperation.getOrElse("")
    DiagnosticPayloadExternalizer.fromGlobal
      .externalizeRecordSummary(operation, "result", record, base, confidentiality)
      .toRecord
  }

  def recordSummary(
    record: Record,
    includeInline: Boolean,
    confidentiality: Map[String, DataConfidentiality],
    payloadKind: String
  ): Record = {
    val base = DiagnosticPayloadSummary.recordSummary(record, includeInline, confidentiality)
    val operation = DiagnosticPayloadExternalizer.currentOperation.getOrElse("")
    DiagnosticPayloadExternalizer.fromGlobal
      .externalizeRecordSummary(operation, payloadKind, record, base, confidentiality)
      .toRecord
  }

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
