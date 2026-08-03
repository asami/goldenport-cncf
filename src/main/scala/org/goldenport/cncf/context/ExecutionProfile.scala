package org.goldenport.cncf.context

import java.math.MathContext
import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.context.{EntropyContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig, RuntimeExecutionProfileConfiguration}
import org.goldenport.record.Record

/*
 * @since   Jul. 15, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
enum ExecutionProfileMode(val name: String) {
  case Standard extends ExecutionProfileMode("standard")
  case Seeded extends ExecutionProfileMode("seeded")
  case Controlled extends ExecutionProfileMode("controlled")
}

object ExecutionProfileMode {
  def parse(value: String): Option[ExecutionProfileMode] =
    _normalized(value) match {
      case "standard" => Some(Standard)
      case "seeded" => Some(Seeded)
      case "controlled" => Some(Controlled)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionTimeMode(val name: String) {
  case System extends ExecutionTimeMode("system")
  case Offset extends ExecutionTimeMode("offset")
  case Manual extends ExecutionTimeMode("manual")
}

object ExecutionTimeMode {
  def parse(value: String): Option[ExecutionTimeMode] =
    _normalized(value) match {
      case "system" => Some(System)
      case "offset" => Some(Offset)
      case "manual" => Some(Manual)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionRandomMode(val name: String) {
  case System extends ExecutionRandomMode("system")
  case Seeded extends ExecutionRandomMode("seeded")
}

object ExecutionRandomMode {
  def parse(value: String): Option[ExecutionRandomMode] =
    _normalized(value) match {
      case "system" => Some(System)
      case "seeded" => Some(Seeded)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionIdMode(val name: String) {
  case Production extends ExecutionIdMode("production")
  case Deterministic extends ExecutionIdMode("deterministic")
}

object ExecutionIdMode {
  def parse(value: String): Option[ExecutionIdMode] =
    _normalized(value) match {
      case "production" => Some(Production)
      case "deterministic" => Some(Deterministic)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionSchedulerMode(val name: String) {
  case Realtime extends ExecutionSchedulerMode("realtime")
  case Manual extends ExecutionSchedulerMode("manual")
}

object ExecutionSchedulerMode {
  def parse(value: String): Option[ExecutionSchedulerMode] =
    _normalized(value) match {
      case "realtime" => Some(Realtime)
      case "manual" => Some(Manual)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionOrderingMode(val name: String) {
  case Concurrent extends ExecutionOrderingMode("concurrent")
  case Deterministic extends ExecutionOrderingMode("deterministic")
}

object ExecutionOrderingMode {
  def parse(value: String): Option[ExecutionOrderingMode] =
    _normalized(value) match {
      case "concurrent" => Some(Concurrent)
      case "deterministic" => Some(Deterministic)
      case _ => None
    }

  private def _normalized(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
}

enum ExecutionProfileActivation(val name: String, val allowsControlled: Boolean) {
  case Ordinary extends ExecutionProfileActivation("ordinary", false)
  case ExplicitTestDescriptor extends ExecutionProfileActivation("test-descriptor", true)
  case TestCommand extends ExecutionProfileActivation("test-command", true)
  case InProcessSpec extends ExecutionProfileActivation("in-process-spec", true)
}

enum ReplayabilityState(val name: String) {
  case Replayable extends ReplayabilityState("replayable")
  case PartiallyControlled extends ReplayabilityState("partially-controlled")
  case Uncontrolled extends ReplayabilityState("uncontrolled")
  case Invalid extends ReplayabilityState("invalid")
}

final case class ReplayabilityAssessment(
  state: ReplayabilityState,
  reasons: Vector[String]
) {
  def toRecord: Record =
    Record.data(
      "state" -> state.name,
      "reasons" -> reasons
    )
}

final case class ExecutionProfileConfig(
  mode: ExecutionProfileMode,
  runKey: Option[String],
  timeMode: ExecutionTimeMode,
  startAt: Option[Instant],
  randomMode: ExecutionRandomMode,
  randomSeed: Option[String],
  idMode: ExecutionIdMode,
  schedulerMode: ExecutionSchedulerMode,
  orderingMode: ExecutionOrderingMode,
  environmentAssumptions: ResolvedEnvironmentAssumptions,
  activation: ExecutionProfileActivation
) {
  override def toString: String =
    s"ExecutionProfileConfig(mode=${mode.name}, time=${timeMode.name}, random=${randomMode.name}, " +
      s"ids=${idMode.name}, scheduler=${schedulerMode.name}, ordering=${orderingMode.name}, " +
      s"activation=${activation.name})"
}

final case class ResolvedEnvironmentAssumptions(
  locale: Locale,
  timezone: ZoneId,
  charset: Charset,
  lineSeparator: String,
  mathContext: MathContext,
  textNormalizationPolicy: String,
  textComparisonPolicy: String,
  dateTimeFormatPolicy: String,
  environmentVariables: Map[String, String]
) {
  private[context] def apply_to(
    current: CoreExecutionContext.Core,
    clock: Clock
  ): CoreExecutionContext.Core =
    current.copy(
      vm = VirtualMachineContext.Instant(
        VirtualMachineContext.Core(
          clock = clock,
          timezone = timezone,
          encoding = charset,
          lineSeparator = lineSeparator,
          mathContext = mathContext,
          environmentVariables = environmentVariables,
          resourceBundleBaseNames = current.vm.resourceBundleBaseNames,
          resourceBundleLocales = current.vm.resourceBundleLocales,
          resourceBundleResolutionOrder = current.vm.resourceBundleResolutionOrder
        )
      ),
      i18n = I18nContext.Instant(
        I18nContext.Core(
          textNormalizationPolicy = textNormalizationPolicy,
          textComparisonPolicy = textComparisonPolicy,
          dateTimeFormatPolicy = dateTimeFormatPolicy,
          locale = Some(locale)
        )
      ),
      locale = locale,
      timezone = timezone,
      encoding = charset,
      clock = clock,
      math = mathContext
    )

  def toRecord: Record =
    Record.data(
      "locale" -> locale.toLanguageTag,
      "timezone" -> timezone.getId,
      "charset" -> charset.name,
      "lineSeparator" -> ResolvedEnvironmentAssumptions.line_separator_name(lineSeparator),
      "mathContext" -> ResolvedEnvironmentAssumptions.math_context_name(mathContext),
      "i18n" -> Record.data(
        "textNormalizationPolicy" -> textNormalizationPolicy,
        "textComparisonPolicy" -> textComparisonPolicy,
        "dateTimeFormatPolicy" -> dateTimeFormatPolicy
      ),
      "environmentNames" -> environmentVariables.keys.toVector.sorted
    )

  private[context] def fingerprint: String = {
    val values = Vector(
      locale.toLanguageTag,
      timezone.getId,
      charset.name,
      lineSeparator,
      mathContext.toString,
      textNormalizationPolicy,
      textComparisonPolicy,
      dateTimeFormatPolicy
    ) ++ environmentVariables.toVector.sortBy(_._1).flatMap { case (name, value) => Vector(name, value) }
    ExecutionProfileHash.digest(values*)
  }

  override def toString: String = toRecord.toString
}

object ResolvedEnvironmentAssumptions {
  val default: ResolvedEnvironmentAssumptions =
    ResolvedEnvironmentAssumptions(
      Locale.ROOT,
      ZoneId.of("UTC"),
      StandardCharsets.UTF_8,
      "\n",
      MathContext.DECIMAL64,
      "default",
      "default",
      "default",
      Map.empty
    )

  private[context] def line_separator_name(value: String): String =
    value match {
      case "\n" => "lf"
      case "\r\n" => "crlf"
      case "\r" => "cr"
      case _ => "custom"
    }

  private[context] def math_context_name(value: MathContext): String =
    if (value == MathContext.DECIMAL32) "decimal32"
    else if (value == MathContext.DECIMAL64) "decimal64"
    else if (value == MathContext.DECIMAL128) "decimal128"
    else if (value == MathContext.UNLIMITED) "unlimited"
    else value.toString
}

final case class ExecutionProfileIdentity(
  name: String,
  mode: ExecutionProfileMode,
  fingerprint: String
) {
  def toRecord: Record =
    Record.data(
      "name" -> name,
      "mode" -> mode.name,
      "fingerprint" -> fingerprint
    )
}

final case class ExecutionInvocationIdentity(
  key: String,
  ordinal: Long,
  operationSelector: String,
  explicit: Boolean
) {
  def toRecord: Record =
    Record.data(
      "key" -> key,
      "ordinal" -> ordinal,
      "operationSelector" -> operationSelector,
      "explicit" -> explicit
    )
}

final case class ExecutionControlContext(
  profile: ExecutionProfileIdentity,
  invocation: Option[ExecutionInvocationIdentity],
  timeMode: ExecutionTimeMode,
  randomMode: ExecutionRandomMode,
  idMode: ExecutionIdMode,
  schedulerMode: ExecutionSchedulerMode,
  orderingMode: ExecutionOrderingMode,
  replayability: ReplayabilityAssessment
) {
  def toRecord: Record =
    Record.data(
      "profile" -> profile.toRecord,
      "invocation" -> invocation.map(_.toRecord),
      "timeMode" -> timeMode.name,
      "randomMode" -> randomMode.name,
      "idMode" -> idMode.name,
      "schedulerMode" -> schedulerMode.name,
      "orderingMode" -> orderingMode.name,
      "replayability" -> replayability.toRecord
    )
}

object ExecutionControlContext {
  val standard: ExecutionControlContext = {
    val identity = ExecutionProfileIdentity("standard", ExecutionProfileMode.Standard, "standard")
    ExecutionControlContext(
      identity,
      invocation = None,
      ExecutionTimeMode.System,
      ExecutionRandomMode.System,
      ExecutionIdMode.Production,
      ExecutionSchedulerMode.Realtime,
      ExecutionOrderingMode.Concurrent,
      ReplayabilityAssessment(ReplayabilityState.Uncontrolled, Vector("system-time", "system-random", "realtime-scheduler", "concurrent-ordering"))
    )
  }
}

object ExecutionInvocationIdentity {
  def operationSelector(
    component: Option[String],
    service: Option[String],
    operation: String
  ): String =
    Vector(component, service, Option(operation))
      .flatten
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString(".")
}

final case class ExecutionProfileBinding(
  clock: Clock,
  random: RandomContext,
  entropy: EntropyContext,
  idGeneration: IdGenerationContext,
  environmentAssumptions: ResolvedEnvironmentAssumptions,
  control: ExecutionControlContext
)

final case class ResolvedExecutionProfile private[context] (
  identity: ExecutionProfileIdentity,
  runtimeClock: RuntimeClock,
  environmentAssumptions: ResolvedEnvironmentAssumptions,
  control: ExecutionControlContext,
  private[cncf] val config: ExecutionProfileConfig
) {
  def newRuntime(namespace: IdGenerationContext.IdNamespace): ExecutionProfileRuntime =
    new ExecutionProfileRuntime(this, namespace)

  override def toString: String =
    s"ResolvedExecutionProfile(${identity.name}, ${identity.fingerprint}, ${control.replayability.state.name})"
}

final class ExecutionProfileRuntime private[context] (
  val profile: ResolvedExecutionProfile,
  namespace: IdGenerationContext.IdNamespace
) {
  private val _invocation_ordinal = new AtomicLong(0L)
  val runtimeClock: RuntimeClock = profile.runtimeClock.runtime_instance
  val schedulingRuntime: ExecutionSchedulingRuntime =
    ExecutionSchedulingRuntime.create(runtimeClock, profile.control.schedulerMode)

  def testControl: Option[ExecutionTestControl] =
    schedulingRuntime.testControl

  def baseBinding: ExecutionProfileBinding =
    _binding(None)

  def nextBinding(
    operationselector: String,
    explicitkey: Option[String] = None
  ): ExecutionProfileBinding = {
    val ordinal = _invocation_ordinal.incrementAndGet()
    val normalizedselector = _normalized_selector(operationselector)
    val key = explicitkey.map(_.trim).filter(_.nonEmpty).getOrElse {
      ExecutionProfileHash.digest(
        profile.config.runKey.getOrElse(profile.identity.fingerprint),
        normalizedselector,
        ordinal.toString
      )
    }
    _binding(Some(ExecutionInvocationIdentity(key, ordinal, normalizedselector, explicitkey.exists(_.trim.nonEmpty))))
  }

  private def _binding(
    invocation: Option[ExecutionInvocationIdentity]
  ): ExecutionProfileBinding = {
    val invocationkey = invocation.map(_.key).getOrElse("runtime-base")
    val random = profile.config.randomMode match {
      case ExecutionRandomMode.System => RandomContext.system()
      case ExecutionRandomMode.Seeded =>
        RandomContext.seeded(profile.config.randomSeed.getOrElse("")).stream(invocationkey)
    }
    val entropy = EntropyContext.secure()
    val idgeneration = profile.config.idMode match {
      case ExecutionIdMode.Production =>
        IdGenerationContext.production(
          namespace,
          schedulingRuntime.clock,
          EntropyContext.secure()
        )
      case ExecutionIdMode.Deterministic =>
        val invocationordinal = invocation.map(_.ordinal).getOrElse(0L)
        val idseed = ExecutionProfileHash.digest(
          profile.identity.fingerprint,
          invocationkey,
          invocationordinal.toString,
          "id-generation"
        )
        IdGenerationContext.deterministic(namespace, schedulingRuntime.clock, idseed)
    }
    ExecutionProfileBinding(
      schedulingRuntime.clock,
      random,
      entropy,
      idgeneration,
      profile.environmentAssumptions,
      profile.control.copy(invocation = invocation)
    )
  }

  private def _normalized_selector(value: String): String = {
    val selector = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("unknown")
    selector.toLowerCase(Locale.ROOT)
  }
}

object ExecutionProfileResolver {
  def standard: ResolvedExecutionProfile = {
    val config = ExecutionProfileConfig(
      ExecutionProfileMode.Standard,
      runKey = None,
      ExecutionTimeMode.System,
      startAt = None,
      ExecutionRandomMode.System,
      randomSeed = None,
      ExecutionIdMode.Production,
      ExecutionSchedulerMode.Realtime,
      ExecutionOrderingMode.Concurrent,
      ResolvedEnvironmentAssumptions.default,
      ExecutionProfileActivation.Ordinary
    )
    _resolved(config)
  }

  def resolve(
    configuration: ResolvedConfiguration,
    operationMode: OperationMode,
    activationOverride: Option[ExecutionProfileActivation] = None
  ): Consequence[ResolvedExecutionProfile] =
    resolve_with_environment(configuration, operationMode, sys.env, activationOverride)

  /**
   * Runtime GCF-09M entry point.  Its caller has already admitted typed
   * configuration and supplies ambient environment explicitly.
   */
  def resolve(
    configuration: RuntimeExecutionProfileConfiguration,
    activation: ExecutionProfileActivation,
    ambientEnvironment: Map[String, String]
  ): Consequence[ResolvedExecutionProfile] =
    if (configuration == null || activation == null || ambientEnvironment == null)
      Consequence.configurationInvalid("runtime execution-profile configuration is invalid")
    else if (configuration.timeStartAt.zip(configuration.virtualStartAt).exists { case (left, right) => left != right })
      Consequence.configurationInvalid(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} conflicts with ${RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY}")
    else if (configuration.environmentValues.keys.exists(name => !configuration.environmentAllow.contains(name))) {
      val name = configuration.environmentValues.keys.filterNot(configuration.environmentAllow.contains).toVector.sorted.head
      Consequence.configurationInvalid(s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.${name} is not declared by ${RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY}")
    } else {
      _typed_start_at(configuration.timeMode, configuration.timeStartAt, configuration.virtualStartAt).flatMap { startat =>
        val config = configuration.toExecutionProfileConfig(activation, ambientEnvironment)
        _validate(config.copy(startAt = startat), configuration.virtualStartAt.map(_.toString)) match {
          case Left(message) => Consequence.configurationInvalid(message)
          case Right(_) => Consequence.success(_resolved(config.copy(startAt = startat)))
        }
      }
    }

  private[context] def resolve_with_environment(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode,
    ambientenvironment: Map[String, String],
    activationoverride: Option[ExecutionProfileActivation] = None
  ): Consequence[ResolvedExecutionProfile] = {
    val activation = activationoverride.getOrElse(_activation(configuration, operationmode))
    _config(configuration, activation, ambientenvironment) match {
      case Left(message) => Consequence.configurationInvalid(message)
      case Right(config) => Consequence.success(_resolved(config))
    }
  }

  def resolveForSpec(
    configuration: ResolvedConfiguration
  ): Consequence[ResolvedExecutionProfile] =
    resolve(configuration, OperationMode.Test, Some(ExecutionProfileActivation.InProcessSpec))

  private def _activation(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode
  ): ExecutionProfileActivation =
    if (RuntimeConfig.getString(configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY).exists(_.trim.nonEmpty))
      ExecutionProfileActivation.ExplicitTestDescriptor
    else if (operationmode == OperationMode.Test)
      ExecutionProfileActivation.TestCommand
    else
      ExecutionProfileActivation.Ordinary

  private def _config(
    configuration: ResolvedConfiguration,
    activation: ExecutionProfileActivation,
    ambientenvironment: Map[String, String]
  ): Either[String, ExecutionProfileConfig] = {
    val profilevalue = _value(configuration, RuntimeConfig.EXECUTION_PROFILE_KEY).getOrElse("standard")
    val mode = ExecutionProfileMode.parse(profilevalue).toRight(s"${RuntimeConfig.EXECUTION_PROFILE_KEY} has unsupported value: ${profilevalue}")
    mode.flatMap { profilemode =>
      val virtualstart = _value(configuration, RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY)
      val timestart = _value(configuration, RuntimeConfig.EXECUTION_TIME_START_AT_KEY)
      val defaulttime = if (virtualstart.nonEmpty) ExecutionTimeMode.Offset else _default_time(profilemode)
      for {
        timemode <- _mode(configuration, RuntimeConfig.EXECUTION_TIME_MODE_KEY, defaulttime, ExecutionTimeMode.parse)
        randommode <- _mode(configuration, RuntimeConfig.EXECUTION_RANDOM_MODE_KEY, _default_random(profilemode), ExecutionRandomMode.parse)
        idmode <- _mode(configuration, RuntimeConfig.EXECUTION_IDS_MODE_KEY, _default_ids(profilemode), ExecutionIdMode.parse)
        schedulermode <- _mode(configuration, RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY, _default_scheduler(profilemode), ExecutionSchedulerMode.parse)
        orderingmode <- _mode(configuration, RuntimeConfig.EXECUTION_ORDERING_MODE_KEY, _default_ordering(profilemode), ExecutionOrderingMode.parse)
        startat <- _start_at(timemode, timestart, virtualstart)
        environment <- _environment(configuration, ambientenvironment)
        config = ExecutionProfileConfig(
          profilemode,
          _value(configuration, RuntimeConfig.EXECUTION_KEY),
          timemode,
          startat,
          randommode,
          _value(configuration, RuntimeConfig.EXECUTION_RANDOM_SEED_KEY),
          idmode,
          schedulermode,
          orderingmode,
          environment,
          activation
        )
        _ <- _validate(config, virtualstart)
      } yield config
    }
  }

  private def _mode[A](
    configuration: ResolvedConfiguration,
    key: String,
    default: A,
    parse: String => Option[A]
  ): Either[String, A] =
    _value(configuration, key) match {
      case Some(value) => parse(value).toRight(s"${key} has unsupported value: ${value}")
      case None => Right(default)
    }

  private def _start_at(
    mode: ExecutionTimeMode,
    configured: Option[String],
    compatibility: Option[String]
  ): Either[String, Option[Instant]] = {
    val configuredinstant = configured.map(_instant(RuntimeConfig.EXECUTION_TIME_START_AT_KEY, _))
    val compatibilityinstant = compatibility.map(_instant(RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY, _))
    for {
      explicit <- _sequence(configuredinstant)
      legacy <- _sequence(compatibilityinstant)
      _ <- (explicit, legacy) match {
        case (Some(x), Some(y)) if x != y => Left(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} conflicts with ${RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY}")
        case _ => Right(())
      }
      result = explicit.orElse(legacy)
      _ <- mode match {
        case ExecutionTimeMode.System if result.nonEmpty => Left(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} requires offset or manual time mode")
        case ExecutionTimeMode.Offset | ExecutionTimeMode.Manual if result.isEmpty => Left(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} is required for ${mode.name} time mode")
        case _ => Right(())
      }
    } yield result
  }

  private def _typed_start_at(
    mode: ExecutionTimeMode,
    configured: Option[Instant],
    compatibility: Option[Instant]
  ): Consequence[Option[Instant]] = {
    val result = configured.orElse(compatibility)
    if (configured.zip(compatibility).exists { case (left, right) => left != right })
      Consequence.configurationInvalid(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} conflicts with ${RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY}")
    else if (mode == ExecutionTimeMode.System && result.nonEmpty)
      Consequence.configurationInvalid(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} requires offset or manual time mode")
    else if ((mode == ExecutionTimeMode.Offset || mode == ExecutionTimeMode.Manual) && result.isEmpty)
      Consequence.configurationInvalid(s"${RuntimeConfig.EXECUTION_TIME_START_AT_KEY} is required for ${mode.name} time mode")
    else
      Consequence.success(result)
  }

  private def _instant(key: String, value: String): Either[String, Instant] =
    try {
      Right(RuntimeClock.parseInstant(value))
    } catch {
      case _: IllegalArgumentException => Left(s"${key} must be an ISO-8601 date-time with an offset: ${value}")
    }

  private def _sequence[A](value: Option[Either[String, A]]): Either[String, Option[A]] =
    value match {
      case Some(Right(x)) => Right(Some(x))
      case Some(Left(message)) => Left(message)
      case None => Right(None)
    }

  private def _validate(
    config: ExecutionProfileConfig,
    virtualstart: Option[String]
  ): Either[String, Unit] = {
    val expected = config.mode match {
      case ExecutionProfileMode.Standard =>
        Set(ExecutionTimeMode.System, ExecutionTimeMode.Offset) ->
          (ExecutionRandomMode.System, ExecutionIdMode.Production, ExecutionSchedulerMode.Realtime, ExecutionOrderingMode.Concurrent)
      case ExecutionProfileMode.Seeded =>
        Set(ExecutionTimeMode.System, ExecutionTimeMode.Offset) ->
          (ExecutionRandomMode.Seeded, ExecutionIdMode.Production, ExecutionSchedulerMode.Realtime, ExecutionOrderingMode.Concurrent)
      case ExecutionProfileMode.Controlled =>
        Set(ExecutionTimeMode.Manual) ->
          (ExecutionRandomMode.Seeded, ExecutionIdMode.Deterministic, ExecutionSchedulerMode.Manual, ExecutionOrderingMode.Deterministic)
    }
    val (times, dimensions) = expected
    val (random, ids, scheduler, ordering) = dimensions
    if (!times.contains(config.timeMode))
      Left(s"${config.mode.name} profile does not allow time mode ${config.timeMode.name}")
    else if (config.randomMode != random)
      Left(s"${config.mode.name} profile requires random mode ${random.name}")
    else if (config.idMode != ids)
      Left(s"${config.mode.name} profile requires id mode ${ids.name}")
    else if (config.schedulerMode != scheduler)
      Left(s"${config.mode.name} profile requires scheduler mode ${scheduler.name}")
    else if (config.orderingMode != ordering)
      Left(s"${config.mode.name} profile requires ordering mode ${ordering.name}")
    else if (config.randomMode == ExecutionRandomMode.Seeded && config.randomSeed.forall(_.trim.isEmpty))
      Left(s"${RuntimeConfig.EXECUTION_RANDOM_SEED_KEY} is required for ${config.mode.name} profile")
    else if (config.mode == ExecutionProfileMode.Controlled && config.runKey.forall(_.trim.isEmpty))
      Left(s"${RuntimeConfig.EXECUTION_KEY} is required for controlled profile")
    else if (config.mode == ExecutionProfileMode.Controlled && !config.activation.allowsControlled)
      Left("controlled execution profile requires an explicit test descriptor, cncf test, or in-process spec builder")
    else if (config.timeMode == ExecutionTimeMode.Manual && virtualstart.nonEmpty)
      Left(s"manual time mode conflicts with ${RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY}")
    else
      Right(())
  }

  private def _environment(
    configuration: ResolvedConfiguration,
    ambientenvironment: Map[String, String]
  ): Either[String, ResolvedEnvironmentAssumptions] = {
    val localevalue = _value(configuration, RuntimeConfig.EXECUTION_LOCALE_KEY).getOrElse("und")
    val timezonevalue = _value(configuration, RuntimeConfig.EXECUTION_TIMEZONE_KEY).getOrElse("UTC")
    val charsetvalue = _value(configuration, RuntimeConfig.EXECUTION_CHARSET_KEY).getOrElse("UTF-8")
    val lineseparatorvalue = _value(configuration, RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY).getOrElse("lf")
    val mathcontextvalue = _value(configuration, RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY).getOrElse("decimal64")
    val allow = _environment_allow(configuration)
    val configuredvalues = _environment_values(configuration)
    for {
      locale <- _locale(localevalue)
      timezone <- _timezone(timezonevalue)
      charset <- _charset(charsetvalue)
      lineseparator <- _line_separator(lineseparatorvalue)
      mathcontext <- _math_context(mathcontextvalue)
      _ <- configuredvalues.keys.filterNot(allow.contains).toVector.sorted.headOption match {
        case Some(name) => Left(s"${RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY}.${name} is not declared by ${RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY}")
        case None => Right(())
      }
      snapshot = allow.flatMap { name =>
        configuredvalues.get(name).orElse(ambientenvironment.get(name)).map(name -> _)
      }.toMap
    } yield ResolvedEnvironmentAssumptions(
      locale,
      timezone,
      charset,
      lineseparator,
      mathcontext,
      _value(configuration, RuntimeConfig.EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY).getOrElse("default"),
      _value(configuration, RuntimeConfig.EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY).getOrElse("default"),
      _value(configuration, RuntimeConfig.EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY).getOrElse("default"),
      snapshot
    )
  }

  private def _locale(value: String): Either[String, Locale] = {
    val locale = Locale.forLanguageTag(value.trim.replace('_', '-'))
    if (value.trim.equalsIgnoreCase("root") || value.trim.equalsIgnoreCase("und")) Right(Locale.ROOT)
    else if (locale == Locale.ROOT) Left(s"${RuntimeConfig.EXECUTION_LOCALE_KEY} has invalid locale: ${value}")
    else Right(locale)
  }

  private def _timezone(value: String): Either[String, ZoneId] =
    scala.util.Try(ZoneId.of(value.trim)).toEither.left.map(_ =>
      s"${RuntimeConfig.EXECUTION_TIMEZONE_KEY} has invalid timezone: ${value}"
    )

  private def _charset(value: String): Either[String, Charset] =
    scala.util.Try(Charset.forName(value.trim)).toEither.left.map(_ =>
      s"${RuntimeConfig.EXECUTION_CHARSET_KEY} has invalid charset: ${value}"
    )

  private def _line_separator(value: String): Either[String, String] =
    value.trim.toLowerCase(Locale.ROOT) match {
      case "lf" => Right("\n")
      case "crlf" => Right("\r\n")
      case "cr" => Right("\r")
      case _ => Left(s"${RuntimeConfig.EXECUTION_LINE_SEPARATOR_KEY} has unsupported value: ${value}")
    }

  private def _math_context(value: String): Either[String, MathContext] =
    value.trim.toLowerCase(Locale.ROOT) match {
      case "decimal32" => Right(MathContext.DECIMAL32)
      case "decimal64" => Right(MathContext.DECIMAL64)
      case "decimal128" => Right(MathContext.DECIMAL128)
      case "unlimited" => Right(MathContext.UNLIMITED)
      case _ => Left(s"${RuntimeConfig.EXECUTION_MATH_CONTEXT_KEY} has unsupported value: ${value}")
    }

  private def _environment_values(
    configuration: ResolvedConfiguration
  ): Map[String, String] = {
    val prefixes = Vector(
      RuntimeConfig.EXECUTION_ENVIRONMENT_VALUES_KEY,
      RuntimeConfig.RUNTIME_EXECUTION_ENVIRONMENT_VALUES_KEY
    )
    val flattened = configuration.configuration.values.iterator.flatMap { case (key, value) =>
      prefixes.iterator.flatMap { prefix =>
        val marker = prefix + "."
        Option.when(key.startsWith(marker))(_configuration_string(value).map(key.drop(marker.length) -> _)).flatten
      }
    }.toMap
    val nested = prefixes.iterator.flatMap(prefix =>
      _configuration_value(configuration.configuration.values, prefix.split('.').toList)
    ).collectFirst {
      case ConfigurationValue.ObjectValue(values) =>
        values.flatMap { case (name, value) => _configuration_string(value).map(name -> _) }
    }.getOrElse(Map.empty)
    nested ++ flattened
  }

  private def _environment_allow(
    configuration: ResolvedConfiguration
  ): Vector[String] = {
    val prefixes = Vector(
      RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY,
      RuntimeConfig.RUNTIME_EXECUTION_ENVIRONMENT_ALLOW_KEY
    )
    val structured = prefixes.iterator.flatMap(prefix =>
      _configuration_value(configuration.configuration.values, prefix.split('.').toList)
    ).collectFirst {
      case ConfigurationValue.ListValue(values) => values.flatMap(_configuration_string).toVector
    }.getOrElse(Vector.empty)
    val textual = _value(configuration, RuntimeConfig.EXECUTION_ENVIRONMENT_ALLOW_KEY)
      .toVector.flatMap(_.split("[,|\\s]+").toVector)
    (structured ++ textual).map(_.trim).filter(_.nonEmpty).distinct
  }

  private def _configuration_value(
    values: Map[String, ConfigurationValue],
    path: List[String]
  ): Option[ConfigurationValue] =
    values.get(path.mkString(".")).orElse(_nested_configuration_value(values, path))

  private def _nested_configuration_value(
    values: Map[String, ConfigurationValue],
    path: List[String]
  ): Option[ConfigurationValue] =
    path match {
      case Nil => None
      case name :: Nil => values.get(name)
      case name :: tail => values.get(name).collect { case ConfigurationValue.ObjectValue(children) => children }.flatMap(_nested_configuration_value(_, tail))
    }

  private def _configuration_string(value: ConfigurationValue): Option[String] =
    value match {
      case ConfigurationValue.StringValue(x) => Some(x)
      case ConfigurationValue.NumberValue(x) => Some(x.toString)
      case ConfigurationValue.BooleanValue(x) => Some(x.toString)
      case _ => None
    }

  private def _resolved(config: ExecutionProfileConfig): ResolvedExecutionProfile = {
    val fingerprint = ExecutionProfileHash.digest(
      config.mode.name,
      config.runKey.getOrElse(""),
      config.timeMode.name,
      config.startAt.map(_.toString).getOrElse(""),
      config.randomMode.name,
      config.randomSeed.getOrElse(""),
      config.idMode.name,
      config.schedulerMode.name,
      config.orderingMode.name,
      config.environmentAssumptions.fingerprint
    )
    val identity = ExecutionProfileIdentity(config.mode.name, config.mode, fingerprint)
    val replayability = config.mode match {
      case ExecutionProfileMode.Standard =>
        ReplayabilityAssessment(ReplayabilityState.Uncontrolled, Vector("system-time", "system-random", "realtime-scheduler", "concurrent-ordering"))
      case ExecutionProfileMode.Seeded =>
        ReplayabilityAssessment(ReplayabilityState.PartiallyControlled, Vector("system-time", "realtime-scheduler", "concurrent-ordering"))
      case ExecutionProfileMode.Controlled =>
        ReplayabilityAssessment(ReplayabilityState.Replayable, Vector.empty)
    }
    val runtimeclock = config.timeMode match {
      case ExecutionTimeMode.System => RuntimeClock.system(Clock.systemUTC())
      case ExecutionTimeMode.Offset => RuntimeClock.offset(Clock.systemUTC(), config.startAt.get)
      case ExecutionTimeMode.Manual => RuntimeClock.manual(config.startAt.get, ZoneOffset.UTC)
    }
    val control = ExecutionControlContext(
      identity,
      invocation = None,
      config.timeMode,
      config.randomMode,
      config.idMode,
      config.schedulerMode,
      config.orderingMode,
      replayability
    )
    ResolvedExecutionProfile(identity, runtimeclock, config.environmentAssumptions, control, config)
  }

  private def _value(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    RuntimeConfig.getString(configuration, key).map(_.trim).filter(_.nonEmpty)

  private def _default_time(mode: ExecutionProfileMode): ExecutionTimeMode =
    mode match {
      case ExecutionProfileMode.Standard | ExecutionProfileMode.Seeded => ExecutionTimeMode.System
      case ExecutionProfileMode.Controlled => ExecutionTimeMode.Manual
    }

  private def _default_random(mode: ExecutionProfileMode): ExecutionRandomMode =
    mode match {
      case ExecutionProfileMode.Standard => ExecutionRandomMode.System
      case ExecutionProfileMode.Seeded | ExecutionProfileMode.Controlled => ExecutionRandomMode.Seeded
    }

  private def _default_ids(mode: ExecutionProfileMode): ExecutionIdMode =
    mode match {
      case ExecutionProfileMode.Standard | ExecutionProfileMode.Seeded => ExecutionIdMode.Production
      case ExecutionProfileMode.Controlled => ExecutionIdMode.Deterministic
    }

  private def _default_scheduler(mode: ExecutionProfileMode): ExecutionSchedulerMode =
    mode match {
      case ExecutionProfileMode.Standard | ExecutionProfileMode.Seeded => ExecutionSchedulerMode.Realtime
      case ExecutionProfileMode.Controlled => ExecutionSchedulerMode.Manual
    }

  private def _default_ordering(mode: ExecutionProfileMode): ExecutionOrderingMode =
    mode match {
      case ExecutionProfileMode.Standard | ExecutionProfileMode.Seeded => ExecutionOrderingMode.Concurrent
      case ExecutionProfileMode.Controlled => ExecutionOrderingMode.Deterministic
    }
}

private object ExecutionProfileHash {
  def digest(values: String*): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    values.foreach { value =>
      digest.update(Option(value).getOrElse("").getBytes(StandardCharsets.UTF_8))
      digest.update(0.toByte)
    }
    digest.digest().take(12).map(b => f"${b & 0xff}%02x").mkString
  }
}
