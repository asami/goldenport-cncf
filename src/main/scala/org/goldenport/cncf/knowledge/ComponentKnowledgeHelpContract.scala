package org.goldenport.cncf.knowledge

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentSubcomponentComposition}
import org.goldenport.cncf.component.repository.{
  ComponentResourceAccessRequest,
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity
}
import org.goldenport.cncf.projection.{
  ComponentResourceConsumer,
  ResolvedComponentResourceConsumerAccess,
  ResolvedComponentResourcesConsumerProjection
}

/*
 * Public, value-only Component Help contract over already-resolved knowledge.
 * It describes deterministic Help/AI transport inputs but neither resolves nor
 * wires HTTP or CLI behavior. Resolver evidence stays private to accessC.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentKnowledgeHelpManifestIdentity(
  componentId: ComponentId,
  logicalRelease: String
)

final case class ComponentKnowledgeHelpManifestRoute(
  manifestIdentity: ComponentKnowledgeHelpManifestIdentity,
  prefix: String,
  path: String
)

final case class ComponentKnowledgeHelpResourceRoute(
  manifestIdentity: ComponentKnowledgeHelpManifestIdentity,
  logicalPath: String,
  path: String
)

final case class ComponentKnowledgeHelpDiscoveryDescriptor(
  relation: String,
  mediaType: String,
  manifestRoute: ComponentKnowledgeHelpManifestRoute
)

final case class ComponentKnowledgeHelpResourceNavigation(
  logicalPath: String,
  kind: ComponentKnowledgeResourceKind,
  role: ComponentKnowledgeResourceRole,
  language: Option[String],
  mediaType: ComponentKnowledgeMediaType,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  route: ComponentKnowledgeHelpResourceRoute
)

final case class ComponentKnowledgeHelpHttpRouteDescriptor(
  discovery: ComponentKnowledgeHelpDiscoveryDescriptor,
  resources: Vector[ComponentKnowledgeHelpResourceRoute]
)

final case class ComponentKnowledgeHelpCliInspectionDescriptor(
  command: Vector[String],
  manifestIdentity: ComponentKnowledgeHelpManifestIdentity,
  manifestPath: String,
  resourcePaths: Vector[String]
)

sealed trait ComponentKnowledgeHelpDevelopmentContextEvidence

object ComponentKnowledgeHelpDevelopmentContextEvidence {
  case object Ready extends ComponentKnowledgeHelpDevelopmentContextEvidence

  final case class Incomplete(
    code: String,
    issues: Vector[ComponentKnowledgeHelpDevelopmentResourceIssue]
  ) extends ComponentKnowledgeHelpDevelopmentContextEvidence
}

final case class ComponentKnowledgeHelpDevelopmentResourceIssue(
  logicalPath: String,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization
)

enum ComponentKnowledgeHelpFrameworkExpectation {
  case NoExpectation
  case ProfileAbsent(expected: FrameworkProductVersion)
  case Exact(expected: FrameworkProductVersion)
  case Mismatch(expected: FrameworkProductVersion, actual: FrameworkProductVersion)
}

enum ComponentKnowledgeHelpFrameworkSnapshotNavigation {
  case Absent
  case Present(resources: Vector[ComponentKnowledgeHelpFrameworkResourceNavigation])
}

final case class ComponentKnowledgeHelpFrameworkResourceNavigation(
  logicalPath: String,
  kind: ComponentKnowledgeResourceKind,
  role: ComponentKnowledgeResourceRole,
  mediaType: ComponentKnowledgeMediaType,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization
)

/*
 * Framework documentation is developer/toolchain navigation, not an operator
 * Component resource inventory. Its availability is descriptive as supplied.
 */
final case class ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation(
  frameworkPublication: Option[FrameworkPublicationContext],
  expectation: ComponentKnowledgeHelpFrameworkExpectation,
  snapshot: ComponentKnowledgeHelpFrameworkSnapshotNavigation
)

