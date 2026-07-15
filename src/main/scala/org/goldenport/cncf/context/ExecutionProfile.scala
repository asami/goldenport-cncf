package org.goldenport.cncf.context

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.{Clock, Instant, ZoneOffset}
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.context.{EntropyContext, RandomContext}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.record.Record

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
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
  activation: ExecutionProfileActivation
) {
  override def toString: String =
    s"ExecutionProfileConfig(mode=${mode.name}, time=${timeMode.name}, random=${randomMode.name}, " +
      s"ids=${idMode.name}, scheduler=${schedulerMode.name}, ordering=${orderingMode.name}, " +
      s"activation=${activation.name})"
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
  control: ExecutionControlContext
)

final case class ResolvedExecutionProfile private[context] (
  identity: ExecutionProfileIdentity,
  runtimeClock: RuntimeClock,
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
          profile.runtimeClock.clock,
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
        IdGenerationContext.deterministic(namespace, profile.runtimeClock.clock, idseed)
    }
    ExecutionProfileBinding(
      profile.runtimeClock.clock,
      random,
      entropy,
      idgeneration,
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
      ExecutionProfileActivation.Ordinary
    )
    _resolved(config)
  }

  def resolve(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode,
    activationoverride: Option[ExecutionProfileActivation] = None
  ): Consequence[ResolvedExecutionProfile] = {
    val activation = activationoverride.getOrElse(_activation(configuration, operationmode))
    _config(configuration, activation) match {
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
    activation: ExecutionProfileActivation
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
      config.orderingMode.name
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
    ResolvedExecutionProfile(identity, runtimeclock, control, config)
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
