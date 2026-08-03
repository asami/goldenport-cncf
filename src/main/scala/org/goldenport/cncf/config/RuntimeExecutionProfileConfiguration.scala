package org.goldenport.cncf.config

import java.nio.charset.{Charset, StandardCharsets}
import java.time.{Instant, ZoneId}
import java.util.Locale
import java.math.MathContext

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionIdMode, ExecutionOrderingMode, ExecutionProfileActivation, ExecutionProfileConfig, ExecutionProfileMode, ExecutionRandomMode, ExecutionSchedulerMode, ExecutionTimeMode, ResolvedEnvironmentAssumptions}
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * The value-only pre-Subsystem execution-determinism input.  Catalog
 * projection owns construction; neither raw configuration nor binding metadata
 * may cross this boundary into execution-profile resolution.
 *
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeExecutionProfileConfiguration(
  mode: ExecutionProfileMode,
  runKey: Option[String],
  timeMode: ExecutionTimeMode,
  timeStartAt: Option[Instant],
  virtualStartAt: Option[Instant],
  randomMode: ExecutionRandomMode,
  randomSeed: Option[String],
  idMode: ExecutionIdMode,
  schedulerMode: ExecutionSchedulerMode,
  orderingMode: ExecutionOrderingMode,
  locale: Locale,
  timezone: ZoneId,
  charset: Charset,
  lineSeparator: String,
  mathContext: MathContext,
  textNormalizationPolicy: String,
  textComparisonPolicy: String,
  dateTimeFormatPolicy: String,
  environmentAllow: Vector[String],
  environmentValues: Map[String, String]
) {
  def toExecutionProfileConfig(
    activation: ExecutionProfileActivation,
    ambientEnvironment: Map[String, String]
  ): ExecutionProfileConfig =
    ExecutionProfileConfig(
      mode,
      runKey,
      timeMode,
      timeStartAt.orElse(virtualStartAt),
      randomMode,
      randomSeed,
      idMode,
      schedulerMode,
      orderingMode,
      ResolvedEnvironmentAssumptions(
        locale,
        timezone,
        charset,
        lineSeparator,
        mathContext,
        textNormalizationPolicy,
        textComparisonPolicy,
        dateTimeFormatPolicy,
        environmentAllow.flatMap(name => environmentValues.get(name).orElse(ambientEnvironment.get(name)).map(name -> _)).toMap
      ),
      activation
    )
}

object RuntimeExecutionProfileConfiguration {
  val default: RuntimeExecutionProfileConfiguration = RuntimeExecutionProfileConfiguration(
    ExecutionProfileMode.Standard,
    None,
    ExecutionTimeMode.System,
    None,
    None,
    ExecutionRandomMode.System,
    None,
    ExecutionIdMode.Production,
    ExecutionSchedulerMode.Realtime,
    ExecutionOrderingMode.Concurrent,
    Locale.ROOT,
    ZoneId.of("UTC"),
    StandardCharsets.UTF_8,
    "\n",
    MathContext.DECIMAL64,
    "default",
    "default",
    "default",
    Vector.empty,
    Map.empty
  )

  def from(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[RuntimeExecutionProfileConfiguration] =
    if (bindings == null)
      Consequence.configurationInvalid("runtime execution-profile bindings are required")
    else
      for {
        profile <- bindings.value(CncfConfigurationParameterCatalog.executionProfile)
        runkey <- bindings.value(CncfConfigurationParameterCatalog.executionKey)
        virtualstart <- bindings.value(CncfConfigurationParameterCatalog.clockVirtualStartAt)
        timemode <- bindings.value(CncfConfigurationParameterCatalog.executionTimeMode)
        timestart <- bindings.value(CncfConfigurationParameterCatalog.executionTimeStartAt)
        randommode <- bindings.value(CncfConfigurationParameterCatalog.executionRandomMode)
        randomseed <- bindings.value(CncfConfigurationParameterCatalog.executionRandomSeed)
        idmode <- bindings.value(CncfConfigurationParameterCatalog.executionIdsMode)
        schedulermode <- bindings.value(CncfConfigurationParameterCatalog.executionSchedulerMode)
        orderingmode <- bindings.value(CncfConfigurationParameterCatalog.executionOrderingMode)
        locale <- bindings.value(CncfConfigurationParameterCatalog.executionLocale)
        timezone <- bindings.value(CncfConfigurationParameterCatalog.executionTimezone)
        charset <- bindings.value(CncfConfigurationParameterCatalog.executionCharset)
        lineseparator <- bindings.value(CncfConfigurationParameterCatalog.executionLineSeparator)
        mathcontext <- bindings.value(CncfConfigurationParameterCatalog.executionMathContext)
        normalization <- bindings.value(CncfConfigurationParameterCatalog.executionI18nTextNormalizationPolicy)
        comparison <- bindings.value(CncfConfigurationParameterCatalog.executionI18nTextComparisonPolicy)
        dateformat <- bindings.value(CncfConfigurationParameterCatalog.executionI18nDateTimeFormatPolicy)
        environmentallow <- bindings.value(CncfConfigurationParameterCatalog.executionEnvironmentAllow)
        environmentvalues <- bindings.value(CncfConfigurationParameterCatalog.executionEnvironmentValues)
      } yield {
        val mode = profile.getOrElse(default.mode)
        RuntimeExecutionProfileConfiguration(
          mode,
          runkey,
          timemode.getOrElse(if (virtualstart.nonEmpty) ExecutionTimeMode.Offset else _default_time(mode)),
          timestart,
          virtualstart,
          randommode.getOrElse(_default_random(mode)),
          randomseed,
          idmode.getOrElse(_default_ids(mode)),
          schedulermode.getOrElse(_default_scheduler(mode)),
          orderingmode.getOrElse(_default_ordering(mode)),
          locale.getOrElse(default.locale),
          timezone.getOrElse(default.timezone),
          charset.getOrElse(default.charset),
          lineseparator.getOrElse(default.lineSeparator),
          mathcontext.getOrElse(default.mathContext),
          normalization.getOrElse(default.textNormalizationPolicy),
          comparison.getOrElse(default.textComparisonPolicy),
          dateformat.getOrElse(default.dateTimeFormatPolicy),
          environmentallow.getOrElse(Vector.empty),
          environmentvalues.getOrElse(Map.empty)
        )
      }

  private def _default_time(mode: ExecutionProfileMode): ExecutionTimeMode =
    mode match {
      case ExecutionProfileMode.Controlled => ExecutionTimeMode.Manual
      case _ => ExecutionTimeMode.System
    }

  private def _default_random(mode: ExecutionProfileMode): ExecutionRandomMode =
    mode match {
      case ExecutionProfileMode.Standard => ExecutionRandomMode.System
      case _ => ExecutionRandomMode.Seeded
    }

  private def _default_ids(mode: ExecutionProfileMode): ExecutionIdMode =
    mode match {
      case ExecutionProfileMode.Controlled => ExecutionIdMode.Deterministic
      case _ => ExecutionIdMode.Production
    }

  private def _default_scheduler(mode: ExecutionProfileMode): ExecutionSchedulerMode =
    mode match {
      case ExecutionProfileMode.Controlled => ExecutionSchedulerMode.Manual
      case _ => ExecutionSchedulerMode.Realtime
    }

  private def _default_ordering(mode: ExecutionProfileMode): ExecutionOrderingMode =
    mode match {
      case ExecutionProfileMode.Controlled => ExecutionOrderingMode.Deterministic
      case _ => ExecutionOrderingMode.Concurrent
    }
}
