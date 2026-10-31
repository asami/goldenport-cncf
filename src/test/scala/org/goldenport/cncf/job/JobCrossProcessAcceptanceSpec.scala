package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.{Child, Identity, identity, withCase}

/*
 * Actual OS processes consume original native terminal and payment contracts.
 *
 * @since   Oct.  4, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-cross-process-acceptance, example:$example, rules:$rules, phase:69.7, slice:JM69-10A")

  "CP1 Original native completion across actual JVMs" should _scenario("CP1", "XP1,XP2,XP3,XP4,XP5") {
    "retain exact signed terminal and paid facts with the existing fresh-reader result facade" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md CP1: one configured native writer, one Persistent Async task and a unique disk namespace")
      val parent = identity()
      withCase { fixture =>
        When("the writer executes once, commits through the original bridge, closes and exits before an independent reader starts on the same SQLite")
        val writer = fixture.writer(1)
        val reader = fixture.reader(writer.receipt.ids)
        Then("distinct actual process/runtime identities retain exact native ID/revision/Absent/payment facts, Ready Registered projections, the existing result facade, zero reader effects and successful closes")
        _accepted_pair(parent, writer, reader, 1)
      }
    }
  }

  "CP2 Empty native completion across actual JVMs" should _scenario("CP2", "XP1,XP2,XP4,XP6") {
    "keep a genuinely empty configured namespace free of paid-root fabrication" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md CP2: an actual empty configured writer namespace without fabricated records or quota singleton preseed")
      val parent = identity()
      withCase { fixture =>
        When("the empty writer native owner closes and exits before an independently empty reader starts from the same physical storage")
        val writer = fixture.writer(0)
        val reader = fixture.reader(writer.receipt.ids)
        Then("both separate processes are Ready with zero recovered candidates/jobs/body/port/mutations and no fabricated paid roots, and both close successfully")
        _accepted_pair(parent, writer, reader, 0)
        Vector(writer, reader).foreach { child =>
          Vector("candidates", "registered", "runtimejobs", "terminaljobs", "paidroots", "bodies",
            "ports", "creates", "writes", "deletes", "lifewrites").foreach(key => child.receipt.number(key) shouldBe 0L)
          Vector("lifetimepresent", "storagepresent", "blobquotapresent").foreach(key => child.receipt.flag(key) shouldBe false)
        }
      }
    }
  }

  "CP3 Foreign native candidate in an actual reader JVM" should _scenario("CP3", "XP1,XP3,XP7") {
    "refuse original startup before Ready or mutation while preserving completed native rows" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md CP3: an actually completed native writer and independently constructed foreign candidate subject/access for the reader")
      val parent = identity()
      withCase { fixture =>
        val writer = fixture.writer(1)
        When("the separate foreign reader invokes original startup authentication against that existing native record")
        val reader = fixture.reader(writer.receipt.ids, refused = true)
        Then("fixed redacted startup refusal precedes Ready/body/port/mutation, native rows remain unchanged and normal reader exit follows expected-refusal assertions and actual cleanup")
        _separate_closed(parent, writer, reader)
        writer.receipt.flag("ready") shouldBe true
        writer.receipt.number("bodies") shouldBe 1L
        reader.receipt.flag("ready") shouldBe false
        reader.receipt("refusal") shouldBe "durable runtime startup refused"
        reader.receipt.number("candidates") shouldBe 1L
        reader.receipt.flag("rowsunchanged") shouldBe true
        _zero_reader_effects(reader)
        reader.receipt.number("registered") shouldBe 0L
        reader.receipt.number("terminaljobs") shouldBe 0L
        reader.receipt.number("paidroots") shouldBe 1L
        reader.receipt.ids shouldBe writer.receipt.ids
        reader.receipt.jobFacts(0) shouldBe writer.receipt.jobFacts(0)
        reader.receipt("job.0.facade") shouldBe "None"
      }
    }
  }

  "CP4 Bounded generated native completion across actual JVMs" should _scenario("CP4", "XP1,XP2,XP3,XP4,XP5,XP8") {
    "preserve every exact native fact through ordinary shrinking with at least twenty-four successful generated cases" in {
      Given("docs/spec/job-cross-process-acceptance-contract.md CP4: Gen.choose(1,3), ordinary shrinking, seed 69710001L, minimum 24 successes and a unique namespace for each generated case")
      val parent = identity()
      val property = Prop.forAll(Gen.choose(1, 3)) { count =>
        Given("CP4 generated case: one unique native/control/log namespace and independently configured JVM fixtures")
        withCase { fixture =>
          When("the generated case launches a genuine sequential writer/reader pair and completes its chosen number of original Persistent Async tasks")
          val writer = fixture.writer(count)
          val reader = fixture.reader(writer.receipt.ids)
          Then("every exact ID/revision/native Absent/payment fact survives independent terminal projection with zero reader execution/payments/writes, distinct process identities and successful native closes")
          _accepted_pair(parent, writer, reader, count)
          true
        }
      }
      When("ScalaCheck executes the ordinary shrinking property with seed 69710001L and minimum 24 successful real-process cases")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710001L))
        .withMinSuccessfulTests(24).withWorkers(1), property)
      Then("the generated real-process property passes and checked.succeeded is at least 24")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _separate_closed(parent: Identity, writer: Child, reader: Child): Unit = {
    val identities = Vector(parent, writer.identity, reader.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
    identities.map(_.pid).distinct.size shouldBe 3
    identities.map(_.uuid).distinct.size shouldBe 3
    writer.receipt("root") shouldBe reader.receipt("root")
    writer.receipt("sqlite") shouldBe reader.receipt("sqlite")
    Vector(writer, reader).foreach { child =>
      child.exitCode shouldBe 0
      child.exited shouldBe true
      Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")
        .foreach(key => child.receipt.flag(key) shouldBe true)
    }
  }

  private def _zero_reader_effects(reader: Child): Unit =
    Vector("runtimejobs", "ports", "bodies", "getters", "queue", "drained", "creates", "writes", "deletes", "lifewrites")
      .foreach(key => reader.receipt.number(key) shouldBe 0L)

  private def _accepted_pair(parent: Identity, writer: Child, reader: Child, count: Int): Unit = {
    _separate_closed(parent, writer, reader)
    Vector(writer, reader).foreach { child =>
      child.receipt.flag("ready") shouldBe true
      child.receipt("refusal") shouldBe ""
      child.receipt.number("count") shouldBe count.toLong
      child.receipt.number("paidroots") shouldBe count.toLong
    }
    writer.receipt.number("bodies") shouldBe count.toLong
    writer.receipt.number("drained") shouldBe count.toLong
    writer.receipt.number("ports") shouldBe 0L
    reader.receipt.number("candidates") shouldBe count.toLong
    reader.receipt.number("registered") shouldBe count.toLong
    reader.receipt.number("terminaljobs") shouldBe count.toLong
    reader.receipt.flag("rowsunchanged") shouldBe true
    _zero_reader_effects(reader)
    reader.receipt.ids shouldBe writer.receipt.ids
    (0 until count).foreach { index =>
      reader.receipt.jobFacts(index) shouldBe writer.receipt.jobFacts(index)
      reader.receipt.number(s"job.$index.revision") shouldBe 6L
      reader.receipt(s"job.$index.status") shouldBe "succeeded"
      reader.receipt(s"job.$index.result") shouldBe "Succeeded(Absent)"
      reader.receipt.number(s"job.$index.chargedtasks") shouldBe 2L
      reader.receipt.number(s"job.$index.historycredits") shouldBe 96L
      reader.receipt.number(s"job.$index.admissions") shouldBe 1L
      reader.receipt.number(s"job.$index.cancelcredits") shouldBe 1L
      reader.receipt.number(s"job.$index.remaining") shouldBe 0L
      reader.receipt.number(s"job.$index.claims") shouldBe 0L
      reader.receipt.flag(s"job.$index.cancelclaimed") shouldBe false
      writer.receipt(s"job.$index.facade") shouldBe "Available"
      reader.receipt(s"job.$index.facade") shouldBe "UnavailableAfterRestart"
    }
    if (count > 0) {
      writer.receipt.number("creates") should be > 0L
      writer.receipt.number("writes") should be > 0L
      writer.receipt.number("lifewrites") should be > 0L
    }
  }
}
