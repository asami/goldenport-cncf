package org.goldenport.cncf.mcp.client

import java.net.URI
import java.time.Instant
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
  def parseC(value: String): Consequence[McpFieldName] = {
    val text = Option(value).getOrElse("")
    if (text.nonEmpty && text.length <= 256 && !text.exists(_.isControl))
      Consequence.success(McpFieldName(text))
    else
      Consequence.argumentFormatError("field", "non-empty bounded JSON field name without control characters", "invalid")
  }
}

final case class McpDisplayText private (value: String) {
  def print: String = value
}

object McpDisplayText {
  def parseC(value: String): Consequence[McpDisplayText] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val hasunsafecontrol = text.exists(x => x.isControl && x != '\n' && x != '\r' && x != '\t')
    if (text.isEmpty || text.length > 2048 || hasunsafecontrol)
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

  private[client] def validateValueC(
    schema: McpInputSchema,
    value: McpValue,
    fieldpath: String
  ): Consequence[Unit] =
    (schema, value) match {
      case (AnyValue, _) => Consequence.unit
      case (NullValue, McpValue.NullValue) => Consequence.unit
      case (StringValue, _: McpValue.StringValue) => Consequence.unit
      case (BooleanValue, _: McpValue.BooleanValue) => Consequence.unit
      case (IntegerValue, _: McpValue.IntegerValue) => Consequence.unit
      case (NumberValue, _: McpValue.NumberValue | _: McpValue.IntegerValue) => Consequence.unit
      case (ArrayValue(items), McpValue.ArrayValue(values)) =>
        values.zipWithIndex.foldLeft(Consequence.unit) { case (z, (child, index)) =>
          z.flatMap(_ => validateValueC(items, child, s"${fieldpath}/${index}"))
        }
      case (ObjectValue(fields, allowsadditionalfields), objectvalue: McpValue.ObjectValue) =>
        _validate_object_c(fields, allowsadditionalfields, objectvalue, fieldpath)
      case _ =>
        Consequence.argumentFieldInvalid(fieldpath, schema.kind.name, value.kind.name)
    }

  private def _validate_object_c(
    fields: Vector[McpInputField],
    allowsadditionalfields: Boolean,
    value: McpValue.ObjectValue,
    fieldpath: String
  ): Consequence[Unit] = {
    val inputfields = value.fields.toMap
    fields.find(x => x.required && !inputfields.contains(x.name)) match {
      case Some(field) =>
        Consequence.argumentFieldPolicyViolation(
          _json_pointer_child(fieldpath, field.name),
          "mcp-client.input-schema",
          "required field",
          "missing"
        )
      case None =>
        val declared = fields.map(_.name).toSet
        value.fields.find(x => !declared.contains(x._1) && !allowsadditionalfields) match {
          case Some((name, _)) =>
            Consequence.argumentFieldPolicyViolation(
              _json_pointer_child(fieldpath, name),
              "mcp-client.input-schema",
              "declared field",
              "additional field"
            )
          case None =>
            fields.foldLeft(Consequence.unit) { case (z, field) =>
              inputfields.get(field.name) match {
                case Some(child) => z.flatMap(_ => validateValueC(field.schema, child, _json_pointer_child(fieldpath, field.name)))
                case None => z
              }
            }
        }
    }
  }

  private def _json_pointer_child(
    parent: String,
    name: McpFieldName
  ): String = {
    val escaped = name.print.replace("~", "~0").replace("/", "~1")
    s"${parent}/${escaped}"
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

final case class McpClientServer private (
  id: McpServerId,
  admittedToolNames: Vector[McpToolName]
) {
  def admits(name: McpToolName): Boolean =
    admittedToolNames.contains(name)
}

object McpClientServer {
  def createC(
    id: McpServerId,
    admittedtoolnames: Iterable[McpToolName]
  ): Consequence[McpClientServer] = {
    val names = admittedtoolnames.toVector
    if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation(
        "admittedToolNames",
        "mcp-client.tool-allowlist",
        "unique tool identities",
        "duplicate"
      )
    else
      Consequence.success(McpClientServer(id, names.sortBy(_.print)))
  }
}

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
) {
  private[client] def validateArgumentsC(arguments: McpValue.ObjectValue): Consequence[Unit] =
    McpInputSchema.validateValueC(inputSchema, arguments, "/arguments")
}

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
    val servers = serverset.servers.map(x => x.id -> x).toMap
    val serverids = servers.keySet
    val admittedtools = tools.filter(x => servers.get(x.identity.serverId).exists(_.admits(x.identity.toolName)))
    val identities = admittedtools.map(_.identity)
    if (tools.exists(x => !serverids.contains(x.identity.serverId)))
      Consequence.argumentPolicyViolation(
        "tools",
        "mcp-client.catalog",
        "tool identities belonging to the bound server set",
        "foreign-server"
      )
    else if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "tools",
        "mcp-client.catalog",
        "unique admitted tool identities",
        "duplicate"
      )
    else
      Consequence.success(McpClientCatalog(serverset, admittedtools.sortBy(_.identity.print)))
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

