package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.{ComponentResourceAuthorization, ComponentResourceAvailability, ComponentResourceIntegrity, ComponentResourceLogicalIdentity, ComponentResourceSourceKind}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.WebDescriptor
import org.goldenport.cncf.knowledge.{ComponentKnowledgeHelpManifestIdentity, ComponentKnowledgeManifestConsumerSafeProvenanceEvidence, ComponentKnowledgeMediaType, ComponentKnowledgeResourceKind, ComponentKnowledgeResourceRole}
import org.goldenport.cncf.security.OperationAuthorization

/*
 * ADM-08A Component Admin surface contract. This is a pure value projection
 * over already validated Admin, Help, management, and Web Descriptor values.
 * It is deliberately not an ingress surface: it discovers nothing, reads no
 * resources, admits no operations, and grants no authority.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class ComponentAdminSurfacePage(
  name: String,
  label: String,
  description: String,
  permission: String,
  href: String,
  audience: WebDescriptor.AdminAudience
)

private[cncf] final case class ComponentAdminSurfaceDocumentationProvenance(
  sourceKind: ComponentResourceSourceKind,
  artifactCoordinate: String,
  logicalSource: String,
  resolutionStep: String,
  externalDeploymentRequired: Boolean,
  matchingDigest: String
)

private[cncf] final case class ComponentAdminSurfaceDocumentation(
  logicalIdentity: ComponentResourceLogicalIdentity,
  helpPath: String,
  kind: ComponentKnowledgeResourceKind,
  role: ComponentKnowledgeResourceRole,
  language: Option[String],
  mediaType: ComponentKnowledgeMediaType,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  digest: String,
  provenance: ComponentAdminSurfaceDocumentationProvenance
)

private[cncf] final case class ComponentAdminManagementDisplay(
  selector: String,
  visible: Boolean,
  available: Boolean,
  authorized: Boolean,
  eligible: Boolean
)

private[cncf] final case class ComponentAdminOrdinaryQueryDisplay(
  selector: String,
  visible: Boolean,
  management: Boolean = false
)

private[cncf] final case class ComponentAdminWebSurfaceDescriptor(
  identityView: ComponentAdminViewModel,
  pages: Vector[ComponentAdminSurfacePage],
  documentation: Vector[ComponentAdminSurfaceDocumentation],
  management: Vector[ComponentAdminManagementDisplay],
  ordinaryQueries: Vector[ComponentAdminOrdinaryQueryDisplay]
)

private[cncf] final case class ComponentAdminHttpSurfaceDescriptor(
  identityView: ComponentAdminViewModel,
  pages: Vector[ComponentAdminSurfacePage],
  documentation: Vector[ComponentAdminSurfaceDocumentation],
  management: Vector[ComponentAdminManagementDisplay],
  ordinaryQueries: Vector[ComponentAdminOrdinaryQueryDisplay]
)

private[cncf] final case class ComponentAdminCliSurfaceDescriptor(
  identityView: ComponentAdminViewModel,
  pages: Vector[ComponentAdminSurfacePage],
  documentation: Vector[ComponentAdminSurfaceDocumentation],
  management: Vector[ComponentAdminManagementDisplay],
  ordinaryQueries: Vector[ComponentAdminOrdinaryQueryDisplay]
)

private[cncf] final case class ComponentAdminMachineSurfaceDescriptor(
  identityView: ComponentAdminViewModel,
  json: String
)

private[cncf] final case class ComponentAdminSurfaceSecurityView(
  web: ComponentAdminWebSurfaceDescriptor,
  http: ComponentAdminHttpSurfaceDescriptor,
  cli: ComponentAdminCliSurfaceDescriptor,
  machine: ComponentAdminMachineSurfaceDescriptor
)

private[cncf] object ComponentAdminSurfaceSecurity {
  def projectC(
    identityView: ComponentAdminViewModel,
    documentationNavigation: ComponentAdminDocumentationNavigationView,
    managementCatalog: ComponentAdminAuthorizedManagementCatalog,
    webDescriptor: WebDescriptor
  )(using context: ExecutionContext): Consequence[ComponentAdminSurfaceSecurityView] =
    ComponentAdminViewModel.validateC(identityView).flatMap { identityview =>
      _project(identityview, documentationNavigation, managementCatalog, webDescriptor).fold(
        Consequence.argumentInvalid,
        Consequence.success
      )
    }

  private def _project(
    identityview: ComponentAdminViewModel,
    documentationnavigation: ComponentAdminDocumentationNavigationView,
    managementcatalog: ComponentAdminAuthorizedManagementCatalog,
    webdescriptor: WebDescriptor
  )(using context: ExecutionContext): Either[String, ComponentAdminSurfaceSecurityView] =
    for {
      navigation <- Option(documentationnavigation).toRight("Component Admin documentation navigation is required")
      _ <- Either.cond(navigation.identityview == identityview, (), "Component Admin documentation navigation must retain the exact validated identity view")
      catalog <- Option(managementcatalog).toRight("Component Admin authorized-management catalog is required")
      descriptor <- Option(webdescriptor).toRight("Component Admin Web Descriptor is required")
      documentation <- _documentation(navigation)
      values = _surface_values(identityview, catalog, descriptor, documentation)
      machinejson = ComponentAdminViewModelCodec.encode(identityview)
    } yield ComponentAdminSurfaceSecurityView(
      ComponentAdminWebSurfaceDescriptor(identityview, values.pages, values.documentation, values.management, values.ordinaryqueries),
      ComponentAdminHttpSurfaceDescriptor(identityview, values.pages, values.documentation, values.management, values.ordinaryqueries),
      ComponentAdminCliSurfaceDescriptor(identityview, values.pages, values.documentation, values.management, values.ordinaryqueries),
      ComponentAdminMachineSurfaceDescriptor(identityview, machinejson)
    )

  private final case class SurfaceValues(
    pages: Vector[ComponentAdminSurfacePage],
    documentation: Vector[ComponentAdminSurfaceDocumentation],
    management: Vector[ComponentAdminManagementDisplay],
    ordinaryqueries: Vector[ComponentAdminOrdinaryQueryDisplay]
  )

  private def _surface_values(
    identityview: ComponentAdminViewModel,
    catalog: ComponentAdminAuthorizedManagementCatalog,
    descriptor: WebDescriptor,
    documentation: Vector[ComponentAdminSurfaceDocumentation]
  )(using context: ExecutionContext): SurfaceValues = {
    val pages = descriptor.adminPagesFor(_component_path(identityview)).flatMap(_page)
    val targetmatches = _target_matches(identityview, catalog.target)
    val targetactive = Option(catalog.target).flatMap(value => Option(value.lifecycle)).exists(_.state == ComponentAdminLifecycleState.Active)
    val management = Option(catalog.managementInventory).getOrElse(Vector.empty).flatMap { registration =>
      Option(registration).map { value =>
        val authorized = OperationAuthorization.authorize(value.selector.value, value.authorization).isSuccess
        val available = value.availability == ComponentAdminManagementAvailability.Available
        val eligible = targetmatches && targetactive && value.visible && available && authorized
        ComponentAdminManagementDisplay(value.selector.value, value.visible, available, authorized, eligible)
      }
    }
    val ordinaryqueries = Option(catalog.ordinaryInventory).getOrElse(Vector.empty).flatMap { query =>
      Option(query).map(value => ComponentAdminOrdinaryQueryDisplay(value.selector.value, value.visible))
    }
    SurfaceValues(pages, documentation, management, ordinaryqueries)
  }

  private def _component_path(identityview: ComponentAdminViewModel): String =
    org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(identityview.componentClass.value.componentId.name)

  private def _page(value: WebDescriptor.AdminPage): Option[ComponentAdminSurfacePage] =
    value.canonicalHref.map { href =>
      ComponentAdminSurfacePage(
        value.normalizedName,
        value.effectiveLabel,
        value.description,
        value.effectivePermission,
        href,
        value.audience
      )
    }

  private def _documentation(
    navigation: ComponentAdminDocumentationNavigationView
  ): Either[String, Vector[ComponentAdminSurfaceDocumentation]] =
    for {
      value <- Option(navigation).toRight("Component Admin documentation navigation is required")
      help <- Option(value.helpnavigation).toRight("Component Admin Help human navigation is required")
      values <- Option(value.navigations).toRight("Component Admin documentation navigation entries are required")
      expectedidentity <- Option(value.identityview).flatMap(identityview =>
        Option(identityview.componentClass).map(_.value).flatMap(Option(_)).flatMap(componentclass =>
          Option(identityview.selectedLogicalRelease).map(_.value).flatMap(Option(_)).map(release =>
            ComponentKnowledgeHelpManifestIdentity(componentclass.componentId, release.release)
          )
        )
      ).toRight("Component Admin Help human navigation identity is required")
      _ <- Either.cond(help.manifestIdentity == expectedidentity, (), "Component Admin Help human navigation must match the Admin identity")
      result <- _sequence(values.map(value => _documentation_entry(value, help, expectedidentity)))
    } yield result

  private def _documentation_entry(
    navigation: ComponentAdminDocumentationResourceNavigation,
    help: org.goldenport.cncf.knowledge.ComponentKnowledgeHelpHumanNavigation,
    expectedidentity: ComponentKnowledgeHelpManifestIdentity
  ): Either[String, ComponentAdminSurfaceDocumentation] =
    for {
      value <- Option(navigation).toRight("Component Admin documentation entry is required")
      resource <- Option(value.resource).toRight("Component Admin documentation resource is required")
      logicalidentity <- Option(resource.logicalIdentity).toRight("Component Admin documentation resource logical identity is required")
      _ <- Either.cond(
        logicalidentity.componentId == expectedidentity.componentId && logicalidentity.logicalRelease == expectedidentity.logicalRelease,
        (),
        "Component Admin documentation resource identity must match the Help manifest identity"
      )
      route <- Option(value.route).toRight("Component Admin Help route is required")
      _ <- Either.cond(
        route.manifestIdentity == expectedidentity && route.logicalPath == resource.logicalPath,
        (),
        "Component Admin Help route must match the Help manifest identity and resource logical path"
      )
      path <- _help_path(route.path)
      resources <- Option(help.resources).toRight("Component Admin Help human navigation resources are required")
      _ <- Either.cond(!resources.exists(_ == null), (), "Component Admin Help human navigation resources must not contain null values")
      matching <- resources.filter(_.logicalPath == resource.logicalPath) match {
        case Vector(value) => Right(value)
        case Vector() => Left("Component Admin Help human navigation has no matching resource")
        case _ => Left("Component Admin Help human navigation has ambiguous matching resources")
      }
      _ <- Either.cond(
        matching.route == route,
        (),
        "Component Admin Help human navigation route must match the projected route"
      )
      provenance <- Option(resource.provenance).toRight("Component Admin documentation provenance is required")
    } yield ComponentAdminSurfaceDocumentation(
      resource.logicalIdentity,
      path,
      resource.kind,
      resource.role,
      resource.language,
      resource.mediaType,
      resource.availability,
      resource.integrity,
      resource.authorization,
      resource.sha256,
      _provenance(provenance)
    )

  private def _provenance(
    value: ComponentKnowledgeManifestConsumerSafeProvenanceEvidence
  ): ComponentAdminSurfaceDocumentationProvenance =
    ComponentAdminSurfaceDocumentationProvenance(
      value.sourceKind,
      value.artifactCoordinate,
      value.logicalSource,
      value.resolutionStep,
      value.externalDeploymentRequired,
      value.matchingDigest
    )

  private def _help_path(value: String): Either[String, String] =
    Option(value).filter { path =>
      val encoded = path.toLowerCase
      path.startsWith("/help/") &&
        !path.exists(_.isControl) &&
        !path.contains('\\') &&
        !path.contains('?') &&
        !path.contains('#') &&
        !encoded.contains("%2f") &&
        !encoded.contains("%5c") &&
        !encoded.contains("%2e") &&
        path.split("/", -1).drop(1).forall(segment => segment.nonEmpty && segment != "." && segment != "..")
    }.toRight("Component Admin Help route must be a canonical encoded Help path")

  private def _target_matches(
    identityview: ComponentAdminViewModel,
    target: ComponentAdminManagementTarget
  ): Boolean =
    Option(target).exists { value =>
      value.componentid == identityview.componentClass.value.componentId &&
        value.loadedinstanceid == identityview.selectedLoadedInstance.value.instanceId &&
        value.identitybinding != null &&
        value.identitybinding.selectedlogicalrelease == identityview.selectedLogicalRelease.value &&
        value.identitybinding.subsystemclass == identityview.subsystemClass.value &&
        value.identitybinding.subsysteminstance == identityview.subsystemInstance.value &&
        value.identitybinding.implicitcomponentsubsystem == identityview.implicitComponentSubsystem.value
    }

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (result, value) =>
      for {
        xs <- result
        x <- value
      } yield xs :+ x
    }
}
