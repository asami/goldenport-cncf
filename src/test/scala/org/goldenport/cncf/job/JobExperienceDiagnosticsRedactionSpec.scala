package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceDiagnosticsRedactionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobExperienceOperationalAuditFixtures.*

  "DR1 Full canonical diagnostic privacy" should _scenario("DR1", "R5,R9") {
    "remove every free form field while conserving exact canonical facts and raw pages" in {
      Given("docs/spec/job-operational-audit-contract.md R5,R9; DR1: complete hostile task and timeline pages with distinct field sentinels")
      val tasks = hostileTasks()
      val timeline = hostileTimeline(tasks)
      val originaltasks = tasks.copy()
      val originaltimeline = timeline.copy()
      When("the operator safe projection copies both canonical pages")
      val safetasks = JobExperienceDiagnosticsRedaction.tasks(tasks)
      val safetimeline = JobExperienceDiagnosticsRedaction.timeline(timeline)
      Then("only ids, times, statuses, booleans, counts and closed facets survive, leaving originals intact")
      _preserved(tasks, safetasks, timeline, safetimeline)
      safetasks.tasks.foreach { task =>
        _private_fields(task) shouldBe empty
        task.taskKind shouldBe "redacted"
        task.targetKind shouldBe None
        task.relation shouldBe None
        task.transactionRole shouldBe None
        task.transactionScope shouldBe None
        task.transactionOutcome shouldBe None
        task.compensationStatus shouldBe None
      }
      safetimeline.events.foreach { event => event.kind shouldBe "redacted"; event.note shouldBe None }
      tasks shouldBe originaltasks
      timeline shouldBe originaltimeline
      tasks.tasks.foreach(task => _private_fields(task).size shouldBe 6)
      timeline.events.foreach(_.note.get should include(secret))
    }
  }

  "DR2 Closed exact diagnostic facets" should _scenario("DR2", "R5,R9") {
    "preserve every canonical and legacy accepted value and reject prefix suffix case and secret variants" in {
      Given("docs/spec/job-operational-audit-contract.md R5,R9; DR2: frozen complete tables and typed canonical parent/time relations")
      val tasks = hostileTasks(count = 1)
      val timeline = hostileTimeline(tasks)
      val task = tasks.tasks.head
      val event = timeline.events.head
      When("each exact table value and each noncanonical variant is projected through real page values")
      val taskkind = Vector("action", "operation", "aggregate", "event", "continuation")
      val taskkindresults = taskkind.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(taskKind = value))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(taskKind = invalid))))
        }
        (value, projected, invalids)
      }
      val targetkind = Vector("action", "operation", "aggregate", "event", "continuation")
      val targetkindresults = targetkind.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(targetKind = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(targetKind = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val relation = Vector("root", "child", "event-reception", "continuation", "compensation")
      val relationresults = relation.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(relation = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(relation = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val transactionrole = Vector("own", "join", "none")
      val transactionroleresults = transactionrole.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionRole = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionRole = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val transactionscope = Vector("per-task", "whole-job", "none")
      val transactionscoperesults = transactionscope.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionScope = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionScope = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val transactionoutcome = Vector("pending", "running", "committed", "failed", "compensated", "compensation-committed", "compensation-failed")
      val transactionoutcomeresults = transactionoutcome.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionOutcome = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionOutcome = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val compensationstatus = Vector("not-required", "pending", "succeeded", "failed")
      val compensationstatusresults = compensationstatus.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(compensationStatus = Some(value)))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(compensationStatus = Some(invalid)))))
        }
        (value, projected, invalids)
      }
      val timelinekind = Vector("created", "submitted", "running", "completed", "succeeded", "failed", "cancelled", "suspended", "resumed", "job.submitted", "job.async.queued", "job.running", "job.scheduler.started", "job.delayed.scheduled", "job.delayed.enqueued", "job.succeeded", "job.failed", "job.cancelled", "job.suspended", "job.resumed", "job.retry.submitted", "job.retry.immediate.submitted", "job.retry.delayed.scheduled", "job.retry.delayed.submitted", "job.retry.delayed.enqueued", "job.retry.exhausted", "job.dead-letter", "job.poison", "job.recovery-required", "job.runtime-rehydrated", "job.same-job-async.queued", "job.compensation.started", "job.compensation.finished", "job.durable-start-intent", "job.durable-running-intent", "job.durable-terminal-outcome-checkpoint", "task.running", "task.succeeded", "task.failed", "task.transaction.committed", "task.transaction.failed", "task.compensation.started", "task.compensation.succeeded", "task.compensation.failed", "task.compensation.missing", "task.durable-start-intent", "task.durable-outcome-checkpoint")
      val timelinekindresults = timelinekind.map { value =>
        val projected = JobExperienceDiagnosticsRedaction.timeline(timeline.copy(events = Vector(event.copy(kind = value))))
        val invalids = _variants(value).map { invalid =>
          JobExperienceDiagnosticsRedaction.timeline(timeline.copy(events = Vector(event.copy(kind = invalid))))
        }
        (value, projected, invalids)
      }
      Then("enum-derived wire facets and compatibility values remain exact without exposing selector metadata")
      taskkindresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.taskKind shouldBe value
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.taskKind shouldBe "redacted")
      }
      targetkindresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.targetKind shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.targetKind shouldBe None)
      }
      relationresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.relation shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.relation shouldBe None)
      }
      transactionroleresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.transactionRole shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.transactionRole shouldBe None)
      }
      transactionscoperesults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.transactionScope shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.transactionScope shouldBe None)
      }
      transactionoutcomeresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.transactionOutcome shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.transactionOutcome shouldBe None)
      }
      compensationstatusresults.foreach { case (value, projected, invalids) =>
        projected.tasks.head.compensationStatus shouldBe Some(value)
        projected.tasks.head.taskId shouldBe task.taskId
        projected.tasks.head.parentTaskId shouldBe task.parentTaskId
        invalids.foreach(_.tasks.head.compensationStatus shouldBe None)
      }
      timelinekindresults.foreach { case (value, projected, invalids) =>
        projected.events.head.kind shouldBe value
        projected.events.head.taskId shouldBe event.taskId
        projected.events.head.occurredAt shouldBe event.occurredAt
        invalids.foreach(_.events.head.kind shouldBe "redacted")
      }
      DurableTransactionRole.values.foreach { role =>
        JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionRole = Some(role.wire))))).tasks.head.transactionRole shouldBe Some(role.wire)
      }
      DurableTransactionScope.values.foreach { scope =>
        JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionScope = Some(scope.wire))))).tasks.head.transactionScope shouldBe Some(scope.wire)
      }
      DurableTransactionOutcome.values.foreach { outcome =>
        JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionOutcome = Some(outcome.wire))))).tasks.head.transactionOutcome shouldBe Some(outcome.wire)
      }
      JobTaskTransactionOutcome.values.foreach { outcome =>
        val projected = JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(transactionOutcome = Some(outcome.print)))))
        projected.tasks.head.transactionOutcome shouldBe Some(outcome.print)
        _private_fields(projected.tasks.head) shouldBe empty
      }
      DurableCompensationStatus.values.foreach { status =>
        JobExperienceDiagnosticsRedaction.tasks(tasks.copy(tasks = Vector(task.copy(compensationStatus = Some(status.wire))))).tasks.head.compensationStatus shouldBe Some(status.wire)
      }
    }
  }

  "DR3 Bounded hostile string properties" should _scenario("DR3", "R3,R5,R9") {
    "conserve typed facts across twenty-four seeded shrinking projections of sensitive strings" in {
      Given("docs/spec/job-operational-audit-contract.md R3,R5,R9; DR3: seed69709401, pages0..3 and strings0..256 with Unicode controls and escapes")
      val strings = for {
        length <- Gen.choose(0, 232)
        chars <- Gen.listOfN(length, Gen.oneOf('a', '秘', 'é', '\n', '\u0001', '"', '\\'))
        count <- Gen.choose(0, 3)
      } yield ("PRIVATE-sensitive-unique:" + chars.mkString, count)
      val property = Prop.forAll(strings) { case (marker, count) =>
        val tasks = hostileTasks(marker, count)
        val timeline = hostileTimeline(tasks, marker)
        val originaltasks = tasks.copy()
        val originaltimeline = timeline.copy()
        val safetasks = JobExperienceDiagnosticsRedaction.tasks(tasks)
        val safetimeline = JobExperienceDiagnosticsRedaction.timeline(timeline)
        _preserved(tasks, safetasks, timeline, safetimeline)
        safetasks.tasks.foreach { task =>
          _private_fields(task) shouldBe empty
          _facets(task).foreach(_ should not include marker)
        }
        safetimeline.events.foreach { event => event.kind shouldBe "redacted"; event.note shouldBe None }
        tasks shouldBe originaltasks
        timeline shouldBe originaltimeline
        true
      }
      When("ScalaCheck runs actual page projections with ordinary shrinking and the frozen seed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709401L)), property)
      Then("all generated projections exclude sensitive strings and preserve exact raw facts")
      withClue(s"DR3 seed69709401: $checked ") { checked.passed shouldBe true }
      checked.succeeded should be >= 24
    }
  }

  "DR4 Independent page and relationship conservation" should _scenario("DR4", "R1,R6,R9") {
    "remain idempotent without recomputing mismatched page counts or relabelling parent and compensation identities" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R6,R9; DR4: independent pages with deliberately mismatched pagination and relationship counts")
      val tasks = hostileTasks(count = 3).copy(offset = 53, limit = 1, totalCount = 701, fetchedCount = 97)
      val timeline = hostileTimeline(tasks).copy(offset = 71, limit = 2, totalCount = 997, fetchedCount = 43)
      When("both pages are safely projected twice")
      val once = JobExperienceDiagnosticsRedaction.tasks(tasks)
      val twice = JobExperienceDiagnosticsRedaction.tasks(once)
      val firstevents = JobExperienceDiagnosticsRedaction.timeline(timeline)
      val secondevents = JobExperienceDiagnosticsRedaction.timeline(firstevents)
      Then("page arity, order, counts, IDs, time and boolean facts remain exact and the second projection is equal")
      once shouldBe twice
      firstevents shouldBe secondevents
      _preserved(tasks, once, timeline, firstevents)
      once.tasks.map(_.compensatesTaskId) shouldBe tasks.tasks.map(_.compensatesTaskId)
      tasks.tasks.head.component.get should include(secret)
      timeline.events.head.note.get should include(secret)
    }
  }

  private def _variants(value: String): Vector[String] =
    Vector("prefix-" + value, value + "-suffix", value.toUpperCase(java.util.Locale.ROOT), value + secret, " " + value)

  private def _private_fields(task: JobTaskReadModel): Vector[String] =
    Vector(task.component, task.service, task.operation, task.compensationActionRef,
      task.result.message, task.compensationFailureSummary).flatten

  private def _facets(task: JobTaskReadModel): Vector[String] =
    Vector(task.taskKind) ++ Vector(task.targetKind, task.relation, task.transactionRole,
      task.transactionScope, task.transactionOutcome, task.compensationStatus).flatten

  private def _preserved(rawtasks: JobTaskPage, safetasks: JobTaskPage,
    rawtimeline: JobTimelinePage, safetimeline: JobTimelinePage): Unit = {
    (safetasks.offset, safetasks.limit, safetasks.totalCount, safetasks.fetchedCount) shouldBe
      (rawtasks.offset, rawtasks.limit, rawtasks.totalCount, rawtasks.fetchedCount)
    (safetimeline.offset, safetimeline.limit, safetimeline.totalCount, safetimeline.fetchedCount) shouldBe
      (rawtimeline.offset, rawtimeline.limit, rawtimeline.totalCount, rawtimeline.fetchedCount)
    safetasks.tasks.map(task => (task.taskId, task.parentTaskId, task.compensatesTaskId, task.status,
      task.startedAt, task.finishedAt, task.result.success, task.recoveryRequired)) shouldBe
      rawtasks.tasks.map(task => (task.taskId, task.parentTaskId, task.compensatesTaskId, task.status,
        task.startedAt, task.finishedAt, task.result.success, task.recoveryRequired))
    safetimeline.events.map(event => (event.sequence, event.occurredAt, event.taskId, event.parentTaskId)) shouldBe
      rawtimeline.events.map(event => (event.sequence, event.occurredAt, event.taskId, event.parentTaskId))
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-audit, example:$example, rules:$rules, phase:69.7")
}
