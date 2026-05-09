package org.goldenport.cncf.observability

import java.nio.charset.StandardCharsets

import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   May. 10, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object CallTreeValueSummary {
  private val InlineByteLimit = 1200
  private val InlineFieldLimit = 20
  private val InlineElementLimit = 20

  def resultAttributes(
    value: Any,
    key: String = "result"
  ): Map[String, String] =
    Map(key -> json(summary(value, inlinePayloads = false)))

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
    response match {
      case OperationResponse.Void() =>
        Record.dataAuto(
          "kind" -> "void"
        )
      case OperationResponse.RecordResponse(record) =>
        recordSummary(record) ++ Record.dataAuto("kind" -> "record")
      case OperationResponse.Json(json) =>
        textSummary("json", json.spaces2)
      case OperationResponse.Yaml(yaml) =>
        textSummary("yaml", yaml)
      case OperationResponse.Scalar(value) =>
        summary(value) ++ Record.dataAuto("kind" -> "scalar")
      case OperationResponse.Http(response) =>
        textSummary("http", response.show)
      case OperationResponse.Opaque(value) =>
        summary(value) ++ Record.dataAuto("kind" -> "opaque")
    }

  def summary(
    value: Any
  ): Record =
    summary(value, inlinePayloads = true)

  private def summary(
    value: Any,
    inlinePayloads: Boolean
  ): Record =
    value match {
      case null =>
        Record.dataAuto("kind" -> "null")
      case () =>
        Record.dataAuto("kind" -> "unit")
      case m: Record =>
        recordSummary(m, inlinePayloads)
      case m: org.goldenport.cncf.directive.SearchResult[?] =>
        Record.dataAuto(
          "kind" -> "search-result",
          "record_count" -> m.data.size,
          "fetched_count" -> m.fetchedCount,
          "total_count" -> m.totalCount,
          "offset" -> m.offset,
          "limit" -> m.limit
        )
      case m: org.goldenport.cncf.datastore.SearchResult =>
        Record.dataAuto(
          "kind" -> "datastore-search-result",
          "record_count" -> m.records.size,
          "range" -> m.range.toString,
          "cursor" -> m.cursor.map(_.value)
        )
      case m: org.goldenport.cncf.entity.CreateResult[?] =>
        Record.dataAuto(
          "kind" -> "entity-create-result",
          "id" -> m.id.print,
          "has_record" -> m.record.isDefined
        )
      case Some(v) =>
        summary(v, inlinePayloads) ++ Record.dataAuto("optional" -> true)
      case None =>
        Record.dataAuto("kind" -> "none")
      case xs: Array[?] =>
        sequenceSummary("array", xs.toVector, inlinePayloads)
      case xs: Seq[?] =>
        sequenceSummary("sequence", xs, inlinePayloads)
      case m: scala.collection.Map[?, ?] =>
        mapSummary(m, inlinePayloads)
      case xs: Iterable[?] =>
        sequenceSummary("iterable", xs.toVector, inlinePayloads)
      case v: String =>
        textSummary("string", v, inlinePayloads)
      case v: Boolean =>
        Record.dataAuto("kind" -> "boolean", "value" -> v)
      case v: Byte =>
        Record.dataAuto("kind" -> "byte", "value" -> v)
      case v: Short =>
        Record.dataAuto("kind" -> "short", "value" -> v)
      case v: Int =>
        Record.dataAuto("kind" -> "int", "value" -> v)
      case v: Long =>
        Record.dataAuto("kind" -> "long", "value" -> v)
      case v: Float =>
        Record.dataAuto("kind" -> "float", "value" -> v)
      case v: Double =>
        Record.dataAuto("kind" -> "double", "value" -> v)
      case v: BigInt =>
        Record.dataAuto("kind" -> "bigint", "value" -> v.toString)
      case v: BigDecimal =>
        Record.dataAuto("kind" -> "bigdecimal", "value" -> v.toString)
      case other =>
        textSummary(other.getClass.getSimpleName.stripSuffix("$"), String.valueOf(other))
    }

  def recordSummary(
    record: Record
  ): Record =
    recordSummary(record, includeInline = true)

  def recordSummary(
    record: Record,
    includeInline: Boolean
  ): Record = {
    val displayRecord = sanitizeRecord(record)
    val jsonText = json(displayRecord)
    val bytes = byteSize(jsonText)
    val base = Record.dataAuto(
      "kind" -> "record",
      "field_count" -> displayRecord.fields.size,
      "size_bytes" -> bytes
    )
    val withData = displayRecord.asMap.get("data") match {
      case Some(xs: Seq[?]) =>
        base ++ Record.dataAuto("record_count" -> xs.size)
      case Some(xs: Array[?]) =>
        base ++ Record.dataAuto("record_count" -> xs.length)
      case _ =>
        base
      }
    if (includeInline && bytes <= InlineByteLimit && record.fields.size <= InlineFieldLimit)
      withData ++ Record.dataAuto("inline" -> displayRecord)
    else
      withData ++ Record.dataAuto("inline" -> false)
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
    val bytes = byteSize(text)
    val base = Record.dataAuto(
      "kind" -> kind,
      "size_bytes" -> bytes,
      "char_count" -> text.length
    )
    if (includeInline && bytes <= InlineByteLimit)
      base ++ Record.dataAuto("inline" -> text)
    else
      base ++ Record.dataAuto("inline" -> false)
  }

  private def sequenceSummary(
    kind: String,
    xs: Iterable[?],
    inlinePayloads: Boolean
  ): Record = {
    val values = xs.toVector
    val base = Record.dataAuto(
      "kind" -> kind,
      "record_count" -> values.size
    )
    if (inlinePayloads && values.size <= InlineElementLimit) {
      val inline = values.map {
        case r: Record => recordSummary(r, inlinePayloads)
        case x => summary(x, inlinePayloads)
      }
      base ++ Record.dataAuto("inline" -> inline)
    } else {
      base ++ Record.dataAuto("inline" -> false)
    }
  }

  private def mapSummary(
    m: scala.collection.Map[?, ?],
    inlinePayloads: Boolean
  ): Record = {
    val record = Record.dataAuto(m.toVector.map {
      case (k, v) => String.valueOf(k) -> sanitizeValue(String.valueOf(k), v)
    }*)
    recordSummary(record, inlinePayloads) ++ Record.dataAuto("kind" -> "map")
  }

  private def sanitizeRecord(
    record: Record
  ): Record =
    Record.dataAuto(record.fields.map { field =>
      field.key -> sanitizeValue(field.key, field.value)
    }*)

  private def sanitizeValue(
    key: String,
    value: Any
  ): Any =
    if (isSensitiveKey(key))
      "[redacted]"
    else
      value match {
        case r: Record => sanitizeRecord(r)
        case xs: Seq[?] => xs.map(sanitizeNested)
        case xs: Array[?] => xs.toVector.map(sanitizeNested)
        case m: scala.collection.Map[?, ?] =>
          Record.dataAuto(m.toVector.map {
            case (k, v) => String.valueOf(k) -> sanitizeValue(String.valueOf(k), v)
          }*)
        case other => other
      }

  private def sanitizeNested(
    value: Any
  ): Any =
    value match {
      case r: Record => sanitizeRecord(r)
      case m: scala.collection.Map[?, ?] =>
        Record.dataAuto(m.toVector.map {
          case (k, v) => String.valueOf(k) -> sanitizeValue(String.valueOf(k), v)
        }*)
      case xs: Seq[?] => xs.map(sanitizeNested)
      case xs: Array[?] => xs.toVector.map(sanitizeNested)
      case other => other
    }

  private def isSensitiveKey(
    key: String
  ): Boolean = {
    val normalized = key.toLowerCase.replace("-", "_")
    normalized.contains("password") ||
      normalized.contains("token") ||
      normalized.contains("secret") ||
      normalized.contains("session") ||
      normalized.contains("credential")
  }

  private def json(record: Record): String =
    RecordEncoder.json(record)

  private def byteSize(text: String): Int =
    text.getBytes(StandardCharsets.UTF_8).length
}
