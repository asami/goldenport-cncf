package org.goldenport.cncf.job

import java.time.Instant

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the pure durable recovery classification.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobRecoverySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "DurableJobRecovery" should {
    "reconstruct terminal facts without synthesizing a live result" in {
      Given("authorized V2 durable records for each terminal lifecycle")
      val succeeded = _record(DurableJobLifecycleStatus.Succeeded)
      val failed = _record(DurableJobLifecycleStatus.Failed)
      val cancelled = _record(DurableJobLifecycleStatus.Cancelled)

      When("the pure recovery classifier receives complete replay evidence")
      val succeededdecision = DurableJobRecovery.classify(succeeded, _complete_evidence).toOption.get
      val faileddecision = DurableJobRecovery.classify(failed, _complete_evidence).toOption.get
      val cancelleddecision = DurableJobRecovery.classify(cancelled, _complete_evidence).toOption.get

      Then("it returns only terminal facts, never a live JobResult or result body")
      succeededdecision shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.TerminalSucceeded,
        Vector(DurableJobRecoveryReason.TerminalSucceeded),
        Vector.empty
      )
      faileddecision shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.TerminalFailed,
        Vector(DurableJobRecoveryReason.TerminalFailed),
        Vector.empty
      )
      cancelleddecision shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.TerminalCancelled,
        Vector(DurableJobRecoveryReason.TerminalCancelled),
        Vector.empty
      )
    }

    "classify unstarted submitted work as resumable only with all six proofs" in {
      Given("an authorized V2 submitted record with a scheduled but unstarted execution")
      val record = _record(DurableJobLifecycleStatus.Submitted)

      When("all six opaque replay-proof tokens are present")
      val result = DurableJobRecovery.classify(record, _complete_evidence)

      Then("the record is resumable with no failed proof category")
      result shouldBe a[Consequence.Success[_]]
      result.toOption.get shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.Resumable,
        Vector(DurableJobRecoveryReason.SubmittedWithoutStart),
        Vector.empty
      )
    }

    "classify an unstarted submitted delayed retry as retryable only with all six proofs" in {
      Given("an authorized V2 submitted record with an unstarted delayed-retry checkpoint")
      val record = _record(
        DurableJobLifecycleStatus.Submitted,
        nextRetryAt = Some(_instant.plusSeconds(60L))
      )

      When("all six opaque replay-proof tokens are present")
      val result = DurableJobRecovery.classify(record, _complete_evidence)

      Then("the classifier returns a retryable fact without inspecting a clock or dispatching it")
      result.toOption.get shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.Retryable,
        Vector(
          DurableJobRecoveryReason.SubmittedWithoutStart,
          DurableJobRecoveryReason.DelayedRetry
        ),
        Vector.empty
      )
    }

    "refuse otherwise eligible submitted work when all six replay proofs are missing" in {
      Given("an authorized unstarted submitted record and no replay evidence")
      val record = _record(DurableJobLifecycleStatus.Submitted)

      When("the classifier receives the six-category empty evidence value")
      val result = DurableJobRecovery.classify(record, DurableReplayEvidence())

      Then("it requires recovery and retains the exact ordered missing categories")
      result.toOption.get shouldBe DurableJobRecoveryDecision(
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
      )
    }

    "retain the exact blank proof category for otherwise eligible submitted work" in {
      Given("an authorized unstarted submitted record with one blank proof token")
      val record = _record(DurableJobLifecycleStatus.Submitted)
      val evidence = _complete_evidence.copy(idempotency = Some("  "))

      When("the classifier assesses the evidence")
      val result = DurableJobRecovery.classify(record, evidence)

      Then("it refuses automatic replay and names only the blank category")
      result.toOption.get shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.RecoveryRequired,
        Vector(DurableJobRecoveryReason.ReplayEvidenceIncomplete),
        Vector(DurableReplayEvidenceCategory.Idempotency)
      )
    }

    "refuse a proof-complete running interruption" in {
      Given("an authorized V2 running record whose execution checkpoint has started")
      val record = _record(
        DurableJobLifecycleStatus.Running,
        startedAt = Some(_instant.plusSeconds(10L))
      )

      When("the classifier receives complete replay evidence")
      val result = DurableJobRecovery.classify(record, _complete_evidence)

      Then("it requires recovery rather than automatically replaying possibly active work")
      result.toOption.get shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.RecoveryRequired,
        Vector(
          DurableJobRecoveryReason.ActiveLifecycle,
          DurableJobRecoveryReason.ExecutionAlreadyStarted
        ),
        Vector.empty
      )
    }

    "refuse an unstarted submitted record marked recovery-required" in {
      Given("an authorized V2 submitted record carrying a recovery-required checkpoint")
      val record = _record(
        DurableJobLifecycleStatus.Submitted,
        recoveryRequired = true
      )

      When("the classifier receives complete replay evidence")
      val result = DurableJobRecovery.classify(record, _complete_evidence)

      Then("the durable checkpoint takes precedence and no automatic replay is classified")
      result.toOption.get shouldBe DurableJobRecoveryDecision(
        DurableJobRecoveryOutcome.RecoveryRequired,
        Vector(DurableJobRecoveryReason.RecoveryRequiredFlag),
        Vector.empty
      )
    }

    "refuse structurally impossible lifecycle and result combinations" in {
      Given("an otherwise admitted submitted V2 record")
      val record = _record(DurableJobLifecycleStatus.Submitted)
      val impossible = record.copy(body = record.body.copy(
        result = DurableResultOutcome.Succeeded(DurableValue.Absent)
      ))

      When("the impossible lifecycle/result combination reaches the classifier")
      val result = DurableJobRecovery.classify(impossible, _complete_evidence)

      Then("it returns a Consequence failure instead of inventing a recovery decision")
      result shouldBe a[Consequence.Failure[_]]
    }

    "keep recovery decisions free of live objects and raw payload fields" in {
      Given("a resumable classification from a durable record")
      val decision = DurableJobRecovery.classify(
        _record(DurableJobLifecycleStatus.Submitted),
        _complete_evidence
      ).toOption.get

      When("the closed decision value is inspected")
      val names = decision.productElementNames.toSet

      Then("it carries only outcome, reason, and failed-category values")
      names shouldBe Set("outcome", "reasons", "failedEvidenceCategories")
      names should not contain "jobTask"
      names should not contain "action"
      names should not contain "provider"
      names should not contain "rawPayload"
      names should not contain "rawResultBody"
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64

  private val _complete_evidence = DurableReplayEvidence(
    idempotency = Some("idempotency-proof"),
    input = Some("input-proof"),
    definition = Some("definition-proof"),
    authorization = Some("authorization-proof"),
    provider = Some("provider-proof"),
    compatibility = Some("compatibility-proof")
  )

  private def _record(
    status: DurableJobLifecycleStatus,
    startedAt: Option[Instant] = None,
    nextRetryAt: Option[Instant] = None,
    recoveryRequired: Boolean = false
  ): DurableJobRecord =
    DurableJobRecord.createV2(_body(status, startedAt, nextRetryAt, recoveryRequired))
      .toOption
      .getOrElse(fail("unable to create durable recovery fixture"))

  private def _body(
    status: DurableJobLifecycleStatus,
    startedAt: Option[Instant],
    nextRetryAt: Option[Instant],
    recoveryRequired: Boolean
  ): DurableJobRecordBody = {
    val terminal = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
      case DurableJobLifecycleStatus.Failed =>
        DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", retryable = false))
      case DurableJobLifecycleStatus.Cancelled => DurableResultOutcome.Cancelled(None)
      case DurableJobLifecycleStatus.Submitted |
          DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended => DurableResultOutcome.Pending
    }
    DurableJobRecordBody(
      identity = DurableJobIdentity("job-001", 1L, _instant, _instant.plusSeconds(20L)),
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
}
