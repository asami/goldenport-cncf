package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.datatype.I18nString
import org.goldenport.protocol.spec.{ServiceDefinition, OperationDefinition}

/*
 * @since   Mar.  5, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjection {
  import MetaProjectionSupport._

  def projectComponent(component: Component): String = {
    val componentName = component.name
    val paths = component.protocol.services.services.flatMap { service =>
      service.operations.operations.toVector.map { op =>
        val path = NamingConventions.toNormalizedPath(component.name, service.name, op.name)
        val method = _infer_method(service.name, op.name)
        val operationId = NamingConventions.toOperationId(component.name, service.name, op.name)
        val summary = _operation_summary(service, op).getOrElse(s"${service.name}.${op.name}")
        val description = _operation_description(service, op)
        s""""${_escape(path)}":{"${method}":{"operationId":"${_escape(operationId)}","summary":"${_escape(summary)}","description":"${_escape(description)}","responses":{"200":{"description":"OK"}}}}"""
      }
    }.mkString(",")
    val aggregates = _json_array_strings(aggregateMetas(component).map(_.name))
    val views = _json_array_views(viewMetas(component))
    val operations = _json_array_operations(operationMetas(component))
    s"""{"openapi":"3.0.0","info":{"title":"${_escape(componentName)} API","version":"0.1.0","x-cncf-aggregate-collections":${aggregates},"x-cncf-view-collections":${views},"x-cncf-operation-definitions":${operations}},"paths":{${paths}}}"""
  }

  private def _trim_i18n(p: Option[I18nString]): Option[String] =
    p.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _operation_summary(service: ServiceDefinition, op: OperationDefinition): Option[String] =
    _trim_i18n(op.specification.summary).
      orElse(_trim_i18n(op.specification.description)).
      orElse(_trim_i18n(service.specification.summary))

  private def _operation_description(service: ServiceDefinition, op: OperationDefinition): String =
    _trim_i18n(op.specification.description).
      orElse(_trim_i18n(service.specification.description)).
      orElse(_operation_summary(service, op)).
      getOrElse(s"${service.name}.${op.name}")

  private def _infer_method(serviceName: String, operationName: String): String = {
    val lowered = operationName.toLowerCase
    if (serviceName.toLowerCase == "http" && (lowered == "post" || lowered == "put" || lowered == "delete"))
      lowered
    else if (lowered == "post" || lowered == "put" || lowered == "delete")
      lowered
    else
      "get"
  }

  private def _escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  private def _json_array_strings(xs: Vector[String]): String =
    xs.map(x => s""""${_escape(x)}"""").mkString("[", ",", "]")

  private def _json_array_views(xs: Vector[ViewMeta]): String = {
    val entries = xs.map { x =>
      val names = _json_array_strings(x.viewNames)
      s"""{"name":"${_escape(x.name)}","entityName":"${_escape(x.entityName)}","viewNames":${names}}"""
    }
    entries.mkString("[", ",", "]")
  }

  private def _json_array_operations(xs: Vector[OperationMeta]): String = {
    val entries = xs.map { x =>
      val parameters = x.parameters.map { p =>
        s"""{"name":"${_escape(p.getString("name").getOrElse(""))}","datatype":"${_escape(p.getString("datatype").getOrElse(""))}","multiplicity":"${_escape(p.getString("multiplicity").getOrElse(""))}"}"""
      }.mkString("[", ",", "]")
      s"""{"name":"${_escape(x.name)}","kind":"${_escape(x.kind)}","inputType":"${_escape(x.inputType)}","outputType":"${_escape(x.outputType)}","inputValueKind":"${_escape(x.inputValueKind)}","parameters":${parameters}}"""
    }
    entries.mkString("[", ",", "]")
  }
}