final case class ComponentKnowledgeHelpFrameworkEvidence(
  profile: Option[FrameworkDocumentationProfile] = None,
  expectedProductVersion: Option[FrameworkProductVersion] = None
)

final case class ComponentKnowledgeHelpHumanNavigation(
  manifestIdentity: ComponentKnowledgeHelpManifestIdentity,
  discovery: ComponentKnowledgeHelpDiscoveryDescriptor,
  resources: Vector[ComponentKnowledgeHelpResourceNavigation],
  developmentContext: ComponentKnowledgeHelpDevelopmentContextEvidence
)

final case class ComponentKnowledgeHelpDirectAiNavigation(
  manifestIdentity: ComponentKnowledgeHelpManifestIdentity,
  discovery: ComponentKnowledgeHelpDiscoveryDescriptor,
  resources: Vector[ComponentKnowledgeHelpResourceNavigation],
  manifestConsumerContract: ComponentKnowledgeManifestConsumerContract,
  developmentContext: ComponentKnowledgeHelpDevelopmentContextEvidence
)

final class ComponentKnowledgeHelpContract private (
  val humanNavigation: ComponentKnowledgeHelpHumanNavigation,
  val directAiNavigation: ComponentKnowledgeHelpDirectAiNavigation,
  val httpRoutes: ComponentKnowledgeHelpHttpRouteDescriptor,
  val cliInspection: ComponentKnowledgeHelpCliInspectionDescriptor,
  val frameworkDeveloperToolchainNavigation: Option[ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation],
  private val _knowledge: ResolvedComponentKnowledge,
  private val _resource_routes: Map[String, ComponentKnowledgeHelpResourceRoute]
) {
  /*
   * This is a membership-and-route gate only. The established Phase 58
   * projection remains the only byte, integrity, archive, and authorization
   * enforcement point; no resource content is read or transformed here.
   */
  def accessC(
    composition: ComponentSubcomponentComposition,
    route: ComponentKnowledgeHelpResourceRoute,
    request: ComponentResourceAccessRequest
  ): Consequence[ResolvedComponentResourceConsumerAccess] =
    _access_request(route, request).fold(
      Consequence.argumentInvalid,
      _ => ResolvedComponentResourcesConsumerProjection.access(
        composition,
        request,
        ComponentResourceConsumer.DirectAi
      )
    )

  private def _access_request(
    route: ComponentKnowledgeHelpResourceRoute,
    request: ComponentResourceAccessRequest
  ): Either[String, Unit] =
    _resource_routes.get(route.logicalPath) match {
      case Some(expectedroute) if expectedroute == route =>
        _knowledge.entries.find(_.entry.logicalPath == route.logicalPath) match {
          case Some(pair) if pair.resource == request.resource => Right(())
          case Some(_) => Left("Component Help access request resource is not the exact supplied resolved resource")
          case None => Left("Component Help access route is not an admitted resource route")
        }
      case _ => Left("Component Help access route is not canonical for the supplied manifest identity and logical path")
    }
}

object ComponentKnowledgeHelpContract {
  val DISCOVERY_RELATION = "describedby"
  val MANIFEST_MEDIA_TYPE = "application/vnd.cncf.component-knowledge+json;version=1"
  private val _product_token_pattern = "[a-z][a-z0-9-]*".r

  def createC(
    knowledge: ResolvedComponentKnowledge,
    developmentContext: ComponentDevelopmentContextOutcome,
    frameworkEvidence: ComponentKnowledgeHelpFrameworkEvidence = ComponentKnowledgeHelpFrameworkEvidence()
  ): Consequence[ComponentKnowledgeHelpContract] =
    _create(knowledge, developmentContext, frameworkEvidence).fold(Consequence.argumentInvalid, Consequence.success)

