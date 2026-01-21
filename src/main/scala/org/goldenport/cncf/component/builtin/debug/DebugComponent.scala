package org.goldenport.cncf.component.builtin.debug

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.action.{ActionCall, Command}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan. 20, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class DebugComponent() extends Component {
}

object DebugComponent {
  val name: String = "debug"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] =
      Vector(DebugComponent())

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      // val request = spec.RequestDefinition()
      // val response = spec.ResponseDefinition()
      // val operations = NonEmptyVector.of(
      //   DebugHttpEchoOperation(request, response, None),
      //   DebugHttpEchoOperation(request, response, Some("get")),
      //   DebugHttpEchoOperation(request, response, Some("post")),
      //   DebugHttpEchoOperation(request, response, Some("put")),
      //   DebugHttpEchoOperation(request, response, Some("delete"))
      // )
      // val service = spec.ServiceDefinition(
      //   name = "http",
      //   operations = spec.OperationDefinitionGroup(operations = operations)
      // )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(DebugHttpService)
      )
      val protocol = Protocol(
        services = services,
        handler = ProtocolHandler(
          ingresses = IngressCollection(Vector(RestIngress())),
          egresses = EgressCollection(Vector(RestEgress())),
          projections = ProjectionCollection()
        )
      )
      val instanceId = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceId,
        protocol
      )
    }
  }
}
