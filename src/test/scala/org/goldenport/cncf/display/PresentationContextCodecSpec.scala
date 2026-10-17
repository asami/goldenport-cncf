package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import domain.logicalui.DomainLogicalUiDefinitions
import io.circe.Json
import logicalui.{DisplayMutationCodecFixture, DisplayMutationContractFixture}
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class PresentationContextCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayProtocolSpecFixture.{success, sample, source}
  import PresentationContextSpecFixture.*

  "Presentation context" should afterWord("in spec:display-presentation-context, phase:96, slice:P96-10A") {
    "C1 inert supported families" which {
      "retain explicit empty each individual family and every optional combination in canonical order" in {
        Given("R1,R2: all eight optional combinations, canonical locale and opaque non-ASCII flow references")
        (0 until 8).foreach { mask =>
          Given("one valid support negotiation or independently selected family combination")
          val context = success(PresentationContext.createC(
            if ((mask & 1) != 0) Some("ja-JP") else None,
            if ((mask & 2) != 0) Some("変種-e\u0301-😀") else None,
            if ((mask & 4) != 0) Some(flow()) else None))
          When("the pure context is encoded and decoded through both public representations")
          val text = success(PresentationContextCodec.encodeC(context))
          val json = success(PresentationContextCodec.encodeJsonC(context))
          val decoded = success(PresentationContextCodec.decodeC(text))
          val typed = success(PresentationContextCodec.decodeJsonC(json))
          Then("only selected safe values survive and encoding remains deterministic")
          sameContext(decoded, context)
          sameContext(typed, context)
          success(PresentationContextCodec.encodeC(decoded)) shouldBe text
          val names = json.hcursor.downField("families").focus.get.asArray.get.map(_.hcursor.get[String]("name").toOption.get)
          names shouldBe (context.locale.toVector.map(_ => "cncf.locale") ++
            context.variant.toVector.map(_ => "cncf.presentation") ++ context.flow.toVector.map(_ => "cncf.display-flow"))
          text.endsWith("\n") shouldBe false
        }
        success(PresentationContextCodec.encodeC(success(PresentationContext.createC()))) shouldBe "{\"version\":1,\"families\":[]}"
      }

      "retain canonical BCP47 Unicode byte boundaries and exact Instant nanos without clock policy" in {
        Given("R1,R2: canonical tags, exact UTF8 limits and past/future nanosecond instants")
        val locales = Vector("und", "en-US", "zh-Hant-TW", "de-DE-u-co-phonebk", "x-client")
        locales.foreach { locale => Vector(Instant.MIN, Instant.MAX, Instant.ofEpochSecond(-1, 987654321)).foreach { instant =>
          Given("one independently supplied canonical tag and instant with opaque bounded identifiers")
          val supplied = success(PresentationContext.Flow.createC("é" * 128, "界" * 85 + "x", instant))
          val context = success(PresentationContext.createC(Some(locale), Some("é" * 64), Some(supplied)))
          When("the context traverses its pure wire boundary")
          val decoded = success(PresentationContextCodec.decodeC(success(PresentationContextCodec.encodeC(context))))
          Then("the exact locale text UTF8 limits references epoch second and nanos remain supplied values")
          sameContext(decoded, context)
          decoded.flow.get.expiresAt.getEpochSecond shouldBe instant.getEpochSecond
          decoded.flow.get.expiresAt.getNano shouldBe instant.getNano
        } }
      }
    }

    "C2 strict values and envelopes" which {
      "reject unsafe values and malformed Option shapes with redacted typed factory failures" in {
        Given("R1: empty trimmed/control/unpaired-Unicode and over-byte-limit values, including nullable Java callers")
        val unsafe = Vector("", " x", "x ", "\u00a0x", "x\u2003", "x\u0000", "x\u0085", "\ud800", "\udfff", "a" * 129, "é" * 65)
        When("validated factories interpret all defective values and null container entries")
        val variants = unsafe.map(value => PresentationContext.createC(variant = Some(value)))
        val locales = Vector("en-us", "EN", "en_US", " en-US", "i-klingon", "en-", "a" * 129)
          .map(value => PresentationContext.createC(locale = Some(value)))
        val references = unsafe.filterNot(value => value == "a" * 129 || value == "é" * 65) ++ Vector("a" * 257, "é" * 129)
        val flows = references.flatMap(value => Vector(
          PresentationContext.Flow.createC(value, "reference", Instant.EPOCH),
          PresentationContext.Flow.createC("reference", value, Instant.EPOCH)))
        val results = variants ++ locales ++ flows ++ Vector(
          PresentationContext.createC(locale = null), PresentationContext.createC(variant = null),
          PresentationContext.createC(flow = null), PresentationContext.createC(locale = Some(null)),
          PresentationContext.createC(variant = Some(null)), PresentationContext.createC(flow = Some(null)),
          PresentationContext.Flow.createC(null, "reference", Instant.EPOCH),
          PresentationContext.Flow.createC("reference", null, Instant.EPOCH),
          PresentationContext.Flow.createC("reference", "reference", null))
        Then("each failure has a fixed typed value cause and contains no supplied value or nested exception")
        results.foreach { result =>
          val failure = DisplayProtocolSpecFixture.failure(result)
          failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
          val carrier = failure.conclusion.observation.cause.getException.get
          carrier shouldBe a[PresentationContext.ValidationException]
          carrier.asInstanceOf[PresentationContext.ValidationException].diagnostic.code shouldBe "DSP96_CONTEXT_VALUE_INVALID"
          Option(carrier.getCause) shouldBe None
        }
      }

      "reject missing extra null wrong primitives and duplicate members at every supported object" in {
        Given("R2,R3: the complete context and a single changed member at each root/envelope/data boundary")
        val json = success(PresentationContextCodec.encodeJsonC(PresentationContextSpecFixture.all()))
        val shapes = DisplayMutationCodecFixture.shapes(json)
        val duplicates = DisplayMutationCodecFixture.duplicates(json)
        When("strict decoding interprets every structural defect and duplicate key")
        val shaped = shapes.map(PresentationContextCodec.decodeC)
        val duplicated = duplicates.map(PresentationContextCodec.decodeC)
        Then("all defects reject the whole context rather than losing a supported family")
        shaped.foreach(result => checkFailure(result, "MEMBER_INVALID"))
        duplicated.foreach(result => checkFailure(result, "JSON_INVALID", Some("$")))
      }

      "reject noncanonical known scalars and independently version the root" in {
        Given("R2,R3: wrong canonical locale, unsafe variant, noncanonical expiry, root versions and primitives")
        val badvalues = Vector(
          family("cncf.locale", Json.obj("languageTag" -> Json.fromString("en-us"))),
          family("cncf.presentation", Json.obj("variant" -> Json.fromString("SECRET "))),
          family("cncf.display-flow", Json.obj("displayInstance" -> Json.fromString(" x"), "correlation" -> Json.fromString("c"), "expiresAt" -> Json.fromString("1970-01-01T00:00:00Z"))),
          family("cncf.display-flow", Json.obj("displayInstance" -> Json.fromString("x"), "correlation" -> Json.fromString("c"), "expiresAt" -> Json.fromString("1970-01-01T00:00:00.000Z"))))
        When("the known values unsupported integer root versions and noninteger versions are decoded")
        val badresults = badvalues.map(value => PresentationContextCodec.decodeJsonC(wire(Vector(value))))
        val incompatible = Vector("0", "-1", "2", "1e1000000000").map(version => PresentationContextCodec.decodeC(
          "{\"version\":" + version + ",\"families\":[]}"))
        val malformed = Vector("null", "true", "\"1\"", "1.5").map(version => PresentationContextCodec.decodeC(
          "{\"version\":" + version + ",\"families\":[]}"))
        Then("known values fail closed root compatibility has a distinct code and wrong primitives stay malformed")
        badresults.foreach(result => checkFailure(result, "VALUE_INVALID"))
        incompatible.foreach(result => checkFailure(result, "VERSION_INCOMPATIBLE", Some("$.version")))
        malformed.foreach(result => checkFailure(result, "MEMBER_INVALID", Some("$.version")))
        checkFailure(PresentationContextCodec.decodeC(null), "INPUT_INVALID", Some("json"))
        checkFailure(PresentationContextCodec.decodeJsonC(null), "INPUT_INVALID", Some("json"))
        checkFailure(PresentationContextCodec.encodeC(null), "INPUT_INVALID", Some("context"))
        checkFailure(PresentationContextCodec.encodeJsonC(null), "INPUT_INVALID", Some("context"))
        Vector("null", "[]", "true", "{SECRET").foreach(text => PresentationContextCodec.decodeC(text).isSuccess shouldBe false)
      }
    }

    "C3 bounded optional extensions" which {
      "discard unknown names and unsupported positive versions before echo without reinterpreting their data" in {
        Given("R3: valid closed extension envelopes and unsupported known-family data which is not a v1 payload")
        val known = family("cncf.locale", Json.obj("languageTag" -> Json.fromString("ja")))
        val extension = family("vendor.optional-family", Json.obj("nested" -> Json.arr(Json.Null, Json.fromBoolean(true))))
        val unsupported = family("cncf.presentation", Json.obj("future" -> Json.fromString("SECRET")), Json.fromInt(2))
        val text = wire(Vector(unsupported, extension, known)).noSpaces
        When("optional extensions and a huge positive integral version are decoded")
        val context = success(PresentationContextCodec.decodeC(text))
        val huge = success(PresentationContextCodec.decodeC(wire(Vector(unsupported)).noSpaces.replace("\"version\":2", "\"version\":1e1000000000")))
        Then("only the supported locale remains and no opaque extension payload is retained")
        context.locale shouldBe Some("ja")
        context.variant shouldBe None
        context.flow shouldBe None
        success(PresentationContextCodec.encodeC(context)) shouldBe wire(Vector(known)).noSpaces
        success(PresentationContextCodec.encodeC(huge)) shouldBe wire(Vector.empty).noSpaces
      }

      "reject duplicate names before filtering and invalid envelope names versions or family counts" in {
        Given("R3: duplicate supported/unknown/unsupported names, invalid grammar and nonpositive family versions")
        val known = family("cncf.locale", Json.obj("languageTag" -> Json.fromString("ja")))
        val unknown = family("vendor.future", Json.obj())
        val unsupported = family("cncf.locale", Json.obj(), Json.fromInt(2))
        When("duplicates and malformed optional envelopes are interpreted")
        val duplicates = Vector(Vector(known, known), Vector(known, unsupported), Vector(unknown, unknown))
          .map(values => PresentationContextCodec.decodeJsonC(wire(values)))
        val names = Vector("locale", "CNCF.locale", "cncf..locale", ".cncf.locale", "cncf.locale-", "cncf.é", "a." + "x" * 63)
          .map(name => PresentationContextCodec.decodeJsonC(wire(Vector(family(name, Json.obj())))))
        val versions = Vector(Json.fromInt(0), Json.fromInt(-1), Json.fromDoubleOrNull(1.5), Json.fromString("1"), Json.Null)
          .map(version => PresentationContextCodec.decodeJsonC(wire(Vector(family("vendor.future", Json.obj(), version)))))
        Then("names remain unique across optional filtering and all envelopes remain strict")
        duplicates.foreach(result => checkFailure(result, "DUPLICATE_FAMILY", Some("$.families[1].name")))
        names.foreach(result => checkFailure(result, "NAME_INVALID", Some("$.families[0].name")))
        versions.foreach(result => checkFailure(result, "MEMBER_INVALID", Some("$.families[0].version")))
        success(PresentationContextCodec.decodeJsonC(wire(Vector.tabulate(8)(index => family("vendor.family" + index, Json.obj()))))).locale shouldBe None
        checkFailure(PresentationContextCodec.decodeJsonC(wire(Vector.tabulate(9)(index => family("vendor.family" + index, Json.obj())))), "LIMIT_EXCEEDED", Some("$.families"))
      }

      "bound bytes and nesting including discarded data before parsing or recursive rendering" in {
        Given("R3: exact 8192-byte optional data and nested containers including quoted escaped brackets")
        val empty = wire(Vector(family("vendor.future", Json.obj("text" -> Json.fromString(""))))).noSpaces
        val exact = empty.replace("\"text\":\"\"", "\"text\":\"" + ("x" * (8192 - empty.length)) + "\"")
        val quoted = wire(Vector(family("vendor.future", Json.obj("text" -> Json.fromString("[\\\"{" * 30))))).noSpaces
        def _nested_(depth: Int): Json = (0 until depth).foldLeft(Json.fromString("leaf"))((value, _) => Json.arr(value))
        val legal = wire(Vector(family("vendor.future", Json.obj("nested" -> _nested_(4)))))
        val deep = wire(Vector(family("vendor.future", Json.obj("nested" -> _nested_(5)))))
        When("raw and decoded-tree entries receive exact limits and over-limit candidates")
        val accepted = Vector(PresentationContextCodec.decodeC(exact), PresentationContextCodec.decodeJsonC(DisplayProtocolSpecFixture.json(exact)),
          PresentationContextCodec.decodeC(quoted), PresentationContextCodec.decodeC(legal.noSpaces), PresentationContextCodec.decodeJsonC(legal))
        val rejected = Vector(PresentationContextCodec.decodeC(exact + " "),
          PresentationContextCodec.decodeC(exact.replace("xxx", "éxx")),
          PresentationContextCodec.decodeJsonC(DisplayProtocolSpecFixture.json(exact.replace("xxx", "éxx"))),
          PresentationContextCodec.decodeC(deep.noSpaces), PresentationContextCodec.decodeJsonC(deep),
          PresentationContextCodec.decodeC("[" * 9 + "SECRET-malformed"),
          PresentationContextCodec.decodeC(" " * 8193 + "SECRET-malformed"))
        Then("exact bounds and quoted brackets pass while all oversize or deep inputs fail with a redacted limit cause")
        exact.getBytes(StandardCharsets.UTF_8).length shouldBe 8192
        accepted.foreach(_.isSuccess shouldBe true)
        rejected.foreach(result => checkFailure(result, "LIMIT_EXCEEDED", Some("$")))
      }
    }

    "C4 Display compatibility and trusted preservation" which {
      "attach and remove inert data without replacing the admitted definition or model" in {
        Given("R4: an already validated List/Detail pair and all supported inert context values")
        val fixture = sample()
        val context = PresentationContextSpecFixture.all()
        fixture.displays.foreach { original =>
          Given("one existing trusted display and its exact original members")
          val before = success(DisplayProtocolCodec.encodeC(source, original))
          When("context is attached, encoded, restored and explicitly removed")
          val attached = success(Display.withPresentationContextC(original, Some(context)))
          val created = success(Display.createC(fixture.definitions, original.model, Some(context)))
          val json = success(DisplayProtocolCodec.encodeJsonC(source, attached))
          val restored = success(DisplayProtocolCodec.decodeC(source, fixture.definitions, success(DisplayProtocolCodec.encodeC(source, attached))))
          val cleared = success(Display.withPresentationContextC(attached, None))
          Then("source references stay exact, absent bytes return unchanged and wire authority remains the original admission")
          Vector(attached, created, cleared).foreach { value =>
            (value.definition eq original.definition) shouldBe true
            (value.model eq original.model) shouldBe true
          }
          (restored.definition eq original.definition) shouldBe true
          DisplayProtocolSpecFixture.checkDisplay(restored, original)
          sameContext(restored.presentationContext.get, context)
          json.asObject.get.keys.toVector.last shouldBe "presentationContext"
          success(DisplayProtocolCodec.encodeC(source, cleared)) shouldBe before
          original.presentationContext shouldBe None
          Vector(Display.createC(fixture.definitions, original.model, null), Display.createC(fixture.definitions, original.model, Some(null)),
            Display.withPresentationContextC(original, null), Display.withPresentationContextC(original, Some(null)),
            Display.withPresentationContextC(null, None)).foreach(_.isSuccess shouldBe false)
          DisplayProtocolCodec.decodeC(source, fixture.definitions, json.mapObject(_.add("presentationContext", Json.Null)).noSpaces).isSuccess shouldBe false
        }
      }

      "retain existing no-context golden bytes and transitive mutation-ready context" in {
        Given("R4: existing generated trusted definitions and two exact absent-context golden resources")
        val definitions = success(GeneratedLogicalUiAbi.admitC(DomainLogicalUiDefinitions.definitions))
        Vector("ess2-01-list.json", "ess2-01-detail.json").foreach { name =>
          Given("one original protocol golden including its required final LF")
          val stream = Option(getClass.getResourceAsStream("/display/protocol/v1/" + name)).getOrElse(fail("expected existing golden"))
          val bytes = try stream.readAllBytes() finally stream.close()
          val text = new String(bytes, StandardCharsets.UTF_8)
          When("the golden is restored against its unchanged definitions and encoded again")
          val display = success(DisplayProtocolCodec.decodeC(source, definitions, text))
          val encoded = success(DisplayProtocolCodec.encodeC(source, display))
          Then("the optional context remains absent and all original bytes including the single LF match")
          display.presentationContext shouldBe None
          (encoded + "\n").getBytes(StandardCharsets.UTF_8).toVector shouldBe bytes.toVector
        }
        Given("R4: an already supplied pure Created receipt with a ready native projection")
        val fixture = DisplayMutationCodecFixture.nativeCase("Title", BigDecimal("1.20"), true, Instant.EPOCH)
        val display = success(Display.withPresentationContextC(fixture.display, Some(PresentationContextSpecFixture.all())))
        val resource = display.model.asInstanceOf[DisplayModel.DetailModel].resource
        val receipt = success(DisplayMutationResult.receiptC(fixture.binding.selection, fixture.request,
          DisplayMutationResult.OutcomeKind.Created, resource, None))
        val result = success(DisplayMutationResult.createC(receipt, Some(DisplayMutationResult.ProjectionState.Ready(
          DisplayMutationResult.ReloadedDisplay(fixture.binding.selection.source, display)))))
        When("the existing pure mutation result codec traverses its nested Display boundary")
        val encoded = success(DisplayMutationCodec.encodeResultC(result))
        val decoded = success(DisplayMutationCodec.decodeResultC(fixture.binding.selection,
          DisplayMutationContractFixture.nativeDefinitions(GeneratedLogicalUiAbi.ValueKind.values.toVector), fixture.request, None, encoded))
        Then("ready context survives transitively and the canonical receipt still prohibits replay")
        decoded.retryCommand shouldBe false
        decoded.projection match {
          case Some(DisplayMutationResult.ProjectionState.Ready(snapshot)) => sameContext(snapshot.display.presentationContext.get, display.presentationContext.get)
          case _ => fail("expected supplied ready projection")
        }
      }

      "serialize only the closed safe context keys and reject authority-shaped members" in {
        Given("R1,R2,R5: all safe families and forbidden internal authority member names")
        val context = PresentationContextSpecFixture.all()
        val forbidden = Vector("credentials", "security", "runtime", "unitOfWork", "provider", "experiment", "run", "arm", "allocation", "executionPlanReference")
        val json = success(PresentationContextCodec.encodeJsonC(context))
        When("the context is encoded and each forbidden root or supported-data member is offered")
        val encoded = success(PresentationContextCodec.encodeC(context))
        val rejected = forbidden.flatMap { name => Vector(
          PresentationContextCodec.decodeJsonC(json.mapObject(_.add(name, Json.obj()))),
          PresentationContextCodec.decodeJsonC(wire(Vector(family("cncf.presentation",
            Json.obj("variant" -> Json.fromString("safe"), name -> Json.fromString("SECRET"))))))) }
        Then("the wire contains only declared public data and no authority-shaped member is admitted")
        forbidden.foreach(name => encoded should not include ("\"" + name + "\""))
        rejected.foreach(result => checkFailure(result, "MEMBER_INVALID"))
        context.flow.get.expiresAt shouldBe expiry
      }
    }
  }
}

