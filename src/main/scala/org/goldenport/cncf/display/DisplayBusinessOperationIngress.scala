package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.goldenport.http.HttpRequest
import org.goldenport.record.Field

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** Ordinary read-only Operation returning already admitted metadata. */
private[cncf] object DisplayBusinessOperationIngress {
  import DisplayBusinessOperation.{Registry, _input_invalid_c}
  private val _names = Vector("resourceType", "screenId")

  def operations(componentId: ComponentId, registry: Registry): Vector[OperationDefinition] =
    if (registry.nonEmpty) Vector(new BoundOperation(componentId, registry)) else Vector.empty

  private final class BoundOperation(private val _component_id: ComponentId, private val _registry: Registry)
      extends OperationDefinition with OpenApiOperationProjection {
    def openApiHttpMethod: OpenApiHttpMethod = OpenApiHttpMethod.GET
    val specification: OperationDefinition.Specification = OperationDefinition.Specification(
      name = "describeBusiness",
      request = RequestDefinition(_names.map(name => ParameterDefinition(content = BaseContent.simple(name),
        kind = ParameterDefinition.Kind.Property, domain = ValueDomain(XString, Multiplicity.One))).toList),
      response = ResponseDefinition(result = List(DataType.Named("OperationResponse"))))

    def createOperationRequest(request: Request): Consequence[OperationRequest] = for {
      _ <- _shape_c(request, _component_id.name)
      resource <- _property_c(request, "resourceType")
      screen <- _property_c(request, "screenId")
      payload <- DisplayBusinessOperationCodec.encodeJsonC(_registry, resource, screen)
    } yield new DescriptorAction(request, payload)
  }
  private final class DescriptorAction(val request: Request, private val _payload: io.circe.Json) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = new DescriptorCall(core, _payload)
  }
  private final class DescriptorCall(val core: ActionCall.Core, private val _payload: io.circe.Json) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = Consequence.success(OperationResponse.Json(_payload))
  }
  private def _shape_c(request: Request, componentname: String): Consequence[Unit] = {
    if (request == null || request.component != Some(componentname) || request.service != Some("display") ||
        request.operation != "describeBusiness" || request.arguments == null || request.arguments.nonEmpty ||
        request.switches == null || request.switches.nonEmpty || request.properties == null ||
        request.properties.exists(value => value == null || value.name == null ||
          (!_names.contains(value.name) && !_framework_property(value.name))))
      _input_invalid_c("ELEMENT_INVALID", "request")
    else request.source match {
      case Some(Request.Source.Http(http)) => _http_shape_c(http)
      case _ => Consequence.unit
    }
  }
  private def _http_shape_c(request: HttpRequest): Consequence[Unit] = {
    if (request == null || request.method != HttpRequest.GET || request.query == null || request.form == null ||
        request.form.fields.nonEmpty || request.body.nonEmpty ||
        request.query.fields.exists(field => field == null || field.key == null ||
          (!_names.contains(field.key) && !_framework_property(field.key))))
      _input_invalid_c("ELEMENT_INVALID", "request.http")
    else _names.foldLeft(Consequence.unit) { (result, name) => result.flatMap { _ =>
      request.query.fields.filter(_.key == name) match {
        case Vector(Field(_, Field.Value.Single(text: String))) if DisplayBusinessOperation._id_valid(text) => Consequence.unit
        case _ => _input_invalid_c("ELEMENT_INVALID", "request." + name)
      }
    } }
  }
  private def _property_c(request: Request, name: String): Consequence[String] =
    request.properties.filter(_.name == name) match {
      case property :: Nil => property.value match {
        case text: String if DisplayBusinessOperation._id_valid(text) => Consequence.success(text)
        case _ => _input_invalid_c("ELEMENT_INVALID", "request." + name)
      }
      case _ => _input_invalid_c("ELEMENT_INVALID", "request." + name)
    }
  private def _framework_property(name: String): Boolean = {
    val lower = name.toLowerCase(java.util.Locale.ROOT)
    name.startsWith("textus.") || name.startsWith("cncf.") || name.startsWith("security.") || name.startsWith("crud.") ||
      lower == "x-textus-session" || lower.startsWith("x-textus-debug-") ||
      Set("principalId", "principal_id", "subjectId", "subject_id", "privilege", "capability", "capabilities", "role", "roles").contains(name)
  }
}
