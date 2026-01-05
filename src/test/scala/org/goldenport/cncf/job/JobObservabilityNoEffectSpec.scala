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
class JobObservabilityNoEffectSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "JobState derivation" should {
    "ignore observability identifiers" in {
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
      val log1 = new InMemoryJobEventLog
      val log2 = new InMemoryJobEventLog
      val e1 = TestEvent(
        eventId = EventId("cncf", "event_1"),
        eventType = eventtype,
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = Some(taskid),
        executionId = Some(ExecutionContextId("cncf", "run_1")),
        traceId = Some("trace-1"),
        spanId = Some("span-1"),
        receivedAt = Instant.now()
      )
      val e2 = e1.copy(
        executionId = Some(ExecutionContextId("cncf", "run_2")),
        traceId = Some("trace-2"),
        spanId = Some("span-2")
      )

      Given("two logs with identical semantic events and different observability IDs")
      log1.append(e1)
      log2.append(e2)

      When("JobState is projected from each log")
      val p1 = _project(plan, log1)
      val p2 = _project(plan, log2)

      Then("both results are identical")
      p1.state shouldBe p2.state
      p1.satisfied.size shouldBe p2.satisfied.size
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
