package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.egress.RestEgress
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.component.Component

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object AdminComponentFactory {
  def helloWorld(): Component = {
    val op = spec.OperationDefinition.Instance(
      spec.OperationDefinition.Specification(
        name = "ping",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition()
      )
    )
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
        ingresses = IngressCollection(),
        egresses = EgressCollection(Vector(RestEgress())),
        projections = ProjectionCollection()
      )
    )
    Component.create(protocol)
  }
}

object HelloWorldSubsystemFactory {
  def helloWorld(): Subsystem =
    Subsystem(
      name = "hello-world",
      components = Map(
        "admin" -> AdminComponentFactory.helloWorld()
      )
    )
}
