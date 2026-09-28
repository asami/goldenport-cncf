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
private[http] trait StaticFormAppRendererFormatPart { self: StaticFormAppRendererSystemAdminPart & StaticFormAppRendererSupport & StaticFormAppRendererBlobTagPart & StaticFormAppRendererComponentAdminPart & StaticFormAppRendererCorePart & StaticFormAppRendererFormPart & StaticFormAppRendererJobPart & StaticFormAppRendererObservabilityPart & StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*
  protected def manual_raw_json(value: Any): Option[Json] =
    value match {
      case Some(x) => manual_raw_json(x)
      case None => Some(Json.Null)
      case null => Some(Json.Null)
      case r: Record =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          r.asMap.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            manual_raw_json(v).map(k -> _)
          }
        )))
      case m: Map[?, ?] =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          m.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            manual_raw_json(v).map(k.toString -> _)
          }
        )))
      case xs: Seq[?] =>
        Some(Json.fromValues(xs.toVector.flatMap(manual_raw_json)))
      case s: String => Some(Json.fromString(s))
      case b: Boolean => Some(Json.fromBoolean(b))
      case i: Int => Some(Json.fromInt(i))
      case l: Long => Some(Json.fromLong(l))
      case d: Double if !d.isNaN && !d.isInfinity => Some(Json.fromDoubleOrNull(d))
      case f: Float if !f.isNaN && !f.isInfinity => Some(Json.fromFloatOrNull(f))
      case n: Number => Some(Json.fromString(n.toString))
      case x => Some(Json.fromString(x.toString))
    }

  protected def manual_raw_text(value: Any, indent: Int = 0): String = {
    val pad = "  " * indent
    value match {
      case Some(x) => manual_raw_text(x, indent)
      case None => "null"
      case null => "null"
      case r: Record =>
        r.asMap.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case m: Map[?, ?] =>
        m.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case xs: Seq[?] =>
        xs.toVector.map { x =>
          val rendered = manual_raw_text(x, indent + 1)
          if (rendered.contains("\n")) s"${pad}-\n$rendered" else s"${pad}- $rendered"
        }.mkString("\n")
      case x => x.toString
    }
  }

  protected def manual_record_values(
    value: Option[Any]
  ): Map[String, Any] =
    value match {
      case Some(Some(x)) => manual_record_values(Some(x))
      case Some(r: Record) => r.asMap
      case Some(m: Map[?, ?]) => m.toVector.collect { case (k: String, v) => k -> v }.toMap
      case _ => Map.empty
    }

  protected def manual_record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value match {
      case Some(Some(x)) => manual_record_seq(Some(x))
      case Some(xs: Seq[?]) => xs.collect { case r: Record => r }.toVector
      case _ => Vector.empty
    }

  protected def manual_seq_values(
    value: Option[Any]
  ): Vector[String] =
    value match {
      case Some(Some(x)) => manual_seq_values(Some(x))
      case Some(xs: Seq[?]) => xs.toVector.flatMap(manual_scalar)
      case Some(x) => manual_scalar(x).toVector
      case None => Vector.empty
    }

  protected def manual_scalar(
    value: Any
  ): Option[String] =
    value match {
      case Some(x) => manual_scalar(x)
      case None => None
      case null => None
      case s: String if s.nonEmpty => Some(s)
      case b: Boolean => Some(b.toString)
      case n: Number => Some(n.toString)
      case _ => None
    }

  protected def raw_format_tabs(
    json: String,
    yaml: String,
    prefix: String
  ): String = {
    val token = s"${prefix}-${math.abs((json + yaml).hashCode)}"
    s"""<div class="${escape(prefix)}-raw-tabs mt-3">
       |  <ul class="nav nav-tabs" id="${token}-tablist" role="tablist">
       |    <li class="nav-item" role="presentation">
       |      <button class="nav-link active" id="${token}-json-tab" data-bs-toggle="tab" data-bs-target="#${token}-json-pane" type="button" role="tab" aria-controls="${token}-json-pane" aria-selected="true">JSON</button>
       |    </li>
       |    <li class="nav-item" role="presentation">
       |      <button class="nav-link" id="${token}-yaml-tab" data-bs-toggle="tab" data-bs-target="#${token}-yaml-pane" type="button" role="tab" aria-controls="${token}-yaml-pane" aria-selected="false">YAML</button>
       |    </li>
       |  </ul>
       |  <div class="tab-content border border-top-0 rounded-bottom">
       |    <div class="tab-pane fade show active" id="${token}-json-pane" role="tabpanel" aria-labelledby="${token}-json-tab" tabindex="0">
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${escape(json)}</code></pre>
       |    </div>
       |    <div class="tab-pane fade" id="${token}-yaml-pane" role="tabpanel" aria-labelledby="${token}-yaml-tab" tabindex="0">
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${escape(yaml)}</code></pre>
       |    </div>
       |  </div>
       |</div>""".stripMargin
  }

  protected def json_to_yaml(jsontext: String): String =
    io.circe.parser.parse(jsontext).toOption.map(json_to_yaml).getOrElse(jsontext)

  protected def json_to_yaml(json: Json): String = {
    def _go_(value: Json, indent: Int): String = {
      val pad = "  " * indent
      value.fold(
        jsonNull = "null",
        jsonBoolean = _.toString,
        jsonNumber = _.toString,
        jsonString = s => yaml_quote(s),
        jsonArray = xs =>
          if (xs.isEmpty) "[]"
          else xs.toVector.map { item =>
            item.fold(
              jsonNull = s"${pad}- null",
              jsonBoolean = b => s"${pad}- ${b}",
              jsonNumber = n => s"${pad}- ${n}",
              jsonString = s => s"${pad}- ${yaml_quote(s)}",
              jsonArray = _ => s"${pad}-\n${_go_(item, indent + 1)}",
              jsonObject = _ => s"${pad}-\n${_go_(item, indent + 1)}"
            )
          }.mkString("\n"),
        jsonObject = obj =>
          if (obj.isEmpty) "{}"
          else obj.toVector.map { case (key, item) =>
            item.fold(
              jsonNull = s"${pad}${key}: null",
              jsonBoolean = b => s"${pad}${key}: ${b}",
              jsonNumber = n => s"${pad}${key}: ${n}",
              jsonString = s => s"${pad}${key}: ${yaml_quote(s)}",
              jsonArray = _ => s"${pad}${key}:\n${_go_(item, indent + 1)}",
              jsonObject = _ => s"${pad}${key}:\n${_go_(item, indent + 1)}"
            )
          }.mkString("\n")
      )
    }
    _go_(json, 0)
  }

  protected def yaml_quote(s: String): String =
    "\"" + s.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c => c.toString
    } + "\""

  protected def manual_card(
    title: String,
    body: String,
    id: Option[String] = None
  ): String = {
    val idtext = id.map(x => s""" id="${escape(x)}"""").getOrElse("")
    s"""<article${idtext} class="card manual-card shadow-sm">
       |  <div class="card-body">
       |    <h2 class="card-title h5">${escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin
  }

  protected def simple_page(
    title: String,
    subtitle: String,
    body: String,
    assetCompletion: StaticFormAppLayout.AssetCompletionOptions =
      StaticFormAppLayout.AssetCompletionOptions()
  ): String =
    StaticFormAppLayout.completeDeclaredAssets(
      StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
        title = title,
        subtitle = subtitle,
        body = body,
        extraHead =
          """|    .admin-card, .manual-card, .textus-card { margin-bottom: 1rem; }
             |    .admin-section { margin-bottom: 1rem; }
             |    .admin-section > h2 { margin-bottom: .75rem; }
             |    .admin-action-row { margin-top: 1rem; }
             |    .admin-empty-state { color: var(--bs-secondary-color); }
             |""".stripMargin
      )),
      assetCompletion
    )

  protected def property_rows(properties: Map[String, String]): String =
    properties.toVector.sortBy(_._1).map { case (key, value) =>
      s"""<dt class="col-sm-3">${escape(key)}</dt><dd class="col-sm-9"><code>${escape(value)}</code></dd>"""
    }.mkString("\n")
}
