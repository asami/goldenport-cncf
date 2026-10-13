package org.goldenport.cncf.display

import logicalui.ExpectedGeneratedLogicalUiFixture
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class GeneratedLogicalUiAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*

  private val _definitions = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _list = _definitions.head.screen.asInstanceOf[ListDefinition]
  private val _detail = _definitions(1).screen.asInstanceOf[DetailDefinition]
  private val _field = _list.itemFields.head
  private val _action = _detail.actions.head
  private final case class InvalidCase(input: Vector[Definition], code: String, path: String)

  private def _with_list(list: ListDefinition): Vector[Definition] = {
    _definitions.updated(0, _definitions.head.copy(screen = list))
  }
  private def _with_detail(detail: DetailDefinition): Vector[Definition] = {
    _definitions.updated(1, _definitions(1).copy(screen = detail))
  }
  private def _with_field(field: FieldDefinition): Vector[Definition] = {
    _with_list(_list.copy(itemFields = Vector(field)))
  }
  private def _diagnostic(result: Consequence[AdmittedDefinitions]): Diagnostic = {
    result match {
      case failure: Consequence.Failure[?] =>
        failure.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
        failure.conclusion.observation.cause.getException match {
          case Some(carrier: AdmissionException) => carrier.diagnostic
          case _ => fail("admission failure must carry the typed diagnostic")
        }
      case _ => fail("invalid definitions must produce a structured failure")
    }
  }
  private def _expect_cases(cases: Vector[InvalidCase], results: Vector[Consequence[AdmittedDefinitions]]): Unit = {
    results.zip(cases).foreach { case (result, expected) =>
      val diagnostic = _diagnostic(result)
      diagnostic.code shouldBe "LUI96_" + expected.code
      diagnostic.path shouldBe expected.path
      diagnostic.reason should not be empty
    }
  }
  private def _preserved(result: Consequence[AdmittedDefinitions], input: Vector[Definition]): Unit = {
    result match {
      case Consequence.Success(admitted) =>
        admitted.definitions shouldBe input
        (admitted.definitions eq input) shouldBe true
        admitted.definitions.zip(input).foreach { case (actual, expected) => (actual eq expected) shouldBe true }
      case _ => fail("valid definitions must be admitted in full")
    }
  }

  "Generated Logical UI ABI" should {
    "preserve raw public definitions behind atomic admission" which {
      "admits the external expected-generated ESS2-01 pair verbatim" in {
        Given("the hand-written external-package provider with independently constructed descriptor")
        val input = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
        When("the complete public provider vector is admitted")
        val result = admitC(input)
        Then("the same vector and definition objects retain the exact fixture identities and order")
        _preserved(result, input)
        input.map(_.screenId) shouldBe Vector("collecting-resources", "capture-detail")
        input.map(_.semanticContract) shouldBe Vector(semanticContract, semanticContract)
        input.map(_.provenance) shouldBe Vector.fill(2)(SourceProvenance("cncf.expected-generated-fixture", "ess2-01", "fixture-1"))
        _list.itemFields.map(field => (field.id, field.label, field.role, field.value.bindingId)) shouldBe Vector(
          ("title", "Title", PresentationRole.Title, "ess.title"),
          ("stage", "Capture stage", PresentationRole.Status, "ess.stage"))
        _detail.sections.head.fields.map(_.id) shouldBe Vector("title", "stage", "description")
        _detail.actions shouldBe Vector(ActionDefinition("request-review", "Request Review", "ess.request-review"))
      }

      "admits unreferenced Details and empty nested containers without text repair" in {
        Given("Detail-only and List/Detail definitions with None titles and spaced nonblank display text")
        val detailonly = Vector(_definitions(1).copy(screen = DetailDefinition("  Detail  ", Vector.empty, Vector.empty)))
        val input = _with_list(ListDefinition("  List  ", Vector.empty, DetailTarget("capture-detail"), Vector.empty, Vector.empty))
          .updated(1, _definitions(1).copy(screen = DetailDefinition(" Detail ", Vector(
            SectionDefinition("empty", None, Vector.empty),
            SectionDefinition("spaced", Some("  Section  "), Vector(_field.copy(label = " Label ")))
          ), Vector.empty)))
        When("both complete vectors are independently admitted")
        val detailresult = admitC(detailonly)
        val result = admitC(input)
        Then("empty containers, optional titles and surrounding display spaces survive unchanged")
        _preserved(detailresult, detailonly)
        _preserved(result, input)
      }

      "prevents external construction factory and copy bypasses by type interpretation" in {
        Given("raw definitions visible to a source in package logicalui")
        val fixture = ExpectedGeneratedLogicalUiFixture
        When("external-package ScalaTest compile-time access probes are interpreted")
        val result = fixture.verifyAdmissionTypeBoundary()
        val admitted = admitC(_definitions)
        Then("constructor, factory and copy attempts are type errors while the public gate is usable")
        result shouldBe (())
        _preserved(admitted, _definitions)
      }
    }

    "reject malformed structure through specific typed diagnostics" which {
      "guards every root and nested null or optional-container boundary before dereference" in {
        Given("null roots, screens, nested vectors and elements, optional titles and value structures")
        val section = _detail.sections.head
        val cases = Vector(
          InvalidCase(null, "ELEMENT_INVALID", "definitions"),
          InvalidCase(Vector.empty, "DEFINITIONS_MISSING", "definitions"),
          InvalidCase(Vector(null), "ELEMENT_INVALID", "definitions[0]"),
          InvalidCase(Vector(_definitions.head.copy(screen = null)), "ELEMENT_INVALID", "definitions[0].screen"),
          InvalidCase(_with_list(_list.copy(itemFields = null)), "ELEMENT_INVALID", "definitions[0].screen.itemFields"),
          InvalidCase(_with_list(_list.copy(itemFields = Vector(null))), "ELEMENT_INVALID", "definitions[0].screen.itemFields[0]"),
          InvalidCase(_with_list(_list.copy(actions = null)), "ELEMENT_INVALID", "definitions[0].screen.actions"),
          InvalidCase(_with_list(_list.copy(actions = Vector(null))), "ELEMENT_INVALID", "definitions[0].screen.actions[0]"),
          InvalidCase(_with_list(_list.copy(itemActions = null)), "ELEMENT_INVALID", "definitions[0].screen.itemActions"),
          InvalidCase(_with_list(_list.copy(itemActions = Vector(null))), "ELEMENT_INVALID", "definitions[0].screen.itemActions[0]"),
          InvalidCase(_with_detail(_detail.copy(sections = null)), "ELEMENT_INVALID", "definitions[1].screen.sections"),
          InvalidCase(_with_detail(_detail.copy(sections = Vector(null))), "ELEMENT_INVALID", "definitions[1].screen.sections[0]"),
          InvalidCase(_with_detail(_detail.copy(actions = null)), "ELEMENT_INVALID", "definitions[1].screen.actions"),
          InvalidCase(_with_detail(_detail.copy(actions = Vector(null))), "ELEMENT_INVALID", "definitions[1].screen.actions[0]"),
          InvalidCase(_with_detail(_detail.copy(sections = Vector(section.copy(title = null)))), "ELEMENT_INVALID", "definitions[1].screen.sections[0].title"),
          InvalidCase(_with_detail(_detail.copy(sections = Vector(section.copy(title = Some(null))))), "ELEMENT_INVALID", "definitions[1].screen.sections[0].title"),
          InvalidCase(_with_detail(_detail.copy(sections = Vector(section.copy(fields = null)))), "ELEMENT_INVALID", "definitions[1].screen.sections[0].fields"),
          InvalidCase(_with_detail(_detail.copy(sections = Vector(section.copy(fields = Vector(null))))), "ELEMENT_INVALID", "definitions[1].screen.sections[0].fields[0]"),
          InvalidCase(_with_field(_field.copy(value = null)), "ELEMENT_INVALID", "definitions[0].screen.itemFields[0].value"),
          InvalidCase(_with_field(_field.copy(value = _field.value.copy(allowedKinds = null))), "ELEMENT_INVALID", "definitions[0].screen.itemFields[0].value.allowedKinds"),
          InvalidCase(_with_field(_field.copy(role = null)), "ROLE_INVALID", "definitions[0].screen.itemFields[0].role"),
          InvalidCase(_definitions.updated(0, _definitions.head.copy(provenance = null)), "PROVENANCE_INVALID", "definitions[0].provenance"),
          InvalidCase(_definitions.updated(0, _definitions.head.copy(semanticContract = null)), "CONTRACT_INCOMPATIBLE", "definitions[0].semanticContract")
        )
        When("each malformed complete vector reaches the public admission gate")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("every boundary returns its exact typed code and constructor-member path without throwing")
        _expect_cases(cases, results)
      }

      "rejects empty padded and null opaque scalars with their declared taxonomy" in {
        Given("each ordinary identity, binding, definition revision and provenance member with malformed opaque text")
        val values = Vector[String]("", " ", " padded", "padded ", null)
        val cases = values.flatMap { value =>
          val definition = _definitions.head
          Vector(
            InvalidCase(_definitions.updated(0, definition.copy(screenId = value)), "ID_INVALID", "definitions[0].screenId"),
            InvalidCase(_definitions.updated(0, definition.copy(definitionRevision = value)), "ID_INVALID", "definitions[0].definitionRevision"),
            InvalidCase(_definitions.updated(0, definition.copy(viewBindingId = value)), "ID_INVALID", "definitions[0].viewBindingId"),
            InvalidCase(_with_field(_field.copy(id = value)), "ID_INVALID", "definitions[0].screen.itemFields[0].id"),
            InvalidCase(_with_field(_field.copy(value = _field.value.copy(bindingId = value))), "ID_INVALID", "definitions[0].screen.itemFields[0].value.bindingId"),
            InvalidCase(_with_detail(_detail.copy(sections = Vector(_detail.sections.head.copy(id = value)))), "ID_INVALID", "definitions[1].screen.sections[0].id"),
            InvalidCase(_with_detail(_detail.copy(actions = Vector(_action.copy(id = value)))), "ID_INVALID", "definitions[1].screen.actions[0].id"),
            InvalidCase(_with_detail(_detail.copy(actions = Vector(_action.copy(bindingId = value)))), "ID_INVALID", "definitions[1].screen.actions[0].bindingId"),
            InvalidCase(_definitions.updated(0, definition.copy(provenance = definition.provenance.copy(producerId = value))), "PROVENANCE_INVALID", "definitions[0].provenance.producerId"),
            InvalidCase(_definitions.updated(0, definition.copy(provenance = definition.provenance.copy(sourceId = value))), "PROVENANCE_INVALID", "definitions[0].provenance.sourceId"),
            InvalidCase(_definitions.updated(0, definition.copy(provenance = definition.provenance.copy(revision = value))), "PROVENANCE_INVALID", "definitions[0].provenance.revision")
          )
        }
        When("every scalar mutation is admitted independently")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("opaque scalar rejection never trims, repairs or substitutes a source revision")
        _expect_cases(cases, results)
      }

      "rejects blank display text while preserving all nonblank supplied spacing" in {
        Given("null and blank titles, field labels, section titles and action labels")
        val cases = Vector[String](null, "", " \t\n ").flatMap { value =>
          Vector(
            InvalidCase(_with_list(_list.copy(title = value)), "TEXT_INVALID", "definitions[0].screen.title"),
            InvalidCase(_with_detail(_detail.copy(title = value)), "TEXT_INVALID", "definitions[1].screen.title"),
            InvalidCase(_with_field(_field.copy(label = value)), "TEXT_INVALID", "definitions[0].screen.itemFields[0].label"),
            InvalidCase(_with_detail(_detail.copy(sections = Vector(_detail.sections.head.copy(title = Some(value))))),
              if (value == null) "ELEMENT_INVALID" else "TEXT_INVALID", "definitions[1].screen.sections[0].title"),
            InvalidCase(_with_detail(_detail.copy(actions = Vector(_action.copy(label = value)))), "TEXT_INVALID", "definitions[1].screen.actions[0].label")
          )
        }
        val spaced = _with_field(_field.copy(label = " \tTitle\n "))
        When("the malformed and nonblank display-text definitions are admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        val preserved = admitC(spaced)
        Then("blank text fails specifically and nonblank text remains verbatim")
        _expect_cases(cases, results)
        _preserved(preserved, spaced)
      }
    }

    "require exact ABI and semantic descriptor while leaving source provenance opaque" which {
      "rejects each independently changed or null ABI and semantic descriptor member" in {
        Given("independent ABI mutations and every full semantic descriptor or driver member mutation")
        val contract = semanticContract
        val driver = contract.driver
        val changed = Vector[String]("future", null).flatMap { value => Vector(
          contract.copy(schema = value), contract.copy(semanticAuthoritySchema = value), contract.copy(assurance = value),
          contract.copy(driver = driver.copy(repository = value)), contract.copy(driver = driver.copy(revision = value)),
          contract.copy(driver = driver.copy(path = value)), contract.copy(driver = driver.copy(scenarioId = value)))
        } ++ Vector(contract.copy(version = 2), contract.copy(driver = null), null)
        val cases = Vector[String](null, "", "cncf.logical-ui-generated.v2", " cncf.logical-ui-generated.v1 ").map { value =>
          InvalidCase(_definitions.updated(0, _definitions.head.copy(abiIdentity = value)), "ABI_INCOMPATIBLE", "definitions[0].abiIdentity")
        } ++ Vector(0, 2, -1).map { value =>
          InvalidCase(_definitions.updated(0, _definitions.head.copy(abiVersion = value)), "ABI_INCOMPATIBLE", "definitions[0].abiVersion")
        } ++ changed.map { value =>
          InvalidCase(_definitions.updated(0, _definitions.head.copy(semanticContract = value)), "CONTRACT_INCOMPATIBLE", "definitions[0].semanticContract")
        }
        When("each independently incompatible descriptor is admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("all descriptor members participate in exact compatibility with specific bounded diagnostics")
        _expect_cases(cases, results)
        identity shouldBe "cncf.logical-ui-generated.v1"
        version shouldBe 1
      }

      "accepts arbitrary valid producer source and revision labels independently of ABI versions" in {
        Given("valid opaque provenance labels unrelated to the expected-generated fixture producer")
        val input = _definitions.map(_.copy(provenance = SourceProvenance("別のProducer:A", "Source internal punctuation", "revision:Ω.900"),
          definitionRevision = "definition:Next.β"))
        When("the complete vector with different provenance and definition revisions is admitted")
        val result = admitC(input)
        Then("no producer commit or fixture-hash pinning changes the accepted raw data")
        _preserved(result, input)
      }
    }

    "enforce scoped identity and role uniqueness" which {
      "rejects the second repeated ID in every declared screen section field and action scope" in {
        Given("duplicates beyond two valid prefix elements in all owning containers")
        val fields = Vector.tabulate(3)(index => _field.copy(id = "field" + index, role = PresentationRole.Content))
        val actions = Vector.tabulate(3)(index => _action.copy(id = "action" + index))
        val sections = Vector.tabulate(3)(index => SectionDefinition("section" + index, None, Vector.empty))
        val detailfields = _detail.copy(sections = Vector(SectionDefinition("main", None, fields :+ fields(1))))
        val cases = Vector(
          InvalidCase(_definitions :+ _definitions(1).copy(definitionRevision = "another"), "DUPLICATE_ID", "definitions[2].screenId"),
          InvalidCase(_with_detail(_detail.copy(sections = sections :+ sections(1))), "DUPLICATE_ID", "definitions[1].screen.sections[3].id"),
          InvalidCase(_with_list(_list.copy(itemFields = fields :+ fields(1))), "DUPLICATE_ID", "definitions[0].screen.itemFields[3].id"),
          InvalidCase(_with_detail(detailfields), "DUPLICATE_ID", "definitions[1].screen.sections[0].fields[3].id"),
          InvalidCase(_with_list(_list.copy(actions = actions :+ actions(1))), "DUPLICATE_ID", "definitions[0].screen.actions[3].id"),
          InvalidCase(_with_list(_list.copy(itemActions = actions :+ actions(1))), "DUPLICATE_ID", "definitions[0].screen.itemActions[3].id"),
          InvalidCase(_with_detail(_detail.copy(actions = actions :+ actions(1))), "DUPLICATE_ID", "definitions[1].screen.actions[3].id")
        )
        When("the complete vectors with later duplicates are admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("each duplicate identifies its first repeated occurrence in the declared scope")
        _expect_cases(cases, results)
      }

      "rejects repeated non-content roles in either field container" in {
        Given("every non-content role repeated after two content fields in List and Detail containers")
        val cases = PresentationRole.values.toVector.filterNot(_ == PresentationRole.Content).flatMap { role =>
          val fields = Vector(_field.copy(role = role), _field.copy(id = "content1", role = PresentationRole.Content),
            _field.copy(id = "content2", role = PresentationRole.Content), _field.copy(id = "repeated", role = role))
          Vector(
            InvalidCase(_with_list(_list.copy(itemFields = fields)), "DUPLICATE_ROLE", "definitions[0].screen.itemFields[3].role"),
            InvalidCase(_with_detail(_detail.copy(sections = Vector(SectionDefinition("main", None, fields)))), "DUPLICATE_ROLE", "definitions[1].screen.sections[0].fields[3].role")
          )
        }
        When("each repeated role vector is admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("non-content uniqueness is local and complete rather than a prefix-only check")
        _expect_cases(cases, results)
      }

      "allows cross-container IDs binding reuse and multiple content roles" in {
        Given("the same IDs and bindings reused by independent field and action containers")
        val fields = Vector(_field.copy(id = "shared", role = PresentationRole.Content),
          _field.copy(id = "other", role = PresentationRole.Content))
        val actions = Vector(_action.copy(id = "shared", bindingId = "ess.title"))
        val input = _with_list(_list.copy(itemFields = fields, actions = actions, itemActions = actions))
          .updated(1, _definitions(1).copy(screen = _detail.copy(sections = Vector(
            SectionDefinition("main", None, fields), SectionDefinition("second", None, fields)), actions = actions)))
        When("the vector with reuse only across permitted scopes is admitted")
        val result = admitC(input)
        Then("scoped reuse and repeated Content roles remain unchanged")
        _preserved(result, input)
      }
    }

    "interpret only the closed role and value-kind vocabulary" which {
      "preserves all five exact role and kind tags and every content subset order" in {
        Given("all documented enum alternatives and every nonempty unique ordered kind subset")
        val subsets = ValueKind.values.toVector.combinations(1).toVector ++
          (2 to 5).toVector.flatMap(size => ValueKind.values.toVector.combinations(size).toVector)
        val inputs = subsets.flatMap(_.permutations.toVector).map { kinds =>
          _with_field(_field.copy(role = PresentationRole.Content, value = _field.value.copy(allowedKinds = kinds)))
        }
        When("the complete content-kind alternatives are admitted")
        val results = inputs.map(admitC)
        Then("all unique subsets including Missing retain their exact order and closed tags")
        results.zip(inputs).foreach { case (result, input) => _preserved(result, input) }
        PresentationRole.values.map(_.tag).toVector shouldBe Vector("content", "title", "subtitle", "description", "status")
        ValueKind.values.map(_.tag).toVector shouldBe Vector("text", "number", "boolean", "timestamp", "missing")
      }

      "rejects empty null and repeated kinds before non-content compatibility" in {
        Given("invalid kind members and every non-content role with a non-text-only allowance")
        val kindcases = Vector(
          Vector.empty[ValueKind] -> "definitions[0].screen.itemFields[0].value.allowedKinds",
          Vector[ValueKind](null) -> "definitions[0].screen.itemFields[0].value.allowedKinds[0]",
          Vector(ValueKind.Text, ValueKind.Missing, ValueKind.Text) -> "definitions[0].screen.itemFields[0].value.allowedKinds[2]")
          .map { case (kinds, path) => InvalidCase(_with_field(_field.copy(value = _field.value.copy(allowedKinds = kinds))), "VALUE_KIND_INVALID", path) }
        val rolecases = PresentationRole.values.toVector.filterNot(_ == PresentationRole.Content).flatMap { role =>
          Vector(Vector(ValueKind.Number), Vector(ValueKind.Missing), Vector(ValueKind.Text, ValueKind.Missing),
            Vector(ValueKind.Missing, ValueKind.Text)).map { kinds =>
            InvalidCase(_with_field(_field.copy(role = role, value = _field.value.copy(allowedKinds = kinds))),
              "ROLE_INVALID", "definitions[0].screen.itemFields[0].role")
          }
        }
        val cases = kindcases ++ rolecases
        When("each malformed or role-incompatible allowance is admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        Then("kind shape and member diagnostics precede the exact text-only role restriction")
        _expect_cases(cases, results)
      }

      "excludes invented role and kind alternatives at compile time" in {
        Given("the companion-qualified closed enums available to external generated source")
        val fixture = ExpectedGeneratedLogicalUiFixture
        When("external-package unknown-alternative type probes are interpreted")
        val result = fixture.verifyClosedVocabulary()
        Then("invented alternatives fail type interpretation without a decoder fallback")
        result shouldBe (())
      }
    }

    "resolve targets only after complete vector admission" which {
      "resolves forward and backward targets while preserving provider order" in {
        Given("the same List/Detail pair in both provider orders and an unreferenced Detail")
        val inputs = Vector(_definitions, _definitions.reverse, Vector(_definitions(1)))
        When("each complete provider selection is admitted")
        val results = inputs.map(admitC)
        Then("target lookup uses the complete selected vector and does not sort the returned definitions")
        results.zip(inputs).foreach { case (result, input) => _preserved(result, input) }
      }

      "rejects null malformed self missing wrong-kind and cross-vector targets" in {
        Given("target references with invalid shape or no same-vector Detail resolution")
        val otherlist = _definitions.head.copy(screenId = "capture-detail")
        val cases = Vector(
          InvalidCase(_with_list(_list.copy(detailTarget = null)), "DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget"),
          InvalidCase(Vector(_definitions.head), "DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget.screenId"),
          InvalidCase(Vector(_definitions.head, otherlist), "DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget.screenId")
        ) ++ Vector[String](null, "", " padded", "padded ", "collecting-resources", "missing-detail").map { value =>
          InvalidCase(_with_list(_list.copy(detailTarget = DetailTarget(value))), "DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget.screenId")
        }
        val otherselection = Vector(_definitions(1))
        When("each invalid selection and the independent Detail selection is admitted")
        val results = cases.map(testcase => admitC(testcase.input))
        val independent = admitC(otherselection)
        Then("a Detail admitted by another selection cannot satisfy any unresolved target")
        _expect_cases(cases, results)
        _preserved(independent, otherselection)
      }
    }

    "choose deterministic bounded first failures without exposing an admitted prefix" which {
      "orders shape ABI contract provenance scalars structure content and vector references" in {
        Given("simultaneous failures at successive traversal boundaries and invalid later elements")
        val definition = _definitions.head
        val badfields = Vector(_field.copy(label = ""), _field.copy(id = "other", role = PresentationRole.Content),
          _field.copy(id = "title", role = PresentationRole.Content))
        val cases = Vector(
          InvalidCase(Vector(definition.copy(abiIdentity = null, screen = null)), "ELEMENT_INVALID", "definitions[0].screen"),
          InvalidCase(_definitions.updated(0, definition.copy(abiIdentity = null, semanticContract = null)), "ABI_INCOMPATIBLE", "definitions[0].abiIdentity"),
          InvalidCase(_definitions.updated(0, definition.copy(semanticContract = null, provenance = null)), "CONTRACT_INCOMPATIBLE", "definitions[0].semanticContract"),
          InvalidCase(_definitions.updated(0, definition.copy(provenance = null, screenId = null)), "PROVENANCE_INVALID", "definitions[0].provenance"),
          InvalidCase(_with_list(_list.copy(itemFields = null)).updated(0, definition.copy(screenId = "", screen = _list.copy(itemFields = null))), "ID_INVALID", "definitions[0].screenId"),
          InvalidCase(_with_list(_list.copy(title = "", itemFields = null)), "TEXT_INVALID", "definitions[0].screen.title"),
          InvalidCase(_with_list(_list.copy(itemFields = badfields)), "DUPLICATE_ID", "definitions[0].screen.itemFields[2].id"),
          InvalidCase(_with_list(_list.copy(itemFields = Vector(_field.copy(label = ""), null))), "ELEMENT_INVALID", "definitions[0].screen.itemFields[1]"),
          InvalidCase(_with_list(_list.copy(actions = null, itemActions = null)), "ELEMENT_INVALID", "definitions[0].screen.actions"),
          InvalidCase(_with_list(_list.copy(itemFields = Vector(_field.copy(label = "", role = null, value = null)))), "TEXT_INVALID", "definitions[0].screen.itemFields[0].label"),
          InvalidCase(_with_list(_list.copy(itemFields = Vector(_field.copy(role = null, value = null)))), "ROLE_INVALID", "definitions[0].screen.itemFields[0].role"),
          InvalidCase(_with_list(_list.copy(detailTarget = null)) :+ _definitions(1), "DUPLICATE_ID", "definitions[2].screenId"),
          InvalidCase(_with_list(_list.copy(detailTarget = null)).updated(1, _definitions(1).copy(abiVersion = 2)), "ABI_INCOMPATIBLE", "definitions[1].abiVersion"),
          InvalidCase(_definitions :+ _definitions(1).copy(screenId = "later", screen = _detail.copy(actions = Vector(_action.copy(label = "")))), "TEXT_INVALID", "definitions[2].screen.actions[0].label"),
          InvalidCase(_definitions.updated(0, definition.copy(screenId = "")).updated(1, _definitions(1).copy(semanticContract = null)), "ID_INVALID", "definitions[0].screenId")
        )
        When("every simultaneous-failure vector is admitted twice")
        val first = cases.map(testcase => admitC(testcase.input))
        val second = cases.map(testcase => admitC(testcase.input))
        Then("the declared first code and path are stable and no successful prefix escapes")
        _expect_cases(cases, first)
        first.map(_diagnostic) shouldBe second.map(_diagnostic)
      }

      "keeps attacker-controlled rejected values out of cause messages and diagnostics" in {
        Given("a malformed opaque identifier carrying an attacker-chosen secret marker")
        val marker = "secret-attacker-value"
        val input = _definitions.updated(0, _definitions.head.copy(screenId = " " + marker))
        When("the malicious vector is admitted repeatedly")
        val first = admitC(input)
        val second = admitC(input)
        Then("the typed diagnostics agree and the structured carrier message contains only bounded contract explanations")
        _diagnostic(first) shouldBe _diagnostic(second)
        _diagnostic(first).toString should not include marker
        first match {
          case failure: Consequence.Failure[?] =>
            failure.conclusion.observation.cause.getException.get.getMessage should not include marker
          case _ => fail("malformed identifier must fail atomically")
        }
      }
    }
  }
}
