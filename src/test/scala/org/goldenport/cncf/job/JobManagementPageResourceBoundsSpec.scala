package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobManagementPageResourceBoundsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Management resource admission" should {
    "request shape and lazy snapshots" which {
      "QP1 admit bounded pages and refuse malformed shapes before observations" in {
        Given("an actual manual engine with three completed persistent jobs, events and counted snapshot/policy suppliers")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val ids = Vector.fill(3)(_submit(fixture))
        val before = ids.map(fixture.engine.query)
        val eventcount = _events(fixture.events)
        try {
          for (request <- Vector(JobManagementQuery(limit = 1), JobManagementQuery(limit = 100), JobManagementQuery())) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("a structurally admitted page is requested")
            val page = _success(JobManagementReader.queryPage(supplier.snapshot, request, policy))
            Then("one snapshot and each candidate are observed exactly once")
            page.entries.size shouldBe math.min(3, request.limit)
            page.totalCount shouldBe 3
            supplier.calls.get() shouldBe 1
            policy.calls.get() shouldBe 3
          }
          val bad = Vector(null, JobManagementQuery(status = null), JobManagementQuery(origin = null),
            JobManagementQuery(cursor = null), JobManagementQuery(status = Some(null)),
            JobManagementQuery(origin = Some(null)), JobManagementQuery(cursor = Some(null))) ++
            Vector(0, -1, 101, Int.MaxValue).map(limit => JobManagementQuery(limit = limit))
          for (request <- bad) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("a malformed request is supplied")
            val result = JobManagementReader.queryPage(supplier.snapshot, request, policy)
            Then("the closed refusal precedes snapshot and policy and preserves Job/event facts")
            result shouldBe a[Consequence.Failure[_]]
            supplier.calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
            ids.map(fixture.engine.query) shouldBe before
            _events(fixture.events) shouldBe eventcount
            fixture.timer.pendingCount shouldBe 0
          }
        } finally fixture.engine.shutdown()
      }

      "QP2 preserve exact task and timeline metadata without offset overflow" in {
        Given("a real job with three executed tasks and its complete ordered timeline")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _submit(fixture, 3)
        val model = fixture.engine.query(id).get
        val tasks = model.tasks.tasks.sortBy(task => (task.startedAt.toEpochMilli, task.taskId.print))
        val timeline = model.timeline.events.sortBy(event => (event.sequence, event.occurredAt.toEpochMilli))
        try {
          for (limit <- Vector(1, 100); offset <- Vector(0, 1, Int.MaxValue - 1, Int.MaxValue)) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("the admitted task and timeline pages use the supplied offset and limit")
            val taskpage = _success(JobManagementReader.queryTasks(supplier.snapshot, id, offset, limit, policy)).get
            val timelinepage = _success(JobManagementReader.queryTimeline(supplier.snapshot, id, offset, limit, policy)).get
            Then("drop/take semantics preserve order, counts, offset and limit even near Int.MaxValue")
            taskpage.tasks shouldBe tasks.drop(offset).take(limit)
            taskpage.offset shouldBe offset
            taskpage.limit shouldBe limit
            taskpage.totalCount shouldBe tasks.size
            taskpage.fetchedCount shouldBe taskpage.tasks.size
            timelinepage.events shouldBe timeline.drop(offset).take(limit)
            timelinepage.offset shouldBe offset
            timelinepage.limit shouldBe limit
            timelinepage.totalCount shouldBe timeline.size
            timelinepage.fetchedCount shouldBe timelinepage.events.size
            supplier.calls.get() shouldBe 2
            policy.calls.get() shouldBe 2
          }
          for ((offset, limit) <- Vector((-1, 1), (0, 0), (0, -1), (0, 101), (0, Int.MaxValue))) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("invalid pagination is sent to both exact page facades")
            val tasksresult = JobManagementReader.queryTasks(supplier.snapshot, id, offset, limit, policy)
            val timelineresult = JobManagementReader.queryTimeline(supplier.snapshot, id, offset, limit, policy)
            Then("both refuse before a snapshot or candidate authorization")
            tasksresult shouldBe a[Consequence.Failure[_]]
            timelineresult shouldBe a[Consequence.Failure[_]]
            supplier.calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
          }
        } finally fixture.engine.shutdown()
      }

      "QP3 bound opaque cursor shape before snapshot or candidate access" in {
        Given("two real jobs and cursor values containing a private sentinel")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val _ = _submit(fixture)
        val _ = _submit(fixture)
        val bad = Vector(null, JobManagementCursor.fromOpaque(null), JobManagementCursor.fromOpaque("  "),
          JobManagementCursor.fromOpaque("PRIVATE-CURSOR/" + "x" * 267),
          JobManagementCursor.fromOpaque("PRIVATE-CURSOR/" + "x" * 1048576))
        try {
          for (cursor <- bad) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("the malformed or oversized opaque token is used")
            val decoded = JobManagementCursor.decode(cursor)
            val result = JobManagementReader.queryPage(supplier.snapshot, JobManagementQuery(cursor = Some(cursor)), policy)
            Then("decode and page refuse without echoing input or observing candidates")
            decoded shouldBe a[Consequence.Failure[_]]
            result shouldBe a[Consequence.Failure[_]]
            _message(result) should not include "PRIVATE-CURSOR"
            _message(decoded) should not include "PRIVATE-CURSOR"
            supplier.calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
          }
          When("an actual bounded continuation is decoded and followed")
          val first = _success(fixture.engine.queryPage(JobManagementQuery(limit = 1)))
          val cursor = first.nextCursor.get
          val second = fixture.engine.queryPage(JobManagementQuery(limit = 1, cursor = Some(cursor)))
          Then("the legal token still decodes and produces the next page")
          cursor.value.length should be <= 280
          JobManagementCursor.decode(cursor) shouldBe a[Consequence.Success[_]]
          second shouldBe a[Consequence.Success[_]]
        } finally fixture.engine.shutdown()
      }
    }

    "continuation grammar and authorization" which {
      "QP4 retain the canonical jmq1 wire grammar, fingerprints and legal padding" in {
        Given("the fixed five-field golden grammar with three SHA-256-shaped fingerprints")
        val filter = "a" * 64
        val caller = "b" * 64
        val snapshot = "c" * 64
        val gold = s"jmq1|$filter|$caller|$snapshot|12"
        val padded = "am1xMXxhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhfGJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJ8Y2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY3wxMg=="
        When("the known payload is wrapped and the unchanged public codec produces a continuation")
        val decoded = _success(JobManagementCursor.decode(JobManagementCursor.fromOpaque(padded)))
        val encoded = JobManagementCursor.encode(filter, caller, snapshot, 12)
        Then("the fields, URL-safe unpadded bytes and previously accepted padding remain exact")
        decoded shouldBe JobManagementCursorPayload("jmq1", filter, caller, snapshot, 12)
        encoded.value shouldBe padded.stripSuffix("==")
        new String(Base64.getUrlDecoder.decode(encoded.value), StandardCharsets.UTF_8) shouldBe gold
        encoded.value should not include "+"
        encoded.value should not include "/"
        encoded.value should not include "="
        _success(JobManagementCursor.decode(encoded)) shouldBe decoded
        JobManagementCursor.fingerprint("abc") shouldBe "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
      }

      "QP5 refuse noncanonical offsets before snapshots and retain canonical Int.MaxValue decoding" in {
        Given("an actual authorized continuation whose binding remains unchanged")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val _ = _submit(fixture)
        val _ = _submit(fixture)
        val first = _success(fixture.engine.queryPage(JobManagementQuery(limit = 1)))
        val payload = _success(JobManagementCursor.decode(first.nextCursor.get))
        try {
          for (offset <- Vector("0", "-1", "01", "2147483648")) {
            val supplier = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("a noncanonical continuation offset is submitted")
            val result = JobManagementReader.queryPage(supplier.snapshot,
              JobManagementQuery(limit = 1, cursor = Some(_opaque(payload, offset))), policy)
            Then("malformed grammar refuses before the snapshot and policy")
            _message(result) should include("malformed cursor")
            supplier.calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
          }
          val supplier = new SnapshotProbe(fixture.engine)
          val policy = new ReadProbe
          val cursor = _opaque(payload, Int.MaxValue.toString)
          When("the maximum canonical positive Int offset is decoded and checked against the actual bound snapshot")
          val decoded = _success(JobManagementCursor.decode(cursor))
          val result = JobManagementReader.queryPage(supplier.snapshot, JobManagementQuery(cursor = Some(cursor)), policy)
          Then("280 encoded characters remain legal while the out-of-range page refuses without clipping")
          cursor.value.length shouldBe 280
          decoded.nextOffset shouldBe Int.MaxValue
          _message(result) should include("offset out of range")
          supplier.calls.get() shouldBe 1
          policy.calls.get() shouldBe 2
        } finally fixture.engine.shutdown()
      }

      "QP6 preserve ordered traversal and filter, caller, snapshot precedence" in {
        Given("four actual records and their first admitted cursor")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val ids = Vector.fill(4)(_submit(fixture))
        val policy = new ReadProbe
        val supplier = new SnapshotProbe(fixture.engine)
        val first = _success(JobManagementReader.queryPage(supplier.snapshot, JobManagementQuery(limit = 1), policy))
        val payload = _success(JobManagementCursor.decode(first.nextCursor.get))
        try {
          When("the caller traverses every page using exactly one snapshot per request")
          val pages = _traverse(fixture.engine, 1)
          val found = pages.flatMap(_.entries.map(_.jobId))
          Then("all ordered records occur once and the initial evaluation is singular")
          found shouldBe ids.sortBy(_.print)
          found.distinct shouldBe found
          pages.map(_.totalCount).distinct shouldBe Vector(4)
          pages.last.nextCursor shouldBe None
          supplier.calls.get() shouldBe 1
          When("simultaneous mismatches use the original filter-first and caller-next precedence")
          val filterbad = payload.copy(filterFingerprint = "0" * 64, callerVisibilityFingerprint = "0" * 64,
            authorizedSnapshotFingerprint = "0" * 64, nextOffset = Int.MaxValue)
          val callerbad = filterbad.copy(filterFingerprint = payload.filterFingerprint)
          val snapshotbad = callerbad.copy(callerVisibilityFingerprint = payload.callerVisibilityFingerprint)
          val filterresult = fixture.engine.queryPage(JobManagementQuery(cursor = Some(_opaque(filterbad, Int.MaxValue.toString))))
          val callerresult = fixture.engine.queryPage(JobManagementQuery(cursor = Some(_opaque(callerbad, Int.MaxValue.toString))))
          val snapshotresult = fixture.engine.queryPage(JobManagementQuery(cursor = Some(_opaque(snapshotbad, Int.MaxValue.toString))))
          Then("the original error categories precede offset refusal")
          _message(filterresult) should include("request filter mismatch")
          _message(callerresult) should include("caller visibility mismatch")
          _message(snapshotresult) should include("expired snapshot")
          When("the actual caller changes while every record stays authorized")
          val foreign = ExecutionContext.test(SecurityContext.Privilege.Anonymous)
          val foreignresult = fixture.engine.queryPage(JobManagementQuery(cursor = first.nextCursor), policy)(using foreign)
          Then("the continuation remains bound to the original caller")
          _message(foreignresult) should include("caller visibility mismatch")
          When("a real authorized record is added before replay")
          val _ = _submit(fixture)
          val changed = fixture.engine.queryPage(JobManagementQuery(cursor = first.nextCursor), policy)
          Then("the changed actual snapshot expires the continuation")
          _message(changed) should include("expired snapshot")
        } finally fixture.engine.shutdown()
      }

      "QP7 omit foreign and denied records before counts and skip policy for malformed requests" in {
        Given("real owner and foreign contexts with one persistent record each")
        val fixture = _fixture()
        val owner = ExecutionContext.test(SecurityContext.Privilege.User)
        val foreign = ExecutionContext.test(SecurityContext.Privilege.Anonymous)
        val ownerid = _success(fixture.engine.submit(List(new CompletedTask), owner, JobSubmitOption(runMode = JobRunMode.Sync)))
        val foreignid = _success(fixture.engine.submit(List(new CompletedTask), foreign, JobSubmitOption(runMode = JobRunMode.Sync)))
        given ExecutionContext = owner
        try {
          When("the owner reads through the original authorization policy")
          val page = _success(fixture.engine.queryPage(JobManagementQuery(limit = 1)))
          val deny = new ReadProbe(deny = true)
          val denied = _success(fixture.engine.queryPage(JobManagementQuery(), deny))
          Then("hidden identities affect neither count nor cursor")
          page.entries.map(_.jobId) shouldBe Vector(ownerid)
          page.totalCount shouldBe 1
          page.nextCursor shouldBe None
          page.entries.map(_.jobId) should not contain foreignid
          denied.entries shouldBe empty
          denied.totalCount shouldBe 0
          denied.nextCursor shouldBe None
          for (models <- Vector(fixture.engine.listJobs(100, false), Vector.empty)) {
            val calls = new AtomicInteger(0)
            val policy = new ReadProbe(deny = true)
            def _snapshot_(): JobManagementReader.Snapshot = {
              calls.incrementAndGet()
              JobManagementReader.Snapshot(models, Map.empty, Set.empty)
            }
            When("a malformed page is submitted against existing, missing or denied facts")
            val result = JobManagementReader.queryPage(_snapshot_(), JobManagementQuery(cursor = Some(JobManagementCursor.fromOpaque("PRIVATE!"))), policy)
            Then("no candidate authorization or supplied identity is exposed")
            calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
            _message(result) should not include foreignid.value
            _message(result) should not include ownerid.value
            _message(result) should not include "PRIVATE!"
          }
        } finally fixture.engine.shutdown()
      }
    }

    "compatibility and generated traversal" which {
      "QP8 retain unrestricted raw projections and complete facts" in {
        Given("101 real records and one job containing 101 executed tasks")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val ids = Vector.fill(101)(_submit(fixture))
        val id = _submit(fixture, 101)
        val model = fixture.engine.query(id).get
        try {
          When("raw list, task and timeline compatibility methods use arbitrary limits and normalized negatives")
          val list = fixture.engine.listJobs(1000)
          val tasks = fixture.engine.queryTasks(id, 0, Int.MaxValue).get
          val timeline = fixture.engine.queryTimeline(id, 0, Int.MaxValue).get
          val detail = _success(fixture.engine.queryManagementDetail(id)).get
          Then("all available facts survive and only the management page is capped")
          list.map(_.jobId).toSet shouldBe (ids :+ id).toSet
          list.size shouldBe 102
          fixture.engine.listJobs(-1) shouldBe empty
          tasks.tasks shouldBe model.tasks.tasks.sortBy(task => (task.startedAt.toEpochMilli, task.taskId.print))
          tasks.tasks.size shouldBe 101
          timeline.events shouldBe model.timeline.events.sortBy(event => (event.sequence, event.occurredAt.toEpochMilli))
          timeline.events.size should be > 100
          fixture.engine.queryTasks(id, -1, 1).get.offset shouldBe 0
          fixture.engine.queryTasks(id, 0, -1).get.tasks shouldBe empty
          fixture.engine.queryTimeline(id, -1, 1).get.offset shouldBe 0
          fixture.engine.queryTimeline(id, 0, -1).get.events shouldBe empty
          fixture.engine.queryTasks(id, Int.MaxValue, Int.MaxValue).get.tasks shouldBe empty
          fixture.engine.queryTimeline(id, Int.MaxValue, Int.MaxValue).get.events shouldBe empty
          detail.taskCount shouldBe 101
          detail.timelineCount shouldBe model.timeline.totalCount
          _success(fixture.engine.queryPage(JobManagementQuery())).entries.size shouldBe 100
          fixture.engine.queryPage(JobManagementQuery(limit = 101)) shouldBe a[Consequence.Failure[_]]
        } finally fixture.engine.shutdown()
      }

      "QP9 conserve generated real record traversal and refuse a late malformed continuation" in {
        Given("finite record/page/task counts generated with one fixed ScalaCheck seed")
        val generator = for {
          count <- Gen.choose(1, 12)
          limit <- Gen.choose(1, 5)
          offset <- Gen.choose(0, 8)
        } yield (count, limit, offset)
        val property = Prop.forAll(generator) { case (count, limit, offset) =>
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val ids = Vector.fill(count)(_submit(fixture, 2))
          try {
            When("generated actual engine records are traversed through the bounded Reader")
            val pages = _traverse(fixture.engine, limit)
            val found = pages.flatMap(_.entries.map(_.jobId))
            val taskpage = _success(fixture.engine.queryManagementTasks(ids.head, offset, limit)).get
            Then("ordered unique traversal, authorized counts and exact task offset survive")
            found shouldBe ids.sortBy(_.print)
            found.distinct shouldBe found
            pages.map(_.totalCount).distinct shouldBe Vector(count)
            taskpage.tasks shouldBe fixture.engine.queryTasks(ids.head, offset, limit).get.tasks
            taskpage.totalCount shouldBe 2
            val probe = new SnapshotProbe(fixture.engine)
            val policy = new ReadProbe
            When("a malformed continuation is appended after the real traversal")
            val refused = JobManagementReader.queryPage(probe.snapshot,
              JobManagementQuery(limit = limit, cursor = Some(JobManagementCursor.fromOpaque("x" * 281))), policy)
            Then("the late request refuses without a further snapshot or policy effect")
            refused shouldBe a[Consequence.Failure[_]]
            probe.calls.get() shouldBe 0
            policy.calls.get() shouldBe 0
            true
          } finally fixture.engine.shutdown()
        }
        When("ScalaCheck executes at least 24 seeded actual behavioral traversals")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709331L)), property)
        Then("every generated traversal and refusal satisfies the contract")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  private val _instant = Instant.parse("2026-10-01T00:00:00Z")

  private final class CompletedTask extends JobTask {
    val actionId: ActionId = ActionId.generate()
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar("complete"))
    }
  }

  private final class ReadProbe(deny: Boolean = false) extends JobQueryPolicy {
    val calls = new AtomicInteger(0)
    def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] = {
      val _ = model
      calls.incrementAndGet()
      if (deny) Consequence.securityPermissionDenied("read refused") else Consequence.unit
    }
  }

  private final class SnapshotProbe(engine: InMemoryJobEngine) {
    val calls = new AtomicInteger(0)
    def snapshot: JobManagementReader.Snapshot = {
      calls.incrementAndGet()
      val models = engine.listJobs(Int.MaxValue, false)
      JobManagementReader.Snapshot(models, models.flatMap(model => engine.getResult(model.jobId).map(model.jobId -> _)).toMap, Set.empty)
    }
  }

  private final case class Fixture(
    engine: InMemoryJobEngine,
    timer: InMemoryJobEngine.ManualJobTimer,
    context: ExecutionContext,
    events: EventStore
  )

  private def _fixture(): Fixture = {
    val clock = new ManualJobTimeSource(_instant)
    val timer = new InMemoryJobEngine.ManualJobTimer(clock)
    val events = EventStore.inMemory
    val engine = new InMemoryJobEngine(schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
      timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
    engine.withEventStore(events)
    Fixture(engine, timer, ExecutionContext.test(), events)
  }

  private def _submit(fixture: Fixture, count: Int = 1): JobId =
    _success(fixture.engine.submit(List.fill(count)(new CompletedTask), fixture.context, JobSubmitOption(runMode = JobRunMode.Sync)))

  private def _traverse(engine: InMemoryJobEngine, limit: Int)(using ExecutionContext): Vector[JobManagementPage] = {
    var pages = Vector.empty[JobManagementPage]
    var cursor = Option.empty[JobManagementCursor]
    var more = true
    while (more) {
      val page = _success(engine.queryPage(JobManagementQuery(limit = limit, cursor = cursor)))
      pages :+= page
      cursor = page.nextCursor
      more = cursor.nonEmpty
    }
    pages
  }

  private def _opaque(payload: JobManagementCursorPayload, offset: String): JobManagementCursor = {
    val text = Vector(payload.version, payload.filterFingerprint, payload.callerVisibilityFingerprint,
      payload.authorizedSnapshotFingerprint, offset).mkString("|")
    JobManagementCursor.fromOpaque(Base64.getUrlEncoder.withoutPadding.encodeToString(text.getBytes(StandardCharsets.UTF_8)))
  }

  private def _events(events: EventStore): Int = _success(events.query(EventStore.Query())).size

  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => ""
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
