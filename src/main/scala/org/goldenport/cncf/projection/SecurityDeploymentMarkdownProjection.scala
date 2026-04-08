package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object SecurityDeploymentMarkdownProjection {
  def project(base: Component): String =
    base.subsystem.map(project).getOrElse(project(_standaloneSubsystem(base)))

  def project(subsystem: Subsystem): String = {
    val auth = subsystem.resolvedSecurityWiring.authentication
    val providers = auth.providers
    val providerTable =
      if (providers.isEmpty)
        "| Name | Component | Kind | Source | Priority | Default | Schemes |\n| --- | --- | --- | --- | ---: | --- | --- |\n| none | - | - | - | 0 | false | - |"
      else
        providers.map { p =>
          val kind = p.kind.getOrElse("")
          val schemes = if (p.schemes.nonEmpty) p.schemes.mkString(", ") else "-"
          s"| ${p.name} | ${_componentDisplayName(subsystem, p.componentName)} | $kind | ${p.source.toString.toLowerCase} | ${p.priority} | ${p.isDefault} | $schemes |"
        }.mkString(
          "| Name | Component | Kind | Source | Priority | Default | Schemes |\n| --- | --- | --- | --- | ---: | --- | --- |\n",
          "\n",
          ""
        )
    Vector(
      "# Security Deployment Specification",
      "",
      "## Subsystem",
      "",
      s"- name: `${subsystem.name}`",
      s"- authentication convention: `${if (auth.conventionEnabled) "enabled" else "disabled"}`",
      s"- fallback privilege: `${if (auth.fallbackPrivilegeEnabled) "enabled" else "disabled"}`",
      "",
      "## Diagram",
      "",
      "```mermaid",
      SecurityDeploymentProjection.projectMermaid(subsystem),
      "```",
      "",
      "## Authentication Providers",
      "",
      providerTable,
      "",
      "## Framework Chokepoints",
      "",
      "- `ActionCall`",
      "- `UnitOfWork`"
    ).mkString("\n")
  }

  private def _standaloneSubsystem(base: Component): Subsystem = {
    val subsystem = org.goldenport.cncf.subsystem.Subsystem(
      base.name,
      configuration = org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    )
    subsystem.add(Vector(base))
  }

  private def _componentDisplayName(subsystem: Subsystem, runtimeName: String): String =
    subsystem.descriptor.toVector.flatMap(_.componentBindings)
      .find(x => org.goldenport.cncf.subsystem.GenericSubsystemDescriptor.runtimeComponentName(x.componentName) == runtimeName)
      .map(_.componentName)
      .getOrElse(runtimeName)
}
