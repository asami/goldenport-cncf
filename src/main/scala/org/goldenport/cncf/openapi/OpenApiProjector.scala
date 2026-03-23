package org.goldenport.cncf.openapi

import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString
import org.slf4j.LoggerFactory

/*
 * @since   Jan.  8, 2026
 *  version Jan. 20, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjector {
  private val log = LoggerFactory.getLogger("org.goldenport.cncf.openapi.OpenApiProjector")

  private final case class PathSpec(
    path: String,
    tag: String,
    component: String,
    service: String,
    operation: String,
    operationId: String,
    httpMethod: String,
    parameters: Vector[String],
    hasRequestBody: Boolean,
    responseSchemaKind: String,
    summary: String,
    description: String
  )

  def forSubsystem(subsystem: Subsystem): String = {
    val paths =
      subsystem.components.flatMap { comp =>
        log.trace(
          s"[openapi:trace] component=${comp.name} services=${comp.protocol.services.services.map(_.name)}"
        )
        _paths(comp.name, comp.protocol.services.services)
      }.distinct.sortBy(_.path)
    _openapi_json(paths)
  }

  private def _paths(
    componentName: String,
    services: Vector[ServiceDefinition]
  ): Vector[PathSpec] =
    services.flatMap { service =>
      log.trace(
        s"[openapi:trace] service=${service.name} operations=${service.operations.operations.map(_.name)}"
      )
      service.operations.operations.toVector.map { op =>
        val normalizedComponent = NamingConventions.toNormalizedSegment(componentName)
        val normalizedService = NamingConventions.toNormalizedSegment(service.name)
        val normalizedOperation = NamingConventions.toNormalizedSegment(op.name)
        val serviceTag = s"${normalizedComponent}.${normalizedService}"
        val httpMethod = _infer_http_method(service.name, op.name).toUpperCase
        val summary = _operation_summary(componentName, service, op)
        val description = _operation_description(componentName, service, op)
        log.trace(
          s"[openapi:trace] path=/${normalizedComponent}/${normalizedService}/${normalizedOperation} method=$httpMethod"
        )
        PathSpec(
          path = NamingConventions.toNormalizedPath(componentName, service.name, op.name),
          tag = s"experimental:${serviceTag}",
          component = componentName,
          service = service.name,
          operation = op.name,
          operationId = NamingConventions.toOperationId(componentName, service.name, op.name),
          httpMethod = httpMethod,
          parameters = Vector.empty,
          hasRequestBody = _has_request_body(op),
          responseSchemaKind = "object",
          summary = summary,
          description = description
        )
      }
    }
 
  private def _trim_string(p: Option[String]): Option[String] =
    p.map(_.trim).filter(_.nonEmpty)

  private def _trim_i18n(p: Option[I18nString]): Option[String] =
    p.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _operation_summary(
    componentName: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.summary).
      orElse(_trim_i18n(operation.specification.description)).
      orElse(_trim_i18n(service.specification.summary)).
      getOrElse(s"${componentName}.${service.name}.${operation.name}")

  private def _operation_description(
    componentName: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.description).
      orElse(_trim_i18n(service.specification.description)).
      orElse(_trim_i18n(service.specification.summary)).
      getOrElse(
        s"Component: ${componentName}\n" +
          s"Service: ${service.name}\n" +
          s"Operation: ${operation.name}\n" +
          "(experimental; subject to change in Phase 2.8)"
      )

  private def _infer_http_method(serviceName: String, operationName: String): String = {
    val lowered = operationName.toLowerCase
    if (serviceName.toLowerCase == "http" && (lowered == "post" || lowered == "put"))
      lowered
    else if (lowered == "post" || lowered == "put")
      lowered
    else
      "get"
  }

  private def _has_request_body(op: OperationDefinition): Boolean =
    op.specification.request.parameters.exists(_.kind == ParameterDefinition.Kind.Property)

  private def _openapi_json(paths: Vector[PathSpec]): String = {
    val pathsJson =
      paths.map { path =>
        val p = _escape(path.path)
        val tag = _escape(path.tag)
        val summary = _escape(path.summary)
        val description = _escape(path.description)
        // TODO (Phase 2.8): Redesign OpenAPI tagging strategy.
        // TODO (Phase 2.8): Decide whether Service should map to OpenAPI tags.
        // TODO (Phase 2.8): Remove or formalize `experimental:*` tags.
        // TODO Phase 2.8: Replace experimental summary/description
        // with contract-level operation specifications.
        val method = path.httpMethod
        val parametersSection = _parameters_section(path.parameters)
        val requestBodySection =
          if (path.hasRequestBody)
            s""","requestBody":{"content":{"application/json":{"schema":{"type":"object"}}}}"""
          else
            ""
        val responsesSection =
          s""""responses":{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"${path.responseSchemaKind}"}}}}}"""
        s""""${p}":{"${method}":{"tags":["${tag}"],"summary":"${summary}","description":"${description}","operationId":"${path.operationId}",${parametersSection}${requestBodySection},${responsesSection}}}"""
      }.mkString(",")
    s"""{"openapi":"3.0.0","info":{"title":"CNCF API","version":"0.1.0"},"paths":{${pathsJson}}}"""
  }

  private def _parameters_section(parameters: Vector[String]): String =
    if (parameters.isEmpty) {
      """"parameters":[]"""
    } else {
      val entries = parameters.map { param =>
        s"""{"name":"${_escape(param)}","in":"query","schema":{"type":"string"}}"""
      }.mkString(",")
      s"""parameters":[${entries}]"""
    }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
