package org.goldenport.cncf.provider.record

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.provider.{ProviderCall, ProviderEngine, ProviderRequest}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.record.{Record, RecordFormat}
import org.goldenport.record.io.RecordImportDecoder
import org.goldenport.record.io.RecordImportDecoder.{RecordImportFormat, RecordImportOptions, RecordImportResult}
import org.goldenport.record.io.RecordExportEncoder
import org.goldenport.record.io.RecordExportEncoder.{RecordExportOptions, RecordExportResult}

/*
 * @since   May. 24, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait RecordImportProvider {
  def importRecords(
    request: RecordImportProviderRequest,
    core: ProviderCall.Core
  ): Consequence[RecordImportProviderResult]
}

trait RecordExportProvider {
  def exportRecords(
    request: RecordExportProviderRequest,
    core: ProviderCall.Core
  ): Consequence[RecordExportProviderResult]
}

final case class RecordImportProviderRequest(
  format: String,
  text: Option[String] = None,
  bytes: Option[Array[Byte]] = None,
  path: Option[Path] = None,
  options: RecordImportOptions = RecordImportOptions.default
)

final case class RecordImportProviderResult(
  records: Vector[Record],
  metadata: Record,
  issueCount: Int,
  detectedFormat: String
)

final case class RecordExportProviderRequest(
  format: String,
  records: Vector[Record],
  options: RecordExportOptions = RecordExportOptions.default
)

final case class RecordExportProviderResult(
  bytes: Array[Byte],
  text: Option[String],
  contentType: String,
  extension: String,
  format: String,
  recordCount: Int
)

final class DefaultRecordImportProvider extends RecordImportProvider {
  def importRecords(
    request: RecordImportProviderRequest,
    core: ProviderCall.Core
  ): Consequence[RecordImportProviderResult] =
    ProviderEngine.execute(_RecordImportCall(core.copy(
      request = ProviderRequest(
        "record-import",
        request.format,
        Map("format" -> request.format)
      )
    ), request))
}

final class DefaultRecordExportProvider extends RecordExportProvider {
  def exportRecords(
    request: RecordExportProviderRequest,
    core: ProviderCall.Core
  ): Consequence[RecordExportProviderResult] =
    ProviderEngine.execute(_RecordExportCall(core.copy(
      request = ProviderRequest(
        "record-export",
        request.format,
        Map("format" -> request.format, "record_count" -> request.records.size.toString)
      )
    ), request))
}

object RecordImportProvider {
  val default: RecordImportProvider = new DefaultRecordImportProvider()
}

object RecordExportProvider {
  val default: RecordExportProvider = new DefaultRecordExportProvider()
}

private final case class _RecordImportCall(
  core: ProviderCall.Core,
  request: RecordImportProviderRequest
) extends ProviderCall[RecordImportProviderResult] {
  private val _decoder = RecordImportDecoder()

  protected def build_Program: ExecUowM[RecordImportProviderResult] =
    provider_step("provider:record-import.decode", Map("format" -> request.format)) {
      for {
        format <- _import_format(request.format)
        result <- _decode(format)
      } yield RecordImportProviderResult(
        records = result.records,
        metadata = result.metadata,
        issueCount = result.issues.size,
        detectedFormat = result.detectedFormat.label.toLowerCase(java.util.Locale.ROOT)
      )
    }

  override def calltreeResultAttributes(result: RecordImportProviderResult): Map[String, String] =
    Map(
      "record_count" -> result.records.size.toString,
      "issue_count" -> result.issueCount.toString,
      "detected_format" -> result.detectedFormat
    )

  private def _decode(format: RecordImportFormat): Consequence[RecordImportResult] =
    request.path match {
      case Some(path) => _decoder.decodePath(path, format, request.options)
      case None => request.bytes match {
        case Some(bytes) => _decoder.decodeBytes(bytes, format, request.options)
        case None => request.text match {
          case Some(text) => _decoder.decode(text, format, request.options)
          case None => Consequence.argumentInvalid("Record import requires text, bytes, or path.")
        }
      }
    }
}

private final case class _RecordExportCall(
  core: ProviderCall.Core,
  request: RecordExportProviderRequest
) extends ProviderCall[RecordExportProviderResult] {
  private val _encoder = RecordExportEncoder()

  protected def build_Program: ExecUowM[RecordExportProviderResult] =
    provider_step("provider:record-export.encode", Map("format" -> request.format)) {
      for {
        format <- _record_format(request.format)
        result <- _encoder.encode(request.records, format, request.options)
      } yield _to_result(result, request.records.size)
    }

  override def calltreeResultAttributes(result: RecordExportProviderResult): Map[String, String] =
    Map(
      "record_count" -> result.recordCount.toString,
      "format" -> result.format,
      "content_type" -> result.contentType
    )

  private def _to_result(
    result: RecordExportResult,
    count: Int
  ): RecordExportProviderResult =
    RecordExportProviderResult(
      bytes = result.bytes,
      text = result.text,
      contentType = result.contentType,
      extension = result.extension,
      format = result.format.productPrefix.toLowerCase(java.util.Locale.ROOT),
      recordCount = count
    )
}

private def _import_format(value: String): Consequence[RecordImportFormat] =
  RecordImportFormat.parse(value)
    .map(Consequence.success)
    .getOrElse(Consequence.argumentInvalid(s"unsupported record import format: $value"))

private def _record_format(value: String): Consequence[RecordFormat] =
  value.trim.toLowerCase(java.util.Locale.ROOT) match {
    case "json" => Consequence.success(RecordFormat.Json)
    case "yaml" | "yml" => Consequence.success(RecordFormat.Yaml)
    case "xml" => Consequence.success(RecordFormat.Xml)
    case "hocon" | "conf" => Consequence.success(RecordFormat.Hocon)
    case "csv" => Consequence.success(RecordFormat.Csv)
    case "tsv" => Consequence.success(RecordFormat.Tsv)
    case "ltsv" => Consequence.success(RecordFormat.Ltsv)
    case "line" | "lines" | "txt" => Consequence.success(RecordFormat.Lines)
    case "tsl" => Consequence.success(RecordFormat.Tsl)
    case "excel" | "xlsx" | "xls" => Consequence.success(RecordFormat.Excel)
    case other => Consequence.argumentInvalid(s"unsupported record export format: $other")
  }
