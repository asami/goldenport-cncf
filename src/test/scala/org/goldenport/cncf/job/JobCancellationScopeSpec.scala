package org.goldenport.cncf.job

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Job-owned active-work cancellation.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobCancellationScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _cancellation_counts = Gen.choose(1, 12)

  "Job cancellation scope" should {
    "invoke each active callback once across repeated cancellation signals" in {
      Given("generated repeated cancellation counts and one registered active callback")
      val property = Prop.forAll(_cancellation_counts) { count =>
        val scope = new JobCancellationScope
        val invocations = new AtomicInteger(0)
        val registration = scope.register {
          invocations.incrementAndGet()
          Consequence.unit
        }
        (1 to count).foreach(_ => scope.cancel())
        registration.close()
        invocations.get == 1 && scope.isCancelled
      }

      When("each generated cancellation sequence is applied")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)

      Then("the active callback is invoked exactly once and the scope remains cancelled")
      checked.passed shouldBe true
    }

    "cancel work registered after the cancellation race deterministically" in {
      Given("a Job cancellation scope already marked cancelled")
      val scope = new JobCancellationScope
      val invocations = new AtomicInteger(0)
      scope.cancel()

      When("a launched runtime effect registers its cancellation callback")
      val registration = scope.register {
        invocations.incrementAndGet()
        Consequence.unit
      }

      Then("the late registration receives the existing cancellation signal immediately")
      invocations.get shouldBe 1
      registration.close()
    }
  }
}
