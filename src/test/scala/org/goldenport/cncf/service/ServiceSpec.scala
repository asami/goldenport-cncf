package org.goldenport.cncf.service

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version Apr. 29, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class ServiceSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "Service" should {

    "satisfy basic properties" in {
      Given("the reserved Service semantic contract")
      When("the pending basic-property specification is evaluated")
      Then("the contract remains explicitly pending")
      pending
    }

    "preserve invariants" in {
      Given("the reserved Service invariant contract")
      When("the pending invariant specification is evaluated")
      Then("the contract remains explicitly pending")
      pending
    }

    "observe direct request construction failures through common diagnostics" in {
      Given("a directly invoked Service installed in its owning subsystem")
      val operation = InvalidRequestOperation()
      val service = spec.ServiceDefinition(
        name = "media",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = TestComponentFactory.admittedEmptySubsystem("direct-service-validation")
      val component = TestComponentFactory.create("direct_service_validation", protocol, subsystem = subsystem)
      subsystem.add(component)
      try {
        val target = component.services.services.head
        val before = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L)

        When("request construction fails before ActionCall creation")
        val result = target.invokeRequest(Request.of(
          component = "direct_service_validation",
          service = "media",
          operation = "upload"
        ))

        Then("the common operation-request validation observer records the structured diagnostic")
        result shouldBe a[Consequence.Failure[_]]
        RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L) should be > before
      } finally {
        subsystem.shutdown()
      }
    }
  }
}

private final case class InvalidRequestOperation() extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "upload",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.argumentFormatError("contentType", "MIME type", "not-a-mime")
}
