package org.goldenport.cncf.usernotification

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.event.{EventPublishOption, EventStore, ReceptionDomainEvent}
import org.goldenport.cncf.job.{ActionId, JobRunMode, JobStatus, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationEventForwardingBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  7, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class UserNotificationProviderRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "UserNotificationProviderRuntime" should {
    "reject notify when the configured provider is disabled" in {
      Given("a disabled user-notification provider")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = false))
      given ExecutionContext = component.logic.executionContext()

      When("a notification is requested")
      val result = UserNotificationProviderRuntime.notify(
        summon[ExecutionContext],
        _request()
      )

      Then("the capability failure is structured and nothing is delivered")
      result.isSuccess shouldBe false
      result match
        case Consequence.Failure(conclusion) =>
          conclusion.observation.taxonomy shouldBe Taxonomy.serviceUnavailable
          conclusion.observation.cause.kind shouldBe Some(Cause.Kind.Capability)
          conclusion.observation.cause.descriptor.facets should contain (Descriptor.Facet.Service("user-notification"))
          conclusion.observation.cause.descriptor.facets should contain (Descriptor.Facet.Name("user-notification-provider"))
          conclusion.observation.cause.descriptor.facets should contain (Descriptor.Facet.State("provider-unavailable"))
        case _ =>
          fail("expected disabled provider failure")
      sink shouldBe empty
    }

    "dispatch to the configured provider when wired" in {
      Given("an enabled and wired user-notification provider")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      given ExecutionContext = component.logic.executionContext()

      When("a notification is requested")
      val result = UserNotificationProviderRuntime.notify(
        summon[ExecutionContext],
        _request()
      )

      Then("the provider receives the notification")
      result.toOption.flatMap(_.notificationId) shouldBe Some("notification-1")
      sink.map(_.recipientUserId).toVector shouldBe Vector("alice")
    }

    "forward configured application Job events through EventBus routing" in {
      Given("default application-visible Job event forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      When("an application Job success event is published")
      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-forward-1", app = Some("blog")),
        EventPublishOption(persistent = true)
      )

      Then("one application notification is delivered with Job context")
      result.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
      sink.head.recipientUserId shouldBe "alice"
      sink.head.notificationType shouldBe "cncf.job"
      sink.head.actionUrl shouldBe Some("/web/blog/jobs/job-forward-1")
      sink.head.metadata.get("jobId") shouldBe Some("job-forward-1")
      sink.head.metadata.get("sourceEventName") shouldBe Some("job.succeeded")
    }

    "skip default Job event forwarding without application context" in {
      Given("default application-visible Job event forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      When("an internal Job event without application context is published")
      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-internal-1", app = None),
        EventPublishOption(persistent = true)
      )

      Then("the event is not forwarded")
      result.toOption.map(_.dispatchedCount) shouldBe Some(0)
      sink shouldBe empty
    }

    "skip default Job event forwarding for synchronous managed jobs" in {
      Given("default application-visible Job event forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      When("a synchronous managed Job event is published")
      val result = subsystem.eventBus.publish(
        _job_event("job.succeeded", "job-sync-1", app = Some("blog"), runmode = "sync"),
        EventPublishOption(persistent = true)
      )

      Then("the event is not forwarded")
      result.toOption.map(_.dispatchedCount) shouldBe Some(0)
      sink shouldBe empty
    }

    "allow explicit event forwarding opt-in for internal Job events" in {
      Given("event forwarding explicitly includes internal Job events")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true, appvisibleonly = Some(false)))

      When("an internal Job failure event is published")
      val result = subsystem.eventBus.publish(
        _job_event("job.failed", "job-internal-opt-in-1", app = None),
        EventPublishOption(persistent = true)
      )

      Then("one high-priority notification without an application link is delivered")
      result.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
      sink.head.actionUrl shouldBe None
      sink.head.priority shouldBe Some("high")
    }

    "deduplicate forwarded Job event notifications by job id and trigger" in {
      Given("default application-visible Job event forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (_, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))

      When("the same Job event trigger is published twice")
      val first = subsystem.eventBus.publish(_job_event("job.succeeded", "job-dedupe-1", app = Some("blog")), EventPublishOption(persistent = true))
      val second = subsystem.eventBus.publish(_job_event("job.succeeded", "job-dedupe-1", app = Some("blog")), EventPublishOption(persistent = true))

      Then("the forwarding reception remains idempotent")
      first.toOption.map(_.dispatchedCount) shouldBe Some(1)
      second.toOption.map(_.dispatchedCount) shouldBe Some(1)
      sink.size shouldBe 1
    }

    "preserve the authorized caller execution profile for forwarding diagnostics" in {
      Given("a caller-scoped notification forwarding dispatch")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      val base = component.logic.executionContext()
      val callerids = IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("caller", "notification"),
        base.clock,
        "notification-forwarding-caller"
      )
      given ExecutionContext = ExecutionContext.withIdGenerationContext(base, callerids)

      When("the forwarding subscription dispatches under the authorized caller context")
      val dispatched = UserNotificationEventForwarder
        .subscription(subsystem)
        .handler
        .dispatchAuthorized(_job_event("job.succeeded", "job-caller-profile-1", app = Some("blog")))

      Then("the diagnostic Event identity retains the caller namespace")
      dispatched.isSuccess shouldBe true
      val diagnostics = subsystem.eventStore
        .query(EventStore.Query(name = Some("user-notification.forwarding.sent")))
        .toOption
        .getOrElse(Vector.empty)
      diagnostics should have size 1
      diagnostics.head.id.major shouldBe "caller"
      diagnostics.head.id.minor shouldBe "notification"
    }

    "forward JobEngine lifecycle events through EventBus routing" in {
      Given("an asynchronous JobEngine task and application-visible forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val deliverylatch = new CountDownLatch(1)
      val (component, subsystem) = _component("textus-user-notification", sink, Some(deliverylatch))
      subsystem.withDescriptor(_descriptor(enabled = true))
      val task = _success_task("job-engine-forwarding")

      When("the Job is submitted")
      val jobid = subsystem.jobEngine.submit(
        List(task),
        component.logic.executionContext(),
        JobSubmitOption(parameters = Map(
          "web.app" -> "blog",
          "web.service" -> "post",
          "web.operation" -> "publish"
        ))
      ).toOption.getOrElse(fail("job submission failed"))
      And("the JobEngine await contract observes its completion")
      subsystem.jobEngine.awaitResult(jobid, 5000L).toOption shouldBe defined
      deliverylatch.await(5L, TimeUnit.SECONDS) shouldBe true

      Then("the completed Job event is forwarded with its Job identity")
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      sink.size shouldBe 1
      sink.head.actionUrl shouldBe Some(s"/web/blog/jobs/${jobid.value}")
      sink.head.metadata.get("jobId") shouldBe Some(jobid.value)
      sink.head.metadata.get("sourceEventName") shouldBe Some("job.succeeded")
    }

    "not forward synchronous JobEngine lifecycle events through default rules" in {
      Given("a synchronous JobEngine task and default forwarding")
      val sink = ArrayBuffer.empty[UserNotificationRequest]
      val (component, subsystem) = _component("textus-user-notification", sink)
      subsystem.withDescriptor(_descriptor(enabled = true))
      val task = _success_task("job-engine-sync-not-forwarded")

      When("the Job is submitted synchronously")
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
      ).toOption.getOrElse(fail("job submission failed"))

      Then("the Job succeeds without emitting an application notification")
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      sink shouldBe empty
    }
  }

  private def _component(
    providername: String,
    sink: ArrayBuffer[UserNotificationRequest],
    deliverylatch: Option[CountDownLatch] = None
  ): (Component, Subsystem) = {
    val subsystem = Subsystem(
      name = "textus-notify",
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )
    val component = new Component() {
      override def userNotificationProviders: Vector[UserNotificationProvider] =
        Vector(_provider(providername, sink, deliverylatch))
    }
    val id = ComponentId("org.goldenport.cncf.test.TextusUserNotification")
    val core = Component.Core.create(
      name = id.name,
      componentId = id,
      instanceId = ComponentInstanceId.default(id),
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
    appvisibleonly: Option[Boolean] = None
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
              eventForwarding = appvisibleonly.toVector.map { visibleonly =>
                GenericSubsystemUserNotificationEventForwardingBinding(
                  event = "job.failed",
                  appVisibleOnly = Some(visibleonly)
                )
              }
            )
          )
        )
      )
    )

  private def _provider(
    providername: String,
    sink: ArrayBuffer[UserNotificationRequest],
    deliverylatch: Option[CountDownLatch]
  ): UserNotificationProvider =
    new UserNotificationProvider {
      val name: String = providername

      def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = {
        sink += request
        deliverylatch.foreach(_.countDown())
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
    jobid: String,
    app: Option[String],
    runmode: String = "async"
  ): ReceptionDomainEvent =
    ReceptionDomainEvent(
      name = name,
      kind = name,
      payload = Map(
        "job-id" -> jobid,
        "status" -> name.stripPrefix("job."),
        "job-run-mode" -> runmode,
        "submitter-principal-id" -> "alice",
        "web.service" -> "post",
        "web.operation" -> "publish",
        "message" -> "done"
      ) ++ app.map("web.app" -> _),
      attributes = Map("correlation-id" -> "corr-1"),
      occurredAt = java.time.Instant.EPOCH
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
