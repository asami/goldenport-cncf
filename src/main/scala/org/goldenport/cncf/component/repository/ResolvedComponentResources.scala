package org.goldenport.cncf.component.repository

import java.net.URI
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component._

/*
 * Resolver-owned resource evidence. Resolution is deliberately descriptive:
 * it neither discovers sources nor grants runtime or deployment authority.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentResourceSourceKind(val precedence: Int) {
  case EmbeddedPrimary extends ComponentResourceSourceKind(0)
  case DevelopmentDirectory extends ComponentResourceSourceKind(1)
  case ExpandedCar extends ComponentResourceSourceKind(2)
  case LocalRepository extends ComponentResourceSourceKind(3)
  case ManagedCache extends ComponentResourceSourceKind(4)
  case OfflineBundle extends ComponentResourceSourceKind(5)
  case RemoteRepository extends ComponentResourceSourceKind(6)
}

enum ComponentResourceAvailability {
  case Available, Restricted, Unavailable, Missing, Stale, Incompatible, Corrupt
}

enum ComponentResourceIntegrity {
  case NotEvaluated, Verified, Unverified
}

enum ComponentResourceAuthorization {
  case NotEvaluated, Granted, Denied
}

enum ComponentResourceCompositionShape {
  case SingleComponent, MultiComponent
}

enum ComponentResourceDiagnosticKind {
  case SingleComponent, MultiComponent, Resolved, Missing, Unavailable, Terminal, Conflict
}

final case class ComponentResourceLogicalIdentity(
  componentId: ComponentId,
  logicalRelease: String,
  parentComponentId: Option[ComponentId],
  childRole: String,
  logicalResource: String
)

final case class ComponentResourceProvenance(
  sourceKind: ComponentResourceSourceKind,
  repository: String,
  artifactCoordinate: String,
  sha256: String,
  normalizedRelativePath: String,
  logicalSource: String,
  physicalSource: String,
  resolutionStep: String,
  childRole: String,
  logicalResource: String,
  access: String,
  license: String,
  externalDeploymentRequired: Boolean
)

final case class ResolvedComponentResource(
  logicalIdentity: ComponentResourceLogicalIdentity,
  provenance: ComponentResourceProvenance,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  activationAuthority: Boolean,
  operationAuthority: Boolean,
  mcpAuthority: Boolean,
  disclosureAuthority: Boolean,
  deploymentAuthority: Boolean
)

final case class ComponentResourceEvidence(candidates: Vector[ResolvedComponentResource])

final case class ComponentResourceDiagnostic(
  kind: ComponentResourceDiagnosticKind,
  componentId: Option[ComponentId],
  sourceKind: Option[ComponentResourceSourceKind],
  message: String
)

final case class ResolvedComponentResources(
  compositionShape: ComponentResourceCompositionShape,
  resources: Vector[ResolvedComponentResource],
  diagnostics: Vector[ComponentResourceDiagnostic]
)

object ResolvedComponentResources {
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _artifact_coordinate_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _source_pattern = "[A-Za-z][A-Za-z0-9+._-]*:[A-Za-z0-9][A-Za-z0-9+._/:=-]*".r
  private val _role_pattern = "[A-Za-z][A-Za-z0-9._-]*".r
  private val _member_integrity_states = Set("verified", "unverified")
  private val _terminal = Set(
    ComponentResourceAvailability.Restricted,
    ComponentResourceAvailability.Stale,
    ComponentResourceAvailability.Incompatible,
    ComponentResourceAvailability.Corrupt
  )

  def resolveC(
    composition: ComponentSubcomponentComposition,
    evidence: ComponentResourceEvidence
  ): Consequence[ResolvedComponentResources] =
    _resolve(composition, evidence).fold(Consequence.argumentInvalid, Consequence.success)

  private final case class Target(
    identity: ComponentResourceLogicalIdentity,
    fallback: ComponentResourceProvenance,
    exactmembership: Boolean,
    externaldeploymentrequired: Boolean
  )

  private def _resolve(
    composition: ComponentSubcomponentComposition,
    evidence: ComponentResourceEvidence
  ): Either[String, ResolvedComponentResources] =
    for {
      targets <- _targets(composition)
      _ <- _validate_evidence(evidence, targets)
    } yield {
      val shape = if (composition.members.isEmpty) ComponentResourceCompositionShape.SingleComponent else ComponentResourceCompositionShape.MultiComponent
      val shapediagnostic = ComponentResourceDiagnostic(
        if (shape == ComponentResourceCompositionShape.SingleComponent) ComponentResourceDiagnosticKind.SingleComponent else ComponentResourceDiagnosticKind.MultiComponent,
        None,
        None,
        if (shape == ComponentResourceCompositionShape.SingleComponent) "composition contains only its primary component" else "composition contains declared child membership"
      )
      val resolved = targets.map { target =>
        val candidates = evidence.candidates.filter(_matches(_, target))
        _select(target, candidates)
      }
      ResolvedComponentResources(
        compositionShape = shape,
        resources = resolved.map(_._1),
        diagnostics = shapediagnostic +: resolved.flatMap(_._2)
      )
    }

  private def _targets(composition: ComponentSubcomponentComposition): Either[String, Vector[Target]] = {
    val parent = composition.parent
    val roles = composition.members.map(_.role)
    val resources = composition.members.map(_.logicalResource)
    for {
      _ <- _safe_text(parent.logicalRelease, "composition.parent.logicalRelease")
      _ <- _car(parent.primaryCar, "primary", "composition.parent.primaryCar")
      _ <- Either.cond(composition.members.map(_.componentId).distinct.size == composition.members.size, (), "composition.members must not repeat a ComponentId")
      _ <- Either.cond(roles.distinct.size == roles.size, (), "composition.members must not repeat a child role")
      _ <- Either.cond(resources.distinct.size == resources.size, (), "composition.members must not repeat a logical resource")
      members <- composition.members.foldLeft[Either[String, Vector[Target]]](Right(Vector.empty)) { (z, member) =>
        for {
          xs <- z
          target <- _member_target(parent, member)
        } yield xs :+ target
      }
    } yield _parent_target(parent) +: members
  }

  private def _parent_target(parent: ComponentSubcomponentParent): Target = {
    val identity = ComponentResourceLogicalIdentity(
      parent.componentId,
      parent.logicalRelease,
      None,
      "parent",
      s"urn:cncf:component:${parent.componentId.name}"
    )
    Target(identity, _provenance(
      parent.primaryCar,
      ComponentResourceSourceKind.EmbeddedPrimary,
      identity.childRole,
      identity.logicalResource,
      "not-evaluated",
      "not-evaluated",
      externaldeploymentrequired = false
    ), exactmembership = false, externaldeploymentrequired = false)
  }

  private def _member_target(
    parent: ComponentSubcomponentParent,
    member: ComponentSubcomponentMember
  ): Either[String, Target] =
    for {
      _ <- Either.cond(member.componentId != parent.componentId, (), "composition member must not repeat the parent ComponentId")
      _ <- _safe_text(member.logicalRelease, s"member ${member.componentId.name}.logicalRelease")
      _ <- Either.cond(_member_integrity_states(member.integrity.state), (), s"member ${member.componentId.name}.integrity.state must be verified or unverified")
      _ <- _safe_role(member.role, s"member ${member.componentId.name}.role")
      _ <- _logical_resource(member.logicalResource, s"member ${member.componentId.name}.logicalResource")
      _ <- _safe_path(member.logicalPath, s"member ${member.componentId.name}.logicalPath")
      _ <- _safe_text(member.access.visibility, s"member ${member.componentId.name}.access.visibility")
      _ <- _safe_text(member.license.spdx, s"member ${member.componentId.name}.license.spdx")
      _ <- _car(member.subcomponentCar, "subcomponent", s"member ${member.componentId.name}.subcomponentCar")
      _ <- Either.cond(!member.payload.authoritative && !member.payload.executable, (), s"member ${member.componentId.name} payload must remain non-authoritative and non-executable")
      _ <- Either.cond(_authority_is_false(member.deployment.authority), (), s"member ${member.componentId.name} deployment authority must remain false")
    } yield {
      val identity = ComponentResourceLogicalIdentity(member.componentId, member.logicalRelease, Some(parent.componentId), member.role, member.logicalResource)
      Target(identity, _provenance(
        member.subcomponentCar,
        ComponentResourceSourceKind.EmbeddedPrimary,
        member.role,
        member.logicalResource,
        member.access.visibility,
        member.license.spdx,
        member.deployment.requiresExplicitPlatformAction
      ), exactmembership = true, externaldeploymentrequired = member.deployment.requiresExplicitPlatformAction)
    }

  private def _validate_evidence(
    evidence: ComponentResourceEvidence,
    targets: Vector[Target]
  ): Either[String, Unit] =
    evidence.candidates.foldLeft[Either[String, Unit]](Right(())) { (z, candidate) =>
      for {
        _ <- z
        _ <- _candidate(candidate)
        target <- targets.find(_matches(candidate, _)).toRight(s"candidate ${candidate.logicalIdentity.componentId.name} is not declared by the composition")
        _ <- Either.cond(!target.exactmembership || _matches_member(candidate, target), (), s"candidate ${candidate.logicalIdentity.componentId.name} does not match declared child membership")
      } yield ()
    }

  private def _candidate(candidate: ResolvedComponentResource): Either[String, Unit] =
    for {
      _ <- _identity(candidate.logicalIdentity, "candidate.logicalIdentity")
      _ <- _provenance_valid(candidate.provenance, "candidate.provenance")
      _ <- Either.cond(
        !candidate.activationAuthority && !candidate.operationAuthority && !candidate.mcpAuthority && !candidate.disclosureAuthority && !candidate.deploymentAuthority,
        (),
        s"candidate ${candidate.logicalIdentity.componentId.name} must not grant activation, operation, MCP, disclosure, or deployment authority"
      )
      _ <- Either.cond(
        candidate.logicalIdentity.childRole == candidate.provenance.childRole && candidate.logicalIdentity.logicalResource == candidate.provenance.logicalResource,
        (),
        s"candidate ${candidate.logicalIdentity.componentId.name} logical identity and provenance must agree"
      )
    } yield ()

  private def _select(
    target: Target,
    candidates: Vector[ResolvedComponentResource]
  ): (ResolvedComponentResource, Vector[ComponentResourceDiagnostic]) = {
    val source = ComponentResourceSourceKind.values.toVector.sortBy(_.precedence).flatMap { kind =>
      val available = candidates.filter(x => x.provenance.sourceKind == kind && x.availability != ComponentResourceAvailability.Missing && x.availability != ComponentResourceAvailability.Unavailable)
      Option.when(available.nonEmpty)(kind -> available.sortBy(x => (!_terminal(x.availability), _candidate_key(x))))
    }.headOption
    source match {
      case Some((kind, selected +: rest)) =>
        val conflict = Option.when(rest.nonEmpty)(ComponentResourceDiagnostic(
          ComponentResourceDiagnosticKind.Conflict, Some(target.identity.componentId), Some(kind), "multiple equally-precedent resource evidence entries were deterministically selected"
        )).toVector
        val diagnostic = ComponentResourceDiagnostic(
          if (_terminal(selected.availability)) ComponentResourceDiagnosticKind.Terminal else ComponentResourceDiagnosticKind.Resolved,
          Some(target.identity.componentId),
          Some(kind),
          if (_terminal(selected.availability)) s"terminal ${selected.availability} resource evidence retained" else "resource evidence resolved without activation"
        )
        (selected, diagnostic +: conflict)
      case None =>
        val unavailable = ComponentResourceSourceKind.values.toVector.sortBy(_.precedence).flatMap { kind =>
          val entries = candidates.filter(x => x.provenance.sourceKind == kind && x.availability == ComponentResourceAvailability.Unavailable)
          Option.when(entries.nonEmpty)(kind -> entries.sortBy(_candidate_key))
        }.headOption
        unavailable match {
          case Some((kind, selected +: rest)) =>
            val conflict = Option.when(rest.nonEmpty)(ComponentResourceDiagnostic(
              ComponentResourceDiagnosticKind.Conflict, Some(target.identity.componentId), Some(kind), "multiple equally-precedent unavailable resource evidence entries were deterministically selected"
            )).toVector
            val diagnostic = ComponentResourceDiagnostic(
              ComponentResourceDiagnosticKind.Unavailable,
              Some(target.identity.componentId),
              Some(kind),
              "unavailable resource evidence retained after lower-precedence fallback"
            )
            (selected, diagnostic +: conflict)
          case None =>
            val missing = ResolvedComponentResource(
              target.identity,
              target.fallback,
              ComponentResourceAvailability.Missing,
              ComponentResourceIntegrity.NotEvaluated,
              ComponentResourceAuthorization.NotEvaluated,
              false, false, false, false, false
            )
            (missing, Vector(ComponentResourceDiagnostic(ComponentResourceDiagnosticKind.Missing, Some(target.identity.componentId), None, "no qualifying resource evidence was supplied")))
        }
    }
  }

  private def _matches(candidate: ResolvedComponentResource, target: Target): Boolean =
    candidate.logicalIdentity.componentId == target.identity.componentId &&
      candidate.logicalIdentity.logicalRelease == target.identity.logicalRelease &&
      candidate.logicalIdentity.parentComponentId == target.identity.parentComponentId &&
      (!target.exactmembership || _matches_member(candidate, target))

  private def _matches_member(candidate: ResolvedComponentResource, target: Target): Boolean =
    candidate.logicalIdentity.childRole == target.identity.childRole &&
      candidate.logicalIdentity.logicalResource == target.identity.logicalResource &&
      candidate.provenance.externalDeploymentRequired == target.externaldeploymentrequired

  private def _candidate_key(candidate: ResolvedComponentResource): String = {
    val identity = candidate.logicalIdentity
    val provenance = candidate.provenance
    Vector(
      identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name).getOrElse(""), identity.childRole, identity.logicalResource,
      provenance.repository, provenance.artifactCoordinate, provenance.sha256, provenance.normalizedRelativePath, provenance.logicalSource,
      provenance.physicalSource, provenance.resolutionStep, provenance.access, provenance.license, candidate.availability.toString,
      candidate.integrity.toString, candidate.authorization.toString, provenance.externalDeploymentRequired.toString
    ).mkString("\u0000")
  }

  private def _provenance(
    car: ComponentSubcomponentCar,
    sourcekind: ComponentResourceSourceKind,
    role: String,
    resource: String,
    access: String,
    license: String,
    externaldeploymentrequired: Boolean
  ): ComponentResourceProvenance =
    ComponentResourceProvenance(
      sourcekind,
      car.artifact.repository,
      car.artifact.coordinate,
      car.artifact.sha256,
      car.artifact.physicalPath,
      car.provenance.logicalSource,
      car.provenance.physicalSource,
      _resolution_step(sourcekind),
      role,
      resource,
      access,
      license,
      externaldeploymentrequired
    )

  private def _identity(identity: ComponentResourceLogicalIdentity, context: String): Either[String, Unit] =
    for {
      _ <- _safe_text(identity.logicalRelease, s"$context.logicalRelease")
      _ <- _safe_role(identity.childRole, s"$context.childRole")
      _ <- _logical_resource(identity.logicalResource, s"$context.logicalResource")
      _ <- identity.parentComponentId.fold[Either[String, Unit]](Right(()))(id => Either.cond(id != identity.componentId, (), s"$context.parentComponentId must differ from componentId"))
    } yield ()

  private def _provenance_valid(provenance: ComponentResourceProvenance, context: String): Either[String, Unit] =
    for {
      _ <- _repository(provenance.repository, s"$context.repository")
      _ <- _artifact_coordinate(provenance.artifactCoordinate, s"$context.artifactCoordinate")
      _ <- _sha256(provenance.sha256, s"$context.sha256")
      _ <- _safe_path(provenance.normalizedRelativePath, s"$context.normalizedRelativePath")
      _ <- _source(provenance.logicalSource, s"$context.logicalSource")
      _ <- _source(provenance.physicalSource, s"$context.physicalSource")
      _ <- Either.cond(provenance.resolutionStep == _resolution_step(provenance.sourceKind), (), s"$context.resolutionStep does not match source kind")
      _ <- _safe_role(provenance.childRole, s"$context.childRole")
      _ <- _logical_resource(provenance.logicalResource, s"$context.logicalResource")
      _ <- _safe_text(provenance.access, s"$context.access")
      _ <- _safe_text(provenance.license, s"$context.license")
    } yield ()

  private def _car(car: ComponentSubcomponentCar, classification: String, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(car.classification == classification, (), s"$context.classification must be $classification")
      _ <- _artifact_coordinate(car.artifact.coordinate, s"$context.artifact.coordinate")
      _ <- _sha256(car.artifact.sha256, s"$context.artifact.sha256")
      _ <- _repository(car.artifact.repository, s"$context.artifact.repository")
      _ <- _safe_path(car.artifact.physicalPath, s"$context.artifact.physicalPath")
      _ <- _source(car.provenance.logicalSource, s"$context.provenance.logicalSource")
      _ <- _source(car.provenance.physicalSource, s"$context.provenance.physicalSource")
      _ <- _safe_path(car.provenance.physicalPath, s"$context.provenance.physicalPath")
    } yield ()

  private def _authority_is_false(authority: ComponentSubcomponentAuthority): Boolean =
    !authority.activation && !authority.operation && !authority.mcp && !authority.disclosure && !authority.deployment

  private def _resolution_step(sourcekind: ComponentResourceSourceKind): String =
    sourcekind match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary:0"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory:1"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car:2"
      case ComponentResourceSourceKind.LocalRepository => "local-repository:3"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache:4"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle:5"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository:6"
    }

  private def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(value.nonEmpty && value == value.trim && !value.exists(_.isControl), (), s"$context must be non-empty trimmed text")

  private def _safe_role(value: String, context: String): Either[String, Unit] =
    Either.cond(_role_pattern.matches(value), (), s"$context must be a safe role token")

  private def _logical_resource(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond(uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty), (), s"$context must be an absolute logical resource URI")
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute logical resource URI")
    }

  private def _repository(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond((uri.getScheme == "https" || uri.getScheme == "http") && Option(uri.getHost).exists(_.nonEmpty) && uri.getUserInfo == null, (), s"$context must be an HTTP(S) repository URI without user information")
    } catch {
      case NonFatal(_) => Left(s"$context must be an HTTP(S) repository URI")
    }

  private def _artifact_coordinate(value: String, context: String): Either[String, Unit] =
    Either.cond(_artifact_coordinate_pattern.matches(value), (), s"$context must be a three-part artifact coordinate")

  private def _sha256(value: String, context: String): Either[String, Unit] =
    Either.cond(_sha256_pattern.matches(value), (), s"$context must be a lowercase SHA-256 digest")

  private def _source(value: String, context: String): Either[String, Unit] =
    Either.cond(_source_pattern.matches(value), (), s"$context must be safe provenance evidence")

  private def _safe_path(value: String, context: String): Either[String, Unit] = {
    val segments = value.split("/", -1).toVector
    Either.cond(value.nonEmpty && value == value.trim && !value.startsWith("/") && !value.startsWith("\\") && !value.matches("^[A-Za-z]:.*") && !value.contains('\\') && segments.forall(x => x.nonEmpty && x != "." && x != ".."), (), s"$context must be a safe relative path")
  }
}
