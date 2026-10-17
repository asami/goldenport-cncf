package org.goldenport.cncf.validation.fixtures.phase103.valid

import scala.concurrent.duration.*
import org.scalatest.GivenWhenThen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Passing real framework registrations and colocated typed metadata providers.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
class DefaultDeclarations extends ValidationDeclarationProvider {
  private var _calls = 0
  def declaration: Declaration = {
    _calls += 1
    Declaration(abiVersion = if (_calls == 1) 1 else 2,
      purposes = Some(Set(Purpose.ADMISSION, Purpose.FULL)),
      features = Set(FeatureReference("component:fixture", "feature-a")),
      expectedDuration = Some(2.seconds), durationClass = Some(DurationClass.LONG_RUNNING),
      executionRequirement = Some(ExecutionRequirement(Vector(CapabilityRequirement("base")),
        RiskLevel("fixture-risk"), ReasoningLevel.Standard, reviewRequired = false)))
  }
}

class FocusDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.SMOKE, Purpose.FOCUSED)),
    features = Set(FeatureReference("component:fixture", "feature-b")),
    expectedDuration = Some(500.millis), durationClass = Some(DurationClass.NORMAL),
    executionRequirement = Some(ExecutionRequirement(Vector(CapabilityRequirement("extra")),
      RiskLevel("fixture-risk"), ReasoningLevel.Deep, reviewRequired = true)))
}

class HeavyDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.HEAVY)))
}

class EmptyDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}

class ClearDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set.empty))
}

class FeatureADeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED)),
    features = Set(FeatureReference("component:fixture", "feature-a")))
}

class FeatureBDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED)),
    features = Set(FeatureReference("component:fixture", "feature-b")))
}

@ValidationSpecification(identity = "fun:spec", declaration = classOf[DefaultDeclarations])
class FunMetadataFixtureSpec extends AnyFunSuite with Matchers with GivenWhenThen {
  test("inherits defaults") { _passing_case(1) }
  test("focused union") { _passing_case(2) }
  test("heavy replacement") { _passing_case(3) }
  test("explicit empty provider") { _passing_case(4) }
  test("cleared purposes") { _passing_case(5) }
  test("parameter case 1") { _passing_case(1) }
  test("parameter case 2") { _passing_case(2) }

  @ValidationScenario(name = "focused union", identity = "focused", revision = "scenario-3",
    declaration = classOf[FocusDeclarations])
  def focusedAnchor(): Unit = ()

  @ValidationScenario(name = "heavy replacement", declaration = classOf[HeavyDeclarations])
  def heavyAnchor(): Unit = ()

  @ValidationScenario(name = "explicit empty provider", declaration = classOf[EmptyDeclarations])
  def emptyAnchor(): Unit = ()

  @ValidationScenario(name = "cleared purposes", declaration = classOf[ClearDeclarations])
  def clearAnchor(): Unit = ()

  @ValidationScenario(name = "parameter case 1", identity = "parameter:1", declaration = classOf[FocusDeclarations])
  def firstParameterAnchor(): Unit = ()

  @ValidationScenario(name = "parameter case 2", identity = "parameter:2", declaration = classOf[HeavyDeclarations])
  def secondParameterAnchor(): Unit = ()

  private def _passing_case(number: Int): Unit = {
    Given("a positive concrete fixture parameter")
    val input = number
    When("the shared parameterized body evaluates that case")
    val result = input + 1
    Then("the concrete registered scenario passes")
    result should be > input
  }
}

@ValidationSpecification(identity = "word:spec", revision = "spec-2", declaration = classOf[DefaultDeclarations])
abstract class InheritedWordDefaults extends AnyWordSpec with Matchers with GivenWhenThen {
  @ValidationScenario(name = "word behavior should inherit its anchor", identity = "inherited",
    declaration = classOf[FocusDeclarations])
  def inheritedAnchor(): Unit = ()

  @ValidationScenario(name = "word behavior should use an unannotated override", declaration = classOf[HeavyDeclarations])
  def overriddenAnchor(): Unit = ()
}

class WordMetadataFixtureSpec extends InheritedWordDefaults {
  "word behavior" should {
    "inherit its anchor" in { _passing_word() }
    "use an unannotated override" in { _passing_word() }
  }

  override def overriddenAnchor(): Unit = ()

  private def _passing_word(): Unit = {
    Given("a WordSpec registration with inherited class defaults")
    val input = "word"
    When("its body executes")
    val result = input.toUpperCase
    Then("the actual registered scenario succeeds")
    result shouldBe "WORD"
  }
}

class UnannotatedFixtureSpec extends AnyFunSuite with Matchers with GivenWhenThen {
  test("unannotated scenario") {
    Given("an actual registration with no declaration")
    val input = 1
    When("the fixture body executes")
    val result = input + 1
    Then("the unannotated scenario still passes")
    result shouldBe 2
  }
}

@ValidationSpecification(declaration = classOf[EmptyDeclarations])
class ExplicitEmptyFixtureSpec extends AnyFunSuite with Matchers with GivenWhenThen {
  test("empty class declaration") {
    Given("an explicit empty provider on the class")
    val input = Vector.empty[Int]
    When("the actual body reads its size")
    val result = input.size
    Then("registration remains an ordinary passing scenario")
    result shouldBe 0
  }
}

class FocusedFeatureFixtureSpec extends AnyFunSuite with Matchers with GivenWhenThen {
  test("focused feature a") { _passing_feature("a") }
  test("focused feature b") { _passing_feature("b") }

  @ValidationScenario(name = "focused feature a", declaration = classOf[FeatureADeclarations])
  def featureAAnchor(): Unit = ()

  @ValidationScenario(name = "focused feature b", declaration = classOf[FeatureBDeclarations])
  def featureBAnchor(): Unit = ()

  private def _passing_feature(identity: String): Unit = {
    Given("a concrete feature fixture")
    val input = identity
    When("the registered body reads its feature label")
    val result = input.length
    Then("the fixture is a passing real scenario")
    result shouldBe 1
  }
}
