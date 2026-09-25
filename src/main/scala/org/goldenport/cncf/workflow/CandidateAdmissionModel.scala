package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import scala.util.Try

/** Optional admission vocabulary above the Phase 77 execution/Continuation layer.
  * Neither submission nor evaluation advances a StateMachine or commits an application effect.
  * @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
object CandidateAdmissionModel {
  final case class CandidateRef(identity: String, revision: String) {
    def validateC: Consequence[CandidateRef] =
      if (!_name(identity) || !_name(revision))
        Consequence.stateConflict("Candidate reference requires identity and revision")
      else Consequence.success(this)
  }

  final case class CandidateSnapshot[C](reference: CandidateRef, payload: WorkflowProtocolV1.TypedValue[C]) {
    def validateC: Consequence[CandidateSnapshot[C]] =
      if (reference == null || payload == null)
        Consequence.stateConflict("Candidate snapshot is incomplete")
      else reference.validateC.flatMap(_ => payload.validateC.map(_ => this))
  }

  enum RequirementKind {
    case Semantic, Deterministic, Authority
  }

  /** Scope has application meaning; CNCF only carries it with its declared type. */
  final case class AdmissionRequirement[S](
    identity: String,
    kind: RequirementKind,
    evidenceType: String,
    scope: WorkflowProtocolV1.TypedValue[S]
  ) {
    def validateC: Consequence[AdmissionRequirement[S]] =
      if (!_name(identity) || kind == null || !_name(evidenceType) || scope == null)
        Consequence.stateConflict("Admission requirement is incomplete")
      else scope.validateC.map(_ => this)
  }

  final case class AdmissionEvidence[E, S](
    identity: String,
    requirementIdentity: String,
    target: CandidateRef,
    payload: WorkflowProtocolV1.TypedValue[E],
    scope: WorkflowProtocolV1.TypedValue[S],
    provenance: String
  ) {
    def validateC: Consequence[AdmissionEvidence[E, S]] =
      if (!_name(identity) || !_name(requirementIdentity) || target == null ||
          payload == null || scope == null || !_name(provenance))
        Consequence.stateConflict("Admission evidence is incomplete")
      else target.validateC.flatMap(_ => payload.validateC.flatMap(_ => scope.validateC.map(_ => this)))
  }

  final case class AdmissionSubmission[C, E, S](
    identity: String,
    candidate: CandidateSnapshot[C],
    evidence: Vector[AdmissionEvidence[E, S]]
  ) {
    def validateC: Consequence[AdmissionSubmission[C, E, S]] =
      if (!_name(identity) || candidate == null || evidence == null || evidence.exists(_ == null) ||
          evidence.map(_.identity).distinct.size != evidence.size)
        Consequence.stateConflict("Admission submission is incomplete or has duplicate evidence")
      else candidate.validateC.flatMap { _ =>
        _validate_all(evidence.map(_.validateC)).map(_ => this)
      }
  }

  enum EvidenceDefect {
    case Missing, WrongTarget, WrongType, WrongScopeType, Stale, InsufficientCoverage
  }

  final case class AdmissionGap[S](requirement: AdmissionRequirement[S], defects: Vector[EvidenceDefect])

  final case class AdmissionEvaluation[S](
    target: CandidateRef,
    acceptedEvidence: Vector[String],
    gaps: Vector[AdmissionGap[S]]
  ) {
    def result: AdmissionResult[S] =
      if (gaps.isEmpty) AdmissionResult.Admitted(target, acceptedEvidence)
      else AdmissionResult.Pending(target, gaps)
  }

  sealed trait AdmissionResult[+S]
  object AdmissionResult {
    final case class Admitted(target: CandidateRef, evidence: Vector[String]) extends AdmissionResult[Nothing]
    final case class Pending[S](target: CandidateRef, gaps: Vector[AdmissionGap[S]]) extends AdmissionResult[S]
  }

  /** Application-owned meaning of freshness and scope coverage. These checks cannot grant
    * transition or commitment authority; they only decide whether evidence is reusable.
    */
  trait EvidencePolicy[E, S] {
    def isFresh(evidence: AdmissionEvidence[E, S], target: CandidateRef): Boolean
    def covers(evidence: AdmissionEvidence[E, S], requirement: AdmissionRequirement[S]): Boolean
  }

  def evaluateC[C, E, S](
    submission: AdmissionSubmission[C, E, S],
    requirements: Vector[AdmissionRequirement[S]],
    policy: EvidencePolicy[E, S]
  ): Consequence[AdmissionEvaluation[S]] =
    evaluateC(submission, requirements, policy, Vector.empty[AdmissionEvidence[E, S]])

  def evaluateC[C, E, S](
    submission: AdmissionSubmission[C, E, S],
    requirements: Vector[AdmissionRequirement[S]],
    policy: EvidencePolicy[E, S],
    additionalEvidence: Vector[AdmissionEvidence[E, S]]
  ): Consequence[AdmissionEvaluation[S]] =
    if (submission == null || requirements == null || policy == null || additionalEvidence == null ||
        requirements.exists(_ == null) ||
        requirements.map(_.identity).distinct.size != requirements.size)
      Consequence.stateConflict("Admission evaluation is incomplete or has duplicate requirements")
    else submission.validateC.flatMap { original =>
      original.copy(evidence = original.evidence ++ additionalEvidence).validateC.flatMap { valid =>
        _validate_all(requirements.map(_.validateC)).map { _ =>
          val assessed = requirements.map { requirement =>
            val candidates = valid.evidence.filter(_.requirementIdentity == requirement.identity)
            val checks = candidates.map { evidence =>
              evidence -> _defects(evidence, requirement, valid.candidate.reference, policy)
            }
            val accepted = checks.collectFirst { case (evidence, defects) if defects.isEmpty => evidence }
            accepted match {
              case Some(evidence) => Right(evidence.identity)
              case None =>
                val defects = if (candidates.isEmpty) Vector(EvidenceDefect.Missing)
                  else checks.flatMap(_._2).distinct
                Left(AdmissionGap(requirement, defects))
            }
          }
          AdmissionEvaluation(
            valid.candidate.reference,
            assessed.collect { case Right(identity) => identity },
            assessed.collect { case Left(gap) => gap }
          )
        }
      }
    }

  private def _defects[E, S](
    evidence: AdmissionEvidence[E, S],
    requirement: AdmissionRequirement[S],
    target: CandidateRef,
    policy: EvidencePolicy[E, S]
  ): Vector[EvidenceDefect] = {
    val correlation = Vector(
      Option.when(evidence.target != target)(EvidenceDefect.WrongTarget),
      Option.when(evidence.payload.typeIdentity != requirement.evidenceType)(EvidenceDefect.WrongType),
      Option.when(evidence.scope.typeIdentity != requirement.scope.typeIdentity)(EvidenceDefect.WrongScopeType)
    ).flatten
    if (correlation.nonEmpty) correlation
    else Vector(
      Option.when(!Try(policy.isFresh(evidence, target)).getOrElse(false))(EvidenceDefect.Stale),
      Option.when(!Try(policy.covers(evidence, requirement)).getOrElse(false))(EvidenceDefect.InsufficientCoverage)
    ).flatten
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _validate_all[A](values: Vector[Consequence[A]]): Consequence[Unit] =
    values.foldLeft[Consequence[Unit]](Consequence.success(())) { (acc, value) =>
      acc.flatMap(_ => value.map(_ => ()))
    }
}
