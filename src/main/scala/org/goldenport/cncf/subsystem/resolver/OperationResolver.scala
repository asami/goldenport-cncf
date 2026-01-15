package org.goldenport.cncf.subsystem.resolver

import org.goldenport.cncf.component.{Component, ComponentOrigin}
import scala.collection.immutable.ListMap

import OperationResolver._

/*
 * @since   Jan. 15, 2026
 * @version Jan. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationResolver private (
  val config: Config,
  private val _entries: Vector[OperationEntry],
  private val _service_slots: Vector[ServiceSlot],
  private val _component_slots: Vector[ComponentSlot],
  private val _implicit_target: Option[OperationEntry]
) {

  /**
   * Phase 2.8 canonical resolve API.
   *
   * Prefix matching is always enabled.
   * Implicit resolution is not part of this phase.
   */
  def resolve(selector: String): ResolutionResult = {
    val trimmed = selector.trim
    val dotCount = trimmed.count(_ == '.')

    val canonical =
      if (config.mode == Mode.OneOperation && dotCount < 2) {
        ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
      } else {
        resolve(
          trimmed,
          allowPrefix = config.mode != Mode.OneOperation && config.prefixMatchingEnabled,
          allowImplicit = false
        )
      }

    canonical match {
      case nf: ResolutionResult.NotFound if config.mode == Mode.OneOperation =>
        _single_operation_optimization(selector) match {
          case res: ResolutionResult.Resolved => res
          case _ => nf
        }
      case other =>
        other
    }
  }

  @deprecated("Phase 2.8: use resolve(selector: String). Flags are no longer part of the public API.")
  def resolve(
    selector: String,
    allowPrefix: Boolean,
    allowImplicit: Boolean
  ): ResolutionResult = {
    val trimmed = selector.trim
    val dotCount = trimmed.count(_ == '.')
    if (dotCount >= 3) {
      return ResolutionResult.Invalid("selector contains too many segments")
    }
    val canonical =
      if (config.mode == Mode.OneOperation) {
        _resolve_with_flags(trimmed, allowPrefix, allowImplicit) match {
          case nf: ResolutionResult.NotFound =>
            val optimized = _single_operation_optimization(trimmed)
            optimized match {
              case res: ResolutionResult.Resolved => res
              case _ => nf
            }
          case other =>
            other
        }
      } else {
        _resolve_with_flags(trimmed, allowPrefix, allowImplicit)
      }
    canonical
  }

  private def _resolve_with_flags(
    trimmed: String,
    allowPrefix: Boolean,
    allowImplicit: Boolean
  ): ResolutionResult =
    if (trimmed.isEmpty) {
      if (allowImplicit) {
        _implicit_target match {
          case Some(target) => ResolutionResult.Resolved(target.fqn, target.component.canonical, target.service.canonical, target.operation.canonical)
          case None => ResolutionResult.Invalid("operation selector is empty")
        }
      } else {
        ResolutionResult.Invalid("operation selector is empty")
      }
    } else {
      val dotCount = trimmed.count(_ == '.')
      dotCount match {
        case 0 => _resolve_operation_only(trimmed, allowPrefix)
        case 1 => _resolve_service_and_operation(trimmed, allowPrefix)
        case 2 => _resolve_component_service_operation(trimmed, allowPrefix)
        case _ => ResolutionResult.Invalid("invalid selector")
      }
    }

  private def _resolve_operation_only(
    selector: String,
    allowPrefix: Boolean
  ): ResolutionResult =
    _match_items(_entries, _.operation, selector, allowPrefix) match {
      case MatchOutcome.Found(result) =>
        result.headOption.map(_to_resolved).getOrElse(ResolutionResult.NotFound(ResolutionStage.Operation, selector))
      case MatchOutcome.Ambiguous(result) =>
        ResolutionResult.Ambiguous(selector, _candidate_fqns(result))
      case MatchOutcome.NotFound =>
        ResolutionResult.NotFound(ResolutionStage.Operation, selector)
    }

  private def _resolve_service_and_operation(
    selector: String,
    allowPrefix: Boolean
  ): ResolutionResult = {
    val Array(serviceSelector, operationSelector) = selector.split("\\.", 2)
    _match_items(_service_slots, _.service, serviceSelector, allowPrefix) match {
      case MatchOutcome.Found(result) =>
        val slot = result.head
        _match_items(slot.operations, _.operation, operationSelector, allowPrefix) match {
          case MatchOutcome.Found(operation) => _to_resolved(operation.head)
          case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationSelector, _candidate_fqns(operation))
          case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationSelector)
        }
      case MatchOutcome.Ambiguous(result) =>
        ResolutionResult.Ambiguous(serviceSelector, _candidate_fqns(result.flatMap(_.operations)))
      case MatchOutcome.NotFound =>
        ResolutionResult.NotFound(ResolutionStage.Service, serviceSelector)
    }
  }

  private def _resolve_component_service_operation(
    selector: String,
    allowPrefix: Boolean
  ): ResolutionResult = {
    val Array(componentSelector, serviceSelector, operationSelector) = selector.split("\\.", 3)
    _match_items(_component_slots, _.component, componentSelector, allowPrefix) match {
      case MatchOutcome.Found(result) =>
        val component = result.head
        _match_items(component.services, _.service, serviceSelector, allowPrefix) match {
          case MatchOutcome.Found(service) =>
            val slot = service.head
            _match_items(slot.operations, _.operation, operationSelector, allowPrefix) match {
              case MatchOutcome.Found(operation) => _to_resolved(operation.head)
              case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationSelector, _candidate_fqns(operation))
              case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationSelector)
            }
          case MatchOutcome.Ambiguous(service) =>
            ResolutionResult.Ambiguous(serviceSelector, _candidate_fqns(service.flatMap(_.operations)))
          case MatchOutcome.NotFound =>
            ResolutionResult.NotFound(ResolutionStage.Component, componentSelector)
        }
      case MatchOutcome.Ambiguous(result) =>
        ResolutionResult.Ambiguous(componentSelector, _candidate_fqns(result.flatMap(_.operations)))
      case MatchOutcome.NotFound =>
        ResolutionResult.NotFound(ResolutionStage.Component, componentSelector)
    }
  }

  private def _to_resolved(entry: OperationEntry): ResolutionResult.Resolved =
    ResolutionResult.Resolved(
      entry.fqn,
      entry.component.canonical,
      entry.service.canonical,
      entry.operation.canonical
    )

  private def _candidate_fqns(entries: Vector[OperationEntry]): Vector[String] =
    entries.map(_.fqn).distinct

  private def _single_operation_optimization(selector: String): ResolutionResult = {
    val trimmed = selector.trim
    if (trimmed.count(_ == '.') != 2) {
      return ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
    }

    val exactMatches =
      _entries.filter { e =>
        e.origin != ComponentOrigin.Builtin &&
        e.fqn == trimmed
      }

    exactMatches match {
      case Vector(entry) => _to_resolved(entry)
      case _ => ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
    }
  }

  private def _match_items[T](
    items: Vector[T],
    accessor: T => NameEntry,
    selector: String,
    allowPrefix: Boolean
  ): MatchOutcome[T] = {
    val exact = items.filter(item => accessor(item).matches(selector))

    if (!allowPrefix) {
      if (exact.size == 1) return MatchOutcome.Found(exact)
      if (exact.size > 1) return MatchOutcome.Ambiguous(exact)
      return MatchOutcome.NotFound
    }

    val prefix = items.filter(item => accessor(item).prefixMatches(selector))
    val combined = (exact ++ prefix).distinct

    combined.size match {
      case 0 => MatchOutcome.NotFound
      case 1 => MatchOutcome.Found(combined)
      case _ => MatchOutcome.Ambiguous(combined)
    }
  }
}