  private def _create(
    knowledge: ResolvedComponentKnowledge,
    developmentcontext: ComponentDevelopmentContextOutcome,
    frameworkevidence: ComponentKnowledgeHelpFrameworkEvidence
  ): Either[String, ComponentKnowledgeHelpContract] =
    for {
      _ <- ComponentKnowledgeManifest.validateC(knowledge.manifest).toOption
        .toRight("Component Help knowledge contains an invalid manifest")
      _ <- _validate_knowledge(knowledge)
      consumer <- ComponentKnowledgeManifestConsumerContract.fromManifestC(knowledge.manifest).toOption
        .toRight("Component Help cannot derive the existing manifest consumer contract")
      identity = ComponentKnowledgeHelpManifestIdentity(knowledge.manifest.componentId, knowledge.manifest.logicalRelease)
      manifestroute = _manifest_route(identity)
      routes = consumer.resources.map(value => _resource_route(identity, manifestroute.prefix, value.logicalPath))
      _ <- Either.cond(routes.map(_.path).distinct.size == routes.size, (), "Component Help resource routes must be distinct")
      resources = consumer.resources.zip(routes).map { case (value, route) => _resource_navigation(value, route) }
      development <- _development_context(knowledge, developmentcontext, resources)
      framework <- _framework_navigation(frameworkevidence)
      discovery = ComponentKnowledgeHelpDiscoveryDescriptor(DISCOVERY_RELATION, MANIFEST_MEDIA_TYPE, manifestroute)
      http = ComponentKnowledgeHelpHttpRouteDescriptor(discovery, routes)
      cli = _cli_inspection(identity, manifestroute, routes)
    } yield new ComponentKnowledgeHelpContract(
      humanNavigation = ComponentKnowledgeHelpHumanNavigation(identity, discovery, resources, development),
      directAiNavigation = ComponentKnowledgeHelpDirectAiNavigation(identity, discovery, resources, consumer, development),
      httpRoutes = http,
      cliInspection = cli,
      frameworkDeveloperToolchainNavigation = framework,
      _knowledge = knowledge,
      _resource_routes = routes.map(route => route.logicalPath -> route).toMap
    )

  private def _validate_knowledge(knowledge: ResolvedComponentKnowledge): Either[String, Unit] = {
    val entries = knowledge.manifest.resources
    val pairs = knowledge.entries
    for {
      _ <- Either.cond(entries.size == pairs.size, (), "Component Help knowledge entries must exactly match manifest resources")
      _ <- Either.cond(
        entries.forall(entry => pairs.count(_.entry == entry) == 1),
        (),
        "Component Help knowledge must retain exactly one supplied pair for every manifest resource"
      )
      _ <- _sequence(pairs.zipWithIndex.map { case (pair, index) =>
        _validate_pair(pair, s"Component Help knowledge.entries[$index]")
      })
    } yield ()
  }

  private def _validate_pair(pair: ResolvedComponentKnowledgeEntry, context: String): Either[String, Unit] = {
    val entry = pair.entry
    val resource = pair.resource
    val provenance = resource.provenance
    for {
      _ <- Either.cond(entry.binding.logicalIdentity == resource.logicalIdentity, (), s"$context logical identity must match its supplied resource")
      _ <- Either.cond(entry.sha256 == provenance.sha256, (), s"$context digest must match its supplied resource")
      _ <- Either.cond(entry.provenance.matchingDigest == provenance.sha256, (), s"$context matching digest must match its supplied resource")
      _ <- Either.cond(entry.provenance.sourceKind == provenance.sourceKind, (), s"$context source kind must match its supplied resource")
      _ <- Either.cond(entry.provenance.artifactCoordinate == provenance.artifactCoordinate, (), s"$context artifact coordinate must match its supplied resource")
      _ <- Either.cond(entry.provenance.logicalSource == provenance.logicalSource, (), s"$context logical source must match its supplied resource")
      _ <- Either.cond(entry.provenance.resolutionStep == provenance.resolutionStep, (), s"$context resolution step must match its supplied resource")
      _ <- Either.cond(entry.provenance.externalDeploymentRequired == provenance.externalDeploymentRequired, (), s"$context deployment requirement must match its supplied resource")
      _ <- Either.cond(entry.availability == resource.availability, (), s"$context availability must match its supplied resource")
      _ <- Either.cond(entry.integrity == resource.integrity, (), s"$context integrity must match its supplied resource")
      _ <- Either.cond(entry.authorization == resource.authorization, (), s"$context authorization must match its supplied resource")
    } yield ()
  }

