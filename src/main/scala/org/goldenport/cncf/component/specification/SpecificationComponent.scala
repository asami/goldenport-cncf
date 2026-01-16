package org.goldenport.cncf.component.specification

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, Query, ResourceAccess}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentInit
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  8, 2026
 * @version Jan. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class SpecificationComponent() extends Component {
}

object SpecificationComponent {
  val name: String = "spec"
  val componentId = ComponentId(name) // TODO static

  class Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] = {
      Vector(SpecificationComponent())
    }

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val subsystem = params.subsystem
      val exportService = new DefaultExportSpecificationService(subsystem)
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition()
      val op = new ExportOperationDefinition(exportService, request, response)
      val service = spec.ServiceDefinition(
        name = "export",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(op)
        )
      )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(service)
      )
      val protocol = Protocol(
        services = services,
        handler = ProtocolHandler(
          ingresses = IngressCollection(Vector(RestIngress())),
          egresses = EgressCollection(Vector(RestEgress())),
          projections = ProjectionCollection()
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }
  }
}

trait ExportSpecificationService {
  def formats(): List[String]
  def exportSpec(format: String): String
}

private final class DefaultExportSpecificationService(
  subsystem: Subsystem
) extends ExportSpecificationService {
  def formats(): List[String] = List("openapi")

  def exportSpec(format: String): String =
    format match {
      case "openapi" => OpenApiProjector.forSubsystem(subsystem)
    }
}

private final class ExportOperationDefinition(
  exportService: ExportSpecificationService,
  request: spec.RequestDefinition,
  response: spec.ResponseDefinition
) extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "openapi",
      request = request,
      response = response
    )

  def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    val _ = req
    Consequence.success(ExportSpecificationAction(req, "openapi", exportService))
  }
}

private final case class ExportSpecificationAction(
  request: Request,
  format: String,
  exportService: ExportSpecificationService
) extends Query() {
//  val name = "openapi"

  def createCall(core: ActionCall.Core): ActionCall =
    ExportSpecificationCall(core, format, exportService)
}

private final case class ExportSpecificationCall(
  core: ActionCall.Core,
  format: String,
  exportService: ExportSpecificationService
) extends ActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(
      OperationResponse.Scalar(exportService.exportSpec(format))
    )
}
