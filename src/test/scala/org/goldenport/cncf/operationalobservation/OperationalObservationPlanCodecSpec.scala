package org.goldenport.cncf.operationalobservation

import io.circe.Json
import org.goldenport.configuration.ConfigurationValue
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
final class OperationalObservationPlanCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OperationalObservationFixtures.*
  private def _json = io.circe.parser.parse(
    OperationalObservationPlanCodec.encode(plan()).toOption.get.asInstanceOf[ConfigurationValue.StringValue].value).toOption.get
  private def _decode(value: Json) = OperationalObservationPlanCodec.decode(ConfigurationValue.StringValue(value.noSpaces))
  "Strict host observation declarations" should {
    "round-trip explicit server/client profiles without inferring machine facts" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R7; distinct ops/dev identities")
      When("valid disabled profiles are encoded and decoded")
      Then("the selected profile, target identity and enabled state survive")
      Vector("server", "client").foreach { profile =>
        val declared = plan().copy(profile = profile)
        OperationalObservationPlanCodec.encode(declared).flatMap(OperationalObservationPlanCodec.decode).toOption shouldBe Some(declared)
      }
    }
    "reject duplicate, unknown, null, oversized, fractional and ambiguous declarations" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R7; exact versioned JSON")
      val target = _json.hcursor.downField("targets").focus.get.asArray.get.head
      val invalid = Vector(
        _json.mapObject(_.add("discovery", Json.True)),
        _json.mapObject(_.add("profile", Json.Null)),
        _json.mapObject(_.add("targets", Json.arr(target, target))),
        _json.mapObject(_.add("targets", Json.fromValues(Vector.fill(101)(target)))),
        _json.mapObject(_.add("owners", Json.arr(Json.fromString(owner.name), Json.fromString(owner.name)))),
        _json.mapObject(_.add("limits", _json.hcursor.downField("limits").focus.get.mapObject(
          _.add("sourceMillis", Json.fromDoubleOrNull(0.5))))))
      When("the decoder handles these declarations")
      Then("configuration fails rather than coercing or discarding fields")
      invalid.foreach(value => _decode(value).isFaillure shouldBe true)
      OperationalObservationPlanCodec.decode(ConfigurationValue.StringValue(
        """{"schema":"a","schema":"b"}""")).isFaillure shouldBe true
    }
    "bound source acquisitions before effects for every excess source cardinality" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R2,R7; an eight-source ceiling")
      When("host declarations request nine or more distinct source bindings")
      val property = Prop.forAll(Gen.choose(9, 30)) { count =>
        plan().copy(sources = Vector.tabulate(count)(index => http("source-" + index))).validateC.isFaillure
      }
      Then("every such declaration is invalid")
      Test.check(Test.Parameters.default, property).passed shouldBe true
    }
  }
}
