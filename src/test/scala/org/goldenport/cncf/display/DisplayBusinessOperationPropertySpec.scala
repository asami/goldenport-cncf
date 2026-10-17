package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{Multiplicity, ValueDomain}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object DisplayBusinessOperationPropertySpec {
  final case class Sample(size: Int, rotation: Int, reversed: Boolean, text: String, multiplicity: Int)
  given Shrink[Sample] = Shrink { sample =>
    val size = if (sample.size == 1) Stream.empty else Stream(sample.copy(size = math.max(1, sample.size / 2)))
    val rotation = if (sample.rotation == 0) Stream.empty else Stream(sample.copy(rotation = sample.rotation / 2))
    val reversed = if (sample.reversed) Stream(sample.copy(reversed = false)) else Stream.empty
    val text = if (sample.text.isEmpty) Stream.empty else Stream(sample.copy(text = sample.text.take(sample.text.length / 2)))
    val multiplicity = if (sample.multiplicity == 0) Stream.empty else Stream(sample.copy(multiplicity = sample.multiplicity / 2))
    size #::: rotation #::: reversed #::: text #::: multiplicity
  }
}

final class DisplayBusinessOperationPropertySpec extends AnyWordSpec with DisplayBusinessOperationFixture.Vocabulary {
  import DisplayBusinessOperation.*
  import DisplayBusinessOperationFixture.*
  import DisplayBusinessOperationPropertySpec.*
  import DisplayBusinessOperationPropertySpec.given
  private val _checks = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _sample = for {
    size <- Gen.choose(1, 6)
    rotation <- Gen.choose(0, 40)
    reversed <- Gen.oneOf(true, false)
    length <- Gen.choose(0, 18)
    chars <- Gen.listOfN(length, Gen.oneOf('a', '日', 'Ω', '\u0301', '\ud800', '\udfff', ' ', '\n', '\u0000'))
    multiplicity <- Gen.choose(0, 3)
  } yield Sample(size, rotation, reversed, chars.mkString, multiplicity)

  private def _declarations(sample: Sample): Vector[GeneratedLogicalUiAbi.Definition] = {
    val original = definitions(sample.size, sample.text)
    val list = original.head.screen.asInstanceOf[GeneratedLogicalUiAbi.ListDefinition]
    val actions = list.actions.map(action => action.copy(id = action.id + ":" + sample.text + ":end"))
    original.updated(0, original.head.copy(screen = list.copy(actions = actions, itemActions = actions)))
  }
  private def _bindings(sample: Sample): Vector[Binding] = bindings(sample.size).map { binding =>
    if (binding.screenId == "collecting-resources") binding.copy(actionId = binding.actionId + ":" + sample.text + ":end") else binding
  }
  private def _permutation(sample: Sample, values: Vector[Binding]): Vector[Binding] = {
    val offset = sample.rotation % values.size
    val rotated = values.drop(offset) ++ values.take(offset)
    if (sample.reversed) rotated.reverse else rotated
  }
  private def _component(subsystem: Subsystem, target: TargetOperation, name: String): SourceComponent = {
    val component = new SourceComponent()
    initialize(component, subsystem, protocol(Vector(target, new TargetOperation(operationName = "alternate"))), name = name)
    component
  }
  private def _law(result: Test.Result): Unit = {
    result.passed shouldBe true
    result.succeeded should be >= 100
    result.discarded shouldBe 0
  }

