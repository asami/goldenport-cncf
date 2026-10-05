package org.goldenport.cncf.display

import java.time.Instant
import logicalui.DisplayMutationContractFixture
import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityRevisionTransport
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationContractPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationBinding.*
  import DisplayMutationResult.*
  import DisplayMutationContractFixture.*
  import GeneratedLogicalUiAbi.*
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _text = for {
    size <- Gen.choose(0, 40)
    characters <- Gen.listOfN(size, Gen.oneOf('a', '日', 'Ω', '\u0301', ' ', '\t', '\n', '\u0000', '\ud800', '\udfff'))
  } yield "valid:" + characters.mkString
  private val _decimal = for {
    coefficient <- Gen.choose(Long.MinValue, Long.MaxValue)
    scale <- Gen.choose(-20, 50)
  } yield BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(coefficient), scale))
  private val _instant = for {
    seconds <- Gen.choose(-100000000000L, 100000000000L)
    nanos <- Gen.choose(0, 999999999)
  } yield Instant.ofEpochSecond(seconds, nanos.toLong)
  private val _revision = Gen.frequency(4 -> Gen.oneOf(1L, 2L, Long.MaxValue - 1, Long.MaxValue),
    6 -> Gen.choose(1L, Long.MaxValue))
  private def _verify_result(result: Test.Result): Unit = {
    withClue(result.toString) {
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }
  private def _value(input: NativePatch, kind: ValueKind): DisplayValue = kind match {
    case ValueKind.Text => DisplayValue.Text(input.text)
    case ValueKind.Number => DisplayValue.Number(input.number)
    case ValueKind.Boolean => DisplayValue.Boolean(input.flag)
    case ValueKind.Timestamp => DisplayValue.Timestamp(input.instant)
    case ValueKind.Missing => DisplayValue.Missing
  }
  "E1 P1 generated native typed inputs" should afterWord("in spec:display-mutation, example:E1, rules:R1,R2, phase:96") {
    "retain generated opaque scopes native values and declaration target order over all permutations" in {
      Given("docs/spec/display-mutation.md R1,R2 E1: total bounded opaque IDs Unicode decimal nanos and field permutation generators")
      val generator = for {
        index <- Gen.choose(1, 1000000)
        text <- _text
        number <- _decimal
        instant <- _instant
        flag <- Gen.oneOf(true, false)
        declared <- Gen.oneOf(ValueKind.values.toVector.permutations.toVector)
        registered <- Gen.oneOf(ValueKind.values.toVector.permutations.toVector)
        requested <- Gen.oneOf(ValueKind.values.toVector.permutations.toVector)
      } yield (index, NativePatch(text, number, flag, instant, true), declared, registered, requested)
      val property = Prop.forAll(generator) { case (index, input, declared, registered, requested) =>
        Given("docs/spec/display-mutation.md R1,R2 E1: one generated opaque scope and explicit native registrations")
        val sectionid = "section:" + index
        val suffix = ":field:" + index
        val definition = nativeDefinitions(declared, sectionid).definitions.head
        val detail = definition.screen match { case value: DetailDefinition => value; case _ => fail("fixture must declare Detail") }
        val candidate = definition.copy(screenId = "screen:" + index, screen = detail.copy(sections = detail.sections.map(section =>
          section.copy(fields = section.fields.map(field => field.copy(id = field.id + suffix))))))
        val admitted = success(GeneratedLogicalUiAbi.admitC(Vector(candidate)))
        val registrations = nativeFields(registered, sectionid).map(field => field.copy(
          location = field.location.copy(fieldId = field.location.fieldId + suffix), bindingId = field.bindingId + suffix,
          target = UpdateTarget.Entity("owner:" + index, field.location.fieldId + suffix)))
        val binding = success(DisplayMutationBinding.admitC(source, admitted, candidate.screenId, "mutation:" + index,
          positiveCapabilities, registrations, Set.empty, (value: NativePatch) => Consequence.success(value)))
        val changes = requested.map(kind => FieldInput(FieldLocator(sectionid, kind.tag + suffix), _value(input, kind)))
        val request: Request.Create = Request.Create(binding.selection, changes)
        val empty = NativePatch("", BigDecimal(0), false, Instant.EPOCH, false)
        When("the generated native request is prepared twice")
        val first = success(binding.prepareCreateC(request, empty, Availability.enabled, authorize()))
        val second = success(binding.prepareCreateC(request, empty, Availability.enabled, authorize()))
        Then("native values and declaration-ordered targets survive in the typed input")
        first.input shouldBe input
        second.input shouldBe first.input
        first.input.text.toVector shouldBe input.text.toVector
        first.input.number.bigDecimal.unscaledValue shouldBe input.number.bigDecimal.unscaledValue
        first.input.number.bigDecimal.scale shouldBe input.number.bigDecimal.scale
        first.input.instant shouldBe input.instant
        first.targets shouldBe declared.map(kind => UpdateTarget.Entity("owner:" + index, kind.tag + suffix))
        first.bindingIds shouldBe declared.map(kind => "write." + kind.tag + suffix)
        true
      }
      When("ScalaCheck interprets the total native property for at least one hundred cases")
      val result = Test.check(_parameters, property)
      Then("at least one hundred successes and zero discards preserve every typed scalar and declaration order")
      _verify_result(result)
    }
  }
  "E2 P2 generated rejection and deterministic replay" should afterWord("in spec:display-mutation, example:E2, rules:R1,R3, phase:96") {
    "reject duplicate unknown readonly and wrong values before callbacks and retain deterministic pure input" in {
      Given("docs/spec/display-mutation.md R1,R3 E2: total text index and invalid-vector generators without filtering")
      val generator = for { text <- _text; index <- Gen.choose(0, 5) } yield (text, index)
      val property = Prop.forAll(generator) { case (text, index) =>
        Given("docs/spec/display-mutation.md R1,R3 E2: one generated typed Patch and invalid request variant")
        val trace = new Trace
        val binding = success(bindingC(Some(trace)))
        val changes = Vector(FieldInput(title, DisplayValue.Text(text)), FieldInput(description, DisplayValue.Text("")))
        val request = update(binding, changes)
        When("the generated valid request is prepared twice")
        val first = success(binding.prepareUpdateC(request, seed, resource(), transport(), Availability.enabled, authorize()))
        val second = success(binding.prepareUpdateC(request, seed, resource(), transport(), Availability.enabled, authorize()))
        Given("docs/spec/display-mutation.md R1,R3 E2: callback trace and one complete invalid field vector")
        val before = trace.calls
        val invalid = index match {
          case 0 => Vector(changes.head, changes.head)
          case 1 => Vector(FieldInput(FieldLocator("main", "unknown:" + text), DisplayValue.Text(text)))
          case 2 => Vector(FieldInput(stage, DisplayValue.Text(text)))
          case 3 => Vector(FieldInput(title, DisplayValue.Number(BigDecimal(1))))
          case 4 => Vector(FieldInput(title, DisplayValue.Text(null)))
          case _ => Vector.empty
        }
        When("the invalid variant is prepared")
        val rejected = binding.prepareUpdateC(update(binding, invalid), seed, resource(), transport(), Availability.enabled, authorize())
        Then("typed replay is deterministic and invalid preparation invokes no conversion or domain callback")
        first.input shouldBe Patch(text, "")
        second.input shouldBe first.input
        rejected.isSuccess shouldBe false
        trace.calls shouldBe before
        true
      }
      When("ScalaCheck interprets the total rejection property for at least one hundred cases")
      val result = Test.check(_parameters, property)
      Then("at least one hundred successes with zero discards preserve replay equality and pre-conversion rejection")
      _verify_result(result)
    }
  }
  "E3 P3 generated observed revisions" should afterWord("in spec:display-mutation, example:E3, rules:R3,R4, phase:96") {
    "admit current strict tags reject stale equal-value requests and never wrap maximum revision" in {
      Given("docs/spec/display-mutation.md R3,R4 E3: total positive revision generator including INITIAL and MAXIMUM boundaries")
      val property = Prop.forAll(_revision) { revision =>
        Given("docs/spec/display-mutation.md R3,R4 E3: one generated canonical current resource and strict observation")
        val trace = new Trace
        val binding = success(bindingC(Some(trace)))
        val current = resource(revision = revision)
        val request = update(binding, Vector(FieldInput(title, DisplayValue.Text(seed.title))), revision)
        val metadata = transport(revision = revision)
        When("the current equal-value request is prepared")
        val first = success(binding.prepareUpdateC(request, seed, current, metadata, Availability.enabled, authorize()))
        Given("docs/spec/display-mutation.md R3 E3: a distinct positive stale observation and retained trace")
        val before = trace.calls
        val stale = if (revision == 1) 2L else revision - 1
        When("the stale equal-value request and canonical tag arithmetic are interpreted")
        val rejected = binding.prepareUpdateC(request, seed, current, transport(revision = stale), Availability.enabled, authorize())
        val parsed = success(EntityRevisionTransport.parseEntityTagC(EntityRevisionTransport.entityTag(current.revision)))
        val next = current.revision.nextC
        Then("current succeeds stale fails before conversion and canonical arithmetic never wraps")
        first.input shouldBe seed
        first.observedRevision shouldBe Some(current.revision)
        parsed shouldBe current.revision
        rejected.isSuccess shouldBe false
        trace.calls shouldBe before
        if (revision == Long.MaxValue) next.isSuccess shouldBe false
        else success(next).value shouldBe revision + 1
        true
      }
      When("ScalaCheck interprets the total positive revision property for at least one hundred cases")
      val result = Test.check(_parameters, property)
      Then("at least one hundred successes and zero discards retain positive canonical arithmetic and stale-before-equality")
      _verify_result(result)
    }
  }
  "E4 P4 generated receipt source correspondence" should afterWord("in spec:display-mutation, example:E4, rules:R2,R4, phase:96") {
    "accept separately allocated equal sources reject foreign ready and retain committed pending receipts" in {
      Given("docs/spec/display-mutation.md R2,R4 E4: total canonical outcome index and positive revision generators")
      val generator = for {
        index <- Gen.choose(1, 1000000)
        observed <- Gen.choose(2L, Long.MaxValue - 1)
        kind <- Gen.oneOf(OutcomeKind.values.toVector)
      } yield (index, observed, kind)
      val property = Prop.forAll(generator) { case (index, observed, kind) =>
        Given("docs/spec/display-mutation.md R2,R4 E4: one generated canonical request outcome and supplied committed resource")
        val binding = success(bindingC(positive = true))
        val committed = kind match {
          case OutcomeKind.Created => 1L
          case OutcomeKind.Updated => observed + 1
          case _ => observed
        }
        val current = resource(index, observed)
        val request: Request = kind match {
          case OutcomeKind.Created => Request.Create(binding.selection, Vector.empty)
          case OutcomeKind.Deleted => Request.Delete(binding.selection, current.entityId)
          case _ => Request.Update(binding.selection, current.entityId, Vector(FieldInput(title, DisplayValue.Text("valid"))))
        }
        val observation = if (kind == OutcomeKind.Created) None else Some(current.revision)
        When("the generated supplied canonical receipt is admitted")
        val receipt = success(receiptC(binding.selection, request, kind, resource(index, committed), observation))
        if (kind == OutcomeKind.Deleted) {
          Given("docs/spec/display-mutation.md R4 E4: deleted receipt and counted reload callback")
          var calls = 0
          When("deleted completion is interpreted")
          val result = success(completeC(receipt, () => { calls += 1; Consequence.success(reload(committed, index)) }))
          Then("deletion preserves its receipt without invoking reload")
          calls shouldBe 0
          result.projection shouldBe None
          (result.receipt eq receipt) shouldBe true
          result.retryCommand shouldBe false
        } else {
          Given("docs/spec/display-mutation.md R2,R4 E4: separately allocated equal source and exact Detail")
          val snapshot = reload(committed, index, success(sourceC()))
          When("the exact source-bearing Ready candidate is admitted")
          val ready = success(createC(receipt, Some(ProjectionState.Ready(snapshot))))
          Then("equal logical source admits Ready and never permits command retry")
          ready.projection shouldBe Some(ProjectionState.Ready(snapshot))
          ready.retryCommand shouldBe false
          Vector(success(sourceC("org.goldenport.fixture.Foreign")), success(sourceC(resourceType = "Foreign"))).foreach { foreign =>
            Given("docs/spec/display-mutation.md R2,R4 E4: identical Detail with foreign logical source and counted reload")
            val candidate = snapshot.copy(source = foreign)
            var calls = 0
            When("foreign direct admission and postcommit completion are interpreted")
            val invalid = createC(receipt, Some(ProjectionState.Ready(candidate)))
            val pending = success(completeC(receipt, () => { calls += 1; Consequence.success(candidate) }))
            Then("foreign source rejects direct Ready and retains committed unavailable without replay")
            invalid.isSuccess shouldBe false
            calls shouldBe 1
            pending.projection shouldBe Some(ProjectionState.Pending(PendingReason.ProjectionUnavailable))
            (pending.receipt eq receipt) shouldBe true
            pending.receipt.resource.revision.value shouldBe committed
            pending.receipt.kind shouldBe kind
            pending.retryCommand shouldBe false
          }
          Given("docs/spec/display-mutation.md R2,R4 E4: correct source definition and identity with a different revision")
          val lagrevision = if (committed == 1L) 2L else committed - 1
          When("the lagging source-bearing reload completes after the supplied commit")
          val lag = success(completeC(receipt, () => Consequence.success(reload(lagrevision, index))))
          Then("correct-source lag is ViewNotCurrent with the exact committed receipt and no retry")
          lag.projection shouldBe Some(ProjectionState.Pending(PendingReason.ViewNotCurrent))
          (lag.receipt eq receipt) shouldBe true
          lag.retryCommand shouldBe false
        }
        true
      }
      When("ScalaCheck interprets the total receipt correspondence property for at least one hundred cases")
      val result = Test.check(_parameters, property)
      Then("at least one hundred successes and zero discards preserve supplied outcomes sources revisions and no replay")
      _verify_result(result)
    }
  }
}
