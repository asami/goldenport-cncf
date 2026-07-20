package org.goldenport.cncf.mcp.client

import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, PortApi, ServiceContract, VariationPoint, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.{Cause, Descriptor}

/*
 * Runtime-owned MCP client Port and transport boundary.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class McpClientRequirement(
  serverSetId: McpServerSetId
)

/** Provider-neutral service installed in a consumer Component.Port. */
abstract class McpClientService {
  def serverSetId: McpServerSetId
  def catalog(using ExecutionContext): Consequence[McpClientCatalog]
  def withInvocation[A](
    body: McpClientInvocation => Consequence[A]
  )(using ExecutionContext): Consequence[A]
}

/** One consumer invocation scope carrying bounded MCP tool-call state. */
abstract class McpClientInvocation {
  def catalog(using ExecutionContext): Consequence[McpClientCatalog]
  def invoke(call: McpClientCall)(using ExecutionContext): Consequence[McpClientResult]
}

/** Runtime-internal protocol boundary implemented by an admitted transport. */
abstract class McpClientTransport extends AutoCloseable {
  def initialize(
    server: McpClientServer,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[Unit]
  def listTools(
    server: McpClientServer,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[Vector[McpClientTool]]
  def callTool(
    server: McpClientServer,
    call: McpClientCall,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[McpClientResult]

  def close(): Unit = ()
}

final case class McpClientTransportRequirement(
  serverSetId: McpServerSetId
)

object McpClientPortApi extends PortApi[McpClientRequirement, McpClientService] {
  private val _contract_prefix = "mcp-client/"

  def resolve(req: McpClientRequirement): Consequence[ServiceContract[McpClientService]] =
    Consequence.success(ServiceContract(
      s"${_contract_prefix}${req.serverSetId.print}",
      classOf[McpClientService]
    ))

  private[client] def serverSetId(contract: ServiceContract[McpClientService]): Option[McpServerSetId] =
    Option(contract.name)
      .filter(_.startsWith(_contract_prefix))
      .flatMap(x => McpServerSetId.parseC(x.substring(_contract_prefix.length)).toOption)
}

object McpClientTransportPortApi
  extends PortApi[McpClientTransportRequirement, McpClientTransport] {
  private val _contract_prefix = "mcp-client-transport/"

  def resolve(req: McpClientTransportRequirement): Consequence[ServiceContract[McpClientTransport]] =
    Consequence.success(ServiceContract(
      s"${_contract_prefix}${req.serverSetId.print}",
      classOf[McpClientTransport]
    ))

  private[client] def serverSetId(contract: ServiceContract[McpClientTransport]): Option[McpServerSetId] =
    Option(contract.name)
      .filter(_.startsWith(_contract_prefix))
      .flatMap(x => McpServerSetId.parseC(x.substring(_contract_prefix.length)).toOption)
}

object McpClientSelectionPoint extends VariationPoint[McpClientRequirement] {
  def current(req: McpClientRequirement)(using ExecutionContext): Consequence[VariationSelection] =
    Consequence.success(VariationSelection())

  def inject(
    req: McpClientRequirement,
    selection: VariationSelection
  )(using ExecutionContext): Consequence[McpClientRequirement] =
    _require_runtime_selection(selection).map(_ => req)

  private def _require_runtime_selection(selection: VariationSelection): Consequence[Unit] =
    if (selection == VariationSelection())
      Consequence.unit
    else
      Consequence.argumentPolicyViolation(
        "selection",
        "mcp-client.runtime-owned-selection",
        "empty caller selection",
        "infrastructure selector"
      )
}

object McpClientTransportSelectionPoint extends VariationPoint[McpClientTransportRequirement] {
  def current(req: McpClientTransportRequirement)(using ExecutionContext): Consequence[VariationSelection] =
    Consequence.success(VariationSelection())

  def inject(
    req: McpClientTransportRequirement,
    selection: VariationSelection
  )(using ExecutionContext): Consequence[McpClientTransportRequirement] =
    if (selection == VariationSelection())
      Consequence.success(req)
    else
      Consequence.argumentPolicyViolation(
        "selection",
        "mcp-client.runtime-owned-transport",
        "empty caller selection",
        "transport selector"
      )
}

/** Registry created by runtime assembly after transport selection. */
final class McpClientRuntimeRegistry private (
  private val _services: Map[McpServerSetId, McpClientService]
) extends AutoCloseable {
  private val _lifecycle_monitor = new Object()
  private var _closed = false

  def serverSetIds: Vector[McpServerSetId] =
    _services.keys.toVector.sortBy(_.print)

  def resolve(serversetid: McpServerSetId): Consequence[McpClientService] =
    _lifecycle_monitor.synchronized {
      if (_closed)
        _lifecycle_failure("registry-closed")
      else
        _services.get(serversetid) match {
          case Some(service) => Consequence.success(service)
          case None => Consequence.serviceUnavailable(s"MCP client server set is unavailable: ${serversetid.print}")
        }
    }

  def extensionPoint: ExtensionPoint[McpClientService] =
    new ExtensionPoint[McpClientService] {
      def supports(
        contract: ServiceContract[McpClientService],
        variation: VariationSelection
      )(using ExecutionContext): Boolean =
        variation == VariationSelection() && _is_open_contract(contract)

      def provide(
        contract: ServiceContract[McpClientService],
        variation: VariationSelection
      )(using ExecutionContext): Consequence[McpClientService] =
        McpClientPortApi.serverSetId(contract) match {
          case Some(serversetid) => resolve(serversetid)
          case None => Consequence.serviceUnavailable(s"Invalid MCP client contract: ${contract.name}")
        }
    }

  def binding: Component.Binding[McpClientRequirement, McpClientService] =
    Component.Binding(Port(
      api = McpClientPortApi,
      spi = Vector(extensionPoint),
      variation = McpClientSelectionPoint
    ))

  def close(): Unit = {
    val services = _lifecycle_monitor.synchronized {
      if (_closed)
        Vector.empty
      else {
        _closed = true
        _services.toVector.sortBy(_._1.print).map(_._2)
      }
    }
    var failure: Option[Throwable] = None
    services.foreach {
      case service: DefaultMcpClientService =>
        try service.close()
        catch {
          case NonFatal(e) if failure.isEmpty => failure = Some(e)
          case NonFatal(_) => ()
        }
      case _ => ()
    }
    failure.foreach(throw _)
  }

  private def _is_open_contract(contract: ServiceContract[McpClientService]): Boolean =
    _lifecycle_monitor.synchronized {
      !_closed && McpClientPortApi.serverSetId(contract).exists(_services.contains)
    }

  private def _lifecycle_failure[A](reason: String): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP client registry is closed",
      Cause.Kind.Exhaustion,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.lifecycle")
      )
    )
}

object McpClientRuntimeRegistry {
  def createC(
    serversets: Vector[McpClientServerSet],
    transportbinding: Component.Binding[McpClientTransportRequirement, McpClientTransport]
  )(using ExecutionContext): Consequence[McpClientRuntimeRegistry] = {
    val identities = serversets.map(_.id)
    if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "serverSets",
        "mcp-client.registry",
        "unique server-set identities",
        "duplicate"
      )
    else
      _bind_transports_c(serversets.sortBy(_.id.print), transportbinding, Vector.empty).map { transports =>
        val services = transports.map { case (serverset, transport) =>
          serverset.id -> new DefaultMcpClientService(serverset, transport)
        }
        new McpClientRuntimeRegistry(services.toMap)
      }
  }

