package org.goldenport.cncf.mcp.client

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeSecretResolver
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * Runtime-owned activation and lifecycle boundary for imported Codex MCP definitions.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class CodexMcpRuntimeAssembly private (
  registry: McpClientRuntimeRegistry
) extends AutoCloseable {
  def serverSetIds: Vector[McpServerSetId] =
    registry.serverSetIds

  def installC(component: Component): Consequence[Component] =
    registry.install(component)

  def close(): Unit =
    registry.close()
}

private[cncf] object CodexMcpRuntimeAssembly {
  def createC(
    source: Record,
    policy: CodexMcpImportPolicy
  )(using ExecutionContext): Consequence[CodexMcpRuntimeAssembly] =
    _create_c(source, policy, None, None)

  def createC(
    source: Record,
    policy: CodexMcpImportPolicy,
    secretresolver: RuntimeSecretResolver
  )(using ExecutionContext): Consequence[CodexMcpRuntimeAssembly] =
    _create_c(source, policy, Some(secretresolver), None)

  private[client] def createC(
    source: Record,
    policy: CodexMcpImportPolicy,
    exchangefactory: () => McpStreamableHttpExchange
  )(using ExecutionContext): Consequence[CodexMcpRuntimeAssembly] =
    _create_c(source, policy, None, Some(exchangefactory))

  private def _create_c(
    source: Record,
    policy: CodexMcpImportPolicy,
    secretresolver: Option[RuntimeSecretResolver],
    exchangefactory: Option[() => McpStreamableHttpExchange]
  )(using ExecutionContext): Consequence[CodexMcpRuntimeAssembly] =
    for {
      imported <- CodexMcpDefinitionImporter.importC(source, policy)
      provider <- (secretresolver, exchangefactory) match {
        case (Some(resolver), Some(factory)) =>
          McpStreamableHttpTransportProvider.createC(
            Vector(imported.transportConfig),
            resolver,
            factory
          )
        case (Some(resolver), None) =>
          McpStreamableHttpTransportProvider.createC(
            Vector(imported.transportConfig),
            resolver
          )
        case (None, Some(factory)) =>
          McpStreamableHttpTransportProvider.createC(
            Vector(imported.transportConfig),
            factory
          )
        case (None, None) =>
          McpStreamableHttpTransportProvider.createC(Vector(imported.transportConfig))
      }
      registry <- McpClientRuntimeRegistry.createC(
        Vector(imported.serverSet),
        provider.binding
      )
    } yield new CodexMcpRuntimeAssembly(registry)
}
