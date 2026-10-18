package org.goldenport.cncf.validation.fixtures.phase62inputs

import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationInputsV1}
import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.scalatest.{GivenWhenThen, Outcome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Trusted typed declarations and one ordinary ConfigMap-consuming scenario.
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class TypedInputsDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(
    requiredSemantics = Set("inputSchema"),
    inputSchema = Some(Vector(
      ValidationInputsV1.Parameter("count", ValidationInputsV1.Kind.Integer, defaultValue = Some(Value.Integer(1L))),
      ValidationInputsV1.Parameter("enabled", ValidationInputsV1.Kind.BooleanValue,
        defaultValue = Some(Value.BooleanValue(true))),
      ValidationInputsV1.Parameter("label", ValidationInputsV1.Kind.Text,
        defaultValue = Some(Value.Text("default case")))
    ))
  )
}

@ValidationSpecification(identity = "typed-inputs:spec", declaration = classOf[TypedInputsDeclarations])
class TypedInputsFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Typed scenario inputs" must afterWord(
    "in spec:validation-test-metadata, example:E1, rules:R1,R2,R3, phase:6.2"
  ) {
    "preserve ordinary fixture execution" in {
      Given("docs/spec/validation-test-metadata.md R1,R2,R3 E1: a trusted declared input schema")
      val schema = new TypedInputsDeclarations().declaration.inputSchema.get
      When("the ordinary scenario observes its declarations")
      val names = schema.map(_.name)
      Then("the one scenario has three typed inputs")
      names shouldBe Vector("count", "enabled", "label")
    }
  }

  @ValidationScenario(
    name = "E1 Typed scenario inputs must in spec:validation-test-metadata, example:E1, rules:R1,R2,R3, phase:6.2 preserve ordinary fixture execution",
    identity = "typed-inputs:operation", declaration = classOf[TypedInputsDeclarations]
  )
  def typedInputsAnchor(): Unit = ()

  override protected def withFixture(test: NoArgTest): Outcome = {
    Given("docs/spec/validation-test-metadata.md R3 E1: an ordinary ScalaTest ConfigMap")
    val schema = new TypedInputsDeclarations().declaration.inputSchema.get
    When("the normal fixture consumes namespaced input strings and declared defaults")
    val inputs = ValidationInputsV1.fromConfigMapC(schema, test.configMap.toMap)
    Then("the exact typed values are admitted before the registered body executes")
    withClue("ordinary typed scenario ConfigMap: ") { inputs.isFaillure shouldBe false }
    inputs.toOption.get.keySet shouldBe Set("count", "enabled", "label")
    super.withFixture(test)
  }
}