  private def _bind_transports_c(
    remaining: Vector[McpClientServerSet],
    transportbinding: Component.Binding[McpClientTransportRequirement, McpClientTransport],
    accumulated: Vector[(McpClientServerSet, McpClientTransport)]
  )(using ExecutionContext): Consequence[Vector[(McpClientServerSet, McpClientTransport)]] =
    remaining.headOption match {
      case None => Consequence.success(accumulated)
      case Some(serverset) =>
        transportbinding.bind(McpClientTransportRequirement(serverset.id)) match {
          case Consequence.Success(transport) =>
            _bind_transports_c(remaining.tail, transportbinding, accumulated :+ (serverset -> transport))
          case Consequence.Failure(primary) =>
            _rollback_transports_c(accumulated.map(_._2)) match {
              case Consequence.Success(_) => Consequence.Failure(primary)
              case Consequence.Failure(cleanup) => Consequence.Failure(cleanup ++ primary)
            }
        }
    }

  private def _rollback_transports_c(
    transports: Vector[McpClientTransport]
  ): Consequence[Unit] = {
    val failures = transports.reverse.flatMap { transport =>
      try {
        transport.close()
        None
      } catch {
        case NonFatal(e) => Some(Conclusion.from(e))
      }
    }
    failures.reduceOption(_ ++ _).map(Consequence.Failure(_)).getOrElse(Consequence.unit)
  }
}

