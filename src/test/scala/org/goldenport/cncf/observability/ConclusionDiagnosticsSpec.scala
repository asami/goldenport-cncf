package org.goldenport.cncf.observability

import org.goldenport.Consequence
import org.goldenport.observation.Cause
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 19, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class ConclusionDiagnosticsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ConclusionDiagnostics.classify" should {
    "project service-unavailable availability kinds into distinct diagnostic keys" in {
      Given("service-unavailable conclusions for the three availability Cause kinds")
      val kinds = Vector(
        Cause.Kind.NotRunning,
        Cause.Kind.ConnectionRefused,
        Cause.Kind.Unreachable
      )

      When("the conclusions are classified")
      val classifications = kinds.map(_classification)

      Then("their diagnostic keys distinguish availability kinds without changing projections")
      classifications.map(_.diagnosticKey) shouldBe Vector(
        "not_running",
        "connection_refused",
        "unreachable"
      )
      classifications.map(_.causeKind) shouldBe kinds.map(kind => Some(kind.name))
      classifications.foreach { classification =>
        classification.webStatus shouldBe 503
        classification.statusText shouldBe "Service Unavailable"
      }
    }
  }

  private def _classification(kind: Cause.Kind): ConclusionDiagnostics.Classification =
    ConclusionDiagnostics.classify(
      Consequence.serviceUnavailable[Unit]("availability failure", kind, Seq.empty).conclusion
    )
}
