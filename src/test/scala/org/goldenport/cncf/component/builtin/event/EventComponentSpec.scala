package org.goldenport.cncf.component.builtin.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobId, JobRunMode, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded, TaskFailed}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.provisional.conclusion.Disposition
import org.goldenport.protocol.{Argument, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventComponentSpec extends AnyWordSpec with Matchers {
  "EventComponent" should {
    "expose event read and job event observation routes" in {
      val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
      val admin = subsystem.components.find(_.name == "admin").get
      val event = subsystem.components.find(_.name == "event").get
      val ctx = ExecutionContext.create()
      val jobId = admin.logic.submitJob(
        List(SleepTask(ActionId.generate(), 10L)),
        ctx,
        JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("event-component"))
      ).toOption.get

      val searchReq = Request(
        component = Some("event"),
        service = Some("event"),
        operation = "search_event",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )
      subsystem.execute(searchReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("job.submitted")
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }

      val loadReq = Request(
        component = Some("event"),
        service = Some("event_admin"),
        operation = "load_job_events",
        arguments = List(Argument("id", jobId.value)),
        switches = Nil,
        properties = Nil
      )
      subsystem.execute(loadReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("job.submitted")
          value should include (jobId.value)
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }

    "expose selected policy and source/target metadata on event inspection surfaces" in {
      val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
      val eventStore = subsystem.eventStore
      val record = EventRecord(
        id = EventId.generate(),
        name = "person.created",
        kind = "created",
        payload = Map("id" -> "p1"),
        attributes = Map(
          "cncf.source.subsystem" -> "crm",
          "cncf.source.component" -> "publisher",
          "cncf.target.subsystem" -> subsystem.name,
          "cncf.target.component" -> "public-notice",
          "cncf.event.originBoundary" -> "external-subsystem",
          "cncf.event.receptionRule" -> "person-created-sync",
          "cncf.event.receptionPolicy" -> "async:new-job:same-saga:new-transaction",
          "cncf.event.policySource" -> "explicit-rule",
          "cncf.event.sagaId" -> "saga-1",
          "cncf.event.failurePolicy" -> "retry",
          "cncf.event.failureDispositionBase" -> "retryable",
          "cncf.event.dispatchKind" -> "async-new-job",
          "cncf.event.dispatchStatus" -> "queued",
          "cncf.event.sagaRelation" -> "same-saga",
          "cncf.event.history" -> "source{source.component=publisher}"
        ),
        createdAt = Instant.now(),
        persistent = true,
        status = EventRecord.Status.Stored,
        lane = EventLane.Transactional
      )
      eventStore.append(Seq(record)) shouldBe a[Consequence.Success[_]]

      val loadReq = Request(
        component = Some("event"),
        service = Some("event"),
        operation = "load_event",
        arguments = List(Argument("id", record.id.value)),
        switches = Nil,
        properties = Nil
      )

      subsystem.execute(loadReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("crm")
          value should include ("public-notice")
          value should include ("person-created-sync")
          value should include ("explicit-rule")
          value should include ("saga-1")
          value should include ("retry")
          value should include ("retryable")
          value should include ("async-new-job")
          value should include ("queued")
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }

    "expose dead-letter and poison metadata for event-triggered failures" in {
      val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
      val admin = subsystem.components.find(_.name == "admin").get
      val ctx = ExecutionContext.create()
      val jobId = admin.logic.submitJob(
        List(_FailureTask(
          ActionId.generate(),
          Conclusion.simple("retry-now").copy(disposition = Disposition(Disposition.UserAction.RetryNow))
        )),
        ctx,
        JobSubmitOption(
          requestSummary = Some("event-triggered-retry"),
          parameters = Map(
            "event.name" -> "person.created",
            "reception.jobRelation" -> "newjob",
            "failure.policy" -> "retry"
          )
        )
      ).toOption.get
      _await_job(admin.jobEngine, jobId)

      val loadReq = Request(
        component = Some("event"),
        service = Some("event_admin"),
        operation = "load_job_events",
        arguments = List(Argument("id", jobId.value)),
        switches = Nil,
        properties = Nil
      )
      subsystem.execute(loadReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("retry_exhausted")
          value should include ("dead_letter")
          value should include ("recovery_required")
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }
  }

  private final case class SleepTask(
    actionId: ActionId,
    durationMillis: Long
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      Thread.sleep(durationMillis)
      TaskSucceeded(OperationResponse.Void())
    }
  }

  private final case class _FailureTask(
    actionId: ActionId,
    conclusion: Conclusion
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(conclusion)
    }
  }

  private def _await_job(
    engine: org.goldenport.cncf.job.JobEngine,
    jobId: JobId,
    timeoutMillis: Long = 4000L
  ): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var status = engine.getStatus(jobId)
    while (
      status.forall(s => s != org.goldenport.cncf.job.JobStatus.Failed && s != org.goldenport.cncf.job.JobStatus.Succeeded) &&
      System.currentTimeMillis() < deadline
    ) {
      Thread.sleep(10L)
      status = engine.getStatus(jobId)
    }
    status should contain (org.goldenport.cncf.job.JobStatus.Failed)
  }
}
