package org.goldenport.cncf.action

import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class AggregateLifecycleMutationCapturePropertySpec extends AnyWordSpec with AggregateLifecycleMutationCaptureFixture.Vocabulary {
  import AggregateLifecycleMutationCaptureFixture.*
  import AggregateLifecycleMutationCaptureFixture.given

  "Aggregate lifecycle generated correspondence" should {
    "retain total Unicode body identity representation and observation laws" which {
      backends.foreach { backend =>
        s"ALC-P1 require 100 sequential $backend creation successes with zero discards" in {
          Given("a total shrinking Unicode/business/body/representation/supplied-ID/root-alias generator and a bounded owned backend")
          withBackend(backend) { environment =>
            val tokens = representations.map(representation => representation -> s"alc_create_law_${representation.toString.toLowerCase}").toMap
            val law = Prop.forAll(samples) { value =>
              val session = new Session(tokens(value.value.representation), value, environment)
              try checkCreateLaw(session) finally session.cleanup()
            }
            When("the real registered Aggregate creation law executes with one worker and no filters or implications")
            val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
            Then("at least 100 successful cases and zero discards establish the complete independent creation correspondence")
            result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
          }
        }
        s"ALC-P2 require 100 sequential $backend current-stale-veto deletion successes with zero discards" in {
          Given("a total shrinking legal-revision/current-stale-veto/logical-physical/root-alias generator and bounded owned roots")
          withBackend(backend) { environment =>
            val tokens = representations.map(representation => representation -> s"alc_delete_law_${representation.toString.toLowerCase}").toMap
            val law = Prop.forAll(samples) { value =>
              val session = new Session(tokens(value.value.representation), value, environment)
              try checkDeleteLaw(session) finally session.cleanup()
            }
            When("actual Aggregate observed deletion runs sequentially through its canonical provider without discarded inputs")
            val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
            Then("at least 100 successes preserve full state on stale/veto and retain the exact prior receipt for admitted deletion")
            result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
          }
        }
      }
    }
  }
}