final case class McpMimeType private (value: String) {
  def print: String = value
}

object McpMimeType {
  private val _pattern = "[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+".r

  def parseC(value: String): Consequence[McpMimeType] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpMimeType(text))
      case _ => Consequence.argumentFormatError("mimeType", "MIME media type", value)
    }
  }
}

final case class McpBase64Data private (value: String) {
  def print: String = value
}

object McpBase64Data {
  private val _pattern = "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?".r

  def parseC(value: String): Consequence[McpBase64Data] = {
    val text = Option(value).map(_.trim).getOrElse("")
    text match {
      case _pattern() => Consequence.success(McpBase64Data(text))
      case _ => Consequence.argumentFormatError("data", "base64 content", "invalid")
    }
  }
}

final case class McpResourceUri private (value: URI) {
  def print: String = value.toASCIIString
}

object McpResourceUri {
  def parseC(value: String): Consequence[McpResourceUri] =
    scala.util.Try(URI.create(Option(value).map(_.trim).getOrElse(""))).toOption match {
      case Some(uri) if uri.isAbsolute && !uri.toASCIIString.exists(_.isControl) =>
        Consequence.success(McpResourceUri(uri))
      case _ => Consequence.argumentFormatError("uri", "absolute resource URI", value)
    }
}

enum McpContentRole(val name: String) {
  case User extends McpContentRole("user")
  case Assistant extends McpContentRole("assistant")
}

object McpContentRole {
  def parseC(value: String): Consequence[McpContentRole] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("user") => Consequence.success(McpContentRole.User)
      case Some("assistant") => Consequence.success(McpContentRole.Assistant)
      case _ => Consequence.argumentFormatError("audience", "user or assistant", value)
    }
}

final case class McpContentAnnotations private (
  audience: Set[McpContentRole],
  priority: Option[BigDecimal],
  lastModified: Option[Instant]
)

object McpContentAnnotations {
  def createC(
    audience: Set[McpContentRole] = Set.empty,
    priority: Option[BigDecimal] = None,
    lastmodified: Option[Instant] = None
  ): Consequence[McpContentAnnotations] =
    priority match {
      case Some(value) if value < 0 || value > 1 =>
        Consequence.argumentInvalid("priority", "number from 0 through 1", value)
      case _ => Consequence.success(McpContentAnnotations(audience, priority, lastmodified))
    }
}

sealed abstract class McpClientContent {
  def annotations: Option[McpContentAnnotations]
}

object McpClientContent {
  final case class Text(
    value: String,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class Image(
    data: McpBase64Data,
    mimeType: McpMimeType,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class Audio(
    data: McpBase64Data,
    mimeType: McpMimeType,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class ResourceLink(
    uri: McpResourceUri,
    name: McpDisplayText,
    title: Option[McpDisplayText] = None,
    description: Option[McpDisplayText] = None,
    mimeType: Option[McpMimeType] = None,
    size: Option[Long] = None,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class EmbeddedTextResource(
    uri: McpResourceUri,
    text: String,
    mimeType: Option[McpMimeType] = None,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class EmbeddedBlobResource(
    uri: McpResourceUri,
    blob: McpBase64Data,
    mimeType: Option[McpMimeType] = None,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
  final case class Structured(
    value: McpValue,
    annotations: Option[McpContentAnnotations] = None
  ) extends McpClientContent
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
