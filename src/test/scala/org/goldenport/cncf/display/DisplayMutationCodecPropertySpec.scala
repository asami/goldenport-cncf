package org.goldenport.cncf.display

import java.time.Instant
import io.circe.Json
import logicalui.{DisplayMutationCodecFixture, DisplayMutationContractFixture}
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationCodecPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationResult.*
  import DisplayMutationCodec.*
  import DisplayMutationCodecFixture.*
  import DisplayMutationContractFixture.{success, resource, definitions, title, description, stage, Trace}
  import GeneratedLogicalUiAbi.ValueKind
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _text = for {
    size <- Gen.choose(0, 32)
    units <- Gen.listOfN(size, Gen.choose(0, 65535).map(_.toChar))
  } yield units.mkString
  private val _decimal = for {
    coefficient <- Gen.choose(Long.MinValue, Long.MaxValue)
    scale <- Gen.frequency(8 -> Gen.choose(-20, 40), 2 -> Gen.oneOf(Int.MinValue, Int.MaxValue))
  } yield BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(coefficient), scale))
  private val _instant = for {
    seconds <- Gen.choose(-100000000000L, 100000000000L)
    nanos <- Gen.choose(0, 999999999)
  } yield Instant.ofEpochSecond(seconds, nanos.toLong)
  private val _order = Gen.oneOf(ValueKind.values.toVector.permutations.toVector)
  private val _revision = Gen.frequency(4 -> Gen.oneOf(1L, 2L, Long.MaxValue - 1, Long.MaxValue),
    6 -> Gen.choose(1L, Long.MaxValue))
  private def _verify(result: Test.Result): Unit = {
    withClue(result.toString) {
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }
  private def _values(actual: Vector[FieldInput], expected: Vector[FieldInput]): Unit = {
    actual.map(_.location) shouldBe expected.map(_.location)
    actual.zip(expected).foreach { case (left, right) => (left.value, right.value) match {
      case (DisplayValue.Text(value), DisplayValue.Text(original)) => value.toVector shouldBe original.toVector
      case (DisplayValue.Number(value), DisplayValue.Number(original)) =>
        value.bigDecimal.unscaledValue shouldBe original.bigDecimal.unscaledValue
        value.bigDecimal.scale shouldBe original.bigDecimal.scale
      case (value, original) => value shouldBe original
    } }
  }
  "E12 Generated mutation wire" should afterWord("in spec:display-mutation, example:E12, rules:R1,R2,R3,R4,R5, phase:96") {
    "native request fidelity BP1" which {
      "retain arbitrary UTF16 exact decimals booleans nanos Missing opaque IDs and caller permutations" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 R5 BP1: total bounded UTF16 coefficient scale Instant opaque ID and permutation generators without filtering")
        val generator = for {
          text <- _text
          number <- _decimal
          flag <- Gen.oneOf(true, false)
          instant <- _instant
          index <- Gen.choose(1, 1000000)
          declared <- _order
          requested <- _order
        } yield (text, number, flag, instant, index, declared, requested)
        val property = Prop.forAll(generator) { case (text, number, flag, instant, index, declared, requested) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 BP1: one generated native definition and ordered Create Update and Delete candidates")
          val fixture = nativeCase(text, number, flag, instant, declared, index)
          val fields = requested.map(kind => fixture.request.fields.find(_.value.kind == kind).get)
          val id = resource(index).entityId
          val create = Request.Create(fixture.binding.selection, fields)
          val update = Request.Update(fixture.binding.selection, id, fields)
          val delete = Request.Delete(fixture.binding.selection, id)
          When("all three generated typed operations round trip with an external operation")
          val actualcreate = success(decodeRequestC(fixture.binding.selection, Operation.Create, success(encodeRequestC(create))))
          val actualupdate = success(decodeRequestC(fixture.binding.selection, Operation.Update, success(encodeRequestC(update))))
          val actualdelete = success(decodeRequestC(fixture.binding.selection, Operation.Delete, success(encodeRequestC(delete))))
          Then("exact coefficient scale code units native branches identity selection and caller order survive")
          actualcreate shouldBe create
          actualupdate shouldBe update
          actualdelete shouldBe delete
          actualcreate match { case Request.Create(_, values) => _values(values, fields); case _ => fail("expected Create") }
          actualupdate match { case Request.Update(_, identity, values) => identity shouldBe id; _values(values, fields); case _ => fail("expected Update") }
          true
        }
        When("ScalaCheck interprets the native request property with minimum one hundred successes")
        val result = Test.check(_parameters, property)
        Then("the total property succeeds at least one hundred times with no discarded cases")
        _verify(result)
      }
    }
    "observable descriptor fidelity BP2" which {
      "retain generated availability bound readonly veto kind order and inert metadata against the supplied Binding" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 R5 BP2: total availability kind-order and bound readonly generators")
        val generator = for {
          states <- Gen.listOfN(5, Gen.oneOf(AvailabilityState.values.toVector))
          titleflag <- Gen.oneOf(true, false)
          descriptionflag <- Gen.oneOf(true, false)
          intrinsic <- Gen.oneOf(true, false)
          order <- _order
        } yield (states, titleflag, descriptionflag, intrinsic, order)
        val property = Prop.forAll(generator) { case (states, titleflag, descriptionflag, intrinsic, order) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 BP2: one admitted metadata Binding and readonly membership with possible intrinsic redundancy")
          val trace = new Trace
          val binding = metadataBinding(trace, order)
          val readonly = Vector(title -> titleflag, description -> descriptionflag, stage -> intrinsic).collect {
            case (location, true) => location
          }.toSet
          val availability = Availability(states(0), states(1), states(2), states(3), states(4), readonly)
          val descriptor = success(binding.descriptorC(availability))
          When("generated descriptor metadata round trips through its exact supplied Binding")
          val encoded = success(encodeDescriptorC(binding, descriptor))
          val actual = success(decodeDescriptorC(binding, encoded))
          Then("observable fields capabilities availability states readonly veto and inert order survive without callbacks")
          actual.fields shouldBe descriptor.fields
          actual.capabilities shouldBe binding.capabilities
          actual.availability shouldBe availability.copy(readonlyFields = readonly - stage)
          actual.fields.head.acceptedKinds shouldBe order
          actual.fields.head.web.values shouldBe descriptor.fields.head.web.values
          success(encodeDescriptorC(binding, actual)) shouldBe encoded
          trace.calls shouldBe Vector.empty
          true
        }
        When("ScalaCheck interprets the supplied Binding property with minimum one hundred successes")
        val result = Test.check(_parameters, property)
        Then("the total descriptor property succeeds at least one hundred times with zero discards")
        _verify(result)
      }
    }
    "supplied receipt projection fidelity BP3" which {
      "preserve generated created updated unchanged deleted ready pending and safe revision endpoints" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 R4 R5 BP3: total outcome positive revision ready pending and reason generators with direct safe arithmetic")
        val generator = for {
          kind <- Gen.oneOf(OutcomeKind.values.toVector)
          revision <- _revision
          index <- Gen.choose(1, 1000000)
          ready <- Gen.oneOf(true, false)
          reason <- Gen.oneOf(PendingReason.values.toVector)
        } yield (kind, revision, index, ready, reason)
        val property = Prop.forAll(generator) { case (kind, revision, index, ready, reason) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 BP3: one authoritative supplied receipt; Updated directly caps observation at MAXIMUM minus one")
          val safe = if (kind == OutcomeKind.Updated && revision == Long.MaxValue) revision - 1 else revision
          val fixture = resultCase(kind, index, safe, ready, reason)
          When("the typed result round trips using its original request and observation")
          val encoded = success(encodeResultC(fixture.result))
          val actual = success(decodeResultC(fixture.result.receipt.selection, definitions, fixture.request, fixture.observedRevision, encoded))
          Then("canonical source selection outcome identity revision projection and no replay survive")
          actual.receipt.selection.definition shouldBe fixture.result.receipt.selection.definition
          actual.receipt.selection.source.componentId.name shouldBe fixture.result.receipt.selection.source.componentId.name
          actual.receipt.selection.source.resourceType shouldBe fixture.result.receipt.selection.source.resourceType
          actual.receipt.kind shouldBe kind
          actual.receipt.resource shouldBe fixture.result.receipt.resource
          actual.retryCommand shouldBe false
          actual.reloadRequired shouldBe fixture.result.reloadRequired
          success(encodeResultC(actual)) shouldBe encoded
          kind match {
            case OutcomeKind.Created => actual.receipt.resource.revision.value shouldBe 1L
            case OutcomeKind.Updated => actual.receipt.resource.revision shouldBe success(fixture.observedRevision.get.nextC)
            case _ => actual.receipt.resource.revision shouldBe fixture.observedRevision.get
          }
          true
        }
        When("ScalaCheck interprets the canonical result property with minimum one hundred successes")
        val result = Test.check(_parameters, property)
        Then("the total receipt projection property succeeds at least one hundred times with zero discards")
        _verify(result)
      }
    }
    "independent wire tampering BP4" which {
      "reject generated source definition scalar scoped-field and member edits including both Ready source boundaries" in {
        Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 R4 R5 BP4: total text opaque identity and member-tampering generators without rejection filters")
        val generator = for { text <- _text; index <- Gen.choose(1, 1000000); choice <- Gen.choose(0, 8) } yield (text, index, choice)
        val property = Prop.forAll(generator) { case (text, index, choice) =>
          Given("docs/spec/display-mutation.md R1,R2,R3,R4,R5 E12 BP4: one all-kind Create and source-corresponding Ready with identical inner and outer identity revision")
          val fixture = nativeCase(text, BigDecimal("1.00"), true, Instant.EPOCH, index = index)
          val receipt = success(receiptC(fixture.binding.selection, fixture.request, OutcomeKind.Created, resource(index, 1), None))
          val result = success(createC(receipt, Some(ProjectionState.Ready(ReloadedDisplay(fixture.binding.selection.source, fixture.display)))))
          val requestwire = success(encodeRequestC(fixture.request))
          val resultwire = success(encodeResultC(result))
          val requestjson = wire(requestwire)
          val resultjson = wire(resultwire)
          val foreign = Json.fromString("org.goldenport.fixture.Foreign" + index)
          val requestcandidate = choice match {
            case 0 => set(requestjson, Vector("definition", "mutationRevision"), foreign).noSpaces
            case 1 => requestwire.replace("\"version\":1", "\"version\":1e0")
            case 2 => set(requestjson, Vector("fields", "1", "value", "number"), Json.fromString("1e0")).noSpaces
            case 3 => set(requestjson, Vector("fields"), Json.fromValues(Vector(at(requestjson, Vector("fields", "0")),
              at(requestjson, Vector("fields", "0"))))).noSpaces
            case _ => set(requestjson, Vector("attacker-secret"), foreign).noSpaces
          }
          val resultcandidate = choice match {
            case 5 => set(resultjson, Vector("componentId"), foreign).noSpaces
            case 6 => set(resultjson, Vector("resourceType"), foreign).noSpaces
            case 7 => set(resultjson, Vector("projection", "display", "componentId"), foreign).noSpaces
            case 8 => set(resultjson, Vector("projection", "display", "resourceType"), foreign).noSpaces
            case _ => set(resultjson, Vector("definition", "revision"), foreign).noSpaces
          }
          When("the generated request and independent outer or inner Ready tampering are decoded")
          val requestresult = decodeRequestC(fixture.binding.selection, Operation.Create, requestcandidate)
          val resultresult = decodeResultC(fixture.binding.selection, fixture.binding.definitions, fixture.request, None, resultcandidate)
          Then("all changed boundaries reject and nested-source edits preserve the original read codec failure")
          requestresult.isSuccess shouldBe false
          resultresult.isSuccess shouldBe false
          problem(requestresult).toString should not include "attacker-secret"
          if (choice >= 7) conclusion(resultresult).observation.cause.getException.collect {
            case carrier: DisplayProtocol.CodecException => carrier.diagnostic.code
          } shouldBe Some("DSP96_WIRE_SOURCE_MISMATCH")
          true
        }
        When("ScalaCheck interprets the tampering property with minimum one hundred successes")
        val result = Test.check(_parameters, property)
        Then("the total independent boundary property succeeds at least one hundred times with zero discards")
        _verify(result)
      }
    }
  }
}
