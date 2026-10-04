package org.goldenport.cncf.display

import logicalui.TypedDisplayProjectionFixture
import logicalui.TypedDisplayProjectionFixture.{Capture, CaptureStatus}
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class DisplayProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*
  import DisplayProjection.*

  private val _fixture = TypedDisplayProjectionFixture
  private val _definitions = _fixture.definitions
  private final case class RegistrationCase(row: RowBinding[Capture], code: String, path: String)
  private final case class ProjectionCase(row: RowBinding[Capture], code: String, path: String)

  private def _success[A](result: Consequence[A]): A = {
    result match {
      case Consequence.Success(value) => value
      case _ => fail("fixture interpretation must succeed")
    }
  }
  private def _admit(definitions: Vector[Definition]): AdmittedDefinitions = {
    _success(GeneratedLogicalUiAbi.admitC(definitions))
  }
  private def _capture(index: Int, revision: Long): Capture = {
    _success(_fixture.captureC(index, revision))
  }
  private def _detail(display: Display): DetailModel = {
    display.model match {
      case model: DetailModel => model
      case _ => fail("expected Detail model")
    }
  }
  private def _list(display: Display): ListModel = {
    display.model match {
      case model: ListModel => model
      case _ => fail("expected List model")
    }
  }
  private def _problem[A](result: Consequence[A]): DisplayProjection.Diagnostic = {
    result match {
      case failure: Consequence.Failure[?] =>
        failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
        failure.conclusion.observation.cause.getException match {
          case Some(carrier: ProjectionException) => carrier.diagnostic
          case _ => fail("expected typed projection diagnostic")
        }
      case _ => fail("expected failure without a partial projector or Display")
    }
  }
  private def _expect[A](result: Consequence[A], code: String, path: String): Unit = {
    val problem = _problem(result)
    problem.code shouldBe "DSP96_PROJECTION_" + code
    problem.path shouldBe path
    problem.reason should not be empty
  }
  private def _display_problem(result: Consequence[Display]): Display.Diagnostic = {
    result match {
      case failure: Consequence.Failure[?] =>
        failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
        failure.conclusion.observation.cause.getException match {
          case Some(carrier: Display.ValidationException) => carrier.diagnostic
          case _ => fail("expected unchanged Display diagnostic")
        }
      case _ => fail("expected final semantic rejection")
    }
  }
  private def _content(id: String, key: String, kinds: Vector[ValueKind]): FieldDefinition = {
    FieldDefinition(id, "  Label Ω  ", PresentationRole.Content, ValueDefinition(key, kinds))
  }
  private def _custom(fields: Vector[FieldDefinition], actions: Vector[ActionDefinition]): Vector[Definition] = {
    Vector(_definitions(1).copy(screen = DetailDefinition("Capture Detail",
      Vector(SectionDefinition("main", None, fields)), actions)))
  }
  private def _row(values: Vector[ValueBinding[Capture]], actions: Vector[ActionBinding[Capture]]): RowBinding[Capture] = {
    RowBinding(source => Consequence.success(source.resource), values, actions)
  }

  "Typed Display projection" should {
    "ESS source interpretation" which {
      "E1 retain exact Detail values identities and inert availability alongside independent semantics" in {
        Given("an admitted ESS definition and immutable typed Capture with authorized availability data")
        val admitted = _admit(_definitions)
        val source = _capture(1, 7)
        val projector = _success(_fixture.detailC(admitted))
        val semantics = _fixture.semanticSummary(source)
        val inputs = ActionAvailability.values.toVector.map(value => source.copy(availability = value))
        When("the same source is projected twice and each availability is interpreted")
        val first = _detail(_success(projector.projectC(source)))
        val repeated = _detail(_success(projector.projectC(source)))
        val states = inputs.map(value => _detail(_success(projector.projectC(value))).actions.head.availability)
        val independent = _fixture.semanticSummary(source)
        Then("every declared byte resource revision and semantic member remains exact and actions are only data")
        first shouldBe repeated
        first.title shouldBe "Capture Detail"
        first.resource shouldBe source.resource
        first.sections.head.fields.map(_.value) shouldBe Vector(DisplayValue.Text(source.title),
          DisplayValue.Text("draft"), DisplayValue.Text(source.description))
        first.sections.head.fields.map(_.id) shouldBe Vector("title", "stage", "description")
        states shouldBe Vector(ActionAvailability.Hidden, ActionAvailability.Disabled, ActionAvailability.Enabled)
        independent shouldBe semantics
        source.status shouldBe CaptureStatus.Draft
      }

      "E2 preserve List order and select a typed row for a separate Detail with the same View key" in {
        Given("screen-specific registrations over the shared ESS View and a supplied reversed row order")
        val admitted = _admit(_definitions)
        val sources = Vector(_capture(2, 19), _capture(1, 7))
        val listprojector = _success(_fixture.listC(admitted))
        var detailcalls = 0
        val detailrow = _fixture.detailRow.copy[Capture](resource = source => {
          detailcalls += 1
          Consequence.success(source.resource)
        })
        val detailprojector = _success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", detailrow))
        val snapshot = Some(QuerySnapshot("source:query:Ω"))
        When("List is interpreted and then its first original typed source is selected for Detail")
        val list = _list(_success(listprojector.projectC(sources, snapshot)))
        val callsbeforeselection = detailcalls
        val selected = sources.head
        val detail = _detail(_success(detailprojector.projectC(selected)))
        Then("List contains its declared fields target and supplied order with no eager Detail evaluation")
        callsbeforeselection shouldBe 0
        detailcalls shouldBe 1
        list.items.map(_.resource) shouldBe sources.map(_.resource)
        list.items.map(_.fields.map(_.value)) shouldBe sources.map(source =>
          Vector(DisplayValue.Text(source.title), DisplayValue.Text(_fixture.stage(source.status))))
        list.detailTarget shouldBe DetailTarget("capture-detail")
        list.snapshot shouldBe snapshot
        detail.resource shouldBe selected.resource
        _fixture.semanticTitles(sources) shouldBe sources.map(_.title)
        listprojector.definition.viewBindingId shouldBe detailprojector.definition.viewBindingId
      }

      "E3 preserve the five closed native kinds including unusual bytes decimal scale and instant precision" in {
        Given("typed native members and explicit Missing declared as five separate Content fields")
        val source = _capture(1, 31)
        val expected = Vector[DisplayValue](DisplayValue.Text(source.description), DisplayValue.Number(source.number),
          DisplayValue.Boolean(source.flag), DisplayValue.Timestamp(source.instant), DisplayValue.Missing)
        val fields = expected.zipWithIndex.map { case (value, index) => _content("field:" + index, "value:" + index, Vector(value.kind)) }
        val bindings = fields.zipWithIndex.map { case (field, index) =>
          ValueBinding[Capture](Vector(field.value.bindingId), value => {
            val converted: DisplayValue = index match {
              case 0 => DisplayValue.Text(value.description)
              case 1 => DisplayValue.Number(value.number)
              case 2 => DisplayValue.Boolean(value.flag)
              case 3 => DisplayValue.Timestamp(value.instant)
              case _ => DisplayValue.Missing
            }
            Consequence.success(Vector(converted))
          })
        }
        val admitted = _admit(_custom(fields, Vector.empty))
        When("the native members are projected through explicit typed closures")
        val result = DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", _row(bindings, Vector.empty))
          .flatMap(_.projectC(source))
        Then("all five payloads remain exact including whitespace Unicode scale nanos and typed Missing")
        val actual = _detail(_success(result)).sections.head.fields.map(_.value)
        actual shouldBe expected
        actual.map(_.kind) shouldBe ValueKind.values.toVector
        source.number.bigDecimal.scale shouldBe 5
        actual.collect { case DisplayValue.Number(value) => value.bigDecimal.scale } shouldBe Vector(5)
        source.instant.getNano shouldBe 123456789
        _fixture.semanticSummary(source)._3 shouldBe source.number
      }

      "E4 evaluate grouped opaque and native fan-out once and reuse keys in each declared location and scope" in {
        Given("repeated field and action keys across sections and scopes with reversed grouped output registration")
        val source = _capture(1, 9)
        val fields = Vector(_content("combined", "combined", Vector(ValueKind.Text)),
          _content("title", "title", Vector(ValueKind.Text)), _content("again", "combined", Vector(ValueKind.Text)))
        val declaredactions = Vector(ActionDefinition("first", "First", "shared"), ActionDefinition("second", "Second", "shared"))
        val detaildefinition = _custom(fields, declaredactions).head.copy(screen = DetailDefinition("Capture Detail",
          Vector(SectionDefinition("main", None, fields), SectionDefinition("other", Some("  Other Ω  "), fields)), declaredactions))
        val listdefinition = _definitions.head.copy(screen = ListDefinition("Collecting Resources", fields,
          DetailTarget("capture-detail"), declaredactions, declaredactions))
        val admitted = _admit(Vector(listdefinition, detaildefinition))
        var conversions = 0
        var rowactions = 0
        var screenactions = 0
        val group = ValueBinding[Capture](Vector("title", "combined"), value => {
          conversions += 1
          Consequence.success(Vector(DisplayValue.Text(value.title),
            DisplayValue.Text(_fixture.stage(value.status) + ":" + value.description)))
        })
        val rowaction = ActionBinding[Capture]("shared", value => { rowactions += 1; Consequence.success(value.availability) })
        val screencallback = ActionBinding[Vector[Capture]]("shared", values => {
          screenactions += 1
          Consequence.success(if (values.isEmpty) ActionAvailability.Hidden else ActionAvailability.Disabled)
        })
        val row = _row(Vector(group), Vector(rowaction))
        When("Detail and List interpret the same typed row using separate action input namespaces")
        val detail = _detail(_success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", row).flatMap(_.projectC(source))))
        val list = _list(_success(DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", row,
          Vector(screencallback)).flatMap(_.projectC(Vector(source), None))))
        Then("positional group outputs populate declaration order and repeated keys evaluate once per scope and row")
        val expected = Vector(DisplayValue.Text("draft:" + source.description), DisplayValue.Text(source.title),
          DisplayValue.Text("draft:" + source.description))
        detail.sections.map(_.fields.map(_.value)) shouldBe Vector(expected, expected)
        list.items.head.fields.map(_.value) shouldBe expected
        conversions shouldBe 2
        rowactions shouldBe 2
        screenactions shouldBe 1
        detail.actions.map(_.availability) shouldBe Vector.fill(2)(ActionAvailability.Enabled)
        list.actions.map(_.availability) shouldBe Vector.fill(2)(ActionAvailability.Disabled)
        list.items.head.actions.map(_.availability) shouldBe Vector.fill(2)(ActionAvailability.Enabled)
        _fixture.semanticSummary(source) shouldBe (source.title, source.status, source.number)
      }

      "E5 allow empty nested containers and evaluate List actions against the original empty Vector" in {
        Given("empty Detail sections and fields and List fields with an explicit screen action")
        val source = _capture(1, 1)
        val emptydetail = _definitions(1).copy(screen = DetailDefinition("Capture Detail",
          Vector(SectionDefinition("empty", None, Vector.empty)), Vector.empty))
        val nosections = emptydetail.copy(screen = DetailDefinition("Capture Detail", Vector.empty, Vector.empty))
        val action = ActionDefinition("empty", "Empty collection", "shared")
        val listdefinition = _definitions.head.copy(screen = ListDefinition("Collecting Resources", Vector.empty,
          DetailTarget("capture-detail"), Vector(action), Vector.empty))
        val sources = Vector.empty[Capture]
        var rowcalls = 0
        var screencalls = 0
        var original = false
        val row = RowBinding[Capture](value => { rowcalls += 1; Consequence.success(value.resource) }, Vector.empty, Vector.empty)
        val callback = ActionBinding[Vector[Capture]]("shared", values => {
          screencalls += 1
          original = values eq sources
          Consequence.success(ActionAvailability.Disabled)
        })
        When("empty List and both empty Detail shapes are projected")
        val list = _list(_success(DisplayProjection.listC(_admit(Vector(listdefinition, emptydetail)),
          "collecting-resources", "ess.reference", row, Vector(callback)).flatMap(_.projectC(sources, None))))
        val detail = _detail(_success(DisplayProjection.detailC(_admit(Vector(emptydetail)), "capture-detail",
          "ess.reference", row).flatMap(_.projectC(source))))
        val emptymodel = _detail(_success(DisplayProjection.detailC(_admit(Vector(nosections)), "capture-detail",
          "ess.reference", row).flatMap(_.projectC(source))))
        Then("zero rows still produce screen availability and empty containers remain present")
        list.items shouldBe Vector.empty
        list.actions.head.availability shouldBe ActionAvailability.Disabled
        screencalls shouldBe 1
        original shouldBe true
        rowcalls shouldBe 2
        detail.sections.head.fields shouldBe Vector.empty
        emptymodel.sections shouldBe Vector.empty
      }
    }

    "Pure registration admission" which {
      "E6 reject absent foreign wrong-branch mismatched and incomplete registrations without callbacks" in {
        Given("typed callbacks counted only if invoked and malformed registration candidates")
        val admitted = _admit(_definitions)
        var calls = 0
        val base = _fixture.detailRow.copy[Capture](resource = value => { calls += 1; Consequence.success(value.resource) },
          values = _fixture.detailRow.values.map(binding => binding.copy[Capture](convert = value => {
            calls += 1
            binding.convert(value)
          })), actions = Vector(ActionBinding[Capture]("ess.request-review", _ => { calls += 1; Consequence.success(ActionAvailability.Enabled) })))
        val values = Vector(_fixture.titleBinding, _fixture.stageBinding, _fixture.descriptionBinding)
        val action = _fixture.reviewBinding
        val cases = Vector(
          RegistrationCase(base.copy(values = values.tail), "BINDING_MISSING", "definition.sections[0].fields[0].value.bindingId"),
          RegistrationCase(base.copy(values = values :+ ValueBinding(Vector("foreign"), _ =>
            Consequence.success(Vector(DisplayValue.Missing)))), "BINDING_UNDECLARED", "row.values[3].bindingIds[0]"),
          RegistrationCase(base.copy(values = values :+ values.head), "BINDING_DUPLICATE", "row.values[3].bindingIds[0]"),
          RegistrationCase(base.copy(values = Vector(ValueBinding(Vector("ess.title", "ess.title"), values.head.convert))),
            "BINDING_DUPLICATE", "row.values[0].bindingIds[1]"),
          RegistrationCase(base.copy(values = Vector(ValueBinding(Vector.empty, values.head.convert))), "BINDING_MISSING", "row.values[0].bindingIds"),
          RegistrationCase(base.copy(values = values.updated(0, values.head.copy(bindingIds = Vector(" key ")))), "ID_INVALID", "row.values[0].bindingIds[0]"),
          RegistrationCase(base.copy(actions = Vector.empty), "BINDING_MISSING", "definition.actions[0].bindingId"),
          RegistrationCase(base.copy(actions = Vector(action, action)), "BINDING_DUPLICATE", "row.actions[1].bindingId"),
          RegistrationCase(base.copy(actions = Vector(action.copy(bindingId = "foreign"))), "BINDING_UNDECLARED", "row.actions[0].bindingId"),
          RegistrationCase(base.copy(actions = Vector(action.copy(bindingId = ""))), "ID_INVALID", "row.actions[0].bindingId"))
        When("factories admit the candidates and perform screen selection without evaluating functions")
        val results = cases.map(testcase => DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", testcase.row))
        val selections = Vector(
          (DisplayProjection.detailC(admitted, "absent", "ess.reference", base), "SCREEN_NOT_FOUND", "screenId"),
          (DisplayProjection.detailC(_admit(Vector(_definitions(1).copy(screenId = "foreign"))), "capture-detail", "ess.reference", base), "SCREEN_NOT_FOUND", "screenId"),
          (DisplayProjection.detailC(admitted, "collecting-resources", "ess.reference", base), "DEFINITION_MISMATCH", "screenId"),
          (DisplayProjection.detailC(admitted, "capture-detail", "other", base), "VIEW_BINDING_MISMATCH", "viewBindingId"),
          (DisplayProjection.detailC(admitted, " capture-detail", "ess.reference", base), "ID_INVALID", "screenId"),
          (DisplayProjection.detailC(admitted, "capture-detail", "ess.reference ", base), "ID_INVALID", "viewBindingId"))
        val wronglist = DisplayProjection.listC(admitted, "capture-detail", "ess.reference", base, Vector.empty)
        val listmissing = DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", _fixture.listRow.copy(values = Vector.empty), Vector.empty)
        val extralistaction = DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", _fixture.listRow,
          Vector(ActionBinding[Vector[Capture]]("extra", _ => { calls += 1; Consequence.success(ActionAvailability.Enabled) })))
        val screenaction = ActionDefinition("screen", "Screen", "screen")
        val listdeclared = _definitions.head.screen match {
          case declared: ListDefinition => declared.copy(actions = Vector(screenaction))
          case _ => fail("fixture declares a List")
        }
        val listadmitted = _admit(Vector(_definitions.head.copy(screen = listdeclared), _definitions(1)))
        val screenbinding = ActionBinding[Vector[Capture]]("screen", _ => { calls += 1; Consequence.success(ActionAvailability.Enabled) })
        val screenregistrations = Vector(
          (Vector.empty[ActionBinding[Vector[Capture]]], "BINDING_MISSING", "definition.actions[0].bindingId"),
          (Vector(screenbinding, screenbinding), "BINDING_DUPLICATE", "actions[1].bindingId"),
          (Vector(screenbinding.copy(bindingId = "other")), "BINDING_UNDECLARED", "actions[0].bindingId"),
          (Vector(screenbinding.copy(bindingId = " screen ")), "ID_INVALID", "actions[0].bindingId"))
        val screenresults = screenregistrations.map { case (bindings, _, _) =>
          DisplayProjection.listC(listadmitted, "collecting-resources", "ess.reference", _fixture.listRow, bindings)
        }
        val valid = DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", base)
        Then("deterministic structured codes paths and zero callback calls cover every invalid registration")
        results.zip(cases).foreach { case (result, testcase) => _expect(result, testcase.code, testcase.path) }
        selections.foreach { case (result, code, path) => _expect(result, code, path) }
        _expect(wronglist, "DEFINITION_MISMATCH", "screenId")
        _expect(listmissing, "BINDING_MISSING", "definition.itemFields[0].value.bindingId")
        _expect(extralistaction, "BINDING_UNDECLARED", "actions[0].bindingId")
        screenresults.zip(screenregistrations).foreach { case (result, (_, code, path)) => _expect(result, code, path) }
        _success(valid).definition shouldBe _definitions(1)
        calls shouldBe 0
      }

      "E7 reject the complete nullable registration source snapshot and callback boundary matrix" in {
        Given("nulls at factory registration and source boundaries plus callbacks returning null successful values")
        val admitted = _admit(_definitions)
        val source = _capture(1, 2)
        val base = _fixture.detailRow
        val value = base.values.head
        val action = base.actions.head
        val screenaction = ActionDefinition("screen", "Screen", "screen")
        val listdefinition = _definitions.head.copy(screen = ListDefinition("Collecting Resources", Vector.empty,
          DetailTarget("capture-detail"), Vector(screenaction), Vector(screenaction)))
        val listadmitted = _admit(Vector(listdefinition, _definitions(1)))
        val listaction = ActionBinding[Vector[Capture]]("screen", _ => Consequence.success(ActionAvailability.Hidden))
        val itemaction = ActionBinding[Capture]("screen", _ => Consequence.success(ActionAvailability.Enabled))
        val actionrow = _row(Vector.empty, Vector(itemaction))
        val registrations = Vector(
          RegistrationCase(null, "ELEMENT_INVALID", "row"),
          RegistrationCase(base.copy(resource = null), "ELEMENT_INVALID", "row.resource"),
          RegistrationCase(base.copy(values = null), "ELEMENT_INVALID", "row.values"),
          RegistrationCase(base.copy(values = Vector(null)), "ELEMENT_INVALID", "row.values[0]"),
          RegistrationCase(base.copy(values = Vector(value.copy(bindingIds = null))), "ELEMENT_INVALID", "row.values[0].bindingIds"),
          RegistrationCase(base.copy(values = Vector(value.copy(bindingIds = Vector(null)))), "ID_INVALID", "row.values[0].bindingIds[0]"),
          RegistrationCase(base.copy(values = base.values.updated(0, value.copy(convert = null))), "ELEMENT_INVALID", "row.values[0].convert"),
          RegistrationCase(base.copy(actions = null), "ELEMENT_INVALID", "row.actions"),
          RegistrationCase(base.copy(actions = Vector(null)), "ELEMENT_INVALID", "row.actions[0]"),
          RegistrationCase(base.copy(actions = Vector(action.copy(bindingId = null))), "ID_INVALID", "row.actions[0].bindingId"),
          RegistrationCase(base.copy(actions = Vector(action.copy(evaluate = null))), "ELEMENT_INVALID", "row.actions[0].evaluate"))
        val projections = Vector(
          ProjectionCase(base.copy(resource = _ => null), "ELEMENT_INVALID", "model.resource"),
          ProjectionCase(base.copy(resource = _ => Consequence.success(null)), "ELEMENT_INVALID", "model.resource"),
          ProjectionCase(base.copy(values = base.values.updated(2, base.values(2).copy(convert = _ => null))), "ELEMENT_INVALID", "model.values[0]"),
          ProjectionCase(base.copy(values = base.values.updated(2, base.values(2).copy(convert = _ => Consequence.success(null)))), "ELEMENT_INVALID", "model.values[0]"),
          ProjectionCase(base.copy(values = base.values.updated(2, base.values(2).copy(convert = _ => Consequence.success(Vector(null))))), "ELEMENT_INVALID", "model.values[0][0]"),
          ProjectionCase(base.copy(actions = Vector(action.copy(evaluate = _ => null))), "ELEMENT_INVALID", "model.actions[0]"),
          ProjectionCase(base.copy(actions = Vector(action.copy(evaluate = _ => Consequence.success(null)))), "ELEMENT_INVALID", "model.actions[0]"))
        var calls = 0
        val counted = _fixture.listRow.copy[Capture](resource = value => { calls += 1; Consequence.success(value.resource) })
        val list = _success(DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", counted, Vector.empty))
        val detail = _success(_fixture.detailC(admitted))
        When("each nullable input is interpreted independently")
        val registrationresults = registrations.map(testcase => DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", testcase.row))
        val projectionresults = projections.map(testcase => DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", testcase.row).flatMap(_.projectC(source)))
        val callbackfields = _definitions(1).screen match {
          case declared: DetailDefinition => declared.sections.head.fields
          case _ => fail("fixture declares a Detail")
        }
        val callbackdefinition = _definitions.head.copy(screen = ListDefinition("Collecting Resources", callbackfields,
          DetailTarget("capture-detail"), Vector.empty, Vector(ActionDefinition("request-review", "Request Review", "ess.request-review"))))
        val callbackadmitted = _admit(Vector(callbackdefinition, _definitions(1)))
        val listprojectionresults = projections.map(testcase => DisplayProjection.listC(callbackadmitted,
          "collecting-resources", "ess.reference", testcase.row, Vector.empty).flatMap(_.projectC(Vector(source), None)))
        val factoryresults = Vector(
          (DisplayProjection.detailC[Capture](null, null, null, null), "ELEMENT_INVALID", "definitions"),
          (DisplayProjection.detailC(admitted, null, "ess.reference", base), "ID_INVALID", "screenId"),
          (DisplayProjection.detailC(admitted, "capture-detail", null, base), "ID_INVALID", "viewBindingId"))
        val sourcecases = Vector(
          (detail.projectC(null), "ELEMENT_INVALID", "source"),
          (list.projectC(null, None), "ELEMENT_INVALID", "source"),
          (list.projectC(Vector(source, null), None), "ELEMENT_INVALID", "source[1]"),
          (list.projectC(Vector(source), null), "ELEMENT_INVALID", "snapshot"),
          (list.projectC(Vector(source), Some(null)), "ELEMENT_INVALID", "snapshot"),
          (list.projectC(Vector(source), Some(QuerySnapshot(null))), "ID_INVALID", "snapshot.reference"),
          (list.projectC(Vector(source), Some(QuerySnapshot(" bad "))), "ID_INVALID", "snapshot.reference"))
        val listnullactions = DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", counted, null)
        val listnullentry = DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", counted, Vector(null))
        val listfactoryresults = Vector(
          (DisplayProjection.listC[Capture](null, null, null, null, null), "ELEMENT_INVALID", "definitions"),
          (DisplayProjection.listC(admitted, null, "ess.reference", counted, Vector.empty), "ID_INVALID", "screenId"),
          (DisplayProjection.listC(admitted, "collecting-resources", null, counted, Vector.empty), "ID_INVALID", "viewBindingId"),
          (DisplayProjection.listC[Capture](admitted, "collecting-resources", "ess.reference", null, Vector.empty), "ELEMENT_INVALID", "row"))
        val listactionregistrations = Vector(
          (Vector(listaction.copy(bindingId = null)), "ID_INVALID", "actions[0].bindingId"),
          (Vector(listaction.copy(evaluate = null)), "ELEMENT_INVALID", "actions[0].evaluate"),
          (Vector.empty[ActionBinding[Vector[Capture]]], "BINDING_MISSING", "definition.actions[0].bindingId"),
          (Vector(listaction, listaction), "BINDING_DUPLICATE", "actions[1].bindingId"))
        val listactionresults = listactionregistrations.map { case (bindings, _, _) =>
          DisplayProjection.listC(listadmitted, "collecting-resources", "ess.reference", actionrow, bindings)
        }
        val nullscreenresults = Vector[Vector[Capture] => Consequence[ActionAvailability]](
          _ => null, _ => Consequence.success(null)).map { callback =>
          DisplayProjection.listC(listadmitted, "collecting-resources", "ess.reference", actionrow,
            Vector(listaction.copy(evaluate = callback))).flatMap(_.projectC(Vector.empty, None))
        }
        val nullitemresults = Vector[Capture => Consequence[ActionAvailability]](
          _ => null, _ => Consequence.success(null)).map { callback =>
          DisplayProjection.listC(listadmitted, "collecting-resources", "ess.reference",
            actionrow.copy(actions = Vector(itemaction.copy(evaluate = callback))), Vector(listaction))
            .flatMap(_.projectC(Vector(source), None))
        }
        val missingitem = DisplayProjection.listC(listadmitted, "collecting-resources", "ess.reference",
          actionrow.copy(actions = Vector.empty), Vector(listaction))
        Then("all failures carry their typed path and invalid source or snapshot invokes no callback")
        registrationresults.zip(registrations).foreach { case (result, testcase) => _expect(result, testcase.code, testcase.path) }
        projectionresults.zip(projections).foreach { case (result, testcase) => _expect(result, testcase.code, testcase.path) }
        listprojectionresults.zip(projections).foreach { case (result, testcase) =>
          _expect(result, testcase.code, testcase.path.replace("model", "model.items[0]"))
        }
        factoryresults.foreach { case (result, code, path) => _expect(result, code, path) }
        sourcecases.foreach { case (result, code, path) => _expect(result, code, path) }
        _expect(listnullactions, "ELEMENT_INVALID", "actions")
        _expect(listnullentry, "ELEMENT_INVALID", "actions[0]")
        listfactoryresults.foreach { case (result, code, path) => _expect(result, code, path) }
        listactionresults.zip(listactionregistrations).foreach { case (result, (_, code, path)) => _expect(result, code, path) }
        nullscreenresults.foreach(result => _expect(result, "ELEMENT_INVALID", "model.actions[0]"))
        nullitemresults.foreach(result => _expect(result, "ELEMENT_INVALID", "model.items[0].actions[0]"))
        _expect(missingitem, "BINDING_MISSING", "definition.itemActions[0].bindingId")
        calls shouldBe 0
      }
    }

    "Failure and public type contracts" which {
      "E8 preserve structured callback failure redact exceptions and short-circuit in declaration order without retries" in {
        Given("reversed registration order and failures at resource value row-action or List-action callbacks")
        val source = _capture(1, 1)
        val other = _capture(2, 2)
        val fields = Vector(_content("first", "first", Vector(ValueKind.Text)), _content("second", "second", Vector(ValueKind.Text)))
        val declaredactions = Vector(ActionDefinition("first", "First", "first"), ActionDefinition("second", "Second", "second"))
        val detaildefinition = _custom(fields, declaredactions).head
        val listdefinition = _definitions.head.copy(screen = ListDefinition("Collecting Resources", fields,
          DetailTarget("capture-detail"), declaredactions, declaredactions))
        val admitted = _admit(Vector(listdefinition, detaildefinition))
        val sentinel = new IllegalArgumentException("private source and key payload")
        val control = new scala.util.control.ControlThrowable {}
        val failure: Consequence.Failure[Nothing] = Consequence.fail(Taxonomy.argumentInvalid, sentinel)
        val stages = Vector("resource", "value", "rowaction", "screenaction")
        When("each callback stage fails once as a structured consequence or a NonFatal exception")
        val results = stages.flatMap { stage => Vector(false, true).map { throws =>
          var trace = Vector.empty[String]
          def _result_[A](label: String, success: A): Consequence[A] = {
            trace = trace :+ label
            if (label == stage) {
              if (throws) throw sentinel else failure
            } else Consequence.success(success)
          }
          val row = RowBinding[Capture](value => _result_("resource", value.resource),
            Vector(ValueBinding(Vector("second"), value => _result_("later-value", Vector[DisplayValue](DisplayValue.Text(value.description)))),
              ValueBinding(Vector("first"), value => _result_("value", Vector[DisplayValue](DisplayValue.Text(value.title))))),
            Vector(ActionBinding("second", _ => _result_("later-action", ActionAvailability.Enabled)),
              ActionBinding("first", _ => _result_("rowaction", ActionAvailability.Disabled))))
          val screen = Vector(ActionBinding[Vector[Capture]]("second", _ => _result_("later-screen", ActionAvailability.Enabled)),
            ActionBinding[Vector[Capture]]("first", _ => _result_("screenaction", ActionAvailability.Hidden)))
          val input = if (stage == "screenaction") Vector(source) else Vector(source, other)
          val result = DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", row, screen)
            .flatMap(_.projectC(input, None))
          val detailresult = if (stage == "screenaction") None else Some(DisplayProjection.detailC(admitted,
            "capture-detail", "ess.reference", row).flatMap(_.projectC(source)))
          (stage, throws, result, detailresult, trace)
        } }
        val controlprojector = _success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference",
          RowBinding[Capture](_ => throw control,
            Vector(ValueBinding(Vector("first", "second"), value => Consequence.success(Vector(
              DisplayValue.Text(value.title), DisplayValue.Text(value.description))))),
            Vector(ActionBinding("first", _ => Consequence.success(ActionAvailability.Enabled)),
              ActionBinding("second", _ => Consequence.success(ActionAvailability.Disabled))))))
        val escaped = intercept[scala.util.control.ControlThrowable] { controlprojector.projectC(source) }
        Then("callback failures retain their cause and thrown failures reveal only fixed diagnostics with no later work")
        results.foreach { case (stage, throws, result, detailresult, trace) =>
          val prefix = stage match {
            case "resource" => Vector("resource")
            case "value" => Vector("resource", "value")
            case "rowaction" => Vector("resource", "value", "later-value", "rowaction")
            case _ => Vector("resource", "value", "later-value", "rowaction", "later-action", "screenaction")
          }
          trace shouldBe (if (detailresult.nonEmpty) prefix ++ prefix else prefix)
          val paths = stage match {
            case "resource" => ("model.items[0].resource", "model.resource")
            case "value" => ("model.items[0].values[0]", "model.values[0]")
            case "rowaction" => ("model.items[0].actions[0]", "model.actions[0]")
            case _ => ("model.actions[0]", "model.actions[0]")
          }
          if (throws) {
            _expect(result, "CALLBACK_FAILED", paths._1)
            detailresult.foreach(value => _expect(value, "CALLBACK_FAILED", paths._2))
            _problem(result).reason shouldBe "callback did not return normally"
          } else {
            (Vector(result) ++ detailresult.toVector).foreach {
              case rejected: Consequence.Failure[?] =>
                rejected.conclusion shouldBe failure.conclusion
                rejected.conclusion.observation.cause.getException shouldBe Some(sentinel)
              case _ => fail("failure-valued conversion must never become Missing or partial success")
            }
          }
        }
        escaped shouldBe control
      }

      "E9 reject fan-out cardinality before zip and pass final kind role and payload diagnostics through unchanged" in {
        Given("ESS Detail fan-out with too few too many null and semantically incompatible outputs")
        val admitted = _admit(_definitions)
        val source = _capture(1, 3)
        val good = Vector[DisplayValue](DisplayValue.Text(source.title), DisplayValue.Text("draft"), DisplayValue.Text(source.description))
        var actioncalls = 0
        val group = ValueBinding[Capture](Vector("ess.title", "ess.stage", "ess.description"), _ => Consequence.success(good))
        val row = _fixture.detailRow.copy(values = Vector(group), actions = Vector(ActionBinding("ess.request-review", value => {
          actioncalls += 1
          Consequence.success(value.availability)
        })))
        val invalidoutputs = Vector(good.take(2), good :+ DisplayValue.Missing, good.updated(1, null))
        val semanticoutputs = Vector(good.updated(1, DisplayValue.Number(BigDecimal(1))), good.updated(0, DisplayValue.Text(" \t")),
          good.updated(2, DisplayValue.Text(null)), good.updated(2, DisplayValue.Missing))
        val payloadfields = Vector(_content("payload", "payload", ValueKind.values.toVector))
        val payloadadmitted = _admit(_custom(payloadfields, Vector.empty))
        val payloads = Vector[DisplayValue](DisplayValue.Number(null), DisplayValue.Timestamp(null))
        When("every output vector is projected through the admitted factory")
        val failures = invalidoutputs.map(outputs => DisplayProjection.detailC(admitted, "capture-detail", "ess.reference",
          row.copy(values = Vector(group.copy(convert = _ => Consequence.success(outputs))))).flatMap(_.projectC(source)))
        val earlycalls = actioncalls
        val semanticresults = semanticoutputs.map { outputs =>
          val projected = DisplayProjection.detailC(admitted, "capture-detail", "ess.reference",
            row.copy(values = Vector(group.copy(convert = _ => Consequence.success(outputs))))).flatMap(_.projectC(source))
          val candidate = DetailModel("capture-detail", "Capture Detail", source.resource,
            Vector(Section("main", None, Vector(
              Field("title", "Title", PresentationRole.Title, outputs(0)),
              Field("stage", "Capture stage", PresentationRole.Status, outputs(1)),
              Field("description", "Description", PresentationRole.Description, outputs(2))))),
            Vector(Action("request-review", "Request Review", "ess.request-review", source.availability)))
          (projected, Display.createC(admitted, candidate))
        }
        val payloadresults = payloads.map { payload =>
          val binding = ValueBinding[Capture](Vector("payload"), _ => Consequence.success(Vector(payload)))
          val projected = DisplayProjection.detailC(payloadadmitted, "capture-detail", "ess.reference",
            _row(Vector(binding), Vector.empty)).flatMap(_.projectC(source))
          val direct = Display.createC(payloadadmitted, DetailModel("capture-detail", "Capture Detail", source.resource,
            Vector(Section("main", None, Vector(Field("payload", "  Label Ω  ", PresentationRole.Content, payload)))), Vector.empty))
          (projected, direct)
        }
        Then("cardinality and null entries stop later callbacks and final semantic validation keeps its original carrier")
        _expect(failures(0), "OUTPUT_COUNT_INVALID", "model.values[0]")
        _expect(failures(1), "OUTPUT_COUNT_INVALID", "model.values[0]")
        _expect(failures(2), "ELEMENT_INVALID", "model.values[0][1]")
        earlycalls shouldBe 0
        semanticresults.foreach { case (projected, direct) => _display_problem(projected) shouldBe _display_problem(direct) }
        semanticresults.map(result => _display_problem(result._1).code) shouldBe Vector(
          "DSP96_VALUE_KIND_INVALID", "DSP96_ROLE_INVALID", "DSP96_ELEMENT_INVALID", "DSP96_VALUE_KIND_INVALID")
        actioncalls shouldBe semanticoutputs.size
        payloadresults.foreach { case (projected, direct) =>
          _display_problem(projected) shouldBe _display_problem(direct)
          _display_problem(projected).code shouldBe "DSP96_ELEMENT_INVALID"
        }
      }

      "E10 distinguish duplicate typed resource identity from revision screen provenance and snapshot spaces" in {
        Given("one EntityId at two revisions and independently changed screen provenance and query correlation")
        val source = _capture(1, 7)
        val newer = source.copy(resource = source.resource.copy(revision = _capture(1, 99).resource.revision))
        val definitions = _definitions.map(definition => definition.copy(definitionRevision = "definition:unrelated",
          provenance = definition.provenance.copy(revision = "producer:unrelated")))
        val projector = _success(_fixture.listC(_admit(definitions)))
        val snapshot = Some(QuerySnapshot("query:unrelated"))
        val renamedscreens = definitions.map { definition =>
          definition.screen match {
            case declared: ListDefinition => definition.copy(screenId = "screen:opaque:Ω",
              screen = declared.copy(detailTarget = DetailTarget("detail:opaque:Ω")))
            case _: DetailDefinition => definition.copy(screenId = "detail:opaque:Ω")
          }
        }
        When("List projects repeated identity and separately projects one independently revisioned row")
        val duplicate = projector.projectC(Vector(source, newer), snapshot)
        val single = _list(_success(projector.projectC(Vector(newer), snapshot)))
        val renamed = _list(_success(DisplayProjection.listC(_admit(renamedscreens), "screen:opaque:Ω",
          "ess.reference", _fixture.listRow, Vector.empty).flatMap(_.projectC(Vector(newer), snapshot))))
        Then("identity duplication fails despite distinct revisions and the successful model preserves each independent space")
        _display_problem(duplicate).code shouldBe "DSP96_DUPLICATE_RESOURCE"
        _display_problem(duplicate).path shouldBe "model.items[1].resource.entityId"
        single.items.head.resource shouldBe newer.resource
        single.snapshot shouldBe snapshot
        single.screenId shouldBe "collecting-resources"
        projector.definition.definitionRevision shouldBe "definition:unrelated"
        projector.definition.provenance.revision shouldBe "producer:unrelated"
        renamed.screenId shouldBe "screen:opaque:Ω"
        renamed.items.head.resource shouldBe newer.resource
        renamed.snapshot shouldBe snapshot
      }

      "E11 enforce external typing and constructor boundaries while a valid fixture produces observable models" in {
        Given("a lexically external application fixture and normally admitted ESS definitions")
        val admitted = _admit(_definitions)
        val source = _capture(1, 8)
        When("external compile probes are evaluated and valid typed projectors interpret their declared sources")
        _fixture.verifyTypeBoundary()
        val detail = _detail(_success(_fixture.detailC(admitted).flatMap(_.projectC(source))))
        val list = _list(_success(_fixture.listC(admitted).flatMap(_.projectC(Vector(source), None))))
        Then("wrong source and result types construction copy and arbitrary kinds are rejected while typed models remain usable")
        detail.resource shouldBe source.resource
        list.items.head.resource shouldBe source.resource
        detail.sections.head.fields.head.value shouldBe DisplayValue.Text(source.title)
        list.items.head.fields.head.value shouldBe DisplayValue.Text(source.title)
      }
    }
  }
}
