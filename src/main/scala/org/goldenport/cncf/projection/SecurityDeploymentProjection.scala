package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, ResolvedAuthenticationProviderBinding, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object SecurityDeploymentProjection {
  def projectMermaid(base: Component): String =
    base.subsystem.map(projectMermaid).getOrElse(projectMermaid(_standaloneSubsystem(base)))

  def projectMermaid(subsystem: Subsystem): String = {
    val authentication = subsystem.resolvedSecurityWiring.authentication
    val providers = authentication.providers
    val displayByRuntime = _componentDisplayNames(subsystem)
    val componentNames =
      (displayByRuntime.values.toVector ++ providers.map(p => _componentDisplayName(displayByRuntime, p.componentName))).distinct.sorted
    val subsystemNode = _nodeId("subsystem", subsystem.name)
    val securityContextNode = "execution_context_security_context"
    val actionCallNode = "action_call"
    val unitOfWorkNode = "unit_of_work"
    val ingressNode = "ingress"
    val header = Vector(
      "flowchart LR",
      s"""  $ingressNode["Ingress"]""",
      s"""  $subsystemNode["Subsystem: ${subsystem.name}"]""",
      s"""  $securityContextNode["ExecutionContext(SecurityContext)"]""",
      s"""  $actionCallNode["ActionCall"]""",
      s"""  $unitOfWorkNode["UnitOfWork"]"""
    )
    val componentNodes = componentNames.map { name =>
      val node = _nodeId("component", name)
      s"""  $node["Component: $name"]"""
    }
    val providerNodes = providers.map { provider =>
      val node = _providerNodeId(provider)
      s"""  $node["${_providerLabel(provider)}"]"""
    }
    val componentEdges = componentNames.map { name =>
      val node = _nodeId("component", name)
      s"  $subsystemNode --> $node"
    }
    val providerEdges = providers.flatMap { provider =>
      val providerNode = _providerNodeId(provider)
      val componentNode = _nodeId("component", _componentDisplayName(displayByRuntime, provider.componentName))
      Vector(
        s"  $componentNode --> $providerNode",
        s"  $ingressNode --> $providerNode",
        s"  $providerNode --> $securityContextNode"
      )
    }
    val chokePointEdges = Vector(
      s"  $securityContextNode --> $actionCallNode",
      s"  $securityContextNode --> $unitOfWorkNode"
    )
    (header ++ componentNodes ++ providerNodes ++ componentEdges ++ providerEdges ++ chokePointEdges).mkString("\n")
  }

  private def _standaloneSubsystem(base: Component): Subsystem = {
    val subsystem = Subsystem(
      base.name,
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )
    subsystem.add(Vector(base))
  }

  private def _componentDisplayNames(subsystem: Subsystem): Map[String, String] = {
    val descriptorMap = subsystem.descriptor.toVector.flatMap(_.componentBindings).map { binding =>
      GenericSubsystemDescriptor.runtimeComponentName(binding.componentName) -> binding.componentName
    }.toMap
    val runtimeNames = subsystem.components.map(_.name)
    runtimeNames.map { name =>
      name -> descriptorMap.getOrElse(name, name)
    }.toMap ++ descriptorMap
  }

  private def _componentDisplayName(displayByRuntime: Map[String, String], runtimeName: String): String =
    displayByRuntime.getOrElse(runtimeName, runtimeName)

  private def _providerNodeId(provider: ResolvedAuthenticationProviderBinding): String =
    _nodeId("provider", s"${provider.componentName}_${provider.name}")

  private def _providerLabel(provider: ResolvedAuthenticationProviderBinding): String =
    Vector(
      s"Auth Provider: ${provider.name}",
      provider.kind.map(kind => s"kind=$kind").getOrElse(""),
      s"source=${provider.source.toString.toLowerCase}",
      if (provider.isDefault) s"default priority=${provider.priority}" else s"priority=${provider.priority}",
      if (provider.schemes.nonEmpty) s"schemes=${provider.schemes.mkString(",")}" else ""
    ).filter(_.nonEmpty).mkString("<br/>")

  private def _nodeId(prefix: String, value: String): String = {
    val normalized = Option(value).getOrElse("")
      .map(c => if (c.isLetterOrDigit) c.toLower else '_')
      .mkString
      .replaceAll("_+", "_")
      .stripPrefix("_")
      .stripSuffix("_")
    s"${prefix}_${if (normalized.nonEmpty) normalized else "unnamed"}"
  }
}
