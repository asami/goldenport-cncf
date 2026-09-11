package org.goldenport.cncf.job

import java.time.Instant

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
 * Executable specification for package-internal terminal durable facts in a
 * distinct fresh engine process.  No runtime-rehydration port is supplied to
 * this path, and the assertions retain the executable maps as the boundary.
 *
 * @since   Sep.  9, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobTerminalProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-terminal-projection-contract, example:E1, rules:R1,R2,R3,R4,R5, phase:69.1, slice:JM69-03H"
  )
  private val _e2 = afterWord(
    "in spec:durable-job-terminal-projection-contract, example:E2, rules:R1,R2,R3,R4, phase:69.1, slice:JM69-03H"
  )
  private val _e3 = afterWord(
    "in spec:durable-job-terminal-projection-contract, example:E3, rules:R4,R5, phase:69.1, slice:JM69-03H"
  )

  "DurableJobTerminalProjection" should {
    "E1 retain succeeded, failed, and cancelled closed facts in a distinct fresh engine" must _e1 {
      "when the terminal-only recovery path receives three admitted terminal candidates" in {
        Given("docs/spec/durable-job-terminal-projection-contract.md; R1-R5; E1; canonically admitted succeeded, failed, and cancelled records in a separate fresh engine")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val succeeded = _record(_job_id("SUCCEEDED"), DurableJobLifecycleStatus.Succeeded)
        val failed = _record(_job_id("FAILED"), DurableJobLifecycleStatus.Failed)
        val cancelled = _record(_job_id("CANCELLED"), DurableJobLifecycleStatus.Cancelled)
        val succeededsnapshot = _create(fixture.store, succeeded)
        _create(fixture.store, failed)
        _create(fixture.store, cancelled)
        val source = _source(Vector(
          _candidate(0L, succeeded.body.identity.jobId),
          _candidate(1L, failed.body.identity.jobId),
          _candidate(2L, cancelled.body.identity.jobId)
        ))
        val enginefixture = createManualJobEngine(state = InMemoryJobEngine.State())
        val engine = enginefixture.engine

        When("the distinct engine admits, classifies, projects, and registers only terminal facts")
        val report = _success(engine.recoverDurableTerminalFacts(source, fixture.store, 3))
        val succeededid = _job(succeeded.body.identity.jobId)
        val failedid = _job(failed.body.identity.jobId)
        val cancelledid = _job(cancelled.body.identity.jobId)
        val firstquery = engine.query(succeededid)
        val secondquery = engine.query(succeededid)
        val authorizedpolicy = new JobQueryPolicy {
          def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] =
            Consequence.unit
        }
        val managementresult = _success(engine.queryManagementResult(succeededid, authorizedpolicy))
        val succeededtasks = engine.queryTasks(succeededid, offset = 0, limit = 10)
        val failedtasks = engine.queryTasks(failedid, offset = 0, limit = 10)
        val compensated = succeededtasks.flatMap(_.tasks.find(_.transactionOutcome.contains("compensation-committed")))
        val failedcompensated = failedtasks.flatMap(_.tasks.find(_.transactionOutcome.contains("compensation-failed")))
        val failedtask = failedtasks.flatMap(_.tasks.headOption)

        Then("the exact durable snapshot stays internal while the existing status and query facade stays stable without result reconstruction or executable side effects")
        report.candidates.map(_.fact) shouldBe Vector.fill(3)(DurableJobTerminalProjectionFact.Registered)
        source.calls shouldBe 1
        engine.durableTerminalProjectionReport shouldBe Some(report)
        engine.durableRuntimeRehydrationReport shouldBe None
        engine.durableTerminalProjection(succeededid).map(_.snapshot) shouldBe Some(succeededsnapshot)
        engine.durableTerminalProjection(succeededid).map(_.snapshot.record) shouldBe Some(succeeded)
        engine.durableTerminalProjection(failedid).map(_.decision.outcome) shouldBe
          Some(DurableJobRecoveryOutcome.TerminalFailed)
        engine.durableTerminalProjection(cancelledid).map(_.decision.outcome) shouldBe
          Some(DurableJobRecoveryOutcome.TerminalCancelled)
        engine.getStatus(succeededid) shouldBe Some(JobStatus.Succeeded)
        engine.getStatus(failedid) shouldBe Some(JobStatus.Failed)
        engine.getStatus(cancelledid) shouldBe Some(JobStatus.Cancelled)
        firstquery shouldBe secondquery
        firstquery.map(_.resultSummary) shouldBe
          Some(JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok")))
        engine.query(failedid).flatMap(_.result) shouldBe None
        engine.getResult(succeededid) shouldBe None
        managementresult shouldBe Some(JobManagementResult.UnavailableAfterRestart(
          JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok"))
        ))
        succeededtasks.map(_.fetchedCount) shouldBe Some(2)
        succeededtasks.map(_.tasks.exists(task =>
          task.status == JobTaskStatus.Running && task.finishedAt.nonEmpty
        )) shouldBe Some(false)
        succeededtasks.map(_.tasks.forall(task =>
          task.status match {
            case JobTaskStatus.Succeeded =>
              task.finishedAt.nonEmpty && task.result.success &&
                task.transactionOutcome.exists(value =>
                  value == "committed" || value == "compensation-committed"
                )
            case JobTaskStatus.Failed =>
              task.finishedAt.nonEmpty && !task.result.success &&
                task.transactionOutcome.exists(value =>
                  value == "failed" || value == "compensation-failed"
                )
            case JobTaskStatus.Running => false
          }
        )) shouldBe Some(true)
        failedtasks.map(_.tasks.exists(task =>
          task.status == JobTaskStatus.Failed && task.transactionOutcome.contains("failed")
        )) shouldBe Some(true)
        compensated.map(_.status) shouldBe Some(JobTaskStatus.Succeeded)
        compensated.flatMap(_.compensationActionRef) shouldBe Some("compensate-operation")
        compensated.flatMap(_.compensationActionRef) should not be Some("lossy-action")
        failedcompensated.map(_.status) shouldBe Some(JobTaskStatus.Failed)
        failedcompensated.flatMap(_.compensationActionRef) shouldBe Some("compensate-failed-operation")
        failedcompensated.flatMap(_.compensationStatus) shouldBe Some("failed")
        compensated.flatMap(task => engine.queryTaskDetail(succeededid, task.taskId)).map(_.task) shouldBe compensated
        failedcompensated.flatMap(task => engine.queryTaskDetail(failedid, task.taskId)).map(_.task) shouldBe failedcompensated
        failedtask.flatMap(task => engine.queryTaskDetail(failedid, task.taskId)).map(_.task.status) shouldBe
          Some(JobTaskStatus.Failed)
        engine.queryTimeline(succeededid, offset = 0, limit = 1).map(_.totalCount) shouldBe Some(1)
        engine.queryTaskExecutionTree(succeededid).map(_.jobId) shouldBe Some(succeededid)
        engine.listJobs().map(_.jobId).toSet shouldBe Set(succeededid, failedid, cancelledid)
        engine.runtimeState.durableJobs.isEmpty shouldBe true
        engine.runtimeState.runtimeJobs.isEmpty shouldBe true
        enginefixture.timer.pendingCount shouldBe 0
        engine.drainAll() shouldBe 0
      }
    }

    "E1 retain task order, parent references, and events for an admitted deep terminal hierarchy" must _e1 {
      "when terminal recovery projects a sufficiently deep closed task chain" in {
        Given("docs/spec/durable-job-terminal-projection-contract.md; R1-R5; E1; an admitted terminal durable record with a deep committed task hierarchy")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val deep = _record(
          _job_id("DEEP"),
          DurableJobLifecycleStatus.Succeeded,
          tasks = _deep_tasks(_deep_task_count)
        )
        _create(fixture.store, deep)
        val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

        When("the terminal projection constructs the existing trace-tree facade")
        val report = _success(engine.recoverDurableTerminalFacts(
          _source(Vector(_candidate(0L, deep.body.identity.jobId))),
          fixture.store,
          1
        ))
        val tree = engine.queryTaskExecutionTree(_job(deep.body.identity.jobId))

        Then("the ordered nested facade preserves every admitted task and event without recursive trace construction")
        report.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
        tree.map(_.roots.map(_.events.map(_.kind))) shouldBe Some(Vector(Vector("job.terminal")))
        tree.map(_.roots.head.parentTaskId) shouldBe Some(None)
        var depth = 0
        var current = tree.flatMap(_.roots.headOption)
        while (current.nonEmpty) {
          depth += 1
          current = current.flatMap(_.children.headOption)
        }
        depth shouldBe _deep_task_count
      }
    }

    "E2 refuse missing, corrupt, access-refused, nonterminal, unrepresentable, and identity-colliding candidates" must _e2 {
      "when terminal recovery receives closed failure cases after one fact has already occupied an identity" in {
        Given("docs/spec/durable-job-terminal-projection-contract.md; R1-R4; E2; missing, corrupt, refused, nonterminal, unrepresentable, and registered-identity candidates")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val corrupt = _record(_job_id("CORRUPT"), DurableJobLifecycleStatus.Succeeded)
        val refused = _record(_job_id("REFUSED"), DurableJobLifecycleStatus.Succeeded)
        val nonterminal = _record(_job_id("SUBMITTED"), DurableJobLifecycleStatus.Submitted)
        val pending = _record(
          _job_id("PENDING"),
          DurableJobLifecycleStatus.Succeeded,
          tasks = Vector(_task_descriptor(
            "PENDING",
            None,
            DurableTaskRelationKind.Root,
            DurableTransactionDescriptor(
              DurableTransactionRole.None,
              DurableTransactionScope.None,
              DurableTransactionOutcome.Pending
            )
          ))
        )
        val compensationmissing = _record(
          _job_id("COMPENSATIONMISSING"),
          DurableJobLifecycleStatus.Succeeded,
          tasks = Vector(_task_descriptor(
            "COMPENSATIONMISSING",
            None,
            DurableTaskRelationKind.Root,
            DurableTransactionDescriptor(
              DurableTransactionRole.Own,
              DurableTransactionScope.PerTask,
              DurableTransactionOutcome.Compensated
            )
          ))
        )
        val collision = _record(_job_id("COLLISION"), DurableJobLifecycleStatus.Succeeded)
        val corruptsnapshot = _create(fixture.store, corrupt)
        _create(fixture.store, refused)
        _create(fixture.store, nonterminal)
        _create(fixture.store, pending)
        _create(fixture.store, compensationmissing)
        _create(fixture.store, collision)
        val corruptentityid = _success(DurableJobStoreEntity.entityId(corrupt.body.identity.jobId))
        _success(fixture.entitystore.updateDetached(
          DurableJobStoreEntity(corruptentityid, corrupt.body.identity.jobId, "not-json"),
          Some(corruptsnapshot.providerrevision),
          _observed_policy(corruptsnapshot.providerrevision)
        ))
        val enginefixture = createManualJobEngine(state = InMemoryJobEngine.State())
        val engine = enginefixture.engine
        val collisioncandidate = _candidate(0L, collision.body.identity.jobId)
        _success(engine.recoverDurableTerminalFacts(_source(Vector(collisioncandidate)), fixture.store, 1))
        val classificationfailure = DurableJobRecovery.classify(
          pending.copy(body = pending.body.copy(result = DurableResultOutcome.Pending)),
          _complete_evidence
        )
        val rejected = _source(Vector(
          _candidate(0L, _job_id("MISSING")),
          _candidate(1L, corrupt.body.identity.jobId),
          _candidate(2L, refused.body.identity.jobId, access = _access.copy(subjectId = "subject-b")),
          _candidate(3L, nonterminal.body.identity.jobId),
          _candidate(4L, pending.body.identity.jobId),
          _candidate(5L, compensationmissing.body.identity.jobId)
        ))

        When("the same fresh engine evaluates only the explicitly supplied failure candidates and the occupied identity")
        val refusedreport = _success(engine.recoverDurableTerminalFacts(rejected, fixture.store, 6))
        val collisionreport = _success(
          engine.recoverDurableTerminalFacts(_source(Vector(collisioncandidate)), fixture.store, 1)
        )

        Then("every failure remains a structured closed fact before terminal or executable insertion, timer registration, queue work, or runtime rehydration")
        refusedreport.candidates.map(_.fact) shouldBe Vector(
          DurableJobTerminalProjectionFact.Missing,
          DurableJobTerminalProjectionFact.Corrupt,
          DurableJobTerminalProjectionFact.Refused,
          DurableJobTerminalProjectionFact.Refused,
          DurableJobTerminalProjectionFact.Refused,
          DurableJobTerminalProjectionFact.Refused
        )
        collisionreport.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Refused)
        classificationfailure shouldBe a[Consequence.Failure[_]]
        engine.durableTerminalProjections.map(_.queryReadModel.jobId) shouldBe Vector(_job(collision.body.identity.jobId))
        engine.runtimeState.durableJobs.isEmpty shouldBe true
        engine.runtimeState.runtimeJobs.isEmpty shouldBe true
        enginefixture.timer.pendingCount shouldBe 0
        engine.durableRuntimeRehydrationReport shouldBe None
        engine.drainAll() shouldBe 0
      }
    }

    "E2 refuse durable terminal and runtime registration collisions in both directions" must _e2 {
      "when a durable identity has already registered through the opposite closed recovery path" in {
        Given("docs/spec/durable-job-terminal-projection-contract.md; R2,R4; E2; one terminal-first identity and one runtime-first identity with individually admitted durable revisions")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val terminalfirstid = _job_id("TERMINALFIRST")
        val runtimefirstid = _job_id("RUNTIMEFIRST")
        val terminalfirstsnapshot = _create(
          fixture.store,
          _record(terminalfirstid, DurableJobLifecycleStatus.Succeeded)
        )
        val runtimefirstsnapshot = _create(
          fixture.store,
          _record(runtimefirstid, DurableJobLifecycleStatus.Submitted)
        )
        val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine
        _success(engine.recoverDurableTerminalFacts(
          _source(Vector(_candidate(0L, terminalfirstid))),
          fixture.store,
          1
        ))
        _success(fixture.store.checkpoint(
          terminalfirstsnapshot,
          _record(terminalfirstid, DurableJobLifecycleStatus.Submitted, revision = 2L),
          _access
        ))
        val terminalfirstport = _port(Map(
          terminalfirstid -> Consequence.success(_runtime(terminalfirstid, fixture.context))
        ))

        When("runtime rehydration reaches an existing terminal fact and terminal projection later reaches an existing runtime record")
        val terminalfirstruntime = _success(engine.recoverDurableStartup(
          _source(Vector(_candidate(0L, terminalfirstid))),
          fixture.store,
          terminalfirstport,
          1
        ))
        val runtimefirstport = _port(Map(
          runtimefirstid -> Consequence.success(_runtime(runtimefirstid, fixture.context))
        ))
        val runtimefirstregistration = _success(engine.recoverDurableStartup(
          _source(Vector(_candidate(0L, runtimefirstid))),
          fixture.store,
          runtimefirstport,
          1
        ))
        _success(fixture.store.checkpoint(
          runtimefirstsnapshot,
          _record(runtimefirstid, DurableJobLifecycleStatus.Succeeded, revision = 2L),
          _access
        ))
        val runtimefirstterminal = _success(engine.recoverDurableTerminalFacts(
          _source(Vector(_candidate(0L, runtimefirstid))),
          fixture.store,
          1
        ))

        Then("neither direction overwrites the existing terminal read model or executable record")
        terminalfirstruntime.candidates.map(_.fact) shouldBe
          Vector(DurableJobRuntimeRehydrationFact.Refused)
        terminalfirstport.calls shouldBe Vector(terminalfirstid)
        engine.durableTerminalProjection(_job(terminalfirstid)).nonEmpty shouldBe true
        engine.runtimeState.durableJobs.containsKey(_job(terminalfirstid)) shouldBe false
        runtimefirstregistration.candidates.map(_.fact) shouldBe
          Vector(DurableJobRuntimeRehydrationFact.Registered)
        runtimefirstterminal.candidates.map(_.fact) shouldBe
          Vector(DurableJobTerminalProjectionFact.Refused)
        runtimefirstport.calls shouldBe Vector(runtimefirstid)
        engine.durableTerminalProjection(_job(runtimefirstid)) shouldBe None
        engine.runtimeState.durableJobs.containsKey(_job(runtimefirstid)) shouldBe true
      }
    }

    "E3 expose no terminal fact from a fresh ephemeral engine" must _e3 {
      "when no terminal-only recovery has completed in that process" in {
        Given("docs/spec/durable-job-terminal-projection-contract.md; R4-R5; E3; a newly created in-memory engine with no durable recovery result")
        val enginefixture = createManualJobEngine(state = InMemoryJobEngine.State())
        val engine = enginefixture.engine
        val absent = JobId("cncf", "terminal", Some(_instant), Some("ABSENT"))

        When("the existing read-only facade is queried before terminal registration")
        val status = engine.getStatus(absent)
        val query = engine.query(absent)

        Then("the process-local terminal map and executable maps remain empty")
        engine.durableTerminalProjections shouldBe Vector.empty
        engine.durableTerminalProjectionReport shouldBe None
        status shouldBe None
        query shouldBe None
        engine.listJobs() shouldBe Vector.empty
        engine.runtimeState.durableJobs.isEmpty shouldBe true
        engine.runtimeState.runtimeJobs.isEmpty shouldBe true
        enginefixture.timer.pendingCount shouldBe 0
        engine.drainAll() shouldBe 0
      }
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _deep_task_count = 2048
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
    var calls: Int = 0

    def candidates(
      maxCandidates: Int
    ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      val _ = maxCandidates
      calls += 1
      Consequence.success(entries)
    }
  }

  private final class TestRuntimePort(
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
        Consequence.stateInvalid[DurableJobRuntimeRehydration]("unexpected runtime rehydration")
      )
    }
  }

  private final class TestTask extends JobTask {
    val actionId: ActionId = ActionId("cncf", "terminal", Some(_instant), Some("RUNTIME"))

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
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
  ): TestRuntimePort =
    new TestRuntimePort(entries)

  private def _candidate(
    ordinal: Long,
    jobid: String,
    access: DurableJobRecordAccess = _access,
    evidence: DurableReplayEvidence = _complete_evidence
  ): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, jobid, access, evidence)

  private def _runtime(
    jobid: String,
    context: ExecutionContext
  ): DurableJobRuntimeRehydration =
    DurableJobRuntimeRehydration(_job(jobid), List(new TestTask), context, None, None)

  private def _create(
    store: DurableJobStore,
    record: DurableJobRecord
  )(using context: ExecutionContext): DurableJobStoreSnapshot =
    _success(store.create(record, _access))

  private def _record(
    jobid: String,
    status: DurableJobLifecycleStatus,
    tasks: Vector[DurableTaskDescriptor] = Vector.empty,
    revision: Long = 1L
  ): DurableJobRecord =
    DurableJobRecord.createV2(_body(jobid, status, tasks, revision))
      .toOption
      .getOrElse(fail("unable to create durable terminal projection fixture"))

  private def _body(
    jobid: String,
    status: DurableJobLifecycleStatus,
    tasks: Vector[DurableTaskDescriptor],
    revision: Long
  ): DurableJobRecordBody = {
    val taskdescriptors = if (tasks.nonEmpty) tasks else _terminal_tasks(status)
    val terminal = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
      case DurableJobLifecycleStatus.Failed =>
        DurableResultOutcome.Failed(
          DurableFailureSummary("execution", "failed", "failed", retryable = false)
        )
      case DurableJobLifecycleStatus.Cancelled =>
        DurableResultOutcome.Cancelled(Some(
          DurableFailureSummary("control", "cancelled", "cancelled", retryable = false)
        ))
      case DurableJobLifecycleStatus.Submitted |
          DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended => DurableResultOutcome.Pending
    }
    DurableJobRecordBody(
      identity = DurableJobIdentity(jobid, revision, _instant, _instant.plusSeconds(20L)),
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
          None,
          exhausted = false,
          recoveryRequired = false
        ),
        schedule = DurableScheduleState(
          Some(_instant),
          Option.when(status != DurableJobLifecycleStatus.Submitted)(_instant.plusSeconds(1L)),
          Option.when(status != DurableJobLifecycleStatus.Submitted)(_instant.plusSeconds(2L))
        )
      ),
      tasks = taskdescriptors,
      inputs = Vector.empty,
      result = terminal,
      timeline = Vector(DurableTimelineEvent(
        1L,
        _instant.plusSeconds(2L),
        "job.terminal",
        Some(taskdescriptors.head.taskId),
        Some(status.wire)
      )),
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

  private def _terminal_tasks(
    status: DurableJobLifecycleStatus
  ): Vector[DurableTaskDescriptor] =
    status match {
      case DurableJobLifecycleStatus.Succeeded =>
        val root = _task_descriptor(
          "ROOT",
          None,
          DurableTaskRelationKind.Root,
          DurableTransactionDescriptor(
            DurableTransactionRole.Own,
            DurableTransactionScope.PerTask,
            DurableTransactionOutcome.Committed
          )
        )
        val compensated = _task_descriptor(
          "COMPENSATED",
          Some(root.taskId),
          DurableTaskRelationKind.Compensation,
          DurableTransactionDescriptor(
            DurableTransactionRole.Own,
            DurableTransactionScope.PerTask,
            DurableTransactionOutcome.Compensated
          ),
          Some(DurableCompensationDescriptor(
            DurableOperationReference(
              "component-a",
              Some("service-a"),
              "compensate-operation",
              Some("lossy-action")
            ),
            root.taskId,
            DurableCompensationStatus.Succeeded,
            None
          ))
        )
        Vector(root, compensated)
      case DurableJobLifecycleStatus.Failed =>
        val root = _task_descriptor(
          "FAILED",
          None,
          DurableTaskRelationKind.Root,
          DurableTransactionDescriptor(
            DurableTransactionRole.Own,
            DurableTransactionScope.PerTask,
            DurableTransactionOutcome.Failed
          )
        )
        val compensated = _task_descriptor(
          "COMPENSATIONFAILED",
          Some(root.taskId),
          DurableTaskRelationKind.Compensation,
          DurableTransactionDescriptor(
            DurableTransactionRole.Own,
            DurableTransactionScope.PerTask,
            DurableTransactionOutcome.Compensated
          ),
          Some(DurableCompensationDescriptor(
            DurableOperationReference(
              "component-a",
              Some("service-a"),
              "compensate-failed-operation",
              Some("lossy-failed-action")
            ),
            root.taskId,
            DurableCompensationStatus.Failed,
            Some(DurableFailureSummary("compensation", "failed", "compensation failed", retryable = false))
          ))
        )
        Vector(root, compensated)
      case DurableJobLifecycleStatus.Cancelled =>
        Vector(_task_descriptor(
          "CANCELLED",
          None,
          DurableTaskRelationKind.Root,
          DurableTransactionDescriptor(
            DurableTransactionRole.Own,
            DurableTransactionScope.PerTask,
            DurableTransactionOutcome.Committed
          )
        ))
      case DurableJobLifecycleStatus.Submitted |
          DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended =>
        Vector(_task_descriptor(
          "ROOT",
          None,
          DurableTaskRelationKind.Root,
          DurableTransactionDescriptor(
            DurableTransactionRole.None,
            DurableTransactionScope.None,
            DurableTransactionOutcome.Pending
          )
        ))
    }

  private def _task_descriptor(
    entropy: String,
    parenttaskid: Option[String],
    relationkind: DurableTaskRelationKind,
    transaction: DurableTransactionDescriptor,
    compensation: Option[DurableCompensationDescriptor] = None
  ): DurableTaskDescriptor =
    DurableTaskDescriptor(
      _task_id(entropy),
      parenttaskid,
      DurableTaskKind.Operation,
      DurableTaskTarget(
        "operation",
        DurableOperationReference("component-a", Some("service-a"), "run", None)
      ),
      DurableTaskRelation(relationkind, parenttaskid),
      transaction,
      compensation
    )

  private def _deep_tasks(
    count: Int
  ): Vector[DurableTaskDescriptor] =
    (0 until count).toVector.map { index =>
      val entropy = f"DEEP$index%04d"
      val parenttaskid = Option.when(index > 0)(f"DEEP${index - 1}%04d").map(_task_id)
      _task_descriptor(
        entropy,
        parenttaskid,
        if (index == 0) DurableTaskRelationKind.Root else DurableTaskRelationKind.Child,
        DurableTransactionDescriptor(
          DurableTransactionRole.Own,
          DurableTransactionScope.PerTask,
          DurableTransactionOutcome.Committed
        )
      )
    }

  private def _job_id(entropy: String): String =
    JobId("cncf", "terminal", Some(_instant), Some(entropy)).value

  private def _task_id(entropy: String): String =
    TaskId("cncf", "terminal", Some(_instant), Some(entropy)).value

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
