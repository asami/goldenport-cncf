package org.goldenport.cncf.context

import java.text.NumberFormat
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZoneId, ZonedDateTime}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, FormatStyle}
import java.util.Locale
import scala.util.control.NonFatal
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.config.{OperationMode, ResolvedParameters, RuntimeConfig}
import org.goldenport.cncf.entity.EntityCreateDefaultsPolicy
import org.goldenport.cncf.naming.PropertyValueResolver
import org.goldenport.cncf.operation.evaluation.{
  OperationEvaluationAttemptId,
  OperationEvaluationDeliveryDiagnostic,
  OperationEvaluationExecutionReport
}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext}
import org.goldenport.datatype.{I18nBrief, I18nDescription, I18nLabel, I18nString, I18nSummary, I18nText, I18nTitle}
import org.goldenport.record.{Field, Record}
import org.goldenport.util.StringUtils

/*
 * @since   Dec. 21, 2025
 *  version Jan. 18, 2026
 *  version Mar. 31, 2026
 *  version Apr. 28, 2026
 *  version May. 10, 2026
 *  version Jun. 18, 2026
 * @version Jul. 23, 2026
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
  val operationMode: OperationMode = RuntimeConfig.DefaultOperationMode,
  val transitionValidationHook: TransitionValidationHook = TransitionValidationHook.noop,
  val entityCreateDefaultsPolicy: EntityCreateDefaultsPolicy = EntityCreateDefaultsPolicy.default
) extends ScopeContext() {
  private var _resolved_parameters: Option[ResolvedParameters] = None
  private var _execution_metadata: RuntimeContext.ExecutionMetadata =
    RuntimeContext.ExecutionMetadata.empty

  lazy val unitOfWork: UnitOfWork = unitOfWorkSupplier()

  def unitOfWorkInterpreter: UnitOfWorkOp ~> Consequence = unitOfWorkInterpreterFn

  def commitC(): Consequence[UnitOfWork.CommitResult] =
    commitC(_operation_evaluation_attempt_id)

  def commitC(
    attemptid: Option[OperationEvaluationAttemptId]
  ): Consequence[UnitOfWork.CommitResult] =
    try {
      commitAction(unitOfWork)
      val result = unitOfWork.lastCommitResult.getOrElse(Consequence.unit)
      result match {
        case Consequence.Success(_) =>
          attemptid.foreach(unitOfWork.markOperationEvaluationSupplementalCommitted)
        case Consequence.Failure(_) =>
          attemptid.foreach(unitOfWork.discardOperationEvaluationSupplemental)
      }
      result
    } catch {
      case NonFatal(e) =>
        attemptid.foreach(unitOfWork.discardOperationEvaluationSupplemental)
        Consequence.Failure(org.goldenport.Conclusion.from(e))
    }

  def abortC(): Consequence[UnitOfWork.AbortResult] =
    abortC(_operation_evaluation_attempt_id)

  def abortC(
    attemptid: Option[OperationEvaluationAttemptId]
  ): Consequence[UnitOfWork.AbortResult] =
    try {
      abortAction(unitOfWork)
      val result = unitOfWork.lastAbortResult.getOrElse(Consequence.unit)
      attemptid.foreach(unitOfWork.discardOperationEvaluationSupplemental)
      result
    } catch {
      case NonFatal(e) =>
        attemptid.foreach(unitOfWork.discardOperationEvaluationSupplemental)
        Consequence.Failure(org.goldenport.Conclusion.from(e))
    }

  def commit(): Unit =
    commitAction(unitOfWork)

  def abort(): Unit =
    abortAction(unitOfWork)

  def dispose(): Unit = disposeAction(unitOfWork)

  def toToken: String = token

  def withContext(context: RuntimeContext.Context): RuntimeContext =
    new RuntimeContext(
      core = core,
      unitOfWorkSupplier = unitOfWorkSupplier,
      unitOfWorkInterpreterFn = unitOfWorkInterpreterFn,
      commitAction = commitAction,
      abortAction = abortAction,
      disposeAction = disposeAction,
      token = token,
      context = context,
      operationMode = operationMode,
      transitionValidationHook = transitionValidationHook,
      entityCreateDefaultsPolicy = entityCreateDefaultsPolicy
    )

  def withUnitOfWorkContext(
    executionContext: => ExecutionContext,
    newToken: String = toToken
  ): RuntimeContext = {
    lazy val reboundunitofwork: UnitOfWork =
      unitOfWork.withContext(executionContext)
    val interpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        new UnitOfWorkInterpreter(reboundunitofwork).interpret(fa)
    }
    val runtime = new RuntimeContext(
      core = core,
      unitOfWorkSupplier = () => reboundunitofwork,
      unitOfWorkInterpreterFn = interpreter,
      commitAction = commitAction,
      abortAction = abortAction,
      disposeAction = disposeAction,
      token = newToken,
      context = context,
      operationMode = operationMode,
      transitionValidationHook = transitionValidationHook,
      entityCreateDefaultsPolicy = entityCreateDefaultsPolicy
    )
    _resolved_parameters.foreach(runtime.setResolvedParameters)
    runtime._execution_metadata = executionMetadata
    runtime
  }

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

  def executionMetadata: RuntimeContext.ExecutionMetadata = synchronized {
    _execution_metadata
  }

  def updateExecutionMetadata(
    f: RuntimeContext.ExecutionMetadata => RuntimeContext.ExecutionMetadata
  ): Unit = synchronized {
    _execution_metadata = f(_execution_metadata)
  }

  def noteResponseJobId(jobid: String): Unit =
    updateExecutionMetadata(_.copy(responseJobId = Some(jobid)))

  def noteDebugJobId(jobid: String): Unit =
    updateExecutionMetadata(_.copy(debugJobId = Some(jobid)))

  def noteInlineCallTree(calltree: Record): Unit =
    updateExecutionMetadata(_.copy(inlineCallTree = Some(calltree)))

  def noteExecutionContext(
    sagaId: Option[String],
    jobId: Option[String],
    taskId: Option[String]
  ): Unit =
    updateExecutionMetadata(_.copy(
      sagaId = sagaId,
      executionJobId = jobId,
      executionTaskId = taskId
    ))

  def noteExecutionDiagnostics(
    traceId: Option[String],
    executionId: Option[String],
    failure: Option[String]
  ): Unit =
    updateExecutionMetadata(_.copy(
      traceId = traceId,
      executionId = executionId,
      failure = failure
    ))

  def noteOperationEvaluationDelivery(
    diagnostic: OperationEvaluationDeliveryDiagnostic
  ): Unit =
    updateExecutionMetadata { metadata =>
      val report = metadata.operationEvaluation
        .getOrElse(OperationEvaluationExecutionReport.empty)
        .append(diagnostic)
      metadata.copy(operationEvaluation = Some(report))
    }

  def clearExecutionMetadata(): Unit = synchronized {
    _execution_metadata = RuntimeContext.ExecutionMetadata.empty
  }

  private def _operation_evaluation_attempt_id: Option[OperationEvaluationAttemptId] =
    unitOfWork.executionContext.operationEvaluation.correlation.map(_.attemptId)
}

object RuntimeContext {
  final case class ExecutionMetadata(
    responseJobId: Option[String] = None,
    debugJobId: Option[String] = None,
    inlineCallTree: Option[Record] = None,
    sagaId: Option[String] = None,
    executionJobId: Option[String] = None,
    executionTaskId: Option[String] = None,
    traceId: Option[String] = None,
    executionId: Option[String] = None,
    failure: Option[String] = None,
    operationEvaluation: Option[OperationEvaluationExecutionReport] = None
  )

  object ExecutionMetadata {
    val empty: ExecutionMetadata = ExecutionMetadata()
  }

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

    def resolver: PropertyValueResolver =
      PropertyValueResolver(this)

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
    dateFormatter: DateTimeFormatter = FormattingContext.defaultDateFormatter,
    timeFormatter: DateTimeFormatter = FormattingContext.defaultTimeFormatter,
    dateTimeFormatter: DateTimeFormatter = FormattingContext.defaultDateTimeFormatter
  ) {
    def withLocale(p: Locale): FormattingContext =
      copy(locale = p)

    def withTimezone(p: ZoneId): FormattingContext =
      copy(timezone = p)

    def transformRecord(record: Record): Record =
      Record(record.fields.map(_transform_field))

    /**
     * Formats a temporal value for an application-facing response.
     *
     * This deliberately uses minute precision.  Operational diagnostics should
     * call [[formatLogDateTime]] instead so that machine-readable ISO output
     * remains an explicit choice.
     */
    def formatApplicationDateTime(value: ZonedDateTime): String =
      _application_date_time_formatter.format(value.withZoneSameInstant(timezone))

    def formatApplicationDateTime(value: OffsetDateTime): String =
      formatApplicationDateTime(value.toZonedDateTime)

    def formatApplicationDateTime(value: LocalDateTime): String =
      _application_date_time_formatter.format(value.atZone(timezone))

    def formatApplicationDateTime(value: Instant): String =
      _application_date_time_formatter.format(value.atZone(timezone))

    /** Formats a temporal value for logs and diagnostics in ISO 8601. */
    def formatLogDateTime(value: ZonedDateTime): String =
      FormattingContext.logDateTimeFormatter.format(value.withZoneSameInstant(timezone))

    def formatLogDateTime(value: OffsetDateTime): String =
      formatLogDateTime(value.toZonedDateTime)

    def formatLogDateTime(value: LocalDateTime): String =
      formatLogDateTime(value.atZone(timezone))

    def formatLogDateTime(value: Instant): String =
      formatLogDateTime(value.atZone(timezone))

    def formatValue(value: Any): Any =
      value match {
        case r: Record => transformRecord(r)
        case xs: Iterable[?] => xs.iterator.map(formatValue).toVector
        case xs: Array[?] => xs.toVector.map(formatValue)
        case x: ZonedDateTime => formatApplicationDateTime(x)
        case x: OffsetDateTime => formatApplicationDateTime(x)
        case x: LocalDateTime => formatApplicationDateTime(x)
        case x: Instant => formatApplicationDateTime(x)
        case x: LocalDate => _date_formatter.format(x)
        case x: LocalTime => _time_formatter.format(x)
        case x: BigDecimal => numberStyle.format(x.bigDecimal, locale)
        case x: java.math.BigDecimal => numberStyle.format(x, locale)
        case x: Double => numberStyle.formatDecimal(x, locale)
        case x: Float => numberStyle.formatDecimal(x.toDouble, locale)
        case x: Byte => numberStyle.formatInteger(x.toLong, locale)
        case x: Short => numberStyle.formatInteger(x.toLong, locale)
        case x: Int => numberStyle.formatInteger(x.toLong, locale)
        case x: Long => numberStyle.formatInteger(x, locale)
        case x: I18nString => x.displayMessage(locale)
        case x: I18nLabel => x.toI18nString.displayMessage(locale)
        case x: I18nTitle => x.toI18nString.displayMessage(locale)
        case x: I18nBrief => x.toI18nString.displayMessage(locale)
        case x: I18nSummary => x.toI18nString.displayMessage(locale)
        case x: I18nDescription => x.toI18nString.displayMessage(locale)
        case x: I18nText => x.toI18nString.displayMessage(locale)
        case other => other
      }

    private def _transform_field(field: Field): Field =
      Field(
        field.key,
        Field.Value.Single(formatValue(field.value.single))
      )

    private def _application_date_time_formatter: DateTimeFormatter =
      _resolve_formatter(
        dateTimeFormatter,
        FormattingContext.defaultDateTimeFormatter,
        FormattingContext.dateTimeFormatter(locale)
      )
        .withLocale(locale)
        .withZone(timezone)

    private def _date_formatter: DateTimeFormatter =
      _resolve_formatter(
        dateFormatter,
        FormattingContext.defaultDateFormatter,
        FormattingContext.dateFormatter(locale)
      )
        .withLocale(locale)

    private def _time_formatter: DateTimeFormatter =
      _resolve_formatter(
        timeFormatter,
        FormattingContext.defaultTimeFormatter,
        FormattingContext.timeFormatter(locale)
      )
        .withLocale(locale)

    private def _resolve_formatter(
      specified: DateTimeFormatter,
      default: DateTimeFormatter,
      localized: => DateTimeFormatter
    ): DateTimeFormatter =
      if (specified eq default) localized else specified
  }

  object FormattingContext {
    def dateFormatter(p: Locale): DateTimeFormatter =
      DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(p)

    def timeFormatter(p: Locale): DateTimeFormatter =
      DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(p)

    def dateTimeFormatter(p: Locale): DateTimeFormatter =
      if (p.getLanguage == Locale.JAPANESE.getLanguage)
        new DateTimeFormatterBuilder()
          .appendPattern("M'月'd'日' H'時'mm'分' (z)")
          .toFormatter(p)
      else
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(p)

    val logDateTimeFormatter: DateTimeFormatter =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME

    val defaultDateFormatter: DateTimeFormatter = dateFormatter(Locale.ROOT)
    val defaultTimeFormatter: DateTimeFormatter = timeFormatter(Locale.ROOT)
    val defaultDateTimeFormatter: DateTimeFormatter = dateTimeFormatter(Locale.ROOT)
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
