package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint}
import org.goldenport.cncf.context.ExecutionContext

/*
 * Resolves SPI sockets from already loaded components.
 *
 * v1 deliberately does not load remote components. Providers must already be
 * present in the repository/subsystem component set or in a component Port
 * registry populated from an existing dependency.
 *
 * @since   Jul.  2, 2026
 * @version Jul.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object SpiResolver {
  def resolve(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Consequence[Vector[Component]] = {
    val providers = _providers(components)
    val sockets = components.collect {
      case socket: SpiSocket[?] if !socket.isSpiInstalled =>
        _SocketSlot(socket.asInstanceOf[Component], socket)
    }
    sockets.foldLeft(Consequence.success(())) { (r, socket) =>
      r.flatMap(_ => _install(socket, providers, bindings))
    }.map(_ => components)
  }

  def resolveOrRaise(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Vector[Component] =
    resolve(components, bindings) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
    }

  private def _providers(
    components: Vector[Component]
  ): Vector[_ProviderSlot] =
    components.flatMap { component =>
      val componentproviders = component match {
        case m: SpiProviderComponent => m.spiProviders
        case _ => Vector.empty
      }
      val portproviders = component.port.entries.collect {
        case m: ExtensionPoint[?] => m.asInstanceOf[SpiProvider[?]]
      }
      val directproviders = component.port.entries.map(_DirectSpiProvider(_))
      (componentproviders ++ portproviders ++ directproviders).map(_ProviderSlot(component, _))
    }

  private def _install(
    socket: _SocketSlot,
    providers: Vector[_ProviderSlot],
    bindings: Vector[SpiRuntimeBinding]
  )(using ExecutionContext): Consequence[Unit] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    _binding(socket, contract, bindings).flatMap { binding =>
      val selection = _selection(rawsocket.spiSelection, binding)
      val candidates = providers.collect {
        case provider if _provider_matches_binding(provider, binding) &&
            provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection) =>
          provider.provider.asInstanceOf[SpiProvider[Any]]
      }
      _install_candidates(rawsocket, contract, selection, candidates)
    }
  }

  private def _install_candidates(
    socket: SpiSocket[?],
    contract: SpiContract[Any],
    selection: SpiSelection,
    candidates: Vector[SpiProvider[Any]]
  )(using ExecutionContext): Consequence[Unit] =
    candidates match {
      case Vector(provider) =>
        provider.provide(contract, selection).map { service =>
          socket.asInstanceOf[SpiSocket[Any]].installSpi(service)
        }
      case Vector() =>
        Consequence.serviceUnavailable(
          s"SPI provider not found: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, selection=$selection"
        )
      case xs =>
        Consequence.serviceUnavailable(
          s"ambiguous SPI providers: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, selection=$selection, candidates=${xs.size}"
        )
    }

  private def _binding(
    socket: _SocketSlot,
    contract: SpiContract[Any],
    bindings: Vector[SpiRuntimeBinding]
  ): Consequence[Option[SpiRuntimeBinding]] = {
    val matches = bindings.filter { binding =>
      _matches_component(binding.socket.component, _component_name(socket.component)) &&
        binding.socket.contract == contract.name
    }
    matches match {
      case Vector() => Consequence.success(None)
      case Vector(binding) =>
        binding.provider.service match {
          case Some(_) =>
            Consequence.resourceInvalid("SPI provider service binding is not supported yet")
          case None =>
            Consequence.success(Some(binding))
        }
      case _ =>
        Consequence.serviceUnavailable(
          s"ambiguous SPI bindings: component=${_component_name(socket.component)}, contract=${contract.name}, bindings=${matches.size}"
        )
    }
  }

  private def _provider_matches_binding(
    provider: _ProviderSlot,
    binding: Option[SpiRuntimeBinding]
  ): Boolean =
    binding.forall { binding =>
      _matches_component(binding.provider.component, _component_name(provider.component))
    }

  private def _matches_component(
    expected: Option[String],
    actual: String
  ): Boolean =
    expected.forall(_.trim == actual.trim)

  private def _selection(
    socket: SpiSelection,
    binding: Option[SpiRuntimeBinding]
  ): SpiSelection =
    binding.map(_.selection).map { selection =>
      SpiSelection(
        provider = selection.provider.orElse(socket.provider),
        mode = selection.mode.orElse(socket.mode),
        engine = selection.engine.orElse(socket.engine)
      )
    }.getOrElse(socket)

  private def _component_name(
    component: Component
  ): String =
    component.coreOption.map(_.name).getOrElse(component.getClass.getSimpleName)

  private final case class _SocketSlot(
    component: Component,
    socket: SpiSocket[?]
  )

  private final case class _ProviderSlot(
    component: Component,
    provider: SpiProvider[?]
  )

  private final case class _DirectSpiProvider(
    service: Any
  ) extends SpiProvider[Any] {
    def supports(
      contract: SpiContract[Any],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.runtimeClass.isInstance(service)

    def provide(
      contract: SpiContract[Any],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[Any] =
      Consequence.success(service)
  }
}
