package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobNotificationCrossProcessFixtures.{Child, Identity, Mode, withCase}

/*
 * spec:job-cross-process-acceptance phase:69.7 slice:JM69-10C7
 * Actual Job lifecycle notifications and fresh native no-replay acceptance.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobNotificationCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String): AfterWord = {
    afterWord(s"in spec:job-cross-process-acceptance, example:$example, rules:$rules, phase:69.7, slice:JM69-10C7")
  }

  "E1_NT1 Actual accepted lifecycle notification" should _scenario("E1_NT1", "NT1,NT2,NT3,NT4,NT5,NT7,NT8") {
    "a genuine paid Persistent Async scalar and enabled accepting provider" which {
      "forwards one exact request and sent diagnostic then recovers without replay" in {
        Given("NT1,NT2,NT3,NT4,NT5,NT7,NT8 E1_NT1: original User and native component-scoped accepting provider with independently selected alpha input")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Accept", Mode.Accepted) { fixture =>
          When("the actual Task completes through six native boundaries and EventBus, writer closes/exits, and a fresh enabled provider reader opens its opaque id")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("literal request/sent diagnostic, exact successful native publication, zero fresh provider/events, read effects and actual process closure hold")
          _pair(parent, writer, reader, Mode.Accepted)
          writer.receipt.number("providercalls") shouldBe 1L
          reader.receipt.number("providercalls") shouldBe 0L
        }
      }
    }
  }

  "E2_NT2 Actual internal lifecycle" should _scenario("E2_NT2", "NT1,NT2,NT5,NT7,NT8") {
    "an enabled provider and real Async Job with service/operation and no web.app" which {
      "skips forwarding and still recovers the successful native Job without replay" in {
        Given("NT1,NT2,NT5,NT7,NT8 E2_NT2: original paid scalar Task with web.service/post and web.operation/publish but no web.app")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Noapp", Mode.NoApp) { fixture =>
          When("the genuine lifecycle emits job.succeeded before writer close/exit and independent enabled-provider native startup")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("writer provider/forwarding diagnostics stay zero and fresh provider/events stay zero with exact native success and zero read effects")
          _pair(parent, writer, reader, Mode.NoApp)
          writer.receipt.number("diagnostics") shouldBe 0L
          writer.receipt.number("succeededevents") shouldBe 1L
        }
      }
    }
  }

  "E3_NT3 Actual disabled provider" should _scenario("E3_NT3", "NT1,NT2,NT5,NT7,NT8") {
    "an actual configured disabled component and application-visible Async Job" which {
      "has no default forwarding rule and no fresh enabled-provider replay" in {
        Given("NT1,NT2,NT5,NT7,NT8 E3_NT3: actual component/provider enabled=false and independently selected genuine app-visible scalar Task")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Disabled", Mode.Disabled) { fixture =>
          When("real Job completion occurs then closed writer is followed by an independently composed enabled accepting reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("writer call/diagnostic counts are zero, fresh event/provider counts are zero and original native success/payment/resources remain intact")
          _pair(parent, writer, reader, Mode.Disabled)
          writer.receipt.number("providercalls") shouldBe 0L
          reader.receipt.number("events") shouldBe 0L
        }
      }
    }
  }

  "E4_NT4 Actual provider refusal" should _scenario("E4_NT4", "NT1,NT3,NT4,NT5,NT7,NT8") {
    "an actual provider returning accepted=false for the genuine successful Job" which {
      "records the exact public refusal without changing Job success or replaying it" in {
        Given("NT1,NT3,NT4,NT5,NT7,NT8 E4_NT4: real native Persistent Async scalar and component-scoped provider refusing its one request")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Refuse", Mode.Refused) { fixture =>
          When("default EventBus forwarding invokes the provider and writer exits before enabled fresh-reader registration")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("the literal request and The notification provider refused delivery. diagnostic preserve Succeeded/native facts and reader provider0")
          _pair(parent, writer, reader, Mode.Refused)
          writer.receipt.number("diagnostics") shouldBe 1L
          writer.receipt("status") shouldBe "succeeded"
        }
      }
    }
  }

  "E5_NT5 Actual provider Consequence failure" should _scenario("E5_NT5", "NT1,NT3,NT4,NT5,NT7,NT8") {
    "a provider returning a structured failure with the fixed private fixture marker" which {
      "emits only the exact allowed public diagnostic and preserves native completion" in {
        Given("NT1,NT3,NT4,NT5,NT7,NT8 E5_NT5: actual provider Consequence failure contains provider-private-credential-c7 while the real scalar Task succeeds")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Failure", Mode.Failure) { fixture =>
          When("real default forwarding handles the failure before closed-writer and independent enabled native reader startup")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("the exact did not accept delivery. public note and marker-free literal request/diagnostic retain successful Job/native facts and no replay")
          _pair(parent, writer, reader, Mode.Failure)
          writer.receipt.number("providercalls") shouldBe 1L
          reader.receipt.flag("nativeUnchanged") shouldBe true
        }
      }
    }
  }

  "E6_NT6 Actual NonFatal provider exception" should _scenario("E6_NT6", "NT1,NT3,NT4,NT5,NT7,NT8") {
    "an actual provider throwing IllegalStateException with the fixed private fixture marker" which {
      "isolates the exception and exposes only the exact allowed forwarding failure" in {
        Given("NT1,NT3,NT4,NT5,NT7,NT8 E6_NT6: real successful paid Task and provider throwing NonFatal IllegalStateException containing the private marker")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Throw", Mode.Throw) { fixture =>
          When("genuine lifecycle forwarding catches the actual provider exception then writer closes/exits and enabled fresh native reader opens")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("the exact failed while accepting delivery. note contains no private detail, no exception escapes completion and fresh provider/events stay zero")
          _pair(parent, writer, reader, Mode.Throw)
          writer.receipt.number("diagnostics") shouldBe 1L
          reader.receipt.flag("zeroEffects") shouldBe true
        }
      }
    }
  }

  "E7_NT7 Actual unsafe application reference" should _scenario("E7_NT7", "NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8") {
    "an enabled accepting provider and genuine Task with web.app ../private" which {
      "retains allowed metadata while omitting the unsafe action link and never replays" in {
        Given("NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8 E7_NT7: original User submits actual Async scalar with app ../private and post/publish context")
        val parent = JobNotificationCrossProcessFixtures.identity()
        withCase("Unsafe", Mode.UnsafeApp) { fixture =>
          When("real lifecycle emits the application-visible event then closed writer is followed by the separate enabled-provider native reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.id)
          Then("one exact request has actionUrl=None and original app metadata, one exact sent diagnostic and unchanged Job/native/effects with fresh calls0")
          _pair(parent, writer, reader, Mode.UnsafeApp)
          writer.receipt.number("providercalls") shouldBe 1L
          reader.receipt.number("events") shouldBe 0L
        }
      }
    }
  }

  "E8_NT8 Ordinary generated lifecycle acceptance" should _scenario("E8_NT8", "NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8,NT9") {
    "normal shrinking over alpha1..8 inputs and seven closed writer modes" which {
      "checks at least twenty-four actual sequential JVM pairs with independent local oracles" in {
        Given("NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8,NT9 E8_NT8: ordinary alpha1..8/seven-mode generator, seed69710091L/min24/workers1")
        val parent = JobNotificationCrossProcessFixtures.identity()
        val modes = Vector(Mode.Accepted, Mode.NoApp, Mode.Disabled, Mode.Refused, Mode.Failure, Mode.Throw, Mode.UnsafeApp)
        val inputs = for {
          length <- Gen.choose(1, 8)
          letters <- Gen.listOfN(length, Gen.alphaChar)
          mode <- Gen.choose(0, 6)
        } yield (letters.mkString, mode)
        val property = Prop.forAll(inputs) { case (input, index) =>
          Given("NT1..NT9 E8_NT8 generated case: independent alpha scalar/mode and unique owned native/control/log namespace")
          val mode = modes(index)
          withCase(input, mode) { fixture =>
            When("this real Task writer completes through native owner/EventBus/provider and closes/exits before its independently enabled reader opens the opaque id")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.id)
            Then("this actual pair obeys its literal mode-specific request/diagnostic oracle, native/payment/physical preservation, zero read effects and closure contract")
            _pair(parent, writer, reader, mode)
            true
          }
        }
        When("ScalaCheck performs ordinary shrinking on genuine JVM pairs with the fixed seed, minimum twenty-four successes and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710091L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the actual property passed with at least twenty-four successful real pairs and zero discarded cases")
        withClue(s"Job notification process property status=${checked.status}, succeeded=${checked.succeeded}, discarded=${checked.discarded}: ") {
          checked.passed shouldBe true
          checked.succeeded should be >= 24
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _pair(parent: Identity, writer: Child, reader: Child, mode: Mode): Unit = {
    withClue(s"C7 notification=${mode.wire} namespace=${writer.receipt("root")}: ") {
      val identities = Vector(parent, writer.identity, reader.identity)
      identities.map(_.pid).distinct.size shouldBe 3
      identities.map(_.start).distinct.size shouldBe 3
      identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
      identities.map(_.uuid).distinct.size shouldBe 3
      Vector(writer, reader).foreach { child =>
        child.exited shouldBe true
        child.exitcode shouldBe 0
        child.receipt("notificationmode") shouldBe mode.wire
        Vector("ready", "domainChecked", "nativeUnchanged", "ownerclose", "nodeclose", "subsystemclose",
          "sqlclose", "providerclose", "schedulerclose").foreach(key => child.receipt.flag(key) shouldBe true)
        child.receipt.number("leases") shouldBe 0L
        child.receipt.number("timers") shouldBe 0L
        child.receipt.number("revision") shouldBe 6L
        child.receipt("status") shouldBe "succeeded"
        child.receipt.number("fixturetasks") shouldBe 0L
        Vector("chargedtasks" -> 2L, "historycredits" -> 96L, "admissions" -> 1L,
          "cancelcredits" -> 1L, "remaining" -> 0L).foreach { case (key, count) => child.receipt.number(key) shouldBe count }
      }
      reader.receipt.id shouldBe writer.receipt.id
      reader.receipt("root") shouldBe writer.receipt("root")
      reader.receipt("sqlite") shouldBe writer.receipt("sqlite")
      reader.receipt.number("providerrevision") shouldBe writer.receipt.number("providerrevision")
      writer.receipt("resultAvailability") shouldBe "Available"
      writer.receipt.flag("zeroEffects") shouldBe false
      writer.receipt.flag("rawabsent") shouldBe false
      writer.receipt.number("tasks") shouldBe 1L
      writer.receipt.number("canonical") shouldBe 1L
      writer.receipt.number("getters") should be > 0L
      writer.receipt.number("sources") shouldBe 6L
      writer.receipt.number("puts") shouldBe 1L
      writer.receipt.number("succeededevents") shouldBe 1L
      writer.receipt.number("events") should be > 0L
      writer.receipt.number("minecount") shouldBe -1L
      val skipped = mode == Mode.NoApp || mode == Mode.Disabled
      writer.receipt.number("providercalls") shouldBe (if (skipped) 0L else 1L)
      writer.receipt.number("diagnostics") shouldBe (if (skipped) 0L else 1L)
      reader.receipt("resultAvailability") shouldBe "UnavailableAfterRestart"
      reader.receipt.flag("zeroEffects") shouldBe true
      reader.receipt.flag("rawabsent") shouldBe true
      reader.receipt.number("minecount") shouldBe 1L
      Vector("tasks", "getters", "canonical", "sources", "puts", "providercalls", "diagnostics", "succeededevents", "events")
        .foreach(key => reader.receipt.number(key) shouldBe 0L)
    }
  }
}
