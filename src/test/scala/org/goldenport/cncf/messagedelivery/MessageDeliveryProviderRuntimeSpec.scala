package org.goldenport.cncf.messagedelivery

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.builtin.messagedeliverystub.MessageDeliveryStubComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemMessageDeliveryBinding, GenericSubsystemMessageDeliveryProviderBinding, GenericSubsystemSecurityBinding, Subsystem}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class MessageDeliveryProviderRuntimeSpec extends AnyWordSpec with Matchers {
  "MessageDeliveryProviderRuntime" should {
    "reject send when the configured message-delivery provider is disabled" in {
      val subsystem = Subsystem(
        name = "textus-notify",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val stub = MessageDeliveryStubComponent.Factory.create(ComponentCreate(
        subsystem = subsystem,
        origin = ComponentOrigin.Builtin
      )).participants.head
      subsystem.add(Vector(stub))
      subsystem.withDescriptor(
        GenericSubsystemDescriptor(
          path = java.nio.file.Path.of("<memory>"),
          subsystemName = "textus-notify",
          componentBindings = Vector(GenericSubsystemComponentBinding("textus-message-delivery-stub")),
          security = Some(
            GenericSubsystemSecurityBinding(
              messageDelivery = Some(
                GenericSubsystemMessageDeliveryBinding(
                  providers = Vector(
                    GenericSubsystemMessageDeliveryProviderBinding(
                      name = "textus-message-delivery-stub",
                      component = "textus-message-delivery-stub",
                      enabled = Some(false),
                      priority = Some(100),
                      isDefault = Some(true)
                    )
                  )
                )
              )
            )
          )
        )
      )
      given ExecutionContext = stub.logic.executionContext()

      val result = MessageDeliveryProviderRuntime.send(
        summon[ExecutionContext],
        UnifiedMessage(
          channel = DeliveryChannel.Email,
          recipient = "alice@example.com",
          subject = Some("Password reset"),
          body = "reset link"
        )
      )

      result.isSuccess shouldBe false
    }

    "record stub deliveries deterministically when wired" in {
      MessageDeliveryStubComponent.clearDeliveries()
      val subsystem = Subsystem(
        name = "textus-notify",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val stub = MessageDeliveryStubComponent.Factory.create(ComponentCreate(
        subsystem = subsystem,
        origin = ComponentOrigin.Builtin
      )).participants.head
      subsystem.add(Vector(stub))
      subsystem.withDescriptor(
        GenericSubsystemDescriptor(
          path = java.nio.file.Path.of("<memory>"),
          subsystemName = "textus-notify",
          componentBindings = Vector(GenericSubsystemComponentBinding("textus-message-delivery-stub")),
          security = Some(
            GenericSubsystemSecurityBinding(
              messageDelivery = Some(
                GenericSubsystemMessageDeliveryBinding(
                  providers = Vector(
                    GenericSubsystemMessageDeliveryProviderBinding(
                      name = "textus-message-delivery-stub",
                      component = "textus-message-delivery-stub",
                      enabled = Some(true),
                      priority = Some(100),
                      isDefault = Some(true)
                    )
                  )
                )
              )
            )
          )
        )
      )
      given ExecutionContext = stub.logic.executionContext()

      val result = MessageDeliveryProviderRuntime.send(
        summon[ExecutionContext],
        UnifiedMessage(
          channel = DeliveryChannel.Email,
          recipient = "alice@example.com",
          subject = Some("Password reset"),
          body = "reset link"
        )
      )

      result.toOption.flatMap(_.providerMessageId) should not be empty
      MessageDeliveryStubComponent.deliveries.map(x => (x.recipient, x.subject)) shouldBe Vector(
        ("alice@example.com", Some("Password reset"))
      )
    }
  }
}
