package org.goldenport.cncf.mcp.client

import java.nio.file.{Path, Paths}
import scala.util.Try

import org.goldenport.Consequence
import org.goldenport.cncf.config.SecretReference
import org.goldenport.record.Record
import org.goldenport.record.io.RecordSourceLoader

/*
 * CNCF-owned activation policy for an external Codex MCP definition source.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class CodexMcpRuntimeConfiguration private (
  definitionSource: Path,
  policy: CodexMcpImportPolicy
)

private[cncf] object CodexMcpRuntimeConfiguration {
  def loadC(path: Path): Consequence[CodexMcpRuntimeConfiguration] =
    RecordSourceLoader.load(path).flatMap(decodeC(_, path))

  def decodeC(
    record: Record,
    origin: Path
  ): Consequence[CodexMcpRuntimeConfiguration] =
    for {
      source <- _source_path_c(record, origin)
      serverset <- _required_record_c(record, "serverSet")
      serversetid <- _required_string_c(serverset, "id").flatMap(McpServerSetId.parseC)
      limits <- _limits_c(serverset.getRecord("limits"))
      serverrecords <- _required_records_c(serverset, "servers")
      servers <- _servers_c(serverrecords)
      policy <- CodexMcpImportPolicy.createC(serversetid, servers, limits)
    } yield CodexMcpRuntimeConfiguration(source, policy)

  private def _source_path_c(
    record: Record,
    origin: Path
  ): Consequence[Path] =
    for {
      source <- _required_record_c(record, "source")
      kind <- _required_string_c(source, "kind")
      _ <- if (kind.trim.equalsIgnoreCase("codex")) Consequence.unit else
        Consequence.argumentPolicyViolation(
          "source.kind",
          "mcp-client.codex-import",
          "codex",
          kind
        )
      value <- _required_string_c(source, "path")
      path <- Consequence {
        val candidate = Paths.get(value).normalize()
        if (candidate.isAbsolute)
          candidate
        else
          Option(origin.toAbsolutePath.normalize().getParent)
            .getOrElse(Paths.get(".").toAbsolutePath.normalize())
            .resolve(candidate)
            .normalize()
      }
    } yield path

  private def _limits_c(record: Option[Record]): Consequence[McpClientLimits] = {
    val defaults = McpClientLimits.default
    record match {
      case None => Consequence.success(defaults)
      case Some(value) =>
        for {
          timeout <- _long_c(value, "timeoutMillis", defaults.timeoutMillis)
          calls <- _int_c(value, "maximumCalls", defaults.maximumCalls)
          inputbytes <- _long_c(value, "maximumInputBytes", defaults.maximumInputBytes)
          outputbytes <- _long_c(value, "maximumOutputBytes", defaults.maximumOutputBytes)
          concurrency <- _int_c(value, "maximumConcurrency", defaults.maximumConcurrency)
          limits <- McpClientLimits.createC(
            timeout,
            calls,
            inputbytes,
            outputbytes,
            concurrency
          )
        } yield limits
    }
  }

  private def _long_c(
    record: Record,
    name: String,
    default: Long
  ): Consequence[Long] =
    record.getAny(name) match {
      case None => Consequence.success(default)
      case Some(value) =>
        _integral_c(name, value).flatMap { number =>
          if (number.isValidLong)
            Consequence.success(number.longValue)
          else
            Consequence.argumentLimitExceeded(name, Long.MaxValue, number, "mcp-client.limit")
        }
    }

  private def _int_c(
    record: Record,
    name: String,
    default: Int
  ): Consequence[Int] =
    record.getAny(name) match {
      case None => Consequence.success(default)
      case Some(value) =>
        _integral_c(name, value).flatMap { number =>
          if (number.isValidInt)
            Consequence.success(number.intValue)
          else
            Consequence.argumentLimitExceeded(name, Int.MaxValue, number, "mcp-client.limit")
        }
    }

  private def _integral_c(
    name: String,
    value: Any
  ): Consequence[BigInt] =
    Try(BigDecimal(value.toString)).toOption.flatMap(_.toBigIntExact) match {
      case Some(number) => Consequence.success(number)
      case None => Consequence.argumentFormatError(name, "integral number", value)
    }

  private def _servers_c(
    records: Vector[Record]
  ): Consequence[Vector[CodexMcpServerImportPolicy]] =
    records.foldLeft(Consequence.success(Vector.empty[CodexMcpServerImportPolicy])) {
      case (z, record) =>
        for {
          servers <- z
          sourcename <- _required_string_c(record, "sourceName")
          tools <- _required_strings_c(record, "admittedTools")
          credential <- _credential_c(record.getString("credentialReference"))
          server <- CodexMcpServerImportPolicy.createC(sourcename, tools, credential)
        } yield servers :+ server
    }

  private def _credential_c(
    reference: Option[String]
  ): Consequence[Option[McpStreamableHttpCredential]] =
    reference match {
      case None => Consequence.success(None)
      case Some(value) =>
        for {
          ref <- SecretReference.fromConfiguration(value)
          credential <- McpStreamableHttpCredential.bearerC(ref)
        } yield Some(credential)
    }

  private def _required_record_c(
    record: Record,
    name: String
  ): Consequence[Record] =
    record.getRecord(name) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(name)
    }

  private def _required_records_c(
    record: Record,
    name: String
  ): Consequence[Vector[Record]] =
    record.getVector(name) match {
      case Some(values) =>
        values.foldLeft(Consequence.success(Vector.empty[Record])) { case (z, value) =>
          for {
            records <- z
            entry <- value match {
              case rec: Record => Consequence.success(rec)
              case _ => Consequence.argumentFormatError(name, "record list", "invalid")
            }
          } yield records :+ entry
        }
      case None => Consequence.argumentMissing(name)
    }

  private def _required_string_c(
    record: Record,
    name: String
  ): Consequence[String] =
    record.getString(name).map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(name)
    }

  private def _required_strings_c(
    record: Record,
    name: String
  ): Consequence[Vector[String]] =
    record.getStringVector(name).map(_.map(_.trim).filter(_.nonEmpty)) match {
      case Some(values) if values.nonEmpty => Consequence.success(values)
      case _ => Consequence.argumentMissing(name)
    }
}
