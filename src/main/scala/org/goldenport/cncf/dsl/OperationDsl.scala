package org.goldenport.cncf.dsl

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/**
 * Stage 5 DSL (Level 1 / Level 2 ready).
 *
 * Level 1:
 *   operation(domain, service, name) { req => Consequence[OperationResponse] }
 *
 * Level 2 will be added later as an overload:
 *   operation_call(domain, service, name) { core => ActionCall }
 */
/*
 * @since   Jan. 11, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
trait OperationDsl {
  protected def dslDefaultServiceName: String = "default"
  protected def dslDomain: String = "default"

  protected final def operation(
    domain: String,
    service: String,
    name: String
  )(
    handler: Request => Consequence[OperationResponse]
  ): spec.OperationDefinition = {
    val op = new OperationDsl.RequestOperationDefinition(domain, service, name, handler)
    register_operation(domain, service, op)
    op
  }

  protected final def operation(
    service: String,
    name: String
  )(
    handler: Request => Consequence[OperationResponse]
  ): spec.OperationDefinition = {
    operation(dslDomain, service, name)(handler)
  }

  protected def register_operation(
    domain: String,
    service: String,
    op: spec.OperationDefinition
  ): Unit = {
    val _ = (domain, service, op)
  }
}

object OperationDsl {
  def buildProtocol(
    serviceName: String,
    operations: NonEmptyVector[spec.OperationDefinition]
  ): Protocol = {
    val request = spec.RequestDefinition()
    val response = spec.ResponseDefinition()
    val service = spec.ServiceDefinition(
      name = serviceName,
      operations = spec.OperationDefinitionGroup(operations = operations)
    )
    val services = spec.ServiceDefinitionGroup(services = Vector(service))
    Protocol(
      services = services,
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector.empty),
        egresses = EgressCollection(Vector.empty),
        projections = ProjectionCollection()
      )
    )
  }

  def buildProtocolFromServices(
    services: Vector[spec.ServiceDefinition]
  ): Protocol = {
    Protocol(
      services = spec.ServiceDefinitionGroup(services = services),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector.empty),
        egresses = EgressCollection(Vector.empty),
        projections = ProjectionCollection()
      )
    )
  }

  final class RequestOperationDefinition(
    domain: String,
    service: String,
    opName: String,
    handler: Request => Consequence[OperationResponse]
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = opName,
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition()
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      val _ = (domain, service)
      Consequence.success(RequestCommand(opName, req, handler))
    }
  }
}
