package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationCanonicalRuntimeReferenceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.test()

  private val _e1 = afterWord(
    "in spec:phase-61-information-canonical-runtime-reference, example:E1, rules:IC-01, phase:61, slice:IC-01A"
  )

  "InformationSpace canonical runtime reference" should {
    "E1 register the generated Information runtime identity" must _e1 {
      "when a minimal valid paper record is registered" in {
        Given(
          "Phase 61 / IC-01A / src/test/scala/org/goldenport/cncf/information/InformationCanonicalRuntimeReferenceSpec.scala / E1 / rule IC-01, an empty InformationSpace and a minimal valid paper Record"
        )
        val space = new InformationSpace

        When("the minimal valid paper Record is registered")
        val registration = space.registerInformation(
          "paper",
          Vector(Record.data("title" -> "Canonical runtime reference"))
        )

        Then("the item is an instance of the generated Information Entity")
        val registered = registration match {
          case Consequence.Success(items) => items.head
          case Consequence.Failure(conclusion) => fail(conclusion.toString)
        }
        registered shouldBe a[org.goldenport.cncf.information.entity.Information]
      }
    }
  }
}
