package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentIdentityCompatibilityAdapter, ComponentInstanceId, ExtensionPoint}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.naming.NamingConventions

/*
 * Resolves SPI sockets from already loaded components.
 *
 * v1 deliberately does not load remote components. Providers must already be
 * present in the repository/subsystem component set or in a component Port
 * registry populated from an existing dependency.
 *
 * @since   Jul.  2, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object SpiResolver {
  final case class Resolution(
    components: Vector[Component],
    componentApiResolver: ComponentApiResolver
  )

  def resolve(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Consequence[Vector[Component]] =
    resolveAssembly(components, bindings).map(_.components)

  def resolveAssembly(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Consequence[Resolution] = {
    val providers = _providers(components)
    val singles = _single_sockets(components)
    val sets = _socket_sets(components)
    _validate_runtime_bindings(singles, sets, bindings, components).flatMap { _ =>
      _resolve_socket_bindings(singles, sets, providers, bindings, components)
    }.flatMap { resolvedbindings =>
      val installedsingles = singles.filterNot(_.socket.isSpiInstalled).foldLeft(
        Consequence.success(Vector.empty[ResolvedSpiMember[?]])
      ) { (result, socket) =>
        result.flatMap { members =>
          _install_single(socket, providers, bindings, components).map(_.fold(members)(members :+ _))
        }
      }
      installedsingles.flatMap { singlemembers =>
        val existingmembers = sets.filter(_.socket.isSpiInstalled).flatMap(_.socket.spiMembers)
        sets.filterNot(_.socket.isSpiInstalled).foldLeft(Consequence.success(singlemembers ++ existingmembers)) { (result, socket) =>
          result.flatMap { members =>
            _install_set(socket, providers, bindings, components).map(members ++ _)
          }
        }
      }.map { members =>
        Resolution(
          components,
          ComponentApiResolver._with_providers(
            members,
            providers.map(_component_api_provider),
            resolvedbindings
          )
        )
      }
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

  def resolveAssemblyOrRaise(
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  )(using ExecutionContext): Resolution =
    resolveAssembly(components, bindings) match {
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
      val componentapiproviders = component.componentApiProviders
      val outputentries = component.port.outputEntries
      val portproviders = outputentries.collect {
        case m: ExtensionPoint[?] => m.asInstanceOf[SpiProvider[?]]
      }
      val directproviders = outputentries.collect {
        case _: ExtensionPoint[?] => None
        case _: SpiSocket[?] => None
        case _: SpiSocketSet[?] => None
        case service => Some(DirectSpiProvider(service))
      }.flatten
      val providers = componentproviders ++ componentapiproviders ++ portproviders ++ directproviders
      providers.foldLeft(Vector.empty[SpiProvider[?]]) { (slots, provider) =>
        if (slots.exists(_ eq provider)) slots else slots :+ provider
      }.map(ProviderSlot(component, _))
    }

  private def _single_sockets(
    components: Vector[Component]
  ): Vector[SingleSocketSlot] =
    components.flatMap { component =>
      val direct = component match {
        case socket: SpiSocket[?] => Vector(socket)
        case _ => Vector.empty
      }
      val inputs = component.port.inputEntries.collect {
        case socket: SpiSocket[?] => socket
      }
      (direct ++ inputs)
        .map(SingleSocketSlot(component, _))
    }.foldLeft(Vector.empty[SingleSocketSlot]) { (slots, slot) =>
      if (slots.exists(existing => (existing.component eq slot.component) && (existing.socket eq slot.socket))) slots
      else slots :+ slot
    }

  private def _socket_sets(
    components: Vector[Component]
  ): Vector[SetSocketSlot] =
    components.flatMap { component =>
      val direct = component match {
        case socket: SpiSocketSet[?] => Vector(socket)
        case _ => Vector.empty
      }
      val inputs = component.port.inputEntries.collect {
        case socket: SpiSocketSet[?] => socket
      }
      (direct ++ inputs)
        .map(SetSocketSlot(component, _))
    }.foldLeft(Vector.empty[SetSocketSlot]) { (slots, slot) =>
      if (slots.exists(existing => (existing.component eq slot.component) && (existing.socket eq slot.socket))) slots
      else slots :+ slot
    }

  private def _validate_runtime_bindings(
    singles: Vector[SingleSocketSlot],
    sets: Vector[SetSocketSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  ): Consequence[Unit] =
    bindings.foldLeft(Consequence.success(())) { (result, binding) =>
      result.flatMap { _ =>
        _validate_component_selector(binding.socket.component, components).flatMap { _ =>
          val singlematches = singles.filter(_single_socket_matches_selector(_, binding.socket, components))
          val setmatches = sets.filter(_set_socket_matches_selector(_, binding.socket, components))
          val matches = singlematches.size + setmatches.size
          matches match {
            case 1 => Consequence.success(())
            case 0 =>
              Consequence.serviceUnavailable(s"SPI socket not found: ${_socket_selector_display(binding.socket)}")
            case size =>
              Consequence.serviceUnavailable(
                s"ambiguous SPI sockets: ${_socket_selector_display(binding.socket)}, candidates=${size}"
              )
          }
        }
      }
    }

  private def _resolve_socket_bindings(
    singles: Vector[SingleSocketSlot],
    sets: Vector[SetSocketSlot],
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Vector[ResolvedSpiBinding]] = {
    val singleresult = singles.foldLeft(Consequence.success(Vector.empty[ResolvedSpiBinding])) { (result, socket) =>
      result.flatMap { resolved =>
        _resolve_single_socket_binding(socket, providers, bindings, components).map(_.fold(resolved)(resolved :+ _))
      }
    }
    singleresult.flatMap { singlebindings =>
      sets.foldLeft(Consequence.success(singlebindings)) { (result, socket) =>
        result.flatMap { resolved =>
          _resolve_set_socket_bindings(socket, providers, bindings, components).map(resolved ++ _)
        }
      }
    }
  }

  private def _resolve_single_socket_binding(
    socket: SingleSocketSlot,
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Option[ResolvedSpiBinding]] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    _single_binding(socket, contract, bindings, components).flatMap { binding =>
      _validate_component_selector(binding.flatMap(_.provider.component), components).flatMap { _ =>
        val selection = _selection(rawsocket.spiSelection, binding)
        val candidates = providers.filter { provider =>
          _provider_matches_binding(provider, binding, components) &&
            _is_healthy(provider.component) &&
            provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection)
        }
        _select_default_instance(binding, candidates).flatMap {
          case Vector(provider) =>
            Consequence.success(Some(_resolved_binding(socket.component, rawsocket.spiSocketName, contract, provider, selection)))
          case Vector() if !rawsocket.spiRequired =>
            Consequence.success(None)
          case Vector() =>
            Consequence.serviceUnavailable(
              s"SPI provider not found: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
                s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection"
            )
          case xs =>
            Consequence.serviceUnavailable(
              s"ambiguous SPI providers: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
                s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection, " +
                s"candidates=${_candidate_display(xs)}"
            )
        }
      }
    }
  }

  private def _resolve_set_socket_bindings(
    socket: SetSocketSlot,
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Vector[ResolvedSpiBinding]] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    val matchedbindings = bindings.filter { binding =>
      _set_socket_matches_selector(socket, binding.socket, components) && binding.socket.contract == contract.name
    }
    val requests = if (matchedbindings.isEmpty) Vector(None) else matchedbindings.map(Some(_))
    requests.foldLeft(Consequence.success(Vector.empty[(ProviderSlot, SpiSelection)])) { (result, binding) =>
      result.flatMap { selected =>
        _validate_component_selector(binding.flatMap(_.provider.component), components).map { _ =>
          val selection = _selection(rawsocket.spiSelection, binding)
          val candidates = providers.filter { provider =>
            _provider_matches_binding(provider, binding, components) &&
              _is_healthy(provider.component) &&
              provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection)
          }
          selected ++ candidates.map(_ -> selection)
        }
      }
    }.flatMap { selected =>
      val duplicate = selected.groupBy(x => _provider_instance_group_key(x._1)).collectFirst {
        case (key, xs) if xs.size > 1 => key
      }
      duplicate match {
        case Some(key) =>
          Consequence.serviceUnavailable(s"duplicate SPI socket set provider instance: $key")
        case None if rawsocket.spiRequired && selected.isEmpty =>
          Consequence.serviceUnavailable(
            s"required SPI socket set is empty: contract=${contract.name}, socket=${rawsocket.spiSocketName}"
          )
        case None =>
          Consequence.success(selected.map { case (provider, selection) =>
            _resolved_binding(socket.component, rawsocket.spiSocketName, contract, provider, selection)
          })
      }
    }
  }

  private def _install_single(
    socket: SingleSocketSlot,
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Option[ResolvedSpiMember[Any]]] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    _single_binding(socket, contract, bindings, components).flatMap { binding =>
      val selection = _selection(rawsocket.spiSelection, binding)
      val candidates = providers.filter { provider =>
        _provider_matches_binding(provider, binding, components) &&
          _is_healthy(provider.component) &&
          provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection)
      }
      _select_default_instance(binding, candidates).flatMap { selected =>
        _install_single_candidates(socket, contract, selection, selected, binding)
      }
    }
  }

  private def _install_single_candidates(
    socket: SingleSocketSlot,
    contract: SpiContract[Any],
    selection: SpiSelection,
    candidates: Vector[ProviderSlot],
    binding: Option[SpiRuntimeBinding]
  )(using ExecutionContext): Consequence[Option[ResolvedSpiMember[Any]]] =
    candidates match {
      case Vector(provider) =>
        _provide_member(socket.component, socket.socket.spiSocketName, provider, contract, selection).map { member =>
          socket.socket.asInstanceOf[SpiSocket[Any]].installSpi(member.service)
          Some(member)
        }
      case Vector() if !socket.socket.spiRequired => Consequence.success(None)
      case Vector() =>
        Consequence.serviceUnavailable(
          s"SPI provider not found: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
            s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection"
        )
      case xs =>
        Consequence.serviceUnavailable(
          s"ambiguous SPI providers: contract=${contract.name}, runtimeClass=${contract.runtimeClass.getName}, " +
            s"provider=${binding.map(_provider_selector_display).getOrElse("automatic")}, selection=$selection, " +
            s"candidates=${_candidate_display(xs)}"
        )
    }

  private def _install_set(
    socket: SetSocketSlot,
    providers: Vector[ProviderSlot],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Vector[ResolvedSpiMember[?]]] = {
    val rawsocket = socket.socket
    val contract = rawsocket.spiContract.asInstanceOf[SpiContract[Any]]
    val matchedbindings = bindings.filter { binding =>
      _set_socket_matches_selector(socket, binding.socket, components) && binding.socket.contract == contract.name
    }
    val requests = if (matchedbindings.isEmpty) Vector(None) else matchedbindings.map(Some(_))
    requests.foldLeft(Consequence.success(Vector.empty[(ProviderSlot, SpiSelection)])) { (result, binding) =>
      result.map { selected =>
        val selection = _selection(rawsocket.spiSelection, binding)
        val candidates = providers.filter { provider =>
          _provider_matches_binding(provider, binding, components) &&
            _is_healthy(provider.component) &&
            provider.provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection)
        }
        selected ++ candidates.map(_ -> selection)
      }
    }.flatMap { selected =>
      val duplicate = selected.groupBy(x => _provider_instance_group_key(x._1)).collectFirst {
        case (key, xs) if xs.size > 1 => key
      }
      duplicate match {
        case Some(key) => Consequence.serviceUnavailable(s"duplicate SPI socket set provider instance: ${key}")
        case None =>
          selected.foldLeft(Consequence.success(Vector.empty[ResolvedSpiMember[Any]])) { case (result, (provider, selection)) =>
            result.flatMap { members =>
              _provide_member(socket.component, socket.socket.spiSocketName, provider, contract, selection).map(members :+ _)
            }
          }.flatMap { members =>
            val eligible = members.filterNot(_.metadata.healthStatus.equalsIgnoreCase("error"))
            if (rawsocket.spiRequired && eligible.isEmpty) {
              Consequence.serviceUnavailable(s"required SPI socket set is empty: contract=${contract.name}, socket=${rawsocket.spiSocketName}")
            } else {
              rawsocket.asInstanceOf[SpiSocketSet[Any]].installSpiMembers(members)
              Consequence.success(members)
            }
          }
      }
    }
  }

  private def _provide_member(
    socketcomponent: Component,
    socketname: String,
    provider: ProviderSlot,
    contract: SpiContract[Any],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[ResolvedSpiMember[Any]] = {
    val binding = _resolved_binding(socketcomponent, socketname, contract, provider, selection)
    val provided = provider.provider match {
      case bound: SpiBoundProvider[?] =>
        bound.asInstanceOf[SpiBoundProvider[Any]].provideBound(binding)
      case standard =>
        standard.asInstanceOf[SpiProvider[Any]].provide(contract, selection)
    }
    provided.map { service =>
      val trace = SpiTraceMetadata(
        contract = contract.name,
        operation = "install",
        socketComponent = _component_name(socketcomponent),
        providerComponent = _component_name(provider.component),
        socketName = Some(socketname),
        providerInstance = Some(_spi_member_metadata(provider.component, contract.name).instanceId.instance),
        selectionProvider = selection.provider,
        selectionMode = selection.mode,
        selectionEngine = selection.engine,
        selectionBasis = Some(SpiSelectionBasis.AssemblyBinding.name)
      )
      ResolvedSpiMember(
        SpiTraceSupport.wrapInstalled(service, trace),
        _spi_member_metadata(provider.component, contract.name)
      )
    }
  }

  private def _resolved_binding(
    socketcomponent: Component,
    socketname: String,
    contract: SpiContract[Any],
    provider: ProviderSlot,
    selection: SpiSelection
  ): ResolvedSpiBinding = {
    val metadata = _spi_member_metadata(provider.component, contract.name)
    val socketinstance = _logical_instance_id(socketcomponent)
      .map(_.instance)
      .orElse(_component_instance(socketcomponent))
    ResolvedSpiBinding(
      socket = Some(SpiSocketRef(
        component = _component_type(socketcomponent),
        name = socketname,
        contract = contract.name,
        instance = socketinstance
      )),
      provider = SpiProviderRef(metadata.component, metadata.instanceId, contract.name),
      metadata = metadata,
      selector = ComponentSelector(
        component = Some(metadata.component),
        instance = Some(metadata.instanceId.instance)
      ),
      selection = selection,
      selectionBasis = SpiSelectionBasis.AssemblyBinding,
      operations = provider.provider match {
        case operationprovider: SpiOperationProvider => operationprovider.spiOperations(contract.name)
        case _ => Vector.empty
      },
      _target_component = provider.component
    )
  }

  private def _spi_member_metadata(
    component: Component,
    contract: String
  ): SpiMemberMetadata = {
    val instancemetadata = component.instanceMetadata
    val health = component.healthSnapshot
    SpiMemberMetadata(
      contract = contract,
      component = _component_type(component),
      instanceId = _logical_instance_id(component).getOrElse(
        ComponentInstanceId(component.componentId, _component_instance(component).getOrElse("default"))
      ),
      purposes = instancemetadata.map(_.purposes.toSet).getOrElse(Set.empty),
      capabilities = instancemetadata.map(_.capabilities.toSet).getOrElse(Set.empty),
      tags = instancemetadata.map(_.tags.toSet).getOrElse(Set.empty),
      priority = instancemetadata.map(_.priority).getOrElse(0),
      isDefault = instancemetadata.exists(_.isDefault),
      healthStatus = health.status
    )
  }

  private def _component_api_provider(provider: ProviderSlot): ComponentApiProvider = {
    val component = provider.component
    ComponentApiProvider(
      _spi_member_metadata(component, ""),
      component,
      provider.provider
    )
  }

  private def _single_binding(
    socket: SingleSocketSlot,
    contract: SpiContract[Any],
    bindings: Vector[SpiRuntimeBinding],
    components: Vector[Component]
  ): Consequence[Option[SpiRuntimeBinding]] = {
    val matches = bindings.filter { binding =>
      _single_socket_matches_selector(socket, binding.socket, components) &&
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
    binding: Option[SpiRuntimeBinding],
    components: Vector[Component]
  ): Boolean =
    binding.forall { binding =>
      _matches_component(binding.provider.component, provider.component, components) &&
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

  private def _single_socket_matches_selector(
    socket: SingleSocketSlot,
    selector: SpiSocketSelector,
    components: Vector[Component]
  ): Boolean =
    !selector.cardinality.isMany &&
      selector.cardinality.isRequired == socket.socket.spiRequired &&
      _socket_identity_matches(socket.component, socket.socket.spiContract.name, socket.socket.spiSocketName, selector, components)

  private def _set_socket_matches_selector(
    socket: SetSocketSlot,
    selector: SpiSocketSelector,
    components: Vector[Component]
  ): Boolean =
    selector.cardinality.isMany &&
      selector.cardinality.isRequired == socket.socket.spiRequired &&
      _socket_identity_matches(socket.component, socket.socket.spiContract.name, socket.socket.spiSocketName, selector, components)

  private def _socket_identity_matches(
    component: Component,
    contract: String,
    socketname: String,
    selector: SpiSocketSelector,
    components: Vector[Component]
  ): Boolean =
    selector.contract == contract &&
      _matches_component(selector.component, component, components) &&
      _matches_instance(selector.instance, component) &&
      selector.name.forall(name => NamingConventions.equivalentByNormalized(name, socketname))

  private def _is_healthy(component: Component): Boolean =
    !component.healthSnapshot.status.equalsIgnoreCase("error")

  private def _validate_component_selector(
    expected: Option[String],
    components: Vector[Component]
  ): Consequence[Unit] =
    _validate_canonical_component_identities(components).flatMap { _ =>
      expected match {
        case Some(value) =>
          _resolve_logical_component_selector(value, components).toConsequence.map(_ => ())
        case None =>
          Consequence.success(())
      }
    }

  private def _validate_canonical_component_identities(
    components: Vector[Component]
  ): Consequence[Unit] =
    components.foldLeft(Consequence.success(())) { (result, component) =>
      result.flatMap { _ =>
        Option(component).flatMap(_.coreOption).map(_.componentId) match {
          case Some(componentid) if componentid != null =>
            Consequence.success(())
          case _ =>
            Consequence.componentInvalid(
              s"SPI component identity is not initialized: component=${Option(component).map(_.getClass.getName).getOrElse("null")}"
            )
        }
      }
    }

  private def _matches_component(
    expected: Option[String],
    component: Component,
    components: Vector[Component]
  ): Boolean =
    expected.forall { value =>
      _resolve_logical_component_selector(value, components) match {
        case resolved: ComponentIdentityCompatibilityAdapter.Canonical =>
          _logical_instance_id(component).exists(_.componentId == resolved.componentid)
        case resolved: ComponentIdentityCompatibilityAdapter.Adapted =>
          _logical_instance_id(component).exists(_.componentId == resolved.componentid)
        case _: ComponentIdentityCompatibilityAdapter.Rejected =>
          false
      }
    }

  private def _resolve_logical_component_selector(
    value: String,
    components: Vector[Component]
  ): ComponentIdentityCompatibilityAdapter.Result =
    ComponentIdentityCompatibilityAdapter.resolve(
      value.trim,
      components.flatMap(_logical_instance_id).map(_.componentId).distinct,
      ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
    )

  private def _matches_instance(
    expected: Option[String],
    component: Component
  ): Boolean =
    expected.forall(value => _logical_instance_id(component).exists(_.instance == value.trim))

  private def _logical_instance_id(component: Component): Option[ComponentInstanceId] =
    component.instanceMetadata.flatMap { metadata =>
      metadata.componentId.map(ComponentInstanceId(_, metadata.instance))
    }.orElse(component.coreOption.map { core =>
      ComponentInstanceId(
        core.componentId,
        component.instanceMetadata.map(_.instance).getOrElse(core.instanceId.instance)
      )
    })

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
      s"name=${selector.name.getOrElse("*")}, contract=${selector.contract}, cardinality=${selector.cardinality}"

  private def _provider_selector_display(binding: SpiRuntimeBinding): String =
    s"component=${binding.provider.component.getOrElse("*")}, instance=${binding.provider.instance.getOrElse("*")}"

  private def _candidate_display(
    candidates: Vector[ProviderSlot]
  ): String =
    candidates.map { candidate =>
      val component = candidate.component
      val identity = component.coreOption.map(_.instanceId.toString).getOrElse("uninitialized")
      s"${_component_name(component)}[$identity,${component.origin.label}]:${candidate.provider.getClass.getName}"
    }.mkString("[", ",", "]")

  private final case class SingleSocketSlot(
    component: Component,
    socket: SpiSocket[?]
  )

  private final case class SetSocketSlot(
    component: Component,
    socket: SpiSocketSet[?]
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
