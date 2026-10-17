package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.Identity
import JobRetryCrossProcessFixtures.Child

/*
 * Five real process leaves retain retry payment and original recovery safety.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobRetryCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-retry-cross-process, example:$example, rules:$rules, phase:69.7, slice:JM69-10C2")

  "E1_RT1 Genuine immediate automatic retry and fresh terminal JVM" should _scenario("E1", "RC1,RC2,RC3,RC6") {
    "retain both closed attempts and expanded native payment without fresh dispatch" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E1 RT1 RC1,RC2,RC3,RC6: a genuine Persistent Async task fails with RetryNow once, then succeeds on its second actual call")
      val parent = JobCrossProcessFixtures.identity()
      JobRetryCrossProcessFixtures.withCase { fixture =>
        When("the actual immediate writer drains both settlements, closes and exits, then an independently configured terminal JVM reads only the native JobId")
        val writer = fixture.writer("immediate-writer", 30)
        val reader = fixture.reader(writer.receipt.jobId, 30)
        Then("Succeeded8 retains Failed then Committed tasks, closed Failed/Succeeded attempts, paid(4,192,2,2), zero fresh work or mutation and actual separate process closure")
        _completed(parent, writer, reader, "immediate-writer")
      }
    }
  }

  "E2_RT2 Genuine default delayed retry and inclusive deadline" should _scenario("E2", "RC1,RC2,RC3,RC6") {
    "drain no retry before sixty seconds and one retry at due before fresh terminal reading" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E2 RT2 RC1,RC2,RC3,RC6: a genuine RetryLater failure pays its retry reservation and schedules the original default60s due, with independent split30")
      val parent = JobCrossProcessFixtures.identity()
      JobRetryCrossProcessFixtures.withCase { fixture =>
        When("the writer advances split30, then29, then1 seconds and drains each boundary before closing; a fresh terminal JVM starts only after actual writer exit")
        val writer = fixture.writer("delayed-writer", 30)
        val reader = fixture.reader(writer.receipt.jobId, 30)
        Then("strict-before drains0 and inclusive due drains1, terminal history/payment matches immediate retry and the fresh reader dispatches and mutates nothing")
        _completed(parent, writer, reader, "delayed-writer")
      }
    }
  }

  "E3_RT3 Genuine paid pending retry after graceful writer exit" should _scenario("E3", "RC1,RC4,RC5,RC6") {
    "retain Running5 and refuse started work without replay reset or refund" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E3 RT3 RC1,RC4,RC5,RC6: one actual failed RetryLater attempt has canonical settlement1, live delayed scheduling and expanded paid native authority")
      val parent = JobCrossProcessFixtures.identity()
      JobRetryCrossProcessFixtures.withCase { fixture =>
        When("the pending writer closes gracefully after first settlement, then a fresh configured JVM supplies its own original candidate and counted trusted Port/Task")
        val writer = fixture.writer("pending-writer", 30)
        val reader = fixture.reader(writer.receipt.jobId, 30, refused = true)
        Then("unchanged signed Running5/Pending/Failed and paid(4,192,2,2)/remaining1 produce original RecoveryRequired refusal, no Ready or replay/refund/mutation, and normal reader resource closure")
        _refused(parent, writer, reader, false)
      }
    }
  }

  "E4_RT4 Genuine pending retry interrupted by owned halt71" should _scenario("E4", "RC1,RC4,RC5,RC6") {
    "observe the actual abrupt exit and the same unchanged safe native refusal" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E4 RT4 RC1,RC4,RC5,RC6: an actual failed RetryLater attempt returns and settles canonically before real reserveRetry and live delayed scheduling")
      val parent = JobCrossProcessFixtures.identity()
      JobRetryCrossProcessFixtures.withCase { fixture =>
        When("the pending writer writes bounded pre-halt observations then halts its own JVM with71; a distinct reader starts against the same native database after that exit")
        val writer = fixture.writer("pending-crash-writer", 30)
        val reader = fixture.reader(writer.receipt.jobId, 30, refused = true)
        Then("the writer has actual exit71 and false graceful closure flags, while the fresh reader preserves Running5/native retry payment without dispatch or mutation and closes normally")
        _refused(parent, writer, reader, true)
      }
    }
  }

  "E5_RT5 Ordinary generated real completed retry process pairs" should _scenario("E5", "RC1,RC2,RC3,RC6") {
    "retain the exact native retry process oracle for at least twenty-four shrinking cases" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E5 RT5 RC1,RC2,RC3,RC6: ordinary Gen.oneOf immediate/delayed and Gen.choose(1,59), seed69710061L/min24/workers1, unique native/control/log namespace per case")
      val parent = JobCrossProcessFixtures.identity()
      val inputs = for {
        mode <- Gen.oneOf("immediate-writer", "delayed-writer")
        split <- Gen.choose(1, 59)
      } yield (mode, split)
      val property = Prop.forAll(inputs) { case (mode, split) =>
        Given("E5 RT5 RC1,RC2,RC3,RC6: independently generated retry mode and split with original runtime retry policy and a fresh owned namespace")
        JobRetryCrossProcessFixtures.withCase { fixture =>
          When("one genuine writer completes the actual automatic retry and exits before a distinct fresh terminal JVM starts")
          val writer = fixture.writer(mode, split)
          val reader = fixture.reader(writer.receipt.jobId, split)
          Then("the same signed task/attempt/payment and no-effect reader/process closure oracle holds for this actual pair")
          _completed(parent, writer, reader, mode)
          true
        }
      }
      When("ScalaCheck interprets the ordinary shrinking actual-process property with seed69710061L and minimum24 successes")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710061L))
        .withMinSuccessfulTests(24).withWorkers(1), property)
      Then("the actual generated process property passes with at least twenty-four successful cases")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _separate(parent: Identity, writer: Child, reader: Child): Unit = {
    val identities = Vector(parent, writer.identity, reader.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
    identities.map(_.pid).distinct.size shouldBe 3
    identities.map(_.uuid).distinct.size shouldBe 3
    reader.receipt("root") shouldBe writer.receipt("root")
    reader.receipt("sqlite") shouldBe writer.receipt("sqlite")
    reader.receipt.jobId shouldBe writer.receipt.jobId
    writer.exited shouldBe true
    reader.exited shouldBe true
  }
  private def _closure(child: Child, crash: Boolean): Unit = {
    child.exitCode shouldBe (if (crash) 71 else 0)
    Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")
      .foreach(key => child.receipt.flag(key) shouldBe !crash)
  }
  private def _paid(child: Child): Unit = {
    child.receipt.number("chargedtasks") shouldBe 4L
    child.receipt.number("historycredits") shouldBe 192L
    child.receipt.number("admissions") shouldBe 2L
    child.receipt.number("cancelcredits") shouldBe 2L
    child.receipt.number("claims") shouldBe 0L
    child.receipt.flag("cancelclaimed") shouldBe false
    child.receipt.number("sourcecalls") shouldBe 1L
    child.receipt.number("port") shouldBe 0L
    child.receipt.number("queue") shouldBe 0L
  }
  private def _zero_reader(child: Child): Unit = {
    Vector("port", "task", "canonical", "getters", "queue", "drain", "nativewrites", "lifewrites", "keyedcalls")
      .foreach(key => child.receipt.number(key) shouldBe 0L)
    child.receipt.flag("rowsunchanged") shouldBe true
  }
  private def _completed(parent: Identity, writer: Child, reader: Child, mode: String): Unit = {
    _separate(parent, writer, reader)
    _closure(writer, false)
    _closure(reader, false)
    writer.receipt("mode") shouldBe mode
    reader.receipt("mode") shouldBe "terminal-reader"
    Vector(writer, reader).foreach { child =>
      _paid(child)
      child.receipt.flag("ready") shouldBe true
      child.receipt("refusal") shouldBe ""
      child.receipt("status") shouldBe "succeeded"
      child.receipt.number("semanticRevision") shouldBe 8L
      child.receipt.number("taskcount") shouldBe 2L
      child.receipt.number("attemptcount") shouldBe 2L
      child.receipt.number("remaining") shouldBe 0L
      child.receipt.number("normalcount") shouldBe 2L
    }
    Vector("task", "canonical", "drain").foreach(key => writer.receipt.number(key) shouldBe 2L)
    writer.receipt.number("keyedcalls") shouldBe 8L
    writer.receipt.number("nativewrites") should be > 0L
    writer.receipt.number("lifewrites") should be > 0L
    reader.receipt.nativeFacts shouldBe writer.receipt.nativeFacts
    _zero_reader(reader)
  }
  private def _refused(parent: Identity, writer: Child, reader: Child, crash: Boolean): Unit = {
    _separate(parent, writer, reader)
    _closure(writer, crash)
    _closure(reader, false)
    writer.receipt("mode") shouldBe (if (crash) "pending-crash-writer" else "pending-writer")
    reader.receipt("mode") shouldBe "refused-reader"
    writer.receipt.flag("ready") shouldBe true
    writer.receipt("refusal") shouldBe ""
    Vector("task", "canonical", "drain").foreach(key => writer.receipt.number(key) shouldBe 1L)
    writer.receipt.number("keyedcalls") shouldBe 5L
    writer.receipt.number("nativewrites") should be > 0L
    writer.receipt.number("lifewrites") should be > 0L
    reader.receipt.flag("ready") shouldBe false
    reader.receipt("refusal") shouldBe "durable runtime startup refused"
    Vector(writer, reader).foreach { child =>
      _paid(child)
      child.receipt("status") shouldBe "running"
      child.receipt.number("semanticRevision") shouldBe 5L
      child.receipt.number("taskcount") shouldBe 1L
      child.receipt.number("remaining") shouldBe 1L
      child.receipt.number("normalcount") shouldBe 1L
    }
    reader.receipt.nativeFacts shouldBe writer.receipt.nativeFacts
    _zero_reader(reader)
  }
}
