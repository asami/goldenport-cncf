package org.goldenport.cncf.validation.fixtures.phase103.invalid

import scala.concurrent.duration.*
import org.scalatest.{GivenWhenThen, Suite}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.goldenport.cncf.validation.fixtures.phase103.valid.{EmptyDeclarations, FocusDeclarations}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Malformed metadata never makes these real fixture test bodies fail.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
class UnsupportedAbiDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(abiVersion = 2)
}

class BadDurationDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(expectedDuration = Some(Duration.Zero))
}

class UnsupportedSemanticsDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(requiredSemantics = Set("unknown-required"))
}

class ThrowingDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = throw new IllegalStateException("provider detail must remain bounded")
}

class NullDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = null
}

abstract class AbstractDeclarations extends ValidationDeclarationProvider {}

class ArgumentDeclarations(value: Int) extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}

class PrivateConstructorDeclarations private () extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}

class NegativeDurationDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(expectedDuration = Some((-1).seconds))
}

class ConflictingDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(executionRequirement = Some(ExecutionRequirement(
    Vector.empty, RiskLevel("different-risk"), ReasoningLevel.Routine, reviewRequired = false)))
}

abstract class PassingInvalidMetadataFixture extends AnyFunSuite with Matchers with GivenWhenThen {
  test("actual scenario") {
    Given("a real scenario whose declaration can be invalid independently")
    val input = 1
    When("the ordinary fixture body executes")
    val result = input + 1
    Then("invalid metadata does not create a failing full-suite fixture")
    result shouldBe 2
  }
}

@ValidationSpecification(declaration = classOf[UnsupportedAbiDeclarations])
class UnsupportedAbiFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[BadDurationDeclarations])
class BadDefaultFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario", declaration = classOf[FocusDeclarations])
  def validOverrideAnchor(): Unit = ()
}

@ValidationSpecification(declaration = classOf[UnsupportedSemanticsDeclarations])
class UnsupportedSemanticsFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[String])
class WrongProviderTypeFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[AbstractDeclarations])
class AbstractProviderFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[ArgumentDeclarations])
class ArgumentProviderFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[PrivateConstructorDeclarations])
class PrivateProviderFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[ThrowingDeclarations])
class ThrowingProviderFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[NullDeclarations])
class NullProviderFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[NegativeDurationDeclarations])
class NegativeDurationFixtureSpec extends PassingInvalidMetadataFixture {}

@ValidationSpecification(declaration = classOf[FocusDeclarations])
class IncompatibleRequirementFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario", declaration = classOf[ConflictingDeclarations])
  def conflictingAnchor(): Unit = ()
}

class BadScenarioFixtureSpec extends PassingInvalidMetadataFixture {
  test("valid sibling") {
    Given("a valid sibling of the scenario with malformed metadata")
    val input = 2
    When("the ordinary sibling body runs")
    val result = input * 2
    Then("that actual registration passes independently")
    result shouldBe 4
  }
  @ValidationScenario(name = "actual scenario", declaration = classOf[UnsupportedAbiDeclarations])
  def badAnchor(): Unit = ()
}

class OrphanAnchorFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "not a registered name", declaration = classOf[EmptyDeclarations])
  def orphanAnchor(): Unit = ()
}

class DuplicateAnchorFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario")
  def firstAnchor(): Unit = ()
  @ValidationScenario(name = "actual scenario")
  def secondAnchor(): Unit = ()
}

class InvalidAnchorFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario")
  def parameterAnchor(value: Int): Unit = ()
}

class NonVoidAnchorFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario")
  def nonVoidAnchor(): Int = 1
}

@ValidationSpecification(identity = " invalid identity ")
class InvalidIdentityFixtureSpec extends PassingInvalidMetadataFixture {}

class InvalidScenarioIdentityFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario", identity = " invalid operation ", revision = " invalid revision ")
  def invalidIdentityAnchor(): Unit = ()
}

@ValidationSpecification(identity = "collision:spec", revision = "first")
class FirstCollisionFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario", identity = "collision:operation")
  def collisionAnchor(): Unit = ()
}

@ValidationSpecification(identity = "collision:spec", revision = "second")
class SecondCollisionFixtureSpec extends PassingInvalidMetadataFixture {
  @ValidationScenario(name = "actual scenario", identity = "collision:operation")
  def collisionAnchor(): Unit = ()
}

class ArgumentSuiteFixtureSpec(value: Int) extends PassingInvalidMetadataFixture {}

class PrivateConstructorSuiteFixtureSpec private () extends PassingInvalidMetadataFixture {}

class NestedSuiteFixtureSpec extends Suite {
  override def nestedSuites: Vector[Suite] = Vector(new BadScenarioFixtureSpec)
}