  "Business descriptor generated laws" should {
    "non-vacuous scoped admission" which {
      "BA7-P1 preserve declaration ordering under total registration permutations" in {
        Given("bounded nonempty action groups, arbitrary Unicode opaque IDs and a reducing domain-valid Sample shrinker")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = _component(subsystem, target, "BusinessPermutationLaw")
          When("one hundred lawful samples permute the same complete bindings before admission")
          val property = Prop.forAll(_sample) { sample =>
            val declarations = _declarations(sample)
            val selected = _bindings(sample)
            val first = registry(component, declarations, selected).TAKE
            val permuted = registry(component, declarations, _permutation(sample, selected)).TAKE
            val expected = Vector(ActionScope.Screen, ActionScope.Item).flatMap(scope =>
              Vector.tabulate(sample.size)(index => scope -> ("review:" + index + ":" + sample.text + ":end")))
            val actual = permuted.describeC(resourceType, "collecting-resources").TAKE
            val original = first.describeC(resourceType, "collecting-resources").TAKE
            actual.source.componentId shouldBe original.source.componentId
            actual.source.resourceType shouldBe original.source.resourceType
            actual.definition shouldBe original.definition
            actual.actions shouldBe original.actions
            actual.actions.map(action => action.scope -> action.id) shouldBe expected
            true
          }
          val result = Test.check(_checks, property)
          Then("at least one hundred successes with zero discards establish registration-independent declaration order and no callbacks")
          _law(result)
          idle(component, target)
        }
      }
      "BA7-P2 admit same-key reuse only for one canonical target" in {
        Given("every generated List action shares a binding identity and two exact ordinary targets are present")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = _component(subsystem, target, "BusinessSharedKeyLaw")
          When("one hundred lawful samples admit shared-target reuse and then change exactly one shared-key target")
          val property = Prop.forAll(_sample) { sample =>
            val declarations = _declarations(sample)
            val selected = _bindings(sample)
            val valid = registry(component, declarations, selected).TAKE.describeC(resourceType, "collecting-resources").TAKE
            valid.actions.map(_.bindingId).distinct shouldBe Vector("business:shared")
            valid.actions.map(_.target).distinct shouldBe Vector(OperationReference(component.componentId, "business", "review"))
            val invalid = registry(component, declarations, selected.updated(1, selected(1).copy(operationName = "alternate")))
            expectFailure(invalid, "TARGET_CONFLICT", "bindings[1]")
            true
          }
          val result = Test.check(_checks, property)
          Then("at least one hundred successes with zero discards distinguish legal reuse from conflicting canonical targets")
          _law(result)
          idle(component, target)
        }
      }
      "BA7-P3 separate identical screen and item action identities without collapsing either scope" in {
        Given("nonempty generated Screen and Item declarations containing identical scoped IDs")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = _component(subsystem, target, "BusinessScopeLaw")
          When("one hundred lawful samples select only Screen actions or only Item actions")
          val property = Prop.forAll(_sample) { sample =>
            val declarations = _declarations(sample)
            val selected = _bindings(sample)
            Vector(ActionScope.Screen, ActionScope.Item).foreach { scope =>
              val own = selected.filter(binding => binding.screenId == "collecting-resources" && binding.scope == scope)
              val descriptor = registry(component, declarations, own).TAKE.describeC(resourceType, "collecting-resources").TAKE
              descriptor.actions.map(_.scope) shouldBe Vector.fill(sample.size)(scope)
              descriptor.actions.map(_.id) shouldBe Vector.tabulate(sample.size)(index => "review:" + index + ":" + sample.text + ":end")
              descriptor.actions.size shouldBe sample.size
            }
            true
          }
          val result = Test.check(_checks, property)
          Then("at least one hundred successes with zero discards preserve each exact scope independently")
          _law(result)
          idle(component, target)
        }
      }
    }
    "closed standalone serialization" which {
      "BA7-P4 roundtrip generated Unicode identities, all parameter kinds and the canonical multiplicity marks" in {
        Given("the total Sample generator and explicit request contracts spanning required, optional and repeated parameters")
        withSubsystem { subsystem =>
          When("one hundred lawful samples encode and decode normally admitted registries")
          val property = Prop.forAll(_sample) { sample =>
            val multiplicities = Vector(Multiplicity.One, Multiplicity.ZeroOne, Multiplicity.OneMore, Multiplicity.ZeroMore)
            val marks = Vector("1", "?", "+", "*")
            val actualparameters = parameters.zipWithIndex.map { case (parameter, index) =>
              parameter.copy(domain = ValueDomain(parameter.datatype, multiplicities((sample.multiplicity + index) % 4)))
            }
            val expected = expectedParameters.zipWithIndex.map { case (parameter, index) =>
              parameter.copy(multiplicity = marks((sample.multiplicity + index) % 4))
            }
            val target = new TargetOperation(actualparameters)
            val component = _component(subsystem, target, "BusinessCodecLaw")
            val declarations = _declarations(sample)
            val admitted = registry(component, declarations, _permutation(sample, _bindings(sample))).TAKE
            Vector("collecting-resources", "capture-detail").foreach { screen =>
              val before = admitted.describeC(resourceType, screen).TAKE
              val encoded = DisplayBusinessOperationCodec.encodeC(admitted, resourceType, screen).TAKE
              val after = DisplayBusinessOperationCodec.decodeC(admitted, encoded).TAKE
              after shouldBe before
              after.actions.foreach(_.parameters shouldBe expected)
              DisplayBusinessOperationCodec.encodeC(admitted, resourceType, screen).TAKE shouldBe encoded
            }
            idle(component, target)
            true
          }
          val result = Test.check(_checks, property)
          Then("at least one hundred successes with zero discards preserve exact metadata and invoke no target or Browser")
          _law(result)
        }
      }
    }
  }
}
