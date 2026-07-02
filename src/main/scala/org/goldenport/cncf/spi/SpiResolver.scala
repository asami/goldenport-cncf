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
 * @version Jul.  2, 2026
 * @author  ASAMI, Tomoharu
 */
object SpiResolver {
  def resolve(
    components: Vector[Component]
  )(using ExecutionContext): Consequence[Vector[Component]] = {
    val providers = _providers(components)
    val sockets = components.collect {
      case socket: SpiSocket[?] if !socket.isSpiInstalled => socket
    }
    sockets.foldLeft(Consequence.success(())) { (r, socket) =>
      r.flatMap(_ => _install(socket, providers))
    }.map(_ => components)
  }

  def resolveOrRaise(
    components: Vector[Component]
  )(using ExecutionContext): Vector[Component] =
    resolve(components) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
    }

  private def _providers(
    components: Vector[Component]
  ): Vector[SpiProvider[?]] =
    components.flatMap { component =>
      val componentproviders = component match {
        case m: SpiProviderComponent => m.spiProviders
        case _ => Vector.empty
      }
      val portproviders = component.port.entries.collect {
        case m: ExtensionPoint[?] => m.asInstanceOf[SpiProvider[?]]
      }
      val directproviders = component.port.entries.map(_DirectSpiProvider(_))
      componentproviders ++ portproviders ++ directproviders
    }

  private def _install(
    socket: SpiSocket[?],
    providers: Vector[SpiProvider[?]]
  )(using ExecutionContext): Consequence[Unit] = {
    val contract = socket.spiContract.asInstanceOf[SpiContract[Any]]
    val selection = socket.spiSelection
    val candidates = providers.collect {
      case provider if provider.asInstanceOf[SpiProvider[Any]].supports(contract, selection) =>
        provider.asInstanceOf[SpiProvider[Any]]
    }
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
  }

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
