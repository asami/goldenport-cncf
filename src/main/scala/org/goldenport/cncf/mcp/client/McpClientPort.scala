package org.goldenport.cncf.mcp.client

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, PortApi, ServiceContract, VariationPoint, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext

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
  def invoke(call: McpClientCall)(using ExecutionContext): Consequence[McpClientResult]
}

/** Runtime-internal protocol boundary implemented by an admitted transport. */
abstract class McpClientTransport extends AutoCloseable {
  def initialize(server: McpClientServer)(using ExecutionContext): Consequence[Unit]
  def listTools(server: McpClientServer)(using ExecutionContext): Consequence[Vector[McpClientTool]]
  def callTool(
    server: McpClientServer,
    call: McpClientCall
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
  def serverSetIds: Vector[McpServerSetId] =
    _services.keys.toVector.sortBy(_.print)

  def resolve(serversetid: McpServerSetId): Consequence[McpClientService] =
    _services.get(serversetid) match {
      case Some(service) => Consequence.success(service)
      case None => Consequence.serviceUnavailable(s"MCP client server set is unavailable: ${serversetid.print}")
    }

  def extensionPoint: ExtensionPoint[McpClientService] =
    new ExtensionPoint[McpClientService] {
      def supports(
        contract: ServiceContract[McpClientService],
        variation: VariationSelection
      )(using ExecutionContext): Boolean =
        variation == VariationSelection() &&
          McpClientPortApi.serverSetId(contract).exists(_services.contains)

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

  def close(): Unit =
    _services.values.foreach {
      case service: DefaultMcpClientService => service.close()
      case _ => ()
    }
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
      serversets.sortBy(_.id.print).foldLeft(Consequence.success(Vector.empty[(McpServerSetId, McpClientService)])) {
        case (z, serverset) =>
          for {
            xs <- z
            transport <- transportbinding.bind(McpClientTransportRequirement(serverset.id))
          } yield xs :+ (serverset.id -> new DefaultMcpClientService(serverset, transport))
      }.map(xs => new McpClientRuntimeRegistry(xs.toMap))
  }
}

final class DefaultMcpClientService private[client] (
  serverset: McpClientServerSet,
  transport: McpClientTransport
) extends McpClientService with AutoCloseable {
  private var _catalog: Option[McpClientCatalog] = None

  def serverSetId: McpServerSetId = serverset.id

  def catalog(using ExecutionContext): Consequence[McpClientCatalog] = synchronized {
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
          _ <- transport.initialize(server)
          tools <- transport.listTools(server)
        } yield xs ++ tools
    }.flatMap(McpClientCatalog.createC(serverset, _))

  def invoke(call: McpClientCall)(using ExecutionContext): Consequence[McpClientResult] =
    catalog.flatMap { current =>
      current.tool(call.toolIdentity) match {
        case Some(_) =>
          serverset.servers.find(_.id == call.toolIdentity.serverId) match {
            case Some(server) => transport.callTool(server, call)
            case None => Consequence.operationNotFound(s"MCP server not admitted: ${call.toolIdentity.serverId.print}")
          }
        case None => Consequence.operationNotFound(s"MCP tool not admitted: ${call.toolIdentity.print}")
      }
    }

  def close(): Unit = transport.close()
}
