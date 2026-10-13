package org.goldenport.cncf.component.capability

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.statemachine.*
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityTargetSource.*
import ComponentCapabilityTargetAdapterSpec.*

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityTargetAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _right[A](result: Either[Diagnostic, A]): A = {
    result.fold(error => fail(s"Unexpected ${error.kind} at ${error.location}: ${error.detail}"), value => value)
  }

  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = {
    result.fold(error => error, _ => fail("Expected a typed target adaptation failure."))
  }

  private def _adapt(snapshots: Vector[Snapshot], bindings: Vector[Binding]): Either[Diagnostic, ComponentCapabilityTargetAdapter.Adapted] = {
    ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, snapshots), ScopeInput.Ready(_scope), bindings)
  }

  "Actual typed target adaptation" should {
    "retain supplied identities and actual metadata" which {
      "CPT01 select all three families across two Components in binding order" in {
        Given("two acquired snapshots with independently declared Operation, Workflow and normalized StateMachine metadata")
        val first = _snapshot("org.example.Payments", "source-A")
        val second = _snapshot("org.example.Orchestrator", "source-B")
        val bindings = Vector(_binding(second, TargetKind.Workflow), _binding(first, TargetKind.StateMachine), _binding(first, TargetKind.Operation))
        val acquisition = AcquisitionInput.Acquired(_scope.scopeId, Vector(first, second))
        val ready = ScopeInput.Ready(_scope)
        When("the batch is adapted using its explicit bindings")
        val adapted = _right(ComponentCapabilityTargetAdapter.adapt(acquisition, ready, bindings))
        Then("all three exact original native objects, contexts, Components and author order survive")
        adapted.acquisition should be theSameInstanceAs acquisition
        adapted.scopeInput should be theSameInstanceAs ready
        adapted.bindings should be theSameInstanceAs bindings
        adapted.targets.map(_.binding) shouldBe bindings
        adapted.targets.map(_.target.component) shouldBe Vector(second.component, first.component, first.component)
        adapted.targets.map(_.contract) shouldBe Vector(Contract.Workflow(second.workflows.head), Contract.StateMachine(first.stateMachines.head), Contract.Operation(first.operations.head))
        adapted.targets.head.snapshot should be theSameInstanceAs second
        adapted.targets.head.binding should be theSameInstanceAs bindings.head
        adapted.targets.head.contract match {
          case Contract.Workflow(definition) => definition should be theSameInstanceAs second.workflows.head
          case _ => fail("Expected actual Workflow metadata.")
        }
        adapted.scope shouldBe ready.scope.copy(targetContracts = adapted.targets.map(_.target))
        adapted.scope.modelFacts should be theSameInstanceAs ready.scope.modelFacts
        adapted.scope.capabilities should be theSameInstanceAs ready.scope.capabilities
        first.operations shouldBe Vector(_operation("Charge"))
      }

      "CPT02 keep supplied model identity separate from same-label native selectors" in {
        Given("two Components whose Operation declarations have the same literal name")
        val first = _snapshot("org.example.Payments", "revision-A")
        val second = _snapshot("org.other.Payments", "revision-B")
        val bindings = Vector(_binding(first, TargetKind.Operation).copy(identity = ModelElementId("model:targets", "É::Model/1")),
          _binding(second, TargetKind.Operation).copy(identity = ModelElementId("model:targets", "E\u0301::Model/1")))
        When("each binding selects within its own acquired snapshot")
        val adapted = _right(_adapt(Vector(first, second), bindings))
        Then("the distinct qualified model identities and exact Component contexts remain distinct")
        adapted.targets.map(_.target.identity) shouldBe bindings.map(_.identity)
        adapted.targets.map(_.binding.selector) shouldBe Vector("Charge", "Charge")
        adapted.targets.map(_.target.component) shouldBe Vector(first.component, second.component)
        adapted.targets.map(_.target.identity).distinct.size shouldBe 2
      }

      "CPT03 retain independently observed snapshot, binding and edge sources" in {
        Given("a snapshot and binding with distinct positive source lines and an independently authored realization edge")
        val selected = _snapshot("org.example.Payments", "target-r90")
        val context = _binding(selected, TargetKind.Operation).copy(source = selected.source.copy(line = Some(19)))
        val declaration = ComponentCapabilityProjectionCodecSpec._empty_projection.component.componentId
        val key = DeclarationKey(declaration, Direction.Provided, CapabilityReference(ModelElementId("model:test", "cap"), "v1"))
        val edgesource = Source("model:test", "models/edge.cml", "edge-r5", Some(7))
        val edge = Realization(key, context.targetKind, ModelReference(context.identity, ReferenceBoundary.External),
          selected.component, context.contractVersion, context.generatedAbiVersion, edgesource, Declared)
        When("the actual target is adapted independently of the edge")
        val target = _right(_adapt(Vector(selected), Vector(context))).targets.head
        Then("target context retains the binding source while snapshot and edge context remain independent")
        target.snapshot.source shouldBe selected.source
        target.snapshot.source.line shouldBe Some(3)
        target.target.source shouldBe context.source
        target.target.source.line shouldBe Some(19)
        edge.source shouldBe edgesource
        target.target.source.sourceRevision shouldBe "target-r90"
        edge.source.sourceRevision shouldBe "edge-r5"
      }

      "CPT04 retain independent opaque contract and ABI values" in {
        Given("a version-7 normalized machine and a Workflow with its own ID, with explicit opaque target versions")
        val selected = _snapshot("org.example.Payments", "source-revision-42")
        val bindings = Vector(_binding(selected, TargetKind.Workflow), _binding(selected, TargetKind.StateMachine))
          .map(_.copy(contractVersion = "Contract:É/opaque", generatedAbiVersion = "ABI|not-SemVer"))
        When("these contexts are adapted")
        val adapted = _right(_adapt(Vector(selected), bindings))
        Then("target contract and ABI remain literal without native-version, ID or source substitution")
        adapted.targets.map(_.target.contractVersion) shouldBe Vector.fill(2)("Contract:É/opaque")
        adapted.targets.map(_.target.generatedAbiVersion) shouldBe Vector.fill(2)("ABI|not-SemVer")
        adapted.targets.map(_.target.source.sourceRevision) shouldBe Vector.fill(2)("source-revision-42")
        adapted.targets(1).contract shouldBe Contract.StateMachine(selected.stateMachines.head)
        selected.stateMachines.head.normalized.get.version.value shouldBe 7
      }
    }

    "honor explicit outcomes and fail closed" which {
      "CPT05 preserve the original rejected scope before malformed bindings" in {
        Given("a rejected selected scope and a successful acquisition with malformed later binding input")
        val diagnostic = Diagnostic(DiagnosticKind.Unauthorized, "Scope access denied.", Some("scope:denied"), None, Some(_scope.source), "/observed/scope")
        When("adaptation receives that original scope rejection")
        val result = ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, null), ScopeInput.Rejected(diagnostic), null)
        Then("the original diagnostic is returned unchanged")
        _left(result) should be theSameInstanceAs diagnostic
      }

      "CPT06 preserve acquisition rejection before malformed scope and bindings" in {
        Given("observed Unsupported, Unavailable, Unauthorized and Stale acquisition outcomes")
        val diagnostics = Vector(DiagnosticKind.Unsupported, DiagnosticKind.Unavailable, DiagnosticKind.Unauthorized, DiagnosticKind.Stale)
          .map(kind => Diagnostic(kind, "Observed acquisition failure.", Some("scope:observed"), Some(ComponentId("org.example.Payments")), Some(_scope.source), "/observed/acquisition"))
        When("each rejected acquisition precedes a rejected or null scope and null bindings")
        val results = diagnostics.map(diagnostic => ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Rejected(diagnostic), null, null))
        val prior = ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Rejected(diagnostics.head), ScopeInput.Rejected(diagnostics.last), null)
        Then("each exact original outcome wins and no empty success is constructed")
        results.zip(diagnostics).foreach { (result, diagnostic) => _left(result) should be theSameInstanceAs diagnostic }
        _left(prior) should be theSameInstanceAs diagnostics.head
      }

      "CPT07 require valid exactly equal acquisition and selected scope identities" in {
        Given("a ready scope and acquisition outcomes with mismatched or invalid identities")
        val acquired = Vector(AcquisitionInput.Acquired("scope:other", Vector.empty), AcquisitionInput.Acquired(null, Vector.empty), AcquisitionInput.Acquired(" scope:test", Vector.empty))
        val invalidscope = _scope.copy(scopeId = null)
        When("the scope identities are checked before adaptation")
        val diagnostics = acquired.map(input => _left(ComponentCapabilityTargetAdapter.adapt(input, ScopeInput.Ready(_scope), Vector.empty)))
        val readyerror = _left(ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Ready(invalidscope), Vector.empty))
        Then("mismatch and invalid values retain their owning pointers and only known context")
        diagnostics.map(_.kind) shouldBe Vector(DiagnosticKind.Incompatible, DiagnosticKind.Malformed, DiagnosticKind.Malformed)
        diagnostics.map(_.location) shouldBe Vector.fill(3)("/acquisition/scopeId")
        diagnostics.head.scopeId shouldBe Some("scope:other")
        readyerror.kind shouldBe DiagnosticKind.Malformed
        readyerror.location shouldBe "/scope/scopeId"
        readyerror.source shouldBe Some(_scope.source)
      }

      "CPT08 reject malformed carriers and literal values without partial targets" in {
        Given("invalid acquisition, snapshot, key, binding, source and vector carriers")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val context = _binding(selected, TargetKind.Operation)
        val snapshots = Vector(
          (selected.copy(component = null), "/component"), (selected.copy(source = null), "/source"),
          (selected.copy(source = selected.source.copy(modelAuthority = " model")), "/source/modelAuthority"),
          (selected.copy(source = selected.source.copy(path = "../model.cml")), "/source/path"),
          (selected.copy(source = selected.source.copy(sourceRevision = null)), "/source/sourceRevision"),
          (selected.copy(source = selected.source.copy(line = Some(0))), "/source/line"),
          (selected.copy(source = selected.source.copy(line = null)), "/source/line"),
          (selected.copy(operations = null), "/operations"), (selected.copy(operations = Vector(null)), "/operations/0"),
          (selected.copy(operations = Vector(_operation(" Charge"))), "/operations/0/name"),
          (selected.copy(workflows = null), "/workflows"), (selected.copy(workflows = Vector(null)), "/workflows/0"),
          (selected.copy(workflows = Vector(_workflow("Checkout\n"))), "/workflows/0/name"),
          (selected.copy(stateMachines = null), "/stateMachines"), (selected.copy(stateMachines = Vector(null)), "/stateMachines/0"),
          (selected.copy(stateMachines = Vector(CmlStateMachineDefinition(" Machine"))), "/stateMachines/0/name"))
        val bindings = Vector(
          (context.copy(snapshot = null), "/snapshot"), (context.copy(snapshot = context.snapshot.copy(component = null)), "/snapshot/component"),
          (context.copy(snapshot = context.snapshot.copy(modelAuthority = "")), "/snapshot/modelAuthority"),
          (context.copy(snapshot = context.snapshot.copy(path = "a//b")), "/snapshot/path"),
          (context.copy(snapshot = context.snapshot.copy(sourceRevision = "\u0000")), "/snapshot/sourceRevision"),
          (context.copy(identity = null), "/identity"), (context.copy(identity = context.identity.copy(elementId = " x")), "/identity/elementId"),
          (context.copy(targetKind = null), "/targetKind"), (context.copy(selector = null), "/selector"),
          (context.copy(contractVersion = ""), "/contractVersion"), (context.copy(generatedAbiVersion = "ABI\u00a0"), "/generatedAbiVersion"),
          (context.copy(source = null), "/source"), (context.copy(source = context.source.copy(line = Some(-1))), "/source/line"))
        When("all malformed inputs are passed through the adapter")
        val snapshoterrors = snapshots.map { (value, suffix) => _left(_adapt(Vector(value), Vector.empty)) -> s"/acquisition/snapshots/0$suffix" }
        val bindingerrors = bindings.map { (value, suffix) => _left(_adapt(Vector(selected), Vector(value))) -> s"/bindings/0$suffix" }
        val branches = Vector(
          ComponentCapabilityTargetAdapter.adapt(null, ScopeInput.Ready(_scope), Vector.empty) -> "/acquisition",
          ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, Vector.empty), null, Vector.empty) -> "/scope",
          ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Ready(null), Vector.empty) -> "/scope",
          ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Rejected(null), null, null) -> "/acquisition/diagnostic",
          ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Rejected(null), null) -> "/scope/diagnostic",
          _adapt(null, Vector.empty) -> "/acquisition/snapshots", _adapt(Vector(null), Vector.empty) -> "/acquisition/snapshots/0",
          _adapt(Vector(selected), null) -> "/bindings", _adapt(Vector(selected), Vector(null)) -> "/bindings/0")
        val literal = selected.copy(operations = Vector(_operation("É::E\u0301/支払")))
        val accepted = _adapt(Vector(literal), Vector(context.copy(selector = literal.operations.head.name)))
        Then("each malformed result names its actual owning pointer, while valid Unicode remains literal")
        (snapshoterrors ++ bindingerrors ++ branches.map { (result, path) => _left(result) -> path }).foreach { (diagnostic, path) =>
          diagnostic.kind shouldBe DiagnosticKind.Malformed
          diagnostic.location shouldBe path
        }
        _right(accepted).targets.head.contract shouldBe Contract.Operation(literal.operations.head)
      }

      "CPT09 reject repeated snapshot keys including equal data and independent lines" in {
        Given("duplicate snapshots whose exact key excludes their source lines")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val duplicates = Vector(selected, selected.copy(source = selected.source.copy(line = None)))
        When("each second exact snapshot is validated")
        val results = duplicates.map(other => _left(_adapt(Vector(selected, other), Vector.empty)))
        Then("the second snapshot is Ambiguous and retains its own source context")
        results.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Ambiguous)
        results.map(_.location) shouldBe Vector.fill(2)("/acquisition/snapshots/1")
        results.map(_.source) shouldBe duplicates.map(value => Some(value.source))
        results.map(_.component) shouldBe Vector.fill(2)(Some(selected.component))
      }

      "CPT10 distinguish unavailable snapshot and unavailable exact native selector" in {
        Given("bindings to a missing snapshot revision and a missing literal native name")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val context = _binding(selected, TargetKind.Operation)
        val variants = Vector(context.copy(snapshot = context.snapshot.copy(sourceRevision = "unknown")), context.copy(selector = "charge"))
        When("the exact snapshot and native selector are selected")
        val results = variants.map(value => _left(_adapt(Vector(selected), Vector(value))))
        Then("both are Unavailable at their distinct owning pointers without case fallback")
        results.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Unavailable)
        results.map(_.location) shouldBe Vector("/bindings/0/snapshot", "/bindings/0/selector")
        results.map(_.source) shouldBe variants.map(value => Some(value.source))
        results.map(_.component) shouldBe Vector.fill(2)(Some(selected.component))
      }

      "CPT11 reject a native selector present only in a different target family" in {
        Given("an Operation-only literal selector requested as Workflow or StateMachine")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val variants = Vector(TargetKind.Workflow, TargetKind.StateMachine).map(kind => _binding(selected, kind).copy(selector = "Charge"))
        When("the requested native family is selected")
        val errors = variants.map(value => _left(_adapt(Vector(selected), Vector(value))))
        Then("wrong-family selection is Incompatible without coercion")
        errors.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Incompatible)
        errors.map(_.location) shouldBe Vector.fill(2)("/bindings/0/targetKind")
      }

      "CPT12 reject repeated same-kind native declarations without deduplication" in {
        Given("equal native declarations in all three families and unequal Operation records with one exact selector")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val variants = Vector(selected.operations.head, selected.operations.head.copy(summary = Some("Independent metadata")))
          .flatMap(other => Vector(Vector(selected.operations.head, other), Vector(other, selected.operations.head)))
        val families = Vector(
          selected.copy(operations = selected.operations ++ selected.operations) -> TargetKind.Operation,
          selected.copy(workflows = selected.workflows ++ selected.workflows) -> TargetKind.Workflow,
          selected.copy(stateMachines = selected.stateMachines ++ selected.stateMachines) -> TargetKind.StateMachine)
        When("each acquired catalog attempts the same native selection")
        val errors = variants.map(values => _left(_adapt(Vector(selected.copy(operations = values)), Vector(_binding(selected, TargetKind.Operation)))))
        val familyerrors = families.map { (value, kind) => _left(_adapt(Vector(value), Vector(_binding(selected, kind)))) }
        Then("all duplicate candidate orders remain Ambiguous")
        errors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Ambiguous)
        errors.map(_.location).distinct shouldBe Vector("/bindings/0/selector")
        familyerrors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Ambiguous)
        familyerrors.map(_.location).distinct shouldBe Vector("/bindings/0/selector")
      }

      "CPT13 reject repeated complete target context across distinct snapshots" in {
        Given("two native Operations on different acquired revisions bound to one exact model/version/ABI context")
        val first = _snapshot("org.example.Payments", "target-r1")
        val second = _snapshot("org.example.Payments", "target-r2")
        val one = _binding(first, TargetKind.Operation)
        val two = _binding(second, TargetKind.Operation)
        When("the same and distinct snapshots supply repeated complete target bindings")
        val same = _left(_adapt(Vector(first), Vector(one, one)))
        val distinct = _left(_adapt(Vector(first, second), Vector(one, two)))
        Then("the second complete context is Ambiguous without source-based winner selection")
        Vector(same, distinct).map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Ambiguous)
        Vector(same, distinct).map(_.location) shouldBe Vector.fill(2)("/bindings/1")
        distinct.source shouldBe Some(two.source)
      }

      "CPT14 reject native aliases and preserve independent historical contexts" in {
        Given("a binding aliased by model ID or contract version and a distinct historical snapshot")
        val first = _snapshot("org.example.Payments", "target-r1")
        val second = _snapshot("org.example.Payments", "target-r2")
        val one = _binding(first, TargetKind.Operation)
        val aliases = Vector(one.copy(identity = one.identity.copy(elementId = "different-model-id")), one.copy(contractVersion = "historical-contract"))
        val historical = _binding(second, TargetKind.Operation).copy(contractVersion = "historical-contract")
        When("native aliases and independent historical bindings are adapted")
        val errors = aliases.map(alias => _left(_adapt(Vector(first), Vector(one, alias))))
        val accepted = _right(_adapt(Vector(first, second), Vector(one, historical)))
        Then("aliases fail at the second binding while explicitly distinct snapshots retain their revisions")
        errors.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Ambiguous)
        errors.map(_.location) shouldBe Vector.fill(2)("/bindings/1")
        accepted.targets.map(_.target.source.sourceRevision) shouldBe Vector("target-r1", "target-r2")
        accepted.targets.map(_.target.contractVersion) shouldBe Vector(one.contractVersion, historical.contractVersion)
      }

      "CPT15 retain a legitimate explicitly acquired and ready empty batch" in {
        Given("an explicit empty acquisition, valid ready scope and empty binding vector")
        val acquisition = AcquisitionInput.Acquired(_scope.scopeId, Vector.empty)
        val ready = ScopeInput.Ready(_scope)
        When("the acquired empty batch is adapted")
        val adapted = _right(ComponentCapabilityTargetAdapter.adapt(acquisition, ready, Vector.empty))
        Then("its originals remain present and no target is manufactured")
        adapted.acquisition should be theSameInstanceAs acquisition
        adapted.scopeInput should be theSameInstanceAs ready
        adapted.scope shouldBe _scope
        adapted.targets shouldBe Vector.empty
        adapted.bindings shouldBe Vector.empty
      }

      "CPT16 reject synthetic scope targets and validate original ready facts" in {
        Given("an original scope with synthetic target metadata and another with malformed acquired facts")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val synthetic = TargetContract(TargetKind.Operation, ModelElementId("model:targets", "synthetic"), selected.component, "v1", "abi1", selected.source)
        val supplied = _scope.copy(targetContracts = Vector(synthetic))
        val malformed = _scope.copy(modelFacts = Vector(null))
        val acquisition: AcquisitionInput.Acquired = AcquisitionInput.Acquired(_scope.scopeId, Vector(selected))
        When("these original ready scopes are passed to adaptation")
        val rejected = _left(ComponentCapabilityTargetAdapter.adapt(acquisition, ScopeInput.Ready(supplied), Vector(_binding(selected, TargetKind.Operation))))
        val invalid = _left(ComponentCapabilityTargetAdapter.adapt(acquisition, ScopeInput.Ready(malformed), Vector.empty))
        Then("neither synthetic targets nor invalid original facts can be replaced by an accepted scope")
        rejected.kind shouldBe DiagnosticKind.Malformed
        rejected.location shouldBe "/scope/targetContracts"
        supplied.targetContracts shouldBe Vector(synthetic)
        acquisition.snapshots shouldBe Vector(selected)
        invalid.kind shouldBe DiagnosticKind.Malformed
        invalid.location shouldBe "/scope/modelFacts/0"
      }
    }

    "respect native contract owners and literal selection" which {
      "CPT17 refuse a selected legacy StateMachine without normalized metadata" in {
        Given("an actual legacy StateMachine definition with normalized=None")
        val selected = _snapshot("org.example.Payments", "target-r1").copy(stateMachines = Vector(CmlStateMachineDefinition("Payment", states = Vector("Open"))))
        When("the selected legacy definition is adapted")
        val error = _left(_adapt(Vector(selected), Vector(_binding(selected, TargetKind.StateMachine))))
        Then("the actual selected contract path is Unsupported without version invention")
        error.kind shouldBe DiagnosticKind.Unsupported
        error.location shouldBe "/acquisition/snapshots/0/stateMachines/0/normalized"
        error.source shouldBe Some(selected.source)
        selected.stateMachines.head.normalized shouldBe None
      }

      "CPT18 retain the normalized StateMachine graph and source declarations" in {
        Given("a real normalized StateMachine with states, a guarded transition, actions and source locations")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val original = selected.stateMachines.head
        When("the normalized definition is adapted")
        val target = _right(_adapt(Vector(selected), Vector(_binding(selected, TargetKind.StateMachine)))).targets.head
        Then("the complete original graph remains solely on the retained actual contract")
        target.contract match {
          case Contract.StateMachine(definition) =>
            definition should be theSameInstanceAs original
            definition.normalized.get should be theSameInstanceAs original.normalized.get
            definition.normalized.get.states.map(_.identity.path.render) shouldBe Vector("Open")
            definition.normalized.get.transitions.head.sourceLocation.declarationPath shouldBe Vector("transitions", "Finish")
            definition.normalized.get.transitions.head.actions.size shouldBe 1
            definition.normalized.get.version.value shouldBe 7
          case _ => fail("Expected actual StateMachine metadata.")
        }
        target.target.contractVersion shouldBe "contract:STATE_MACHINE"
        target.target.generatedAbiVersion shouldBe "abi:STATE_MACHINE"
      }

      "CPT19 reject conflicting source contexts and malformed selected native shapes" in {
        Given("valid snapshots with source conflicts and constructible invalid selected native fields")
        val selected = _snapshot("org.example.Payments", "target-r1")
        val context = _binding(selected, TargetKind.Operation)
        val conflicts = Vector(context.copy(source = context.source.copy(modelAuthority = "model:other")),
          context.copy(source = context.source.copy(path = "models/other.cml")), context.copy(source = context.source.copy(sourceRevision = "wrong")),
          context.copy(identity = context.identity.copy(modelId = "model:other")))
        val operationvariants = Vector(selected.operations.head.copy(kind = null), selected.operations.head.copy(inputType = ""),
          selected.operations.head.copy(outputType = " Type"), selected.operations.head.copy(inputValueKind = "\u0000"))
        val workflowvariants = Vector(selected.workflows.head.copy(id = null), selected.workflows.head.copy(registrations = null), selected.workflows.head.copy(registrations = Vector(null)))
        val invalidmachine = selected.stateMachines.head.copy(normalized = Some(selected.stateMachines.head.normalized.get.copy(version = null)))
        When("the exact selected contexts and contract fields are validated")
        val sourceerrors = conflicts.map(value => _left(_adapt(Vector(selected), Vector(value))))
        val operationerrors = operationvariants.map(value => _left(_adapt(Vector(selected.copy(operations = Vector(value))), Vector(context))))
        val workflowerrors = workflowvariants.map(value => _left(_adapt(Vector(selected.copy(workflows = Vector(value))), Vector(_binding(selected, TargetKind.Workflow)))))
        val machineerror = _left(_adapt(Vector(selected.copy(stateMachines = Vector(invalidmachine))), Vector(_binding(selected, TargetKind.StateMachine))))
        Then("source conflicts are Incompatible and invalid selected fields are Malformed at actual contract pointers")
        sourceerrors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Incompatible)
        sourceerrors.map(_.location) shouldBe Vector("/bindings/0/source/modelAuthority", "/bindings/0/source/path", "/bindings/0/source/sourceRevision", "/bindings/0/source/modelAuthority")
        operationerrors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
        operationerrors.map(_.location) shouldBe Vector("kind", "inputType", "outputType", "inputValueKind").map(field => s"/acquisition/snapshots/0/operations/0/$field")
        workflowerrors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
        workflowerrors.map(_.location) shouldBe Vector("id", "registrations", "registrations/0").map(field => s"/acquisition/snapshots/0/workflows/0/$field")
        machineerror.kind shouldBe DiagnosticKind.Malformed
        machineerror.location shouldBe "/acquisition/snapshots/0/stateMachines/0/normalized/version"
      }

      "CPT20 select independently of snapshot and unrelated native enumeration order" in {
        Given("three snapshots and unrelated Operation declarations with a fixed supplied binding order")
        val first = _snapshot("org.example.Payments", "r1").copy(operations = Vector(_operation("Unused"), _operation("Charge"), _operation("Other")))
        val second = _snapshot("org.other.Payments", "r2")
        val third = _snapshot("org.third.Payments", "r3")
        val bindings = Vector(_binding(second, TargetKind.Workflow), _binding(first, TargetKind.Operation).copy(selector = "Charge"))
        When("all snapshot and native declaration permutations are adapted")
        val results = Vector(first, second, third).permutations.toVector.flatMap { catalog =>
          first.operations.permutations.toVector.map { definitions =>
            _right(_adapt(catalog.map(value => if (value == first) value.copy(operations = definitions) else value), bindings))
          }
        }
        Then("the exact contracts and supplied binding order are invariant")
        results.foreach { result =>
          result.targets.map(_.binding) shouldBe bindings
          result.targets.map(_.contract) shouldBe Vector(Contract.Workflow(second.workflows.head), Contract.Operation(_operation("Charge")))
          result.targets.map(_.target.source) shouldBe bindings.map(_.source)
        }
      }

      "CPT21 preserve literal identity and selectors in an active property" in {
        Given("valid case, composed/decomposed Unicode and delimiter variations for model identities and native names")
        val values = Gen.oneOf("É::支払/1", "E\u0301::支払/1", "MODEL|Pay#1", "model::Pay:v2")
        val samples = for {
          authority <- values
          identity <- values
          selector <- values
        } yield (authority, identity, selector)
        When("ScalaCheck adapts at least one hundred literal actual target contexts")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(samples) { (sample: (String, String, String)) =>
          val selected = _snapshot("org.example.Payments", "opaque-r1").copy(source = Source(sample._1, "models/targets.cml", "opaque-r1", None), operations = Vector(_operation(sample._3)))
          val context = _binding(selected, TargetKind.Operation).copy(identity = ModelElementId(sample._1, sample._2), selector = sample._3)
          _adapt(Vector(selected), Vector(context)).exists { adapted =>
            val target = adapted.targets.head
            target.target.identity == context.identity && target.binding.selector == sample._3 && target.contract == Contract.Operation(selected.operations.head)
          }
        })
        Then("all successful samples retain the literal domains with no discarded cases")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }

      "CPT22 preserve independent source tuples under catalog permutations in an active property" in {
        Given("three same-label Operation snapshots with distinct Component/source tuples and arbitrary enumeration orders")
        val catalogs = Vector(_snapshot("org.a.Payments", "source-A"), _snapshot("org.b.Payments", "source-B"), _snapshot("org.c.Payments", "source-C"))
        val samples = Gen.zip(Gen.oneOf(catalogs.permutations.toVector), Gen.choose(0, 2))
        When("ScalaCheck selects one exact context for at least one hundred acquired catalog permutations")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(samples) { (sample: (Vector[Snapshot], Int)) =>
          val selected = catalogs(sample._2)
          val context = _binding(selected, TargetKind.Operation)
          _adapt(sample._1, Vector(context)).exists { adapted =>
            val target = adapted.targets.head
            (target.snapshot eq selected) && target.target.component == selected.component && target.target.source == context.source &&
              target.contract == Contract.Operation(selected.operations.head)
          }
        })
        Then("every successful selection retains its own exact source and Component, with zero discards")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }
}