  private def _development_context(
    knowledge: ResolvedComponentKnowledge,
    outcome: ComponentDevelopmentContextOutcome,
    resources: Vector[ComponentKnowledgeHelpResourceNavigation]
  ): Either[String, ComponentKnowledgeHelpDevelopmentContextEvidence] =
    outcome match {
      case ComponentDevelopmentContextOutcome.Ready(context) =>
        Either.cond(
          context.knowledge == knowledge,
          ComponentKnowledgeHelpDevelopmentContextEvidence.Ready,
          "Component Help Ready development context must retain the supplied resolved knowledge"
        )
      case ComponentDevelopmentContextOutcome.Incomplete(failure) =>
        val paths = resources.map(resource => resource.logicalPath -> resource).toMap
        for {
          _ <- Either.cond(
            failure.issues.map(_.logicalIdentity).distinct.size == failure.issues.size,
            (),
            "Component Help incomplete development context must not repeat a logical identity"
          )
          issues <- _sequence_values(failure.issues.map { issue =>
            knowledge.entries.find(_.resource.logicalIdentity == issue.logicalIdentity) match {
              case Some(pair) if pair == issue.pair =>
                paths.get(pair.entry.logicalPath).toRight("Component Help incomplete development context refers to an unprojected logical path").map { resource =>
                  ComponentKnowledgeHelpDevelopmentResourceIssue(
                    logicalPath = resource.logicalPath,
                    availability = issue.availability,
                    integrity = issue.integrity,
                    authorization = issue.authorization
                  )
                }
              case Some(_) => Left("Component Help incomplete development context does not retain the exact supplied pair")
              case None => Left("Component Help incomplete development context refers to unknown supplied knowledge")
            }
          })
        } yield ComponentKnowledgeHelpDevelopmentContextEvidence.Incomplete(failure.code, issues)
    }

  private def _framework_navigation(
    evidence: ComponentKnowledgeHelpFrameworkEvidence
  ): Either[String, Option[ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation]] =
    evidence.profile match {
      case None =>
        _expected_product_version(evidence.expectedProductVersion).map {
          case Some(expected) => Some(ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation(
            frameworkPublication = None,
            expectation = ComponentKnowledgeHelpFrameworkExpectation.ProfileAbsent(expected),
            snapshot = ComponentKnowledgeHelpFrameworkSnapshotNavigation.Absent
          ))
          case None => None
        }
      case Some(profile) =>
        for {
          _ <- FrameworkPublicationContext.validateC(profile.frameworkPublication).toOption
            .toRight("Component Help framework profile contains invalid publication evidence")
          expected <- _expected_product_version(evidence.expectedProductVersion)
          snapshot = _framework_snapshot(profile.snapshot)
          expectation = expected match {
            case None => ComponentKnowledgeHelpFrameworkExpectation.NoExpectation
            case Some(value) if value == profile.frameworkPublication.productVersion => ComponentKnowledgeHelpFrameworkExpectation.Exact(value)
            case Some(value) => ComponentKnowledgeHelpFrameworkExpectation.Mismatch(value, profile.frameworkPublication.productVersion)
          }
        } yield Some(ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation(Some(profile.frameworkPublication), expectation, snapshot))
    }