final class DefaultMcpClientService private[client] (
  serverset: McpClientServerSet,
  transport: McpClientTransport
) extends McpClientService with AutoCloseable {
  private enum LifecycleState {
    case Open, Closing, Closed
  }

  private val _lifecycle_monitor = new Object()
  private val _close_monitor = new Object()
  private var _catalog: Option[McpClientCatalog] = None
  private var _lifecycle_state = LifecycleState.Open
  private var _active_threads = Map.empty[Thread, Int]
  private var _transport_closed = false

  def serverSetId: McpServerSetId = serverset.id

  def catalog(using ExecutionContext): Consequence[McpClientCatalog] =
    McpClientObservability.catalog(serverset.id) {
      _with_service_call_c(_catalog_c)
    }

  private def _catalog_c(using ExecutionContext): Consequence[McpClientCatalog] = synchronized {
    _catalog match {
      case Some(catalog) => Consequence.success(catalog)
      case None =>
        _load_catalog.map { catalog =>
          _catalog = Some(catalog)
          catalog
        }
    }
  }

  private def _load_catalog(using ExecutionContext): Consequence[McpClientCatalog] =
    serverset.servers.foldLeft(Consequence.success(Vector.empty[McpClientTool])) {
      case (z, server) =>
        for {
          xs <- z
          _ <- transport.initialize(server, serverset.limits)
          tools <- transport.listTools(server, serverset.limits)
        } yield xs ++ tools
    }.flatMap(McpClientCatalog.createC(serverset, _))

  def withInvocation[A](
    body: McpClientInvocation => Consequence[A]
  )(using ExecutionContext): Consequence[A] =
    _ensure_open_c.flatMap { _ =>
      val invocation = new _Invocation()
      try body(invocation)
      finally invocation.close()
    }

  private final class _Invocation extends McpClientInvocation with AutoCloseable {
    private var _calls = 0
    private var _active = 0
    private var _closed = false

    def catalog(using ExecutionContext): Consequence[McpClientCatalog] =
      DefaultMcpClientService.this.catalog

    def invoke(call: McpClientCall)(using ExecutionContext): Consequence[McpClientResult] =
      McpClientObservability.invoke(serverset.id, call.toolIdentity) {
        _with_service_call_c {
          DefaultMcpClientService.this._catalog_c.flatMap { current =>
            current.tool(call.toolIdentity) match {
              case Some(tool) =>
                tool.validateArgumentsC(call.arguments).flatMap { _ =>
                  serverset.servers.find(_.id == call.toolIdentity.serverId) match {
                    case Some(server) =>
                      _admit_c.flatMap { _ =>
                        try transport.callTool(server, call, serverset.limits)
                        finally _release()
                      }
                    case None => Consequence.operationNotFound(s"MCP server not admitted: ${call.toolIdentity.serverId.print}")
                    }
                }
              case None => Consequence.operationNotFound(s"MCP tool not admitted: ${call.toolIdentity.print}")
            }
          }
        }
      }

    private def _admit_c: Consequence[Unit] = synchronized {
      if (_closed)
        _limit_failure("invocation-closed", 0L, 1L)
      else if (_calls >= serverset.limits.maximumCalls)
        _limit_failure("maximum-calls", serverset.limits.maximumCalls.toLong, _calls.toLong + 1L)
      else if (_active >= serverset.limits.maximumConcurrency)
        _limit_failure("maximum-concurrency", serverset.limits.maximumConcurrency.toLong, _active.toLong + 1L)
      else {
        _calls += 1
        _active += 1
        Consequence.unit
      }
    }

    private def _release(): Unit = synchronized {
      _active -= 1
    }

    private def _limit_failure(
      reason: String,
      limit: Long,
      actual: Long
    ): Consequence.Failure[Unit] =
      Consequence.operationInvalid(
        "MCP client invocation limit exceeded",
        Cause.Kind.Limit,
        Vector(
          Descriptor.Facet.Reason(reason),
          Descriptor.Facet.Policy("mcp-client.limits"),
          Descriptor.Facet.Limit(limit),
          Descriptor.Facet.Actual(actual)
        )
      )

    def close(): Unit = synchronized {
      _closed = true
    }
  }

  def close(): Unit =
    _close_monitor.synchronized {
      val active = _lifecycle_monitor.synchronized {
        _lifecycle_state match {
          case LifecycleState.Closed => Vector.empty
          case LifecycleState.Open | LifecycleState.Closing =>
            _lifecycle_state = LifecycleState.Closing
            _active_threads.keys.toVector
        }
      }
      if (!_transport_closed) {
        val current = Thread.currentThread()
        active.filterNot(_ eq current).foreach(_.interrupt())
        var interrupted = false
        _lifecycle_monitor.synchronized {
          while (_active_threads.keys.exists(_ ne current))
            try _lifecycle_monitor.wait()
            catch {
              case _: InterruptedException => interrupted = true
            }
        }
        try transport.close()
        finally {
          _lifecycle_monitor.synchronized {
            _transport_closed = true
            _lifecycle_state = LifecycleState.Closed
            _lifecycle_monitor.notifyAll()
          }
          if (interrupted)
            current.interrupt()
        }
      }
    }

  private def _with_service_call_c[A](body: => Consequence[A]): Consequence[A] =
    _admit_service_call_c.flatMap { _ =>
      try body
      finally _release_service_call()
    }

  private def _admit_service_call_c: Consequence[Unit] =
    _lifecycle_monitor.synchronized {
      _lifecycle_state match {
        case LifecycleState.Open =>
          val thread = Thread.currentThread()
          _active_threads = _active_threads.updated(thread, _active_threads.getOrElse(thread, 0) + 1)
          Consequence.unit
        case LifecycleState.Closing => _lifecycle_failure("service-closing")
        case LifecycleState.Closed => _lifecycle_failure("service-closed")
      }
    }

  private def _release_service_call(): Unit =
    _lifecycle_monitor.synchronized {
      val thread = Thread.currentThread()
      _active_threads.get(thread) match {
        case Some(count) if count > 1 => _active_threads = _active_threads.updated(thread, count - 1)
        case Some(_) => _active_threads -= thread
        case None => ()
      }
      _lifecycle_monitor.notifyAll()
    }

  private def _ensure_open_c: Consequence[Unit] =
    _lifecycle_monitor.synchronized {
      _lifecycle_state match {
        case LifecycleState.Open => Consequence.unit
        case LifecycleState.Closing => _lifecycle_failure("service-closing")
        case LifecycleState.Closed => _lifecycle_failure("service-closed")
      }
    }

  private def _lifecycle_failure[A](reason: String): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP client service is unavailable during shutdown",
      Cause.Kind.Exhaustion,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.lifecycle")
      )
    )
}
