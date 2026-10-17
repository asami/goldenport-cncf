package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import domain.logicalui.DomainLogicalUiDefinitions
import io.circe.Json
import logicalui.{DisplayConsumerHandoffFixture, DisplayMutationCodecFixture, DisplayMutationContractFixture,
  TypedDisplayProjectionFixture}
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Consumer inputs consolidate existing contracts; supplied receipts are not commit evidence. */
final class DisplayConsumerHandoffSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayConsumerHandoffFixture.*
  import DisplayMutation.*
  import DisplayMutationResult.*
  import DisplayMutationContractFixture.success
  import DisplayConsumerHandoffSpec.*

  private def _resource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path)).getOrElse(fail("missing consumer resource " + path))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8) finally stream.close()
  }
  private def _golden(name: String): String = _resource("/display/consumer/v1/" + name)
  private def _context(value: Option[PresentationContext]): Unit = {
    value.isDefined shouldBe true
    val actual = value.getOrElse(fail("expected explicit context"))
    actual.locale shouldBe Some("en-US")
    actual.variant shouldBe Some("server-variant")
    actual.flow.map(flow => (flow.displayInstance, flow.correlation, flow.expiresAt)) shouldBe
      Some(("consumer-display-1", "consumer-correlation-1", Instant.parse("2026-10-17T00:30:00.123456789Z")))
    actual.flow.map(_.expiresAt.getNano) shouldBe Some(123456789)
  }
  private def _values(actual: Vector[FieldInput], expected: Vector[FieldInput]): Unit = {
    actual.map(_.location) shouldBe expected.map(_.location)
    actual.zip(expected).foreach { case (left, right) => (left.value, right.value) match {
      case (DisplayValue.Text(text), DisplayValue.Text(original)) => text.toVector shouldBe original.toVector
      case (DisplayValue.Number(number), DisplayValue.Number(original)) =>
        number.bigDecimal.unscaledValue shouldBe original.bigDecimal.unscaledValue
        number.bigDecimal.scale shouldBe original.bigDecimal.scale
      case (value, original) => value shouldBe original
    } }
  }

  "CH1 admitted read inputs" should afterWord("in spec:display-consumer-handoff, example:CH1, rules:R1,R3,R7, phase:96") {
    "actual Cozy List and Detail provenance" which {
      "restore unchanged golden bytes against actual generated definitions" in {
        Given("docs/spec/display-consumer-handoff.md CH1 R1,R3,R7: actual Cozy definitions, trusted generated-consumer Source and original List/Detail inputs")
        val admitted = success(GeneratedLogicalUiAbi.admitC(DomainLogicalUiDefinitions.definitions))
        val source = DisplayProtocolSpecFixture.source
        val listwire = _resource("/display/protocol/v1/ess2-01-list.json")
        val detailwire = _resource("/display/protocol/v1/ess2-01-detail.json")
        When("the original read inputs are decoded with the trusted source and admission")
        val list = success(DisplayProtocolCodec.decodeC(source, admitted, listwire))
        val detail = success(DisplayProtocolCodec.decodeC(source, admitted, detailwire))
        Then("screens, exact resource identities and revisions, generated provenance and canonical bytes survive")
        source.componentId.name shouldBe "org.goldenport.cncf.test.GeneratedLogicalUiConsumer"
        source.resourceType shouldBe "collecting-reference"
        list.model.screenId shouldBe "collecting-resources"
        detail.model.screenId shouldBe "capture-detail"
        list.definition.provenance shouldBe GeneratedLogicalUiAbi.SourceProvenance(
          "cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1")
        detail.definition.provenance shouldBe list.definition.provenance
        list.definition.definitionRevision shouldBe "ess-definition-1"
        detail.definition.definitionRevision shouldBe "ess-definition-1"
        list.model match {
          case model: DisplayModel.ListModel =>
            model.items.map(_.resource) shouldBe Vector(
              success(TypedDisplayProjectionFixture.resourceC(1, 7)),
              success(TypedDisplayProjectionFixture.resourceC(2, 19)))
            model.detailTarget.screenId shouldBe "capture-detail"
          case _ => fail("expected List")
        }
        detail.model match {
          case model: DisplayModel.DetailModel => model.resource shouldBe success(TypedDisplayProjectionFixture.resourceC(1, 7))
          case _ => fail("expected Detail")
        }
        success(DisplayProtocolCodec.encodeC(source, list)) + "\n" shouldBe listwire
        success(DisplayProtocolCodec.encodeC(source, detail)) + "\n" shouldBe detailwire
      }
    }
  }

  "CH2 admitted mutation inputs" should afterWord("in spec:display-consumer-handoff, example:CH2, rules:R2,R3,R4, phase:96") {
    "external operation and trusted selection" which {
      "preserve ordered Create Update and Delete candidates" in {
        Given("docs/spec/display-consumer-handoff.md CH2 R2,R3,R4: positive-contract-1 selection and three independently stored request inputs")
        val examples = Vector(("mutation-create-request.json", Operation.Create, createRequest),
          ("mutation-update-request.json", Operation.Update, updateRequest),
          ("mutation-delete-request.json", Operation.Delete, deleteRequest))
        val inputs = examples.map { case (name, operation, expected) => (name, operation, expected, _golden(name)) }
        When("each raw input is decoded with its route-selected operation and exact trusted selection")
        val decoded = inputs.map { case (name, operation, expected, raw) =>
          (expected, raw, success(DisplayMutationCodec.decodeRequestC(binding.selection, operation, raw)))
        }
        Then("typed requests, opaque IDs, explicit selection and caller field order are retained exactly")
        decoded.foreach { case (expected, raw, actual) =>
          actual shouldBe expected
          success(DisplayMutationCodec.encodeRequestC(actual)) + "\n" shouldBe raw
        }
        decoded.head._3 match {
          case Request.Create(selection, fields) =>
            selection.definition shouldBe binding.selection.definition
            fields.map(_.location.fieldId) shouldBe Vector("title", "description")
            fields.map(_.value) shouldBe Vector(DisplayValue.Text("Created title 日本"), DisplayValue.Text("Created description"))
          case _ => fail("expected Create")
        }
        decoded(1)._3 match {
          case Request.Update(_, identity, fields) =>
            identity shouldBe updateRequest.resourceId
            fields.map(_.location.fieldId) shouldBe Vector("title", "description")
            fields.map(_.value) shouldBe Vector(DisplayValue.Text("Updated title 日本"), DisplayValue.Text("Updated description"))
          case _ => fail("expected Update")
        }
      }
    }
  }

  "CH3 editor metadata" should afterWord("in spec:display-consumer-handoff, example:CH3, rules:R3,R4, phase:96") {
    "explicit writable registration" which {
      "retain title and description bindings and intrinsic stage readonly without callbacks" in {
        Given("docs/spec/display-consumer-handoff.md CH3 R3,R4: the same positive binding with traced converter/domain callbacks and the stored descriptor")
        val trace = new DisplayMutationContractFixture.Trace
        val selected = success(DisplayMutationContractFixture.bindingC(Some(trace), positive = true))
        val expected = success(selected.descriptorC(Availability.enabled))
        val raw = _golden("mutation-descriptor.json")
        When("the descriptor input is restored against its exact registered binding")
        val actual = success(DisplayMutationCodec.decodeDescriptorC(selected, raw))
        val encoded = success(DisplayMutationCodec.encodeDescriptorC(selected, actual))
        Then("declaration order, editable bindings and readonly stage survive without conversion or domain interpretation")
        actual.fields shouldBe expected.fields
        actual.capabilities shouldBe selected.capabilities
        actual.availability shouldBe Availability.enabled
        actual.fields.map(_.location.fieldId) shouldBe Vector("title", "stage", "description")
        actual.fields.map(_.editability) shouldBe Vector(FieldEditability.Editable, FieldEditability.ReadOnly, FieldEditability.Editable)
        actual.fields.map(_.bindingId) shouldBe Vector(Some("write.title"), None, Some("write.description"))
        actual.fields(1).web.readonly shouldBe true
        encoded + "\n" shouldBe raw
        trace.calls shouldBe empty
      }
    }
  }

  "CH4 supplied outcome inputs" should afterWord("in spec:display-consumer-handoff, example:CH4, rules:R1,R3,R4,R5, phase:96") {
    "receipt and corresponding projection" which {
      "preserve all five outcomes and the contextual Ready without authorizing replay" in {
        Given("docs/spec/display-consumer-handoff.md CH4 R1,R3,R4,R5: coherent supplied receipts, original requests/observations, stored outcomes and fixed inert context")
        val inputs = mutationExamples.map(example => (example, _golden(example.fileName)))
        val expected = Vector((OutcomeKind.Created, 1L), (OutcomeKind.Updated, 8L),
          (OutcomeKind.Unchanged, 7L), (OutcomeKind.Deleted, 7L), (OutcomeKind.Updated, 8L), (OutcomeKind.Updated, 8L))
        When("stored results are restored using their original request and observed revision")
        val decoded = inputs.map { case (example, raw) =>
          success(DisplayMutationCodec.decodeResultC(binding.selection, binding.definitions, example.request, example.observedRevision, raw))
        }
        Then("outcome, identity, exact revision, projection values and context correspondence survive with retryCommand false")
        decoded.zip(inputs).zip(expected).foreach { case ((actual, (example, raw)), (kind, revision)) =>
          actual.receipt.kind shouldBe kind
          actual.receipt.resource shouldBe success(TypedDisplayProjectionFixture.resourceC(1, revision))
          actual.receipt.selection.definition shouldBe binding.selection.definition
          actual.receipt.selection.source.componentId.name shouldBe "org.goldenport.fixture.Mutation"
          actual.receipt.selection.source.resourceType shouldBe "Capture"
          actual.retryCommand shouldBe false
          actual.reloadRequired shouldBe (example.fileName == "mutation-deleted.json" || example.fileName == "mutation-updated-pending.json")
          success(DisplayMutationCodec.encodeResultC(actual)) + "\n" shouldBe raw
          if (example.fileName == "mutation-updated-ready-context.json") _context(actual.presentationContext)
          else actual.presentationContext shouldBe None
          actual.projection match {
            case Some(ProjectionState.Ready(snapshot)) =>
              snapshot.source.componentId.name shouldBe "org.goldenport.fixture.Mutation"
              snapshot.source.resourceType shouldBe "Capture"
              snapshot.display.definition shouldBe binding.definitions.definitions(1)
              snapshot.display.definition.provenance shouldBe GeneratedLogicalUiAbi.SourceProvenance(
                "cncf.expected-generated-fixture", "ess2-01", "fixture-1")
              val model = snapshot.display.model match {
                case value: DisplayModel.DetailModel => value
                case _ => fail("expected corresponding Detail")
              }
              model.resource shouldBe actual.receipt.resource
              model.sections.head.fields.map(_.id) shouldBe Vector("title", "stage", "description")
              val prefix = if (kind == OutcomeKind.Created) "Created" else "Updated"
              model.sections.head.fields.map(_.value) shouldBe Vector(DisplayValue.Text(prefix + " title 日本"),
                DisplayValue.Text("draft"), DisplayValue.Text(prefix + " description"))
              model.actions.map(_.bindingId) shouldBe Vector("ess.request-review")
              if (example.fileName == "mutation-updated-ready-context.json") _context(snapshot.display.presentationContext)
              else snapshot.display.presentationContext shouldBe None
            case Some(ProjectionState.Pending(reason)) => reason shouldBe PendingReason.ViewNotCurrent
            case None => kind shouldBe OutcomeKind.Deleted
          }
        }
      }
    }
  }

  "CH5 Business metadata input" should afterWord("in spec:display-consumer-handoff, example:CH5, rules:R2,R3,R6,R7, phase:96") {
    "ordinary canonical target description" which {
      "restore the registered Detail action and ordered parameters while target and browser stay idle" in {
        Given("docs/spec/display-consumer-handoff.md CH5 R2,R3,R6,R7: a normally bootstrapped existing Business fixture, its immutable registry and stored descriptor")
        val session = DisplayBusinessOperationFixture.open()
        try {
          val registry = success(DisplayBusinessOperationFixture.registry(session.component))
          val expected = success(registry.describeC(DisplayBusinessOperationFixture.resourceType, "capture-detail"))
          val raw = _golden("business-descriptor.json")
          When("the stored metadata is decoded against that actual registry and encoded through the public codec")
          val actual = success(DisplayBusinessOperationCodec.decodeC(registry, raw))
          val encoded = success(DisplayBusinessOperationCodec.encodeC(registry,
            DisplayBusinessOperationFixture.resourceType, "capture-detail"))
          Then("the canonical target, action and descriptive ordered input survive without any Business or browser execution")
          actual shouldBe expected
          actual.source.componentId.name shouldBe "org.goldenport.cncf.test.BusinessDescriptor"
          actual.source.resourceType shouldBe "collecting-reference"
          actual.actions.map(_.id) shouldBe Vector("request-review")
          actual.actions.head.target.componentId.name shouldBe "org.goldenport.cncf.test.BusinessDescriptor"
          actual.actions.head.target.service shouldBe "business"
          actual.actions.head.target.operation shouldBe "review"
          actual.actions.head.parameters shouldBe DisplayBusinessOperationFixture.expectedParameters
          encoded + "\n" shouldBe raw
          session.target.requests shouldBe 0
          session.target.calls shouldBe 0
          session.target.executions shouldBe 0
          session.component.browser.findCalls shouldBe empty
          session.component.browser.queryCalls shouldBe empty
          session.component.browser.contextFreeCalls shouldBe 0
          session.component.browser.countCalls shouldBe 0
        } finally session.close()
      }
    }
  }

  "CH6 optional context input" should afterWord("in spec:display-consumer-handoff, example:CH6, rules:R1,R3,R5, phase:96") {
    "safe inert context and absence" which {
      "retain exact locale variant flow nanos and old read absence" in {
        Given("docs/spec/display-consumer-handoff.md CH6 R1,R3,R5: fixed standalone context and an unchanged actual Detail without context")
        val raw = _golden("presentation-context.json")
        val plainwire = _resource("/display/protocol/v1/ess2-01-detail.json")
        val admitted = success(GeneratedLogicalUiAbi.admitC(DomainLogicalUiDefinitions.definitions))
        When("the context and plain Display are restored through their existing public codecs")
        val actual = success(PresentationContextCodec.decodeC(raw))
        val plain = success(DisplayProtocolCodec.decodeC(DisplayProtocolSpecFixture.source, admitted, plainwire))
        Then("the supplied context remains exact inert data and absence is preserved without inserting an empty context")
        _context(Some(actual))
        success(PresentationContextCodec.encodeC(actual)) + "\n" shouldBe raw
        plain.presentationContext shouldBe None
        success(DisplayProtocolCodec.encodeC(DisplayProtocolSpecFixture.source, plain)) + "\n" shouldBe plainwire
      }
    }
  }

  "CH7 closed untrusted input" should afterWord("in spec:display-consumer-handoff, example:CH7, rules:R1,R2,R4,R5, phase:96") {
    "independent stored-input tampering" which {
      "reject an incompatible version, body observation and replay command with structured failures" in {
        Given("docs/spec/display-consumer-handoff.md CH7 R1,R2,R4,R5: concrete request and updated-pending result inputs with one independently changed member each")
        val request = DisplayMutationCodecFixture.wire(_golden("mutation-create-request.json"))
        val result = DisplayMutationCodecFixture.wire(_golden("mutation-updated-pending.json"))
        val version = request.mapObject(_.add("version", Json.fromInt(2))).noSpaces
        val observation = request.mapObject(_.add("observedRevision", Json.fromLong(7))).noSpaces
        val replay = result.mapObject(_.add("retryCommand", Json.True)).noSpaces
        val observed = Some(success(TypedDisplayProjectionFixture.resourceC(1, 7)).revision)
        When("the existing strict codecs interpret each tampered consumer candidate")
        val rejected = Vector(
          DisplayMutationCodec.decodeRequestC(binding.selection, Operation.Create, version),
          DisplayMutationCodec.decodeRequestC(binding.selection, Operation.Create, observation),
          DisplayMutationCodec.decodeResultC(binding.selection, binding.definitions, updateRequest, observed, replay))
        Then("all candidates reject through argument taxonomy and typed redacted mutation diagnostics")
        rejected.foreach { value =>
          value.isSuccess shouldBe false
          val conclusion = DisplayMutationCodecFixture.conclusion(value)
          conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
          conclusion.observation.cause.getException.collect {
            case error: MutationException => error.diagnostic.code
          }.getOrElse(fail("expected typed mutation rejection")) should startWith("DSP96_MUTATION_")
        }
      }
    }
  }

  "CHP1 native consumer fidelity" should afterWord("in spec:display-consumer-handoff, example:CHP1, rules:R1,R3,R4,R5, phase:96") {
    "total bounded native inputs and lawful reducing shrinkers" which {
      "retain exact ordered native values and optional context in requests and Displays" in {
        Given("docs/spec/display-consumer-handoff.md CHP1 R1,R3,R4,R5: total bounded UTF16, decimal coefficient/scale, Boolean, seconds/nanos and optional fixed-context generators")
        val property = Prop.forAll(inputGenerator) { sample =>
          Given("docs/spec/display-consumer-handoff.md CHP1 R1,R3,R4,R5: one generated native typed Create and corresponding admitted Display with optional inert context")
          val number = BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(sample.coefficient), sample.scale))
          val instant = Instant.ofEpochSecond(sample.seconds, sample.nanos.toLong)
          val fixture = DisplayMutationCodecFixture.nativeCase(sample.text, number, sample.flag, instant)
          val suppliedcontext = if (sample.contextual) Some(context) else None
          val display = success(Display.withPresentationContextC(fixture.display, suppliedcontext))
          When("the generated native request and Display round trip through the public codecs")
          val requestwire = success(DisplayMutationCodec.encodeRequestC(fixture.request))
          val restoredrequest = success(DisplayMutationCodec.decodeRequestC(fixture.binding.selection, Operation.Create, requestwire))
          val displaywire = success(DisplayProtocolCodec.encodeC(fixture.binding.selection.source, display))
          val restoreddisplay = success(DisplayProtocolCodec.decodeC(fixture.binding.selection.source, fixture.binding.definitions, displaywire))
          Then("field order and all five typed values preserve exact UTF16, unscaled decimal/scale and timestamp nanos with trusted definition/model and context")
          restoredrequest match {
            case Request.Create(selection, fields) =>
              selection.definition shouldBe fixture.binding.selection.definition
              _values(fields, fixture.request.fields)
              fields.map(_.value.kind) shouldBe GeneratedLogicalUiAbi.ValueKind.values.toVector
            case _ => fail("expected native Create")
          }
          (restoreddisplay.definition eq display.definition) shouldBe true
          restoreddisplay.model shouldBe display.model
          val fields = restoreddisplay.model match {
            case model: DisplayModel.DetailModel => model.sections.head.fields
            case _ => fail("expected native Detail")
          }
          _values(fields.map(field => FieldInput(FieldLocator("native", field.id), field.value)), fixture.request.fields)
          if (sample.contextual) _context(restoreddisplay.presentationContext)
          else restoreddisplay.presentationContext shouldBe None
          success(DisplayProtocolCodec.encodeC(fixture.binding.selection.source, restoreddisplay)) shouldBe displaywire
          true
        }
        When("ScalaCheck interprets the property with one worker and at least one hundred successful cases")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        Then("the law succeeds at least one hundred times without implications, filtering or discarded cases")
        withClue(result.toString) {
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
}

/** Instance-independent property data, total generators and strictly reducing shrinkers. */
object DisplayConsumerHandoffSpec {
  final case class NativeInput(text: String, coefficient: Long, scale: Int, flag: Boolean,
    seconds: Long, nanos: Int, contextual: Boolean)

  private val _text = Gen.frequency(
    3 -> Gen.oneOf("", "é", "e\u0301", "日本 Ω", "\u0000", "\ud800", "\udfff", "\ud83d\ude00"),
    7 -> (for {
      size <- Gen.choose(0, 32)
      units <- Gen.listOfN(size, Gen.choose(0, 65535).map(_.toChar))
    } yield units.mkString))
  val inputGenerator: Gen[NativeInput] = for {
    text <- _text
    coefficient <- Gen.choose(Long.MinValue, Long.MaxValue)
    scale <- Gen.choose(-20, 40)
    flag <- Gen.oneOf(false, true)
    seconds <- Gen.choose(-100000000000L, 100000000000L)
    nanos <- Gen.choose(0, 999999999)
    contextual <- Gen.oneOf(false, true)
  } yield NativeInput(text, coefficient, scale, flag, seconds, nanos, contextual)

  implicit val nativeShrink: Shrink[NativeInput] = Shrink { sample =>
    val text = if (sample.text.isEmpty) Vector.empty else Vector(
      sample.copy(text = ""), sample.copy(text = sample.text.take(sample.text.length / 2)))
    val coefficient = if (sample.coefficient == 0) Vector.empty else Vector(
      sample.copy(coefficient = 0), sample.copy(coefficient = sample.coefficient / 2))
    val scale = if (sample.scale == 0) Vector.empty else Vector(
      sample.copy(scale = 0), sample.copy(scale = sample.scale / 2))
    val flag = if (sample.flag) Vector(sample.copy(flag = false)) else Vector.empty
    val seconds = if (sample.seconds == 0) Vector.empty else Vector(
      sample.copy(seconds = 0), sample.copy(seconds = sample.seconds / 2))
    val nanos = if (sample.nanos == 0) Vector.empty else Vector(
      sample.copy(nanos = 0), sample.copy(nanos = sample.nanos / 2))
    val context = if (sample.contextual) Vector(sample.copy(contextual = false)) else Vector.empty
    (text ++ coefficient ++ scale ++ flag ++ seconds ++ nanos ++ context).distinct.toStream
  }
}
