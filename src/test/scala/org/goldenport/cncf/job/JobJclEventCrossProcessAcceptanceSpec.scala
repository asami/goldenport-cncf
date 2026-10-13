package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobJclEventCrossProcessFixtures.{Child, Identity, Scenario, identity, withCase}

/*
 * Nine public JCL event/continuation contracts use actual sequential JVMs.
 * Generated invocations retain complete local native and cleanup oracles.
 *
 * @since   Oct.  6, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobJclEventCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "JM69-10C10 public JCL events across independently fresh processes" should {
    "EC1 PersistentRoot" which {
      "keeps the root scalar while its two source-parent continuations commit in declaration order" in {
        Given("EC1: canonical inline Persistent root, a persistent accepted event and two matching handlers in an owned native namespace")
        val parent = identity()
        withCase(Scenario.PersistentRoot, "Root", 2) { fixture =>
          When("public JobControl executes root, native observer and both handlers, then its writer closes and exits before a fresh reader starts")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("Root/no parent and both Action Continuations/reference-root preserve root result, source Running during nested boundaries, exact paid6/291/3/3 and fresh Registered revision10 without redispatch")
          _persistent_pair(parent, writer, reader, Scenario.PersistentRoot, 2)
        }
      }
    }
    "EC2 PersistentFlow" which {
      "keeps the source scalar and inherited root parameters with each source and handler override" in {
        Given("EC2: Persistent root/source JCL, persistent emission after source and three ordered handlers with independent inherited/shared/marker/position selectors")
        val parent = identity()
        withCase(Scenario.PersistentFlow, "Flow", 3) { fixture =>
          When("the real public submission executes root/source/observer/handler1/handler2/handler3 and the writer exits before the native reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("Root then Child/reference-root and all Continuations/reference-source retain actual contexts, canonical commits and source result; signed revision14/Absent and paid10/452/4/4 survive with zero reader execution")
          _persistent_pair(parent, writer, reader, Scenario.PersistentFlow, 3)
        }
      }
    }
    "EC3 NonPersistentEvent" which {
      "dispatches once and completes continuations while retaining zero selected EventStore records" in {
        Given("EC3: Persistent two-task Job, persistent=false event and two matching same-Job handlers")
        val parent = identity()
        withCase(Scenario.NonPersistentEvent, "Volatile", 2) { fixture =>
          When("public JCL performs the actual observer and handler chain, then an independent reader opens the completed native Job namespace")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("all four actual task descriptors, source result and paid8/356/3/3 survive revision12 while the selected event count is zero in both processes")
          _persistent_pair(parent, writer, reader, Scenario.NonPersistentEvent, 2)
        }
      }
    }
    "EC4 EphemeralJob" which {
      "stores its event in the writer baseline while leaving the durable Job namespace empty" in {
        Given("EC4: explicitly Ephemeral root/source JCL with one persistent event and two successful continuations")
        val parent = identity()
        withCase(Scenario.EphemeralJob, "Ephem", 2) { fixture =>
          When("the real public writer executes all four commands and exits, then a reader with no Job ID starts on the same empty native namespace")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("the source scalar, four canonical commits and one writer Stored event remain observable, with no durable Job/lifetime/storage/Blob rows and no event in the fresh in-memory reader")
          _empty_pair(parent, writer, reader, Scenario.EphemeralJob, 2)
        }
      }
    }
    "EC5 SourceFailure" which {
      "retains the original argumentInvalid conclusion before any event or continuation" in {
        Given("EC5: Ephemeral two-task public JCL whose real source CommandAction returns a fixed original argumentInvalid before staging a commit")
        val parent = identity()
        withCase(Scenario.SourceFailure, "Source", 3) { fixture =>
          When("public submission executes the successful root and failing source, then closes before the fresh empty native reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("only root committed; source UoW aborted and its original conclusion remains, with zero event/observer/handler and no manufactured signed failed terminal or durable rows")
          _empty_pair(parent, writer, reader, Scenario.SourceFailure, 3)
        }
      }
    }
    "EC6 DispatchFailure" which {
      "preserves the source delegate commit and stored event when the existing observer refuses dispatch" in {
        Given("EC6: Ephemeral root/source public JCL and one pre-existing real observer returning a fixed argumentInvalid for its persistent event")
        val parent = identity()
        withCase(Scenario.DispatchFailure, "Dispatch", 3) { fixture =>
          When("the native EventBus appends once then reports the observer refusal through the source wrapper, and the writer exits before the fresh empty reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("the original event.dispatch typed consequence retains its previous cause, source wrapper fails and no handler runs; both underlying command commits and the one writer Stored event remain actual observations")
          _empty_pair(parent, writer, reader, Scenario.DispatchFailure, 3)
        }
      }
    }
    "EC7 HandlerFailure" which {
      "stops after the first failed continuation without rolling back the successful source delegate" in {
        Given("EC7: Ephemeral two-task JCL with a successful observer, persistent event and a fixed argumentInvalid from handler1 among three declared handlers")
        val parent = identity()
        withCase(Scenario.HandlerFailure, "Handler", 3) { fixture =>
          When("actual root/source commits and observer dispatch precede the first failing continuation, then the writer exits before a fresh empty reader")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("source wrapper and Continuation1 fail with the original handler conclusion, handler1 UoW aborts, later handlers never run and root/source commits plus one Stored event remain")
          _empty_pair(parent, writer, reader, Scenario.HandlerFailure, 3)
        }
      }
    }
    "EC8 ForeignReader" which {
      "registers original signed authority then refuses and conceals the Job from an independent foreign caller" in {
        Given("EC8: a genuine completed PersistentFlow writer and independently selected foreign-subject/foreign-tenant in the fresh native reader")
        val parent = identity()
        withCase(Scenario.ForeignReader, "Foreign", 2) { fixture =>
          When("the public writer exits and its reader registers the terminal Job under original authority before making foreign queryVisible and management requests")
          val writer = fixture.writer()
          val reader = fixture.reader(writer.receipt.ids)
          Then("strict original typed visibility refusal and management concealment retain unchanged descriptors/payment/native rows with zero body leakage, event, redispatch, port, publication or write")
          _persistent_pair(parent, writer, reader, Scenario.ForeignReader, 2)
        }
      }
    }
    "EC9 GeneratedPersistentFlow" which {
      "preserves all actual public/native/process oracles under ordinary dependent generation and shrinking" in {
        Given("EC9: dependent alpha length1..8 and handlercount1..3 with seed69710102L, minimum24 successful tests, workers1 and a unique native namespace per invocation")
        val parent = identity()
        val selections = for {
          length <- Gen.choose(1, 8)
          letters <- Gen.listOfN(length, Gen.alphaChar)
          count <- Gen.choose(1, 3)
        } yield (letters.mkString, count)
        val property = Prop.forAll(selections) { case (alpha, count) =>
          Given("EC9 generated invocation: independently selected bounded alpha/N derive canonical Persistent root/source/event/handlers in a unique owned SQLite namespace")
          withCase(Scenario.GeneratedPersistentFlow, alpha, count) { fixture =>
            When("this real public writer performs the native sequence and exits before this independently constructed native reader receives its opaque Job ID")
            val writer = fixture.writer()
            val reader = fixture.reader(writer.receipt.ids)
            Then("this invocation satisfies complete order/parameter/context/parent/Running-prefix/payment/privacy/native/no-reader-effects/resource-closure oracles")
            _persistent_pair(parent, writer, reader, Scenario.GeneratedPersistentFlow, count)
            true
          }
        }
        When("ScalaCheck runs the genuine sequential process property with ordinary shrinking, fixed seed, minimum24 successes and workers1")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710102L)).withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the complete selected process property passes at least24 invocations with no discarded cases")
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
      Vector("ready", "nativeunchanged", "privateabsent", "eventchecked", "subsystemclosed", "nodeclosed", "sqlclosed",
        "ownerclosed", "maintenanceclosed", "leaseunavailable", "workersclosed", "timersclosed", "schedulersclosed")
        .foreach(name => child.receipt.flag(name) shouldBe true)
      Vector("ports", "publications", "gets", "opens", "puts", "deletes", "queue", "drained", "pending", "executing", "leases")
        .foreach(name => child.receipt.number(name) shouldBe 0L)
      child.receipt.number("subscriptions") shouldBe 1L
    }
    reader.receipt.number("eventcount") shouldBe 0L
    reader.receipt.number("observers") shouldBe 0L
  }
  private def _zero_reader(reader: Child): Unit = {
    Vector("actions", "commits", "sources", "pairs", "ports", "sqlwrites", "entitywrites", "publications", "gets", "opens",
      "puts", "deletes", "runtimejobs", "queue", "drained", "failedactions", "eventcount", "observers")
      .foreach(name => reader.receipt.number(name) shouldBe 0L)
    reader.receipt.flag("wrapperfailed") shouldBe false
  }
  private def _persistent_pair(parent: Identity, writer: Child, reader: Child, scenario: Scenario, handlers: Int): Unit = {
    val total = scenario.baseCount + handlers
    _separate_closed(parent, writer, reader)
    writer.receipt.ids should have size 1
    reader.receipt.ids shouldBe writer.receipt.ids
    writer.receipt.number("actions") shouldBe total.toLong
    writer.receipt.number("commits") shouldBe total.toLong
    writer.receipt.number("sources") shouldBe 4L + 2L * total
    writer.receipt.number("pairs") shouldBe 4L + 2L * total
    writer.receipt.number("eventcount") shouldBe scenario.eventCount.toLong
    writer.receipt.number("observers") shouldBe 1L
    writer.receipt.flag("wrapperfailed") shouldBe false
    writer.receipt.number("failedactions") shouldBe 0L
    writer.receipt.flag("domainchecked") shouldBe true
    writer.receipt.number("sqlwrites") should be > 0L
    writer.receipt.number("entitywrites") should be > 0L
    reader.receipt.number("registered") shouldBe 1L
    reader.receipt.number("entityloads") should be > 0L
    _zero_reader(reader)
    Vector(writer, reader).foreach { child =>
      child.receipt.flag("descriptorchecked") shouldBe true
      child.receipt.flag("sourcechecked") shouldBe true
      child.receipt.number("revision") shouldBe 4L + 2L * total
      child.receipt.number("paidroots") shouldBe 1L
      child.receipt.number("chargedtasks") shouldBe 2L * total
      child.receipt.number("historycredits") shouldBe 32L + 64L * total + 32L * handlers + (if (scenario.baseCount == 1) { 3L } else { 4L })
      child.receipt.number("admissions") shouldBe 1L + handlers
      child.receipt.number("cancelcredits") shouldBe 1L + handlers
      child.receipt.number("remaining") shouldBe 0L
      child.receipt.number("normalids") shouldBe total.toLong
      child.receipt.number("claims") shouldBe 0L
      child.receipt.flag("cancelclaimed") shouldBe false
      child.receipt.flag("lifetimepresent") shouldBe true
      child.receipt.flag("storagepresent") shouldBe true
      child.receipt.flag("blobquotapresent") shouldBe false
    }
    reader.receipt.number("providerrevision") shouldBe writer.receipt.number("providerrevision")
    writer.receipt("facade") shouldBe "Available"
    reader.receipt("facade") shouldBe (if (scenario == Scenario.ForeignReader) { "Refused" } else { "UnavailableAfterRestart" })
    reader.receipt.flag("foreignrefused") shouldBe (scenario == Scenario.ForeignReader)
  }
  private def _empty_pair(parent: Identity, writer: Child, reader: Child, scenario: Scenario, handlers: Int): Unit = {
    _separate_closed(parent, writer, reader)
    writer.receipt.ids should have size 1
    reader.receipt.ids shouldBe Vector.empty
    writer.receipt.number("actions") shouldBe scenario.attemptedCount(handlers).toLong
    writer.receipt.number("commits") shouldBe scenario.committedCount(handlers).toLong
    writer.receipt.number("eventcount") shouldBe scenario.eventCount.toLong
    writer.receipt.number("observers") shouldBe scenario.observerCount.toLong
    writer.receipt.flag("wrapperfailed") shouldBe scenario.failed
    writer.receipt.number("failedactions") shouldBe (if (scenario == Scenario.SourceFailure || scenario == Scenario.HandlerFailure) { 1L } else { 0L })
    writer.receipt("facade") shouldBe "Available"
    reader.receipt("facade") shouldBe "Empty"
    Vector(writer, reader).foreach { child =>
      Vector("sources", "pairs", "sqlwrites", "entitywrites", "registered", "paidroots", "revision", "chargedtasks", "historycredits",
        "admissions", "cancelcredits", "normalids", "claims", "remaining").foreach(name => child.receipt.number(name) shouldBe 0L)
      Vector("lifetimepresent", "storagepresent", "blobquotapresent", "descriptorchecked", "sourcechecked", "cancelclaimed", "foreignrefused")
        .foreach(name => child.receipt.flag(name) shouldBe false)
    }
    _zero_reader(reader)
  }
}
