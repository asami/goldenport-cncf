package org.goldenport.cncf.observability

import java.nio.charset.StandardCharsets
import io.circe.Json
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality

/*
 * @since   May. 11, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DiagnosticPayloadReference(
  href: Option[String] = None,
  url: Option[String] = None,
  path: Option[String] = None,
  ref: Option[String] = None,
  storage: Option[String] = None,
  contentType: Option[String] = None,
  sizeBytes: Option[Int] = None
) {
  def isEmpty: Boolean =
    href.isEmpty && url.isEmpty && path.isEmpty && ref.isEmpty &&
      storage.isEmpty && contentType.isEmpty && sizeBytes.isEmpty

  def toRecord: Record =
    Record.dataOption(
      "href" -> href,
      "url" -> url,
      "path" -> path,
      "ref" -> ref,
      "storage" -> storage,
      "content_type" -> contentType,
      "size_bytes" -> sizeBytes
    )
}

object DiagnosticPayloadReference {
  def fromRecord(record: Record): Option[DiagnosticPayloadReference] = {
    val ref = DiagnosticPayloadReference(
      href = _string(record, "payload_href").orElse(_string(record, "external_href")).orElse(_string(record, "href")),
      url = _string(record, "payload_url").orElse(_string(record, "external_url")).orElse(_string(record, "url")),
      path = _string(record, "payload_path").orElse(_string(record, "external_path")).orElse(_string(record, "path")),
      ref = _string(record, "payload_ref").orElse(_string(record, "external_ref")).orElse(_string(record, "ref")),
      storage = _string(record, "payload_storage").orElse(_string(record, "external_storage")).orElse(_string(record, "storage")),
      contentType = _string(record, "content_type").orElse(_string(record, "contentType")),
      sizeBytes = _int(record, "payload_size_bytes").orElse(_int(record, "external_size_bytes")).orElse(_int(record, "size_bytes"))
    )
    Option.when(!ref.isEmpty)(ref)
  }

  private def _string(record: Record, key: String): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private def _int(record: Record, key: String): Option[Int] =
    record.getInt(key)
}

final case class DiagnosticPayloadSummary(
  kind: String,
  valueType: Option[String] = None,
  status: Option[String] = None,
  outcome: Option[String] = None,
  sizeBytes: Option[Int] = None,
  charCount: Option[Int] = None,
  fieldCount: Option[Int] = None,
  recordCount: Option[Int] = None,
  elementCount: Option[Int] = None,
  offset: Option[Int] = None,
  limit: Option[Int] = None,
  fetchedCount: Option[Int] = None,
  totalCount: Option[Int] = None,
  inline: Option[Any] = None,
  truncated: Boolean = false,
  truncationReason: Option[String] = None,
  externalizationStatus: Option[String] = None,
  externalizationReason: Option[String] = None,
  payloadReference: Option[DiagnosticPayloadReference] = None
) {
  def toRecord: Record = {
    val reference = payloadReference.filterNot(_.isEmpty)
    val base = Record.dataAuto(
      "kind" -> kind
    ) ++ Record.dataOption(
      "value_type" -> valueType,
      "status" -> status,
      "outcome" -> outcome,
      "size_bytes" -> sizeBytes,
      "char_count" -> charCount,
      "field_count" -> fieldCount,
      "record_count" -> recordCount,
      "element_count" -> elementCount,
      "offset" -> offset,
      "limit" -> limit,
      "fetched_count" -> fetchedCount,
      "total_count" -> totalCount
    ) ++ Record.dataOption(
      "externalization_status" -> externalizationStatus,
      "externalization_reason" -> externalizationReason
    ) ++ Record.dataAuto(
      "inline" -> inline.getOrElse(false)
    ) ++ (
      if (truncated)
        Record.dataAuto("truncated" -> true) ++ Record.dataOption("truncation_reason" -> truncationReason)
      else
        Record.empty
    )
    reference.map { r =>
      base ++ r.toRecord ++ Record.dataOption(
        "payload_href" -> r.href,
        "payload_url" -> r.url,
        "payload_path" -> r.path,
        "payload_ref" -> r.ref,
        "payload_storage" -> r.storage
      )
    }.getOrElse(base)
  }

  def displayText: String = {
    val parts = Vector(
      Some(kind),
      recordCount.map(x => s"records=$x"),
      elementCount.map(x => s"elements=$x"),
      fieldCount.map(x => s"fields=$x"),
      fetchedCount.map(x => s"fetched=$x"),
      totalCount.map(x => s"total=$x"),
      sizeBytes.map(x => s"${x} bytes"),
      charCount.map(x => s"${x} chars"),
      Option.when(truncated)("truncated"),
      externalizationStatus.map(x => s"externalization=$x"),
      payloadReference.flatMap(r => r.href.orElse(r.url).orElse(r.path).orElse(r.ref)).map(x => s"ref=$x")
    ).flatten
    parts.mkString(" ")
  }
}

object DiagnosticPayloadSummary {
  final case class Policy(
    inlineByteLimit: Int = 1200,
    inlineFieldLimit: Int = 20,
    inlineElementLimit: Int = 20,
    textPreviewByteLimit: Int = 0
  )

  val DefaultPolicy: Policy = Policy()

  def summarize(
    value: Any,
    includeInline: Boolean = true,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    policy: Policy = DefaultPolicy
  ): DiagnosticPayloadSummary =
    _summary(value, includeInline, confidentiality, policy)

  def operationResponse(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    policy: Policy = DefaultPolicy
  ): DiagnosticPayloadSummary =
    response match {
      case OperationResponse.Void() =>
        DiagnosticPayloadSummary("void", valueType = Some("Void"))
      case OperationResponse.RecordResponse(record) =>
        recordSummary(record, includeInline = true, confidentiality, policy).copy(valueType = Some("RecordResponse"))
      case OperationResponse.Json(json) =>
        jsonSummary("json", json, includeInline = false, policy = policy).copy(valueType = Some("Json"))
      case OperationResponse.Yaml(yaml) =>
        textSummary("yaml", yaml, includeInline = false, policy = policy).copy(valueType = Some("Yaml"))
      case OperationResponse.Scalar(value) =>
        summarize(value, includeInline = false, confidentiality, policy).copy(kind = "scalar", valueType = Some("Scalar"))
      case OperationResponse.Http(response) =>
        textSummary("http", response.show, includeInline = false, policy = policy).copy(valueType = Some("Http"))
      case OperationResponse.Opaque(value) =>
        summarize(value, includeInline = false, confidentiality, policy).copy(kind = "opaque", valueType = Some("Opaque"))
    }

  def recordSummary(
    record: Record,
    includeInline: Boolean = true,
    confidentiality: Map[String, DataConfidentiality] = Map.empty,
    policy: Policy = DefaultPolicy
  ): DiagnosticPayloadSummary = {
    val sanitized = sanitizeRecord(record, confidentiality)
    val jsonText = RecordEncoder.json(sanitized)
    val bytes = byteSize(jsonText)
    val count = _record_count(sanitized)
    val inline =
      if (includeInline && bytes <= policy.inlineByteLimit && sanitized.fields.size <= policy.inlineFieldLimit)
        Some(sanitized)
      else
        None
    DiagnosticPayloadSummary(
      kind = "record",
      valueType = Some("Record"),
      sizeBytes = Some(bytes),
      fieldCount = Some(sanitized.fields.size),
      recordCount = count,
      inline = inline,
      payloadReference = DiagnosticPayloadReference.fromRecord(sanitized)
    )
  }

  def textSummary(
    kind: String,
    text: String,
    includeInline: Boolean = true,
    policy: Policy = DefaultPolicy
  ): DiagnosticPayloadSummary = {
    val bytes = byteSize(text)
    val preview =
      if (includeInline && bytes <= policy.inlineByteLimit)
        Some(text)
      else if (includeInline && policy.textPreviewByteLimit > 0)
        Some(_truncate_bytes(text, policy.textPreviewByteLimit))
      else
        None
    DiagnosticPayloadSummary(
      kind = kind,
      valueType = Some("text"),
      sizeBytes = Some(bytes),
      charCount = Some(text.length),
      inline = preview,
      truncated = preview.exists(_ != text),
      truncationReason = Option.when(preview.exists(_ != text))("preview")
    )
  }

  def jsonSummary(
    kind: String,
    json: Json,
    includeInline: Boolean = true,
    policy: Policy = DefaultPolicy
  ): DiagnosticPayloadSummary = {
    val text = json.spaces2
    val bytes = byteSize(text)
    val inline =
      if (includeInline && bytes <= policy.inlineByteLimit)
        Some(_json_to_value(json))
      else
        None
    DiagnosticPayloadSummary(
      kind = kind,
      valueType = Some("json"),
      sizeBytes = Some(bytes),
      charCount = Some(text.length),
      inline = inline
    )
  }

  def sanitizeRecord(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    Record.dataAuto(record.fields.map { field =>
      field.key -> sanitizeValue(field.key, field.value.single, confidentiality)
    }*)

  def sanitizeValue(
    key: String,
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    if (isSensitiveKey(key, confidentiality))
      "[redacted]"
    else
      sanitizeNested(value, confidentiality)

  def sanitizeNested(
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    value match {
      case r: Record =>
        sanitizeRecord(r, confidentiality)
      case xs: Seq[?] =>
        xs.map(sanitizeNested(_, confidentiality))
      case xs: Array[?] =>
        xs.toVector.map(sanitizeNested(_, confidentiality))
      case m: scala.collection.Map[?, ?] =>
        Record.dataAuto(m.toVector.map {
          case (k, v) =>
            val key = Option(k).map(_.toString).getOrElse("")
            key -> sanitizeValue(key, v, confidentiality)
        }*)
      case other =>
        other
    }

  def isSensitiveKey(
    key: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Boolean = {
    val normalized = normalizeKey(key)
    confidentiality.get(key)
      .orElse(confidentiality.find { case (k, _) => normalizeKey(k) == normalized }.map(_._2))
      .exists(_.shouldRedactByDefault) ||
    normalized.contains("password") ||
      normalized.contains("passwd") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie") ||
      normalized.contains("credential") ||
      normalized.contains("apikey") ||
      normalized.contains("privatekey")
  }

  def normalizeKey(key: String): String =
    Option(key).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  private def _summary(
    value: Any,
    includeInline: Boolean,
    confidentiality: Map[String, DataConfidentiality],
    policy: Policy
  ): DiagnosticPayloadSummary =
    value match {
      case null =>
        DiagnosticPayloadSummary("null")
      case () =>
        DiagnosticPayloadSummary("unit")
      case m: Record =>
        recordSummary(m, includeInline, confidentiality, policy)
      case m: org.goldenport.cncf.directive.SearchResult[?] =>
        DiagnosticPayloadSummary(
          kind = "search-result",
          recordCount = Some(m.data.size),
          fetchedCount = Some(m.fetchedCount),
          totalCount = m.totalCount,
          offset = m.offset,
          limit = m.limit
        )
      case m: org.goldenport.cncf.datastore.SearchResult =>
        DiagnosticPayloadSummary(
          kind = "datastore-search-result",
          recordCount = Some(m.records.size),
          status = m.cursor.map(_.value)
        )
      case m: org.goldenport.cncf.entity.CreateResult[?] =>
        DiagnosticPayloadSummary(
          kind = "entity-create-result",
          status = Some(m.id.print),
          fieldCount = Option.when(m.record.isDefined)(1)
        )
      case Some(v) =>
        _summary(v, includeInline, confidentiality, policy).copy(valueType = Some("optional"))
      case None =>
        DiagnosticPayloadSummary("none")
      case xs: Array[?] =>
        sequenceSummary("array", xs.toVector, includeInline, confidentiality, policy)
      case xs: Seq[?] =>
        sequenceSummary("sequence", xs, includeInline, confidentiality, policy)
      case m: scala.collection.Map[?, ?] =>
        mapSummary(m, includeInline, confidentiality, policy)
      case xs: Iterable[?] =>
        sequenceSummary("iterable", xs.toVector, includeInline, confidentiality, policy)
      case v: String =>
        textSummary("string", v, includeInline = includeInline && false, policy = policy)
      case v: Boolean =>
        scalarSummary("boolean", v, includeInline)
      case v: Byte =>
        scalarSummary("byte", v, includeInline)
      case v: Short =>
        scalarSummary("short", v, includeInline)
      case v: Int =>
        scalarSummary("int", v, includeInline)
      case v: Long =>
        scalarSummary("long", v, includeInline)
      case v: Float =>
        scalarSummary("float", v, includeInline)
      case v: Double =>
        scalarSummary("double", v, includeInline)
      case v: BigInt =>
        scalarSummary("bigint", v.toString, includeInline)
      case v: BigDecimal =>
        scalarSummary("bigdecimal", v.toString, includeInline)
      case other =>
        textSummary(other.getClass.getSimpleName.stripSuffix("$"), String.valueOf(other), includeInline = false, policy = policy)
    }

  private def scalarSummary(
    kind: String,
    value: Any,
    includeInline: Boolean
  ): DiagnosticPayloadSummary =
    DiagnosticPayloadSummary(
      kind = kind,
      inline = Option.when(includeInline)(value)
    )

  private def sequenceSummary(
    kind: String,
    xs: Iterable[?],
    includeInline: Boolean,
    confidentiality: Map[String, DataConfidentiality],
    policy: Policy
  ): DiagnosticPayloadSummary = {
    val values = xs.toVector
    val inline =
      if (includeInline && values.size <= policy.inlineElementLimit)
        Some(values.map {
          case r: Record => recordSummary(r, includeInline = false, confidentiality, policy).toRecord
          case x => _summary(x, includeInline = false, confidentiality, policy).toRecord
        })
      else
        None
    DiagnosticPayloadSummary(
      kind = kind,
      elementCount = Some(values.size),
      recordCount = Some(values.size),
      inline = inline
    )
  }

  private def mapSummary(
    m: scala.collection.Map[?, ?],
    includeInline: Boolean,
    confidentiality: Map[String, DataConfidentiality],
    policy: Policy
  ): DiagnosticPayloadSummary = {
    val record = Record.dataAuto(m.toVector.map {
      case (k, v) =>
        val key = Option(k).map(_.toString).getOrElse("")
        key -> sanitizeValue(key, v, confidentiality)
    }*)
    recordSummary(record, includeInline, confidentiality, policy).copy(kind = "map")
  }

  private def _record_count(record: Record): Option[Int] =
    record.asMap.get("data") match {
      case Some(xs: Seq[?]) => Some(xs.size)
      case Some(xs: Array[?]) => Some(xs.length)
      case _ => None
    }

  private def _json_to_value(json: Json): Any =
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toLong.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(_json_to_value).toVector,
      jsonObject = obj => Record.dataAuto(obj.toVector.map { case (k, v) => k -> _json_to_value(v) }*)
    )

  private def _truncate_bytes(text: String, limit: Int): String = {
    if (byteSize(text) <= limit)
      text
    else {
      val builder = new StringBuilder
      var bytes = 0
      text.takeWhile { ch =>
        val size = ch.toString.getBytes(StandardCharsets.UTF_8).length
        val ok = bytes + size <= limit
        if (ok) {
          builder.append(ch)
          bytes += size
        }
        ok
      }
      builder.toString + "..."
    }
  }

  private def byteSize(text: String): Int =
    text.getBytes(StandardCharsets.UTF_8).length
}
