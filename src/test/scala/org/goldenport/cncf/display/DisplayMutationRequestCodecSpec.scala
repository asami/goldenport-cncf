package org.goldenport.cncf.display

import java.time.Instant
import io.circe.Json
import logicalui.{DisplayMutationCodecFixture, DisplayMutationContractFixture}
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationRequestCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationCodec.*
  import DisplayMutationCodecFixture.*
  import DisplayMutationContractFixture.{success, bindingC, title, description, resource, Trace}
  private def _decode(request: Request, operation: Operation, json: String): Consequence[Request] =
    decodeRequestC(DisplayMutation._request_selection(request), operation, json)
  private def _redacted(result: Consequence[Request]): Unit = {
    result.isSuccess shouldBe false
    conclusion(result).observation.taxonomy shouldBe Taxonomy.argumentInvalid
    problem(result).reason shouldBe "invalid Display mutation input"
    problem(result).path should not include "attacker-secret"
    problem(result).toString should not include "attacker-secret"
  }
  "E9 Request wire" should afterWord("in spec:display-mutation, example:E9, rules:R1,R2,R3,R5, phase:96") {
    "typed operation admission RQ1" which {
      "round trip empty and nonempty Create ordered Update and opaque Delete without business revision" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 RQ1: one admitted selection and ordered caller input for each externally selected operation")
        val binding = success(bindingC())
        val fields = Vector(FieldInput(description, DisplayValue.Text("second")), FieldInput(title, DisplayValue.Text("first")))
        val cases = Vector[(Request, Operation)](Request.Create(binding.selection, Vector.empty) -> Operation.Create,
          Request.Create(binding.selection, fields) -> Operation.Create,
          Request.Update(binding.selection, resource().entityId, fields) -> Operation.Update,
          Request.Delete(binding.selection, resource().entityId) -> Operation.Delete)
        cases.foreach { case (request, operation) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ1: a typed request and its external route operation")
          When("the standalone codec encodes and decodes the request")
          val json = success(encodeRequestC(request))
          val actual = success(_decode(request, operation, json))
          Then("selection opaque identity native fields and caller order survive exactly")
          actual shouldBe request
          val root = wire(json).asObject.get
          Vector("componentId", "operation", "observedRevision", "expectedRevision", "revision", "target", "path", "converterId")
            .foreach(name => root.contains(name) shouldBe false)
        }
      }
    }
    "native scalars RQ2" which {
      "retain every UTF16 code unit exact decimal coefficient and full scale boundaries timestamp nanos and Missing" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 RQ2: all five native values including text escapes isolated surrogates and decimal scale endpoints")
        val text = "\u0000\b\f\n\r\t\"\\日本Ωe\u0301\ud83d\ude00\ud800x\udfff"
        val decimals = Vector(("12345000", 5), ("-7", -3), ("1", Int.MinValue), ("1", Int.MaxValue),
          ("0", Int.MinValue), ("0", Int.MaxValue), ("0", 7))
        decimals.foreach { case (coefficient, scale) => Vector(Instant.MIN, Instant.MAX, Instant.ofEpochSecond(-1, 123456789)).foreach { instant =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ2: an exact coefficient scale and nanosecond Instant candidate")
          val number = BigDecimal(new java.math.BigDecimal(new java.math.BigInteger(coefficient), scale))
          val fixture = nativeCase(text, number, true, instant)
          When("Create retains native branch payloads through the pure codec")
          val encoded = success(encodeRequestC(fixture.request))
          val decoded = success(decodeRequestC(fixture.binding.selection, Operation.Create, encoded))
          Then("all representations survive without numeric equivalence or charset normalization")
          decoded shouldBe fixture.request
          val fields = decoded match { case Request.Create(_, values) => values; case _ => fail("expected Create") }
          fields.head.value match { case DisplayValue.Text(value) => value.toVector shouldBe text.toVector; case _ => fail("expected Text") }
          fields(1).value match {
            case DisplayValue.Number(value) =>
              value.bigDecimal.unscaledValue shouldBe number.bigDecimal.unscaledValue
              value.bigDecimal.scale shouldBe scale
            case _ => fail("expected Number")
          }
          fields(2).value shouldBe DisplayValue.Boolean(true)
          fields(3).value shouldBe DisplayValue.Timestamp(instant)
          fields(4).value shouldBe DisplayValue.Missing
          encoded should include ("\\ud800")
          encoded should include ("\\udfff")
        } }
      }
    }
    "strict shapes and canonical spelling RQ3" which {
      "reject duplicate members at every applicable object and missing unknown null or wrong primitive members" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 RQ3: Create Update Delete and all five native value objects")
        val native = nativeCase("attacker-secret", BigDecimal("1.00"), true, Instant.EPOCH)
        val cases = Vector[(Request, Operation)](native.request -> Operation.Create,
          Request.Update(native.binding.selection, resource().entityId, native.request.fields) -> Operation.Update,
          Request.Delete(native.binding.selection, resource().entityId) -> Operation.Delete)
        cases.foreach { case (request, operation) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ3: every nested object and declared member of a canonical request")
          val root = wire(success(encodeRequestC(request)))
          val candidates = duplicates(root) ++ shapes(root) ++ Vector("{", "[]", "null", "true")
          candidates.foreach { candidate =>
            Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ3: a complete malformed wire candidate")
            When("strict request decoding interprets the supplied shape")
            val result = _decode(request, operation, candidate)
            Then("a structured fixed diagnostic rejects the whole object with no leaked key or scalar")
            _redacted(result)
          }
        }
      }
      "reject noncanonical version identity decimal timestamp and closed value tags" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 RQ3: valid all-kind Create and canonical Delete identity")
        val fixture = nativeCase("text", BigDecimal("1.00"), false, Instant.EPOCH)
        val root = wire(success(encodeRequestC(fixture.request)))
        val numbers = Vector("01", "+1", "1e0", "1E0", "1.0E+0", "-0", "NaN", "1E+2147483649")
        val instants = Vector("1970-01-01T00:00:00+00:00", "1970-01-01T00:00:00.000Z", "not-an-instant")
        val candidates = Vector("0", "2", "1.0", "1e0", "true", "\"1\"").map(value =>
          success(encodeRequestC(fixture.request)).replace("\"version\":1", "\"version\":" + value)) ++
          numbers.map(value => set(root, Vector("fields", "1", "value", "number"), Json.fromString(value)).noSpaces) ++
          instants.map(value => set(root, Vector("fields", "3", "value", "instant"), Json.fromString(value)).noSpaces) ++
          Vector(set(root, Vector("schema"), Json.fromString("foreign")).noSpaces,
            set(root, Vector("fields", "0", "value", "type"), Json.fromString("foreign")).noSpaces,
            set(root, Vector("fields", "1", "value", "number"), Json.fromInt(1)).noSpaces,
            set(root, Vector("fields", "2", "value", "boolean"), Json.fromString("false")).noSpaces)
        candidates.foreach { candidate =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ3: one noncanonical scalar or unsupported descriptor")
          When("the native Create boundary decodes it")
          val result = decodeRequestC(fixture.binding.selection, Operation.Create, candidate)
          Then("canonical native lexical forms and closed tags reject coercion")
          result.isSuccess shouldBe false
        }
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ3: opaque EntityId candidates which do not equal canonical factory output")
        val request = Request.Delete(fixture.binding.selection, resource().entityId)
        val deletion = wire(success(encodeRequestC(request)))
        val identities = Vector("", " " + resource().entityId.value, resource().entityId.value + " ", "attacker-secret")
        When("each identity is admitted through EntityId.parse with exact encoded equality")
        val rejected = identities.map(value => _decode(request, Operation.Delete,
          set(deletion, Vector("resourceId"), Json.fromString(value)).noSpaces))
        Then("noncanonical opaque identities have redacted ID_INVALID diagnostics")
        rejected.foreach(result => problem(result).code shouldBe "DSP96_MUTATION_ID_INVALID")
      }
    }
    "selection and separation RQ4" which {
      "reject foreign selection duplicate locations empty Update operation additions and null public inputs without callbacks" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 RQ4: a traced pure binding and one ordered Update")
        val trace = new Trace
        val binding = success(bindingC(Some(trace)))
        val fields = Vector(FieldInput(title, DisplayValue.Text("value")))
        val request = Request.Update(binding.selection, resource().entityId, fields)
        val encoded = success(encodeRequestC(request))
        val root = wire(encoded)
        val paths = Vector(Vector("resourceType"), Vector("definition", "screenId"), Vector("definition", "revision"),
          Vector("definition", "mutationRevision"), Vector("provenance", "producerId"), Vector("provenance", "sourceId"),
          Vector("provenance", "revision"))
        val candidates = paths.map(path => set(root, path, Json.fromString("attacker-secret")).noSpaces) ++
          Vector(set(root, Vector("changes"), Json.arr()).noSpaces,
            set(root, Vector("changes"), Json.fromValues(Vector(at(root, Vector("changes", "0")), at(root, Vector("changes", "0"))))).noSpaces) ++
          Vector("componentId", "operation", "observedRevision", "expectedRevision", "revision", "fields", "target", "path", "converterId")
            .map(name => set(root, Vector(name), Json.fromString("attacker-secret")).noSpaces)
        When("selection and operation-specific member admission interpret every candidate and null API input")
        val results = candidates.map(candidate => _decode(request, Operation.Update, candidate)) ++ Vector(
          decodeRequestC(null, Operation.Update, encoded), decodeRequestC(binding.selection, null, encoded),
          decodeRequestC(binding.selection, Operation.Update, null), encodeRequestC(null).map(_ => request),
          encodeRequestC(Request.Create(null, Vector.empty)).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, null)).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, Vector(null))).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, Vector(FieldInput(null, DisplayValue.Text("value"))))).map(_ => request),
          encodeRequestC(Request.Update(binding.selection, resource().entityId, Vector.empty)).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, Vector(FieldInput(title, DisplayValue.Text(null))))).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, Vector(FieldInput(title, DisplayValue.Number(null))))).map(_ => request),
          encodeRequestC(Request.Create(binding.selection, Vector(FieldInput(title, DisplayValue.Timestamp(null))))).map(_ => request),
          _decode(request, Operation.Create, encoded), _decode(request, Operation.Delete, encoded))
        Then("all invalid inputs reject and pure codecs invoke no authorization converter or domain callback")
        results.foreach(_redacted)
        trace.calls shouldBe Vector.empty
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 RQ4: an unregistered but well-shaped scoped field candidate")
        val unbound = Request.Create(binding.selection, Vector(FieldInput(FieldLocator("main", "unregistered"), DisplayValue.Missing)))
        When("the request shape codec round trips the field without binding preparation")
        val decoded = success(decodeRequestC(binding.selection, Operation.Create, success(encodeRequestC(unbound))))
        Then("shape admission grants no registration while retaining the inert typed candidate")
        decoded shouldBe unbound
        trace.calls shouldBe Vector.empty
      }
      "expose only the typed public facade and deny private admission bypasses TYPE" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R5 E9 R5 TYPE: lexical external logicalui compile-time probes")
        When("the external probes typecheck the public API and rejected private constructors")
        verifyTypeBoundary()
        Then("protocol constants identify the three standalone schemas with version one")
        DisplayMutationProtocol.requestSchema shouldBe "cncf.display-mutation.v1"
        DisplayMutationProtocol.descriptorSchema shouldBe "cncf.display-mutation-descriptor.v1"
        DisplayMutationProtocol.resultSchema shouldBe "cncf.display-mutation-result.v1"
        DisplayMutationProtocol.version shouldBe 1
      }
    }
  }
}
