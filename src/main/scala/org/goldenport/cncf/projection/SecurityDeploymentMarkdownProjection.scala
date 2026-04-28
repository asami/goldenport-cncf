package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Apr.  9, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object SecurityDeploymentMarkdownProjection {
  def project(base: Component): String =
    base.subsystem.map(project).getOrElse(project(_standaloneSubsystem(base)))

  def project(subsystem: Subsystem): String = {
    val auth = subsystem.resolvedSecurityWiring.authentication
    val providers = auth.providers
    val policies = AuthorizationPolicyProjection.project(subsystem.components, subsystem.name)
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
      "- `UnitOfWork`",
      "",
      "## Authorization Resource Policies",
      "",
      _resource_policy_table(policies),
      "",
      "## Role Definitions",
      "",
      _role_definition_table(policies)
    ).mkString("\n")
  }

  private def _resource_policy_table(
    policies: org.goldenport.record.Record
  ): String = {
    val rows = _records(policies.asMap.get("resourcePolicies"))
    if (rows.isEmpty)
      "| Family | Resource | Action | Capabilities | Permission | Source |\n| --- | --- | --- | --- | --- | --- |\n| none | - | - | - | - | - |"
    else
      rows.map { r =>
        s"| ${_string(r, "family")} | ${_string(r, "resource")} | ${_string(r, "action")} | ${_strings(r, "requiredCapabilities").mkString(", ")} | ${_dash(_string(r, "permissionOverride"))} | ${_string(r, "source")} |"
      }.mkString(
        "| Family | Resource | Action | Capabilities | Permission | Source |\n| --- | --- | --- | --- | --- | --- |\n",
        "\n",
        ""
      )
  }

  private def _role_definition_table(
    policies: org.goldenport.record.Record
  ): String = {
    val rows = _records(policies.asMap.get("roleDefinitions"))
    if (rows.isEmpty)
      "| Role | Includes | Capabilities | Source |\n| --- | --- | --- | --- |\n| none | - | - | - |"
    else
      rows.map { r =>
        s"| ${_string(r, "name")} | ${_strings(r, "includes").mkString(", ")} | ${_strings(r, "capabilities").mkString(", ")} | ${_string(r, "source")} |"
      }.mkString(
        "| Role | Includes | Capabilities | Source |\n| --- | --- | --- | --- |\n",
        "\n",
        ""
      )
  }

  private def _records(value: Option[Any]): Vector[org.goldenport.record.Record] =
    value match {
      case Some(xs: Seq[?]) => xs.collect { case r: org.goldenport.record.Record => r }.toVector
      case _ => Vector.empty
    }

  private def _strings(
    record: org.goldenport.record.Record,
    key: String
  ): Vector[String] =
    record.asMap.get(key) match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString).filter(_.nonEmpty)
      case Some(value) if value.toString.nonEmpty => Vector(value.toString)
      case _ => Vector.empty
    }

  private def _string(
    record: org.goldenport.record.Record,
    key: String
  ): String =
    record.getString(key).getOrElse("")

  private def _dash(value: String): String =
    if (value.trim.isEmpty) "-" else value

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
