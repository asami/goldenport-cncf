package org.goldenport.cncf.knowledge

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.{
  ComponentResourceAuthorization,
  ComponentResourceAvailability,
  ComponentResourceIntegrity,
  ComponentResourceLogicalIdentity,
  ComponentResourceSourceKind
}

/*
 * Stable, read-only projection for later Component knowledge consumers. It is
 * a value-only contract: it contains no resource content, physical source,
 * repository, credential, approval, configuration, or operational authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentKnowledgeManifestConsumerMetadataEvidence(
  authority: ComponentKnowledgeAuthority,
  stability: ComponentKnowledgeStability,
  source: ComponentKnowledgeSource,
  license: String,
  disclosure: ComponentKnowledgeDisclosure,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerSafeProvenanceEvidence(
  sourceKind: ComponentResourceSourceKind,
  artifactCoordinate: String,
  logicalSource: String,
  resolutionStep: String,
  externalDeploymentRequired: Boolean,
  matchingDigest: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerResourceEvidence(
  logicalIdentity: ComponentResourceLogicalIdentity,
  logicalPath: String,
  kind: ComponentKnowledgeResourceKind,
  role: ComponentKnowledgeResourceRole,
  language: Option[String],
  mediaType: ComponentKnowledgeMediaType,
  size: Long,
  sha256: String,
  metadata: ComponentKnowledgeManifestConsumerMetadataEvidence,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  provenance: ComponentKnowledgeManifestConsumerSafeProvenanceEvidence,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence(
  componentId: ComponentId,
  logicalRelease: String,
  publicationSha256: String,
  availability: FrameworkPublicationReferenceAvailability,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence(
  product: String,
  version: String,
  canonicalUrl: String,
  publicationGeneration: String,
  documentId: String,
  sectionId: Option[String],
  sha256: String,
  availability: FrameworkPublicationReferenceAvailability,
  sourceIdentity: String,
  sourceSha256: String,
  documentationComponentSnapshot: Option[ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence],
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerModelReferenceEvidence(
  logicalIdentity: ComponentResourceLogicalIdentity,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence(
  sourceIdentity: ComponentResourceLogicalIdentity,
  sourceSha256: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerDiagramEvidence(
  logicalIdentity: ComponentResourceLogicalIdentity,
  generatedFrom: Vector[ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence],
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerModelEvidence(
  models: Vector[ComponentKnowledgeManifestConsumerModelReferenceEvidence],
  diagrams: Vector[ComponentKnowledgeManifestConsumerDiagramEvidence],
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerPublicDirectiveMetadata(
  logicalIdentity: ComponentResourceLogicalIdentity,
  directiveId: String,
  profileId: String,
  ruleId: String,
  origin: String,
  version: String,
  authority: PublicDirectiveAuthority,
  visibility: PublicMetadataVisibility,
  sourceSha256: String,
  redaction: PublicDirectiveRedaction,
  guideReference: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerSkillCatalogMetadata(
  logicalIdentity: ComponentResourceLogicalIdentity,
  catalogId: String,
  owner: String,
  purpose: String,
  trigger: String,
  requirements: Vector[String],
  permissions: Vector[String],
  sideEffects: Vector[String],
  mcpRequirements: Vector[String],
  installationReference: String,
  visibility: PublicMetadataVisibility,
  version: String,
  sourceSha256: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentKnowledgeManifestConsumerContract(
  componentId: ComponentId,
  logicalRelease: String,
  resources: Vector[ComponentKnowledgeManifestConsumerResourceEvidence],
  frameworkPublication: Option[ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence] = None,
  modelResources: Option[ComponentKnowledgeManifestConsumerModelEvidence] = None,
  publicDirective: Option[ComponentKnowledgeManifestConsumerPublicDirectiveMetadata] = None,
  skillCatalog: Option[ComponentKnowledgeManifestConsumerSkillCatalogMetadata] = None,
  extensions: Map[String, Json] = Map.empty
)

object ComponentKnowledgeManifestConsumerContract {
  val SCHEMA = "cncf.component-knowledge-consumer.v1"

  def fromManifestC(manifest: ComponentKnowledgeManifest): Consequence[ComponentKnowledgeManifestConsumerContract] =
    for {
      _ <- ComponentKnowledgeManifest.validateC(manifest)
      contract <- validateC(_project(manifest))
    } yield contract

  def validateC(contract: ComponentKnowledgeManifestConsumerContract): Consequence[ComponentKnowledgeManifestConsumerContract] =
    _validate(contract).fold(Consequence.argumentInvalid, Consequence.success)

  private def _project(manifest: ComponentKnowledgeManifest): ComponentKnowledgeManifestConsumerContract =
    ComponentKnowledgeManifestConsumerContract(
      componentId = manifest.componentId,
      logicalRelease = manifest.logicalRelease,
      resources = manifest.resources.sortBy(_resource_order).map(_resource),
      frameworkPublication = manifest.frameworkPublication.map(_framework_publication),
      modelResources = manifest.modelResources.map(_model_resources),
      publicDirective = manifest.publicDirective.map(_public_directive),
      skillCatalog = manifest.skillCatalog.map(_skill_catalog)
    )

  private def _resource(value: ComponentKnowledgeResourceEntry): ComponentKnowledgeManifestConsumerResourceEvidence =
    ComponentKnowledgeManifestConsumerResourceEvidence(
      logicalIdentity = value.binding.logicalIdentity,
      logicalPath = value.logicalPath,
      kind = value.kind,
      role = value.role,
      language = value.language,
      mediaType = value.mediaType,
      size = value.size,
      sha256 = value.sha256,
      metadata = ComponentKnowledgeManifestConsumerMetadataEvidence(
        authority = value.metadata.authority,
        stability = value.metadata.stability,
        source = value.metadata.source,
        license = value.metadata.license,
        disclosure = value.metadata.disclosure
      ),
      availability = value.availability,
      integrity = value.integrity,
      authorization = value.authorization,
      provenance = ComponentKnowledgeManifestConsumerSafeProvenanceEvidence(
        sourceKind = value.provenance.sourceKind,
        artifactCoordinate = value.provenance.artifactCoordinate,
        logicalSource = value.provenance.logicalSource,
        resolutionStep = value.provenance.resolutionStep,
        externalDeploymentRequired = value.provenance.externalDeploymentRequired,
        matchingDigest = value.provenance.matchingDigest
      )
    )

  private def _framework_publication(value: FrameworkPublicationContext): ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence =
    ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence(
      product = value.productVersion.product,
      version = value.productVersion.version,
      canonicalUrl = value.canonicalUrl,
      publicationGeneration = value.publicationGeneration,
      documentId = value.documentId,
      sectionId = value.sectionId,
      sha256 = value.sha256,
      availability = value.availability,
      sourceIdentity = value.generatedFrom.sourceIdentity,
      sourceSha256 = value.generatedFrom.sourceSha256,
      documentationComponentSnapshot = value.documentationComponentSnapshot.map(_framework_snapshot)
    )

  private def _framework_snapshot(value: FrameworkDocumentationComponentSnapshot): ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence =
    ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence(
      componentId = value.componentId,
      logicalRelease = value.logicalRelease,
      publicationSha256 = value.publicationSha256,
      availability = value.availability
    )

  private def _model_resources(value: PortableModelResourceContext): ComponentKnowledgeManifestConsumerModelEvidence =
    ComponentKnowledgeManifestConsumerModelEvidence(
      models = value.models.sortBy(model => _identity_order(model.entry.binding.logicalIdentity)).map(model => ComponentKnowledgeManifestConsumerModelReferenceEvidence(model.entry.binding.logicalIdentity)),
      diagrams = value.diagrams.sortBy(diagram => _identity_order(diagram.entry.binding.logicalIdentity)).map { diagram =>
        ComponentKnowledgeManifestConsumerDiagramEvidence(
          logicalIdentity = diagram.entry.binding.logicalIdentity,
          generatedFrom = diagram.generatedFrom.sortBy(source => _identity_order(source.sourceIdentity)).map { source =>
            ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence(source.sourceIdentity, source.sourceSha256)
          }
        )
      }
    )

  private def _public_directive(value: PublicDirectiveProjection): ComponentKnowledgeManifestConsumerPublicDirectiveMetadata =
    ComponentKnowledgeManifestConsumerPublicDirectiveMetadata(
      logicalIdentity = value.entry.binding.logicalIdentity,
      directiveId = value.directiveId,
      profileId = value.profileId,
      ruleId = value.ruleId,
      origin = value.origin,
      version = value.version,
      authority = value.authority,
      visibility = value.visibility,
      sourceSha256 = value.sourceSha256,
      redaction = value.redaction,
      guideReference = value.guideReference
    )

  private def _skill_catalog(value: PublicSkillCatalog): ComponentKnowledgeManifestConsumerSkillCatalogMetadata =
    ComponentKnowledgeManifestConsumerSkillCatalogMetadata(
      logicalIdentity = value.entry.binding.logicalIdentity,
      catalogId = value.catalogId,
      owner = value.owner,
      purpose = value.purpose,
      trigger = value.trigger,
      requirements = value.requirements,
      permissions = value.permissions,
      sideEffects = value.sideEffects,
      mcpRequirements = value.mcpRequirements,
      installationReference = value.installationReference,
      visibility = value.visibility,
      version = value.version,
      sourceSha256 = value.sourceSha256
    )

  private def _validate(contract: ComponentKnowledgeManifestConsumerContract): Either[String, ComponentKnowledgeManifestConsumerContract] = {
    val resources = contract.resources.map(_resource_entry)
    val manifest = ComponentKnowledgeManifest(contract.componentId, contract.logicalRelease, resources)
    for {
      _ <- ComponentKnowledgeManifest.validateC(manifest).toOption.toRight("consumer contract contains invalid Component manifest resource evidence")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(contract.extensions, "consumer.extensions")
      _ <- _sequence(contract.resources.zipWithIndex.map { case (value, index) => _resource_extensions(value, s"consumer.resources[$index]") })
      _ <- contract.frameworkPublication.map(_framework_publication_validation(_, "consumer.frameworkPublication")).getOrElse(Right(()))
      _ <- contract.modelResources.map(_model_resources_validation(_, resources, "consumer.modelResources")).getOrElse(Right(()))
      _ <- contract.publicDirective.map(_public_directive_validation(_, resources, "consumer.publicDirective")).getOrElse(Right(()))
      _ <- contract.skillCatalog.map(_skill_catalog_validation(_, resources, "consumer.skillCatalog")).getOrElse(Right(()))
    } yield contract
  }

  private def _resource_extensions(value: ComponentKnowledgeManifestConsumerResourceEvidence, context: String): Either[String, Unit] =
    for {
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(value.extensions, s"$context.extensions")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(value.metadata.extensions, s"$context.metadata.extensions")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(value.provenance.extensions, s"$context.provenance.extensions")
    } yield ()

  private def _framework_publication_validation(
    value: ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence,
    context: String
  ): Either[String, Unit] = {
    val snapshot = value.documentationComponentSnapshot.map { item =>
      FrameworkDocumentationComponentSnapshot(item.componentId, item.logicalRelease, item.publicationSha256, item.availability)
    }
    val framework = FrameworkPublicationContext(
      productVersion = FrameworkProductVersion(value.product, value.version),
      canonicalUrl = value.canonicalUrl,
      publicationGeneration = value.publicationGeneration,
      documentId = value.documentId,
      sectionId = value.sectionId,
      sha256 = value.sha256,
      availability = value.availability,
      generatedFrom = FrameworkPublicationGeneratedFrom(value.sourceIdentity, value.sourceSha256),
      documentationComponentSnapshot = snapshot
    )
    for {
      _ <- FrameworkPublicationContext.validateC(framework).toOption.toRight(s"$context violates framework publication validation")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(value.extensions, s"$context.extensions")
      _ <- value.documentationComponentSnapshot.map(item => PublicDirectiveSkillCatalogContextValidation._validate_extensions(item.extensions, s"$context.documentationComponentSnapshot.extensions")).getOrElse(Right(()))
    } yield ()
  }

  private def _model_resources_validation(
    value: ComponentKnowledgeManifestConsumerModelEvidence,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] = {
    val models = value.models.map { item =>
      _resource_for(item.logicalIdentity, resources, s"$context.models").map(entry => PortableModelResource(entry))
    }
    val diagrams = value.diagrams.map { item =>
      for {
        entry <- _resource_for(item.logicalIdentity, resources, s"$context.diagrams")
      } yield PortableDiagramResource(
        entry,
        item.generatedFrom.map(source => PortableDiagramGeneratedFrom(source.sourceIdentity, source.sourceSha256))
      )
    }
    for {
      modelvalues <- _sequence(models)
      diagramvalues <- _sequence(diagrams)
      _ <- PortableModelResourceContext.validateC(PortableModelResourceContext(modelvalues, diagramvalues), resources).toOption.toRight(s"$context violates model evidence validation")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(value.extensions, s"$context.extensions")
      _ <- _sequence(value.models.zipWithIndex.map { case (item, index) => PublicDirectiveSkillCatalogContextValidation._validate_extensions(item.extensions, s"$context.models[$index].extensions") })
      _ <- _sequence(value.diagrams.zipWithIndex.map { case (item, index) =>
        for {
          _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(item.extensions, s"$context.diagrams[$index].extensions")
          _ <- _sequence(item.generatedFrom.zipWithIndex.map { case (source, sourceindex) => PublicDirectiveSkillCatalogContextValidation._validate_extensions(source.extensions, s"$context.diagrams[$index].generatedFrom[$sourceindex].extensions") })
        } yield ()
      })
    } yield ()
  }

  private def _public_directive_validation(
    value: ComponentKnowledgeManifestConsumerPublicDirectiveMetadata,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    for {
      entry <- _resource_for(value.logicalIdentity, resources, s"$context.logicalIdentity")
      projection = PublicDirectiveProjection(entry, value.directiveId, value.profileId, value.ruleId, value.origin, value.version, value.authority, value.visibility, value.sourceSha256, value.redaction, value.guideReference, value.extensions)
      _ <- PublicDirectiveProjection.validateC(projection, resources).toOption.toRight(s"$context violates public Directive validation")
    } yield ()

  private def _skill_catalog_validation(
    value: ComponentKnowledgeManifestConsumerSkillCatalogMetadata,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    for {
      entry <- _resource_for(value.logicalIdentity, resources, s"$context.logicalIdentity")
      catalog = PublicSkillCatalog(entry, value.catalogId, value.owner, value.purpose, value.trigger, value.requirements, value.permissions, value.sideEffects, value.mcpRequirements, value.installationReference, value.visibility, value.version, value.sourceSha256, value.extensions)
      _ <- PublicSkillCatalog.validateC(catalog, resources).toOption.toRight(s"$context violates public Skill Catalog validation")
    } yield ()

  private def _resource_entry(value: ComponentKnowledgeManifestConsumerResourceEvidence): ComponentKnowledgeResourceEntry =
    ComponentKnowledgeResourceEntry(
      binding = ComponentKnowledgeResourceBinding(value.logicalIdentity),
      logicalPath = value.logicalPath,
      kind = value.kind,
      role = value.role,
      language = value.language,
      mediaType = value.mediaType,
      size = value.size,
      sha256 = value.sha256,
      metadata = ComponentKnowledgeMetadata(value.metadata.authority, value.metadata.stability, value.metadata.source, value.metadata.license, value.metadata.disclosure, value.metadata.extensions),
      availability = value.availability,
      integrity = value.integrity,
      authorization = value.authorization,
      provenance = ComponentKnowledgeSafeProvenance(value.provenance.sourceKind, value.provenance.artifactCoordinate, value.provenance.logicalSource, value.provenance.resolutionStep, value.provenance.externalDeploymentRequired, value.provenance.matchingDigest, value.provenance.extensions),
      extensions = value.extensions
    )

  private def _resource_for(
    identity: ComponentResourceLogicalIdentity,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, ComponentKnowledgeResourceEntry] =
    resources.filter(_.binding.logicalIdentity == identity) match {
      case Vector(entry) => Right(entry)
      case Vector() => Left(s"$context must identify exactly one consumer resource evidence entry")
      case _ => Left(s"$context matches more than one consumer resource evidence entry")
    }

  private def _resource_order(value: ComponentKnowledgeResourceEntry): (String, String, String, String, String, String) = {
    val identity = value.binding.logicalIdentity
    (identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name).getOrElse(""), identity.childRole, identity.logicalResource, value.logicalPath)
  }

  private def _identity_order(value: ComponentResourceLogicalIdentity): (String, String, String, String, String) =
    (value.componentId.name, value.logicalRelease, value.parentComponentId.map(_.name).getOrElse(""), value.childRole, value.logicalResource)

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }
}
