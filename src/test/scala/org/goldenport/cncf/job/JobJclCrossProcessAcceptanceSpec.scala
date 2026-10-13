package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobJclCrossProcessFixtures.{Child, Identity, Scenario, identity, withCase}

/*
 * Seven public JCL scenarios consume actual native writer/fresh-reader JVMs.
 * Each generated invocation retains its own adjacent setup/action/oracles.
 *
 * @since   Oct.  6, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobJclCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "JM69-10C9 real public JCL across sequential JVMs" should {
    "E1_JX1 canonical inline Persistent root" which {
      "preserves original signed terminal/payment/privacy facts through a fresh native reader" in {
        Given("JX1: an independent configured native writer, the installed real CommandAction target and one canonical inline Persistent JCL root")
        val parent = identity()
        withCase(Scenario.Inline, "Root", 1) { fixture =>
          When("public submit_job_definition executes the selected scalar and the writer closes and exits before the reader starts")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("the sole actual Action is Root with no parent/reference; signed semantic6/Absent and paid2/97/1/1 survive Registered projection with no replay or writes and the existing UnavailableAfterRestart facade")
          _accepted_pair(parent, writer, reader, 1, stored = false)
        }
      }
    }
    "E2_JX2 root and finite declaration-order flow" which {
      "executes the root then two actual targets with independently selected parameter overrides" in {
        Given("JX2: canonical root/first/last JCL with independently declared root values, inherited shared value and step marker/position overrides")
        val parent = identity()
        withCase(Scenario.Inline, "Flow", 3) { fixture =>
          When("the real JCL bridge produces one three-task Job through public JobControl and its writer exits before the fresh reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("root/first/last inputs and canonical commits retain Root/no parent then Child/previous actual ID as parent/reference at each genuine boundary prefix and final descriptor; signed revision10 and paid6/226/1/1 retain three distinct normal IDs, no claims and zero reader execution")
          _accepted_pair(parent, writer, reader, 3, stored = false)
        }
      }
    }
    "E3_JX3 lightweight stored definition immutable submitted source" which {
      "independently reads native JobEntity source A and current retired JobDefinition source B plus separate signed metadata" in {
        Given("JX3: public create/get of active private source A, a legitimately issued opaque definition identity and the actual same-subsystem root target")
        val parent = identity()
        withCase(Scenario.Stored, "Snapshot", 1) { fixture =>
          When("public submit-by-reference completes A, public update/retire stores B, and the writer closes and exits before an independent reader searches the exact key and loads both real typed native entities")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("positive revision carriers preserve source A in the engine-created JobEntity and B/Retired in the current definition with the same saved ID/key; genuine signed ID/key/format metadata cross-matches separately, with zero B execution/replay/write or source exposure")
          _accepted_pair(parent, writer, reader, 1, stored = true)
          writer.receipt.flag("sourcechecked") shouldBe true
          reader.receipt.flag("sourcechecked") shouldBe true
          reader.receipt.number("entityloads") should be > 0L
        }
      }
    }
    "E4_JX4 pre-execution refusals" which {
      "rejects Draft, Retired, malformed and unsupported JCL before new native admission" in {
        Given("JX4: real publicly created Draft and Retired definitions, malformed/unsupported source selectors, and native counters/rows recorded after legitimate setup")
        val parent = identity()
        withCase(Scenario.Refusals, "Refuse", 1) { fixture =>
          When("the writer invokes all four actual public submissions and exits after typed refusal and unchanged-definition/native-baseline checks")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("four expected typed pre-execution refusals leave no Job, admission, source pair, lifetime/storage/publication or target execution, and both actual processes close")
          _separate_closed(parent, writer, reader)
          writer.receipt.number("refusals") shouldBe 4L
          Vector(writer, reader).foreach { child =>
            child.receipt.ids shouldBe Vector.empty
            Vector("actions", "commits", "sources", "pairs", "ports", "registered", "runtimejobs", "paidroots",
              "chargedtasks", "admissions").foreach(name => child.receipt.number(name) shouldBe 0L)
            Vector("lifetimepresent", "storagepresent", "blobquotapresent").foreach(name => child.receipt.flag(name) shouldBe false)
          }
          reader.receipt.number("sqlwrites") shouldBe 0L
          reader.receipt.number("entitywrites") shouldBe 0L
        }
      }
    }
    "E5_JX5 current foreign caller refusal and concealment" which {
      "retains full original ownership policy against genuine signed completion" in {
        Given("JX5: a genuine completed Persistent public JCL writer and an independently created foreign current subject/tenant in the fresh reader")
        val parent = identity()
        withCase(Scenario.Inline, "Foreign", 1) { fixture =>
          val writer = fixture.writer()
          When("the reader registers under original owner authority then calls queryVisible and management using its foreign caller")
          val reader = fixture.reader(writer.receipt.ids, foreign = true)
          Then("queryVisible preserves the original strict typed refusal, management conceals the Job, native bytes/payment remain unchanged and no source body, replay or mutation escapes")
          _accepted_pair(parent, writer, reader, 1, stored = false, foreign = true)
          reader.receipt.flag("foreignrefused") shouldBe true
          reader.receipt("facade") shouldBe "Refused"
        }
      }
    }
    "E6_JX6 explicitly Ephemeral canonical JCL" which {
      "executes its actual scalar while retaining an empty durable namespace" in {
        Given("JX6: canonical inline JCL explicitly selects Ephemeral persistence against the installed real command target")
        val parent = identity()
        withCase(Scenario.Ephemeral, "Volatile", 1) { fixture =>
          When("public JobControl executes the command once, its writer closes/exits, and a fresh independent native reader opens the same empty namespace")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("the actual live scalar and canonical commit succeed without durable Job/source/payment/Blob rows, and fresh reader effects and paid roots remain zero")
          _separate_closed(parent, writer, reader)
          writer.receipt.ids should have size 1
          writer.receipt.number("actions") shouldBe 1L
          writer.receipt.number("commits") shouldBe 1L
          writer.receipt("facade") shouldBe "Available"
          reader.receipt.ids shouldBe Vector.empty
          Vector(writer, reader).foreach { child =>
            Vector("sources", "pairs", "sqlwrites", "entitywrites", "publications", "paidroots", "revision",
              "chargedtasks", "historycredits", "admissions", "cancelcredits").foreach(name => child.receipt.number(name) shouldBe 0L)
            Vector("lifetimepresent", "storagepresent", "blobquotapresent").foreach(name => child.receipt.flag(name) shouldBe false)
          }
          _zero_reader(reader)
        }
      }
    }
    "E7_JX7 generated canonical finite flow" which {
      "preserves all public/native/descriptor/paid/privacy/cleanup oracles with ordinary shrinking" in {
        Given("JX7: dependent Gen alpha length1..8 and taskcount1..3, seed69710101L/min24/workers1 and an owned namespace per invocation")
        val parent = identity()
        val selections = for {
          length <- Gen.choose(1, 8)
          letters <- Gen.listOfN(length, Gen.alphaChar)
          count <- Gen.choose(1, 3)
        } yield (letters.mkString, count)
        val property = Prop.forAll(selections) { case (alpha, count) =>
          Given("JX7 generated invocation: independent bounded alpha/count selectors derive canonical root/finite-flow JCL in separate configured JVM fixtures")
          withCase(Scenario.Inline, alpha, count) { fixture =>
            When("each generated public writer executes its actual ordered command inputs and exits before its independent native reader receives only the opaque Job ID")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.ids)
            Then("all selected tasks retain the actual Root/Child previous-ID chain, genuine accumulated boundary prefixes and complete scalar/context/Action/payment/source/privacy/native/no-replay/resource oracles for this invocation")
            _accepted_pair(parent, writer, reader, count, stored = false)
            true
          }
        }
        When("ScalaCheck runs the ordinary shrinking genuine-process property with seed69710101L, minimum24 successes and workers1")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710101L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the complete real-process property passes with at least24 successes and no discarded cases")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
        checked.discarded shouldBe 0
      }
    }
  }

  private def _separate_closed(parent: Identity, writer: Child, reader: Child): Unit = {
    val identities = Vector(parent, writer.identity, reader.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
    identities.map(_.pid).distinct.size shouldBe 3
    identities.map(_.uuid).distinct.size shouldBe 3
    writer.exited shouldBe true
    reader.identity.start.isBefore(writer.identity.start) shouldBe false
    writer.receipt("root") shouldBe reader.receipt("root")
    writer.receipt("sqlite") shouldBe reader.receipt("sqlite")
    Vector(writer, reader).foreach { child =>
      child.exitcode shouldBe 0
      child.exited shouldBe true
      Vector("ready", "nativeunchanged", "privateabsent", "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed",
        "maintenanceclosed", "leaseunavailable", "workersclosed", "timersclosed", "schedulersclosed")
        .foreach(name => child.receipt.flag(name) shouldBe true)
      Vector("ports", "publications", "gets", "opens", "puts", "deletes", "queue", "drained", "pending", "executing", "leases")
        .foreach(name => child.receipt.number(name) shouldBe 0L)
    }
  }
  private def _zero_reader(reader: Child): Unit = {
    Vector("actions", "commits", "sources", "pairs", "ports", "sqlwrites", "entitywrites", "publications",
      "gets", "opens", "puts", "deletes", "runtimejobs", "queue", "drained")
      .foreach(name => reader.receipt.number(name) shouldBe 0L)
  }
  private def _accepted_pair(parent: Identity, writer: Child, reader: Child, count: Int,
    stored: Boolean, foreign: Boolean = false): Unit = {
    _separate_closed(parent, writer, reader)
    writer.receipt.ids should have size 1
    reader.receipt.ids shouldBe writer.receipt.ids
    writer.receipt.number("actions") shouldBe count.toLong
    writer.receipt.number("commits") shouldBe count.toLong
    writer.receipt.number("sources") shouldBe 4L + 2L * count
    writer.receipt.number("pairs") shouldBe 4L + 2L * count
    writer.receipt.flag("domainchecked") shouldBe true
    writer.receipt.number("sqlwrites") should be > 0L
    writer.receipt.number("entitywrites") should be > 0L
    reader.receipt.number("registered") shouldBe 1L
    _zero_reader(reader)
    Vector(writer, reader).foreach { child =>
      child.receipt.flag("descriptorchecked") shouldBe true
      child.receipt.flag("sourcechecked") shouldBe stored
      child.receipt.number("revision") shouldBe 4L + 2L * count
      child.receipt.number("paidroots") shouldBe 1L
      child.receipt.number("chargedtasks") shouldBe 2L * count
      child.receipt.number("historycredits") shouldBe 32L + 64L * count + (if (count == 1) { 1L } else { 2L })
      child.receipt.number("admissions") shouldBe 1L
      child.receipt.number("cancelcredits") shouldBe 1L
      child.receipt.number("remaining") shouldBe 0L
      child.receipt.number("normalids") shouldBe count.toLong
      child.receipt.number("claims") shouldBe 0L
      child.receipt.flag("cancelclaimed") shouldBe false
      child.receipt.flag("lifetimepresent") shouldBe true
      child.receipt.flag("storagepresent") shouldBe true
      child.receipt.flag("blobquotapresent") shouldBe false
    }
    reader.receipt.number("providerrevision") shouldBe writer.receipt.number("providerrevision")
    writer.receipt("facade") shouldBe "Available"
    reader.receipt("facade") shouldBe (if (foreign) { "Refused" } else { "UnavailableAfterRestart" })
    reader.receipt.flag("foreignrefused") shouldBe foreign
  }
}
