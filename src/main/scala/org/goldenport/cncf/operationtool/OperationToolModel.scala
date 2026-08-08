package org.goldenport.cncf.operationtool

import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.datatype.I18nString
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, XBoolean, XDouble, XFloat, XInt, XInteger, XLong}

/*
 * Provider-neutral CNCF Operation tool values.
 *
 * @since   Jul. 21, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationToolSetId private (value: String) {
  def print: String = value
}

object OperationToolSetId {
  private val _pattern = "[a-z][a-z0-9.-]{0,127}".r

  def parseC(value: String): Consequence[OperationToolSetId] = {
    val text = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(OperationToolSetId(text))
      case _ => Consequence.argumentFormatError("toolSet", "lowercase logical Operation tool-set identity", value)
    }
  }
}

final case class OperationToolIdentity private (
  component: String,
  service: String,
  operation: String
) {
  def print: String = s"$component.$service.$operation"
}

object OperationToolIdentity {
  private val _segment_pattern = "[A-Za-z0-9][A-Za-z0-9_-]{0,127}".r

  def parseC(value: String): Consequence[OperationToolIdentity] =
    Option(value).map(_.trim).getOrElse("").split("\\.", -1).toVector match {
      case Vector(component, service, operation) =>
        createC(component, service, operation)
      case _ =>
        Consequence.argumentFormatError(
          "tool",
          "exact component.service.operation identity",
          value
        )
    }

  def createC(
    component: String,
    service: String,
    operation: String
  ): Consequence[OperationToolIdentity] =
    _segment_c("component", component).flatMap { c =>
      _segment_c("service", service).flatMap { s =>
        _segment_c("operation", operation).map(OperationToolIdentity(c, s, _))
      }
    }

  private def _segment_c(
    parameter: String,
    value: String
  ): Consequence[String] =
    Option(value).map(_.trim).getOrElse("") match {
      case text @ _segment_pattern() => Consequence.success(text)
      case _ => Consequence.argumentFormatError(parameter, "bounded Operation identity segment", value)
    }
}

enum OperationToolValueKind(val name: String) {
  case AnyValue extends OperationToolValueKind("any")
  case StringValue extends OperationToolValueKind("string")
  case BooleanValue extends OperationToolValueKind("boolean")
  case IntegerValue extends OperationToolValueKind("integer")
  case NumberValue extends OperationToolValueKind("number")
  case ArrayValue extends OperationToolValueKind("array")
  case ObjectValue extends OperationToolValueKind("object")
}

sealed abstract class OperationToolInputSchema {
  def kind: OperationToolValueKind
}

object OperationToolInputSchema {
  case object AnyValue extends OperationToolInputSchema {
    val kind = OperationToolValueKind.AnyValue
  }
  case object StringValue extends OperationToolInputSchema {
    val kind = OperationToolValueKind.StringValue
  }
  case object BooleanValue extends OperationToolInputSchema {
    val kind = OperationToolValueKind.BooleanValue
  }
  case object IntegerValue extends OperationToolInputSchema {
    val kind = OperationToolValueKind.IntegerValue
  }
  case object NumberValue extends OperationToolInputSchema {
    val kind = OperationToolValueKind.NumberValue
  }
  final case class ArrayValue(items: OperationToolInputSchema) extends OperationToolInputSchema {
    val kind = OperationToolValueKind.ArrayValue
  }
  final case class ObjectValue(fields: Vector[OperationToolInputField]) extends OperationToolInputSchema {
    val kind = OperationToolValueKind.ObjectValue
  }
}

final case class OperationToolInputField(
  name: String,
  schema: OperationToolInputSchema,
  required: Boolean
)

final case class OperationToolDefinition(
  identity: OperationToolIdentity,
  description: String,
  inputSchema: OperationToolInputSchema.ObjectValue
) {
  def validateArgumentsC(arguments: Record): Consequence[Unit] = {
    val inputnames = arguments.fields.map(_.key)
    val declared = inputSchema.fields.map(_.name).toSet
    if (inputnames.distinct.size != inputnames.size)
      Consequence.argumentPolicyViolation(
        "arguments",
        "operation-tool.input",
        "unique argument fields",
        "duplicate"
      )
    else
      inputnames.find(!declared.contains(_)) match {
        case Some(name) =>
          Consequence.argumentFieldPolicyViolation(
            s"/arguments/${_json_pointer_segment(name)}",
            "operation-tool.input",
            "declared operation parameter",
            "additional field"
          )
        case None =>
          inputSchema.fields.find(field => field.required && !inputnames.contains(field.name)) match {
            case Some(field) =>
              Consequence.argumentFieldPolicyViolation(
                s"/arguments/${_json_pointer_segment(field.name)}",
                "operation-tool.input",
                "required operation parameter",
                "missing"
              )
            case None => Consequence.unit
          }
      }
  }

  private def _json_pointer_segment(value: String): String =
    value.replace("~", "~0").replace("/", "~1")
}

final case class OperationToolCall(
  identity: OperationToolIdentity,
  arguments: Record
)

final case class OperationToolResult(response: OperationResponse)

final case class OperationToolCatalog private[operationtool] (
  toolSetId: OperationToolSetId,
  definitions: Vector[OperationToolDefinition]
) {
  private val _by_identity = definitions.map(x => x.identity -> x).toMap

  def definition(identity: OperationToolIdentity): Option[OperationToolDefinition] =
    _by_identity.get(identity)
}

final case class OperationToolLimits private (
  maximumCalls: Int,
  maximumInputBytes: Long,
  maximumResultBytes: Long,
  maximumConcurrency: Int
)

object OperationToolLimits {
  def createC(
    maximumcalls: Int,
    maximuminputbytes: Long,
    maximumresultbytes: Long,
    maximumconcurrency: Int
  ): Consequence[OperationToolLimits] = {
    val values = Vector(
      "maximumCalls" -> maximumcalls.toLong,
      "maximumInputBytes" -> maximuminputbytes,
      "maximumResultBytes" -> maximumresultbytes,
      "maximumConcurrency" -> maximumconcurrency.toLong
    )
    values.find(_._2 <= 0L) match {
      case Some((name, actual)) =>
        Consequence.argumentLimitExceeded(name, 1L, actual, "operation-tool.limits")
      case None =>
        Consequence.success(OperationToolLimits(
          maximumcalls,
          maximuminputbytes,
          maximumresultbytes,
          maximumconcurrency
        ))
    }
  }
}

final case class OperationToolAdmission private (
  toolSetId: OperationToolSetId,
  identities: Vector[OperationToolIdentity],
  limits: OperationToolLimits
)

object OperationToolAdmission {
  def createC(
    toolsetid: OperationToolSetId,
    identities: Vector[OperationToolIdentity],
    limits: OperationToolLimits
  ): Consequence[OperationToolAdmission] = {
    val ordered = identities.sortBy(_.print)
    if (ordered.distinct.size != ordered.size)
      Consequence.argumentPolicyViolation(
        "identities",
        "operation-tool.admission",
        "unique exact Operation identities",
        "duplicate"
      )
    else
      Consequence.success(OperationToolAdmission(toolsetid, ordered, limits))
  }
}

object OperationToolDefinitionBuilder {
  def definitionC(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Consequence[OperationToolDefinition] =
    _definition_c(component.displayName, Some(component), service, operation)

  def definitionC(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Consequence[OperationToolDefinition] =
    _definition_c(componentname, None, service, operation)

  private def _definition_c(
    componentname: String,
    component: Option[Component],
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Consequence[OperationToolDefinition] =
    OperationToolIdentity.createC(componentname, service.name, operation.name).map { identity =>
      OperationToolDefinition(
        identity,
        _description(component, componentname, service, operation),
        inputSchema(operation.specification.request.parameters.toVector)
      )
    }

  def inputSchema(parameters: Vector[ParameterDefinition]): OperationToolInputSchema.ObjectValue = {
    val fields = parameters
      .groupBy(_.name)
      .toVector
      .sortBy(_._1)
      .map(_._2.head)
      .map(inputField)
    OperationToolInputSchema.ObjectValue(fields)
  }

  def inputField(parameter: ParameterDefinition): OperationToolInputField = {
    val scalar = parameter.datatype match {
      case XBoolean => OperationToolInputSchema.BooleanValue
      case XInt | XInteger | XLong => OperationToolInputSchema.IntegerValue
      case XFloat | XDouble => OperationToolInputSchema.NumberValue
      case datatype =>
        datatype.name.toLowerCase(Locale.ROOT) match {
          case "boolean" | "bool" => OperationToolInputSchema.BooleanValue
          case "int" | "integer" | "long" | "short" => OperationToolInputSchema.IntegerValue
          case "float" | "double" | "decimal" | "number" => OperationToolInputSchema.NumberValue
          case _ => OperationToolInputSchema.StringValue
        }
    }
    val schema = parameter.multiplicity match {
      case Multiplicity.OneMore | Multiplicity.ZeroMore => OperationToolInputSchema.ArrayValue(scalar)
      case _ => scalar
    }
    OperationToolInputField(
      parameter.name,
      schema,
      parameter.multiplicity == Multiplicity.One || parameter.multiplicity == Multiplicity.OneMore
    )
  }

  private def _description(
    component: Option[Component],
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.summary)
      .orElse(_trim_i18n(operation.specification.description))
      .orElse(component.flatMap(_component_operation_summary(_, operation.name)))
      .orElse(_trim_i18n(service.specification.summary))
      .orElse(_trim_i18n(service.specification.description))
      .getOrElse(s"$componentname.${service.name}.${operation.name}")

  private def _trim_i18n(value: Option[I18nString]): Option[String] =
    value.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _component_operation_summary(
    component: Component,
    operationname: String
  ): Option[String] = {
    val target = _normalized_name(operationname)
    val summaries = component.operationDefinitions
      .filter(x => _normalized_name(x.name) == target)
      .flatMap(_.summary.map(_.trim).filter(_.nonEmpty))
      .distinct
    summaries match {
      case Vector(summary) => Some(summary)
      case _ => None
    }
  }

  private def _normalized_name(value: String): String =
    Option(value).getOrElse("").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
}
