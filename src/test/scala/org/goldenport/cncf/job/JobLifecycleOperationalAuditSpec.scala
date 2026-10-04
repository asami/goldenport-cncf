package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SpanId, CorrelationId}
import org.goldenport.cncf.event.{EventId, EventPublishResult, EventRecord}
import org.goldenport.record.Record
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
final class JobLifecycleOperationalAuditSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifecycleOperationalAuditFixtures.*

  "LA1 Closed original source facts" should _scenario("LA1", "R1,R2,R3,R5,R9") {
    "observe exactly fourteen native names with actual captured status policy mode and retry facts" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R2,R3,R5,R9; LA1: closed names and actual immutable source records; seed69709501 shrinking")
      given ExecutionContext = JobExperienceOperationalAuditFixtures.context()
      val source = record(summon[ExecutionContext])
      val backend = new RecordingBackend
      val transformations = for {
        status <- Gen.oneOf(Vector(JobStatus.Submitted, JobStatus.Running, JobStatus.Suspended, JobStatus.Cancelled, JobStatus.Succeeded, JobStatus.Failed))
        persistence <- Gen.oneOf(JobPersistencePolicy.values.toVector)
        mode <- Gen.oneOf(JobRunMode.values.toVector)
        attempt <- Gen.choose(0, 9)
        name <- Gen.oneOf(names)
      } yield (status, persistence, mode, attempt, name)
      val property = Prop.forAll(transformations) { case (status, persistence, mode, attempt, name) =>
        val captured = source.copy(status = status, persistence = persistence, runMode = mode,
          retry = JobRetryState(JobRetryKind.Immediate, attempt, 10, exhausted = true, recoveryRequired = true, deadLetter = true, poison = true))
        backend.clear()
        withBackend(backend)(JobLifecycleOperationalAudit.observeUnavailable(captured, name, instant))
        val row = backend.calls.head.attributes
        row.getString("status") shouldBe Some(status.toString.toLowerCase(java.util.Locale.ROOT))
        row.getString("persistence") shouldBe Some(persistence.toString.toLowerCase(java.util.Locale.ROOT))
        row.getString("run-mode") shouldBe Some(mode.toString.toLowerCase(java.util.Locale.ROOT))
        row.getInt("attempt-count") shouldBe Some(attempt)
        row.getInt("max-attempts") shouldBe Some(10)
        row.getString("retry-kind") shouldBe Some("now")
        Vector("retry-exhausted", "recovery-required", "dead-letter", "poison").foreach(row.getBoolean(_) shouldBe Some(true))
        row.getString("occurred-at") shouldBe Some(instant.toString)
        row.getString("event-kind") shouldBe Some(name)
        row.getString("transport") shouldBe Some("unavailable")
        row.getString("outcome") shouldBe Some("unavailable")
        captured.retry.attemptCount shouldBe attempt
        true
      }
      When("all closed names and unknown variants are observed and actual transformed records are checked")
      backend.clear()
      withBackend(backend) {
        names.foreach(name => JobLifecycleOperationalAudit.observeUnavailable(source, name, instant))
        Vector("job.private", "JOB.SUBMITTED", "job.submitted.private", "job.retry.delayed.enqueued").foreach(name =>
          JobLifecycleOperationalAudit.observeUnavailable(source, name, instant))
      }
      Then("exactly the fourteen names appear and no unknown name is normalized or inferred")
      backend.calls.flatMap(_.attributes.getString("event-kind")) shouldBe names
      backend.calls.foreach { event =>
        event.attributes.getString("audit.kind") shouldBe Some("job-lifecycle")
        event.attributes.getString("operation") shouldBe Some("emit")
        event.attributes.getString("record-kind") shouldBe Some("call")
        event.attributes.getString("status") shouldBe Some("submitted")
      }
      When("ScalaCheck observes the actual captured bounded record transformations with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709501L)), property)
      Then("generated source facts stay original under every transformed closed enum and numeric facet")
      withClue(s"LA1 seed69709501 $checked ") { checked.passed shouldBe true }
      checked.succeeded should be >= 24
      source shouldBe record(summon[ExecutionContext])
    }
  }

  "LA2 Bounded original Task snapshots" should _scenario("LA2", "R3,R5,R6,R9") {
    "retain first hundred ordered associations true counts and safe closed facets without touching private task fields" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R3,R5,R6,R9; LA2: actual hostile zero hundred and hundred-one Task model vectors")
      given ExecutionContext = JobExperienceOperationalAuditFixtures.context()
      Vector(0, 100, 101).foreach { count =>
        val tasks = JobExperienceOperationalAuditFixtures.hostileTasks(count = count).tasks
        val source = record(summon[ExecutionContext], tasks)
        val original = source.copy()
        val backend = new RecordingBackend
        When(s"a captured $count-task source is observed after native unavailable publication")
        withBackend(backend)(JobLifecycleOperationalAudit.observeUnavailable(source, "job.failed", instant))
        Then("the call retains the true size and original first hundred rows retain their safe references in source order")
        backend.calls.head.attributes.getInt("task-count") shouldBe Some(count)
        backend.calls.head.attributes.getBoolean("truncated") shouldBe Some(count > 100)
        val rows = backend.lifecycle.filter(_.attributes.getString("record-kind").contains("task"))
        rows.size shouldBe math.min(count, 100)
        rows.flatMap(_.attributes.getString("task-id")) shouldBe tasks.take(100).map(_.taskId.value)
        rows.zip(tasks).foreach { case (row, task) =>
          row.attributes.getString("parent-task-id") shouldBe task.parentTaskId.map(_.value)
          row.attributes.getString("compensates-task-id") shouldBe task.compensatesTaskId.map(_.value)
          row.attributes.getString("task-kind") shouldBe Some("redacted")
          row.attributes.getBoolean("recovery-required") shouldBe Some(true)
          Vector("relation", "target-kind", "transaction-role", "transaction-scope", "transaction-outcome", "compensation-status").foreach(row.attributes.getString(_) shouldBe None)
          row.message should not include secret
        }
        source shouldBe original
      }
      val task = JobExperienceOperationalAuditFixtures.hostileTasks(count = 1).tasks.head
      val prefix = TaskId("native", "task", Some(instant), Some("")).value.getBytes(UTF_8).length
      val entropy = "秘" * ((512 - prefix) / 3) + "a" * ((512 - prefix) % 3)
      val exact = TaskId("native", "task", Some(instant), Some(entropy))
      val longid = TaskId("native", "task", Some(instant), Some(entropy + "a"))
      exact.value.getBytes(UTF_8).length shouldBe 512
      val safe = task.copy(taskId = exact, parentTaskId = Some(longid), compensatesTaskId = Some(exact),
        taskKind = "action", targetKind = Some("operation"), relation = Some("compensation"),
        transactionRole = Some("own"), transactionScope = Some("per-task"), transactionOutcome = Some("compensation-committed"), compensationStatus = Some("succeeded"))
      val source = record(summon[ExecutionContext], Vector(safe))
      val backend = new RecordingBackend
      When("multibyte boundary references and closed Task facets are observed")
      withBackend(backend)(JobLifecycleOperationalAudit.observeUnavailable(source, "job.failed", instant))
      Then("512 bytes are kept exactly and 513-byte optional parent is omitted")
      val row = backend.lifecycle.last.attributes
      row.getString("task-id") shouldBe Some(exact.value)
      row.getString("parent-task-id") shouldBe None
      row.getString("compensates-task-id") shouldBe Some(exact.value)
      row.getString("transaction-outcome") shouldBe Some("compensation-committed")
      row.getString("compensation-status") shouldBe Some("succeeded")
      backend.clear()
      val badjob = source.copy(id = JobId("native", "job", Some(instant), Some("x" * 600)))
      withBackend(backend)(JobLifecycleOperationalAudit.observeUnavailable(badjob, "job.failed", instant))
      backend.lifecycle.size shouldBe 1
      backend.calls.head.attributes.getBoolean("job-id-omitted") shouldBe Some(true)
      backend.calls.head.attributes.getInt("task-count") shouldBe Some(1)
    }
  }

  "LA3 Native store acknowledgement provenance" should _scenario("LA3", "R1,R4,R5,R8") {
    "return the exact original result and copy identity only from one matching nonnegative safe acknowledgement" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R4,R5,R8; LA3: actual typed native results with malformed and ambiguous ack shapes")
      withNative() { fixture =>
        given ExecutionContext = fixture.context
        val source = record(fixture.context)
        val ack = acknowledgement(source)
        val successful = Vector(Vector(ack), Vector(ack.copy(sequence = 0L)))
        val malformed = Vector(null, Vector(null), Vector(ack.copy(name = "job.running")), Vector(ack.copy(kind = "job.running")),
          Vector(ack.copy(attributes = Map("cncf.job.id" -> "foreign"))), Vector(ack, ack),
          Vector(ack.copy(sequence = -1L)), Vector(ack.copy(id = EventId("native", "event", Some(instant), Some("x" * 600)))),
          Vector(ack.copy(id = null)), Vector(ack.copy(attributes = null)), Vector.empty[EventRecord])
        val backend = new RecordingBackend
        val before = fixture.observations
        When("successful and malformed original store results are observed strictly by value")
        (successful ++ malformed).zipWithIndex.foreach { case (rows, index) =>
          val result = Consequence.success(rows)
          backend.clear()
          val observed = withBackend(backend)(JobLifecycleOperationalAudit.observeStore(source, "job.submitted", instant, result))
          Then("the same result instance remains and invalid ack facts never become persistence or identity claims")
          (observed eq result) shouldBe true
          val event = backend.calls.head.attributes
          event.getString("outcome") shouldBe Some("returned")
          event.getString("transport") shouldBe Some("store")
          event.getBoolean("event-identity-available") shouldBe Some(index < 2)
          event.getString("event-id") shouldBe (if (index < 2) Some(ack.id.value) else None)
          event.getLong("event-sequence") shouldBe (if (index < 2) Some(rows.head.sequence) else None)
          event.getBoolean("persisted") shouldBe None
          fixture.observations shouldBe before
        }
        val failure = Consequence.stateInvalid[Vector[EventRecord]](secret)
        backend.clear()
        val observed = withBackend(backend)(JobLifecycleOperationalAudit.observeStore(source, "job.submitted", instant, failure))
        (observed eq failure) shouldBe true
        backend.calls.head.attributes.getString("outcome") shouldBe Some("failed")
        backend.calls.head.attributes.getString("event-id") shouldBe None
        backend.calls.head.message should not include secret
        fixture.observations shouldBe before
      }
    }
  }

  "LA4 Bus and absent-sink facts" should _scenario("LA4", "R1,R3,R4,R8") {
    "retain normal success failure and unavailable facts without reconstructing bus identity or reading failure text" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R3,R4,R8; LA4: original typed bus results and a captured source without a publication sink")
      withNative() { fixture =>
        given ExecutionContext = fixture.context
        val source = record(fixture.context)
        val backend = new RecordingBackend
        val before = fixture.observations
        val results = Vector(Consequence.success(EventPublishResult(2, true)), Consequence.success(EventPublishResult(-1, false)),
          Consequence.stateInvalid[EventPublishResult](secret))
        When("the original normal bus results and an absent native sink are observed")
        withBackend(backend) {
          results.foreach { result => (JobLifecycleOperationalAudit.observeBus(source, "job.submitted", instant, result) eq result) shouldBe true }
          JobLifecycleOperationalAudit.observeUnavailable(source, "job.submitted", instant)
        }
        Then("bus returned booleans and nonnegative dispatch counts are exact while failure and unavailable rows omit all ack claims")
        backend.calls.flatMap(_.attributes.getString("outcome")) shouldBe Vector("returned", "returned", "failed", "unavailable")
        backend.calls.flatMap(_.attributes.getString("transport")) shouldBe Vector("bus", "bus", "bus", "unavailable")
        backend.calls.head.attributes.getBoolean("persisted") shouldBe Some(true)
        backend.calls(1).attributes.getBoolean("persisted") shouldBe Some(false)
        backend.calls.head.attributes.getInt("dispatched-count") shouldBe Some(2)
        backend.calls.drop(1).foreach(_.attributes.getInt("dispatched-count") shouldBe None)
        backend.calls.drop(2).foreach(_.attributes.getBoolean("persisted") shouldBe None)
        backend.calls.foreach { event =>
          event.attributes.getBoolean("event-identity-available") shouldBe Some(false)
          event.attributes.getString("event-id") shouldBe None
          event.attributes.getLong("event-sequence") shouldBe None
          event.message should not include secret
        }
        val nullresult: Consequence[EventPublishResult] = null
        val nullsuccess = Consequence.success[EventPublishResult](null)
        backend.clear()
        withBackend(backend) {
          JobLifecycleOperationalAudit.observeBus(source, "job.submitted", instant, nullresult) shouldBe null
          (JobLifecycleOperationalAudit.observeBus(source, "job.submitted", instant, nullsuccess) eq nullsuccess) shouldBe true
        }
        backend.events shouldBe empty
        fixture.observations shouldBe before
      }
    }
  }

  "LA5 Seventeen facet privacy" should _scenario("LA5", "R3,R5,R6,R9") {
    "keep original opaque references and presence flags while excluding private source and arbitrary Event objects" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R3,R5,R6,R9; LA5: all seventeen native privacy facets and a payload object whose toString throws")
      withNative() { fixture =>
        val context = ExecutionContext.withObservabilityContext(fixture.context, fixture.context.observability.copy(
          spanId = Some(SpanId("native", "span", "operation")), correlationId = Some(CorrelationId("native", "boundary"))))
        given ExecutionContext = context
        val facets = Vector("Job", "Task", "attempt", "Event", "Operation", "Workflow", "definition", "trace", "span", "CallTree", "payload", "recovery", "subject", "tenant", "credential", "provider", "path")
        val before = fixture.observations
        val values = for {
          length <- Gen.choose(0, 128)
          chars <- Gen.listOfN(length, Gen.oneOf('a', '秘', 'é', '\n', '\u0001', '"', '\\'))
        } yield secret + chars.mkString
        val property = Prop.forAll(values) { marker =>
          val privatevalues = facets.map(facet => facet -> (marker + facet)).toMap
          val hostile = new Object { override def toString: String = throw new IllegalStateException(marker) }
          val task = JobExperienceOperationalAuditFixtures.hostileTasks(marker, 1).tasks.head
          val source = record(context, Vector(task)).copy(
            debug = JobDebugInfo(Some(marker), privatevalues, Vector(marker),
              jobDefinitionSnapshot = Some(JobDefinitionSnapshot(marker, marker, None, Some(marker), Some(marker))),
              calltree = Some(Record.dataAuto("private" -> marker))),
            input = Some(JobInput(Vector(JobInputPayload(marker, filename = Some(marker), blobId = Some(marker), createdAt = instant)), createdAt = instant)),
            retry = JobRetryState(attemptCount = 3, recoveryRequired = true, lastFailureMessage = Some(marker)))
          val ack = acknowledgement(source, "job.failed").copy(payload = Map("private" -> hostile), attributes = Map("cncf.job.id" -> source.id.value) ++ privatevalues)
          val result = Consequence.success(Vector(ack))
          val backend = new RecordingBackend
          val observed = withBackend(backend)(JobLifecycleOperationalAudit.observeStore(source, "job.failed", instant, result))
          (observed eq result) shouldBe true
          backend.calls.head.attributes.getBoolean("event-identity-available") shouldBe Some(true)
          backend.calls.head.attributes.getString("event-id") shouldBe Some(ack.id.value)
          val row = backend.calls.head.attributes
          row.getString("job-id") shouldBe Some(source.id.value)
          row.getString("trace-id") shouldBe Some(context.observability.traceId.value)
          row.getString("span-id") shouldBe Some(context.observability.spanId.get.value)
          row.getString("correlation-id") shouldBe Some(context.observability.correlationId.get.value)
          Vector("definition-present", "input-present", "calltree-present", "recovery-required").foreach(row.getBoolean(_) shouldBe Some(true))
          row.getInt("attempt-count") shouldBe Some(3)
          backend.lifecycle.foreach { event =>
            event.message should not include marker
            privatevalues.values.foreach(value => event.message should not include value)
            event.attributes.asMap.values.foreach(value => String.valueOf(value) should not include marker)
            framed(event.message).asObject.get.keys.toSet shouldBe event.attributes.asMap.keySet
          }
          (ack.payload("private").asInstanceOf[AnyRef] eq hostile) shouldBe true
          source.debug.parameters shouldBe privatevalues
          source.input.get.payloads.head.blobId shouldBe Some(marker)
          task.result.message.get should include(marker)
          fixture.observations shouldBe before
          true
        }
        When("ScalaCheck observes actual captured native sources with seed69709501 and ordinary shrinking over seventeen hostile facet values")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709501L)), property)
        Then("all private entity values and raw payloads remain unformatted while canonical source and providers stay exact")
        withClue(s"LA5 seed69709501 $checked ") { checked.passed shouldBe true }
        checked.succeeded should be >= 24
      }
    }
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-lifecycle-operational-audit, example:$example, rules:$rules, phase:69.7")
}
