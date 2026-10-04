package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.{CapabilityDefinition, TargetKind}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityAdmissionScope {
  enum DiagnosticKind {
    case Malformed, Unsupported, Ambiguous, Incompatible, Unavailable, Unauthorized, Stale
  }

  final case class Diagnostic(
    kind: DiagnosticKind,
    detail: String,
    scopeId: Option[String],
    component: Option[ComponentId],
    source: Option[Source],
    location: String
  ) {}

  sealed abstract class SourceInput {}
  object SourceInput {
    final case class Acquired(scopeId: String, json: String) extends SourceInput {}
    final case class Rejected(diagnostic: Diagnostic) extends SourceInput {}
  }

  sealed abstract class ScopeInput {}
  object ScopeInput {
    final case class Ready(scope: Scope) extends ScopeInput {}
    final case class Rejected(diagnostic: Diagnostic) extends ScopeInput {}
  }

  final case class Scope(
    scopeId: String,
    source: Source,
    modelFacts: Vector[ModelFact],
    termFacts: Vector[TermFact],
    relationFacts: Vector[RelationFact],
    profileFacts: Vector[ProfileFact],
    capabilities: Vector[CapabilityDefinition],
    targetContracts: Vector[TargetContract]
  ) {}

  enum ModelKind(val tag: String) {
    case DomainConcept extends ModelKind("DOMAIN_CONCEPT")
    case Goal extends ModelKind("GOAL")
    case UseCase extends ModelKind("USE_CASE")
    case Scenario extends ModelKind("SCENARIO")
    case UseCaseSlice extends ModelKind("USE_CASE_SLICE")
    case Component extends ModelKind("COMPONENT")
    case ComponentCapability extends ModelKind("COMPONENT_CAPABILITY")
    case ApplicationCapability extends ModelKind("APPLICATION_CAPABILITY")
    case Specification extends ModelKind("SPECIFICATION")
    case Evidence extends ModelKind("EVIDENCE")
    case Operation extends ModelKind("OPERATION")
    case Workflow extends ModelKind("WORKFLOW")
    case StateMachine extends ModelKind("STATE_MACHINE")
  }

  final case class ModelFact(identity: ModelElementId, kind: ModelKind, source: Source) {}
  final case class TermFact(identity: TermId, source: Source) {}
  final case class RelationFact(identity: RelationId, source: Source) {}
  final case class ProfileFact(identity: ProfileId, source: Source) {}

  final case class TargetContract(
    targetKind: TargetKind,
    identity: ModelElementId,
    component: ComponentId,
    contractVersion: String,
    generatedAbiVersion: String,
    source: Source
  ) {}
}
