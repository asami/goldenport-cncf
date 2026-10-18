package org.goldenport.cncf.validation

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.processexecution.{SbtOperation, SbtScenarioSelection, SbtTarget}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import ValidationDiscoveryV1.*
import ValidationMetadataV1.{FeatureReference, Purpose}

/** Public exact binding over real discovery, without acquiring execution authority.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ValidationOperationBindingV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _workspace_id = WorkspaceId.parseC("fixture").toOption.get

  "Validation operation binding" should {
    "bind the metadata-selected parameter and the same supplemental reference identically" in {
      Given("real scoped discovery and a FOCUSED feature query containing parameter:1")
      val inventory = _inventory()
      val selected = selectC(inventory, SelectionQuery(Set(Purpose.FOCUSED),
        Set(FeatureReference("component:fixture", "feature-b")))).toOption.get
        .find(_.reference.operation == "parameter:1").get
      When("a selected reference and the identical explicit supplemental reference are bound")
      val queried = ValidationOperationBindingV1.bindC(inventory, selected.reference, _workspace_id).toOption.get
      val supplemental = ValidationOperationBindingV1.bindC(inventory, selected.reference, _workspace_id).toOption.get
      Then("both retain the original metadata, execution requirement, provenance and exact typed intent")
      queried.operation shouldBe selected
      supplemental.operation shouldBe selected
      supplemental.intent shouldBe queried.intent
      queried.intent shouldBe UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id,
        Some(SbtScenarioSelection(selected.selector.suiteClassName, "parameter case 1"))), SbtOperation.TestOnly)
      queried.operation.provenance shouldBe selected.provenance
      queried.operation.metadata shouldBe selected.metadata
      queried.operation.metadata.get.executionRequirement shouldBe selected.metadata.get.executionRequirement
      queried.operation.metadata.get.executionRequirement.get.reviewRequired shouldBe true
      queried.operation.metadata.get.executionRequirement.get.capabilities.map(_.identity) shouldBe Vector("base", "extra")
    }

    "bind supplemental HEAVY and unannotated operations without changing their declarations" in {
      Given("actual HEAVY and unannotated registrations excluded by an ADMISSION query")
      val inventory = _inventory()
      val originals = Vector("parameter case 2", "unannotated scenario").map { name =>
        inventory.operations.find(_.selector.testName == name).get
      }
      val admission = selectC(inventory, SelectionQuery(Set(Purpose.ADMISSION))).toOption.get
      When("both supplemental references use the same public binder")
      val bound = originals.map(x => ValidationOperationBindingV1.bindC(inventory, x.reference, _workspace_id).toOption.get)
      Then("binding only constructs TestOnly intent while retaining each original operation")
      bound.map(_.operation) shouldBe originals
      originals.foreach { original => admission should not contain original }
      bound.head.operation.metadata.get.purposes shouldBe Set(Purpose.HEAVY)
      bound.last.operation.metadata.get.purposes shouldBe empty
      bound.last.operation.metadata.get.origins shouldBe ValidationMetadataV1.Origins(false, false)
      bound.map(_.intent.operation).distinct shouldBe Vector(SbtOperation.TestOnly)
    }

    "preserve exact resolution failures for absent, invalid and ambiguous operations" in {
      Given("real invalid discovery retaining provider failures and duplicate logical references")
      val inventory = _inventory(invalid = true)
      val invalid = inventory.operations.find(_.selector.suiteClassName.endsWith("UnsupportedAbiFixtureSpec")).get
      val references = Vector(OperationReference("component:fixture", "absent", "operation"), invalid.reference,
        OperationReference("component:fixture", "collision:spec", "collision:operation"))
      When("the public binder resolves each exact reference")
      val outcomes = references.map(ValidationOperationBindingV1.bindC(inventory, _, _workspace_id))
      Then("no invalid reference can produce an executable intent")
      outcomes.map(_policy) shouldBe Vector(Some("validation.discovery.absent"),
        Some("validation.discovery.invalid"), Some("validation.discovery.ambiguous"))
    }

    "reject null boundaries and unsupported literal selectors" in {
      Given("a real operation and copied framework names unsupported by literal SBT rendering")
      val inventory = _inventory()
      val operation = inventory.operations.find(_.reference.operation == "parameter:1").get
      val malformed = Vector("name\"quoted", "back\\slash", "name;test", " name", "name\n", "x" * 513)
      When("binding admits copied selectors, null inventory/reference and a null typed workspace")
      val names = malformed.map { name =>
        val copied = inventory.copy(operations = Vector(operation.copy(selector = operation.selector.copy(testName = name))))
        ValidationOperationBindingV1.bindC(copied, operation.reference, _workspace_id)
      }
      val nulls = Vector(ValidationOperationBindingV1.bindC(null, operation.reference, _workspace_id),
        ValidationOperationBindingV1.bindC(inventory, null, _workspace_id),
        ValidationOperationBindingV1.bindC(inventory, operation.reference, null.asInstanceOf[WorkspaceId]))
      Then("every malformed value remains a structured failure without a context or process service")
      (names ++ nulls).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "preserve generated source and operation revisions without changing logical selector identity" in {
      Given("a real discovered operation and independent generated revision labels")
      val inventory = _inventory()
      val original = inventory.operations.find(_.reference.operation == "parameter:1").get
      When("a revision-only inventory update is bound by the same exact reference")
      val property = Prop.forAll(Gen.choose(1, 100000), Gen.choose(1, 100000)) { (source, revision) =>
        val changed = original.copy(provenance = original.provenance.copy(sourceRevision = s"source-$source",
          operationRevision = s"operation-$revision"))
        val scope = inventory.scope.copy(artifacts = inventory.scope.artifacts.map(_.copy(sourceRevision = s"source-$source")))
        val copied = inventory.copy(scope = scope, operations = Vector(changed))
        ValidationOperationBindingV1.bindC(copied, original.reference, _workspace_id).toOption.exists { bound =>
          bound.operation == changed && bound.operation.reference == original.reference &&
            bound.intent.target.scenario.contains(SbtScenarioSelection(original.selector.suiteClassName, original.selector.testName))
        }
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
      Then("binding preserves provenance while revisions remain outside stable logical references")
      checked.passed shouldBe true
    }
  }

  private def _inventory(invalid: Boolean = false): Inventory = {
    val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val prefix = "org.goldenport.cncf.validation.fixtures.phase103." + (if (invalid) "invalid" else "valid")
    val artifact = ArtifactScope("component:fixture", "artifact:tests", "artifact-1", "source-1", root, prefix)
    discoverC(DiscoveryScope("binding-fixture", "scope-1", Vector(artifact)), getClass.getClassLoader).toOption.get
  }

  private def _policy[A](result: Consequence[A]): Option[String] = result match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => fail("expected structured binding failure")
  }
}
