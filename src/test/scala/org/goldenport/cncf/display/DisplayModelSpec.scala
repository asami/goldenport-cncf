package org.goldenport.cncf.display {

import java.time.Instant
import logicalui.ExpectedGeneratedLogicalUiFixture
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class DisplayModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*

  private val _definitions = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _list_definition = _definitions.head.screen.asInstanceOf[ListDefinition]
  private val _detail_definition = _definitions(1).screen.asInstanceOf[DetailDefinition]
  private final case class InvalidCase(model: DisplayModel, code: String, path: String)

  private def _admit(definitions: Vector[Definition]): AdmittedDefinitions = {
    GeneratedLogicalUiAbi.admitC(definitions) match {
      case Consequence.Success(admitted) => admitted
      case _ => fail("specification definitions must be admitted normally")
    }
  }
  private def _resource(index: Int, revision: Long): Resource = {
    val identity = EntityId.restore("fixture", "display", EntityCollectionId("fixture", "display", "Capture"),
      Instant.ofEpochMilli(1800000000000L), "resource_" + index) match {
      case Consequence.Success(value) => value
      case _ => fail("explicit fixture identity must restore successfully")
    }
    val managed = EntityRevision.createC(revision) match {
      case Consequence.Success(value) => value
      case _ => fail("positive fixture revision must be admitted")
    }
    Resource(identity, managed)
  }
  private def _fields(declared: Vector[FieldDefinition]): Vector[Field] = {
    declared.map(field => Field(field.id, field.label, field.role, DisplayValue.Text("  retained Ω  ")))
  }
  private def _actions(declared: Vector[ActionDefinition]): Vector[Action] = {
    declared.map(action => Action(action.id, action.label, action.bindingId, ActionAvailability.Enabled))
  }
  private def _detail: DetailModel = {
    DetailModel("capture-detail", _detail_definition.title, _resource(1, 7),
      _detail_definition.sections.map(section => Section(section.id, section.title, _fields(section.fields))),
      _actions(_detail_definition.actions))
  }
  private def _list: ListModel = {
    ListModel("collecting-resources", _list_definition.title,
      Vector.tabulate(2)(index => ListItem(_resource(index + 1, index + 2L),
        _fields(_list_definition.itemFields), _actions(_list_definition.itemActions))),
      _list_definition.detailTarget, _actions(_list_definition.actions), Some(QuerySnapshot("source-query:Ω-1")))
  }
  private def _content_definition(fields: Vector[FieldDefinition]): Vector[Definition] = {
    Vector(_definitions(1).copy(screen = DetailDefinition("Capture Detail",
      Vector(SectionDefinition("main", None, fields)), Vector.empty)))
  }
  private def _content_model(fields: Vector[Field]): DetailModel = {
    DetailModel("capture-detail", "Capture Detail", _resource(1, 7), Vector(Section("main", None, fields)), Vector.empty)
  }
  private def _content_field(id: String, kinds: Vector[ValueKind]): FieldDefinition = {
    FieldDefinition(id, "  Label Ω  ", PresentationRole.Content, ValueDefinition("binding:" + id, kinds))
  }
  private def _success(result: Consequence[Display]): Display = {
    result match {
      case Consequence.Success(display) => display
      case _ => fail("valid runtime model must produce Display")
    }
  }
  private def _diagnostic(result: Consequence[Display]): Display.Diagnostic = {
    result match {
      case failure: Consequence.Failure[?] =>
        failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
        failure.conclusion.observation.cause.getException match {
          case Some(carrier: Display.ValidationException) => carrier.diagnostic
          case _ => fail("structured validation failure must carry typed Diagnostic")
        }
      case _ => fail("invalid runtime model must fail without partial Display")
    }
  }
  private def _expect_cases(cases: Vector[InvalidCase], results: Vector[Consequence[Display]]): Unit = {
    results.zip(cases).foreach { case (result, expected) =>
      val diagnostic = _diagnostic(result)
      diagnostic.code shouldBe "DSP96_" + expected.code
      diagnostic.path shouldBe expected.path
      diagnostic.reason should not be empty
    }
  }

  "E1 ESS2-01 Detail preservation" should afterWord("in spec:display-model-runtime, example:E1, rules:R01,R02,R03, phase:96") {
    "retain the admitted definition and every supplied runtime object" in {
      Given("docs/spec/display-model-runtime.md R01/R02/R03 E1: admitted ESS2-01 and explicitly restored typed resource")
      val admitted = _admit(_definitions)
      val input = _detail
      val resource = input.resource
      val section = input.sections.head
      val field = section.fields.head
      When("the Detail candidate is constructed as a validated Display")
      val result = Display.createC(admitted, input)
      Then("the same definition, model, nested objects and exact whitespace survive")
      val display = _success(result)
      (display.definition eq _definitions(1)) shouldBe true
      (display.model eq input) shouldBe true
      val actual = display.model.asInstanceOf[DetailModel]
      (actual.resource eq resource) shouldBe true
      (actual.resource.entityId eq resource.entityId) shouldBe true
      (actual.resource.revision eq resource.revision) shouldBe true
      (actual.sections eq input.sections) shouldBe true
      (actual.sections.head eq section) shouldBe true
      (actual.sections.head.fields.head eq field) shouldBe true
      (actual.sections.head.fields.head.value eq field.value) shouldBe true
      actual.sections.head.fields.head.value shouldBe DisplayValue.Text("  retained Ω  ")
      actual.resource.revision.value shouldBe 7L
    }
  }

  "E2 ordered ESS2-01 List resources" should afterWord("in spec:display-model-runtime, example:E2, rules:R01,R03, phase:96") {
    "retain multiple typed resources and logical target with an independent query snapshot" in {
      Given("docs/spec/display-model-runtime.md R01/R03 E2: ordered distinct resources, target and opaque source query")
      val admitted = _admit(_definitions)
      val input = _list
      When("the List candidate reaches the pure construction boundary")
      val result = Display.createC(admitted, input)
      Then("the original item vector, resources, target and snapshot remain separate from definition/source/entity revisions")
      val display = _success(result)
      (display.definition eq _definitions.head) shouldBe true
      (display.model eq input) shouldBe true
      val actual = display.model.asInstanceOf[ListModel]
      (actual.items eq input.items) shouldBe true
      actual.items.zip(input.items).foreach { case (item, expected) =>
        (item eq expected) shouldBe true
        (item.resource.entityId eq expected.resource.entityId) shouldBe true
      }
      actual.items.map(_.resource.revision.value) shouldBe Vector(2L, 3L)
      (actual.detailTarget eq _list_definition.detailTarget) shouldBe true
      actual.detailTarget.screenId shouldBe "capture-detail"
      (actual.snapshot eq input.snapshot) shouldBe true
      actual.snapshot shouldBe Some(QuerySnapshot("source-query:Ω-1"))
      display.definition.definitionRevision shouldBe "ess-definition-1"
      display.definition.provenance.revision shouldBe "fixture-1"
      assertTypeError("""val item: org.goldenport.cncf.display.DisplayModel.ListItem = null; item.detail""")
    }
  }

  "E3 closed runtime values" should afterWord("in spec:display-model-runtime, example:E3, rules:R02, phase:96") {
    "retain Text Number Boolean Timestamp and explicit Missing without coercion" in {
      Given("docs/spec/display-model-runtime.md R02 E3: all declared Content kinds, precise decimal and nanosecond instant")
      val decimal = BigDecimal("12345678901234567890.123456789012345678901234567890")
      val instant = Instant.ofEpochSecond(1800000000L, 123456789L)
      val values = Vector[DisplayValue](DisplayValue.Text(" \t日本語 Ω \n"), DisplayValue.Number(decimal),
        DisplayValue.Boolean(true), DisplayValue.Timestamp(instant), DisplayValue.Missing)
      val declared = values.zipWithIndex.map { case (value, index) => _content_field("value:" + index, Vector(value.kind)) }
      val admitted = _admit(_content_definition(declared))
      val input = _content_model(declared.zip(values).map { case (field, value) => Field(field.id, field.label, field.role, value) })
      When("all five supplied runtime values are validated against their declared kinds")
      val result = Display.createC(admitted, input)
      Then("every value and reference payload is retained exactly, including typed Missing")
      val actual = _success(result).model.asInstanceOf[DetailModel]
      (actual eq input) shouldBe true
      actual.sections.head.fields.map(_.value) shouldBe values
      actual.sections.head.fields.map(_.value.kind) shouldBe ValueKind.values.toVector
      (actual.sections.head.fields(1).value.asInstanceOf[DisplayValue.Number].value eq decimal) shouldBe true
      (actual.sections.head.fields(3).value.asInstanceOf[DisplayValue.Timestamp].value eq instant) shouldBe true
      actual.sections.head.fields(4).value shouldBe DisplayValue.Missing
    }
  }

  "E4 empty declared containers" should afterWord("in spec:display-model-runtime, example:E4, rules:R01,R02,R03, phase:96") {
    "accept empty Lists sections fields and actions with optional titles and snapshots" in {
      Given("docs/spec/display-model-runtime.md R01/R02/R03 E4: empty declarations and None or exact optional section title")
      val sections = Vector(SectionDefinition("none", None, Vector.empty), SectionDefinition("some", Some("  Section Ω  "), Vector.empty))
      val definitions = Vector(
        _definitions.head.copy(screen = ListDefinition("Collecting Resources", Vector.empty, DetailTarget("capture-detail"), Vector.empty, Vector.empty)),
        _definitions(1).copy(screen = DetailDefinition("Capture Detail", sections, Vector.empty)))
      val admitted = _admit(definitions)
      val list = ListModel("collecting-resources", "Collecting Resources", Vector.empty, DetailTarget("capture-detail"), Vector.empty, None)
      val detail = DetailModel("capture-detail", "Capture Detail", _resource(1, 1), sections.map(section => Section(section.id, section.title, Vector.empty)), Vector.empty)
      val emptydefinitions = Vector(definitions(1).copy(screen = DetailDefinition("Capture Detail", Vector.empty, Vector.empty)))
      val emptyadmitted = _admit(emptydefinitions)
      val emptydetail = detail.copy(sections = Vector.empty)
      When("the empty List, declared empty field containers and empty Detail section vector are validated")
      val results = Vector(Display.createC(admitted, list), Display.createC(admitted, detail), Display.createC(emptyadmitted, emptydetail))
      Then("all actual empty vectors and optional title/snapshot shapes remain supplied values")
      results.zip(Vector[DisplayModel](list, detail, emptydetail)).foreach { case (result, input) =>
        (_success(result).model eq input) shouldBe true
      }
      (results.head.toOption.get.model.asInstanceOf[ListModel].items eq list.items) shouldBe true
      detail.sections.map(_.title) shouldBe Vector(None, Some("  Section Ω  "))
      list.snapshot shouldBe None
      detail.resource.revision shouldBe EntityRevision.INITIAL
    }
  }

  "E5 scoped identities and roles" should afterWord("in spec:display-model-runtime, example:E5, rules:R01,R02, phase:96") {
    "allow cross-container reuse and Content repetition while rejecting each local duplicate" in {
      Given("docs/spec/display-model-runtime.md R01/R02 E5: reused field/action IDs in separate containers and repeated Content")
      val fields = Vector(_content_field("shared", Vector(ValueKind.Text)), _content_field("other", Vector(ValueKind.Text)))
      val actions = Vector(ActionDefinition("shared", " Action ", "binding:shared"), ActionDefinition("other", " Other ", "binding:other"))
      val sections = Vector(SectionDefinition("one", None, fields), SectionDefinition("two", None, fields))
      val definitions = Vector(_definitions.head.copy(screen = _list_definition.copy(itemFields = fields, actions = actions, itemActions = actions)),
        _definitions(1).copy(screen = _detail_definition.copy(sections = sections, actions = actions)))
      val admitted = _admit(definitions)
      val list = _list.copy(items = Vector.tabulate(2)(index => ListItem(_resource(index + 1, 1), _fields(fields), _actions(actions))), actions = _actions(actions))
      val detail = _detail.copy(sections = sections.map(section => Section(section.id, section.title, _fields(section.fields))), actions = _actions(actions))
      val item = list.items.head
      val section = detail.sections.head
      val cases = Vector(
        InvalidCase(detail.copy(sections = detail.sections.updated(1, section)), "DUPLICATE_ID", "model.sections[1].id"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields :+ section.fields.head))), "DUPLICATE_ID", "model.sections[0].fields[2].id"),
        InvalidCase(list.copy(items = list.items.updated(0, item.copy(fields = item.fields :+ item.fields.head))), "DUPLICATE_ID", "model.items[0].fields[2].id"),
        InvalidCase(detail.copy(actions = detail.actions :+ detail.actions.head), "DUPLICATE_ID", "model.actions[2].id"),
        InvalidCase(list.copy(actions = list.actions :+ list.actions.head), "DUPLICATE_ID", "model.actions[2].id"),
        InvalidCase(list.copy(items = list.items.updated(0, item.copy(actions = item.actions :+ item.actions.head))), "DUPLICATE_ID", "model.items[0].actions[2].id")) ++
        PresentationRole.values.toVector.filter(_ != PresentationRole.Content).flatMap { role =>
          val duplicatefields = section.fields.map(_.copy(role = role))
          Vector(InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = duplicatefields))), "DUPLICATE_ROLE", "model.sections[0].fields[1].role"),
            InvalidCase(list.copy(items = list.items.updated(0, item.copy(fields = duplicatefields))), "DUPLICATE_ROLE", "model.items[0].fields[1].role"))
        }
      When("valid scoped reuse and independently invalid local scopes reach Display construction")
      val successes = Vector(Display.createC(admitted, list), Display.createC(admitted, detail))
      val failures = cases.map(testcase => Display.createC(admitted, testcase.model))
      Then("reuse succeeds unchanged and each duplicate fails at its second occurrence in its own scope")
      successes.zip(Vector[DisplayModel](list, detail)).foreach { case (result, input) => (_success(result).model eq input) shouldBe true }
      _expect_cases(cases, failures)
    }
  }

  "E6 nullable runtime boundaries" should afterWord("in spec:display-model-runtime, example:E6, rules:R02,R03, phase:96") {
    "return structured failures for every nullable candidate container Option text and payload" in {
      Given("docs/spec/display-model-runtime.md R02/R03 E6: deliberate null roots, nested elements, Option shapes and reference payloads")
      val admitted = _admit(_definitions)
      val detail = _detail
      val list = _list
      val section = detail.sections.head
      val field = section.fields.head
      val action = detail.actions.head
      val item = list.items.head
      val cases = Vector(
        InvalidCase(null, "ELEMENT_INVALID", "model"),
        InvalidCase(detail.copy(title = null), "ELEMENT_INVALID", "model.title"),
        InvalidCase(list.copy(title = null), "ELEMENT_INVALID", "model.title"),
        InvalidCase(detail.copy(resource = null), "ELEMENT_INVALID", "model.resource"),
        InvalidCase(detail.copy(resource = detail.resource.copy(entityId = null)), "RESOURCE_INVALID", "model.resource.entityId"),
        InvalidCase(detail.copy(resource = detail.resource.copy(revision = null)), "RESOURCE_INVALID", "model.resource.revision"),
        InvalidCase(detail.copy(sections = null), "ELEMENT_INVALID", "model.sections"),
        InvalidCase(detail.copy(sections = Vector(null)), "ELEMENT_INVALID", "model.sections[0]"),
        InvalidCase(detail.copy(sections = Vector(section.copy(title = null))), "ELEMENT_INVALID", "model.sections[0].title"),
        InvalidCase(detail.copy(sections = Vector(section.copy(title = Some(null)))), "ELEMENT_INVALID", "model.sections[0].title"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = null))), "ELEMENT_INVALID", "model.sections[0].fields"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = Vector(null)))), "ELEMENT_INVALID", "model.sections[0].fields[0]"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = section.fields.updated(0, field.copy(label = null))))), "ELEMENT_INVALID", "model.sections[0].fields[0].label"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = section.fields.updated(0, field.copy(role = null))))), "ROLE_INVALID", "model.sections[0].fields[0].role"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = section.fields.updated(0, field.copy(value = null))))), "ELEMENT_INVALID", "model.sections[0].fields[0].value"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = section.fields.updated(0, field.copy(value = DisplayValue.Text(null)))))), "ELEMENT_INVALID", "model.sections[0].fields[0].value"),
        InvalidCase(detail.copy(actions = null), "ELEMENT_INVALID", "model.actions"),
        InvalidCase(detail.copy(actions = Vector(null)), "ELEMENT_INVALID", "model.actions[0]"),
        InvalidCase(detail.copy(actions = Vector(action.copy(label = null))), "ELEMENT_INVALID", "model.actions[0].label"),
        InvalidCase(detail.copy(actions = Vector(action.copy(bindingId = null))), "ELEMENT_INVALID", "model.actions[0].bindingId"),
        InvalidCase(detail.copy(actions = Vector(action.copy(availability = null))), "ELEMENT_INVALID", "model.actions[0].availability"),
        InvalidCase(list.copy(detailTarget = null), "ELEMENT_INVALID", "model.detailTarget"),
        InvalidCase(list.copy(detailTarget = DetailTarget(null)), "ID_INVALID", "model.detailTarget.screenId"),
        InvalidCase(list.copy(snapshot = null), "ELEMENT_INVALID", "model.snapshot"),
        InvalidCase(list.copy(snapshot = Some(null)), "ELEMENT_INVALID", "model.snapshot"),
        InvalidCase(list.copy(snapshot = Some(QuerySnapshot(null))), "ID_INVALID", "model.snapshot.reference"),
        InvalidCase(list.copy(items = null), "ELEMENT_INVALID", "model.items"),
        InvalidCase(list.copy(items = Vector(null)), "ELEMENT_INVALID", "model.items[0]"),
        InvalidCase(list.copy(items = Vector(item.copy(resource = null))), "ELEMENT_INVALID", "model.items[0].resource"),
        InvalidCase(list.copy(items = Vector(item.copy(resource = item.resource.copy(entityId = null)))), "RESOURCE_INVALID", "model.items[0].resource.entityId"),
        InvalidCase(list.copy(items = Vector(item.copy(resource = item.resource.copy(revision = null)))), "RESOURCE_INVALID", "model.items[0].resource.revision"),
        InvalidCase(list.copy(items = Vector(item.copy(fields = null))), "ELEMENT_INVALID", "model.items[0].fields"),
        InvalidCase(list.copy(items = Vector(item.copy(fields = Vector(null)))), "ELEMENT_INVALID", "model.items[0].fields[0]"),
        InvalidCase(list.copy(items = Vector(item.copy(actions = null))), "ELEMENT_INVALID", "model.items[0].actions"),
        InvalidCase(list.copy(items = Vector(item.copy(actions = Vector(null)))), "ELEMENT_INVALID", "model.items[0].actions[0]"),
        InvalidCase(list.copy(actions = null), "ELEMENT_INVALID", "model.actions"),
        InvalidCase(list.copy(actions = Vector(null)), "ELEMENT_INVALID", "model.actions[0]"))
      val payloads = Vector[DisplayValue](DisplayValue.Text(null), DisplayValue.Number(null), DisplayValue.Timestamp(null))
      val content = _content_field("content", ValueKind.values.toVector)
      val contentadmitted = _admit(_content_definition(Vector(content)))
      val payloadcases = payloads.map(value => InvalidCase(_content_model(Vector(Field(content.id, content.label, content.role, value))), "ELEMENT_INVALID", "model.sections[0].fields[0].value"))
      When("each nullable input and the null admitted-definition wrapper is submitted independently")
      val rootfailure = Display.createC(null, null)
      val failures = cases.map(testcase => Display.createC(admitted, testcase.model))
      val payloadfailures = payloadcases.map(testcase => Display.createC(contentadmitted, testcase.model))
      Then("no boundary throws and null precedence uses its exact code and member path")
      _diagnostic(rootfailure).code shouldBe "DSP96_ELEMENT_INVALID"
      _diagnostic(rootfailure).path shouldBe "definitions"
      _expect_cases(cases, failures)
      _expect_cases(payloadcases, payloadfailures)
    }
  }

  "E7 exact declaration correspondence" should afterWord("in spec:display-model-runtime, example:E7, rules:R01,R02,R03, phase:96") {
    "reject malformed and missing screens and every structural text role or binding mismatch" in {
      Given("docs/spec/display-model-runtime.md R01/R02/R03 E7: runtime shapes differing from admitted screen members")
      val fielddefinitions = Vector(_content_field("first", Vector(ValueKind.Text)), _content_field("second", Vector(ValueKind.Text)))
      val sectiondefinitions = Vector(SectionDefinition("first", None, fielddefinitions), SectionDefinition("second", Some(" Section "), fielddefinitions))
      val actiondefinitions = Vector(ActionDefinition("first", " First ", "binding:first"), ActionDefinition("second", " Second ", "binding:second"))
      val definitions = Vector(_definitions.head.copy(screen = _list_definition.copy(itemFields = fielddefinitions, actions = actiondefinitions, itemActions = actiondefinitions)),
        _definitions(1).copy(screen = _detail_definition.copy(sections = sectiondefinitions, actions = actiondefinitions)))
      val admitted = _admit(definitions)
      val detail = _detail.copy(sections = sectiondefinitions.map(section => Section(section.id, section.title, _fields(section.fields))), actions = _actions(actiondefinitions))
      val list = _list.copy(items = Vector(ListItem(_resource(1, 2), _fields(fielddefinitions), _actions(actiondefinitions))), actions = _actions(actiondefinitions))
      val section = detail.sections.head
      val field = section.fields.head
      val item = list.items.head
      val action = detail.actions.head
      val cases = Vector(
        InvalidCase(detail.copy(screenId = "unknown:Ω"), "SCREEN_NOT_FOUND", "model.screenId"),
        InvalidCase(detail.copy(screenId = "collecting-resources"), "DEFINITION_MISMATCH", "model"),
        InvalidCase(list.copy(screenId = "capture-detail"), "DEFINITION_MISMATCH", "model"),
        InvalidCase(detail.copy(title = "Capture Detail "), "DEFINITION_MISMATCH", "model.title"),
        InvalidCase(list.copy(title = "collecting resources"), "DEFINITION_MISMATCH", "model.title"),
        InvalidCase(list.copy(detailTarget = DetailTarget("other-detail")), "DEFINITION_MISMATCH", "model.detailTarget"),
        InvalidCase(detail.copy(sections = Vector.empty), "DEFINITION_MISMATCH", "model.sections"),
        InvalidCase(detail.copy(sections = detail.sections :+ section.copy(id = "extra")), "DEFINITION_MISMATCH", "model.sections"),
        InvalidCase(detail.copy(sections = detail.sections.reverse), "DEFINITION_MISMATCH", "model.sections[0].id"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(id = "other"))), "DEFINITION_MISMATCH", "model.sections[0].id"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(title = Some("different")))), "DEFINITION_MISMATCH", "model.sections[0].title"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = Vector.empty))), "DEFINITION_MISMATCH", "model.sections[0].fields"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields :+ field.copy(id = "extra")))), "DEFINITION_MISMATCH", "model.sections[0].fields"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields.reverse))), "DEFINITION_MISMATCH", "model.sections[0].fields[0].id"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields.updated(0, field.copy(id = "other"))))), "DEFINITION_MISMATCH", "model.sections[0].fields[0].id"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields.updated(0, field.copy(label = "changed"))))), "DEFINITION_MISMATCH", "model.sections[0].fields[0].label"),
        InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields.updated(0, field.copy(role = PresentationRole.Status))))), "DEFINITION_MISMATCH", "model.sections[0].fields[0].role"),
        InvalidCase(detail.copy(actions = Vector.empty), "DEFINITION_MISMATCH", "model.actions"),
        InvalidCase(detail.copy(actions = detail.actions :+ action.copy(id = "extra")), "DEFINITION_MISMATCH", "model.actions"),
        InvalidCase(detail.copy(actions = detail.actions.reverse), "DEFINITION_MISMATCH", "model.actions[0].id"),
        InvalidCase(detail.copy(actions = detail.actions.updated(0, action.copy(id = "other"))), "DEFINITION_MISMATCH", "model.actions[0].id"),
        InvalidCase(detail.copy(actions = detail.actions.updated(0, action.copy(label = "changed"))), "DEFINITION_MISMATCH", "model.actions[0].label"),
        InvalidCase(detail.copy(actions = detail.actions.updated(0, action.copy(bindingId = "changed"))), "DEFINITION_MISMATCH", "model.actions[0].bindingId"),
        InvalidCase(list.copy(items = Vector(item.copy(fields = Vector.empty))), "DEFINITION_MISMATCH", "model.items[0].fields"),
        InvalidCase(list.copy(items = Vector(item.copy(fields = item.fields :+ item.fields.head.copy(id = "extra")))), "DEFINITION_MISMATCH", "model.items[0].fields"),
        InvalidCase(list.copy(items = Vector(item.copy(fields = item.fields.reverse))), "DEFINITION_MISMATCH", "model.items[0].fields[0].id"),
        InvalidCase(list.copy(items = Vector(item.copy(actions = Vector.empty))), "DEFINITION_MISMATCH", "model.items[0].actions"),
        InvalidCase(list.copy(items = Vector(item.copy(actions = item.actions.reverse))), "DEFINITION_MISMATCH", "model.items[0].actions[0].id"),
        InvalidCase(list.copy(actions = list.actions.reverse), "DEFINITION_MISMATCH", "model.actions[0].id")) ++
        Vector[String](null, "", " ", " padded", "padded ").flatMap { id => Vector(
          InvalidCase(detail.copy(screenId = id), "ID_INVALID", "model.screenId"),
          InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(id = id))), "ID_INVALID", "model.sections[0].id"),
          InvalidCase(detail.copy(sections = detail.sections.updated(0, section.copy(fields = section.fields.updated(0, field.copy(id = id))))), "ID_INVALID", "model.sections[0].fields[0].id"),
          InvalidCase(detail.copy(actions = detail.actions.updated(0, action.copy(id = id))), "ID_INVALID", "model.actions[0].id"),
          InvalidCase(list.copy(snapshot = Some(QuerySnapshot(id))), "ID_INVALID", "model.snapshot.reference")) }
      When("each missing extra reordered or mismatched candidate is validated")
      val failures = cases.map(testcase => Display.createC(admitted, testcase.model))
      Then("every mismatch fails at the exact scoped member and no candidate bypasses its declaration")
      _expect_cases(cases, failures)
    }
  }

  "E8 kind and role compatibility" should afterWord("in spec:display-model-runtime, example:E8, rules:R02, phase:96") {
    "reject undeclared kinds and blank Title while retaining empty or whitespace in other text roles" in {
      Given("docs/spec/display-model-runtime.md R02 E8: text-only non-Content declarations, explicit Missing and blank Title")
      val nontext = Vector[DisplayValue](DisplayValue.Number(BigDecimal(1)), DisplayValue.Boolean(false),
        DisplayValue.Timestamp(Instant.ofEpochSecond(1)), DisplayValue.Missing)
      val roles = PresentationRole.values.toVector.filter(_ != PresentationRole.Content)
      val cases = roles.flatMap { role =>
        val field = _content_field("role", Vector(ValueKind.Text)).copy(role = role)
        val admitted = _admit(_content_definition(Vector(field)))
        nontext.map(value => (admitted, InvalidCase(_content_model(Vector(Field(field.id, field.label, role, value))), "VALUE_KIND_INVALID", "model.sections[0].fields[0].value")))
      }
      val title = _content_field("title", Vector(ValueKind.Text)).copy(role = PresentationRole.Title)
      val titleadmitted = _admit(_content_definition(Vector(title)))
      val blankcases = Vector("", " \t\r\n ").map(text => InvalidCase(_content_model(Vector(Field(title.id, title.label, title.role, DisplayValue.Text(text)))), "ROLE_INVALID", "model.sections[0].fields[0].value"))
      val permitted = roles.filter(_ != PresentationRole.Title).flatMap { role =>
        val field = _content_field("role", Vector(ValueKind.Text)).copy(role = role)
        val admitted = _admit(_content_definition(Vector(field)))
        Vector("", " \t ").map(text => (admitted, _content_model(Vector(Field(field.id, field.label, role, DisplayValue.Text(text))))))
      }
      val content = _content_field("content", Vector(ValueKind.Text))
      val contentadmitted = _admit(_content_definition(Vector(content)))
      val missing = _content_model(Vector(Field(content.id, content.label, content.role, DisplayValue.Missing)))
      When("incompatible and permitted role values independently reach the factory")
      val failures = cases.map { case (admitted, testcase) => Display.createC(admitted, testcase.model) }
      val blankfailures = blankcases.map(testcase => Display.createC(titleadmitted, testcase.model))
      val successes = permitted.map { case (admitted, model) => Display.createC(admitted, model) }
      val missingfailure = Display.createC(contentadmitted, missing)
      Then("declared-kind checks precede role checks, blank Title fails, and permitted text remains untrimmed")
      _expect_cases(cases.map(_._2), failures)
      _expect_cases(blankcases, blankfailures)
      successes.zip(permitted).foreach { case (result, (_, model)) => (_success(result).model eq model) shouldBe true }
      _diagnostic(missingfailure).code shouldBe "DSP96_VALUE_KIND_INVALID"
      _diagnostic(missingfailure).path shouldBe "model.sections[0].fields[0].value"
    }
  }

  "E9 typed resource uniqueness" should afterWord("in spec:display-model-runtime, example:E9, rules:R03, phase:96") {
    "compare EntityId independently of revision and reject absent typed members" in {
      Given("docs/spec/display-model-runtime.md R03 E9: equal restored IDs with different positive revisions and distinct IDs with one revision")
      val admitted = _admit(_definitions)
      val list = _list
      val first = list.items.head
      val distinct = list.copy(items = Vector(first.copy(resource = _resource(1, 5)), first.copy(resource = _resource(2, 5))))
      val repeated = list.copy(items = Vector(first.copy(resource = _resource(1, 5)), first.copy(resource = _resource(1, 9))))
      val cases = Vector(InvalidCase(repeated, "DUPLICATE_RESOURCE", "model.items[1].resource.entityId"),
        InvalidCase(list.copy(items = Vector(first.copy(resource = first.resource.copy(entityId = null)))), "RESOURCE_INVALID", "model.items[0].resource.entityId"),
        InvalidCase(list.copy(items = Vector(first.copy(resource = first.resource.copy(revision = null)))), "RESOURCE_INVALID", "model.items[0].resource.revision"))
      When("distinct and repeated resources reach construction")
      val success = Display.createC(admitted, distinct)
      val failures = cases.map(testcase => Display.createC(admitted, testcase.model))
      Then("equal revisions do not collapse distinct IDs and changed revision cannot conceal a repeated identity")
      (_success(success).model eq distinct) shouldBe true
      _expect_cases(cases, failures)
      (repeated.items.head.resource.entityId eq repeated.items(1).resource.entityId) shouldBe false
      repeated.items.head.resource.entityId shouldBe repeated.items(1).resource.entityId
      repeated.items.map(_.resource.revision.value) shouldBe Vector(5L, 9L)
    }
  }

  "E10 inert action availability" should afterWord("in spec:display-model-runtime, example:E10, rules:R05,R08, phase:96") {
    "retain Hidden Disabled and Enabled in declared order in every action container" in {
      Given("docs/spec/display-model-runtime.md R05/R08 E10: inert declared bindings with all presentation availability states")
      val states = ActionAvailability.values.toVector
      val declared = states.zipWithIndex.map { case (_, index) => ActionDefinition("action:" + index, " Action " + index, "binding:" + index) }
      val actions = declared.zip(states).map { case (action, state) => Action(action.id, action.label, action.bindingId, state) }
      val definitions = Vector(_definitions.head.copy(screen = _list_definition.copy(actions = declared, itemActions = declared)),
        _definitions(1).copy(screen = _detail_definition.copy(actions = declared)))
      val admitted = _admit(definitions)
      val detail = _detail.copy(actions = actions)
      val list = _list.copy(actions = actions, items = _list.items.map(_.copy(actions = actions)))
      When("all availability states are validated as presentation descriptors")
      val results = Vector(Display.createC(admitted, detail), Display.createC(admitted, list))
      Then("all bindings and states survive identically without an executable or capability member")
      val actualdetail = _success(results.head).model.asInstanceOf[DetailModel]
      val actuallist = _success(results(1)).model.asInstanceOf[ListModel]
      (actualdetail.actions eq actions) shouldBe true
      (actuallist.actions eq actions) shouldBe true
      actuallist.items.foreach(item => (item.actions eq actions) shouldBe true)
      actualdetail.actions.map(_.availability) shouldBe Vector(ActionAvailability.Hidden, ActionAvailability.Disabled, ActionAvailability.Enabled)
      actualdetail.actions.map(_.bindingId) shouldBe declared.map(_.bindingId)
      assertTypeError("""val action: org.goldenport.cncf.display.DisplayModel.Action = null; action.execute()""")
      assertTypeError("""val action: org.goldenport.cncf.display.DisplayModel.Action = null; action.capability""")
    }
  }

  "E11 deterministic redacted first failure" should afterWord("in spec:display-model-runtime, example:E11, rules:R01,R02,R03, phase:96") {
    "stop before invalid later siblings and never include submitted text in diagnostics" in {
      Given("docs/spec/display-model-runtime.md R01/R02/R03 E11: simultaneous early mismatches and later null siblings with sensitive marker")
      val admitted = _admit(_definitions)
      val detail = _detail
      val list = _list
      val marker = "submitted-secret-marker-Ω"
      val section = detail.sections.head
      val cases = Vector(
        InvalidCase(detail.copy(screenId = " " + marker, sections = null, actions = null), "ID_INVALID", "model.screenId"),
        InvalidCase(detail.copy(screenId = marker, title = null, resource = null), "SCREEN_NOT_FOUND", "model.screenId"),
        InvalidCase(detail.copy(screenId = "collecting-resources", title = null), "DEFINITION_MISMATCH", "model"),
        InvalidCase(detail.copy(title = marker, resource = null, sections = null, actions = null), "DEFINITION_MISMATCH", "model.title"),
        InvalidCase(list.copy(detailTarget = null, snapshot = null, items = null, actions = null), "ELEMENT_INVALID", "model.detailTarget"),
        InvalidCase(list.copy(snapshot = Some(QuerySnapshot(" " + marker)), items = null, actions = null), "ID_INVALID", "model.snapshot.reference"),
        InvalidCase(detail.copy(resource = null, sections = null, actions = null), "ELEMENT_INVALID", "model.resource"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = null), null), actions = null), "ELEMENT_INVALID", "model.sections[1]"),
        InvalidCase(detail.copy(sections = Vector(section.copy(id = " " + marker, fields = null)), actions = null), "ID_INVALID", "model.sections[0].id"),
        InvalidCase(detail.copy(sections = Vector(section.copy(fields = section.fields.updated(0, section.fields.head.copy(label = marker)))), actions = null), "DEFINITION_MISMATCH", "model.sections[0].fields[0].label"),
        InvalidCase(list.copy(items = Vector(list.items.head.copy(resource = null), list.items(1).copy(fields = null)), actions = null), "ELEMENT_INVALID", "model.items[0].resource"))
      When("the same malformed candidates reach the factory twice")
      val first = cases.map(testcase => Display.createC(admitted, testcase.model))
      val second = cases.map(testcase => Display.createC(admitted, testcase.model))
      Then("each first code/path is stable, later members are not dereferenced and typed causes contain bounded structural reasons")
      _expect_cases(cases, first)
      first.zip(second).foreach { case (one, two) =>
        val diagnostic = _diagnostic(one)
        diagnostic shouldBe _diagnostic(two)
        diagnostic.reason.length should be <= 120
        diagnostic.reason should not include marker
        diagnostic.code should not include marker
        diagnostic.path should not include marker
        one match {
          case failure: Consequence.Failure[?] => failure.conclusion.observation.cause.getException.get.getMessage should not include marker
          case _ => fail("malformed candidate must not construct Display")
        }
      }
    }
  }

  "E12 public candidate and private Display construction" should afterWord("in spec:display-model-runtime, example:E12, rules:R01,R02, phase:96") {
    "permit external candidate and factory usage while preventing construction apply and copy bypasses" in {
      Given("docs/spec/display-model-runtime.md R01/R02 E12: a lexical external-package compile-time probe in this specification file")
      val admitted = _admit(_definitions)
      val input = _detail
      When("the external probe interprets public source types and the ordinary factory constructs a Display")
      logicalui.DisplayRuntimeTypeProbe.verifyTypeBoundary()
      val result = Display.createC(admitted, input)
      Then("candidate construction and the public factory are usable while unchecked Display construction and copy are unavailable")
      (_success(result).model eq input) shouldBe true
      assertTypeError("""import org.goldenport.cncf.display.*; new Display(null, null)""")
    }
  }
}
}

