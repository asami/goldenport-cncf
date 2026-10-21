package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterBootstrap, ComponentParameterProvenance, ComponentParameterRequirement}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class WorkflowMechanicalReceiptParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _metadata = afterWord("in spec:workflow-mechanical-receipt-capacity, example:E1, rules:R1,R2, phase:capacity-repair")
  private val _name = WorkflowMechanicalReceiptParameters.maxBytes.name

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Mechanical receipt parameters failed: $other")
  }

  private def _parameters(value: Option[ConfigurationValue], componentname: String = "org.test.MechanicalParameters"):
      Consequence[ComponentInitializationParameters] = {
    val values = value.map(_name -> _).toMap
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "mechanical-parameters", version = None,
      configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val id = ComponentId(componentname)
    val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
    ComponentParameterBootstrap.resolve(ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)),
      id, ComponentInstanceId(id, "default"), Vector(WorkflowMechanicalReceiptParameters.maxBytes), Vector.empty)
  }

  private def _resolve(value: ConfigurationValue): Consequence[Option[WorkflowMechanicalReceiptLimits]] =
    _parameters(Some(value)).flatMap(WorkflowMechanicalReceiptParameters.resolveC)

  "Workflow mechanical receipt Component parameters" should {
    "E1 compare immutable limits by their validated byte value" must _metadata {
      "when independent factories select the same or different policies" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1; Example: E1; independently validated finite limits")
        val property = Prop.forAll(Gen.choose(1, WorkflowMechanicalReceiptLimits.maximumBytes)) { bytes =>
          val first = _take(WorkflowMechanicalReceiptLimits.createC(bytes))
          val second = _take(WorkflowMechanicalReceiptLimits.createC(bytes))
          val different = _take(WorkflowMechanicalReceiptLimits.createC(
            if (bytes == WorkflowMechanicalReceiptLimits.maximumBytes) bytes - 1 else bytes + 1))
          first == second && second == first && first.hashCode == second.hashCode &&
            first != different && Set(first, second, different).size == 2
        }
        When("the independently constructed policy values are compared and used as collection keys")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        val defaultlimits = _take(WorkflowMechanicalReceiptLimits.createC(1048576))
        Then("equal byte limits have value equality and equal hashes while distinct limits remain distinct")
        checked.passed shouldBe true
        defaultlimits shouldBe WorkflowMechanicalReceiptLimits.default
        defaultlimits.hashCode shouldBe WorkflowMechanicalReceiptLimits.default.hashCode
        defaultlimits.equals(null) shouldBe false
        defaultlimits.equals(defaultlimits.maxBytes) shouldBe false
      }
    }

    "E1 preserve optional absence for the consumer default" must _metadata {
      "when a canonical Component declares the key without a value" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R2; Example: E1; an optional declaration")
        val parameters = _take(_parameters(None))
        When("the immutable resolved snapshot is consumed")
        val result = _take(WorkflowMechanicalReceiptParameters.resolveC(parameters))
        val resolution = _take(parameters.resolve(WorkflowMechanicalReceiptParameters.maxBytes))
        Then("absence is preserved and CNCF's separate default remains exactly one MiB")
        _name shouldBe "workflow.mechanical-receipt.max-bytes"
        WorkflowMechanicalReceiptParameters.maxBytes.requirement shouldBe ComponentParameterRequirement.Optional
        result shouldBe None
        resolution.provenance shouldBe ComponentParameterProvenance.Absent
        WorkflowMechanicalReceiptLimits.default.maxBytes shouldBe 1048576
        WorkflowMechanicalReceiptLimits.maximumBytes shouldBe 67108864
      }
    }

    "E1 admit integer numeric and ASCII decimal settings throughout the finite range" must _metadata {
      "when independent canonical Components resolve their own snapshots" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R2; Example: E1; generated valid byte settings")
        val property = Prop.forAll(Gen.choose(1, WorkflowMechanicalReceiptLimits.maximumBytes)) { bytes =>
          val numeric = _resolve(ConfigurationValue.NumberValue(BigDecimal(bytes)))
          val decimal = _resolve(ConfigurationValue.StringValue(bytes.toString))
          Vector(numeric, decimal).forall(_.toOption.flatten.exists(_.maxBytes == bytes))
        }
        val first = _take(_parameters(Some(ConfigurationValue.StringValue("2097152")), "org.test.MechanicalFirst"))
        val second = _take(_parameters(None, "org.test.MechanicalSecond"))
        When("valid settings and independent absence are resolved through normal Component initialization")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        val firstlimits = _take(WorkflowMechanicalReceiptParameters.resolveC(first)).get
        val secondlimits = _take(WorkflowMechanicalReceiptParameters.resolveC(second))
        val endpoints = Vector(1, 67108864).map(bytes => _take(_resolve(ConfigurationValue.StringValue(bytes.toString))).get.maxBytes)
        Then("the full inclusive range is accepted without leaking one Component's policy into another")
        checked.passed shouldBe true
        endpoints shouldBe Vector(1, 67108864)
        firstlimits.maxBytes shouldBe 2097152
        secondlimits shouldBe None
        _take(first.resolve(WorkflowMechanicalReceiptParameters.maxBytes)).provenance shouldBe
          ComponentParameterProvenance.RuntimeConfiguration
      }
    }

    "E1 refuse malformed and out-of-range settings without coercion" must _metadata {
      "when a present setting does not denote an allowed exact integer" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R2; Example: E1; malformed numeric, textual and structured values")
        val invalid = Vector(
          ConfigurationValue.StringValue(""), ConfigurationValue.StringValue(" 2"),
          ConfigurationValue.StringValue("2 "), ConfigurationValue.StringValue("+2"),
          ConfigurationValue.StringValue("-1"), ConfigurationValue.StringValue("0"),
          ConfigurationValue.StringValue("1.0"), ConfigurationValue.StringValue("1e6"),
          ConfigurationValue.StringValue("２"), ConfigurationValue.StringValue("67108865"),
          ConfigurationValue.StringValue("2147483648"), ConfigurationValue.StringValue("99999999999999999999999"),
          ConfigurationValue.NumberValue(BigDecimal("1.5")), ConfigurationValue.NumberValue(BigDecimal(0)),
          ConfigurationValue.NumberValue(BigDecimal(-1)), ConfigurationValue.NumberValue(BigDecimal(67108865)),
          ConfigurationValue.NumberValue(BigDecimal("2147483648")), ConfigurationValue.BooleanValue(true),
          ConfigurationValue.NullValue, ConfigurationValue.ObjectValue(Map.empty))
        When("canonical initialization decodes each present setting")
        val results = invalid.map(_resolve)
        val factories = Vector(Int.MinValue, -1, 0, 67108865, Int.MaxValue).map(WorkflowMechanicalReceiptLimits.createC)
        Then("each malformed setting or invalid factory input is a typed failure")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        factories.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }

    "E1 refuse missing or undeclared parameter snapshots" must _metadata {
      "when no declared immutable resolution is available" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R2; Example: E1; null and undeclared snapshots")
        When("the consumer asks for its optional declaration")
        val results = Vector(WorkflowMechanicalReceiptParameters.resolveC(null),
          WorkflowMechanicalReceiptParameters.resolveC(ComponentInitializationParameters.empty))
        Then("undeclared data does not become optional absence")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }
  }
}
