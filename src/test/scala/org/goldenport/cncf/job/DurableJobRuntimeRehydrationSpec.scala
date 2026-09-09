package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationExecutionPolicy,
  EntityStore,
  RevisionPreconditionPolicy
}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for provider-owned runtime registration after
 * startup recovery has canonically admitted and classified one durable record.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobRuntimeRehydrationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-runtime-rehydration-contract, example:E1, rules:R1,R2,R3,R4,R5, phase:69.1, slice:JM69-03G"
  )
  private val _e2 = afterWord(
    "in spec:durable-job-runtime-rehydration-contract, example:E2, rules:R1,R2,R3,R4,R5, phase:69.1, slice:JM69-03G"
  )
  private val _e3 = afterWord(
    "in spec:durable-job-runtime-rehydration-contract, example:E3, rules:R2,R3,R4,R5, phase:69.1, slice:JM69-03G"
  )

  "DurableJobRuntimeRehydration" should {
    "E1 register a future durable scheduled start before normal queue execution" must _e1 {
      "when a provider reconstructs one proof-complete unstarted scheduled candidate" in {
        Given("docs/spec/durable-job-runtime-rehydration-contract.md; R1-R5; E1; an admitted V2 Submitted record with a future durable schedule, provider-owned live task, and no JobEntity binding")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val jobid = _job_id("scheduled")
        val snapshot = _create(
          fixture.store,
          _record(jobid, scheduledAt = Some(_instant.plusSeconds(10L)))
        )
        val runs = new AtomicInteger(0)
        val source = _source(Vector(_candidate(0L, jobid)))
        val port = _port(Map(
          jobid -> Consequence.success(_runtime(jobid, new CountingTask(runs), fixture.context))
        ))
        val enginefixture = createManualJobEngine(initialTime = _instant)

        When("the distinct runtime registers the admitted provider result")
        val report = _success(
          enginefixture.engine.recoverDurableStartup(source, fixture.store, port, 1)
        )

        Then("it records the entity-sync diagnostic while retaining exact durable identity timestamps before the existing delayed-start and queue path")
        report shouldBe DurableJobRuntimeRehydrationReport(Vector(
          DurableJobRuntimeRehydrationCandidateReport(
            0L,
            jobid,
            DurableJobRuntimeRehydrationFact.Registered
          )
        ))
        enginefixture.engine.durableRuntimeRehydrationReport shouldBe Some(report)
        val registered = enginefixture.engine.runtimeState.durableJobs.get(_job(jobid))
        registered.createdAt shouldBe snapshot.record.body.identity.createdAt
        registered.updatedAt shouldBe snapshot.record.body.identity.updatedAt
        registered.debug.parameters.get("cncf.job.entitySync") shouldBe Some("failed")
        registered.debug.executionNotes.exists(_.startsWith("job-entity-sync-failed: ")) shouldBe true
        enginefixture.timer.pendingCount shouldBe 1
        enginefixture.engine.drainAll() shouldBe 0
        runs.get() shouldBe 0

        When("the existing timer promotes the due state and the normal queue drains")
        enginefixture.timer.advanceBy(Duration.ofSeconds(10L)) shouldBe 1
        enginefixture.engine.drainAll() shouldBe 1

        Then("the live task runs only through normal JobEngine execution")
        enginefixture.engine.getStatus(_job(jobid)) shouldBe Some(JobStatus.Succeeded)
        runs.get() shouldBe 1
      }
    }

    "E2 register a future durable delayed retry before normal queue execution" must _e2 {
      "when a provider reconstructs one proof-complete delayed-retry candidate" in {
        Given("docs/spec/durable-job-runtime-rehydration-contract.md; R1-R5; E2; an admitted V2 Submitted record with a future retry due state and provider-owned live task")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val jobid = _job_id("retry")
        _create(fixture.store, _record(jobid, nextRetryAt = Some(_instant.plusSeconds(15L))))
        val runs = new AtomicInteger(0)
        val source = _source(Vector(_candidate(0L, jobid)))
        val port = _port(Map(
          jobid -> Consequence.success(_runtime(jobid, new CountingTask(runs), fixture.context))
        ))
        val enginefixture = createManualJobEngine(initialTime = _instant)

        When("the distinct runtime registers the admitted provider result")
        val report = _success(
          enginefixture.engine.recoverDurableStartup(source, fixture.store, port, 1)
        )

        Then("it retains one delayed-retry timer without direct task execution")
        report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
        enginefixture.timer.pendingCount shouldBe 1
        enginefixture.engine.drainAll() shouldBe 0
        runs.get() shouldBe 0

        When("the existing retry timer promotes its due state and the normal queue drains")
        enginefixture.timer.advanceBy(Duration.ofSeconds(15L)) shouldBe 1
        enginefixture.engine.drainAll() shouldBe 1

        Then("the live retry task runs only through normal JobEngine execution")
        enginefixture.engine.getStatus(_job(jobid)) shouldBe Some(JobStatus.Succeeded)
        runs.get() shouldBe 1
      }
    }

    "E3 refuse non-admitted, non-eligible, and invalid provider results without runnable work" must _e3 {
      "when a source presents every refused lifecycle and provider-result case" in {
        Given("docs/spec/durable-job-runtime-rehydration-contract.md; R2-R5; E3; missing, corrupt, access-refused, running, incomplete, terminal, failed-port, valid-distinct id-mismatched, empty, and malformed candidates")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val missing = _job_id("missing")
        val corrupt = _job_id("corrupt")
        val refused = _job_id("refused")
        val running = _job_id("running")
        val incomplete = _job_id("incomplete")
        val terminal = _job_id("terminal")
        val failedport = _job_id("failed-port")
        val mismatch = _job_id("mismatch")
        val empty = _job_id("empty")
        val malformed = _job_id("malformed")
        val corruptsnapshot = _create(fixture.store, _record(corrupt))
        val corruptentityid = _success(DurableJobStoreEntity.entityId(corrupt))
        _success(fixture.entitystore.updateDetached(
          DurableJobStoreEntity(corruptentityid, corrupt, "not-json"),
          Some(corruptsnapshot.providerrevision),
          _observed_policy(corruptsnapshot.providerrevision)
        ))
        _create(fixture.store, _record(refused))
        _create(fixture.store, _record(
          running,
          status = DurableJobLifecycleStatus.Running,
          startedAt = Some(_instant.plusSeconds(1L))
        ))
        _create(fixture.store, _record(incomplete))
        _create(fixture.store, _record(terminal, status = DurableJobLifecycleStatus.Succeeded))
        _create(fixture.store, _record(failedport))
        _create(fixture.store, _record(mismatch))
        _create(fixture.store, _record(empty))
        _create(fixture.store, _record(malformed))
        val runs = new AtomicInteger(0)
        val source = _source(Vector(
          _candidate(0L, missing),
          _candidate(1L, corrupt),
          _candidate(2L, refused, access = _access.copy(subjectId = "subject-b")),
          _candidate(3L, running),
          _candidate(4L, incomplete, evidence = DurableReplayEvidence()),
          _candidate(5L, terminal),
          _candidate(6L, failedport),
          _candidate(7L, mismatch),
          _candidate(8L, empty),
          _candidate(9L, malformed)
        ))
        val port = _port(Map(
          failedport -> Consequence.stateInvalid[DurableJobRuntimeRehydration]("provider reconstruction failed"),
          mismatch -> Consequence.success(
            _runtime(_job_id("anotherruntimejob"), new CountingTask(runs), fixture.context)
          ),
          empty -> Consequence.success(DurableJobRuntimeRehydration(
            _job(empty),
            Nil,
            fixture.context,
            None,
            None
          )),
          malformed -> Consequence.success(null.asInstanceOf[DurableJobRuntimeRehydration])
        ))
        val enginefixture = createManualJobEngine(initialTime = _instant)

        When("runtime recovery consumes the explicit source")
        val report = _success(
          enginefixture.engine.recoverDurableStartup(source, fixture.store, port, 10)
        )

        Then("every case is a closed refusal before runtime insertion, timer registration, queue work, or live execution")
        report.candidates.map(_.fact) shouldBe Vector.fill(10)(DurableJobRuntimeRehydrationFact.Refused)
        port.calls shouldBe Vector(failedport, mismatch, empty, malformed)
        enginefixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
        enginefixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
        enginefixture.timer.pendingCount shouldBe 0
        enginefixture.engine.drainAll() shouldBe 0
        runs.get() shouldBe 0
      }
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _complete_evidence = DurableReplayEvidence(
    idempotency = Some("idempotency-proof"),
    input = Some("input-proof"),
    definition = Some("definition-proof"),
    authorization = Some("authorization-proof"),
    provider = Some("provider-proof"),
    compatibility = Some("compatibility-proof")
  )

  private final case class Fixture(
    store: DurableJobStore,
    entitystore: EntityStore,
    context: ExecutionContext
  )

  private final class TestRecoverySource(
    entries: Vector[DurableJobStartupRecoveryCandidate]
  ) extends DurableJobStartupRecoverySource {
    def candidates(
      maxCandidates: Int
    ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      val _ = maxCandidates
      Consequence.success(entries)
    }
  }

  private final class TestPort(
    entries: Map[String, Consequence[DurableJobRuntimeRehydration]]
  ) extends DurableJobRuntimeRehydrationPort {
    var calls = Vector.empty[String]

    def rehydrate(
      candidate: DurableJobStartupRecoveryCandidate,
      snapshot: DurableJobStoreSnapshot,
      decision: DurableJobRecoveryDecision
    ): Consequence[DurableJobRuntimeRehydration] = {
      val _ = snapshot
      val _ = decision
      calls = calls :+ candidate.jobId
      entries.getOrElse(
        candidate.jobId,
        Consequence.stateInvalid[DurableJobRuntimeRehydration]("unexpected provider reconstruction")
      )
    }
  }

  private final class CountingTask(runs: AtomicInteger) extends JobTask {
    val actionId: ActionId = ActionId("cncf", "runtime", Some(_instant), Some("RUNTIME-REHYDRATION"))

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val _ = runs.incrementAndGet()
      TaskSucceeded(OperationResponse.Scalar("rehydrated"))
    }
  }

  private def _fixture(): Fixture = {
    val context = ExecutionContext.test()
    val entitystore = EntityStore.standard()
    Fixture(new DurableJobStore(entitystore), entitystore, context)
  }

  private def _source(
    candidates: Vector[DurableJobStartupRecoveryCandidate]
  ): TestRecoverySource =
    new TestRecoverySource(candidates)

  private def _port(
    entries: Map[String, Consequence[DurableJobRuntimeRehydration]]
  ): TestPort =
    new TestPort(entries)

  private def _candidate(
    ordinal: Long,
    jobid: String,
    access: DurableJobRecordAccess = _access,
    evidence: DurableReplayEvidence = _complete_evidence
  ): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, jobid, access, evidence)

  private def _runtime(
    jobid: String,
    task: JobTask,
    context: ExecutionContext
  ): DurableJobRuntimeRehydration =
    DurableJobRuntimeRehydration(_job(jobid), List(task), context, None, None)

  private def _create(
    store: DurableJobStore,
    record: DurableJobRecord
  )(using context: ExecutionContext): DurableJobStoreSnapshot =
    _success(store.create(record, _access))

  private def _record(
    jobid: String,
    status: DurableJobLifecycleStatus = DurableJobLifecycleStatus.Submitted,
    scheduledAt: Option[Instant] = Some(_instant),
    startedAt: Option[Instant] = None,
    nextRetryAt: Option[Instant] = None
  ): DurableJobRecord =
    DurableJobRecord.createV2(_body(jobid, status, scheduledAt, startedAt, nextRetryAt))
      .toOption
      .getOrElse(fail("unable to create durable runtime rehydration fixture"))

  private def _body(
    jobid: String,
    status: DurableJobLifecycleStatus,
    scheduledat: Option[Instant],
    startedat: Option[Instant],
    nextretryat: Option[Instant]
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
          recoveryRequired = false
        ),
        schedule = DurableScheduleState(scheduledat, startedat, None)
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

  private def _job_id(entropy: String): String =
    JobId("cncf", "runtime", Some(_instant), Some(entropy)).value

  private def _job(value: String): JobId =
    _success(JobId.parse(value))

  private def _observed_policy(
    revision: org.simplemodeling.model.datatype.EntityRevision
  ): EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy(
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired,
      observedRevision = Some(revision)
    )

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
