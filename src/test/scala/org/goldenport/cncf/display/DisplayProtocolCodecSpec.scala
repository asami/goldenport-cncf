package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import io.circe.{Json, JsonObject}
import logicalui.{ExpectedGeneratedLogicalUiFixture, GeneratedDisplayConsumerFixture, TypedDisplayProjectionFixture}
import domain.logicalui.DomainLogicalUiDefinitions
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin, ComponentFactory, DisplayProtocolComponentFixture}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
final class DisplayProtocolCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayModel.*
  import GeneratedLogicalUiAbi.*
  import DisplayProtocolSpecFixture.*

  "E1 Actual generated source snapshots" should afterWord("in spec:display-model-protocol, example:E1, rules:R1,R2,R3,R4,R8, phase:96") {
    "restore the two exact goldens against the unchanged component-owned admission" in {
      Given("docs/spec/display-model-protocol.md R1,R2,R3,R4,R8 E1: actual two-source Cozy delivery and deterministic Capture rows")
      val subsystem = TestComponentFactory.emptySubsystem("display-protocol-consumer")
      try {
        given ExecutionContext = ExecutionContext.create()
        val rows = Vector(success(TypedDisplayProjectionFixture.captureC(1, 7)), success(TypedDisplayProjectionFixture.captureC(2, 19)))
        val component = new GeneratedDisplayConsumerFixture.ActualComponent(rows)
        val componentid = source.componentId
        component.initialize(ComponentInit(subsystem = subsystem,
          core = Component.Core.create(name = componentid.name, componentId = componentid,
            instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty), origin = ComponentOrigin.Builtin))
        When("normal bootstrap admits the actual definitions and contextual query then read produces the matching snapshots")
        success(new ComponentFactory().bootstrapC(component))
        val registry = component.displayRuntime.getOrElse(fail("expected complete normal registry"))
        val list = success(registry.queryC("collecting-resources", Query("all actual resources")))
        val detail = success(registry.readC("capture-detail", rows.head.resource.entityId))
        val browser = component.installed.getOrElse(fail("expected installed typed source")).browser
        val readcalls = browser.findCalls
        val querycalls = browser.queryCalls
        val metadatareads = component.metadataReads
        val bindingreads = component.bindingReads
        val stored = DisplayProtocolComponentFixture.admittedDefinitions(component)
        Then("normal bootstrap supplies a nonempty component-owned wrapper without another provider or source call")
        stored.isDefined shouldBe true
        val admitted = stored.getOrElse(fail("bootstrap must retain admitted definitions"))
        val definitions = admitted.definitions
        (definitions eq DomainLogicalUiDefinitions.definitions) shouldBe true
        metadatareads shouldBe 1
        bindingreads shouldBe 1
        When("the pure codec encodes and restores each actual snapshot using that exact admission")
        val listjson = success(DisplayProtocolCodec.encodeC(source, list))
        val detailjson = success(DisplayProtocolCodec.encodeC(source, detail))
        val decodedlist = success(DisplayProtocolCodec.decodeC(source, admitted, listjson))
        val decodeddetail = success(DisplayProtocolCodec.decodeC(source, admitted, detailjson))
        Then("golden bytes include exactly one LF and values revisions provenance and object identities remain unchanged with idle sources")
        (listjson + "\n").getBytes(StandardCharsets.UTF_8).toVector shouldBe _golden("ess2-01-list.json")
        (detailjson + "\n").getBytes(StandardCharsets.UTF_8).toVector shouldBe _golden("ess2-01-detail.json")
        decodedlist.model shouldBe list.model
        decodeddetail.model shouldBe detail.model
        (decodedlist.definition eq definitions.head) shouldBe true
        (decodeddetail.definition eq definitions(1)) shouldBe true
        (DisplayProtocolComponentFixture.admittedDefinitions(component) eq stored) shouldBe true
        (DisplayProtocolComponentFixture.admittedDefinitions(component).get eq admitted) shouldBe true
        (admitted.definitions eq definitions) shouldBe true
        definitions.foreach(_.provenance shouldBe SourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1"))
        definitions.head.provenance should not be ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions.head.provenance
        component.metadataReads shouldBe metadatareads
        component.bindingReads shouldBe bindingreads
        (browser.findCalls eq readcalls) shouldBe true
        (browser.queryCalls eq querycalls) shouldBe true
        browser.contextFreeCalls shouldBe 0
        browser.countCalls shouldBe 0
      } finally subsystem.shutdown()
    }
  }

  "E2 Independent all-kind scalar snapshots" should afterWord("in spec:display-model-protocol, example:E2, rules:R1,R2,R3,R4, phase:96") {
    "preserve every UTF16 unit decimal coefficient and scale and typed resource or Instant limit" in {
      Given("docs/spec/display-model-protocol.md R1,R2,R3,R4 E2: independent admitted all-kind definitions and full scalar boundary values")
      val texts = Vector("", " \t\b\f\n\r\u0000\"\\日本 Ω e\u0301 \ud83d\ude00\ud800\udfff")
      val decimals = Vector(decimal("12345678901234567890123456789012345000", 7), decimal("-7", -3),
        decimal("1", Int.MinValue), decimal("1", Int.MaxValue), decimal("0", Int.MinValue),
        decimal("0", Int.MaxValue), decimal("0", 7), decimal("12345000", 5))
      val instants = Vector(Instant.ofEpochSecond(-1, 987654321), Instant.MIN, Instant.MAX)
      texts.foreach { text => decimals.foreach { number => instants.foreach { instant =>
        Given("docs/spec/display-model-protocol.md R3 E2: a declared all-kind case with exact independently supplied scalar representation")
        val fixture = sample(text, number, instant, 2, true, Long.MaxValue)
        When("the pure codec round trips each admitted List and Detail")
        val decoded = fixture.displays.map(display => roundTrip(fixture, display))
        Then("typed models retain exact code units coefficient scale nanos opaque identity and maximum positive revision")
        decoded.zip(fixture.displays).foreach { case (actual, expected) => checkDisplay(actual, expected) }
        fixture.definitions.definitions.head.provenance shouldBe SourceProvenance("cncf.protocol-all-kind-fixture", "pure-values", "fixture-1")
      } } }
    }
  }

  "E3 Scoped ordered containers and absence" should afterWord("in spec:display-model-protocol, example:E3, rules:R2,R3,R4, phase:96") {
    "retain legal reused IDs empty containers optional titles snapshots and logical Detail targets" in {
      Given("docs/spec/display-model-protocol.md R2,R3,R4 E3: legal finite container layouts and both optional states")
      Vector(0, 1, 3).foreach { size => Vector(false, true).foreach { optional =>
        Given("docs/spec/display-model-protocol.md R3 E3: scoped reused field and action IDs with supplied array order")
        val fixture = sample(" supplied ", decimal("1200", 2), Instant.EPOCH, size, optional)
        When("each declared snapshot is encoded and restored")
        val encoded = fixture.displays.map(display => success(DisplayProtocolCodec.encodeC(source, display)))
        val decoded = encoded.map(json => success(DisplayProtocolCodec.decodeC(source, fixture.definitions, json)))
        Then("business arrays and allowed optional members preserve exact shape without null or default insertion")
        decoded.zip(fixture.displays).foreach { case (actual, expected) => checkDisplay(actual, expected) }
        val list = json(encoded.head).hcursor.downField("model")
        list.downField("detailTarget").get[String]("screenId").toOption shouldBe Some("pure-detail")
        list.downField("items").focus.flatMap(_.asArray).map(_.size) shouldBe Some(size)
        list.downField("snapshot").focus.isDefined shouldBe optional
        val sections = json(encoded(1)).hcursor.downField("model").downField("sections").focus.get.asArray.get
        sections.head.asObject.get.contains("title") shouldBe optional
        sections(1).asObject.get.contains("title") shouldBe false
        encoded.foreach(_ should not include ":null")
      } }
      Given("docs/spec/display-model-protocol.md R3 E3: explicit admitted definitions with no fields sections or actions")
      val fixture = emptySample()
      When("the empty List and Detail round trip")
      val decoded = fixture.displays.map(display => roundTrip(fixture, display))
      Then("all required empty arrays are retained")
      decoded.zip(fixture.displays).foreach { case (actual, expected) => checkDisplay(actual, expected) }
    }
  }

  "E4 Independent compatibility dimensions" should afterWord("in spec:display-model-protocol, example:E4, rules:R2,R5,R6, phase:96") {
    "reject every changed protocol ABI semantic or driver member at its exact path" in {
      Given("docs/spec/display-model-protocol.md R2,R5,R6 E4: a canonical snapshot and separately changed independent descriptors")
      val fixture = sample()
      compatibilityCases(fixture).foreach { candidate =>
        Given(s"docs/spec/display-model-protocol.md R2 E4: one changed descriptor at ${candidate.path}")
        When("the candidate is restored against the unchanged trusted admission")
        val result = DisplayProtocolCodec.decodeC(source, fixture.definitions, candidate.input)
        Then("the first incompatible dimension has its own structured redacted code and exact path")
        checkFailure(result, candidate)
      }
    }
  }

  "E5 Strict JSON and API input shapes" should afterWord("in spec:display-model-protocol, example:E5, rules:R1,R5,R6, phase:96") {
    "reject malformed duplicate missing unknown null and wrong primitive members at every nested scope" in {
      Given("docs/spec/display-model-protocol.md R1,R5,R6 E5: every nested object and member of canonical List and Detail input")
      val fixture = sample()
      shapeCases(fixture).foreach { candidate =>
        Given(s"docs/spec/display-model-protocol.md R5 E5: one structural defect at ${candidate.path}")
        When("strict decoding interprets the candidate")
        val result = DisplayProtocolCodec.decodeC(source, fixture.definitions, candidate.input)
        Then("fixed typed code and path reject the entire payload without exception text or partial success")
        checkFailure(result, candidate)
      }
      Given("docs/spec/display-model-protocol.md R1,R6 E5: null API inputs and invalid explicit source parameters")
      val display = fixture.displays.head
      val text = success(DisplayProtocolCodec.encodeC(source, display))
      When("the guarded boundary receives each invalid input")
      val results = Vector(
        DisplayProtocolCodec.encodeC(null, display) -> "source", DisplayProtocolCodec.encodeC(source, null) -> "display",
        DisplayProtocolCodec.decodeC(null, fixture.definitions, text) -> "expectedSource",
        DisplayProtocolCodec.decodeC(source, null, text) -> "definitions",
        DisplayProtocolCodec.decodeC(source, fixture.definitions, null) -> "json",
        DisplayProtocol.Source.createC(null, "type") -> "componentId",
        DisplayProtocol.Source.createC(source.componentId, null) -> "resourceType",
        DisplayProtocol.Source.createC(source.componentId, " type ") -> "resourceType",
        DisplayProtocol.Source.createC(source.componentId, "") -> "resourceType")
      Then("each guard returns INPUT_INVALID at its API argument")
      results.foreach { case (result, path) => checkFailure(result, InvalidCase("", "INPUT_INVALID", path)) }
    }
  }

  "E6 Trusted source definitions and closed tags" should afterWord("in spec:display-model-protocol, example:E6, rules:R1,R2,R3,R5,R6, phase:96") {
    "reject foreign identity provenance definition and unknown kinds without changing trusted authority" in {
      Given("docs/spec/display-model-protocol.md R1,R2,R3,R5,R6 E6: exact trusted objects and one changed source metadata or tag")
      val fixture = sample()
      val definitions = fixture.definitions.definitions
      identityCases(fixture).foreach { candidate =>
        Given(s"docs/spec/display-model-protocol.md R2,R3 E6: one closed boundary defect at ${candidate.path}")
        When("the pure decoder interprets that single candidate")
        val result = DisplayProtocolCodec.decodeC(source, fixture.definitions, candidate.input)
        Then("the typed mismatch is exact and original source admission definitions and models retain identity")
        checkFailure(result, candidate)
        (fixture.definitions.definitions eq definitions) shouldBe true
        fixture.displays.foreach(display => (display.definition eq definitions.find(_.screenId == display.model.screenId).get) shouldBe true)
      }
    }
  }

  "E7 Resource scalars and semantic construction" should afterWord("in spec:display-model-protocol, example:E7, rules:R3,R5,R6, phase:96") {
    "reject noncanonical resources and pass original Display validation diagnostics unchanged" in {
      Given("docs/spec/display-model-protocol.md R3,R5,R6 E7: malformed opaque resources and semantic model candidates")
      val fixture = sample()
      resourceCases(fixture).foreach { candidate =>
        Given(s"docs/spec/display-model-protocol.md R3 E7: one resource defect at ${candidate.path}")
        When("the strict decoder reads the candidate")
        val result = DisplayProtocolCodec.decodeC(source, fixture.definitions, candidate.input)
        Then("the canonical resource boundary reports its fixed typed diagnostic")
        checkFailure(result, candidate)
      }
      val list = fixture.displays.head.model match { case value: ListModel => value; case _ => fail("expected List") }
      val item = list.items.head
      def _with_field_(field: Field): ListModel =
        list.copy(items = Vector(item.copy(fields = item.fields.updated(0, field))))
      val semantic = Vector(
        list.copy(items = list.items :+ item), list.copy(title = "different title"),
        list.copy(items = Vector(item.copy(fields = item.fields.reverse))),
        _with_field_(item.fields.head.copy(value = DisplayValue.Text("  "))),
        _with_field_(item.fields.head.copy(role = PresentationRole.Status)),
        _with_field_(item.fields.head.copy(value = DisplayValue.Number(BigDecimal(1)))))
      semantic.foreach { model =>
        Given("docs/spec/display-model-protocol.md R3 E7: a structurally expressible candidate violating declared model semantics")
        val candidate = json(success(DisplayProtocolCodec.encodeC(source, fixture.displays.head)))
        val altered = replaceModel(candidate, model)
        When("normal Display construction and wire restoration interpret the same candidate")
        val direct = Display.createC(fixture.definitions, model)
        val decoded = DisplayProtocolCodec.decodeC(source, fixture.definitions, altered.noSpaces)
        Then("the original DSP96 code path reason taxonomy and typed cause pass unchanged")
        semanticDiagnostic(decoded) shouldBe semanticDiagnostic(direct)
        failure(decoded).conclusion.observation.taxonomy shouldBe failure(direct).conclusion.observation.taxonomy
      }
    }
  }

  "E8 Canonical text and typed scalar admission" should afterWord("in spec:display-model-protocol, example:E8, rules:R3,R4,R5,R6, phase:96") {
    "recanonicalize reordered whitespace input and reject noncanonical decimal and Instant strings" in {
      Given("docs/spec/display-model-protocol.md R3,R4,R5,R6 E8: valid snapshots with recursively reordered members and whitespace")
      val fixture = sample()
      fixture.displays.foreach { display =>
        Given("docs/spec/display-model-protocol.md R4 E8: the same semantic object in a different JSON layout")
        val canonical = success(DisplayProtocolCodec.encodeC(source, display))
        val reordered = reorder(json(canonical)).spaces2
        When("the decoder restores that layout and the encoder renders it again")
        val restored = success(DisplayProtocolCodec.decodeC(source, fixture.definitions, reordered))
        val encoded = success(DisplayProtocolCodec.encodeC(source, restored))
        Then("output exactly equals canonical input with all semantic bytes preserved")
        encoded shouldBe canonical
        checkDisplay(restored, display)
      }
      scalarCases(fixture).foreach { candidate =>
        Given(s"docs/spec/display-model-protocol.md R3 E8: one noncanonical typed scalar at ${candidate.path}")
        When("the decoder interprets that scalar")
        val result = DisplayProtocolCodec.decodeC(source, fixture.definitions, candidate.input)
        Then("VALUE_INVALID retains a fixed exact payload path")
        checkFailure(result, candidate)
      }
    }
  }

  private def _golden(name: String): Vector[Byte] = {
    val stream = Option(getClass.getResourceAsStream("/display/protocol/v1/" + name)).getOrElse(fail("expected golden resource"))
    try stream.readAllBytes().toVector finally stream.close()
  }
}

