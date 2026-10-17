package org.goldenport.cncf.display

import logicalui.ExpectedGeneratedLogicalUiFixture
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
final class GeneratedLogicalUiAbiPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*

  private val _definitions = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _list = _definitions.head.screen.asInstanceOf[ListDefinition]
  private val _detail = _definitions(1).screen.asInstanceOf[DetailDefinition]
  private val _opaque = for {
    text <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
    suffix <- Gen.oneOf("Ω:内部.Upper", "é/Case punctuation", "日本語:MiXeD", "β.internal")
  } yield text + ":" + suffix
  private val _kinds = for {
    mask <- Gen.choose(1, 31)
    subset = ValueKind.values.toVector.zipWithIndex.collect { case (kind, index) if (mask & (1 << index)) != 0 => kind }
    permutation <- Gen.oneOf(subset.permutations.toVector)
  } yield permutation

  private def _field(id: String): FieldDefinition = {
    FieldDefinition(id, "  Label Ω  ", PresentationRole.Content, ValueDefinition("shared:Binding", Vector(ValueKind.Text)))
  }
  private def _action(id: String): ActionDefinition = {
    ActionDefinition(id, "  Action Ω  ", "shared:Binding")
  }
  private def _pair(list: ListDefinition, detail: DetailDefinition): Vector[Definition] = {
    Vector(_definitions.head.copy(screen = list), _definitions(1).copy(screen = detail))
  }
  private def _preserved(result: Consequence[AdmittedDefinitions], input: Vector[Definition]): Boolean = {
    result match {
      case Consequence.Success(admitted) =>
        (admitted.definitions eq input) && admitted.definitions == input &&
          admitted.definitions.zip(input).forall { case (actual, expected) => actual eq expected }
      case _ => false
    }
  }
  private def _diagnostic(result: Consequence[AdmittedDefinitions]): Option[Diagnostic] = {
    result match {
      case failure: Consequence.Failure[?] if failure.conclusion.observation.taxonomy == Taxonomy.argumentInvalid =>
        failure.conclusion.observation.cause.getException.collect { case carrier: AdmissionException => carrier.diagnostic }
      case _ => None
    }
  }
  private def _rejected(result: Consequence[AdmittedDefinitions], code: String, path: String): Boolean = {
    _diagnostic(result).exists(diagnostic => diagnostic.code == "LUI96_" + code && diagnostic.path == path)
  }

  "Generated Logical UI admission laws" should {
    "preserve opaque data and ordered collections" which {
      "retains generated Unicode punctuation case source revisions and collection permutations verbatim" in {
        Given("unique generated opaque IDs, independent arbitrary revisions and rotated or reversed field/action orders")
        val geninput = for {
          token <- _opaque
          revision <- _opaque
          count <- Gen.choose(2, 12)
          offset <- Gen.choose(0, count - 1)
          reversed <- Gen.oneOf(true, false)
          detailfirst <- Gen.oneOf(true, false)
        } yield {
          val ids = Vector.tabulate(count)(index => token + ":" + index)
          val rotated = ids.drop(offset) ++ ids.take(offset)
          val ordered = if (reversed) rotated.reverse else rotated
          val fields = ordered.map(_field)
          val actions = ordered.map(_action)
          val listid = "List:" + token
          val detailid = "Detail:" + token
          val provenance = SourceProvenance("Producer:" + token, "Source:" + token, revision)
          val input = Vector(
            _definitions.head.copy(screenId = listid, definitionRevision = revision, provenance = provenance,
              viewBindingId = token, screen = _list.copy(itemFields = fields, actions = actions,
                itemActions = actions.reverse, detailTarget = DetailTarget(detailid))),
            _definitions(1).copy(screenId = detailid, definitionRevision = revision, provenance = provenance,
              viewBindingId = token, screen = _detail.copy(sections = Vector(SectionDefinition(token, Some("  Section  "), fields.reverse)), actions = actions))
          )
          if (detailfirst) input.reverse else input
        }
        When("generated complete provider vectors are admitted")
        val property = Prop.forAll(geninput) { input => _preserved(admitC(input), input) }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("all generated raw vectors and objects survive with at least one hundred successful cases")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }
    }

    "preserve the closed value-kind algebra" which {
      "admits every generated nonempty unique content subset permutation and rejects its repeated member" in {
        Given("a generated nonempty unique ordered subset of the five closed kinds")
        val geninput = for {
          kinds <- _kinds
          index <- Gen.choose(0, kinds.size - 1)
        } yield (kinds, index)
        When("each subset and a repeated member appended to it are independently admitted")
        val property = Prop.forAll(geninput) { case (kinds, index) =>
          val field = _field("content").copy(value = ValueDefinition("binding", kinds))
          val valid = _pair(_list.copy(itemFields = Vector(field)), _detail)
          val invalid = _pair(_list.copy(itemFields = Vector(field.copy(value = field.value.copy(allowedKinds = kinds :+ kinds(index))))), _detail)
          val first = admitC(invalid)
          val second = admitC(invalid)
          _preserved(admitC(valid), valid) &&
            _rejected(first, "VALUE_KIND_INVALID", s"definitions[0].screen.itemFields[0].value.allowedKinds[${kinds.size}]") &&
            _diagnostic(first) == _diagnostic(second)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("content subsets retain supplied order and repeated kinds have deterministic typed code and path")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }

      "requires exactly Text for every generated non-content role allowance" in {
        Given("a non-content role and a generated unique nonempty kind permutation")
        val geninput = for {
          role <- Gen.oneOf(PresentationRole.Title, PresentationRole.Subtitle, PresentationRole.Description, PresentationRole.Status)
          kinds <- _kinds
        } yield (role, kinds)
        When("the role-kind combination is admitted")
        val property = Prop.forAll(geninput) { case (role, kinds) =>
          val input = _pair(_list.copy(itemFields = Vector(_field("role").copy(role = role, value = ValueDefinition("binding", kinds)))), _detail)
          val result = admitC(input)
          if (kinds == Vector(ValueKind.Text)) _preserved(result, input)
          else _rejected(result, "ROLE_INVALID", "definitions[0].screen.itemFields[0].role")
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("only the independently stated singleton text allowance succeeds")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }
    }

    "respect complete scoped identity" which {
      "allows reuse across containers and rejects a duplicate at any generated later index in its owning scope" in {
        Given("unique generated container IDs reused independently and an arbitrary later duplicate position")
        val geninput = for {
          token <- _opaque
          count <- Gen.choose(2, 12)
          previous <- Gen.choose(0, count - 1)
          scope <- Gen.oneOf("listfields", "detailfields", "sections", "listactions", "itemactions", "detailactions", "screens")
        } yield (token, count, previous, scope)
        When("the scoped reuse vector and one prohibited later duplicate are admitted repeatedly")
        val property = Prop.forAll(geninput) { case (token, count, previous, scope) =>
          val ids = Vector.tabulate(count)(index => token + ":" + index)
          val fields = ids.map(_field)
          val actions = ids.map(_action)
          val sections = ids.map(id => SectionDefinition(id, None, fields))
          val valid = _pair(_list.copy(itemFields = fields, actions = actions, itemActions = actions),
            _detail.copy(sections = sections, actions = actions))
          val (invalid, path) = scope match {
            case "listfields" => (_pair(_list.copy(itemFields = fields :+ fields(previous)), _detail), s"definitions[0].screen.itemFields[$count].id")
            case "detailfields" => (_pair(_list, _detail.copy(sections = Vector(SectionDefinition("main", None, fields :+ fields(previous))))), s"definitions[1].screen.sections[0].fields[$count].id")
            case "sections" => (_pair(_list, _detail.copy(sections = sections :+ sections(previous))), s"definitions[1].screen.sections[$count].id")
            case "listactions" => (_pair(_list.copy(actions = actions :+ actions(previous)), _detail), s"definitions[0].screen.actions[$count].id")
            case "itemactions" => (_pair(_list.copy(itemActions = actions :+ actions(previous)), _detail), s"definitions[0].screen.itemActions[$count].id")
            case "detailactions" => (_pair(_list, _detail.copy(actions = actions :+ actions(previous))), s"definitions[1].screen.actions[$count].id")
            case _ =>
              val screens = Vector.tabulate(count)(index => _definitions(1).copy(screenId = ids(index)))
              (screens :+ screens(previous), s"definitions[$count].screenId")
          }
          val first = admitC(invalid)
          val second = admitC(invalid)
          _preserved(admitC(valid), valid) && _rejected(first, "DUPLICATE_ID", path) &&
            _diagnostic(first) == _diagnostic(second)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("independent scopes permit reuse while later duplicates identify their exact scope and repeated index")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }
    }

    "resolve the complete selected vector" which {
      "resolves generated target identities independently of order and rejects removal of the matching Detail" in {
        Given("generated opaque List and Detail identities with both provider orders")
        val geninput = for {
          token <- _opaque
          detailfirst <- Gen.oneOf(true, false)
        } yield {
          val detailid = "Detail:" + token
          val list = _definitions.head.copy(screenId = "List:" + token, screen = _list.copy(detailTarget = DetailTarget(detailid)))
          val detail = _definitions(1).copy(screenId = detailid)
          if (detailfirst) Vector(detail, list) else Vector(list, detail)
        }
        When("both complete orders and the selection with its Detail removed are admitted")
        val property = Prop.forAll(geninput) { input =>
          val missing = input.filter(_.screen.isInstanceOf[ListDefinition])
          val reversed = input.reverse
          _preserved(admitC(input), input) && _preserved(admitC(reversed), reversed) &&
            _rejected(admitC(missing), "DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget.screenId")
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("target existence is independent of order, returned order is preserved and foreign selections cannot repair removal")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
      }
    }
  }
}
