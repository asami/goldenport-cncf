package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.cncf.context.ExecutionContextId
import org.goldenport.cncf.event.{EventId, EventTypeId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class JobEventLogIdempotencySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "JobEventLog idempotency" should {
    "not change JobState when the same EventId is appended twice" in {
      val jobid = JobId.generate()
      val taskid = TaskId.generate()
      val eventtype = EventTypeId("TaskDone")
      val plan = JobPlan(
        jobId = jobid,
        expected = Set(
          ExpectedEvent(
            kind = ExpectedEventKind.TaskCompletion,
            matcher = EventMatcher(eventtype, CorrelationPredicate.Any),
            taskId = Some(taskid)
          )
        )
      )
      val log = new InMemoryJobEventLog
      val eventid = EventId("cncf", "event_1")
      val e1 = TestEvent(
        eventId = eventid,
        eventType = eventtype,
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = Some(taskid),
        executionId = Some(ExecutionContextId("cncf", "run_1")),
        traceId = None,
        spanId = None,
        attributes = Map.empty,
        receivedAt = Instant.now()
      )
      val e1alt = e1.copy(
        executionId = Some(ExecutionContextId("cncf", "run_2"))
      )

      Given("an event is appended twice with the same EventId")
      log.append(e1)
      log.append(e1alt)

      When("the JobState is derived from the log")
      val projection = _project(plan, log)

      Then("the log stores a single entry and the plan is satisfied once")
      log.entriesByJob(jobid).size shouldBe 1
      projection.state shouldBe JobState.Done
      projection.satisfied.size shouldBe 1
    }
  }

  private final case class TestEvent(
    eventId: EventId,
    eventType: EventTypeId,
    occurredAt: Instant,
    jobId: Option[JobId],
    taskId: Option[TaskId],
    executionId: Option[ExecutionContextId],
    traceId: Option[String],
    spanId: Option[String],
    attributes: Map[String, Any],
    receivedAt: Instant
  )

  private sealed trait JobState
  private object JobState {
    case object Running extends JobState
    case object Done extends JobState
  }

  private final case class Projection(
    state: JobState,
    satisfied: Set[ExpectedEvent]
  )

  private final class InMemoryJobEventLog {
    private var _entries: Map[EventId, TestEvent] = Map.empty

    def append(e: TestEvent): Unit = {
      if (!_entries.contains(e.eventId)) {
        _entries = _entries.updated(e.eventId, e)
      }
    }

    def entriesByJob(jobid: JobId): Vector[TestEvent] =
      _entries.values.filter(_.jobId.contains(jobid)).toVector
  }

  private def _project(
    plan: JobPlan,
    log: InMemoryJobEventLog
  ): Projection = {
    val entries = log.entriesByJob(plan.jobId)
    val satisfied = plan.expected.filter { expected =>
      entries.exists(e => _matches(plan.jobId, expected, e))
    }
    val state =
      if (satisfied.size == plan.expected.size) JobState.Done
      else JobState.Running
    Projection(state, satisfied)
  }

  private def _matches(
    jobid: JobId,
    expected: ExpectedEvent,
    e: TestEvent
  ): Boolean = {
    val jobok = e.jobId.contains(jobid)
    val typeok = e.eventType == expected.matcher.eventType
    val taskok = expected.taskId.forall(t => e.taskId.contains(t))
    jobok && typeok && taskok
  }
}
