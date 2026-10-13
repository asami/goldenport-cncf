package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.Identity
import JobCursorCrossProcessFixtures.{Child, Mode}

/*
 * spec:job-cursor-cross-process phase:69.7 slice:JM69-10C3
 * example:E1..E7 rules:UC1..UC6
 *
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
class JobCursorCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _native_pages = afterWord("independent native management readers")

  "Durable management cursors" should _native_pages {
    "unchanged authorized native snapshots" which {
      "E1_CU1 continue three genuine Jobs across fresh JVMs [example:E1 rules:UC1,UC2,UC6]" in {
        Given("three Persistent Async Jobs, first limit one and an owned native process namespace")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          When("the genuine writer exits and separate first and continuation readers interpret its native Jobs")
          val writer = fixture.writer(3)
          val first = fixture.reader(Mode.FirstReader, writer.receipt.ids, 1)
          val next = fixture.reader(Mode.ContinueReader, writer.receipt.ids, 1, first.receipt.cursor)
          Then("canonical ordered pages exhaust the three Jobs without overlap, dispatch or reader native writes")
          _traversal(parent, writer, first, next, 1)
        }
      }

      "E7_CU7 traverse ordinary shrinking native process cases [example:E7 rules:UC1,UC2,UC6]" in {
        Given("Gen.choose(2,3), dependent first limit one through count minus one, seed69710071L and twenty-four successes")
        val parent = JobCrossProcessFixtures.identity()
        val inputs = for {
          count <- Gen.choose(2, 3)
          firstlimit <- Gen.choose(1, count - 1)
        } yield (count, firstlimit)
        val property = Prop.forAll(inputs) { case (count, firstlimit) =>
          Given("a generated native Job count and first-page limit in a new owned namespace")
          JobCursorCrossProcessFixtures.withCase { fixture =>
            When("an actual writer and two independently fresh readers interpret only IDs and the actual API cursor")
            val writer = fixture.writer(count)
            val first = fixture.reader(Mode.FirstReader, writer.receipt.ids, firstlimit)
            val next = fixture.reader(Mode.ContinueReader, writer.receipt.ids, firstlimit, first.receipt.cursor)
            Then("the complete native, authorization, ordering, payment, no-write and process oracle holds")
            _traversal(parent, writer, first, next, firstlimit)
            true
          }
        }
        When("ScalaCheck checks the actual process property with ordinary shrinking and one worker")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710071L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the checked property passes with at least twenty-four successful real process cases")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }

    "bounded visibility and structural refusals" which {
      "E2_CU2 reject changed filters [example:E2 rules:UC1,UC3,UC6]" in {
        Given("two genuinely completed native Jobs and an authorized first page")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          val writer = fixture.writer(2)
          val first = fixture.reader(Mode.FirstReader, writer.receipt.ids, 1)
          When("a distinct fresh reader requests Failed status using the original unfiltered cursor")
          val refused = fixture.reader(Mode.FilterReader, writer.receipt.ids, 1, first.receipt.cursor)
          Then("request filter mismatch returns no mixed page and changes no native Job or paid state")
          _refusal(parent, writer, first, refused, "invalid cursor: request filter mismatch", 2)
        }
      }

      "E3_CU3 reject foreign caller continuation and hide its fresh count [example:E3 rules:UC1,UC3,UC6]" in {
        Given("two native Jobs and a cursor authorized for the original independently restored caller")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          val writer = fixture.writer(2)
          val first = fixture.reader(Mode.FirstReader, writer.receipt.ids, 1)
          When("a separate reader retains trusted startup access and queries with a new foreign principal without session or capabilities")
          val refused = fixture.reader(Mode.CallerReader, writer.receipt.ids, 1, first.receipt.cursor)
          Then("caller visibility mismatch refuses the cursor and a cursor-free foreign query returns empty, zero and no continuation")
          _refusal(parent, writer, first, refused, "invalid cursor: caller visibility mismatch", 4)
          refused.receipt.number("total") shouldBe 0L
          refused.receipt.pageIds shouldBe Vector.empty
          refused.receipt.cursor shouldBe ""
        }
      }

      "E4_CU4 reject malformed and overbound opaque tokens before policy [example:E4 rules:UC1,UC5,UC6]" in {
        Given("two genuinely completed native Jobs and an independent registered reader")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          val writer = fixture.writer(2)
          When("the fresh reader queries literal percent and a 281-character opaque token")
          val refused = fixture.reader(Mode.InvalidReader, writer.receipt.ids, 1)
          Then("both original structural cursor failures precede all candidate-policy calls and preserve native state")
          _processes(parent, Vector(writer, refused))
          _writer(writer, 2)
          _reader(writer, refused)
          refused.receipt("outcome") shouldBe "invalid cursor: malformed cursor"
          refused.receipt.number("failures") shouldBe 2L
          refused.receipt.number("policycalls") shouldBe 0L
        }
      }

      "E6_CU6 reject zero and 101 limits before policy [example:E6 rules:UC1,UC5,UC6]" in {
        Given("two genuinely completed native Jobs and an independently fresh registered reader")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          val writer = fixture.writer(2)
          When("the reader invokes the actual queryPage with limits zero and 101")
          val refused = fixture.reader(Mode.BoundsReader, writer.receipt.ids, 1)
          Then("both original limit-range failures precede policy calls and leave all native state unchanged")
          _processes(parent, Vector(writer, refused))
          _writer(writer, 2)
          _reader(writer, refused)
          refused.receipt("outcome") shouldBe "limit must be in 1..100"
          refused.receipt.number("failures") shouldBe 2L
          refused.receipt.number("policycalls") shouldBe 0L
        }
      }
    }

    "genuine authorized source change" which {
      "E5_CU5 expire an earlier cursor after an actual native appender [example:E5 rules:UC1,UC4,UC6]" in {
        Given("two genuinely completed native Jobs and their authorized first page cursor")
        val parent = JobCrossProcessFixtures.identity()
        JobCursorCrossProcessFixtures.withCase { fixture =>
          val writer = fixture.writer(2)
          val first = fixture.reader(Mode.FirstReader, writer.receipt.ids, 1)
          When("a separate appender genuinely submits and drains a third Job, exits, then a fresh reader uses the old cursor over all three Jobs")
          val appended = fixture.appender(writer.receipt.ids)
          val expired = fixture.reader(Mode.ExpiredReader, appended.receipt.ids, 1, first.receipt.cursor)
          Then("the authorized snapshot expires, the appender owns one newly paid terminal root and the final reader performs no work or mutation")
          _processes(parent, Vector(writer, first, appended, expired))
          _writer(writer, 2)
          _reader(writer, first)
          appended.receipt.ids.size shouldBe 3
          appended.receipt.ids.toSet -- writer.receipt.ids.toSet should have size 1
          writer.receipt.ids.foreach { id => appended.receipt.jobFacts(id) shouldBe writer.receipt.jobFacts(id) }
          Vector("tasks", "canonical", "drained").foreach(key => appended.receipt.number(key) shouldBe 1L)
          appended.receipt.number("keyedcalls") shouldBe 6L
          appended.receipt.number("paidroots") shouldBe 3L
          appended.receipt.flag("oldrowsunchanged") shouldBe true
          appended.receipt.number("writes") should be > 0L
          appended.receipt.number("lifewrites") should be > 0L
          _reader(appended, expired)
          expired.receipt("outcome") shouldBe "expired snapshot: authorized source changed"
          expired.receipt.number("failures") shouldBe 1L
          expired.receipt.number("policycalls") shouldBe 3L
          expired.receipt.pageIds shouldBe Vector.empty
          expired.receipt.cursor shouldBe ""
        }
      }
    }
  }

  private def _processes(parent: Identity, children: Vector[Child]): Unit = {
    val identities = parent +: children.map(_.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe identities.size
    identities.map(_.pid).distinct.size shouldBe identities.size
    identities.map(_.start).distinct.size shouldBe identities.size
    identities.map(_.uuid).distinct.size shouldBe identities.size
    children.foreach { child =>
      child.exitCode shouldBe 0
      child.exited shouldBe true
      Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed", "ready")
        .foreach(key => child.receipt.flag(key) shouldBe true)
      child.receipt.number("leases") shouldBe 0L
      child.receipt.number("ports") shouldBe 0L
      child.receipt.number("queue") shouldBe 0L
      child.receipt.number("sourcecalls") shouldBe 1L
      child.receipt.number("paidroots") shouldBe child.receipt.ids.size.toLong
    }
  }

  private def _writer(child: Child, count: Int): Unit = {
    child.receipt.ids.size shouldBe count
    Vector("tasks", "canonical", "drained").foreach(key => child.receipt.number(key) shouldBe count.toLong)
    child.receipt.number("keyedcalls") shouldBe 6L * count
    child.receipt.number("creates") should be > 0L
    child.receipt.number("writes") should be > 0L
    child.receipt.number("lifewrites") should be > 0L
  }

  private def _reader(producer: Child, reader: Child): Unit = {
    reader.receipt.ids shouldBe producer.receipt.ids
    reader.receipt("root") shouldBe producer.receipt("root")
    reader.receipt("sqlite") shouldBe producer.receipt("sqlite")
    reader.receipt.ids.foreach(id => reader.receipt.jobFacts(id) shouldBe producer.receipt.jobFacts(id))
    Vector("tasks", "canonical", "getters", "drained", "keyedcalls", "creates", "writes", "deletes", "lifewrites", "runtimejobs")
      .foreach(key => reader.receipt.number(key) shouldBe 0L)
    reader.receipt.number("registered") shouldBe reader.receipt.ids.size.toLong
    reader.receipt.flag("rowsunchanged") shouldBe true
    reader.receipt.flag("rawabsent") shouldBe true
    reader.receipt.flag("managementunavailable") shouldBe true
  }

  private def _traversal(parent: Identity, writer: Child, first: Child, next: Child, firstlimit: Int): Unit = {
    _processes(parent, Vector(writer, first, next))
    val ids = writer.receipt.ids.sortBy(_.print)
    _writer(writer, ids.size)
    _reader(writer, first)
    _reader(writer, next)
    first.receipt.pageIds shouldBe ids.take(firstlimit)
    next.receipt.pageIds shouldBe ids.drop(firstlimit)
    val all = first.receipt.pageIds ++ next.receipt.pageIds
    all shouldBe ids
    all.distinct.size shouldBe ids.size
    first.receipt.cursor.length should (be > 0 and be <= 280)
    next.receipt.cursor shouldBe ""
    Vector(first, next).foreach { child =>
      child.receipt("outcome") shouldBe "page"
      child.receipt.number("total") shouldBe ids.size.toLong
      child.receipt.number("policycalls") shouldBe ids.size.toLong
      child.receipt.number("failures") shouldBe 0L
    }
  }

  private def _refusal(parent: Identity, writer: Child, first: Child, refused: Child, message: String, calls: Int): Unit = {
    _processes(parent, Vector(writer, first, refused))
    _writer(writer, writer.receipt.ids.size)
    _reader(writer, first)
    _reader(writer, refused)
    refused.receipt("outcome") shouldBe message
    refused.receipt.number("failures") shouldBe 1L
    refused.receipt.number("policycalls") shouldBe calls.toLong
    refused.receipt.pageIds shouldBe Vector.empty
    refused.receipt.cursor shouldBe ""
  }
}
