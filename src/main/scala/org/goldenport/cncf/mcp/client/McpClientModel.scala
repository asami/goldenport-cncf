package org.goldenport.cncf.mcp.client

import java.util.Locale

import org.goldenport.Consequence

/*
 * Provider-neutral values crossing the CNCF MCP client Port.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class McpServerSetId private (value: String) {
  def print: String = value
}

object McpServerSetId {
  private val _pattern = "[a-z][a-z0-9.-]{0,127}".r

  def parseC(value: String): Consequence[McpServerSetId] =
    _logical_id_c("serverSet", value).map(McpServerSetId.apply)

  private def _logical_id_c(
    parameter: String,
    value: String
  ): Consequence[String] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(text)
      case _ => Consequence.argumentFormatError(parameter, "lowercase logical MCP identity", value)
    }
  }
}

final case class McpServerId private (value: String) {
  def print: String = value
}

object McpServerId {
  private val _pattern = "[a-z][a-z0-9.-]{0,127}".r

  def parseC(value: String): Consequence[McpServerId] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpServerId(text))
      case _ => Consequence.argumentFormatError("server", "lowercase logical MCP server identity", value)
    }
  }
}

final case class McpToolName private (value: String) {
  def print: String = value
}

object McpToolName {
  private val _pattern = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}".r

  def parseC(value: String): Consequence[McpToolName] = {
    val text = Option(value).map(_.trim).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpToolName(text))
      case _ => Consequence.argumentFormatError("tool", "bounded logical MCP tool name", value)
    }
  }
}

final case class McpToolIdentity(
  serverId: McpServerId,
  toolName: McpToolName
) {
  def print: String = s"${serverId.print}/${toolName.print}"
}

final case class McpFieldName private (value: String) {
  def print: String = value
}

object McpFieldName {
  private val _pattern = "[A-Za-z_][A-Za-z0-9_.-]{0,127}".r

  def parseC(value: String): Consequence[McpFieldName] = {
    val text = Option(value).map(_.trim).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpFieldName(text))
      case _ => Consequence.argumentFormatError("field", "bounded MCP field name", value)
    }
  }
}

final case class McpDisplayText private (value: String) {
  def print: String = value
}

object McpDisplayText {
  def parseC(value: String): Consequence[McpDisplayText] = {
    val text = Option(value).map(_.trim).getOrElse("")
    if (text.isEmpty || text.length > 2048 || text.exists(_.isControl))
      Consequence.argumentFormatError("displayText", "non-empty bounded display text", "invalid")
    else
      Consequence.success(McpDisplayText(text))
  }
}

enum McpValueKind(val name: String) {
  case AnyValue extends McpValueKind("any")
  case NullValue extends McpValueKind("null")
  case StringValue extends McpValueKind("string")
  case BooleanValue extends McpValueKind("boolean")
  case IntegerValue extends McpValueKind("integer")
  case NumberValue extends McpValueKind("number")
  case ArrayValue extends McpValueKind("array")
  case ObjectValue extends McpValueKind("object")
}

sealed abstract class McpInputSchema {
  def kind: McpValueKind
}

object McpInputSchema {
  case object AnyValue extends McpInputSchema {
    val kind = McpValueKind.AnyValue
  }
  case object NullValue extends McpInputSchema {
    val kind = McpValueKind.NullValue
  }
  case object StringValue extends McpInputSchema {
    val kind = McpValueKind.StringValue
  }
  case object BooleanValue extends McpInputSchema {
    val kind = McpValueKind.BooleanValue
  }
  case object IntegerValue extends McpInputSchema {
    val kind = McpValueKind.IntegerValue
  }
  case object NumberValue extends McpInputSchema {
    val kind = McpValueKind.NumberValue
  }
  final case class ArrayValue private[client] (
    items: McpInputSchema
  ) extends McpInputSchema {
    val kind = McpValueKind.ArrayValue
  }
  final case class ObjectValue private[client] (
    fields: Vector[McpInputField],
    allowsAdditionalFields: Boolean
  ) extends McpInputSchema {
    val kind = McpValueKind.ObjectValue
  }

  def array(items: McpInputSchema): McpInputSchema =
    ArrayValue(items)

  def objectC(
    fields: Vector[McpInputField],
    allowsadditionalfields: Boolean = false
  ): Consequence[McpInputSchema] = {
    val names = fields.map(_.name)
    if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation(
        "fields",
        "mcp-client.schema",
        "unique field names",
        "duplicate"
      )
    else
      Consequence.success(ObjectValue(fields.sortBy(_.name.print), allowsadditionalfields))
  }
}

final case class McpInputField private (
  name: McpFieldName,
  schema: McpInputSchema,
  required: Boolean,
  description: Option[McpDisplayText]
)

object McpInputField {
  def createC(
    name: McpFieldName,
    schema: McpInputSchema,
    required: Boolean = false,
    description: Option[McpDisplayText] = None
  ): Consequence[McpInputField] =
    Consequence.success(McpInputField(name, schema, required, description))
}

sealed abstract class McpValue {
  def kind: McpValueKind
}

object McpValue {
  case object NullValue extends McpValue {
    val kind = McpValueKind.NullValue
  }
  final case class StringValue(value: String) extends McpValue {
    val kind = McpValueKind.StringValue
  }
  final case class BooleanValue(value: Boolean) extends McpValue {
    val kind = McpValueKind.BooleanValue
  }
  final case class IntegerValue(value: BigInt) extends McpValue {
    val kind = McpValueKind.IntegerValue
  }
  final case class NumberValue(value: BigDecimal) extends McpValue {
    val kind = McpValueKind.NumberValue
  }
  final case class ArrayValue(values: Vector[McpValue]) extends McpValue {
    val kind = McpValueKind.ArrayValue
  }
  final case class ObjectValue private[client] (
    fields: Vector[(McpFieldName, McpValue)]
  ) extends McpValue {
    val kind = McpValueKind.ObjectValue

    def get(name: McpFieldName): Option[McpValue] =
      fields.find(_._1 == name).map(_._2)
  }

  def objectC(
    fields: Vector[(McpFieldName, McpValue)]
  ): Consequence[ObjectValue] = {
    val names = fields.map(_._1)
    if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation(
        "fields",
        "mcp-client.value",
        "unique field names",
        "duplicate"
      )
    else
      Consequence.success(ObjectValue(fields.sortBy(_._1.print)))
  }
}

final case class McpClientLimits private (
  timeoutMillis: Long,
  maximumCalls: Int,
  maximumInputBytes: Long,
  maximumOutputBytes: Long,
  maximumConcurrency: Int
)

object McpClientLimits {
  val DEFAULT_TIMEOUT_MILLIS = 30000L
  val DEFAULT_MAXIMUM_CALLS = 32
  val DEFAULT_MAXIMUM_INPUT_BYTES = 1024L * 1024L
  val DEFAULT_MAXIMUM_OUTPUT_BYTES = 8L * 1024L * 1024L
  val DEFAULT_MAXIMUM_CONCURRENCY = 4

  def default: McpClientLimits =
    McpClientLimits(
      DEFAULT_TIMEOUT_MILLIS,
      DEFAULT_MAXIMUM_CALLS,
      DEFAULT_MAXIMUM_INPUT_BYTES,
      DEFAULT_MAXIMUM_OUTPUT_BYTES,
      DEFAULT_MAXIMUM_CONCURRENCY
    )

  def createC(
    timeoutmillis: Long,
    maximumcalls: Int,
    maximuminputbytes: Long,
    maximumoutputbytes: Long,
    maximumconcurrency: Int
  ): Consequence[McpClientLimits] = {
    val values = Vector(
      "timeoutMillis" -> timeoutmillis,
      "maximumCalls" -> maximumcalls.toLong,
      "maximumInputBytes" -> maximuminputbytes,
      "maximumOutputBytes" -> maximumoutputbytes,
      "maximumConcurrency" -> maximumconcurrency.toLong
    )
    values.find(_._2 <= 0L) match {
      case Some((name, value)) =>
        Consequence.argumentLimitExceeded(name, 1L, value, "mcp-client.limits")
      case None =>
        Consequence.success(McpClientLimits(
          timeoutmillis,
          maximumcalls,
          maximuminputbytes,
          maximumoutputbytes,
          maximumconcurrency
        ))
    }
  }
}

final case class McpClientServer(
  id: McpServerId
)

final case class McpClientServerSet private (
  id: McpServerSetId,
  servers: Vector[McpClientServer],
  limits: McpClientLimits
)

object McpClientServerSet {
  def createC(
    id: McpServerSetId,
    servers: Vector[McpClientServer],
    limits: McpClientLimits = McpClientLimits.default
  ): Consequence[McpClientServerSet] =
    if (servers.isEmpty)
      Consequence.argumentMissing("servers")
    else if (servers.map(_.id).distinct.size != servers.size)
      Consequence.argumentPolicyViolation(
        "servers",
        "mcp-client.server-set",
        "unique server identities",
        "duplicate"
      )
    else
      Consequence.success(McpClientServerSet(id, servers.sortBy(_.id.print), limits))
}

final case class McpClientTool private (
  identity: McpToolIdentity,
  title: Option[McpDisplayText],
  description: Option[McpDisplayText],
  inputSchema: McpInputSchema
)

object McpClientTool {
  def createC(
    identity: McpToolIdentity,
    inputSchema: McpInputSchema,
    title: Option[McpDisplayText] = None,
    description: Option[McpDisplayText] = None
  ): Consequence[McpClientTool] =
    Consequence.success(McpClientTool(identity, title, description, inputSchema))
}

final case class McpClientCatalog private (
  serverSet: McpClientServerSet,
  tools: Vector[McpClientTool]
) {
  def tool(identity: McpToolIdentity): Option[McpClientTool] =
    tools.find(_.identity == identity)
}

object McpClientCatalog {
  def createC(
    serverset: McpClientServerSet,
    tools: Vector[McpClientTool]
  ): Consequence[McpClientCatalog] = {
    val identities = tools.map(_.identity)
    val serverids = serverset.servers.map(_.id).toSet
    if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "tools",
        "mcp-client.catalog",
        "unique admitted tool identities",
        "duplicate"
      )
    else if (identities.exists(x => !serverids.contains(x.serverId)))
      Consequence.argumentPolicyViolation(
        "tools",
        "mcp-client.catalog",
        "tool identities belonging to the bound server set",
        "foreign-server"
      )
    else
      Consequence.success(McpClientCatalog(serverset, tools.sortBy(_.identity.print)))
  }
}

final case class McpClientCall private (
  toolIdentity: McpToolIdentity,
  arguments: McpValue.ObjectValue
)

object McpClientCall {
  def createC(
    toolidentity: McpToolIdentity,
    arguments: McpValue.ObjectValue
  ): Consequence[McpClientCall] =
    Consequence.success(McpClientCall(toolidentity, arguments))
}

sealed abstract class McpClientContent

object McpClientContent {
  final case class Text(value: String) extends McpClientContent
  final case class Structured(value: McpValue) extends McpClientContent
}

final case class McpClientResult(
  content: Vector[McpClientContent],
  structuredContent: Option[McpValue]
)

enum McpClientDiagnosticKind(val name: String) {
  case Admission extends McpClientDiagnosticKind("admission")
  case Validation extends McpClientDiagnosticKind("validation")
  case Limit extends McpClientDiagnosticKind("limit")
  case Transport extends McpClientDiagnosticKind("transport")
  case Protocol extends McpClientDiagnosticKind("protocol")
  case RemoteTool extends McpClientDiagnosticKind("remote-tool")
}

final case class McpClientDiagnosticReason private (value: String) {
  def print: String = value
}

object McpClientDiagnosticReason {
  private val _pattern = "[a-z][a-z0-9.-]{0,127}".r

  def parseC(value: String): Consequence[McpClientDiagnosticReason] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpClientDiagnosticReason(text))
      case _ => Consequence.argumentFormatError("diagnosticReason", "bounded logical reason", value)
    }
  }
}

final case class McpClientDiagnosticSummary private (value: String) {
  def print: String = value
}

object McpClientDiagnosticSummary {
  def fromSafeTextC(value: String): Consequence[McpClientDiagnosticSummary] = {
    val text = Option(value).map(_.trim).getOrElse("")
    if (text.isEmpty || text.length > 256 || text.exists(_.isControl))
      Consequence.argumentFormatError(
        "diagnosticSummary",
        "pre-redacted bounded diagnostic summary",
        "invalid"
      )
    else
      Consequence.success(McpClientDiagnosticSummary(text))
  }
}

final case class McpClientDiagnostic(
  kind: McpClientDiagnosticKind,
  reason: McpClientDiagnosticReason,
  summary: Option[McpClientDiagnosticSummary]
)
