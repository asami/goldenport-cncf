package org.goldenport.cncf.display

import java.time.Instant
import logicalui.TypedDisplayProjectionFixture
import logicalui.TypedDisplayProjectionFixture.{Capture, CaptureStatus}
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class DisplayProjectionPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*
  import DisplayProjection.*

  private val _fixture = TypedDisplayProjectionFixture
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _keys = Vector("text", "number", "flag", "instant", "missing")
  private val _action_keys = Vector("hidden", "disabled", "enabled")
  private val _text = for {
    size <- Gen.choose(0, 40)
    chars <- Gen.listOfN(size, Gen.oneOf('a', 'Ω', '日', 'é', '\u0301', ' ', '\t', '\n', '\r', '\u2003'))
  } yield chars.mkString
  private val _decimal = for {
    coefficient <- Gen.choose(Long.MinValue, Long.MaxValue)
    scale <- Gen.choose(-20, 50)
  } yield BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(coefficient), scale))
  private val _instant = for {
    seconds <- Gen.choose(-100000000000L, 100000000000L)
    nanos <- Gen.choose(0, 999999999)
  } yield Instant.ofEpochSecond(seconds, nanos.toLong)
  private val _capture = for {
    title <- _text
    description <- _text
    number <- _decimal
    flag <- Gen.oneOf(true, false)
    instant <- _instant
    status <- Gen.oneOf(CaptureStatus.Draft, CaptureStatus.Ready, CaptureStatus.Held)
    revision <- Gen.choose(1L, 1000000L)
    availability <- Gen.oneOf(ActionAvailability.values.toVector)
  } yield _success(_fixture.captureC(1, revision)).copy(title = "prefix:" + title + ":end", description = description,
    number = number, flag = flag, instant = instant, status = status, availability = availability)

  private def _success[A](result: Consequence[A]): A = {
    result match {
      case Consequence.Success(value) => value
      case _ => fail("generated total fixture must interpret successfully")
    }
  }
  private def _value(source: Capture, key: String): DisplayValue = {
    key match {
      case "text" => DisplayValue.Text(source.description)
      case "number" => DisplayValue.Number(source.number)
      case "flag" => DisplayValue.Boolean(source.flag)
      case "instant" => DisplayValue.Timestamp(source.instant)
      case "missing" => DisplayValue.Missing
      case _ => fail("generator supplies only the explicitly declared native keys")
    }
  }
  private def _kind(key: String): ValueKind = {
    key match {
      case "text" => ValueKind.Text
      case "number" => ValueKind.Number
      case "flag" => ValueKind.Boolean
      case "instant" => ValueKind.Timestamp
      case "missing" => ValueKind.Missing
      case _ => fail("generator supplies only the closed explicit keys")
    }
  }
  private def _availability(key: String): ActionAvailability = {
    key match {
      case "hidden" => ActionAvailability.Hidden
      case "disabled" => ActionAvailability.Disabled
      case "enabled" => ActionAvailability.Enabled
      case _ => fail("generator supplies only explicit availability bindings")
    }
  }
  private def _fields(order: Vector[String]): Vector[FieldDefinition] = {
    order.map(key => FieldDefinition("field:" + key, "  Label Ω e\u0301  ", PresentationRole.Content,
      ValueDefinition(key, Vector(_kind(key)))))
  }
  private def _actions(order: Vector[String]): Vector[ActionDefinition] = {
    order.map(key => ActionDefinition("action:" + key, "  Action Ω  ", key))
  }
  private def _definitions(fields: Vector[FieldDefinition], sections: Vector[String],
    actions: Vector[ActionDefinition]): AdmittedDefinitions = {
    val detail = _fixture.definitions(1).copy(screen = DetailDefinition("Capture Detail",
      sections.map(id => SectionDefinition(id, Some("  Section Ω  "), fields)), actions))
    val list = _fixture.definitions.head.copy(screen = ListDefinition("Collecting Resources", fields,
      DetailTarget("capture-detail"), actions, actions))
    _success(GeneratedLogicalUiAbi.admitC(Vector(list, detail)))
  }
  private def _rows(template: Capture, order: Vector[Int]): Vector[Capture] = {
    order.map(index => template.copy(resource = _success(_fixture.resourceC(index + 1, index + 1L)).copy(revision = template.resource.revision),
      title = template.title + ":row:" + index))
  }
  private def _detail(display: Display): DetailModel = {
    display.model match {
      case model: DetailModel => model
      case _ => fail("expected projected Detail")
    }
  }
  private def _list(display: Display): ListModel = {
    display.model match {
      case model: ListModel => model
      case _ => fail("expected projected List")
    }
  }
  private def _problem[A](result: Consequence[A]): Option[DisplayProjection.Diagnostic] = {
    result match {
      case rejected: Consequence.Failure[?] if rejected.conclusion.observation.taxonomy == Taxonomy.argumentInvalid =>
        rejected.conclusion.observation.cause.getException.collect { case carrier: ProjectionException => carrier.diagnostic }
      case _ => None
    }
  }
  private def _display_problem(result: Consequence[Display]): Option[Display.Diagnostic] = {
    result match {
      case rejected: Consequence.Failure[?] if rejected.conclusion.observation.taxonomy == Taxonomy.argumentInvalid =>
        rejected.conclusion.observation.cause.getException.collect { case carrier: Display.ValidationException => carrier.diagnostic }
      case _ => None
    }
  }

  "Generated typed projection" should {
    "P1 preserve native values source schema order revisions and independent semantic use in at least one hundred cases" in {
      Given("total generators for Unicode whitespace scaled decimals precise instants revisions and empty through multiple rows")
      val input = for {
        template <- _capture
        count <- Gen.choose(0, 5)
        roworder <- Gen.oneOf(Vector.tabulate(count)(index => index).permutations.toVector)
        fieldorder <- Gen.oneOf(_keys.permutations.toVector)
        sectionorder <- Gen.oneOf(Vector("main", "other").permutations.toVector)
        actionorder <- Gen.oneOf(_action_keys.permutations.toVector)
      } yield (template, roworder, fieldorder, sectionorder, actionorder)
      When("each generated typed source is projected repeatedly through admitted List and Detail contracts")
      val property = Prop.forAll(input) { case (template, roworder, fieldorder, sectionorder, actionorder) =>
        val fields = _fields(fieldorder)
        val actions = _actions(actionorder)
        val admitted = _definitions(fields, sectionorder, actions)
        val rows = _rows(template, roworder)
        val independent = _fixture.semanticTitles(rows)
        val independentrow = _fixture.semanticSummary(template)
        val group = ValueBinding[Capture](_keys, source => Consequence.success(_keys.map(key => _value(source, key))))
        val rowactions = actionorder.reverse.map(key => ActionBinding[Capture](key, _ => Consequence.success(_availability(key))))
        val screenactions = actionorder.reverse.map(key => ActionBinding[Vector[Capture]](key, _ => Consequence.success(_availability(key))))
        val row = RowBinding[Capture](source => Consequence.success(source.resource), Vector(group), rowactions)
        val listprojector = _success(DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", row, screenactions))
        val detailprojector = _success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", row))
        val snapshot = Some(QuerySnapshot("query:" + template.title + ":end"))
        val first = _list(_success(listprojector.projectC(rows, snapshot)))
        val second = _list(_success(listprojector.projectC(rows, snapshot)))
        val detail = _detail(_success(detailprojector.projectC(template)))
        val repeated = _detail(_success(detailprojector.projectC(template)))
        first == second && detail == repeated &&
          first.items.map(_.resource) == rows.map(_.resource) && first.items.map(_.resource.entityId).distinct.size == rows.size &&
          first.items.zip(rows).forall { case (item, source) =>
            item.fields.map(_.id) == fields.map(_.id) && item.fields.map(_.value) == fieldorder.map(key => _value(source, key)) &&
              item.fields.map(_.label) == fields.map(_.label) && item.actions.map(_.id) == actions.map(_.id)
          } && first.actions.map(_.availability) == actionorder.map(_availability) && first.snapshot == snapshot &&
          detail.resource == template.resource && detail.sections.map(_.id) == sectionorder &&
          detail.sections.forall(section => section.fields.map(_.value) == fieldorder.map(key => _value(template, key))) &&
          detail.actions.map(_.availability) == actionorder.map(_availability) &&
          _fixture.semanticTitles(rows) == independent && _fixture.semanticSummary(template) == independentrow &&
          detail.sections.head.fields.map(_.value.kind).toSet == ValueKind.values.toSet &&
          detail.sections.head.fields.collect { case Field(_, _, _, DisplayValue.Number(value)) => value.bigDecimal.scale } ==
            Vector(template.number.bigDecimal.scale)
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred successful cases retain all five kinds and equal repeated models without changing semantic inputs")
      withClue(result.toString) {
        result.passed shouldBe true
        result.succeeded should be >= 100
      }
    }

    "P2 compile registration and output permutations once per row with shared scopes and stable invalid results" in {
      Given("independent field output registration and action permutations and zero single or multiple row inputs")
      val input = for {
        template <- _capture
        count <- Gen.choose(0, 4)
        fieldorder <- Gen.oneOf(_keys.permutations.toVector)
        outputorder <- Gen.oneOf(_keys.permutations.toVector)
        split <- Gen.choose(1, _keys.size - 1)
        reversed <- Gen.oneOf(true, false)
        actionorder <- Gen.oneOf(_action_keys.permutations.toVector)
        registrationorder <- Gen.oneOf(_action_keys.permutations.toVector)
      } yield (template, count, fieldorder, outputorder, split, reversed, actionorder, registrationorder)
      When("each grouped fan-out and availability scope is compiled and projected and incompatible outputs are interpreted twice")
      val property = Prop.forAll(input) { case (template, count, fieldorder, outputorder, split, reversed, actionorder, registrationorder) =>
        val fields = _fields(fieldorder) :+ FieldDefinition("reused", "  Reused Ω  ", PresentationRole.Content,
          ValueDefinition(fieldorder.head, Vector(_kind(fieldorder.head))))
        val actions = _actions(actionorder) :+ ActionDefinition("reused", "  Reused Ω  ", actionorder.head)
        val admitted = _definitions(fields, Vector("main", "other"), actions)
        val rows = _rows(template, Vector.tabulate(count)(index => index))
        val groups = Vector(outputorder.take(split), outputorder.drop(split))
        var trace = Vector.empty[String]
        val bindings = groups.zipWithIndex.map { case (keys, index) =>
          ValueBinding[Capture](keys, source => {
            trace = trace :+ ("values:" + index)
            Consequence.success(keys.map(key => _value(source, key)))
          })
        }
        val rowactions = registrationorder.map(key => ActionBinding[Capture](key, _ => {
          trace = trace :+ ("row:" + key)
          Consequence.success(_availability(key))
        }))
        val screenactions = registrationorder.map(key => ActionBinding[Vector[Capture]](key, source => {
          trace = trace :+ ("screen:" + key)
          Consequence.success(if (source.size == rows.size) _availability(key) else ActionAvailability.Hidden)
        }))
        val row = RowBinding[Capture](source => { trace = trace :+ "resource"; Consequence.success(source.resource) },
          if (reversed) bindings.reverse else bindings, rowactions)
        val list = _list(_success(DisplayProjection.listC(admitted, "collecting-resources", "ess.reference", row,
          screenactions).flatMap(_.projectC(rows, None))))
        val listtrace = trace
        trace = Vector.empty
        val detail = _detail(_success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", row)
          .flatMap(_.projectC(template))))
        val detailtrace = trace
        val grouporder = fieldorder.map(key => groups.indexWhere(_.contains(key))).distinct
        val expectedrowtrace = Vector("resource") ++ grouporder.map(index => "values:" + index) ++ actionorder.map(key => "row:" + key)
        val expected = fieldorder.map(key => _value(template, key)) :+ _value(template, fieldorder.head)
        val invalidkind = ValueBinding[Capture](outputorder, source => Consequence.success(outputorder.map { key =>
          if (key == "text") DisplayValue.Number(source.number) else _value(source, key)
        }))
        val kindrow = row.copy(values = Vector(invalidkind))
        val kindprojector = _success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", kindrow))
        val kindfirst = kindprojector.projectC(template)
        val kindsecond = kindprojector.projectC(template)
        val cardinality = ValueBinding[Capture](outputorder, source => Consequence.success(outputorder.dropRight(1).map(key => _value(source, key))))
        val countprojector = _success(DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", row.copy(values = Vector(cardinality))))
        val countfirst = countprojector.projectC(template)
        val countsecond = countprojector.projectC(template)
        val kindpath = s"model.sections[0].fields[${fieldorder.indexOf("text")}].value"
        listtrace == (Vector.fill(count)(expectedrowtrace).flatten ++ actionorder.map(key => "screen:" + key)) &&
          detailtrace == expectedrowtrace && detail.sections.forall(_.fields.map(_.value) == expected) &&
          list.items.zip(rows).forall { case (item, source) =>
            item.fields.map(_.value) == (fieldorder.map(key => _value(source, key)) :+ _value(source, fieldorder.head))
          } && detail.actions.map(_.availability) == (actionorder :+ actionorder.head).map(_availability) &&
          list.actions.map(_.availability) == (actionorder :+ actionorder.head).map(_availability) &&
          _display_problem(kindfirst) == _display_problem(kindsecond) &&
          _display_problem(kindfirst).exists(problem => problem.code == "DSP96_VALUE_KIND_INVALID" && problem.path == kindpath) &&
          _problem(countfirst) == _problem(countsecond) &&
          _problem(countfirst).exists(problem => problem.code == "DSP96_PROJECTION_OUTPUT_COUNT_INVALID" && problem.path == "model.values[0]")
      }
      val result = Test.check(_parameters, property)
      Then("at least one hundred cases preserve declaration order shared keys and callback counts and reject invalid outputs without partial success")
      withClue(result.toString) {
        result.passed shouldBe true
        result.succeeded should be >= 100
      }
    }
  }
}
