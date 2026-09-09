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
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * Executable specification for provider-owned bounded durable startup
 * recovery.  It deliberately exercises a fresh in-memory engine after
 * provider-backed durable creation; recovery observes closed facts only.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobStartupRecoverySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E1, rules:R1,R4,R5,R6,R7,R8, phase:69.1, slice:JM69-03F"
  )
  private val _e2 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E2, rules:R1,R5,R6,R7,R8, phase:69.1, slice:JM69-03F"
  )
  private val _e3 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E3, rules:R5,R6,R7,R8, phase:69.1, slice:JM69-03F"
  )
  private val _e4 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E4, rules:R5,R6,R7,R8, phase:69.1, slice:JM69-03F"
  )
  private val _e5 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E5, rules:R1,R2,R3,R4, phase:69.1, slice:JM69-03F"
  )
  private val _e6 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E6, rules:R1,R2,R3,R4,R5,R6, phase:69.1, slice:JM69-03F"
  )
  private val _e7 = afterWord(
    "in spec:durable-job-startup-recovery-contract, example:E7, rules:R1,R4,R8,R9, phase:69.1, slice:JM69-03F"
  )

  "DurableJobStartupRecovery" should {
    "E1 report a terminal durable fact in a fresh engine without search, scheduling, or task execution" must _e1 {
      "when observing a terminal provider-owned candidate" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R1,R4,R5,R6,R7,R8; E1; one provider-backed terminal durable record and a provider-owned single candidate")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      _create(fixture.store, _record("job-terminal", DurableJobLifecycleStatus.Succeeded))
      val source = _source(Vector(_candidate(0L, "job-terminal")))
      val enginefixture = createManualJobEngine(state = InMemoryJobEngine.State())

      When("a distinct runtime invokes bounded startup recovery")
      val report = _success(
        enginefixture.engine.recoverDurableStartup(source, fixture.store, 1)
      )

      Then("it retains only the terminal fact and creates no runnable engine work")
      report shouldBe DurableJobStartupRecoveryReport(Vector(
        DurableJobStartupRecoveryCandidateReport(
          0L,
          "job-terminal",
          DurableJobStartupRecoveryFact.Decision(DurableJobRecoveryDecision(
            DurableJobRecoveryOutcome.TerminalSucceeded,
            Vector(DurableJobRecoveryReason.TerminalSucceeded),
            Vector.empty
          ))
        )
      ))
      source.calls shouldBe 1
      source.requestedBounds shouldBe Vector(1)
      enginefixture.engine.durableStartupRecoveryReport shouldBe Some(report)
      enginefixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
      enginefixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
      enginefixture.timer.pendingCount shouldBe 0
      enginefixture.engine.drainAll() shouldBe 0
      }
    }

    "E2 classify unstarted submitted and delayed-retry records only with complete evidence" must _e2 {
      "when observing submitted candidates with complete and incomplete evidence" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R1,R5,R6,R7,R8; E2; provider-backed V2 submitted and delayed-retry records with complete opaque evidence, plus an incomplete submitted candidate")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      _create(fixture.store, _record("job-resumable", DurableJobLifecycleStatus.Submitted))
      _create(fixture.store, _record(
        "job-retryable",
        DurableJobLifecycleStatus.Submitted,
        nextRetryAt = Some(_instant.plusSeconds(60L))
      ))
      _create(fixture.store, _record("job-incomplete", DurableJobLifecycleStatus.Submitted))
      val source = _source(Vector(
        _candidate(0L, "job-resumable"),
        _candidate(1L, "job-retryable"),
        _candidate(2L, "job-incomplete", evidence = DurableReplayEvidence())
      ))
      val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

      When("the fresh engine loads and classifies only those provider candidates")
      val report = _success(engine.recoverDurableStartup(source, fixture.store, 3))

      Then("the closed report distinguishes resumable and retryable facts, while incomplete proof remains recovery-required")
      report.candidates.map(_.fact) shouldBe Vector(
        DurableJobStartupRecoveryFact.Decision(DurableJobRecoveryDecision(
          DurableJobRecoveryOutcome.Resumable,
          Vector(DurableJobRecoveryReason.SubmittedWithoutStart),
          Vector.empty
        )),
        DurableJobStartupRecoveryFact.Decision(DurableJobRecoveryDecision(
          DurableJobRecoveryOutcome.Retryable,
          Vector(
            DurableJobRecoveryReason.SubmittedWithoutStart,
            DurableJobRecoveryReason.DelayedRetry
          ),
          Vector.empty
        )),
        DurableJobStartupRecoveryFact.Decision(DurableJobRecoveryDecision(
          DurableJobRecoveryOutcome.RecoveryRequired,
          Vector(DurableJobRecoveryReason.ReplayEvidenceIncomplete),
          Vector(
            DurableReplayEvidenceCategory.Idempotency,
            DurableReplayEvidenceCategory.Input,
            DurableReplayEvidenceCategory.Definition,
            DurableReplayEvidenceCategory.Authorization,
            DurableReplayEvidenceCategory.Provider,
            DurableReplayEvidenceCategory.Compatibility
          )
        ))
      )
      source.calls shouldBe 1
      engine.drainAll() shouldBe 0
      }
    }

    "E3 retain running interruption as recovery-required rather than replaying it" must _e3 {
      "when observing an interrupted running candidate" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R5,R6,R7,R8; E3; a provider-backed running record whose durable execution checkpoint has started")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      _create(fixture.store, _record(
        "job-interrupted",
        DurableJobLifecycleStatus.Running,
        startedAt = Some(_instant.plusSeconds(10L))
      ))
      val source = _source(Vector(_candidate(0L, "job-interrupted")))
      val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

      When("the fresh engine observes the explicit candidate")
      val report = _success(engine.recoverDurableStartup(source, fixture.store, 1))

      Then("the report leaves the interrupted work recovery-required and schedules nothing")
      report.candidates.map(_.fact) shouldBe Vector(
        DurableJobStartupRecoveryFact.Decision(DurableJobRecoveryDecision(
          DurableJobRecoveryOutcome.RecoveryRequired,
          Vector(
            DurableJobRecoveryReason.ActiveLifecycle,
            DurableJobRecoveryReason.ExecutionAlreadyStarted
          ),
          Vector.empty
        ))
      )
      engine.drainAll() shouldBe 0
      }
    }

    "E4 retain missing, corrupt, and refused candidates as distinct closed facts" must _e4 {
      "when observing missing, corrupt, and access-refused candidates" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R5,R6,R7,R8; E4; a missing id, corrupt canonical storage, and a valid record with refused access")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val corrupt = _record("job-corrupt", DurableJobLifecycleStatus.Succeeded)
      val corruptsnapshot = _create(fixture.store, corrupt)
      val corruptentityid = _success(DurableJobStoreEntity.entityId("job-corrupt"))
      _success(fixture.entitystore.updateDetached(
        DurableJobStoreEntity(corruptentityid, "job-corrupt", "not-json"),
        Some(corruptsnapshot.providerrevision),
        _observed_policy(corruptsnapshot.providerrevision)
      ))
      _create(fixture.store, _record("job-refused", DurableJobLifecycleStatus.Succeeded))
      val refusedaccess = _access.copy(subjectId = "subject-b")
      val source = _source(Vector(
        _candidate(0L, "job-missing"),
        _candidate(1L, "job-corrupt"),
        _candidate(2L, "job-refused", access = refusedaccess)
      ))
      val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

      When("the engine loads each provider-supplied id through the durable-store boundary")
      val report = _success(engine.recoverDurableStartup(source, fixture.store, 3))

      Then("no partial record is replayed and each load failure remains distinguishable")
      report.candidates.map(_.fact) shouldBe Vector(
        DurableJobStartupRecoveryFact.Missing,
        DurableJobStartupRecoveryFact.Corrupt,
        DurableJobStartupRecoveryFact.Refused
      )
      engine.drainAll() shouldBe 0
      }
    }

    "E5 refuse invalid source responses before durable-store admission" must _e5 {
      "when receiving invalid bounded provider responses" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R1,R2,R3,R4; E5; provider sources with nonpositive bounds, oversized, blank-id, duplicate-id, and nonmatching-ordinal responses")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val oversized = _source(Vector(
        _candidate(0L, "job-first"),
        _candidate(1L, "job-second")
      ))
      val unordered = _source(Vector(_candidate(1L, "job-out-of-order")))
      val blank = _source(Vector(_candidate(0L, "   ")))
      val duplicate = _source(Vector(
        _candidate(0L, "job-duplicate"),
        _candidate(1L, "job-duplicate")
      ))
      val unused = _source(Vector(_candidate(0L, "job-never-requested")))
      val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

      When("the fresh engine receives invalid bounded discovery responses")
      val oversizedresult = engine.recoverDurableStartup(oversized, fixture.store, 1)
      val oversizedcollection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
      val unorderedresult = engine.recoverDurableStartup(unordered, fixture.store, 1)
      val unorderedcollection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
      val blankresult = engine.recoverDurableStartup(blank, fixture.store, 1)
      val blankcollection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
      val duplicateresult = engine.recoverDurableStartup(duplicate, fixture.store, 2)
      val duplicatecollection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
      val nonpositiveresult = engine.recoverDurableStartup(unused, fixture.store, 0)
      val nonpositivecollection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)

      Then("each response is refused before a report, durable collection registration, or candidate load can be produced")
      oversizedresult shouldBe a[Consequence.Failure[_]]
      unorderedresult shouldBe a[Consequence.Failure[_]]
      blankresult shouldBe a[Consequence.Failure[_]]
      duplicateresult shouldBe a[Consequence.Failure[_]]
      nonpositiveresult shouldBe a[Consequence.Failure[_]]
      oversized.calls shouldBe 1
      unordered.calls shouldBe 1
      blank.calls shouldBe 1
      duplicate.calls shouldBe 1
      unused.calls shouldBe 0
      oversizedcollection shouldBe None
      unorderedcollection shouldBe None
      blankcollection shouldBe None
      duplicatecollection shouldBe None
      nonpositivecollection shouldBe None
      engine.durableStartupRecoveryReport shouldBe None
      }
    }

    "E6 preserve deterministic candidate order and missing facts for bounded fresh-store sequences" must _e6 {
      "when ScalaCheck generates accepted bounded provider candidate sequences" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R1,R2,R3,R4,R5,R6; E6; generated positive bounded unique candidate identifiers and a fresh empty durable store")
      val property = Prop.forAll(_candidate_seeds) { seeds =>
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val candidates = seeds.zipWithIndex.map { case (seed, index) =>
          _candidate(index.toLong, s"job-generated-$seed")
        }
        val source = _source(candidates)
        val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

        When("the fresh engine admits the generated provider sequence")
        val report = _success(engine.recoverDurableStartup(source, fixture.store, candidates.size))

        Then("the report preserves ordinal/id order and contains only missing facts")
        report.candidates.map(_.ordinal) == candidates.map(_.ordinal) &&
          report.candidates.map(_.jobId) == candidates.map(_.jobId) &&
          report.candidates.map(_.fact).forall(_ == DurableJobStartupRecoveryFact.Missing) &&
          source.calls == 1 &&
          engine.drainAll() == 0
      }

      When("ScalaCheck evaluates the bounded deterministic admission property")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)

      Then("every generated accepted sequence remains ordered and missing-only")
      checked.passed shouldBe true
      }
    }

    "E7 leave omitted Ephemeral work outside provider-owned startup recovery" must _e7 {
      "when observing a provider sequence that omits Ephemeral work" in {
      Given("docs/spec/durable-job-startup-recovery-contract.md; R1,R4,R8,R9; E7; a provider source that names only its durable terminal candidate and omits runtime-only Ephemeral work")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      _create(fixture.store, _record("job-durable", DurableJobLifecycleStatus.Succeeded))
      val source = _source(Vector(_candidate(0L, "job-durable")))
      val engine = createManualJobEngine(state = InMemoryJobEngine.State()).engine

      When("the fresh engine consumes the provider-owned candidate sequence")
      val report = _success(engine.recoverDurableStartup(source, fixture.store, 1))

      Then("only the supplied durable id is reported; omitted Ephemeral work enters neither recovery nor execution")
      report.candidates.map(_.jobId) shouldBe Vector("job-durable")
      report.candidates.map(_.jobId) should not contain "ephemeral-runtime-only"
      engine.runtimeState.durableJobs.isEmpty shouldBe true
      engine.runtimeState.runtimeJobs.isEmpty shouldBe true
      engine.drainAll() shouldBe 0
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
  private val _candidate_seeds = Gen.choose(1, 8).flatMap { count =>
    Gen.pick(count, (1L to 64L).toVector).map(_.toVector.sorted)
  }

  private final case class Fixture(
    store: DurableJobStore,
    entitystore: EntityStore,
    context: ExecutionContext
  )

  private final class TestRecoverySource(
    entries: Vector[DurableJobStartupRecoveryCandidate]
  ) extends DurableJobStartupRecoverySource {
    var calls: Int = 0
    var requestedBounds: Vector[Int] = Vector.empty

    def candidates(
      maxCandidates: Int
    ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      calls += 1
      requestedBounds = requestedBounds :+ maxCandidates
      Consequence.success(entries)
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

  private def _candidate(
    ordinal: Long,
    jobid: String,
    access: DurableJobRecordAccess = _access,
    evidence: DurableReplayEvidence = _complete_evidence
  ): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, jobid, access, evidence)

  private def _create(
    store: DurableJobStore,
    record: DurableJobRecord
  )(using context: ExecutionContext): DurableJobStoreSnapshot =
    _success(store.create(record, _access))

  private def _record(
    jobid: String,
    status: DurableJobLifecycleStatus,
    startedAt: Option[Instant] = None,
    nextRetryAt: Option[Instant] = None,
    recoveryRequired: Boolean = false
  ): DurableJobRecord =
    DurableJobRecord.createV2(_body(jobid, status, startedAt, nextRetryAt, recoveryRequired))
      .toOption
      .getOrElse(fail("unable to create durable startup recovery fixture"))

  private def _body(
    jobid: String,
    status: DurableJobLifecycleStatus,
    startedAt: Option[Instant],
    nextRetryAt: Option[Instant],
    recoveryRequired: Boolean
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
          nextRetryAt,
          exhausted = false,
          recoveryRequired = recoveryRequired
        ),
        schedule = DurableScheduleState(Some(_instant), startedAt, None)
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
