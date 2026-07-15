package org.goldenport.cncf.openapi

import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString
import org.goldenport.schema.{DataConfidentiality, DataType, Multiplicity, WebValidationHints, XBlob, XFileBundle}
import org.slf4j.LoggerFactory

/*
 * @since   Jan.  8, 2026
 *  version Jan. 20, 2026
 *  version Apr. 30, 2026
 *  version May.  8, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjector {
  private val _log = LoggerFactory.getLogger("org.goldenport.cncf.openapi.OpenApiProjector")
  private val REST_BASE_PATH = "/rest/v1"

  private final case class PathSpec(
    path: String,
    tag: String,
    component: String,
    service: String,
    operation: String,
    operationId: String,
    httpMethod: String,
    parameters: Vector[ParameterSpec],
    hasRequestBody: Boolean,
    hasMultipartBody: Boolean,
    requestBodyFields: Vector[ParameterSpec],
    responseSchemaKind: String,
    summary: String,
    description: String
  )

  private final case class ParameterSpec(
    name: String,
    datatype: DataType,
    multiplicity: Multiplicity,
    required: Boolean,
    validation: WebValidationHints,
    binary: Boolean = false,
    confidentiality: DataConfidentiality = DataConfidentiality.Public
  ) {
    def multiple: Boolean =
      multiplicity match {
        case Multiplicity.OneMore | Multiplicity.ZeroMore => true
        case Multiplicity.Range(_, to) => to > 1
        case Multiplicity.Ranges(ranges) => ranges.exists(_.head.to > 1)
        case _ => false
      }
  }

  def forSubsystem(subsystem: Subsystem): String = {
    val paths =
      subsystem.components.flatMap { comp =>
        _log.trace(
          s"[openapi:trace] component=${comp.name} services=${comp.protocol.services.services.map(_.name)}"
        )
        _paths(comp.name, comp.protocol.services.services)
      }.distinct.sortBy(_.path)
    _openapi_json(paths)
  }

  private def _paths(
    componentname: String,
    services: Vector[ServiceDefinition]
  ): Vector[PathSpec] =
    services.flatMap { service =>
      _log.trace(
        s"[openapi:trace] service=${service.name} operations=${service.operations.operations.map(_.name)}"
      )
      service.operations.operations.toVector.map { op =>
        val normalizedcomponent = NamingConventions.toNormalizedSegment(componentname)
        val normalizedservice = NamingConventions.toNormalizedSegment(service.name)
        val normalizedoperation = NamingConventions.toNormalizedSegment(op.name)
        val servicetag = s"${normalizedcomponent}.${normalizedservice}"
        val hasmultipartbody = _has_multipart_body(op)
        val hasrequestbody = _has_request_body(op)
        val inferredhttpmethod = _infer_http_method(service.name, op.name).toUpperCase
        val httpmethod =
          if (hasrequestbody && inferredhttpmethod == "GET") "POST"
          else inferredhttpmethod
        val summary = _operation_summary(componentname, service, op)
        val description = _operation_description(componentname, service, op)
        _log.trace(
          s"[openapi:trace] path=${REST_BASE_PATH}/${normalizedcomponent}/${normalizedservice}/${normalizedoperation} method=$httpmethod"
        )
        val requestparameters = _query_parameters(op)
        val requestbodyfields = _request_body_fields(op)
        PathSpec(
          path = s"${REST_BASE_PATH}${NamingConventions.toNormalizedPath(componentname, service.name, op.name)}",
          tag = s"experimental:${servicetag}",
          component = componentname,
          service = service.name,
          operation = op.name,
          operationId = NamingConventions.toOperationId(componentname, service.name, op.name),
          httpMethod = httpmethod,
          parameters = requestparameters,
          hasRequestBody = hasrequestbody,
          hasMultipartBody = hasmultipartbody,
          requestBodyFields = requestbodyfields,
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
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.summary).
      orElse(_trim_i18n(operation.specification.description)).
      orElse(_trim_i18n(service.specification.summary)).
      getOrElse(s"${componentname}.${service.name}.${operation.name}")

  private def _operation_description(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.description).
      orElse(_trim_i18n(service.specification.description)).
      orElse(_trim_i18n(service.specification.summary)).
      getOrElse(
        s"Component: ${componentname}\n" +
          s"Service: ${service.name}\n" +
          s"Operation: ${operation.name}\n" +
          "(experimental; subject to change in Phase 2.8)"
      )

  private def _infer_http_method(servicename: String, operationname: String): String = {
    val lowered = operationname.toLowerCase
    if (servicename.toLowerCase == "http" && (lowered == "post" || lowered == "put"))
      lowered
    else if (lowered == "post" || lowered == "put")
      lowered
    else
      "get"
  }

  private def _has_request_body(op: OperationDefinition): Boolean =
    op.specification.request.parameters.exists(_.kind == ParameterDefinition.Kind.Property) ||
      _has_multipart_body(op)

  private def _has_multipart_body(op: OperationDefinition): Boolean =
    op.specification.request.parameters.exists(_is_binary_upload_parameter)

  private def _is_binary_upload_parameter(parameter: ParameterDefinition): Boolean =
    parameter.datatype == XBlob ||
      parameter.datatype == XFileBundle ||
      Option(parameter.datatype).map(_.name).exists { name =>
        val normalized = name.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)
        normalized == "blob" || normalized == "filebundle"
      }

  private def _query_parameters(op: OperationDefinition): Vector[ParameterSpec] =
    op.specification.request.parameters.toVector
      .filterNot(_.kind == ParameterDefinition.Kind.Property)
      .filterNot(_is_binary_upload_parameter)
      .map(_parameter_spec)

  private def _request_body_fields(op: OperationDefinition): Vector[ParameterSpec] =
    op.specification.request.parameters.toVector
      .collect {
      case p if _is_binary_upload_parameter(p) => _parameter_spec(p).copy(binary = true)
      case p if p.kind == ParameterDefinition.Kind.Property => _parameter_spec(p)
    }

  private def _parameter_spec(p: ParameterDefinition): ParameterSpec =
    ParameterSpec(
      name = p.name,
      datatype = p.datatype,
      multiplicity = p.multiplicity,
      required = p.web.required.getOrElse(_is_required(p.multiplicity)),
      validation = p.web.validation,
      confidentiality = p.confidentiality
    )

  private def _is_required(p: Multiplicity): Boolean =
    p match {
      case Multiplicity.One | Multiplicity.OneMore => true
      case Multiplicity.Range(from, _) => from > 0
      case Multiplicity.Ranges(ranges) => ranges.exists(_.head.from > 0)
      case _ => false
    }

  private def _openapi_json(paths: Vector[PathSpec]): String = {
    val pathsjson =
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
        val parameterssection = _parameters_section(path.parameters)
        val requestbodysection =
          if (path.hasMultipartBody)
            s""","requestBody":{"content":{"multipart/form-data":{"schema":${_request_body_schema(path.requestBodyFields)}}}}"""
          else if (path.hasRequestBody)
            s""","requestBody":{"content":{"application/json":{"schema":${_request_body_schema(path.requestBodyFields)}}}}"""
          else
            ""
        val responsesection =
          s""""responses":{"200":{"description":"OK","content":{"application/json":{"schema":{"type":"${path.responseSchemaKind}"}}}}}"""
        s""""${p}":{"${method}":{"tags":["${tag}"],"summary":"${summary}","description":"${description}","operationId":"${path.operationId}",${parameterssection}${requestbodysection},${responsesection}}}"""
      }.mkString(",")
    s"""{"openapi":"3.0.0","info":{"title":"CNCF API","version":"0.1.0"},"paths":{${pathsjson}}}"""
  }

  private def _parameters_section(parameters: Vector[ParameterSpec]): String =
    if (parameters.isEmpty) {
      """"parameters":[]"""
    } else {
      val entries = parameters.map { param =>
        s"""{"name":"${_escape(param.name)}","in":"query","required":${param.required},"schema":${_parameter_schema(param)}}"""
      }.mkString(",")
      s""""parameters":[${entries}]"""
    }

  private def _request_body_schema(fields: Vector[ParameterSpec]): String =
    if (fields.isEmpty) {
      """{"type":"object"}"""
    } else {
      val properties = fields.map {
        case parameter =>
          s""""${_escape(parameter.name)}":${_parameter_schema(parameter)}"""
      }.mkString(",")
      val required = fields.filter(_.required).map(x => s""""${_escape(x.name)}"""").mkString(",")
      val requiredsection = if (required.isEmpty) "" else s""","required":[${required}]"""
      s"""{"type":"object","properties":{${properties}}${requiredsection}}"""
    }

  private def _parameter_schema(p: ParameterSpec): String = {
    val valuefields =
      if (p.binary)
        Vector("\"type\":\"string\"", "\"format\":\"binary\"")
      else if (_is_i18n_text(p.datatype)) {
        val scalar = _json_object(_string_schema_fields(p.validation))
        val localemap = _json_object(Vector("\"type\":\"object\"", s"\"additionalProperties\":${scalar}"))
        Vector(s"\"oneOf\":[${scalar},${localemap}]")
      } else
        _scalar_schema_fields(p.datatype, p.validation)
    val shapedfields =
      if (p.multiple)
        Vector("\"type\":\"array\"", s"\"items\":${_json_object(valuefields)}")
      else
        valuefields
    _json_object(
      shapedfields ++ Vector(
        s"\"x-textus-datatype\":\"${_escape(p.datatype.name)}\"",
        s"\"x-textus-confidentiality\":\"${p.confidentiality.label}\""
      )
    )
  }

  private def _scalar_schema_fields(
    datatype: DataType,
    validation: WebValidationHints
  ): Vector[String] =
    _normalized_datatype(datatype) match {
      case "boolean" => Vector("\"type\":\"boolean\"")
      case "byte" | "short" | "int" | "integer" | "long" | "nonnegativeinteger" | "positiveinteger" =>
        Vector("\"type\":\"integer\"") ++ _numeric_schema_fields(validation)
      case "float" | "double" | "decimal" =>
        Vector("\"type\":\"number\"") ++ _numeric_schema_fields(validation)
      case _ => _string_schema_fields(validation)
    }

  private def _string_schema_fields(validation: WebValidationHints): Vector[String] =
    Vector("\"type\":\"string\"") ++ Vector(
      validation.minLength.map(x => s"\"minLength\":$x"),
      validation.maxLength.map(x => s"\"maxLength\":$x"),
      validation.pattern.map(x => s"\"pattern\":\"${_escape(x)}\"")
    ).flatten

  private def _numeric_schema_fields(validation: WebValidationHints): Vector[String] =
    Vector(
      validation.min.map(x => s"\"minimum\":$x"),
      validation.max.map(x => s"\"maximum\":$x"),
      validation.step.map(x => s"\"multipleOf\":$x")
    ).flatten

  private def _is_i18n_text(p: DataType): Boolean =
    Set(
      "title",
      "text",
      "i18nlabel",
      "i18ntitle",
      "i18nbrief",
      "i18nsummary",
      "i18ndescription",
      "i18ntext",
      "i18nmessage"
    ).contains(_normalized_datatype(p))

  private def _normalized_datatype(p: DataType): String =
    Option(p.name).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)

  private def _json_object(fields: Vector[String]): String =
    fields.mkString("{", ",", "}")

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
