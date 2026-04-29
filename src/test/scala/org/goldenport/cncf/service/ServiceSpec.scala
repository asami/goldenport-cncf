package org.goldenport.cncf.service

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version Dec. 23, 2025
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
class ServiceSpec extends AnyWordSpec with Matchers {

  "Service" should {

    "satisfy basic properties" in {
      pending
    }

    "preserve invariants" in {
      pending
    }

    "observe direct request construction failures through common diagnostics" in {
      val operation = _InvalidRequestOperation()
      val service = spec.ServiceDefinition(
        name = "media",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val component = TestComponentFactory.create("direct_service_validation", protocol)
      val target = component.services.services.head
      val before = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L)

      val result = target.invokeRequest(Request.of(
        component = "direct_service_validation",
        service = "media",
        operation = "upload"
      ))

      result shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L) should be > before
    }
  }
}

private final case class _InvalidRequestOperation() extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "upload",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.argumentFormatError("contentType", "MIME type", "not-a-mime")
}