  private def _expected_product_version(
    expected: Option[FrameworkProductVersion]
  ): Either[String, Option[FrameworkProductVersion]] =
    expected match {
      case Some(value) =>
        Either.cond(
          Option(value.product).exists(_product_token_pattern.matches) &&
            Option(value.version).exists(text => text.nonEmpty && text == text.trim && !text.exists(_.isControl)),
          Some(value),
          "Component Help expected framework product/version must contain a safe product token and non-empty trimmed, control-free version evidence"
        )
      case None => Right(None)
    }

  private def _framework_snapshot(
    snapshot: FrameworkDocumentationSnapshotProfile
  ): ComponentKnowledgeHelpFrameworkSnapshotNavigation =
    snapshot match {
      case FrameworkDocumentationSnapshotProfile.Absent => ComponentKnowledgeHelpFrameworkSnapshotNavigation.Absent
      case FrameworkDocumentationSnapshotProfile.Present(documentation, _, _) =>
        ComponentKnowledgeHelpFrameworkSnapshotNavigation.Present(
          documentation.map { entry =>
            ComponentKnowledgeHelpFrameworkResourceNavigation(
              logicalPath = entry.logicalPath,
              kind = entry.kind,
              role = entry.role,
              mediaType = entry.mediaType,
              availability = entry.availability,
              integrity = entry.integrity,
              authorization = entry.authorization
            )
          }
        )
    }

  private def _manifest_route(identity: ComponentKnowledgeHelpManifestIdentity): ComponentKnowledgeHelpManifestRoute = {
    val prefix = s"/help/${_segment(identity.componentId.name)}/knowledge/${_segment(identity.logicalRelease)}"
    ComponentKnowledgeHelpManifestRoute(identity, prefix, s"$prefix/manifest.json")
  }

  private def _resource_route(
    identity: ComponentKnowledgeHelpManifestIdentity,
    prefix: String,
    logicalpath: String
  ): ComponentKnowledgeHelpResourceRoute =
    ComponentKnowledgeHelpResourceRoute(
      identity,
      logicalpath,
      s"$prefix/resources/${logicalpath.split("/", -1).map(_segment).mkString("/")}"
    )

  private def _resource_navigation(
    value: ComponentKnowledgeManifestConsumerResourceEvidence,
    route: ComponentKnowledgeHelpResourceRoute
  ): ComponentKnowledgeHelpResourceNavigation =
    ComponentKnowledgeHelpResourceNavigation(
      logicalPath = value.logicalPath,
      kind = value.kind,
      role = value.role,
      language = value.language,
      mediaType = value.mediaType,
      availability = value.availability,
      integrity = value.integrity,
      authorization = value.authorization,
      route = route
    )

  private def _cli_inspection(
    identity: ComponentKnowledgeHelpManifestIdentity,
    manifestroute: ComponentKnowledgeHelpManifestRoute,
    routes: Vector[ComponentKnowledgeHelpResourceRoute]
  ): ComponentKnowledgeHelpCliInspectionDescriptor =
    ComponentKnowledgeHelpCliInspectionDescriptor(
      command = Vector("component-knowledge", "inspect", "--component", identity.componentId.name, "--logical-release", identity.logicalRelease),
      manifestIdentity = identity,
      manifestPath = manifestroute.path,
      resourcePaths = routes.map(_.path)
    )

  private def _segment(value: String): String =
    value.getBytes(StandardCharsets.UTF_8).iterator.map { byte =>
      val code = byte & 0xff
      if (
        (code >= 'a' && code <= 'z') ||
        (code >= 'A' && code <= 'Z') ||
        (code >= '0' && code <= '9') ||
        code == '-' || code == '.' || code == '_'
      ) code.toChar.toString
      else f"%%$code%02X"
    }.mkString

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft(Right(()): Either[String, Unit]) { (z, value) => z.flatMap(_ => value) }

  private def _sequence_values[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, value) =>
      for {
        xs <- z
        x <- value
      } yield xs :+ x
    }
}
