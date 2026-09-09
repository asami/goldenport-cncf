package org.goldenport.cncf.job

import java.time.Instant

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobRecordMigrationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Durable Job record v2 migration" should {
    "canonically create, decode, and redact a pending v2 record" in {
      Given("a submitted body with the v2-only pending result")
      val body = _pending_body(DurableJobLifecycleStatus.Submitted)

      When("the body is explicitly created as v2 and decoded by its authorized subject")
      val created = DurableJobRecord.createV2(body).toOption.getOrElse(fail("unable to create v2 pending record"))
      val canonical = DurableJobRecordCodec.canonicalJson(created).toOption.getOrElse(fail("unable to render v2 pending record"))
      val decoded = DurableJobRecordCodec.decode(canonical, _access)
      val projection = created.publicProjection(_access)

      Then("the format-bound record round trips and the public result exposes only pending metadata")
      created.format shouldBe DurableRecordFormat.V2
      decoded shouldBe Consequence.Success(created)
      projection.toOption.get.result shouldBe DurablePublicResult("pending", None, None)
      canonical should include("\"version\":2")
    }

    "refuse a pending result in the legacy v1 constructor" in {
      Given("a pending body that is otherwise valid for v2")
      val body = _pending_body(DurableJobLifecycleStatus.Running)

      When("the legacy v1 constructor attempts to sign it")
      val result = DurableJobRecord.create(body)

      Then("v1 admission fails without producing a durable record")
      result shouldBe a[Consequence.Failure[_]]
    }

    "migrate an admitted v1 record one way by re-signing its unchanged body as v2" in {
      Given("an admitted terminal v1 record")
      val source = DurableJobRecord.create(_terminal_body).toOption.getOrElse(fail("unable to create v1 source record"))

      When("the explicit v1-to-v2 migration is requested")
      val migrated = DurableJobRecord.migrateV1ToV2(source)

      Then("the source body is retained, the destination is v2, and both signatures remain format-bound")
      migrated.toOption.get.format shouldBe DurableRecordFormat.V2
      migrated.toOption.get.body shouldBe source.body
      migrated.toOption.get.integrity.unsignedBodySha256 should not be source.integrity.unsignedBodySha256
      source.format shouldBe DurableRecordFormat.V1
    }

    "refuse malformed and lifecycle-mismatched v2 input without a partial record" in {
      Given("one canonical v2 pending record")
      val record = DurableJobRecord.createV2(_pending_body(DurableJobLifecycleStatus.Suspended)).toOption.getOrElse(fail("unable to create v2 fixture"))
      val canonical = DurableJobRecordCodec.canonicalJson(record).toOption.getOrElse(fail("unable to render v2 fixture"))

      When("its result outcome is malformed or its pending result is paired with a terminal lifecycle")
      val malformed = canonical.replace("\"outcome\":\"pending\"", "\"outcome\":\"unknown\"")
      val mismatched = canonical.replace("\"status\":\"suspended\"", "\"status\":\"succeeded\"")
      val refusals = Vector(malformed, mismatched).map(DurableJobRecordCodec.decode(_, _access))

      Then("each v2 input is refused")
      refusals.foreach(_ shouldBe a[Consequence.Failure[_]])
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))

  private def _pending_body(status: DurableJobLifecycleStatus): DurableJobRecordBody =
    _body(status, DurableResultOutcome.Pending)

  private def _body(status: DurableJobLifecycleStatus, result: DurableResultOutcome): DurableJobRecordBody = {
    val operation = DurableOperationReference("component-a", None, "run", None)
    DurableJobRecordBody(
      DurableJobIdentity("job-001", 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(
        status,
        0,
        DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 1, None, false, false),
        DurableScheduleState(Some(_instant), None, None)
      ),
      Vector(DurableTaskDescriptor(
        "task-001",
        None,
        DurableTaskKind.Operation,
        DurableTaskTarget("operation", operation),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending),
        None
      )),
      Vector(DurableInputReference("request", DurableValue.Absent)),
      result,
      Vector.empty,
      Vector.empty,
      None,
      DurableDefinitionSnapshot("definition-001", "job", 1, 1L, _digest, None, None, Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    )
  }

  private val _terminal_body = _body(
    DurableJobLifecycleStatus.Succeeded,
    DurableResultOutcome.Succeeded(DurableValue.Absent)
  )
}