private[display] object PresentationContextSpecFixture extends Matchers {
  import DisplayProtocolSpecFixture.success
  val expiry: Instant = Instant.ofEpochSecond(-1, 987654321)
  def flow(): PresentationContext.Flow = success(PresentationContext.Flow.createC("表示-😀", "相関-e\u0301", expiry))
  def all(): PresentationContext = success(PresentationContext.createC(Some("ja-JP"), Some("variant-😀"), Some(flow())))
  def family(name: String, data: Json, version: Json = Json.fromInt(1)): Json =
    Json.obj("name" -> Json.fromString(name), "version" -> version, "data" -> data)
  def wire(families: Vector[Json]): Json = Json.obj("version" -> Json.fromInt(1), "families" -> Json.fromValues(families))
  def sameContext(actual: PresentationContext, expected: PresentationContext): Unit = {
    actual.locale shouldBe expected.locale
    actual.variant shouldBe expected.variant
    actual.flow.isDefined shouldBe expected.flow.isDefined
    actual.flow.zip(expected.flow).foreach { case (left, right) =>
      left.displayInstance shouldBe right.displayInstance
      left.correlation shouldBe right.correlation
      left.expiresAt shouldBe right.expiresAt
    }
  }
  def checkFailure(result: Consequence[?], code: String, path: Option[String] = None): Unit = {
    val failure = DisplayProtocolSpecFixture.failure(result)
    failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    failure.conclusion.observation.cause.getException match {
      case Some(carrier: PresentationContextCodec.CodecException) =>
        carrier.diagnostic.code shouldBe "DSP96_CONTEXT_" + code
        path.foreach(value => carrier.diagnostic.path shouldBe value)
        carrier.getMessage should not include "SECRET"
        Option(carrier.getCause) shouldBe None
      case _ => fail("expected typed context codec diagnostic")
    }
  }
}
