package org.goldenport.cncf.job

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.*
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder, StructuredLogEvent}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventPersistenceOperationalAuditIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EventPersistenceOperationalAuditTestSupport.*

  "EI1 Independent genuine Event graphs" should _scenario("EI1", "E1,E2,E3,E5,E6,E7") {
    "preserve full source records results exact handler objects contexts and counters under ordinary shrinking repeated publications" in {
      Given("E1,E2,E3,E5,E6,E7: two independent counted native EventEngine/EventStore/DefaultEventBus graphs and equal immutable ReceptionDomainEvents")
      val property = Prop.forAll(Gen.choose(1, 3)) { count =>
        Given("equal immutable source events and independently seeded native providers/stores")
        val event = _event(s"event-$count")
        val backend = new RecordingBackend
        When("Nop and recording graphs publish the equal events persistently one to three times")
        val left = _run(event.copy(), count, LogBackend.NopLogBackend)
        val right = _run(event.copy(), count, backend)
        Then("complete canonical receipts/results and original handler/context/order/provider effects are equal with one exact frame per receipt")
        _same_native(left, right)
        right.records.size shouldBe count
        right.results shouldBe Vector.fill(count)(Consequence.success(EventPublishResult(3, persisted = true)))
        right.received.map(_._1) shouldBe Vector.fill(count)(Vector("first", "second", "last")).flatten
        right.receipts.zip(right.records).foreach { case (receipt, stored) => (receipt eq stored) shouldBe true }
        _bound_frames(backend.events, right.receipts, right.context)
        true
      }
      When("the ordinary shrinking repeat property runs with seed69709602L and minimum24")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709602L)), property)
      Then("at least24 successful checks pass without audit-created calls or identities")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  "EI2 Source Failure and committed effects" should _scenario("EI2", "E2,E3,E4,E6,E7") {
    "preserve exact original failure conclusions and distinguish before-append from committed-then-Failure without guessing effects" in {
      Given("E2,E3,E4,E6,E7: genuine native store delegates with original before-append and commit-then-Failure responses")
      Vector(StoreMode.BeforeFailure, StoreMode.AfterFailure).foreach { mode =>
        Given(s"a fresh native graph with source fault mode $mode")
        val graph = new NativeGraph
        graph.store.mode = mode
        graph.bus.register(EventSubscription("native", handler = graph.handler("native")))
        val backend = new RecordingBackend
        val event = _event("failed-source")
        val before = graph.observations
        When("the contextual persistent bus receives the original normal Failure return")
        val result = withBackend(backend)(graph.publish(event))
        Then("the exact source conclusion survives once, no handler runs, and canonical committed effects differ truthfully")
        result match {
          case Consequence.Failure(conclusion) =>
            graph.store.failure match {
              case Consequence.Failure(original) => (conclusion eq original) shouldBe true
              case _ => fail("fixture did not retain original source Failure")
            }
          case _ => fail("original native Failure was lost")
        }
        (graph.store.results.head eq graph.store.failure) shouldBe true
        graph.store.appendCalls.get() shouldBe 1
        graph.store.readCalls.get() shouldBe before._4
        graph.received shouldBe empty
        graph.bodyCalls.get() shouldBe 0
        graph.clock.calls.get() shouldBe before._1
        graph.ids.calls.get() should be > before._2
        graph.store.stored.size shouldBe (if (mode == StoreMode.AfterFailure) 1 else 0)
        if (mode == StoreMode.AfterFailure) {
          graph.store.stored.head.payload shouldBe event.payload
          graph.store.stored.head.attributes shouldBe event.attributes
        }
        backend.events.size shouldBe 1
        val row = backend.events.head.attributes
        row.getString("outcome") shouldBe Some("failed")
        row.getBoolean("event-identity-available") shouldBe Some(false)
        Vector("event-id", "event-sequence", "event-lane", "occurred-at", "persisted", "no-effect", "rollback")
          .foreach(key => row.asMap.keySet should not contain key)
        markers.foreach(marker => backend.events.head.message should not include marker)
        sameAtoms(row, framed(backend.events.head.message))
      }
    }
  }

  "EI3 Source and dispatch fault precedence" should _scenario("EI3", "E1,E2,E6,E7") {
    "observe only normal store returns before priority short-circuit handlers while retaining original faults" in {
      Given("E1,E2,E6,E7: native priority subscriptions and separate handler Failure/throw or source throw")
      Vector(false, true).foreach { throwing =>
        Given("a real store and deterministic first/fault/last priority subscriptions")
        val graph = new NativeGraph
        val order = ArrayBuffer.empty[String]
        val failure = Consequence.stateInvalid[Unit](markers.mkString("|"))
        val fault = new IllegalStateException("original-handler-fault")
        val backend = new RecordingBackend(_ => order += "acknowledgement")
        graph.bus.register(EventSubscription("last", priority = 9, handler = graph.handler("last", _ => { order += "last"; Consequence.unit })))
        graph.bus.register(EventSubscription("first", priority = 1, handler = graph.handler("first", _ => { order += "first"; Consequence.unit })))
        graph.bus.register(EventSubscription("fault", priority = 1, handler = graph.handler("fault", _ => {
          order += "fault"
          if (throwing) throw fault else failure
        })))
        When("the native store acknowledges and then the handler fails or throws")
        val result: Either[IllegalStateException, Consequence[EventPublishResult]] = withBackend(backend) {
          if (throwing) {
            Left(intercept[IllegalStateException](graph.publish(_event("dispatch-fault"))))
          } else Right(graph.publish(_event("dispatch-fault")))
        }
        Then("the original handler fault follows one succeeded persistence frame and stops all later handlers")
        result match {
          case Left(caught) => (caught eq fault) shouldBe true
          case Right(Consequence.Failure(conclusion)) => failure match {
            case Consequence.Failure(original) =>
              conclusion.previous should not be empty
              (conclusion.previous.get eq original) shouldBe true
            case _ => fail("missing native handler Failure")
          }
          case _ => fail("dispatch Failure lost")
        }
        order.toVector shouldBe Vector("acknowledgement", "first", "fault")
        graph.received.map(_._1).toVector shouldBe Vector("first", "fault")
        graph.store.appendCalls.get() shouldBe 1
        graph.store.readCalls.get() shouldBe 0
        graph.store.stored.size shouldBe 1
        _bound_frames(backend.events, graph.store.results.flatMap(_.toOption.get), graph.executionContext)
        backend.events.head.attributes.getString("outcome") shouldBe Some("succeeded")
        backend.events.head.attributes.asMap.keySet should not contain "dispatched-count"
      }
      Given("a separate real EventEngine whose native store throws before normal source return")
      val graph = new NativeGraph
      graph.store.mode = StoreMode.ThrowBeforeReturn
      graph.bus.register(EventSubscription("native", handler = graph.handler("native")))
      val backend = new RecordingBackend
      When("the persistent source throws its original object")
      val caught = withBackend(backend)(intercept[IllegalStateException](graph.publish(_event("source-throw"))))
      Then("the source throw propagates with zero observation/dispatch/retry and no manufactured response")
      (caught eq graph.store.fault) shouldBe true
      graph.store.appendCalls.get() shouldBe 1
      graph.store.results shouldBe empty
      graph.store.stored shouldBe empty
      graph.store.readCalls.get() shouldBe 0
      graph.received shouldBe empty
      backend.events shouldBe empty
    }
  }

  "EI4 Native hostile source privacy" should _scenario("EI4", "E1,E2,E4,E6,E7") {
    "preserve seventeen private facets in genuine canonical store and handler objects with no audit traversal or invented correlation" in {
      Given("E1,E2,E4,E6,E7: seventeen hostile Event names/kinds/payloads/attributes and nested throwing toString")
      val reads = new AtomicInteger(0)
      val trap = new Object {
        override def toString: String = { reads.incrementAndGet(); throw new IllegalStateException(markers.mkString) }
      }
      val event = ReceptionDomainEvent(markers.mkString("|"), markers.reverse.mkString("|"),
        Map("nested" -> Map("private" -> trap), "facets" -> markers), facets.zip(markers).toMap, instant)
      val backend = new RecordingBackend
      When("independent Nop and observing graphs genuinely persist and deliver their original source event")
      val left = _run(event.copy(), 1, LogBackend.NopLogBackend)
      val right = _run(event.copy(), 1, backend)
      Then("complete native private values/results/effects remain equal and frames bind genuine receipts/context without raw facets")
      _same_native(left, right)
      right.records.head.name shouldBe event.name
      right.records.head.kind shouldBe event.kind
      right.records.head.payload shouldBe event.payload
      right.records.head.attributes shouldBe event.attributes
      (right.records.head.payload("nested").asInstanceOf[Map[String, Any]]("private").asInstanceOf[AnyRef] eq trap) shouldBe true
      _bound_frames(backend.events, right.receipts, right.context)
      backend.events.foreach { row =>
        markers.foreach { marker =>
          row.message should not include marker
          row.attributes.asMap.values.mkString should not include marker
        }
        Vector("job-id", "task-id", "operation-action-id", "workflow-instance-id", "workflow-definition-id", "job-definition-id")
          .foreach(key => row.attributes.asMap.keySet should not contain key)
      }
      reads.get() shouldBe 0
    }
  }

  "EI5 Genuine native Job source join" should _scenario("EI5", "E2,E3,E4,E5,E6,E7") {
    "bind actual submitted running succeeded Event receipts to original Job context while preserving full Job facts and C5 bus bytes" in {
      Given("E2,E3,E4,E5,E6,E7: original private[job] lifecycle fixtures, independent Sync Nil Jobs and shared DefaultEventBus")
      val backend = new RecordingBackend
      val prior = new RecordingBackend
      When("Nop and event-recording native fixtures submit the genuine synchronous Nil Job")
      val left = _job_run(LogBackend.NopLogBackend)
      val right = _job_run(backend)
      val reference = _job_run(prior, suppresseventframe = true)
      Then("complete native Job status/timeline/result/resource/lifetime/clock-ID-time-store-bus-dispatch facts remain equal")
      right shouldBe left
      reference shouldBe left
      right.records.map(_.name) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
      right.status shouldBe Some(JobStatus.Succeeded)
      right.observations._5 shouldBe 3
      right.observations._6 shouldBe 0
      right.observations._7 shouldBe 3
      right.dispatched.size shouldBe 3
      backend.events.size shouldBe 3
      right.records.zip(backend.events).foreach { case (record, row) =>
        record.attributes("cncf.job.id") shouldBe right.jobid
        row.attributes.getString("event-id") shouldBe Some(record.id.value)
        row.attributes.getLong("event-sequence") shouldBe Some(record.sequence)
        row.attributes.getString("occurred-at") shouldBe Some(record.createdAt.toString)
        row.attributes.getString("trace-id") shouldBe Some(right.traceid)
        row.attributes.getString("correlation-id") shouldBe right.correlationid
        row.attributes.getString("job-id") shouldBe None
        sameAtoms(row.attributes, framed(row.message))
      }
      val jobframes = backend.allEvents.filter(_job_frame).map(row => (row.message, row.attributes.asMap))
      jobframes shouldBe prior.allEvents.filter(_job_frame).map(row => (row.message, row.attributes.asMap))
      val c5 = backend.allEvents.filter(row => row.message.contains("job.lifecycle.audit.v1 bytes=") &&
        row.attributes.getString("record-kind").contains("call"))
      c5.size shouldBe 3
      c5.foreach { row =>
        row.attributes.getString("transport") shouldBe Some("bus")
        row.attributes.getBoolean("event-identity-available") shouldBe Some(false)
        row.attributes.getString("event-id") shouldBe None
        row.attributes.getLong("event-sequence") shouldBe None
      }
    }
  }

  "EI6 Genuine native logging isolation" should _scenario("EI6", "E1,E2,E5,E6,E7") {
    "preserve source receipts handlers and providers under Nop refusal and throwing event-prefix output with exact restoration" in {
      Given("E1,E2,E5,E6,E7: counted genuine native graphs, original holder/visibility and matching event-prefix output fault")
      val holder = LogBackendHolder.backend.get
      val visibility = ObservabilityEngine.visibilityPolicy
      val backend = new RecordingBackend
      val throwing = new ThrowingBackend
      val event = _event("logging-isolation")
      When("the real persistent bus runs independently under Nop, visibility refusal and NonFatal logging failure")
      val original = _run(event.copy(), 1, LogBackend.NopLogBackend)
      val refused = _run(event.copy(), 1, backend, VisibilityPolicy(minLevel = LogLevel.Error))
      val failed = _run(event.copy(), 1, throwing)
      Then("actual acknowledged receipts/results/handlers/clock-ID-store-provider effects remain equal without source retry")
      _same_native(original, refused)
      _same_native(original, failed)
      original.results shouldBe Vector(Consequence.success(EventPublishResult(3, persisted = true)))
      backend.events shouldBe empty
      throwing.calls.get() shouldBe 1
      (LogBackendHolder.backend.get eq holder) shouldBe true
      (ObservabilityEngine.visibilityPolicy eq visibility) shouldBe true
    }
  }

  private case class EventRun(records: Vector[EventRecord], receipts: Vector[EventRecord],
    results: Vector[Consequence[EventPublishResult]], received: Vector[(String, DomainEvent, Option[ExecutionContext])],
    observations: (Int, Int, Int, Int, Int), context: ExecutionContext, event: ReceptionDomainEvent)

  private def _event(name: String): ReceptionDomainEvent =
    ReceptionDomainEvent(name, "original-kind", Map("original" -> "value"), Map("original" -> "attribute"), instant)

  private def _run(event: ReceptionDomainEvent, count: Int, backend: LogBackend,
    policy: VisibilityPolicy = VisibilityPolicy.AllowAll): EventRun = {
    val graph = new NativeGraph
    graph.bus.register(EventSubscription("last", priority = 9, handler = graph.handler("last")))
    graph.bus.register(EventSubscription("first", priority = 1, handler = graph.handler("first")))
    graph.bus.register(EventSubscription("second", priority = 1, handler = graph.handler("second")))
    val results = withBackend(backend, policy)(Vector.fill(count)(graph.publish(event)))
    EventRun(graph.store.stored, graph.store.results.flatMap(_.toOption.get), results,
      graph.received.toVector, graph.observations, graph.executionContext, event)
  }

  private def _same_native(left: EventRun, right: EventRun): Unit = {
    right.records shouldBe left.records
    right.receipts shouldBe left.receipts
    right.results shouldBe left.results
    right.observations shouldBe left.observations
    right.received.map(row => (row._1, row._2)) shouldBe left.received.map(row => (row._1, row._2))
    Vector(left, right).foreach { run =>
      run.observations._4 shouldBe 0
      run.received.foreach { case (_, event, context) =>
        (event.asInstanceOf[AnyRef] eq run.event) shouldBe true
        context.nonEmpty shouldBe true
        (context.get eq run.context) shouldBe true
      }
      run.receipts.zip(run.records).foreach { case (receipt, stored) => (receipt eq stored) shouldBe true }
    }
  }

  private def _bound_frames(rows: Vector[StructuredLogEvent], receipts: Vector[EventRecord], ctx: ExecutionContext): Unit = {
    rows.size shouldBe receipts.size
    rows.zip(receipts).foreach { case (row, record) =>
      framed(row.message).noSpaces shouldBe expected(ctx, record).noSpaces
      sameAtoms(row.attributes, framed(row.message))
    }
  }

  private case class JobRun(jobid: String, status: Option[JobStatus], result: Option[JobResult],
    createdat: java.time.Instant, updatedat: java.time.Instant, timeline: Vector[JobTimelineEvent],
    retry: JobRetryState, tasks: Vector[JobTaskReadModel], records: Vector[EventRecord],
    dispatched: Vector[DomainEvent], resources: JobResourceSnapshot, lifetime: Option[JobLifetimeSnapshot],
    observations: (Int, Int, Int, Int, Int, Int, Int), traceid: String, correlationid: Option[String])

  private def _job_run(backend: LogBackend, suppresseventframe: Boolean = false): JobRun =
    JobLifecycleOperationalAuditFixtures.withNative(useBus = true) { fixture =>
      Given("the genuine original native Job fixture with independently seeded providers, store and shared bus")
      val target = if (suppresseventframe) new LogBackend {
        override def logStructured(event: StructuredLogEvent): Unit = if (!event.message.contains(prefix)) backend.logStructured(event)
        def writeLine(line: String): Unit = if (!line.contains(prefix)) backend.writeLine(line)
      } else backend
      withBackend(target) {
        When("the original Job engine submits a synchronous Nil Job")
        val jobid = fixture.submit(option = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync))
        val source = fixture.engine._get_record(jobid).get
        Then("exact original returned receipts and dispatched source fields are retained before fact projection")
        fixture.store.results.flatMap(_.toOption.get).zip(fixture.store.stored).foreach { case (receipt, stored) =>
          (receipt eq stored) shouldBe true
        }
        fixture.dispatched.zip(fixture.store.stored).foreach { case (domain, stored) =>
          val event = domain.asInstanceOf[ReceptionDomainEvent]
          event.name shouldBe stored.name
          event.kind shouldBe stored.kind
          event.payload shouldBe stored.payload
          event.attributes shouldBe stored.attributes
          event.occurredAt shouldBe stored.createdAt
        }
        JobRun(jobid.value, fixture.engine.getStatus(jobid), fixture.engine.getResult(jobid),
          source.createdAt, source.updatedAt, source.timeline, source.retry, source.taskReadModels,
          fixture.store.stored, fixture.dispatched.toVector, fixture.engine.resourceSnapshot,
          fixture.engine.lifetimeSnapshot(jobid), fixture.observations,
          source.submittedContext.observability.traceId.value, source.submittedContext.observability.correlationId.map(_.value))
      }
    }

  private def _job_frame(row: StructuredLogEvent): Boolean =
    Vector("job.audit.v1 bytes=", "job.lifecycle.audit.v1 bytes=", "job.producer.audit.v1 bytes=").exists(row.message.contains)

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:event-persistence-operational-audit, example:$example, rules:$rules, phase:69.7, slice:C7")
}
