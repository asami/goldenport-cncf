package org.goldenport.cncf.job

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.entity.EntityStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed

/*
 * @since   Oct. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobInputResourceBoundsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobInputBoundsFixtures.*

  "Runtime JobInput admission (spec:job-input-resource-contract, phase:69.7, slice:JM69-09B3B3C1)" should {
    "IC1 preserve absent, empty, inline and opaque Blob input through all ordinary modes" in {
      Given("None, empty and legacy opaque inline/Blob input in Sync/Async Persistent/Ephemeral engines")
      val payload = descriptor.copy(inlineBase64 = Some("YWJj"), byteSize = Some(3L),
        sha256 = Some("abc"), blobId = Some("blob-1"), filename = Some("original.txt"))
      val inputs = Vector(None, Some(input()), Some(input(Vector(payload))))
      for (mode <- Vector(JobRunMode.Sync, JobRunMode.Async);
           persistence <- Vector(JobPersistencePolicy.Persistent, JobPersistencePolicy.Ephemeral);
           value <- inputs) {
        val fixture = fresh()
        val task = new CountingTask
        try {
          When("the original explicit option admits and the manual queue drains")
          val result = fixture.engine.submit(List(task), fixture.context,
            JobSubmitOption(persistence = persistence, runMode = mode, input = value))
          Then("admitted facts retain identity and raw content, and the task succeeds")
          result.isSuccess shouldBe true
          val id = success(result)
          fixture.engine._get_record(id).get.input shouldBe value
          value.foreach(v => (fixture.engine._get_record(id).get.input.get eq v) shouldBe true)
          fixture.engine.drainAll() shouldBe (if (mode == JobRunMode.Async) 1 else 0)
          task.runs.get() shouldBe 1
          fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
          fixture.engine._get_record(id).get.input shouldBe value
        } finally fixture.engine.shutdown()
      }
    }

    "IC2 refuse every malformed input container before all engine effects" in {
      Given("null option/input/vector/element/time/retention and null optional metadata shapes")
      val base = input(Vector(descriptor))
      val payloads = Vector(
        null, descriptor.copy(storage = null), descriptor.copy(storage = "  "),
        descriptor.copy(createdAt = null), descriptor.copy(fieldName = null),
        descriptor.copy(fieldName = Some(null)), descriptor.copy(filename = null),
        descriptor.copy(filename = Some(null)), descriptor.copy(contentType = null),
        descriptor.copy(contentType = Some(null)), descriptor.copy(byteSize = null),
        descriptor.copy(byteSize = Some(null).asInstanceOf[Option[Long]]),
        descriptor.copy(sha256 = null), descriptor.copy(sha256 = Some(null)),
        descriptor.copy(inlineBase64 = null), descriptor.copy(inlineBase64 = Some(null)),
        descriptor.copy(blobId = null), descriptor.copy(blobId = Some(null))
      )
      val inputs: Vector[Option[JobInput]] = Vector(null, Some(null),
        Some(base.copy(payloads = null)), Some(base.copy(retentionPolicy = null)),
        Some(base.copy(createdAt = null)), Some(base.copy(ttl = null)),
        Some(base.copy(cleanedAt = null)), Some(base.copy(cleanedAt = Some(null)))) ++
        payloads.map(p => Some(input(Vector(p))))
      val options = Vector(null) ++ inputs.map(v => sync.copy(input = v))
      options.foreach { option =>
        val fixture = fresh()
        val task = new CountingTask
        try {
          val before = fixture.observation(task)
          When("an actual engine receives each malformed explicit option")
          val result = fixture.engine.submit(List(task), fixture.context, option)
          Then("the exact fixed invalid failure precedes getters, observers, IDs, clocks, events and reservations")
          _assert_refusal(result, Consequence.argumentInvalid[JobId]("JobEngine input request refused"))
          fixture.observation(task) shouldBe before
          fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
          fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
          fixture.engine.drainAll() shouldBe 0
        } finally fixture.engine.shutdown()
      }
    }

    "IC3 admit 1024 descriptors and atomically refuse the 1025th" in {
      Given("a valid prefix of zero-byte opaque descriptors and one extra descriptor")
      Vector(1024, 1025).foreach { count =>
        val fixture = fresh()
        val task = new CountingTask
        val value = input(Vector.fill(count)(descriptor))
        try {
          val before = fixture.observation(task)
          When("the full descriptor vector is submitted")
          val result = fixture.engine.submit(List(task), fixture.context, sync.copy(input = Some(value)))
          Then("the inclusive limit is preserved without prefix effects or clipping")
          result.isSuccess shouldBe (count == 1024)
          if (count == 1024) fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
          else {
            _assert_refusal(result, JobResourcePolicy.refused[JobId])
            fixture.observation(task) shouldBe before
          }
        } finally fixture.engine.shutdown()
      }
    }

    "IC4 count actual decoded 8MiB and reject inconsistent or oversized declared bytes" in {
      Given("real Basic Base64 for 8MiB and 8MiB+1, which share the maximum encoded length")
      val exact = encoded(8388608)
      val over = encoded(8388609)
      exact.length shouldBe 11184812
      over.length shouldBe 11184812
      val cases = Vector(
        (exact, None, "accepted"), (exact, Some(8388608L), "accepted"),
        (over, None, "capacity"), (exact, Some(0L), "invalid"),
        ("YWJj", Some(2L), "invalid"), ("", Some(-1L), "invalid"),
        ("", Some(8388609L), "capacity")
      )
      cases.foreach { case (raw, size, expected) =>
        val fixture = fresh()
        val task = new CountingTask
        try {
          val before = fixture.observation(task)
          When("the ephemeral engine admits the actual inline body")
          val result = fixture.engine.submit(List(task), fixture.context,
            sync.copy(input = Some(input(Vector(descriptor.copy(inlineBase64 = Some(raw), byteSize = size))))))
          Then("actual bytes and declared consistency select the exact bounded outcome")
          result.isSuccess shouldBe (expected == "accepted")
          if (expected != "accepted") {
            _assert_refusal(result, refusal(expected))
            fixture.observation(task) shouldBe before
          } else task.runs.get() shouldBe 1
        } finally fixture.engine.shutdown()
      }
    }

    "IC5 combine actual inline and known external bytes with safe aggregate accounting" in {
      Given("two 4MiB inline pieces, a late extra byte and mixed external declared bytes")
      val half = descriptor.copy(inlineBase64 = Some(encoded(4194304)))
      val external = descriptor.copy(byteSize = Some(4194304L), blobId = Some("blob-1"))
      val cases = Vector(
        Vector(half, half) -> true,
        Vector(half, half, descriptor.copy(inlineBase64 = Some("YQ"))) -> false,
        Vector(half, external) -> true,
        Vector(half, external.copy(byteSize = Some(4194305L))) -> false,
        Vector(descriptor.copy(byteSize = Some(Long.MaxValue))) -> false,
        Vector(descriptor.copy(blobId = Some("unknown-external"), byteSize = None)) -> true
      )
      cases.foreach { case (payloads, accepted) =>
        val fixture = fresh()
        val task = new CountingTask
        val value = input(payloads)
        try {
          val before = fixture.observation(task)
          When("one actual engine submission accounts for all descriptors")
          val result = fixture.engine.submit(List(task), fixture.context, sync.copy(input = Some(value)))
          Then("the mixed aggregate is inclusive and opaque external references remain untouched")
          result.isSuccess shouldBe accepted
          if (accepted) fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
          else {
            _assert_refusal(result, JobResourcePolicy.refused[JobId])
            fixture.observation(task) shouldBe before
          }
        } finally fixture.engine.shutdown()
      }
    }

    "IC6 preserve legal Basic Base64 forms and redact malformed and oversized private tokens" in {
      Given("padded, unpadded, empty, malformed and oversized private encoded strings")
      val sentinel = "PRIVATE-INPUT-SENTINEL"
      val cases = Vector("YQ==" -> "accepted", "YQ" -> "accepted", "" -> "accepted",
        "YWJj" -> "accepted", "Y Q==" -> "invalid", "YQ==\n" -> "invalid",
        "Y" -> "invalid", "_w==" -> "invalid", "@@PRIVATE-INPUT-SENTINEL" -> "invalid",
        (sentinel + ("A" * (11184813 - sentinel.length))) -> "capacity",
        (sentinel + ("A" * 11184813)) -> "capacity")
      cases.foreach { case (raw, expected) =>
        val fixture = fresh()
        val task = new CountingTask
        try {
          val before = fixture.observation(task)
          When("the encoded string reaches explicit submit")
          val result = fixture.engine.submit(List(task), fixture.context,
            sync.copy(input = Some(input(Vector(descriptor.copy(inlineBase64 = Some(raw)))))))
          Then("the original decoder grammar is retained and refusals expose no private body")
          result.isSuccess shouldBe (expected == "accepted")
          if (expected != "accepted") {
            _assert_refusal(result, refusal(expected))
            result match {
              case Consequence.Failure(conclusion) => conclusion.show should not include sentinel
              case _ => fail("expected encoded input refusal")
            }
            fixture.observation(task) shouldBe before
          }
        } finally fixture.engine.shutdown()
      }
    }

    "IC7 admit every inclusive UTF8 field limit and refuse byte or character oversupply" in {
      Given("each storage/fieldName/filename/contentType/SHA/Blob field at its individual ASCII and multibyte cap")
      val fields: Vector[(Int, String => JobInputPayload)] = Vector(
        256 -> ((s: String) => descriptor.copy(storage = s)),
        512 -> ((s: String) => descriptor.copy(fieldName = Some(s))),
        1024 -> ((s: String) => descriptor.copy(filename = Some(s))),
        512 -> ((s: String) => descriptor.copy(contentType = Some(s))),
        512 -> ((s: String) => descriptor.copy(sha256 = Some(s))),
        2048 -> ((s: String) => descriptor.copy(blobId = Some(s))))
      fields.foreach { case (limit, build) =>
        Vector(("a" * limit) -> true, ("a" * (limit + 1)) -> false,
          ("é" * (limit / 2)) -> true, (("é" * (limit / 2)) + "x") -> false,
          ("界" * (limit + 1)) -> false).foreach { case (text, accepted) =>
          val fixture = fresh()
          val task = new CountingTask
          val value = input(Vector(build(text)))
          try {
            val before = fixture.observation(task)
            When("actual input admission measures the bounded field")
            val result = fixture.engine.submit(List(task), fixture.context, sync.copy(input = Some(value)))
            Then("UTF8 bytes select admission without rewriting opaque content")
            result.isSuccess shouldBe accepted
            if (accepted) fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
            else {
              _assert_refusal(result, JobResourcePolicy.refused[JobId])
              fixture.observation(task) shouldBe before
            }
          } finally fixture.engine.shutdown()
        }
      }
      val fixture = fresh()
      val value = input(Vector(descriptor.copy(fieldName = Some(""), filename = Some(""),
        contentType = Some(""), sha256 = Some("sha256"), blobId = Some("blob-1"))))
      try {
        When("empty optional strings and legacy opaque SHA/Blob IDs are submitted")
        val result = fixture.engine.submit(Nil, fixture.context, sync.copy(input = Some(value)))
        Then("all bounded legacy values remain exactly admitted")
        result.isSuccess shouldBe true
        fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
      } finally fixture.engine.shutdown()
    }

    "IC8 admit exactly 1MiB metadata and refuse one late aggregate byte" in {
      Given("1024 opaque descriptors whose six-byte storage and 1018-byte filename total exactly 1MiB")
      val exact = Vector.fill(1024)(descriptor.copy(filename = Some("f" * 1018)))
      Vector(exact -> true, exact.updated(1023, exact.last.copy(filename = Some("f" * 1019))) -> false)
        .foreach { case (payloads, accepted) =>
          val fixture = fresh()
          val task = new CountingTask
          val value = input(payloads)
          try {
            val before = fixture.observation(task)
            When("the complete actual input vector reaches the aggregate gate")
            val result = fixture.engine.submit(List(task), fixture.context, sync.copy(input = Some(value)))
            Then("individual fitting fields cannot evade aggregate admission or cause partial records")
            result.isSuccess shouldBe accepted
            if (accepted) fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
            else {
              _assert_refusal(result, JobResourcePolicy.refused[JobId])
              fixture.observation(task) shouldBe before
            }
          } finally fixture.engine.shutdown()
        }
    }

    "IC9 retain default TTL cleanup, negative TTL and unused extreme TTL compatibility" in {
      Given("ordinary seven-day input with legacy metadata and a raw inline/Blob reference")
      val fixture = fresh()
      val payload = descriptor.copy(inlineBase64 = Some("YQ"), blobId = Some("blob-1"), sha256 = Some("abc"))
      val value = input(Vector(payload)).copy(retentionPolicy = JobInputRetentionPolicy.Ttl)
      try {
        When("Sync finishes and the manual clock advances eight days before explicit cleanup")
        val id = success(fixture.engine.submit(List(new CountingTask), fixture.context, sync.copy(input = Some(value))))
        val raw = fixture.engine._get_record(id).get.input
        fixture.clock.advanceBy(Duration.ofDays(8))
        val cleaned = fixture.engine.cleanupExpiredInputs(fixture.clock.peek)
        Then("raw-before-cleanup becomes the exact existing sanitized metadata after expiry")
        raw shouldBe Some(value)
        cleaned shouldBe 1
        fixture.engine._get_record(id).get.input shouldBe Some(value.cleaned(fixture.clock.peek))
        fixture.engine._get_record(id).get.input.get.payloads.head.sha256 shouldBe Some("abc")
      } finally fixture.engine.shutdown()
      val cases = Vector(
        value.copy(ttl = Duration.ofSeconds(-1)) -> true,
        value.copy(createdAt = Instant.MAX) -> false,
        value.copy(createdAt = Instant.MAX, ttl = Duration.ofSeconds(Long.MaxValue),
          retentionPolicy = JobInputRetentionPolicy.Keep) -> true,
        value.copy(createdAt = Instant.MAX, ttl = Duration.ofSeconds(Long.MaxValue),
          retentionPolicy = JobInputRetentionPolicy.DeleteOnCompletion) -> true)
      cases.foreach { case (candidate, accepted) =>
        val actual = fresh()
        val task = new CountingTask
        try {
          val before = actual.observation(task)
          When("representable negative, overflowing active, or unused extreme TTL is admitted")
          val result = actual.engine.submit(List(task), actual.context, sync.copy(input = Some(candidate)))
          Then("only required Ttl addition overflow is invalid and an admitted negative TTL is already due")
          result.isSuccess shouldBe accepted
          if (!accepted) {
            _assert_refusal(result, Consequence.argumentInvalid[JobId]("JobEngine input request refused"))
            actual.observation(task) shouldBe before
          } else if (candidate.ttl.isNegative) {
            actual.engine.cleanupExpiredInputs(actual.clock.peek) shouldBe 1
            actual.engine._get_record(success(result)).get.input.get.cleanedAt shouldBe Some(actual.clock.peek)
          }
        } finally actual.engine.shutdown()
      }
    }

    "IC10 gate actual native lifecycle projection before its source and preserve a valid guarded dispatch" in {
      Given("a real standard EntityStore and durable write bridge with a closed evidence source")
      val fixture = fresh()
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val calls = new AtomicInteger(0)
      val task = new CountingTask
      val bridge = new DurableJobLifecycleWriteBridge(store, request => {
        val revision = calls.incrementAndGet().toLong
        val models = request match {
          case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
          case _ => Vector.empty
        }
        val supplied = evidence(revision)
        Consequence.success(supplied.copy(projection = supplied.projection.copy(
          tasks = if (models.nonEmpty) models.map(taskDescriptor) else supplied.projection.tasks,
          inputs = Vector(DurableInputReference("upload", DurableValue.Absent)),
          retry = if (request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
            DurableRetryEvidence(Vector(DurableAttemptEvidence(1, instant, Some(instant.plusMillis(1)),
              DurableAttemptOutcome.Succeeded, None)), 3, None, false, false) else supplied.projection.retry,
          result = if (request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
            DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending)))
      })
      fixture.engine.bindDurableLifecycleWriteBridge(bridge)
      try {
        val before = fixture.observation(task)
        val missingid = id("unadmitted")
        val missing = success(store.load(missingid.value, access))
        When("a malformed input is presented to the bound Persistent engine")
        val refused = fixture.engine.submit(List(task), fixture.context,
          JobSubmitOption(input = Some(input(Vector(descriptor.copy(inlineBase64 = Some("@@")))))))
        Then("there is no evidence request, native lifecycle record, checkpoint or engine effect")
        _assert_refusal(refused, Consequence.argumentInvalid[JobId]("JobEngine input request refused"))
        fixture.observation(task) shouldBe before
        calls.get() shouldBe 0
        fixture.engine.durableLifecycleWriteFailureFacts shouldBe empty
        success(store.load(missingid.value, access)) shouldBe missing
        When("a small valid Persistent input traverses the original guarded queued dispatch")
        val admitted = fixture.engine.submit(List(task), fixture.context,
          JobSubmitOption(input = Some(input(Vector(descriptor.copy(fieldName = Some("upload")))))))
        val jobid = success(admitted)
        val native = success(store.load(jobid.value, access)).get
        fixture.engine.drainAll() shouldBe 1
        Then("signed native admission and task checkpoints remain authoritative and the task executes")
        admitted.isSuccess shouldBe true
        native.record.body.identity.revision shouldBe 1L
        bridge.hasAdmittedSnapshot(jobid) shouldBe true
        task.runs.get() shouldBe 1
        calls.get() shouldBe 6
        success(store.load(jobid.value, access)).get.record.body.identity.revision shouldBe 6L
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        success(fixture.events.query(EventStore.Query())).exists(_.name == "job.submitted") shouldBe true
      } finally fixture.engine.shutdown()
    }

    "IC11 retain option observer and quiescing precedence while input refusal precedes option handling" in {
      Given("the original scheduled Sync option refusal and a counting admission observer")
      val fixture = fresh()
      val task = new CountingTask
      val scheduled = sync.copy(scheduledStartAt = Some(instant.plusSeconds(10)), input = Some(input()))
      try {
        When("a valid input reaches the original scheduled Sync option validator")
        val original = fixture.engine.submit(List(task), fixture.context, scheduled)
        Then("its original error and admission observer remain unchanged")
        _assert_refusal(original, Consequence.argumentInvalid[JobId]("scheduledStartAt requires async runMode"))
        task.admissions.get() shouldBe 1
        fixture.ids.calls.get() shouldBe 0
        val before = fixture.observation(task)
        When("the same option contains an invalid input before the original validator")
        val invalid = fixture.engine.submit(List(task), fixture.context, scheduled.copy(input = Some(null)))
        Then("the input refusal has no additional callback or clock effect")
        _assert_refusal(invalid, Consequence.argumentInvalid[JobId]("JobEngine input request refused"))
        fixture.observation(task) shouldBe before
        When("the engine is quiesced and receives even a null explicit option")
        fixture.engine.quiesce()
        val quiesced = fixture.engine.submit(List(task), fixture.context, null)
        Then("the existing quiescing error retains first precedence")
        _assert_refusal(quiesced, Consequence.serviceUnavailable[JobId]("JobEngine is quiescing"))
        task.admissions.get() shouldBe 1
      } finally fixture.engine.shutdown()
      val ordinary = fresh()
      val defaulttask = new CountingTask
      try {
        When("ordinary default Async submission is admitted")
        val admitted = ordinary.engine.submit(List(defaulttask), ordinary.context)
        Then("the default remains Async/Persistent and dispatches through the manual queue")
        admitted.isSuccess shouldBe true
        ordinary.engine._get_record(success(admitted)).get.runMode shouldBe JobRunMode.Async
        ordinary.engine._get_record(success(admitted)).get.persistence shouldBe JobPersistencePolicy.Persistent
        ordinary.engine.drainAll() shouldBe 1
        defaulttask.runs.get() shouldBe 1
      } finally ordinary.engine.shutdown()
    }

    "IC12 conserve actual generated input admissions with at least 24 fixed-seed checks" in {
      Given("finite nonempty payload lists, real inline values, declared sizes and opaque metadata")
      val generator = for {
        count <- Gen.choose(1, 8)
        size <- Gen.choose(0, 128)
        kind <- Gen.choose(0, 5)
        text <- Gen.choose(0, 16)
      } yield (count, size, kind, text)
      val property = Prop.forAll(generator) { case (count, size, kind, text) =>
        val fixture = fresh()
        val task = new CountingTask
        val payload = descriptor.copy(filename = Some("é" * text),
          inlineBase64 = if (kind == 0) Some("@@") else if (kind <= 3) Some(encoded(size)) else None,
          byteSize = if (kind == 1) Some(size.toLong + 1L) else if (kind == 2) Some(-1L)
            else if (kind == 3 || kind == 4) Some(size.toLong) else None)
        val value = input(Vector.fill(count)(payload))
        val accepted = kind >= 3
        try {
          val before = fixture.observation(task)
          When("the generated actual input is explicitly submitted")
          val result = fixture.engine.submit(List(task), fixture.context, sync.copy(input = Some(value)))
          Then("model-derived admission preserves original facts and invalid cases conserve every effect")
          result.isSuccess shouldBe accepted
          if (accepted) {
            fixture.engine._get_record(success(result)).get.input shouldBe Some(value)
            task.runs.get() shouldBe 1
            fixture.engine.resourceSnapshot.retained shouldBe 1
          } else {
            _assert_refusal(result, Consequence.argumentInvalid[JobId]("JobEngine input request refused"))
            fixture.observation(task) shouldBe before
          }
          true
        } finally fixture.engine.shutdown()
      }
      When("ScalaCheck executes finite actual admissions with the fixed seed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(697093341L)), property)
      Then("at least 24 behavioral cases satisfy admission and effect conservation")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _assert_refusal(actual: Consequence[JobId], expected: Consequence[JobId]): Unit =
    (actual, expected) match {
      case (Consequence.Failure(actualconclusion), Consequence.Failure(expectedconclusion)) =>
        actualconclusion.status shouldBe expectedconclusion.status
        actualconclusion.observation.taxonomy shouldBe expectedconclusion.observation.taxonomy
        actualconclusion.observation.phenomenon shouldBe expectedconclusion.observation.phenomenon
        actualconclusion.observation.cause.kind shouldBe expectedconclusion.observation.cause.kind
        actualconclusion.displayMessage shouldBe expectedconclusion.displayMessage
        actualconclusion.interpretation shouldBe expectedconclusion.interpretation
        actualconclusion.disposition shouldBe expectedconclusion.disposition
        actualconclusion.previous shouldBe expectedconclusion.previous
      case (Consequence.Success(_), _) => fail("expected refusal")
      case (_, Consequence.Success(_)) => fail("expected failure fixture")
    }
}

private[job] object JobInputBoundsFixtures {
  val instant: Instant = Instant.parse("2026-10-01T00:00:00Z")
  val access: DurableJobRecordAccess = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  val sync: JobSubmitOption = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)
  val descriptor: JobInputPayload = JobInputPayload("opaque", createdAt = instant)

  def input(payloads: Vector[JobInputPayload] = Vector.empty): JobInput =
    JobInput(payloads, retentionPolicy = JobInputRetentionPolicy.Keep, createdAt = instant)

  def encoded(size: Int): String = Base64.getEncoder.encodeToString(new Array[Byte](size))

  def refusal(kind: String): Consequence[JobId] =
    if (kind == "capacity") JobResourcePolicy.refused
    else Consequence.argumentInvalid("JobEngine input request refused")

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.show)
  }

  def id(entropy: String): JobId = JobId("cncf", "input", Some(instant), Some(entropy))

  final class CountingIds extends IdGenerationContext {
    private val _delegate = IdGenerationContext.default(IdGenerationContext.IdNamespace("cncf", "input"))
    val calls = new AtomicInteger(0)
    def namespace: IdGenerationContext.IdNamespace = _delegate.namespace
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
      calls.incrementAndGet()
      _delegate.entityId(collection, purpose)
    }
    def opaqueId(purpose: String): String = {
      calls.incrementAndGet()
      _delegate.opaqueId(purpose)
    }
  }

  final class CountingClock extends Clock with JobTimeSource {
    private val _manual = new ManualJobTimeSource(JobInputBoundsFixtures.instant)
    val calls = new AtomicInteger(0)
    def peek: Instant = _manual.now()
    def manual: ManualJobTimeSource = _manual
    def now(): Instant = { calls.incrementAndGet(); peek }
    def instant(): Instant = now()
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(peek, zone)
    def advanceBy(duration: Duration): Instant = _manual.advanceBy(duration)
  }

  final class CountingTask extends JobTask {
    val getters = new AtomicInteger(0)
    val runs = new AtomicInteger(0)
    val admissions = new AtomicInteger(0)
    def actionId: ActionId = {
      getters.incrementAndGet()
      ActionId("cncf", "input", Some(JobInputBoundsFixtures.instant), Some("task"))
    }
    override def requestSummary: Option[String] = { getters.incrementAndGet(); None }
    override def requestParameters: Map[String, String] = { getters.incrementAndGet(); Map.empty }
    override def defaultPersistence: JobPersistencePolicy = { getters.incrementAndGet(); JobPersistencePolicy.Persistent }
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      runs.incrementAndGet()
      TaskSucceeded(OperationResponse.Scalar("ok"))
    }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit = {
      val _ = conclusion
      val _ = ctx
      admissions.incrementAndGet()
      ()
    }
  }

  final class CountingTimer(clock: CountingClock) extends JobTimer {
    private val _delegate = new InMemoryJobEngine.ManualJobTimer(clock.manual)
    val calls = new AtomicInteger(0)
    def pendingCount: Int = _delegate.pendingCount
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = {
      calls.incrementAndGet()
      _delegate.schedule(dueat)(body)
    }
    def advanceBy(duration: Duration): Int = _delegate.advanceBy(duration)
    override def shutdown(): Unit = _delegate.shutdown()
  }

  final case class Observation(resources: JobResourceSnapshot, ids: Int, clocks: Int,
    events: Int, timers: Int, pending: Int, getters: Int, runs: Int, admissions: Int,
    durable: Int, runtime: Int, scopes: Int, queue: Int)

  final case class Fixture(engine: InMemoryJobEngine, clock: CountingClock, timer: CountingTimer,
    context: ExecutionContext, ids: CountingIds, events: EventStore) {
    def observation(task: CountingTask): Observation = Observation(engine.resourceSnapshot,
      ids.calls.get(), clock.calls.get(), success(events.query(EventStore.Query())).size,
      timer.calls.get(), timer.pendingCount, task.getters.get(), task.runs.get(), task.admissions.get(),
      engine.runtimeState.durableJobs.size(), engine.runtimeState.runtimeJobs.size(),
      engine._cancellation_scopes.size(), engine._work_queue.size())
  }

  def fresh(state: InMemoryJobEngine.State = InMemoryJobEngine.State()): Fixture = {
    val clock = new CountingClock
    val timer = new CountingTimer(clock)
    val ids = new CountingIds
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    val events = EventStore.inMemory
    val engine = new InMemoryJobEngine(runtimeState = state,
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
      timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
    engine.withEventStore(events)
    Fixture(engine, clock, timer, context, ids, events)
  }

  def evidence(revision: Long): DurableJobLifecycleWriteEvidence =
    DurableJobLifecycleWriteEvidence(DurableJobProjectionEvidence(
      revision,
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      Vector(DurableTaskDescriptor(TaskId("cncf", "input", Some(instant), Some("task001")).value,
        None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component-a", Some("service-a"), "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, DurableResultOutcome.Pending, DurableRetryEvidence(Vector.empty, 3, None, false, false),
      Vector.empty, None,
      DurableDefinitionSnapshot("definition001", "input-job", 1, 1L, "a" * 64, None, Some("jcl"), Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)), access)

  def taskDescriptor(model: JobTaskReadModel): DurableTaskDescriptor =
    DurableTaskDescriptor(model.taskId.value, model.parentTaskId.map(_.value), DurableTaskKind.Operation,
      DurableTaskTarget("operation", DurableOperationReference("component-a", Some("service-a"), "run", None)),
      DurableTaskRelation(DurableTaskRelationKind.Root, model.parentTaskId.map(_.value)),
      DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask,
        model.status match {
          case JobTaskStatus.Running => DurableTransactionOutcome.Pending
          case JobTaskStatus.Succeeded => DurableTransactionOutcome.Committed
          case JobTaskStatus.Failed => DurableTransactionOutcome.Failed
        }), None)
}
