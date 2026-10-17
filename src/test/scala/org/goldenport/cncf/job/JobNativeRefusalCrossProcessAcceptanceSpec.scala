package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.Identity
import JobNativeRefusalCrossProcessFixtures.{Child, Fault, withCase}

/*
 * spec:job-cross-process-acceptance phase:69.7 slice:JM69-10C4
 * Seven actual native fault examples consume the original startup contracts.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobNativeRefusalCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String) = {
    afterWord(s"spec:job-cross-process-acceptance, example:$example, rules:NF1,NF2,NF3,NF4,NF5,NF6,NF7,NF8, phase:69.7, slice:JM69-10C4")
  }

  "Malformed native canonical text" should _scenario("E1_NF1") {
    "independently fresh original startup" which {
      "retains Corrupt and Refused facts with the real original payment" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E1_NF1: a genuine original Persistent Async Task completes six signed native checkpoints and Published payment before the explicit malformed-text fault")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the writer executes, damages only its native Job text, closes and exits before a separate original configured reader starts")
          val writer = fixture.writer(1, Fault.Malformed, 0)
          val reader = fixture.reader(writer.receipt.ids, Fault.Malformed, 0)
          Then("Corrupt/Refused causes fixed Degraded startup and bad quota API refusal with unchanged stored Published payment, authenticated pre-fault quota, zero effects and actual resource closure")
          _oracle(parent, writer, reader, Fault.Malformed, 1, 0)
        }
      }
    }
  }

  "Native integrity mismatch" should _scenario("E2_NF2") {
    "the original signed-record consumer" which {
      "refuses an altered digest without repair or re-signing" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E2_NF2: genuine completed V2 execution precedes changing only its exact unsignedBodySha256 to a distinct lowercase 64-character digest")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the completed writer applies that one fault and exits before an independent reader invokes original native startup")
          val writer = fixture.writer(1, Fault.Integrity, 0)
          val reader = fixture.reader(writer.receipt.ids, Fault.Integrity, 0)
          Then("the digest fault remains Corrupt, all runtime facts are Refused, bad quota API refuses while stored Published payment equals authenticated pre-fault facts and every reader effect is zero")
          _oracle(parent, writer, reader, Fault.Integrity, 1, 0)
        }
      }
    }
  }

  "Unsupported native format version" should _scenario("E3_NF3") {
    "independent original version admission" which {
      "keeps version three corrupt without downgrade or migration" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E3_NF3: genuine signed V2 completion precedes changing only root format.version from two to three without changing the body or signing")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the writer closes after that single unsupported-version input and a separate configured JVM starts from the same SQLite")
          val writer = fixture.writer(1, Fault.FutureVersion, 0)
          val reader = fixture.reader(writer.receipt.ids, Fault.FutureVersion, 0)
          Then("Corrupt/Refused prevents Ready ownership, bad quota API refuses and unchanged stored Published payment matches authenticated pre-fault facts without native normalization")
          _oracle(parent, writer, reader, Fault.FutureVersion, 1, 0)
        }
      }
    }
  }

  "Noncanonical native text" should _scenario("E4_NF4") {
    "canonical storage admission in a fresh JVM" which {
      "refuses a single trailing ASCII space without rewriting storage" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E4_NF4: exact genuine canonical signed V2 terminal text receives only one trailing ASCII space after completed Task assertions")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the writer exits and a fresh original runtime consumes the actual noncanonical native row")
          val writer = fixture.writer(1, Fault.Noncanonical, 0)
          val reader = fixture.reader(writer.receipt.ids, Fault.Noncanonical, 0)
          Then("the admissible JSON grammar produces native Corrupt/Refused and fixed bad quota API refusal with retained stored Published payment, authenticated pre-fault quota and zero reader effects")
          _oracle(parent, writer, reader, Fault.Noncanonical, 1, 0)
        }
      }
    }
  }

  "Missing native Job with retained payment" should _scenario("E5_NF5") {
    "an independently declared original opaque candidate" which {
      "preserves Missing distinctly from Corrupt without recreating or refunding its root" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E5_NF5: genuine completed paid execution precedes deleting only its native Job row while storage and Published lifetime authority remain")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the writer closes and exits before a separate configured reader loads that original opaque candidate from native storage")
          val writer = fixture.writer(1, Fault.Missing, 0)
          val reader = fixture.reader(writer.receipt.ids, Fault.Missing, 0)
          Then("terminal Missing and runtime Refused include fixed bad quota API refusal with unchanged stored Published payment matching authenticated pre-fault facts, no ghost refund, recreation or execution")
          _oracle(parent, writer, reader, Fault.Missing, 1, 0)
        }
      }
    }
  }

  "Mixed healthy and malformed native Jobs" should _scenario("E6_NF6") {
    "the full ordered original candidate vector" which {
      "keeps both healthy terminal summaries while refusing the whole runtime" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E6_NF6: three genuinely completed paid Tasks retain original opaque order and only the middle native Job is deliberately malformed")
        val parent = JobCrossProcessFixtures.identity()
        withCase { fixture =>
          When("the completed writer exits and an independent reader starts the original cached vector with ordinals zero, one and two")
          val writer = fixture.writer(3, Fault.Malformed, 1)
          val reader = fixture.reader(writer.receipt.ids, Fault.Malformed, 1)
          Then("Registered/Corrupt/Registered and all Refused preserve body-free good summaries, absent bad query, Degraded ownership, authenticated healthy/pre-fault quota and fixed bad quota API refusal with unchanged stored Published payment")
          _oracle(parent, writer, reader, Fault.Malformed, 3, 1)
        }
      }
    }
  }

  "Bounded generated native refusal" should _scenario("E7_NF7") {
    "ordinary dependent shrinking over all five faults" which {
      "checks at least twenty-four genuine writer and independently fresh reader pairs" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E7_NF7: Gen.oneOf five native faults, Gen.choose one through three Jobs, dependent bad index, seed69710081L and twenty-four successes")
        val parent = JobCrossProcessFixtures.identity()
        val inputs = for {
          fault <- Gen.oneOf(Fault.values.toVector)
          count <- Gen.choose(1, 3)
          badindex <- Gen.choose(0, count - 1)
        } yield (fault, count, badindex)
        val property = Prop.forAll(inputs) { case (fault, count, badindex) =>
          Given("docs/spec/job-cross-process-acceptance-contract.md NF1..NF8 E7_NF7 generated case: a unique native/control/log namespace and independently constructed original writer and reader fixtures")
          withCase { fixture =>
            When("this generated case really executes its Tasks, changes exactly one native row, exits its writer and starts a separate configured reader")
            val writer = fixture.writer(count, fault, badindex)
            val reader = fixture.reader(writer.receipt.ids, fault, badindex)
            Then("the complete classification, good-summary, no-effect, process and closure oracle holds with fixed bad quota API refusal, unchanged stored Published payment and authenticated healthy/pre-fault quota for this actual pair")
            _oracle(parent, writer, reader, fault, count, badindex)
            true
          }
        }
        When("ScalaCheck executes this ordinary shrinking real-JVM property with seed69710081L, minSuccessfulTests twenty-four and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710081L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("checked.passed is true and at least twenty-four actual-process cases satisfy fixed bad quota API refusal, unchanged stored Published payment and authenticated healthy/pre-fault quota without discarded replacement")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
        checked.discarded shouldBe 0
      }
    }
  }

  private def _oracle(parent: Identity, writer: Child, reader: Child, fault: Fault, count: Int, badindex: Int): Unit = {
    val identities = Vector(parent, writer.identity, reader.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
    identities.map(_.pid).distinct.size shouldBe 3
    identities.map(_.uuid).distinct.size shouldBe 3
    writer.receipt("root") shouldBe reader.receipt("root")
    writer.receipt("sqlite") shouldBe reader.receipt("sqlite")
    writer.receipt.ids shouldBe reader.receipt.ids
    Vector(writer, reader).foreach { child =>
      child.exitCode shouldBe 0
      child.exited shouldBe true
      child.receipt("fault") shouldBe fault.wire
      child.receipt.number("badindex") shouldBe badindex.toLong
      child.receipt.number("count") shouldBe count.toLong
      child.receipt.number("paidroots") shouldBe count.toLong
      child.receipt.number("activeleases") shouldBe 0L
      Vector("subsystemclosed", "nodeclosed", "sqlclosed").foreach(key => child.receipt.flag(key) shouldBe true)
    }
    writer.receipt("terminalobservation") shouldBe "pre-fault"
    writer.receipt.flag("ready") shouldBe true
    writer.receipt("refusal") shouldBe ""
    writer.receipt.flag("ownerinstalled") shouldBe true
    writer.receipt("ownerclosure") shouldBe "installed-closed"
    writer.receipt.number("bodies") shouldBe count.toLong
    writer.receipt.number("drained") shouldBe count.toLong
    writer.receipt.number("ports") shouldBe 0L
    Vector("creates", "writes", "lifewrites").foreach(key => writer.receipt.number(key) should be > 0L)
    reader.receipt("terminalobservation") shouldBe "startup-read"
    reader.receipt.flag("ready") shouldBe false
    reader.receipt("refusal") shouldBe "durable runtime startup refused"
    reader.receipt.flag("ownerinstalled") shouldBe false
    reader.receipt("ownerclosure") shouldBe "not-installed"
    reader.receipt.number("candidates") shouldBe count.toLong
    reader.receipt.number("registered") shouldBe (count - 1).toLong
    reader.receipt.number("terminaljobs") shouldBe (count - 1).toLong
    reader.receipt.flag("rowsunchanged") shouldBe true
    Vector("runtimejobs", "ports", "bodies", "getters", "queue", "drained", "creates", "writes", "deletes",
      "lifewrites", "ids", "clocks", "timers").foreach(key => reader.receipt.number(key) shouldBe 0L)
    (0 until count).foreach { index =>
      writer.receipt(s"job.$index.terminalfact") shouldBe "PreFaultSucceeded"
      writer.receipt.number(s"job.$index.revision") shouldBe 6L
      writer.receipt(s"job.$index.status") shouldBe "succeeded"
      writer.receipt(s"job.$index.result") shouldBe "Succeeded(Absent)"
      writer.receipt(s"job.$index.quotaaccess") shouldBe "Authenticated"
      reader.receipt.number(s"job.$index.ordinal") shouldBe index.toLong
      reader.receipt(s"job.$index.runtimefact") shouldBe "Refused"
      reader.receipt(s"job.$index.quotaaccess") shouldBe
        (if (index == badindex) "RefusedNativeObservation" else "Authenticated")
      reader.receipt.paymentFacts(index) shouldBe writer.receipt.paymentFacts(index)
      reader.receipt.number(s"job.$index.chargedtasks") shouldBe 2L
      reader.receipt.number(s"job.$index.historycredits") shouldBe 96L
      reader.receipt.number(s"job.$index.admissions") shouldBe 1L
      reader.receipt.number(s"job.$index.cancelcredits") shouldBe 1L
      reader.receipt.number(s"job.$index.remaining") shouldBe 0L
      reader.receipt.number(s"job.$index.claims") shouldBe 0L
      reader.receipt.flag(s"job.$index.cancelclaimed") shouldBe false
      if (index == badindex) {
        reader.receipt(s"job.$index.terminalfact") shouldBe (if (fault == Fault.Missing) "Missing" else "Corrupt")
        Vector("revision", "providerrevision", "status", "result")
          .foreach(key => reader.receipt(s"job.$index.$key") shouldBe "NotAdmitted")
      } else {
        reader.receipt(s"job.$index.terminalfact") shouldBe "Registered"
        Vector("revision", "providerrevision", "status", "result")
          .foreach(key => reader.receipt(s"job.$index.$key") shouldBe writer.receipt(s"job.$index.$key"))
      }
    }
  }
}
