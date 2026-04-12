package org.goldenport.cncf.context

import java.text.NumberFormat
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.Locale
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.entity.EntityCreateDefaultsPolicy
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext}
import org.goldenport.record.{Field, Record}
import org.goldenport.util.StringUtils

/*
 * @since   Dec. 21, 2025
 *  version Jan. 18, 2026
 *  version Mar. 31, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeContext(
  val core: ScopeContext.Core,
  unitOfWorkSupplier: () => UnitOfWork,
  unitOfWorkInterpreterFn: UnitOfWorkOp ~> Consequence,
  commitAction: UnitOfWork => Unit,
  abortAction: UnitOfWork => Unit,
  disposeAction: UnitOfWork => Unit,
  token: String,
  val context: RuntimeContext.Context = RuntimeContext.Context.default,
  val transitionValidationHook: TransitionValidationHook = TransitionValidationHook.noop,
  val entityCreateDefaultsPolicy: EntityCreateDefaultsPolicy = EntityCreateDefaultsPolicy.default
) extends ScopeContext() {
  private var _resolved_parameters: Option[ResolvedParameters] = None

  lazy val unitOfWork: UnitOfWork = unitOfWorkSupplier()

  def unitOfWorkInterpreter: UnitOfWorkOp ~> Consequence = unitOfWorkInterpreterFn

  def commit(): Unit = commitAction(unitOfWork)

  def abort(): Unit = abortAction(unitOfWork)

  def dispose(): Unit = disposeAction(unitOfWork)

  def toToken: String = token

  def resolvedParameters: ResolvedParameters =
    _resolved_parameters.getOrElse(
      ResolvedParameters.empty(GlobalRuntimeContext.current.map(_.resolvedParameters))
    )

  def setResolvedParameters(
    params: ResolvedParameters
  ): Unit =
    _resolved_parameters = Some(params)

  def clearResolvedParameters(): Unit =
    _resolved_parameters = None
}

object RuntimeContext {
  final case class Context(
    propertyName: PropertyNameContext = PropertyNameContext.default,
    formatting: FormattingContext = FormattingContext.default,
    i18n: I18nContext = I18nContext.default
  ) {
    def transformRecord(record: Record): Record =
      formatting.transformRecord(propertyName.transformRecord(record))
  }

  object Context {
    val default: Context = Context()
  }

  final case class PropertyNameContext(
    outputStyle: PropertyNameStyle = PropertyNameStyle.SnakeCase,
    acceptedInputStyles: Set[PropertyNameStyle] = Set(
      PropertyNameStyle.CamelCase,
      PropertyNameStyle.SnakeCase,
      PropertyNameStyle.KebabCase
    )
  ) {
    def outputName(name: String): String =
      outputStyle.transform(name)

    def normalizeInputName(name: String): String =
      PropertyNameStyle.CamelCase.transform(name)

    def aliases(name: String): Vector[String] =
      Vector(
        PropertyNameStyle.CamelCase.transform(name),
        PropertyNameStyle.SnakeCase.transform(name),
        PropertyNameStyle.KebabCase.transform(name)
      ).distinct

    def preferredName(
      keyset: Set[String],
      canonicalName: String
    ): String =
      aliases(canonicalName).find(keyset.contains).getOrElse(outputName(canonicalName))

    def transformRecord(record: Record): Record =
      Record(record.fields.map(_transform_field))

    private def _transform_field(field: Field): Field =
      Field(
        outputName(field.key),
        Field.Value.Single(_transform_value(field.value.single))
      )

    private def _transform_value(value: Any): Any =
      value match {
        case r: Record => transformRecord(r)
        case xs: Iterable[?] => xs.iterator.map(_transform_value).toVector
        case xs: Array[?] => xs.toVector.map(_transform_value)
        case other => other
      }
  }

  object PropertyNameContext {
    val default: PropertyNameContext = PropertyNameContext()
  }

  final case class FormattingContext(
    locale: Locale = Locale.ROOT,
    timezone: ZoneId = ZoneId.of("UTC"),
    numberStyle: NumberStyle = NumberStyle.Plain,
    dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE,
    timeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME,
    dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  ) {
    def transformRecord(record: Record): Record =
      Record(record.fields.map(_transform_field))

    def formatValue(value: Any): Any =
      value match {
        case r: Record => transformRecord(r)
        case xs: Iterable[?] => xs.iterator.map(formatValue).toVector
        case xs: Array[?] => xs.toVector.map(formatValue)
        case x: ZonedDateTime => dateTimeFormatter.withZone(timezone).format(x.withZoneSameInstant(timezone))
        case x: OffsetDateTime => dateTimeFormatter.withZone(timezone).format(x.atZoneSameInstant(timezone))
        case x: LocalDateTime => dateTimeFormatter.withZone(timezone).format(x.atZone(timezone))
        case x: Instant => dateTimeFormatter.withZone(timezone).format(x.atZone(timezone))
        case x: LocalDate => dateFormatter.format(x)
        case x: LocalTime => timeFormatter.format(x)
        case x: BigDecimal => numberStyle.format(x.bigDecimal, locale)
        case x: java.math.BigDecimal => numberStyle.format(x, locale)
        case x: Double => numberStyle.formatDecimal(x, locale)
        case x: Float => numberStyle.formatDecimal(x.toDouble, locale)
        case x: Byte => numberStyle.formatInteger(x.toLong, locale)
        case x: Short => numberStyle.formatInteger(x.toLong, locale)
        case x: Int => numberStyle.formatInteger(x.toLong, locale)
        case x: Long => numberStyle.formatInteger(x, locale)
        case other => other
      }

    private def _transform_field(field: Field): Field =
      Field(
        field.key,
        Field.Value.Single(formatValue(field.value.single))
      )
  }

  object FormattingContext {
    val default: FormattingContext = FormattingContext()
  }

  final case class I18nContext(
    locale: Locale = Locale.ROOT,
    fallbacks: Vector[Locale] = Vector.empty,
    messages: Map[String, String] = Map.empty
  ) {
    def message(key: String, default: => String): String =
      messages.getOrElse(key, default)
  }

  object I18nContext {
    val default: I18nContext = I18nContext()
  }

  enum NumberStyle {
    case Plain
    case LocalizedString

    def format(decimal: java.math.BigDecimal, locale: Locale): Any =
      this match {
        case Plain => decimal
        case LocalizedString => _number_format(locale).format(decimal)
      }

    def formatDecimal(decimal: Double, locale: Locale): Any =
      this match {
        case Plain => decimal
        case LocalizedString => _number_format(locale).format(decimal)
      }

    def formatInteger(integer: Long, locale: Locale): Any =
      this match {
        case Plain => integer
        case LocalizedString => _number_format(locale).format(integer)
      }

    private def _number_format(locale: Locale): NumberFormat =
      NumberFormat.getNumberInstance(locale)
  }

  enum PropertyNameStyle {
    case CamelCase
    case SnakeCase
    case KebabCase

    def transform(name: String): String =
      this match {
        case CamelCase => _to_camel(name)
        case SnakeCase => _to_snake(name)
        case KebabCase => _to_kebab(name)
      }

    private def _to_snake(name: String): String =
      StringUtils.camelToSnake(_normalize_to_camel(name))

    private def _to_kebab(name: String): String =
      _to_snake(name).replace('_', '-')

    private def _to_camel(name: String): String = {
      val normalized = _normalize_to_camel(name)
      if (normalized.isEmpty) normalized
      else s"${normalized.head.toLower}${normalized.tail}"
    }

    private def _normalize_to_camel(name: String): String = {
      val trimmed = Option(name).getOrElse("").trim
      if (trimmed.isEmpty)
        ""
      else if (trimmed.contains("_") || trimmed.contains("-")) {
        trimmed
          .split("[-_]")
          .toVector
          .filter(_.nonEmpty)
          .zipWithIndex
          .map {
            case (part, 0) => s"${part.head.toLower}${part.tail}"
            case (part, _) => s"${part.head.toUpper}${part.tail}"
          }
          .mkString
      } else {
        s"${trimmed.head.toLower}${trimmed.tail}"
      }
    }
  }

  def core(
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver] = None,
    datastore: Option[DataStoreContext] = None,
    entitystore: Option[EntityStoreContext] = None,
    entityspace: Option[EntitySpaceContext] = None
  ): ScopeContext.Core =
    ScopeContext.Core(
      kind = ScopeKind.Runtime,
      name = name,
      parent = parent,
      observabilityContext = observabilityContext,
      httpDriverOption = httpDriverOption,
      datastore = datastore,
      entitystore = entitystore,
      entityspace = entityspace
    )
}
