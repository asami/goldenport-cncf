package org.goldenport.cncf.observability

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.blob.{BlobKind, BlobPutRequest, BlobStorageRef, BlobStoreConfig, BlobStoreFactory}
import org.goldenport.cncf.config.{OperationMode, ResolvedParameter, ResolvedParameters}
import org.goldenport.datatype.ContentType
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   May. 11, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DiagnosticPayloadExternalizationConfig(
  enabled: Boolean = false,
  destination: Option[String] = None,
  localRoot: Path = DiagnosticPayloadExternalizationConfig.DefaultLocalRoot,
  thresholdBytes: Int = DiagnosticPayloadExternalizationConfig.DefaultThresholdBytes,
  payloadTargets: Set[String] = DiagnosticPayloadExternalizationConfig.DefaultPayloadTargets,
  operationExact: Set[String] = Set.empty,
  operationContains: Vector[String] = Vector.empty,
  allowRequestOverride: Boolean = false,
  unsafeOpaquePayloads: Boolean = false,
  retentionDays: Option[Int] = None,
  validationError: Option[String] = None
) {
  def normalizedDestination(operationMode: OperationMode): Option[String] =
    destination.map(DiagnosticPayloadExternalizationConfig.normalizeDestination).orElse {
      if (enabled && operationMode != OperationMode.Production)
        Some(DiagnosticPayloadExternalizationConfig.DestinationLocalFile)
      else
        None
    }

  def isProductionDestinationMissing(operationMode: OperationMode): Boolean =
    enabled && operationMode == OperationMode.Production && normalizedDestination(operationMode).isEmpty

  def matches(
    operation: String,
    payloadKind: String,
    sizeBytes: Int
  ): Boolean =
    enabled &&
      sizeBytes >= math.max(0, thresholdBytes) &&
      payloadTargets.contains(DiagnosticPayloadExternalizationConfig.normalizePayloadKind(payloadKind)) &&
      _operation_matches(operation)

  private def _operation_matches(operation: String): Boolean =
    if (operationExact.isEmpty && operationContains.isEmpty)
      true
    else
      operationExact.contains(operation) || operationContains.exists(operation.contains)
}

object DiagnosticPayloadExternalizationConfig {
  val DestinationLocalFile: String = "local-file"
  val DestinationBlobStore: String = "blob-store"
  val DefaultLocalRoot: Path = Path.of("target/cncf.d/observability/payloads")
  val DefaultThresholdBytes: Int = 1200
  val DefaultPayloadTargets: Set[String] = Set("result", "response")

  def normalizeDestination(value: String): String =
    value.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-')

  def normalizePayloadKind(value: String): String =
    value.trim.toLowerCase(java.util.Locale.ROOT).replace('-', '_')

  def fromValues(
    enabled: Boolean,
    destination: Option[String],
    localRoot: Option[String],
    thresholdBytes: Option[Int],
    payloadTargets: Vector[String],
    operationExact: Vector[String],
    operationContains: Vector[String],
    allowRequestOverride: Option[Boolean],
    unsafeOpaquePayloads: Option[Boolean],
    retentionDays: Option[Int],
    operationMode: OperationMode
  ): DiagnosticPayloadExternalizationConfig = {
    val normalizedDestination = destination.map(normalizeDestination).filter(_.nonEmpty)
    val normalizedPayloads =
      payloadTargets.map(normalizePayloadKind).filter(_.nonEmpty) match {
        case Vector() => DefaultPayloadTargets
        case xs => xs.toSet
      }
    val config = DiagnosticPayloadExternalizationConfig(
      enabled = enabled,
      destination = normalizedDestination,
      localRoot = localRoot.map(x => Path.of(x.trim)).getOrElse(DefaultLocalRoot),
      thresholdBytes = thresholdBytes.getOrElse(DefaultThresholdBytes),
      payloadTargets = normalizedPayloads,
      operationExact = operationExact.map(_.trim).filter(_.nonEmpty).toSet,
      operationContains = operationContains.map(_.trim).filter(_.nonEmpty),
      allowRequestOverride = allowRequestOverride.getOrElse(operationMode != OperationMode.Production),
      unsafeOpaquePayloads = unsafeOpaquePayloads.getOrElse(false),
      retentionDays = retentionDays.filter(_ >= 0)
    )
    val error =
      if (config.isProductionDestinationMissing(operationMode))
        Some("textus.observability.payload.externalization.destination is required when externalization is enabled in production")
      else
        normalizedDestination.collect {
          case other if other != DestinationLocalFile && other != DestinationBlobStore =>
            s"unknown diagnostic payload externalization destination: $other"
        }
    config.copy(validationError = error)
  }
}

