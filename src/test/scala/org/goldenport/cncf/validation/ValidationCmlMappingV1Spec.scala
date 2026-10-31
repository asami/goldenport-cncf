package org.goldenport.cncf.validation

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import ValidationCmlMappingV1.{DurationUnit, DurationValue, Properties}
import ValidationMetadataV1.*

/** CML-normalized properties and Scala declarations have one semantic interpreter.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ValidationCmlMappingV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _feature_a = FeatureReference("component:orders", "approval")
  private val _feature_b = FeatureReference("slice:orders", "review")
  private val _base_requirement = ExecutionRequirement(Vector(CapabilityRequirement("base")), RiskLevel("orders-risk"),
    ReasoningLevel.Standard, reviewRequired = false)
  private val _operation_requirement = _base_requirement.copy(capabilities = Vector(CapabilityRequirement("extra")),
    reasoning = ReasoningLevel.Deep, reviewRequired = true,
    disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required, "origin:orders")),
    reasoningClass = Some(ReasoningClassReference("orders:careful")))

  "Normalized CML metadata" should {
    "map every property to the same validated Scala declaration" in {
      Given("equivalent Scala and CML-normalized properties for every public declaration field")
      val properties = Properties(abiVersion = 1, purposes = Some(Set("SMOKE", "FOCUSED")), features = Set(_feature_a),
        expectedDuration = Some(DurationValue(3L, DurationUnit.SECONDS)), durationClass = Some("LONG_RUNNING"),
        executionRequirement = Some(_operation_requirement), requiredSemantics = Set("purposes", "executionRequirement"))
      val declaration = Declaration(abiVersion = 1, purposes = Some(Set(Purpose.SMOKE, Purpose.FOCUSED)),
        features = Set(_feature_a), expectedDuration = Some(FiniteDuration(3000000000L, TimeUnit.NANOSECONDS)),
        durationClass = Some(DurationClass.LONG_RUNNING), executionRequirement = Some(_operation_requirement),
        requiredSemantics = Set("purposes", "executionRequirement"))
      When("properties are admitted and both representations are normalized")
      val admitted = ValidationCmlMappingV1.declarationC(properties)
      val mapped = ValidationCmlMappingV1.resolveC(Some(properties), None)
      val scalaresult = ValidationMetadataV1.resolveC(Some(declaration), None)
      Then("the exact declaration and normalized metadata agree without a second merge policy")
      admitted.toOption shouldBe Some(declaration)
      mapped.toOption shouldBe scalaresult.toOption
      properties.declarationC.toOption shouldBe admitted.toOption
    }

    "parse canonical ON_DEMAND without adding casing aliases or losing inherited metadata" in {
      val defaults = Properties(purposes = Some(Set("ON_DEMAND", "FULL")), features = Set(_feature_a))
      val resolved = _resolve(Some(defaults), Some(Properties()))
      resolved.purposes shouldBe Set(Purpose.ON_DEMAND, Purpose.FULL)
      resolved.features shouldBe Set(_feature_a)
      resolved.origins shouldBe Origins(true, true)
      defaults.declarationC.toOption.get.purposes shouldBe Some(Set(Purpose.ON_DEMAND, Purpose.FULL))
      Vector("on_demand", "ON-DEMAND", "ON_DEMAND ").foreach { label =>
        Properties(purposes = Some(Set(label))).declarationC shouldBe a[Consequence.Failure[?]]
      }
    }

    "preserve absent, explicit empty and field-specific override semantics" in {
      Given("all-field defaults and an operation replacing purposes, duration and duration class")
      val defaults = Properties(purposes = Some(Set("ADMISSION", "FULL")), features = Set(_feature_a),
        expectedDuration = Some(DurationValue(2L, DurationUnit.SECONDS)), durationClass = Some("LONG_RUNNING"),
        executionRequirement = Some(_base_requirement))
      val overrides = Properties(purposes = Some(Set("SMOKE", "FOCUSED")), features = Set(_feature_b),
        expectedDuration = Some(DurationValue(250L, DurationUnit.MILLISECONDS)), durationClass = Some("NORMAL"),
        executionRequirement = Some(_operation_requirement))
      When("absent, empty, clearing and overriding declarations use the same public resolver")
      val absent = _resolve(None, None)
      val explicit = _resolve(None, Some(Properties()))
      val inherited = _resolve(Some(defaults), Some(Properties()))
      val cleared = _resolve(Some(defaults), Some(Properties(purposes = Some(Set.empty))))
      val resolved = _resolve(Some(defaults), Some(overrides))
      Then("origins, purpose replacement, feature union and existing aggregate composition agree with Scala")
      absent.origins shouldBe Origins(false, false)
      explicit shouldBe absent.copy(origins = Origins(false, true))
      inherited.purposes shouldBe Set(Purpose.ADMISSION, Purpose.FULL)
      inherited.expectedDuration shouldBe Some(FiniteDuration(2000000000L, TimeUnit.NANOSECONDS))
      cleared.purposes shouldBe empty
      cleared.features shouldBe Set(_feature_a)
      resolved.purposes shouldBe Set(Purpose.SMOKE, Purpose.FOCUSED)
      resolved.features shouldBe Set(_feature_a, _feature_b)
      resolved.expectedDuration shouldBe Some(FiniteDuration(250000000L, TimeUnit.NANOSECONDS))
      resolved.durationClass shouldBe DurationClass.NORMAL
      resolved.executionRequirement.get shouldBe _operation_requirement.copy(
        capabilities = Vector(CapabilityRequirement("base"), CapabilityRequirement("extra")))
      resolved shouldBe ValidationMetadataV1.resolveC(defaults.declarationC.toOption, overrides.declarationC.toOption).toOption.get
    }

    "map each exact duration unit and the maximum positive nanosecond count" in {
      Given("all seven supported units and the largest Long nanosecond duration")
      val cases = Vector(DurationUnit.NANOSECONDS -> 1L, DurationUnit.MICROSECONDS -> 1000L,
        DurationUnit.MILLISECONDS -> 1000000L, DurationUnit.SECONDS -> 1000000000L,
        DurationUnit.MINUTES -> 60000000000L, DurationUnit.HOURS -> 3600000000000L, DurationUnit.DAYS -> 86400000000000L)
      When("the normalized mapper performs exact integer unit conversion")
      val mapped = cases.map { case (unit, _) =>
        Properties(expectedDuration = Some(DurationValue(1L, unit))).declarationC.toOption.get.expectedDuration.get
      }
      val maximum = Properties(expectedDuration = Some(DurationValue(Long.MaxValue, DurationUnit.NANOSECONDS))).declarationC
      Then("every unit yields exact nanos and Long maximum is admitted without saturation")
      mapped shouldBe cases.map(x => FiniteDuration(x._2, TimeUnit.NANOSECONDS))
      maximum.toOption.get.expectedDuration shouldBe Some(FiniteDuration(Long.MaxValue, TimeUnit.NANOSECONDS))
    }

    "reject zero, negative and overflowing durations before constructing FiniteDuration" in {
      Given("nonpositive amounts and positive amounts whose exact nanosecond product exceeds Long maximum")
      val durations = Vector(DurationValue(0L, DurationUnit.NANOSECONDS), DurationValue(-1L, DurationUnit.SECONDS),
        DurationValue(Long.MinValue, DurationUnit.DAYS)) ++ DurationUnit.values.filter(_ != DurationUnit.NANOSECONDS)
        .map(unit => DurationValue(Long.MaxValue, unit)).toVector
      When("the property boundary admits each duration value")
      val outcomes = durations.map(x => Properties(expectedDuration = Some(x)).declarationC)
      Then("all invalid durations are domain failures rather than constructor exceptions")
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject unknown exact labels, unsupported ABI semantics and invalid inherited defaults" in {
      Given("unknown and case-variant labels, unsupported versions and shadowed malformed defaults")
      val properties = Vector(Properties(purposes = Some(Set("admission"))), Properties(purposes = Some(Set("ADMISSION "))),
        Properties(purposes = Some(Set("FUTURE"))), Properties(durationClass = Some("long_running")),
        Properties(durationClass = Some("LONG")), Properties(abiVersion = 2),
        Properties(requiredSemantics = Set("future")), Properties(requiredSemantics = Set("Purposes")))
      When("declaration mapping and a shadowing operation override are interpreted")
      val outcomes = properties.map(_.declarationC)
      val shadowed = ValidationCmlMappingV1.resolveC(Some(properties.head), Some(Properties(purposes = Some(Set("FULL")))))
      Then("unknown required meanings never silently inherit or fall back")
      (outcomes :+ shadowed).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject explicit null values at every normalized property boundary" in {
      Given("null property objects, options, members, collections and existing requirement facts")
      val malformed = Vector(null, Properties(purposes = null), Properties(purposes = Some(null)),
        Properties(purposes = Some(Set(null))), Properties(features = null), Properties(features = Set(null)),
        Properties(expectedDuration = null), Properties(expectedDuration = Some(null)),
        Properties(expectedDuration = Some(DurationValue(1L, null))), Properties(durationClass = null),
        Properties(durationClass = Some(null)), Properties(executionRequirement = null),
        Properties(executionRequirement = Some(null)), Properties(requiredSemantics = null),
        Properties(requiredSemantics = Set(null)), Properties(executionRequirement = Some(_base_requirement.copy(reasoning = null))))
      When("public declarations and optional specification/operation boundaries are admitted")
      val outcomes = malformed.map(ValidationCmlMappingV1.declarationC)
      val boundaries = Vector(ValidationCmlMappingV1.resolveC(null, None), ValidationCmlMappingV1.resolveC(None, null),
        ValidationCmlMappingV1.resolveC(Some(null), None), ValidationCmlMappingV1.resolveC(None, Some(null)))
      Then("every malformed value produces a structured Consequence failure")
      (outcomes ++ boundaries).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject conflicting existing requirements without weakening class policy" in {
      Given("a class requirement and operation requirements conflicting on opaque risk, disposition or reasoning class")
      val defaults = Properties(executionRequirement = Some(_operation_requirement))
      val conflicts = Vector(_operation_requirement.copy(risk = RiskLevel("other-risk")),
        _operation_requirement.copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Inline,
          ExecutionIndependence.Required, "origin:orders"))),
        _operation_requirement.copy(reasoningClass = Some(ReasoningClassReference("other-class"))))
      When("both normalized CML declarations reach the existing S1 requirement composition")
      val outcomes = conflicts.map(x => ValidationCmlMappingV1.resolveC(Some(defaults), Some(Properties(executionRequirement = Some(x)))))
      Then("no conflicting existing aggregate is replaced by a weaker operation value")
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "preserve equivalent generated positive unit amounts and reject generated overflow" in {
      Given("all duration units and generated positive amounts within each exact nanosecond bound")
      val units = Gen.oneOf(DurationUnit.values.toVector)
      When("equivalent unit and nanosecond declarations are normalized")
      val property = Prop.forAll(units, Gen.choose(1L, 10000L)) { (unit, amount) =>
        val multiplier = BigInt(TimeUnit.valueOf(unit.toString).toNanos(1L))
        val nanos = BigInt(amount) * multiplier
        val mapped = Properties(expectedDuration = Some(DurationValue(amount, unit))).declarationC.toOption.get
        val exact = Declaration(expectedDuration = Some(FiniteDuration(nanos.toLong, TimeUnit.NANOSECONDS)))
        mapped == exact && (unit == DurationUnit.NANOSECONDS ||
          Properties(expectedDuration = Some(DurationValue(Long.MaxValue, unit))).declarationC.isFaillure)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      Then("unit conversion is exact and overflowing products remain structured refusals")
      checked.passed shouldBe true
    }
  }

  private def _resolve(specification: Option[Properties], operation: Option[Properties]): Metadata =
    ValidationCmlMappingV1.resolveC(specification, operation).toOption.get
}
