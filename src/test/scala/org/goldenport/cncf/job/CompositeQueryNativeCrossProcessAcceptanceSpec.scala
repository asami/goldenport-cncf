package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CompositeQueryNativeCrossProcessFixtures.{Child, Identity, Persistence, withCase}

/*
 * spec:job-cross-process-acceptance phase:69.7 slice:JM69-10C8
 * Real Query aggregates, canonical native diagnostics and fresh no-replay reads.
 *
 * @since   Oct.  5, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class CompositeQueryNativeCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String): AfterWord = {
    afterWord(s"in spec:job-cross-process-acceptance, example:$example, rules:$rules, phase:69.7, slice:JM69-10C8")
  }

  "E1_CQNT1 Actual single native diagnostic" should _scenario("E1_CQNT1", "CQNT1,CQNT2,CQNT3,CQNT4,CQNT6,CQNT7,CQNT8,CQ2-R5,CQ2-R6,CQ2-R12") {
    "one independently selected private scalar Query under original User subject-a" which {
      "keeps the aggregate local and registers the canonical body-free Job in a fresh JVM" in {
        Given("CQNT1,CQNT2,CQNT3,CQNT4,CQNT6,CQNT7,CQNT8 CQ2-R5,CQ2-R6,CQ2-R12 E1_CQNT1: original configured SQLite/keyed owner, original REALTIME engine and independent target-c8 Query fixture")
        val parent = CompositeQueryNativeCrossProcessFixtures.identity()
        withCase("Single", 1, Persistence.PersistentDiagnostics) { fixture =>
          When("CompositeQueryEngine(native.subsystem) executes the real Query, awaits actual Void and native monitor release, then writer closes/exits before fresh MANUAL reader startup")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("the exact successful local scalar/context and six signed paid boundaries yield Succeeded(Absent), immutable wrapper descriptor, authorized unavailable result and no replay")
          _pair(parent, writer, reader, Persistence.PersistentDiagnostics, 1, false)
        }
      }
    }
  }

  "E2_CQNT2 Actual pair of native diagnostics" should _scenario("E2_CQNT2", "CQNT1,CQNT2,CQNT3,CQNT4,CQNT6,CQNT7,CQNT8,CQ2-R4,CQ2-R5,CQ2-R6,CQ2-R12") {
    "two real required payload queries on the independent immutable target alias" which {
      "retain declaration order and separate genuine native Job identities without storing their bodies" in {
        Given("CQNT1,CQNT2,CQNT3,CQNT4,CQNT6,CQNT7,CQNT8 CQ2-R4,CQ2-R5,CQ2-R6,CQ2-R12 E2_CQNT2: original realtime native worker pool, two independent private scalar branches and distinct caller/target context cells")
        val parent = CompositeQueryNativeCrossProcessFixtures.identity()
        withCase("Pair", 2, Persistence.PersistentDiagnostics) { fixture =>
          When("the public owning-subsystem facade completes two genuine Async diagnostic jobs and bounded canonical observations before writer exit and a separate native reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("both exact ordered scalar values stay invocation-local while each distinct native rev6/Absent record has paid2,96,1,1 remaining0, one original task, no claims and zero fresh effects")
          _pair(parent, writer, reader, Persistence.PersistentDiagnostics, 2, false)
          writer.receipt.ids.distinct.size shouldBe 2
        }
      }
    }
  }

  "E3_CQNT3 Actual default ephemeral composition" should _scenario("E3_CQNT3", "CQNT1,CQNT2,CQNT3,CQNT5,CQNT6,CQNT7,CQNT8,CQ2-R5,CQ2-R6,CQ2-R12") {
    "one or two genuine target Queries with the unchanged default Ephemeral policy" which {
      "succeed on the native owning engine while leaving its signed Job and payment namespace empty" in {
        Given("CQNT1,CQNT2,CQNT3,CQNT5,CQNT6,CQNT7,CQNT8 CQ2-R5,CQ2-R6,CQ2-R12 E3_CQNT3: initially empty original configured native owner, default Ephemeral policy and independent private scalar input")
        val parent = CompositeQueryNativeCrossProcessFixtures.identity()
        Vector(1, 2).foreach { count =>
          withCase("Ephem", count, Persistence.Ephemeral) { fixture =>
            When("the same public native facade executes real default-policy Queries, returns actual Ephemeral IDs and exits before an independent reader with no candidates")
            val writer = fixture.writer()
            val reader = fixture.reader(Vector.empty)
            Then("the exact scalar/context aggregate succeeds, keyed source/native Job/payment/publication counts remain zero, and the fresh reader has no prior IDs or Query execution")
            _pair(parent, writer, reader, Persistence.Ephemeral, count, false)
          }
        }
      }
    }
  }

  "E4_CQNT4 Actual foreign current caller" should _scenario("E4_CQNT4", "CQNT1,CQNT2,CQNT4,CQNT6,CQNT7,CQNT8,CQ2-R6,CQ2-R12") {
    "a completed original subject-a diagnostic and independently fresh foreign User" which {
      "preserve native startup authority while refusing foreign current-caller reads" in {
        Given("CQNT1,CQNT2,CQNT4,CQNT6,CQNT7,CQNT8 CQ2-R6,CQ2-R12 E4_CQNT4: real successful paid diagnostic, original internal startup grant and foreign current User without fabricated overview privilege")
        val parent = CompositeQueryNativeCrossProcessFixtures.identity()
        withCase("Foreign", 1, Persistence.PersistentDiagnostics) { fixture =>
          When("the closed writer is followed by an independent reader that registers the original native candidate and invokes actual current-caller queryVisible and management APIs")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids, foreign = true)
          Then("queryVisible preserves the exact original ownership refusal and management conceals existence as successful None without result or private metadata leak, with unchanged native success/payment/rows and zero Query redispatch or writes")
          _pair(parent, writer, reader, Persistence.PersistentDiagnostics, 1, true)
        }
      }
    }
  }

  "E5_CQNT5 Ordinary generated native composition" should _scenario("E5_CQNT5", "CQNT1,CQNT2,CQNT3,CQNT4,CQNT5,CQNT6,CQNT7,CQNT8,CQNT9,CQ2-R4,CQ2-R5,CQ2-R6,CQ2-R12") {
    "ordinary shrinking over independent alpha1..8, count1..2 and the two persistence policies" which {
      "checks at least twenty-four actual sequential writer and reader JVM pairs" in {
        Given("CQNT1..CQNT9 CQ2-R4,CQ2-R5,CQ2-R6,CQ2-R12 E5_CQNT5: ordinary bounded generator, seed69710081L/min24/workers1 and real process-local oracles")
        val parent = CompositeQueryNativeCrossProcessFixtures.identity()
        val inputs = for {
          length <- Gen.choose(1, 8)
          letters <- Gen.listOfN(length, Gen.alphaChar)
          count <- Gen.choose(1, 2)
          persistent <- Gen.oneOf(false, true)
        } yield (letters.mkString, count, persistent)
        val property = Prop.forAll(inputs) { case (alpha, count, persistent) =>
          Given("CQNT1..CQNT9 CQ2-R4,CQ2-R5,CQ2-R6,CQ2-R12 E5_CQNT5 generated case: independent alpha/count/policy and unique owned native/control/log namespace")
          val persistence = if (persistent) Persistence.PersistentDiagnostics else Persistence.Ephemeral
          withCase(alpha, count, persistence) { fixture =>
            When("this original native realtime public facade performs its actual Query branches, awaits canonical Void/checkpoint, closes/exits and opens a fresh manual reader using only opaque persistent IDs")
            val writer = fixture.writer()
            val reader = fixture.reader(if (persistent) writer.receipt.ids else Vector.empty)
            Then("this actual pair satisfies exact local aggregate/context, native or empty persistence, confidentiality, caller/no-replay/no-effect and complete owned cleanup oracles")
            _pair(parent, writer, reader, persistence, count, false)
            true
          }
        }
        When("ScalaCheck executes the ordinary shrinking property with the fixed seed, at least twenty-four successes and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710081L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the property actually passes with at least twenty-four successful real pairs and zero discarded cases")
        withClue(s"C8 property status=${checked.status}, succeeded=${checked.succeeded}, discarded=${checked.discarded}: ") {
          checked.passed shouldBe true
          checked.succeeded should be >= 24
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _pair(parent: Identity, writer: Child, reader: Child, persistence: Persistence,
    count: Int, foreign: Boolean): Unit = {
    withClue(s"C8 persistence=${persistence.wire} count=$count namespace=${writer.receipt("root")}: ") {
      val persistent = persistence == Persistence.PersistentDiagnostics
      val identities = Vector(parent, writer.identity, reader.identity)
      identities.map(_.pid).distinct.size shouldBe 3
      identities.map(_.start).distinct.size shouldBe 3
      identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
      identities.map(_.uuid).distinct.size shouldBe 3
      Vector(writer, reader).foreach { child =>
        child.exited shouldBe true
        child.exitcode shouldBe 0
        child.receipt("persistence") shouldBe persistence.wire
        child.receipt.number("count") shouldBe count.toLong
        Vector("ready", "privateAbsent", "nativeUnchanged", "callerclosed", "targetclosed", "targetnodeclosed",
          "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed", "leaseunavailable", "maintenanceclosed",
          "enginesclosed", "timersclosed", "workersclosed", "schedulersclosed").foreach(key => child.receipt.flag(key) shouldBe true)
        Vector("ports", "resolvers", "metadata", "gets", "opens", "closes", "puts", "deletes", "publications",
          "commands", "drained", "queue", "pending", "executing", "leases").foreach(key => child.receipt.number(key) shouldBe 0L)
        child.receipt.flag("descriptorChecked") shouldBe persistent
        child.receipt.flag("lifetimepresent") shouldBe persistent
        child.receipt.flag("storagepresent") shouldBe persistent
        child.receipt.flag("blobquotapresent") shouldBe false
        child.receipt.number("paidroots") shouldBe (if (persistent) count.toLong else 0L)
        child.receipt.ids.zipWithIndex.foreach { case (_, index) =>
          val prefix = s"job.$index."
          child.receipt.flag(prefix + "native") shouldBe persistent
          child.receipt.number(prefix + "revision") shouldBe (if (persistent) 6L else 0L)
          child.receipt(prefix + "status") shouldBe "succeeded"
          child.receipt(prefix + "result") shouldBe (if (persistent) "Succeeded(Absent)" else "AbsentNative")
          child.receipt.number(prefix + "normalids") shouldBe (if (persistent) 1L else 0L)
          Vector("chargedtasks" -> 2L, "historycredits" -> 96L, "admissions" -> 1L, "cancelcredits" -> 1L)
            .foreach { case (key, paid) => child.receipt.number(prefix + key) shouldBe (if (persistent) paid else 0L) }
          child.receipt.number(prefix + "remaining") shouldBe 0L
          child.receipt.number(prefix + "claims") shouldBe 0L
          child.receipt.flag(prefix + "cancelclaimed") shouldBe false
        }
      }
      reader.receipt("root") shouldBe writer.receipt("root")
      reader.receipt("sqlite") shouldBe writer.receipt("sqlite")
      reader.receipt("alpha") shouldBe writer.receipt("alpha")
      writer.receipt.number("idcount") shouldBe count.toLong
      reader.receipt.number("idcount") shouldBe (if (persistent) count.toLong else 0L)
      reader.receipt.ids shouldBe (if (persistent) writer.receipt.ids else Vector.empty)
      writer.receipt.flag("domainChecked") shouldBe true
      writer.receipt.flag("contextChecked") shouldBe true
      writer.receipt.flag("zeroEffects") shouldBe false
      writer.receipt.flag("rawabsent") shouldBe false
      writer.receipt.flag("managementunavailable") shouldBe false
      writer.receipt.flag("foreignrefused") shouldBe false
      writer.receipt.number("queries") shouldBe count.toLong
      writer.receipt.number("observations") shouldBe count.toLong
      writer.receipt.number("sources") shouldBe (if (persistent) 6L * count else 0L)
      writer.receipt.number("pairs") shouldBe (if (persistent) 6L * count else 0L)
      writer.receipt.number("candidates") shouldBe 0L
      writer.receipt.number("registered") shouldBe 0L
      if (persistent) {
        writer.receipt.number("sqlwrites") should be > 0L
        writer.receipt.number("entitywrites") should be > 0L
      } else {
        writer.receipt.number("sqlwrites") shouldBe 0L
        writer.receipt.number("entitywrites") shouldBe 0L
      }
      reader.receipt.flag("domainChecked") shouldBe false
      reader.receipt.flag("contextChecked") shouldBe false
      reader.receipt.flag("zeroEffects") shouldBe true
      reader.receipt.flag("rawabsent") shouldBe true
      reader.receipt.flag("managementunavailable") shouldBe (persistent && !foreign)
      reader.receipt.flag("foreignrefused") shouldBe foreign
      reader.receipt.number("candidates") shouldBe (if (persistent) count.toLong else 0L)
      reader.receipt.number("registered") shouldBe (if (persistent) count.toLong else 0L)
      reader.receipt.number("terminaljobs") shouldBe (if (persistent) count.toLong else 0L)
      Vector("queries", "observations", "sources", "pairs", "sqlwrites", "entitywrites", "runtimejobs")
        .foreach(key => reader.receipt.number(key) shouldBe 0L)
      writer.receipt.ids.indices.foreach { index =>
        val prefix = s"job.$index."
        writer.receipt.flag(prefix + "liveVoid") shouldBe true
        writer.receipt(prefix + "facade") shouldBe "Available"
        if (persistent) {
          reader.receipt.flag(prefix + "liveVoid") shouldBe false
          reader.receipt(prefix + "facade") shouldBe (if (foreign) "Refused" else "UnavailableAfterRestart")
          reader.receipt.number(prefix + "providerrevision") shouldBe writer.receipt.number(prefix + "providerrevision")
        }
      }
    }
  }
}
