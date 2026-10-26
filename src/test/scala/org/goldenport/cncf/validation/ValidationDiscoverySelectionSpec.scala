package org.goldenport.cncf.validation

import java.nio.file.Path
import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.observability.ConclusionDiagnostics
import ValidationDiscoveryV1.*
import ValidationMetadataV1.*

/** Pure consumer queries and exact supplemental reference behavior over real inventory.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
@annotation.ValidationSpecification(declaration = classOf[AdmissionTestDeclaration])
class ValidationDiscoverySelectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ValidationDiscoveryV1 selection" should {
    "query every independent purpose over actual registered operations" in {
      Given("real fixture metadata with defaults, replacements and multi-purpose memberships")
      val inventory = _inventory()
      val expected = Vector(
        Purpose.SMOKE -> Set("focused union", "parameter case 1", "word behavior should inherit its anchor"),
        Purpose.FOCUSED -> Set("focused union", "parameter case 1", "word behavior should inherit its anchor", "focused feature a", "focused feature b"),
        Purpose.ADMISSION -> Set("inherits defaults", "explicit empty provider", "word behavior should use an unannotated override"),
        Purpose.FULL -> Set("inherits defaults", "explicit empty provider", "word behavior should use an unannotated override"),
        Purpose.HEAVY -> Set("heavy replacement", "parameter case 2"))
      When("each public purpose is used independently")
      val selections = expected.map { case (purpose, names) =>
        (purpose, names, _select(inventory, SelectionQuery(purposes = Set(purpose))))
      }
      Then("only declared memberships are returned and every purpose is exercised")
      selections.map(_._1).toSet shouldBe Purpose.values.toSet
      selections.foreach { case (_, names, operations) => operations.map(_.selector.testName).toSet shouldBe names }
      selections.find(_._1 == Purpose.HEAVY).get._3.foreach { operation =>
        operation.metadata.get.purposes shouldBe Set(Purpose.HEAVY)
      }
    }

    "apply purpose OR and feature OR with AND between dimensions" in {
      Given("FOCUSED registrations covering A only, B only and their union")
      val inventory = _inventory()
      val featurea = FeatureReference("component:fixture", "feature-a")
      val featureb = FeatureReference("component:fixture", "feature-b")
      When("feature union and multi-purpose queries select actual scenarios")
      val focuseda = _select(inventory, SelectionQuery(Set(Purpose.FOCUSED), Set(featurea)))
      val focusedb = _select(inventory, SelectionQuery(Set(Purpose.FOCUSED), Set(featureb)))
      val focusedunion = _select(inventory, SelectionQuery(Set(Purpose.FOCUSED), Set(featurea, featureb)))
      val purposes = _select(inventory, SelectionQuery(Set(Purpose.SMOKE, Purpose.HEAVY)))
      Then("either feature matches within FOCUSED and either purpose matches without inferred membership")
      focusedunion.toSet shouldBe focuseda.toSet.union(focusedb.toSet)
      focusedunion.size shouldBe 5
      focuseda.map(_.selector.testName) should contain ("focused feature a")
      focuseda.map(_.selector.testName) should not contain "focused feature b"
      focusedb.map(_.selector.testName) should contain ("focused feature b")
      focusedb.map(_.selector.testName) should not contain "focused feature a"
      purposes.map(_.selector.testName).toSet shouldBe Set("focused union", "parameter case 1",
        "word behavior should inherit its anchor", "heavy replacement", "parameter case 2")
    }

    "treat empty dimensions as no filter and preserve exact feature scopes" in {
      Given("a complete inventory containing declared and unannotated actual registrations")
      val inventory = _inventory()
      val featurea = FeatureReference("component:fixture", "feature-a")
      When("empty, feature-only and equal-label/different-scope queries are applied")
      val all = _select(inventory, SelectionQuery())
      val featureonly = _select(inventory, SelectionQuery(features = Set(featurea)))
      val other = _select(inventory, SelectionQuery(features = Set(featurea.copy(scope = "another:scope"))))
      Then("all valid rows remain available while opaque feature scopes are not inferred")
      all shouldBe inventory.operations
      all.map(_.selector.testName) should contain ("unannotated scenario")
      featureonly.map(_.selector.testName) should contain ("cleared purposes")
      featureonly.map(_.selector.testName) should not contain "unannotated scenario"
      other shouldBe empty
    }

    "resolve supplemental operations without changing metadata or execution requirements" in {
      Given("a SMOKE selection and an explicitly referenced HEAVY operation outside it")
      val inventory = _inventory()
      val supplemental = inventory.operations.find(_.selector.testName == "heavy replacement").get
      val original = supplemental.metadata.get
      When("the ordinary query and exact supplemental reference are resolved")
      val selected = _select(inventory, SelectionQuery(purposes = Set(Purpose.SMOKE)))
      val resolved = ValidationDiscoveryV1.resolveC(inventory, supplemental.reference).toOption.get
      Then("supplemental binding preserves its HEAVY membership and existing aggregate without weakening it")
      selected should not contain supplemental
      resolved shouldBe supplemental
      resolved.metadata shouldBe Some(original)
      resolved.metadata.get.purposes shouldBe Set(Purpose.HEAVY)
      resolved.metadata.get.executionRequirement shouldBe original.executionRequirement
      resolved.metadata.get.executionRequirement.get.capabilities.map(_.identity) shouldBe Vector("base")
      inventory.operations.find(_.reference == supplemental.reference).get shouldBe supplemental
    }

    "resolve a visible unannotated operation without assigning a purpose" in {
      Given("the real unannotated scenario and a purpose query that excludes it")
      val inventory = _inventory()
      val unannotated = inventory.operations.find(_.selector.testName == "unannotated scenario").get
      When("purpose selection and explicit supplemental resolution use the same inventory")
      val selected = _select(inventory, SelectionQuery(purposes = Set(Purpose.FULL)))
      val resolved = ValidationDiscoveryV1.resolveC(inventory, unannotated.reference).toOption.get
      Then("absence is valid visible metadata and exact resolution does not add membership")
      selected should not contain unannotated
      resolved.metadata.get.origins shouldBe Origins(false, false)
      resolved.metadata.get.purposes shouldBe empty
      resolved.selector shouldBe unannotated.selector
    }

    "distinguish absent, invalid and ambiguous supplemental references" in {
      Given("an inventory retaining invalid provider rows and two colliding logical references")
      val inventory = _inventory(invalid = true)
      val invalid = inventory.operations.find(_.selector.suiteClassName.endsWith("UnsupportedAbiFixtureSpec")).get
      val collision = OperationReference("component:fixture", "collision:spec", "collision:operation")
      val absent = OperationReference("component:fixture", "absent:spec", "absent:operation")
      When("each exact reference is resolved through the public API")
      val outcomes = Vector(ValidationDiscoveryV1.resolveC(inventory, absent), ValidationDiscoveryV1.resolveC(inventory, invalid.reference), ValidationDiscoveryV1.resolveC(inventory, collision))
      Then("structured policies distinguish missing rows from malformed interpretation and ambiguity")
      outcomes.map(_policy) shouldBe Vector(Some("validation.discovery.absent"),
        Some("validation.discovery.invalid"), Some("validation.discovery.ambiguous"))
      _select(inventory, SelectionQuery()).foreach(_.metadata should not be empty)
    }

    "retain incomplete scope context when no operation matches a query" in {
      Given("the real partial inventory and the same public inventory data marked unknown")
      val partial = _inventory(invalid = true)
      val unknown = partial.copy(completeness = Completeness.UNKNOWN)
      val scopes = Vector(partial, unknown)
      val original = scopes.map(x => (x.scope, x.completeness, x.diagnostics))
      When("a valid new feature reference selects no normalized operation")
      val results = scopes.map(x => _select(x, SelectionQuery(Set(Purpose.FOCUSED), Set(FeatureReference("future:scope", "new-feature")))))
      Then("empty query results leave completeness and scope diagnostics intact for consumer interpretation")
      partial.completeness shouldBe Completeness.PARTIAL
      results.foreach(_ shouldBe empty)
      scopes.map(x => (x.scope, x.completeness, x.diagnostics)) shouldBe original
      partial.diagnostics should not be empty
    }

    "return deterministic selections under generated purpose and feature insertion permutations" in {
      Given("real normalized inventory and permutations of purpose/feature sets")
      val inventory = _inventory()
      val purposes = Purpose.values.toVector
      val features = Vector(FeatureReference("component:fixture", "feature-a"), FeatureReference("component:fixture", "feature-b"))
      val property = Prop.forAll(Gen.pick(purposes.size, purposes), Gen.pick(features.size, features)) { (ordered, scoped) =>
        val query = SelectionQuery(ordered.toSet, scoped.toSet)
        _select(inventory, query) == _select(inventory, SelectionQuery(purposes.toSet, features.toSet))
      }
      When("generated equivalent typed queries select repeatedly")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
      Then("selection order and original metadata are stable")
      checked.passed shouldBe true
      _select(inventory, SelectionQuery()).map(_.reference) shouldBe inventory.operations.map(_.reference)
    }

    "reject malformed query and reference boundaries without exceptions" in {
      Given("null queries, null sets/members and malformed scoped feature/reference text")
      val inventory = _inventory()
      val queries = Vector(null, SelectionQuery(purposes = null), SelectionQuery(features = null),
        SelectionQuery(purposes = Set(null.asInstanceOf[Purpose])), SelectionQuery(features = Set(null)),
        SelectionQuery(features = Set(FeatureReference(" scope", "id"))),
        SelectionQuery(features = Set(FeatureReference("scope", "id\n"))),
        SelectionQuery(features = Set(FeatureReference("scope", "i" * 513))))
      val references = Vector(null, OperationReference(null, "spec", "op"), OperationReference("component", "", "op"),
        OperationReference("component", "spec", " op"), OperationReference("component", "spec", "op\u0000"))
      When("the public consumer boundaries admit each input")
      val queryoutcomes = queries.map(selectC(inventory, _))
      val referenceoutcomes = references.map(ValidationDiscoveryV1.resolveC(inventory, _))
      Then("all malformed values produce validation.discovery policy failures")
      (queryoutcomes ++ referenceoutcomes).foreach { outcome =>
        outcome shouldBe a[Consequence.Failure[?]]
        _policy(outcome).exists(_.startsWith("validation.discovery.")) shouldBe true
      }
    }

    "reject unsupported ABI and malformed consumer inventory fields" in {
      Given("real inventory copied with unsupported ABI, null fields and malformed normalized metadata")
      val inventory = _inventory()
      val operation = inventory.operations.head
      val malformed = Vector(operation.copy(provenance = operation.provenance.copy(abiVersion = 2)),
        operation.copy(reference = null), operation.copy(selector = null), operation.copy(provenance = null),
        operation.copy(metadata = null), operation.copy(metadata = Some(null)), operation.copy(diagnostics = null),
        operation.copy(selector = operation.selector.copy(suiteClassName = "bad..Class")),
        operation.copy(selector = operation.selector.copy(testName = "")),
        operation.copy(metadata = operation.metadata.map(_.copy(origins = null))),
        operation.copy(metadata = operation.metadata.map(_.copy(purposes = null))),
        operation.copy(diagnostics = Vector(Diagnostic("unknown-code", "source", "message"))),
        operation.copy(diagnostics = Vector(Diagnostic("metadata", "s" * 513, "message"))))
      val invalidinventories = malformed.map(x => inventory.copy(operations = Vector(x))) ++ Vector(
        null, inventory.copy(scope = null), inventory.copy(completeness = null), inventory.copy(operations = null),
        inventory.copy(operations = Vector(null)), inventory.copy(diagnostics = null),
        inventory.copy(scope = inventory.scope.copy(artifacts = null)))
      When("query and exact resolution admit each copied consumer inventory")
      val selections = invalidinventories.map(selectC(_, SelectionQuery()))
      val resolutions = invalidinventories.map(ValidationDiscoveryV1.resolveC(_, operation.reference))
      Then("malformed and incompatible inventories fail before they can return executable rows")
      (selections ++ resolutions).foreach(_ shouldBe a[Consequence.Failure[?]])
      _policy(selections.head) shouldBe Some("validation.discovery.abi")
    }

    "exclude ambiguous consumer rows even when their copied metadata is valid" in {
      Given("a public inventory containing duplicate logical references with valid copied metadata")
      val inventory = _inventory()
      val operation = inventory.operations.head
      val duplicate = inventory.copy(operations = Vector(operation, operation))
      When("pure selection and supplemental resolution encounter that duplicate reference")
      val selected = _select(duplicate, SelectionQuery())
      val resolved = ValidationDiscoveryV1.resolveC(duplicate, operation.reference)
      Then("no duplicate becomes an executable query result and exact resolution reports ambiguity")
      selected shouldBe empty
      _policy(resolved) shouldBe Some("validation.discovery.ambiguous")
    }

    "admit explicit complete empty inventories without touching their local artifact roots" in {
      Given("an explicit empty scope with complete discovery semantics")
      val scope = DiscoveryScope("empty-scope", "revision-1", Vector.empty)
      val inventory = Inventory(scope, Completeness.COMPLETE, Vector.empty, Vector.empty)
      When("selection and an absent supplemental reference use only the inventory data")
      val selected = selectC(inventory, SelectionQuery())
      val resolved = ValidationDiscoveryV1.resolveC(inventory, OperationReference("component", "specification", "operation"))
      Then("selection succeeds with no rows and resolution reports an absent reference")
      selected shouldBe Consequence.success(Vector.empty)
      _policy(resolved) shouldBe Some("validation.discovery.absent")
    }
  }

  private def _inventory(invalid: Boolean = false): Inventory = {
    val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val prefix = "org.goldenport.cncf.validation.fixtures.phase103." + (if (invalid) "invalid" else "valid")
    val artifact = ArtifactScope("component:fixture", "artifact:tests", "artifact-1", "source-1", root, prefix)
    discoverC(DiscoveryScope("fixture-scope", "scope-1", Vector(artifact)), getClass.getClassLoader).toOption
      .getOrElse(fail("valid explicit scope must produce inventory"))
  }

  private def _select(inventory: Inventory, query: SelectionQuery): Vector[Operation] =
    selectC(inventory, query).toOption.getOrElse(fail("valid inventory/query must select"))

  private def _policy[A](result: Consequence[A]): Option[String] = result match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => fail("expected structured discovery failure")
  }
}
