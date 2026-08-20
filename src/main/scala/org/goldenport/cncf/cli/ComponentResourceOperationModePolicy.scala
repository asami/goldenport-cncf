package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cncf.component._
import org.goldenport.cncf.component.repository._
import org.goldenport.cncf.config.OperationMode

/*
 * Runtime-owned operation-mode composition policy.  This policy consumes the
 * resolver's descriptive values only; it does not discover, fetch, activate,
 * mount, deploy, or grant authority to any resource.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] final case class ComponentResourceOperationModePolicyResult(
  operationMode: OperationMode,
  resources: Vector[ResolvedComponentResource],
  readiness: Vector[ComponentResourceReadiness],
  platformHandoffs: Vector[ComponentResourcePlatformHandoff]
)

private[cli] final case class ComponentResourceReadiness(
  componentId: ComponentId,
  availability: ComponentResourceAvailability,
  required: Boolean
)

private[cli] final case class ComponentResourcePlatformHandoff(
  componentId: ComponentId,
  platform: String,
  mode: String,
  requiresExplicitPlatformAction: Boolean
)

private[cli] object ComponentResourceOperationModePolicy {
  def resolveC(
    mode: OperationMode,
    composition: ComponentSubcomponentComposition,
    resolved: ResolvedComponentResources,
    demoRemoteDocumentationEnabled: Boolean
  ): Consequence[ComponentResourceOperationModePolicyResult] =
    _resolve(mode, composition, resolved, demoRemoteDocumentationEnabled).fold(
      Consequence.argumentInvalid,
      Consequence.success
    )

  private final case class ExpectedResource(
    componentid: ComponentId,
    logicalrelease: String,
    parentcomponentid: Option[ComponentId],
    role: String,
    logicalresource: Option[String],
    required: Boolean,
    platform: Option[String],
    deploymentmode: Option[String],
    externaldeploymentrequired: Boolean
  )

  private def _resolve(
    mode: OperationMode,
    composition: ComponentSubcomponentComposition,
    resolved: ResolvedComponentResources,
    demoremotedocumentationenabled: Boolean
  ): Either[String, ComponentResourceOperationModePolicyResult] =
    for {
      _ <- _validate_composition(composition)
      expected <- _expected(composition)
      resources <- _match_resources(expected, resolved.resources)
      _ <- _validate_authority(resources)
      selected <- _select(mode, expected, resources, demoremotedocumentationenabled)
    } yield ComponentResourceOperationModePolicyResult(
      mode,
      selected,
      _readiness(expected, selected),
      _platform_handoffs(expected, selected)
    )

  private def _validate_composition(
    composition: ComponentSubcomponentComposition
  ): Either[String, Unit] =
    composition.members.find { member =>
      member.payload.authoritative ||
      member.payload.executable ||
      member.deployment.authority.activation ||
      member.deployment.authority.operation ||
      member.deployment.authority.mcp ||
      member.deployment.authority.disclosure ||
      member.deployment.authority.deployment
    } match {
      case Some(member) => Left(s"composition member ${member.componentId.name} contains an authority flag")
      case None => Right(())
    }

  private def _expected(
    composition: ComponentSubcomponentComposition
  ): Either[String, Vector[ExpectedResource]] = {
    val parent = ExpectedResource(
      composition.parent.componentId,
      composition.parent.logicalRelease,
      None,
      "parent",
      None,
      required = true,
      None,
      None,
      externaldeploymentrequired = false
    )
    val members = composition.members.map { member =>
      ExpectedResource(
        member.componentId,
        member.logicalRelease,
        Some(composition.parent.componentId),
        member.role,
        Some(member.logicalResource),
        member.required,
        Some(member.deployment.platform),
        Some(member.deployment.mode),
        member.deployment.requiresExplicitPlatformAction
      )
    }
    val expected = parent +: members
    Either.cond(
      expected.map(_.componentid).distinct.size == expected.size,
      expected,
      "composition contains duplicate parent or member ComponentId values"
    )
  }

  private def _match_resources(
    expected: Vector[ExpectedResource],
    resources: Vector[ResolvedComponentResource]
  ): Either[String, Vector[ResolvedComponentResource]] = {
    val duplicates = resources.groupBy(_.logicalIdentity.componentId).collect {
      case (componentid, entries) if entries.size > 1 => componentid
    }
    if (duplicates.nonEmpty)
      Left(s"resolved resources contain duplicate ComponentId values: ${duplicates.map(_.name).toVector.sorted.mkString(", ")}")
    else {
      val matched = expected.map { target =>
        resources.find(resource => _matches(target, resource))
      }
      if (matched.forall(_.nonEmpty) && matched.flatten.size == resources.size)
        Right(matched.flatten)
      else {
        val missing = expected.zip(matched).collect {
          case (target, None) => target.componentid.name
        }
        val extra = resources.filterNot(resource => expected.exists(_matches(_, resource))).map(_.logicalIdentity.componentId.name)
        Left(
          Vector(
            Option.when(missing.nonEmpty)(s"missing declared resources: ${missing.mkString(", ")}"),
            Option.when(extra.nonEmpty)(s"undeclared resources: ${extra.distinct.sorted.mkString(", ")}")
          ).flatten.mkString("; ")
        )
      }
    }
  }

  private def _matches(
    target: ExpectedResource,
    resource: ResolvedComponentResource
  ): Boolean = {
    val identity = resource.logicalIdentity
    val provenance = resource.provenance
    identity.componentId == target.componentid &&
      identity.logicalRelease == target.logicalrelease &&
      identity.parentComponentId == target.parentcomponentid &&
      identity.childRole == target.role &&
      target.logicalresource.forall(_ == identity.logicalResource) &&
      identity.childRole == provenance.childRole &&
      identity.logicalResource == provenance.logicalResource &&
      provenance.externalDeploymentRequired == target.externaldeploymentrequired
  }

  private def _validate_authority(
    resources: Vector[ResolvedComponentResource]
  ): Either[String, Unit] =
    resources.find { resource =>
      resource.activationAuthority ||
      resource.operationAuthority ||
      resource.mcpAuthority ||
      resource.disclosureAuthority ||
      resource.deploymentAuthority
    } match {
      case Some(resource) => Left(s"resource ${resource.logicalIdentity.componentId.name} contains an authority flag")
      case None => Right(())
    }

  private def _select(
    mode: OperationMode,
    expected: Vector[ExpectedResource],
    resources: Vector[ResolvedComponentResource],
    demoremotedocumentationenabled: Boolean
  ): Either[String, Vector[ResolvedComponentResource]] = {
    val candidates = mode match {
      case OperationMode.Production =>
        expected.zip(resources).collect {
          case (target, resource)
              if resource.provenance.sourceKind == ComponentResourceSourceKind.EmbeddedPrimary && target.role != "SourceCode" => resource
        }
      case _ => resources
    }
    val remote = candidates.filter(_.provenance.sourceKind == ComponentResourceSourceKind.RemoteRepository)
    val remoteallowed = mode match {
      case OperationMode.Develop => true
      case OperationMode.Test => remote.isEmpty
      case OperationMode.Demo =>
        remote.forall(resource =>
          resource.logicalIdentity.childRole == "Documentation" && demoremotedocumentationenabled
        )
      case OperationMode.Production => true
    }
    if (!remoteallowed)
      Left(s"${mode.name} policy does not permit implicit remote resource access")
    else Right(candidates)
  }

  private def _readiness(
    expected: Vector[ExpectedResource],
    resources: Vector[ResolvedComponentResource]
  ): Vector[ComponentResourceReadiness] = {
    val bycomponentid = resources.map(resource => resource.logicalIdentity.componentId -> resource).toMap
    expected.flatMap { target =>
      bycomponentid.get(target.componentid).collect {
        case resource
          if target.required && Set(
            ComponentResourceAvailability.Missing,
            ComponentResourceAvailability.Stale,
            ComponentResourceAvailability.Corrupt,
            ComponentResourceAvailability.Incompatible
          ).contains(resource.availability) =>
        ComponentResourceReadiness(resource.logicalIdentity.componentId, resource.availability, required = true)
      }
    }
  }

  private def _platform_handoffs(
    expected: Vector[ExpectedResource],
    resources: Vector[ResolvedComponentResource]
  ): Vector[ComponentResourcePlatformHandoff] = {
    val bycomponentid = resources.map(resource => resource.logicalIdentity.componentId -> resource).toMap
    expected.flatMap { target =>
      bycomponentid.get(target.componentid).collect {
        case resource
          if target.externaldeploymentrequired && resource.provenance.externalDeploymentRequired =>
        ComponentResourcePlatformHandoff(
          resource.logicalIdentity.componentId,
          target.platform.getOrElse(""),
          target.deploymentmode.getOrElse(""),
          requiresExplicitPlatformAction = true
        )
      }
    }
  }
}
