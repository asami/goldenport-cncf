package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.operation.evaluation.{CorpusCaseReference, CorpusEvaluationCorrelation, CorpusRevisionReference, ExperimentArmReference, ExperimentEvaluationCorrelation, ExperimentReference, ExperimentRunReference, OperationEvaluationAssignment, OperationEvaluationContext, OperationEvaluationCrossSinkPolicy, OperationEvaluationCrossSinkRoute, OperationEvaluationLimitationKind, OperationEvaluationName, OperationEvaluationOperationIdentity, OperationEvaluationSinkIdentity, OperationEvaluationText}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSinkSocket, DeterministicCorpusEvaluationSink, DeterministicExperimentEvaluationSink, ExperimentEvaluationSinkSocket}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the operation-evaluation runtime carrier.
 *
 * @since   Jul. 23, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _instant = Instant.parse("2026-07-23T13:00:00Z")
  private val _clock = Clock.fixed(_instant, ZoneOffset.UTC)

  "OperationEvaluationContext" should {
    "preserve causal runtime state" which {
    "preserve immutable invocation and attempt correlation through context rebinding" in {
      Given("a prepared logical operation invocation with an active provider sink")
      val operation = _success(OperationEvaluationOperationIdentity.createC("catalog", "pricing", "quote"))
      val sink = _sink_identity("catalog", "textus-corpus")
      val policy = _cross_sink_policy(maximumdepth = 3)
      val rawbase = _execution_context("rebind")
      val base = rawbase.withScope(
        ScopeContext.withOperationEvaluationCrossSinkPolicy(
          rawbase.cncfCore.scope,
          policy
        )
      )
      val (corpus, experiment) = _admitted_correlations()
      val assignment = OperationEvaluationAssignment(
        _success(OperationEvaluationName.parseC("variant-b")),
        Some(_success(OperationEvaluationText.parseC("execution-plan-b")))
      )
      val prepared = _success(ExecutionContext.prepareOperationEvaluation(
        base,
        operation,
        Some(corpus),
        Some(experiment),
        Some(assignment)
      ))
      val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(prepared))
      val active = _success(ExecutionContext.withActiveOperationEvaluationSink(attempted, sink))
      val correlation = active.operationEvaluation.correlation.getOrElse(fail("attempt correlation missing"))

      When("security, runtime, and scope bindings are replaced")
      val secured = ExecutionContext.withSecurityContext(
        active,
        ExecutionContext.create(SecurityContext.Privilege.System).security
      )
      val runtime = ExecutionContext.withRuntimeContext(secured, secured.runtime)
      val rebound = runtime.withScope(runtime.cncfCore.scope.createChildScope(ScopeKind.Action, "quote"))
      val nested = _success(ExecutionContext.prepareOperationEvaluation(rebound, operation))

      Then("the current invocation remains stable and a nested invocation receives explicit parentage")
      rebound.operationEvaluation shouldBe active.operationEvaluation
      rebound.operationEvaluation.correlation shouldBe Some(correlation)
      correlation.corpus shouldBe Some(corpus)
      correlation.experiment shouldBe Some(experiment)
      rebound.operationEvaluation.invocation.flatMap(_.assignment) shouldBe Some(assignment)
      rebound.operationEvaluation.activeSinks shouldBe Vector(sink)
      rebound.cncfCore.scope.operationEvaluationCrossSinkPolicy shouldBe policy
      val nestedinvocation = nested.operationEvaluation.invocation.getOrElse(fail("nested invocation missing"))
      nestedinvocation.executionId should not be correlation.executionId
      nestedinvocation.parentExecutionId shouldBe Some(correlation.executionId)
      nested.operationEvaluation.correlation shouldBe None
      nested.operationEvaluation.activeSinks shouldBe Vector(sink)
      nestedinvocation.corpus shouldBe None
      nestedinvocation.experiment shouldBe None
      nestedinvocation.assignment shouldBe None
      val projected = nested.operationEvaluation.toRecord.print
      projected should include ("catalog")
      projected should include ("textus-corpus")
      projected should not include "DeterministicCorpusEvaluationSink"
      projected should not include "provider-payload"
    }
    }

    "resolve scope-owned evaluation capabilities" which {
    "inherit component-owned evaluation sinks only into descendant scopes" in {
      Given("separate components with installed Corpus and Experiment sinks plus an unrelated sibling")
      val subsystem = TestComponentFactory.emptySubsystem("operation_evaluation_scope")
      val corpussink = _success(DeterministicCorpusEvaluationSink.createC("catalog", "textus-corpus"))
      val experimentsink = _success(DeterministicExperimentEvaluationSink.createC("pricing", "textus-experiment"))
      val corpusowner = _initialized_component(subsystem, "catalog", CorpusComponent())
      corpusowner.installSpi(corpussink)
      val experimentowner = _initialized_component(subsystem, "pricing", ExperimentComponent())
      experimentowner.installSpi(experimentsink)
      val sibling = _initialized_component(subsystem, "pricing", PlainComponent())

      When("Action scopes are created below both owning components")
      val corpusaction = corpusowner.scopeContext.createChildScope(ScopeKind.Action, "quote")
      val experimentaction = experimentowner.scopeContext.createChildScope(ScopeKind.Action, "compare")

      Then("each owner and its descendants see only their capability while the sibling sees neither")
      corpusowner.scopeContext.corpusEvaluationSinkOption shouldBe Some(corpussink)
      corpusaction.corpusEvaluationSinkOption shouldBe Some(corpussink)
      corpusaction.experimentEvaluationSinkOption shouldBe None
      experimentowner.scopeContext.experimentEvaluationSinkOption shouldBe Some(experimentsink)
      experimentaction.experimentEvaluationSinkOption shouldBe Some(experimentsink)
      experimentaction.corpusEvaluationSinkOption shouldBe None
      sibling.scopeContext.corpusEvaluationSinkOption shouldBe None
      sibling.scopeContext.experimentEvaluationSinkOption shouldBe None
    }

    "inherit subsystem-owned cross-sink policy into component and Action scopes" in {
      Given("a subsystem assembled with one explicit Experiment-to-Corpus route")
      val policy = _cross_sink_policy(maximumdepth = 3)
      val subsystem = Subsystem(
        name = "operation_evaluation_policy_scope",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command,
        operationEvaluationCrossSinkPolicyOption = Some(policy)
      )
      val component = _initialized_component(subsystem, "catalog", CorpusComponent())
      subsystem.add(component)

      When("the component creates a descendant Action scope")
      val action = component.scopeContext.createChildScope(ScopeKind.Action, "quote")

      Then("both scopes resolve the immutable subsystem policy")
      component.scopeContext.operationEvaluationCrossSinkPolicy shouldBe policy
      action.operationEvaluationCrossSinkPolicy shouldBe policy
    }
    }

    "bound cross-sink execution" which {
    "resolve cross-sink routes by current source, target, and finite depth" in {
      Given("one allowlisted Experiment-to-Corpus route and distinct logical sink identities")
      val policy = _cross_sink_policy(maximumdepth = 2)
      val experiment = _sink_identity(
        "pricing",
        "textus-experiment",
        "experiment-evaluation-sink"
      )
      val corpus = _sink_identity("catalog", "textus-corpus")
      val reverse = _sink_identity(
        "pricing",
        "textus-experiment-2",
        "experiment-evaluation-sink"
      )

      When("same-sink, allowed, reverse, and depth-exhausted routes are evaluated")
      val same = policy.limitation(Vector(experiment), experiment)
      val allowed = policy.limitation(Vector(experiment), corpus)
      val denied = policy.limitation(Vector(corpus), reverse)
      val exhausted = policy.limitation(Vector(experiment, reverse), corpus)
      val invalidshallow = OperationEvaluationCrossSinkPolicy.createC(
        policy.allowedRoutes,
        1
      )
      val invalidoverflow = OperationEvaluationCrossSinkPolicy.createC(
        Vector.empty,
        OperationEvaluationCrossSinkPolicy.MAXIMUM_DEPTH + 1
      )

      Then("same-sink remains forbidden and only the bounded allowlisted direction proceeds")
      same shouldBe Some(OperationEvaluationLimitationKind.ReentrantSuppressed)
      allowed shouldBe None
      denied shouldBe Some(OperationEvaluationLimitationKind.CrossSinkSuppressed)
      exhausted shouldBe Some(OperationEvaluationLimitationKind.CrossSinkDepthExceeded)
      invalidshallow shouldBe a[Consequence.Failure[_]]
      invalidoverflow shouldBe a[Consequence.Failure[_]]
      OperationEvaluationCrossSinkPolicy.disabled
        .limitation(Vector(experiment), corpus) shouldBe
        Some(OperationEvaluationLimitationKind.CrossSinkSuppressed)
    }

    "bound active sink ancestry deterministically" in {
      Given("generated distinct sink identities within and beyond the nesting limit")
      val counts = Gen.choose(1, OperationEvaluationContext.MAXIMUM_ACTIVE_SINKS)

      When("the identities are entered into an immutable evaluation context")
      val property = Prop.forAll(counts) { count =>
        val sinks = Vector.tabulate(count)(index => _sink_identity(s"socket-$index", s"provider-$index"))
        val result = sinks.foldLeft[Consequence[OperationEvaluationContext]](Consequence.success(OperationEvaluationContext.empty)) {
          case (context, sink) => context.flatMap(_.enterSinkC(sink))
        }
        result.toOption.exists(_.activeSinks == sinks)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)
      val full = Vector.tabulate(OperationEvaluationContext.MAXIMUM_ACTIVE_SINKS)(index =>
        _sink_identity(s"full-socket-$index", s"full-provider-$index")
      ).foldLeft(OperationEvaluationContext.empty)((context, sink) => _success(context.enterSinkC(sink)))
      val overflow = full.enterSinkC(_sink_identity("overflow-socket", "overflow-provider"))

      Then("all legal ancestries are preserved and the first overflow is rejected")
      checked.passed shouldBe true
      overflow shouldBe a[Consequence.Failure[_]]
    }
    }
  }

  private final case class CorpusComponent() extends Component with CorpusEvaluationSinkSocket
  private final case class ExperimentComponent() extends Component with ExperimentEvaluationSinkSocket
  private final case class PlainComponent() extends Component

  private def _admitted_correlations(): (CorpusEvaluationCorrelation, ExperimentEvaluationCorrelation) = {
    val revision = _success(CorpusRevisionReference.parseC("revision-9"))
    val corpus = CorpusEvaluationCorrelation.create(
      revision,
      Some(_success(CorpusCaseReference.parseC("case-17")))
    )
    val experiment = _success(ExperimentEvaluationCorrelation.createC(
      _success(ExperimentReference.parseC("experiment-17")),
      Some(_success(ExperimentArmReference.parseC("arm-b"))),
      Some(_success(ExperimentRunReference.parseC("run-4"))),
      Some(revision)
    ))
    corpus -> experiment
  }

  private def _execution_context(seed: String): ExecutionContext = {
    val ids = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("operation_evaluation", "context"),
      _clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(ExecutionContext.create(_clock), ids)
  }

  private def _sink_identity(
    socketcomponent: String,
    providercomponent: String,
    contract: String = "corpus-evaluation-sink"
  ): OperationEvaluationSinkIdentity =
    _success(OperationEvaluationSinkIdentity.createC(
      contract,
      socketcomponent,
      providercomponent
    ))

  private def _cross_sink_policy(maximumdepth: Int): OperationEvaluationCrossSinkPolicy = {
    val route = _success(OperationEvaluationCrossSinkRoute.createC(
      "experiment-evaluation-sink",
      "corpus-evaluation-sink"
    ))
    _success(OperationEvaluationCrossSinkPolicy.createC(Vector(route), maximumdepth))
  }

  private def _initialized_component[A <: Component](
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    name: String,
    component: A
  ): A = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(
        name = componentid.name,
        componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid),
        protocol = Protocol.empty
      ),
      origin = ComponentOrigin.Builtin
    ))
    component
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(result.toString))
}