object DiagnosticPayloadReferenceCodec {
  private val BlobPrefix = "blob-"

  def encodeBlobRef(ref: BlobStorageRef): String =
    BlobPrefix + Base64.getUrlEncoder.withoutPadding.encodeToString(ref.print.getBytes(StandardCharsets.UTF_8))

  def decodeBlobRef(id: String): Option[BlobStorageRef] =
    if (!id.startsWith(BlobPrefix))
      None
    else
      try {
        val bytes = Base64.getUrlDecoder.decode(id.stripPrefix(BlobPrefix))
        BlobStorageRef.parse(new String(bytes, StandardCharsets.UTF_8)).toOption
      } catch {
        case NonFatal(_) => None
      }
}

final case class DiagnosticPayloadWriteRequest(
  operation: String,
  payloadKind: String,
  contentType: ContentType,
  filename: String,
  payload: Array[Byte],
  attributes: Map[String, String] = Map.empty
)

final case class DiagnosticPayloadWriteResult(
  reference: DiagnosticPayloadReference,
  sizeBytes: Int
)

object DiagnosticPayloadExternalizer {
  private final case class Scope(
    operation: String,
    overrideConfig: Option[RequestOverride] = None
  )
  private final case class RequestOverride(
    enabled: Option[Boolean] = None,
    payloadTargets: Option[Set[String]] = None
  )

  private val _scope = new ThreadLocal[Scope]()

  def withOperation[A](
    operation: String
  )(
    body: => A
  ): A =
    withOperation(operation, None)(body)

  def withOperation[A](
    operation: String,
    params: ResolvedParameters
  )(
    body: => A
  ): A =
    withOperation(operation, Some(params))(body)

  private def withOperation[A](
    operation: String,
    params: Option[ResolvedParameters]
  )(
    body: => A
  ): A = {
    val previous = Option(_scope.get())
    _scope.set(Scope(operation, params.flatMap(_request_override)))
    try body
    finally {
      previous match {
        case Some(value) => _scope.set(value)
        case None => _scope.remove()
      }
    }
  }

  def currentOperation: Option[String] =
    Option(_scope.get()).map(_.operation).filter(_.nonEmpty)

  private def currentOverride: Option[RequestOverride] =
    Option(_scope.get()).flatMap(_.overrideConfig)

  def fromGlobal: DiagnosticPayloadExternalizer =
    org.goldenport.cncf.context.GlobalRuntimeContext.current
      .map(global => DiagnosticPayloadExternalizer(
        global.config.diagnosticPayloadExternalizationConfig,
        global.config.operationMode,
        global.config.blobStoreConfig
      ))
      .getOrElse(DiagnosticPayloadExternalizer.disabled)

  private def _request_override(
    params: ResolvedParameters
  ): Option[RequestOverride] = {
    val enabled = _boolean_param(params, "textus.debug.payload.externalize")
    val payloads = _string_param(params, "textus.debug.payload.externalize.payloads")
      .map(_.split(",").toVector.map(DiagnosticPayloadExternalizationConfig.normalizePayloadKind).filter(_.nonEmpty).toSet)
      .filter(_.nonEmpty)
    Option.when(enabled.nonEmpty || payloads.nonEmpty)(RequestOverride(enabled, payloads))
  }

  private def _string_param(
    params: ResolvedParameters,
    key: String
  ): Option[String] =
    params.get(key).map(p => ResolvedParameter.format_value(p.value).trim).filter(_.nonEmpty)

  private def _boolean_param(
    params: ResolvedParameters,
    key: String
  ): Option[Boolean] =
    _string_param(params, key).flatMap { value =>
      value.toLowerCase(java.util.Locale.ROOT) match {
        case "true" | "1" | "yes" | "on" => Some(true)
        case "false" | "0" | "no" | "off" => Some(false)
        case _ => None
      }
    }

  val disabled: DiagnosticPayloadExternalizer =
    DiagnosticPayloadExternalizer(
      DiagnosticPayloadExternalizationConfig(),
      OperationMode.Develop,
      BlobStoreConfig()
    )
}

