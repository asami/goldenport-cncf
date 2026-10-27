package org.goldenport.cncf.operationalobservation

import java.time.Instant
import io.circe.parser.parse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
final class OperationalObservationModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Independent safe observations" should {
    "preserve a deployment change without changing any other axis" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R4,R6; independent finite axes")
      val now = Instant.parse("2026-10-24T00:00:00Z")
      val health = AxisObservation.Observed("unhealthy", "app-health", now, now.plusSeconds(1), Some("component"))
      When("each valid Docker state changes only the deployment axis")
      val property = Prop.forAll(Gen.oneOf(DockerOperationalObservationProvider.states.toVector)) { state =>
        val original = Map[ObservationAxis, AxisObservation](ObservationAxis.Health -> health,
          ObservationAxis.Readiness -> AxisObservation.Unavailable(ObservationReason.Stale),
          ObservationAxis.Reachability -> AxisObservation.Unavailable(ObservationReason.SourceUnavailable))
        val changed = original.updated(ObservationAxis.Deployment, AxisObservation.Observed(state, "docker", now, now.plusSeconds(1)))
        original.forall { case (axis, observation) => changed(axis) == observation }
      }
      Then("health, readiness and reachability retain their independent evidence")
      Test.check(Test.Parameters.default, property).passed shouldBe true
    }
    "project finite unavailable and not-applicable values without successful fields" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R4,R6; every finite reason")
      When("availability is projected")
      Then("unavailable records contain no fabricated timestamps or value")
      ObservationReason.values.foreach { reason =>
        val fields = parse(org.goldenport.record.io.RecordEncoder.json(
          AxisObservation.Unavailable(reason, Some("safe-source")).toRecord)).toOption.get.hcursor
        fields.get[String]("reasonCode").toOption shouldBe Some(reason.name)
        fields.downField("observedAt").focus shouldBe None
        fields.downField("value").focus shouldBe None
      }
      AxisObservation.NotApplicable.toRecord.getString("availability") shouldBe Some("not-applicable")
    }
    "reject future and expired samples at the current execution clock" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R4; finite current, expired and future samples")
      val now = Instant.parse("2026-10-24T00:00:00Z")
      When("freshness is checked, including the exact expiry boundary")
      Then("only a currently valid interval remains observed")
      AxisObservation.Observed("ready", "probe", now, now.plusMillis(1)).currentAt(now)
        .isInstanceOf[AxisObservation.Observed] shouldBe true
      AxisObservation.Observed("ready", "probe", now.minusSeconds(1), now).currentAt(now) shouldBe
        AxisObservation.Unavailable(ObservationReason.Stale, Some("probe"))
      AxisObservation.Observed("ready", "probe", now.plusMillis(1), now.plusSeconds(1)).currentAt(now) shouldBe
        AxisObservation.Unavailable(ObservationReason.Stale, Some("probe"))
    }
  }
}
