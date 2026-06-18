package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.naming.PropertyValueResolver
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
 *  version May. 30, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererTemplatePart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererObservabilityPart with StaticFormAppRendererSystemAdminPart =>
  import StaticFormAppRendererSupport.*

  protected def render_template(
    template: String,
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String = WebTableColumnResolver.defaultViewName
  ): String = {
    val widgets = render_widgets(template, properties, tableColumns, defaultTableView)
    val conditional = render_render_condition_controls(widgets, properties)
    val gated = render_capability_controls(conditional, properties)
    render_property_expansions(gated, properties)
  }

  protected def is_html_document(template: String): Boolean = {
    val text = template.dropWhile(_.isWhitespace).toLowerCase(java.util.Locale.ROOT)
    text.startsWith("<!doctype html") || text.startsWith("<html")
  }

  protected def complete_widget_assets(
    template: String,
    rendered: String,
    options: StaticFormAppLayout.AssetCompletionOptions
  ): String = {
    val hastextuswidgets = has_textus_widgets(template)
    StaticFormAppLayout.completeWidgetAssets(
      rendered,
      options.copy(
        requiresBootstrap = hastextuswidgets,
        requiresTextusWidgets = hastextuswidgets
      )
    )
  }

  protected def has_textus_widgets(template: String): Boolean =
    """<textus(?::|-)[A-Za-z0-9-]+\b""".r.findFirstIn(template).nonEmpty

  protected def render_property_expansions(
    template: String,
    properties: FormPageProperties
  ): String =
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(
        escape(
          property_value(properties, m.group(1))
            .orElse(source_json(m.group(1), properties).map(json_cell))
            .getOrElse("")
        )
      )
    )

  protected def render_widgets(
    template: String,
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val resultview = """<textus-result-view\s+source="([^"]+)"\s*></textus-result-view>""".r
    val table = """<textus:table\b([^>]*)></textus:table>""".r
    val card = """(?s)<textus(?::card(?!-)|-card(?!-))\b([^>]*)>(.*?)</textus(?::card|-card)>""".r
    val recordcard = """<textus(?::record-card|-record-card)\b([^>]*)></textus(?::record-card|-record-card)>""".r
    val cardlist = """<textus(?::card-list|-card-list)\b([^>]*)></textus(?::card-list|-card-list)>""".r
    val linelist = """<textus(?::line-list|-line-list)\b([^>]*)></textus(?::line-list|-line-list)>""".r
    val summarycard = """<textus(?::summary-card|-summary-card)\b([^>]*)></textus(?::summary-card|-summary-card)>""".r
    val actioncard = """<textus(?::action-card|-action-card)\b([^>]*)></textus(?::action-card|-action-card)>""".r
    val actiongroup = """<textus(?::action-group|-action-group)\b([^>]*)></textus(?::action-group|-action-group)>""".r
    val confirmaction = """<textus(?::confirm-action|-confirm-action)\b([^>]*)></textus(?::confirm-action|-confirm-action)>""".r
    val jobpanel = """<textus(?::job-panel|-job-panel)\b([^>]*)></textus(?::job-panel|-job-panel)>""".r
    val jobticket = """<textus(?::job-ticket|-job-ticket)\b([^>]*)></textus(?::job-ticket|-job-ticket)>""".r
    val jobactions = """<textus(?::job-actions|-job-actions)\b([^>]*)></textus(?::job-actions|-job-actions)>""".r
    val alert = """<textus(?::alert|-alert)\b([^>]*)></textus(?::alert|-alert)>""".r
    val emptystate = """<textus(?::empty-state|-empty-state)\b([^>]*)></textus(?::empty-state|-empty-state)>""".r
    val statusbadge = """<textus(?::status-badge|-status-badge)\b([^>]*)></textus(?::status-badge|-status-badge)>""".r
    val pagination = """<textus(?::pagination|-pagination)\b([^>]*)></textus(?::pagination|-pagination)>""".r
    val navlist = """<textus(?::nav-list|-nav-list)\b([^>]*)></textus(?::nav-list|-nav-list)>""".r
    val formlink = """<textus-form-link\s+href="([^"]+)"\s+label="([^"]+)"\s*></textus-form-link>""".r
    val actionlink = """<textus(?::action-link|-action-link)\b([^>]*)></textus(?::action-link|-action-link)>""".r
    val actionform = """<textus(?::action-form|-action-form)\b([^>]*)></textus(?::action-form|-action-form)>""".r
    val operationpanel = """<textus(?::operation-panel|-operation-panel)\b([^>]*)>(.*?)</textus(?::operation-panel|-operation-panel)>""".r
    val hiddencontext = """<textus(?::hidden-context|-hidden-context)\b([^>]*)></textus(?::hidden-context|-hidden-context)>""".r
    val fieldlist = """<textus(?::field-list|-field-list)\b([^>]*)></textus(?::field-list|-field-list)>""".r
    val candidatelist = """<textus(?::candidate-list|-candidate-list)\b([^>]*)></textus(?::candidate-list|-candidate-list)>""".r
    val knowledgesummary = """<textus(?::knowledge-summary|-knowledge-summary)\b([^>]*)></textus(?::knowledge-summary|-knowledge-summary)>""".r
    val descriptionlist = """<textus(?::description-list|-description-list)\b([^>]*)></textus(?::description-list|-description-list)>""".r
    val htmlfield = """<textus(?::html-field|-html-field)\b([^>]*)></textus(?::html-field|-html-field)>""".r
    val capabilitymessage = """(?s)<textus(?::capability-message|-capability-message)\b([^>]*)>(.*?)</textus(?::capability-message|-capability-message)>""".r
    val propertylist = """<textus-property-list\s+source="([^"]+)"\s*></textus-property-list>""".r
    val errorpanel = """<textus-error-panel\s+source="([^"]+)"\s*></textus-error-panel>""".r
    val a = resultview.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(render_result_view(m.group(1), properties))
    )
    val b = table.replaceAllIn(a, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_table(attrs, properties, tableColumns, defaultTableView))
    })
    val b1 = card.replaceAllIn(b, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_card(attrs, m.group(2), properties))
    })
    val c = recordcard.replaceAllIn(b1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_record_card(attrs, properties, tableColumns, defaultTableView))
    })
    val d = cardlist.replaceAllIn(c, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_card_list(attrs, properties, tableColumns, defaultTableView))
    })
    val d1 = linelist.replaceAllIn(d, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_line_list(attrs, properties, tableColumns, defaultTableView))
    })
    val e = summarycard.replaceAllIn(d1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_summary_card(attrs, properties))
    })
    val e1 = actioncard.replaceAllIn(e, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_action_card(attrs, properties))
    })
    val e1a = actiongroup.replaceAllIn(e1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_action_group(attrs, properties))
    })
    var confirmactionindex = 0
    val e1b = confirmaction.replaceAllIn(e1a, m => {
      confirmactionindex = confirmactionindex + 1
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_confirm_action(attrs, properties, confirmactionindex))
    })
    val e2 = jobpanel.replaceAllIn(e1b, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_job_panel(attrs, properties))
    })
    val f0 = jobticket.replaceAllIn(e2, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_job_ticket(attrs, properties))
    })
    val f1 = jobactions.replaceAllIn(f0, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_job_actions(attrs, properties))
    })
    val f = alert.replaceAllIn(f1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_alert(attrs, properties))
    })
    val g = emptystate.replaceAllIn(f, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_empty_state(attrs, properties))
    })
    val g1 = statusbadge.replaceAllIn(g, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_status_badge(attrs, properties))
    })
    val h = pagination.replaceAllIn(g1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_pagination(attrs, properties))
    })
    val h1 = navlist.replaceAllIn(h, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_nav_list(attrs, properties))
    })
    val i = formlink.replaceAllIn(h1, m =>
      java.util.regex.Matcher.quoteReplacement(render_form_link(m.group(1), m.group(2), properties))
    )
    val j = actionlink.replaceAllIn(i, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_action_link(attrs, properties))
    })
    val k = actionform.replaceAllIn(j, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_action_form(attrs, properties))
    })
    val k1 = operationpanel.replaceAllIn(k, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_operation_panel(attrs, m.group(2), properties))
    })
    val l = hiddencontext.replaceAllIn(k1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_hidden_context(attrs, properties))
    })
    val l1a = fieldlist.replaceAllIn(l, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_field_list(attrs, properties))
    })
    val l1b = candidatelist.replaceAllIn(l1a, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_candidate_list(attrs, properties))
    })
    val l1c = knowledgesummary.replaceAllIn(l1b, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_knowledge_summary(attrs, properties))
    })
    val l1 = descriptionlist.replaceAllIn(l1c, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_description_list(attrs, properties, tableColumns, defaultTableView))
    })
    val l2 = htmlfield.replaceAllIn(l1, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_html_field(attrs, properties))
    })
    val l3 = capabilitymessage.replaceAllIn(l2, m => {
      val attrs = widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(render_capability_message(attrs, m.group(2), properties))
    })
    val n = propertylist.replaceAllIn(l3, m =>
      java.util.regex.Matcher.quoteReplacement(render_property_list(m.group(1), properties))
    )
    errorpanel.replaceAllIn(n, m =>
      java.util.regex.Matcher.quoteReplacement(render_error_panel(m.group(1), properties))
    )
  }

  protected def render_capability_controls(
    template: String,
    properties: FormPageProperties
  ): String = {
    val out = new StringBuilder
    var index = 0
    while (index < template.length) {
      find_next_capability_start(template, index) match {
        case Some(start) =>
          out.append(template.substring(index, start))
          parse_start_tag(template, start) match {
            case Some((tag, attrs, tagend, selfclosing)) =>
              val end = {
                if (selfclosing || is_void_html_tag(tag))
                  tagend
                else
                  find_balanced_end_tag(template, tag, tagend).getOrElse(tagend)
              }
              val html = template.substring(start, end)
              out.append(apply_capability_control(html, attrs, properties))
              index = end
            case None =>
              out.append(template.charAt(start))
              index = start + 1
          }
        case None =>
          out.append(template.substring(index))
          index = template.length
      }
    }
    out.toString
  }

  protected def render_render_condition_controls(
    template: String,
    properties: FormPageProperties
  ): String = {
    val out = new StringBuilder
    var index = 0
    while (index < template.length) {
      find_next_render_condition_start(template, index) match {
        case Some(start) =>
          out.append(template.substring(index, start))
          parse_start_tag(template, start) match {
            case Some((tag, attrs, tagend, selfclosing)) =>
              val end = {
                if (selfclosing || is_void_html_tag(tag))
                  tagend
                else
                  find_balanced_end_tag(template, tag, tagend).getOrElse(tagend)
              }
              val html = template.substring(start, end)
              out.append(apply_render_condition_control(html, attrs, properties))
              index = end
            case None =>
              out.append(template.charAt(start))
              index = start + 1
          }
        case None =>
          out.append(template.substring(index))
          index = template.length
      }
    }
    out.toString
  }

  protected def find_next_render_condition_start(
    template: String,
    from: Int
  ): Option[Int] = {
    var index = template.indexOf("<", from)
    while (index >= 0) {
      parse_start_tag(template, index) match {
        case Some((_, attrs, _, _)) if has_render_condition_attr(attrs) =>
          return Some(index)
        case _ =>
          index = template.indexOf("<", index + 1)
      }
    }
    None
  }

  protected def find_next_capability_start(
    template: String,
    from: Int
  ): Option[Int] = {
    var index = template.indexOf("<", from)
    while (index >= 0) {
      parse_start_tag(template, index) match {
        case Some((_, attrs, _, _)) if has_capability_attr(attrs) =>
          return Some(index)
        case _ =>
          index = template.indexOf("<", index + 1)
      }
    }
    None
  }

  protected def parse_start_tag(
    template: String,
    start: Int
  ): Option[(String, String, Int, Boolean)] = {
    if (start + 1 >= template.length || !template.charAt(start + 1).isLetter)
      None
    else {
      var index = start + 2
      while (index < template.length && is_tag_name_char(template.charAt(index)))
        index = index + 1
      val tag = template.substring(start + 1, index)
      val end = find_tag_end(template, index)
      if (end < 0)
        None
      else {
        val body = template.substring(index, end - 1)
        val trimmed = body.trim
        val selfclosing = trimmed.endsWith("/")
        val attrs =
          if (selfclosing) {
            val slash = body.lastIndexOf("/")
            if (slash >= 0) body.substring(0, slash) else body
          } else {
            body
          }
        Some((tag, attrs, end, selfclosing))
      }
    }
  }

  protected def find_balanced_end_tag(
    template: String,
    tag: String,
    from: Int
  ): Option[Int] = {
    var depth = 1
    var index = from
    while (index < template.length) {
      val next = template.indexOf("<", index)
      if (next < 0)
        return None
      else if (tag_name_matches(template, next, tag, closing = true)) {
        val end = find_tag_end(template, next + 2 + tag.length)
        if (end < 0)
          return None
        depth = depth - 1
        if (depth == 0)
          return Some(end)
        index = end
      } else if (tag_name_matches(template, next, tag, closing = false)) {
        parse_start_tag(template, next) match {
          case Some((nestedtag, _, tagend, selfclosing)) =>
            if (!selfclosing && !is_void_html_tag(nestedtag))
              depth = depth + 1
            index = tagend
          case None =>
            index = next + 1
        }
      } else {
        index = next + 1
      }
    }
    None
  }

  protected def tag_name_matches(
    template: String,
    start: Int,
    tag: String,
    closing: Boolean
  ): Boolean = {
    val prefix = if (closing) "</" else "<"
    val tagstart = start + prefix.length
    val tagend = tagstart + tag.length
    template.regionMatches(true, start, prefix, 0, prefix.length) &&
      tagend <= template.length &&
      template.regionMatches(true, tagstart, tag, 0, tag.length) &&
      (tagend == template.length || is_tag_boundary(template.charAt(tagend)))
  }

  protected def find_tag_end(
    template: String,
    from: Int
  ): Int = {
    var index = from
    var quote: Char = 0.toChar
    while (index < template.length) {
      val c = template.charAt(index)
      if (quote != 0.toChar) {
        if (c == quote)
          quote = 0.toChar
      } else {
        c match {
          case '"' | '\'' => quote = c
          case '>' => return index + 1
          case _ =>
        }
      }
      index = index + 1
    }
    -1
  }

  protected def has_capability_attr(
    attrs: String
  ): Boolean =
    """(?i)\bdata-textus-capability\s*=""".r.findFirstIn(attrs).isDefined

  protected def has_render_condition_attr(
    attrs: String
  ): Boolean =
    """(?i)\bdata-textus-render-if(?:-(?:any|all))?\s*=""".r.findFirstIn(attrs).isDefined

  protected def is_tag_name_char(c: Char): Boolean =
    c.isLetterOrDigit || c == ':' || c == '-'

  protected def is_tag_boundary(c: Char): Boolean =
    c.isWhitespace || c == '>' || c == '/'

  protected def is_void_html_tag(tag: String): Boolean =
    tag.toLowerCase(java.util.Locale.ROOT) match {
      case "area" | "base" | "br" | "col" | "embed" | "hr" | "img" |
          "input" | "link" | "meta" | "param" | "source" | "track" | "wbr" => true
      case _ => false
    }

  protected def render_capability_message(
    attrs: Map[String, String],
    inner: String,
    properties: FormPageProperties
  ): String = {
    val capability = attrs.getOrElse("capability", "")
    val policy = attrs.getOrElse("policy", "subject")
    if (capability_allowed(capability, policy, properties))
      ""
    else {
      val variant = attrs.get("variant").map(bootstrap_variant).getOrElse("info")
      val login = attrs.get("login").exists(_.equalsIgnoreCase("true"))
      val loginhref = attrs.get("login-href").orElse(attrs.get("href")).getOrElse("/web/textus-user-account/signin")
      val action =
        if (login)
          s"""<div class="mt-2"><a class="btn btn-sm btn-outline-${escape(variant)}" href="${escape(loginhref)}">Log in</a></div>"""
        else
          ""
      s"""<div class="alert alert-${escape(variant)} textus-capability-message" role="status">${inner}${action}</div>"""
    }
  }

  protected def apply_render_condition_control(
    html: String,
    attrs: String,
    properties: FormPageProperties
  ): String = {
    val parsed = widget_attrs(attrs)
    val allowed = parsed.get("data-textus-render-if-any").map { value =>
      render_condition_keys(value).exists(property_has_value(_, properties))
    }.orElse(parsed.get("data-textus-render-if-all").map { value =>
      val keys = render_condition_keys(value)
      keys.nonEmpty && keys.forall(property_has_value(_, properties))
    }).orElse(parsed.get("data-textus-render-if").map { value =>
      property_has_value(value, properties)
    }).getOrElse(true)
    if (allowed) html else ""
  }

  protected def render_condition_keys(value: String): Vector[String] =
    value.split("[,\\s]+").iterator.map(_.trim).filter(_.nonEmpty).toVector

  protected def property_has_value(
    key: String,
    properties: FormPageProperties
  ): Boolean =
    property_value(properties, key).exists(_.trim.nonEmpty) ||
      source_json(key, properties).exists(json_has_value)

  protected def json_has_value(json: Json): Boolean =
    json.fold(
      false,
      _ => true,
      _ => true,
      value => value.trim.nonEmpty,
      values => values.nonEmpty,
      fields => fields.nonEmpty
    )

  protected def apply_capability_control(
    html: String,
    attrs: String,
    properties: FormPageProperties
  ): String = {
    val parsed = widget_attrs(attrs)
    val capability = parsed.getOrElse("data-textus-capability", "")
    val policy = parsed.getOrElse("data-textus-capability-policy", "subject")
    if (capability_allowed(capability, policy, properties))
      html
    else {
      parsed.getOrElse("data-textus-capability-mode", "hide").trim.toLowerCase(java.util.Locale.ROOT) match {
        case "disable" | "disabled" => disable_capability_html(html)
        case _ => ""
      }
    }
  }

  protected def capability_allowed(
    capability: String,
    policy: String,
    properties: FormPageProperties
  ): Boolean = {
    val normalizedcapability = normalize_capability_token(capability)
    if (normalizedcapability.isEmpty)
      true
    else {
      policy.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "authenticated" | "login" | "session" =>
          property_value(properties, "pageContext.session.authenticated").exists(_.equalsIgnoreCase("true"))
        case _ =>
          capability_tokens(property_value(properties, "pageContext.security.capabilities").getOrElse("")).contains(normalizedcapability)
      }
    }
  }

  protected def capability_tokens(
    value: String
  ): Set[String] =
    value.split("[,\\s]+").iterator
      .map(normalize_capability_token)
      .filter(_.nonEmpty)
      .toSet

  protected def normalize_capability_token(
    value: String
  ): String =
    value.trim.toLowerCase(java.util.Locale.ROOT).replace('-', '_')

  protected def disable_capability_html(
    html: String
  ): String = {
    val outer = add_attribute_to_first_tag(
      add_class_to_first_tag(html, "textus-capability-disabled"),
      "aria-disabled=\"true\""
    )
    val controls = """(?i)<(input|button|select|textarea)\b([^>]*)>""".r.replaceAllIn(outer, m => {
      val tag = m.group(1)
      val attrs = m.group(2)
      val disabled =
        if ("""(?i)\bdisabled\b""".r.findFirstIn(attrs).isDefined) ""
        else " disabled"
      java.util.regex.Matcher.quoteReplacement(s"<${tag}${attrs}${disabled}>")
    })
    """<a\b([^>]*)>""".r.replaceAllIn(controls, m => {
      val attrs = m.group(1)
      val nohref = """\s+href\s*=\s*(?:"[^"]*"|'[^']*')""".r.replaceAllIn(attrs, "")
      val withclass = add_class_to_attrs(nohref, "disabled textus-capability-disabled")
      val aria =
        if ("""(?i)\baria-disabled\s*=""".r.findFirstIn(withclass).isDefined) ""
        else " aria-disabled=\"true\""
      val tabindex =
        if ("""(?i)\btabindex\s*=""".r.findFirstIn(withclass).isDefined) ""
        else " tabindex=\"-1\""
      java.util.regex.Matcher.quoteReplacement(s"<a${withclass}${aria}${tabindex}>")
    })
  }

  protected def add_class_to_first_tag(
    html: String,
    css: String
  ): String = {
    val pattern = """<([A-Za-z][A-Za-z0-9:-]*)([^>]*)>""".r
    pattern.findFirstMatchIn(html).map { m =>
      val replacement = s"<${m.group(1)}${add_class_to_attrs(m.group(2), css)}>"
      html.substring(0, m.start) + replacement + html.substring(m.end)
    }.getOrElse(html)
  }

  protected def add_attribute_to_first_tag(
    html: String,
    attribute: String
  ): String = {
    val pattern = """<([A-Za-z][A-Za-z0-9:-]*)([^>]*)>""".r
    pattern.findFirstMatchIn(html).map { m =>
      val attrs = m.group(2)
      val key = attribute.takeWhile(c => c != '=' && !c.isWhitespace).toLowerCase(java.util.Locale.ROOT)
      val exists = attrs.toLowerCase(java.util.Locale.ROOT).contains(s"${key}=")
      val extra = if (exists) "" else s" ${attribute}"
      val replacement = s"<${m.group(1)}${attrs}${extra}>"
      html.substring(0, m.start) + replacement + html.substring(m.end)
    }.getOrElse(html)
  }

  protected def add_class_to_attrs(
    attrs: String,
    css: String
  ): String = {
    val classpattern = """\bclass\s*=\s*(?:"([^"]*)"|'([^']*)')""".r
    classpattern.findFirstMatchIn(attrs) match {
      case Some(m) =>
        val current = Option(m.group(1)).getOrElse(m.group(2))
        val merged = (current.split("\\s+").toVector ++ css.split("\\s+").toVector)
          .filter(_.nonEmpty)
          .distinct
          .mkString(" ")
        classpattern.replaceFirstIn(attrs, java.util.regex.Matcher.quoteReplacement(s"""class="${merged}""""))
      case None =>
        s"""${attrs} class="${css}""""
    }
  }

  protected def render_hidden_context(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val standard = properties.values.toVector.collect {
      case (key, value) if isHiddenFormContextKey(key) && value.nonEmpty => key -> value
    }.sortBy(_._1)
    val standardKeys = standard.map(_._1).toSet
    val explicit = attrs.get("keys").toVector.flatMap(hidden_context_keys).flatMap { key =>
      property_value(properties, key).filter(_.nonEmpty).map(key -> _)
    }.filterNot { case (key, _) => standardKeys.contains(key) }
    (standard ++ explicit).map { case (key, value) =>
      s"""<input type="hidden" name="${escape(key)}" value="${escape(value)}">"""
    }.mkString("\n")
  }

  protected def render_operation_panel(
    attrs: Map[String, String],
    inner: String,
    properties: FormPageProperties
  ): String = {
    val title = attr_value(attrs, "title", properties).getOrElse("Operation")
    val subtitle = attr_value(attrs, "subtitle", properties)
    val status = property_non_empty(properties, "result.status").getOrElse("")
    val ok = property_non_empty(properties, "result.ok").getOrElse("")
    val subtitlehtml = subtitle.filter(_.nonEmpty).map { x =>
      s"""<p class="text-secondary mb-0">${escape(x)}</p>"""
    }.getOrElse("")
    val badge =
      if (status.isEmpty)
        ""
      else {
        val variant =
          if (ok == "true") "success"
          else if (ok == "false") "danger"
          else "secondary"
        s"""<span class="badge text-bg-${escape(variant)}">HTTP ${escape(status)}</span>"""
      }
    s"""<section class="card textus-operation-panel"><div class="card-body"><div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3"><div><h3 class="h5 mb-1">${escape(title)}</h3>${subtitlehtml}</div>${badge}</div>${inner}</div></section>"""
  }

  protected def render_field_list(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.body.data.records.0.fields")
    val fields = source_json(source, properties).flatMap(_.asArray).getOrElse(Vector.empty)
    if (fields.isEmpty)
      empty_state(attrs.getOrElse("empty", "No fields"))
    else {
      val items = fields.flatMap(_.asObject).map { obj =>
        val map = obj.toMap
        val descriptor = map.get("descriptor").flatMap(_.asObject).map(_.toMap).getOrElse(Map.empty)
        val label = json_string(map, "label").orElse(json_string(descriptor, "label")).getOrElse(json_string(map, "field_path").getOrElse("Field"))
        val path = json_string(map, "field_path").getOrElse("")
        val value = map.get("value").map(json_cell).filter(_.nonEmpty).getOrElse("not set")
        val description = json_string(descriptor, "description").getOrElse("")
        val requiredness = json_string(descriptor, "requiredness").getOrElse("")
        val hint = json_string(descriptor, "validation_hint").getOrElse("")
        val candidates = map.get("resolution_candidates").flatMap(_.asArray).map(_.size).getOrElse(0)
        val fieldstate = json_string(map, "field_state")
        val eventsummary = json_string(map, "field_event_summary")
        val eventcount = map.get("field_event_count").flatMap(_.asNumber).flatMap(_.toLong).getOrElse(0L)
        val requiredhtml =
          if (requiredness.isEmpty)
            ""
          else
            s"""<span class="badge text-bg-light border">${escape(requiredness)}</span>"""
        val candidatehtml =
          if (candidates == 0)
            ""
          else
            s"""<span class="badge text-bg-info">${candidates} candidates</span>"""
        val statehtml = fieldstate.fold("")(x => s"""<span class="badge text-bg-secondary">${escape(x)}</span>""")
        val eventhtml =
          if (eventcount == 0)
            ""
          else {
            val summary = eventsummary.filter(_.nonEmpty).map(x => s" ${escape(x)}").getOrElse("")
            s"""<p class="small text-secondary mb-0">Event:${summary}</p>"""
          }
        val descriptionhtml =
          if (description.isEmpty)
            ""
          else
            s"""<p class="text-secondary mb-1">${escape(description)}</p>"""
        val hinthtml =
          if (hint.isEmpty)
            ""
          else
            s"""<p class="small text-secondary mb-0">${escape(hint)}</p>"""
        s"""<article class="textus-field-list-item border rounded p-3"><div class="d-flex flex-wrap justify-content-between gap-2"><div><h4 class="h6 mb-1">${escape(label)}</h4><p class="small text-secondary mb-2">${escape(path)}</p></div><div class="d-flex flex-wrap gap-1">${requiredhtml}${candidatehtml}${statehtml}</div></div>${descriptionhtml}<p class="mb-1"><strong>${escape(value)}</strong></p>${hinthtml}${eventhtml}</article>"""
      }
      s"""<div class="textus-field-list d-grid gap-2">${items.mkString("\n")}</div>"""
    }
  }

  protected def render_candidate_list(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.body.data.records.0.fields.0.resolution_candidates")
    val candidates = source_json(source, properties).flatMap(_.asArray).getOrElse(Vector.empty)
    if (candidates.isEmpty)
      empty_state(attrs.getOrElse("empty", "No candidates"))
    else {
      val items = candidates.flatMap(_.asObject).map { obj =>
        val map = obj.toMap
        val id = json_string(map, "id").getOrElse("")
        val label = json_string(map, "label").getOrElse("Candidate")
        val source = json_string(map, "source").getOrElse("")
        val confidence = map.get("confidence").map(json_cell).filter(_.nonEmpty).getOrElse("")
        val evidence = json_string(map, "evidence").getOrElse("")
        val selected = json_bool(map, "selected")
        val selectedhtml =
          if (selected)
            """<span class="badge text-bg-success">selected</span>"""
          else
            """<span class="badge text-bg-secondary">candidate</span>"""
        val meta = Vector(
          Option.when(source.nonEmpty)(s"source=${source}"),
          Option.when(confidence.nonEmpty)(s"confidence=${confidence}"),
          Option.when(id.nonEmpty)(s"id=${id}")
        ).flatten.mkString(" / ")
        val evidencehtml =
          if (evidence.isEmpty)
            ""
          else
            s"""<p class="small text-secondary mb-0">${escape(evidence)}</p>"""
        s"""<article class="textus-candidate-list-item border rounded p-3"><div class="d-flex flex-wrap justify-content-between gap-2"><div><h4 class="h6 mb-1">${escape(label)}</h4><p class="small text-secondary mb-2">${escape(meta)}</p></div>${selectedhtml}</div>${evidencehtml}</article>"""
      }
      s"""<div class="textus-candidate-list d-grid gap-2">${items.mkString("\n")}</div>"""
    }
  }

  protected def render_knowledge_summary(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.body.data")
    val obj = source_json(source, properties).flatMap(_.asObject).map(_.toMap).getOrElse(Map.empty)
    val state = json_string(obj, "knowledge_space_state")
      .orElse(obj.get("knowledge_summary").flatMap(_.asObject).flatMap(x => json_string(x.toMap, "state")))
      .getOrElse("unknown")
    val counts = obj.get("knowledge_counts")
      .flatMap(_.asObject)
      .map(_.toMap)
      .orElse(obj.get("knowledge_summary").flatMap(_.asObject).flatMap(_.toMap.get("counts")).flatMap(_.asObject).map(_.toMap))
      .getOrElse(Map.empty)
    val rows = Vector(
      "node_count" -> "Nodes",
      "relationship_count" -> "Relationships",
      "frame_count" -> "Frames",
      "fact_count" -> "Facts",
      "evidence_count" -> "Evidence",
      "provenance_count" -> "Provenance"
    ).map { case (key, label) =>
      val value = counts.get(key).orElse(counts.get(key.stripSuffix("_count") + "s")).map(json_cell).getOrElse("0")
      s"""<div class="col"><article class="border rounded p-3 h-100"><p class="text-secondary mb-1">${escape(label)}</p><strong class="h4 mb-0">${escape(value)}</strong></article></div>"""
    }.mkString("\n")
    s"""<section class="textus-knowledge-summary"><div class="d-flex flex-wrap justify-content-between align-items-center mb-2"><h3 class="h6 mb-0">KnowledgeSpace</h3><span class="badge text-bg-light border">${escape(state)}</span></div><div class="row row-cols-2 row-cols-md-3 g-2">${rows}</div></section>"""
  }

  protected def json_string(
    obj: Map[String, Json],
    name: String
  ): Option[String] =
    obj.get(name).flatMap(_.asString).orElse(obj.get(name).map(json_cell)).map(_.trim).filter(_.nonEmpty)

  protected def json_bool(
    obj: Map[String, Json],
    name: String
  ): Boolean =
    obj.get(name).exists(_.asBoolean.contains(true))

  protected def hidden_context_keys(value: String): Vector[String] =
    value.split(',').toVector.map(_.trim).filter(_.nonEmpty).distinct

  protected def render_action_link(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val action = resolve_action(attrs, properties)
    action.map {
      case ActionWidgetValue(href, label, css, method) if method.equalsIgnoreCase("GET") =>
        s"""<a class="${escape(css)}" href="${escape(href)}">${escape(label)}</a>"""
      case ActionWidgetValue(href, label, css, method) =>
        action_form_html(method, href, css, label, render_hidden_context(Map.empty, properties))
    }.getOrElse("")
  }

  protected def render_action_form(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String =
    resolve_action(attrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val effectiveMethod = attrs.getOrElse("method", method)
        val context =
          if (widget_bool(attrs, "context", default = true))
            render_hidden_context(Map.empty, properties)
          else
            ""
        action_form_html(effectiveMethod, href, css, label, context)
    }.getOrElse("")

  protected def render_action_group(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val sourceActions = attrs.get("source").toVector.flatMap { source =>
      source_json(source, properties).flatMap(_.asArray).toVector.flatten.flatMap(FormResultMetadata.Action.fromJson)
    }
    val sourcePrefix = attrs.getOrElse("source-prefix", "result.action")
    val context =
      if (widget_bool(attrs, "context", default = true))
        render_hidden_context(Map.empty, properties)
      else
        ""
    val buttons =
      if (sourceActions.nonEmpty)
        sourceActions.flatMap { action =>
          action_value(action, attrs.get("button-class").getOrElse(action_group_button_class(action.name.getOrElse(""))))
            .map(action_html(_, context))
        }
      else
        action_group_names(attrs, properties).flatMap { name =>
          val actionAttrs = Map(
            "source" -> s"${sourcePrefix}.${name}",
            "class" -> attrs.getOrElse("button-class", action_group_button_class(name))
          )
          resolve_action(actionAttrs, properties).map(action_html(_, context))
        }
    if (buttons.isEmpty)
      ""
    else {
      val css = attrs.getOrElse("class", "d-flex flex-wrap gap-2 mt-3 textus-action-group")
      s"""<div class="${escape(css)}">${buttons.mkString}</div>"""
    }
  }

  protected def render_confirm_action(
    attrs: Map[String, String],
    properties: FormPageProperties,
    index: Int
  ): String = {
    val actionAttrs =
      if (attrs.contains("class"))
        attrs
      else
        attrs + ("class" -> "btn btn-outline-danger")
    resolve_action(actionAttrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val modalId = attrs.get("id").filter(_.trim.nonEmpty)
          .getOrElse(s"textus-confirm-action-${index}")
        val title = attr_value(attrs, "title", properties).getOrElse("Confirm action")
        val message = attr_value(attrs, "message", properties)
          .getOrElse(s"Please confirm ${label}.")
        val variant = bootstrap_variant(attrs.getOrElse("variant", "danger"))
        val confirmLabel = attr_value(attrs, "confirm-label", properties).getOrElse(label)
        val cancelLabel = attr_value(attrs, "cancel-label", properties).getOrElse("Cancel")
        val context =
          if (widget_bool(attrs, "context", default = true))
            render_hidden_context(Map.empty, properties)
          else
            ""
        val confirm = action_html(ActionWidgetValue(href, confirmLabel, css, method), context)
        val fallback = confirm
        s"""<span class="textus-confirm-action"><button type="button" class="${escape(css)}" data-bs-toggle="modal" data-bs-target="#${escape(modalId)}">${escape(label)}</button></span><div class="modal fade" id="${escape(modalId)}" tabindex="-1" aria-labelledby="${escape(modalId)}-label" aria-hidden="true"><div class="modal-dialog"><div class="modal-content"><div class="modal-header border-${escape(variant)}"><h2 class="modal-title h5" id="${escape(modalId)}-label">${escape(title)}</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="${escape(cancelLabel)}"></button></div><div class="modal-body">${escape(message)}</div><div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">${escape(cancelLabel)}</button>${confirm}</div></div></div></div><noscript>${fallback}</noscript>"""
    }.getOrElse("")
  }

  protected def action_group_names(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): Vector[String] =
    attrs.get("actions").map(_.split(',').toVector.map(_.trim).filter(_.nonEmpty)).filter(_.nonEmpty)
      .getOrElse {
        val count = property_value(properties, "result.actions.count").flatMap(_.toIntOption).getOrElse(0)
        if (count > 0)
          (0 until count).map(_.toString).toVector
        else
          Vector("primary")
      }

  protected def action_group_button_class(name: String): String =
    name.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "primary" | "submit" | "save" | "await" | "refresh" | "result" => "btn btn-primary"
      case _ => "btn btn-outline-primary"
    }

  protected def action_html(
    action: ActionWidgetValue,
    hiddenContext: String
  ): String =
    action match {
      case ActionWidgetValue(href, label, css, method) if method.equalsIgnoreCase("GET") =>
        s"""<a class="${escape(css)}" href="${escape(href)}">${escape(label)}</a>"""
      case ActionWidgetValue(href, label, css, method) =>
        action_form_html(method, href, css, label, hiddenContext)
    }

  protected def action_value(
    action: FormResultMetadata.Action,
    css: String
  ): Option[ActionWidgetValue] =
    action.href.map { href =>
      val label = action.label.orElse(action.name).getOrElse("Open")
      val method = action.method.getOrElse("GET")
      ActionWidgetValue(href, label, css, method)
    }

  protected final case class ActionWidgetValue(
    href: String,
    label: String,
    css: String,
    method: String
  )

  protected def resolve_action(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): Option[ActionWidgetValue] = {
    val source = attrs.getOrElse("source", "result.action.primary")
    val href = property_value(properties, s"${source}.href").getOrElse("")
    Option.when(href.nonEmpty) {
      val label = attrs.get("label")
        .orElse(property_non_empty(properties, s"${source}.label"))
        .orElse(property_non_empty(properties, s"${source}.name"))
        .getOrElse("Open")
      val css = attrs.getOrElse("class", "btn btn-primary")
      val method = property_non_empty(properties, s"${source}.method").getOrElse("GET")
      ActionWidgetValue(href, label, css, method)
    }
  }

  protected def action_form_html(
    method: String,
    href: String,
    css: String,
    label: String,
    hiddenContext: String
  ): String = {
    val inputs =
      if (hiddenContext.isEmpty)
        ""
      else
        s"${hiddenContext}\n"
    s"""<form method="${escape(method.toLowerCase(java.util.Locale.ROOT))}" action="${escape(href)}" class="d-inline">${inputs}<button type="submit" class="${escape(css)}">${escape(label)}</button></form>"""
  }

  protected def widget_bool(
    attrs: Map[String, String],
    name: String,
    default: Boolean
  ): Boolean =
    attrs.get(name).map(_.trim.toLowerCase(java.util.Locale.ROOT)) match {
      case Some("false" | "no" | "off" | "0") => false
      case Some("true" | "yes" | "on" | "1") => true
      case Some(_) => default
      case None => default
    }

  protected def property_non_empty(
    properties: FormPageProperties,
    name: String
  ): Option[String] =
    property_value(properties, name).map(_.trim).filter(_.nonEmpty)

  protected def render_form_link(
    hrefPath: String,
    label: String,
    properties: FormPageProperties
  ): String =
    property_value(properties, hrefPath).filter(_.nonEmpty) match {
      case Some(href) =>
        s"""<p><a class="btn btn-outline-primary" href="${escape(href)}">${escape(label)}</a></p>"""
      case None =>
        ""
    }

  protected def render_result_view(
    source: String,
    properties: FormPageProperties
  ): String = {
    val value = source_text(source, properties).getOrElse("")
    s"""<pre class="mt-3 p-3 bg-light border rounded">${escape(value)}</pre>"""
  }

  protected def render_table(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val totalPath = attrs.getOrElse("total", "paging.total")
    val hrefPath = attrs.getOrElse("href", "paging.href")
    val columns = table_columns(attrs.get("columns")).orElse(table_columns(source, attrs, tableColumns, defaultTableView))
    val page = int_property(properties, pagePath, 1)
    val pageSize = int_property(properties, pageSizePath, 20)
    val total = optional_int_property(properties, totalPath)
    val href = property_value(properties, hrefPath).getOrElse("")
    val table = json_table(source, properties, page, pageSize, columns, attrs).getOrElse("")
    val download = render_table_download(attrs, properties, source)
    if (widget_bool(attrs, "pagination", default = true))
      s"""${download}${table}<div class="mt-3">${render_pagination(attrs, properties)}</div>"""
    else
      s"""${download}${table}"""
  }

  protected def render_table_download(
    attrs: Map[String, String],
    properties: FormPageProperties,
    displaysource: String
  ): String = {
    val formats = table_download_formats(attrs)
    if (formats.isEmpty)
      ""
    else {
      val source = attrs.getOrElse("download-source", displaysource)
      val name = attrs.getOrElse("download-name", s"${properties.componentPath}-${properties.servicePath}-${properties.operationPath}")
      val label = attrs.getOrElse("download-label", "Download")
      val links = formats.map { format =>
        val href = table_download_href(properties, source, format, name)
        s"""<li><a class="dropdown-item" href="${escape(href)}">${escape(format.toUpperCase(java.util.Locale.ROOT))}</a></li>"""
      }.mkString
      s"""<div class="d-flex justify-content-end mb-2 textus-table-download">
         |  <div class="dropdown">
         |    <button class="btn btn-sm btn-outline-secondary dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">${escape(label)}</button>
         |    <ul class="dropdown-menu dropdown-menu-end">${links}</ul>
         |  </div>
         |</div>""".stripMargin
    }
  }

  protected def table_download_formats(
    attrs: Map[String, String]
  ): Vector[String] =
    attrs.get("download-formats")
      .orElse(attrs.get("download"))
      .toVector
      .flatMap(_.split(',').toVector)
      .map(_.trim.toLowerCase(java.util.Locale.ROOT))
      .filter(_.nonEmpty)
      .distinct

  protected def table_download_href(
    properties: FormPageProperties,
    source: String,
    format: String,
    name: String
  ): String = {
    val base =
      s"/form/${escape_path_segment(properties.componentPath)}/${escape_path_segment(properties.servicePath)}/${escape_path_segment(properties.operationPath)}/result"
    val current = properties.values.toVector.filterNot { case (key, _) =>
      is_table_download_context_key(key)
    }
    val params = current ++ Vector(
      "textus.download" -> "true",
      "textus.download.source" -> source,
      "textus.download.format" -> format,
      "textus.download.filename" -> s"${name}.${format}"
    )
    val query = params.collect {
      case (key, value) if key.nonEmpty && value.nonEmpty =>
        s"${escapeQuery(key)}=${escapeQuery(value)}"
    }.mkString("&")
    if (query.isEmpty) base else s"${base}?${query}"
  }

  protected def is_table_download_context_key(key: String): Boolean = {
    val reservedprefixes = Vector(
      "form.",
      "result.",
      "paging.",
      "pageContext.",
      "operation.",
      "error.",
      "debug.",
      "textus."
    )
    reservedprefixes.exists(key.startsWith) ||
      key == "component" ||
      key == "service" ||
      key == "operation"
  }

  protected def render_card(
    attrs: Map[String, String],
    inner: String,
    properties: FormPageProperties
  ): String = {
    val title = attr_value(attrs, "title", properties)
    val subtitle = attr_value(attrs, "subtitle", properties)
    val footer = attr_value(attrs, "footer", properties)
    val extraClass = attrs.get("class").map(x => s" ${escape(x)}").getOrElse("")
    val titlehtml = title.filter(_.nonEmpty).map { x =>
      s"""<h3 class="h5 card-title">${escape(x)}</h3>"""
    }.getOrElse("")
    val subtitlehtml = subtitle.filter(_.nonEmpty).map { x =>
      s"""<p class="card-subtitle text-secondary mb-2">${escape(x)}</p>"""
    }.getOrElse("")
    val footerhtml = footer.filter(_.nonEmpty).map { x =>
      s"""<div class="card-footer text-secondary">${escape(x)}</div>"""
    }.getOrElse("")
    s"""<article class="card textus-card${extraClass}"><div class="card-body">${titlehtml}${subtitlehtml}${inner}</div>${footerhtml}</article>"""
  }

  protected def render_record_card(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val columns = table_columns(attrs.get("columns")).orElse(table_columns(source, attrs, tableColumns, defaultTableView))
    source_json(source, properties).flatMap(record_json).flatMap(_.asObject).map { obj =>
      val map = obj.toMap
      if (map.isEmpty)
        empty_state(attrs.getOrElse("empty", "No record"))
      else
        record_card_html(map, columns, attrs)
    }.getOrElse(empty_state(attrs.getOrElse("empty", "No record")))
  }

  protected def render_card_list(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val columns = table_columns(attrs.get("columns")).orElse(table_columns(source, attrs, tableColumns, defaultTableView))
    val page = int_property(properties, pagePath, 1)
    val pageSize = int_property(properties, pageSizePath, 20)
    val cards = source_json(source, properties).flatMap(table_rows).map { rows =>
      val objects = page_rows(rows, page, pageSize).flatMap(_.asObject).map(_.toMap)
      if (objects.isEmpty)
        empty_state(attrs.getOrElse("empty", "No records"))
      else {
        val body = objects.map { obj =>
          s"""<div class="col">${record_card_html(obj, columns, attrs)}</div>"""
        }.mkString("\n")
        s"""<div class="${card_list_row_class(attrs)}">${body}</div>"""
      }
    }.getOrElse(empty_state(attrs.getOrElse("empty", "No records")))
    if (widget_bool(attrs, "pagination", default = true))
      s"""${cards}<div class="mt-3">${render_pagination(attrs, properties)}</div>"""
    else
      cards
  }

  protected def render_line_list(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val columns = table_columns(attrs.get("columns")).orElse(table_columns(source, attrs, tableColumns, defaultTableView))
    source_json(source, properties).flatMap(table_rows).map { rows =>
      val objects = rows.flatMap(_.asObject).map(_.toMap)
      if (objects.isEmpty)
        empty_state(attrs.getOrElse("empty", "No records"), None, Some("textus:line-list"))
      else
        s"""<ul class="list-group textus-line-list" data-textus-widget="textus:line-list">${objects.map(line_list_item_html(_, columns, attrs)).mkString("\n")}</ul>"""
    }.getOrElse(empty_state(attrs.getOrElse("empty", "No records"), None, Some("textus:line-list")))
  }

  protected def line_list_item_html(
    obj: Map[String, Json],
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): String = {
    val fields = columns.getOrElse(obj.keys.toVector.map(name => TableColumn(name, name)))
    val titlefield = attrs.get("title").orElse(first_existing_field(obj, Vector("title", "subject", "name", "label", "id")))
    val subtitlefield = attrs.get("subtitle").orElse(first_existing_field(obj, Vector("summary", "description", "state", "status", "updated_at")))
    val badgefield = attrs.get("badge")
    val title = titlefield.flatMap(obj.get).map(json_cell).filter(_.nonEmpty).getOrElse(attrs.getOrElse("label", "Record"))
    val subtitle = subtitlefield.flatMap(obj.get).map(json_cell).filter(_.nonEmpty)
    val badge = badgefield.flatMap(obj.get).map(json_cell).filter(_.nonEmpty)
    val excluded = (titlefield.toSet ++ subtitlefield.toSet ++ badgefield.toSet)
    val details = fields.filterNot(column => excluded.contains(column.name)).map { column =>
      val value = obj.get(column.name).map(json_cell).getOrElse("")
      s"""<dt class="col-sm-3">${escape(column.label)}</dt><dd class="col-sm-9">${escape(value)}</dd>"""
    }.mkString
    val subtitlehtml = subtitle.map(x => s"""<p class="text-secondary mb-1">${escape(x)}</p>""").getOrElse("")
    val badgehtml = badge.map { x =>
      s"""<span class="badge text-bg-${escape(status_variant(x))}">${escape(x)}</span>"""
    }.getOrElse("")
    val detailhtml =
      if (details.isEmpty)
        ""
      else
        s"""<dl class="row mb-0 mt-2 textus-line-list-fields">${details}</dl>"""
    val actionhtml = record_action_html(obj, attrs)
    val href = attrs.get("detail-href").flatMap(record_href(_, obj, attrs))
    val rowhref = href.filter(_ => widget_bool(attrs, "click-row", default = false)).
      map(x => s""" data-textus-row-href="${escape(x)}" tabindex="0" role="link"""").
      getOrElse("")
    s"""<li class="list-group-item textus-line-list-item"$rowhref><div class="d-flex align-items-start justify-content-between gap-3"><div><strong>${escape(title)}</strong>${subtitlehtml}</div>${badgehtml}</div>${detailhtml}${actionhtml}</li>"""
  }

  protected def card_list_row_class(
    attrs: Map[String, String]
  ): String = {
    val cols = bootstrap_col_count(attrs.get("cols"), 1)
    val md = bootstrap_col_count(attrs.get("md"), 2)
    val lg = attrs.get("lg").flatMap(bootstrap_col_count_option)
    (Vector("row", s"row-cols-${cols}", s"row-cols-md-${md}") ++
      lg.map(x => s"row-cols-lg-${x}") ++
      Vector("g-3", "mt-3")).mkString(" ")
  }

  protected def bootstrap_col_count(
    value: Option[String],
    default: Int
  ): Int =
    value.flatMap(bootstrap_col_count_option).getOrElse(default)

  protected def bootstrap_col_count_option(
    value: String
  ): Option[Int] =
    scala.util.Try(value.trim.toInt).toOption.filter(x => x >= 1 && x <= 6)

  protected def record_json(json: Json): Option[Json] =
    json.asObject.map(_ => json).orElse {
      table_rows(json).flatMap(_.headOption)
    }

  protected def record_card_html(
    obj: Map[String, Json],
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): String = {
    val fields = columns.getOrElse(obj.keys.toVector.map(name => TableColumn(name, name)))
    val titleField = attrs.get("title").orElse(first_existing_field(obj, Vector("title", "subject", "name", "label", "id")))
    val subtitleField = attrs.get("subtitle").orElse(first_existing_field(obj, Vector("recipient_name", "sender_name", "status", "updated_at")))
    val title = titleField.flatMap(obj.get).map(json_cell).filter(_.nonEmpty).getOrElse(attrs.getOrElse("label", "Record"))
    val subtitle = subtitleField.flatMap(obj.get).map(json_cell).filter(_.nonEmpty)
    val rows = fields.map { column =>
      val value = obj.get(column.name).map(json_cell).getOrElse("")
      s"""<dt class="col-sm-4">${escape(column.label)}</dt><dd class="col-sm-8">${escape(value)}</dd>"""
    }.mkString
    val subtitlehtml = subtitle.map(x => s"""<p class="card-subtitle text-secondary mb-2">${escape(x)}</p>""").getOrElse("")
    val actionhtml = record_action_html(obj, attrs)
    s"""<article class="card h-100 textus-record-card"><div class="card-body"><h3 class="h5 card-title">${escape(title)}</h3>${subtitlehtml}<dl class="row mb-0">${rows}</dl>${actionhtml}</div></article>"""
  }

  protected def record_action_html(
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): String =
    attrs.get("detail-href").flatMap(record_href(_, obj, attrs)).map { href =>
      val label = attrs.getOrElse("detail-label", "Open detail")
      s"""<div class="mt-3"><a class="btn btn-sm btn-outline-primary" href="${escape(href)}">${escape(label)}</a></div>"""
    }.getOrElse("")

  protected def record_href(
    template: String,
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): Option[String] = {
    val pattern = """\{([A-Za-z0-9_.-]+)\}""".r
    var ok = true
    val base = pattern.replaceAllIn(template, m => {
      obj.get(m.group(1)).map(json_cell).filter(_.nonEmpty) match {
        case Some(value) => java.util.regex.Matcher.quoteReplacement(value)
        case None =>
          ok = false
          ""
      }
    })
    Option.when(ok)(append_detail_params(base, obj, attrs))
  }

  protected def append_detail_params(
    href: String,
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): String = {
    val params = attrs.toVector.collect {
      case (key, value) if key.startsWith("detail-param-") =>
        key.stripPrefix("detail-param-") -> record_value_template(value, obj)
    }.collect {
      case (key, Some(value)) if key.nonEmpty && value.nonEmpty =>
        s"${escapeQuery(key)}=${escapeQuery(value)}"
    }
    if (params.isEmpty)
      href
    else {
      val sep = if (href.contains("?")) "&" else "?"
      s"${href}${sep}${params.mkString("&")}"
    }
  }

  protected def record_value_template(
    template: String,
    obj: Map[String, Json]
  ): Option[String] = {
    val pattern = """\{([A-Za-z0-9_.-]+)\}""".r
    var ok = true
    val value = pattern.replaceAllIn(template, m => {
      obj.get(m.group(1)).map(json_cell).filter(_.nonEmpty) match {
        case Some(value) => java.util.regex.Matcher.quoteReplacement(value)
        case None =>
          ok = false
          ""
      }
    })
    Option.when(ok)(value)
  }

  protected def first_existing_field(
    obj: Map[String, Json],
    names: Vector[String]
  ): Option[String] =
    names.find(obj.contains)

  protected def empty_state(
    message: String
  ): String =
    empty_state(message, None)

  protected def empty_state(
    message: String,
    action: Option[(String, String)],
    widget: Option[String] = None
  ): String = {
    val actionhtml = action.map { case (label, href) =>
      s"""<div class="mt-2"><a class="btn btn-sm btn-primary" href="${escape(href)}">${escape(label)}</a></div>"""
    }.getOrElse("")
    val widgetattr = widget.map(x => s""" data-textus-widget="${escape(x)}"""").getOrElse("")
    s"""<div class="alert alert-secondary textus-empty-state" role="status"${widgetattr}>${escape(message)}${actionhtml}</div>"""
  }

  protected def render_summary_card(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val title = attr_value(attrs, "title", properties).getOrElse("Summary")
    val value = attr_value(attrs, "value", properties)
      .orElse(attrs.get("source").flatMap(source => source_text(source, properties)))
      .getOrElse("")
    val subtitle = attr_value(attrs, "subtitle", properties)
    val variant = bootstrap_variant(attrs.getOrElse("variant", "primary"))
    val subtitlehtml = subtitle.filter(_.nonEmpty).map { x =>
      s"""<p class="text-secondary mb-0">${escape(x)}</p>"""
    }.getOrElse("")
    s"""<article class="card h-100 textus-summary-card border-${escape(variant)}" data-textus-widget="textus:summary-card"><div class="card-body"><p class="text-secondary mb-1">${escape(title)}</p><strong class="display-6 text-${escape(variant)}">${escape(value)}</strong>${subtitlehtml}</div></article>"""
  }

  protected def render_action_card(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String =
    resolve_action(attrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val title = attr_value(attrs, "title", properties).getOrElse(label)
        val description = attr_value(attrs, "description", properties)
          .orElse(attr_value(attrs, "subtitle", properties))
        val body = description.filter(_.nonEmpty).map { x =>
          s"""<p class="card-text text-secondary">${escape(x)}</p>"""
        }.getOrElse("")
        val action = action_html(ActionWidgetValue(href, label, css, method), render_hidden_context(Map.empty, properties))
        s"""<article class="card h-100 textus-action-card"><div class="card-body"><h3 class="h5 card-title">${escape(title)}</h3>${body}<div class="mt-3">${action}</div></div></article>"""
    }.getOrElse("")

  protected def render_job_ticket(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.job")
    val jobid = property_value(properties, s"${source}.id").getOrElse("")
    if (jobid.isEmpty)
      ""
    else {
      val title = attr_value(attrs, "title", properties).getOrElse("Job accepted")
      val status = property_non_empty(properties, s"${source}.status").getOrElse("accepted")
      val message = property_non_empty(properties, s"${source}.message")
        .orElse(property_non_empty(properties, "result.message"))
        .getOrElse("The command is running asynchronously.")
      val variant = job_status_variant(status)
      val actions =
        if (widget_bool(attrs, "actions", default = true))
          render_job_actions(attrs, properties)
        else
          ""
      s"""<article class="card textus-job-ticket border-${escape(variant)} mb-3"><div class="card-body"><div class="d-flex flex-wrap align-items-start justify-content-between gap-2"><div><h3 class="h5 card-title mb-1">${escape(title)}</h3><p class="text-secondary mb-2">${escape(message)}</p></div><span class="badge text-bg-${escape(variant)}">${escape(status)}</span></div><dl class="row mb-3"><dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${escape(jobid)}</code></dd></dl>${actions}</div></article>"""
    }
  }

  protected def render_job_panel(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.job")
    val jobid = property_value(properties, s"${source}.id").getOrElse("")
    if (jobid.isEmpty)
      ""
    else {
      val title = attr_value(attrs, "title", properties).getOrElse("Command accepted")
      val description = attr_value(attrs, "description", properties)
        .orElse(attr_value(attrs, "subtitle", properties))
        .orElse(property_non_empty(properties, "result.message"))
        .getOrElse("The command is running asynchronously.")
      val ticketattrs = attrs + ("actions" -> "false")
      val ticket = render_job_ticket(ticketattrs, properties)
      val actions = render_job_actions(attrs, properties)
      val apphref = property_value(properties, s"${source}.href").getOrElse("")
      val appjobshref = property_value(properties, "result.jobs.href").getOrElse("")
      val systemhref = s"/web/system/jobs/${escape_path_segment(jobid)}"
      val adminhref = s"/web/system/admin/jobs/${escape_path_segment(jobid)}"
      val applinks = Vector(
        Option.when(apphref.nonEmpty)(s"""<a class="btn btn-outline-primary btn-sm" href="${escape(apphref)}">Open job result</a>"""),
        Option.when(appjobshref.nonEmpty)(s"""<a class="btn btn-outline-secondary btn-sm" href="${escape(appjobshref)}">My jobs</a>""")
      ).flatten.mkString
      s"""<section class="textus-job-panel border rounded p-3 mb-3 bg-light"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-3"><div><h3 class="h5 mb-1">${escape(title)}</h3><p class="text-secondary mb-0">${escape(description)}</p></div><div class="d-flex flex-wrap gap-2">${applinks}<a class="btn btn-outline-secondary btn-sm" href="${escape(systemhref)}">System job page</a><a class="btn btn-outline-secondary btn-sm" href="${escape(adminhref)}">Debug detail</a></div></div>${ticket}${actions}</section>"""
    }
  }

  protected def render_job_actions(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val names = attrs.get("actions")
      .map(_.split(',').toVector.map(_.trim).filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .getOrElse(Vector("await", "detail"))
    val buttons = names.flatMap { name =>
      val context = render_hidden_context(Map.empty, properties)
      resolve_action(Map("source" -> s"result.action.${name}", "class" -> job_action_class(name)), properties)
        .map(action_html(_, context))
    }
    if (buttons.isEmpty)
      ""
    else
      s"""<div class="d-flex flex-wrap gap-2 textus-job-actions">${buttons.mkString}</div>"""
  }

  protected def job_status_variant(
    status: String
  ): String =
    status.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "completed" | "complete" | "succeeded" | "success" | "done" => "success"
      case "failed" | "failure" | "error" => "danger"
      case "running" | "queued" | "accepted" | "pending" => "primary"
      case "cancelled" | "canceled" | "suspended" => "warning"
      case _ => "secondary"
    }

  protected def job_action_class(
    name: String
  ): String =
    name.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "await" | "refresh" | "result" => "btn btn-primary"
      case _ => "btn btn-outline-primary"
    }

  protected def render_alert(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val variant = bootstrap_variant(attrs.getOrElse("variant", attrs.getOrElse("type", "info")))
    val title = attr_value(attrs, "title", properties)
    val message = attr_value(attrs, "message", properties)
      .orElse(attrs.get("source").flatMap(source => source_text(source, properties)))
      .orElse(property_non_empty(properties, "error.message"))
      .orElse(property_non_empty(properties, "result.message"))
      .getOrElse("")
    if (title.exists(_.nonEmpty) || message.nonEmpty) {
      val titlehtml = title.filter(_.nonEmpty).map(x => s"""<p class="alert-heading fw-semibold mb-1">${escape(x)}</p>""").getOrElse("")
      s"""<div class="alert alert-${escape(variant)} textus-alert" role="alert" data-textus-widget="textus:alert">${titlehtml}${escape(message)}</div>"""
    } else {
      ""
    }
  }

  protected def render_empty_state(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val shouldRender = attrs.get("source") match {
      case Some(source) =>
        source_json(source, properties).flatMap(table_rows).forall(_.isEmpty)
      case None =>
        true
    }
    if (shouldRender) {
      val message = attr_value(attrs, "message", properties).getOrElse("No records")
      val action = for {
        label <- attr_value(attrs, "action-label", properties)
        href <- attr_value(attrs, "action-href", properties)
      } yield label -> href
      empty_state(message, action, Some("textus:empty-state"))
    } else {
      ""
    }
  }

  protected def render_status_badge(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val value = attrs.get("value").map(resolve_attr_value(_, properties))
      .orElse(attrs.get("source").flatMap(source => source_text(source, properties)))
      .orElse(property_non_empty(properties, "result.status"))
      .orElse(property_non_empty(properties, "result.outcome"))
      .getOrElse("")
    if (value.isEmpty)
      ""
    else {
      val variant = attrs.get("variant").map(bootstrap_variant).getOrElse(status_variant(value))
      val label = attrs.get("label").map(resolve_attr_value(_, properties)).filter(_.nonEmpty).getOrElse(value)
      s"""<span class="badge text-bg-${escape(variant)} textus-status-badge" data-textus-widget="textus:status-badge">${escape(label)}</span>"""
    }
  }

  protected def attr_value(
    attrs: Map[String, String],
    name: String,
    properties: FormPageProperties
  ): Option[String] =
    attrs.get(name).map(resolve_attr_value(_, properties)).filter(_.nonEmpty)

  protected def resolve_attr_value(
    value: String,
    properties: FormPageProperties
  ): String = {
    val propertyPattern = """^\$\{([A-Za-z0-9_.-]+)\}$""".r
    value match {
      case propertyPattern(name) => property_value(properties, name).getOrElse("")
      case _ => property_value(properties, value).filter(_.nonEmpty).getOrElse(value)
    }
  }

  protected def bootstrap_variant(
    value: String
  ): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "error" | "danger" => "danger"
      case "warn" | "warning" => "warning"
      case "success" => "success"
      case "primary" => "primary"
      case "secondary" => "secondary"
      case "light" => "light"
      case "dark" => "dark"
      case "info" => "info"
      case _ => "secondary"
    }

  protected def status_variant(
    value: String
  ): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "ok" | "success" | "succeeded" | "done" | "completed" | "published" | "active" | "stable" => "success"
      case "warn" | "warning" | "pending" | "queued" | "running" | "draft" | "editing" | "imported" => "warning"
      case "error" | "failed" | "failure" | "denied" | "rejected" | "inactive" | "unresolved" => "danger"
      case "info" | "accepted" | "review" => "primary"
      case _ => "secondary"
    }

  protected def render_pagination(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val totalPath = attrs.getOrElse("total", "paging.total")
    val hrefPath = attrs.getOrElse("href", "paging.href")
    val hasNextPath = attrs.getOrElse("has-next", "paging.hasNext")
    val page = int_property(properties, pagePath, 1)
    val pageSize = int_property(properties, pageSizePath, 20)
    val total = optional_int_property(properties, totalPath)
    val href = property_value(properties, hrefPath).getOrElse("")
    val hasNext = optional_bool_property(properties, hasNextPath)
    paging_nav(page, pageSize, total, href, hasNext)
  }

  protected def render_nav_list(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val style = attrs.getOrElse("style", "buttons").trim.toLowerCase(java.util.Locale.ROOT)
    val items =
      attrs.get("items").toVector.flatMap(_.split("\\|").toVector).flatMap(nav_item(_, properties)) ++
        attrs.get("source").toVector.flatMap(source => source_json(source, properties).toVector.flatMap(nav_items_from_json(_, properties)))
    if (items.isEmpty)
      ""
    else if (style == "list")
      s"""<nav class="textus-nav-list"><div class="list-group">${items.map(nav_item_html(_, listStyle = true, properties)).mkString}</div></nav>"""
    else
      s"""<nav class="d-flex flex-wrap gap-2 mt-3 textus-nav-list">${items.map(nav_item_html(_, listStyle = false, properties)).mkString}</nav>"""
  }

  protected final case class NavListItem(
    label: String,
    href: String,
    css: String,
    method: String = "GET"
  )

  protected def nav_item(
    text: String,
    properties: FormPageProperties
  ): Option[NavListItem] = {
    val index = text.indexOf(':')
    if (index < 0)
      None
    else {
      val label = text.take(index).trim
      val rest = text.drop(index + 1).trim
      val last = rest.lastIndexOf(':')
      val (href, css) =
        if (last >= 0 && looks_like_css_class(rest.drop(last + 1).trim))
          rest.take(last).trim -> rest.drop(last + 1).trim
        else
          rest -> "btn btn-outline-secondary"
      Some(NavListItem(resolve_attr_value(label, properties), resolve_attr_value(href, properties), css))
    }
  }

  protected def nav_items_from_json(
    json: Json,
    properties: FormPageProperties
  ): Vector[NavListItem] =
    json.asArray.getOrElse(Vector.empty).flatMap(_.asObject).flatMap { obj =>
      val values = obj.toMap
      for {
        label <- values.get("label").map(json_cell).filter(_.nonEmpty)
        href <- values.get("href").map(json_cell).filter(_.nonEmpty)
      } yield {
        val css = values.get("class").map(json_cell).filter(_.nonEmpty).getOrElse("btn btn-outline-secondary")
        val method = values.get("method").map(json_cell).filter(_.nonEmpty).getOrElse("GET")
        NavListItem(
          resolve_attr_value(label, properties),
          resolve_attr_value(href, properties),
          css,
          method
        )
      }
    }

  protected def nav_item_html(
    item: NavListItem,
    listStyle: Boolean,
    properties: FormPageProperties
  ): String = {
    val method = item.method.trim.toUpperCase(java.util.Locale.ROOT)
    if (method == "GET") {
      val css =
        if (listStyle)
          s"list-group-item list-group-item-action ${item.css}"
        else
          item.css
      s"""<a class="${escape(css)}" href="${escape(item.href)}">${escape(item.label)}</a>"""
    } else {
      val css =
        if (listStyle)
          s"list-group-item list-group-item-action ${item.css}"
        else
          item.css
      action_form_html(method, item.href, css, item.label, render_hidden_context(Map.empty, properties))
    }
  }

  protected def looks_like_css_class(value: String): Boolean =
    value.startsWith("btn ") ||
      value.startsWith("btn-") ||
      value.startsWith("link-") ||
      value.startsWith("textus-")

  protected def render_property_list(
    source: String,
    properties: FormPageProperties
  ): String = {
    val prefix = if (source.endsWith(".")) source else source + "."
    val xs = properties.values.collect {
      case (key, value) if key == source || key.startsWith(prefix) => key -> value
    }
    s"""<dl class="row">${property_rows(xs)}</dl>"""
  }

  protected def render_description_list(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val columns = table_columns(attrs.get("columns")).orElse(table_columns(source, attrs, tableColumns, defaultTableView))
    source_json(source, properties).flatMap(record_json).flatMap(_.asObject).map { obj =>
      val fields = columns.getOrElse(obj.keys.toVector.map(name => TableColumn(name, name)))
      val rows = fields.flatMap { column =>
        obj(column.name).map { json =>
          s"""<dt class="col-sm-4">${escape(column.label)}</dt><dd class="col-sm-8">${escape(json_cell(json))}</dd>"""
        }
      }.mkString
      if (rows.isEmpty)
        empty_state(attrs.getOrElse("empty", "No details"))
      else
        s"""<dl class="row textus-description-list">${rows}</dl>"""
    }.getOrElse(empty_state(attrs.getOrElse("empty", "No details")))
  }

  protected def render_html_field(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val field = attrs.getOrElse("field", "content")
    val css = attrs.getOrElse("class", "textus-html-field")
    val html = source_json(source, properties)
      .flatMap(record_json)
      .flatMap(json => json_at(json, field.split('.').toVector))
      .flatMap(_.asString)
      .getOrElse("")
    if (html.isEmpty)
      ""
    else
      s"""<div class="${escape(css)}">${html}</div>"""
  }

  protected def render_error_panel(
    source: String,
    properties: FormPageProperties
  ): String = {
    val prefix = if (source.endsWith(".")) source else source + "."
    val xs = properties.values.collect {
      case (key, value) if key == source || key.startsWith(prefix) => key -> value
    }
    if (xs.isEmpty) ""
    else s"""<div class="alert alert-danger" role="alert">${property_rows(xs)}</div>"""
  }

  protected def json_table(
    source: String,
    properties: FormPageProperties,
    page: Int,
    pageSize: Int,
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): Option[String] =
    source_json(source, properties).flatMap { json =>
      val rows = table_rows(json)
      rows.flatMap(xs => records_table(page_rows(xs, page, pageSize), columns, attrs))
    }

  protected def table_rows(
    json: Json
  ): Option[Vector[Json]] =
    json.asArray
      .orElse(json_array_at(json, "data"))
      .orElse(json_array_at(json, "items"))
      .orElse(json_array_at(json, "result"))
      .orElse(json_array_at(json, "records"))

  protected def json_array_at(
    json: Json,
    name: String
  ): Option[Vector[Json]] =
    json.hcursor.downField(name).focus.flatMap(_.asArray)

  protected def table_columns(columns: Option[String]): Option[Vector[TableColumn]] =
    columns.map(_.split(',').toVector.flatMap(table_column)).filter(_.nonEmpty)

  protected def table_column(value: String): Option[TableColumn] = {
    val text = value.trim
    if (text.isEmpty)
      None
    else
      text.split(":", 2).toList match {
        case name :: label :: Nil => Some(TableColumn(name.trim, label.trim))
        case name :: Nil => Some(TableColumn(name.trim, name.trim))
        case _ => None
      }
  }

  protected def table_columns(
    source: String,
    attrs: Map[String, String],
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): Option[Vector[TableColumn]] =
    table_column_key(source, attrs, defaultTableView).flatMap(tableColumns.get)
      .orElse(table_column_key(s"${source}.data", attrs, defaultTableView).flatMap(tableColumns.get))
      .orElse(tableColumns.get(source))
      .orElse(tableColumns.get(s"${source}.data"))
      .orElse(tableColumns.get("result.data"))
      .orElse(tableColumns.get("result.body.data"))
      .filter(_.nonEmpty)

  protected def table_column_key(
    source: String,
    attrs: Map[String, String],
    defaultTableView: String
  ): Option[String] =
    attrs.get("entity").map { entity =>
      val view = attrs.getOrElse("view", defaultTableView)
      table_column_key(source, entity, view)
    }

  protected def table_column_key(
    source: String,
    entity: String,
    view: String
  ): String =
    s"${source}|entity=${NamingConventions.toNormalizedSegment(entity)}|view=${NamingConventions.toNormalizedSegment(view)}"

  protected def widget_attrs(source: String): Map[String, String] =
    """([A-Za-z0-9_.:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r.findAllMatchIn(source).map { m =>
      m.group(1) -> Option(m.group(2)).getOrElse(m.group(3))
    }.toMap

  protected def source_text(
    source: String,
    properties: FormPageProperties
  ): Option[String] =
    property_value(properties, source).orElse(source_json(source, properties).map(_.spaces2))

  protected def source_json(
    source: String,
    properties: FormPageProperties
  ): Option[Json] =
    property_value(properties, source).flatMap(parse(_).toOption).orElse {
      properties.resultBodyJson.flatMap { json =>
        val path =
          if (source.startsWith("result.body."))
            source.stripPrefix("result.body.").split('.').toVector
          else if (source.startsWith("result."))
            source.stripPrefix("result.").split('.').toVector
          else
            Vector.empty
        if (path.isEmpty)
          None
        else
          json_at(json, path).orElse {
            if (path.headOption.contains("data"))
              json_at(json, path.tail)
            else
              None
          }
      }
    }

  protected def json_at(
    json: Json,
    path: Vector[String]
  ): Option[Json] =
    path.foldLeft(Option(json)) { (z, name) =>
      z.flatMap { current =>
        current.asArray.flatMap { xs =>
          name.toIntOption.flatMap(i => xs.lift(i))
        }.orElse(PropertyValueResolver.jsonField(current, name))
      }
    }

  protected def property_value(
    properties: FormPageProperties,
    name: String
  ): Option[String] =
    PropertyValueResolver.value(properties.values, name)

  protected def page_rows(
    rows: Vector[Json],
    page: Int,
    pageSize: Int
  ): Vector[Json] = {
    val offset = math.max(0, page - 1) * math.max(1, pageSize)
    rows.slice(offset, offset + math.max(1, pageSize))
  }

  protected def records_table(
    rows: Vector[Json],
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): Option[String] = {
    val objects = rows.flatMap(_.asObject)
    if (objects.isEmpty) {
      None
    } else {
      val headers = columns.getOrElse(objects.flatMap(_.keys).distinct.map(name => TableColumn(name, name)))
      val actionHeader = attrs.get("detail-href").map(_ => "<th>Actions</th>").getOrElse("")
      val head = headers.map(h => s"<th>${escape(h.label)}</th>").mkString + actionHeader
      val body = objects.map { obj =>
        val cells = headers.map { h =>
          val value = obj(h.name).map(json_cell).getOrElse("")
          s"<td>${escape(value)}</td>"
        }.mkString
        val detailhref = attrs.get("detail-href").flatMap(record_href(_, obj.toMap, attrs))
        val rowattrs = if (widget_bool(attrs, "row-link", default = false))
          detailhref.map(href =>
            s""" class="textus-clickable-row" data-textus-row-href="${escape(href)}" tabindex="0" role="link""""
          ).getOrElse("")
        else
          ""
        val actioncell = detailhref.map { href =>
          val label = attrs.getOrElse("detail-label", "Open detail")
          s"""<td><a class="btn btn-sm btn-outline-primary" href="${escape(href)}">${escape(label)}</a></td>"""
        }.getOrElse(attrs.get("detail-href").map(_ => "<td></td>").getOrElse(""))
        s"<tr${rowattrs}>${cells}${actioncell}</tr>"
      }.mkString("\n")
      Some(s"""<div class="table-responsive mt-3"><table class="table table-sm table-striped"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>""")
    }
  }

  protected def json_cell(json: Json): String =
    json.asString
      .orElse(json.asNumber.map(_.toString))
      .orElse(json.asBoolean.map(_.toString))
      .getOrElse(json.noSpaces)

  protected def paging_nav(
    page: Int,
    pageSize: Int,
    total: Option[Int],
    href: String,
    hasNext: Option[Boolean] = None
  ): String = {
    val prev = math.max(1, page - 1)
    val next = page + 1
    val last = total.map(t => math.max(1, (t + pageSize - 1) / pageSize))
    val prevDisabled = page <= 1
    val nextDisabled = last.exists(page >= _) || hasNext.contains(false)
    val pages = last.map(l => (1 to math.min(l, 5)).toVector).getOrElse(Vector.empty)
    val pageItems = pages.map { p =>
      val active = if (p == page) " active" else ""
      s"""<li class="page-item${active}"><a class="page-link" href="${escape(paging_href(href, p, pageSize))}">${p}</a></li>"""
    }.mkString
    val current =
      if (last.isDefined) ""
      else s"""<li class="page-item active"><span class="page-link">Page ${page}</span></li>"""
    s"""<nav aria-label="Result pages"><ul class="pagination">
       |<li class="page-item${if (prevDisabled) " disabled" else ""}"><a class="page-link" href="${escape(paging_href(href, prev, pageSize))}">Previous</a></li>
       |${pageItems}${current}
       |<li class="page-item${if (nextDisabled) " disabled" else ""}"><a class="page-link" href="${escape(paging_href(href, next, pageSize))}">Next</a></li>
       |</ul></nav>""".stripMargin
  }

  protected def paging_href(
    href: String,
    page: Int,
    pageSize: Int
  ): String =
    href.replace("{page}", page.toString).replace("{pageSize}", pageSize.toString)

  protected def int_property(
    properties: FormPageProperties,
    name: String,
    default: Int
  ): Int =
    optional_int_property(properties, name).getOrElse(default)

  protected def optional_int_property(
    properties: FormPageProperties,
    name: String
  ): Option[Int] =
    property_value(properties, name).flatMap(_.toIntOption)

  protected def optional_bool_property(
    properties: FormPageProperties,
    name: String
  ): Option[Boolean] =
    property_value(properties, name).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "yes" | "on" | "1" => Some(true)
      case "false" | "no" | "off" | "0" => Some(false)
      case _ => None
    }

  protected def dashboard_state_json(
    components: Vector[Component],
    scope: String,
    name: String,
    version: Option[String],
    jobs: (Int, Int, Int, Int),
    subsystemName: String,
    subsystemVersion: Option[String],
    assemblyWarningCount: Int
  ): String = {
    val serviceCount = components.map(_.protocol.services.services.size).sum
    val operationCount = components.flatMap(_.protocol.services.services).map(_.operations.operations.length).sum
    val componentJson = components.map(component_json).mkString("[", ",", "]")
    val (running, queued, completed, failed) = jobs
    val htmlRequests = RuntimeDashboardMetrics.htmlSnapshot
    val actionCalls = RuntimeDashboardMetrics.actionCallSnapshot
    val authorizationDecisions = RuntimeDashboardMetrics.authorizationDecisionSnapshot
    val authorizationDiagnostics = RuntimeDashboardMetrics.authorizationDiagnosticCounts
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val validation = RuntimeDashboardMetrics.validationSnapshot
    val validationDiagnostics = RuntimeDashboardMetrics.validationDiagnosticCounts
    val operationRequestValidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot
    val operationRequestValidationDiagnostics = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts
    val blobOperations = RuntimeDashboardMetrics.blobOperationSnapshot
    val blobDiagnostics = RuntimeDashboardMetrics.blobDiagnosticCounts
    val avgMillis =
      if (htmlRequests.recent.isEmpty) 0L
      else htmlRequests.recent.map(_.elapsedMillis).sum / htmlRequests.recent.size
    val adminPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/admin"
      else "/web/system/admin"
    val manualPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/document"
      else "/web/system/document"
    s"""{"scope":"${json(scope)}","name":"${json(name)}","version":${version.map(v => "\"" + json(v) + "\"").getOrElse("null")},"observedAt":"${java.time.Instant.now.toString}","status":"UP","cncf":{"version":"${json(CncfVersion.current)}"},"subsystem":{"name":"${json(subsystemName)}","version":${subsystemVersion.map(v => "\"" + json(v) + "\"").getOrElse("null")}},"componentCount":${components.size},"serviceCount":${serviceCount},"operationCount":${operationCount},"actions":{"actionCalls":${snapshot_json(actionCalls, includeRecent = false)},"jobs":${jobs_json(running, queued, completed, failed)}},"dsl":{"chokepoints":${snapshot_json(dslChokepoints, includeRecent = false)},"validation":${snapshot_json(validation, includeRecent = false)},"validationDiagnostics":${string_long_map_json(validationDiagnostics)},"operationRequestValidation":${snapshot_json(operationRequestValidation, includeRecent = false)},"operationRequestValidationDiagnostics":${string_long_map_json(operationRequestValidationDiagnostics)}},"authorization":{"decisions":${snapshot_json(authorizationDecisions, includeRecent = false)},"diagnostics":${string_long_map_json(authorizationDiagnostics)}},"blob":{"operations":${snapshot_json(blobOperations, includeRecent = false)},"diagnostics":${string_long_map_json(blobDiagnostics)}},"assembly":{"warnings":{"count":${assemblyWarningCount}}},"html":{"requests":${snapshot_json(htmlRequests, includeRecent = true, Some(avgMillis))}},"links":{"admin":"${json(adminPath)}","performance":"/web/system/performance","manual":"${json(manualPath)}","console":"/web/console","assemblyWarnings":"/web/system/admin/assembly/warnings"},"components":${componentJson}}"""
  }

  protected def snapshot_json(
    snapshot: RuntimeDashboardMetrics.Snapshot,
    includeRecent: Boolean,
    recentAverageMillis: Option[Long] = None
  ): String = {
    val recent =
      if (includeRecent) {
        val xs = snapshot.recent.map { x =>
          s"""{"observedAt":${x.observedAt},"method":"${json(x.method)}","path":"${json(x.path)}","status":${x.status},"elapsedMillis":${x.elapsedMillis}}"""
        }.mkString("[", ",", "]")
        s""","recent":${xs}"""
      } else {
        ""
      }
    val avg = recentAverageMillis.map(x => s""","recentAverageMillis":${x}""").getOrElse("")
    s"""{"summary":${summary_json(snapshot.summary)},"series":{"minute":${buckets_json(snapshot.bucketsByMinute)},"hour":${buckets_json(snapshot.bucketsByHour)},"day":${buckets_json(snapshot.bucketsByDay)}}${avg}${recent}}"""
  }

  protected def summary_json(summary: RuntimeDashboardMetrics.CountSummary): String =
    s"""{"cumulative":${window_json(summary.cumulative)},"day":${window_json(summary.day)},"hour":${window_json(summary.hour)},"minute":${window_json(summary.minute)}}"""

  protected def window_json(window: RuntimeDashboardMetrics.CountWindow): String =
    s"""{"count":${window.total},"errors":${window.errors}}"""

  protected def buckets_json(buckets: Vector[RuntimeDashboardMetrics.RequestBucket]): String =
    buckets.map(x => s"""{"period":${x.period},"count":${x.count},"errors":${x.errors}}""").mkString("[", ",", "]")

  protected def string_long_map_json(values: Map[String, Long]): String =
    values.toVector
      .sortBy(_._1)
      .map { case (k, v) => s""""${json(k)}":${v}""" }
      .mkString("{", ",", "}")

}
