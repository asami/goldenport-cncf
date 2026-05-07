package org.goldenport.cncf.usernotification

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem}
import org.goldenport.protocol.Protocol
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class UserNotificationProviderRuntimeSpec extends AnyWordSpec with Matchers {
  "UserNotificationProviderRuntime" should {
    "reject notify when the configured provider is disabled" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = false))
      given ExecutionContext = component.logic.executionContext()

      val result = UserNotificationProviderRuntime.notify(
        summon[ExecutionContext],
        _request()
      )

      result.isSuccess shouldBe false
      sink shouldBe empty
    }

    "dispatch to the configured provider when wired" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      given ExecutionContext = component.logic.executionContext()

      val result = UserNotificationProviderRuntime.notify(
        summon[ExecutionContext],
        _request()
      )

      result.toOption.flatMap(_.notificationId) shouldBe Some("notification-1")
      sink.map(_.recipientUserId).toVector shouldBe Vector("alice")
    }
  }

  private def _component(
    providerName: String,
    sink: ArrayBuffer[UserNotificationRequest]
  ): (Component, Subsystem) = {
    val subsystem = Subsystem(
      name = "textus-notify",
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )
    val component = new Component() {
      override def userNotificationProviders: Vector[UserNotificationProvider] =
        Vector(_provider(providerName, sink))
    }
    val id = ComponentId("textus_user_notification")
    val core = Component.Core.create(
      name = "UserNotification",
      componentid = id,
      instanceid = ComponentInstanceId.default(id),
      protocol = Protocol.empty,
      jobEngine = subsystem.jobEngine
    )
    val initialized = component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
      .withArtifactMetadata(
        Component.ArtifactMetadata(
          sourceType = "spec",
          name = "UserNotification",
          version = "0.0.0",
          component = Some("textus-user-notification")
        )
      )
    subsystem.add(Vector(initialized))
    initialized -> subsystem
  }

  private def _descriptor(enabled: Boolean): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = "textus-notify",
      componentBindings = Vector(GenericSubsystemComponentBinding("textus-user-notification")),
      runtime = Some(
        GenericSubsystemRuntimeBinding(
          userNotification = Some(
            GenericSubsystemUserNotificationBinding(
              providers = Vector(
                GenericSubsystemUserNotificationProviderBinding(
                  name = "textus-user-notification",
                  component = "textus-user-notification",
                  enabled = Some(enabled),
                  priority = Some(100),
                  isDefault = Some(true)
                )
              )
            )
          )
        )
      )
    )

  private def _provider(
    providerName: String,
    sink: ArrayBuffer[UserNotificationRequest]
  ): UserNotificationProvider =
    new UserNotificationProvider {
      val name: String = providerName

      def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = {
        sink += request
        Consequence.success(UserNotificationResult(notificationId = Some("notification-1")))
      }
    }

  private def _request(): UserNotificationRequest =
    UserNotificationRequest(
      recipientUserId = "alice",
      notificationType = "cncf.job",
      channel = "in-app",
      title = "Job succeeded",
      body = "done"
    )
}
