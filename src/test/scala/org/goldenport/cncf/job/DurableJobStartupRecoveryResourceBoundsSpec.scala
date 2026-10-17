package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed

/*
 * Whole-source admission before durable recovery effects, in all three modes.
 * @since   Oct.  1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobStartupRecoveryResourceBoundsSpec
    extends AnyWordSpec with Matchers with GivenWhenThen {
  "Durable startup recovery source admission" should {
    "R1 refuse invalid bounds and required dependencies before discovery" in {
      Given("all three modes, fresh standard stores and counting sources/ports/callbacks")
      for (mode <- 0 to 2; bound <- Vector(0, -1, 1025, Int.MaxValue)) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(Vector(_candidate(0L, "unused"))))
        When("recovery receives a bound outside the inclusive 1..1024 range")
        val result = _invoke(probe, mode, source, probe.store, bound)
        Then("the fixed refusal precedes source, collection, port and callback work")
        _refused(result)
        source.calls shouldBe 0
        _no_effects(probe)
      }
      for (mode <- 0 to 2; missing <- Vector("source", "store")) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(Vector.empty))
        When("a required source or store is null")
        val result = _invoke(probe, mode, if (missing == "source") null else source,
          if (missing == "store") null else probe.store, 1)
        Then("no discovery or downstream effect is admitted")
        _refused(result)
        source.calls shouldBe 0
        _no_effects(probe)
      }
      val probe = new Probe
      given ExecutionContext = probe.context
      val source = new CountingSource(() => Consequence.success(Vector.empty))
      val coordinator = new DurableJobStartupRecoveryCoordinator(source, probe.store)
      When("runtime port or either required registration callback is null")
      val results = Vector(
        coordinator.recoverRuntime(1, null)(probe.runtimeRegistration).map(_ => ()),
        coordinator.recoverRuntime(1, probe.port)(null).map(_ => ()),
        coordinator.recoverTerminal(1)(null).map(_ => ())
      )
      Then("each dependency failure precedes discovery")
      results.foreach(_refused)
      source.calls shouldBe 0
      _no_effects(probe)
    }

    "R2 admit inclusive bounds and retain exact opaque identity order" in {
      Given("fresh standard stores and unique zero-based candidate vectors at both inclusive bounds")
      for (bound <- Vector(1, 1024)) {
        val probe = new Probe
        given ExecutionContext = probe.context
        val candidates = Vector.tabulate(bound)(index => _candidate(index.toLong, s"opaque-job-$index"))
        val source = new CountingSource(() => Consequence.success(candidates))
        When("observational recovery admits exactly the requested vector")
        val report = _success(new DurableJobStartupRecoveryCoordinator(source, probe.store).recover(bound))
        Then("all original Missing facts, exact ids and ordinals remain in source order")
        report.candidates.map(_.jobId) shouldBe candidates.map(_.jobId)
        report.candidates.map(_.ordinal) shouldBe candidates.map(_.ordinal)
        report.candidates.map(_.fact) shouldBe Vector.fill(bound)(DurableJobStartupRecoveryFact.Missing)
        source.calls shouldBe 1
        source.bounds shouldBe Vector(bound)
        for (mode <- 1 to 2) {
          val modeprobe = new Probe
          val modesource = new CountingSource(() => Consequence.success(candidates))
          When("runtime and terminal recovery admit the same inclusive vector")
          val ordered = _success(_invoke(modeprobe, mode, modesource, modeprobe.store, bound))
          Then("both modes preserve exact ids and ordinals without port/callback work for missing records")
          ordered shouldBe candidates.map(candidate => candidate.ordinal -> candidate.jobId)
          modesource.calls shouldBe 1
          modesource.bounds shouldBe Vector(bound)
          modeprobe.portCalls shouldBe 0
          modeprobe.callbackCalls shouldBe 0
        }
      }
      for (mode <- 0 to 2; bound <- Vector(1, 1024)) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(Vector.empty))
        When("any mode receives an empty vector under a valid requested bound")
        val report = _success(_invoke(probe, mode, source, probe.store, bound))
        Then("the empty report requires one exact-bound discovery and no store or port effects")
        report shouldBe Vector.empty
        source.calls shouldBe 1
        source.bounds shouldBe Vector(bound)
        _no_effects(probe)
      }
    }

    "R3 redact null results, source failures and NonFatal exceptions" in {
      Given("sources returning a null vector, null Consequence, Failure or credential-bearing exception")
      val responses: Vector[() => Consequence[Vector[DurableJobStartupRecoveryCandidate]]] = Vector(
        () => Consequence.success(null),
        () => null,
        () => Consequence.argumentInvalid("SECRET-subject /private/provider/credential"),
        () => throw new IllegalStateException("SECRET-subject /private/provider/credential")
      )
      for (mode <- 0 to 2; response <- responses) {
        val probe = new Probe
        val source = new CountingSource(response)
        When("the source is called exactly once")
        val result = _invoke(probe, mode, source, probe.store, 1)
        Then("every source failure has the same redacted argument-invalid result and zero effects")
        _refused(result)
        source.calls shouldBe 1
        source.bounds shouldBe Vector(1)
        _no_effects(probe)
      }
    }

    "R4 refuse oversupply without processing a valid prefix" in {
      Given("requested 1/1024 responses with one extra candidate after a valid prefix")
      for (mode <- 0 to 2; bound <- Vector(1, 1024)) {
        val probe = new Probe
        val entries = Vector.tabulate(bound + 1)(index => _candidate(index.toLong, s"job-$index"))
        val source = new CountingSource(() => Consequence.success(entries))
        When("the source supplies more candidates than requested")
        val result = _invoke(probe, mode, source, probe.store, bound)
        Then("the whole vector refuses without truncation or prefix registration/load")
        _refused(result)
        source.calls shouldBe 1
        _no_effects(probe)
      }
    }

    "R5 refuse every malformed late identity or access shape" in {
      Given("a valid first candidate followed by a table of malformed identity and access fields")
      val last = _candidate(1L, "SECRET-final-id")
      val malformed = Vector(
        null, last.copy(jobId = null), last.copy(jobId = "  "), last.copy(access = null),
        last.copy(access = _access.copy(tenantId = null)),
        last.copy(access = _access.copy(tenantId = " ")),
        last.copy(access = _access.copy(subjectId = null)),
        last.copy(access = _access.copy(subjectId = " ")),
        last.copy(access = _access.copy(scopes = null)),
        last.copy(access = _access.copy(scopes = Set(null))),
        last.copy(access = _access.copy(scopes = Set(" ")))
      )
      for (mode <- 0 to 2; candidate <- malformed) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(Vector(_candidate(0L, "first"), candidate)))
        When("the final candidate has invalid structural identity or access")
        val result = _invoke(probe, mode, source, probe.store, 2)
        Then("admission refuses the entire response before any prefix effect")
        _refused(result)
        source.calls shouldBe 1
        _no_effects(probe)
      }
    }

    "R6 refuse duplicate ids and unstable ordinals without leaking values" in {
      Given("a valid prefix and duplicate, negative, nonzero-first or unstable-final ordinals")
      val sequences = Vector(
        Vector(_candidate(0L, "SECRET-id"), _candidate(1L, "SECRET-id")),
        Vector(_candidate(0L, "first"), _candidate(-99L, "SECRET-final")),
        Vector(_candidate(9L, "SECRET-first")),
        Vector(_candidate(0L, "first"), _candidate(88L, "SECRET-final"))
      )
      for (mode <- 0 to 2; entries <- sequences) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(entries))
        When("the source violates exact uniqueness or zero-based positional order")
        val result = _invoke(probe, mode, source, probe.store, entries.size)
        Then("the same fixed refusal contains none of the supplied identities or ordinals")
        _refused(result)
        source.calls shouldBe 1
        _no_effects(probe)
      }
    }

    "R7 reject null proof containers while preserving incomplete-token semantics" in {
      Given("a valid prefix and null evidence or a null Option in each of the six proof positions")
      val malformed = Vector(null) ++ (0 until 6).map(index => _evidence_at(index, null))
      for (mode <- 0 to 2; evidence <- malformed) {
        val probe = new Probe
        val source = new CountingSource(() => Consequence.success(Vector(
          _candidate(0L, "first"), _candidate(1L, "last").copy(replayEvidence = evidence))))
        When("the final proof object or one Option container is null")
        val result = _invoke(probe, mode, source, probe.store, 2)
        Then("whole-vector refusal occurs before store or port effects")
        _refused(result)
        _no_effects(probe)
      }
      for (token <- Vector(None, Some(" "), Some(null))) {
        val evidence = DurableReplayEvidence(token, token, token, token, token, token)
        val probe = new Probe
        given ExecutionContext = probe.context
        val submittedid = _job_id("SUBMITTED")
        val terminalid = _job_id("TERMINAL")
        _success(probe.store.create(_record(submittedid, DurableJobLifecycleStatus.Submitted), _access))
        _success(probe.store.create(_record(terminalid, DurableJobLifecycleStatus.Succeeded), _access))
        val entries = Vector(_candidate(0L, submittedid), _candidate(1L, terminalid))
          .map(_.copy(replayEvidence = evidence))
        val source = new CountingSource(() => Consequence.success(entries))
        When("None, blank and Some(null) tokens reach actual signed submitted and terminal records")
        val assessment = DurableReplayAssessment.assess(evidence)
        val report = _success(new DurableJobStartupRecoveryCoordinator(source, probe.store).recover(2))
        Then("the pure category order and RecoveryRequired/terminal facts retain their original meanings")
        assessment.failedCategoryNames shouldBe Vector("idempotency", "input", "definition", "authorization", "provider", "compatibility")
        val submitted = report.candidates.head.fact.asInstanceOf[DurableJobStartupRecoveryFact.Decision].value
        submitted.outcome shouldBe DurableJobRecoveryOutcome.RecoveryRequired
        submitted.failedEvidenceCategories.map(_.wire) shouldBe assessment.failedCategoryNames
        report.candidates.last.fact.asInstanceOf[DurableJobStartupRecoveryFact.Decision].value.outcome shouldBe
          DurableJobRecoveryOutcome.TerminalSucceeded
        When("runtime and terminal modes process the same signed records with incomplete tokens")
        val runtime = _success(new DurableJobStartupRecoveryCoordinator(
          new CountingSource(() => Consequence.success(entries)), probe.store)
          .recoverRuntime(2, probe.port)(probe.runtimeRegistration))
        val terminal = _success(new DurableJobStartupRecoveryCoordinator(
          new CountingSource(() => Consequence.success(entries)), probe.store)
          .recoverTerminal(2)(probe.terminalRegistration))
        Then("incomplete submitted proof never invokes the port and the valid terminal projection remains terminal")
        runtime.candidates.map(_.fact) shouldBe Vector.fill(2)(DurableJobRuntimeRehydrationFact.Refused)
        terminal.candidates.map(_.fact) shouldBe Vector(
          DurableJobTerminalProjectionFact.Refused, DurableJobTerminalProjectionFact.Registered)
        probe.portCalls shouldBe 0
        probe.callbackCalls shouldBe 1
      }
    }

    "R8 preserve per-record authorization refusal alongside terminal and missing facts" in {
      Given("actual signed durable records and structurally valid wrong subject, wrong tenant and empty scopes")
      val probe = new Probe
      given ExecutionContext = probe.context
      val terminalid = _job_id("AUTHORIZED")
      _success(probe.store.create(_record(terminalid, DurableJobLifecycleStatus.Succeeded), _access))
      val wrong = Vector(_access.copy(subjectId = "subject-b"), _access.copy(tenantId = "tenant-b"), _access.copy(scopes = Set.empty))
      val entries = wrong.zipWithIndex.map { case (access, index) =>
        val jobid = _job_id(s"PROTECTED$index")
        _success(probe.store.create(_record(jobid, DurableJobLifecycleStatus.Succeeded), _access))
        _candidate(index.toLong, jobid).copy(access = access)
      } ++ Vector(_candidate(3L, terminalid), _candidate(4L, "missing"))
      val source = new CountingSource(() => Consequence.success(entries))
      When("observation passes shape admission and delegates authorization to the unchanged store")
      val report = _success(new DurableJobStartupRecoveryCoordinator(source, probe.store).recover(5))
      Then("unauthorized candidates retain original Refused facts while terminal and Missing remain distinct")
      report.candidates.take(3).map(_.fact) shouldBe Vector.fill(3)(DurableJobStartupRecoveryFact.Refused)
      report.candidates(3).fact.asInstanceOf[DurableJobStartupRecoveryFact.Decision].value.outcome shouldBe
        DurableJobRecoveryOutcome.TerminalSucceeded
      report.candidates(4).fact shouldBe DurableJobStartupRecoveryFact.Missing
      source.calls shouldBe 1
      probe.portCalls shouldBe 0
      probe.callbackCalls shouldBe 0
      When("runtime and terminal modes apply unchanged authorization to the same signed candidates")
      val runtime = _success(new DurableJobStartupRecoveryCoordinator(
        new CountingSource(() => Consequence.success(entries)), probe.store)
        .recoverRuntime(5, probe.port)(probe.runtimeRegistration))
      val terminal = _success(new DurableJobStartupRecoveryCoordinator(
        new CountingSource(() => Consequence.success(entries)), probe.store)
        .recoverTerminal(5)(probe.terminalRegistration))
      Then("unauthorized access remains per-record Refused and only the authorized terminal is registered")
      runtime.candidates.map(_.fact) shouldBe Vector.fill(5)(DurableJobRuntimeRehydrationFact.Refused)
      terminal.candidates.map(_.fact) shouldBe Vector(
        DurableJobTerminalProjectionFact.Refused, DurableJobTerminalProjectionFact.Refused,
        DurableJobTerminalProjectionFact.Refused, DurableJobTerminalProjectionFact.Registered,
        DurableJobTerminalProjectionFact.Missing)
      probe.portCalls shouldBe 0
      probe.callbackCalls shouldBe 1
    }

    "R9 retain the exact successful engine reports after a malformed response" in {
      Given("each engine recovery mode has already published a successful report")
      for (mode <- 0 to 2) {
        val enginefixture = JobEngineTestFixture.manual()
        val goodprobe = new Probe
        val badprobe = new Probe
        val good = new CountingSource(() => Consequence.success(Vector.empty))
        val bad = new CountingSource(() => Consequence.success(Vector(_candidate(0L, "first"), null)))
        try {
          _success(_engine_invoke(enginefixture.engine, goodprobe, mode, good, 1))
          val previous = _engine_report(enginefixture.engine, mode).get
          When("the next invocation uses fresh unregistered storage and a malformed late candidate")
          val result = _engine_invoke(enginefixture.engine, badprobe, mode, bad, 2)
          Then("the same successful report object remains and no runtime, timer, task or port work appears")
          _refused(result)
          (_engine_report(enginefixture.engine, mode).get eq previous) shouldBe true
          bad.calls shouldBe 1
          _no_effects(badprobe)
          enginefixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
          enginefixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
          enginefixture.timer.pendingCount shouldBe 0
          enginefixture.engine.drainAll() shouldBe 0
        } finally enginefixture.engine.shutdown()
      }
    }

    "R10 preserve generated order and refuse a generated late defect before effects" in {
      Given("seeded finite vectors of 1..16 unique opaque ids and one late structural/duplicate/ordinal mutation")
      val generator = for {
        count <- Gen.choose(1, 16)
        salt <- Gen.choose(1, 100000)
        defect <- Gen.choose(0, 2)
      } yield (count, salt, defect)
      val property = Prop.forAll(generator) { case (count, salt, defect) =>
        val entries = Vector.tabulate(count)(index => _candidate(index.toLong, s"generated-$salt-$index"))
        val mutated = defect match {
          case 0 => entries.updated(count - 1, entries.last.copy(access = null))
          case 1 => entries :+ entries.head.copy(ordinal = count.toLong)
          case _ => entries.updated(count - 1, entries.last.copy(ordinal = -1L))
        }
        for (mode <- 0 to 2) {
          val probe = new Probe
          val source = new CountingSource(() => Consequence.success(entries))
          When("a generated valid vector is admitted in its source order")
          val report = _success(_invoke(probe, mode, source, probe.store, count))
          Then("exact ordinal/id order and the unchanged requested bound survive")
          report shouldBe entries.map(candidate => candidate.ordinal -> candidate.jobId)
          source.calls shouldBe 1
          source.bounds shouldBe Vector(count)
          val badprobe = new Probe
          val badsource = new CountingSource(() => Consequence.success(mutated))
          When("the generated vector receives one late defect")
          val result = _invoke(badprobe, mode, badsource, badprobe.store, mutated.size)
          Then("complete refusal happens before collection registration, port or callback effects")
          _refused(result)
          _no_effects(badprobe)
        }
        true
      }
      When("ScalaCheck runs 24 successful checks with an explicit fixed seed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(6970933L)), property)
      Then("all generated admission and refusal cases satisfy the behavior")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _complete_evidence = DurableReplayEvidence(
    Some("idempotency-proof"), Some("input-proof"), Some("definition-proof"),
    Some("authorization-proof"), Some("provider-proof"), Some("compatibility-proof"))

  private final class CountingSource(
    response: () => Consequence[Vector[DurableJobStartupRecoveryCandidate]]
  ) extends DurableJobStartupRecoverySource {
    private var _calls = 0
    private var _bounds = Vector.empty[Int]
    def calls: Int = _calls
    def bounds: Vector[Int] = _bounds
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      _calls += 1
      _bounds = _bounds :+ maxCandidates
      response()
    }
  }

  private final class Probe {
    val context: ExecutionContext = ExecutionContext.test()
    val entitystore: EntityStore = EntityStore.standard()
    val store: DurableJobStore = new DurableJobStore(entitystore)
    private var _port_calls = 0
    private var _callback_calls = 0
    def portCalls: Int = _port_calls
    def callbackCalls: Int = _callback_calls
    val port: DurableJobRuntimeRehydrationPort = new DurableJobRuntimeRehydrationPort {
      def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
        decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] = {
        val _ = (candidate, snapshot, decision)
        _port_calls += 1
        Consequence.stateInvalid("counted port refusal")
      }
    }
    val runtimeRegistration: (DurableJobStartupRecoveryCandidate, DurableJobStoreSnapshot,
      DurableJobRecoveryDecision, DurableJobRuntimeRehydration) => DurableJobRuntimeRehydrationFact =
      (_, _, _, _) => { _callback_calls += 1; DurableJobRuntimeRehydrationFact.Refused }
    val terminalRegistration: (DurableJobStartupRecoveryCandidate, DurableJobStoreSnapshot,
      DurableJobRecoveryDecision, DurableJobTerminalProjection) => DurableJobTerminalProjectionFact =
      (_, _, _, _) => { _callback_calls += 1; DurableJobTerminalProjectionFact.Registered }
  }

  private def _invoke(probe: Probe, mode: Int, source: DurableJobStartupRecoverySource,
    store: DurableJobStore, bound: Int): Consequence[Vector[(Long, String)]] = {
    given ExecutionContext = probe.context
    val coordinator = new DurableJobStartupRecoveryCoordinator(source, store)
    mode match {
      case 0 => coordinator.recover(bound).map(_.candidates.map(candidate => candidate.ordinal -> candidate.jobId))
      case 1 => coordinator.recoverRuntime(bound, probe.port)(probe.runtimeRegistration)
        .map(_.candidates.map(candidate => candidate.ordinal -> candidate.jobId))
      case _ => coordinator.recoverTerminal(bound)(probe.terminalRegistration)
        .map(_.candidates.map(candidate => candidate.ordinal -> candidate.jobId))
    }
  }

  private def _engine_invoke(engine: InMemoryJobEngine, probe: Probe, mode: Int,
    source: DurableJobStartupRecoverySource, bound: Int): Consequence[Unit] = {
    given ExecutionContext = probe.context
    mode match {
      case 0 => engine.recoverDurableStartup(source, probe.store, bound).map(_ => ())
      case 1 => engine.recoverDurableStartup(source, probe.store, probe.port, bound).map(_ => ())
      case _ => engine.recoverDurableTerminalFacts(source, probe.store, bound).map(_ => ())
    }
  }

  private def _engine_report(engine: InMemoryJobEngine, mode: Int): Option[AnyRef] = mode match {
    case 0 => engine.durableStartupRecoveryReport
    case 1 => engine.durableRuntimeRehydrationReport
    case _ => engine.durableTerminalProjectionReport
  }

  private def _no_effects(probe: Probe): Unit = {
    probe.context.entitySpace.entityOption(DurableJobStoreEntity.Collection) shouldBe None
    probe.portCalls shouldBe 0
    probe.callbackCalls shouldBe 0
  }

  private def _refused[A](result: Consequence[A]): Unit = result match {
    case Consequence.Failure(conclusion) =>
      val expected = Consequence.argumentInvalid[Unit]("Durable Job startup recovery batch refused")
      expected match {
        case Consequence.Failure(fixed) => conclusion.show shouldBe fixed.show
        case null => fail("expected structured argument-invalid refusal")
      }
    case _ => fail("expected a fixed startup recovery refusal")
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _candidate(ordinal: Long, jobid: String): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, jobid, _access, _complete_evidence)

  private def _evidence_at(index: Int, token: Option[String]): DurableReplayEvidence = index match {
    case 0 => _complete_evidence.copy(idempotency = token)
    case 1 => _complete_evidence.copy(input = token)
    case 2 => _complete_evidence.copy(definition = token)
    case 3 => _complete_evidence.copy(authorization = token)
    case 4 => _complete_evidence.copy(provider = token)
    case _ => _complete_evidence.copy(compatibility = token)
  }

  private def _job_id(entropy: String): String =
    JobId("cncf", "terminal", Some(_instant), Some(entropy)).value

  private def _record(jobid: String, status: DurableJobLifecycleStatus): DurableJobRecord = {
    val body = _body(jobid, status, None, None, false)
    val tasks = body.tasks.map(task => task.copy(
      taskId = TaskId("cncf", "terminal", Some(_instant), Some("ROOT")).value,
      transaction = task.transaction.copy(
        role = DurableTransactionRole.Own,
        scope = DurableTransactionScope.PerTask,
        outcome =
        if (status == DurableJobLifecycleStatus.Succeeded) DurableTransactionOutcome.Committed
        else DurableTransactionOutcome.Pending)
    ))
    _success(DurableJobRecord.createV2(body.copy(tasks = tasks)))
  }

  private def _body(
    jobid: String,
    status: DurableJobLifecycleStatus,
    startedat: Option[Instant],
    nextretryat: Option[Instant],
    recoveryrequired: Boolean
  ): DurableJobRecordBody = {
    val terminal = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
      case DurableJobLifecycleStatus.Failed =>
        DurableResultOutcome.Failed(
          DurableFailureSummary("execution", "failed", "failed", retryable = false)
        )
      case DurableJobLifecycleStatus.Cancelled => DurableResultOutcome.Cancelled(None)
      case DurableJobLifecycleStatus.Submitted |
          DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended => DurableResultOutcome.Pending
    }
    DurableJobRecordBody(
      identity = DurableJobIdentity(jobid, 1L, _instant, _instant.plusSeconds(20L)),
      authorization = DurableJobAuthorization(
        "tenant-a",
        DurableSubject("subject-a", "user"),
        DurableVisibility.Subject,
        Set("job.read")
      ),
      lifecycle = DurableJobLifecycle(
        status,
        priority = 0,
        runMode = DurableRunMode.Async,
        retry = DurableRetryEvidence(
          Vector.empty,
          3,
          nextretryat,
          exhausted = false,
          recoveryRequired = recoveryrequired
        ),
        schedule = DurableScheduleState(Some(_instant), startedat, None)
      ),
      tasks = Vector(DurableTaskDescriptor(
        "task-001",
        None,
        DurableTaskKind.Operation,
        DurableTaskTarget(
          "operation",
          DurableOperationReference("component-a", Some("service-a"), "run", None)
        ),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(
          DurableTransactionRole.None,
          DurableTransactionScope.None,
          DurableTransactionOutcome.Pending
        ),
        None
      )),
      inputs = Vector.empty,
      result = terminal,
      timeline = Vector.empty,
      diagnostics = Vector.empty,
      calltreeReference = None,
      definitionSnapshot = DurableDefinitionSnapshot(
        "definition-001",
        "daily-job",
        1,
        1L,
        _digest,
        None,
        Some("jcl"),
        Map.empty
      ),
      retention = DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    )
  }

}
