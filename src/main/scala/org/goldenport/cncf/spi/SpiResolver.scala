package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInstanceId, ExtensionPoint}
import org.goldenport.cncf.context.ExecutionContext

/*
 * Resolves SPI sockets from already loaded components.
 *
 * v1 deliberately does not load remote components. Providers must already be
 * present in the repository/subsystem component set or in a component Port
 * registry populated from an existing dependency.
 *
 * @since   Jul.  2, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object SpiResolver {
  def resolve(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Consequence[Vector[Component]] = {
    val providers = _providers(components)
    val sockets = _sockets(components)
    _validate_runtime_bindings(sockets, bindings).flatMap { _ =>
      sockets.filterNot(_.socket.isSpiInstalled).foldLeft(Consequence.success(())) { (r, socket) =>
        r.flatMap(_ => _install(socket, providers, bindings))
      }.map(_ => components)
    }
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
  ): Vector[ProviderSlot] =
    components.flatMap { component =>
      val componentproviders = component match {
        case m: SpiProviderComponent => m.spiProviders
        case _ => Vector.empty
      }
      val outputentries = component.port.outputEntries
      val portproviders = outputentries.collect {
        case m: ExtensionPoint[?] => m.asInstanceOf[SpiProvider[?]]
      }
      val directproviders = outputentries.collect {
        case _: ExtensionPoint[?] => None
        case _: SpiSocket[?] => None
        case service => Some(DirectSpiProvider(service))
      }.flatten
      (componentproviders ++ portproviders ++ directproviders).map(ProviderSlot(component, _))
    }

  private def _sockets(
    components: Vector[Component]
  ): Vector[SocketSlot] =
    components.flatMap { component =>
      val direct = component match {
        case socket: SpiSocket[?] => Vector(socket)
        case _ => Vector.empty
      }
      val inputs = component.port.inputEntries.collect {
        case socket: SpiSocket[?] => socket
      }
      (direct ++ inputs)
        .map(SocketSlot(component, _))
    }.foldLeft(Vector.empty[SocketSlot]) { (slots, slot) =>
      if (slots.exists(existing => (existing.component eq slot.component) && (existing.socket eq slot.socket))) slots
      else slots :+ slot
    }

  private def _validate_runtime_bindings(
    sockets: Vector[SocketSlot],
    bindings: Vector[SpiRuntimeBinding]
  ): Consequence[Unit] =
    bindings.foldLeft(Consequence.success(())) { (result, binding) =>
      result.flatMap { _ =>
        val matches = sockets.filter(_socket_matches_selector(_, binding.socket))
        matches match {
          case Vector(_) => Consequence.success(())
          case Vector() =>
            Consequence.serviceUnavailable(s"SPI socket not found: ${_socket_selector_display(binding.socket)}")
          case xs =>
            Consequence.serviceUnavailable(
              s"ambiguous SPI sockets: ${_socket_selector_display(binding.socket)}, candidates=${xs.size}"
            )
        }
      }
    }

  private def _install(
    socket: SocketSlot,
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding]
  )(using ExecutionContext): Consequence[Unit] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    _binding(socket, contract, bindings).flatMap { binding =>
      val selection = _selection(rawsocket.spiSelection, binding)
      val candidates = providers.filter { provider =>
        _provider_matches_binding(provider, binding) &&
          provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection)
      }
      _select_default_instance(binding, candidates).flatMap { selected =>
        _install_candidates(socket, contract, selection, selected, binding)
      }
    }
  }

  private def _install_candidates(
    socket: SocketSlot,
    contract: SpiContract[Any],
    selection: SpiSelection,
    candidates: Vector[ProviderSlot],
    binding: Option[SpiRuntimeBinding]
  )(using ExecutionContext): Consequence[Unit] =
    candidates match {
      case Vector(provider) =>
        provider.provider.asInstanceOf[SpiProvider[Any]].provide(contract, selection).map { service =>
          val metadata = SpiTraceMetadata(
            contract = contract.name,
            operation = "install",
            socketComponent = _component_name(socket.component),
            providerComponent = _component_name(provider.component),
            selectionProvider = selection.provider,
            selectionMode = selection.mode,
            selectionEngine = selection.engine
          )
          socket.socket.asInstanceOf[SpiSocket[Any]].installSpi(SpiTraceSupport.wrapInstalled(service, metadata))
        }
      case Vector() =>
        Consequence.serviceUnavailable(
          s"SPI provider not found: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
            s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection"
        )
      case xs =>
        Consequence.serviceUnavailable(
          s"ambiguous SPI providers: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
            s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection, candidates=${xs.size}"
        )
    }

  private def _binding(
    socket: SocketSlot,
    contract: SpiContract[Any],
    bindings: Vector[SpiRuntimeBinding]
  ): Consequence[Option[SpiRuntimeBinding]] = {
    val matches = bindings.filter { binding =>
      _socket_matches_selector(socket, binding.socket) &&
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
          s"ambiguous SPI bindings: component=${_component_type(socket.component)}, " +
            s"instance=${_component_instance(socket.component).getOrElse("unknown")}, " +
            s"socket=${socket.socket.spiSocketName}, contract=${contract.name}, bindings=${matches.size}"
        )
    }
  }

  private def _provider_matches_binding(
    provider: ProviderSlot,
    binding: Option[SpiRuntimeBinding]
  ): Boolean =
    binding.forall { binding =>
      _matches_component(binding.provider.component, _component_type(provider.component)) &&
        _matches_instance(binding.provider.instance, provider.component)
    }

  private def _select_default_instance(
    binding: Option[SpiRuntimeBinding],
    candidates: Vector[ProviderSlot]
  ): Consequence[Vector[ProviderSlot]] =
    binding match {
      case Some(runtimebinding)
          if runtimebinding.provider.component.nonEmpty && runtimebinding.provider.instance.isEmpty =>
        val groups = candidates.groupBy(_provider_instance_group_key)
        if (groups.size <= 1) {
          Consequence.success(candidates)
        } else {
          val declareddefaults = groups.filter { case (_, xs) => xs.exists(_.component.instanceMetadata.exists(_.isDefault)) }
          val defaults =
            if (declareddefaults.nonEmpty) declareddefaults
            else groups.filter { case (_, xs) => xs.exists(x => _component_instance(x.component).contains("default")) }
          defaults.toVector match {
            case Vector((_, selected)) => Consequence.success(selected)
            case Vector() => Consequence.success(candidates)
            case xs =>
              Consequence.serviceUnavailable(
                s"ambiguous default SPI provider instances: ${_provider_selector_display(runtimebinding)}, defaults=${xs.size}"
              )
          }
        }
      case _ => Consequence.success(candidates)
    }

  private def _provider_instance_group_key(provider: ProviderSlot): String =
    _logical_instance_id(provider.component)
      .map(_.canonicalKey)
      .getOrElse(s"uninitialized:${System.identityHashCode(provider.component)}")

  private def _socket_matches_selector(
    socket: SocketSlot,
    selector: SpiSocketSelector
  ): Boolean =
    selector.contract == socket.socket.spiContract.name &&
      _matches_component(selector.component, _component_type(socket.component)) &&
      _matches_instance(selector.instance, socket.component) &&
      selector.name.forall { name =>
        ComponentInstanceId("socket", name).canonicalKey ==
          ComponentInstanceId("socket", socket.socket.spiSocketName).canonicalKey
      }

  private def _matches_component(
    expected: Option[String],
    actual: String
  ): Boolean =
    expected.forall { value =>
      ComponentInstanceId(value.trim, "default").canonicalKey ==
        ComponentInstanceId(actual.trim, "default").canonicalKey
    }

  private def _matches_instance(
    expected: Option[String],
    component: Component
  ): Boolean =
    expected.forall { value =>
      _logical_instance_id(component).exists { instanceid =>
        ComponentInstanceId(_component_type(component), value).canonicalKey == instanceid.canonicalKey
      }
    }

  private def _logical_instance_id(component: Component): Option[ComponentInstanceId] =
    component.instanceMetadata.map(_.instanceId)
      .orElse(component.coreOption.map(_.instanceId))

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

  private def _component_type(
    component: Component
  ): String =
    component.instanceMetadata.map(_.componentName)
      .orElse(component.artifactMetadata.flatMap(_.component))
      .getOrElse(_component_name(component))

  private def _component_instance(
    component: Component
  ): Option[String] =
    component.instanceMetadata.map(_.instance)
      .orElse(component.coreOption.map(_.instanceId.instance))

  private def _socket_selector_display(selector: SpiSocketSelector): String =
    s"component=${selector.component.getOrElse("*")}, instance=${selector.instance.getOrElse("*")}, " +
      s"name=${selector.name.getOrElse("*")}, contract=${selector.contract}"

  private def _provider_selector_display(binding: SpiRuntimeBinding): String =
    s"component=${binding.provider.component.getOrElse("*")}, instance=${binding.provider.instance.getOrElse("*")}"

  private final case class SocketSlot(
    component: Component,
    socket: SpiSocket[?]
  )

  private final case class ProviderSlot(
    component: Component,
    provider: SpiProvider[?]
  )

  private final case class DirectSpiProvider(
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