package logicalui {
  /** Test-only lexical external-package type probes; no runtime public API is added. */
  object DisplayRuntimeTypeProbe {
    def verifyTypeBoundary(): Unit = {
      org.scalatest.Assertions.assertCompiles("""
        import org.goldenport.cncf.display.*
        import org.goldenport.cncf.display.DisplayModel.*
        val candidate: DisplayModel = DetailModel("opaque", "Title", Resource(null, null), Vector.empty, Vector.empty)
        val result: org.goldenport.Consequence[Display] = Display.createC(null, candidate)
        val text: DisplayValue = DisplayValue.Text("value")
      """)
      org.scalatest.Assertions.assertTypeError("""import org.goldenport.cncf.display.*; new Display(null, null)""")
      org.scalatest.Assertions.assertTypeError("""import org.goldenport.cncf.display.*; Display(null, null)""")
      org.scalatest.Assertions.assertTypeError("""import org.goldenport.cncf.display.*; val display: Display = null; display.copy(model = null)""")
      org.scalatest.Assertions.assertTypeError("""import org.goldenport.cncf.display.*; Display.createUnchecked(null, null)""")
      org.scalatest.Assertions.assertTypeError("""import org.goldenport.cncf.display.*; DisplayValue.Date(java.time.LocalDate.MIN)""")
    }
  }
}
