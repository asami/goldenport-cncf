package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.Identity
import JobScheduledCrossProcessFixtures.Child

/*
 * Four real sequential process scenarios consume unchanged native contracts.
 *
 * @since   Oct.  4, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobScheduledCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-scheduled-cross-process, example:$example, rules:$rules, phase:69.7, slice:JM69-10C1")

  "E1_SCH1 Native scheduled admission across three actual JVMs" should _scenario("E1", "SC1,SC2,SC3,SC6") {
    "retain paid ownership and execute exactly at the inclusive deadline before fresh terminal observation" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E1 SCH1 SC1,SC2,SC3,SC6: one genuine scheduled Persistent Async admission with independent delay10 and no body invocation")
      val parent = JobCrossProcessFixtures.identity()
      JobScheduledCrossProcessFixtures.withCase { fixture =>
        When("the graceful writer exits, a fresh native runtime retains original admission and executes at due, then a third JVM reads its terminal facts")
        val writer = fixture.writer(10)
        val reader = fixture.reader(writer.receipt.jobId, 10)
        val terminal = fixture.reader(writer.receipt.jobId, 10, "terminal-reader")
        Then("distinct processes retain exact native payment and revisions, no task runs strictly before due, one task and canonical completion run at due, and terminal reads have zero effects and actual closure")
        _accepted_trio(parent, writer, reader, terminal, false)
      }
    }
  }

  "E2_CR1 Actual abrupt unstarted writer exit" should _scenario("E2", "SC1,SC2,SC3,SC4,SC6") {
    "recover the committed native admission after the owned JVM halts with exit71" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E2 CR1 SC1,SC2,SC3,SC4,SC6: one genuine committed Submitted semantic1 schedule with delay10 and original native paid authority")
      val parent = JobCrossProcessFixtures.identity()
      JobScheduledCrossProcessFixtures.withCase { fixture =>
        When("the unstarted writer halts its own JVM after native assertions, then fresh scheduled and terminal readers start sequentially")
        val writer = fixture.writer(10, "unstarted-crash-writer")
        val reader = fixture.reader(writer.receipt.jobId, 10)
        val terminal = fixture.reader(writer.receipt.jobId, 10, "terminal-reader")
        Then("the actual writer exited71 without graceful closure, while the committed schedule executes once at due with unchanged payments and a closed fresh terminal reader")
        _accepted_trio(parent, writer, reader, terminal, true)
      }
    }
  }

  "E3_CR2 Actual interruption after native Running TaskStart" should _scenario("E3", "SC1,SC4,SC5,SC6") {
    "refuse ambiguous started work without dispatch mutation replay or refund in a fresh JVM" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E3 CR2 SC1,SC4,SC5,SC6: a genuine immediate Persistent Async task reaches Running semantic4 and Pending transaction with one actual native association")
      val parent = JobCrossProcessFixtures.identity()
      JobScheduledCrossProcessFixtures.withCase { fixture =>
        When("the actual task body halts its owned writer JVM before canonical completion and a distinct reader supplies independent original evidence and a fresh trusted Port/Task")
        val writer = fixture.writer(10, "running-crash-writer")
        val reader = fixture.reader(writer.receipt.jobId, 10, "refused-reader")
        Then("the writer exited71 after one run and zero canonical completions; original RecoveryRequired causes fixed startup refusal, no Ready/Port/body/replay/native writes/refund, exact unchanged native rows and normal reader closure")
        _separate(parent, Vector(writer, reader))
        _closure(writer, true)
        _closure(reader, false)
        writer.receipt("mode") shouldBe "running-crash-writer"
        reader.receipt("mode") shouldBe "refused-reader"
        writer.receipt.flag("ready") shouldBe true
        writer.receipt.number("task") shouldBe 1L
        writer.receipt.number("canonical") shouldBe 0L
        writer.receipt.number("nativewrites") should be > 0L
        reader.receipt.flag("ready") shouldBe false
        reader.receipt("refusal") shouldBe "durable runtime startup refused"
        reader.receipt.nativeFacts shouldBe writer.receipt.nativeFacts
        Vector(writer, reader).foreach { child =>
          child.receipt("status") shouldBe "running"
          child.receipt.number("semanticRevision") shouldBe 4L
          child.receipt.number("remaining") shouldBe 0L
          child.receipt.number("normalcount") shouldBe 1L
          _paid(child)
        }
        _zero_reader(reader)
      }
    }
  }

  "E4_SCH4 Generated inclusive scheduling across real JVM trios" should _scenario("E4", "SC1,SC2,SC3,SC6") {
    "retain exact native observations under ordinary shrinking for at least twenty-four independently selected delays" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md E4 SCH4 SC1,SC2,SC3,SC6: Gen.choose(1,20), ordinary shrinking, seed69710051L, minimum24, workers1 and a unique native namespace per case")
      val parent = JobCrossProcessFixtures.identity()
      val property = Prop.forAll(Gen.choose(1, 20)) { delay =>
        Given("E4 generated schedule: independent bounded delay and three fresh fixtures in one unique native/control/log namespace")
        JobScheduledCrossProcessFixtures.withCase { fixture =>
          When("the genuine scheduled writer exits before recovery checks delay-1 and inclusive due, followed by a fresh terminal JVM")
          val writer = fixture.writer(delay)
          val reader = fixture.reader(writer.receipt.jobId, delay)
          val terminal = fixture.reader(writer.receipt.jobId, delay, "terminal-reader")
          Then("the exact native schedule/payment/tail revisions execute once at due and fresh terminal reads preserve native rows without work or mutation, with distinct identities and actual closure")
          _accepted_trio(parent, writer, reader, terminal, false)
          true
        }
      }
      When("ScalaCheck interprets the ordinary shrinking real-process property with seed69710051L and minimum24 successful cases")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710051L))
        .withMinSuccessfulTests(24).withWorkers(1), property)
      Then("the real-process generated property passes with at least twenty-four successes")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _separate(parent: Identity, children: Vector[Child]): Unit = {
    val identities = parent +: children.map(_.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe identities.size
    identities.map(_.pid).distinct.size shouldBe identities.size
    identities.map(_.uuid).distinct.size shouldBe identities.size
    children.map(_.receipt("root")).distinct.size shouldBe 1
    children.map(_.receipt("sqlite")).distinct.size shouldBe 1
    children.map(_.receipt.jobId).distinct.size shouldBe 1
    children.foreach(_.exited shouldBe true)
  }
  private def _closure(child: Child, crash: Boolean): Unit = {
    child.exitCode shouldBe (if (crash) 71 else 0)
    Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")
      .foreach(key => child.receipt.flag(key) shouldBe !crash)
  }
  private def _paid(child: Child): Unit = {
    child.receipt.number("chargedtasks") shouldBe 2L
    child.receipt.number("historycredits") shouldBe 96L
    child.receipt.number("admissions") shouldBe 1L
    child.receipt.number("cancelcredits") shouldBe 1L
    child.receipt.number("claims") shouldBe 0L
    child.receipt.flag("cancelclaimed") shouldBe false
    child.receipt.number("sourcecalls") shouldBe 1L
  }
  private def _zero_reader(child: Child): Unit =
    Vector("port", "task", "canonical", "queue", "drain", "nativewrites")
      .foreach(key => child.receipt.number(key) shouldBe 0L)

  private def _accepted_trio(parent: Identity, writer: Child, reader: Child,
    terminal: Child, crash: Boolean): Unit = {
    _separate(parent, Vector(writer, reader, terminal))
    _closure(writer, crash)
    _closure(reader, false)
    _closure(terminal, false)
    writer.receipt("mode") shouldBe (if (crash) "unstarted-crash-writer" else "scheduled-writer")
    reader.receipt("mode") shouldBe "scheduled-reader"
    terminal.receipt("mode") shouldBe "terminal-reader"
    Vector(writer, reader, terminal).foreach { child =>
      child.receipt.flag("ready") shouldBe true
      child.receipt("refusal") shouldBe ""
      child.receipt.number("queue") shouldBe 0L
      _paid(child)
    }
    writer.receipt("status") shouldBe "submitted"
    writer.receipt.number("semanticRevision") shouldBe 1L
    writer.receipt.number("remaining") shouldBe 1L
    writer.receipt.number("normalcount") shouldBe 0L
    Vector("port", "task", "canonical", "drain").foreach(key => writer.receipt.number(key) shouldBe 0L)
    writer.receipt.number("nativewrites") should be > 0L
    reader.receipt("status") shouldBe "succeeded"
    reader.receipt.number("semanticRevision") shouldBe 6L
    reader.receipt.number("remaining") shouldBe 0L
    reader.receipt.number("normalcount") shouldBe 1L
    Vector("port", "task", "canonical", "drain").foreach(key => reader.receipt.number(key) shouldBe 1L)
    reader.receipt.number("nativewrites") should be > 0L
    terminal.receipt.nativeFacts shouldBe reader.receipt.nativeFacts
    _zero_reader(terminal)
  }
}
