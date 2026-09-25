package org.goldenport.cncf.workflow

import cats.~>
import cats.syntax.all.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.cncf.statemachine.StateMachineRequiredOperationAction
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/** @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionGapRoutingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = CandidateRef("candidate", "revision-1")
  private val _semantic = AdmissionRequirement(
    "semantic-review", RequirementKind.Semantic, "Review", TypedValue("Scope", "all"))
  private val _deterministic = AdmissionRequirement(
    "deterministic-check", RequirementKind.Deterministic, "Check", TypedValue("Scope", "all"))
  private val _authority = AdmissionRequirement(
    "authority-choice", RequirementKind.Authority, "Decision", TypedValue("Scope", "all"))
  private val _evaluation = AdmissionEvaluation(_target, Vector.empty,
    Vector(_semantic, _deterministic, _authority).map(r => AdmissionGap(r, Vector(EvidenceDefect.Missing))))
  private def _operation(capability: String) = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(capability),
    s"action.$capability",
    StateMachineOperationIdentity("service", capability),
    None,
    None,
    StateMachineRequiredOperationMetadata(
      ContextContract("context", Vector.empty, Vector.empty),
      CompletionContract("completion", Vector.empty),
      EvidenceContract("evidence", Vector.empty),
      Vector.empty
    )
  )
  private val _semantic_operation = _operation("semantic-review")
  private val _deterministic_operation = _operation("deterministic-check")
  private val _bindings = Vector(
    Binding(_semantic.identity, Target.SemanticAction(_semantic_operation)),
    Binding(_deterministic.identity, Target.DeterministicOperation(_deterministic_operation)),
    Binding(_authority.identity, Target.AuthorityDecision("review-authority"))
  )

  "Candidate Admission Gap Routing" should {
    "leave an authority gap at the ordinary Decision boundary, not a WorkOrder result" in {
      Given("an authority requirement bound to an application-owned Decision")
      val gap = _evaluation.copy(gaps = Vector(_evaluation.gaps.last))
      When("the unmet authority requirement is routed")
      val route = resolveC(gap, _bindings).toOption.get.head
      Then("the route remains a Decision rather than a semantic WorkOrder")
      route.target shouldBe Target.AuthorityDecision("review-authority")
      val authorityoperation = _operation("review-authority").copy(
        inputType = Some(StateMachineInputTypeReference("DecisionInput")),
        resultType = Some(StateMachineResultTypeReference("DecisionResult")))
      val continuation = Continuation(StateMachineRunIdentity("run-decision"),
        ContinuationIdentity("decision-1"), StateMachineRevision("state-1"),
        authorityoperation, ContextBundle("authority choice", Vector.empty, Vector.empty,
          ContextSnapshot("workflow-v1")))
      val handle = WorkflowHandle(ComponentId("org.goldenport.cncf.test.CamReferenceSpec"),
        WorkflowDefinitionIdentity("CamReference"), WorkflowDefinitionRevision("workflow-v1"),
        InstanceIdentity("instance-1"))
      val request = ContinuationRequest[String](continuation.runId, continuation.continuationId,
        continuation.expectedRevision, continuation.requiredOperation.identity,
        continuation.requiredOperation.operation, Some(TypedValue("DecisionInput", "choose")),
        continuation.requiredOperation.resultType, continuation.context,
        continuation.requiredOperation.metadata.completionContract,
        continuation.requiredOperation.metadata.evidenceContract,
        StateMachineOperationIdentity("CamService", "submitDecision"))
      val decision = WorkflowInteraction[String, Nothing](handle,
        WorkflowContinuation.Decision(request,
          MinimalPresentation("Authority decision", "A human choice is required")))
      val codec = new PayloadCodec[String] {
        val typeIdentity = "DecisionInput"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected decision input")
      }

      When("the Decision is encoded for the normal Workflow wire boundary")
      val wire = WorkflowDecisionJsonV1.encodeC(decision, codec).toOption.get
      Then("it decodes as a Decision and cannot be consumed as a WorkOrder")
      WorkflowDecisionJsonV1.decodeC(wire, codec) shouldBe Consequence.success(decision)
      WorkflowWorkOrderJsonV1.decodeC(wire, codec) shouldBe a[Consequence.Failure[?]]
      val attemptedresult = ContinuationResult(handle, continuation.runId,
        continuation.continuationId, continuation.expectedRevision,
        continuation.context.snapshot, TypedValue("DecisionResult", "approved"),
        ContextReference("decision-result", "1"), Vector.empty, ExecutionEvidence(Vector.empty))
      And("a WorkOrder result cannot directly satisfy the Decision")
      admitResultC(decision, attemptedresult) shouldBe a[Consequence.Failure[?]]
    }

    "dispatch a deterministic gap through the ordinary bound Provider while leaving authority undecided" in {
      Given("deterministic and authority gaps with distinct declared boundaries")
      val gaps = _evaluation.copy(gaps = _evaluation.gaps.tail)
      When("the gaps are routed")
      val routes = resolveC(gaps, _bindings).toOption.get
      Then("the deterministic operation is separate from the authority Decision")
      routes.map(_.target) shouldBe Vector(
        Target.DeterministicOperation(_deterministic_operation),
        Target.AuthorityDecision("review-authority"))
      val operation = routes.head.target match {
        case Target.DeterministicOperation(value) => value
        case other => fail(s"unexpected deterministic route: $other")
      }
      val result = ActionExecution.Completed(StateMachineOperationResult(
        StateMachineResultTypeReference("CheckResult"), ContextReference("check-result", "1")))
      var providercalls = 0
      val provider = new StateMachineDeterministicProvider {
        val identity = ProviderIdentity("provider.deterministic-check")
        def execute(request: ProviderExecutionRequest): ActionExecution = {
          request.requiredOperation shouldBe operation
          providercalls += 1
          result
        }
      }
      val resolver = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(operation.identity, provider.identity)), Vector(provider)).toOption.get
      val component = new Component() {}
      component.withStateMachineProviderResolver(resolver)
      val root = ExecutionContext.create()
      val context = root.withScope(Component.Context("candidate-admission-gap", root.scope,
        component, ComponentOrigin.Embed))
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(context))
      val request = ProviderExecutionRequest(StateMachineRunIdentity("run-check"), operation,
        None, ContextBundle("deterministic check", Vector.empty, Vector.empty, ContextSnapshot("workflow-1")))
      val action = new StateMachineRequiredOperationAction[Unit, Unit]((_, _) => request)

      When("the deterministic Action is interpreted with its bound Provider")
      val executed = interpreter.run(action.program((), ()))
      Then("the Provider runs once and the authority remains undecided")
      executed shouldBe Consequence.success(result)
      providercalls shouldBe 1
      routes.last.target shouldBe Target.AuthorityDecision("review-authority")
      providercalls shouldBe 1 // The authority gap was not converted to a Provider call.

      val unbound = new Component() {}
      unbound.withStateMachineProviderResolver(StateMachineProviderResolver.empty)
      val unboundcontext = root.withScope(Component.Context("unbound-candidate-gap", root.scope,
        unbound, ComponentOrigin.Embed))
      When("the same Action is interpreted without its Provider binding")
      val unboundresult = new UnitOfWorkInterpreter(new UnitOfWork(unboundcontext))
        .run(action.program((), ()))
      Then("execution fails closed without invoking the earlier Provider again")
      unboundresult shouldBe a[Consequence.Failure[?]]
      providercalls shouldBe 1
    }

    "accept only operations present in the admitted Cozy Workflow Required SPI" in {
      Given("a Cozy Candidate-Admission definition admitted to a Workflow instance")
      import WorkflowInstancePersistence.{CausationReference, CommittedEntityTransitionReference,
        CorrelationReference, DerivedCompositeOccurrenceReference, HistoryEntry, HistorySequence,
        InstanceRecord, InstanceRevision, Lifecycle, ProgressionReference, initialRevision}
      def _resource_(path: String): String = {
        val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
        try source.mkString finally source.close()
      }
      val sidecar = CandidateAdmissionProducerAbi.parseC(
        _resource_("/workflow/candidate-admission-producer-abi.json")).toOption.get
      val workflow = CandidateWorkflowAbi.parseC(
        _resource_("/workflow/candidate-admission-workflow-abi.json").stripSuffix("\n"),
        sidecar, "modeler/candidate-admission-workflow.cml").toOption.get
      val admitted = WorkflowInstancePersistence.bindCandidateDefinitionC(workflow).toOption.get
      val declared = _operation("capture-payment-capability").copy(
        actionIdentity = "judge-payment",
        operation = StateMachineOperationIdentity("OrderService", "capturePayment"),
        inputType = Some(StateMachineInputTypeReference("PaymentCommand")),
        resultType = Some(StateMachineResultTypeReference("PaymentResult"))
      )
      val gap = _evaluation.copy(gaps = Vector(_evaluation.gaps.head))
      val bound = Vector(Binding(_semantic.identity, Target.SemanticAction(declared)))
      val initial = InstanceRecord(InstanceIdentity("cam-instance"), admitted, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("cam-correlation"),
        CausationReference("cam-causation"),
        Some(DerivedCompositeOccurrenceReference("cam-composite")),
        Some(CommittedEntityTransitionReference("cam-predecessor")), None, Vector.empty)
      val active = initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L),
        InstanceRevision(1L), Lifecycle.Active, Some(ProgressionReference("cam-progress")),
        CorrelationReference("cam-correlation"), CausationReference("cam-causation"),
        Some(DerivedCompositeOccurrenceReference("cam-composite")),
        Some(CommittedEntityTransitionReference("cam-predecessor")), None)).toOption.get
      When("the declared gap is resolved against active and unstarted instances")
      val activeroute = resolveForInstanceC(gap, bound, active)
      val unstartedroute = resolveForInstanceC(gap, bound, initial)
      Then("only the active instance admits the declared operation")
      activeroute shouldBe
        Consequence.success(Vector(Resolution(gap.gaps.head, bound.head.target)))
      unstartedroute shouldBe a[Consequence.Failure[?]]
      val suspendedboundary = WorkflowInstancePersistence.SuspensionBoundary(
        WorkflowInstancePersistence.ContinuationIdentity("cam-continuation"), InstanceRevision(1L),
        WorkflowInstancePersistence.ContextSnapshotReference("cam-snapshot"),
        WorkflowInstancePersistence.CompletionReference("cam-completion"),
        WorkflowInstancePersistence.EvidenceReference("cam-evidence"),
        CorrelationReference("cam-resume"))
      val suspended = initial.appendC(initialRevision,
        active.history.head.copy(suspension = Some(suspendedboundary))).toOption.get
      When("suspended or missing instances and undeclared operation shapes are resolved")
      val suspendedroute = resolveForInstanceC(gap, bound, suspended)
      val missingroute = resolveForInstanceC(gap, bound, null)
      val declaredroute = resolveDeclaredC(gap, bound, admitted)
      val wrongaction = resolveDeclaredC(gap, Vector(Binding(_semantic.identity,
        Target.SemanticAction(declared.copy(actionIdentity = "admit-payment")))), admitted)
      val wrongresult = resolveDeclaredC(gap, Vector(Binding(_semantic.identity,
        Target.SemanticAction(declared.copy(resultType = None)))), admitted)
      val missingdefinition = resolveDeclaredC(gap, bound, null)
      Then("suspended, missing, and undeclared boundaries fail closed")
      suspendedroute shouldBe a[Consequence.Failure[?]]
      missingroute shouldBe a[Consequence.Failure[?]]
      declaredroute shouldBe
        Consequence.success(Vector(Resolution(gap.gaps.head, bound.head.target)))
      wrongaction shouldBe a[Consequence.Failure[?]]
      wrongresult shouldBe a[Consequence.Failure[?]]
      missingdefinition shouldBe a[Consequence.Failure[?]]
    }

    "map every gap to its declared existing boundary without choosing one" in {
      Given("semantic, deterministic, and authority gaps with declared bindings")
      When("all gaps and then no gaps are routed")
      val allroutes = resolveC(_evaluation, _bindings)
      val noroutes = resolveC(_evaluation.copy(gaps = Vector.empty), _bindings)
      Then("each gap retains its own boundary and an admitted candidate needs no route")
      allroutes shouldBe Consequence.success(Vector(
        Resolution(_evaluation.gaps(0), Target.SemanticAction(_semantic_operation)),
        Resolution(_evaluation.gaps(1), Target.DeterministicOperation(_deterministic_operation)),
        Resolution(_evaluation.gaps(2), Target.AuthorityDecision("review-authority"))
      ))
      noroutes shouldBe Consequence.success(Vector.empty)
    }

    "lower a declared semantic gap through the ordinary Required SPI Action" in {
      Given("a semantic gap bound to an ordinary Required SPI Action")
      val resolved = resolveC(_evaluation.copy(gaps = Vector(_evaluation.gaps.head)), _bindings)
        .toOption.get.head
      val required = resolved.target match {
        case Target.SemanticAction(value) => value
        case other => fail(s"unexpected gap route: $other")
      }
      val run = StateMachineRunIdentity("run-1")
      val context = ContextBundle("context", Vector.empty, Vector.empty, ContextSnapshot("workflow-r1"))
      val request = ProviderExecutionRequest(run, required, None, context)
      val continuation = Continuation(run, ContinuationIdentity("continue-1"),
        StateMachineRevision("state-r1"), required, context)
      val action = new StateMachineRequiredOperationAction[Unit, Unit]((_, _) => request)
      var observed = Option.empty[ProviderExecutionRequest]

      When("the Action program is interpreted by the existing UnitOfWork operation algebra")
      val result = action.program((), ()).value.foldMap(new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.StateMachineProviderExecute(value) =>
            observed = Some(value)
            Consequence.success(ActionExecution.Suspended(continuation)).asInstanceOf[Consequence[A]]
          case other => fail(s"unexpected UnitOfWork operation: $other")
        }
      })

      Then("the ordinary Provider request may suspend through the Phase 77 Continuation")
      observed shouldBe Some(request)
      result.toOption shouldBe Some(Consequence.success(ActionExecution.Suspended(continuation)))
    }

    "fail closed for a missing, duplicate, malformed, or kind-mismatched binding" in {
      Given("gap bindings that are absent, duplicated, malformed, or assigned the wrong kind")
      When("each invalid binding set is resolved")
      val missing = resolveC(_evaluation, _bindings.tail)
      val duplicate = resolveC(_evaluation, _bindings :+ _bindings.head)
      val wrongkind = resolveC(_evaluation, _bindings.updated(0,
        Binding(_semantic.identity, Target.DeterministicOperation(_semantic_operation))))
      val emptydecision = resolveC(_evaluation, _bindings.updated(2,
        Binding(_authority.identity, Target.AuthorityDecision(""))))
      val duplicategap = resolveC(_evaluation.copy(gaps = _evaluation.gaps :+ _evaluation.gaps.head), _bindings)
      val emptydefects = resolveC(_evaluation.copy(gaps = Vector(AdmissionGap(_semantic, Vector.empty))), _bindings)
      val invalidmetadata = resolveC(_evaluation, _bindings.updated(0,
        Binding(_semantic.identity, Target.SemanticAction(
          _semantic_operation.copy(metadata = _semantic_operation.metadata.copy(evidenceContract = null))))))
      Then("none may produce an executable route")
      missing shouldBe a[Consequence.Failure[?]]
      duplicate shouldBe a[Consequence.Failure[?]]
      wrongkind shouldBe a[Consequence.Failure[?]]
      emptydecision shouldBe a[Consequence.Failure[?]]
      duplicategap shouldBe a[Consequence.Failure[?]]
      emptydefects shouldBe a[Consequence.Failure[?]]
      invalidmetadata shouldBe a[Consequence.Failure[?]]
    }
  }
}
