package org.goldenport.cncf.component.capability

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityReference {
  final case class ModelElementId(modelId: String, elementId: String) {}
  final case class TermId(vocabularyId: String, termId: String) {}
  final case class RelationId(vocabularyId: String, relationId: String) {}
  final case class ProfileId(vocabularyId: String, profileId: String) {}

  final case class Source(
    modelAuthority: String,
    path: String,
    sourceRevision: String,
    line: Option[Int]
  ) {}

  sealed abstract class Presence[+A] {}
  final case class Present[A](value: A) extends Presence[A] {}
  final case class Absent(reason: AbsenceReason, detail: String) extends Presence[Nothing] {}

  enum AbsenceReason {
    case NotDeclared, NotRepresented, Unsupported, NotApplicable
  }

  sealed abstract class Origin {}
  case object Declared extends Origin {}
  final case class Derived(ruleId: String, inputs: Vector[Source]) extends Origin {}

  enum ReferenceBoundary(val tag: String) {
    case Local extends ReferenceBoundary("LOCAL")
    case External extends ReferenceBoundary("EXTERNAL")
  }

  final case class ModelReference(identity: ModelElementId, boundary: ReferenceBoundary) {}
  sealed abstract class SemanticTarget {}
  final case class ModelTarget(identity: ModelElementId) extends SemanticTarget {}
  final case class TermTarget(identity: TermId) extends SemanticTarget {}
  final case class LocalizedLabel(text: String, language: Option[String]) {}

  enum ModelRole(val tag: String) {
    case Goal extends ModelRole("GOAL")
    case UseCase extends ModelRole("USE_CASE")
    case Scenario extends ModelRole("SCENARIO")
    case UseCaseSlice extends ModelRole("USE_CASE_SLICE")
    case Component extends ModelRole("COMPONENT")
    case Specification extends ModelRole("SPECIFICATION")
    case Evidence extends ModelRole("EVIDENCE")
  }

  enum TraceabilityRole(val tag: String) {
    case Specification extends TraceabilityRole("SPECIFICATION")
    case Evidence extends TraceabilityRole("EVIDENCE")
  }

  final case class ModelLink(
    role: ModelRole,
    target: ModelReference,
    source: Source,
    origin: Origin
  ) {}

  final case class Traceability(
    role: TraceabilityRole,
    target: ModelReference,
    source: Source,
    origin: Origin
  ) {}

  final case class Grounding(
    target: SemanticTarget,
    boundary: ReferenceBoundary,
    relation: Presence[RelationId],
    profile: Presence[ProfileId],
    context: Presence[String],
    preferredLabel: Presence[LocalizedLabel],
    source: Source,
    origin: Origin
  ) {}
}
