package org.goldenport.cncf.mcp.client

import java.util.Locale

import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * Isolated import adapter for the supported Codex MCP definition subset.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class CodexMcpServerImportPolicy private (
  sourceName: String,
  admittedToolNames: Vector[McpToolName],
  credential: Option[McpStreamableHttpCredential]
)

private[cncf] object CodexMcpServerImportPolicy {
  def createC(
    sourcename: String,
    admittedtoolnames: Iterable[String],
    credential: Option[McpStreamableHttpCredential] = None
  ): Consequence[CodexMcpServerImportPolicy] = {
    val source = Option(sourcename).map(_.trim).getOrElse("")
    val names = admittedtoolnames.toVector
    if (source.isEmpty || source.length > 256 || source.exists(_.isControl))
      Consequence.argumentFormatError(
        "sourceName",
        "non-empty bounded Codex MCP source name without control characters",
        "invalid"
      )
    else if (names.isEmpty)
      Consequence.argumentPolicyViolation(
        "admittedToolNames",
        "mcp-client.codex-import",
        "non-empty exact CNCF tool allowlist",
        "empty"
      )
    else
      _tool_names_c(names).flatMap { tools =>
        if (tools.distinct.size != tools.size)
          Consequence.argumentPolicyViolation(
            "admittedToolNames",
            "mcp-client.codex-import",
            "unique exact CNCF tool allowlist entries",
            "duplicate"
          )
        else
          Consequence.success(CodexMcpServerImportPolicy(
            source,
            tools.sortBy(_.print),
            credential
          ))
      }
  }

  private def _tool_names_c(
    names: Vector[String]
  ): Consequence[Vector[McpToolName]] =
    names.foldLeft(Consequence.success(Vector.empty[McpToolName])) { case (z, name) =>
      for {
        tools <- z
        tool <- McpToolName.parseC(name)
      } yield tools :+ tool
    }
}

private[cncf] final case class CodexMcpImportPolicy private (
  serverSetId: McpServerSetId,
  servers: Vector[CodexMcpServerImportPolicy],
  limits: McpClientLimits
)

private[cncf] object CodexMcpImportPolicy {
  def createC(
    serversetid: McpServerSetId,
    servers: Vector[CodexMcpServerImportPolicy],
    limits: McpClientLimits
  ): Consequence[CodexMcpImportPolicy] = {
    val names = servers.map(_.sourceName)
    if (servers.isEmpty)
      Consequence.argumentMissing("servers")
    else if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation(
        "servers",
        "mcp-client.codex-import",
        "unique Codex MCP source names",
        "duplicate"
      )
    else
      Consequence.success(CodexMcpImportPolicy(
        serversetid,
        servers.sortBy(_.sourceName),
        limits
      ))
  }
}

private[cncf] final case class CodexMcpImportedServerSet(
  serverSet: McpClientServerSet,
  transportConfig: McpStreamableHttpServerSetConfig
)

