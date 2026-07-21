package org.goldenport.cncf.component.builtin.tool

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, FunctionalActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent

/*
 * Deterministic provider-neutral builtin Operations.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ToolComponent() extends Component

object ToolComponent {
  val name: String = "tool"
  val componentId: ComponentId = ComponentId(name)

  private val MAX_DECIMAL_TEXT_LENGTH = 128
  private val MAX_DECIMAL_PRECISION = 128
  private val MAX_TIMEZONE_LENGTH = 128
  private val DECIMAL_PATTERN = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)".r

  private enum DecimalOperator(val name: String) {
    case Add extends DecimalOperator("add")
    case Subtract extends DecimalOperator("subtract")
    case Multiply extends DecimalOperator("multiply")
  }

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component = {
      val _ = params
      val component = ToolComponent()
      component.withMcpReadyServices(Set("time", "decimal"))
    }

    protected def create_Core(
      params: ComponentCreate,
      component: Component
    ): Component.Core = {
      val _ = params
      val _ = component
      val recordresponse = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      val timeservice = spec.ServiceDefinition(
        name = "time",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.one(new TimeNowOperationDefinition(recordresponse))
        )
      )
      val decimalservice = spec.ServiceDefinition(
        name = "decimal",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.one(new DecimalCalculateOperationDefinition(recordresponse))
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(timeservice, decimalservice)),
        handler = ProtocolHandler.default
      )
      Component.Core.create(
        name,
        componentId,
        ComponentInstanceId.default(componentId),
        protocol
      )
    }
  }

  private final class TimeNowOperationDefinition(
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("now")
          .summary("Read the deterministic CNCF runtime clock.")
          .description("Return one runtime-clock instant rendered in an optional bounded IANA timezone.")
          .build(),
        request = spec.RequestDefinition(parameters = List(_optional_property("timezone"))),
        response = response
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      _optional_timezone_c(request, "timezone").map(TimeNowAction(request, _))
  }

  private final class DecimalCalculateOperationDefinition(
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("calculate")
          .summary("Calculate with bounded exact decimals.")
          .description("Apply add, subtract, or multiply to two exact plain-decimal strings without expression evaluation.")
          .build(),
        request = spec.RequestDefinition(parameters = List(
          _required_property("operator"),
          _required_property("left"),
          _required_property("right")
        )),
        response = response
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      for {
        operator <- _required_string(request, "operator").flatMap(_operator_c)
        left <- _required_string(request, "left").flatMap(_decimal_c("left", _))
        right <- _required_string(request, "right").flatMap(_decimal_c("right", _))
      } yield DecimalCalculateAction(request, operator, left, right)
  }

  private final case class TimeNowAction(
    request: Request,
    timezone: Option[ZoneId]
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      TimeNowActionCall(core, timezone)
  }

  private final case class DecimalCalculateAction(
    request: Request,
    operator: DecimalOperator,
    left: BigDecimal,
    right: BigDecimal
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      DecimalCalculateActionCall(core, operator, left, right)
  }

  private final case class TimeNowActionCall(
    core: ActionCall.Core,
    timezone: Option[ZoneId]
  ) extends FunctionalActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val instant = current_instant
      val zone = timezone.getOrElse(execution_context.timezone)
      exec_pure(OperationResponse.RecordResponse(_time_record(instant, zone)))
    }
  }

  private final case class DecimalCalculateActionCall(
    core: ActionCall.Core,
    operator: DecimalOperator,
    left: BigDecimal,
    right: BigDecimal
  ) extends FunctionalActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val result = operator match {
        case DecimalOperator.Add => left + right
        case DecimalOperator.Subtract => left - right
        case DecimalOperator.Multiply => left * right
      }
      exec_pure(OperationResponse.RecordResponse(Record.dataAuto(
        "operator" -> operator.name,
        "left" -> _decimal_text(left),
        "right" -> _decimal_text(right),
        "value" -> _decimal_text(result)
      )))
    }
  }

  private def _time_record(instant: Instant, timezone: ZoneId): Record = {
    val zoned = ZonedDateTime.ofInstant(instant, timezone)
    Record.dataAuto(
      "instant" -> DateTimeFormatter.ISO_INSTANT.format(instant),
      "epochMillis" -> instant.toEpochMilli,
      "timezone" -> timezone.getId,
      "zonedDateTime" -> DateTimeFormatter.ISO_ZONED_DATE_TIME.format(zoned)
    )
  }

  private def _operator_c(value: String): Consequence[DecimalOperator] = {
    val normalized = value.trim.toLowerCase(Locale.ROOT)
    DecimalOperator.values.find(_.name == normalized) match {
      case Some(operator) => Consequence.success(operator)
      case None =>
        Consequence.argumentExpectedActualMismatch(
          "operator",
          "add, subtract, or multiply",
          value
        )
    }
  }

  private def _decimal_c(name: String, value: String): Consequence[BigDecimal] = {
    val normalized = value.trim
    if (normalized.length > MAX_DECIMAL_TEXT_LENGTH)
      Consequence.argumentLimitExceeded(
        name,
        MAX_DECIMAL_TEXT_LENGTH,
        normalized.length,
        "tool.decimal.input-length"
      )
    else if (!DECIMAL_PATTERN.matches(normalized))
      Consequence.argumentFormatError(name, "plain base-10 decimal string", value)
    else
      scala.util.Try(BigDecimal(normalized)).toOption match {
        case Some(decimal) if decimal.precision <= MAX_DECIMAL_PRECISION =>
          Consequence.success(decimal)
        case Some(decimal) =>
          Consequence.argumentLimitExceeded(
            name,
            MAX_DECIMAL_PRECISION,
            decimal.precision,
            "tool.decimal.precision"
          )
        case None =>
          Consequence.argumentFormatError(name, "plain base-10 decimal string", value)
      }
  }

  private def _decimal_text(value: BigDecimal): String =
    value.bigDecimal.stripTrailingZeros.toPlainString

  private def _required_string(request: Request, name: String): Consequence[String] =
    _property_value(request, name) match {
      case Some(value: String) if value.trim.nonEmpty => Consequence.success(value)
      case Some(value) => Consequence.argumentExpectedActualMismatch(name, "non-empty string", value)
      case None => Consequence.argumentMissing(name)
    }

  private def _optional_timezone_c(request: Request, name: String): Consequence[Option[ZoneId]] =
    _property_value(request, name) match {
      case Some(value: String) if value.trim.nonEmpty => _timezone_c(name, value).map(Some(_))
      case Some(_: String) | None => Consequence.success(None)
      case Some(value) => Consequence.argumentExpectedActualMismatch(name, "IANA timezone string", value)
    }

  private def _timezone_c(name: String, value: String): Consequence[ZoneId] = {
    val normalized = value.trim
    if (normalized.length > MAX_TIMEZONE_LENGTH)
      Consequence.argumentLimitExceeded(
        name,
        MAX_TIMEZONE_LENGTH,
        normalized.length,
        "tool.time.timezone-length"
      )
    else if (
      normalized == "UTC" ||
      (normalized.contains("/") && ZoneId.getAvailableZoneIds.contains(normalized))
    )
      Consequence.success(ZoneId.of(normalized))
    else
      Consequence.argumentFormatError(name, "UTC or registered IANA region timezone", value)
  }

  private def _property_value(request: Request, name: String): Option[Any] =
    request.properties.find(_.name == name).map(_.value)
      .orElse(request.arguments.find(_.name == name).map(_.value))

  private def _required_property(name: String): spec.ParameterDefinition =
    _property(name, Multiplicity.One)

  private def _optional_property(name: String): spec.ParameterDefinition =
    _property(name, Multiplicity.ZeroOne)

  private def _property(name: String, multiplicity: Multiplicity): spec.ParameterDefinition =
    spec.ParameterDefinition(
      content = BaseContent.simple(name),
      kind = spec.ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = multiplicity)
    )
}
