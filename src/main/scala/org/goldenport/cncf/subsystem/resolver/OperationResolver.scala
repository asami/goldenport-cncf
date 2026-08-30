package org.goldenport.cncf.subsystem.resolver

import org.goldenport.cncf.component.{Component, ComponentId, ComponentIdentityCompatibilityAdapter, ComponentOrigin}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.OperationDefinition
import scala.collection.immutable.ListMap

import OperationResolver._

/*
 * @since   Jan. 15, 2026
 *  version Jan. 16, 2026
 *  version Mar. 28, 2026
 *  version Apr. 11, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationResolver private (
  val config: Config,
  private val _entries: Vector[OperationEntry],
  private val _service_slots: Vector[ServiceSlot],
  private val _component_slots: Vector[ComponentSlot],
  private val _implicit_target: Option[OperationEntry],
  private val _runtime_alias_candidates: Vector[ComponentIdentityCompatibilityAdapter.AliasCandidate]
) {

  private[cncf] final case class DetailedResolution(
    result: ResolutionResult,
    notices: Vector[ComponentIdentityCompatibilityAdapter.Notice]
  )

  /**
   * Phase 2.8 canonical resolve API.
   *
   * Prefix matching is always enabled.
   * Implicit resolution is not part of this phase.
   */
  def resolve(selector: String): ResolutionResult = {
    resolveWithNotices(selector).result
  }

  private[cncf] def resolveWithNotices(selector: String): DetailedResolution = {
    val trimmed = selector.trim
    val dotcount = trimmed.count(_ == '.')

    val canonical =
      if (config.mode == Mode.OneOperation && dotcount < 2) {
        DetailedResolution(ResolutionResult.NotFound(ResolutionStage.Operation, trimmed), Vector.empty)
      } else {
        _resolve_with_flags_detailed(
          trimmed,
          allowprefix = config.mode != Mode.OneOperation && config.prefixMatchingEnabled,
          allowimplicit = false
        )
      }

    canonical match {
      case DetailedResolution(_: ResolutionResult.NotFound, _) if config.mode == Mode.OneOperation =>
        _single_operation_optimization(selector) match {
          case res: ResolutionResult.Resolved => DetailedResolution(res, Vector.empty)
          case _ => canonical
        }
      case other =>
        other
    }
  }

  def resolveOperationDefinition(
    selector: String
  ): Option[OperationDefinition] = {
    resolve(selector) match {
      case ResolutionResult.Resolved(fqn, _, _, _) =>
        _entries.find(_.fqn == fqn).flatMap(_.operationdefinition)
      case _ => None
    }
  }

  @deprecated("Phase 2.8: use resolve(selector: String). Flags are no longer part of the public API.")
  def resolve(
    selector: String,
    allowPrefix: Boolean,
    allowImplicit: Boolean
  ): ResolutionResult = {
    val trimmed = selector.trim
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
    allowprefix: Boolean,
    allowimplicit: Boolean
  ): ResolutionResult =
    if (trimmed.isEmpty) {
      if (allowimplicit) {
        _implicit_target match {
          case Some(target) => ResolutionResult.Resolved(target.fqn, target.component.canonical, target.service.canonical, target.operation.canonical)
          case None => ResolutionResult.Invalid("operation selector is empty")
        }
      } else {
        ResolutionResult.Invalid("operation selector is empty")
      }
    } else {
      val parts = trimmed.split("\\.", -1).toVector
      if (parts.exists(_.isEmpty)) {
        ResolutionResult.Invalid("operation selector has an empty segment")
      } else parts.size match {
        case 0 => ResolutionResult.Invalid("operation selector is empty")
        case 1 => _resolve_operation_only(trimmed, allowprefix)
        case 2 => _resolve_service_and_operation(trimmed, allowprefix)
        case _ =>
          _resolve_component_service_operation_detailed(
            parts.dropRight(2).mkString("."),
            parts(parts.size - 2),
            parts.last,
            allowprefix
          ).result
      }
    }

  private def _resolve_operation_only(
    selector: String,
    allowprefix: Boolean
  ): ResolutionResult =
    _match_items(_entries, _.operation, selector, allowprefix) match {
      case MatchOutcome.Found(result) =>
        result.headOption.map(_to_resolved).getOrElse(ResolutionResult.NotFound(ResolutionStage.Operation, selector))
      case MatchOutcome.Ambiguous(result) =>
        ResolutionResult.Ambiguous(selector, _candidate_fqns(result))
      case MatchOutcome.NotFound =>
        ResolutionResult.NotFound(ResolutionStage.Operation, selector)
    }

  private def _resolve_service_and_operation(
    selector: String,
    allowprefix: Boolean
  ): ResolutionResult = {
    val Array(serviceselector, operationselector) = selector.split("\\.", 2)
    _match_items(_service_slots, _.service, serviceselector, allowprefix) match {
      case MatchOutcome.Found(result) =>
        val slot = result.head
        _match_items(slot.operations, _.operation, operationselector, allowprefix) match {
          case MatchOutcome.Found(operation) => _to_resolved(operation.head)
          case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationselector, _candidate_fqns(operation))
          case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationselector)
        }
      case MatchOutcome.Ambiguous(result) =>
        ResolutionResult.Ambiguous(serviceselector, _candidate_fqns(result.flatMap(_.operations)))
      case MatchOutcome.NotFound =>
        ResolutionResult.NotFound(ResolutionStage.Service, serviceselector)
    }
  }

  private def _resolve_with_flags_detailed(
    trimmed: String,
    allowprefix: Boolean,
    allowimplicit: Boolean
  ): DetailedResolution = {
    val parts = trimmed.split("\\.", -1).toVector
    if (trimmed.isEmpty || parts.exists(_.isEmpty) || parts.size < 3)
      DetailedResolution(_resolve_with_flags(trimmed, allowprefix, allowimplicit), Vector.empty)
    else
      _resolve_component_service_operation_detailed(
        parts.dropRight(2).mkString("."),
        parts(parts.size - 2),
        parts.last,
        allowprefix
      )
  }

  private def _resolve_component_service_operation_detailed(
    componentselector: String,
    serviceselector: String,
    operationselector: String,
    allowprefix: Boolean
  ): DetailedResolution = {
    ComponentIdentityCompatibilityAdapter.resolveAliases(
      componentselector,
      _runtime_alias_candidates,
      ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector,
      allowprefix
    ) match {
      case result: ComponentIdentityCompatibilityAdapter.Canonical =>
        DetailedResolution(
          _resolve_component_service_operation_for_id(result.componentid, componentselector, serviceselector, operationselector, allowprefix),
          Vector.empty
        )
      case result: ComponentIdentityCompatibilityAdapter.Adapted =>
        DetailedResolution(
          _resolve_component_service_operation_for_id(result.componentid, componentselector, serviceselector, operationselector, allowprefix),
          Vector(result.notice)
        )
      case ComponentIdentityCompatibilityAdapter.Rejected(rejection: ComponentIdentityCompatibilityAdapter.Ambiguous) =>
        val components = _component_slots.filter(slot => rejection.candidates.contains(slot.component.id))
        DetailedResolution(
          ResolutionResult.Ambiguous(
            componentselector,
            _candidate_fqns(_matching_component_operations(components, serviceselector, operationselector, allowprefix))
          ),
          Vector.empty
        )
      case _: ComponentIdentityCompatibilityAdapter.Rejected =>
        DetailedResolution(ResolutionResult.NotFound(ResolutionStage.Component, componentselector), Vector.empty)
    }
  }

  private def _resolve_component_service_operation_for_id(
    componentid: ComponentId,
    componentselector: String,
    serviceselector: String,
    operationselector: String,
    allowprefix: Boolean
  ): ResolutionResult = {
    val matching = _component_slots.filter(_.component.id == componentid)
    matching match {
      case Vector(component) =>
        _match_items(component.services, _.service, serviceselector, allowprefix) match {
          case MatchOutcome.Found(service) =>
            _match_items(service.head.operations, _.operation, operationselector, allowprefix) match {
              case MatchOutcome.Found(operation) => _to_resolved(operation.head)
              case MatchOutcome.Ambiguous(operation) => ResolutionResult.Ambiguous(operationselector, _candidate_fqns(operation))
              case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Operation, operationselector)
            }
          case MatchOutcome.Ambiguous(service) => ResolutionResult.Ambiguous(serviceselector, _candidate_fqns(service.flatMap(_.operations)))
          case MatchOutcome.NotFound => ResolutionResult.NotFound(ResolutionStage.Component, componentselector)
        }
      case _ => ResolutionResult.NotFound(ResolutionStage.Component, componentselector)
    }
  }

  private def _matching_component_operations(
    components: Vector[ComponentSlot],
    serviceselector: String,
    operationselector: String,
    allowprefix: Boolean
  ): Vector[OperationEntry] =
    components.flatMap { component =>
      _match_items(component.services, _.service, serviceselector, allowprefix) match {
        case MatchOutcome.Found(services) => _matching_service_operations(services, operationselector, allowprefix)
        case MatchOutcome.Ambiguous(services) => _matching_service_operations(services, operationselector, allowprefix)
        case MatchOutcome.NotFound => Vector.empty
      }
    }

  private def _matching_service_operations(
    services: Vector[ServiceSlot],
    operationselector: String,
    allowprefix: Boolean
  ): Vector[OperationEntry] =
    services.flatMap { service =>
      _match_items(service.operations, _.operation, operationselector, allowprefix) match {
        case MatchOutcome.Found(operations) => operations
        case MatchOutcome.Ambiguous(operations) => operations
        case MatchOutcome.NotFound => Vector.empty
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

    val exactmatches =
      _entries.filter { e =>
        e.origin != ComponentOrigin.Builtin &&
        NamingConventions.equivalentSelector(e.fqn, trimmed)
      }

    exactmatches match {
      case Vector(entry) => _to_resolved(entry)
      case _ => ResolutionResult.NotFound(ResolutionStage.Operation, trimmed)
    }
  }

  private def _match_items[T](
    items: Vector[T],
    accessor: T => SelectorEntry,
    selector: String,
    allowprefix: Boolean
  ): MatchOutcome[T] = {
    val exact = items.filter(item => accessor(item).matches(selector))

    if (!allowprefix) {
      if (exact.size == 1) return MatchOutcome.Found(exact)
      if (exact.size > 1) return MatchOutcome.Ambiguous(exact)
      return MatchOutcome.NotFound
    }

    if (exact.nonEmpty) {
      if (exact.size == 1) return MatchOutcome.Found(exact)
      return MatchOutcome.Ambiguous(exact)
    }

    val prefix = items.filter(item => accessor(item).prefixMatches(selector))
    prefix.distinct.size match {
      case 0 => MatchOutcome.NotFound
      case 1 => MatchOutcome.Found(prefix.distinct)
      case _ => MatchOutcome.Ambiguous(prefix.distinct)
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
    prefixMatchingEnabled: Boolean = false
  )

  def empty: OperationResolver =
    new OperationResolver(
      Config(),
      Vector.empty,
      Vector.empty,
      Vector.empty,
      None,
      Vector.empty
    )

  def build(components: Seq[Component]): OperationResolver = {
    val entries = components.toVector.flatMap { component =>
      val componentaliases =
        component.artifactMetadata.toVector.flatMap(metadata =>
          Vector(Some(metadata.name), metadata.component).flatten
        ).filter(_.trim.nonEmpty).distinct
      component.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map { operation =>
          OperationEntry(
            component = ComponentEntry(
              component.componentId,
              (Vector(component.displayName) ++ componentaliases)
                .filterNot(_ == component.componentId.name)
                .distinct
            ),
            service = NameEntry(service.name),
            operation = NameEntry(operation.name),
            origin = component.origin,
            operationdefinition = Some(operation)
          )
        }
      }
    }
    _build_from_entries(entries, ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(components))
  }

  def fromFqns(
    fqns: Seq[String],
    aliases: Map[String, String] = Map.empty
  ): OperationResolver = {
    val tuples = fqns.map { fqn =>
      val parts = fqn.split("\\.", -1).toVector
      require(parts.size >= 3, s"Invalid FQN: $fqn")
      require(parts.forall(_.nonEmpty), s"Invalid FQN: $fqn")
      val componentid = parts.dropRight(2).mkString(".")
      val componentvalue = ComponentId.parseC(componentid).toOption.getOrElse(
        throw new IllegalArgumentException(s"Invalid FQN: $fqn")
      )
      (componentvalue, parts(parts.size - 2), parts.last)
    }
    _build_from_tuples(tuples, aliases)
  }

  private def _build_from_tuples(
    tuples: Seq[(ComponentId, String, String)],
    aliases: Map[String, String]
  ): OperationResolver = {
    val entries = tuples.toVector.map { case (componentid, servicename, operationname) =>
      val alias = aliases.get(s"${componentid.name}.$servicename.$operationname").toVector
      OperationEntry(
        component = ComponentEntry(componentid),
        service = NameEntry(servicename),
        operation = NameEntry(operationname, alias),
        origin = ComponentOrigin.Main,
        operationdefinition = None
      )
    }
    _build_from_entries(entries)
  }

  private def _build_from_entries(
    entries: Vector[OperationEntry],
    runtimealiascandidates: Vector[ComponentIdentityCompatibilityAdapter.AliasCandidate] = Vector.empty
  ): OperationResolver = {
    val serviceslots = _build_service_slots(entries)
    val componentslots = _build_component_slots(serviceslots)
    val implicittarget = _find_implicit_target(entries)

    val nonbuiltinentries = entries.filter(_.origin != ComponentOrigin.Builtin)
    val nonbuiltincomponents = nonbuiltinentries.map(_.component.id).distinct
    val nonbuiltinoperations = nonbuiltinentries.map(_.operation.canonical).distinct

    val mode =
      if (nonbuiltincomponents.size == 1 && nonbuiltinoperations.size == 1)
        Mode.OneOperation
      else
        Mode.Normal

    new OperationResolver(
      Config(
        mode = mode,
        prefixMatchingEnabled = true
      ),
      entries,
      serviceslots,
      componentslots,
      implicittarget,
      if (runtimealiascandidates.nonEmpty) runtimealiascandidates else _alias_candidates(componentslots)
    )
  }

  private def _alias_candidates(
    componentslots: Vector[ComponentSlot]
  ): Vector[ComponentIdentityCompatibilityAdapter.AliasCandidate] =
    componentslots.map(slot => ComponentIdentityCompatibilityAdapter.AliasCandidate(slot.component.id, slot.component.aliases))

  private def _build_service_slots(entries: Vector[OperationEntry]): Vector[ServiceSlot] = {
    val grouped = entries.foldLeft(ListMap.empty[(ComponentId, String), Vector[OperationEntry]]) {
      case (map, entry) =>
        val key = (entry.component.id, entry.service.canonical)
        val values = map.getOrElse(key, Vector.empty) :+ entry
        map.updated(key, values)
    }
    grouped.map { case ((_, _), ops) =>
      ServiceSlot(ops.head.component, ops.head.service, ops)
    }.toVector
  }

  private def _build_component_slots(serviceslots: Vector[ServiceSlot]): Vector[ComponentSlot] = {
    val grouped = serviceslots.foldLeft(ListMap.empty[ComponentId, Vector[ServiceSlot]]) {
      case (map, slot) =>
        val key = slot.component.id
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
    val nonbuiltinentries = entries.filter(_.origin != ComponentOrigin.Builtin)
    if (nonbuiltinentries.isEmpty) return None
    val componentgroup = nonbuiltinentries.groupBy(_.component.id)
    if (componentgroup.size != 1) return None
    val operations = componentgroup.values.head
    val servicegroup = operations.groupBy(_.service.canonical)
    if (servicegroup.size != 1) return None
    val operationgroup = operations.groupBy(_.operation.canonical)
    if (operationgroup.size != 1) return None
    operations.headOption
  }

  private sealed trait SelectorEntry {
    def canonical: String
    def canonicalMatches(input: String): Boolean
    def matches(input: String): Boolean
    def prefixMatches(input: String): Boolean
  }

  private final case class NameEntry(
    canonical: String,
    aliases: Vector[String] = Vector.empty
  ) extends SelectorEntry {
    private val _alias_comparison = aliases.map(NamingConventions.toComparisonKey).distinct

    def canonicalMatches(input: String): Boolean =
      canonical == input.trim || NamingConventions.equivalentByNormalized(canonical, input.trim)

    def matches(input: String): Boolean = {
      val trimmed = input.trim
      if (trimmed.isEmpty) {
        false
      } else {
        canonicalMatches(trimmed) || _alias_comparison.contains(NamingConventions.toComparisonKey(trimmed))
      }
    }

    def prefixMatches(input: String): Boolean = {
      val trimmed = input.trim
      if (trimmed.isEmpty) {
        false
      } else {
        val key = NamingConventions.toComparisonKey(trimmed)
        (NamingConventions.toComparisonKey(canonical).startsWith(key) ||
          _alias_comparison.exists(_.startsWith(key)))
      }
    }
  }

  private final case class ComponentEntry(
    id: ComponentId,
    aliases: Vector[String] = Vector.empty
  ) extends SelectorEntry {
    private val _alias_comparison = aliases.map(NamingConventions.toComparisonKey).distinct

    def canonical: String = id.name

    def canonicalMatches(input: String): Boolean =
      canonical == input.trim

    def matches(input: String): Boolean = {
      val trimmed = input.trim
      trimmed.nonEmpty &&
        (canonicalMatches(trimmed) || _alias_comparison.contains(NamingConventions.toComparisonKey(trimmed)))
    }

    def prefixMatches(input: String): Boolean = {
      val trimmed = input.trim
      if (trimmed.isEmpty)
        false
      else {
        val key = NamingConventions.toComparisonKey(trimmed)
        NamingConventions.toComparisonKey(canonical).startsWith(key) ||
          _alias_comparison.exists(_.startsWith(key))
      }
    }
  }

  private final case class OperationEntry(
    component: ComponentEntry,
    service: NameEntry,
    operation: NameEntry,
    origin: ComponentOrigin,
    operationdefinition: Option[OperationDefinition]
  ) {
    def fqn: String = s"${component.canonical}.${service.canonical}.${operation.canonical}"
  }

  private final case class ServiceSlot(
    component: ComponentEntry,
    service: NameEntry,
    operations: Vector[OperationEntry]
  )

  private final case class ComponentSlot(
    component: ComponentEntry,
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
