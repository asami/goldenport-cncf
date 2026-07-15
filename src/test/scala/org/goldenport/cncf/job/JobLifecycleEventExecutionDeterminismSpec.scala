package org.goldenport.cncf.job

import java.time.{Clock, Instant, ZoneOffset}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{
  DomainEvent,
  EventBus,
  EventDispatchHandler,
  EventEngine,
  EventRecord,
  EventRecordFactory,
  EventStore,
  EventSubscription
}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobLifecycleEventExecutionDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Job lifecycle Event execution capabilities" should {
    "materialize EventStore fallback records from the submitted Job profile" in {
      Given("generated execution instants and deterministic submitted Job profiles")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _fallback_records(instant, "job-event-seed")
        val right = _fallback_records(instant, "job-event-seed")

        left.map(_.id) == right.map(_.id) &&
        left.map(_.name) == Vector("job.submitted", "job.running", "job.succeeded") &&
        left.map(_.id).distinct.size == 3 &&
        left.forall(_.id.major == "submitted") &&
        left.forall(_.id.minor == "job_event") &&
        left.forall(_.createdAt == instant)
      }

      When("equivalent synchronous Jobs are replayed through the EventStore fallback")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("lifecycle Event identity and time are replay-stable and remain distinct")
      checked.passed shouldBe true
    }

    "preserve the submitted Job profile through EventBus persistence and dispatch" in {
      Given("an EventBus whose fallback profile differs from the submitted Job profile")
      val instant = Instant.parse("2026-07-16T00:00:00Z")
      val context = _execution_context(instant, "submitted-job-seed")
      val fallbackclock = Clock.fixed(instant.minusSeconds(60L), ZoneOffset.UTC)
      val fallbackfactory = EventRecordFactory(
        fallbackclock,
        IdGenerationContext.deterministic(
          IdGenerationContext.IdNamespace("fallback", "job_event"),
          fallbackclock,
          "fallback-job-event-seed"
        )
      )
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(
        DataStore.noop(),
        eventstore = eventstore,
        recordfactory = fallbackfactory
      )
      val eventbus = EventBus.default(eventengine)
      val dispatched = ArrayBuffer.empty[ExecutionContext]
      eventbus.register(
        EventSubscription(
          name = "job-lifecycle-profile",
          kind = Some("job.submitted"),
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              val _ = event
              Consequence.operationInvalid("runtime dispatch context was not propagated")
            }

            override def dispatchAuthorized(
              event: DomainEvent
            )(using current: ExecutionContext): Consequence[Unit] = {
              val _ = event
              dispatched += current
              Consequence.unit
            }
          }
        )
      )
      val timesource = new ManualJobTimeSource(instant)
      val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
      val engine = new InMemoryJobEngine(
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
        timeSource = timesource,
        timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global).withEventBus(eventbus)

      When("the Job publishes its lifecycle Events")
      val submitted =
        try {
          engine.submit(Nil, context, JobSubmitOption(runMode = JobRunMode.Sync))
        } finally {
          engine.shutdown()
        }

      Then("EventBus records and handlers use the submitted execution profile")
      submitted.toOption should not be empty
      dispatched.toVector shouldBe Vector(context)
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records.map(_.name) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
      records.forall(_.id.major == "submitted") shouldBe true
      records.forall(_.id.minor == "job_event") shouldBe true
      records.forall(_.createdAt == instant) shouldBe true
    }
  }

  private def _fallback_records(
    instant: Instant,
    seed: String
  ): Vector[EventRecord] = {
    val context = _execution_context(instant, seed)
    val eventstore = EventStore.inMemory
    val timesource = new ManualJobTimeSource(instant)
    val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
    val engine = new InMemoryJobEngine(
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
      timeSource = timesource,
      timer = Some(timer)
    )(scala.concurrent.ExecutionContext.global).withEventStore(eventstore)
    try {
      engine.submit(Nil, context, JobSubmitOption(runMode = JobRunMode.Sync)).toOption
      eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
    } finally {
      engine.shutdown()
    }
  }

  private def _execution_context(
    instant: Instant,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("submitted", "job_event"),
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }
}
