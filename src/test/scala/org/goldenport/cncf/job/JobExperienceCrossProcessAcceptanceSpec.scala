package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobExperienceCrossProcessFixtures.{CallerMode, Child, Identity, Selection, withCase}

/*
 * spec:job-cross-process-acceptance phase:69.7 slice:JM69-10C6
 * Eight semantic examples use the real production facade in independent JVMs.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobExperienceCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String): AfterWord = {
    afterWord(s"in spec:job-cross-process-acceptance, example:$example, rules:$rules, phase:69.7, slice:JM69-10C6")
  }

  "E1_UX1 Original owner Mine" should _scenario("E1_UX1", "UX1,UX2,UX3,UX8,UX9") {
    "a genuine paid owned scalar terminal" which {
      "presents exact Completed vocabulary and truthful unavailable result after restart" in {
        Given("UX1,UX2,UX3,UX8,UX9 E1_UX1: an independent alpha input and original owner User principal with no broad capabilities")
        val parent = JobExperienceCrossProcessFixtures.identity()
        withCase("Owner", CallerMode.Owner, Selection.Views) { fixture =>
          When("the actual writer completes six native boundaries, closes and exits before a fresh owner invokes production Mine list/get")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("the local exact id/status/times/progress/vocabulary/unavailable/no-control oracle, native preservation, zero service effects and actual process closure hold")
          _pair(parent, writer, reader, CallerMode.Owner, Selection.Views)
          reader.receipt.number("minecount") shouldBe 1L
          reader.receipt("resultAvailability") shouldBe "UnavailableAfterRestart"
        }
      }
    }
  }

  "E2_UX2 Original Operator capabilities" should _scenario("E2_UX2", "UX1,UX2,UX4,UX8,UX9") {
    "independent job_admin and content_admin native pairs" which {
      "admits Operator list/get and diagnostics while preserving foreign Mine hiding" in {
        Given("UX1,UX2,UX4,UX8,UX9 E2_UX2: independently selected foreign User principals with exactly one original administrator capability")
        val parent = JobExperienceCrossProcessFixtures.identity()
        Vector(CallerMode.JobAdmin, CallerMode.ContentAdmin).foreach { mode =>
          withCase("Admin", mode, Selection.Operator) { fixture =>
            When(s"the genuine writer exits before this separate ${mode.wire} JVM requests Operator then Mine and bounded diagnostics")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("Operator recovers the exact terminal id, Mine stays empty/not-found, diagnostics is native/redacted and every native/service/closure oracle holds")
            _pair(parent, writer, reader, mode, Selection.Operator)
            reader.receipt.number("operatorcount") shouldBe 1L
            reader.receipt.number("minecount") shouldBe 0L
            reader.receipt.number("diagnostictasks") shouldBe 1L
          }
        }
      }
    }
  }

  "E3_UX3 Original Mine ownership" should _scenario("E3_UX3", "UX2,UX4,UX5,UX8,UX9") {
    "foreign ordinary, foreign administrator and different-kind current subjects" which {
      "keeps Mine empty and exact get not-found without native or payload effects" in {
        Given("UX2,UX4,UX5,UX8,UX9 E3_UX3: independent foreign ordinary/admin cases and the matching principal with original Subsystem rather than User kind")
        val parent = JobExperienceCrossProcessFixtures.identity()
        Vector(CallerMode.Foreign, CallerMode.JobAdmin, CallerMode.SamePrincipalOtherKind).foreach { mode =>
          withCase("Foreign", mode, Selection.Foreign) { fixture =>
            When(s"an actual closed writer is followed by this fresh ${mode.wire} caller's Mine list/get through the production facade")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("an empty count0/cursorNone page and original not-found exact consequence retain complete native rows/payment/bytes and zero service effects")
            _pair(parent, writer, reader, mode, Selection.Foreign)
            reader.receipt.number("minecount") shouldBe 0L
            reader.receipt("resultAvailability") shouldBe "Hidden"
          }
        }
      }
    }
  }

  "E4_UX4 Actual anonymous caller" should _scenario("E4_UX4", "UX2,UX5,UX8,UX9") {
    "an Anonymous subject with an empty capability set" which {
      "refuses Mine list/get and diagnostics through the original semantic boundary" in {
        Given("UX2,UX5,UX8,UX9 E4_UX4: independent native startup and a separately selected actual Anonymous current subject")
        val parent = JobExperienceCrossProcessFixtures.identity()
        withCase("Anon", CallerMode.Anonymous, Selection.Anonymous) { fixture =>
          When("the real writer exits and a fresh Anonymous JVM invokes original Mine list/get and diagnostics")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("original authentication/not-found/diagnostic refusals preserve native identity/body/payment/physical state, zero service effects and true closure")
          _pair(parent, writer, reader, CallerMode.Anonymous, Selection.Anonymous)
          reader.receipt.number("minecount") shouldBe -1L
          reader.receipt("outcome") shouldBe "anonymous-refused"
        }
      }
    }
  }

  "E5_UX5 Original broad reader limits" should _scenario("E5_UX5", "UX2,UX4,UX8,UX9") {
    "separate job_view and content_manager current subjects" which {
      "refuses Operator and diagnostics while admitting an empty unannotated Application" in {
        Given("UX2,UX4,UX8,UX9 E5_UX5: independent foreign User callers with exactly job_view or content_manager and original terminal debug parameters empty")
        val parent = JobExperienceCrossProcessFixtures.identity()
        Vector(CallerMode.BroadReader, CallerMode.ContentManager).foreach { mode =>
          withCase("Broad", mode, Selection.Broad) { fixture =>
            When(s"the genuine writer exits before this fresh ${mode.wire} JVM requests Operator, Mine, diagnostics and Application(blog)")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("base read is admitted, Operator/diagnostics refuse, Application and Mine are empty/not-found and no stored annotation or native effect is invented")
            _pair(parent, writer, reader, mode, Selection.Broad)
            reader.receipt.number("applicationcount") shouldBe 0L
            reader.receipt.number("operatorcount") shouldBe -1L
            reader.receipt.number("minecount") shouldBe 0L
          }
        }
      }
    }
  }

  "E6_UX6 Native redacted diagnostics" should _scenario("E6_UX6", "UX2,UX4,UX6,UX8,UX9") {
    "both administrators with independently selected valid and invalid bounds" which {
      "preserves exact safe task/timeline facts and strict offset/limit refusal" in {
        Given("UX2,UX4,UX6,UX8,UX9 E6_UX6: independent job_admin/content_admin cases at offset0/limit100 and separately offset-1, limit0, limit101")
        val parent = JobExperienceCrossProcessFixtures.identity()
        Vector(CallerMode.JobAdmin, CallerMode.ContentAdmin).foreach { mode =>
          Vector(Selection.Diagnostics, Selection.NegativeOffset, Selection.ZeroLimit, Selection.OversizeLimit).foreach { selection =>
            withCase("Diag", mode, selection) { fixture =>
              When(s"this actual writer closes/exits and a distinct ${mode.wire} JVM invokes production diagnostics for ${selection.wire}")
              val writer = fixture.writer()
              val reader = fixture.reader(writer.receipt.id)
              Then("valid native identities/dates/counts/literal enum facts are exactly redacted, invalid bounds return the original refusal and all effects/native/closure observations hold")
              _pair(parent, writer, reader, mode, selection)
              if (selection == Selection.Diagnostics) {
                reader.receipt.number("diagnostictasks") shouldBe 1L
                reader.receipt.number("diagnostictimeline") should be > 0L
              } else reader.receipt.number("diagnostictasks") shouldBe -1L
            }
          }
        }
      }
    }
  }

  "E7_UX7 Durable terminal control" should _scenario("E7_UX7", "UX2,UX3,UX4,UX7,UX8,UX9") {
    "original authorized owner and both operator contexts" which {
      "exposes no controls and refuses all four mutations without work or refund" in {
        Given("UX2,UX3,UX4,UX7,UX8,UX9 E7_UX7: genuine paid Succeeded terminal and separate owner/job_admin/content_admin contexts")
        val parent = JobExperienceCrossProcessFixtures.identity()
        Vector(CallerMode.Owner, CallerMode.JobAdmin, CallerMode.ContentAdmin).foreach { mode =>
          withCase("Control", mode, Selection.Controls) { fixture =>
            When(s"the actual writer exits before this fresh ${mode.wire} JVM requests its admitted view and Cancel/Suspend/Resume/Retry")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("both owner/operator views have empty controls, every command refuses, complete signed/native/payment/quota/physical/runtime facts remain equal and original timer closes")
            _pair(parent, writer, reader, mode, Selection.Controls)
            reader.receipt.number("controlrefusals") shouldBe 4L
            reader.receipt("resultAvailability") shouldBe "UnavailableAfterRestart"
          }
        }
      }
    }
  }

  "E8_UX8 Ordinary generated caller acceptance" should _scenario("E8_UX8", "UX1,UX2,UX3,UX4,UX5,UX8,UX9") {
    "normal shrinking over alpha1..8 input and six closed current callers" which {
      "checks at least twenty-four genuine sequential writer-reader pairs" in {
        Given("UX1,UX2,UX3,UX4,UX5,UX8,UX9 E8_UX8: independent ordinary alpha1..8 and caller index0..5 generator with seed69710081L/min24/workers1")
        val parent = JobExperienceCrossProcessFixtures.identity()
        val modes = Vector(CallerMode.Owner, CallerMode.JobAdmin, CallerMode.ContentAdmin,
          CallerMode.Foreign, CallerMode.Anonymous, CallerMode.BroadReader)
        val inputs = for {
          length <- Gen.choose(1, 8)
          letters <- Gen.listOfN(length, Gen.alphaChar)
          mode <- Gen.choose(0, 5)
        } yield (letters.mkString, mode)
        val property = Prop.forAll(inputs) { case (input, index) =>
          Given("UX1,UX2,UX3,UX4,UX5,UX8,UX9 E8_UX8 generated case: independently selected input/current caller and unique native/control/log tree")
          val mode = modes(index)
          withCase(input, mode, Selection.Generated) { fixture =>
            When("this actual scalar Task writer completes/closes/exits before its independent current-caller JVM invokes Mine and Operator list/get")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("this exact pair obeys the independent owner/admin/foreign/anonymous/broad-reader oracle and full local native, zero-effect, process identity and closure contract")
            _pair(parent, writer, reader, mode, Selection.Generated)
            true
          }
        }
        When("ScalaCheck drives normal shrinking over actual JVM pairs with the original fixed seed, minimum twenty-four successes and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710081L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the actual property passed with at least twenty-four successful real pairs and zero discarded inputs")
        withClue(s"Job experience process property status=${checked.status}, succeeded=${checked.succeeded}, discarded=${checked.discarded}: ") {
          checked.passed shouldBe true
          checked.succeeded should be >= 24
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _pair(parent: Identity, writer: Child, reader: Child, mode: CallerMode, selection: Selection): Unit = {
    withClue(s"C6 caller=${mode.wire} case=${selection.wire} namespace=${writer.receipt("root")}: ") {
      val identities = Vector(parent, writer.identity, reader.identity)
      identities.map(_.pid).distinct.size shouldBe 3
      identities.map(_.start).distinct.size shouldBe 3
      identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
      identities.map(_.uuid).distinct.size shouldBe 3
      Vector(writer, reader).foreach { child =>
        child.exited shouldBe true
        child.exitcode shouldBe 0
        Vector("ready", "domainChecked", "nativeUnchanged", "ownerclose", "nodeclose", "subsystemclose", "sqlclose")
          .foreach(key => child.receipt.flag(key) shouldBe true)
        child.receipt.number("leases") shouldBe 0L
        child.receipt.number("revision") shouldBe 6L
        child.receipt("status") shouldBe "succeeded"
        Vector("chargedtasks" -> 2L, "historycredits" -> 96L, "admissions" -> 1L,
          "cancelcredits" -> 1L, "remaining" -> 0L).foreach { case (key, count) => child.receipt.number(key) shouldBe count }
      }
      reader.receipt.id shouldBe writer.receipt.id
      reader.receipt("root") shouldBe writer.receipt("root")
      reader.receipt("sqlite") shouldBe writer.receipt("sqlite")
      reader.receipt.number("providerrevision") shouldBe writer.receipt.number("providerrevision")
      writer.receipt.flag("zeroEffects") shouldBe false
      writer.receipt.flag("rawabsent") shouldBe false
      writer.receipt("resultAvailability") shouldBe "Available"
      writer.receipt("outcome") shouldBe "published"
      writer.receipt.number("tasks") shouldBe 1L
      writer.receipt.number("canonical") shouldBe 1L
      writer.receipt.number("sources") shouldBe 6L
      writer.receipt.number("puts") shouldBe 1L
      reader.receipt.flag("zeroEffects") shouldBe true
      reader.receipt.flag("rawabsent") shouldBe true
      Vector("tasks", "canonical", "sources", "puts").foreach(key => reader.receipt.number(key) shouldBe 0L)
      val owner = mode == CallerMode.Owner
      val anonymous = mode == CallerMode.Anonymous
      val admin = mode == CallerMode.JobAdmin || mode == CallerMode.ContentAdmin
      val operatorselected = Set(Selection.Operator, Selection.Generated, Selection.Broad, Selection.Controls).contains(selection)
      reader.receipt.number("minecount") shouldBe (if (owner) 1L else if (anonymous) -1L else 0L)
      reader.receipt.number("operatorcount") shouldBe (if (operatorselected && admin) 1L else -1L)
      reader.receipt("resultAvailability") shouldBe (if (owner || operatorselected && admin) "UnavailableAfterRestart" else "Hidden")
      reader.receipt("outcome") shouldBe (if (operatorselected && admin) "operator-visible" else if (owner) "owner-visible"
        else if (anonymous) "anonymous-refused" else "hidden")
    }
  }
}
