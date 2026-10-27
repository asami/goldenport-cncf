package org.goldenport.cncf.operationalobservation

import java.time.Instant
import cats.~>
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 26, 2026
 */
final class HttpOperationalObservationProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OperationalObservationFixtures.*
  private val _now = Instant.parse("2026-10-24T00:00:00Z")

  "Independent HTTP observations" should {
    "retain declared vocabulary, scope and source time without inventing another axis" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R4; a reported health source")
      val source = http("health").copy(sampleMode = "reported")
      val property = Prop.forAll(Gen.choose(0, 29999)) { age =>
        val sampled = _now.minusMillis(age.toLong)
        val json = Json.obj("status" -> Json.fromString("unhealthy"), "scope" -> Json.fromString("component"),
          "observedAt" -> Json.fromString(sampled.toString))
        HttpOperationalObservationProvider.decode(source, json, _now, _now) ==
          Right(AxisObservation.Observed("unhealthy", "health", sampled, sampled.plusSeconds(30), Some("component")))
      }
      When("valid observations are decoded for different source ages")
      Then("the source timestamp is retained and health stays within its vocabulary and scope")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property).passed shouldBe true
      Vector("foreign", "ready").foreach { status =>
        HttpOperationalObservationProvider.decode(source, Json.obj("status" -> Json.fromString(status)), _now, _now) shouldBe
          Left(ObservationReason.InvalidSource)
      }
    }

    "reject stale, future, malformed and mismatched reported observations" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R4; a source requiring its own timestamp and scope")
      val source = http("health").copy(sampleMode = "reported")
      def _payload_(time: String, scope: String = "component") = Json.obj("value" -> Json.fromString("healthy"),
        "scope" -> Json.fromString(scope), "observedAt" -> Json.fromString(time))
      When("invalid source metadata is decoded")
      Then("there is no fresh value made from a caller or registry timestamp")
      Vector(_now.minusSeconds(30), _now.plusMillis(1)).foreach { time =>
        HttpOperationalObservationProvider.decode(source, _payload_(time.toString), _now, _now) shouldBe Left(ObservationReason.Stale)
      }
      HttpOperationalObservationProvider.decode(source, _payload_("invalid"), _now, _now) shouldBe Left(ObservationReason.InvalidSource)
      HttpOperationalObservationProvider.decode(source, _payload_(_now.toString, "deployment"), _now, _now) shouldBe
        Left(ObservationReason.InvalidSource)
      HttpOperationalObservationProvider.decode(source, Json.obj("status" -> Json.fromString("healthy")), _now, _now) shouldBe
        Left(ObservationReason.InvalidSource)
      val current = source.copy(sampleMode = "current")
      HttpOperationalObservationProvider.decode(current, _payload_(_now.minusSeconds(31).toString), _now, _now) shouldBe
        Left(ObservationReason.Stale)
      Vector("scope", "observedAt").foreach { field =>
        HttpOperationalObservationProvider.decode(current, _payload_(_now.toString).mapObject(_.add(field, Json.Null)), _now, _now) shouldBe
          Left(ObservationReason.InvalidSource)
      }
    }

    "use only bounded admitted GETs and keep health, readiness and reachability independent" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R7; a fixture completed HTTP error response")
      var calls = Vector.empty[Vector[Property]]
      val effects = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](op: UnitOfWorkOp[A]): Consequence[A] = op match {
          case UnitOfWorkOp.HttpGet(_, _, properties) =>
            calls = calls :+ properties
            Consequence.success(HttpResponse.internalServerError("private raw body"))
          case _ => Consequence.operationInvalid("Unexpected fixture effect")
        }
      }
      val request = ObservationRead(context(), 17, 64)
      When("the same completed response is evaluated for separate declared axes")
      val health = HttpOperationalObservationProvider.readC(http("health"), request, effects)
      val readiness = HttpOperationalObservationProvider.readC(http("readiness", ObservationAxis.Readiness), request, effects)
      val reachability = HttpOperationalObservationProvider.readC(http("reachability", ObservationAxis.Reachability), request, effects)
      Then("a completed exchange proves reachability alone and bounds are carried to the existing HTTP UoW")
      health shouldBe AxisObservation.Unavailable(ObservationReason.SourceUnavailable, Some("health"))
      readiness shouldBe AxisObservation.Unavailable(ObservationReason.SourceUnavailable, Some("readiness"))
      reachability.asInstanceOf[AxisObservation.Observed].value shouldBe "reachable"
      calls.foreach { properties =>
        properties should contain (Property("http.total-timeout-ms", "17", None))
        properties should contain (Property("http.max-response-bytes", "64", None))
        properties should contain (Property("http.follow-redirects", "false", None))
      }
    }

    "sanitize malformed or oversized payloads and transport timeouts" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R3,R7; bounded fixture transports")
      val request = ObservationRead(context(), 1000, 32)
      val timeout = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](op: UnitOfWorkOp[A]): Consequence[A] = throw new java.net.SocketTimeoutException("private endpoint")
      }
      When("a payload is malformed, too large or the transport times out")
      val results = Vector(
        HttpOperationalObservationProvider.readC(http("health"), request, new Effects("{malformed")),
        HttpOperationalObservationProvider.readC(http("health"), request, new Effects("x" * 33)),
        HttpOperationalObservationProvider.readC(http("health"), request, timeout))
      Then("finite unavailable reasons carry no raw provider data")
      results shouldBe Vector(AxisObservation.Unavailable(ObservationReason.InvalidSource, Some("health")),
        AxisObservation.Unavailable(ObservationReason.InvalidSource, Some("health")),
        AxisObservation.Unavailable(ObservationReason.Timeout, Some("health")))
    }

    "release the existing Job cancellation registration after both success and failure" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R7; an active Job cancellation scope")
      val thread = Thread.currentThread()
      val initiallyinterrupted = thread.isInterrupted
      try {
        Vector(false, true).foreach { failing =>
          val cancellation = new org.goldenport.cncf.job.JobCancellationScope
          val execution = org.goldenport.cncf.context.ExecutionContext.withJobContext(context(),
            org.goldenport.cncf.job.JobContext.empty.copy(cancellationScope = Some(cancellation)))
          val effects = new Effects
          effects.failure = failing
          When("an exchange completes and its former Job is cancelled afterwards")
          OperationalObservationProvider.fetchC(http("health").url, ObservationRead(execution, 1000, 65536), effects)
          cancellation.cancel()
          Then("the finished exchange no longer registers an interrupt callback")
          thread.isInterrupted shouldBe initiallyinterrupted
        }
      } finally {
        if (!initiallyinterrupted) {
          val _ = Thread.interrupted() // Clear only a failed fixture's own cancellation callback.
        }
      }
    }
  }
}
