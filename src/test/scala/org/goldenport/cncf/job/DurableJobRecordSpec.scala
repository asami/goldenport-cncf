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
final class DurableJobRecordSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Durable Job record v1 codec" should {
    "round-trip every admitted field group with deterministic canonical JSON" in {
      Given("equivalent bodies constructed with differently ordered declared-profile maps")
      val left = _record(Map("environment" -> "production", "recovery" -> "manual"))
      val right = _record(Map("recovery" -> "manual", "environment" -> "production"))

      When("both are signed and canonically rendered")
      val leftjson = _canonical(left)
      val rightjson = _canonical(right)
      val decoded = DurableJobRecordCodec.decode(leftjson, _access)
      val leftbytes = DurableJobRecordCodec.canonicalBytes(left).toOption.get.toVector
      val rightbytes = DurableJobRecordCodec.canonicalBytes(right).toOption.get.toVector

      Then("the bytes are identical and all closed record groups survive the authorized round trip")
      leftjson shouldBe rightjson
      leftbytes shouldBe rightbytes
      decoded shouldBe Consequence.Success(left)
      decoded.toOption.get.body.tasks.size shouldBe 2
      decoded.toOption.get.body.inputs.head.value shouldBe a[DurableValue.External]
      decoded.toOption.get.body.calltreeReference.map(_.reference) shouldBe Some("calltree://job-001")
      decoded.toOption.get.body.definitionSnapshot.declaredProfile shouldBe Map(
        "environment" -> "production",
        "recovery" -> "manual"
      )
    }

    "migrate the one admitted v0 body to a valid canonical v1 record and reject unknown versions and fields" in {
      Given("the closed v0 body and a separately signed v1 equivalent")
      val record = _record()

      When("the v0 body is rendered and decoded for its authorized subject")
      val v0 = DurableJobRecordCodec.canonicalV0Json(record.body).toOption.get
      val migrated = DurableJobRecordCodec.decode(v0, _access)
      val canonical = _canonical(record)
      val migratedcanonical = _canonical(migrated.toOption.get)
      val unknownversion = canonical.replaceFirst("\"version\":1", "\"version\":99")
      val unknownfield = canonical.dropRight(1) + ",\"unrecognized\":true}"
      val unknownversionresult = DurableJobRecordCodec.decode(unknownversion, _access)
      val unknownfieldresult = DurableJobRecordCodec.decode(unknownfield, _access)

      Then("migration signs the v1 unsigned body deterministically")
      migrated shouldBe Consequence.Success(record)
      migratedcanonical shouldBe canonical

      And("other versions and unknown contract fields fail closed")
      unknownversionresult shouldBe a[Consequence.Failure[_]]
      unknownfieldresult shouldBe a[Consequence.Failure[_]]
    }

    "fail closed for integrity tampering and malformed nested, task-tree, and timeline data" in {
      Given("one valid canonical v1 record")
      val record = _record()
      val canonical = _canonical(record)

      When("its signed body, digest, or closed nested structures are malformed")
      val bodytampered = canonical.replace("\"priority\":7", "\"priority\":8")
      val malformeddigest = canonical.replace(
        s"\"unsignedBodySha256\":\"${record.integrity.unsignedBodySha256}\"",
        "\"unsignedBodySha256\":\"not-a-digest\""
      )
      val malformednested = canonical.replace("\"priority\":7", "\"priority\":\"seven\"")
      val duplicatetask = canonical.replace("\"taskId\":\"task-child\"", "\"taskId\":\"task-root\"")
      val orphanedtask = canonical.replace("\"parentTaskId\":\"task-root\"", "\"parentTaskId\":\"task-missing\"")
      val nonmonotonictimeline = canonical.replace("\"sequence\":2", "\"sequence\":1")
      val refusals = Vector(bodytampered, malformeddigest, malformednested, duplicatetask, orphanedtask, nonmonotonictimeline)
        .map(value => DurableJobRecordCodec.decode(value, _access))

      Then("each input is refused without a partial durable record")
      refusals.foreach(_ shouldBe a[Consequence.Failure[_]])
    }

    "redact ordinary projections while retaining admitted opaque references for an authorized subject" in {
      Given("an authorized record containing input, result, and calltree opaque references")
      val record = _record()

      When("the subject asks for the ordinary projection")
      val projection = record.publicProjection(_access).toOption.get

      Then("the projection exposes only metadata or opaque reference identities, never payload bodies or calltree internals")
      projection.inputs.head.value.inlineMetadata shouldBe None
      projection.inputs.head.value.externalReference.map(_.reference) shouldBe Some("blob://input-001")
      projection.result.value.flatMap(_.externalReference).map(_.reference) shouldBe Some("blob://result-001")
      projection.calltreeReference.map(_.reference) shouldBe Some("calltree://job-001")
      projection.productElementNames.toSet should not contain "rawPayload"
      projection.productElementNames.toSet should not contain "credential"
      projection.productElementNames.toSet should not contain "secret"
      projection.productElementNames.toSet should not contain "rawResultBody"
      projection.productElementNames.toSet should not contain "calltreeInternals"
    }

    "refuse cross-tenant and cross-subject access before decode or projection" in {
      Given("a valid canonical record for one tenant and subject")
      val record = _record()
      val canonical = _canonical(record)
      val othertenant = _access.copy(tenantId = "tenant-b")
      val othersubject = _access.copy(subjectId = "subject-b")

      When("the tenant and subject variants request decode and ordinary projection")
      val othertenantdecode = DurableJobRecordCodec.decode(canonical, othertenant)
      val othersubjectdecode = DurableJobRecordCodec.decode(canonical, othersubject)
      val othertenantprojection = record.publicProjection(othertenant)
      val othersubjectprojection = record.publicProjection(othersubject)

      Then("the decoder and ordinary projection both fail closed")
      othertenantdecode shouldBe a[Consequence.Failure[_]]
      othersubjectdecode shouldBe a[Consequence.Failure[_]]
      othertenantprojection shouldBe a[Consequence.Failure[_]]
      othersubjectprojection shouldBe a[Consequence.Failure[_]]
    }

    "return a failure rather than overflowing the stack for a deeply nested acyclic task tree" in {
      Given("a finite deeply nested acyclic task tree and an invalid input")
      val record = _record()
      val deeptasks = _deep_tasks(5000)
      val deepbody = record.body.copy(
        tasks = deeptasks,
        inputs = Vector(DurableInputReference("", DurableValue.Absent)),
        timeline = Vector.empty
      )

      When("the deeply nested body is validated")
      val result = DurableJobRecord.create(deepbody)

      Then("validation returns a Consequence failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read", "job.inspect"))

  private def _record(profile: Map[String, String] = Map("environment" -> "production")): DurableJobRecord =
    DurableJobRecord.create(_body(profile)).toOption.getOrElse(fail("unable to sign durable fixture"))

  private def _canonical(record: DurableJobRecord): String =
    DurableJobRecordCodec.canonicalJson(record).toOption.getOrElse(fail("unable to render durable fixture"))

  private def _body(profile: Map[String, String]): DurableJobRecordBody = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    val externalinput = DurableExternalReference("blob://input-001", "blob", Some("application/json"), 42L, _digest)
    val externalresult = DurableExternalReference("blob://result-001", "blob", Some("application/json"), 84L, _digest)
    DurableJobRecordBody(
      identity = DurableJobIdentity("job-001", 3L, _instant, _instant.plusSeconds(30L)),
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
        ),
        DurableTaskDescriptor(
          "task-child",
          Some("task-root"),
          DurableTaskKind.Continuation,
          DurableTaskTarget("operation", operation.copy(operationId = "continue")),
          DurableTaskRelation(DurableTaskRelationKind.Continuation, Some("task-root")),
          DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed),
          None
        )
      ),
      inputs = Vector(DurableInputReference("payload", DurableValue.External(externalinput))),
      result = DurableResultOutcome.Succeeded(DurableValue.External(externalresult)),
      timeline = Vector(
        DurableTimelineEvent(1L, _instant, "submitted", Some("task-root"), Some("accepted")),
        DurableTimelineEvent(2L, _instant.plusSeconds(20L), "completed", Some("task-child"), Some("completed"))
      ),
      diagnostics = Vector(DurableDiagnosticSummary("execution", "ok", "info", "completed")),
      calltreeReference = Some(DurableExternalReference("calltree://job-001", "calltree", Some("application/json"), 64L, _digest)),
      definitionSnapshot = DurableDefinitionSnapshot(
        "job-definition-001",
        "daily-job",
        1,
        2L,
        _digest,
        Some(DurableExternalReference("definition://daily-job", "definition", Some("text/plain"), 20L, _digest)),
        Some("jcl"),
        profile
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

  private def _deep_tasks(count: Int): Vector[DurableTaskDescriptor] = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    Vector.tabulate(count) { index =>
      val taskid = s"deep-$index"
      val parenttaskid = if (index == 0) None else Some(s"deep-${index - 1}")
      DurableTaskDescriptor(
        taskid,
        parenttaskid,
        DurableTaskKind.Operation,
        DurableTaskTarget("operation", operation),
        DurableTaskRelation(
          if (index == 0) DurableTaskRelationKind.Root else DurableTaskRelationKind.Child,
          parenttaskid
        ),
        DurableTransactionDescriptor(
          DurableTransactionRole.Own,
          DurableTransactionScope.PerTask,
          DurableTransactionOutcome.Committed
        ),
        None
      )
    }
  }
}
