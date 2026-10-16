package org.goldenport.cncf.display

import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object DisplayMutationServiceListPropertySpec {
  import DisplayMutationServiceFixture.*
  import DisplayMutationServiceListFixture.*
  import DisplayMutationServiceLifecyclePropertySpec.{samples, parameters, fields}
  import DisplayMutationServiceLifecyclePropertySpec.given

  trait Laws extends DisplayMutationServiceListFixture.Vocabulary { self: org.scalatest.Informing =>
    def lifecycleLaw(pool: Pool, label: String): Test.Result = {
      val property = Prop.forAll(samples) { sample =>
        Given("a total generated typed value and target/representation/identity/body/delete-mode model with an intact lifecycle sequence")
        val session = pool.session(sample)
        val inputs = fields(sample)
        val changes = inputs.map(input => if (input.location.fieldId == "title")
          input.copy(value = DisplayValue.Text("Updated:" + sample.text)) else input)
        When("the complete generated witness/create/List/update/no-op/stale/delete/List sequence uses its owned managed session")
        val selected = createAndList(session, inputs)
        updateDeleteSequence(session, selected, changes, sample.logical)
        Then("every independent full-state and exact List oracle holds and no more than four owned sessions remain live")
        pool.liveSessionCount should be <= 4
        true
      }
      When("ScalaCheck executes at least one hundred total samples with value and finite-mode shrinking that retains the whole sequence")
      val result = Test.check(parameters, property)
      Then("the actual property result must pass with at least one hundred successes one worker and zero discarded cases")
      withClue(s"$label ${result.status}: ") {
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
      result
    }
  }
}

final class DisplayMutationServiceListPropertySpec extends AnyWordSpec with DisplayMutationServiceListPropertySpec.Laws {
  import DisplayMutationServiceListFixture.Pool
  private val _governed = afterWord("under docs/spec/display-mutation-service.md C4C-07, docs/design/display-mutation-service.md C4C-D01, Phase96 P96-07C4C and RULE.md")
  "Canonical List generated lifecycle" should {
    "portable total shrinking sequence law" which _governed {
      "LST-P1 preserve complete typed and durable state on memory and SQLite with one hundred successful sequences each" in {
        for (backend <- Vector("memory", "sqlite")) {
          Given(s"a $backend finite owned pool with at most four sessions and independent typed Unicode decimal Boolean Instant nullable and body-mode inputs")
          val pool = new Pool(backend)
          When("the complete lifecycle law runs with the total existing generator and meaningful value/mode shrinkers")
          val result = try lifecycleLaw(pool, "LST-P1 " + backend) finally pool.close()
          Then("the actual portable result has at least one hundred successes and zero discards after all owned sessions close")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          pool.liveSessionCount shouldBe 0
        }
      }
    }
  }
}
