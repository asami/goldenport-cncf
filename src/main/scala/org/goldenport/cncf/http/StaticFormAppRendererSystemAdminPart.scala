package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.component.ComponentIdentityCompatibilityAdapter
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobQueryReadModel
import org.goldenport.cncf.knowledge.{KnowledgeNodeId, KnowledgeSpaceProjection}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, DiagnosticPayloadReference}
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.projection.{AuthorizationPolicyProjection, DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.cncf.search.{SearchMode, SearchPlanningProfile, WebSearchQueryPlanner}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataConfidentiality, Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   May. 18, 2026
 *  version May. 20, 2026
 *  version Jun. 19, 2026
 *  version Aug. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererSystemAdminPart  extends StaticFormAppRendererDashboardPart with StaticFormAppRendererDescriptorPart with StaticFormAppRendererManualPart with StaticFormAppRendererFormatPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererJobPart with StaticFormAppRendererObservabilityPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def renderSubsystemDashboard(subsystem: Subsystem): Page =
    Page(dashboard_shell(
      title = "CNCF Health",
      subtitle = subsystem.name + subsystem.version.map(v => s" ${escape(v)}").getOrElse(""),
      statePath = "/web/system/dashboard/state"
    ))

  def renderComponentDashboard(component: Component): Page =
    renderComponentDashboard(component, NamingConventions.toNormalizedSegment(component.name))

  def renderComponentDashboard(component: Component, componentpath: String): Page =
    Page(dashboard_shell(
      title = s"${escape(component.name)} Dashboard",
      subtitle = "Component health",
      statePath = s"/web/${componentpath}/dashboard/state"
    ))

  def renderDashboardState(
    subsystem: Subsystem,
    componentName: Option[String]
  ): Option[Page] =
    componentName match {
      case Some(name) =>
        find_component(subsystem, name).map(component =>
          Page(component_dashboard_state(component))
        )
      case None =>
        Some(Page(subsystem_dashboard_state(subsystem)))
    }

  def renderSystemAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(admin_page(
      title = "System Admin Configuration",
      subtitle = "Current CNCF runtime configuration",
      components = subsystem.components,
      subsystemName = subsystem.name,
      subsystemVersion = subsystem.version,
      dashboardPath = "/web/system/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = Some(subsystem.configuration),
      operationalDetails = Some(system_admin_operational_details(webDescriptor, subsystem.components)),
      componentFormsPath = None
    ))

  def renderApplicationAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(simple_page(
      title = "Application Admin",
      subtitle = "Application operator console",
      body =
        s"""${admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "Tags" -> "/web/admin/tags",
             "Associations" -> "/web/admin/associations"
           ))}
           |${application_admin_entry_pages(webDescriptor)}
           |${application_admin_component_cards(subsystem, webDescriptor)}""".stripMargin
    ))

  def renderSystemAdminDescriptor(
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(simple_page(
      title = "System Web Descriptor",
      subtitle = "Management Console descriptor view",
      body =
        s"""${admin_nav_card(Vector("System admin" -> "/web/system/admin", "System dashboard" -> "/web/system/dashboard"))}
           |${web_descriptor_section_nav}
           |${web_descriptor_control_tables(webDescriptor)}
           |${web_descriptor_asset_composition_table(webDescriptor)}
           |${web_descriptor_json_panel(
             "completed-descriptor",
             "Completed Descriptor JSON",
             "The completed view applies framework defaults so the descriptor can be inspected as the runtime sees it.",
             web_descriptor_json(webDescriptor, completed = true)
           )}
           |${web_descriptor_json_panel(
             "configured-descriptor",
             "Configured Descriptor JSON",
             "The configured view keeps explicit descriptor entries for comparison.",
             web_descriptor_json(webDescriptor, completed = false)
           )}""".stripMargin
    ))

  def renderSystemAdminAssemblyWarnings(
    subsystem: Subsystem
  ): Page = {
    val report = assembly_report(subsystem)
    val rows =
      if (report.warnings.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No assembly warnings.</td></tr>"""
      else
        report.warnings.map { warning =>
          s"""<tr>
             |  <td><code>${escape(warning.kind)}</code></td>
             |  <td>${escape(warning.severity)}</td>
             |  <td>${escape(warning.componentName)}</td>
             |  <td>${escape(warning.message)}</td>
             |  <td>${escape(warning.selectedOrigin.getOrElse(""))}</td>
             |  <td>${escape(warning.droppedOrigins.mkString(", "))}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    Page(simple_page(
      title = "Assembly Warnings",
      subtitle = "Runtime assembly diagnostics",
      body =
        s"""${admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly report" -> "/web/system/admin/assembly/report"))}
           |${admin_card(
             "Warnings",
             s"""<p>${report.warnings.size} warning(s). Assembly warnings are diagnostics, not health errors.</p>
                |${admin_table(
                  Some("<tr><th>Kind</th><th>Severity</th><th>Component</th><th>Message</th><th>Selected</th><th>Dropped</th></tr>"),
                  rows
                )}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderSystemAdminAssemblyReport(
    subsystem: Subsystem
  ): Page = {
    val record = assembly_report(subsystem).toRecord
    val json = manual_raw_json(record).map(_.spaces2).getOrElse(manual_raw_text(record))
    val yaml = manual_raw_json(record).map(json_to_yaml).getOrElse(manual_raw_text(record))
    Page(simple_page(
      title = "Assembly Report",
      subtitle = "Runtime assembly report",
      body =
        s"""${admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly warnings" -> "/web/system/admin/assembly/warnings"))}
           |${admin_card("Report", raw_format_tabs(json, yaml, "assembly-report"))}""".stripMargin
    ))
  }


  def renderSystemConsole(subsystem: Subsystem): Page =
    Page(simple_page(
      title = "System Console",
      subtitle = "Controlled operation entry",
      body =
        s"""${admin_nav_card(Vector(
             "System dashboard" -> "/web/system/dashboard",
             "Admin configuration" -> "/web/system/admin",
             "Performance details" -> "/web/system/performance",
             "Manuals" -> "/man/system"
           ))}
           |${admin_card(
             "Operation forms",
             s"""<p>Console links to operation forms. It does not execute operations inline.</p>
                |${component_form_list(subsystem.components)}""".stripMargin
           )}""".stripMargin
    ))

}
