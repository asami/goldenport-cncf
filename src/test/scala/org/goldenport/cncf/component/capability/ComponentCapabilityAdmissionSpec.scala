package org.goldenport.cncf.component.capability

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityAdmission.*
import ComponentCapabilityProjectionCodecSpec.{_definition, _empty_projection}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentCapabilityAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) = {
    afterWord(s"in spec:component-capability, example:$example, rules:$rules, phase:78")
  }

  private def _right[A](result: Either[Diagnostic, A]): A = {
    result.fold(error => fail(s"Unexpected ${error.kind} at ${error.location}: ${error.detail}"), value => value)
  }

  private def _left[A](result: Either[Diagnostic, A]): Diagnostic = {
    result.fold(error => error, _ => fail("Expected a typed admission failure."))
  }

  private def _present[A](value: Presence[A]): A = {
    value match {
      case Present(item) => item
      case _: Absent => fail("Expected explicitly present semantic facts.")
    }
  }

  private def _source(authority: String, revision: String): Source = {
    Source(authority, "models/facts.cml", revision, None)
  }

  private def _scope: Scope = {
    Scope("scope:test", _source("scope:authority", "scope-r100"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
  }

  private def _reference(version: String): CapabilityReference = {
    CapabilityReference(ModelElementId("model:test", "cap:base"), version)
  }

  private def _declaration(direction: Direction, version: String): Declaration = {
    Declaration(ComponentId("org.example.Payments"), direction, _reference(version),
      _source("model:test", s"declaration-${direction.tag}"), Present(Vector.empty), Present(Vector.empty))
  }

  private def _base: Projection = {
    _empty_projection.copy(
      capabilities = Vector(_definition("model:test", "cap:base", "v2")),
      declarations = Vector(_declaration(Direction.Provided, "v2")))
  }

  private def _admit(projection: Projection, scope: Scope): Either[Diagnostic, Admitted] = {
    ComponentCapabilityAdmission.admit(projection, ScopeInput.Ready(scope))
  }

  private def _target_case(kind: TargetKind, boundary: ReferenceBoundary): (Projection, Scope) = {
    val declaration = _declaration(Direction.Provided, "v2")
    val identity = ModelElementId("model:target", s"target:${kind.tag}")
    val edge = Realization(declaration._key, kind, ModelReference(identity, boundary),
      ComponentId("org.example.Targets"), s"contract-${kind.tag}", s"abi-${kind.tag}",
      _source("model:test", s"edge-${kind.tag}-r12"), Declared)
    val contract = TargetContract(kind, identity, ComponentId("org.example.Targets"),
      s"contract-${kind.tag}", s"abi-${kind.tag}", _source("model:target", s"target-${kind.tag}-r90"))
    (_base.copy(declarations = Vector(declaration.copy(realizations = Present(Vector(edge))))),
      _scope.copy(targetContracts = Vector(contract)))
  }

  private def _grounding_case: (Projection, Scope) = {
    val relation = Present(RelationId("vocab:test", "relation:denotes"))
    val profile = Present(ProfileId("vocab:test", "profile:public"))
    val source = _source("model:test", "grounding-edge-r13")
    val origin = Derived("rule:explicit", Vector(_source("vocab:test", "input-r1"), _source("model:domain", "input-r2")))
    val term = Grounding(TermTarget(TermId("vocab:test", "term:pay")), ReferenceBoundary.Local,
      relation, profile, Present("Context"), Present(LocalizedLabel("Pay", Some("en"))), source, origin)
    val model = Grounding(ModelTarget(ModelElementId("model:domain", "concept:pay")), ReferenceBoundary.Local,
      Absent(AbsenceReason.NotDeclared, "No relation."), Absent(AbsenceReason.NotDeclared, "No profile."),
      Absent(AbsenceReason.NotApplicable, "No context."), Absent(AbsenceReason.NotRepresented, "No label."),
      _source("model:test", "model-edge-r14"), Declared)
    val capability = model.copy(target = ModelTarget(ModelElementId("model:test", "cap:base")), source = _source("model:test", "cap-edge-r15"))
    val external = term.copy(boundary = ReferenceBoundary.External, source = _source("model:test", "external-edge-r16"))
    val unknown = term.copy(target = TermTarget(TermId("vocab:unknown", "term:unknown")),
      boundary = ReferenceBoundary.External, relation = Present(RelationId("vocab:unknown", "relation:unknown")),
      profile = Present(ProfileId("vocab:unknown", "profile:unknown")), source = _source("model:test", "unknown-edge-r17"))
    val definition = _base.capabilities.head.copy(grounding = Present(Vector(term, model, capability, external, unknown)))
    val scope = _scope.copy(
      modelFacts = Vector(ModelFact(ModelElementId("model:domain", "concept:pay"), ModelKind.DomainConcept, _source("model:domain", "domain-fact-r81"))),
      termFacts = Vector(TermFact(TermId("vocab:test", "term:pay"), _source("vocab:test", "term-fact-r82"))),
      relationFacts = Vector(RelationFact(RelationId("vocab:test", "relation:denotes"), _source("vocab:test", "relation-fact-r83"))),
      profileFacts = Vector(ProfileFact(ProfileId("vocab:test", "profile:public"), _source("vocab:test", "profile-fact-r84"))))
    (_base.copy(capabilities = Vector(definition)), scope)
  }

  private def _compatibility(required: String, provided: String): Compatibility = {
    Compatibility(ModelElementId("model:test", "cap:base"), required, provided,
      _source("model:test", s"compatibility-$required-$provided-r70"), Declared)
  }

  private def _permuted[A](values: Vector[A], order: List[Int]): Vector[A] = {
    values.zipWithIndex.sortBy { (_, index) => order(index % order.size) }.map(_._1)
  }

  "Selected Component metadata admission" should {
    "CPA-01 keep distinct Component providers and both declaration directions" should _metadata("CPA-01", "R2,R3,R12") {
      "retain three full keys across separately acquired Component scopes" in {
        Given("two exact Components with the same local ID and Capability; the first also requires it")
        val first = _base.copy(declarations = Vector(_declaration(Direction.Provided, "v2"), _declaration(Direction.Required, "v2")))
        val othercomponent = ComponentId("org.other.Payments")
        val second = _base.copy(component = _base.component.copy(componentId = othercomponent),
          declarations = Vector(_declaration(Direction.Provided, "v2").copy(component = othercomponent)))
        When("each Component snapshot is admitted in its separately selected scope")
        val admittedfirst = _right(_admit(first, _scope))
        val admittedsecond = _right(_admit(second, _scope.copy(scopeId = "scope:other")))
        val declarations = admittedfirst.projection.declarations ++ admittedsecond.projection.declarations
        Then("provider identity and direction stay distinct without a selected provider")
        declarations.map(_._key).distinct.size shouldBe 3
        declarations.map(_.component).toSet shouldBe Set(ComponentId("org.example.Payments"), othercomponent)
        declarations.map(_.capability).distinct shouldBe Vector(_reference("v2"))
        admittedfirst.scopeId shouldBe "scope:test"
        admittedsecond.scopeId shouldBe "scope:other"
      }
    }

    "CPA-02 reject repeated complete keys and snapshot definition identity" should _metadata("CPA-02", "R2,R3,R11,R12") {
      "report the duplicate owning input even when presentation, version or source changes" in {
        Given("definition identity repetitions and full declaration repetitions with independently conflicting provenance")
        val definition = _base.capabilities.head
        val declaration = _base.declarations.head
        val projections = Vector(
          _base.copy(capabilities = Vector(definition, definition.copy(contractVersion = "v3", name = "Different label", source = definition.source.copy(sourceRevision = "other-definition")))),
          _base.copy(declarations = Vector(declaration, declaration.copy(source = declaration.source.copy(sourceRevision = "other-declaration")))))
        When("the repeated snapshots are admitted")
        val failures = projections.map(projection => _left(_admit(projection, _scope)))
        Then("duplicates are Ambiguous rather than overwritten or selected by order")
        failures.map(_.kind) shouldBe Vector(DiagnosticKind.Ambiguous, DiagnosticKind.Ambiguous)
        failures.map(_.location) shouldBe Vector("/capabilities/1", "/declarations/1")
        failures.map(_.source.map(_.sourceRevision)) shouldBe Vector(Some("other-definition"), Some("other-declaration"))
      }
    }

    "CPA-03 preserve envelope and edge source ownership" should _metadata("CPA-03", "R3,R5,R11") {
      "reject a different Component or a different qualified source authority at the exact owner" in {
        Given("Component and definition/declaration/grounding source ownership conflicts")
        val (grounded, scope) = _grounding_case
        val edge = _present(grounded.capabilities.head.grounding).head
        val projections = Vector(
          _base.copy(declarations = Vector(_base.declarations.head.copy(component = ComponentId("org.other.Payments")))),
          _base.copy(declarations = Vector(_base.declarations.head.copy(source = _source("model:other", "conflicting-declaration")))),
          _base.copy(capabilities = Vector(_base.capabilities.head.copy(source = _source("model:other", "conflicting-definition")))),
          grounded.copy(capabilities = Vector(grounded.capabilities.head.copy(grounding = Present(Vector(edge.copy(source = _source("model:other", "conflicting-edge"))))))))
        When("these metadata conflicts reach raw admission")
        val failures = projections.map(projection => _left(_admit(projection, scope)))
        Then("the exact Component/source disagreement is Incompatible with its owning pointer")
        failures.map(_.kind).distinct shouldBe Vector(DiagnosticKind.Incompatible)
        failures.map(_.location) shouldBe Vector("/declarations/0/component", "/declarations/0/source/modelAuthority", "/capabilities/0/source/modelAuthority", "/capabilities/0/grounding/value/0/source/modelAuthority")
        failures.last.source.map(_.sourceRevision) shouldBe Some("conflicting-edge")
      }
    }

    "CPA-04 resolve exact Capability identity and version" should _metadata("CPA-04", "R2,R3,R12") {
      "separate missing, wrong-version and duplicate supplemental candidates" in {
        Given("a missing qualified identity, a missing exact version and duplicate acquired supplemental definitions")
        val missing = _base.copy(capabilities = Vector.empty)
        val wrongversion = _base.copy(declarations = Vector(_declaration(Direction.Provided, "unknown-version")))
        val external = _definition("model:test", "cap:base", "v2").copy(source = _source("model:test", "external-definition-r200"))
        val duplicate = _scope.copy(capabilities = Vector(external, external.copy(source = _source("model:test", "external-definition-r201"))))
        When("these requests are admitted alongside a single explicit supplemental candidate")
        val failures = Vector(_left(_admit(missing, _scope)), _left(_admit(wrongversion, _scope)), _left(_admit(missing, duplicate)))
        val bound = _right(_admit(missing, _scope.copy(capabilities = Vector(external))))
        Then("the unique supplemental candidate binds and each failure preserves its taxonomy")
        failures.map(_.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Incompatible, DiagnosticKind.Ambiguous)
        failures.map(_.location) shouldBe Vector("/declarations/0/capability/identity", "/declarations/0/capability/contractVersion", "/scope/capabilities/1")
        bound.capabilities shouldBe Vector(external)
      }
    }

    "CPA-05 bind Local grounding without changing External facts" should _metadata("CPA-05", "R5,R6,R12") {
      "retain model, term, relation, profile and derived input order while rejecting missing or duplicate Local facts" in {
        Given("explicit Local model/vocabulary facts, a Capability model identity and independent External grounding")
        val (projection, scope) = _grounding_case
        val variants = Vector(
          scope.copy(termFacts = Vector.empty) -> "/capabilities/0/grounding/value/0/target/identity",
          scope.copy(termFacts = scope.termFacts ++ scope.termFacts) -> "/capabilities/0/grounding/value/0/target/identity",
          scope.copy(relationFacts = Vector.empty) -> "/capabilities/0/grounding/value/0/relation/value",
          scope.copy(relationFacts = scope.relationFacts ++ scope.relationFacts) -> "/capabilities/0/grounding/value/0/relation/value",
          scope.copy(profileFacts = Vector.empty) -> "/capabilities/0/grounding/value/0/profile/value",
          scope.copy(profileFacts = scope.profileFacts ++ scope.profileFacts) -> "/capabilities/0/grounding/value/0/profile/value",
          scope.copy(modelFacts = Vector.empty) -> "/capabilities/0/grounding/value/1/target/identity",
          scope.copy(modelFacts = scope.modelFacts ++ scope.modelFacts) -> "/capabilities/0/grounding/value/1/target/identity")
        When("the complete grounding and each independently defective Local fact scope are admitted")
        val admitted = _right(_admit(projection, scope))
        val failures = variants.map { (selected, path) => _left(_admit(projection, selected)) -> path }
        Then("Local references resolve uniquely and both known and unknown External references remain external")
        admitted.projection shouldBe projection
        failures.map(_._1.kind) shouldBe Vector(DiagnosticKind.Malformed, DiagnosticKind.Ambiguous, DiagnosticKind.Malformed, DiagnosticKind.Ambiguous, DiagnosticKind.Malformed, DiagnosticKind.Ambiguous, DiagnosticKind.Malformed, DiagnosticKind.Ambiguous)
        failures.foreach { (error, path) => error.location shouldBe path; error.scopeId shouldBe Some(scope.scopeId) }
        val grounding = _present(admitted.projection.capabilities.head.grounding)
        grounding.map(_.boundary) shouldBe Vector(ReferenceBoundary.Local, ReferenceBoundary.Local, ReferenceBoundary.Local, ReferenceBoundary.External, ReferenceBoundary.External)
        grounding.head.origin shouldBe Derived("rule:explicit", Vector(_source("vocab:test", "input-r1"), _source("model:domain", "input-r2")))
      }
    }

    "CPA-06 validate every admitted scope fact and its authority" should _metadata("CPA-06", "R2,R5,R6,R12") {
      "reject malformed identity/revision and authority mismatches with the fact source retained" in {
        Given("independently authored malformed model and vocabulary facts and an invalid target source")
        val (_, scope) = _grounding_case
        val model = scope.modelFacts.head
        val term = scope.termFacts.head
        val relation = scope.relationFacts.head
        val profile = scope.profileFacts.head
        val (_, targetscope) = _target_case(TargetKind.Operation, ReferenceBoundary.Local)
        val cases = Vector(
          (scope.copy(modelFacts = Vector(model.copy(identity = model.identity.copy(elementId = " padded")))), DiagnosticKind.Malformed, "/scope/modelFacts/0/identity/elementId", model.source),
          (scope.copy(modelFacts = Vector(model.copy(source = _source("model:wrong", "model-wrong")))), DiagnosticKind.Incompatible, "/scope/modelFacts/0/source/modelAuthority", _source("model:wrong", "model-wrong")),
          (scope.copy(termFacts = Vector(term.copy(source = term.source.copy(sourceRevision = "")))), DiagnosticKind.Malformed, "/scope/termFacts/0/source/sourceRevision", term.source.copy(sourceRevision = "")),
          (scope.copy(termFacts = Vector(term.copy(source = _source("vocab:wrong", "term-wrong")))), DiagnosticKind.Incompatible, "/scope/termFacts/0/source/modelAuthority", _source("vocab:wrong", "term-wrong")),
          (scope.copy(relationFacts = Vector(relation.copy(identity = relation.identity.copy(relationId = "bad\u0001")))), DiagnosticKind.Malformed, "/scope/relationFacts/0/identity/relationId", relation.source),
          (scope.copy(profileFacts = Vector(profile.copy(source = _source("vocab:wrong", "profile-wrong")))), DiagnosticKind.Incompatible, "/scope/profileFacts/0/source/modelAuthority", _source("vocab:wrong", "profile-wrong")),
          (targetscope.copy(targetContracts = Vector(targetscope.targetContracts.head.copy(source = _source("model:wrong", "target-wrong")))), DiagnosticKind.Incompatible, "/scope/targetContracts/0/source/modelAuthority", _source("model:wrong", "target-wrong")))
        When("each scope is selected for otherwise valid metadata")
        val failures = cases.map { (selected, kind, path, source) => (_left(_admit(_base, selected)), kind, path, source) }
        Then("the scope is rejected with the exact fact pointer, scope and supplied source")
        failures.foreach { (error, kind, path, source) =>
          error.kind shouldBe kind
          error.location shouldBe path
          error.scopeId shouldBe Some("scope:test")
          error.source shouldBe Some(source)
        }
      }
    }

    "CPA-07 bind each full discovery and traceability role" should _metadata("CPA-07", "R9,R12") {
      "retain independent Specification and Evidence edges and reject wrong-kind Local references" in {
        Given("all seven discovery roles and distinct Specification/Evidence facts with independent source revisions")
        val roles = Vector(
          (ModelRole.Goal, ModelKind.Goal, "goal"), (ModelRole.UseCase, ModelKind.UseCase, "use-case"),
          (ModelRole.Scenario, ModelKind.Scenario, "scenario"), (ModelRole.UseCaseSlice, ModelKind.UseCaseSlice, "slice"),
          (ModelRole.Component, ModelKind.Component, "component"), (ModelRole.Specification, ModelKind.Specification, "spec"),
          (ModelRole.Evidence, ModelKind.Evidence, "evidence"))
        val facts = roles.map { (_, kind, id) => ModelFact(ModelElementId("model:links", id), kind, _source("model:links", s"fact-$id")) }
        val links = roles.map { (role, _, id) => ModelLink(role, ModelReference(ModelElementId("model:links", id), ReferenceBoundary.Local), _source("model:test", s"discovery-$id"), Declared) }
        val traces = Vector(
          Traceability(TraceabilityRole.Specification, ModelReference(ModelElementId("model:links", "spec"), ReferenceBoundary.Local), _source("model:test", "trace-spec"), Declared),
          Traceability(TraceabilityRole.Evidence, ModelReference(ModelElementId("model:links", "evidence"), ReferenceBoundary.Local), _source("model:test", "trace-evidence"), Declared))
        val projection = _base.copy(capabilities = Vector(_base.capabilities.head.copy(discoveryReferences = Present(links), traceability = Present(traces))))
        val scope = _scope.copy(modelFacts = facts)
        val wrongdiscovery = scope.copy(modelFacts = facts.updated(0, facts.head.copy(kind = ModelKind.UseCase)))
        val wrongtrace = projection.copy(capabilities = Vector(projection.capabilities.head.copy(traceability = Present(Vector(traces.head.copy(target = traces(1).target))))))
        When("exact and wrong-kind Local edges are admitted")
        val admitted = _right(_admit(projection, scope))
        val discoveryfailure = _left(_admit(projection, wrongdiscovery))
        val tracefailure = _left(_admit(wrongtrace, scope))
        val missing = _left(_admit(projection, scope.copy(modelFacts = facts.tail)))
        val duplicate = _left(_admit(projection, scope.copy(modelFacts = facts :+ facts.head)))
        Then("full roles and edge sources survive, while wrong-kind/missing/duplicate targets fail distinctly")
        admitted.projection.capabilities.head.discoveryReferences shouldBe Present(links)
        admitted.projection.capabilities.head.traceability shouldBe Present(traces)
        discoveryfailure.kind shouldBe DiagnosticKind.Incompatible
        discoveryfailure.location shouldBe "/capabilities/0/discoveryReferences/value/0/target/identity"
        tracefailure.kind shouldBe DiagnosticKind.Incompatible
        tracefailure.location shouldBe "/capabilities/0/traceability/value/0/target/identity"
        tracefailure.source shouldBe Some(traces.head.source)
        missing.kind shouldBe DiagnosticKind.Malformed
        duplicate.kind shouldBe DiagnosticKind.Ambiguous
      }
    }

    "CPA-08 bind all explicit realization target kinds" should _metadata("CPA-08", "R4,R5,R7,R12") {
      "retain the original edge and independently admitted contract for Local and External boundaries" in {
        Given("independent exact Operation, Workflow and StateMachine target contracts without general model facts")
        val cases = for {
          kind <- TargetKind.values.toVector
          boundary <- ReferenceBoundary.values.toVector
        } yield _target_case(kind, boundary)
        When("each declared realization is admitted against its explicit target contract")
        val results = cases.map { (projection, scope) => _right(_admit(projection, scope)) -> scope }
        Then("all three kinds bind with separate source revisions and target ABI domains")
        results.foreach { (admitted, scope) =>
          admitted.realizations.size shouldBe 1
          admitted.realizations.head.target shouldBe scope.targetContracts.head
          admitted.realizations.head.edge shouldBe _present(admitted.projection.declarations.head.realizations).head
          admitted.realizations.head.edge.source.sourceRevision should not be admitted.realizations.head.target.source.sourceRevision
          admitted.realizations.head.target.generatedAbiVersion should not be admitted.projection.generatedAbiVersion
          scope.modelFacts shouldBe Vector.empty
        }
      }
    }

    "CPA-09 fail closed for every target identity, kind, version and ABI" should _metadata("CPA-09", "R7,R12") {
      "apply the same binding failures to all three kinds including External edges" in {
        Given("each target kind/boundary and unknown, duplicate, wrong-kind, wrong-version and wrong-ABI scope plans")
        val plans = for {
          kind <- TargetKind.values.toVector
          boundary <- ReferenceBoundary.values.toVector
        } yield {
          val (projection, scope) = _target_case(kind, boundary)
          val target = scope.targetContracts.head
          val otherkind = TargetKind.values.find(_ != kind).get
          val scopes = Vector(
            (scope.copy(targetContracts = Vector.empty), DiagnosticKind.Malformed, "target"),
            (scope.copy(targetContracts = Vector(target.copy(component = ComponentId("org.other.Targets")))), DiagnosticKind.Malformed, "target"),
            (scope.copy(targetContracts = Vector(target, target.copy(source = target.source.copy(sourceRevision = "conflicting-target-source")))), DiagnosticKind.Ambiguous, "target"),
            (scope.copy(targetContracts = Vector(target.copy(targetKind = otherkind))), DiagnosticKind.Incompatible, "targetKind"),
            (scope.copy(targetContracts = Vector(target.copy(contractVersion = "wrong-version"))), DiagnosticKind.Incompatible, "targetContractVersion"),
            (scope.copy(targetContracts = Vector(target.copy(generatedAbiVersion = "wrong-abi"))), DiagnosticKind.Incompatible, "generatedAbiVersion"))
          projection -> scopes
        }
        When("the realization candidates are resolved independently")
        val failures = plans.flatMap { (projection, scopes) =>
          scopes.map { (scope, kind, field) => (_left(_admit(projection, scope)), kind, field, projection) }
        }
        Then("each failure retains its exact edge location/source and cannot fabricate a bound realization")
        failures.foreach { (error, kind, field, projection) =>
          error.kind shouldBe kind
          error.location shouldBe s"/declarations/0/realizations/value/0/$field"
          error.scopeId shouldBe Some("scope:test")
          error.component shouldBe Some(ComponentId("org.example.Payments"))
          error.source shouldBe Some(_present(projection.declarations.head.realizations).head.source)
        }
      }
    }

    "CPA-10 distinguish declaration-key conflict from no realization" should _metadata("CPA-10", "R3,R7,R12") {
      "reject a mismatched repeated key and preserve present-empty and all explicit absences" in {
        Given("a repeated declaration key with a different direction and valid empty/absent realization facts")
        val (projection, scope) = _target_case(TargetKind.Operation, ReferenceBoundary.Local)
        val edge = _present(projection.declarations.head.realizations).head
        val mismatch = projection.copy(declarations = Vector(projection.declarations.head.copy(realizations = Present(Vector(edge.copy(declarationKey = edge.declarationKey.copy(direction = Direction.Required)))))))
        val relationships: Vector[Presence[Vector[Realization]]] = Vector(Present(Vector.empty)) ++ AbsenceReason.values.toVector.map(reason => Absent(reason, s"Explicit $reason absence"))
        val empty = relationships.map(value => _base.copy(declarations = Vector(_base.declarations.head.copy(realizations = value))))
        When("the mismatched and explicitly non-executable declarations are admitted")
        val failure = _left(_admit(mismatch, scope))
        val admitted = empty.map(value => _right(_admit(value, _scope)))
        Then("only the mismatched key is Incompatible; absence and present-empty manufacture no target")
        failure.kind shouldBe DiagnosticKind.Incompatible
        failure.location shouldBe "/declarations/0/realizations/value/0/declarationKey"
        admitted.map(_.realizations).distinct shouldBe Vector(Vector.empty)
        admitted.map(_.projection.declarations.head.realizations) shouldBe relationships
      }
    }

    "CPA-11 retain exact and directly authored compatibility" should _metadata("CPA-11", "R4,R5,R8,R12") {
      "match exact known versions and the supplied required-old to provided-new direction" in {
        Given("one old acquired definition, one current definition and direct plus redundant self-version edges")
        val edges = Vector(_compatibility("v1", "v2"), _compatibility("v2", "v2"))
        val projection = _base.copy(compatibility = Present(edges))
        val old = _definition("model:test", "cap:base", "v1").copy(source = _source("model:test", "old-source-r200"))
        val scope = _scope.copy(capabilities = Vector(old))
        When("the metadata and both compatibility questions are admitted")
        val admitted = _right(_admit(projection, scope))
        val exact = isCompatible(admitted, _reference("v2"), _reference("v2"))
        val directed = isCompatible(admitted, _reference("v1"), _reference("v2"))
        Then("the explicit edges remain separate from Component/package/producer/source versions")
        exact shouldBe true
        directed shouldBe true
        admitted.projection.compatibility shouldBe Present(edges)
        admitted.capabilities.map(_.contractVersion) shouldBe Vector("v2", "v1")
        admitted.projection.component.version shouldBe "3.7.0-SNAPSHOT"
        admitted.projection.packageCoordinate.version shouldBe "package-v21"
        admitted.projection.producer.version shouldBe "producer-v9"
        admitted.capabilities.last.source.sourceRevision shouldBe "old-source-r200"
      }
    }

    "CPA-12 reject inferred compatibility and provider order preference" should _metadata("CPA-12", "R2,R3,R8,R12") {
      "keep directed answers invariant under declaration and admitted fact permutations" in {
        Given("a direct v1-to-v2-to-v3 chain, known adjacent versions, equal labels on another identity and explicit Local facts")
        val (grounded, grounds) = _grounding_case
        val other = _definition("model:test", "cap:other", "v2")
        val projection = grounded.copy(capabilities = grounded.capabilities :+ other,
          declarations = Vector(_declaration(Direction.Provided, "v2"), _declaration(Direction.Required, "v1")),
          compatibility = Present(Vector(_compatibility("v1", "v2"), _compatibility("v2", "v3"))))
        val scope = grounds.copy(capabilities = Vector("v1", "v3", "v2.1").map(version => _definition("model:test", "cap:base", version)),
          modelFacts = grounds.modelFacts :+ ModelFact(ModelElementId("model:domain", "concept:other"), ModelKind.DomainConcept, _source("model:domain", "other-domain")),
          termFacts = grounds.termFacts :+ TermFact(TermId("vocab:test", "term:other"), _source("vocab:test", "other-term")),
          relationFacts = grounds.relationFacts :+ RelationFact(RelationId("vocab:test", "relation:other"), _source("vocab:test", "other-relation")),
          profileFacts = grounds.profileFacts :+ ProfileFact(ProfileId("vocab:test", "profile:other"), _source("vocab:test", "other-profile")))
        val questions = Vector(
          _reference("v2") -> _reference("v1"), _reference("v1") -> _reference("v3"),
          _reference("v2") -> _reference("v2.1"), _reference("v2") -> CapabilityReference(other.identity, "v2"),
          _reference("unknown") -> _reference("unknown"))
        val orders = Gen.listOfN(3, Gen.choose(-1000000, 1000000))
        When("compatibility is asked directly and ScalaCheck permutes declarations and every reference-fact vector")
        val admitted = _right(_admit(projection, scope))
        val inferred = questions.map { (required, provided) => isCompatible(admitted, required, provided) }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(80), Prop.forAll(orders) { (order: List[Int]) =>
          val changed = projection.copy(declarations = _permuted(projection.declarations, order), capabilities = _permuted(projection.capabilities, order))
          val selected = scope.copy(modelFacts = _permuted(scope.modelFacts, order), termFacts = _permuted(scope.termFacts, order),
            relationFacts = _permuted(scope.relationFacts, order), profileFacts = _permuted(scope.profileFacts, order), capabilities = _permuted(scope.capabilities, order))
          _admit(changed, selected).exists { result =>
            result.projection.declarations.map(_._key).toSet == projection.declarations.map(_._key).toSet &&
              isCompatible(result, _reference("v1"), _reference("v2")) &&
              questions.forall { (required, provided) => !isCompatible(result, required, provided) }
          }
        })
        Then("reverse, transitive, SemVer, name and unknown-reference inferences stay false in every order")
        inferred shouldBe Vector.fill(questions.size)(false)
        checked.passed shouldBe true
      }
    }

    "CPA-13 resolve both directed compatibility endpoints uniquely" should _metadata("CPA-13", "R8,R12") {
      "reject dangling, wrong-version, duplicate direction and duplicate candidate facts" in {
        Given("the old/current definitions and single-edge compatibility mutation plans")
        val edge = _compatibility("v1", "v2")
        val scope = _scope.copy(capabilities = Vector(_definition("model:test", "cap:base", "v1")))
        val cases = Vector(
          (_base.copy(compatibility = Present(Vector(edge.copy(identity = ModelElementId("model:missing", "cap:base"))))), scope, DiagnosticKind.Malformed, "/compatibility/value/0/identity"),
          (_base.copy(compatibility = Present(Vector(edge.copy(requiredVersion = "missing")))), scope, DiagnosticKind.Incompatible, "/compatibility/value/0/requiredVersion"),
          (_base.copy(compatibility = Present(Vector(edge.copy(providedVersion = "missing")))), scope, DiagnosticKind.Incompatible, "/compatibility/value/0/providedVersion"),
          (_base.copy(compatibility = Present(Vector(edge, edge.copy(source = edge.source.copy(sourceRevision = "duplicate-edge"))))), scope, DiagnosticKind.Ambiguous, "/compatibility/value/1"),
          (_base.copy(compatibility = Present(Vector(edge))), scope.copy(capabilities = scope.capabilities ++ scope.capabilities), DiagnosticKind.Ambiguous, "/scope/capabilities/1"))
        When("each authored edge reaches selected-scope admission")
        val failures = cases.map { (projection, selected, kind, path) => (_left(_admit(projection, selected)), kind, path) }
        Then("no dangling version or repeated direction is silently dropped")
        failures.foreach { (error, kind, path) => error.kind shouldBe kind; error.location shouldBe path }
        failures(3)._1.source.map(_.sourceRevision) shouldBe Some("duplicate-edge")
      }
    }

    "CPA-14 distinguish acquired empty metadata from failed outcomes" should _metadata("CPA-14", "R1,R9,R10,R12") {
      "admit only ready acquired emptiness and forward rejected diagnostics unchanged" in {
        Given("an acquired empty projection, an explicit ready empty scope and each supplied failure taxonomy")
        val projection = _empty_projection
        val scope = _scope
        val errors = DiagnosticKind.values.toVector.map(kind => Diagnostic(kind, "Observed acquisition/scope outcome.", Some("scope:observed"), Some(ComponentId("org.example.Payments")), Some(_source("model:observed", "observed-revision")), "/observed/input"))
        When("empty acquisition and source/scope rejection branches are admitted")
        val json = _right(ComponentCapabilityProjectionCodec.encode(projection))
        val empty = _right(admitJson(SourceInput.Acquired(scope.scopeId, json), ScopeInput.Ready(scope)))
        val sources = errors.map(error => admitJson(SourceInput.Rejected(error), ScopeInput.Ready(scope)))
        val scopes = errors.map(error => admitJson(SourceInput.Acquired(scope.scopeId, "invalid-json"), ScopeInput.Rejected(error)))
        val rawscopes = errors.map(error => admit(projection, ScopeInput.Rejected(error)))
        Then("ready empty metadata succeeds and failure kinds, source and locations are preserved exactly")
        empty.projection shouldBe projection
        empty.capabilities shouldBe Vector.empty
        empty.realizations shouldBe Vector.empty
        sources shouldBe errors.map(Left(_))
        scopes shouldBe errors.map(Left(_))
        rawscopes shouldBe errors.map(Left(_))
      }
    }

    "CPA-15 require the acquired and selected scope identities to agree" should _metadata("CPA-15", "R10,R12") {
      "reject an unselected scope without turning it into admitted emptiness" in {
        Given("an acquisition selected as scope:other and a ready scope:test metadata scope")
        val source = SourceInput.Acquired("scope:other", "invalid-json")
        val selected = ScopeInput.Ready(_scope)
        When("the two acquisition outcomes are joined")
        val failure = _left(admitJson(source, selected))
        Then("scope mismatch is Incompatible before bytes can be used as selected metadata")
        failure.kind shouldBe DiagnosticKind.Incompatible
        failure.location shouldBe "/scopeId"
        failure.scopeId shouldBe Some("scope:other")
        failure.source shouldBe None
        failure.component shouldBe None
      }
    }

    "CPA-16 retain stable Capability identity across explicit realization changes" should _metadata("CPA-16", "R2,R4,R5,R7,R8,R12") {
      "bind a separately authored replacement target and keep changed edge/target revisions independent" in {
        Given("a stable Capability contract and separately supplied replacement target identity, version, ABI and revisions")
        val cases = TargetKind.values.toVector.map { kind =>
          val (projection, scope) = _target_case(kind, ReferenceBoundary.Local)
          val edge = _present(projection.declarations.head.realizations).head
          val identity = ModelElementId("model:replacement", s"replacement:${kind.tag}")
          val changededge = edge.copy(target = ModelReference(identity, ReferenceBoundary.Local), targetContractVersion = "replacement-contract", generatedAbiVersion = "replacement-abi", source = _source("model:test", "changed-edge-r300"))
          val target = TargetContract(kind, identity, ComponentId("org.example.Targets"), "replacement-contract", "replacement-abi", _source("model:replacement", "changed-target-r400"))
          val changed = projection.copy(declarations = Vector(projection.declarations.head.copy(realizations = Present(Vector(changededge)))))
          (projection, scope, changed, scope.copy(targetContracts = Vector(target)), target)
        }
        When("the original and replacement facts are independently admitted and replacement edges are tried against the old scope")
        val results = cases.map { (original, oldscope, changed, newscope, target) =>
          (_right(_admit(original, oldscope)), _right(_admit(changed, newscope)), _left(_admit(changed, oldscope)), target)
        }
        Then("the explicitly unchanged Capability identity/version stays stable and only the exact new target binds")
        results.foreach { (original, changed, staleedge, target) =>
          changed.capabilities shouldBe original.capabilities
          changed.projection.declarations.head.capability shouldBe original.projection.declarations.head.capability
          changed.realizations.head.target shouldBe target
          changed.realizations.head.edge.source.sourceRevision shouldBe "changed-edge-r300"
          changed.realizations.head.target.source.sourceRevision shouldBe "changed-target-r400"
          staleedge.kind shouldBe DiagnosticKind.Malformed
          staleedge.location shouldBe "/declarations/0/realizations/value/0/target"
        }
      }
    }
  }
}
