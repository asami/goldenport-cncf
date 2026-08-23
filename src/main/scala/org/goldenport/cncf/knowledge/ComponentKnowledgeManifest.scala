package org.goldenport.cncf.knowledge

import java.net.URI
import java.util.Locale
import scala.util.control.NonFatal

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceSourceKind,
  ResolvedComponentResource,
  ResolvedComponentResources
}

/*
 * Stable v1, read-only Component knowledge resource contract. It binds only
 * caller-supplied Phase 58 evidence and does not discover, read, or authorize
 * any resource.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentKnowledgeResourceKind(val code: String) {
  case Documentation extends ComponentKnowledgeResourceKind("documentation")
  case SourceCode extends ComponentKnowledgeResourceKind("source-code")
  case Entity extends ComponentKnowledgeResourceKind("entity")
  case Powertype extends ComponentKnowledgeResourceKind("powertype")
  case StateMachine extends ComponentKnowledgeResourceKind("state-machine")
  case Value extends ComponentKnowledgeResourceKind("value")
  case Datatype extends ComponentKnowledgeResourceKind("datatype")
  case Relationship extends ComponentKnowledgeResourceKind("relationship")
  case ClassDiagram extends ComponentKnowledgeResourceKind("class-diagram")
  case StateDiagram extends ComponentKnowledgeResourceKind("state-diagram")
  case FrameworkDocumentation extends ComponentKnowledgeResourceKind("framework-documentation")
  case Directive extends ComponentKnowledgeResourceKind("directive")
  case SkillCatalog extends ComponentKnowledgeResourceKind("skill-catalog")
}

object ComponentKnowledgeResourceKind {
  def fromCode(code: String): Option[ComponentKnowledgeResourceKind] =
    ComponentKnowledgeResourceKind.values.find(_.code == code)
}

enum ComponentKnowledgeResourceRole(val code: String) {
  case Documentation extends ComponentKnowledgeResourceRole("documentation")
  case SourceCode extends ComponentKnowledgeResourceRole("source-code")
  case Model extends ComponentKnowledgeResourceRole("model")
  case Diagram extends ComponentKnowledgeResourceRole("diagram")
  case FrameworkDocumentation extends ComponentKnowledgeResourceRole("framework-documentation")
  case Directive extends ComponentKnowledgeResourceRole("directive")
  case SkillCatalog extends ComponentKnowledgeResourceRole("skill-catalog")
}

object ComponentKnowledgeResourceRole {
  def fromCode(code: String): Option[ComponentKnowledgeResourceRole] =
    ComponentKnowledgeResourceRole.values.find(_.code == code)
}

enum ComponentKnowledgeMediaType(val value: String) {
  case TextMarkdown extends ComponentKnowledgeMediaType("text/markdown")
  case TextXScala extends ComponentKnowledgeMediaType("text/x-scala")
  case ApplicationJson extends ComponentKnowledgeMediaType("application/json")
  case ApplicationYaml extends ComponentKnowledgeMediaType("application/yaml")
  case ImageSvgXml extends ComponentKnowledgeMediaType("image/svg+xml")
}

object ComponentKnowledgeMediaType {
  def fromValue(value: String): Option[ComponentKnowledgeMediaType] =
    ComponentKnowledgeMediaType.values.find(_.value == value)
}

enum ComponentKnowledgeAuthority(val code: String) {
  case Component extends ComponentKnowledgeAuthority("component")
  case Framework extends ComponentKnowledgeAuthority("framework")
  case External extends ComponentKnowledgeAuthority("external")
}

object ComponentKnowledgeAuthority {
  def fromCode(code: String): Option[ComponentKnowledgeAuthority] =
    ComponentKnowledgeAuthority.values.find(_.code == code)
}

enum ComponentKnowledgeStability(val code: String) {
  case Stable extends ComponentKnowledgeStability("stable")
  case Experimental extends ComponentKnowledgeStability("experimental")
}

object ComponentKnowledgeStability {
  def fromCode(code: String): Option[ComponentKnowledgeStability] =
    ComponentKnowledgeStability.values.find(_.code == code)
}

enum ComponentKnowledgeSource(val code: String) {
  case SuppliedPhase58 extends ComponentKnowledgeSource("supplied-phase58")
  case ComponentDeclared extends ComponentKnowledgeSource("component-declared")
  case Generated extends ComponentKnowledgeSource("generated")
}

object ComponentKnowledgeSource {
  def fromCode(code: String): Option[ComponentKnowledgeSource] =
    ComponentKnowledgeSource.values.find(_.code == code)
}

enum ComponentKnowledgeDisclosure(val code: String) {
  case MetadataOnly extends ComponentKnowledgeDisclosure("metadata-only")
  case ReferenceOnly extends ComponentKnowledgeDisclosure("reference-only")
}

object ComponentKnowledgeDisclosure {
  def fromCode(code: String): Option[ComponentKnowledgeDisclosure] =
    ComponentKnowledgeDisclosure.values.find(_.code == code)
}

final case class ComponentKnowledgeResourceBinding(
  logicalIdentity: ComponentResourceLogicalIdentity,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeSafeProvenance(
  sourceKind: ComponentResourceSourceKind,
  artifactCoordinate: String,
  logicalSource: String,
  resolutionStep: String,
  externalDeploymentRequired: Boolean,
  matchingDigest: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeMetadata(
  authority: ComponentKnowledgeAuthority,
  stability: ComponentKnowledgeStability,
  source: ComponentKnowledgeSource,
  license: String,
  disclosure: ComponentKnowledgeDisclosure,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeResourceEntry(
  binding: ComponentKnowledgeResourceBinding,
  logicalPath: String,
  kind: ComponentKnowledgeResourceKind,
  role: ComponentKnowledgeResourceRole,
  language: Option[String],
  mediaType: ComponentKnowledgeMediaType,
  size: Long,
  sha256: String,
  metadata: ComponentKnowledgeMetadata,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  provenance: ComponentKnowledgeSafeProvenance,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifest(
  componentId: ComponentId,
  logicalRelease: String,
  resources: Vector[ComponentKnowledgeResourceEntry],
  extensions: Map[String, Json] = Map.empty
)

object ComponentKnowledgeManifest {
  val SCHEMA = "cncf.component-knowledge.v1"

  def createC(
    suppliedResources: ResolvedComponentResources,
    candidate: ComponentKnowledgeManifest
  ): Consequence[ComponentKnowledgeManifest] =
    for {
      _ <- validateC(candidate)
      _ <- _validate_bindings(suppliedResources, candidate)
    } yield candidate

  def validateC(manifest: ComponentKnowledgeManifest): Consequence[ComponentKnowledgeManifest] =
    _validate(manifest).fold(Consequence.argumentInvalid, Consequence.success)

  private final case class ResourceKey(
    componentid: String,
    release: String,
    parentid: Option[String],
    childrole: String,
    logicalresource: String
  )

  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _artifact_coordinate_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _source_pattern = "[A-Za-z][A-Za-z0-9+._-]*:[A-Za-z0-9][A-Za-z0-9+._/:=-]*".r
  private val _role_pattern = "[A-Za-z][A-Za-z0-9._-]*".r
  private val _token_pattern = "[a-z][a-z0-9-]*".r
  private val _license_pattern = "[A-Za-z0-9][A-Za-z0-9.+-]*".r
  private val _protected_extension_keys = Set(
    "repository",
    "repositorylocation",
    "normalizedrelativepath",
    "hostpath",
    "physicalsource",
    "physicalpath",
    "path",
    "bytes",
    "content",
    "authorization",
    "credential",
    "credentials",
    "activation",
    "operation",
    "mcp",
    "deployment",
    "disclosureauthority",
    "activationauthority",
    "operationauthority",
    "mcpauthority",
    "deploymentauthority"
  )

  private def _validate(manifest: ComponentKnowledgeManifest): Either[String, ComponentKnowledgeManifest] = {
    val identities = manifest.resources.map(entry => _resource_key(entry.binding.logicalIdentity))
    val paths = manifest.resources.map(_.logicalPath)
    for {
      _ <- _component_id(manifest.componentId.name, "manifest.componentId")
      _ <- _safe_text(manifest.logicalRelease, "manifest.logicalRelease")
      _ <- Either.cond(manifest.resources.nonEmpty, (), "manifest.resources must not be empty")
      _ <- Either.cond(identities.distinct.size == identities.size, (), "manifest.resources must not repeat a Phase 58 logical identity")
      _ <- Either.cond(paths.distinct.size == paths.size, (), "manifest.resources must not repeat a canonical logical path")
      _ <- _validate_extensions(manifest.extensions, "manifest.extensions")
      _ <- _sequence(manifest.resources.zipWithIndex.map { case (entry, index) =>
        for {
          _ <- _resource(entry, s"manifest.resources[$index]")
          _ <- _manifest_binding(manifest, entry.binding.logicalIdentity, s"manifest.resources[$index].binding.logicalIdentity")
        } yield ()
      })
    } yield manifest
  }

  private def _validate_bindings(
    suppliedresources: ResolvedComponentResources,
    manifest: ComponentKnowledgeManifest
  ): Consequence[Unit] =
    _bindings(suppliedresources.resources, manifest.resources).fold(Consequence.argumentInvalid, _ => Consequence.unit)

  private def _bindings(
    suppliedresources: Vector[ResolvedComponentResource],
    resources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, Unit] =
    _sequence(resources.zipWithIndex.map { case (entry, index) =>
      val matches = suppliedresources.filter(_.logicalIdentity == entry.binding.logicalIdentity)
      matches match {
        case Vector(resource) => _binding(entry, resource, s"manifest.resources[$index]")
        case Vector() => Left(s"manifest.resources[$index] does not bind a supplied Phase 58 logical identity")
        case _ => Left(s"manifest.resources[$index] matches more than one supplied Phase 58 resource")
      }
    })

  private def _binding(
    entry: ComponentKnowledgeResourceEntry,
    resource: ResolvedComponentResource,
    context: String
  ): Either[String, Unit] = {
    val provenance = resource.provenance
    for {
      _ <- Either.cond(entry.sha256 == provenance.sha256, (), s"$context.sha256 must match supplied Phase 58 digest")
      _ <- Either.cond(entry.provenance.matchingDigest == provenance.sha256, (), s"$context.provenance.matchingDigest must match supplied Phase 58 digest")
      _ <- Either.cond(entry.provenance.sourceKind == provenance.sourceKind, (), s"$context.provenance.sourceKind must match supplied Phase 58 provenance")
      _ <- Either.cond(entry.provenance.artifactCoordinate == provenance.artifactCoordinate, (), s"$context.provenance.artifactCoordinate must match supplied Phase 58 provenance")
      _ <- Either.cond(entry.provenance.logicalSource == provenance.logicalSource, (), s"$context.provenance.logicalSource must match supplied Phase 58 provenance")
      _ <- Either.cond(entry.provenance.resolutionStep == provenance.resolutionStep, (), s"$context.provenance.resolutionStep must match supplied Phase 58 provenance")
      _ <- Either.cond(entry.provenance.externalDeploymentRequired == provenance.externalDeploymentRequired, (), s"$context.provenance.externalDeploymentRequired must match supplied Phase 58 provenance")
      _ <- Either.cond(entry.availability == resource.availability, (), s"$context.availability must match supplied Phase 58 state")
      _ <- Either.cond(entry.integrity == resource.integrity, (), s"$context.integrity must match supplied Phase 58 state")
      _ <- Either.cond(entry.authorization == resource.authorization, (), s"$context.authorization must match supplied Phase 58 state")
    } yield ()
  }

  private def _resource(entry: ComponentKnowledgeResourceEntry, context: String): Either[String, Unit] =
    for {
      _ <- _identity(entry.binding.logicalIdentity, s"$context.binding.logicalIdentity")
      _ <- _safe_path(entry.logicalPath, s"$context.logicalPath")
      _ <- entry.language.map(_language(_, s"$context.language")).getOrElse(Right(()))
      _ <- _sha256(entry.sha256, s"$context.sha256")
      _ <- Either.cond(entry.size >= 0, (), s"$context.size must be non-negative")
      _ <- _validate_extensions(entry.extensions, s"$context.extensions")
      _ <- _validate_extensions(entry.binding.extensions, s"$context.binding.extensions")
      _ <- _metadata(entry.metadata, s"$context.metadata")
      _ <- _provenance(entry.provenance, s"$context.provenance")
      _ <- Either.cond(entry.sha256 == entry.provenance.matchingDigest, (), s"$context.sha256 must equal matchingDigest")
      _ <- _kind_role_media(entry, context)
    } yield ()

  private def _identity(identity: ComponentResourceLogicalIdentity, context: String): Either[String, Unit] =
    for {
      _ <- _component_id(identity.componentId.name, s"$context.componentId")
      _ <- _safe_text(identity.logicalRelease, s"$context.logicalRelease")
      _ <- identity.parentComponentId.map(id => _component_id(id.name, s"$context.parentComponentId")).getOrElse(Right(()))
      _ <- Either.cond(identity.parentComponentId.forall(_ != identity.componentId), (), s"$context.parentComponentId must differ from componentId")
      _ <- _safe_role(identity.childRole, s"$context.childRole")
      _ <- _logical_resource(identity.logicalResource, s"$context.logicalResource")
    } yield ()

  private def _manifest_binding(
    manifest: ComponentKnowledgeManifest,
    identity: ComponentResourceLogicalIdentity,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(identity.logicalRelease == manifest.logicalRelease, (), s"$context.logicalRelease must equal manifest.logicalRelease")
      _ <- Either.cond(
        (identity.componentId == manifest.componentId && identity.parentComponentId.isEmpty) ||
          identity.parentComponentId.contains(manifest.componentId),
        (),
        s"$context must identify the manifest component or one of its declared children"
      )
    } yield ()

  private def _metadata(metadata: ComponentKnowledgeMetadata, context: String): Either[String, Unit] =
    for {
      _ <- _license(metadata.license, s"$context.license")
      _ <- _validate_extensions(metadata.extensions, s"$context.extensions")
    } yield ()

  private def _provenance(provenance: ComponentKnowledgeSafeProvenance, context: String): Either[String, Unit] =
    for {
      _ <- _artifact_coordinate(provenance.artifactCoordinate, s"$context.artifactCoordinate")
      _ <- _source(provenance.logicalSource, s"$context.logicalSource")
      _ <- _safe_text(provenance.resolutionStep, s"$context.resolutionStep")
      _ <- _sha256(provenance.matchingDigest, s"$context.matchingDigest")
      _ <- Either.cond(provenance.resolutionStep == _resolution_step(provenance.sourceKind), (), s"$context.resolutionStep does not match sourceKind")
      _ <- _validate_extensions(provenance.extensions, s"$context.extensions")
    } yield ()

  private def _kind_role_media(entry: ComponentKnowledgeResourceEntry, context: String): Either[String, Unit] = {
    val allowed = entry.kind match {
      case ComponentKnowledgeResourceKind.Documentation => entry.role == ComponentKnowledgeResourceRole.Documentation && entry.mediaType == ComponentKnowledgeMediaType.TextMarkdown
      case ComponentKnowledgeResourceKind.SourceCode => entry.role == ComponentKnowledgeResourceRole.SourceCode && entry.mediaType == ComponentKnowledgeMediaType.TextXScala
      case ComponentKnowledgeResourceKind.Entity | ComponentKnowledgeResourceKind.Powertype | ComponentKnowledgeResourceKind.StateMachine | ComponentKnowledgeResourceKind.Value | ComponentKnowledgeResourceKind.Datatype | ComponentKnowledgeResourceKind.Relationship => entry.role == ComponentKnowledgeResourceRole.Model && entry.mediaType == ComponentKnowledgeMediaType.ApplicationJson
      case ComponentKnowledgeResourceKind.ClassDiagram | ComponentKnowledgeResourceKind.StateDiagram => entry.role == ComponentKnowledgeResourceRole.Diagram && entry.mediaType == ComponentKnowledgeMediaType.ImageSvgXml
      case ComponentKnowledgeResourceKind.FrameworkDocumentation => entry.role == ComponentKnowledgeResourceRole.FrameworkDocumentation && entry.mediaType == ComponentKnowledgeMediaType.TextMarkdown
      case ComponentKnowledgeResourceKind.Directive => entry.role == ComponentKnowledgeResourceRole.Directive && entry.mediaType == ComponentKnowledgeMediaType.ApplicationYaml
      case ComponentKnowledgeResourceKind.SkillCatalog => entry.role == ComponentKnowledgeResourceRole.SkillCatalog && entry.mediaType == ComponentKnowledgeMediaType.ApplicationJson
    }
    Either.cond(allowed, (), s"$context kind, role, and mediaType are not an admitted v1 combination")
  }

  private def _component_id(value: String, context: String): Either[String, Unit] =
    ComponentId.parseC(value).toOption match {
      case Some(id) if id.name == value => Right(())
      case _ => Left(s"$context must be a canonical ComponentId")
    }

  private def _logical_resource(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond(uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty), (), s"$context must be an absolute logical resource URI")
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute logical resource URI")
    }

  private def _safe_path(value: String, context: String): Either[String, Unit] = {
    val segments = Option(value).map(_.split("/", -1).toVector).getOrElse(Vector.empty)
    Either.cond(
      Option(value).exists(text => text.nonEmpty && text == text.trim && !text.startsWith("/") && !text.startsWith("\\") && !text.matches("^[A-Za-z]:.*") && !text.contains('\\') && !text.exists(_.isControl)) &&
        segments.forall(segment => segment.nonEmpty && segment != "." && segment != ".." && !segment.exists(_.isControl)),
      (),
      s"$context must be a safe relative canonical path"
    )
  }

  private def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(text => text.nonEmpty && text == text.trim && !text.exists(_.isControl)), (), s"$context must be non-empty trimmed text")

  private def _safe_role(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_role_pattern.matches), (), s"$context must be a safe role token")

  private def _language(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_token_pattern.matches), (), s"$context must be a safe language token")

  private def _license(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_license_pattern.matches), (), s"$context must be an SPDX-like license token")

  private def _sha256(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_sha256_pattern.matches), (), s"$context must be a lowercase SHA-256 digest")

  private def _artifact_coordinate(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_artifact_coordinate_pattern.matches), (), s"$context must be a three-part artifact coordinate")

  private def _source(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_source_pattern.matches), (), s"$context must be safe logical provenance evidence")

  private def _validate_extensions(extensions: Map[String, Json], context: String): Either[String, Unit] =
    _sequence(extensions.toVector.map { case (key, value) => _validate_extension(key, value, context) })

  private def _validate_extension(key: String, value: Json, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(!_protected_extension_keys.contains(Option(key).map(_.toLowerCase(Locale.ROOT)).getOrElse("")), (), s"$context must not expose protected Phase 58 evidence: $key")
      _ <- _validate_extension_json(value, s"$context.$key")
    } yield ()

  private def _validate_extension_json(value: Json, context: String): Either[String, Unit] =
    value.arrayOrObject(
      Right(()),
      values => _sequence(values.toVector.map(_validate_extension_json(_, context))),
      obj => _validate_extensions(obj.toMap, context)
    )

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

  private def _resource_key(identity: ComponentResourceLogicalIdentity): ResourceKey =
    ResourceKey(identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name), identity.childRole, identity.logicalResource)

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft(Right(()): Either[String, Unit]) { (z, x) => z.flatMap(_ => x) }
}
