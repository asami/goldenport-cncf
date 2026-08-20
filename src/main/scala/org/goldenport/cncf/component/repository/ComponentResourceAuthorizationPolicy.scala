package org.goldenport.cncf.component.repository

import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.component._

/*
 * Authorization and integrity policy for resolved component resources.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentResourceAccessDisposition:
  case Granted, Restricted, Rejected

enum ComponentResourceAccessDiagnosticKind:
  case Grant
  case Unauthorized
  case ParentMismatch
  case ReleaseMismatch
  case DigestMismatch
  case SignatureMismatch
  case UnsafeArchive
  case AmbiguousArchive
  case CorruptCache
  case AuthorityViolation
  case Unavailable

final case class ComponentResourceAccessDiagnostic(
  kind: ComponentResourceAccessDiagnosticKind,
  componentId: ComponentId,
  sourceKind: ComponentResourceSourceKind
)

final case class ComponentResourceAccessResult(
  disposition: ComponentResourceAccessDisposition,
  content: Option[Array[Byte]],
  diagnostic: ComponentResourceAccessDiagnostic
)

final case class ComponentResourceArchiveEntry(
  path: String,
  linkTarget: Option[String] = None
)

final case class ComponentResourceAccessRequest(
  resource: ResolvedComponentResource,
  content: Array[Byte],
  signature: String,
  signingKeyId: String,
  grantedRoles: Set[String],
  admittedRepositories: Set[String],
  trustedSigningKeyIds: Set[String],
  archiveEntries: Vector[ComponentResourceArchiveEntry],
  managedCacheVerified: Boolean
)

object ComponentResourceAuthorizationPolicy:
  def authorizeC(
    composition: ComponentSubcomponentComposition,
    request: ComponentResourceAccessRequest
  ): Consequence[ComponentResourceAccessResult] =
    val resource = request.resource

    def diagnostic(kind: ComponentResourceAccessDiagnosticKind) =
      ComponentResourceAccessDiagnostic(
        kind,
        resource.logicalIdentity.componentId,
        resource.provenance.sourceKind
      )

    def reject(kind: ComponentResourceAccessDiagnosticKind) =
      ComponentResourceAccessResult(
        ComponentResourceAccessDisposition.Rejected,
        None,
        diagnostic(kind)
      )

    def restrict =
      ComponentResourceAccessResult(
        ComponentResourceAccessDisposition.Restricted,
        None,
        diagnostic(ComponentResourceAccessDiagnosticKind.Unauthorized)
      )

    val result =
      if _hasAuthority(resource) then
        reject(ComponentResourceAccessDiagnosticKind.AuthorityViolation)
      else _archiveProblem(request.archiveEntries) match
        case Some(problem) => reject(problem)
        case None if resource.provenance.sourceKind == ComponentResourceSourceKind.ManagedCache && !request.managedCacheVerified =>
          reject(ComponentResourceAccessDiagnosticKind.CorruptCache)
        case None =>
          _authorizeComposition(composition, request) match
            case Some(result) => result
            case None =>
              ComponentResourceAccessResult(
                ComponentResourceAccessDisposition.Granted,
                Some(request.content.clone()),
                diagnostic(ComponentResourceAccessDiagnosticKind.Grant)
              )

    Consequence.success(result)

  private def _authorizeComposition(
    composition: ComponentSubcomponentComposition,
    request: ComponentResourceAccessRequest
  ): Option[ComponentResourceAccessResult] =
    val resource = request.resource
    val identity = resource.logicalIdentity
    val provenance = resource.provenance

    def diagnostic(kind: ComponentResourceAccessDiagnosticKind) =
      ComponentResourceAccessDiagnostic(kind, identity.componentId, provenance.sourceKind)

    def rejected(kind: ComponentResourceAccessDiagnosticKind) =
      Some(ComponentResourceAccessResult(
        ComponentResourceAccessDisposition.Rejected,
        None,
        diagnostic(kind)
      ))

    def restricted =
      Some(ComponentResourceAccessResult(
        ComponentResourceAccessDisposition.Restricted,
        None,
        diagnostic(ComponentResourceAccessDiagnosticKind.Unauthorized)
      ))

    val (artifact, roleandresourcematch) =
      if identity.componentId == composition.parent.componentId then
        if identity.parentComponentId.nonEmpty then
          return rejected(ComponentResourceAccessDiagnosticKind.ParentMismatch)
        else if identity.logicalRelease != composition.parent.logicalRelease then
          return rejected(ComponentResourceAccessDiagnosticKind.ReleaseMismatch)
        else
          (
            composition.parent.primaryCar.artifact,
            identity.childRole == provenance.childRole &&
              identity.logicalResource == provenance.logicalResource
          )
      else
        val matches = composition.members.filter(_.componentId == identity.componentId)
        if identity.parentComponentId != Some(composition.parent.componentId) || matches.size != 1 then
          return rejected(ComponentResourceAccessDiagnosticKind.ParentMismatch)
        val member = matches.head
        if identity.logicalRelease != member.logicalRelease then
          return rejected(ComponentResourceAccessDiagnosticKind.ReleaseMismatch)
        (
          member.subcomponentCar.artifact,
          identity.childRole == member.role &&
            identity.logicalResource == member.logicalResource &&
            provenance.childRole == identity.childRole &&
            provenance.logicalResource == identity.logicalResource
        )

    if resource.availability != ComponentResourceAvailability.Available then
      rejected(ComponentResourceAccessDiagnosticKind.Unavailable)
    else if resource.authorization != ComponentResourceAuthorization.Granted then
      restricted
    else if resource.integrity != ComponentResourceIntegrity.Verified then
      rejected(ComponentResourceAccessDiagnosticKind.DigestMismatch)
    else
      val carmatch =
        provenance.artifactCoordinate == artifact.coordinate &&
          provenance.repository == artifact.repository
      val admitted =
        request.grantedRoles.contains(identity.childRole) &&
          request.admittedRepositories.contains(provenance.repository)

      if !roleandresourcematch || !carmatch then
        restricted
      else if !admitted then
        restricted
      else
        val digest = _sha256(request.content)
        val digestmatch = digest == provenance.sha256 && digest == artifact.sha256
        val signaturematch =
          request.signature == artifact.signature &&
            request.trustedSigningKeyIds.contains(request.signingKeyId)

        if !digestmatch then
          rejected(ComponentResourceAccessDiagnosticKind.DigestMismatch)
        else if !signaturematch then
          rejected(ComponentResourceAccessDiagnosticKind.SignatureMismatch)
        else
          None

  private def _hasAuthority(resource: ResolvedComponentResource): Boolean =
    resource.activationAuthority ||
      resource.operationAuthority ||
      resource.mcpAuthority ||
      resource.disclosureAuthority ||
      resource.deploymentAuthority

  private def _archiveProblem(
    entries: Vector[ComponentResourceArchiveEntry]
  ): Option[ComponentResourceAccessDiagnosticKind] =
    if entries.exists(entry => entry.linkTarget.isDefined || _unsafePath(entry.path)) then
      Some(ComponentResourceAccessDiagnosticKind.UnsafeArchive)
    else
      val rawPaths = entries.map(_.path)
      val normalizedPaths = rawPaths.map(_normalizePath)
      if rawPaths.distinct.size != rawPaths.size || normalizedPaths.distinct.size != normalizedPaths.size then
        Some(ComponentResourceAccessDiagnosticKind.AmbiguousArchive)
      else
        None

  private def _unsafePath(path: String): Boolean =
    path.isEmpty ||
      path.startsWith("/") ||
      path.startsWith("\\") ||
      (path.length >= 2 && path.charAt(0).isLetter && path.charAt(1) == ':') ||
      path.contains('\\') ||
      path.exists(character => Character.isISOControl(character)) ||
      path.split("/", -1).contains("..")

  private def _normalizePath(path: String): String =
    path.split("/", -1).iterator.filter(segment => segment.nonEmpty && segment != ".").mkString("/")

  private def _sha256(content: Array[Byte]): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(content)
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
