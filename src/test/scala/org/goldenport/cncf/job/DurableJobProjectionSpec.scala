package org.goldenport.cncf.job

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the closed live-to-durable job projection and
 * its fail-closed replay assessment boundary.
 *
 * @since   Sep.  9, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "DurableJobProjection" should {
    "project a persistent terminal job from closed evidence without capturing live objects" in {
      Given("a persistent terminal JobRecord with a live task and closed projection evidence")
      val record = _record()
      val evidence = _evidence()

      When("the closed evidence is projected and canonically signed")
      val projected = DurableJobProjection.project(record, evidence)
      val canonical = projected.flatMap(DurableJobRecordCodec.canonicalJson)

      Then("the signed record preserves admitted facts and excludes live task and result bodies")
      projected shouldBe a[Consequence.Success[_]]
      canonical shouldBe a[Consequence.Success[_]]
      canonical.toOption.get should not include "LIVE-ACTION-BODY"
      canonical.toOption.get should not include "LIVE-RESULT-BODY"
      canonical.toOption.get should not include "RAW-INPUT-BODY"
      projected.toOption.get.body.identity.revision shouldBe 7L
      projected.toOption.get.body.tasks.head.target.operation.operationId shouldBe "run"
      projected.toOption.get.body.inputs.head.name shouldBe "payload"
    }

    "reject ephemeral work and cross-wired closed task evidence before returning a record" in {
      Given("one valid closed projection and a second record whose persistence or task evidence is invalid")
      val record = _record()
      val evidence = _evidence()
      val ephemeral = record.copy(persistence = JobPersistencePolicy.Ephemeral)
      val crosswired = evidence.copy(
        tasks = evidence.tasks.map(task =>
          task.copy(target = task.target.copy(
            operation = task.target.operation.copy(operationId = "other-operation")
          )
        )
      )
    )

      When("each invalid variant is projected")
      val ephemeralResult = DurableJobProjection.project(ephemeral, evidence)
      val crosswiredResult = DurableJobProjection.project(record, crosswired)

      Then("both variants fail closed without a partial durable record")
      ephemeralResult shouldBe a[Consequence.Failure[_]]
      crosswiredResult shouldBe a[Consequence.Failure[_]]
    }

    "refuse replay deterministically when all six evidence categories are absent" in {
      Given("a replay assessment with no idempotency, input, definition, authorization, provider, or compatibility evidence")
      val evidence = DurableReplayEvidence()

      When("the replay assessment is requested twice")
      val first = DurableJobProjection.assessReplay(evidence)
      val second = DurableJobProjection.assessReplay(evidence)

      Then("both decisions refuse with the same ordered list of failed categories")
      first shouldBe second
      first shouldBe DurableReplayDecision.Refused(
        Vector(
          DurableReplayEvidenceCategory.Idempotency,
          DurableReplayEvidenceCategory.Input,
          DurableReplayEvidenceCategory.Definition,
          DurableReplayEvidenceCategory.Authorization,
          DurableReplayEvidenceCategory.Provider,
          DurableReplayEvidenceCategory.Compatibility
        ),
        Vector(
          "idempotency:missing",
          "input:missing",
          "definition:missing",
          "authorization:missing",
          "provider:missing",
          "compatibility:missing"
        )
      )
      first.failedCategoryNames shouldBe Vector(
        "idempotency",
        "input",
        "definition",
        "authorization",
        "provider",
        "compatibility"
      )
      first.message should include("idempotency,input,definition,authorization,provider,compatibility")
    }

    "refuse a null replay evidence token without throwing or changing category order" in {
      Given("one typed null idempotency token and nonblank evidence for the other five replay categories")
      val evidence = DurableReplayEvidence(
        idempotency = Some(null: String),
        input = Some("input-proof"),
        definition = Some("definition-proof"),
        authorization = Some("authorization-proof"),
        provider = Some("provider-proof"),
        compatibility = Some("compatibility-proof")
      )

      When("the replay evidence is assessed")
      val decision = DurableJobProjection.assessReplay(evidence)

      Then("the policy returns the single deterministic fail-closed refusal")
      decision shouldBe DurableReplayDecision.Refused(
        Vector(DurableReplayEvidenceCategory.Idempotency),
        Vector("idempotency:missing")
      )
    }

    "allow replay assessment only when every category has explicit nonblank evidence" in {
      Given("one opaque nonblank proof token for each of the six replay evidence categories")
      val evidence = DurableReplayEvidence(
        idempotency = Some("idempotency-proof"),
        input = Some("input-proof"),
        definition = Some("definition-proof"),
        authorization = Some("authorization-proof"),
        provider = Some("provider-proof"),
        compatibility = Some("compatibility-proof")
      )

      When("the replay evidence is assessed")
      val decision = DurableJobProjection.assessReplay(evidence)

      Then("the policy returns an allowed assessment without resolving or executing anything")
      decision shouldBe DurableReplayDecision.Allowed
      decision.failedCategoryNames shouldBe empty
    }

    "project a scheduled submitted job as a v2 pending record without live task read-models" in {
      Given("a persistent submitted job with no task read-models and nonempty closed task descriptors")
      val record = _record().copy(
        status = JobStatus.Submitted,
        result = None,
        scheduledStartAt = Some(_instant.plusSeconds(30L)),
        updatedAt = _instant,
        taskReadModels = Vector.empty,
        timeline = Vector(JobTimelineEvent(1L, _instant, "job.submitted", None, None, None)),
        retry = JobRetryState(maxAttempts = 3)
      )
      val evidence = _evidence().copy(
        result = DurableResultOutcome.Pending,
        retry = DurableRetryEvidence(Vector.empty, 3, None, false, false)
      )

      When("the explicit v2 projection is requested")
      val projected = DurableJobProjection.projectV2(record, evidence)
      val canonical = projected.flatMap(DurableJobRecordCodec.canonicalJson)

      Then("the pending record retains only the schedule and closed task descriptors")
      projected shouldBe a[Consequence.Success[_]]
      canonical shouldBe a[Consequence.Success[_]]
      canonical.toOption.get should not include "LIVE-ACTION-BODY"
      canonical.toOption.get should not include "LIVE-RESULT-BODY"
      projected.toOption.get.format shouldBe DurableRecordFormat.V2
      projected.toOption.get.body.result shouldBe DurableResultOutcome.Pending
      projected.toOption.get.body.tasks shouldBe evidence.tasks
      projected.toOption.get.body.lifecycle.schedule shouldBe DurableScheduleState(
        Some(_instant.plusSeconds(30L)),
        None,
        None
      )
    }

    "require exact closed descriptor correspondence for a live Running task model" in {
      Given("a persistent running job with one structurally valid running task and its exact closed descriptor")
      val task = _record().taskReadModels.head.copy(
        status = JobTaskStatus.Running,
        startedAt = _instant.plusSeconds(5L),
        finishedAt = None,
        result = JobTaskResultSummary(success = true, message = Some("running")),
        transactionOutcome = Some("running")
      )
      val record = _record().copy(
        status = JobStatus.Running,
        result = None,
        updatedAt = _instant.plusSeconds(10L),
        taskReadModels = Vector(task),
        timeline = Vector(
          JobTimelineEvent(1L, _instant, "job.submitted", None, None, None),
          JobTimelineEvent(2L, _instant.plusSeconds(5L), "task.running", Some(task.taskId), None, None)
        ),
        retry = JobRetryState(maxAttempts = 3)
      )
      val evidence = _evidence().copy(
        result = DurableResultOutcome.Pending,
        retry = DurableRetryEvidence(Vector.empty, 3, None, false, false),
        tasks = _evidence().tasks.map(task =>
          task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Pending))
        )
      )

      When("the exact and a structurally mismatched descriptor are projected")
      val projected = DurableJobProjection.projectV2(record, evidence)
      val mismatchedDescriptor = DurableJobProjection.projectV2(
        record,
        evidence.copy(tasks = evidence.tasks.map(task =>
          task.copy(target = task.target.copy(
            operation = task.target.operation.copy(operationId = "other-operation")
          ))
        ))
      )

      Then("only the exact descriptor produces the pending record with its started time")
      projected shouldBe a[Consequence.Success[_]]
      mismatchedDescriptor shouldBe a[Consequence.Failure[_]]
      projected.toOption.get.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
      projected.toOption.get.body.lifecycle.schedule shouldBe DurableScheduleState(
        Some(_instant),
        Some(_instant.plusSeconds(5L)),
        None
      )
      val contradictoryTask = task.copy(transactionOutcome = Some("committed"))
      val contradictoryEvidence = evidence.copy(tasks = evidence.tasks.map(task =>
        task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Committed))
      ))
      DurableJobProjection.projectV2(
        record.copy(taskReadModels = Vector(contradictoryTask)),
        contradictoryEvidence
      ) shouldBe a[Consequence.Failure[_]]
    }

    "permit only nonempty closed descriptors for a Running v2 record without live task models" in {
      Given("a persistent Running job with no live task models and nonempty closed pending descriptors")
      val record = _record().copy(
        status = JobStatus.Running,
        result = None,
        updatedAt = _instant.plusMillis(1L),
        taskReadModels = Vector.empty,
        timeline = Vector(
          JobTimelineEvent(1L, _instant, "job.submitted", None, None, None),
          JobTimelineEvent(2L, _instant.plusMillis(1L), "job.durable-running-intent", None, None, None)
        ),
        retry = JobRetryState(maxAttempts = 3)
      )
      val evidence = _evidence().copy(
        result = DurableResultOutcome.Pending,
        retry = DurableRetryEvidence(Vector.empty, 3, None, false, false),
        tasks = _evidence().tasks.map(task =>
          task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Pending))
        )
      )

      When("the Running candidate is projected with nonempty and empty closed descriptor forms")
      val permitted = DurableJobProjection.projectV2(record, evidence)
      val refused = DurableJobProjection.projectV2(record, evidence.copy(tasks = Vector.empty))

      Then("only the nonempty closed descriptor form produces the Running v2 record")
      permitted shouldBe a[Consequence.Success[_]]
      permitted.toOption.get.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
      permitted.toOption.get.body.tasks shouldBe evidence.tasks
      refused shouldBe a[Consequence.Failure[_]]
    }

    "refuse v2 status/result and task-evidence mismatches before signing" in {
      Given("a scheduled submitted job with one pending result and one closed descriptor")
      val record = _record().copy(
        status = JobStatus.Submitted,
        result = None,
        scheduledStartAt = Some(_instant),
        updatedAt = _instant,
        taskReadModels = Vector.empty,
        timeline = Vector(JobTimelineEvent(1L, _instant, "job.submitted", None, None, None)),
        retry = JobRetryState(maxAttempts = 3)
      )
      val evidence = _evidence().copy(
        result = DurableResultOutcome.Pending,
        retry = DurableRetryEvidence(Vector.empty, 3, None, false, false)
      )

      When("a lifecycle/result mismatch and an empty descriptor vector are projected")
      val mismatchedResult = DurableJobProjection.projectV2(
        record,
        evidence.copy(result = DurableResultOutcome.Succeeded(DurableValue.Absent))
      )
      val mismatchedTaskEvidence = DurableJobProjection.projectV2(record, evidence.copy(tasks = Vector.empty))

      Then("neither mismatch produces a partial v2 record")
      mismatchedResult shouldBe a[Consequence.Failure[_]]
      mismatchedTaskEvidence shouldBe a[Consequence.Failure[_]]
    }

    "project a v2 succeeded retry history with a closed compensation model but refuse a missing non-compensation durable pair" in {
      Given("a succeeded persistent retry history with failed and successful bridge-eligible attempts plus a closed local compensation model")
      val failedid = TaskId("cncf", "task", Some(_instant), Some("task-failed"))
      val retryid = TaskId("cncf", "task", Some(_instant), Some("task-retry"))
      val compensationid = TaskId("cncf", "task", Some(_instant), Some("task-compensation"))
      val base = _record().taskReadModels.head
      val failed = base.copy(
        taskId = failedid,
        status = JobTaskStatus.Failed,
        startedAt = _instant.plusSeconds(5L),
        finishedAt = Some(_instant.plusSeconds(10L)),
        result = JobTaskResultSummary(success = false, message = Some("failed")),
        transactionOutcome = Some("failed")
      )
      val retried = base.copy(
        taskId = retryid,
        startedAt = _instant.plusSeconds(15L),
        finishedAt = Some(_instant.plusSeconds(20L)),
        transactionOutcome = Some("committed")
      )
      val compensation = base.copy(
        taskId = compensationid,
        parentTaskId = Some(retryid),
        startedAt = _instant.plusSeconds(21L),
        finishedAt = Some(_instant.plusSeconds(25L)),
        relation = Some("compensation"),
        transactionOutcome = Some("compensation-committed"),
        compensationActionRef = Some("compensate"),
        compensatesTaskId = Some(retryid),
        compensationStatus = Some("succeeded")
      )
      val descriptor = _evidence().tasks.head
      val compensationdescriptor = descriptor.copy(
        taskId = compensationid.value,
        parentTaskId = Some(retryid.value),
        relation = DurableTaskRelation(DurableTaskRelationKind.Compensation, Some(retryid.value)),
        transaction = descriptor.transaction.copy(outcome = DurableTransactionOutcome.Compensated),
        compensation = Some(DurableCompensationDescriptor(
          DurableOperationReference("component-a", Some("service-a"), "compensate", None),
          retryid.value,
          DurableCompensationStatus.Succeeded,
          None
        ))
      )
      val record = _record().copy(
        updatedAt = _instant.plusSeconds(30L),
        taskReadModels = Vector(failed, retried, compensation),
        timeline = Vector(
          JobTimelineEvent(1L, _instant, "job.submitted", None, None, None),
          JobTimelineEvent(2L, _instant.plusSeconds(5L), "task.durable-start-intent", Some(failedid), None, None),
          JobTimelineEvent(3L, _instant.plusSeconds(10L), "task.durable-outcome-checkpoint", Some(failedid), None, None),
          JobTimelineEvent(4L, _instant.plusSeconds(15L), "task.durable-start-intent", Some(retryid), None, None),
          JobTimelineEvent(5L, _instant.plusSeconds(20L), "task.durable-outcome-checkpoint", Some(retryid), None, None),
          JobTimelineEvent(6L, _instant.plusSeconds(30L), "job.succeeded", None, None, None)
        ),
        retry = JobRetryState(attemptCount = 1, maxAttempts = 3)
      )
      val evidence = _evidence().copy(
        tasks = Vector(
          descriptor.copy(
            taskId = failedid.value,
            transaction = descriptor.transaction.copy(outcome = DurableTransactionOutcome.Failed)
          ),
          descriptor.copy(taskId = retryid.value),
          compensationdescriptor
        ),
        retry = DurableRetryEvidence(
          Vector(
            DurableAttemptEvidence(1, _instant.plusSeconds(5L), Some(_instant.plusSeconds(10L)), DurableAttemptOutcome.Failed, Some(DurableFailureSummary("execution", "failed", "failed", retryable = true))),
            DurableAttemptEvidence(2, _instant.plusSeconds(15L), Some(_instant.plusSeconds(20L)), DurableAttemptOutcome.Succeeded, None)
          ),
          3,
          None,
          exhausted = false,
          recoveryRequired = false
        )
      )

      When("the v2 terminal retry history is projected with and without the successful non-compensation outcome pair")
      val projected = DurableJobProjection.projectV2(record, evidence)
      val missingnoncompensationpair = DurableJobProjection.projectV2(
        record.copy(timeline = record.timeline.filterNot(event =>
          event.taskId.contains(retryid) && event.kind == "task.durable-outcome-checkpoint"
        )),
        evidence
      )

      Then("the compensation remains represented and closed without a bridge pair while every non-compensation model still requires its exact pair")
      projected shouldBe a[Consequence.Success[_]]
      projected.toOption.get.body.tasks.map(_.taskId) shouldBe Vector(
        failedid.value,
        retryid.value,
        compensationid.value
      )
      projected.toOption.get.body.tasks.last.relation.kind shouldBe DurableTaskRelationKind.Compensation
      missingnoncompensationpair shouldBe a[Consequence.Failure[_]]
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64

  private def _record(): JobRecord = {
    val taskid = TaskId("cncf", "task", Some(_instant), Some("task-001"))
    val task = JobTaskReadModel(
      taskId = taskid,
      parentTaskId = None,
      status = JobTaskStatus.Succeeded,
      startedAt = _instant.plusSeconds(5L),
      finishedAt = Some(_instant.plusSeconds(20L)),
      result = JobTaskResultSummary(success = true, message = Some("completed")),
      component = Some("component-a"),
      service = Some("service-a"),
      operation = Some("run"),
      taskKind = "operation",
      targetKind = Some("operation"),
      relation = Some("root"),
      transactionRole = Some("own"),
      transactionScope = Some("per-task"),
      transactionOutcome = Some("committed")
    )
    JobRecord(
      id = JobId("cncf", "job", Some(_instant), Some("job-001")),
      tasks = List(new LiveTask),
      submittedContext = ExecutionContext.test(),
      status = JobStatus.Succeeded,
      result = Some(JobResult.Success(OperationResponse.Scalar("LIVE-RESULT-BODY"))),
      persistence = JobPersistencePolicy.Persistent,
      runMode = JobRunMode.Async,
      priority = 7,
      scheduledStartAt = Some(_instant),
      createdAt = _instant,
      updatedAt = _instant.plusSeconds(20L),
      taskReadModels = Vector(task),
      timeline = Vector(
        JobTimelineEvent(1L, _instant, "job.submitted", None, None, None),
        JobTimelineEvent(2L, _instant.plusSeconds(20L), "job.succeeded", None, None, None)
      ),
      debug = JobDebugInfo(
        requestSummary = Some("live summary"),
        parameters = Map(
          "jcl.jobDefinition.id" -> "definition-001",
          "jcl.jobDefinition.key" -> "daily-job",
          "jcl.jobDefinition.version" -> "1",
          "jcl.jobDefinition.revision" -> "2",
          "jcl.jobDefinition.hash" -> _digest
        ),
        executionNotes = Vector.empty,
        jobDefinitionSnapshot = Some(JobDefinitionSnapshot(
          "definition-001",
          "daily-job",
          1,
          2,
          _digest,
          None,
          None,
          Some("jcl")
        )),
      ),
      input = Some(JobInput(
        payloads = Vector(JobInputPayload(
          storage = "blob",
          fieldName = Some("payload"),
          filename = None,
          contentType = Some("application/json"),
          byteSize = Some(42L),
          sha256 = Some(_digest),
          inlineBase64 = Some("RAW-INPUT-BODY"),
          blobId = Some("blob-001"),
          createdAt = _instant
        )),
        retentionPolicy = JobInputRetentionPolicy.Keep,
        createdAt = _instant
      )
    )
    )
  }

  private def _evidence(): DurableJobProjectionEvidence = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", None)
    DurableJobProjectionEvidence(
      semanticRevision = 7L,
      authorization = DurableJobAuthorization(
        "tenant-a",
        DurableSubject("subject-a", "user"),
        DurableVisibility.Subject,
        Set("job.read")
      ),
      tasks = Vector(DurableTaskDescriptor(
        taskId = TaskId("cncf", "task", Some(_instant), Some("task-001")).value,
        parentTaskId = None,
        kind = DurableTaskKind.Operation,
        target = DurableTaskTarget("operation", operation),
        relation = DurableTaskRelation(DurableTaskRelationKind.Root, None),
        transaction = DurableTransactionDescriptor(
          DurableTransactionRole.Own,
          DurableTransactionScope.PerTask,
          DurableTransactionOutcome.Committed
        ),
        compensation = None
      )),
      inputs = Vector(DurableInputReference(
        "payload",
        DurableValue.External(DurableExternalReference(
          "blob://input-001",
          "blob",
          Some("application/json"),
          42L,
          _digest
        ))
      )),
      result = DurableResultOutcome.Succeeded(DurableValue.External(
        DurableExternalReference("blob://result-001", "blob", Some("application/json"), 84L, _digest)
      )),
      retry = DurableRetryEvidence(
        attempts = Vector(DurableAttemptEvidence(
          1,
          _instant.plusSeconds(5L),
          Some(_instant.plusSeconds(20L)),
          DurableAttemptOutcome.Succeeded,
          None
        )),
        maxAttempts = 3,
        nextRetryAt = None,
        exhausted = false,
        recoveryRequired = false
      ),
      diagnostics = Vector(DurableDiagnosticSummary("execution", "ok", "info", "completed")),
      calltreeReference = Some(DurableExternalReference(
        "calltree://job-001",
        "calltree",
        Some("application/json"),
        64L,
        _digest
      )),
      definitionSnapshot = DurableDefinitionSnapshot(
        "definition-001",
        "daily-job",
        1,
        2L,
        _digest,
        None,
        Some("jcl"),
        Map("environment" -> "production")
      ),
      retention = DurableRetentionState(
        Some(_instant.plusSeconds(86400L)),
        None,
        None,
        DurableDeletionState.Active,
        None
      )
    )
  }

  private final class LiveTask extends JobTask {
    val actionId: ActionId = ActionId("cncf", "action", Some(_instant), Some("LIVE-ACTION-BODY"))

    override def taskKind: String = "operation"

    def run(ctx: ExecutionContext): TaskOutcome =
      TaskSucceeded(OperationResponse.Scalar("LIVE-ACTION-BODY"))
  }
}