/** Pure independent fixtures shared by the two codec behavior suites. */
private[display] object DisplayProtocolSpecFixture extends Matchers {
  import DisplayModel.*
  import GeneratedLogicalUiAbi.*
  final case class Sample(definitions: AdmittedDefinitions, displays: Vector[Display])
  final case class InvalidCase(input: String, code: String, path: String)
  val source: DisplayProtocol.Source = success(DisplayProtocol.Source.createC(
    ComponentId("org.goldenport.cncf.test.GeneratedLogicalUiConsumer"), "collecting-reference"))
  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case _ => org.scalatest.Assertions.fail("expected admitted fixture or successful codec result")
  }
  def failure(result: Consequence[?]): Consequence.Failure[?] = result match {
    case value: Consequence.Failure[?] => value
    case _ => org.scalatest.Assertions.fail("expected structured rejection")
  }
  def diagnostic(result: Consequence[?]): DisplayProtocol.Diagnostic =
    failure(result).conclusion.observation.cause.getException match {
      case Some(value: DisplayProtocol.CodecException) => value.diagnostic
      case _ => org.scalatest.Assertions.fail("expected typed wire cause")
    }
  def semanticDiagnostic(result: Consequence[?]): Display.Diagnostic =
    failure(result).conclusion.observation.cause.getException match {
      case Some(value: Display.ValidationException) => value.diagnostic
      case _ => org.scalatest.Assertions.fail("expected unchanged typed Display cause")
    }
  def decimal(coefficient: String, scale: Int): BigDecimal =
    BigDecimal(new java.math.BigDecimal(new java.math.BigInteger(coefficient), scale))
  def sample(text: String = "exact text", number: BigDecimal = decimal("1234500", 4),
    instant: Instant = Instant.ofEpochSecond(-1, 123456789), size: Int = 2,
    optional: Boolean = true, revision: Long = 7, reverse: Boolean = false): Sample = {
    val values = Vector(DisplayValue.Text("title"), DisplayValue.Text(text), DisplayValue.Number(number),
      DisplayValue.Boolean(true), DisplayValue.Timestamp(instant), DisplayValue.Missing)
    val originalfields = values.zipWithIndex.map { case (value, index) =>
      Field("field" + index, "Label " + index, if (index == 0) PresentationRole.Title else PresentationRole.Content, value)
    }
    val fields = if (reverse) originalfields.reverse else originalfields
    val fielddefinitions = fields.map(field => FieldDefinition(field.id, field.label, field.role,
      ValueDefinition("binding-" + field.id, if (field.role == PresentationRole.Title) Vector(ValueKind.Text) else ValueKind.values.toVector)))
    val actions = Vector(Action("a", "Action a", "binding-a", ActionAvailability.Hidden),
      Action("b", "Action b", "binding-b", ActionAvailability.Disabled), Action("c", "Action c", "binding-c", ActionAvailability.Enabled))
    val actiondefinitions = actions.map(action => ActionDefinition(action.id, action.label, action.bindingId))
    val title = if (optional) Some("Section title") else None
    val sections = Vector(Section("one", title, fields), Section("two", None, fields.reverse))
    val sectiondefinitions = Vector(SectionDefinition("one", title, fielddefinitions), SectionDefinition("two", None, fielddefinitions.reverse))
    val provenance = SourceProvenance("cncf.protocol-all-kind-fixture", "pure-values", "fixture-1")
    val definitions = success(GeneratedLogicalUiAbi.admitC(Vector(
      Definition(identity, version, semanticContract, provenance, "pure-list", "definition-1", "typed-source",
        ListDefinition("Pure List", fielddefinitions, DetailTarget("pure-detail"), actiondefinitions, actiondefinitions)),
      Definition(identity, version, semanticContract, provenance, "pure-detail", "definition-1", "typed-source",
        DetailDefinition("Pure Detail", sectiondefinitions, actiondefinitions)))))
    val resources = Vector.tabulate(size)(index => success(TypedDisplayProjectionFixture.resourceC(index + 1, revision)))
    val detailresource = success(TypedDisplayProjectionFixture.resourceC(1, revision))
    val orderedresources = if (reverse) resources.reverse else resources
    val list = ListModel("pure-list", "Pure List", orderedresources.map(resource => ListItem(resource, fields, actions)),
      DetailTarget("pure-detail"), actions, if (optional) Some(QuerySnapshot("query-snapshot")) else None)
    val detail = DetailModel("pure-detail", "Pure Detail", detailresource, sections, actions)
    Sample(definitions, Vector(success(Display.createC(definitions, list)), success(Display.createC(definitions, detail))))
  }
  def emptySample(): Sample = {
    val provenance = SourceProvenance("cncf.protocol-all-kind-fixture", "empty-values", "fixture-1")
    val definitions = success(GeneratedLogicalUiAbi.admitC(Vector(
      Definition(identity, version, semanticContract, provenance, "pure-list", "definition-1", "typed-source",
        ListDefinition("Pure List", Vector.empty, DetailTarget("pure-detail"), Vector.empty, Vector.empty)),
      Definition(identity, version, semanticContract, provenance, "pure-detail", "definition-1", "typed-source",
        DetailDefinition("Pure Detail", Vector.empty, Vector.empty)))))
    Sample(definitions, Vector(success(Display.createC(definitions,
      ListModel("pure-list", "Pure List", Vector.empty, DetailTarget("pure-detail"), Vector.empty, None))),
      success(Display.createC(definitions, DetailModel("pure-detail", "Pure Detail",
        success(TypedDisplayProjectionFixture.resourceC(1, 1)), Vector.empty, Vector.empty)))))
  }
  def roundTrip(fixture: Sample, display: Display): Display =
    success(DisplayProtocolCodec.decodeC(source, fixture.definitions, success(DisplayProtocolCodec.encodeC(source, display))))
  def checkDisplay(actual: Display, expected: Display): Unit = {
    actual.model shouldBe expected.model
    (actual.definition eq expected.definition) shouldBe true
    def _fields_(model: DisplayModel): Vector[Field] = model match {
      case value: ListModel => value.items.flatMap(_.fields)
      case value: DetailModel => value.sections.flatMap(_.fields)
    }
    _fields_(actual.model).zip(_fields_(expected.model)).foreach { case (decoded, original) =>
      (decoded.value, original.value) match {
        case (DisplayValue.Text(a), DisplayValue.Text(b)) => a.toCharArray.toVector shouldBe b.toCharArray.toVector
        case (DisplayValue.Number(a), DisplayValue.Number(b)) =>
          a.bigDecimal.unscaledValue shouldBe b.bigDecimal.unscaledValue
          a.bigDecimal.scale shouldBe b.bigDecimal.scale
        case (DisplayValue.Timestamp(a), DisplayValue.Timestamp(b)) =>
          a.getEpochSecond shouldBe b.getEpochSecond
          a.getNano shouldBe b.getNano
        case (a, b) => a shouldBe b
      }
    }
  }
  def checkFailure(result: Consequence[?], candidate: InvalidCase): Unit = {
    val observed = diagnostic(result)
    observed.code shouldBe "DSP96_WIRE_" + candidate.code
    observed.path shouldBe candidate.path
    failure(result).conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    val carrier = failure(result).conclusion.observation.cause.getException.get
    carrier.getMessage should not include "SECRET"
    carrier.getCause shouldBe null
  }
  def json(text: String): Json = io.circe.parser.parse(text).fold(_ => org.scalatest.Assertions.fail("expected valid candidate JSON"), value => value)
  def alter(value: Json, path: Vector[String], change: Json => Json): Json = {
    if (path.isEmpty) change(value)
    else value.asObject match {
      case Some(obj) => Json.fromJsonObject(obj.add(path.head, alter(obj(path.head).get, path.tail, change)))
      case None => Json.fromValues(value.asArray.get.zipWithIndex.map { case (entry, index) =>
        if (index == path.head.toInt) alter(entry, path.tail, change) else entry
      })
    }
  }
  def candidate(base: Json, path: String, replacement: Json, code: String): InvalidCase =
    InvalidCase(alter(base, path.split("/").toVector, _ => replacement).noSpaces, code, wirePath(path))
  def wirePath(path: String): String = path.split("/").foldLeft("$") { (acc, part) =>
    if (part.forall(_.isDigit)) acc + "[" + part + "]" else acc + "." + part
  }
  def base(fixture: Sample, detail: Boolean = false): Json =
    json(success(DisplayProtocolCodec.encodeC(source, fixture.displays(if (detail) 1 else 0))))
  def compatibilityCases(fixture: Sample): Vector[InvalidCase] = {
    val value = base(fixture)
    val protocols = Vector("schema", "version").map(path => candidate(value, path,
      if (path == "version") Json.fromInt(2) else Json.fromString("SECRET-foreign"), "PROTOCOL_INCOMPATIBLE"))
    val abi = Vector("abi/identity", "abi/version").map(path => candidate(value, path,
      if (path.endsWith("version")) Json.fromInt(2) else Json.fromString("SECRET-foreign"), "ABI_INCOMPATIBLE"))
    val contract = Vector("schema", "version", "semanticAuthoritySchema", "assurance", "driver/repository", "driver/revision", "driver/path", "driver/scenarioId")
      .map(path => candidate(value, "semanticContract/" + path,
        if (path == "version") Json.fromInt(2) else Json.fromString("SECRET-foreign"), "CONTRACT_INCOMPATIBLE"))
    protocols ++ abi ++ contract
  }
  def identityCases(fixture: Sample): Vector[InvalidCase] = {
    val value = base(fixture)
    Vector("componentId", "resourceType").map(path => candidate(value, path, Json.fromString("SECRET-foreign"), "SOURCE_MISMATCH")) ++
      Vector("definition/screenId", "definition/revision", "provenance/producerId", "provenance/sourceId", "provenance/revision", "model/screenId")
        .map(path => candidate(value, path, Json.fromString("SECRET-foreign"), "DEFINITION_MISMATCH")) ++
      Vector(("model/type", "MODEL_KIND_INVALID"), ("model/items/0/fields/1/value/type", "VALUE_KIND_INVALID"),
        ("model/items/0/fields/1/role", "ROLE_INVALID"), ("model/actions/0/availability", "AVAILABILITY_INVALID"))
        .map { case (path, code) => candidate(value, path, Json.fromString("SECRET-future"), code) }
  }
  def resourceCases(fixture: Sample): Vector[InvalidCase] = {
    val value = base(fixture)
    val path = "model/items/0/resource/"
    val identity = value.hcursor.downField("model").downField("items").downN(0).downField("resource").get[String]("entityId").toOption.get
    val missing = InvalidCase(alter(value, Vector("model", "items", "0", "resource"), current =>
      Json.fromJsonObject(current.asObject.get.remove("revision"))).noSpaces, "MEMBER_INVALID", "$.model.items[0].resource")
    Vector(missing, candidate(value, path + "revision", Json.Null, "MEMBER_INVALID")) ++
      Vector("", "SECRET-malformed", "capture:book-1", " " + identity, identity + " ")
        .map(text => candidate(value, path + "entityId", Json.fromString(text), "RESOURCE_INVALID")) ++
      Vector("", "0", "-1", "9223372036854775808", "01", "+1", " 1", "1.0", "1E+1")
        .map(text => candidate(value, path + "revision", Json.fromString(text), "RESOURCE_INVALID")) ++
      Vector(candidate(value, path + "revision", Json.fromInt(1), "MEMBER_INVALID"))
  }
  def scalarCases(fixture: Sample): Vector[InvalidCase] = {
    val value = base(fixture)
    Vector("+1", "01", "1.0e0", "1E0", "1E+0", "-0", "1E+2147483649", "1E-2147483648", "SECRET", " NaN ")
      .map(text => candidate(value, "model/items/0/fields/2/value/number", Json.fromString(text), "VALUE_INVALID")) ++
      Vector("2026-10-04T00:00:00+00:00", "2026-10-04T00:00:00.000Z", "SECRET")
        .map(text => candidate(value, "model/items/0/fields/4/value/instant", Json.fromString(text), "VALUE_INVALID"))
  }
  def shapeCases(fixture: Sample): Vector[InvalidCase] = {
    def _walk_(original: Json, value: Json, path: Vector[String]): Vector[InvalidCase] = value.asObject match {
      case Some(obj) =>
        val objectpath = if (path.isEmpty) "$" else wirePath(path.mkString("/"))
        val extra = InvalidCase(alter(original, path, current => Json.fromJsonObject(current.asObject.get.add("SECRET-unknown", Json.True))).noSpaces,
          "MEMBER_INVALID", objectpath)
        Vector(extra) ++ obj.toVector.flatMap { case (name, member) =>
          val memberpath = path :+ name
          val location = wirePath(memberpath.mkString("/"))
          val optional = (name == "snapshot" && path == Vector("model")) || (name == "title" && path.contains("sections"))
          val missing = if (optional) Vector.empty else Vector(InvalidCase(alter(original, path,
            current => Json.fromJsonObject(current.asObject.get.remove(name))).noSpaces, "MEMBER_INVALID", objectpath))
          val wrong = if (member.isString) Json.True else Json.fromString("SECRET-wrong-primitive")
          missing ++ Vector(InvalidCase(alter(original, memberpath, _ => Json.Null).noSpaces, "MEMBER_INVALID", location),
            InvalidCase(alter(original, memberpath, _ => wrong).noSpaces, "MEMBER_INVALID", location)) ++ _walk_(original, member, memberpath)
        }
      case None => value.asArray.map { values => values.zipWithIndex.flatMap { case (entry, index) =>
        val entrypath = path :+ index.toString
        Vector(InvalidCase(alter(original, entrypath, _ => Json.Null).noSpaces, "MEMBER_INVALID", wirePath(entrypath.mkString("/")))) ++
          _walk_(original, entry, entrypath)
      } }.getOrElse(Vector.empty)
    }
    val listbase = base(fixture)
    val detailbase = base(fixture, true)
    val listcases = _walk_(listbase, listbase, Vector.empty)
    val detailcases = _walk_(detailbase, detailbase, Vector.empty)
    val malformed = Vector("{", "[]", "null", "true", "\"SECRET\"", "{\"schema\":1,\"schema\":2}",
      "{\"nested\":{\"x\":1,\"x\":2}}", "{\"nested\":[{\"x\":1,\"x\":2}]}")
      .map(text => InvalidCase(text, "JSON_INVALID", "$"))
    malformed ++ listcases ++ detailcases
  }
  def replaceModel(value: Json, model: ListModel): Json = {
    val old = value.hcursor.downField("model").focus.get
    val items = model.items.map { item =>
      Json.obj("resource" -> Json.obj("entityId" -> Json.fromString(item.resource.entityId.value), "revision" -> Json.fromString(item.resource.revision.value.toString)),
        "fields" -> Json.fromValues(item.fields.map { field =>
          val payload = field.value match {
            case DisplayValue.Text(text) => Json.obj("type" -> Json.fromString("text"), "text" -> Json.fromString(text))
            case DisplayValue.Number(number) => Json.obj("type" -> Json.fromString("number"), "number" -> Json.fromString(number.bigDecimal.toString))
            case other => old.hcursor.downField("items").downN(0).downField("fields").downN(item.fields.indexOf(field)).downField("value").focus.get
          }
          Json.obj("id" -> Json.fromString(field.id), "label" -> Json.fromString(field.label), "role" -> Json.fromString(field.role.tag), "value" -> payload)
        }), "actions" -> old.hcursor.downField("items").downN(0).downField("actions").focus.get)
    }
    alter(value, Vector("model"), current => Json.fromJsonObject(current.asObject.get
      .add("title", Json.fromString(model.title)).add("items", Json.fromValues(items))))
  }
  def reorder(value: Json): Json = value.asObject match {
    case Some(obj) => Json.fromFields(obj.toVector.reverse.map { case (name, member) => name -> reorder(member) })
    case None => value.asArray.map(values => Json.fromValues(values.map(reorder))).getOrElse(value)
  }
}
