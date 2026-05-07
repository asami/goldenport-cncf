package org.goldenport.cncf.usernotification

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventPublishOption, ReceptionDomainEvent}
import org.goldenport.cncf.job.{ActionId, InMemoryJobEngine, JobRunMode, JobStatus, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationEventForwardingBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
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

    "forward configured application Job events through EventBus routing" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-forward-1", app = Some("blog")),
        EventPublishOption(persistent = true)
      )

      result.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
      sink.head.recipientUserId shouldBe "alice"
      sink.head.notificationType shouldBe "cncf.job"
      sink.head.actionUrl shouldBe Some("/web/blog/jobs/job-forward-1")
      sink.head.metadata.get("jobId") shouldBe Some("job-forward-1")
      sink.head.metadata.get("sourceEventName") shouldBe Some("job.succeeded")
    }

    "skip default Job event forwarding without application context" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-internal-1", app = None),
        EventPublishOption(persistent = true)
      )

      result.toOption.map(_.dispatchedCount) shouldBe Some(0)
      sink shouldBe empty
    }

    "skip default Job event forwarding for synchronous managed jobs" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-sync-1", app = Some("blog"), runMode = "sync"),
        EventPublishOption(persistent = true)
      )

      result.toOption.map(_.dispatchedCount) shouldBe Some(0)
      sink shouldBe empty
    }

    "allow explicit event forwarding opt-in for internal Job events" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true, appVisibleOnly = Some(false)))

      val result = subsystem.eventBus.publish(
        _job_event("job.failed", "job-internal-opt-in-1", app = None),
        EventPublishOption(persistent = true)
      )

      result.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
      sink.head.actionUrl shouldBe None
      sink.head.priority shouldBe Some("high")
    }

    "deduplicate forwarded Job event notifications by job id and trigger" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      val first = subsystem.eventBus.publish(_job_event("job.succeeded", "job-dedupe-1", app = Some("blog")), EventPublishOption(persistent = true))
      val second = subsystem.eventBus.publish(_job_event("job.succeeded", "job-dedupe-1", app = Some("blog")), EventPublishOption(persistent = true))

      first.toOption.map(_.dispatchedCount) shouldBe Some(1)
      second.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
    }

    "forward JobEngine lifecycle events through EventBus routing" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      val task = _success_task("job-engine-forwarding")

      val jobid = subsystem.jobEngine.submit(
        List(task),
        component.logic.executionContext(),
        JobSubmitOption(parameters = Map(
          "web.app" -> "blog",
          "web.service" -> "post",
          "web.operation" -> "publish"
        ))
      ).toOption.get
      subsystem.jobEngine match {
        case m: InMemoryJobEngine => m.drainAll()
        case _ => ()
      }

      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      sink.size shouldBe 1
      sink.head.actionUrl shouldBe Some(s"/web/blog/jobs/${jobid.value}")
      sink.head.metadata.get("jobId") shouldBe Some(jobid.value)
      sink.head.metadata.get("sourceEventName") shouldBe Some("job.succeeded")
    }

    "do not forward synchronous JobEngine lifecycle events through default rules" in {
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      val task = _success_task("job-engine-sync-not-forwarded")

      val jobid = subsystem.jobEngine.submit(
        List(task),
        component.logic.executionContext(),
        JobSubmitOption(
          runMode = JobRunMode.Sync,
          parameters = Map(
            "web.app" -> "blog",
            "web.service" -> "post",
            "web.operation" -> "publish"
          )
        )
      ).toOption.get

      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      sink shouldBe empty
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

  private def _descriptor(
    enabled: Boolean,
    appVisibleOnly: Option[Boolean] = None
  ): GenericSubsystemDescriptor =
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
              ),
              eventForwarding = appVisibleOnly.toVector.map { visibleOnly =>
                GenericSubsystemUserNotificationEventForwardingBinding(
                  event = "job.failed",
                  appVisibleOnly = Some(visibleOnly)
                )
              }
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

  private def _job_event(
    name: String,
    jobId: String,
    app: Option[String],
    runMode: String = "async"
  ): ReceptionDomainEvent =
    ReceptionDomainEvent(
      name = name,
      kind = name,
      payload = Map(
        "job-id" -> jobId,
        "status" -> name.stripPrefix("job."),
        "job-run-mode" -> runMode,
        "submitter-principal-id" -> "alice",
        "web.service" -> "post",
        "web.operation" -> "publish",
        "message" -> "done"
      ) ++ app.map("web.app" -> _),
      attributes = Map("correlation-id" -> "corr-1")
    )

  private def _success_task(name: String): JobTask =
    new JobTask {
      val actionId: ActionId = ActionId.generate()
      override def operationName: Option[String] = Some(name)
      def run(ctx: ExecutionContext): TaskOutcome = {
        val _ = ctx
        TaskSucceeded(OperationResponse.Scalar(name))
      }
    }
}
