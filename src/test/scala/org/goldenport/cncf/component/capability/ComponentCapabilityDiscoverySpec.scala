package org.goldenport.cncf.component.capability

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityMetadataProvider.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjectionCodecSpec.{_empty_projection, _definition}
import ComponentCapabilityPackageLoaderSpec.{_with_directory, _package, _write, _json}
import ComponentCapabilityDiscoverySpec.*

/*
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityDiscoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(id: String) = afterWord(s"in spec:R14, example:$id, rules:R14, phase:78")
  private def _right[A](result: Either[Diagnostic, A]): A = result.fold(error => fail(error.toString), value => value)
  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = result.fold(value => value, _ => fail("Expected discovery rejection."))

  "Public static native discovery" should {
    "CPD01 bind three original native families" should _metadata("CPD01") {
      "retain author edge order and independent actual objects" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD01; actual native metadata and reversed/repeated architectural edges")
        _with_directory { root =>
          val nativeinput = _native
          val edges = nativeinput.bindings.reverse :+ nativeinput.bindings.head
          val projection = _projection(edges, "first")
          val selected = _package(root, Vector("first.json" -> projection))
          val provider = new CountingProvider(nativeinput)
          When("the package and public native observation are discovered")
          val result = _right(ComponentCapabilityDiscovery.discover(selected, provider))
          Then("all native families and original snapshot/binding objects survive in edge order")
          result.nativeInput should be theSameInstanceAs nativeinput
          provider.reads shouldBe 1
          val targets = result.entries.head.realizations
          targets.map(_.binding) shouldBe edges
          targets.map(_.realization.edge.source.sourceRevision) shouldBe Vector("edge-first-0", "edge-first-1", "edge-first-2", "edge-first-3")
          targets.zip(edges).foreach { (target, binding) => target.binding should be theSameInstanceAs binding }
          targets.head.contract shouldBe NativeContract.StateMachine(_snapshots(nativeinput).head.stateMachines.head)
          targets(1).contract shouldBe NativeContract.Workflow(_snapshots(nativeinput)(1).workflows.head)
          targets(2).contract shouldBe NativeContract.Operation(_snapshots(nativeinput).head.operations.head)
          targets(2).snapshot should be theSameInstanceAs _snapshots(nativeinput).head
          targets(2).target.source.line shouldBe Some(11)
          targets(2).snapshot.source.line shouldBe Some(3)
        }
      }
    }
    "CPD02 preserve native acquisition rejection" should _metadata("CPD02") {
      "return its original diagnostic before a rejected scope and null bindings" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD02; independent native and scope rejection observations")
        _with_directory { root =>
          val diagnostic = Diagnostic(DiagnosticKind.Stale, "Native source is stale.", Some("scope:observed"), None, Some(_empty_projection.source), "/observed/native")
          val scopeerror = diagnostic.copy(kind = DiagnosticKind.Unauthorized, location = "/observed/scope")
          val nativeinput = NativeInput(NativeAcquisition.Rejected(diagnostic), ScopeInput.Rejected(scopeerror), null)
          val selected = _package(root, Vector.empty)
          When("the acquired package requests this native observation")
          val error = _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          Then("the original native rejection wins unchanged")
          error should be theSameInstanceAs diagnostic
        }
      }
    }
    "CPD03 refuse malformed native carriers" should _metadata("CPD03") {
      "preserve scope rejection and reject null acquisition, snapshots, bindings and targets" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD03; acquired package and explicit rejected or malformed native input")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val base = _native
          val diagnostic = Diagnostic(DiagnosticKind.Unauthorized, "Scope access denied.", None, None, None, "/scope/observed")
          val rejected = base.copy(scopeInput = ScopeInput.Rejected(diagnostic), bindings = null)
          val variants = Vector(base.copy(acquisition = null), base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, null)), base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, Vector(null))), base.copy(bindings = null), base.copy(bindings = Vector(null)), base.copy(bindings = Vector(base.bindings.head.copy(target = null))), base.copy(scopeInput = null))
          When("each pure observation reaches discovery")
          val scopeerror = _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(rejected)))
          val errors = variants.map(value => _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(value))))
          Then("scope rejection stays original and every malformed input is typed without a partial catalog")
          scopeerror should be theSameInstanceAs diagnostic
          errors.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Malformed)
        }
      }
    }
    "CPD04 retain literal native contexts" should _metadata("CPD04") {
      "refuse missing selectors, source conflicts, duplicates, aliases and wrong edge domains" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD04; one explicit context and independently changed native/edge facts")
        _with_directory { root =>
          val base = _native
          val binding = base.bindings.head
          val projection = _projection(Vector(binding), "first")
          val selected = _package(root, Vector("first.json" -> projection))
          val inputs = Vector(base.copy(bindings = Vector(binding.copy(selector = "charge"))), base.copy(bindings = Vector(binding.copy(target = binding.target.copy(targetKind = TargetKind.Workflow)))), base.copy(bindings = Vector(binding.copy(snapshotSource = binding.snapshotSource.copy(sourceRevision = "unknown")))), base.copy(bindings = Vector(binding.copy(target = binding.target.copy(source = binding.target.source.copy(sourceRevision = "conflict"))))), base.copy(bindings = Vector(binding, binding)), base.copy(bindings = Vector(binding, binding.copy(target = binding.target.copy(identity = ModelElementId("model:targets", "alias"))))))
          When("native variants and wrong model/version edges are discovered")
          val errors = inputs.map(value => _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(value))))
          val declaration = projection.declarations.head
          val edge = _edges(projection).head
          val wrongedges = Vector(edge.copy(target = edge.target.copy(identity = ModelElementId("model:targets", "unknown"))), edge.copy(targetContractVersion = "other-contract"))
          val edgeerrors = wrongedges.map { changed =>
            _write(root, metadataDirectory + "/first.json", _json(projection.copy(declarations = Vector(declaration.copy(realizations = Present(Vector(changed)))))))
            _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(base)))
          }
          Then("literal selection and original failures never choose nearby names or inferred versions")
          errors.map(_.kind) shouldBe Vector(DiagnosticKind.Unavailable, DiagnosticKind.Incompatible, DiagnosticKind.Unavailable, DiagnosticKind.Incompatible, DiagnosticKind.Ambiguous, DiagnosticKind.Ambiguous)
          errors.map(_.location) shouldBe Vector("/bindings/0/selector", "/bindings/0/targetKind", "/bindings/0/snapshot", "/bindings/0/source/sourceRevision", "/bindings/1", "/bindings/1")
          edgeerrors.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Incompatible)
          edgeerrors.last.location shouldBe "/declarations/0/realizations/value/0/targetContractVersion"
        }
      }
    }
    "CPD05 preserve actual shape admission" should _metadata("CPD05") {
      "refuse legacy machines and invalid selected Operation fields" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD05; actual native definitions with invalid selected shapes")
        _with_directory { root =>
          val base = _native
          val snapshots = _snapshots(base)
          val first = snapshots.head
          val legacy = base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, snapshots.updated(0, first.copy(stateMachines = Vector(first.stateMachines.head.copy(normalized = None))))))
          val invalid = base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, snapshots.updated(0, first.copy(operations = Vector(first.operations.head.copy(inputType = ""))))))
          val selected = _package(root, Vector("first.json" -> _projection(base.bindings, "first")))
          When("these actual metadata shapes are discovered")
          val errors = Vector(legacy, invalid).map(value => _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(value))))
          Then("legacy remains Unsupported and selected invalid shape stays Malformed at native pointers")
          errors.map(_.kind) shouldBe Vector(DiagnosticKind.Unsupported, DiagnosticKind.Malformed)
          errors.map(_.location) shouldBe Vector("/acquisition/snapshots/0/stateMachines/0/normalized", "/acquisition/snapshots/0/operations/0/inputType")
        }
      }
    }
    "CPD06 require exact selected scope agreement" should _metadata("CPD06") {
      "reject package/native and native/Ready disagreement with source attribution" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD06; independently selected package, native and Ready scope IDs")
        _with_directory { root =>
          val base = _native
          val selected = _package(root, Vector.empty)
          val acquisition = base.copy(acquisition = NativeAcquisition.Acquired("scope:other", _snapshots(base)))
          val ready = base.copy(scopeInput = ScopeInput.Ready(_scope.copy(scopeId = "scope:other")))
          When("discovery compares these exact independent observations")
          val packageerror = _left(ComponentCapabilityDiscovery.discover(selected.copy(scopeId = "scope:package"), new CountingProvider(base)))
          val nativeerrors = Vector(acquisition, ready).map(value => _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(value))))
          Then("all mismatches are Incompatible at the owning scope boundary")
          packageerror.kind shouldBe DiagnosticKind.Incompatible
          packageerror.location shouldBe "/package/scopeId"
          packageerror.source shouldBe Some(_scope.source)
          nativeerrors.map(_.kind) shouldBe Vector.fill(2)(DiagnosticKind.Incompatible)
          nativeerrors.map(_.location) shouldBe Vector.fill(2)("/acquisition/scopeId")
        }
      }
    }
    "CPD07 reject repeated cross-resource keys" should _metadata("CPD07") {
      "refuse duplicate definitions and declarations after complete admission" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD07; independent source snapshots with repeated complete catalog keys")
        val cases = Vector("definition", "declaration")
        cases.foreach { variant => _with_directory { root =>
          val definition = _definition("model:test", "shared", "contract-v1")
          val first = _projection(Vector.empty, "first")
          val second = _projection(Vector.empty, "second")
          val key = CapabilityReference(definition.identity, definition.contractVersion)
          val values = if (variant == "definition") {
            Vector("first.json" -> first.copy(capabilities = Vector(definition), declarations = Vector.empty), "second.json" -> second.copy(capabilities = Vector(definition), declarations = Vector.empty))
          } else {
            Vector("first.json" -> first.copy(capabilities = Vector.empty, declarations = Vector(first.declarations.head.copy(capability = key))), "second.json" -> second.copy(capabilities = Vector.empty, declarations = Vector(second.declarations.head.copy(capability = key))))
          }
          val nativeinput = NativeInput(NativeAcquisition.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Ready(if (variant == "declaration") _scope.copy(capabilities = Vector(definition)) else _scope), Vector.empty)
          val selected = _package(root, values)
          When("both resources are discovered as one catalog")
          val error = _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          Then("the second owning resource is Ambiguous and no partial catalog is returned")
          error.kind shouldBe DiagnosticKind.Ambiguous
          error.location shouldBe (if (variant == "definition") "/meta/component-capability/second.json/capabilities/0" else "/meta/component-capability/second.json/declarations/0")
        } }
      }
    }
    "CPD08 preserve R12 semantic diagnostics" should _metadata("CPD08") {
      "retain original grounding, discovery-role and traceability failures" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD08; structurally valid R11 with dangling semantic Local facts")
        _with_directory { root =>
          val base = _projection(Vector.empty, "first")
          val definition = base.capabilities.head
          val target = ModelReference(ModelElementId("model:missing", "unknown"), ReferenceBoundary.Local)
          val grounding = Grounding(ModelTarget(target.identity), ReferenceBoundary.Local, Absent(AbsenceReason.NotDeclared, "No relation."), Absent(AbsenceReason.NotDeclared, "No profile."), Absent(AbsenceReason.NotDeclared, "No context."), Absent(AbsenceReason.NotDeclared, "No label."), definition.source, Declared)
          val variants = Vector(definition.copy(grounding = Present(Vector(grounding))), definition.copy(discoveryReferences = Present(Vector(ModelLink(ModelRole.Goal, target, definition.source, Declared)))), definition.copy(traceability = Present(Vector(Traceability(TraceabilityRole.Specification, target, definition.source, Declared)))))
          val nativeinput = NativeInput(NativeAcquisition.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Ready(_scope), Vector.empty)
          When("each package is discovered and compared with original pure R12 admission")
          val errors = variants.map { value =>
            val projection = base.copy(capabilities = Vector(value))
            val selected = _package(root, Vector("first.json" -> projection))
            (_left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput))), _left(ComponentCapabilityAdmission.admit(projection, ScopeInput.Ready(_scope))))
          }
          Then("original typed diagnostics and nested owning pointers survive exactly")
          errors.foreach { (error, original) => error shouldBe original; error.kind shouldBe DiagnosticKind.Malformed }
          errors.map(_._1.location) shouldBe Vector("/capabilities/0/grounding/value/0/target/identity", "/capabilities/0/discoveryReferences/value/0/target/identity", "/capabilities/0/traceability/value/0/target/identity")
        }
      }
    }
    "CPD09 preserve realization boundary and absence" should _metadata("CPD09") {
      "bind both Local and External and retain empty or absent edges" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD09; independent realization boundary and Presence states")
        _with_directory { root =>
          val nativeinput = _native
          val base = _projection(Vector(nativeinput.bindings.head), "first")
          val declaration = base.declarations.head
          val edge = _edges(base).head
          val states: Vector[Presence[Vector[Realization]]] = ReferenceBoundary.values.toVector.map(boundary => Present(Vector(edge.copy(target = edge.target.copy(boundary = boundary))))) ++ Vector(Present(Vector.empty)) ++ AbsenceReason.values.toVector.map(reason => Absent(reason, s"Exact $reason detail."))
          When("each actual package is discovered")
          val results = states.map { state =>
            val selected = _package(root, Vector("first.json" -> base.copy(declarations = Vector(declaration.copy(realizations = state)))))
            _right(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          }
          Then("boundaries bind original actual objects and exact absence remains architectural data")
          results.take(2).map(_.entries.head.realizations.head.realization.edge.target.boundary) shouldBe ReferenceBoundary.values.toVector
          results.take(2).foreach(_.entries.head.realizations.head.contract shouldBe NativeContract.Operation(_snapshots(nativeinput).head.operations.head))
          results.drop(2).foreach(_.entries.head.realizations shouldBe Vector.empty)
          results.map(_.entries.head.admitted.projection.declarations.head.realizations) shouldBe states
        }
      }
    }
    "CPD10 retain native procedure metadata" should _metadata("CPD10") {
      "expose original declared procedures without running them" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD10; declared command/job, Workflow status and machine action metadata")
        _with_directory { root =>
          val base = _native
          val snapshots = _snapshots(base)
          val first = snapshots.head
          val operation = first.operations.head.copy(execution = Some("async"), commandKind = Some("declared-command"), jobDefinitionRef = Some("job:declared"), implementation = Some("poison.must.not.Invoke"))
          val machine = ComponentCapabilityTargetAdapterSpec._machine("Payment", "DeclaredAction")
          val workflow = snapshots(1).workflows.head.copy(registrations = snapshots(1).workflows.head.registrations.map(value => value.copy(statusRules = Vector(org.goldenport.cncf.workflow.WorkflowStatusRule("Open", "Compensate", Some("Declared metadata"))))))
          val nativeinput = base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, Vector(first.copy(operations = Vector(operation), stateMachines = Vector(machine)), snapshots(1).copy(workflows = Vector(workflow)))))
          val selected = _package(root, Vector("first.json" -> _projection(base.bindings, "first")))
          When("these actual immutable definitions are discovered")
          val result = _right(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          Then("procedure remains on the original objects while architectural mapping stays separate")
          val targets = result.entries.head.realizations
          targets.map(_.contract) shouldBe Vector(NativeContract.Operation(operation), NativeContract.Workflow(workflow), NativeContract.StateMachine(machine))
          targets.head.contract match {
            case NativeContract.Operation(value) => value should be theSameInstanceAs operation
            case _ => fail("Expected original Operation.")
          }
          operation.jobDefinitionRef shouldBe Some("job:declared")
          workflow.registrations.head.statusRules.head.nextAction shouldBe "Compensate"
          machine.normalized.get.transitions.head.actions.head.bindingName shouldBe "DeclaredAction"
          result.entries.head.admitted.projection.capabilities.head.name shouldBe "Same label"
        }
      }
    }
    "CPD11 distinguish successful empty catalog" should _metadata("CPD11") {
      "require original Ready native acquisition and forbid synthetic target bypass" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD11; acquired empty index and explicit empty or synthetic native Ready inputs")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val nativeinput = NativeInput(NativeAcquisition.Acquired(_scope.scopeId, Vector.empty), ScopeInput.Ready(_scope), Vector.empty)
          val synthetic = nativeinput.copy(scopeInput = ScopeInput.Ready(_scope.copy(targetContracts = Vector(_native.bindings.head.target))))
          When("the empty and synthetic observations reach discovery")
          val empty = _right(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          val error = _left(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(synthetic)))
          Then("only successful actual empty acquisition yields empty and the R13 bypass remains Malformed")
          empty.entries shouldBe Vector.empty
          empty.nativeInput should be theSameInstanceAs nativeinput
          error.kind shouldBe DiagnosticKind.Malformed
          error.location shouldBe "/scope/targetContracts"
        }
      }
    }
    "CPD12 sequence provider acquisition" should _metadata("CPD12") {
      "read exactly once after package success and type throws or null observations" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD12; actual package and counting, throwing or null providers")
        _with_directory { root =>
          val selected = _package(root, Vector.empty)
          val success = new CountingProvider(_native)
          val missing = new CountingProvider(_native)
          val nullinput = new CountingProvider(null)
          var throws = 0
          val throwing = new ComponentCapabilityMetadataProvider {
            def componentCapabilityMetadata: NativeInput = { throws += 1; throw new IllegalStateException("observed provider failure") }
          }
          When("complete, missing, throwing and null observations are discovered")
          val result = _right(ComponentCapabilityDiscovery.discover(selected, success))
          val errors = Vector(_left(ComponentCapabilityDiscovery.discover(selected.copy(location = Location.Directory(root.resolve("absent"))), missing)), _left(ComponentCapabilityDiscovery.discover(selected, throwing)), _left(ComponentCapabilityDiscovery.discover(selected, nullinput)), _left(ComponentCapabilityDiscovery.discover(selected, null)))
          Then("failures never retry and package failure never inspects its provider")
          result.nativeInput should be theSameInstanceAs success.input
          success.reads shouldBe 1
          missing.reads shouldBe 0
          nullinput.reads shouldBe 1
          throws shouldBe 1
          errors.map(_.kind) shouldBe Vector(DiagnosticKind.Unavailable, DiagnosticKind.Unavailable, DiagnosticKind.Malformed, DiagnosticKind.Malformed)
          errors.map(_.location).tail shouldBe Vector("/provider", "/provider/input", "/provider")
        }
      }
    }
    "CPD13 expose lossless accepted readout" should _metadata("CPD13") {
      "retain selected descriptor, resources and independently authored source contexts" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD13; explicit package/native/model/edge provenance")
        _with_directory { root =>
          val nativeinput = _native
          val projection = _projection(nativeinput.bindings, "first")
          val selected = _package(root, Vector("unrelated-label.json" -> projection))
          When("the public catalog is discovered")
          val result = _right(ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)))
          Then("all public readout points retain their exact owning values and original resolved objects")
          result.acquired.input should be theSameInstanceAs selected
          result.acquired.descriptor.componentId shouldBe Some(selected.component.componentId)
          result.acquired.descriptor.version shouldBe Some(selected.component.version)
          val entry = result.entries.head
          entry.loaded should be theSameInstanceAs result.acquired.projections.head
          entry.loaded.resourcePath shouldBe "meta/component-capability/unrelated-label.json"
          entry.admitted.projection should be theSameInstanceAs entry.loaded.projection
          entry.realizations.zip(entry.admitted.realizations).foreach { (target, original) =>
            target.realization should be theSameInstanceAs original
            target.target should be theSameInstanceAs original.target
            target.binding.target shouldBe target.target
          }
          entry.loaded.projection.source.sourceRevision shouldBe "snapshot-first"
          entry.loaded.projection.capabilities.head.source.sourceRevision shouldBe "definition-r1"
          entry.realizations.head.realization.edge.source.sourceRevision shouldBe "edge-first-0"
          entry.realizations.head.snapshot.source.sourceRevision shouldBe "native-A"
        }
      }
    }
    "CPD14 preserve literal contexts under permutation" should _metadata("CPD14") {
      "actively check index, native catalog, binding and edge order with exact opaque contexts" in {
        Given("Spec: docs/spec/component-capability.md; R14; CPD14; literal model/version/ABI values and independent order permutations")
        val base = _native
        val literals = Gen.oneOf("É::literal/1", "E\u0301::literal/1", "MODEL|支払#2", "model:Exact")
        val samples = Gen.zip(literals, Gen.oneOf(Vector(false, true)), Gen.oneOf(base.bindings.permutations.toVector))
        When("ScalaCheck discovers two actual ordered resources for every complete native-context sample")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(samples) { (sample: (String, Boolean, Vector[NativeBinding])) =>
          _with_directory { root =>
            val bindings = sample._3.zipWithIndex.map { (binding, index) => binding.copy(target = binding.target.copy(identity = ModelElementId("model:targets", sample._1 + index), contractVersion = "Contract|" + sample._1, generatedAbiVersion = "ABI|" + sample._1)) }
            val snapshots = if (sample._2) _snapshots(base).reverse else _snapshots(base)
            val nativeinput = base.copy(acquisition = NativeAcquisition.Acquired(_scope.scopeId, snapshots), bindings = bindings)
            val edges = bindings.reverse
            val projections = Vector("Z.json" -> _projection(edges, "first"), "A.json" -> _projection(bindings, "second"))
            val ordered = if (sample._2) projections.reverse else projections
            val selected = _package(root, ordered)
            ComponentCapabilityDiscovery.discover(selected, new CountingProvider(nativeinput)).exists { result =>
              result.acquired.index == ordered.map(_._1) && result.entries.map(_.admitted.projection) == ordered.map(_._2) &&
                result.entries.zip(ordered).forall { (entry, pair) =>
                  entry.realizations.map(_.realization.edge) == _edges(pair._2) && entry.realizations.forall { target =>
                    bindings.exists(binding => (target.binding eq binding) && target.target == binding.target) && snapshots.exists(snapshot => target.snapshot eq snapshot)
                  }
                }
            }
          }
        })
        Then("actual successes retain literal contexts and every ordering with zero discards")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }
}

object ComponentCapabilityDiscoverySpec {
  private[capability] final class CountingProvider(val input: NativeInput) extends ComponentCapabilityMetadataProvider {
    private var _reads: Int = 0
    def reads: Int = _reads
    def componentCapabilityMetadata: NativeInput = { _reads += 1; input }
  }

  private[capability] def _scope: Scope = ComponentCapabilityTargetAdapterSpec._scope

  private[capability] def _snapshots(input: NativeInput): Vector[NativeSnapshot] = input.acquisition match {
    case NativeAcquisition.Acquired(_, values) => values
    case _ => throw new IllegalArgumentException("Expected actual acquired test metadata.")
  }

  private[capability] def _native: NativeInput = {
    val first = ComponentCapabilityTargetAdapterSpec._snapshot("org.example.Payments", "native-A")
    val second = ComponentCapabilityTargetAdapterSpec._snapshot("org.example.Orchestrator", "native-B")
    val snapshots = Vector(first, second).map(value => NativeSnapshot(value.component, value.source, value.operations, value.workflows, value.stateMachines))
    val contexts = Vector((snapshots.head, TargetKind.Operation, "Charge"), (snapshots(1), TargetKind.Workflow, "Checkout"), (snapshots.head, TargetKind.StateMachine, "Payment"))
    val bindings = contexts.map { (snapshot, kind, selector) =>
      NativeBinding(snapshot.component, snapshot.source, TargetContract(kind, ModelElementId("model:targets", "explicit:" + kind.tag), snapshot.component, "contract:" + kind.tag, "abi:" + kind.tag, snapshot.source.copy(line = Some(11))), selector)
    }
    NativeInput(NativeAcquisition.Acquired(_scope.scopeId, snapshots), ScopeInput.Ready(_scope), bindings)
  }

  private[capability] def _projection(bindings: Vector[NativeBinding], suffix: String): Projection = {
    val definition = _definition("model:test", "cap:" + suffix, "cap-v1")
    val declaration = Declaration(_empty_projection.component.componentId, Direction.Provided, CapabilityReference(definition.identity, definition.contractVersion), Source("model:test", "models/declaration.cml", "declaration-" + suffix, Some(5)), Present(Vector.empty), Present(Vector.empty))
    val edges = bindings.zipWithIndex.map { (binding, index) =>
      val target = binding.target
      Realization(declaration._key, target.targetKind, ModelReference(target.identity, ReferenceBoundary.Local), target.component, target.contractVersion, target.generatedAbiVersion, Source("model:test", "models/edges.cml", s"edge-$suffix-$index", Some(index + 20)), Declared)
    }
    _empty_projection.copy(source = _empty_projection.source.copy(sourceRevision = "snapshot-" + suffix), capabilities = Vector(definition), declarations = Vector(declaration.copy(realizations = Present(edges))))
  }

  private def _edges(projection: Projection): Vector[Realization] = projection.declarations.head.realizations match {
    case Present(values) => values
    case _ => Vector.empty
  }
}
