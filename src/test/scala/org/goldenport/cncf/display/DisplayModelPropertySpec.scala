package org.goldenport.cncf.display

import java.time.Instant
import logicalui.ExpectedGeneratedLogicalUiFixture
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class DisplayModelPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*

  private val _definitions = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _text = for {
    size <- Gen.choose(0, 40)
    chars <- Gen.listOfN(size, Gen.oneOf('a', 'Z', '0', 'Ω', 'é', '日', '本', '語', ' ', '\t', '\n', '\r', '\u0301', '\u2003'))
  } yield chars.mkString
  private val _opaque = for {
    size <- Gen.choose(0, 20)
    chars <- Gen.listOfN(size, Gen.oneOf('a', 'Z', '0', 'Ω', 'é', '日', ':', '/', '.', ' ', '\u0301'))
  } yield "opaque:" + chars.mkString + ":end"
  private val _decimal = for {
    coefficient <- Gen.choose(Long.MinValue, Long.MaxValue)
    scale <- Gen.choose(-20, 50)
  } yield BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(coefficient), scale))
  private val _instant = for {
    seconds <- Gen.choose(-100000000000L, 100000000000L)
    nanos <- Gen.choose(0, 999999999)
  } yield Instant.ofEpochSecond(seconds, nanos.toLong)
  private val _value: Gen[DisplayValue] = Gen.oneOf(
    _text.map(text => DisplayValue.Text(text): DisplayValue),
    _decimal.map(number => DisplayValue.Number(number): DisplayValue),
    Gen.oneOf(true, false).map(value => DisplayValue.Boolean(value): DisplayValue),
    _instant.map(timestamp => DisplayValue.Timestamp(timestamp): DisplayValue),
    Gen.const(DisplayValue.Missing: DisplayValue))

  private def _admit(definitions: Vector[Definition]): AdmittedDefinitions = {
    GeneratedLogicalUiAbi.admitC(definitions) match {
      case Consequence.Success(admitted) => admitted
      case _ => fail("property fixture definitions must be admitted normally")
    }
  }
  private def _resource(index: Int, revision: Long): Resource = {
    val identity = EntityId.restore("fixture", "display", EntityCollectionId("fixture", "display", "Capture"),
      Instant.ofEpochMilli(1800000000000L), "resource_" + index) match {
      case Consequence.Success(value) => value
      case _ => fail("deterministically supplied fixture identity must restore")
    }
    val managed = EntityRevision.createC(revision) match {
      case Consequence.Success(value) => value
      case _ => fail("positive generated revision must be admitted")
    }
    Resource(identity, managed)
  }
  private def _field(id: String, kinds: Vector[ValueKind]): FieldDefinition = {
    FieldDefinition(id, "  Label Ω  ", PresentationRole.Content, ValueDefinition("shared:binding", kinds))
  }
  private def _fields(declared: Vector[FieldDefinition]): Vector[Field] = {
    declared.map(field => Field(field.id, field.label, field.role, DisplayValue.Text("  value Ω  ")))
  }
  private def _action(id: String): ActionDefinition = {
    ActionDefinition(id, "  Action Ω  ", "shared:binding")
  }
  private def _actions(declared: Vector[ActionDefinition]): Vector[Action] = {
    declared.zipWithIndex.map { case (action, index) =>
      Action(action.id, action.label, action.bindingId, ActionAvailability.values(index % 3))
    }
  }
  private def _detail_definition(fields: Vector[FieldDefinition]): Definition = {
    _definitions(1).copy(screen = DetailDefinition("Capture Detail", Vector(SectionDefinition("main", None, fields)), Vector.empty))
  }
  private def _detail(fields: Vector[Field]): DetailModel = {
    DetailModel("capture-detail", "Capture Detail", _resource(1, 1), Vector(Section("main", None, fields)), Vector.empty)
  }
  private def _list_definition(fields: Vector[FieldDefinition]): Definition = {
    _definitions.head.copy(screen = ListDefinition("Collecting Resources", fields, DetailTarget("capture-detail"), Vector.empty, Vector.empty))
  }
  private def _list(items: Vector[ListItem]): ListModel = {
    ListModel("collecting-resources", "Collecting Resources", items, DetailTarget("capture-detail"), Vector.empty, None)
  }
  private def _preserved(result: Consequence[Display], definition: Definition, input: DisplayModel): Boolean = {
    result match {
      case Consequence.Success(display) => (display.definition eq definition) && (display.model eq input) && display.model == input
      case _ => false
    }
  }
  private def _diagnostic(result: Consequence[Display]): Option[Display.Diagnostic] = {
    result match {
      case failure: Consequence.Failure[?] if failure.conclusion.observation.taxonomy == Taxonomy.argumentInvalid =>
        failure.conclusion.observation.cause.getException.collect { case carrier: Display.ValidationException => carrier.diagnostic }
      case _ => None
    }
  }
  private def _rejected(result: Consequence[Display], code: String, path: String): Boolean = {
    _diagnostic(result).exists(problem => problem.code == "DSP96_" + code && problem.path == path)
  }

  "E13 P1 exact runtime value preservation" should afterWord("in spec:display-model-runtime, example:E13, rules:R01,R02, phase:96") {
    "retain generated Unicode whitespace decimals instants booleans and Missing with original model identity" in {
      Given("docs/spec/display-model-runtime.md R01/R02 E13 P1: total bounded generators for all five kinds and empty Content containers")
      val geninput = for {
        size <- Gen.choose(0, 12)
        values <- Gen.listOfN(size, _value).map(_.toVector)
      } yield values
      When("each generated exact value vector is declared normally and constructed as a Display")
      val property = Prop.forAll(geninput) { values =>
        val fields = values.zipWithIndex.map { case (value, index) => _field("field:" + index, Vector(value.kind)) }
        val definition = _detail_definition(fields)
        val admitted = _admit(Vector(definition))
        val runtime = fields.zip(values).map { case (field, value) => Field(field.id, field.label, field.role, value) }
        val input = _detail(runtime)
        val result = Display.createC(admitted, input)
        _preserved(result, definition, input) && (result match {
          case Consequence.Success(display) =>
            val actual = display.model.asInstanceOf[DetailModel].sections.head.fields
            (actual eq runtime) && actual.map(_.value) == values &&
              actual.zip(values).forall { case (field, value) => field.value eq value }
          case _ => false
        })
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred generated cases preserve exact payloads and original candidate references")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }

  "E14 P2 joint declaration and runtime order" should afterWord("in spec:display-model-runtime, example:E14, rules:R01,R02,R03, phase:96") {
    "retain jointly permuted fields sections actions and unique resource items including empty vectors" in {
      Given("docs/spec/display-model-runtime.md R01/R02/R03 E14 P2: independently permuted bounded scoped identities and resource order")
      val geninput = for {
        token <- _opaque
        fieldcount <- Gen.choose(0, 6)
        sectioncount <- Gen.choose(0, 4)
        actioncount <- Gen.choose(0, 6)
        itemcount <- Gen.choose(0, 6)
        fieldorder <- Gen.pick(fieldcount, Vector.tabulate(fieldcount)(index => token + ":field:" + index)).map(_.toVector)
        sectionorder <- Gen.pick(sectioncount, Vector.tabulate(sectioncount)(index => token + ":section:" + index)).map(_.toVector)
        actionorder <- Gen.pick(actioncount, Vector.tabulate(actioncount)(index => token + ":action:" + index)).map(_.toVector)
        itemorder <- Gen.pick(itemcount, Vector.tabulate(itemcount)(index => index)).map(_.toVector)
        detailfirst <- Gen.oneOf(true, false)
      } yield (fieldorder, sectionorder, actionorder, itemorder, detailfirst)
      When("jointly reordered definitions and candidates are admitted and validated")
      val property = Prop.forAll(geninput) { case (fieldorder, sectionorder, actionorder, itemorder, detailfirst) =>
        val fields = fieldorder.map(id => _field(id, Vector(ValueKind.Text)))
        val actions = actionorder.map(_action)
        val sections = sectionorder.map(id => SectionDefinition(id, Some("  Section Ω  "), fields))
        val listdefinition = _list_definition(fields).copy(screen = ListDefinition("Collecting Resources", fields,
          DetailTarget("capture-detail"), actions, actions.reverse))
        val detaildefinition = _definitions(1).copy(screen = DetailDefinition("Capture Detail", sections, actions))
        val definitions = if (detailfirst) Vector(detaildefinition, listdefinition) else Vector(listdefinition, detaildefinition)
        val admitted = _admit(definitions)
        val list = _list(itemorder.map(index => ListItem(_resource(index + 1, 1), _fields(fields), _actions(actions.reverse))))
          .copy(actions = _actions(actions))
        val detail = _detail(Vector.empty).copy(sections = sections.map(section => Section(section.id, section.title, _fields(section.fields))),
          actions = _actions(actions))
        val listresult = Display.createC(admitted, list)
        val detailresult = Display.createC(admitted, detail)
        _preserved(listresult, listdefinition, list) && _preserved(detailresult, detaildefinition, detail) &&
          list.items.map(_.resource.entityId) == itemorder.map(index => _resource(index + 1, 1).entityId) &&
          list.items.forall(_.fields.map(_.id) == fieldorder) &&
          detail.sections.map(_.id) == sectionorder && detail.sections.forall(_.fields.map(_.id) == fieldorder) &&
          list.actions.map(_.id) == actionorder && list.items.forall(_.actions.map(_.id) == actionorder.reverse) &&
          detail.actions.map(_.id) == actionorder
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred cases retain every supplied order and container-local identity")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }

  "E15 P3 scoped reuse and duplicate rejection" should afterWord("in spec:display-model-runtime, example:E15, rules:R01,R02, phase:96") {
    "permit cross-container reuse and identify a second field action section or non-Content role occurrence" in {
      Given("docs/spec/display-model-runtime.md R01/R02 E15 P3: unique IDs reused between scopes and generated duplicate scopes")
      val geninput = for {
        token <- _opaque
        count <- Gen.choose(2, 6)
        previous <- Gen.choose(0, count - 1)
        scope <- Gen.oneOf("detailfields", "listfields", "sections", "detailactions", "listactions", "itemactions", "detailroles", "listroles")
        role <- Gen.oneOf(PresentationRole.Title, PresentationRole.Subtitle, PresentationRole.Description, PresentationRole.Status)
      } yield (token, count, previous, scope, role)
      When("valid reuse and one generated within-container duplicate are validated repeatedly")
      val property = Prop.forAll(geninput) { case (token, count, previous, scope, role) =>
        val ids = Vector.tabulate(count)(index => token + ":" + index)
        val fields = ids.map(id => _field(id, Vector(ValueKind.Text)))
        val actions = ids.map(_action)
        val sections = ids.map(id => SectionDefinition(id, None, fields))
        val listdefinition = _list_definition(fields).copy(screen = ListDefinition("Collecting Resources", fields,
          DetailTarget("capture-detail"), actions, actions))
        val detaildefinition = _definitions(1).copy(screen = DetailDefinition("Capture Detail", sections, actions))
        val admitted = _admit(Vector(listdefinition, detaildefinition))
        val detail = _detail(Vector.empty).copy(sections = sections.map(section => Section(section.id, section.title, _fields(section.fields))), actions = _actions(actions))
        val list = _list(Vector.tabulate(2)(index => ListItem(_resource(index + 1, 1), _fields(fields), _actions(actions)))).copy(actions = _actions(actions))
        val section = detail.sections.head
        val item = list.items.head
        val rolefields = section.fields.updated(previous, section.fields(previous).copy(role = role)) :+
          section.fields.head.copy(id = "additional:role", role = role)
        val (invalid, code, path): (DisplayModel, String, String) = scope match {
          case "detailfields" => (detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields :+ section.fields(previous)))), "DUPLICATE_ID", s"model.sections[0].fields[$count].id")
          case "listfields" => (list.copy(items = list.items.updated(0, item.copy(fields = item.fields :+ item.fields(previous)))), "DUPLICATE_ID", s"model.items[0].fields[$count].id")
          case "sections" => (detail.copy(sections = detail.sections :+ detail.sections(previous)), "DUPLICATE_ID", s"model.sections[$count].id")
          case "detailactions" => (detail.copy(actions = detail.actions :+ detail.actions(previous)), "DUPLICATE_ID", s"model.actions[$count].id")
          case "listactions" => (list.copy(actions = list.actions :+ list.actions(previous)), "DUPLICATE_ID", s"model.actions[$count].id")
          case "itemactions" => (list.copy(items = list.items.updated(0, item.copy(actions = item.actions :+ item.actions(previous)))), "DUPLICATE_ID", s"model.items[0].actions[$count].id")
          case "detailroles" => (detail.copy(sections = detail.sections.updated(0, section.copy(fields = rolefields))), "DUPLICATE_ROLE", s"model.sections[0].fields[$count].role")
          case _ => (list.copy(items = list.items.updated(0, item.copy(fields = rolefields))), "DUPLICATE_ROLE", s"model.items[0].fields[$count].role")
        }
        val first = Display.createC(admitted, invalid)
        val second = Display.createC(admitted, invalid)
        _preserved(Display.createC(admitted, list), listdefinition, list) &&
          _preserved(Display.createC(admitted, detail), detaildefinition, detail) &&
          _rejected(first, code, path) && _diagnostic(first) == _diagnostic(second)
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred cases permit reuse and return stable second-occurrence code/path in the selected scope")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }

  "E16 P4 typed resource identity" should afterWord("in spec:display-model-runtime, example:E16, rules:R03, phase:96") {
    "accept unique typed IDs and reject a repeated ID with a different positive revision without input changes" in {
      Given("docs/spec/display-model-runtime.md R03 E16 P4: total unique-resource generator including empty Lists and independent positive revisions")
      val geninput = for {
        size <- Gen.choose(0, 6)
        offset <- Gen.choose(0, 100000)
        revision <- Gen.choose(1L, Long.MaxValue - 1000000L)
        increment <- Gen.choose(1L, 1000000L)
      } yield (size, offset, revision, increment)
      When("each unique List and a two-item duplicate List are constructed repeatedly")
      val property = Prop.forAll(geninput) { case (size, offset, revision, increment) =>
        val listdefinition = _list_definition(Vector.empty)
        val detaildefinition = _definitions(1).copy(screen = DetailDefinition("Capture Detail", Vector.empty, Vector.empty))
        val admitted = _admit(Vector(listdefinition, detaildefinition))
        val items = Vector.tabulate(size)(index => ListItem(_resource(offset + index, revision), Vector.empty, Vector.empty))
        val unique = _list(items)
        val one = _resource(offset, revision)
        val two = _resource(offset, revision + increment)
        val duplicateitems = Vector(ListItem(one, Vector.empty, Vector.empty), ListItem(two, Vector.empty, Vector.empty))
        val duplicate = _list(duplicateitems)
        val first = Display.createC(admitted, duplicate)
        val second = Display.createC(admitted, duplicate)
        _preserved(Display.createC(admitted, unique), listdefinition, unique) &&
          _rejected(first, "DUPLICATE_RESOURCE", "model.items[1].resource.entityId") && _diagnostic(first) == _diagnostic(second) &&
          (unique.items eq items) && (duplicate.items eq duplicateitems) &&
          duplicate.items.map(_.resource.revision.value) == Vector(revision, revision + increment) &&
          duplicate.items.head.resource.entityId == duplicate.items(1).resource.entityId &&
          duplicate.items.head.resource.revision != duplicate.items(1).resource.revision
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred cases distinguish typed identity from revision and preserve both successful and failed inputs")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }

  "E17 P5 independent revision and snapshot spaces" should afterWord("in spec:display-model-runtime, example:E17, rules:R03, phase:96") {
    "retain independently varied source definition resource revisions and query correlation without substitution" in {
      Given("docs/spec/display-model-runtime.md R03 E17 P5: independent opaque source/definition/snapshot generators and positive EntityRevision")
      val geninput = for {
        source <- _opaque
        definitionrevision <- _opaque
        snapshot <- _opaque
        revision <- Gen.choose(1L, Long.MaxValue)
      } yield ("source:" + source, "definition:" + definitionrevision, "snapshot:" + snapshot, revision)
      When("the admitted static provenance and runtime resource/query candidate are validated together")
      val property = Prop.forAll(geninput) { case (source, definitionrevision, snapshot, revision) =>
        val listdefinition = _list_definition(Vector.empty).copy(definitionRevision = definitionrevision,
          provenance = _definitions.head.provenance.copy(revision = source))
        val detaildefinition = _definitions(1).copy(screen = DetailDefinition("Capture Detail", Vector.empty, Vector.empty))
        val admitted = _admit(Vector(listdefinition, detaildefinition))
        val resource = _resource(1, revision)
        val correlation = QuerySnapshot(snapshot)
        val model = _list(Vector(ListItem(resource, Vector.empty, Vector.empty))).copy(snapshot = Some(correlation))
        val result = Display.createC(admitted, model)
        _preserved(result, listdefinition, model) && (result match {
          case Consequence.Success(display) =>
            val actual = display.model.asInstanceOf[ListModel]
            display.definition.abiIdentity == "cncf.logical-ui-generated.v1" && display.definition.abiVersion == 1 &&
              display.definition.semanticContract == GeneratedLogicalUiAbi.semanticContract &&
              display.definition.provenance.revision == source && display.definition.definitionRevision == definitionrevision &&
              (actual.items.head.resource eq resource) && (actual.items.head.resource.revision eq resource.revision) &&
              actual.items.head.resource.revision.value == revision && actual.snapshot.exists(_ eq correlation) &&
              actual.snapshot.map(_.reference).contains(snapshot)
          case _ => false
        })
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred cases preserve distinct identity/version/revision/correlation members verbatim")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }

  "E18 P6 deterministic kind role and Title rejection" should afterWord("in spec:display-model-runtime, example:E18, rules:R02, phase:96") {
    "reject generated incompatible kinds roles and blank Title without normalization or omission" in {
      Given("docs/spec/display-model-runtime.md R02 E18 P6: generated kind/role mismatches, Missing and whitespace-only Title values")
      val geninput = for {
        mode <- Gen.choose(0, 3)
        role <- Gen.oneOf(PresentationRole.Title, PresentationRole.Subtitle, PresentationRole.Description, PresentationRole.Status)
        invalid <- Gen.oneOf[DisplayValue](DisplayValue.Number(BigDecimal(3)), DisplayValue.Boolean(false),
          DisplayValue.Timestamp(Instant.ofEpochSecond(3, 123456789)), DisplayValue.Missing)
        size <- Gen.choose(0, 30)
        blank <- Gen.listOfN(size, Gen.oneOf(' ', '\t', '\n', '\r')).map(_.mkString)
      } yield (mode, role, invalid, blank)
      When("each incompatible candidate reaches the factory twice against normally admitted definitions")
      val property = Prop.forAll(geninput) { case (mode, role, invalid, blank) =>
        val base = _field("field", Vector(ValueKind.Text))
        val (declared, runtimerole, value, code, path) = mode match {
          case 0 => (base.copy(role = role), role, invalid, "VALUE_KIND_INVALID", "model.sections[0].fields[0].value")
          case 1 => (base.copy(role = PresentationRole.Title), PresentationRole.Title, DisplayValue.Text(blank): DisplayValue,
            "ROLE_INVALID", "model.sections[0].fields[0].value")
          case 2 => (base, role, DisplayValue.Text("  unchanged Ω  "): DisplayValue,
            "DEFINITION_MISMATCH", "model.sections[0].fields[0].role")
          case _ => (base, PresentationRole.Content, DisplayValue.Missing: DisplayValue,
            "VALUE_KIND_INVALID", "model.sections[0].fields[0].value")
        }
        val definition = _detail_definition(Vector(declared))
        val admitted = _admit(Vector(definition))
        val field = Field(declared.id, declared.label, runtimerole, value)
        val fields = Vector(field)
        val model = _detail(fields)
        val first = Display.createC(admitted, model)
        val second = Display.createC(admitted, model)
        _rejected(first, code, path) && _diagnostic(first) == _diagnostic(second) &&
          (model.sections.head.fields eq fields) && (model.sections.head.fields.head eq field) &&
          (model.sections.head.fields.head.value eq value) && model.sections.head.fields.size == 1 &&
          (value match {
            case DisplayValue.Text(text) if mode == 1 => text == blank
            case _ => true
          })
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred failures retain their original failed field and return equal typed diagnostics on repetition")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }
  }
}