object ComponentCapabilityTargetAdapterSpec {
  private[capability] def _scope: Scope = {
    Scope("scope:actual-targets", Source("scope:authority", "scope/facts.cml", "scope-r100", None),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
  }

  private[capability] def _operation(name: String): CmlOperationDefinition = {
    CmlOperationDefinition(name = name, kind = "command", inputType = "Input", outputType = "Output", inputValueKind = "record")
  }

  private[capability] def _workflow(name: String): WorkflowDefinition = {
    WorkflowDefinition(WorkflowDefinitionId("cncf", "fixed_metadata"), name,
      Vector(WorkflowRegistration("Registration", "ObservedEvent", "Orders", "id", "status", Vector(WorkflowStatusRule("Open", "Finish")))))
  }

  private[capability] def _machine(name: String, actionname: String = "FinishAction"): CmlStateMachineDefinition = {
    val identity = CmlStateMachineIdentity(name)
    val version = CmlStateMachineVersion(7)
    val state = CmlStateMachineStateIdentity(identity, CmlStateMachineStatePath(Vector("Open")))
    val transition = CmlStateMachineTransitionIdentity(identity, 0)
    val triggeridentity = CmlStateMachineTriggerIdentity(identity, "Finish")
    val trigger = CmlStateMachineTrigger(triggeridentity, CmlStateMachineTriggerContext(CmlStateMachineTriggerContextIdentity(triggeridentity), version, Vector.empty))
    val guard = CmlStateMachineGuardProgram.Named(CmlStateMachineGuardIdentity(transition, "Allowed"), "DeclaredGuard")
    val action = CmlStateMachineActionBinding(CmlStateMachineActionIdentity(transition, CmlStateMachineActionPhase.Transition, 0), actionname)
    val edge = CmlStateMachineTransition(transition, state, CmlStateMachineTransitionTarget.Final, trigger, 0, guard,
      Vector(action), Vector.empty, CmlStateMachineSourceLocation(identity, Vector("transitions", "Finish")))
    val normalized = CmlNormalizedStateMachine(identity, version, state,
      Vector(CmlStateMachineStateDefinition(state, CmlStateMachineStateKind.Leaf)), Vector(edge), Vector(transition))
    CmlStateMachineDefinition(name, states = Vector("Open"), events = Vector("Finish"), normalized = Some(normalized))
  }

  private[capability] def _snapshot(componentname: String, sourcerevision: String): Snapshot = {
    Snapshot(ComponentId(componentname), Source("model:targets", "models/targets.cml", sourcerevision, Some(3)),
      Vector(_operation("Charge")), Vector(_workflow("Checkout")), Vector(_machine("Payment")))
  }

  private[capability] def _binding(snapshot: Snapshot, targetkind: TargetKind): Binding = {
    val selector = targetkind match {
      case TargetKind.Operation => snapshot.operations.head.name
      case TargetKind.Workflow => snapshot.workflows.head.name
      case TargetKind.StateMachine => snapshot.stateMachines.head.name
    }
    Binding(snapshot.key, ModelElementId(snapshot.source.modelAuthority, s"explicit:${targetkind.tag}"), targetkind,
      selector, s"contract:${targetkind.tag}", s"abi:${targetkind.tag}", snapshot.source.copy(line = Some(11)))
  }
}
