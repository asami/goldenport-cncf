package org.goldenport.cncf.job

import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobCrossProcessFixtures.{Child, Identity, identity}
import JobSecurityCrossProcessFixtures.{Mode, ReaderChild, withCase}

/*
 * Original tenant/subject/scope startup authorization across genuine native JVMs.
 *
 * @since   Oct.  6, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobSecurityCrossProcessAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given _security_case_shrink: Shrink[(Mode, Int, Int)] = Shrink { case (mode, count, badindex) =>
    Shrink.shrink[Int](count).filter(candidate => candidate >= 1 && candidate <= 3 && badindex < candidate)
      .map(candidate => (mode, candidate, badindex)) ++
      Shrink.shrink[Int](badindex).filter(candidate => candidate >= 0 && candidate < count)
        .map(candidate => (mode, count, candidate))
  }

  private def _scenario(example: String, rules: String) = {
    afterWord(s"in spec:job-security-cross-process, example:$example, rules:$rules, phase:69.7, slice:JM69-10C11")
  }

  "S1 Exact and additional-scope native startup" should _scenario("S1", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "admit the required scope subset for one and three genuinely completed jobs" in {
      Given("SEC1..SEC7 S1: exact access or job.read plus unused.read, counts one and three, and independently configured native JVMs")
      val parent = identity()
      Vector(Mode.ExactAccess, Mode.ExtraScopes).foreach { mode =>
        Vector(1, 3).foreach { count =>
          Given(s"S1 matrix invocation: a fresh owned native/control/log tree for ${mode.wire}, count $count and selected index zero")
          withCase { fixture =>
            When("the unchanged writer completes Persistent Async tasks, closes and exits before the independently supplied positive-access reader starts")
            val writer = fixture.writer(count)
            val reader = fixture.reader(writer.receipt.ids, mode, 0)
            Then("all ordered terminal facts are Registered, runtime facts Refused, the owner is Ready, original body-free queries retain UnavailableAfterRestart, native payments/rows and zero replay effects persist with one unchanged maintenance registration, and actual closure cancels it")
            _accepted_pair(parent, writer, reader, mode, count, 0)
          }
        }
      }
    }
  }

  "S2 Foreign tenant native startup" should _scenario("S2", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "refuse before Ready and conceal the denied projection without changing native authority" in {
      Given("SEC1..SEC7 S2: one genuinely completed writer job, a fresh reader and independently supplied foreign-tenant access at ordinal zero")
      val parent = identity()
      withCase { fixture =>
        val writer = fixture.writer(1)
        When("the separate reader supplies ForeignTenant to original configured startup over the writer's unchanged SQLite")
        val reader = fixture.reader(writer.receipt.ids, Mode.ForeignTenant, 0)
        Then("typed terminal Refused and fixed startup refusal leave Degraded execution unavailable, no owner/query/status/projection, intact trusted native/payment observations, zero effects and successful closes")
        _accepted_pair(parent, writer, reader, Mode.ForeignTenant, 1, 0)
      }
    }
  }

  "S3 Foreign subject native startup" should _scenario("S3", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "retain submitting-subject equality despite the configured job_admin capability" in {
      Given("SEC1..SEC7 S3: one genuine native completion and a same-tenant foreign-subject candidate in the original job_admin configured context")
      val parent = identity()
      withCase { fixture =>
        val writer = fixture.writer(1)
        When("the independent reader invokes original startup and native authorization with its ForeignSubject candidate")
        val reader = fixture.reader(writer.receipt.ids, Mode.ForeignSubject, 0)
        Then("administrative capability cannot override subject equality; terminal Refused, fixed refusal, concealed projection, unchanged native/payment rows and no execution or write effects precede closed normal exit")
        _accepted_pair(parent, writer, reader, Mode.ForeignSubject, 1, 0)
      }
    }
  }

  "S4 Missing required scopes native startup" should _scenario("S4", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "classify absent job.read as Refused while original trusted observation proves intact storage" in {
      Given("SEC1..SEC7 S4: a genuine completed native job and independently constructed matching tenant/subject access with an empty scope set")
      val parent = identity()
      withCase { fixture =>
        val writer = fixture.writer(1)
        When("the fresh MissingScopes reader supplies empty scopes to the original startup candidate and typed native loader")
        val reader = fixture.reader(writer.receipt.ids, Mode.MissingScopes, 0)
        Then("the access denial is Refused, with Degraded startup, concealed query/status/projection, trusted signed revision six and paid facts, unchanged rows and zero effects after actual close")
        _accepted_pair(parent, writer, reader, Mode.MissingScopes, 1, 0)
      }
    }
  }

  "S5 Unrelated administrative scope names native startup" should _scenario("S5", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "refuse job_admin and content_admin scopes when the signed record requires job.read" in {
      Given("SEC1..SEC7 S5: a genuine native completion and independently supplied job_admin/content_admin scopes in the actual job_admin configured ExecutionContext")
      val parent = identity()
      withCase { fixture =>
        val writer = fixture.writer(1)
        When("the separate UnrelatedScopes reader invokes original startup without the required job.read grant")
        val reader = fixture.reader(writer.receipt.ids, Mode.UnrelatedScopes, 0)
        Then("scope names and configured capability cannot bypass the original subset rule; terminal Refused and fixed redacted refusal retain intact paid native rows, conceal the denied job and close without execution or writes")
        _accepted_pair(parent, writer, reader, Mode.UnrelatedScopes, 1, 0)
      }
    }
  }

  "S6 Ordered mixed native authorization refusal" should _scenario("S6", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7") {
    "retain healthy prefixes and suffixes for every denied mode and selected ordinal" in {
      Given("SEC1..SEC7 S6: three genuine native completions, four denied modes and each selected ordinal zero through two")
      val parent = identity()
      Vector(Mode.ForeignTenant, Mode.ForeignSubject, Mode.MissingScopes, Mode.UnrelatedScopes).foreach { mode =>
        (0 until 3).foreach { badindex =>
          Given(s"S6 matrix invocation: a unique native/control/log tree, three jobs and ${mode.wire} only at ordinal $badindex")
          withCase { fixture =>
            When("the genuine three-task writer closes and exits before the independent reader supplies the selected denial among exact-access candidates")
            val writer = fixture.writer(3)
            val reader = fixture.reader(writer.receipt.ids, mode, badindex)
            Then("ordered reports refuse only the selected terminal candidate, retain both healthy body-free projections, refuse all terminal runtime replay, remain Degraded without an owner, and preserve all native/payment rows with zero effects and real closure")
            _accepted_pair(parent, writer, reader, mode, 3, badindex)
          }
        }
      }
    }
  }

  "S7 Generated independent native startup authorization" should _scenario("S7", "SEC1,SEC2,SEC3,SEC4,SEC5,SEC6,SEC7,SEC8") {
    "check at least twenty-four complete process pairs with ordinary dependent shrinking and no discarded cases" in {
      Given("SEC1..SEC8 S7: six closed modes, count one through three and a dependent selected index, seed 69710103L, minimum 24 and one worker")
      val parent = identity()
      val cases = for {
        mode <- Gen.oneOf(Mode.values.toVector)
        count <- Gen.choose(1, 3)
        badindex <- Gen.choose(0, count - 1)
      } yield (mode, count, badindex)
      val property = Prop.forAll(cases) { case (mode, count, badindex) =>
        Given(s"S7 generated invocation: fresh owned native/control/log storage for ${mode.wire}, count $count and selected ordinal $badindex")
        withCase { fixture =>
          When("this generated invocation runs the unchanged genuine writer to closed exit and starts a fresh independently configured security reader on the same SQLite")
          val writer = fixture.writer(count)
          val reader = fixture.reader(writer.receipt.ids, mode, badindex)
          Then("the full ordered authorization/native/payment/query/zero-replay/identity/closure oracle holds, with one unchanged maintenance registration for Ready or zero for refusal, and zero timers after actual closure")
          _accepted_pair(parent, writer, reader, mode, count, badindex)
          true
        }
      }
      When("ScalaCheck runs the ordinary shrinking dependent property with seed 69710103L, minimum 24 successful cases and workers one")
      val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710103L))
        .withMinSuccessfulTests(24).withWorkers(1), property)
      Then("the actual property passes with at least 24 succeeded complete native process pairs and zero discarded cases")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
      checked.discarded shouldBe 0
    }
  }

  private def _accepted_pair(parent: Identity, writer: Child, reader: ReaderChild,
    mode: Mode, count: Int, badindex: Int): Unit = {
    val identities = Vector(parent, writer.identity, reader.identity)
    identities.map(value => value.pid -> value.start).distinct.size shouldBe 3
    identities.map(_.pid).distinct.size shouldBe 3
    identities.map(_.uuid).distinct.size shouldBe 3
    writer.exitCode shouldBe 0
    reader.exitCode shouldBe 0
    writer.exited shouldBe true
    reader.exited shouldBe true
    writer.receipt("root") shouldBe reader.receipt("root")
    writer.receipt("sqlite") shouldBe reader.receipt("sqlite")
    Vector("subsystemclosed", "nodeclosed", "sqlclosed").foreach { key =>
      writer.receipt.flag(key) shouldBe true
      reader.receipt.flag(key) shouldBe true
    }
    writer.receipt.flag("ownerclosed") shouldBe true
    reader.receipt.number("activeleases") shouldBe 0L
    reader.receipt.flag("ownerinstalled") shouldBe !mode.denied
    reader.receipt("ownerclosure") shouldBe (if (mode.denied) "not-installed" else "installed-closed")
    writer.receipt.flag("ready") shouldBe true
    writer.receipt("refusal") shouldBe ""
    writer.receipt.number("count") shouldBe count.toLong
    writer.receipt.number("paidroots") shouldBe count.toLong
    writer.receipt.number("bodies") shouldBe count.toLong
    writer.receipt.number("drained") shouldBe count.toLong
    writer.receipt.number("ports") shouldBe 0L
    writer.receipt.number("creates") should be > 0L
    writer.receipt.number("writes") should be > 0L
    writer.receipt.number("lifewrites") should be > 0L
    reader.receipt.mode shouldBe mode
    reader.receipt.number("badindex") shouldBe badindex.toLong
    reader.receipt.number("count") shouldBe count.toLong
    reader.receipt.number("candidates") shouldBe count.toLong
    reader.receipt.number("sourcecalls") shouldBe 1L
    reader.receipt.ids shouldBe writer.receipt.ids
    reader.receipt.flag("ready") shouldBe !mode.denied
    reader.receipt("health") shouldBe (if (mode.denied) "Degraded" else "Ready")
    reader.receipt.flag("executionavailable") shouldBe !mode.denied
    reader.receipt.flag("runtimeready") shouldBe !mode.denied
    reader.receipt("refusal") shouldBe (if (mode.denied) "durable runtime startup refused" else "")
    val registered = count - (if (mode.denied) 1 else 0)
    reader.receipt.number("registered") shouldBe registered.toLong
    reader.receipt.number("terminaljobs") shouldBe registered.toLong
    reader.receipt.number("paidroots") shouldBe count.toLong
    reader.receipt.flag("rowsunchanged") shouldBe true
    Vector("lifetimepresent", "storagepresent", "blobquotapresent").foreach(key =>
      reader.receipt.flag(key) shouldBe writer.receipt.flag(key))
    Vector("runtimejobs", "ports", "bodies", "getters", "queue", "drained", "creates", "writes", "deletes",
      "lifewrites", "ids", "clocks").foreach(key => reader.receipt.number(key) shouldBe 0L)
    reader.receipt.number("timers") shouldBe (if (mode.denied) 0L else 1L)
    (0 until count).foreach { index =>
      val denied = mode.denied && index == badindex
      reader.receipt.number(s"job.$index.ordinal") shouldBe index.toLong
      reader.receipt(s"job.$index.terminalfact") shouldBe (if (denied) "Refused" else "Registered")
      reader.receipt(s"job.$index.runtimefact") shouldBe "Refused"
      reader.receipt.flag(s"job.$index.queryvisible") shouldBe !denied
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
      reader.receipt(s"job.$index.facade") shouldBe
        (if (denied) "None" else if (mode.denied) "NotObserved" else "UnavailableAfterRestart")
    }
  }
}
