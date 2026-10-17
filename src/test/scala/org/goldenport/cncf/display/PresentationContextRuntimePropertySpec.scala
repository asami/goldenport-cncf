package org.goldenport.cncf.display

import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class PresentationContextRuntimePropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PresentationContextRuntimeFixture.*
  import PresentationContextRuntimePropertySpec.*
  import PresentationContextRuntimePropertySpec.given
  import PresentationContextRuntime.*
  import PresentationContextResolver.Result

  "Server continuation laws" should afterWord("in spec:display-presentation-context, example:BP1,BP2, rules:R5,R6, phase:96") {
    "BP1 authoritative values" which {
      "preserve only varying server references variant canonical locale and nanosecond expiry" in {
        Given("R5,R6: total bounded Unicode reference and variant generators canonical locales and positive server lifetimes")
        val base = context()
        val property = Prop.forAll(samples) { sample =>
          Given("one generated legal continuation with conflicting inert client claims")
          val provider = new Provider
          val serverflow = flow("d" + sample.payload.mkString, "c" + sample.payload.reverse.mkString,
            Instant.ofEpochSecond(instant.getEpochSecond + sample.seconds, sample.nanos.toLong))
          provider.answer = (request, _) => Consequence.success(Result.Supported(success(
            Resolution.createC(request.binding, Some("v" + sample.payload.mkString), Some(serverflow)))))
          val ctx = ExecutionContext.withLocale(base.withScope(ScopeContext.withPresentationContextResolver(base.scope, provider)),
            Locale.forLanguageTag(sample.locale))
          val incoming = wire(flow(serverflow.displayInstance, serverflow.correlation, instant.minusSeconds(sample.seconds)))
          When("the runtime restores and projects this continuation")
          val projected = project(success(restoreC(instanceId, Some(incoming), ctx))).get
          Then("the client supplies only opaque reference equality and every projected claim comes from server data")
          projected.locale shouldBe Some(sample.locale)
          projected.variant shouldBe Some("v" + sample.payload.mkString)
          projected.flow.get.displayInstance shouldBe serverflow.displayInstance
          projected.flow.get.correlation shouldBe serverflow.correlation
          projected.flow.get.expiresAt shouldBe serverflow.expiresAt
          provider.requests should have size 1
          true
        }
        When("ScalaCheck interprets the total generated continuation law with reducing shrinkers")
        val result = Test.check(parameters, property)
        Then("at least one hundred cases succeed without discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
    "BP2 one-dimensional authority defects" which {
      "reject binding and reference mismatch and clear expired server flow" in {
        Given("R6: one varying valid semantic case and precisely one binding reference or expiry defect")
        val base = context()
        val property = Prop.forAll(defects) { defect =>
          Given("one generated continuation whose reply changes exactly one authority dimension")
          val provider = new Provider
          val payload = defect.sample.payload.mkString
          val requested = flow("d" + payload, "c" + payload)
          provider.answer = (request, _) => {
            val original = request.binding
            val binding = defect.kind match {
              case 0 => success(Binding.createC(ComponentInstanceId(componentId, "other" + payload), original.principalId, original.sessionId))
              case 1 => success(Binding.createC(instanceId, PrincipalId("other" + payload), original.sessionId))
              case 2 => success(Binding.createC(instanceId, original.principalId, "other" + payload))
              case _ => original
            }
            val returned = flow(if (defect.kind == 3) "other" + payload else requested.displayInstance,
              if (defect.kind == 4) "other" + payload else requested.correlation,
              if (defect.kind == 5) instant.minusNanos(defect.sample.nanos.toLong) else instant.plusSeconds(defect.sample.seconds))
            Consequence.success(Result.Supported(success(Resolution.createC(binding, Some("server"), Some(returned)))))
          }
          val ctx = base.withScope(ScopeContext.withPresentationContextResolver(base.scope, provider))
          When("the defective provider result attempts to confer continuation authority")
          val result = restoreC(instanceId, Some(wire(requested)), ctx)
          Then("identity and reference defects fail structurally while expired flow succeeds only with cleared state")
          if (defect.kind == 5) success(result).presentationContext shouldBe None
          else result.isSuccess shouldBe false
          provider.requests should have size 1
          true
        }
        When("ScalaCheck interprets every generated single-defect case with strictly reducing semantic shrinkers")
        val result = Test.check(parameters, property)
        Then("at least one hundred defect cases succeed without discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}

object PresentationContextRuntimePropertySpec {
  final case class Sample(payload: Vector[String], locale: String, seconds: Long, nanos: Int)
  final case class Defect(sample: Sample, kind: Int)
  val parameters: Test.Parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  val samples: Gen[Sample] = for {
    size <- Gen.choose(0, 14)
    payload <- Gen.listOfN(size, Gen.oneOf("a", "Z", "7", "é", "日", "e\u0301", "😀"))
    locale <- Gen.oneOf("und", "en-US", "ja-JP", "zh-Hant-TW", "de-DE-u-co-phonebk")
    seconds <- Gen.choose(1L, 3600L)
    nanos <- Gen.choose(0, 999999999)
  } yield Sample(payload.toVector, locale, seconds, nanos)
  val defects: Gen[Defect] = for { sample <- samples; kind <- Gen.choose(0, 5) } yield Defect(sample, kind)

  given Shrink[Sample] = Shrink { sample =>
    val payload = if (sample.payload.isEmpty) Stream.empty else Stream(sample.copy(payload = sample.payload.take(sample.payload.size / 2)))
    val locale = if (sample.locale == "und") Stream.empty else Stream(sample.copy(locale = "und"))
    val seconds = if (sample.seconds == 1) Stream.empty else Stream(sample.copy(seconds = math.max(1L, sample.seconds / 2)))
    val nanos = if (sample.nanos == 0) Stream.empty else Stream(sample.copy(nanos = sample.nanos / 2))
    payload #::: locale #::: seconds #::: nanos
  }
  given Shrink[Defect] = Shrink { defect =>
    summon[Shrink[Sample]].shrink(defect.sample).map(sample => defect.copy(sample = sample)) #:::
      (if (defect.kind == 0) Stream.empty else Stream(defect.copy(kind = defect.kind - 1)))
  }
}
