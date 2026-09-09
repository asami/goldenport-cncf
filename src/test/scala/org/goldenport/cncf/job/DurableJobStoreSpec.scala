package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationExecutionPolicy,
  EntityRevisionRepresentation,
  EntityStore,
  RevisionPreconditionPolicy
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for canonical durable job-record storage.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobStoreSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:durable-job-record-contract, example:E1, rules:JM69-03A, phase:69.1, slice:JM69-03A"
  )
  private val _e2 = afterWord(
    "in spec:durable-job-record-contract, example:E2, rules:JM69-03A, phase:69.1, slice:JM69-03A"
  )
  private val _e3 = afterWord(
    "in spec:durable-job-record-contract, example:E3, rules:JM69-03A, phase:69.1, slice:JM69-03A"
  )
  private val _e4 = afterWord(
    "in spec:durable-job-record-contract, example:E4, rules:JM69-03A, phase:69.1, slice:JM69-03A"
  )
  private val _e5 = afterWord(
    "in spec:durable-job-record-contract, example:E5, rules:JM69-03A, phase:69.1, slice:JM69-03A"
  )

  "DurableJobStore" should {
    "E1 load canonical records in a fresh runtime context sharing the provider" must _e1 {
      "when exercising a fresh-context canonical load" in {
        Given("src/test/scala/org/goldenport/cncf/job/DurableJobStoreSpec.scala; JM69-03A; E1; one canonical record and a detached-revision EntityStore provider")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val record = _record("job-fresh", 3L)
        val created = _success(fixture.store.create(record, _access))
        val freshcontext = ExecutionContext.withJobContext(fixture.context, JobContext.empty)

        When("a distinct runtime context over the same provider loads the durable job")
        val loaded = (new DurableJobStore(fixture.entitystore)).load(record.body.identity.jobId, _access)(using freshcontext)

        Then("the load admits the same canonical record with its provider revision")
        loaded shouldBe Consequence.Success(Some(created))
      }
    }

    "E2 refuse duplicate creates and stale checkpoints without overwriting the authoritative record" must _e2 {
      "when exercising duplicate and stale refusal" in {
        Given("src/test/scala/org/goldenport/cncf/job/DurableJobStoreSpec.scala; JM69-03A; E2; one created durable record and its observed provider revision")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val initial = _record("job-stale", 3L)
        val created = _success(fixture.store.create(initial, _access))
        val next = _next(initial)
        val advanced = _success(fixture.store.checkpoint(created, next, _access))

        When("the same identity is created again and the stale snapshot attempts the checkpoint")
        val duplicate = fixture.store.create(initial, _access)
        val stale = fixture.store.checkpoint(created, next, _access)
        val loaded = fixture.store.load(initial.body.identity.jobId, _access)

        Then("both operations fail and the provider retains the contiguous authoritative checkpoint")
        duplicate shouldBe a[Consequence.Failure[_]]
        stale shouldBe a[Consequence.Failure[_]]
        loaded shouldBe Consequence.Success(Some(advanced))
      }
    }

    "E3 refuse corrupt stored text and unauthorized access without returning a partial durable record" must _e3 {
      "when exercising corrupt and access refusal" in {
        Given("src/test/scala/org/goldenport/cncf/job/DurableJobStoreSpec.scala; JM69-03A; E3; one created durable record, a foreign access request, and a controlled corrupt storage write")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val record = _record("job-refusal", 3L)
        val created = _success(fixture.store.create(record, _access))
        val entityid = _success(DurableJobStoreEntity.entityId(record.body.identity.jobId))

        When("a foreign subject loads the record and the provider is later presented corrupt canonical text")
        val unauthorized = fixture.store.load(record.body.identity.jobId, _access.copy(subjectId = "subject-b"))
        val corrupted = fixture.entitystore.updateDetached(
          DurableJobStoreEntity(entityid, record.body.identity.jobId, "not-json"),
          Some(created.providerrevision),
          _observed_policy(created.providerrevision)
        )
        val corrupt = fixture.store.load(record.body.identity.jobId, _access)

        Then("both reads fail and neither returns a partial durable record")
        unauthorized shouldBe a[Consequence.Failure[_]]
        corrupted shouldBe a[Consequence.Success[_]]
        corrupt shouldBe a[Consequence.Failure[_]]
      }
    }

    "E4 persist contiguous semantic checkpoints and return their new provider revision" must _e4 {
      "when exercising a contiguous checkpoint" in {
        Given("src/test/scala/org/goldenport/cncf/job/DurableJobStoreSpec.scala; JM69-03A; E4; one admitted canonical record and its observed provider revision")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val initial = _record("job-checkpoint", 3L)
        val created = _success(fixture.store.create(initial, _access))
        val next = _next(initial)

        When("the next contiguous durable semantic revision is checkpointed with the observed provider revision")
        val checkpointed = fixture.store.checkpoint(created, next, _access)
        val loaded = fixture.store.load(initial.body.identity.jobId, _access)

        Then("the canonical successor is durable and carries the advanced provider revision")
        checkpointed.map(_.record) shouldBe Consequence.Success(next)
        checkpointed.map(_.providerrevision.value) shouldBe Consequence.Success(created.providerrevision.value + 1L)
        loaded shouldBe checkpointed.map(Some(_))
      }
    }

    "E5 register a detached optimistic collection for an unregistered runtime context" must _e5 {
      "when exercising adapter CRUD without test-only collection registration" in {
        Given("src/test/scala/org/goldenport/cncf/job/DurableJobStoreSpec.scala; JM69-03A; E5; an unregistered EntitySpace and one canonical durable record")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection) shouldBe None
        val initial = _record("job-unregistered", 3L)
        val next = _next(initial)

        When("the adapter creates, loads, and checkpoints the durable record")
        val created = fixture.store.create(initial, _access)
        val loaded = fixture.store.load(initial.body.identity.jobId, _access)
        val checkpointed = created.flatMap(fixture.store.checkpoint(_, next, _access))

        Then("CRUD succeeds and the installed collection retains detached optimistic binding")
        created.map(_.record) shouldBe Consequence.Success(initial)
        loaded shouldBe created.map(Some(_))
        checkpointed.map(_.record) shouldBe Consequence.Success(next)
        fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
          .map(_.descriptor.revisionBinding.map(_.representation)) shouldBe
          Some(Some(EntityRevisionRepresentation.Detached))
        fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)
          .map(_.descriptor.plan.concurrencyPolicy) shouldBe
          Some(EntityConcurrencyPolicy.Optimistic)
      }
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))

  private final case class Fixture(
    store: DurableJobStore,
    entitystore: EntityStore,
    context: ExecutionContext
  )

  private def _fixture(): Fixture = {
    val context = ExecutionContext.test()
    val entitystore = EntityStore.standard()
    Fixture(new DurableJobStore(entitystore), entitystore, context)
  }

  private def _record(jobid: String, revision: Long): DurableJobRecord =
    DurableJobRecord.create(_body(jobid, revision)).toOption.getOrElse(
      fail("unable to sign durable fixture")
    )

  private def _next(record: DurableJobRecord): DurableJobRecord =
    DurableJobRecord.create(
      record.body.copy(
        identity = record.body.identity.copy(
          revision = record.body.identity.revision + 1L,
          updatedAt = record.body.identity.updatedAt.plusSeconds(30L)
        )
      )
    ).toOption.getOrElse(fail("unable to sign durable checkpoint fixture"))

  private def _body(jobid: String, revision: Long): DurableJobRecordBody = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    DurableJobRecordBody(
      identity = DurableJobIdentity(jobid, revision, _instant, _instant.plusSeconds(30L)),
      authorization = DurableJobAuthorization(
        "tenant-a",
        DurableSubject("subject-a", "user"),
        DurableVisibility.Subject,
        Set("job.read")
      ),
      lifecycle = DurableJobLifecycle(
        DurableJobLifecycleStatus.Succeeded,
        7,
        DurableRunMode.Async,
        DurableRetryEvidence(
          Vector(DurableAttemptEvidence(1, _instant, Some(_instant.plusSeconds(20L)), DurableAttemptOutcome.Succeeded, None)),
          maxAttempts = 3,
          nextRetryAt = None,
          exhausted = false,
          recoveryRequired = false
        ),
        DurableScheduleState(Some(_instant), Some(_instant), Some(_instant.plusSeconds(20L)))
      ),
      tasks = Vector(
        DurableTaskDescriptor(
          "task-root",
          None,
          DurableTaskKind.Operation,
          DurableTaskTarget("operation", operation),
          DurableTaskRelation(DurableTaskRelationKind.Root, None),
          DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed),
          Some(DurableCompensationDescriptor(operation.copy(operationId = "undo"), "task-root", DurableCompensationStatus.NotRequired, None))
        )
      ),
      inputs = Vector.empty,
      result = DurableResultOutcome.Succeeded(DurableValue.Absent),
      timeline = Vector(
        DurableTimelineEvent(1L, _instant, "submitted", Some("task-root"), Some("accepted")),
        DurableTimelineEvent(2L, _instant.plusSeconds(20L), "completed", Some("task-root"), Some("completed"))
      ),
      diagnostics = Vector(DurableDiagnosticSummary("execution", "ok", "info", "completed")),
      calltreeReference = None,
      definitionSnapshot = DurableDefinitionSnapshot(
        "job-definition-001",
        "daily-job",
        1,
        2L,
        _digest,
        None,
        None,
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
