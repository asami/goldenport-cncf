package org.goldenport.cncf.processexecution

import java.nio.file.Path
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.validation.{ValidationExecutionModeV1 as M, ValidationMetadataV1 as V}
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D
import org.goldenport.cncf.validation.fixtures.phase611mode.ManagedModeFixtureSpec
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.{Args, ConfigMap, GivenWhenThen, Reporter}
import org.scalatest.events.Event
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Required variants, real fixture consumption and ordinary framework exclusions.
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 */
class SbtValidationModeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed validation modes" should {
    "inherit canonical required variants and replace them only with explicit operation requirements" in {
      Given("a class requiring dev and production, and a test requiring only test")
      val defaults = V.Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("dev", "production")))
      val overrides = V.Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("test")))
      When("the shared metadata owner resolves declarations")
      val inherited = V.resolveC(Some(defaults), None).toOption.get
      val replaced = V.resolveC(Some(defaults), Some(overrides)).toOption.get
      Then("the canonical sets preserve the exact required variants and ordinary absent defaults")
      inherited.executionModes shouldBe Some(Set("develop", "production"))
      replaced.executionModes shouldBe Some(Set("test"))
      M.variantsC(None).toOption.get shouldBe Vector("develop")
      V.Declaration(executionModes = Some(Set("test"))).validateC.isFaillure shouldBe true
      V.Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set.empty)).validateC.isFaillure shouldBe true
    }

    "consume selected modes in the actual harmless ScalaTest fixture without leaking consecutive configurations" in {
      Given("one real fixture and separate ConfigMaps for each supported operation mode")
      val reporter = new Reporter { def apply(event: Event): Unit = () }
      val output = new java.io.ByteArrayOutputStream()
      When("the actual registered test runs with each independent ConfigMap")
      val observations = OperationMode.values.toVector.map { mode =>
        val suite = new ManagedModeFixtureSpec
        val name = suite.testNames.head
        Console.withOut(output) {
          val status = suite.run(Some(name), Args(reporter, configMap = ConfigMap(M.modeKey -> mode.name, M.providerKey -> "fixture")))
          status.waitUntilCompleted()
          status.succeeds() shouldBe true
        }
        M.Observation(suite.getClass.getName, name, mode.name, "fixture").line
      }
      Then("each real execution emits exactly its own mode and actual fixture adapter")
      output.toString("UTF-8").linesIterator.filter(_.startsWith(M.observationPrefix)).toVector shouldBe observations
      M.consumeC(Map(M.modeKey -> "production", M.providerKey -> "live"), "suite", "test", OperationMode.Production, "fixture")
        .isFaillure shouldBe true
    }

    "exclude standard Slow cases and DoNotDiscover suites before automatic construction" in {
      Given("real framework tags, a mode fixture, and an explicit-only suite whose constructor refuses discovery")
      val root = Path.of(classOf[ManagedModeFixtureSpec].getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val scope = D.DiscoveryScope("mode-fixture", "1", Vector(D.ArtifactScope("fixture", "tests", "1", "source-1", root,
        "org.goldenport.cncf.validation.fixtures.phase611mode")))
      When("ordinary discovery and standard FULL selection run")
      val inventory = D.discoverC(scope, getClass.getClassLoader).toOption.get
      val selected = D.selectC(inventory, D.SelectionQuery(standardFramework = true)).toOption.get
      val slow = D.selectC(inventory, D.SelectionQuery(standardFramework = true, includeSlow = true)).toOption.get
      Then("FULL contains the one ordinary case and the focused Slow opt-in retains actual tags")
      inventory.completeness shouldBe D.Completeness.COMPLETE
      selected.map(_.selector.testName) shouldBe Vector("ordinary behavior should be included")
      slow should have size 3
      inventory.operations.exists(_.selector.suiteClassName.endsWith("ManagedModeFixtureSpec")) shouldBe false
      inventory.diagnostics shouldBe empty
    }

    "normalize recognized aliases without narrowing a declared required set" in {
      Given("generated required mode sets including dev aliases")
      val modes = Gen.nonEmptyListOf(Gen.oneOf("dev", "develop", "test", "demo", "production"))
      When("the shared mode owner normalizes and expands variants")
      val property = Prop.forAll(modes) { values =>
        M.variantsC(Some(values.toSet)).toOption.exists(result => result.toSet == values.map(OperationMode.from(_).get.name).toSet)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
      Then("every declared canonical variant survives once")
      checked.passed shouldBe true
    }
  }
}