private[cncf] object CodexMcpDefinitionImporter {
  private val _unsafe_key_fragments = Vector(
    "arg",
    "auth",
    "command",
    "credential",
    "env",
    "header",
    "password",
    "secret",
    "token"
  )
  private val _supported_transport_names = Set("http", "streamablehttp")

  def importC(
    source: Record,
    policy: CodexMcpImportPolicy
  ): Consequence[CodexMcpImportedServerSet] =
    for {
      definitions <- _definitions_c(source)
      imported <- _import_servers_c(definitions, policy.servers)
      serverset <- McpClientServerSet.createC(policy.serverSetId, imported.map(_._1), policy.limits)
      transportconfig <- McpStreamableHttpServerSetConfig.createC(policy.serverSetId, imported.map(_._2))
    } yield CodexMcpImportedServerSet(serverset, transportconfig)

  private def _definitions_c(
    source: Record
  ): Consequence[Map[String, Any]] =
    source.getAny("mcp_servers").orElse(source.getAny("mcpServers")) match {
      case Some(value) =>
        _record_c("mcp_servers", value).map(_.asMap)
      case None => Consequence.argumentMissing("mcp_servers")
    }

  private def _import_servers_c(
    definitions: Map[String, Any],
    policies: Vector[CodexMcpServerImportPolicy]
  ): Consequence[Vector[(McpClientServer, McpStreamableHttpServerConfig)]] =
    policies.foldLeft(Consequence.success(Vector.empty[(McpClientServer, McpStreamableHttpServerConfig)])) {
      case (z, policy) =>
        for {
          imported <- z
          source <- definitions.get(policy.sourceName) match {
            case Some(value) => Consequence.success(value)
            case None => Consequence.argumentPolicyViolation(
              "sourceName",
              "mcp-client.codex-import",
              "selected Codex MCP source definition",
              policy.sourceName
            )
          }
          definition <- _record_c(s"mcp_servers.${policy.sourceName}", source)
          pair <- _import_server_c(policy, definition)
        } yield imported :+ pair
    }.flatMap(_reject_identity_collisions_c)

  private def _import_server_c(
    policy: CodexMcpServerImportPolicy,
    definition: Record
  ): Consequence[(McpClientServer, McpStreamableHttpServerConfig)] =
    for {
      _ <- _validate_definition_c(policy.sourceName, definition)
      serverid <- _server_id_c(policy.sourceName)
      endpoint <- _url_c(policy.sourceName, definition)
      client <- McpClientServer.createC(serverid, policy.admittedToolNames)
      transport <- McpStreamableHttpServerConfig.createC(
        serverid,
        endpoint,
        credential = policy.credential
      )
    } yield client -> transport

  private def _validate_definition_c(
    sourcename: String,
    definition: Record
  ): Consequence[Unit] = {
    val unsafe = definition.asMap.keys.toVector.filter(_is_unsafe_key).sorted
    if (unsafe.nonEmpty)
      Consequence.argumentPolicyViolation(
        s"mcp_servers.$sourcename",
        "mcp-client.codex-import",
        "URL-only definition without command, environment, header, or embedded credential authority",
        unsafe.mkString(",")
      )
    else
      definition.asMap.collectFirst {
        case (key, value) if _normalized_key(key) == "transport" => value
        case (key, value) if _normalized_key(key) == "type" => value
      } match {
        case Some(value) if !_supported_transport_names.contains(_normalized_key(value.toString)) =>
          Consequence.argumentPolicyViolation(
            s"mcp_servers.$sourcename.transport",
            "mcp-client.codex-import",
            "Streamable HTTP transport",
            "unsupported"
          )
        case _ => Consequence.unit
      }
  }

  private def _url_c(
    sourcename: String,
    definition: Record
  ): Consequence[String] =
    definition.asMap.get("url") match {
      case Some(value: String) if value.trim.nonEmpty => Consequence.success(value.trim)
      case Some(_) => Consequence.argumentFormatError(
        s"mcp_servers.$sourcename.url",
        "non-empty HTTP(S) URL string",
        "invalid"
      )
      case None => Consequence.argumentMissing(s"mcp_servers.$sourcename.url")
    }

  private def _server_id_c(
    sourcename: String
  ): Consequence[McpServerId] = {
    val lowered = sourcename.trim.toLowerCase(Locale.ROOT)
    val separated = lowered.replaceAll("[^a-z0-9.]+", "-").replaceAll("-+", "-")
    val trimmed = separated.stripPrefix("-").stripSuffix("-")
    val normalized = trimmed.headOption match {
      case Some(value) if value.isLetter => trimmed
      case Some(_) => s"server-$trimmed"
      case None => ""
    }
    McpServerId.parseC(normalized)
  }

  private def _reject_identity_collisions_c(
    imported: Vector[(McpClientServer, McpStreamableHttpServerConfig)]
  ): Consequence[Vector[(McpClientServer, McpStreamableHttpServerConfig)]] = {
    val identities = imported.map(_._1.id)
    if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "servers",
        "mcp-client.codex-import",
        "unique normalized MCP server identities",
        "collision"
      )
    else
      Consequence.success(imported.sortBy(_._1.id.print))
  }

  private def _record_c(
    parameter: String,
    value: Any
  ): Consequence[Record] =
    value match {
      case record: Record => Consequence.success(record)
      case values: Map[?, ?] if values.keys.forall(_.isInstanceOf[String]) =>
        Consequence.success(Record.create(values.toVector.map { case (key, entry) => key.toString -> entry }))
      case values: java.util.Map[?, ?] if values.keySet().asScala.forall(_.isInstanceOf[String]) =>
        Consequence.success(Record.create(values.asScala.toVector.map { case (key, entry) => key.toString -> entry }))
      case _ => Consequence.argumentFormatError(parameter, "record", "invalid")
    }

  private def _is_unsafe_key(key: String): Boolean = {
    val normalized = _normalized_key(key)
    _unsafe_key_fragments.exists(normalized.contains)
  }

  private def _normalized_key(value: String): String =
    Option(value).getOrElse("").toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)
}
