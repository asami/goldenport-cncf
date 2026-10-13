package org.goldenport.cncf.component.capability

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowStatusRule
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityTargetSource.*
import ComponentCapabilityTargetAdapter.*
import ComponentCapabilityTargetAdapterSpec.{_scope, _snapshot, _binding, _operation, _workflow, _machine}
import ComponentCapabilityProjectionCodecSpec.{_empty_projection, _definition, _read_fixture, _fixture_scope}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityTargetBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _right[A](result: Either[Diagnostic, A]): A = {
    result.fold(error => fail(s"Unexpected ${error.kind} at ${error.location}: ${error.detail}"), value => value)
  }

  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = {
    result.fold(error => error, _ => fail("Expected a typed target binding failure."))
  }

  private def _present[A](value: Presence[A]): A = {
    value match {
      case Present(item) => item
      case _: Absent => fail("Expected present conformance input.")
    }
  }

  private def _adapt(snapshots: Vector[Snapshot], bindings: Vector[Binding], selected: Scope): Adapted = {
    _right(adapt(AcquisitionInput.Acquired(selected.scopeId, snapshots), ScopeInput.Ready(selected), bindings))
  }

  private def _batch: Adapted = {
    val first = _snapshot("org.example.Payments", "target-r90")
    val second = _snapshot("org.example.Orchestrator", "target-r91")
    _adapt(Vector(first, second), Vector(_binding(first, TargetKind.Operation), _binding(second, TargetKind.Workflow), _binding(first, TargetKind.StateMachine)), _scope)
  }

  private def _projection(contexts: Vector[Binding]): Projection = {
    val definition = _definition("model:test", "cap:base", "cap-v2")
    val declaration = Declaration(_empty_projection.component.componentId, Direction.Provided,
      CapabilityReference(definition.identity, definition.contractVersion),
      Source("model:test", "models/declaration.cml", "declaration-r3", Some(5)), Present(Vector.empty), Present(Vector.empty))
    val edges = contexts.zipWithIndex.map { (context, index) =>
      Realization(declaration._key, context.targetKind, ModelReference(context.identity, ReferenceBoundary.Local),
        context.snapshot.component, context.contractVersion, context.generatedAbiVersion,
        Source("model:test", "models/edges.cml", s"edge-r${index + 10}", Some(index + 20)), Declared)
    }
    _empty_projection.copy(capabilities = Vector(definition), declarations = Vector(declaration.copy(realizations = Present(edges))))
  }

  private def _fixture_inputs: (Scope, Vector[Snapshot], Vector[Binding]) = {
    val selected = _fixture_scope.copy(targetContracts = Vector.empty)
    val operationcontext = Snapshot(ComponentId("org.example.Payments"),
      Source("model:targets", "models/targets.cml", "target-operation-r101", Some(2)),
      Vector(_operation("Charge")), Vector.empty, Vector.empty)
    val workflowcontext = Snapshot(ComponentId("org.example.Orchestrator"),
      Source("model:targets", "models/targets.cml", "target-workflow-r102", Some(4)),
      Vector.empty, Vector(_workflow("Checkout")), Vector.empty)
    val machinecontext = Snapshot(ComponentId("org.example.Payments"),
      Source("model:targets", "models/targets.cml", "target-machine-r103", None),
      Vector.empty, Vector.empty, Vector(_machine("Payment")))
    val contexts = Vector(
      Binding(operationcontext.key, ModelElementId("model:targets", "operation:charge"), TargetKind.Operation, "Charge", "operation-contract-2", "operation-abi-5", operationcontext.source.copy(line = Some(10))),
      Binding(workflowcontext.key, ModelElementId("model:targets", "workflow:checkout"), TargetKind.Workflow, "Checkout", "workflow-contract-6", "workflow-abi-8", workflowcontext.source.copy(line = None)),
      Binding(machinecontext.key, ModelElementId("model:targets", "machine:payment"), TargetKind.StateMachine, "Payment", "machine-contract-3", "machine-abi-10", machinecontext.source.copy(line = Some(12))))
    (selected, Vector(operationcontext, workflowcontext, machinecontext), contexts)
  }

  private def _fixture_adapted: Adapted = {
    val (selected, snapshots, bindings) = _fixture_inputs
    _adapt(snapshots, bindings, selected)
  }

  "Actual target realization binding" should {
    "delegate raw and strict wire admission to the original R12 contract" which {
      "CPT23 bind three actual families and repeated edges in original admitted order" in {
        Given("an actual acquired three-family catalog and explicit ordered raw realization edges")
        val adapted = _batch
        val projection = _projection(adapted.bindings.reverse :+ adapted.bindings.head)
        When("the unchanged R12 raw admission succeeds before actual-object binding")
        val original = _right(ComponentCapabilityAdmission.admit(projection, ScopeInput.Ready(adapted.scope)))
        val bound = _right(admit(projection, adapted))
        Then("original edge order, exact flat targets and exact selected native objects remain available")
        bound.adapted should be theSameInstanceAs adapted
        bound.admitted.projection should be theSameInstanceAs projection
        bound.admitted.capabilities shouldBe original.capabilities
        bound.realizations.map(_.realization.edge) shouldBe original.realizations.map(_.edge)
        bound.realizations.map(_.realization.target) shouldBe original.realizations.map(_.target)
        bound.realizations.map(_.target.contract) shouldBe Vector(adapted.targets(2).contract, adapted.targets(1).contract, adapted.targets(0).contract, adapted.targets(0).contract)
        bound.realizations.zip(bound.admitted.realizations).foreach { (entry, resolved) => entry.realization should be theSameInstanceAs resolved }
        bound.realizations(2).target should be theSameInstanceAs bound.realizations(3).target
        bound.realizations.head.realization.target.source shouldBe adapted.bindings(2).source
        bound.realizations.head.realization.edge.source.sourceRevision shouldBe "edge-r10"
      }

      "CPT24 bind the existing hand-authored strict wire to independent actual metadata" in {
        Given("the existing portable resource, its unchanged companion facts except synthetic targets, and independently declared native contexts")
        val adapted = _fixture_adapted
        val text = _read_fixture()
        When("the adapter delegates strict JSON admission using the effective actual-target scope")
        val bound = _right(admitJson(SourceInput.Acquired(adapted.scope.scopeId, text), adapted))
        val decoded = _right(ComponentCapabilityProjectionCodec.decode(text))
        Then("the complete original contract and actual targets are retained without claiming generated producer evidence")
        bound.admitted.projection shouldBe decoded
        adapted.scopeInput.scope shouldBe _fixture_scope.copy(targetContracts = Vector.empty)
        adapted.scope.targetContracts shouldBe _fixture_scope.targetContracts
        bound.realizations.map(_.target.contract) shouldBe Vector(Contract.Operation(adapted.acquisition.snapshots(0).operations.head),
          Contract.Workflow(adapted.acquisition.snapshots(1).workflows.head), Contract.StateMachine(adapted.acquisition.snapshots(2).stateMachines.head))
        bound.realizations.map(_.realization.target.source.sourceRevision) shouldBe Vector("target-operation-r101", "target-workflow-r102", "target-machine-r103")
        bound.realizations.map(_.realization.edge.source.sourceRevision) shouldBe Vector("operation-edge-r14", "workflow-edge-r15", "machine-edge-r17")
      }

      "CPT25 preserve strict schema, ABI, malformed raw and acquisition-scope failures" in {
        Given("valid actual targets and projections with unsupported headers or malformed input")
        val adapted = _batch
        val valid = _projection(adapted.bindings)
        val variants = Vector(valid.copy(schemaVersion = 2), valid.copy(generatedAbiVersion = "operation-abi"),
          valid.copy(capabilities = null), null)
        val json = _right(ComponentCapabilityProjectionCodec.encode(valid))
        When("raw and strict wire admission encounter these original R11/R12 failures")
        val raw = variants.map(value => _left(admit(value, adapted)))
        val direct = variants.map(value => _left(ComponentCapabilityAdmission.admit(value, ScopeInput.Ready(adapted.scope))))
        val wires = Vector(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), json.replace("cncf.component-capability.v1", "unsupported-abi"), "{", "null")
        val wireerrors = wires.map(text => _left(admitJson(SourceInput.Acquired(adapted.scope.scopeId, text), adapted)))
        val scoped = _left(admitJson(SourceInput.Acquired("scope:unknown", json), adapted))
        Then("the original diagnostics survive and no partial Bound is returned")
        raw shouldBe direct
        raw.map(_.kind) shouldBe Vector(DiagnosticKind.Unsupported, DiagnosticKind.Unsupported, DiagnosticKind.Malformed, DiagnosticKind.Malformed)
        raw.map(_.location) shouldBe Vector("/schemaVersion", "/generatedAbiVersion", "/capabilities", "/")
        wireerrors.map(_.kind) shouldBe Vector(DiagnosticKind.Unsupported, DiagnosticKind.Unsupported, DiagnosticKind.Malformed, DiagnosticKind.Malformed)
        scoped.kind shouldBe DiagnosticKind.Incompatible
        scoped.location shouldBe "/scopeId"
        scoped.scopeId shouldBe Some("scope:unknown")
      }

      "CPT26 require exact actual targets for both Local and External realizations" in {
        Given("one acquired actual Operation target and otherwise identical Local/External edges")
        val adapted = _batch
        val base = _projection(Vector(adapted.bindings.head))
        val declaration = base.declarations.head
        val edge = _present(declaration.realizations).head
        val variants = ReferenceBoundary.values.toVector.map(boundary => base.copy(declarations = Vector(declaration.copy(realizations = Present(Vector(edge.copy(target = edge.target.copy(boundary = boundary))))))))
        When("each declared edge is bound with actual targets, no targets or an unknown explicit target")
        val known = variants.map(value => _right(admit(value, adapted)))
        val empty = _adapt(Vector.empty, Vector.empty, _scope)
        val missing = variants.map(value => _left(admit(value, empty)))
        val unknown = variants.map { value =>
          val original = value.declarations.head
          val selected = _present(original.realizations).head
          val changed = selected.copy(target = selected.target.copy(identity = ModelElementId("model:targets", "unknown")))
          _left(admit(value.copy(declarations = Vector(original.copy(realizations = Present(Vector(changed))))), adapted))
        }
        Then("both boundaries bind the actual target and both refuse missing or unknown target facts")
        known.map(_.realizations.head.target) shouldBe Vector.fill(2)(adapted.targets.head)
        known.map(_.realizations.head.realization.edge.target.boundary) shouldBe ReferenceBoundary.values.toVector
        (missing ++ unknown).foreach { error =>
          error.kind shouldBe DiagnosticKind.Malformed
          error.location shouldBe "/declarations/0/realizations/value/0/target"
        }
      }

      "CPT27 retain present-empty and exact absence with zero bound realizations" in {
        Given("an explicitly acquired empty catalog and explicit absent or present-empty architectural realization facts")
        val adapted = _adapt(Vector.empty, Vector.empty, _scope)
        val base = _projection(Vector.empty)
        val states: Vector[Presence[Vector[Realization]]] = Vector(Present(Vector.empty)) ++ AbsenceReason.values.toVector.map(reason => Absent(reason, s"Exact $reason realization detail."))
        val variants = states.map(state => base.copy(declarations = Vector(base.declarations.head.copy(realizations = state))))
        When("each architectural realization state is admitted and bound")
        val bound = variants.map(value => _right(admit(value, adapted)))
        Then("each original reason/detail or present-empty fact survives without a fabricated target")
        bound.map(_.admitted.projection.declarations.head.realizations) shouldBe states
        bound.foreach { result =>
          result.realizations shouldBe Vector.empty
          result.admitted.realizations shouldBe Vector.empty
          result.adapted.targets shouldBe Vector.empty
        }
      }
    }

    "preserve traceability and reject incorrect contracts" which {
      "CPT28 retain independent proof roles, sources and origins under original Local/External rules" in {
        Given("the conformance projection with independent Specification/Evidence links and an explicitly derived External Evidence link")
        val adapted = _fixture_adapted
        val original = _right(ComponentCapabilityProjectionCodec.decode(_read_fixture()))
        val proof = _present(original.capabilities.head.traceability)
        val links = proof.updated(1, proof(1).copy(target = proof(1).target.copy(boundary = ReferenceBoundary.External),
          origin = Derived("rule:observed-proof", Vector(Source("model:observer", "models/observer.cml", "observation-r9", Some(4))))))
        val projection = original.copy(capabilities = original.capabilities.updated(0, original.capabilities.head.copy(traceability = Present(links))))
        val (selected, snapshots, bindings) = _fixture_inputs
        val missing = _adapt(snapshots, bindings, selected.copy(modelFacts = selected.modelFacts.filterNot(_.kind == ModelKind.Specification)))
        val wrong = _adapt(snapshots, bindings, selected.copy(modelFacts = selected.modelFacts.map(fact => if (fact.kind == ModelKind.Specification) fact.copy(kind = ModelKind.Evidence) else fact)))
        val traceonly = original.copy(capabilities = original.capabilities.updated(0, original.capabilities.head.copy(discoveryReferences = Present(Vector.empty))))
        val missingevidence = _adapt(snapshots, bindings, selected.copy(modelFacts = selected.modelFacts.filterNot(_.kind == ModelKind.Evidence)))
        val wrongevidence = _adapt(snapshots, bindings, selected.copy(modelFacts = selected.modelFacts.map(fact => if (fact.kind == ModelKind.Evidence) fact.copy(kind = ModelKind.Specification) else fact)))
        When("the original proof relations are admitted with known, missing and wrong-kind Local facts")
        val accepted = _right(admit(projection, adapted))
        val errors = Vector(_left(admit(original, missing)), _left(admit(original, wrong)))
        val traceerrors = Vector(_left(admit(traceonly, missing)), _left(admit(traceonly, wrong)))
        val evidenceerrors = Vector(_left(admit(traceonly, missingevidence)), _left(admit(traceonly, wrongevidence)))
        Then("independent links stay intact and Local missing/wrong-kind proof targets still fail in R12")
        accepted.admitted.projection.capabilities.head.traceability shouldBe Present(links)
        accepted.admitted.projection.declarations.map(_.traceability) shouldBe projection.declarations.map(_.traceability)
        links.map(_.role) shouldBe Vector(TraceabilityRole.Specification, TraceabilityRole.Evidence)
        links.map(_.source.sourceRevision) shouldBe Vector("trace-spec-r6", "trace-evidence-r8")
        links.last.origin shouldBe Derived("rule:observed-proof", Vector(Source("model:observer", "models/observer.cml", "observation-r9", Some(4))))
        errors.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Incompatible)
        errors.foreach(_.location shouldBe "/capabilities/0/discoveryReferences/value/0/target/identity")
        traceerrors.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Incompatible)
        traceerrors.foreach(_.location shouldBe "/capabilities/0/traceability/value/0/target/identity")
        evidenceerrors.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Incompatible)
        evidenceerrors.foreach(_.location shouldBe "/capabilities/0/traceability/value/1/target/identity")
      }

      "CPT29 reject wrong Component, model identity, kind, contract and ABI for every family" in {
        Given("actual targets for all three families and independently varied wrong edge domains")
        val adapted = _batch
        val cases = adapted.bindings.flatMap { context =>
          val base = _projection(Vector(context))
          val declaration = base.declarations.head
          val edge = _present(declaration.realizations).head
          val otherkind = TargetKind.values.find(_ != context.targetKind).get
          Vector(
            (edge.copy(targetComponent = ComponentId("org.unknown.Target")), DiagnosticKind.Malformed, "target"),
            (edge.copy(target = edge.target.copy(identity = ModelElementId(context.identity.modelId, "unknown-model-id"))), DiagnosticKind.Malformed, "target"),
            (edge.copy(targetKind = otherkind), DiagnosticKind.Incompatible, "targetKind"),
            (edge.copy(targetContractVersion = "wrong-contract"), DiagnosticKind.Incompatible, "targetContractVersion"),
            (edge.copy(generatedAbiVersion = "wrong-abi"), DiagnosticKind.Incompatible, "generatedAbiVersion"))
            .map { (changed, kind, field) => (base.copy(declarations = Vector(declaration.copy(realizations = Present(Vector(changed))))), kind, field) }
        }
        When("R12 admits each edge against the actual adapted target scope")
        val errors = cases.map { (projection, kind, field) => (_left(admit(projection, adapted)), kind, field, _left(ComponentCapabilityAdmission.admit(projection, ScopeInput.Ready(adapted.scope)))) }
        Then("every family rejects the wrong domain at its original R12 pointer without nearby selection")
        errors.foreach { (error, kind, field, direct) =>
          error shouldBe direct
          error.kind shouldBe kind
          error.location shouldBe s"/declarations/0/realizations/value/0/$field"
          error.component shouldBe Some(_empty_projection.component.componentId)
          error.source shouldBe Some(Source("model:test", "models/edges.cml", "edge-r10", Some(20)))
        }
      }

      "CPT30 refuse duplicate, malformed and unresolved semantic input without partial Bound" in {
        Given("valid actual targets and raw projections with independently invalid definitions, declarations or Local grounding")
        val adapted = _batch
        val base = _projection(adapted.bindings)
        val definition = base.capabilities.head
        val grounding = Grounding(ModelTarget(ModelElementId("model:missing", "concept")), ReferenceBoundary.Local,
          Absent(AbsenceReason.NotDeclared, "No relation declared."), Absent(AbsenceReason.NotDeclared, "No profile declared."),
          Absent(AbsenceReason.NotDeclared, "No context declared."), Absent(AbsenceReason.NotDeclared, "No label declared."), definition.source, Declared)
        val cases = Vector(
          (base.copy(capabilities = Vector(definition, definition)), DiagnosticKind.Ambiguous, "/capabilities/1"),
          (base.copy(capabilities = Vector(definition.copy(name = " name"))), DiagnosticKind.Malformed, "/capabilities/0/name"),
          (base.copy(declarations = base.declarations ++ base.declarations), DiagnosticKind.Ambiguous, "/declarations/1"),
          (base.copy(declarations = Vector(base.declarations.head.copy(direction = null))), DiagnosticKind.Malformed, "/declarations/0/direction"),
          (base.copy(capabilities = Vector(definition.copy(grounding = Present(Vector(grounding))))), DiagnosticKind.Malformed, "/capabilities/0/grounding/value/0/target/identity"))
        When("the original raw admission validates each projection before binding")
        val errors = cases.map { (projection, kind, path) => (_left(admit(projection, adapted)), kind, path) }
        Then("the exact original failures survive and acquired context never repairs the projection")
        errors.foreach { (error, kind, path) =>
          error.kind shouldBe kind
          error.location shouldBe path
        }
        adapted.targets.size shouldBe 3
      }

      "CPT31 preserve JSON acquisition rejection even with null adapted input" in {
        Given("an observed rejected JSON acquisition with exact scope/source and a valid acquired JSON input")
        val diagnostic = Diagnostic(DiagnosticKind.Stale, "Observed source is stale.", Some("scope:observed"), None, Some(_scope.source), "/observed/source")
        val acquired = SourceInput.Acquired(_scope.scopeId, "{}")
        When("rejected and acquired inputs reach a null adapted context")
        val rejected = admitJson(SourceInput.Rejected(diagnostic), null)
        val malformed = _left(admitJson(acquired, null))
        val raw = _left(admit(_empty_projection, null))
        Then("rejection wins unchanged while otherwise missing adapted context is Malformed")
        _left(rejected) should be theSameInstanceAs diagnostic
        malformed.kind shouldBe DiagnosticKind.Malformed
        malformed.location shouldBe "/adapted"
        raw.kind shouldBe DiagnosticKind.Malformed
        raw.location shouldBe "/adapted"
      }

      "CPT32 retain native procedure variations without changing the architectural mapping" in {
        Given("two actual catalogs varying Operation execution/job fields, Workflow status rules and StateMachine action bindings")
        val original = _snapshot("org.example.Payments", "target-r90")
        val changedoperation = original.operations.head.copy(execution = Some("async"), commandKind = Some("declared-command"), jobDefinitionRef = Some("job:declared"), implementation = Some("procedure:declared"))
        val changedworkflow = original.workflows.head.copy(registrations = original.workflows.head.registrations.map(registration =>
          registration.copy(statusRules = Vector(WorkflowStatusRule("Open", "Compensate", Some("Declared retry metadata"))))))
        val changed = original.copy(operations = Vector(changedoperation), workflows = Vector(changedworkflow), stateMachines = Vector(_machine("Payment", "CompensateAction")))
        val contexts = TargetKind.values.toVector.map(kind => _binding(original, kind))
        val first = _adapt(Vector(original), contexts, _scope)
        val second = _adapt(Vector(changed), contexts, _scope)
        val projection = _projection(contexts)
        When("both acquired actual catalogs are bound through original admission")
        val before = _right(admit(projection, first))
        val after = _right(admit(projection, second))
        Then("native procedure declarations remain original objects while flat mapping and Capability metadata stay equal")
        before.admitted.projection should be theSameInstanceAs projection
        after.admitted.projection should be theSameInstanceAs projection
        first.scope.targetContracts shouldBe second.scope.targetContracts
        before.realizations.map(_.realization.target) shouldBe after.realizations.map(_.realization.target)
        after.realizations.map(_.target.contract) shouldBe Vector(Contract.Operation(changedoperation), Contract.Workflow(changedworkflow), Contract.StateMachine(changed.stateMachines.head))
        after.realizations.head.target.contract match {
          case Contract.Operation(definition) => definition should be theSameInstanceAs changedoperation
          case _ => fail("Expected actual Operation metadata.")
        }
        changedworkflow.registrations.head.statusRules.head.nextAction shouldBe "Compensate"
        changed.stateMachines.head.normalized.get.transitions.head.actions.head.bindingName shouldBe "CompensateAction"
      }
    }
  }
}
