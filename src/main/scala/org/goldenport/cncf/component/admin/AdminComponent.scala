package org.goldenport.cncf.component.admin

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInitParams}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.component.ComponentLogic
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
class AdminComponent(val core: Component.Core) extends Component {
}

object AdminComponent {
  val name: String = "admin"
  val componentId = ComponentId(name) // TODO static

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentInitParams): Vector[Component] = {
      Vector(_admin())
    }

    private def _admin(): Component = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition()
      val op = new PingOperationDefinition(request, response)
      val service = spec.ServiceDefinition(
        name = "system",
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
      val core = Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
      AdminComponent(core)
    }
  }

  private final class PingOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "ping",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: org.goldenport.protocol.Request
    ): Consequence[OperationRequest] = {
      val _ = req
      Consequence.success(ComponentLogic.PingAction())
    }
  }
}
