package org.goldenport.cncf.validation

import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Executable declaration interpretation contract, without framework discovery.
 * @since   Oct. 18, 2026
 * @version Oct. 19, 2026
 */
final class ValidationMetadataV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ValidationMetadataV1.*

  "Validation Metadata V1" should {
    "interpret declaration presence and membership" which {
      "distinguishes unannotated operations from explicit empty declarations" in {
        Given("absent declarations and otherwise identical explicit empty declarations")
        val empty = Declaration()
        When("each presence combination is resolved")
        val absent = _resolved(None, None)
        val specification = _resolved(Some(empty), None)
        val operation = _resolved(None, Some(empty))
        val both = _resolved(Some(empty), Some(empty))
        Then("empty semantic defaults retain their distinct declaration origins")
        absent shouldBe Metadata(Set.empty, Set.empty, None, DurationClass.NORMAL, None, Origins(false, false))
        specification shouldBe absent.copy(origins = Origins(true, false))
        operation shouldBe absent.copy(origins = Origins(false, true))
        both shouldBe absent.copy(origins = Origins(true, true))
      }

      "inherits specification defaults for each resolved operation" in {
        Given("a specification with purpose, feature, duration, class and execution defaults")
        val feature = FeatureReference("component:orders", "approval")
        val defaults = Declaration(
          purposes = Some(Set(Purpose.ADMISSION, Purpose.FULL)), features = Set(feature),
          expectedDuration = Some(2.seconds), durationClass = Some(DurationClass.LONG_RUNNING),
          executionRequirement = Some(_requirement)
        )
        When("an operation without field overrides is resolved")
        val resolved = _resolved(Some(defaults), Some(Declaration()))
        val admitted = defaults.validateC
        Then("every default applies to that operation without aggregate duration inference")
        resolved shouldBe Metadata(Set(Purpose.ADMISSION, Purpose.FULL), Set(feature),
          Some(FiniteDuration(2000000000L, NANOSECONDS)), DurationClass.LONG_RUNNING,
          Some(_requirement), Origins(true, true))
        admitted shouldBe Consequence.success(defaults)
      }

      "uses explicit operation purposes independently of all other memberships" in {
        Given("ADMISSION and FULL defaults and multiple, HEAVY-only and empty operation memberships")
        val defaults = Some(Declaration(purposes = Some(Set(Purpose.ADMISSION, Purpose.FULL))))
        val memberships = Vector(Set(Purpose.SMOKE, Purpose.FOCUSED), Set(Purpose.HEAVY), Set.empty[Purpose])
        When("the operations replace or inherit their purpose defaults")
        val replaced = memberships.map(x => _resolved(defaults, Some(Declaration(purposes = Some(x)))))
        val inherited = _resolved(defaults, Some(Declaration(purposes = None)))
        Then("no hierarchy adds memberships and explicit empty purposes clear defaults")
        replaced.map(_.purposes) shouldBe memberships
        replaced(1).purposes should not contain Purpose.ADMISSION
        replaced(1).purposes should not contain Purpose.FULL
        inherited.purposes shouldBe Set(Purpose.ADMISSION, Purpose.FULL)
      }

      "unions scoped features independently of insertion order and admits new identities" in {
        Given("permutations of scoped features including equal labels in different scopes and a new identity")
        val features = Vector(FeatureReference("component:a", "approval"),
          FeatureReference("component:b", "approval"), FeatureReference("application:new", "future-feature"))
        val permutations = Gen.pick(features.size, features).map(_.toVector)
        val property = Prop.forAll(permutations, Gen.choose(0, features.size)) { (ordered, split) =>
          val left = Declaration(features = ordered.take(split).toSet)
          val right = Declaration(features = ordered.drop(split).toSet)
          resolveC(Some(left), Some(right)).toOption.exists(_.features == features.toSet) &&
            left.abiVersion == ABI_VERSION && right.abiVersion == ABI_VERSION
        }
        When("feature declarations are interpreted over the generated permutations and partitions")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)
        Then("the exact scoped feature union is preserved at ABI version one")
        checked.passed shouldBe true
        ABI_VERSION shouldBe 1
      }

      "admits exact bounded feature text and rejects malformed scope or identity" in {
        Given("opaque labels, maximum-length text and malformed feature references")
        val valid = Vector(FeatureReference("scope", "not-a-Capability-mapping"),
          FeatureReference("s" * 512, "i" * 512))
        val invalid = Vector(FeatureReference("", "id"), FeatureReference("scope", ""),
          FeatureReference(" scope", "id"), FeatureReference("scope", "id "),
          FeatureReference("scope\n", "id"), FeatureReference("scope", "i\u0000d"),
          FeatureReference("s" * 513, "id"), FeatureReference("scope", "i" * 513),
          FeatureReference(null, "id"), FeatureReference("scope", null))
        When("each feature and declaration is admitted")
        val accepted = valid.map(_.validateC)
        val rejected = invalid.map(x => Declaration(features = Set(x)).validateC)
        Then("only exact nonempty trimmed bounded text survives without inferred mappings")
        accepted shouldBe valid.map(Consequence.success(_))
        rejected.foreach(_ shouldBe a[Consequence.Failure[_]])
        rejected.foreach(x => _policy(x) shouldBe Some("validation.metadata.feature"))
      }
    }

    "interpret declared operation duration" which {
      "normalizes units and overrides duration and duration class independently" in {
        Given("one class duration and class, plus operation declarations with independent overrides")
        val defaults = Some(Declaration(expectedDuration = Some(2.seconds),
          durationClass = Some(DurationClass.LONG_RUNNING)))
        val operation = Some(Declaration(expectedDuration = Some(1500.millis), durationClass = Some(DurationClass.NORMAL)))
        When("each operation is normalized in nanoseconds")
        val overridden = _resolved(defaults, operation)
        val durationonly = _resolved(defaults, Some(Declaration(expectedDuration = Some(1.second))))
        val classonly = _resolved(defaults, Some(Declaration(durationClass = Some(DurationClass.NORMAL))))
        val equivalent = _resolved(None, Some(Declaration(expectedDuration = Some(1500000000.nanos))))
        Then("duration is per operation, canonical in nanoseconds and independent of duration class")
        overridden.expectedDuration shouldBe equivalent.expectedDuration
        overridden.expectedDuration.map(_.unit) shouldBe Some(NANOSECONDS)
        overridden.durationClass shouldBe DurationClass.NORMAL
        durationonly.expectedDuration shouldBe Some(1000000000.nanos)
        durationonly.durationClass shouldBe DurationClass.LONG_RUNNING
        classonly.expectedDuration shouldBe Some(2000000000.nanos)
        classonly.durationClass shouldBe DurationClass.NORMAL
      }

      "rejects zero and negative durations even when an operation overrides them" in {
        Given("zero and negative specification durations with an otherwise valid operation override")
        val invalid = Vector(Duration.Zero, (-1).nanos, (-2).seconds)
        val operation = Some(Declaration(expectedDuration = Some(1.second)))
        When("all declarations are admitted before composition")
        val outcomes = invalid.map(x => resolveC(Some(Declaration(expectedDuration = Some(x))), operation))
        Then("invalid defaults fail with a structured duration policy rather than being shadowed")
        outcomes.foreach(_ shouldBe a[Consequence.Failure[_]])
        outcomes.foreach(x => _policy(x) shouldBe Some("validation.metadata.duration"))
      }

      "accepts the largest positive duration representable by the typed ABI" in {
        Given("the nanosecond boundary of Scala FiniteDuration's constructible range")
        val largest = FiniteDuration(Long.MaxValue, NANOSECONDS)
        When("the boundary duration is admitted and resolved")
        val declaration = Declaration(expectedDuration = Some(largest))
        val admitted = declaration.validateC
        val resolved = _resolved(None, Some(declaration))
        Then("the exact positive nanosecond count is retained without overflow or saturation")
        admitted shouldBe Consequence.success(declaration)
        resolved.expectedDuration.map(_.length) shouldBe Some(Long.MaxValue)
        resolved.expectedDuration.map(_.unit) shouldBe Some(NANOSECONDS)
      }
    }

    "admit required semantics and null boundaries" which {
      "rejects unsupported ABI and semantic names before applying valid overrides" in {
        Given("unsupported versions, unknown required semantics and the complete supported semantic set")
        val supported = Set("purposes", "features", "expectedDuration", "durationClass", "executionRequirement", "inputSchema")
        val invalid = Vector(Declaration(abiVersion = 0), Declaration(abiVersion = 2),
          Declaration(requiredSemantics = Set("provider")), Declaration(requiredSemantics = Set("Purposes")))
        val overridevalue = Some(Declaration(purposes = Some(Set(Purpose.SMOKE))))
        When("class admission and operation admission use the same public interpretation")
        val classoutcomes = invalid.map(x => resolveC(Some(x), overridevalue))
        val operationoutcomes = invalid.map(x => resolveC(None, Some(x)))
        val known = Declaration(requiredSemantics = supported).validateC
        Then("unknown required contracts fail closed while known names need not supply optional values")
        (classoutcomes ++ operationoutcomes).foreach(_ shouldBe a[Consequence.Failure[_]])
        classoutcomes.take(2).foreach(x => _policy(x) shouldBe Some("validation.metadata.abi"))
        classoutcomes.drop(2).foreach(x => _policy(x) shouldBe Some("validation.metadata.required-semantics"))
        known shouldBe Consequence.success(Declaration(requiredSemantics = supported))
      }

      "returns domain failures for null declaration options and their members" in {
        Given("null boundary options and null declarations instead of absent optional declarations")
        val inputs = Vector[(Option[Declaration], Option[Declaration])](
          (null, None), (None, null), (Some(null), None), (None, Some(null)))
        When("the public resolver admits each pair")
        val outcomes = inputs.map { case (specification, operation) => resolveC(specification, operation) }
        Then("each malformed pair fails without an exception while absence remains valid")
        outcomes.foreach(_ shouldBe a[Consequence.Failure[_]])
        outcomes.foreach(x => _policy(x) shouldBe Some("validation.metadata.null"))
      }

      "rejects null metadata fields and set members without treating them as absence" in {
        Given("explicit nulls in each optional field, set and enum or reference member")
        val invalid = Vector(
          Declaration(purposes = null), Declaration(purposes = Some(null)),
          Declaration(purposes = Some(Set(null.asInstanceOf[Purpose]))),
          Declaration(features = null), Declaration(features = Set(null.asInstanceOf[FeatureReference])),
          Declaration(expectedDuration = null), Declaration(expectedDuration = Some(null)),
          Declaration(durationClass = null), Declaration(durationClass = Some(null)),
          Declaration(executionRequirement = null), Declaration(executionRequirement = Some(null)),
          Declaration(requiredSemantics = null), Declaration(requiredSemantics = Set(null.asInstanceOf[String]))
        )
        When("declaration validation admits each malformed field")
        val outcomes = invalid.map(_.validateC)
        val empty = Declaration().validateC
        Then("all null cases are structured policy failures and an empty declaration is valid")
        outcomes.foreach(_ shouldBe a[Consequence.Failure[_]])
        outcomes.foreach(x => _policy(x).exists(_.startsWith("validation.metadata.")) shouldBe true)
        empty shouldBe Consequence.success(Declaration())
      }

      "rejects malformed existing execution requirements including nested optional nulls" in {
        Given("requirements with malformed required facts, capability members and optional facts")
        val invalid = Vector(
          _requirement.copy(capabilities = null), _requirement.copy(capabilities = Vector(null)),
          _requirement.copy(capabilities = Vector(CapabilityRequirement(null))),
          _requirement.copy(capabilities = Vector(CapabilityRequirement(" "))),
          _requirement.copy(risk = null), _requirement.copy(risk = RiskLevel(null)),
          _requirement.copy(risk = RiskLevel("")), _requirement.copy(reasoning = null),
          _requirement.copy(disposition = null), _requirement.copy(disposition = Some(null)),
          _requirement.copy(disposition = Some(_disposition.copy(placement = null))),
          _requirement.copy(disposition = Some(_disposition.copy(independence = null))),
          _requirement.copy(disposition = Some(_disposition.copy(originContextIdentity = null))),
          _requirement.copy(reasoningClass = null), _requirement.copy(reasoningClass = Some(null)),
          _requirement.copy(reasoningClass = Some(ReasoningClassReference(null))),
          _requirement.copy(reasoningClass = Some(ReasoningClassReference("")))
        )
        When("the existing validator is reused at metadata admission")
        val outcomes = invalid.map(x => resolveC(Some(Declaration(executionRequirement = Some(x))),
          Some(Declaration(executionRequirement = Some(_requirement)))))
        Then("malformed inherited requirements cannot be hidden by a valid operation requirement")
        outcomes.foreach(_ shouldBe a[Consequence.Failure[_]])
        outcomes.foreach(x => _policy(x) shouldBe Some("validation.metadata.execution-requirement"))
      }
    }

    "compose the existing execution aggregate" which {
      "inherits either single requirement with canonical distinct sorted capabilities" in {
        Given("one valid requirement with repeated unsorted capabilities")
        val requirement = _requirement.copy(capabilities = Vector("z", "a", "z").map(CapabilityRequirement(_)))
        val declared = Some(Declaration(executionRequirement = Some(requirement)))
        When("the requirement appears at either declaration level")
        val specification = _resolved(declared, None)
        val operation = _resolved(None, declared)
        Then("both yield the same validated aggregate with identity-normalized capabilities")
        val canonical = Some(requirement.copy(capabilities = Vector("a", "z").map(CapabilityRequirement(_))))
        specification.executionRequirement shouldBe canonical
        operation.executionRequirement shouldBe canonical
        requirement.capabilities.map(_.identity) shouldBe Vector("z", "a", "z")
      }

      "unions capabilities and preserves strongest reasoning and required review" in {
        Given("all pairs of existing reasoning levels and review facts with overlapping capability identities")
        val levels = ReasoningLevel.values.toVector
        val cases = for {
          left <- levels; right <- levels; leftreview <- Vector(false, true); rightreview <- Vector(false, true)
        } yield (_requirement.copy(capabilities = Vector("z", "a").map(CapabilityRequirement(_)),
          reasoning = left, reviewRequired = leftreview),
          _requirement.copy(capabilities = Vector("b", "a").map(CapabilityRequirement(_)),
            reasoning = right, reviewRequired = rightreview))
        When("each pair is composed in both declaration directions")
        val outcomes = cases.map { case (left, right) =>
          (left, right, _composed(left, right), _composed(right, left))
        }
        Then("composition never weakens reasoning or review and always returns the capability union")
        outcomes.foreach { case (left, right, forward, reverse) =>
          forward shouldBe reverse
          forward.capabilities.map(_.identity) shouldBe Vector("a", "b", "z")
          forward.reasoning shouldBe levels(math.max(left.reasoning.ordinal, right.reasoning.ordinal))
          forward.reviewRequired shouldBe (left.reviewRequired || right.reviewRequired)
          forward.risk shouldBe RiskLevel("project-specific-risk")
        }
      }

      "inherits optional execution facts and accepts matching opaque facts" in {
        Given("a disposition and semantic reasoning class present at one or both declaration levels")
        val populated = _requirement.copy(disposition = Some(_disposition),
          reasoningClass = Some(ReasoningClassReference("application:analysis")))
        val pairs = Vector((_requirement, populated), (populated, _requirement), (populated, populated))
        When("each pair composes the singular existing requirement aggregate")
        val outcomes = pairs.map { case (left, right) => _composed(left, right) }
        Then("optional facts inherit in either direction and matching facts remain unchanged")
        outcomes shouldBe Vector.fill(3)(populated)
      }

      "rejects incompatible opaque risks, dispositions and reasoning classes" in {
        Given("otherwise valid requirements differing in each equality-constrained fact")
        val defaults = _requirement.copy(disposition = Some(_disposition),
          reasoningClass = Some(ReasoningClassReference("application:analysis")))
        val variants = Vector(
          ("risk", defaults.copy(risk = RiskLevel("another-risk"))),
          ("disposition", defaults.copy(disposition = Some(_disposition.copy(originContextIdentity = "other")))),
          ("reasoningClass", defaults.copy(reasoningClass = Some(ReasoningClassReference("application:other"))))
        )
        When("the incompatible facts are composed")
        val outcomes = variants.map { case (field, value) =>
          (field, resolveC(Some(Declaration(executionRequirement = Some(defaults))),
            Some(Declaration(executionRequirement = Some(value)))))
        }
        Then("each mismatch returns its stable field policy without selecting a weaker or different provider")
        outcomes.foreach { case (field, result) =>
          result shouldBe a[Consequence.Failure[_]]
          _policy(result) shouldBe Some(s"validation.metadata.execution-requirement.$field")
        }
      }
    }

    "remain deterministic and preserve source declarations" in {
      Given("immutable declarations with differently represented duration and overlapping features and capabilities")
      val feature = FeatureReference("component:orders", "approval")
      val specification = Declaration(purposes = Some(Set(Purpose.FULL)), features = Set(feature),
        expectedDuration = Some(2.seconds), executionRequirement = Some(_requirement))
      val operation = Declaration(purposes = Some(Set(Purpose.FOCUSED, Purpose.ADMISSION)), features = Set(feature),
        expectedDuration = Some(500.millis), executionRequirement = Some(_requirement.copy(reviewRequired = true)))
      val original = (specification, operation)
      When("the same declarations are repeatedly interpreted")
      val outcomes = Vector.fill(10)(resolveC(Some(specification), Some(operation)))
      Then("each resolution is equal and all exact source facts remain unchanged")
      outcomes.distinct.size shouldBe 1
      outcomes.head.toOption should not be empty
      (specification, operation) shouldBe original
      specification.expectedDuration.map(_.unit) shouldBe Some(SECONDS)
      operation.expectedDuration.map(_.unit) shouldBe Some(MILLISECONDS)
      specification.executionRequirement.map(_.reviewRequired) shouldBe Some(false)
    }
  }

  "E5 Whole input schema declarations" must afterWord(
    "in spec:validation-test-metadata, example:E5, rules:R1,R4, phase:6.2"
  ) {
    "normalize schema order and replace or explicitly clear the whole inherited schema" in {
      Given("docs/spec/validation-test-metadata.md R1,R4 E5: trusted specification and operation schemas")
      val defaults = Vector(ValidationInputsV1.Parameter("z", ValidationInputsV1.Kind.Text),
        ValidationInputsV1.Parameter("a", ValidationInputsV1.Kind.Integer))
      val replacement = Vector(ValidationInputsV1.Parameter("enabled", ValidationInputsV1.Kind.BooleanValue))
      val declaration = Declaration(requiredSemantics = Set("inputSchema"), inputSchema = Some(defaults))
      When("operations inherit, replace or explicitly clear the schema")
      val inherited = _resolved(Some(declaration), Some(Declaration()))
      val replaced = _resolved(Some(declaration), Some(Declaration(requiredSemantics = Set("inputSchema"),
        inputSchema = Some(replacement))))
      val cleared = _resolved(Some(declaration), Some(Declaration(inputSchema = Some(Vector.empty))))
      Then("schemas sort by name and whole operation overrides never merge individual parameters")
      declaration.validateC.toOption.get.inputSchema shouldBe Some(defaults.reverse)
      inherited.inputSchema shouldBe defaults.reverse
      replaced.inputSchema shouldBe replacement
      cleared.inputSchema shouldBe empty
      inherited.origins shouldBe Origins(true, true)
      ABI_VERSION shouldBe 1
    }

    "reject missing compatibility semantics, null schemas and invalid defaults before inheritance" in {
      Given("docs/spec/validation-test-metadata.md R1,R4 E5: invalid specification declarations under valid overrides")
      val schema = Vector(ValidationInputsV1.Parameter("count", ValidationInputsV1.Kind.Integer))
      val invalid = Vector(Declaration(inputSchema = Some(schema)),
        Declaration(requiredSemantics = Set("inputSchemas"), inputSchema = Some(schema)),
        Declaration(inputSchema = null), Declaration(inputSchema = Some(null)),
        Declaration(requiredSemantics = Set("inputSchema"), inputSchema = Some(Vector(null))),
        Declaration(requiredSemantics = Set("inputSchema"), inputSchema = Some(Vector(
          ValidationInputsV1.Parameter("count", ValidationInputsV1.Kind.Integer,
            defaultValue = Some(org.goldenport.cncf.processexecution.SbtScenarioParameters.Value.Text("1")))))))
      val overridevalue = Declaration(inputSchema = Some(Vector.empty))
      When("every declaration is admitted before applying an operation override")
      val outcomes = invalid.map(x => resolveC(Some(x), Some(overridevalue)))
      Then("invalid inheritance cannot be hidden and nonempty schemas require explicit inputSchema semantics")
      outcomes.foreach(_.isFaillure shouldBe true)
      _policy(outcomes.head) shouldBe Some("validation.metadata.required-semantics")
      _policy(outcomes(1)) shouldBe Some("validation.metadata.required-semantics")
      Declaration(requiredSemantics = Set("inputSchema")).validateC.isFaillure shouldBe false
    }
  }

  private def _resolved(specification: Option[Declaration], operation: Option[Declaration]): Metadata =
    resolveC(specification, operation).toOption.getOrElse(fail("declarations must resolve"))

  private def _composed(left: ExecutionRequirement, right: ExecutionRequirement): ExecutionRequirement =
    _resolved(Some(Declaration(executionRequirement = Some(left))),
      Some(Declaration(executionRequirement = Some(right)))).executionRequirement
      .getOrElse(fail("composed requirement must be present"))

  private def _requirement: ExecutionRequirement =
    ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("project-specific-risk"),
      ReasoningLevel.Standard, reviewRequired = false)

  private def _disposition: ExecutionDisposition =
    ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required, "origin:review")

  private def _policy[A](result: Consequence[A]): Option[String] = result match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => fail("expected structured policy failure")
  }
}
