package org.goldenport.cncf.component.builtin.tool

import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import cats.data.NonEmptyVector
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, FunctionalActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.resource.{ResourceContent, ResourceReference, WebTargetAdmission}
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

  private val _max_decimal_text_length = 128
  private val _max_decimal_precision = 128
  private val _max_resource_byte_size = 1024 * 1024
  private val _max_resource_reference_length = 2048
  private val _max_timezone_length = 128
  private val _decimal_pattern = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)".r

  private enum DecimalOperator(val name: String) {
    case Add extends DecimalOperator("add")
    case Subtract extends DecimalOperator("subtract")
    case Multiply extends DecimalOperator("multiply")
  }

  private enum WebReadMode(val name: String, val includeText: Boolean) {
    case Fetch extends WebReadMode("fetch", includeText = true)
    case Head extends WebReadMode("head", includeText = false)
  }

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component = {
      val _ = params
      val component = ToolComponent()
      component.withMcpReadyServices(Set("resource", "web", "time", "decimal"))
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
      val resourceservice = spec.ServiceDefinition(
        name = "resource",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.one(new ResourceReadOperationDefinition(recordresponse))
        )
      )
      val webservice = spec.ServiceDefinition(
        name = "web",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            new WebReadOperationDefinition(WebReadMode.Fetch, recordresponse),
            new WebReadOperationDefinition(WebReadMode.Head, recordresponse)
          )
        )
      )
      val decimalservice = spec.ServiceDefinition(
        name = "decimal",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.one(new DecimalCalculateOperationDefinition(recordresponse))
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          services = Vector(resourceservice, webservice, timeservice, decimalservice)
        ),
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

  private final class WebReadOperationDefinition(
    mode: WebReadMode,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder(mode.name)
          .summary(s"${mode.name.capitalize} one policy-admitted static Web resource.")
          .description("Read an HTTPS URL through the configured ResourceAccess host policy without redirects.")
          .build(),
        request = spec.RequestDefinition(parameters = List(_required_property("url"))),
        response = response
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      _required_string(request, "url")
        .flatMap(_bounded_resource_reference_c)
        .flatMap(ResourceReference.parseC)
        .map(WebReadAction(request, mode, _))
  }

  private final class ResourceReadOperationDefinition(
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("read")
          .summary("Read one bounded logical text resource.")
          .description("Resolve an absolute URL or URN only through the runtime ResourceAccess policy.")
          .build(),
        request = spec.RequestDefinition(parameters = List(_required_property("reference"))),
        response = response
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      _required_string(request, "reference")
        .flatMap(_bounded_resource_reference_c)
        .flatMap(ResourceReference.parseC)
        .map(ResourceReadAction(request, _))
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

  private final case class ResourceReadAction(
    request: Request,
    reference: ResourceReference
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ResourceReadActionCall(core, reference)
  }

  private final case class WebReadAction(
    request: Request,
    mode: WebReadMode,
    reference: ResourceReference
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      WebReadActionCall(core, mode, reference)
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

  private final case class ResourceReadActionCall(
    core: ActionCall.Core,
    reference: ResourceReference
  ) extends FunctionalActionCall {
    protected def build_Program: ExecUowM[OperationResponse] =
      for {
        content <- exec_from(read_resource(reference))
        _ <- exec_from(_resource_size_c(content))
        text <- exec_from(content.textC())
      } yield OperationResponse.RecordResponse(_resource_record(content, text))
  }

  private final case class WebReadActionCall(
    core: ActionCall.Core,
    mode: WebReadMode,
    reference: ResourceReference
  ) extends FunctionalActionCall {
    protected def build_Program: ExecUowM[OperationResponse] =
      for {
        admitted <- exec_from(WebTargetAdmission.admitC(reference))
        content <- exec_from(read_static_web_resource(admitted))
        _ <- exec_from(_resource_size_c(content))
        _ <- exec_from(_web_content_type_c(content))
        text <- if (mode.includeText) exec_from(content.textC()).map(Some(_)) else exec_pure(None)
      } yield OperationResponse.RecordResponse(_web_record(mode, content, text))
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

  private def _resource_size_c(content: ResourceContent): Consequence[Unit] =
    if (content.byteSize <= _max_resource_byte_size)
      Consequence.success(())
    else
      Consequence.argumentFieldLimitExceeded(
        "payload.byteSize",
        _max_resource_byte_size,
        content.byteSize,
        "tool.resource.max-byte-size"
      )

  private def _bounded_resource_reference_c(value: String): Consequence[String] =
    if (value.length <= _max_resource_reference_length)
      Consequence.success(value)
    else
      Consequence.argumentLimitExceeded(
        "reference",
        _max_resource_reference_length,
        value.length,
        "tool.resource.reference-length"
      )

  private def _resource_record(content: ResourceContent, text: String): Record =
    Record.dataAuto(
      "text" -> text,
      "byteSize" -> content.byteSize,
      "scheme" -> content.reference.scheme,
      "mediaType" -> content.mediaType,
      "charset" -> content.declaredCharset.getOrElse(StandardCharsets.UTF_8).name
    )

  private def _web_content_type_c(content: ResourceContent): Consequence[Unit] = {
    val mediatype = content.mediaType.map(_.split(";", 2).head.trim.toLowerCase(Locale.ROOT))
    val accepted = mediatype.exists { value =>
      value.startsWith("text/") ||
        value == "application/json" ||
        value == "application/xml" ||
        value == "application/xhtml+xml"
    }
    if (accepted)
      Consequence.unit
    else
      Consequence.argumentFieldPolicyViolation(
        "response.mediaType",
        "tool.web.static-content-type",
        "text/*, application/json, application/xml, or application/xhtml+xml",
        mediatype.getOrElse("missing")
      )
  }

  private def _web_record(
    mode: WebReadMode,
    content: ResourceContent,
    text: Option[String]
  ): Record =
    Record.dataAuto(
      "mode" -> mode.name,
      "text" -> text,
      "byteSize" -> content.byteSize,
      "scheme" -> content.reference.scheme,
      "mediaType" -> content.mediaType,
      "charset" -> content.declaredCharset.getOrElse(StandardCharsets.UTF_8).name
    )

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
    if (normalized.length > _max_decimal_text_length)
      Consequence.argumentLimitExceeded(
        name,
        _max_decimal_text_length,
        normalized.length,
        "tool.decimal.input-length"
      )
    else if (!_decimal_pattern.matches(normalized))
      Consequence.argumentFormatError(name, "plain base-10 decimal string", value)
    else
      scala.util.Try(BigDecimal(normalized)).toOption match {
        case Some(decimal) if decimal.precision <= _max_decimal_precision =>
          Consequence.success(decimal)
        case Some(decimal) =>
          Consequence.argumentLimitExceeded(
            name,
            _max_decimal_precision,
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
    if (normalized.length > _max_timezone_length)
      Consequence.argumentLimitExceeded(
        name,
        _max_timezone_length,
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