object OperationResolver {
  enum Mode {
    case Normal
    case OneOperation
  }

  final case class Config(
    mode: Mode = Mode.Normal,
    prefixMatchingEnabled: Boolean = true
  )

  def empty: OperationResolver =
    new OperationResolver(
      Config(),
      Vector.empty,
      Vector.empty,
      Vector.empty,
      None
    )

  def build(components: Seq[Component]): OperationResolver = {
    val entries = components.toVector.flatMap { component =>
      component.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map { operation =>
          OperationEntry(
            component = NameEntry(component.name),
            service = NameEntry(service.name),
            operation = NameEntry(operation.name),
            origin = component.origin
          )
        }
      }
    }
    _build_from_entries(entries)
  }

  def fromFqns(
    fqns: Seq[String],
    aliases: Map[String, String] = Map.empty
  ): OperationResolver = {
    val tuples = fqns.map { fqn =>
      val parts = fqn.split("\\.")
      require(parts.length == 3, s"Invalid FQN: $fqn")
      (parts(0), parts(1), parts(2))
    }
    _build_from_tuples(tuples, aliases)
  }

  private def _build_from_tuples(
    tuples: Seq[(String, String, String)],
    aliases: Map[String, String]
  ): OperationResolver = {
    val entries = tuples.toVector.map { case (componentName, serviceName, operationName) =>
      val alias = aliases.get(s"$componentName.$serviceName.$operationName").toVector
      OperationEntry(
        component = NameEntry(componentName),
        service = NameEntry(serviceName),
        operation = NameEntry(operationName, alias),
        origin = ComponentOrigin.Main
      )
    }
    _build_from_entries(entries)
  }

  private def _build_from_entries(entries: Vector[OperationEntry]): OperationResolver = {
    val serviceSlots = _build_service_slots(entries)
    val componentSlots = _build_component_slots(serviceSlots)
    val implicitTarget = _find_implicit_target(entries)

    val non_builtin_entries = entries.filter(_.origin != ComponentOrigin.Builtin)
    val non_builtin_components = non_builtin_entries.map(_.component.canonical).distinct
    val non_builtin_operations = non_builtin_entries.map(_.operation.canonical).distinct

    val mode =
      if (non_builtin_components.size == 1 && non_builtin_operations.size == 1)
        Mode.OneOperation
      else
        Mode.Normal

    new OperationResolver(
      Config(
        mode = mode,
        prefixMatchingEnabled = true
      ),
      entries,
      serviceSlots,
      componentSlots,
      implicitTarget
    )
  }

  private def _build_service_slots(entries: Vector[OperationEntry]): Vector[ServiceSlot] = {
    val grouped = entries.foldLeft(ListMap.empty[(String, String), Vector[OperationEntry]]) {
      case (map, entry) =>
        val key = (entry.component.canonical, entry.service.canonical)
        val values = map.getOrElse(key, Vector.empty) :+ entry
        map.updated(key, values)
    }
    grouped.map { case ((_, _), ops) =>
      ServiceSlot(ops.head.component, ops.head.service, ops)
    }.toVector
  }

  private def _build_component_slots(serviceSlots: Vector[ServiceSlot]): Vector[ComponentSlot] = {
    val grouped = serviceSlots.foldLeft(ListMap.empty[String, Vector[ServiceSlot]]) {
      case (map, slot) =>
        val key = slot.component.canonical
        val values = map.getOrElse(key, Vector.empty) :+ slot
        map.updated(key, values)
    }
    grouped.map { case (_, slots) =>
      ComponentSlot(
        component = slots.head.component,
        services = slots,
        operations = slots.flatMap(_.operations)
      )
    }.toVector
  }

  private def _find_implicit_target(entries: Vector[OperationEntry]): Option[OperationEntry] = {
    val nonBuiltinEntries = entries.filter(_.origin != ComponentOrigin.Builtin)
    if (nonBuiltinEntries.isEmpty) return None
    val componentGroup = nonBuiltinEntries.groupBy(_.component.canonical)
    if (componentGroup.size != 1) return None
    val operations = componentGroup.values.head
    val serviceGroup = operations.groupBy(_.service.canonical)
    if (serviceGroup.size != 1) return None
    val operationGroup = operations.groupBy(_.operation.canonical)
    if (operationGroup.size != 1) return None
    operations.headOption
  }

  private final case class NameEntry(
    canonical: String,
    aliases: Vector[String] = Vector.empty
  ) {
    private val all = canonical +: aliases
    def matches(input: String): Boolean = all.contains(input)
    def prefixMatches(input: String): Boolean = all.exists(_.startsWith(input))
  }

  private final case class OperationEntry(
    component: NameEntry,
    service: NameEntry,
    operation: NameEntry,
    origin: ComponentOrigin
  ) {
    def fqn: String = s"${component.canonical}.${service.canonical}.${operation.canonical}"
  }

  private final case class ServiceSlot(
    component: NameEntry,
    service: NameEntry,
    operations: Vector[OperationEntry]
  )

  private final case class ComponentSlot(
    component: NameEntry,
    services: Vector[ServiceSlot],
    operations: Vector[OperationEntry]
  )

  private sealed trait MatchOutcome[+T]
  private object MatchOutcome {
    final case class Found[T](entries: Vector[T]) extends MatchOutcome[T]
    final case class Ambiguous[T](entries: Vector[T]) extends MatchOutcome[T]
    case object NotFound extends MatchOutcome[Nothing]
  }

  sealed trait ResolutionResult
  object ResolutionResult {
    final case class Resolved(fqn: String, component: String, service: String, operation: String) extends ResolutionResult
    final case class NotFound(stage: ResolutionStage, input: String) extends ResolutionResult
    final case class Ambiguous(input: String, candidates: Vector[String]) extends ResolutionResult
    final case class Invalid(reason: String) extends ResolutionResult
  }

  enum ResolutionStage {
    case Component
    case Service
    case Operation
  }
}
