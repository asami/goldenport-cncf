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
 *  version Sep. 14, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "DurableJobProjection" should {
    "CP1 strict empty Continuation correspondence" must afterWord(
      "in spec:durable-job-record-contract, example:CP1, rules:R3,R6, phase:69.7"
    ) {
      "preserve Running and Succeeded Action continuations through canonical V2 admission and terminal V1 compatibility" in {
        Vector(false, true).foreach { running =>
          Given(s"CP1: a Running=$running Root and distinct Action Continuation have empty compensation fields")
          val (record, evidence) = _continuation_case(running)
          val rootid = _record().taskReadModels.head.taskId.value
          val childid = TaskId("cncf", "task", Some(_instant), Some("strict-continuation")).value
          val access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
          When("the exact source is projected through V2, canonical JSON, authorized decode and the V1 projector")
          val projected = DurableJobProjection.projectV2(record, evidence)
          val canonical = projected.flatMap(DurableJobRecordCodec.canonicalJson)
          val decoded = canonical.flatMap(text => DurableJobRecordCodec.decode(text, access))
          val legacy = DurableJobProjection.project(record, evidence)
          Then("canonical integrity admission preserves the exact ordered descriptors and independent Continuation identity without live bodies")
          projected shouldBe a[Consequence.Success[_]]
          canonical shouldBe a[Consequence.Success[_]]
          decoded shouldBe a[Consequence.Success[_]]
          decoded.toOption.get shouldBe projected.toOption.get
          val body = projected.toOption.get.body
          body.tasks shouldBe evidence.tasks
          body.tasks.map(_.taskId) shouldBe Vector(rootid, childid)
          body.tasks.head.parentTaskId shouldBe None
          body.tasks.head.relation shouldBe DurableTaskRelation(DurableTaskRelationKind.Root, None)
          body.tasks.last.parentTaskId shouldBe Some(rootid)
          body.tasks.last.relation shouldBe DurableTaskRelation(DurableTaskRelationKind.Continuation, Some(rootid))
          body.tasks.last.kind shouldBe DurableTaskKind.Action
          body.tasks.last.target shouldBe DurableTaskTarget("action",
            DurableOperationReference("component-a", Some("service-a"), "continue", None))
          body.tasks.map(_.transaction) shouldBe Vector.fill(2)(DurableTransactionDescriptor(
            DurableTransactionRole.Own, DurableTransactionScope.PerTask,
            if (running) DurableTransactionOutcome.Pending else DurableTransactionOutcome.Committed))
          body.tasks.map(_.compensation) shouldBe Vector(None, None)
          body.lifecycle.status shouldBe
            (if (running) DurableJobLifecycleStatus.Running else DurableJobLifecycleStatus.Succeeded)
          body.result shouldBe (if (running) DurableResultOutcome.Pending else _evidence().result)
          canonical.toOption.get should not include "LIVE-ACTION-BODY"
          canonical.toOption.get should not include "LIVE-RESULT-BODY"
          canonical.toOption.get should not include "RAW-INPUT-BODY"
          if (running) {
            legacy shouldBe a[Consequence.Failure[_]]
          } else {
            legacy shouldBe a[Consequence.Success[_]]
            legacy.toOption.get.body.tasks shouldBe evidence.tasks
          }
        }
      }
    }

    "CP2 strict Continuation compensation metadata" must afterWord(
      "in spec:durable-job-record-contract, example:CP2, rules:R3,R6, phase:69.7"
    ) {
      "refuse every targetless nonempty subset while preserving complete historical explicit correspondence" in {
        Vector(false, true).foreach { running =>
          (1 to 7).foreach { subset =>
            Given(s"CP2: Running=$running Continuation has targetless compensation subset=$subset and an exact None source descriptor")
            val (record, evidence) = _continuation_case(running)
            val child = record.taskReadModels.last.copy(
              compensationActionRef = Option.when((subset & 1) != 0)("undo"),
              compensationStatus = Option.when((subset & 2) != 0)("succeeded"),
              compensationFailureSummary = Option.when((subset & 4) != 0)("failed"))
            val candidate = record.copy(taskReadModels = record.taskReadModels.updated(1, child))
            When("the incomplete live Continuation is projected through V2 without source or model normalization")
            val projected = DurableJobProjection.projectV2(candidate, evidence)
            Then("every nonempty targetless subset refuses before a durable record even though the source says None")
            projected shouldBe a[Consequence.Failure[_]]
          }
        }
        Given("CP2: a terminal Continuation has complete undo, explicit Root target and succeeded metadata with its exact supplied compensation")
        val (record, evidence) = _continuation_case(false)
        val rootid = record.taskReadModels.head.taskId
        val child = record.taskReadModels.last.copy(compensationActionRef = Some("undo"),
          compensatesTaskId = Some(rootid), compensationStatus = Some("succeeded"))
        val compensation = DurableCompensationDescriptor(
          DurableOperationReference("component-a", Some("service-a"), "undo", None),
          rootid.value, DurableCompensationStatus.Succeeded, None)
        val candidate = record.copy(taskReadModels = record.taskReadModels.updated(1, child))
        val supplied = evidence.copy(tasks = evidence.tasks.updated(1,
          evidence.tasks.last.copy(compensation = Some(compensation))))
        When("the complete historical Continuation correspondence is projected through V1 and V2")
        val legacy = DurableJobProjection.project(candidate, supplied)
        val projected = DurableJobProjection.projectV2(candidate, supplied)
        Then("both versions retain the exact explicit compensation and full supplied descriptors without inventing a companion")
        legacy shouldBe a[Consequence.Success[_]]
        projected shouldBe a[Consequence.Success[_]]
        legacy.toOption.get.body.tasks shouldBe supplied.tasks
        projected.toOption.get.body.tasks shouldBe supplied.tasks
        projected.toOption.get.body.tasks.last.compensation shouldBe Some(compensation)
      }
    }

    "CP3 exact Continuation source correspondence" must afterWord(
      "in spec:durable-job-record-contract, example:CP3, rules:R3,R6, phase:69.7"
    ) {
      "refuse each independent supplied descriptor divergence in both valid lifecycles" in {
        Vector(false, true).foreach { running =>
          Vector("parent", "relation", "operation", "kind", "transaction", "compensation").foreach { variant =>
            Given(s"CP3: Running=$running live Continuation stays valid while only supplied $variant correspondence diverges")
            val (record, evidence) = _continuation_case(running)
            val child = evidence.tasks.last
            val divergent = variant match {
              case "parent" => child.copy(parentTaskId = None)
              case "relation" => child.copy(relation = child.relation.copy(kind = DurableTaskRelationKind.Child))
              case "operation" => child.copy(target = child.target.copy(
                operation = child.target.operation.copy(operationId = "other-operation")))
              case "kind" => child.copy(kind = DurableTaskKind.Operation)
              case "transaction" => child.copy(transaction = child.transaction.copy(outcome =
                if (running) DurableTransactionOutcome.Committed else DurableTransactionOutcome.Pending))
              case _ => child.copy(compensation = Some(DurableCompensationDescriptor(
                DurableOperationReference("component-a", Some("service-a"), "undo", None),
                record.taskReadModels.head.taskId.value, DurableCompensationStatus.Succeeded, None)))
            }
            val supplied = evidence.copy(tasks = evidence.tasks.updated(1, divergent))
            When("V2 compares the unchanged live record with that divergent supplied descriptor")
            val projected = DurableJobProjection.projectV2(record, supplied)
            Then("the independent source mismatch refuses before a durable record")
            projected shouldBe a[Consequence.Failure[_]]
          }
        }
      }
    }

    "NRT-P1 normal compensation aggregates" must afterWord(
      "in spec:durable-job-record-contract, example:NRT-P1, rules:R3,R6, phase:69.7"
    ) {
      "preserve V2 declaration and closed original recovery facts without inventing a companion descriptor" in {
        Given("NRT-P1: a normal succeeded task with a nonblank compensation declaration in docs/spec/durable-job-record-contract")
        val declaration = _record().copy(taskReadModels = _record().taskReadModels.map(
          _.copy(compensationActionRef = Some("undo"))))
        When("V2 and the strict V1 projector receive the same declaration-only original")
        val declared = DurableJobProjection.projectV2(declaration, _evidence())
        Then("V2 signs the exact ordinary descriptor while V1 still refuses incomplete compensation metadata")
        declared shouldBe a[Consequence.Success[_]]
        declared.toOption.get.body.tasks.head.compensation shouldBe None
        DurableJobProjection.project(declaration, _evidence()) shouldBe a[Consequence.Failure[_]]
        Vector(("succeeded", true), ("failed", true), ("missing", true), ("missing", false)).foreach {
          case (mode, action) =>
            Given(s"NRT-P1: a committed succeeded original with $mode aggregate, declared action=$action, and exact actual companion facts")
            val (record, evidence) = _aggregate_case(mode, action)
            When("the unchanged aggregate and exact closed task descriptors are projected")
            val projected = DurableJobProjection.projectV2(record, evidence)
            Then("the normal original retains descriptor None, real companions stay exact, and checked missing/failure recovery remains signed")
            projected shouldBe a[Consequence.Success[_]]
            projected.toOption.get.body.tasks.head.compensation shouldBe None
            projected.toOption.get.body.tasks shouldBe evidence.tasks
            projected.toOption.get.body.lifecycle.retry.recoveryRequired shouldBe
              (mode == "failed" || mode == "missing")
            record.taskReadModels.head.compensationStatus shouldBe Some(mode)
            DurableJobProjection.project(record, evidence) shouldBe a[Consequence.Failure[_]]
        }
      }
    }

    "NRT-P2 malformed normal aggregates" must afterWord(
      "in spec:durable-job-record-contract, example:NRT-P2, rules:R3,R6, phase:69.7"
    ) {
      "refuse malformed aggregate metadata and mismatched or absent companion evidence without deleting it" in {
        val (missing, missingevidence) = _aggregate_case("missing", false)
        val original = missing.taskReadModels.head
        val variants = Vector(
          "unknown" -> original.copy(compensationStatus = Some("unknown")),
          "stray" -> original.copy(compensationStatus = None),
          "blankfailure" -> original.copy(compensationFailureSummary = Some(" ")),
          "blankaction" -> original.copy(compensationActionRef = Some(" ")),
          "modelrecovery" -> original.copy(recoveryRequired = false),
          "uncommitted" -> original.copy(transactionOutcome = Some("failed")),
          "unfinished" -> original.copy(finishedAt = None)
        )
        variants.foreach { case (name, model) =>
          Given(s"NRT-P2: the absent-action missing original has malformed $name metadata under R3,R6")
          val candidate = missing.copy(taskReadModels = model +: missing.taskReadModels.tail)
          When("the full typed candidate is projected without preprocessing normalization")
          val result = DurableJobProjection.projectV2(candidate, missingevidence)
          Then("the invalid aggregate is refused before a durable record is returned")
          result shouldBe a[Consequence.Failure[_]]
          candidate.taskReadModels.head shouldBe model
        }
        Given("NRT-P2: a no-action missing original has its model recovery flag but no job recovery flag")
        When("the record and its matching retry evidence both omit recoveryRequired")
        val norecovery = DurableJobProjection.projectV2(missing.copy(
          retry = missing.retry.copy(recoveryRequired = false)), missingevidence.copy(
          retry = missingevidence.retry.copy(recoveryRequired = false)))
        Then("missing still refuses even when supplied retry evidence agrees with the malformed live record")
        norecovery shouldBe a[Consequence.Failure[_]]
        Vector("succeeded", "failed").foreach { mode =>
          val (record, evidence) = _aggregate_case(mode, true)
          val companion = record.taskReadModels.last
          Vector("absent", "multiple", "status", "target", "aggregate").foreach { variant =>
            Given(s"NRT-P2: a $mode original has $variant actual companion correspondence")
            val models = variant match {
              case "absent" => record.taskReadModels.init
              case "multiple" => record.taskReadModels :+ companion.copy(
                taskId = TaskId("cncf", "task", Some(_instant), Some("duplicate-companion")))
              case "status" => record.taskReadModels.init :+ companion.copy(
                compensationStatus = Some(if (mode == "succeeded") "failed" else "succeeded"))
              case "target" => record.taskReadModels.init :+ companion.copy(
                compensatesTaskId = Some(record.taskReadModels(1).taskId))
              case _ => record.taskReadModels.updated(0,
                record.taskReadModels.head.copy(compensationStatus = Some("running")))
            }
            val supplied = evidence.copy(tasks = models.map(_descriptor))
            When("the original aggregate is compared with complete matching source descriptors for those actual models")
            val result = DurableJobProjection.projectV2(record.copy(taskReadModels = models), supplied)
            Then("none of the malformed correspondences becomes a terminal signed aggregate")
            result shouldBe a[Consequence.Failure[_]]
          }
          if (mode == "failed") {
            Vector(true, false).foreach { modelmissing =>
              Given("NRT-P2: failed compensation lacks exactly one of the original/model and job recovery flags")
              val candidate = if (modelmissing) record.copy(taskReadModels = record.taskReadModels.updated(0,
                record.taskReadModels.head.copy(recoveryRequired = false)))
              else record.copy(retry = record.retry.copy(recoveryRequired = false))
              val supplied = evidence.copy(retry = evidence.retry.copy(recoveryRequired = candidate.retry.recoveryRequired))
              When("the flag-deficient failed aggregate is projected with exact companion evidence")
              val result = DurableJobProjection.projectV2(candidate, supplied)
              Then("both independently missing flags refuse")
              result shouldBe a[Consequence.Failure[_]]
            }
          }
        }
      }
    }

    "NRT-P3 strict actual descriptors and historical compatibility" must afterWord(
      "in spec:durable-job-record-contract, example:NRT-P3, rules:R3,R6, phase:69.7"
    ) {
      "retain V1/V2 complete normal descriptor equality and refuse incomplete actual companions or source divergence" in {
        Given("NRT-P3: a historical complete normal descriptor has explicit action, original target and succeeded status")
        val original = _record().taskReadModels.head.copy(compensationActionRef = Some("undo"),
          compensatesTaskId = Some(_record().taskReadModels.head.taskId), compensationStatus = Some("succeeded"))
        val record = _record().copy(taskReadModels = Vector(original))
        val supplied = _evidence().copy(tasks = Vector(_descriptor(original)))
        When("the complete historical normal correspondence is projected by both versions")
        val v1 = DurableJobProjection.project(record, supplied)
        val v2 = DurableJobProjection.projectV2(record, supplied)
        Then("both preserve the exact existing descriptor and neither invents a different target")
        v1 shouldBe a[Consequence.Success[_]]
        v2 shouldBe a[Consequence.Success[_]]
        v1.toOption.get.body.tasks shouldBe supplied.tasks
        v2.toOption.get.body.tasks shouldBe supplied.tasks
        val (aggregate, evidence) = _aggregate_case("succeeded", true)
        val companion = aggregate.taskReadModels.last
        Vector("action", "target", "status").foreach { field =>
          Given(s"NRT-P3: an actual compensation-relation companion lacks its $field field")
          val broken = field match {
            case "action" => companion.copy(compensationActionRef = None)
            case "target" => companion.copy(compensatesTaskId = None)
            case _ => companion.copy(compensationStatus = None)
          }
          When("the incomplete actual companion is projected against the original complete source descriptor")
          val result = DurableJobProjection.projectV2(
            aggregate.copy(taskReadModels = aggregate.taskReadModels.init :+ broken), evidence)
          Then("the actual companion never enters the normal aggregate exception")
          result shouldBe a[Consequence.Failure[_]]
        }
        Given("NRT-P3: a complete actual companion has a source descriptor naming another compensation action")
        val mismatched = evidence.copy(tasks = evidence.tasks.init :+ evidence.tasks.last.copy(
          compensation = evidence.tasks.last.compensation.map(value =>
            value.copy(operation = value.operation.copy(operationId = "different-action")))))
        When("the exact live companion and divergent source action are projected")
        val mismatch = DurableJobProjection.projectV2(aggregate, mismatched)
        Then("unchanged strict source equality refuses the companion")
        mismatch shouldBe a[Consequence.Failure[_]]
      }
    }
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
      val ephemeralresult = DurableJobProjection.project(ephemeral, evidence)
      val crosswiredresult = DurableJobProjection.project(record, crosswired)

      Then("both variants fail closed without a partial durable record")
      ephemeralresult shouldBe a[Consequence.Failure[_]]
      crosswiredresult shouldBe a[Consequence.Failure[_]]
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
      val mismatcheddescriptor = DurableJobProjection.projectV2(
        record,
        evidence.copy(tasks = evidence.tasks.map(task =>
          task.copy(target = task.target.copy(
            operation = task.target.operation.copy(operationId = "other-operation")
          ))
        ))
      )

      Then("only the exact descriptor produces the pending record with its started time")
      projected shouldBe a[Consequence.Success[_]]
      mismatcheddescriptor shouldBe a[Consequence.Failure[_]]
      projected.toOption.get.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
      projected.toOption.get.body.lifecycle.schedule shouldBe DurableScheduleState(
        Some(_instant),
        Some(_instant.plusSeconds(5L)),
        None
      )
      val contradictorytask = task.copy(transactionOutcome = Some("committed"))
      val contradictoryevidence = evidence.copy(tasks = evidence.tasks.map(task =>
        task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Committed))
      ))
      DurableJobProjection.projectV2(
        record.copy(taskReadModels = Vector(contradictorytask)),
        contradictoryevidence
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
      val mismatchedresult = DurableJobProjection.projectV2(
        record,
        evidence.copy(result = DurableResultOutcome.Succeeded(DurableValue.Absent))
      )
      val mismatchedtaskevidence = DurableJobProjection.projectV2(record, evidence.copy(tasks = Vector.empty))

      Then("neither mismatch produces a partial v2 record")
      mismatchedresult shouldBe a[Consequence.Failure[_]]
      mismatchedtaskevidence shouldBe a[Consequence.Failure[_]]
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

  private def _continuation_case(running: Boolean): (JobRecord, DurableJobProjectionEvidence) = {
    val base = _record()
    val root = base.taskReadModels.head.copy(
      status = if (running) JobTaskStatus.Running else JobTaskStatus.Succeeded,
      finishedAt = if (running) None else Some(_instant.plusSeconds(20L)),
      result = if (running) JobTaskResultSummary(success = true, message = None) else base.taskReadModels.head.result,
      transactionOutcome = Some(if (running) "running" else "committed"))
    val child = root.copy(
      taskId = TaskId("cncf", "task", Some(_instant), Some("strict-continuation")),
      parentTaskId = Some(root.taskId),
      startedAt = _instant.plusSeconds(6L),
      finishedAt = if (running) None else Some(_instant.plusSeconds(15L)),
      operation = Some("continue"), taskKind = "action", targetKind = Some("action"),
      relation = Some("continuation"))
    val started = Vector(
      JobTimelineEvent(1L, _instant, "job.submitted", None, None, None),
      JobTimelineEvent(2L, _instant.plusSeconds(5L), "task.durable-start-intent", Some(root.taskId), None, None),
      JobTimelineEvent(3L, _instant.plusSeconds(6L), "task.durable-start-intent", Some(child.taskId), Some(root.taskId), None))
    val timeline = if (running) started else started ++ Vector(
      JobTimelineEvent(4L, _instant.plusSeconds(15L), "task.durable-outcome-checkpoint", Some(child.taskId), Some(root.taskId), None),
      JobTimelineEvent(5L, _instant.plusSeconds(20L), "task.durable-outcome-checkpoint", Some(root.taskId), None, None),
      JobTimelineEvent(6L, _instant.plusSeconds(20L), "job.succeeded", None, None, None))
    val record = base.copy(
      status = if (running) JobStatus.Running else JobStatus.Succeeded,
      result = if (running) None else base.result,
      taskReadModels = Vector(root, child), timeline = timeline,
      retry = if (running) JobRetryState(maxAttempts = 3) else base.retry)
    val original = _evidence()
    val evidence = original.copy(
      tasks = Vector(_descriptor(root), _descriptor(child).copy(kind = DurableTaskKind.Action)),
      result = if (running) DurableResultOutcome.Pending else original.result,
      retry = if (running) DurableRetryEvidence(Vector.empty, 3, None, false, false) else original.retry)
    record -> evidence
  }

  private def _aggregate_case(mode: String, action: Boolean): (JobRecord, DurableJobProjectionEvidence) = {
    val base = _record()
    val original = base.taskReadModels.head.copy(
      compensationActionRef = Option.when(action)("undo"), compensationStatus = Some(mode),
      compensationFailureSummary = Option.when(mode == "failed" || mode == "missing")("compensation unavailable"),
      recoveryRequired = mode == "failed" || mode == "missing")
    val failedid = TaskId("cncf", "task", Some(_instant), Some("aggregate-failure"))
    val failed = base.taskReadModels.head.copy(taskId = failedid, parentTaskId = Some(original.taskId),
      status = JobTaskStatus.Failed, startedAt = _instant.plusSeconds(21),
      finishedAt = Some(_instant.plusSeconds(25)), result = JobTaskResultSummary(false, Some("failed")),
      relation = Some("child"), transactionOutcome = Some("failed"))
    val companion = base.taskReadModels.head.copy(
      taskId = TaskId("cncf", "task", Some(_instant), Some("aggregate-companion")),
      parentTaskId = Some(failedid), relation = Some("compensation"),
      status = if (mode == "failed") JobTaskStatus.Failed else JobTaskStatus.Succeeded,
      startedAt = _instant.plusSeconds(26), finishedAt = Some(_instant.plusSeconds(27)),
      result = JobTaskResultSummary(mode != "failed", Some("compensated")),
      transactionOutcome = Some(if (mode == "failed") "compensation-failed" else "compensation-committed"),
      compensationActionRef = Some("undo"), compensatesTaskId = Some(original.taskId),
      compensationStatus = Some(mode),
      compensationFailureSummary = Option.when(mode == "failed")("compensation unavailable"),
      recoveryRequired = mode == "failed")
    val models = Vector(original, failed) ++ Option.when(mode != "missing")(companion)
    val record = base.copy(status = JobStatus.Failed,
      result = Some(JobResult.Failure(Consequence.stateInvalid[Nothing]("failed").conclusion)),
      updatedAt = _instant.plusSeconds(30), taskReadModels = models,
      timeline = Vector(JobTimelineEvent(1, _instant, "job.submitted", None, None, None),
        JobTimelineEvent(2, _instant.plusSeconds(30), "job.failed", None, None, None)),
      retry = JobRetryState(maxAttempts = 3, recoveryRequired = mode == "failed" || mode == "missing"))
    val evidence = _evidence().copy(tasks = models.map(_descriptor),
      result = DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", false)),
      retry = DurableRetryEvidence(Vector(DurableAttemptEvidence(1, _instant.plusSeconds(5),
        Some(_instant.plusSeconds(30)), DurableAttemptOutcome.Failed,
        Some(DurableFailureSummary("execution", "failed", "failed", false)))),
        3, None, false, record.retry.recoveryRequired))
    record -> evidence
  }

  private def _descriptor(model: JobTaskReadModel): DurableTaskDescriptor = {
    val relation = DurableTaskRelationKind.parse(model.relation.get).toOption.get
    val outcome = model.transactionOutcome.get match {
      case "running" => DurableTransactionOutcome.Pending
      case "committed" => DurableTransactionOutcome.Committed
      case "failed" => DurableTransactionOutcome.Failed
      case _ => DurableTransactionOutcome.Compensated
    }
    val compensation = if (model.compensatesTaskId.nonEmpty) Some(DurableCompensationDescriptor(
      DurableOperationReference(model.component.get, model.service, model.compensationActionRef.get, None),
      model.compensatesTaskId.get.value, DurableCompensationStatus.parse(model.compensationStatus.get).toOption.get,
      model.compensationFailureSummary.map(value => DurableFailureSummary("compensation", "failed", value, false))))
    else None
    _evidence().tasks.head.copy(taskId = model.taskId.value, parentTaskId = model.parentTaskId.map(_.value),
      target = DurableTaskTarget(model.targetKind.get,
        DurableOperationReference(model.component.get, model.service, model.operation.get, None)),
      relation = DurableTaskRelation(relation, model.parentTaskId.map(_.value)),
      transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
        DurableTransactionScope.PerTask, outcome), compensation = compensation)
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
          "jcl.jobDefinition.format" -> "jcl"
        ),
        executionNotes = Vector.empty,
        jobDefinitionSnapshot = Some(JobDefinitionSnapshot(
          "definition-001",
          "daily-job",
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
