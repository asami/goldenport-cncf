package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Opaque addressing, exact response provenance and unchanged legacy admission.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobKeyedEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobKeyedEvidenceFixtures.*
  import JobDurableLifetimeRecoveryFixtures.success

  private def _scenario(id: String, rules: String) = afterWord(
    s"in spec:durable-job-keyed-evidence, example:$id, rules:$rules, phase:69.7, slice:JM69-09D1A")

  "Keyed closed lifecycle evidence" should {
    "exact invocation identity" which {
      "KE1 forward every original boundary and the identical evidence pair" must _scenario("KE1", "R2,R4,R5") {
        "resolve once with the original opaque key, next revision and nested request" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE1; R2,R4,R5; six original requests with typed closed Task models")
          val jobid = JobInputBoundsFixtures.id("original-opaque-key")
          requests(jobid).zipWithIndex.foreach { case (request, index) =>
            val revision = index.toLong + 1L
            val supplied = pair(DurableJobKeyedEvidenceRequest(jobid, revision, request), recipe())
            val source = new Probe(key => Consequence.success(DurableJobKeyedEvidenceResponse(
              key.jobid, key.expectedrevision, supplied)))
            When("the addressed invocation requests independently supplied closed evidence")
            val result = DurableJobKeyedEvidence.fetch(jobid, revision, request, source)
            Then("one resolve retains the exact original request and evidence/access object")
            source.requests.size shouldBe 1
            source.requests.head.jobid should be theSameInstanceAs(jobid)
            source.requests.head.expectedrevision shouldBe revision
            source.requests.head.request should be theSameInstanceAs(request)
            success(result) should be theSameInstanceAs(supplied)
            success(result).access should be theSameInstanceAs(supplied.access)
          }
        }
      }

      "KE2 refuse a successful response for another Job" must _scenario("KE2", "R4,R5") {
        "keep both opaque identities out of the fixed diagnostic" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE2; R4,R5; a well-formed pair addressed to a distinct opaque Job")
          val jobid = JobInputBoundsFixtures.id("private-original-key")
          val foreign = JobInputBoundsFixtures.id("private-foreign-key")
          val supplied = JobInputBoundsFixtures.evidence(1L)
          val source = new Probe(_ => Consequence.success(DurableJobKeyedEvidenceResponse(foreign, 1L, supplied)))
          When("the original Job requests its admission evidence")
          val result = DurableJobKeyedEvidence.fetch(jobid, 1L, DurableJobLifecycleWriteRequest.Admission, source)
          Then("the successful foreign envelope is refused once with a redacted fixed conclusion")
          _assert_refusal(result)
          source.requests.size shouldBe 1
          _failure_conclusion(result).show should not include jobid.value
          _failure_conclusion(result).show should not include foreign.value
        }
      }

      "KE3 refuse stale or future response revisions without repairing projection evidence" must _scenario("KE3", "R4,R5") {
        "retain one provider invocation and leave matched projection validation to the bridge" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE3; R4,R5; the right Job with stale/future headers and an unchanged stale projection")
          val jobid = JobInputBoundsFixtures.id("revision-key")
          val request = DurableJobLifecycleWriteRequest.StartIntent
          val supplied = JobInputBoundsFixtures.evidence(1L)
          Vector(1L, 3L, 2L).foreach { responsevalue =>
            val source = new Probe(_ => Consequence.success(DurableJobKeyedEvidenceResponse(jobid, responsevalue, supplied)))
            When("the retained next semantic revision is two")
            val result = DurableJobKeyedEvidence.fetch(jobid, 2L, request, source)
            Then("stale/future headers refuse and a matching header returns the untouched pair")
            source.requests.size shouldBe 1
            if (responsevalue != 2L) _assert_refusal(result)
            else {
              success(result) should be theSameInstanceAs(supplied)
              success(result).projection.semanticRevision shouldBe 1L
            }
          }
        }
      }

      "KE4 preserve provider failure identity and close legacy apply" must _scenario("KE4", "R3,R5") {
        "refuse the legacy trap independently without resolve, retry or fallback" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE4; R3,R5; one provider failure and a subtype assignable to the old source alias")
          val failure = Consequence.stateInvalid[DurableJobKeyedEvidenceResponse]("original provider failure")
          val source = new Probe(_ => failure)
          val legacy: DurableJobLifecycleWriteEvidenceSource = source
          When("an actual keyed invocation encounters that source failure")
          val result = DurableJobKeyedEvidence.fetch(JobInputBoundsFixtures.id("failure-key"), 1L,
            DurableJobLifecycleWriteRequest.Admission, source)
          Then("the same failure object returns after exactly one resolve")
          result should be theSameInstanceAs(failure)
          source.requests.size shouldBe 1
          When("the original legacy function surface is separately invoked")
          val trapped = legacy(DurableJobLifecycleWriteRequest.Admission)
          Then("the final apply trap refuses without another source call")
          _assert_refusal(trapped)
          source.requests.size shouldBe 1
        }
      }
    }

    "closed admission and response guards" which {
      "KE5 admit the inclusive native ID limit and refuse invalid inputs before resolve" must _scenario("KE5", "R4") {
        "use the unchanged 512 UTF8 byte policy" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE5; R4; null inputs, nonpositive revisions and inclusive/oversized opaque native IDs")
          val overhead = JobInputBoundsFixtures.id("").value.getBytes(StandardCharsets.UTF_8).length
          val exact = JobInputBoundsFixtures.id("x" * (512 - overhead))
          val oversize = JobInputBoundsFixtures.id("x" * (513 - overhead))
          val multibyte = JobInputBoundsFixtures.id("界" * 171)
          val jobid = JobInputBoundsFixtures.id("input-key")
          val request = DurableJobLifecycleWriteRequest.Admission
          val source = new Probe(key => Consequence.success(DurableJobKeyedEvidenceResponse(
            key.jobid, key.expectedrevision, JobInputBoundsFixtures.evidence(key.expectedrevision))))
          When("malformed input envelopes request evidence")
          val refused = Vector(
            DurableJobKeyedEvidence.fetch(jobid, 1L, request, null),
            DurableJobKeyedEvidence.fetch(null, 1L, request, source),
            DurableJobKeyedEvidence.fetch(jobid, 1L, null, source),
            DurableJobKeyedEvidence.fetch(jobid, 0L, request, source),
            DurableJobKeyedEvidence.fetch(jobid, -1L, request, source),
            DurableJobKeyedEvidence.fetch(oversize, 1L, request, source),
            DurableJobKeyedEvidence.fetch(multibyte, 1L, request, source))
          Then("each refuses with the fixed conclusion before any resolve")
          refused.foreach(_assert_refusal)
          source.requests shouldBe empty
          exact.value.getBytes(StandardCharsets.UTF_8).length shouldBe 512
          When("the inclusive original native limit requests evidence")
          val admitted = DurableJobKeyedEvidence.fetch(exact, 1L, request, source)
          Then("the original opaque value is accepted unchanged once")
          admitted.isSuccess shouldBe true
          source.requests.size shouldBe 1
          source.requests.head.jobid should be theSameInstanceAs(exact)
        }
      }

      "KE6 refuse null result envelopes and preserve missing pair ownership" must _scenario("KE6", "R5") {
        "leave matched null evidence to the original bridge guard" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE6; R5; null Consequence, successful response, response key and evidence pair")
          val jobid = JobInputBoundsFixtures.id("null-key")
          val supplied = JobInputBoundsFixtures.evidence(1L)
          val outputs: Vector[Consequence[DurableJobKeyedEvidenceResponse]] = Vector(null,
            Consequence.success(null), Consequence.success(DurableJobKeyedEvidenceResponse(null, 1L, supplied)),
            Consequence.success(DurableJobKeyedEvidenceResponse(jobid, 1L, null)))
          outputs.zipWithIndex.foreach { case (output, index) =>
            val source = new Probe(_ => output)
            When("the original Job receives one malformed or matched-empty source response")
            val result = DurableJobKeyedEvidence.fetch(jobid, 1L, DurableJobLifecycleWriteRequest.Admission, source)
            Then("envelope malformations refuse once while a matched null pair stays untouched")
            source.requests.size shouldBe 1
            if (index < 3) _assert_refusal(result)
            else success(result) shouldBe null
          }
        }
      }

      "KE7 preserve the original Function1 admission failure" must _scenario("KE7", "R3,R6") {
        "use the old bridge constructor and source request without native publication or execution" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE7; R3,R6; an actual SQLite engine and ordinary legacy source")
          withDatabase { database =>
            val owner = database.open()
            val task = new JobInputBoundsFixtures.CountingTask
            val failure = Consequence.stateInvalid[DurableJobLifecycleWriteEvidence]("legacy admission failure")
            var received = Vector.empty[DurableJobLifecycleWriteRequest]
            val legacy: DurableJobLifecycleWriteEvidenceSource = request => {
              received :+= request
              failure
            }
            owner.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(owner.store, legacy))
            When("the old configured constructor receives an actual Persistent submission")
            val result = owner.engine.submit(List(task), owner.context, JobSubmitOption())
            Then("the original Admission singleton and failure conclusion precede native publication and body")
            result.isSuccess shouldBe false
            _failure_conclusion(result) should be theSameInstanceAs(failure.conclusion)
            received shouldBe Vector(DurableJobLifecycleWriteRequest.Admission)
            received.head should be theSameInstanceAs(DurableJobLifecycleWriteRequest.Admission)
            ledger(owner) shouldBe None
            owner.engine.runtimeState.durableJobs.isEmpty shouldBe true
            owner.engine.drainAll() shouldBe 0
            task.runs.get() shouldBe 0
            task.admissions.get() shouldBe 1
          }
        }
      }

      "KE8 preserve opaque keys and positive revisions under shrinking" must _scenario("KE8", "R2,R4,R5") {
        "check independently generated matching and foreign response addresses once" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KE8; R2,R4,R5; seed69709701 and shrinking opaque key/revision spaces")
          val keygen = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(48).mkString)
          val property = Prop.forAll(keygen, keygen, Gen.chooseNum(1L, 1000000L)) { (first, second, revision) =>
            val jobid = JobInputBoundsFixtures.id(s"first-$first")
            val foreign = JobInputBoundsFixtures.id(s"second-$second")
            val request = DurableJobLifecycleWriteRequest.StartIntent
            val supplied = JobInputBoundsFixtures.evidence(revision)
            Vector("matching", "foreign", "revision").foreach { mode =>
              val source = new Probe(_ => Consequence.success(DurableJobKeyedEvidenceResponse(
                if (mode == "foreign") foreign else jobid,
                if (mode == "revision") revision + 1L else revision, supplied)))
              When("a generated opaque Job key and its positive next revision request evidence")
              val result = DurableJobKeyedEvidence.fetch(jobid, revision, request, source)
              Then("the exact original objects survive only matching addresses, with one invocation")
              source.requests.size shouldBe 1
              source.requests.head.request should be theSameInstanceAs(request)
              source.requests.head.jobid should be theSameInstanceAs(jobid)
              source.requests.head.expectedrevision shouldBe revision
              if (mode == "matching") success(result) should be theSameInstanceAs(supplied)
              else _assert_refusal(result)
            }
            true
          }
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
            .withInitialSeed(Seed(69709701L)), property)
          withClue(s"KE8 shrinking seed69709701: $checked") { checked.passed shouldBe true }
          checked.succeeded should be >= 24
        }
      }
    }
  }

  private def _assert_refusal(result: Consequence[DurableJobLifecycleWriteEvidence]): Unit = {
    result.isSuccess shouldBe false
    val expected = Consequence.argumentInvalid[DurableJobLifecycleWriteEvidence]("durable keyed lifecycle evidence refused")
    _failure_conclusion(result).status shouldBe expected.conclusion.status
    _failure_conclusion(result).status.webCode.code shouldBe expected.conclusion.status.webCode.code
    _failure_conclusion(result).observation.getEffectiveMessage shouldBe expected.conclusion.observation.getEffectiveMessage
  }

  private def _failure_conclusion[A](result: Consequence[A]): org.goldenport.Conclusion = result match {
    case Consequence.Failure(value) => value
    case Consequence.Success(_) => fail("expected a consequence failure")
  }
}
