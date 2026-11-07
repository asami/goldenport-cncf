package org.goldenport.cncf.validation.fixtures.phase611mode

import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationExecutionModeV1 as M}
import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.{DoNotDiscover, GivenWhenThen, Outcome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Harmless explicit child fixture; its actual adapter is always fixture.
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 */
class ManagedModeDeclaration extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes"),
    executionModes = Some(Set("dev", "test", "demo", "production")))
}

@DoNotDiscover
@ValidationSpecification(declaration = classOf[ManagedModeDeclaration])
class ManagedModeFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed mode fixture" should {
    "consume its actual application mode" in {
      Given("a fixture application constructed from the selected child ConfigMap")
      When("its ordinary assertions run")
      val adapter = "fixture"
      Then("no external provider is accessed")
      adapter shouldBe "fixture"
    }
  }
  override protected def withFixture(test: NoArgTest): Outcome = {
    val mode = test.configMap.get(M.modeKey).collect { case value: String => OperationMode.from(value) }.flatten
    mode should not be empty
    val observed = M.consumeC(test.configMap.toMap, getClass.getName, test.name, mode.get, "fixture")
    observed.isSuccess shouldBe true
    // This is emitted by the actual child fixture, not the requesting process.
    println(observed.toOption.get.line)
    super.withFixture(test)
  }
}

class OrdinaryFrameworkFixtureSpec extends AnyWordSpec {
  "ordinary behavior" should {
    "be included" in { () }
    "retain per-test Slow" taggedAs org.scalatest.tagobjects.Slow in { () }
  }
}

@org.scalatest.tags.Slow
class SlowFrameworkFixtureSpec extends AnyWordSpec {
  "class Slow behavior" should { "remain excluded" in { () } }
}

@DoNotDiscover
class ExplicitFrameworkFixtureSpec extends AnyWordSpec {
  throw new IllegalStateException("automatic discovery must exclude this suite before construction")
}
