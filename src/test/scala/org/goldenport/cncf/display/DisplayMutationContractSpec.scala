package org.goldenport.cncf.display

import logicalui.DisplayMutationContractFixture
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.observation.{Descriptor as ObservationDescriptor, Taxonomy}
import org.goldenport.schema.{WebColumn, WebValidationHints}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationBinding.*
  import GeneratedLogicalUiAbi.ValueKind
  import DisplayMutationContractFixture.*
  private def _problem[A](result: Consequence[A]): Diagnostic = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.getException.collect {
      case carrier: MutationException => carrier.diagnostic
    }.getOrElse(fail("expected typed redacted mutation diagnostic"))
    case _ => fail("expected structured rejection")
  }
  private def _conclusion[A](result: Consequence[A]): Conclusion = result match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => fail("expected structured rejection")
  }
  private def _reasons[A](result: Consequence[A]): Vector[String] =
    _conclusion(result).observation.cause.descriptor.facets.collect { case ObservationDescriptor.Facet.Reason(name) => name }.toVector
  private def _prepare(binding: Binding[Patch], changes: Vector[FieldInput], trace: Trace): Consequence[Prepared[Patch]] =
    binding.prepareUpdateC(update(binding, changes), seed, resource(), transport(), Availability.enabled, authorize(Some(trace)))

  "E1 M1 typed ESS admission" should afterWord("in spec:display-mutation, example:E1, rules:R1,R2, phase:96") {
    "preserve explicit targets declaration order and inert readonly metadata" in {
      Given("docs/spec/display-mutation.md R1,R2 E1: ESS Detail definitions and typed pure Patch registrations")
      val trace = new Trace
      When("complete bindings and current descriptor are admitted")
      val binding = success(bindingC(Some(trace)))
      val descriptor = success(binding.descriptorC(Availability.enabled))
      Then("all declared fields and provenance survive with no callback and no inferred stage inverse")
      trace.calls shouldBe Vector.empty
      descriptor.selection.definition.provenance shouldBe definitions.definitions(1).provenance
      descriptor.fields.map(_.location) shouldBe Vector(title, stage, description)
      descriptor.fields.map(_.bindingId) shouldBe Vector(Some("write.title"), None, Some("write.description"))
      descriptor.fields(1).editability shouldBe FieldEditability.ReadOnly
      descriptor.fields(1).web shouldBe WebColumn.empty.copy(readonly = true)
      descriptor.fields.head.web shouldBe fields().last.web
      Given("docs/spec/display-mutation.md R1 E1: malformed and duplicate explicit registrations")
      val registrations = fields()
      val invalid = Vector(
        registrations :+ registrations.head,
        Vector(registrations.head, registrations.last.copy(bindingId = registrations.head.bindingId)),
        Vector(registrations.head.copy(acceptedKinds = Vector.empty)),
        Vector(registrations.head.copy(acceptedKinds = Vector(ValueKind.Text, ValueKind.Text))),
        Vector(registrations.head.copy(acceptedKinds = Vector(ValueKind.Number))),
        Vector(registrations.head.copy(web = WebColumn.empty.copy(readonly = true))),
        Vector(registrations.head.copy(web = WebColumn.empty.copy(system = true))),
        Vector(registrations.head.copy(web = WebColumn.empty.copy(values = null))),
        Vector(registrations.head.copy(web = WebColumn.empty.copy(validation = WebValidationHints(min = Some(BigDecimal(2)), max = Some(BigDecimal(1)))))),
        Vector(registrations.head.copy(convert = null)))
      When("each complete candidate registration is admitted")
      val rejected = invalid.map(candidate => admitC(source, definitions, "capture-detail", "mutation", capabilities,
        candidate, Set.empty, input => validateC(input)))
      Then("every static defect rejects before any converter or domain callback")
      rejected.foreach(result => result.isSuccess shouldBe false)
      trace.calls shouldBe Vector.empty
    }
  }
  "E2 M2 typed preparation" should afterWord("in spec:display-mutation, example:E2, rules:R1,R3, phase:96") {
    "retain exact native text and use declaration order rather than request registration order" in {
      Given("docs/spec/display-mutation.md R1,R3 E2: a current resource and reversed title description input")
      val trace = new Trace
      val binding = success(bindingC(Some(trace)))
      val text = "  日 Ω e\u0301 \ud83d\ude00\ud800\udfff\t\n  "
      val changes = Vector(FieldInput(description, DisplayValue.Text("")), FieldInput(title, DisplayValue.Text(text)))
      When("the pure update is prepared under strict observed metadata")
      val prepared = success(_prepare(binding, changes, trace))
      Then("canonical typed Patch preserves code units empty description and strict policy without source effects")
      prepared.input shouldBe Patch(text, "")
      prepared.input.title.toVector shouldBe text.toVector
      prepared.targets shouldBe Vector(titleTarget, descriptionTarget)
      prepared.bindingIds shouldBe Vector("write.title", "write.description")
      prepared.observedRevision shouldBe Some(resource().revision)
      prepared.executionPolicy shouldBe transport().executionPolicy
      trace.calls shouldBe Vector("authorize", "title", "description", "domain")
    }
  }
  "E3 M3 complete input rejection" should afterWord("in spec:display-mutation, example:E3, rules:R1,R3, phase:96") {
    "reject invalid vectors managed targets and selected aliases before conversion" in {
      val cases = Vector(
        Vector.empty[FieldInput] -> "VALUE_INVALID",
        Vector(FieldInput(title, DisplayValue.Text("a")), FieldInput(title, DisplayValue.Text("b"))) -> "FIELD_DUPLICATE",
        Vector(FieldInput(FieldLocator("main", "foreign"), DisplayValue.Text("a"))) -> "FIELD_UNKNOWN",
        Vector(FieldInput(stage, DisplayValue.Text("draft"))) -> "FIELD_READ_ONLY",
        Vector(FieldInput(title, DisplayValue.Number(BigDecimal(1)))) -> "VALUE_KIND_INVALID",
        Vector(FieldInput(title, DisplayValue.Text(null))) -> "VALUE_INVALID")
      cases.foreach { case (changes, code) =>
        Given("docs/spec/display-mutation.md R1,R3 E3: a complete invalid field vector " + code)
        val trace = new Trace
        val binding = success(bindingC(Some(trace)))
        When("the current revision update is prepared")
        val result = _prepare(binding, changes, trace)
        Then("the specific failure precedes converters and domain validation")
        _problem(result).code shouldBe "DSP96_MUTATION_" + code
        trace.calls.filterNot(_ == "authorize") shouldBe Vector.empty
      }
      Given("docs/spec/display-mutation.md R1 E3: a managed target and two legal aliased registrations")
      val trace = new Trace
      val registrations = fields(Some(trace))
      When("managed registration is admitted and aliased inputs are prepared")
      val managed = admitC(source, definitions, "capture-detail", "mutation", capabilities, registrations,
        Set(titleTarget), input => validateC(input, Some(trace)))
      val aliases = success(admitC(source, definitions, "capture-detail", "mutation", capabilities,
        registrations.map(_.copy(target = titleTarget)), Set.empty, input => validateC(input, Some(trace))))
      val duplicate = _prepare(aliases, Vector(FieldInput(title, DisplayValue.Text("a")),
        FieldInput(description, DisplayValue.Text("b"))), trace)
      Then("managed registration and duplicate canonical target selection have distinct rejection codes")
      _problem(managed).code shouldBe "DSP96_MUTATION_TARGET_MANAGED"
      _problem(duplicate).code shouldBe "DSP96_MUTATION_TARGET_DUPLICATE"
      trace.calls shouldBe Vector("authorize")
      Given("docs/spec/display-mutation.md R1 E3: admitted Number and Timestamp fields with null native payloads")
      val kinds = Vector(ValueKind.Number, ValueKind.Timestamp)
      val native = success(admitC(source, nativeDefinitions(kinds), "capture-detail", "native", positiveCapabilities,
        nativeFields(kinds), Set.empty, (input: NativePatch) => Consequence.success(input)))
      val nativeseed = NativePatch("", BigDecimal(0), false, java.time.Instant.EPOCH, false)
      When("each null native payload is prepared")
      val rejected = Vector(DisplayValue.Number(null), DisplayValue.Timestamp(null)).map(value =>
        native.prepareCreateC(Request.Create(native.selection, Vector(FieldInput(FieldLocator("native", value.kind.tag), value))),
          nativeseed, Availability.enabled, authorize()))
      Then("typed native nulls reject rather than coerce")
      rejected.foreach(result => _problem(result).code shouldBe "DSP96_MUTATION_VALUE_INVALID")
    }
  }
  "E4 M4 authorization support and availability" should afterWord("in spec:display-mutation, example:E4, rules:R1,R3, phase:96") {
    "preserve forbidden conclusions and distinguish unsupported unavailable readonly and atomic capability" in {
      Given("docs/spec/display-mutation.md R1,R3 E4: a shape-valid update and forbidden current authorizer")
      val trace = new Trace
      val binding = success(bindingC(Some(trace)))
      val forbidden = Consequence.securityPermissionDenied[Unit]("contract fixture denied")
      val request = update(binding, Vector(FieldInput(title, DisplayValue.Text("valid"))))
      When("the authorizer rejects once")
      val denied = binding.prepareUpdateC(request, seed, resource(), transport(), Availability.enabled, () => {
        trace.record("authorize"); forbidden
      })
      Then("the original security conclusion survives without conversion")
      _conclusion(denied) shouldBe _conclusion(forbidden)
      trace.calls shouldBe Vector("authorize")
      Vector(AvailabilityState.Disabled, AvailabilityState.Hidden).foreach { state =>
        Given("docs/spec/display-mutation.md R1 E4: current unavailable update state")
        When("the otherwise valid update is prepared")
        val result = binding.prepareUpdateC(request, seed, resource(), transport(),
          Availability.enabled.copy(update = state), authorize())
        Then("availability veto is distinct from support")
        _reasons(result) should contain("operation-unavailable")
      }
      Given("docs/spec/display-mutation.md R1,R3 E4: unsupported create, current readonly, and supported delete without atomic capability")
      val positive = success(bindingC(Some(trace), positive = true))
      When("each independent guard is interpreted")
      val unsupported = binding.prepareCreateC(Request.Create(binding.selection, Vector.empty), seed, Availability.enabled, authorize())
      val readonly = binding.prepareUpdateC(request, seed, resource(), transport(),
        Availability.enabled.copy(readonlyFields = Set(title)), authorize())
      val atomic = positive.prepareDeleteC(Request.Delete(positive.selection, resource().entityId), resource(),
        transport(Operation.Delete), Availability.enabled, false, authorize())
      val unknown = binding.descriptorC(Availability.enabled.copy(readonlyFields = Set(FieldLocator("main", "unknown"))))
      Then("the operation capability, readonly and atomic guards reject without invoking mutation callbacks")
      _reasons(unsupported) should contain("operation-unsupported")
      _problem(readonly).code shouldBe "DSP96_MUTATION_FIELD_READ_ONLY"
      _reasons(atomic) should contain("atomic-delete-unsupported")
      _problem(unknown).code shouldBe "DSP96_MUTATION_FIELD_UNKNOWN"
      trace.calls shouldBe Vector("authorize")
    }
  }
  "E5 M5 domain and callback failure" should afterWord("in spec:display-mutation, example:E5, rules:R1,R3, phase:96") {
    "retain domain veto and stop after failure exception or null callback output" in {
      Given("docs/spec/display-mutation.md R1,R3 E5: permissive editor hints and a blank canonical title")
      val trace = new Trace
      val binding = success(bindingC(Some(trace)))
      When("blank text reaches the authoritative domain adapter")
      val veto = _prepare(binding, Vector(FieldInput(title, DisplayValue.Text(" \t\n"))), trace)
      Then("permissive hints cannot weaken the original domain conclusion")
      _conclusion(veto) shouldBe _conclusion(domainFailure)
      trace.calls shouldBe Vector("authorize", "title", "domain")
      val callbacks: Vector[(Patch, DisplayValue) => Consequence[Patch]] = Vector(
        (_, _) => domainFailure, (_, _) => throw new IllegalStateException("SECRET callback payload"),
        (_, _) => null, (_, _) => Consequence.success(null: Patch))
      callbacks.zipWithIndex.foreach { case (callback, index) =>
        Given("docs/spec/display-mutation.md R3 E5: a pure callback returns Failure NonFatal null consequence or null output")
        val trace = new Trace
        val registrations = fields(Some(trace)).map(field => if (field.location == title) field.copy(convert = callback) else field)
        val binding = success(admitC(source, definitions, "capture-detail", "callback", capabilities, registrations,
          Set.empty, input => validateC(input, Some(trace))))
        When("title and later description conversions are requested")
        val result = _prepare(binding, Vector(FieldInput(title, DisplayValue.Text("valid")),
          FieldInput(description, DisplayValue.Text("later"))), trace)
        Then("failure short-circuits all later callbacks and hides exception content")
        trace.calls shouldBe Vector("authorize")
        if (index == 0) _conclusion(result) shouldBe _conclusion(domainFailure)
        else {
          _problem(result).code shouldBe "DSP96_MUTATION_CALLBACK_INVALID"
          _problem(result).reason should not include "SECRET"
        }
      }
      val domaincallbacks: Vector[Patch => Consequence[Patch]] = Vector(_ => domainFailure,
        _ => throw new IllegalStateException("SECRET domain"), _ => null, _ => Consequence.success(null: Patch))
      domaincallbacks.zipWithIndex.foreach { case (callback, index) =>
        Given("docs/spec/display-mutation.md R3 E5: an authoritative domain callback fails after all selected conversions")
        val trace = new Trace
        val binding = success(admitC(source, definitions, "capture-detail", "domain-callback", capabilities,
          fields(Some(trace)), Set.empty, callback))
        When("the prepared typed title reaches that domain adapter")
        val result = _prepare(binding, Vector(FieldInput(title, DisplayValue.Text("valid"))), trace)
        Then("the domain failure survives or a defect becomes a fixed redacted callback failure")
        trace.calls shouldBe Vector("authorize", "title")
        if (index == 0) _conclusion(result) shouldBe _conclusion(domainFailure)
        else _problem(result).code shouldBe "DSP96_MUTATION_CALLBACK_INVALID"
      }
    }
  }
  "E6 M6 source selection and revision correspondence" should afterWord("in spec:display-mutation, example:E6, rules:R2,R3, phase:96") {
    "accept equal logical Source and reject changed selection identity or stale revision before callbacks" in {
      Given("docs/spec/display-mutation.md R2,R3 E6: separately allocated Source with identical logical values")
      val trace = new Trace
      val binding = success(bindingC(Some(trace)))
      val changes = Vector(FieldInput(title, DisplayValue.Text(seed.title)))
      val request = update(binding, changes)
      val equal: Request.Update = request.copy(selection = binding.selection.copy(source = success(sourceC())))
      When("the equal source request is prepared")
      val accepted = binding.prepareUpdateC(equal, seed, resource(), transport(), Availability.enabled, authorize())
      Then("Source reference allocation does not change correspondence")
      success(accepted).input shouldBe seed
      trace.calls shouldBe Vector("title", "domain")
      val reference = binding.selection.definition
      val selections = Vector(
        binding.selection.copy(source = success(sourceC("org.goldenport.fixture.Foreign"))),
        binding.selection.copy(source = success(sourceC(resourceType = "Foreign"))),
        binding.selection.copy(definition = reference.copy(screenId = "other")),
        binding.selection.copy(definition = reference.copy(definitionRevision = "other")),
        binding.selection.copy(definition = reference.copy(mutationRevision = "other")),
        binding.selection.copy(definition = reference.copy(provenance = reference.provenance.copy(producerId = "other"))),
        binding.selection.copy(definition = reference.copy(provenance = reference.provenance.copy(sourceId = "other"))),
        binding.selection.copy(definition = reference.copy(provenance = reference.provenance.copy(revision = "other"))))
      selections.foreach { selection =>
        Given("docs/spec/display-mutation.md R2 E6: one source definition or provenance value differs")
        val before = trace.calls
        When("the changed selection request is prepared")
        val result = binding.prepareUpdateC(request.copy(selection = selection), seed, resource(), transport(),
          Availability.enabled, authorize(Some(trace)))
        Then("selection fails before authorization and conversion")
        _problem(result).code shouldBe "DSP96_MUTATION_SELECTION_MISMATCH"
        trace.calls shouldBe before
      }
      Given("docs/spec/display-mutation.md R3 E6: foreign current resource and stale observation with equal business values")
      val before = trace.calls
      val expectedconflict = Consequence.operationConflict[Unit]("display-mutation", Vector.empty)
      When("current identity and stale revision guards are interpreted")
      val foreign = binding.prepareUpdateC(request, seed, resource(index = 2), transport(), Availability.enabled, authorize())
      val stale = binding.prepareUpdateC(request, seed, resource(revision = 8), transport(), Availability.enabled, authorize())
      Then("identity mismatch and stale conflict prevent value equality from bypassing revision checks")
      _problem(foreign).code shouldBe "DSP96_MUTATION_SELECTION_MISMATCH"
      _conclusion(stale).observation.taxonomy shouldBe _conclusion(expectedconflict).observation.taxonomy
      _reasons(stale) should contain ("stale-entity-revision")
      trace.calls shouldBe before
    }
  }
  "E7 M7 separate positive contract" should afterWord("in spec:display-mutation, example:E7, rules:R1,R3, phase:96") {
    "prepare create defaults and strict delete without claiming persistence lifecycle" in {
      Given("docs/spec/display-mutation.md R1,R3 E7: explicitly supported separate positive-contract binding")
      val trace = new Trace
      val binding = success(bindingC(Some(trace), positive = true))
      When("empty create uses typed defaults and delete uses exact current strict metadata")
      val created = success(binding.prepareCreateC(Request.Create(binding.selection, Vector.empty), seed,
        Availability.enabled, authorize(Some(trace))))
      val deleted = success(binding.prepareDeleteC(Request.Delete(binding.selection, resource().entityId), resource(),
        transport(Operation.Delete), Availability.enabled, true, authorize(Some(trace))))
      Then("create validates defaults once and delete calls no converter or domain adapter")
      created.input shouldBe seed
      created.observedRevision shouldBe None
      created.executionPolicy shouldBe None
      deleted.observedRevision shouldBe resource().revision
      deleted.executionPolicy shouldBe transport(Operation.Delete).executionPolicy.get
      trace.calls shouldBe Vector("authorize", "domain", "authorize")
      Given("docs/spec/display-mutation.md R1,R3 E7: typed create defaults violate canonical title validity")
      When("empty create is prepared with invalid typed defaults")
      val invalid = binding.prepareCreateC(Request.Create(binding.selection, Vector.empty), Patch(" ", ""),
        Availability.enabled, authorize())
      Then("empty create still passes through the authoritative domain veto")
      _conclusion(invalid) shouldBe _conclusion(domainFailure)
    }
  }
  "E8 TYPE admitted construction" should afterWord("in spec:display-mutation, example:E8, rules:R1,R4, phase:96") {
    "reject untyped callbacks bare Display ready and admitted constructor copy bypasses at compile time" in {
      Given("docs/spec/display-mutation.md R1,R4 E8: external native compile-time probes")
      When("the compiler interprets prohibited public type constructions")
      val result = verifyTypeBoundary()
      Then("every assertTypeError probe rejects while ReloadedDisplay remains only a candidate")
      result shouldBe (())
    }
  }
}
