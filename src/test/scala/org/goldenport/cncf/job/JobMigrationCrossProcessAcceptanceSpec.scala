package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.Identity
import JobMigrationCrossProcessFixtures.{CasePlan, Child, Form, withCase}

/*
 * spec:job-cross-process-acceptance phase:69.7 slice:JM69-10C5
 * Seven real-process examples consume the original migration and startup contract.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobMigrationCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String): AfterWord = {
    afterWord(s"in spec:job-cross-process-acceptance, example:$example, rules:MG1,MG2,MG3,MG4,MG5,MG6,MG7,MG8, phase:69.7, slice:JM69-10C5")
  }

  "E1_MG1 Genuine completed legacy Job" should _scenario("E1_MG1") {
    "explicit migration followed by independently fresh V2 startup" which {
      "retains the complete original body and payment while registering only terminal facts" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG3,MG7,MG8 E1_MG1: one actual Persistent Async Task completes six checkpoints and Published payment before original signed V1 preparation without payload")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(1, Form.AllV1, false, "job_admin")
        withCase(plan) { fixture =>
          When("the genuine writer closes and exits, then an enabled migrating JVM exits before an independently enabled fresh V2 JVM starts")
          val writer = fixture.writer()
          val migrating = fixture.reader("migrating-reader", writer.receipt.ids)
          val fresh = fixture.reader("fresh-reader", writer.receipt.ids)
          Then("only migration advances semantic/provider revision; complete local bodies and payment persist, terminal Registered/runtime Refused means Ready, audit clocks are three then zero, original seed/timer effects and actual closure hold")
          _positive_oracle(parent, writer, migrating, fresh, plan)
        }
      }
    }
  }

  "E2_MG2 Original exclusive owned input" should _scenario("E2_MG2") {
    "independent physical migration and fresh V2 preflight" which {
      "preserves exact original reference binding metadata bytes and payment" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG2,MG3,MG7,MG8 E2_MG2: genuine completion is followed by original putObserved of bytes[1,2,3], a signed input checkpoint and original V1 preparation")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(1, Form.AllV1, true, "job_admin")
        withCase(plan) { fixture =>
          When("a genuine closed writer is followed sequentially by separate enabled migrating and independently fresh V2 readers")
          val writer = fixture.writer()
          val migrating = fixture.reader("migrating-reader", writer.receipt.ids)
          val fresh = fixture.reader("fresh-reader", writer.receipt.ids)
          Then("each reader balances one actual stream, exact native metadata bytes and paid rows stay equal locally, one migration checkpoint and zero fresh writes coexist with original Ready terminal seed and maintenance closure")
          _positive_oracle(parent, writer, migrating, fresh, plan)
        }
      }
    }
  }

  "E3_MG3 Complete mixed native vector" should _scenario("E3_MG3") {
    "ordered original V1 V2 V1 candidates" which {
      "migrates exactly the legacy pair before a zero-write independently fresh startup" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG2,MG3,MG7,MG8 E3_MG3: three real completed Tasks with owned bytes retain candidate order and independently supplied fixed recipe, with native forms V1,V2,V1")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(3, Form.Mixed, true, "job_admin")
        withCase(plan) { fixture =>
          When("the writer exits before one enabled whole-vector migration reader and then a third independently fresh V2 reader")
          val writer = fixture.writer()
          val migrating = fixture.reader("migrating-reader", writer.receipt.ids)
          val fresh = fixture.reader("fresh-reader", writer.receipt.ids)
          Then("exactly two Job checkpoints and six audit writes/clocks preserve the middle complete V2 and all payments/physical facts, both complete original ordered reports and Ready seed/timer/closure oracles hold without redispatch")
          _positive_oracle(parent, writer, migrating, fresh, plan)
        }
      }
    }
  }

  "E4_MG4 Unchanged default migration policy" should _scenario("E4_MG4") {
    "absent and explicitly false original runtime inputs" which {
      "refuses legacy startup in separate JVMs without any migration or seed effects" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG4,MG7,MG8 E4_MG4: a genuine closed paid V1 writer without payload and two independently composed original providers")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(1, Form.AllV1, false, "job_admin")
        withCase(plan) { fixture =>
          When("the writer exits before a default-input refusal JVM exits and an explicitly false-input refusal JVM starts")
          val writer = fixture.writer()
          val defaultreader = fixture.reader("default-reader", writer.receipt.ids)
          val falsereader = fixture.reader("false-reader", writer.receipt.ids)
          Then("both fixed original refusals retain default bridge binding, exact native rows and payment, no owner/projection/paid seed/timer or native/business effects, unchanged runtime time and actual resource closure")
          _processes(parent, Vector(writer, defaultreader, falsereader), plan)
          _writer_oracle(writer, plan)
          _refusal_oracle(writer, defaultreader, plan, "default-reader")
          _refusal_oracle(writer, falsereader, plan, "false-reader")
        }
      }
    }
  }

  "E5_MG5 Original Operator admission" should _scenario("E5_MG5") {
    "enabled migration with empty capabilities" which {
      "refuses before additional native migration loads or bridge binding" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG5,MG7,MG8 E5_MG5: genuine closed paid V1 and a fresh provider whose original principal tenant and scopes are retained with an empty capability set")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(1, Form.AllV1, false, "job_admin")
        withCase(plan) { fixture =>
          When("the completed writer exits and the separate enabled unprivileged JVM invokes original migration admission")
          val writer = fixture.writer()
          val reader = fixture.reader("unauthorized-reader", writer.receipt.ids)
          Then("the exact startup refusal retains the source-before native load baseline, no bridge/owner/native writes/clocks/local paid seeds/timer or business effects, native payment unchanged and real closure")
          _processes(parent, Vector(writer, reader), plan)
          _writer_oracle(writer, plan)
          _refusal_oracle(writer, reader, plan, "unauthorized-reader")
        }
      }
    }
  }

  "E6_MG6 Whole-vector malformed tail" should _scenario("E6_MG6") {
    "genuine legacy prefix with only the final canonicalJson changed" which {
      "retains Corrupt and refuses every prefix checkpoint" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG6,MG7,MG8 E6_MG6: three genuinely completed legacy roots without payload precede one exact malformed tail text, every other native field/row stays equal")
        val parent = JobCrossProcessFixtures.identity()
        val plan = CasePlan(3, Form.InvalidTail, false, "job_admin")
        withCase(plan) { fixture =>
          When("the writer closes after its explicit single-field fault and a separate enabled Operator JVM preflights the complete ordered vector")
          val writer = fixture.writer()
          val reader = fixture.reader("invalid-reader", writer.receipt.ids)
          Then("complete preflight refuses before any prefix checkpoint, preserves all native rows and privileged stored payment, keeps the malformed suffix Corrupt without invented authentication, no owner/bridge/local seed/timer/native clocks or business effects and actual closure")
          _processes(parent, Vector(writer, reader), plan)
          _writer_oracle(writer, plan)
          _refusal_oracle(writer, reader, plan, "invalid-reader")
        }
      }
    }
  }

  "E7_MG7 Ordinary generated real migration" should _scenario("E7_MG7") {
    "dependent all-V1 or mixed vectors and original Operator choices" which {
      "checks at least twenty-four actual writer migrating and fresh V2 trios" in {
        Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG2,MG3,MG7,MG8 E7_MG7: ordinary Boolean/count-offset/payload/Operator generator, dependent mixed count three, seed69710091L/min24/workers1 and unique namespace per case")
        val parent = JobCrossProcessFixtures.identity()
        val inputs = for {
          mixed <- Gen.oneOf(false, true)
          extra <- Gen.choose(0, 2)
          payload <- Gen.oneOf(false, true)
          contentadmin <- Gen.oneOf(false, true)
        } yield (mixed, extra, payload, contentadmin)
        val property = Prop.forAll(inputs) { case (mixed, extra, payload, contentadmin) =>
          Given("docs/spec/job-cross-process-acceptance-contract.md MG1,MG2,MG3,MG7,MG8 E7_MG7 generated case: independent unique actual native/control/log tree and original fixtures for a valid dependent vector")
          val plan = CasePlan(if (mixed) 3 else extra + 1, if (mixed) Form.Mixed else Form.AllV1,
            payload, if (contentadmin) "content_admin" else "job_admin")
          withCase(plan) { fixture =>
            When("this generated case genuinely executes and closes its writer, then exits its enabled migration JVM before the independently fresh enabled V2 JVM starts")
            val writer = fixture.writer()
            val migrating = fixture.reader("migrating-reader", writer.receipt.ids)
            val fresh = fixture.reader("fresh-reader", writer.receipt.ids)
            Then("the complete original local native/body/revision/payment/physical/counter oracle and exact ordered Registered/Refused Ready facts, original paid seed/one timer and all real process/closure assertions hold for this actual trio")
            _positive_oracle(parent, writer, migrating, fresh, plan)
            true
          }
        }
        When("ScalaCheck executes ordinary shrinking over real JVM trios with seed69710091L, minimum twenty-four successes and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710091L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("checked.passed is true, at least twenty-four real cases succeeded and no discarded input supplied a replacement")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
        checked.discarded shouldBe 0
      }
    }
  }

  private def _processes(parent: Identity, children: Vector[Child], plan: CasePlan): Unit = {
    val identities = parent +: children.map(_.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe identities.size
    identities.map(_.pid).distinct.size shouldBe identities.size
    identities.map(_.uuid).distinct.size shouldBe identities.size
    children.foreach { child =>
      child.exitCode shouldBe 0
      child.exited shouldBe true
      child.receipt("root") shouldBe children.head.receipt("root")
      child.receipt("sqlite") shouldBe children.head.receipt("sqlite")
      child.receipt.ids shouldBe children.head.receipt.ids
      child.receipt.number("count") shouldBe plan.count.toLong
      child.receipt("form") shouldBe plan.form.wire
      child.receipt.flag("payload") shouldBe plan.payload
      child.receipt.number("paidroots") shouldBe plan.count.toLong
      child.receipt.number("activeleases") shouldBe 0L
      child.receipt.number("maintenanceTimersAfterClose") shouldBe 0L
      Vector("subsystemclosed", "nodeclosed", "sqlclosed").foreach(key => child.receipt.flag(key) shouldBe true)
    }
  }
  private def _writer_oracle(writer: Child, plan: CasePlan): Unit = {
    val receipt = writer.receipt
    receipt("mode") shouldBe "writer"
    receipt("capability") shouldBe plan.capability
    receipt.flag("ready") shouldBe true
    receipt("refusal") shouldBe ""
    receipt("ownerclosure") shouldBe "installed-closed"
    receipt.number("sourcecalls") shouldBe 1L
    receipt.number("v1count") shouldBe plan.v1Count.toLong
    receipt.number("bodies") shouldBe plan.count.toLong
    receipt.number("drained") shouldBe plan.count.toLong
    receipt.number("getters") should be > 0L
    receipt.number("evidence") shouldBe 6L * plan.count
    receipt.number("runtimejobs") shouldBe plan.count.toLong
    receipt.number("retainedTerminalCount") shouldBe 0L
    receipt.number("maintenanceTimersBeforeClose") shouldBe 1L
    receipt.flag("bridgebound") shouldBe true
    receipt.flag("rowsunchanged") shouldBe false
    Vector("creates", "nativewrites", "auditclocks", "jobwrites", "jobacks", "lifewrites", "ids")
      .foreach(key => receipt.number(key) should be > 0L)
    Vector("ports", "queue", "pending", "executing", "deletes", "blobdeletes", "physicalopens", "physicalcloses", "physicalbytes")
      .foreach(key => receipt.number(key) shouldBe 0L)
    receipt.number("blobputs") shouldBe (if (plan.payload) plan.count.toLong else 0L)
    (0 until plan.count).foreach { index =>
      val v1 = !(plan.form == Form.Mixed && index == 1)
      val revision = 6L + (if (plan.payload) 1L else 0L) + (if (v1) 1L else 0L)
      receipt.number(s"job.$index.ordinal") shouldBe index.toLong
      receipt.number(s"job.$index.format") shouldBe (if (v1) 1L else 2L)
      receipt.number(s"job.$index.revision") shouldBe revision
      receipt.number(s"job.$index.providerrevision") shouldBe revision
      receipt(s"job.$index.sourceformat") shouldBe receipt(s"job.$index.format")
      receipt(s"job.$index.sourcerevision") shouldBe receipt(s"job.$index.revision")
      receipt(s"job.$index.sourceproviderrevision") shouldBe receipt(s"job.$index.providerrevision")
      receipt(s"job.$index.terminalfact") shouldBe "PreparedSucceeded"
      receipt(s"job.$index.runtimefact") shouldBe "NotRecovered"
      receipt(s"job.$index.nativefact") shouldBe (if (plan.form == Form.InvalidTail && index == plan.count - 1) "Corrupt" else "Admitted")
      receipt(s"job.$index.status") shouldBe "succeeded"
      receipt(s"job.$index.result") shouldBe "Succeeded(Absent)"
      receipt(s"job.$index.quotaaccess") shouldBe "Authenticated"
      _payment_oracle(writer, index)
    }
  }
  private def _positive_oracle(parent: Identity, writer: Child, migrating: Child, fresh: Child, plan: CasePlan): Unit = {
    _processes(parent, Vector(writer, migrating, fresh), plan)
    _writer_oracle(writer, plan)
    migrating.receipt("mode") shouldBe "migrating-reader"
    fresh.receipt("mode") shouldBe "fresh-reader"
    Vector(migrating, fresh).foreach { child =>
      val receipt = child.receipt
      val count = if (child eq migrating) plan.v1Count.toLong else 0L
      receipt("capability") shouldBe plan.capability
      receipt.flag("ready") shouldBe true
      receipt("refusal") shouldBe ""
      receipt("ownerclosure") shouldBe "installed-closed"
      receipt.number("sourcecalls") shouldBe 1L
      receipt.number("v1count") shouldBe count
      receipt.number("jobwrites") shouldBe count
      receipt.number("jobacks") shouldBe count
      receipt.number("nativewrites") shouldBe 3L * count
      receipt.number("auditclocks") shouldBe 3L * count
      receipt.number("retainedTerminalCount") shouldBe plan.count.toLong
      receipt.number("maintenanceTimersBeforeClose") shouldBe 1L
      receipt.flag("bridgebound") shouldBe true
      receipt.flag("rowsunchanged") shouldBe (count == 0L)
      receipt.number("physicalopens") shouldBe (if (plan.payload) plan.count.toLong else 0L)
      receipt.number("physicalcloses") shouldBe receipt.number("physicalopens")
      receipt.number("physicalbytes") shouldBe (if (plan.payload) 3L * plan.count else 0L)
      Vector("runtimejobs", "ports", "evidence", "bodies", "getters", "queue", "drained", "creates", "deletes",
        "lifewrites", "blobputs", "blobdeletes", "ids", "pending", "executing")
        .foreach(key => receipt.number(key) shouldBe 0L)
      (0 until plan.count).foreach { index =>
        val source = if (child eq migrating) writer.receipt else migrating.receipt
        val advance = if (child eq migrating) source.number(s"job.$index.format") == 1L else false
        receipt.number(s"job.$index.ordinal") shouldBe index.toLong
        receipt.number(s"job.$index.format") shouldBe 2L
        receipt.number(s"job.$index.revision") shouldBe source.number(s"job.$index.revision") + (if (advance) 1L else 0L)
        receipt.number(s"job.$index.providerrevision") shouldBe source.number(s"job.$index.providerrevision") + (if (advance) 1L else 0L)
        receipt(s"job.$index.sourceformat") shouldBe source(s"job.$index.format")
        receipt(s"job.$index.sourcerevision") shouldBe source(s"job.$index.revision")
        receipt(s"job.$index.sourceproviderrevision") shouldBe source(s"job.$index.providerrevision")
        receipt(s"job.$index.terminalfact") shouldBe "Registered"
        receipt(s"job.$index.runtimefact") shouldBe "Refused"
        receipt(s"job.$index.nativefact") shouldBe "Admitted"
        receipt(s"job.$index.status") shouldBe "succeeded"
        receipt(s"job.$index.result") shouldBe "Succeeded(Absent)"
        receipt(s"job.$index.quotaaccess") shouldBe "Authenticated"
        receipt.paymentFacts(index) shouldBe writer.receipt.paymentFacts(index)
        _payment_oracle(child, index)
      }
    }
  }
  private def _refusal_oracle(writer: Child, reader: Child, plan: CasePlan, mode: String): Unit = {
    val receipt = reader.receipt
    receipt("mode") shouldBe mode
    receipt("capability") shouldBe (if (mode == "unauthorized-reader") "" else plan.capability)
    receipt.flag("ready") shouldBe false
    receipt("refusal") shouldBe "durable runtime startup refused"
    receipt("ownerclosure") shouldBe "not-installed"
    receipt.number("sourcecalls") shouldBe 1L
    receipt.number("v1count") shouldBe (if (mode == "invalid-reader") plan.count - 1L else plan.count.toLong)
    receipt.flag("bridgebound") shouldBe Set("default-reader", "false-reader").contains(mode)
    receipt.flag("rowsunchanged") shouldBe true
    Vector("jobwrites", "jobacks", "nativewrites", "auditclocks", "creates", "deletes", "lifewrites", "blobputs",
      "blobdeletes", "physicalopens", "physicalcloses", "physicalbytes", "ids", "ports", "evidence", "bodies", "getters",
      "queue", "drained", "runtimejobs", "retainedTerminalCount", "pending", "executing", "maintenanceTimersBeforeClose")
      .foreach(key => receipt.number(key) shouldBe 0L)
    (0 until plan.count).foreach { index =>
      val bad = mode == "invalid-reader" && index == plan.count - 1
      receipt.number(s"job.$index.ordinal") shouldBe index.toLong
      receipt(s"job.$index.terminalfact") shouldBe (if (Set("default-reader", "false-reader").contains(mode)) "Refused" else "NotRecovered")
      receipt(s"job.$index.runtimefact") shouldBe (if (Set("default-reader", "false-reader").contains(mode)) "Refused" else "NotRecovered")
      receipt(s"job.$index.nativefact") shouldBe (if (bad) "Corrupt" else "Admitted")
      receipt(s"job.$index.quotaaccess") shouldBe (if (bad) "PrivilegedNativeObservation" else "Authenticated")
      receipt.paymentFacts(index) shouldBe writer.receipt.paymentFacts(index)
      _payment_oracle(reader, index)
      if (bad) {
        receipt(s"job.$index.sourceformat") shouldBe "Corrupt"
        Vector("format", "revision", "providerrevision", "sourcerevision", "sourceproviderrevision", "status", "result")
          .foreach(key => receipt(s"job.$index.$key") shouldBe "NotAdmitted")
      } else {
        Vector("format", "revision", "providerrevision", "status", "result")
          .foreach(key => receipt(s"job.$index.$key") shouldBe writer.receipt(s"job.$index.$key"))
        receipt(s"job.$index.sourceformat") shouldBe writer.receipt(s"job.$index.format")
        receipt(s"job.$index.sourcerevision") shouldBe writer.receipt(s"job.$index.revision")
        receipt(s"job.$index.sourceproviderrevision") shouldBe writer.receipt(s"job.$index.providerrevision")
      }
    }
  }
  private def _payment_oracle(child: Child, index: Int): Unit = {
    child.receipt.number(s"job.$index.chargedtasks") shouldBe 2L
    child.receipt.number(s"job.$index.historycredits") shouldBe 96L
    child.receipt.number(s"job.$index.admissions") shouldBe 1L
    child.receipt.number(s"job.$index.cancelcredits") shouldBe 1L
    child.receipt.number(s"job.$index.remaining") shouldBe 0L
    child.receipt(s"job.$index.normalid") should not be empty
    child.receipt.number(s"job.$index.claims") shouldBe 0L
    child.receipt.flag(s"job.$index.cancelclaimed") shouldBe false
  }
}
