package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity
}

/*
 * Value-only development composition over already admitted S01 knowledge.
 * Classifications bind caller-declared categories to exact Phase 58 logical
 * identities; this contract neither selects a source nor reads a resource.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentDevelopmentResourceCategory {
  case Manual
  case Model
  case API
  case Configuration
  case Example
  case Source
  case GeneratedSource
  case Scaladoc
  case Test
  case Provenance
  case DependencyDocumentation
}

/*
 * Source is a content category here. It carries no selection, precedence, or
 * access instruction: the exact identity has already been admitted by S01.
 */
final case class ComponentDevelopmentResourceAssignment(
  category: ComponentDevelopmentResourceCategory,
  logicalIdentity: ComponentResourceLogicalIdentity
)

final case class ComponentDevelopmentContextCandidate(
  assignments: Vector[ComponentDevelopmentResourceAssignment],
  requiredLogicalIdentities: Vector[ComponentResourceLogicalIdentity]
)

/*
 * The original S01 pair is retained rather than re-projected so the complete
 * Phase 58 resource evidence remains exact and untouched in each category.
 */
final case class ComponentDevelopmentResourceClassification(
  category: ComponentDevelopmentResourceCategory,
  pair: ResolvedComponentKnowledgeEntry
) {
  def logicalIdentity: ComponentResourceLogicalIdentity = pair.resource.logicalIdentity
}

final case class ComponentDevelopmentContext(
  knowledge: ResolvedComponentKnowledge,
  classifications: Vector[ComponentDevelopmentResourceClassification],
  requiredLogicalIdentities: Vector[ComponentResourceLogicalIdentity]
)

/*
 * One issue represents one non-ready required logical identity. The retained
 * S01 pair supplies all Phase 58 facts; the accessors expose the three facts
 * that determine development readiness without rewriting them.
 */
final case class ComponentDevelopmentResourceIssue(
  pair: ResolvedComponentKnowledgeEntry
) {
  def logicalIdentity: ComponentResourceLogicalIdentity = pair.resource.logicalIdentity
  def availability: ComponentResourceAvailability = pair.resource.availability
  def integrity: ComponentResourceIntegrity = pair.resource.integrity
  def authorization: ComponentResourceAuthorization = pair.resource.authorization
}

final case class ComponentDevelopmentContextFailure(
  issues: Vector[ComponentDevelopmentResourceIssue]
) {
  val code: String = ComponentDevelopmentContextFailure.CODE
}

object ComponentDevelopmentContextFailure {
  val CODE = "development-resource-incomplete"
}

sealed trait ComponentDevelopmentContextOutcome

object ComponentDevelopmentContextOutcome {
  final case class Ready(context: ComponentDevelopmentContext) extends ComponentDevelopmentContextOutcome
  final case class Incomplete(failure: ComponentDevelopmentContextFailure) extends ComponentDevelopmentContextOutcome
}

object ComponentDevelopmentContextCandidate {
  def validateC(
    knowledge: ResolvedComponentKnowledge,
    candidate: ComponentDevelopmentContextCandidate
  ): Consequence[ComponentDevelopmentContextCandidate] =
    _validate(knowledge, candidate).fold(Consequence.argumentInvalid, Consequence.success)

  private[knowledge] def _validate(
    knowledge: ResolvedComponentKnowledge,
    candidate: ComponentDevelopmentContextCandidate
  ): Either[String, ComponentDevelopmentContextCandidate] = {
    val assignmentpairs = candidate.assignments.map(x => x.category -> x.logicalIdentity)
    val assignedidentities = candidate.assignments.map(_.logicalIdentity)
    val admittedidentities = knowledge.entries.map(_.resource.logicalIdentity)
    for {
      _ <- Either.cond(candidate.assignments.nonEmpty, (), "development candidate.assignments must not be empty")
      _ <- Either.cond(
        assignmentpairs.distinct.size == assignmentpairs.size,
        (),
        "development candidate.assignments must not repeat a category/logical-identity pair"
      )
      _ <- Either.cond(
        candidate.requiredLogicalIdentities.nonEmpty,
        (),
        "development candidate.requiredLogicalIdentities must not be empty"
      )
      _ <- Either.cond(
        candidate.requiredLogicalIdentities.distinct.size == candidate.requiredLogicalIdentities.size,
        (),
        "development candidate.requiredLogicalIdentities must not repeat a logical identity"
      )
      _ <- _all_admitted(assignedidentities, admittedidentities, "development candidate.assignments")
      _ <- _all_admitted(candidate.requiredLogicalIdentities, admittedidentities, "development candidate.requiredLogicalIdentities")
      _ <- Either.cond(
        candidate.requiredLogicalIdentities.forall(assignedidentities.contains),
        (),
        "development candidate.requiredLogicalIdentities must each have a declared assignment"
      )
    } yield candidate
  }

  private def _all_admitted(
    identities: Vector[ComponentResourceLogicalIdentity],
    admitted: Vector[ComponentResourceLogicalIdentity],
    context: String
  ): Either[String, Unit] =
    identities.find(identity => !admitted.contains(identity)) match {
      case Some(identity) => Left(s"$context contains unknown logical identity ${identity.logicalResource}")
      case None => Right(())
    }
}

object ComponentDevelopmentContext {
  import ComponentDevelopmentContextOutcome.{Incomplete, Ready}

  def composeC(
    knowledge: ResolvedComponentKnowledge,
    candidate: ComponentDevelopmentContextCandidate
  ): Consequence[ComponentDevelopmentContextOutcome] =
    _compose(knowledge, candidate).fold(Consequence.argumentInvalid, Consequence.success)

  private def _compose(
    knowledge: ResolvedComponentKnowledge,
    candidate: ComponentDevelopmentContextCandidate
  ): Either[String, ComponentDevelopmentContextOutcome] =
    ComponentDevelopmentContextCandidate._validate(knowledge, candidate).map { _ =>
      val context = ComponentDevelopmentContext(
        knowledge = knowledge,
        classifications = _classifications(knowledge, candidate.assignments),
        requiredLogicalIdentities = candidate.requiredLogicalIdentities.sortBy(_identity_key)
      )
      val issues = context.requiredLogicalIdentities.flatMap { identity =>
        val pair = knowledge.entries.find(_.resource.logicalIdentity == identity).get
        Option.when(!_is_ready(pair))(ComponentDevelopmentResourceIssue(pair))
      }
      if (issues.isEmpty) Ready(context)
      else Incomplete(ComponentDevelopmentContextFailure(issues))
    }

  private def _classifications(
    knowledge: ResolvedComponentKnowledge,
    assignments: Vector[ComponentDevelopmentResourceAssignment]
  ): Vector[ComponentDevelopmentResourceClassification] =
    assignments.map { assignment =>
      val pair = knowledge.entries.find(_.resource.logicalIdentity == assignment.logicalIdentity).get
      ComponentDevelopmentResourceClassification(assignment.category, pair)
    }.sortBy(classification => (classification.category.ordinal, _identity_key(classification.logicalIdentity)))

  private def _is_ready(pair: ResolvedComponentKnowledgeEntry): Boolean = {
    val resource = pair.resource
    resource.availability == ComponentResourceAvailability.Available &&
    resource.integrity == ComponentResourceIntegrity.Verified &&
    resource.authorization == ComponentResourceAuthorization.Granted
  }

  private def _identity_key(identity: ComponentResourceLogicalIdentity): (String, String, String, String, String) =
    (
      identity.componentId.name,
      identity.logicalRelease,
      identity.parentComponentId.map(_.name).getOrElse(""),
      identity.childRole,
      identity.logicalResource
    )
}
