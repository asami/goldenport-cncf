package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
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
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererCorePart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererFormPart with StaticFormAppRendererSystemAdminPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def render(
    subsystem: Subsystem,
    app: String,
    page: Vector[String] = Vector.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] = {
    page match {
      case Vector() if app == "document" =>
        Some(renderSystemDocument(subsystem))
      case Vector("specification") if app == "document" =>
        Some(renderSystemManual(subsystem))
      case Vector() if app == "console" =>
        Some(renderSystemConsole(subsystem))
      case _ if !webDescriptor.isAppEnabled(app, page) =>
        None
      case Vector("dashboard") =>
        find_component(subsystem, app).map(renderComponentDashboard(_, NamingConventions.toNormalizedSegment(app)))
      case Vector() =>
        renderFormIndex(subsystem, app, webDescriptor)
      case _ =>
        None
    }
  }

  def renderStaticTemplate(
    app: String,
    page: Vector[String],
    template: String,
    assetCompletion: StaticFormAppLayout.AssetCompletionOptions =
      StaticFormAppLayout.AssetCompletionOptions(),
    pageContext: WebPageContext = WebPageContext.empty,
    webdescriptor: WebDescriptor = WebDescriptor.empty
  ): Page = {
    val pageName =
      if (page.isEmpty) "index"
      else page.mkString("/")
    val profile = webdescriptor.staticPageProfile(app, page)
    val properties = FormPageProperties(
      app,
      "web",
      pageName,
      Map(
        "app" -> app,
        "page.name" -> pageName,
        "page.path" -> ("/web/" + (app +: page).mkString("/")),
        "page.uxProfile" -> profile.name,
        "textus.uxProfile" -> profile.name
      ) ++ page_context_properties(pageContext)
    )
    val rendered = render_template(template, properties, Map.empty)
    Page(complete_widget_assets(template, rendered, assetCompletion))
  }

  protected def page_context_properties(
    pageContext: WebPageContext
  ): Map[String, String] = {
    defaultPageViewContextValues ++ pageContext.values
  }

  def isHtmlDocumentTemplate(template: String): Boolean =
    is_html_document(template)

  def hasTextusMarkup(template: String): Boolean =
    has_textus_widgets(template)


  def renderComponentAdmin(
    component: Component,
    webDescriptor: WebDescriptor
  ): Page =
    renderComponentAdmin(component, NamingConventions.toNormalizedSegment(component.name), webDescriptor)

  def renderComponentAdmin(
    component: Component,
    componentPath: String,
    webDescriptor: WebDescriptor
  ): Page = {
    Page(admin_page(
      title = s"${escape(component.name)} Admin Configuration",
      subtitle = "Current component runtime configuration",
      components = Vector(component),
      subsystemName = component.subsystem.map(_.name).getOrElse(component.name),
      subsystemVersion = component.subsystem.flatMap(_.version),
      dashboardPath = s"/web/${componentPath}/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = None,
      operationalDetails = None,
      componentFormsPath = Some(s"/form/${componentPath}")
    ))
  }

  protected def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  protected def ux_profile_attr(profile: WebUxProfile): String =
    s""" data-textus-ux-profile="${escape(profile.name)}""""

  protected def title_label(
    segment: String
  ): String =
    segment
      .split("[-_]")
      .toVector
      .filter(_.nonEmpty)
      .map(x => x.head.toUpper + x.tail)
      .mkString(" ")

  protected def json(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
}