final case class DiagnosticPayloadExternalizer(
  config: DiagnosticPayloadExternalizationConfig,
  operationMode: OperationMode,
  blobStoreConfig: BlobStoreConfig
) {
  def externalizeRecordSummary(
    operation: String,
    payloadKind: String,
    record: Record,
    summary: DiagnosticPayloadSummary,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): DiagnosticPayloadSummary = {
    val text = RecordEncoder.json(DiagnosticPayloadSummary.sanitizeRecord(record, confidentiality))
    externalizeText(operation, payloadKind, "application/json", "json", text, summary)
  }

  def externalizeText(
    operation: String,
    payloadKind: String,
    contentType: String,
    extension: String,
    text: String,
    summary: DiagnosticPayloadSummary
  ): DiagnosticPayloadSummary = {
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    val normalizedKind = DiagnosticPayloadExternalizationConfig.normalizePayloadKind(payloadKind)
    val effectiveConfig = _effective_config
    if (!effectiveConfig.enabled)
      summary.copy(externalizationStatus = Some("disabled"))
    else if (effectiveConfig.validationError.isDefined)
      summary.copy(externalizationStatus = Some("unavailable"), externalizationReason = effectiveConfig.validationError)
    else if (!effectiveConfig.matches(operation, normalizedKind, bytes.length))
      summary.copy(externalizationStatus = Some("not_matched"))
    else
      _write(effectiveConfig, DiagnosticPayloadWriteRequest(
        operation = operation,
        payloadKind = normalizedKind,
        contentType = ContentType.parse(contentType),
        filename = _filename(operation, normalizedKind, extension),
        payload = bytes,
        attributes = Map(
          "cncf.observability.payload" -> "true",
          "operation" -> operation,
          "payloadKind" -> normalizedKind
        )
      )) match {
        case Consequence.Success(result) =>
          summary.copy(
            inline = None,
            payloadReference = Some(result.reference),
            externalizationStatus = Some("stored")
          )
        case Consequence.Failure(conclusion) =>
          summary.copy(
            externalizationStatus = Some("failed"),
            externalizationReason = Some(conclusion.display)
          )
      }
  }

  def externalizeUnsafeTextSummary(
    operation: String,
    payloadKind: String,
    contentType: String,
    extension: String,
    text: String,
    summary: DiagnosticPayloadSummary
  ): DiagnosticPayloadSummary = {
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    val normalizedKind = DiagnosticPayloadExternalizationConfig.normalizePayloadKind(payloadKind)
    val effectiveConfig = _effective_config
    if (!effectiveConfig.enabled)
      summary.copy(externalizationStatus = Some("disabled"))
    else if (effectiveConfig.validationError.isDefined)
      summary.copy(externalizationStatus = Some("unavailable"), externalizationReason = effectiveConfig.validationError)
    else if (!effectiveConfig.matches(operation, normalizedKind, bytes.length))
      summary.copy(externalizationStatus = Some("not_matched"))
    else if (!effectiveConfig.unsafeOpaquePayloads)
      summary.copy(
        externalizationStatus = Some("not_supported"),
        externalizationReason = Some("unsafe opaque payload externalization is disabled")
      )
    else
      externalizeText(operation, normalizedKind, contentType, extension, _redact_sensitive_text(text), summary)
  }

  private def _effective_config: DiagnosticPayloadExternalizationConfig =
    DiagnosticPayloadExternalizer.currentOverride match {
      case Some(overrideConfig) if config.allowRequestOverride =>
        config.copy(
          enabled = overrideConfig.enabled.getOrElse(config.enabled),
          payloadTargets = overrideConfig.payloadTargets.getOrElse(config.payloadTargets),
          operationExact = Set.empty,
          operationContains = Vector.empty
        )
      case _ =>
        config
    }

  private def _write(
    effectiveConfig: DiagnosticPayloadExternalizationConfig,
    request: DiagnosticPayloadWriteRequest
  ): Consequence[DiagnosticPayloadWriteResult] =
    effectiveConfig.normalizedDestination(operationMode) match {
      case Some(DiagnosticPayloadExternalizationConfig.DestinationLocalFile) =>
        _write_local(effectiveConfig, request)
      case Some(DiagnosticPayloadExternalizationConfig.DestinationBlobStore) =>
        _write_blob(request)
      case Some(other) =>
        Consequence.configurationInvalid(s"unknown diagnostic payload externalization destination: $other")
      case None =>
        Consequence.configurationInvalid("diagnostic payload externalization destination is not configured")
    }

  private def _write_local(
    effectiveConfig: DiagnosticPayloadExternalizationConfig,
    request: DiagnosticPayloadWriteRequest
  ): Consequence[DiagnosticPayloadWriteResult] =
    Consequence {
      val day = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(Instant.now())
      val dir = effectiveConfig.localRoot.resolve(day).toAbsolutePath.normalize
      Files.createDirectories(dir)
      _cleanup_local(effectiveConfig)
      val path = dir.resolve(request.filename).normalize
      if (!path.startsWith(effectiveConfig.localRoot.toAbsolutePath.normalize))
        throw new IllegalArgumentException("diagnostic payload path escaped configured root")
      Files.write(path, request.payload)
      DiagnosticPayloadWriteResult(
        DiagnosticPayloadReference(
          href = Some(s"/web/system/admin/observability/payloads/${path.getFileName.toString}"),
          path = Option.when(operationMode != OperationMode.Production)(path.toString),
          ref = Some(path.getFileName.toString),
          storage = Some("local-file"),
          contentType = Some(request.contentType.header),
          sizeBytes = Some(request.payload.length)
        ),
        request.payload.length
      )
    }

  private def _write_blob(
    request: DiagnosticPayloadWriteRequest
  ): Consequence[DiagnosticPayloadWriteResult] =
    if (blobStoreConfig.normalizedBackend == BlobStoreConfig.BackendInMemory)
      Consequence.configurationInvalid("diagnostic payload blob-store externalization requires a durable BlobStore backend")
    else
      for {
        store <- BlobStoreFactory.create(blobStoreConfig)
        id = _blob_entity_id()
        result <- store.put(
          BlobPutRequest(
            id = id,
            kind = BlobKind.Attachment,
            filename = Some(request.filename),
            contentType = request.contentType,
            attributes = request.attributes ++ Map("diagnosticPayload" -> "true")
          ),
          Bag.binary(request.payload)
        )
      } yield DiagnosticPayloadWriteResult(
        DiagnosticPayloadReference(
          href = Some(s"/web/system/admin/observability/payloads/${DiagnosticPayloadReferenceCodec.encodeBlobRef(result.storageRef)}"),
          url = Option(result.accessUrl.downloadPath).filter(_.nonEmpty),
          ref = Some(result.storageRef.print),
          storage = Some("blob-store"),
          contentType = Some(result.contentType.header),
          sizeBytes = Some(result.byteSize.toInt)
        ),
        result.byteSize.toInt
      )

  private def _redact_sensitive_text(value: String): String = {
    val sensitive = "(?i)(password|passwd|secret|token|session|authorization|cookie|credential|apikey|privatekey)"
    val jsonLike = (s"""("([^"]*$sensitive[^"]*)"\\s*:\\s*)("[^"]*"|[^,}\\]]+)""").r
    val formLike = (s"""((^|[&;,\\s])([^=&;,\\s]*$sensitive[^=&;,\\s]*)\\s*=\\s*)([^&;,\\s]+)""").r
    val yamlLike = (s"""((?m)^\\s*([^:\\n]*$sensitive[^:\\n]*)\\s*:\\s*)(.+)$$""").r
    val jsonRedacted = jsonLike.replaceAllIn(value, m => s"""${m.group(1)}"[redacted]"""")
    val formRedacted = formLike.replaceAllIn(jsonRedacted, m => s"${m.group(1)}[redacted]")
    yamlLike.replaceAllIn(formRedacted, m => s"${m.group(1)}[redacted]")
  }

  private def _cleanup_local(
    effectiveConfig: DiagnosticPayloadExternalizationConfig
  ): Unit =
    effectiveConfig.retentionDays.foreach { days =>
      val base = effectiveConfig.localRoot.toAbsolutePath.normalize
      if (Files.isDirectory(base)) {
        val cutoff = java.time.LocalDate.now(ZoneOffset.UTC).minusDays(days.toLong)
        val dirs = Files.newDirectoryStream(base)
        try {
          dirs.iterator().asScala.foreach { path =>
            val name = path.getFileName.toString
            if (Files.isDirectory(path) && name.matches("[0-9]{8}")) {
              val date = java.time.LocalDate.parse(name, DateTimeFormatter.BASIC_ISO_DATE)
              if (date.isBefore(cutoff))
                _delete_recursively(path)
            }
          }
        } finally {
          dirs.close()
        }
      }
    }

  private def _delete_recursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      val children = Files.newDirectoryStream(path)
      try children.iterator().asScala.foreach(_delete_recursively)
      finally children.close()
    }
    Files.deleteIfExists(path)
  }

  private def _filename(
    operation: String,
    payloadKind: String,
    extension: String
  ): String = {
    val op = operation.replaceAll("[^A-Za-z0-9._-]", "_").take(120)
    val id = UUID.randomUUID().toString
    s"${op}_${payloadKind}_${id}.${extension.stripPrefix(".")}"
  }

  private def _blob_entity_id(): EntityId =
    EntityId(
      "cncf",
      "p" + UUID.randomUUID().toString.replace("-", ""),
      EntityCollectionId("cncf", "observability", "diagnostic_payload")
    )
}
