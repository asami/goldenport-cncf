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
class JobTerminalStateRecordOnlySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Job terminal states" should {
    "remain Done when additional events are recorded" in {
      val jobid = JobId.generate()
      val taskid = TaskId.generate()
      val expectedtype = EventTypeId("TaskDone")
      val plan = JobPlan(
        jobId = jobid,
        expected = Set(
          ExpectedEvent(
            kind = ExpectedEventKind.TaskCompletion,
            matcher = EventMatcher(expectedtype, CorrelationPredicate.Any),
            taskId = Some(taskid)
          )
        )
      )
      val log = new InMemoryJobEventLog
      val e1 = TestEvent(
        eventId = EventId("cncf", "event_1"),
        eventType = expectedtype,
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = Some(taskid),
        executionId = Some(ExecutionContextId("cncf", "run_1")),
        receivedAt = Instant.now()
      )
      val e2 = TestEvent(
        eventId = EventId("cncf", "event_2"),
        eventType = EventTypeId("Unrelated"),
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = None,
        executionId = None,
        receivedAt = Instant.now()
      )

      Given("a job that reaches Done")
      log.append(e1)
      val p1 = _project(plan, log)
      p1.state shouldBe JobState.Done

      When("an additional event is recorded")
      log.append(e2)
      val p2 = _project(plan, log)

      Then("state remains Done and the event is recorded")
      p2.state shouldBe JobState.Done
      log.entriesByJob(jobid).size shouldBe 2
    }

    "remain Failed when additional events are recorded" in {
      val jobid = JobId.generate()
      val plan = JobPlan(
        jobId = jobid,
        expected = Set.empty
      )
      val log = new InMemoryJobEventLog
      val errorEventType = EventTypeId("Error")
      val e1 = TestEvent(
        eventId = EventId("cncf", "event_1"),
        eventType = errorEventType,
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = None,
        executionId = None,
        receivedAt = Instant.now()
      )
      val e2 = TestEvent(
        eventId = EventId("cncf", "event_2"),
        eventType = EventTypeId("Unrelated"),
        occurredAt = Instant.now(),
        jobId = Some(jobid),
        taskId = None,
        executionId = None,
        receivedAt = Instant.now()
      )

      Given("a job that reaches Failed")
      log.append(e1)
      val p1 = _project(plan, log)
      p1.state shouldBe JobState.Failed

      When("an additional event is recorded")
      log.append(e2)
      val p2 = _project(plan, log)

      Then("state remains Failed and the event is recorded")
      p2.state shouldBe JobState.Failed
      log.entriesByJob(jobid).size shouldBe 2
    }
  }

  private final case class TestEvent(
    eventId: EventId,
    eventType: EventTypeId,
    occurredAt: Instant,
    jobId: Option[JobId],
    taskId: Option[TaskId],
    executionId: Option[ExecutionContextId],
    receivedAt: Instant
  )

  private sealed trait JobState
  private object JobState {
    case object Running extends JobState
    case object Done extends JobState
    case object Failed extends JobState
  }

  private final case class Projection(
    state: JobState
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
    val haserror = entries.exists(_.eventType == EventTypeId("Error"))
    val satisfied = plan.expected.filter { expected =>
      entries.exists(e => _matches(plan.jobId, expected, e))
    }
    val state =
      if (haserror) JobState.Failed
      else if (satisfied.size == plan.expected.size) JobState.Done
      else JobState.Running
    Projection(state)
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
