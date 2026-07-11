package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentInstanceId, ExtensionPoint, Port, PortApi, ServiceContract, VariationPoint, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext

/*
 * CNCF canonical SPI vocabulary.
 *
 * The current implementation intentionally aliases the pre-existing
 * component.Port binding vocabulary so existing Port-based providers remain
 * source compatible while new code can import org.goldenport.cncf.spi.*.
 *
 * @since   Jul.  2, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
type SpiContract[S] = ServiceContract[S]
object SpiContract {
  def apply[S](
    name: String,
    runtimeclass: Class[? <: S]
  ): SpiContract[S] =
    ServiceContract(name, runtimeclass)

  def unapply[S](p: SpiContract[S]): Option[(String, Class[? <: S])] =
    Some((p.name, p.runtimeClass))
}

type SpiPortApi[-Req, S] = PortApi[Req, S]
type SpiSelection = VariationSelection
object SpiSelection {
  def apply(
    provider: Option[String] = None,
    mode: Option[String] = None,
    engine: Option[String] = None
  ): SpiSelection =
    VariationSelection(provider, mode, engine)

  def unapply(p: SpiSelection): Option[(Option[String], Option[String], Option[String])] =
    Some((p.provider, p.mode, p.engine))
}

type SpiSelectionPoint[Req] = VariationPoint[Req]
type SpiProvider[S] = ExtensionPoint[S]
type SpiPort[Req, S] = Port[Req, S]
object SpiPort {
  def apply[Req, S](
    api: SpiPortApi[Req, S],
    providers: Vector[SpiProvider[S]],
    selectionpoint: SpiSelectionPoint[Req]
  ): SpiPort[Req, S] =
    Port(api, providers, selectionpoint)
}

trait SpiProviderComponent {
  def spiProviders: Vector[SpiProvider[?]]
}

trait SpiSocket[S] {
  def spiContract: SpiContract[S]
  def spiSocketName: String = "default"
  def spiSelection: SpiSelection = SpiSelection()
  def spiRequired: Boolean = true
  def isSpiInstalled: Boolean = false
  def installSpi(spi: S): Unit
}

trait SpiSocketSet[S] {
  def spiContract: SpiContract[S]
  def spiSocketName: String = "default"
  def spiSelection: SpiSelection = SpiSelection()
  def spiSelectionPolicy: ComponentSelectionPolicy = ComponentSelectionPolicy.allowAll
  def spiRequired: Boolean = false
  def spiMembers: Vector[ResolvedSpiMember[S]]
  def isSpiInstalled: Boolean = spiMembers.nonEmpty
  def installSpiMembers(members: Vector[ResolvedSpiMember[S]]): Unit

  final def resolve(selector: ComponentSelector = ComponentSelector())(using ExecutionContext): Consequence[S] =
    ComponentApiResolver(spiMembers, spiSelectionPolicy).resolve(spiContract, selector)
}

sealed trait SpiCardinality {
  def isMany: Boolean
  def isRequired: Boolean
}

object SpiCardinality {
  case object One extends SpiCardinality {
    val isMany = false
    val isRequired = true
  }
  case object Optional extends SpiCardinality {
    val isMany = false
    val isRequired = false
  }
  case object Many extends SpiCardinality {
    val isMany = true
    val isRequired = false
  }
  case object OneOrMore extends SpiCardinality {
    val isMany = true
    val isRequired = true
  }

  def from(cardinality: String, required: Boolean): Consequence[SpiCardinality] =
    cardinality.trim.toLowerCase.replace('_', '-').replace(' ', '-') match {
      case "one" | "single" => Consequence.success(if (required) One else Optional)
      case "optional" | "zero-or-one" if required =>
        Consequence.resourceInvalid("optional SPI socket cardinality cannot be required")
      case "optional" | "zero-or-one" => Consequence.success(Optional)
      case "many" | "set" | "zero-or-more" => Consequence.success(if (required) OneOrMore else Many)
      case "one-or-more" | "non-empty" if !required =>
        Consequence.resourceInvalid("non-empty SPI socket cardinality cannot be optional")
      case "one-or-more" | "non-empty" => Consequence.success(OneOrMore)
      case value => Consequence.resourceInvalid(s"unknown SPI socket cardinality: ${value}")
    }
}

final case class ComponentSelector(
  component: Option[String] = None,
  instance: Option[String] = None,
  purpose: Option[String] = None,
  capabilities: Set[String] = Set.empty,
  tags: Set[String] = Set.empty
)

final case class SpiMemberMetadata(
  contract: String,
  component: String,
  instanceId: ComponentInstanceId,
  purposes: Set[String] = Set.empty,
  capabilities: Set[String] = Set.empty,
  tags: Set[String] = Set.empty,
  priority: Int = 0,
  isDefault: Boolean = false,
  healthStatus: String = "ok"
)

final case class ResolvedSpiMember[S](
  service: S,
  metadata: SpiMemberMetadata
)

trait ComponentSelectionPolicy {
  def accept(member: SpiMemberMetadata, selector: ComponentSelector): Consequence[Boolean]

  final def and(rhs: ComponentSelectionPolicy): ComponentSelectionPolicy = {
    val lhs = this
    new ComponentSelectionPolicy {
      def accept(member: SpiMemberMetadata, selector: ComponentSelector): Consequence[Boolean] =
        lhs.accept(member, selector).flatMap { accepted =>
          if (accepted) rhs.accept(member, selector) else Consequence.success(false)
        }
    }
  }
}

object ComponentSelectionPolicy {
  val allowAll: ComponentSelectionPolicy = new ComponentSelectionPolicy {
    def accept(member: SpiMemberMetadata, selector: ComponentSelector): Consequence[Boolean] =
      Consequence.success(true)
  }
}

final class ComponentApiResolver private (
  val members: Vector[ResolvedSpiMember[?]],
  private[spi] val providers: Vector[ComponentApiProvider],
  private[spi] val policy: ComponentSelectionPolicy
) {
  def resolve[S](contract: SpiContract[S], selector: ComponentSelector)(using ExecutionContext): Consequence[S] =
    resolveMember(contract, selector).map(_.service)

  def resolveMember[S](
    contract: SpiContract[S],
    selector: ComponentSelector
  )(using ExecutionContext): Consequence[ResolvedSpiMember[S]] = {
    val resolved = members.collect {
      case member
          if member.metadata.contract == contract.name && contract.runtimeClass.isInstance(member.service) =>
        val typed = member.asInstanceOf[ResolvedSpiMember[S]]
        ComponentApiCandidate(typed.metadata, () => Consequence.success(typed))
    }
    val resolvedids = resolved.map(x => _member_key(x.metadata)).toSet
    val unresolved = providers.flatMap { provider =>
      val metadata = provider.metadata.copy(contract = contract.name)
      if (
        !resolvedids.contains(_member_key(metadata)) &&
          !metadata.healthStatus.equalsIgnoreCase("error") &&
          provider.provider.asInstanceOf[SpiProvider[S]].supports(contract, provider.selection)
      ) {
        Some(ComponentApiCandidate(
          metadata,
          () => provider.provider.asInstanceOf[SpiProvider[S]].provide(contract, provider.selection).map { service =>
            val trace = SpiTraceMetadata(
              contract = contract.name,
              operation = "resolve",
              socketComponent = "component-api-resolver",
              providerComponent = metadata.component,
              selectionProvider = provider.selection.provider,
              selectionMode = provider.selection.mode,
              selectionEngine = provider.selection.engine
            )
            ResolvedSpiMember(SpiTraceSupport.wrapInstalled(service, trace).asInstanceOf[S], metadata)
          }
        ))
      } else {
        None
      }
    }
    val candidates = (resolved ++ unresolved).filter(candidate => _matches(candidate.metadata, selector))
    _filter_policy(candidates, selector).flatMap { eligible =>
      _select(eligible, selector, contract.name).flatMap(_.materialize())
    }
  }

  def merge(rhs: ComponentApiResolver): ComponentApiResolver =
    ComponentApiResolver.create(
      (members ++ rhs.members).distinct,
      (providers ++ rhs.providers).distinct,
      policy.and(rhs.policy)
    )

  private def _filter_policy[S](
    candidates: Vector[ComponentApiCandidate[S]],
    selector: ComponentSelector
  ): Consequence[Vector[ComponentApiCandidate[S]]] =
    candidates.foldLeft(Consequence.success(Vector.empty[ComponentApiCandidate[S]])) { (result, candidate) =>
      result.flatMap { xs =>
        policy.accept(candidate.metadata, selector).map(accepted => if (accepted) xs :+ candidate else xs)
      }
    }

  private def _select[S](
    candidates: Vector[ComponentApiCandidate[S]],
    selector: ComponentSelector,
    contract: String
  ): Consequence[ComponentApiCandidate[S]] = {
    val prioritized = candidates match {
      case Vector() => Vector.empty
      case xs =>
        val priority = xs.map(_.metadata.priority).max
        xs.filter(_.metadata.priority == priority)
    }
    val selected = prioritized match {
      case xs if xs.size <= 1 => xs
      case xs =>
        val declared = xs.filter(_.metadata.isDefault)
        if (declared.nonEmpty) declared
        else xs.filter(x => _canonical(x.metadata.instanceId.instance) == _canonical("default")) match {
          case Vector() => xs
          case defaults => defaults
        }
    }
    selected match {
      case Vector(member) => Consequence.success(member)
      case Vector() => Consequence.serviceUnavailable(s"component API provider not found: contract=${contract}, selector=${selector}")
      case xs => Consequence.serviceUnavailable(s"ambiguous component API providers: contract=${contract}, selector=${selector}, candidates=${xs.size}")
    }
  }

  private def _matches(metadata: SpiMemberMetadata, selector: ComponentSelector): Boolean =
    metadata.healthStatus.toLowerCase != "error" &&
      selector.component.forall(x => _canonical(x) == _canonical(metadata.component)) &&
      selector.instance.forall(x => _canonical(x) == _canonical(metadata.instanceId.instance)) &&
      selector.purpose.forall(x => metadata.purposes.exists(_canonical(_) == _canonical(x))) &&
      selector.capabilities.forall(x => metadata.capabilities.exists(_canonical(_) == _canonical(x))) &&
      selector.tags.forall(x => metadata.tags.exists(_canonical(_) == _canonical(x)))

  private def _canonical(value: String): String =
    ComponentInstanceId("selector", value).canonicalKey

  private def _member_key(metadata: SpiMemberMetadata): String =
    s"${metadata.contract}/${metadata.instanceId.canonicalKey}"
}

object ComponentApiResolver {
  val empty: ComponentApiResolver = apply(Vector.empty)

  def apply(
    members: Vector[ResolvedSpiMember[?]],
    policy: ComponentSelectionPolicy = ComponentSelectionPolicy.allowAll
  ): ComponentApiResolver =
    create(members, Vector.empty, policy)

  private[spi] def withProviders(
    members: Vector[ResolvedSpiMember[?]],
    providers: Vector[ComponentApiProvider],
    policy: ComponentSelectionPolicy = ComponentSelectionPolicy.allowAll
  ): ComponentApiResolver =
    create(members, providers, policy)

  private def create(
    members: Vector[ResolvedSpiMember[?]],
    providers: Vector[ComponentApiProvider],
    policy: ComponentSelectionPolicy
  ): ComponentApiResolver =
    new ComponentApiResolver(members, providers, policy)
}

private[spi] final case class ComponentApiProvider(
  metadata: SpiMemberMetadata,
  provider: SpiProvider[?],
  selection: SpiSelection = SpiSelection()
)

private final case class ComponentApiCandidate[S](
  metadata: SpiMemberMetadata,
  materialize: () => Consequence[ResolvedSpiMember[S]]
)

final case class SpiSocketBinding[S](
  socket: SpiSocket[S],
  provider: SpiProvider[S],
  contract: SpiContract[S],
  selection: SpiSelection
) {
  def install()(using ExecutionContext): Consequence[Unit] =
    provider.provide(contract, selection).map { spi =>
      socket.installSpi(spi)
    }
}

final case class SpiProviderSelector(
  component: Option[String] = None,
  service: Option[String] = None,
  instance: Option[String] = None
)

final case class SpiSocketSelector(
  component: Option[String] = None,
  contract: String,
  instance: Option[String] = None,
  name: Option[String] = None,
  cardinality: SpiCardinality = SpiCardinality.One
)

final case class SpiRuntimeBinding(
  socket: SpiSocketSelector,
  provider: SpiProviderSelector,
  selection: SpiSelection = SpiSelection()
)
