package org.goldenport.cncf.component

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Real Component bootstrap for operational mapping; contract tables do not
  * claim that a concrete AI provider has executed semantic application work.
  * @since   Oct.  8, 2026
  *  version Oct.  8, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class WorkflowExecutionProfileMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _identity = ComponentId("org.goldenport.fixture.ExecutionMapping")
  private val _programming = WorkflowConcreteExecutionProfile("programming", "codex", "gpt-6.1-luna", Map("reasoning-effort" -> "high"))
  private val _engineering = WorkflowConcreteExecutionProfile("engineering", "codex", "gpt-6.1-sol", Map("reasoning-effort" -> "high"))
  private def _profile(value: WorkflowConcreteExecutionProfile) = Json.obj(
    "identity" -> Json.fromString(value.identity), "provider" -> Json.fromString(value.provider),
    "model" -> Json.fromString(value.model), "controls" -> Json.obj(value.controls.toVector.map((key, value) => key -> Json.fromString(value))*))
  private def _settings(model: String = "gpt-6.1-luna") = Json.obj(
    "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
    "identity" -> Json.fromString("local-profiles"), "revision" -> Json.fromString("1"),
    "profiles" -> Json.arr(_profile(_programming.copy(model = model)), _profile(_engineering)),
    "classes" -> Json.arr(
      Json.obj("reasoningClass" -> Json.fromString("CodingProgrammingRoutine"), "profile" -> Json.fromString("programming")),
      Json.obj("reasoningClass" -> Json.fromString("CodingProgrammingStandard"), "profile" -> Json.fromString("programming")),
      Json.obj("reasoningClass" -> Json.fromString("CodingEngineeringStandard"), "profile" -> Json.fromString("engineering")),
      Json.obj("reasoningClass" -> Json.fromString("ReviewStandard"), "profile" -> Json.fromString("engineering"))))
  private final class Probe extends Component {
    var mapping = Option.empty[WorkflowExecutionProfileMapping]
    override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
      WorkflowExecutionProfileParameters.resolveC(params.initializationParameters).map(value => mapping = value)
  }
  private def _bootstrap(default: Option[Json], overridevalue: Option[Json] = None): Consequence[Probe] = {
    val owner = TestComponentFactory.emptySubsystem("execution-mapping")
    val factory = new Component.Factory {
      override def initializationParameterDeclarations = Vector(WorkflowExecutionProfileParameters.declaration)
      protected def create_Component(params: ComponentCreate): Component = new Probe
      protected def create_Core(params: ComponentCreate, component: Component): Component.Core =
        spec_create(_identity.name, _identity, Vector.empty[spec.ServiceDefinition])
    }
    val descriptor = ComponentDescriptor(name = Some(_identity.name), componentName = Some(_identity.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(_identity),
      config = default.map(value => Map(WorkflowExecutionProfileParameters.declaration.name -> value.noSpaces)).getOrElse(Map.empty))
    val metadata = ComponentInstanceMetadata(_identity.name, "selected",
      overridevalue.map(value => Map(WorkflowExecutionProfileParameters.declaration.name -> value.noSpaces)).getOrElse(Map.empty),
      componentId = Some(_identity))
    factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin, Vector(descriptor))
      .withInstanceMetadata(metadata)).map(_.asInstanceOf[Probe])
  }
  private def _requirement(name: String, review: Boolean = false) = ExecutionRequirement(
    Vector(CapabilityRequirement("semantic-work")), RiskLevel("SOURCE"), ReasoningLevel.Standard, review,
    reasoningClass = Some(ReasoningClassReference(name)))
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:workflow-execution-disposition, example:$example, rules:$rules, phase:80")

  "Workflow execution profile mapping" should {
    "E9 bootstrap an optional explicitly configured distinct alternate" in {
      Given("Spec: docs/spec/workflow-execution-disposition.md; Example: E9; Rules: R6,R9; canonical Component settings without fallbacks and with a ReviewStandard alternate")
      val binding = Json.obj("reasoningClass" -> Json.fromString("ReviewStandard"), "profile" -> Json.fromString("programming"))
      val configured = _settings().mapObject(_.add("fallbacks", Json.arr(binding)))
      When("the actual Component Factory decodes both snapshots")
      val absent = _bootstrap(Some(_settings())).toOption.get.mapping.get
      val present = _bootstrap(Some(configured)).toOption.get.mapping.get
      Then("absence stays empty and the one configured alternate resolves by the existing semantic class")
      absent.fallbackClassC(ReasoningClassReference("ReviewStandard")).toOption.get shouldBe None
      present.fallbackClassC(ReasoningClassReference("ReviewStandard")).toOption.get shouldBe Some(_programming)
      present.resolveClassC(ReasoningClassReference("ReviewStandard")).toOption.get shouldBe _engineering
    }
    "E10 reject duplicate dangling and concrete-alias alternates at bootstrap" in {
      Given("Spec: docs/spec/workflow-execution-disposition.md; Example: E10; Rules: R6,R9; invalid fallback declarations and an identity alias of the same provider/model/controls")
      def _binding_(name: String, profile: String) = Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(profile))
      val binding = _binding_("ReviewStandard", "programming")
      val alias = _engineering.copy(identity = "engineering-alias")
      val configurations = Vector(Json.arr(binding, binding), Json.arr(_binding_("AbsentClass", "programming")),
        Json.arr(_binding_("ReviewStandard", "absent-profile")), Json.arr(_binding_("ReviewStandard", "engineering")))
        .map(value => _settings().mapObject(_.add("fallbacks", value))) :+
        _settings().mapObject(_.add("profiles", Json.arr(_profile(_programming), _profile(_engineering), _profile(alias)))
          .add("fallbacks", Json.arr(_binding_("ReviewStandard", alias.identity))))
      When("the normal Factory initializes each invalid canonical snapshot")
      val results = configurations.map(value => _bootstrap(Some(value)))
      Then("each contradiction fails without a default or a same-executor alias fallback")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "E5 consume canonical Component parameters and changed instance configuration" must _metadata("E5", "R6") {
      "through the normal Factory initialization hook" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rule: R6; Example: E5; packaged mapping plus an instance override")
        val initial = _bootstrap(Some(_settings())).toOption.get.mapping.get
        When("a subsequent Component assembly overrides the mapping without changing the semantic class")
        val later = _bootstrap(Some(_settings()), Some(_settings("different-supported-model"))).toOption.get.mapping.get
        val first = initial.resolveClassC(ReasoningClassReference("CodingProgrammingStandard"))
        val second = later.resolveClassC(ReasoningClassReference("CodingProgrammingStandard"))
        Then("standard bootstrap precedence changes subsequent resolution and leaves the earlier mapping historical")
        first.toOption.get.model shouldBe "gpt-6.1-luna"
        second.toOption.get.model shouldBe "different-supported-model"
        initial.resolveClassC(ReasoningClassReference("CodingProgrammingRoutine")) shouldBe first
      }
    }
    "E6 resolve runtime placement without letting model equality waive independent review" must _metadata("E6", "R6,R7") {
      "for trivial, bounded, programming, review and isolation cases" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R6,R7; Example: E6; a canonical mapping and an engineering executor descriptor")
        val mapping = _bootstrap(Some(_settings())).toOption.get.mapping.get
        val current = WorkflowExecutorDescriptor("requesting-context", _engineering)
        val cases = Vector(
          (_requirement("CodingProgrammingRoutine"), WorkflowExecutionPreference(forceInline = true), ExecutionPlacement.Inline, ExecutionIndependence.Optional, _engineering),
          (_requirement("CodingEngineeringStandard"), WorkflowExecutionPreference(), ExecutionPlacement.Inline, ExecutionIndependence.Optional, _engineering),
          (_requirement("CodingProgrammingStandard"), WorkflowExecutionPreference(), ExecutionPlacement.Delegated, ExecutionIndependence.Optional, _programming),
          (_requirement("ReviewStandard", review = true), WorkflowExecutionPreference(), ExecutionPlacement.Delegated, ExecutionIndependence.Required, _engineering),
          (_requirement("CodingEngineeringStandard"), WorkflowExecutionPreference(isolateContext = true), ExecutionPlacement.Delegated, ExecutionIndependence.Optional, _engineering))
        When("the generic runtime resolves the requests")
        val outcomes = cases.map { (request, preference, placement, independence, profile) =>
          (mapping.resolveC(request, current, preference).toOption.get, placement, independence, profile)
        }
        Then("placement uses concrete matching and explicit preferences while independent review stays separate")
        outcomes.foreach { (result, placement, independence, profile) =>
          result.requirement.disposition shouldBe Some(ExecutionDisposition(placement, independence, "requesting-context"))
          result.profile shouldBe profile
        }
      }
    }
    "E7 require actual matching executor observations and retain issued decisions" must _metadata("E7", "R7,R8") {
      "rather than reconstructing execution from a semantic reply" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R7,R8; Example: E7; resolved independent review")
        val mapping = _bootstrap(Some(_settings())).toOption.get.mapping.get
        val current = WorkflowExecutorDescriptor("implementation-context", _engineering)
        val resolved = mapping.resolveC(_requirement("ReviewStandard", review = true), current).toOption.get
        When("descriptor observations conflict with profile or independence, or an issued request is resolved again")
        val samecontext = resolved.observeC(current)
        val wrongprofile = resolved.observeC(WorkflowExecutorDescriptor("review-context", _programming))
        val actual = resolved.observeC(WorkflowExecutorDescriptor("review-context", _engineering))
        val reissue = mapping.resolveC(resolved.requirement, current)
        Then("only the matching distinct-context observation satisfies the selected contract and re-resolution is rejected")
        samecontext shouldBe a[Consequence.Failure[_]]
        wrongprofile shouldBe a[Consequence.Failure[_]]
        actual.toOption.get.executorContextIdentity shouldBe "review-context"
        reissue shouldBe a[Consequence.Failure[_]]
      }
    }
    "E8 reject incomplete configuration and contradictory runtime preferences" must _metadata("E8", "R6,R7") {
      "without a private configuration fallback" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R6,R7; Example: E8; absent or malformed Component parameter and a valid review request")
        val absence = _bootstrap(None).toOption.get.mapping
        val extra = _settings().deepMerge(Json.obj("defaultProfile" -> Json.fromString("engineering")))
        val dangling = _settings().mapObject(_.add("classes", Json.arr(Json.obj(
          "reasoningClass" -> Json.fromString("ReviewStandard"), "profile" -> Json.fromString("absent")))))
        val mapping = _bootstrap(Some(_settings())).toOption.get.mapping.get
        When("actual bootstrap/lookup or runtime preference admission processes the contradictory input")
        val malformed = Vector(extra, dangling).map(value => _bootstrap(Some(value)))
        val missing = mapping.resolveClassC(ReasoningClassReference("UnconfiguredSemanticClass"))
        val forced = mapping.resolveC(_requirement("ReviewStandard", review = true),
          WorkflowExecutorDescriptor("origin", _engineering), WorkflowExecutionPreference(forceInline = true))
        Then("legacy absence stays absent, missing classes/configuration reject and review cannot become forced inline")
        absence shouldBe None
        malformed.foreach(_ shouldBe a[Consequence.Failure[_]])
        missing shouldBe a[Consequence.Failure[_]]
        forced shouldBe a[Consequence.Failure[_]]
      }
    }
  }
}
