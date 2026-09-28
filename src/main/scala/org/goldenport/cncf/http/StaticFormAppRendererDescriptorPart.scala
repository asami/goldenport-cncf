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
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] trait StaticFormAppRendererDescriptorPart { self: StaticFormAppRendererSystemAdminPart & StaticFormAppRendererSupport & StaticFormAppRendererBlobTagPart & StaticFormAppRendererComponentAdminPart & StaticFormAppRendererCorePart & StaticFormAppRendererFormPart & StaticFormAppRendererJobPart & StaticFormAppRendererObservabilityPart & StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*
  protected def web_descriptor_summary(
    descriptor: WebDescriptor,
    descriptorPath: String
  ): String =
    s"""<div class="table-responsive"><table class="table table-sm">
       |  <tbody>
       |    <tr><th>Status</th><td>${if (descriptor.hasControls) "configured" else "default"}</td></tr>
       |    <tr><th>Auth mode</th><td>${escape(descriptor.auth.mode)}</td></tr>
       |    <tr><th>Exposure entries</th><td>${descriptor.expose.size}</td></tr>
       |    <tr><th>Authorization entries</th><td>${descriptor.authorization.size}</td></tr>
       |    <tr><th>Form entries</th><td>${descriptor.form.size}</td></tr>
       |    <tr><th>App entries</th><td>${descriptor.apps.size}</td></tr>
       |    <tr><th>Route entries</th><td>${descriptor.routes.size}</td></tr>
       |    <tr><th>Admin entries</th><td>${descriptor.admin.size}</td></tr>
       |  </tbody>
       |</table></div>
       |${admin_action_row(Vector("Completed descriptor JSON" -> descriptorPath), primary = false)}
       |${web_descriptor_app_list(descriptor)}
       |${web_descriptor_route_list(descriptor)}
       |${web_descriptor_exposure_list(descriptor)}
       |${web_descriptor_admin_list(descriptor)}""".stripMargin

  protected def web_descriptor_admin_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.admin.isEmpty) {
      "<p>No Management Console controls are configured.</p>"
    } else {
      val rows = descriptor.admin.toVector.sortBy(_._1).map {
        case (selector, admin) =>
          val fields = admin.fields.map(_.name).mkString(", ")
          val kind = admin_surface_kind(selector).getOrElse("deferred")
          val destination = web_descriptor_admin_surface_destination(selector, None)
          s"""<tr><td>${destination}</td><td>${escape(kind)}</td><td>${escape(admin.totalCount.name)}</td><td>${escape(fields)}</td><td><code>${escape(selector)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Management Console Controls</h3>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Destination</th><th>Type</th><th>Total count</th><th>Fields</th><th>Raw selector</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_json(
    descriptor: WebDescriptor,
    completed: Boolean = false,
    componentSegment: Option[String] = None
  ): String =
    Json.obj(
      "status" -> Json.fromString(if (descriptor.hasControls) "configured" else "default"),
      "defaultView" -> Json.fromString(descriptor.defaultView),
      "auth" -> Json.obj(
        "mode" -> Json.fromString(descriptor.auth.mode)
      ),
      "assets" -> web_descriptor_assets_json(descriptor.assets),
      "theme" -> web_descriptor_theme_json(descriptor.theme),
      "assetComposition" -> (
        if (completed)
          web_descriptor_asset_composition_json(descriptor, componentSegment)
        else
          Json.Null
      ),
      "expose" -> Json.fromFields(
        descriptor.expose.toVector.sortBy(_._1).map {
          case (selector, exposure) => selector -> Json.fromString(exposure.name)
        }
      ),
      "authorization" -> Json.fromFields(
        descriptor.authorization.toVector.sortBy(_._1).map {
          case (selector, authorization) =>
            selector -> Json.obj(
              "roles" -> Json.arr(authorization.roles.map(Json.fromString)*),
              "scopes" -> Json.arr(authorization.scopes.map(Json.fromString)*),
              "capabilities" -> Json.arr(authorization.capabilities.map(Json.fromString)*)
            )
        }
      ),
      "form" -> Json.fromFields(
        descriptor.form.toVector.sortBy(_._1).map {
          case (selector, form) =>
            selector -> Json.obj(
              "enabled" -> form.enabled.map(Json.fromBoolean).getOrElse(Json.Null),
              "assets" -> web_descriptor_assets_json(form.assets)
            )
        }
      ),
      "pages" -> Json.fromFields(
        descriptor.pages.toVector.sortBy(_._1).map {
          case (selector, page) =>
            selector -> Json.obj(
              "title" -> page.title.map(Json.fromString).getOrElse(Json.Null),
              "heading" -> page.heading.map(Json.fromString).getOrElse(Json.Null),
              "subtitle" -> page.subtitle.map(Json.fromString).getOrElse(Json.Null),
              "submitLabel" -> page.submitLabel.map(Json.fromString).getOrElse(Json.Null),
              "fields" -> Json.arr(page.fields.map(Json.fromString)*),
              "controls" -> Json.fromFields(page.controls.toVector.sortBy(_._1).map {
                case (name, control) => name -> web_descriptor_form_control_json(control)
              })
            )
        }
      ),
      "admin" -> Json.fromFields(
        descriptor.admin.toVector.sortBy(_._1).map {
          case (selector, admin) =>
            selector -> Json.obj(
              "totalCount" -> Json.fromString(admin.totalCount.name),
              "fields" -> Json.arr(admin.fields.map(field =>
                Json.obj(
                  "name" -> Json.fromString(field.name),
                  "type" -> field.control.controlType.map(Json.fromString).getOrElse(Json.Null),
                  "label" -> field.control.label.map(Json.fromString).getOrElse(Json.Null),
                  "required" -> field.control.required.map(Json.fromBoolean).getOrElse(Json.Null),
                  "hidden" -> Json.fromBoolean(field.control.hidden),
                  "readonly" -> Json.fromBoolean(field.control.readonly),
                  "placeholder" -> field.control.placeholder.map(Json.fromString).getOrElse(Json.Null),
                  "help" -> field.control.help.map(Json.fromString).getOrElse(Json.Null),
                  "defaultValue" -> field.control.defaultValue.map(Json.fromString).getOrElse(Json.Null),
                  "values" -> Json.arr(field.control.values.map(Json.fromString)*)
                )
              )*)
            )
        }
      ),
      "apps" -> Json.arr(
        descriptor.apps.map { app =>
          web_descriptor_app_json(if (completed) app.completedFor(componentSegment) else app)
        }*
      ),
      "routes" -> Json.arr(
        descriptor.routes.map(web_descriptor_route_json)*
      )
    ).spaces2

  protected def web_descriptor_form_control_json(
    control: WebDescriptor.FormControl
  ): Json =
    Json.obj(
      "type" -> control.controlType.map(Json.fromString).getOrElse(Json.Null),
      "label" -> control.label.map(Json.fromString).getOrElse(Json.Null),
      "required" -> control.required.map(Json.fromBoolean).getOrElse(Json.Null),
      "hidden" -> Json.fromBoolean(control.hidden),
      "readonly" -> Json.fromBoolean(control.readonly),
      "placeholder" -> control.placeholder.map(Json.fromString).getOrElse(Json.Null),
      "help" -> control.help.map(Json.fromString).getOrElse(Json.Null),
      "defaultValue" -> control.defaultValue.map(Json.fromString).getOrElse(Json.Null),
      "values" -> Json.arr(control.values.map(Json.fromString)*)
    )

  protected def web_descriptor_assets_json(
    assets: WebDescriptor.Assets
  ): Json =
    Json.obj(
      "autoComplete" -> Json.fromBoolean(assets.autoComplete),
      "css" -> Json.arr(assets.css.map(Json.fromString)*),
      "js" -> Json.arr(assets.js.map(Json.fromString)*)
    )

  protected def web_descriptor_theme_json(
    theme: WebDescriptor.Theme
  ): Json =
    Json.obj(
      "name" -> theme.name.map(Json.fromString).getOrElse(Json.Null),
      "css" -> Json.arr(theme.css.map(Json.fromString)*),
      "variables" -> Json.fromFields(
        theme.variables.toVector.sortBy(_._1).map {
          case (key, value) => key -> Json.fromString(value)
        }
      )
    )

  protected def web_descriptor_asset_composition_json(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Json = {
    val forms = web_descriptor_form_asset_entries(descriptor, componentSegment)
    Json.obj(
      "global" -> web_descriptor_assets_json(descriptor.assets),
      "apps" -> Json.fromFields(
        descriptor.apps.map(app => app.normalizedName -> web_descriptor_assets_json(app.assets))
      ),
      "forms" -> Json.fromFields(
        forms.map {
          case (selector, _, _, _, form) => selector -> web_descriptor_assets_json(form.assets)
        }
      ),
      "resolvedForms" -> Json.fromFields(
        forms.map {
          case (selector, component, service, operation, _) =>
            selector -> Json.obj(
              "component" -> Json.fromString(component),
              "service" -> Json.fromString(service),
              "operation" -> Json.fromString(operation),
              "componentFormIndex" -> web_descriptor_assets_json(descriptor.formIndexAssets(component)),
              "operationInput" -> web_descriptor_assets_json(descriptor.resultAssets(component, service, operation)),
              "operationResult" -> web_descriptor_assets_json(descriptor.resultAssets(component, service, operation))
            )
        }
      )
    )
  }

  protected def web_descriptor_form_asset_entries(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Vector[(String, String, String, String, WebDescriptor.Form)] =
    descriptor.form.toVector.sortBy(_._1).flatMap {
      case (selector, form) =>
        selector.split("\\.", 3).toVector match {
          case Vector(component, service, operation) if componentSegment.forall(_ == component) =>
            Some((selector, component, service, operation, form))
          case _ =>
            None
        }
    }

  protected def web_descriptor_control_tables(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val body =
      s"""<p>Completed apps, routes, form access, authorization, and admin surfaces.</p>
         |${admin_action_row(Vector("Completed JSON" -> "#completed-descriptor"), primary = false)}
         |${web_descriptor_filter_control}
         |${web_descriptor_apps_table(descriptor, componentSegment)}
         |${web_descriptor_routes_table(descriptor, componentSegment)}
         |${web_descriptor_form_controls_table(descriptor, componentSegment)}
         |${web_descriptor_admin_surfaces_table(descriptor, componentSegment)}
         |${web_descriptor_filter_script}""".stripMargin
    admin_card("Descriptor Controls", body, Some("descriptor-controls"))
  }

  protected def web_descriptor_section_nav: String =
    admin_card(
      "Descriptor Sections",
      """<nav class="nav nav-pills flex-column flex-sm-row gap-2 descriptor-section-nav">
        |  <a class="nav-link border" href="#descriptor-controls">Descriptor Controls</a>
        |  <a class="nav-link border" href="#asset-composition">Asset Composition</a>
        |  <a class="nav-link border" href="#completed-descriptor">Completed JSON</a>
        |  <a class="nav-link border" href="#configured-descriptor">Configured JSON</a>
        |</nav>""".stripMargin
    )

  protected def web_descriptor_json_panel(
    id: String,
    title: String,
    description: String,
    json: String
  ): String =
    admin_card(
      title,
      s"""<details class="descriptor-json-details">
         |  <summary class="h5 mb-3">${escape(title)}</summary>
         |  <p>${escape(description)}</p>
         |  ${raw_format_tabs(json, json_to_yaml(json), "descriptor")}
         |</details>""".stripMargin,
      Some(id)
    )

  protected def web_descriptor_filter_control: String =
    """<div class="mb-3">
      |  <label class="form-label" for="web-descriptor-filter">Filter descriptor tables</label>
      |  <input class="form-control" id="web-descriptor-filter" type="search" placeholder="Filter apps, routes, forms, authorization, and admin surfaces" data-textus-descriptor-filter>
      |  <p class="alert alert-warning mt-2 mb-0 d-none" data-textus-descriptor-filter-empty>No descriptor rows match the filter.</p>
      |</div>""".stripMargin

  protected def web_descriptor_filter_script: String =
    """<script>
      |(() => {
      |  const input = document.querySelector("[data-textus-descriptor-filter]");
      |  if (!input) return;
      |  const rows = Array.from(document.querySelectorAll("[data-descriptor-row]"));
      |  const empty = document.querySelector("[data-textus-descriptor-filter-empty]");
      |  input.addEventListener("input", () => {
      |    const query = input.value.trim().toLowerCase();
      |    let visible = 0;
      |    rows.forEach((row) => {
      |      const hidden = query.length > 0 && !row.textContent.toLowerCase().includes(query);
      |      row.classList.toggle("d-none", hidden);
      |      if (!hidden) visible += 1;
      |    });
      |    if (empty) empty.classList.toggle("d-none", query.length === 0 || visible > 0);
      |  });
      |})();
      |</script>""".stripMargin

  protected def web_descriptor_apps_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val apps = web_descriptor_scoped_apps(descriptor, componentSegment)
    val rows = apps.map { app =>
      val completed = app.completedFor(componentSegment)
      s"""<tr data-descriptor-row>
         |  <td><code>${escape(completed.name)}</code></td>
         |  <td>${web_descriptor_path_link(completed.effectivePath)}</td>
         |  <td><code>${escape(completed.effectiveRoot)}</code></td>
         |  <td>${web_descriptor_route_link(completed.effectiveRoute)}</td>
         |  <td>${escape(completed.effectiveKind)}</td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No Web app descriptor entries are configured.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Apps <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Name</th><th>Path</th><th>Root</th><th>Route</th><th>Kind</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  protected def web_descriptor_scoped_apps(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Vector[WebDescriptor.App] =
    componentSegment match {
      case None => descriptor.apps
      case Some(component) =>
        descriptor.apps.filter { app =>
          NamingConventions.equivalentByNormalized(app.name, component) ||
            descriptor.routes.exists(route =>
              NamingConventions.equivalentByNormalized(route.target.component, component) &&
                NamingConventions.equivalentByNormalized(route.target.app, app.name)
            )
        }
    }

  protected def web_descriptor_routes_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val filtered = descriptor.routes.filter(route =>
      componentSegment.forall(component => NamingConventions.equivalentByNormalized(route.target.component, component))
    )
    val rows = filtered.map { route =>
      s"""<tr data-descriptor-row>
         |  <td>${web_descriptor_path_link(route.path)}</td>
         |  <td>${escape(route.kind.name)}</td>
         |  <td><code>${escape(route.target.component)}</code></td>
         |  <td><code>${escape(route.target.app)}</code></td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="4" class="text-secondary">No Web route descriptor entries are configured for this scope.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Routes <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Path</th><th>Kind</th><th>Target component</th><th>Target app</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  protected def web_descriptor_form_controls_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val selectors = (descriptor.expose.keySet ++ descriptor.form.keySet ++ descriptor.authorization.keySet)
      .toVector
      .filter(selector => selector_matches_component(selector, componentSegment))
      .sortBy(identity)
    val rows = selectors.map { selector =>
      val exposure = descriptor.exposureOf(selector).name
      val form = descriptor.form.get(selector)
      val authorization = descriptor.authorization.get(selector)
      s"""<tr data-descriptor-row>
         |  <td>${web_descriptor_form_link(selector)}</td>
         |  <td>${escape(exposure)}</td>
         |  <td>${form.flatMap(_.enabled).map(_.toString).getOrElse("default")}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.roles).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.scopes).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.capabilities).getOrElse(Vector.empty)))}</td>
         |  <td>${authorization.exists(_.allowAnonymous)}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.operationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.anonymousOperationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="9" class="text-secondary">No form, exposure, or authorization entries are configured for this scope.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Form Access And Authorization <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Selector</th><th>Exposure</th><th>Form</th><th>Roles</th><th>Scopes</th><th>Capabilities</th><th>Anonymous</th><th>Operation modes</th><th>Anonymous modes</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  protected def web_descriptor_admin_surfaces_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val rows = descriptor.admin.toVector.sortBy(_._1).filter {
      case (selector, _) => admin_selector_matches_component(selector, componentSegment)
    }.map {
      case (selector, admin) =>
        val fields = admin.fields.map { field =>
          val control = field.control.controlType.getOrElse("default")
          val required = field.control.required.map(value => s", required=${value}").getOrElse("")
          s"${field.name}:${control}${required}"
        }
        val kind = admin_surface_kind(selector).getOrElse("deferred")
        val destination = web_descriptor_admin_surface_destination(selector, componentSegment)
        val support =
          if (web_descriptor_admin_surface_path(selector, componentSegment).isDefined) "implemented baseline"
          else "deferred"
        s"""<tr data-descriptor-row>
           |  <td>${destination}</td>
           |  <td>${escape(kind)}</td>
           |  <td>${escape(admin.totalCount.name)}</td>
           |  <td>${escape(web_descriptor_csv(fields))}</td>
           |  <td>${escape(support)}</td>
           |  <td><code>${escape(selector)}</code></td>
           |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No Management Console surfaces are configured.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Admin Surfaces <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Destination</th><th>Type</th><th>Total count</th><th>Fields</th><th>Support</th><th>Raw selector</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  protected def selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).headOption.exists(head =>
        NamingConventions.equivalentByNormalized(head, component)
      )
    }

  protected def admin_selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).toVector match {
        case Vector(surface, _) if admin_surface_path(surface).isDefined =>
          true
        case Vector(head, _) =>
          NamingConventions.equivalentByNormalized(head, component)
        case _ =>
          true
      }
    }

  protected def web_descriptor_csv(
    values: Vector[String]
  ): String =
    if (values.isEmpty)
      "none"
    else
      values.mkString(", ")

  protected def web_descriptor_path_link(
    path: String
  ): String =
    if (path.startsWith("/web"))
      s"""<a href="${escape(path)}"><code>${escape(path)}</code></a>"""
    else
      s"""<code>${escape(path)}</code>"""

  protected def web_descriptor_route_link(
    route: String
  ): String =
    if (route.startsWith("/web") && !route.contains("{"))
      s"""<a href="${escape(route)}"><code>${escape(route)}</code></a>"""
    else
      s"""<code>${escape(route)}</code>"""

  protected def web_descriptor_form_link(
    selector: String
  ): String =
    selector.split("\\.", 3).toVector match {
      case Vector(component, service, operation) =>
        val path = s"/form/${component}/${service}/${operation}"
        s"""<a href="${escape(path)}"><code>${escape(selector)}</code></a>"""
      case _ =>
        s"""<code>${escape(selector)}</code>"""
    }

  protected def web_descriptor_admin_surface_path(
    selector: String,
    componentSegment: Option[String]
  ): Option[String] = {
    def _component_from_selector_(): Option[(String, String)] =
      selector.split("\\.", 3).toVector match {
        case Vector(component, surface, name) => Some(component -> s"${surface}.${name}")
        case _ => None
      }
    _component_from_selector_().flatMap {
      case (component, rest) => admin_surface_relative_path(rest).map(path => s"/web/${component}/admin/${path}")
    }.orElse(
      componentSegment.flatMap(component =>
        admin_surface_relative_path(selector).map(path => s"/web/${component}/admin/${path}")
      )
    )
  }

  protected def web_descriptor_admin_surface_destination(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybepath =
      web_descriptor_admin_surface_path(selector, componentSegment)
    maybepath match {
      case Some(path) => s"""<a href="${escape(path)}"><code>${escape(path)}</code></a>"""
      case None => """<span class="text-secondary">Deferred or unsupported</span>"""
    }
  }

  protected def web_descriptor_admin_surface_link(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybepath =
      web_descriptor_admin_surface_path(selector, componentSegment)
    maybepath match {
      case Some(path) => s"""<a href="${escape(path)}"><code>${escape(selector)}</code></a>"""
      case None => s"""<code>${escape(selector)}</code>"""
    }
  }

  protected def admin_surface_kind(
    selector: String
  ): Option[String] =
    (selector.split("\\.", 3).toVector match {
      case Vector(surface, _) =>
        admin_surface_path(surface)
      case Vector(_, surface, _) =>
        admin_surface_path(surface)
      case _ =>
        None
    }).map {
      case "entities" => "entity"
      case "data" => "data"
      case "aggregates" => "aggregate"
      case "views" => "view"
      case other => other
    }

  protected def admin_surface_selector_count(
    descriptor: WebDescriptor,
    componentSegment: Option[String],
    kind: String
  ): Int =
    descriptor.admin.keys.count(selector =>
      admin_selector_matches_component(selector, componentSegment) &&
      admin_surface_kind(selector).contains(kind)
    )

  protected def pluralize(
    n: Int,
    singular: String,
    plural: String
  ): String =
    if (n == 1) s"1 ${singular}" else s"${n} ${plural}"

  protected def admin_entry_card(
    title: String,
    description: String,
    href: String,
    badge: Option[String] = None
  ): String =
    s"""<div class="col-12 col-md-6 col-xl-4">
       |  <article class="card h-100 shadow-sm admin-card">
       |    <div class="card-body">
       |      <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
       |        <h3 class="h5 card-title mb-0">${escape(title)}</h3>
       |        ${badge.map(v => s"""<span class="badge text-bg-secondary">${escape(v)}</span>""").getOrElse("")}
       |      </div>
       |      <p class="card-text text-body-secondary">${escape(description)}</p>
       |      <a class="btn btn-primary btn-sm" href="${escape(href)}">Open ${escape(title)}</a>
       |    </div>
       |  </article>
       |</div>""".stripMargin

  protected def admin_entry_cards(
    cards: Vector[String]
  ): String =
    s"""<div class="row g-3">${cards.mkString("\n")}</div>"""

  protected def system_admin_component_inventory(
    components: Vector[Component]
  ): String = {
    val rows = components.sortBy(_.name).map { component =>
      val componentpath = NamingConventions.toNormalizedSegment(component.name)
      s"""<tr>
         |  <td>${escape(component.name)}</td>
         |  <td><a href="/web/${componentpath}/admin">Component admin</a></td>
         |  <td><a href="/web/${componentpath}/admin/descriptor">Descriptor</a></td>
         |  <td><a href="/form/${componentpath}">Forms</a></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    admin_card(
      "Component Management Console",
      s"""<p>Use component admin pages for ordinary entity/data/view/aggregate work. System admin stays read-only.</p>
         |${admin_table(
           Some("<tr><th>Component</th><th>Admin</th><th>Descriptor</th><th>Forms</th></tr>"),
           rows
         )}""".stripMargin
    )
  }

  protected def admin_surface_relative_path(
    selector: String
  ): Option[String] =
    selector.split("\\.", 2).toVector match {
      case Vector(surface, name) =>
        admin_surface_path(surface).map(path => s"${path}/${NamingConventions.toNormalizedSegment(name)}")
      case _ =>
        None
    }

  protected def admin_surface_path(
    surface: String
  ): Option[String] =
    NamingConventions.toNormalizedSegment(surface) match {
      case "entity" | "entities" => Some("entities")
      case "data" => Some("data")
      case "aggregate" | "aggregates" => Some("aggregates")
      case "view" | "views" => Some("views")
      case _ => None
    }

  protected def web_descriptor_asset_composition_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val forms = web_descriptor_form_asset_entries(descriptor, componentSegment)
    val scoperows =
      Vector(web_descriptor_asset_scope_row("global", "web.assets", descriptor.assets)) ++
        Vector(web_descriptor_theme_scope_row("global", "web.theme", descriptor.theme)) ++
        descriptor.apps.map(app =>
          web_descriptor_asset_scope_row("app", app.normalizedName, app.assets)
        ) ++
        descriptor.apps.map(app =>
          web_descriptor_theme_scope_row("app theme", app.normalizedName, app.theme)
        ) ++
        forms.map {
          case (selector, _, _, _, form) =>
            web_descriptor_asset_scope_row("form", selector, form.assets)
        }
    val resolvedrows = forms.flatMap {
      case (selector, component, service, operation, _) =>
        Vector(
          web_descriptor_resolved_asset_row(
            selector,
            "component form index",
            descriptor.formIndexAssets(component)
          ),
          web_descriptor_resolved_asset_row(
            selector,
            "operation input",
            descriptor.resultAssets(component, service, operation)
          ),
          web_descriptor_resolved_asset_row(
            selector,
            "operation result",
            descriptor.resultAssets(component, service, operation)
          )
        )
    }
    val scopebody =
      if (scoperows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No descriptor asset scopes are configured.</td></tr>"""
      else
        scoperows.mkString("\n")
    val resolvedbody =
      if (resolvedrows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No form asset compositions are resolved for this scope.</td></tr>"""
      else
        resolvedrows.mkString("\n")
    s"""<article>
       |  <h2 id="asset-composition">Asset Composition <a class="btn btn-sm btn-outline-secondary ms-2" href="#completed-descriptor">Completed JSON</a></h2>
       |  <p>Configured descriptor asset scopes and completed Static Form page asset lists.</p>
       |  <h3>Configured Scopes</h3>
       |  <div class="table-responsive"><table class="table table-sm align-middle">
       |    <thead><tr><th>Scope</th><th>Selector</th><th>Auto complete</th><th>CSS</th><th>JS</th></tr></thead>
       |    <tbody>${scopebody}</tbody>
       |  </table></div>
       |  <h3>Resolved Form Pages</h3>
       |  <div class="table-responsive"><table class="table table-sm align-middle">
       |    <thead><tr><th>Form</th><th>Page</th><th>Auto complete</th><th>CSS</th><th>JS</th></tr></thead>
       |    <tbody>${resolvedbody}</tbody>
       |  </table></div>
       |</article>""".stripMargin
  }

  protected def web_descriptor_asset_scope_row(
    scope: String,
    selector: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td>${escape(scope)}</td>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  protected def web_descriptor_theme_scope_row(
    scope: String,
    selector: String,
    theme: WebDescriptor.Theme
  ): String =
    s"""<tr>
       |  <td>${escape(scope)}</td>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>true</td>
       |  <td>${web_descriptor_asset_url_list(theme.css)}${web_descriptor_theme_variables(theme)}</td>
       |  <td><span class="text-secondary">none</span></td>
       |</tr>""".stripMargin

  protected def web_descriptor_theme_variables(
    theme: WebDescriptor.Theme
  ): String =
    if (theme.variables.isEmpty)
      ""
    else {
      val vars = theme.variables.toVector.sortBy(_._1).map {
        case (key, value) => s"${key}=${value}"
      }.mkString(", ")
      s"""<div><small class="text-secondary">variables: ${escape(vars)}</small></div>"""
    }

  protected def web_descriptor_resolved_asset_row(
    selector: String,
    page: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>${escape(page)}</td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  protected def web_descriptor_asset_url_list(
    urls: Vector[String]
  ): String =
    if (urls.isEmpty)
      """<span class="text-secondary">none</span>"""
    else
      urls.map(url => s"""<div><code>${escape(url)}</code></div>""").mkString

  protected def web_descriptor_app_json(
    app: WebDescriptor.App
  ): Json = {
    val configured = Json.obj(
      "name" -> Json.fromString(app.name),
      "path" -> Json.fromString(app.path),
      "kind" -> Json.fromString(app.kind),
      "theme" -> web_descriptor_theme_json(app.theme),
      "assets" -> web_descriptor_assets_json(app.assets)
    )
    val optionalfields = Vector(
      app.root.map("root" -> Json.fromString(_)),
      app.route.map("route" -> Json.fromString(_))
    ).flatten
    if (optionalfields.isEmpty)
      configured
    else
      configured.deepMerge(Json.obj(optionalfields*))
  }

  protected def web_descriptor_app_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.apps.isEmpty) {
      "<p>Using built-in Web HTML app defaults.</p>"
    } else {
      val rows = descriptor.apps.map { app =>
        val completed = app.completed
        s"""<tr><td>${escape(completed.name)}</td><td><code>${escape(completed.effectivePath)}</code></td><td><code>${escape(completed.effectiveRoot)}</code></td><td><code>${escape(completed.effectiveRoute)}</code></td><td>${escape(completed.effectiveKind)}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Apps</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Name</th><th>Path</th><th>Root</th><th>Route</th><th>Kind</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_route_json(
    route: WebDescriptor.Route
  ): Json =
    Json.obj(
      "path" -> Json.fromString(route.path),
      "kind" -> Json.fromString(route.kind.name),
      "target" -> Json.obj(
        "component" -> Json.fromString(route.target.component),
        "app" -> Json.fromString(route.target.app)
      )
    )

  protected def web_descriptor_route_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.routes.isEmpty) {
      "<p>No subsystem Web route aliases are configured.</p>"
    } else {
      val rows = descriptor.routes.map { route =>
        s"""<tr><td><code>${escape(route.path)}</code></td><td>${escape(route.kind.name)}</td><td><code>${escape(route.target.component)}</code></td><td><code>${escape(route.target.app)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Routes</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Path</th><th>Kind</th><th>Target component</th><th>Target app</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_exposure_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.expose.isEmpty) {
      "<p>No explicit Web exposure entries.</p>"
    } else {
      val rows = descriptor.expose.toVector.sortBy(_._1).map {
        case (selector, exposure) =>
          val auth = descriptor.authorization.get(selector).map(_ => "yes").getOrElse("no")
          val form = descriptor.form.get(selector).flatMap(_.enabled).map(_.toString).getOrElse("default")
          s"""<tr><td><code>${escape(selector)}</code></td><td>${escape(exposure.name)}</td><td>${auth}</td><td>${form}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Operation Exposure</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Selector</th><th>Exposure</th><th>Authorization</th><th>Form</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

}
