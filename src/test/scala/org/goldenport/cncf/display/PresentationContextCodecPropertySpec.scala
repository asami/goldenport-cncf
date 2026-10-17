package org.goldenport.cncf.display

import java.time.Instant
import io.circe.Json
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class PresentationContextCodecPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayProtocolSpecFixture.{success, sample, source}
  import PresentationContextSpecFixture.{sameContext, checkFailure, family, wire}
  import PresentationContextCodecPropertySpec.*
  import PresentationContextCodecPropertySpec.given

  "Presentation context laws" should afterWord("in spec:display-presentation-context, phase:96, slice:P96-10A") {
    "P1 total valid optional combinations" which {
      "retain exact supplied values under family permutation canonical round trips and Display attachment" in {
        Given("R1,R2,R3,R4: total generators vary optional masks Unicode scalar sequences canonical locales seconds and nanos")
        val fixture = sample()
        val property = Prop.forAll(samples) { generated =>
          Given("one legal generated optional context and the independently admitted trusted display pair")
          val context = generated.context
          val before = fixture.displays.map(display => success(DisplayProtocolCodec.encodeC(source, display)))
          val canonical = success(PresentationContextCodec.encodeJsonC(context))
          val reversed = canonical.mapObject(_.add("families", Json.fromValues(canonical.hcursor.downField("families").focus.get.asArray.get.reverse)))
          When("both permuted representations and contextual Display snapshots traverse their strict pure codecs")
          val decoded = success(PresentationContextCodec.decodeC(reversed.spaces2))
          val typed = success(PresentationContextCodec.decodeJsonC(reversed))
          val attached = fixture.displays.map(display => success(Display.withPresentationContextC(display, Some(context))))
          val restored = attached.map(display => success(DisplayProtocolCodec.decodeC(source, fixture.definitions,
            success(DisplayProtocolCodec.encodeC(source, display)))))
          Then("all option choices exact supplied Unicode nanos trusted members and canonical order survive without authority")
          context.locale shouldBe (if ((generated.mask & 1) != 0) Some(generated.locale) else None)
          context.variant shouldBe (if ((generated.mask & 2) != 0) Some("v" + generated.payload.mkString) else None)
          context.flow.isDefined shouldBe ((generated.mask & 4) != 0)
          context.flow.foreach { flow =>
            flow.displayInstance shouldBe "i" + generated.payload.mkString
            flow.correlation shouldBe "c" + generated.payload.reverse.mkString
            flow.expiresAt shouldBe Instant.ofEpochSecond(generated.seconds, generated.nanos.toLong)
          }
          sameContext(decoded, context)
          sameContext(typed, context)
          success(PresentationContextCodec.encodeJsonC(decoded)) shouldBe canonical
          success(PresentationContextCodec.encodeC(typed)) shouldBe success(PresentationContextCodec.encodeC(context))
          restored.zip(fixture.displays).foreach { case (actual, original) =>
            sameContext(actual.presentationContext.get, context)
            DisplayProtocolSpecFixture.checkDisplay(actual, original)
          }
          attached.zip(fixture.displays).foreach { case (actual, original) =>
            (actual.definition eq original.definition) shouldBe true
            (actual.model eq original.model) shouldBe true
          }
          fixture.displays.map(display => success(DisplayProtocolCodec.encodeC(source, display))) shouldBe before
          true
        }
        When("ScalaCheck interprets at least one hundred legal cases with reducing optional-value shrinkers")
        val result = Test.check(parameters, property)
        Then("the law passes with at least one hundred successes and zero discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "P2 total single-defect rejection" which {
      "reject one varying defect with stable redacted diagnostics and unchanged trusted Displays" in {
        Given("R2,R3,R4: total generators independently vary lawful payloads and one closed defect, preserving the defect through shrinking")
        val fixture = sample()
        val property = Prop.forAll(rejected) { generated =>
          Given("one generated candidate with precisely one value envelope name or compatibility defect")
          val candidate = generated.candidate
          val definitions = fixture.definitions.definitions
          val models = fixture.displays.map(_.model)
          val before = fixture.displays.map(display => success(DisplayProtocolCodec.encodeC(source, display)))
          val contextual = DisplayProtocolSpecFixture.json(before.head).mapObject(_.add("presentationContext", candidate._1))
          When("raw and typed context boundaries plus trusted Display restoration interpret that single defect")
          val raw = PresentationContextCodec.decodeC(candidate._1.noSpaces)
          val typed = PresentationContextCodec.decodeJsonC(candidate._1)
          val display = DisplayProtocolCodec.decodeC(source, fixture.definitions, contextual.noSpaces)
          Then("each result rejects with the prescribed typed code and path while no trusted source is replaced")
          Vector(raw, typed, display).foreach(result => checkFailure(result, candidate._2, Some(candidate._3)))
          (fixture.definitions.definitions eq definitions) shouldBe true
          fixture.displays.zip(models).foreach { case (original, model) => (original.model eq model) shouldBe true }
          fixture.displays.map(display => success(DisplayProtocolCodec.encodeC(source, display))) shouldBe before
          true
        }
        When("ScalaCheck interprets at least one hundred single-defect cases with payload and defect shrinkers")
        val result = Test.check(parameters, property)
        Then("closed rejection passes with at least one hundred successes and zero discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}

object PresentationContextCodecPropertySpec {
  import DisplayProtocolSpecFixture.success
  import PresentationContextSpecFixture.{family, wire}

  final case class Sample(mask: Int, payload: Vector[String], locale: String, seconds: Long, nanos: Int) {
    def context: PresentationContext = {
      val flow = if ((mask & 4) == 0) None else Some(success(PresentationContext.Flow.createC(
        "i" + payload.mkString, "c" + payload.reverse.mkString, Instant.ofEpochSecond(seconds, nanos.toLong))))
      success(PresentationContext.createC(if ((mask & 1) != 0) Some(locale) else None,
        if ((mask & 2) != 0) Some("v" + payload.mkString) else None, flow))
    }
  }

  final case class Rejected(sample: Sample, kind: Int) {
    def candidate: (Json, String, String) = {
      val text = "v" + sample.payload.mkString
      val valid = family("cncf.presentation", Json.obj("variant" -> Json.fromString(text)))
      kind match {
        case 0 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.fromString(" " + text))))),
          "VALUE_INVALID", "$.families[0].data.variant")
        case 1 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.fromString(text + "\u0000"))))),
          "VALUE_INVALID", "$.families[0].data.variant")
        case 2 =>
          val unknown = family("vendor.future", Json.obj("opaque" -> Json.fromString(text)))
          (wire(Vector(unknown, unknown)), "DUPLICATE_FAMILY", "$.families[1].name")
        case 3 => (wire(Vector(valid)).mapObject(_.add("version", Json.fromLong(2L + math.abs(sample.seconds)))),
          "VERSION_INCOMPATIBLE", "$.version")
        case 4 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.fromString(text), "extra" -> Json.fromString("SECRET"))))),
          "MEMBER_INVALID", "$.families[0].data")
        case 5 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.Null)))),
          "MEMBER_INVALID", "$.families[0].data.variant")
        case 6 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.fromString(text + "\ud800"))))),
          "VALUE_INVALID", "$.families[0].data.variant")
        case 7 => (wire(Vector(family("cncf.presentation", Json.obj("variant" -> Json.fromString(text)), Json.fromDoubleOrNull(1.5)))),
          "MEMBER_INVALID", "$.families[0].version")
      }
    }
  }

  val parameters: Test.Parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  val samples: Gen[Sample] = for {
    mask <- Gen.choose(0, 7)
    size <- Gen.choose(0, 18)
    payload <- Gen.listOfN(size, Gen.oneOf("a", "Z", "7", "é", "日", "e\u0301", "😀", "-"))
    locale <- Gen.oneOf("und", "en-US", "ja-JP", "zh-Hant-TW", "de-DE-u-co-phonebk")
    seconds <- Gen.choose(-1000000000L, 1000000000L)
    nanos <- Gen.choose(0, 999999999)
  } yield Sample(mask, payload.toVector, locale, seconds, nanos)
  val rejected: Gen[Rejected] = for { sample <- samples; kind <- Gen.choose(0, 7) } yield Rejected(sample, kind)

  given Shrink[Sample] = Shrink { sample =>
    val mask = if (sample.mask == 0) Stream.empty else Stream(sample.copy(mask = sample.mask & (sample.mask - 1)))
    val payload = if (sample.payload.isEmpty) Stream.empty else Stream(sample.copy(payload = sample.payload.take(sample.payload.size / 2)))
    val locale = if (sample.locale == "und") Stream.empty else Stream(sample.copy(locale = "und"))
    val seconds = if (sample.seconds == 0L) Stream.empty else Stream(sample.copy(seconds = sample.seconds / 2L))
    val nanos = if (sample.nanos == 0) Stream.empty else Stream(sample.copy(nanos = sample.nanos / 2))
    mask #::: payload #::: locale #::: seconds #::: nanos
  }
  given Shrink[Rejected] = Shrink { rejected =>
    summon[Shrink[Sample]].shrink(rejected.sample).map(sample => rejected.copy(sample = sample)) #:::
      (if (rejected.kind == 0) Stream.empty else Stream(rejected.copy(kind = rejected.kind - 1)))
  }
}
