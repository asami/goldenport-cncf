/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
package org.goldenport.cncf.component

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall}
import org.goldenport.cncf.event.ReceptionDomainEvent
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class OperationRequestActionDispatcherSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "OperationRequestActionDispatcher" should {
    "project event payload values with the operation parameter kind" in {
      Given("an event operation with one required property")
      val operation = _PropertyOperation("consume-event")
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
          name = "events",
          operations = spec.OperationDefinitionGroup(NonEmptyVector.one(operation))
        )))
      )
      val component = TestComponentFactory.create("event_property_dispatch", protocol)
      val dispatcher = new OperationRequestActionDispatcher(component.logic)
      val event = ReceptionDomainEvent(
        name = "sample.created",
        kind = "sample-event",
        payload = Map("id" -> "sample-1"),
        attributes = Map.empty,
        occurredAt = java.time.Instant.now()
      )

      When("the event is parsed as an operation request")
      val parsed = dispatcher.parseValidateAction("events.consume-event", event)

      Then("the payload value is available as the declared property")
      parsed.toOption.getOrElse(fail(s"event dispatch parse failed: $parsed"))
        .action.request.properties.map(x => x.name -> x.value) should contain("id" -> "sample-1")
    }
  }
}

private final case class _PropertyOperation(
  opname: String
) extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(parameters = List(spec.ParameterDefinition(
        content = BaseContent.simple("id"),
        kind = spec.ParameterDefinition.Kind.Property,
        domain = ValueDomain(XString, Multiplicity.One)
      ))),
      response = spec.ResponseDefinition.void
    )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    request.properties.find(_.name == "id") match {
      case Some(_) => Consequence.success(_PropertyAction(request))
      case None => Consequence.argumentMissing("id")
    }
}

private final case class _PropertyAction(
  request: Request
) extends Action {
  def createCall(core: ActionCall.Core): ActionCall =
    throw new UnsupportedOperationException("parse-only test action")
}
