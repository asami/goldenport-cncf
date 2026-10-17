package org.goldenport.cncf.display

import java.time.Instant
import io.circe.Json
import logicalui.{DisplayMutationCodecFixture, DisplayMutationContractFixture}
import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationResultCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationResult.*
  import DisplayMutationCodec.*
  import DisplayMutationCodecFixture.*
  import DisplayMutationContractFixture.{success, definitions, resource, sourceC, Trace}
  private def _decode(fixture: ResultCase, json: String): Consequence[Result] =
    decodeResultC(fixture.result.receipt.selection, definitions, fixture.request, fixture.observedRevision, json)
  private def _equivalent(actual: Result, expected: Result): Unit = {
    actual.receipt.selection.definition shouldBe expected.receipt.selection.definition
    actual.receipt.selection.source.componentId.name shouldBe expected.receipt.selection.source.componentId.name
    actual.receipt.selection.source.resourceType shouldBe expected.receipt.selection.source.resourceType
    actual.receipt.resource shouldBe expected.receipt.resource
    actual.receipt.kind shouldBe expected.receipt.kind
    actual.retryCommand shouldBe false
    actual.reloadRequired shouldBe expected.reloadRequired
    (actual.projection, expected.projection) match {
      case (Some(ProjectionState.Ready(left)), Some(ProjectionState.Ready(right))) =>
        left.source.componentId.name shouldBe right.source.componentId.name
        left.source.resourceType shouldBe right.source.resourceType
        left.display.model shouldBe right.display.model
        left.display.definition shouldBe right.display.definition
      case (left, right) => left shouldBe right
    }
  }
  "E11 Result wire" should afterWord("in spec:display-mutation, example:E11, rules:R2,R3,R4,R5, phase:96") {
    "authoritative supplied outcomes RS1" which {
      "round trip Created INITIAL Updated next Unchanged current and Deleted last matched revisions" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS1: supplied canonical receipts for all four closed outcomes")
        val cases = Vector(resultCase(OutcomeKind.Created), resultCase(OutcomeKind.Updated),
          resultCase(OutcomeKind.Unchanged, revision = Long.MaxValue), resultCase(OutcomeKind.Deleted, revision = Long.MaxValue))
        cases.foreach { fixture =>
          Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 RS1: one typed original request observation and supplied admitted Result")
          When("the pure result boundary encodes and validates via receiptC and createC")
          val encoded = success(encodeResultC(fixture.result))
          val actual = success(_decode(fixture, encoded))
          Then("supplied source identity outcome positive revision projection and no replay survive")
          _equivalent(actual, fixture.result)
          at(wire(encoded), Vector("resource", "revision")).asNumber.get.toString shouldBe fixture.result.receipt.resource.revision.value.toString
          fixture.result.receipt.kind match {
            case OutcomeKind.Created => actual.receipt.resource.revision.value shouldBe 1L
            case OutcomeKind.Updated => actual.receipt.resource.revision shouldBe success(fixture.observedRevision.get.nextC)
            case _ => actual.receipt.resource.revision shouldBe fixture.observedRevision.get
          }
        }
      }
      "reject wrong original request observation outcome identity retry flag and noncanonical outer revision scalars" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS1: canonical Updated result and independent result-boundary tampering")
        val fixture = resultCase(OutcomeKind.Updated)
        val encoded = success(encodeResultC(fixture.result))
        val root = wire(encoded)
        val paths = Vector(Vector("componentId"), Vector("resourceType"), Vector("definition", "screenId"),
          Vector("definition", "revision"), Vector("definition", "mutationRevision"), Vector("provenance", "producerId"),
          Vector("provenance", "sourceId"), Vector("provenance", "revision"))
        val candidates = paths.map(path => set(root, path, Json.fromString("attacker-secret")).noSpaces) ++
          Vector("created", "unchanged", "deleted", "foreign").map(value => set(root, Vector("outcome"), Json.fromString(value)).noSpaces) ++
          Vector(set(root, Vector("resource", "entityId"), Json.fromString(resource(2).entityId.value)).noSpaces,
            set(root, Vector("resource", "entityId"), Json.fromString(" " + resource().entityId.value)).noSpaces,
            set(root, Vector("retryCommand"), Json.fromBoolean(true)).noSpaces,
            set(root, Vector("retryCommand"), Json.fromString("false")).noSpaces) ++
          Vector("0", "-1", "8.0", "8e0", "8.5", "9223372036854775808", "\"8\"", "true")
            .map(value => encoded.replace("\"revision\":8", "\"revision\":" + value)) ++
          Vector("1.0", "1e0", "\"1\"").map(value => encoded.replace("\"version\":1", "\"version\":" + value))
        When("each tampered outer receipt and incompatible original request or observation is decoded")
        val results = candidates.map(_decode(fixture, _)) ++ Vector(
          decodeResultC(fixture.result.receipt.selection, definitions, fixture.request, None, encoded),
          decodeResultC(fixture.result.receipt.selection, definitions, fixture.request, Some(resource(revision = 6).revision), encoded),
          decodeResultC(fixture.result.receipt.selection, definitions, Request.Delete(fixture.result.receipt.selection, resource().entityId),
            fixture.observedRevision, encoded),
          decodeResultC(fixture.result.receipt.selection, definitions, Request.Update(fixture.result.receipt.selection, resource(2).entityId,
            Vector(FieldInput(DisplayMutationContractFixture.title, DisplayValue.Text("new")))), fixture.observedRevision, encoded))
        Then("every mismatched or noncanonical receipt fails without fabricating a committed success")
        results.foreach { result =>
          result.isSuccess shouldBe false
          conclusion(result).observation.taxonomy shouldBe Taxonomy.argumentInvalid
          problem(result).toString should not include "attacker-secret"
        }
        OutcomeKind.values.toVector.foreach { kind =>
          Given("docs/spec/display-mutation.md R4,R5 E11 RS1: each original request kind and its supplied positive receipt")
          val candidate = resultCase(kind)
          val json = wire(success(encodeResultC(candidate.result)))
          val wrongrevision = if (kind == OutcomeKind.Created) 2L else 11L
          val wrongobservation = if (kind == OutcomeKind.Created) Some(resource().revision) else None
          When("the receipt revision or original operation observation is changed independently")
          val revisionresult = _decode(candidate, set(json, Vector("resource", "revision"), Json.fromLong(wrongrevision)).noSpaces)
          val observationresult = decodeResultC(candidate.result.receipt.selection, definitions, candidate.request,
            wrongobservation, success(encodeResultC(candidate.result)))
          Then("Created initial Updated next and Unchanged or Deleted matched revision rules all reject substitution")
          revisionresult.isSuccess shouldBe false
          observationresult.isSuccess shouldBe false
        }
      }
    }
    "unchanged nested read authority RS2" which {
      "round trip native Ready fidelity and retain the existing read-v1 resource revision string" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS2: independently admitted all-kind display with exact text decimal scale and Instant nanos")
        val text = " \ud800\udfff\n日 Ω e\u0301\"\\ "
        val number = BigDecimal(new java.math.BigDecimal(new java.math.BigInteger("12345000"), 5))
        val fixture = nativeCase(text, number, false, Instant.ofEpochSecond(-1, 987654321))
        val current = fixture.display.model match { case detail: DisplayModel.DetailModel => detail.resource; case _ => fail("expected Detail") }
        val receipt = success(receiptC(fixture.binding.selection, fixture.request, OutcomeKind.Created, current, None))
        val result = success(createC(receipt, Some(ProjectionState.Ready(ReloadedDisplay(fixture.binding.selection.source, fixture.display)))))
        When("admitted Result uses the unchanged read encoder then the read decoder before Ready construction")
        val encoded = success(encodeResultC(result))
        val actual = success(decodeResultC(fixture.binding.selection, fixture.binding.definitions, fixture.request, None, encoded))
        val nested = at(wire(encoded), Vector("projection", "display"))
        val independent = wire(success(DisplayProtocolCodec.encodeC(fixture.binding.selection.source, fixture.display)))
        Then("inner read-v1 shape and every native scalar survive without revision or schema rewriting")
        nested shouldBe independent
        at(nested, Vector("model", "resource", "revision")).asString shouldBe Some("1")
        _equivalent(actual, result)
        val display = actual.projection match { case Some(ProjectionState.Ready(snapshot)) => snapshot.display; case _ => fail("expected Ready") }
        val values = display.model match { case detail: DisplayModel.DetailModel => detail.sections.head.fields.map(_.value); case _ => fail("expected Detail") }
        values.head match { case DisplayValue.Text(value) => value.toVector shouldBe text.toVector; case _ => fail("expected Text") }
        values(1) match { case DisplayValue.Number(value) =>
          value.bigDecimal.unscaledValue shouldBe number.bigDecimal.unscaledValue
          value.bigDecimal.scale shouldBe number.bigDecimal.scale
        case _ => fail("expected Number") }
        values(3) shouldBe DisplayValue.Timestamp(Instant.ofEpochSecond(-1, 987654321))
      }
      "reject foreign nested component and resourceType independently of outer source while equal logical Source succeeds" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS2: a valid Ready with identical definition identity and revision in all nested candidates")
        val fixture = resultCase(OutcomeKind.Updated)
        val encoded = success(encodeResultC(fixture.result))
        val root = wire(encoded)
        val inner = Vector("projection", "display")
        val candidates = Vector(set(root, inner :+ "componentId", Json.fromString("org.goldenport.fixture.Foreign")).noSpaces,
          set(root, inner :+ "resourceType", Json.fromString("Foreign")).noSpaces)
        When("each foreign inner source is validated by the original read decoder before a ReloadedDisplay is constructed")
        val results = candidates.map(_decode(fixture, _))
        Then("the original structured read failure carrier and source mismatch code survive unchanged")
        results.foreach { result =>
          result.isSuccess shouldBe false
          conclusion(result).observation.cause.getException.collect { case carrier: DisplayProtocol.CodecException =>
            carrier.diagnostic.code
          } shouldBe Some("DSP96_WIRE_SOURCE_MISMATCH")
        }
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 RS2: a separately allocated source with equal logical component and resourceType")
        val equal = success(sourceC())
        val expected = fixture.result.receipt.selection.copy(source = equal)
        When("the same Ready is decoded against the independently allocated equal trusted Source")
        val actual = success(decodeResultC(expected, definitions, fixture.request, fixture.observedRevision, encoded))
        Then("logical correspondence succeeds and the validated read carries that supplied source")
        _equivalent(actual, fixture.result)
        actual.projection match {
          case Some(ProjectionState.Ready(snapshot)) => (snapshot.source eq equal) shouldBe true
          case _ => fail("expected Ready")
        }
      }
    }
    "projection branches RS3" which {
      "preserve both committed Pending reasons and Deleted reload-list with no display or replay" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS3: admitted pending receipts and deletion receipt")
        val cases = Vector(resultCase(OutcomeKind.Updated, ready = false, reason = PendingReason.ViewNotCurrent),
          resultCase(OutcomeKind.Unchanged, ready = false, reason = PendingReason.ProjectionUnavailable), resultCase(OutcomeKind.Deleted))
        cases.foreach { fixture =>
          Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 RS3: one admitted committed receipt and its pending or deleted projection")
          When("the pure result codec round trips it")
          val encoded = success(encodeResultC(fixture.result))
          val actual = success(_decode(fixture, encoded))
          Then("outcome stays committed retryCommand remains false and deletion has only reload-list")
          _equivalent(actual, fixture.result)
          actual.reloadRequired shouldBe true
          if (fixture.result.receipt.kind == OutcomeKind.Deleted) {
            at(wire(encoded), Vector("projection")) shouldBe Json.obj("type" -> Json.fromString("reload-list"))
            actual.projection shouldBe None
          }
        }
      }
      "reject wrong outcome projection pairing lag foreign definition identity and nested success shapes" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS3: complete Ready and Deleted envelopes with supplied observation")
        val ready = resultCase(OutcomeKind.Updated)
        val root = wire(success(encodeResultC(ready.result)))
        val inner = Vector("projection", "display")
        val candidates = duplicates(root) ++ shapes(root) ++ Vector(
          set(root, Vector("projection"), Json.obj("type" -> Json.fromString("reload-list"))).noSpaces,
          set(root, Vector("projection"), Json.obj("type" -> Json.fromString("pending"), "reason" -> Json.fromString("foreign"))).noSpaces,
          set(root, inner ++ Vector("model", "resource", "revision"), Json.fromString("7")).noSpaces,
          set(root, inner ++ Vector("model", "resource", "entityId"), Json.fromString(resource(2).entityId.value)).noSpaces,
          set(root, inner ++ Vector("definition", "revision"), Json.fromString("foreign")).noSpaces,
          set(root, inner ++ Vector("provenance", "revision"), Json.fromString("foreign")).noSpaces,
          set(root, inner, Json.Null).noSpaces,
          set(root, Vector("projection", "type"), Json.fromString("foreign")).noSpaces)
        When("the original request result boundary decodes each malformed correspondence or success envelope")
        val rejected = candidates.map(_decode(ready, _))
        Then("none of the unusable Ready objects or invalid projections is fabricated into a Result")
        rejected.foreach(_.isSuccess shouldBe false)
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 RS3: Deleted with a forged Ready or Pending instead of exact reload-list")
        val deleted = resultCase(OutcomeKind.Deleted)
        val deletion = wire(success(encodeResultC(deleted.result)))
        val projections = Vector(at(root, Vector("projection")), Json.obj("type" -> Json.fromString("pending"),
          "reason" -> Json.fromString("view-not-current")), Json.obj("type" -> Json.fromString("reload-list"), "display" -> Json.Null))
        When("Deleted is decoded with each nondeleted or extended projection")
        val failures = projections.map(projection => _decode(deleted, set(deletion, Vector("projection"), projection).noSpaces))
        Then("Deleted admits no display and no other projection member")
        failures.foreach(_.isSuccess shouldBe false)
      }
    }
    "failure separation RS4" which {
      "retain structured null failure and indeterminate outcomes with no writer callback or fake result" in {
        Given("docs/spec/display-mutation.md R2,R3,R4,R5 E11 R4 R5 RS4: pure fixtures plus a canonical indeterminate Consequence without a receipt")
        val trace = new Trace
        val binding = success(DisplayMutationContractFixture.bindingC(Some(trace), positive = true))
        val fixture = resultCase(OutcomeKind.Updated)
        val encoded = success(encodeResultC(fixture.result))
        val indeterminate = indeterminateC[Result]()
        val before = conclusion(indeterminate)
        When("result boundary receives null parameters and ordinary failure envelopes rather than admitted success JSON")
        val failures = Vector(decodeResultC(null, definitions, fixture.request, fixture.observedRevision, encoded),
          decodeResultC(binding.selection, null, fixture.request, fixture.observedRevision, encoded),
          decodeResultC(binding.selection, definitions, null, fixture.observedRevision, encoded),
          decodeResultC(binding.selection, definitions, fixture.request, null, encoded),
          decodeResultC(binding.selection, definitions, fixture.request, Some(null), encoded), _decode(fixture, null),
          _decode(fixture, "{\"error\":\"indeterminate\"}"), _decode(fixture, "{\"error\":\"forbidden\"}"))
        val encodednull = encodeResultC(null)
        Then("invalid inputs stay structured failures and indeterminate stays the original no-receipt failure")
        failures.foreach(_.isSuccess shouldBe false)
        encodednull.isSuccess shouldBe false
        indeterminate.isSuccess shouldBe false
        conclusion(indeterminate) shouldBe before
        before.observation.taxonomy shouldBe Taxonomy.serviceUnavailable
        before.observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
        trace.calls shouldBe Vector.empty
      }
    }
  }
}
