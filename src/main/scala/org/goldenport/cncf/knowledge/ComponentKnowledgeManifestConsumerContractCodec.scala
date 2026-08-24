package org.goldenport.cncf.knowledge

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
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
 * Deterministic codec for the stable, value-only Component knowledge consumer
 * contract. It retains only recursively safe unknown fields and performs no
 * resource-content or runtime action.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentKnowledgeManifestConsumerContractCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)

  def decodeC(json: String): Consequence[ComponentKnowledgeManifestConsumerContract] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(contract: ComponentKnowledgeManifestConsumerContract): String = {
    val encoded = _contract_json(contract).noSpaces
    decodeC(encoded).toOption match {
      case Some(_) => encoded
      case None => throw new IllegalArgumentException("Component knowledge manifest consumer contract failed codec self-validation")
    }
  }

  private def _decode(text: String): Either[String, ComponentKnowledgeManifestConsumerContract] =
    try {
      for {
        json <- _strict_json_parser.parse(text).left.map(error => s"Invalid Component knowledge consumer contract JSON: ${error.message}")
        root <- _object(json, "consumer")
        schema <- _string(root, "schema", "consumer")
        _ <- Either.cond(schema == ComponentKnowledgeManifestConsumerContract.SCHEMA, (), s"Unsupported Component knowledge consumer contract schema: $schema")
        componentid <- _component_id(root, "componentId", "consumer")
        release <- _string(root, "logicalRelease", "consumer")
        resourcesjson <- _field(root, "resources", "consumer")
        resources <- _array(resourcesjson, "consumer.resources").flatMap(_resources)
        framework <- _optional_framework_publication(root, "frameworkPublication", "consumer")
        models <- _optional_model_resources(root, "modelResources", "consumer")
        directive <- _optional_public_directive(root, "publicDirective", "consumer")
        catalog <- _optional_skill_catalog(root, "skillCatalog", "consumer")
        contract = ComponentKnowledgeManifestConsumerContract(
          componentId = componentid,
          logicalRelease = release,
          resources = resources,
          frameworkPublication = framework,
          modelResources = models,
          publicDirective = directive,
          skillCatalog = catalog,
          extensions = _extensions(root, Set("schema", "componentId", "logicalRelease", "frameworkPublication", "modelResources", "publicDirective", "skillCatalog", "resources"))
        )
        _ <- ComponentKnowledgeManifestConsumerContract.validateC(contract).toOption.toRight("Component knowledge consumer contract violates v1 validation")
      } yield contract
    } catch {
      case NonFatal(error) => Left(s"Invalid Component knowledge consumer contract: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _resources(values: Vector[Json]): Either[String, Vector[ComponentKnowledgeManifestConsumerResourceEvidence]] =
    _sequence(values.zipWithIndex.map { case (value, index) => _resource(value, s"consumer.resources[$index]") })

  private def _resource(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerResourceEvidence] =
    for {
      obj <- _object(json, context)
      identityjson <- _field(obj, "logicalIdentity", context)
      identity <- _identity(identityjson, s"$context.logicalIdentity")
      path <- _string(obj, "logicalPath", context)
      kindvalue <- _string(obj, "kind", context)
      kind <- _kind(kindvalue, s"$context.kind")
      rolevalue <- _string(obj, "role", context)
      role <- _role(rolevalue, s"$context.role")
      language <- _optional_string(obj, "language", context)
      mediavalue <- _string(obj, "mediaType", context)
      media <- _media_type(mediavalue, s"$context.mediaType")
      size <- _non_negative_long(obj, "size", context)
      digest <- _string(obj, "sha256", context)
      metadatajson <- _field(obj, "metadata", context)
      metadata <- _metadata(metadatajson, s"$context.metadata")
      availabilityvalue <- _string(obj, "availability", context)
      availability <- _availability(availabilityvalue, s"$context.availability")
      integrityvalue <- _string(obj, "integrity", context)
      integrity <- _integrity(integrityvalue, s"$context.integrity")
      authorizationvalue <- _string(obj, "authorization", context)
      authorization <- _authorization(authorizationvalue, s"$context.authorization")
      provenancejson <- _field(obj, "provenance", context)
      provenance <- _provenance(provenancejson, s"$context.provenance")
    } yield ComponentKnowledgeManifestConsumerResourceEvidence(
      logicalIdentity = identity,
      logicalPath = path,
      kind = kind,
      role = role,
      language = language,
      mediaType = media,
      size = size,
      sha256 = digest,
      metadata = metadata,
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      provenance = provenance,
      extensions = _extensions(obj, Set("logicalIdentity", "logicalPath", "kind", "role", "language", "mediaType", "size", "sha256", "metadata", "availability", "integrity", "authorization", "provenance"))
    )

  private def _metadata(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerMetadataEvidence] =
    for {
      obj <- _object(json, context)
      authorityvalue <- _string(obj, "authority", context)
      authority <- _authority(authorityvalue, s"$context.authority")
      stabilityvalue <- _string(obj, "stability", context)
      stability <- _stability(stabilityvalue, s"$context.stability")
      sourcevalue <- _string(obj, "source", context)
      source <- _source(sourcevalue, s"$context.source")
      license <- _string(obj, "license", context)
      disclosurevalue <- _string(obj, "disclosure", context)
      disclosure <- _disclosure(disclosurevalue, s"$context.disclosure")
    } yield ComponentKnowledgeManifestConsumerMetadataEvidence(authority, stability, source, license, disclosure, _extensions(obj, Set("authority", "stability", "source", "license", "disclosure")))

  private def _provenance(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerSafeProvenanceEvidence] =
    for {
      obj <- _object(json, context)
      sourcekindvalue <- _string(obj, "sourceKind", context)
      sourcekind <- _source_kind(sourcekindvalue, s"$context.sourceKind")
      coordinate <- _string(obj, "artifactCoordinate", context)
      logicalsource <- _string(obj, "logicalSource", context)
      step <- _string(obj, "resolutionStep", context)
      external <- _boolean(obj, "externalDeploymentRequired", context)
      digest <- _string(obj, "matchingDigest", context)
    } yield ComponentKnowledgeManifestConsumerSafeProvenanceEvidence(sourcekind, coordinate, logicalsource, step, external, digest, _extensions(obj, Set("sourceKind", "artifactCoordinate", "logicalSource", "resolutionStep", "externalDeploymentRequired", "matchingDigest")))

  private def _optional_framework_publication(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _framework_publication(value, s"$context.$field").map(Some(_))
    }

  private def _framework_publication(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence] =
    for {
      obj <- _object(json, context)
      product <- _string(obj, "product", context)
      version <- _string(obj, "version", context)
      canonicalurl <- _string(obj, "canonicalUrl", context)
      generation <- _string(obj, "publicationGeneration", context)
      documentid <- _string(obj, "documentId", context)
      sectionid <- _optional_string(obj, "sectionId", context)
      digest <- _string(obj, "sha256", context)
      availabilityvalue <- _string(obj, "availability", context)
      availability <- _framework_availability(availabilityvalue, s"$context.availability")
      sourceidentity <- _string(obj, "sourceIdentity", context)
      sourcesha256 <- _string(obj, "sourceSha256", context)
      snapshot <- _optional_framework_snapshot(obj, "documentationComponentSnapshot", context)
    } yield ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence(
      product, version, canonicalurl, generation, documentid, sectionid, digest, availability, sourceidentity, sourcesha256, snapshot,
      _extensions(obj, Set("product", "version", "canonicalUrl", "publicationGeneration", "documentId", "sectionId", "sha256", "availability", "sourceIdentity", "sourceSha256", "documentationComponentSnapshot"))
    )

  private def _optional_framework_snapshot(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _framework_snapshot(value, s"$context.$field").map(Some(_))
    }

  private def _framework_snapshot(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence] =
    for {
      obj <- _object(json, context)
      componentid <- _component_id(obj, "componentId", context)
      release <- _string(obj, "logicalRelease", context)
      digest <- _string(obj, "publicationSha256", context)
      availabilityvalue <- _string(obj, "availability", context)
      availability <- _framework_availability(availabilityvalue, s"$context.availability")
    } yield ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence(componentid, release, digest, availability, _extensions(obj, Set("componentId", "logicalRelease", "publicationSha256", "availability")))

  private def _optional_model_resources(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentKnowledgeManifestConsumerModelEvidence]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _model_resources(value, s"$context.$field").map(Some(_))
    }

  private def _model_resources(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerModelEvidence] =
    for {
      obj <- _object(json, context)
      modelsjson <- _field(obj, "models", context)
      models <- _array(modelsjson, s"$context.models").flatMap(_models(_, s"$context.models"))
      diagramsjson <- _field(obj, "diagrams", context)
      diagrams <- _array(diagramsjson, s"$context.diagrams").flatMap(_diagrams(_, s"$context.diagrams"))
    } yield ComponentKnowledgeManifestConsumerModelEvidence(models, diagrams, _extensions(obj, Set("models", "diagrams")))

  private def _models(values: Vector[Json], context: String): Either[String, Vector[ComponentKnowledgeManifestConsumerModelReferenceEvidence]] =
    _sequence(values.zipWithIndex.map { case (value, index) =>
      for {
        obj <- _object(value, s"$context[$index]")
        identityjson <- _field(obj, "logicalIdentity", s"$context[$index]")
        identity <- _identity(identityjson, s"$context[$index].logicalIdentity")
      } yield ComponentKnowledgeManifestConsumerModelReferenceEvidence(identity, _extensions(obj, Set("logicalIdentity")))
    })

  private def _diagrams(values: Vector[Json], context: String): Either[String, Vector[ComponentKnowledgeManifestConsumerDiagramEvidence]] =
    _sequence(values.zipWithIndex.map { case (value, index) =>
      for {
        obj <- _object(value, s"$context[$index]")
        identityjson <- _field(obj, "logicalIdentity", s"$context[$index]")
        identity <- _identity(identityjson, s"$context[$index].logicalIdentity")
        sourcesjson <- _field(obj, "generatedFrom", s"$context[$index]")
        sources <- _array(sourcesjson, s"$context[$index].generatedFrom").flatMap(_generated_from(_, s"$context[$index].generatedFrom"))
      } yield ComponentKnowledgeManifestConsumerDiagramEvidence(identity, sources, _extensions(obj, Set("logicalIdentity", "generatedFrom")))
    })

  private def _generated_from(values: Vector[Json], context: String): Either[String, Vector[ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence]] =
    _sequence(values.zipWithIndex.map { case (value, index) =>
      for {
        obj <- _object(value, s"$context[$index]")
        identityjson <- _field(obj, "sourceIdentity", s"$context[$index]")
        identity <- _identity(identityjson, s"$context[$index].sourceIdentity")
        digest <- _string(obj, "sourceSha256", s"$context[$index]")
      } yield ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence(identity, digest, _extensions(obj, Set("sourceIdentity", "sourceSha256")))
    })

  private def _optional_public_directive(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentKnowledgeManifestConsumerPublicDirectiveMetadata]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _public_directive(value, s"$context.$field").map(Some(_))
    }

  private def _public_directive(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerPublicDirectiveMetadata] =
    for {
      obj <- _object(json, context)
      identityjson <- _field(obj, "logicalIdentity", context)
      identity <- _identity(identityjson, s"$context.logicalIdentity")
      directiveid <- _string(obj, "directiveId", context)
      profileid <- _string(obj, "profileId", context)
      ruleid <- _string(obj, "ruleId", context)
      origin <- _string(obj, "origin", context)
      version <- _string(obj, "version", context)
      authorityvalue <- _string(obj, "authority", context)
      authority <- _directive_authority(authorityvalue, s"$context.authority")
      visibilityvalue <- _string(obj, "visibility", context)
      visibility <- _visibility(visibilityvalue, s"$context.visibility")
      digest <- _string(obj, "sourceSha256", context)
      redactionvalue <- _string(obj, "redaction", context)
      redaction <- _directive_redaction(redactionvalue, s"$context.redaction")
      guide <- _string(obj, "guideReference", context)
    } yield ComponentKnowledgeManifestConsumerPublicDirectiveMetadata(
      identity, directiveid, profileid, ruleid, origin, version, authority, visibility, digest, redaction, guide,
      _extensions(obj, Set("logicalIdentity", "directiveId", "profileId", "ruleId", "origin", "version", "authority", "visibility", "sourceSha256", "redaction", "guideReference"))
    )

  private def _optional_skill_catalog(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentKnowledgeManifestConsumerSkillCatalogMetadata]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _skill_catalog(value, s"$context.$field").map(Some(_))
    }

  private def _skill_catalog(json: Json, context: String): Either[String, ComponentKnowledgeManifestConsumerSkillCatalogMetadata] =
    for {
      obj <- _object(json, context)
      identityjson <- _field(obj, "logicalIdentity", context)
      identity <- _identity(identityjson, s"$context.logicalIdentity")
      catalogid <- _string(obj, "catalogId", context)
      owner <- _string(obj, "owner", context)
      purpose <- _string(obj, "purpose", context)
      trigger <- _string(obj, "trigger", context)
      requirementsjson <- _field(obj, "requirements", context)
      requirements <- _string_vector(requirementsjson, s"$context.requirements")
      permissionsjson <- _field(obj, "permissions", context)
      permissions <- _string_vector(permissionsjson, s"$context.permissions")
      sideeffectsjson <- _field(obj, "sideEffects", context)
      sideeffects <- _string_vector(sideeffectsjson, s"$context.sideEffects")
      mcprequirementsjson <- _field(obj, "mcpRequirements", context)
      mcprequirements <- _string_vector(mcprequirementsjson, s"$context.mcpRequirements")
      installationreference <- _string(obj, "installationReference", context)
      visibilityvalue <- _string(obj, "visibility", context)
      visibility <- _visibility(visibilityvalue, s"$context.visibility")
      version <- _string(obj, "version", context)
      digest <- _string(obj, "sourceSha256", context)
    } yield ComponentKnowledgeManifestConsumerSkillCatalogMetadata(
      identity, catalogid, owner, purpose, trigger, requirements, permissions, sideeffects, mcprequirements, installationreference, visibility, version, digest,
      _extensions(obj, Set("logicalIdentity", "catalogId", "owner", "purpose", "trigger", "requirements", "permissions", "sideEffects", "mcpRequirements", "installationReference", "visibility", "version", "sourceSha256"))
    )

  private def _contract_json(contract: ComponentKnowledgeManifestConsumerContract): Json = {
    val framework = contract.frameworkPublication.map(value => Vector("frameworkPublication" -> _framework_publication_json(value))).getOrElse(Vector.empty)
    val models = contract.modelResources.map(value => Vector("modelResources" -> _model_resources_json(value))).getOrElse(Vector.empty)
    val directive = contract.publicDirective.map(value => Vector("publicDirective" -> _public_directive_json(value))).getOrElse(Vector.empty)
    val catalog = contract.skillCatalog.map(value => Vector("skillCatalog" -> _skill_catalog_json(value))).getOrElse(Vector.empty)
    _json_object(
      Vector(
        "schema" -> Json.fromString(ComponentKnowledgeManifestConsumerContract.SCHEMA),
        "componentId" -> Json.fromString(contract.componentId.name),
        "logicalRelease" -> Json.fromString(contract.logicalRelease)
      ) ++ framework ++ models ++ directive ++ catalog ++ Vector(
        "resources" -> Json.arr(contract.resources.sortBy(_resource_order).map(_resource_json)*)
      ),
      contract.extensions
    )
  }

  private def _resource_json(value: ComponentKnowledgeManifestConsumerResourceEvidence): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(value.logicalIdentity),
        "logicalPath" -> Json.fromString(value.logicalPath),
        "kind" -> Json.fromString(value.kind.code),
        "role" -> Json.fromString(value.role.code),
        "language" -> value.language.map(Json.fromString).getOrElse(Json.Null),
        "mediaType" -> Json.fromString(value.mediaType.value),
        "size" -> Json.fromLong(value.size),
        "sha256" -> Json.fromString(value.sha256),
        "metadata" -> _metadata_json(value.metadata),
        "availability" -> Json.fromString(_availability_code(value.availability)),
        "integrity" -> Json.fromString(_integrity_code(value.integrity)),
        "authorization" -> Json.fromString(_authorization_code(value.authorization)),
        "provenance" -> _provenance_json(value.provenance)
      ),
      value.extensions
    )

  private def _metadata_json(value: ComponentKnowledgeManifestConsumerMetadataEvidence): Json =
    _json_object(
      Vector(
        "authority" -> Json.fromString(value.authority.code),
        "stability" -> Json.fromString(value.stability.code),
        "source" -> Json.fromString(value.source.code),
        "license" -> Json.fromString(value.license),
        "disclosure" -> Json.fromString(value.disclosure.code)
      ),
      value.extensions
    )

  private def _provenance_json(value: ComponentKnowledgeManifestConsumerSafeProvenanceEvidence): Json =
    _json_object(
      Vector(
        "sourceKind" -> Json.fromString(_source_kind_code(value.sourceKind)),
        "artifactCoordinate" -> Json.fromString(value.artifactCoordinate),
        "logicalSource" -> Json.fromString(value.logicalSource),
        "resolutionStep" -> Json.fromString(value.resolutionStep),
        "externalDeploymentRequired" -> Json.fromBoolean(value.externalDeploymentRequired),
        "matchingDigest" -> Json.fromString(value.matchingDigest)
      ),
      value.extensions
    )

  private def _framework_publication_json(value: ComponentKnowledgeManifestConsumerFrameworkPublicationEvidence): Json =
    _json_object(
      Vector(
        "product" -> Json.fromString(value.product),
        "version" -> Json.fromString(value.version),
        "canonicalUrl" -> Json.fromString(value.canonicalUrl),
        "publicationGeneration" -> Json.fromString(value.publicationGeneration),
        "documentId" -> Json.fromString(value.documentId),
        "sectionId" -> value.sectionId.map(Json.fromString).getOrElse(Json.Null),
        "sha256" -> Json.fromString(value.sha256),
        "availability" -> Json.fromString(value.availability.code),
        "sourceIdentity" -> Json.fromString(value.sourceIdentity),
        "sourceSha256" -> Json.fromString(value.sourceSha256),
        "documentationComponentSnapshot" -> value.documentationComponentSnapshot.map(_framework_snapshot_json).getOrElse(Json.Null)
      ),
      value.extensions
    )

  private def _framework_snapshot_json(value: ComponentKnowledgeManifestConsumerFrameworkSnapshotEvidence): Json =
    _json_object(
      Vector(
        "componentId" -> Json.fromString(value.componentId.name),
        "logicalRelease" -> Json.fromString(value.logicalRelease),
        "publicationSha256" -> Json.fromString(value.publicationSha256),
        "availability" -> Json.fromString(value.availability.code)
      ),
      value.extensions
    )

  private def _model_resources_json(value: ComponentKnowledgeManifestConsumerModelEvidence): Json =
    _json_object(
      Vector(
        "models" -> Json.arr(value.models.sortBy(item => _identity_order(item.logicalIdentity)).map(_model_json)*),
        "diagrams" -> Json.arr(value.diagrams.sortBy(item => _identity_order(item.logicalIdentity)).map(_diagram_json)*)
      ),
      value.extensions
    )

  private def _model_json(value: ComponentKnowledgeManifestConsumerModelReferenceEvidence): Json =
    _json_object(Vector("logicalIdentity" -> _identity_json(value.logicalIdentity)), value.extensions)

  private def _diagram_json(value: ComponentKnowledgeManifestConsumerDiagramEvidence): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(value.logicalIdentity),
        "generatedFrom" -> Json.arr(value.generatedFrom.sortBy(source => _identity_order(source.sourceIdentity)).map(_generated_from_json)*)
      ),
      value.extensions
    )

  private def _generated_from_json(value: ComponentKnowledgeManifestConsumerDiagramGeneratedFromEvidence): Json =
    _json_object(
      Vector(
        "sourceIdentity" -> _identity_json(value.sourceIdentity),
        "sourceSha256" -> Json.fromString(value.sourceSha256)
      ),
      value.extensions
    )

  private def _public_directive_json(value: ComponentKnowledgeManifestConsumerPublicDirectiveMetadata): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(value.logicalIdentity),
        "directiveId" -> Json.fromString(value.directiveId),
        "profileId" -> Json.fromString(value.profileId),
        "ruleId" -> Json.fromString(value.ruleId),
        "origin" -> Json.fromString(value.origin),
        "version" -> Json.fromString(value.version),
        "authority" -> Json.fromString(value.authority.code),
        "visibility" -> Json.fromString(value.visibility.code),
        "sourceSha256" -> Json.fromString(value.sourceSha256),
        "redaction" -> Json.fromString(value.redaction.code),
        "guideReference" -> Json.fromString(value.guideReference)
      ),
      value.extensions
    )

  private def _skill_catalog_json(value: ComponentKnowledgeManifestConsumerSkillCatalogMetadata): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(value.logicalIdentity),
        "catalogId" -> Json.fromString(value.catalogId),
        "owner" -> Json.fromString(value.owner),
        "purpose" -> Json.fromString(value.purpose),
        "trigger" -> Json.fromString(value.trigger),
        "requirements" -> Json.arr(value.requirements.map(Json.fromString)*),
        "permissions" -> Json.arr(value.permissions.map(Json.fromString)*),
        "sideEffects" -> Json.arr(value.sideEffects.map(Json.fromString)*),
        "mcpRequirements" -> Json.arr(value.mcpRequirements.map(Json.fromString)*),
        "installationReference" -> Json.fromString(value.installationReference),
        "visibility" -> Json.fromString(value.visibility.code),
        "version" -> Json.fromString(value.version),
        "sourceSha256" -> Json.fromString(value.sourceSha256)
      ),
      value.extensions
    )

  private def _identity(json: Json, context: String): Either[String, ComponentResourceLogicalIdentity] =
    for {
      obj <- _object(json, context)
      _ <- Either.cond(obj.keys.toSet == Set("componentId", "logicalRelease", "parentComponentId", "childRole", "logicalResource"), (), s"$context must contain only a canonical logical identity")
      componentid <- _component_id(obj, "componentId", context)
      release <- _string(obj, "logicalRelease", context)
      parentid <- _optional_component_id(obj, "parentComponentId", context)
      childrole <- _string(obj, "childRole", context)
      logicalresource <- _string(obj, "logicalResource", context)
    } yield ComponentResourceLogicalIdentity(componentid, release, parentid, childrole, logicalresource)

  private def _identity_json(value: ComponentResourceLogicalIdentity): Json =
    Json.obj(
      "componentId" -> Json.fromString(value.componentId.name),
      "logicalRelease" -> Json.fromString(value.logicalRelease),
      "parentComponentId" -> value.parentComponentId.map(id => Json.fromString(id.name)).getOrElse(Json.Null),
      "childRole" -> Json.fromString(value.childRole),
      "logicalResource" -> Json.fromString(value.logicalResource)
    )

  private def _kind(value: String, context: String): Either[String, ComponentKnowledgeResourceKind] =
    ComponentKnowledgeResourceKind.fromCode(value).toRight(s"$context is not an admitted consumer resource kind")

  private def _role(value: String, context: String): Either[String, ComponentKnowledgeResourceRole] =
    ComponentKnowledgeResourceRole.fromCode(value).toRight(s"$context is not an admitted consumer resource role")

  private def _media_type(value: String, context: String): Either[String, ComponentKnowledgeMediaType] =
    ComponentKnowledgeMediaType.fromValue(value).toRight(s"$context is not an admitted consumer media type")

  private def _authority(value: String, context: String): Either[String, ComponentKnowledgeAuthority] =
    ComponentKnowledgeAuthority.fromCode(value).toRight(s"$context is not an admitted consumer authority")

  private def _stability(value: String, context: String): Either[String, ComponentKnowledgeStability] =
    ComponentKnowledgeStability.fromCode(value).toRight(s"$context is not an admitted consumer stability")

  private def _source(value: String, context: String): Either[String, ComponentKnowledgeSource] =
    ComponentKnowledgeSource.fromCode(value).toRight(s"$context is not an admitted consumer source")

  private def _disclosure(value: String, context: String): Either[String, ComponentKnowledgeDisclosure] =
    ComponentKnowledgeDisclosure.fromCode(value).toRight(s"$context is not an admitted consumer disclosure")

  private def _availability(value: String, context: String): Either[String, ComponentResourceAvailability] =
    value match {
      case "available" => Right(ComponentResourceAvailability.Available)
      case "restricted" => Right(ComponentResourceAvailability.Restricted)
      case "unavailable" => Right(ComponentResourceAvailability.Unavailable)
      case "missing" => Right(ComponentResourceAvailability.Missing)
      case "stale" => Right(ComponentResourceAvailability.Stale)
      case "incompatible" => Right(ComponentResourceAvailability.Incompatible)
      case "corrupt" => Right(ComponentResourceAvailability.Corrupt)
      case _ => Left(s"$context is not an admitted consumer availability state")
    }

  private def _integrity(value: String, context: String): Either[String, ComponentResourceIntegrity] =
    value match {
      case "not-evaluated" => Right(ComponentResourceIntegrity.NotEvaluated)
      case "verified" => Right(ComponentResourceIntegrity.Verified)
      case "unverified" => Right(ComponentResourceIntegrity.Unverified)
      case _ => Left(s"$context is not an admitted consumer integrity state")
    }

  private def _authorization(value: String, context: String): Either[String, ComponentResourceAuthorization] =
    value match {
      case "not-evaluated" => Right(ComponentResourceAuthorization.NotEvaluated)
      case "granted" => Right(ComponentResourceAuthorization.Granted)
      case "denied" => Right(ComponentResourceAuthorization.Denied)
      case _ => Left(s"$context is not an admitted consumer authorization state")
    }

  private def _source_kind(value: String, context: String): Either[String, ComponentResourceSourceKind] =
    value match {
      case "embedded-primary" => Right(ComponentResourceSourceKind.EmbeddedPrimary)
      case "development-directory" => Right(ComponentResourceSourceKind.DevelopmentDirectory)
      case "expanded-car" => Right(ComponentResourceSourceKind.ExpandedCar)
      case "local-repository" => Right(ComponentResourceSourceKind.LocalRepository)
      case "managed-cache" => Right(ComponentResourceSourceKind.ManagedCache)
      case "offline-bundle" => Right(ComponentResourceSourceKind.OfflineBundle)
      case "remote-repository" => Right(ComponentResourceSourceKind.RemoteRepository)
      case _ => Left(s"$context is not an admitted consumer source kind")
    }

  private def _framework_availability(value: String, context: String): Either[String, FrameworkPublicationReferenceAvailability] =
    FrameworkPublicationReferenceAvailability.fromCode(value).toRight(s"$context is not an admitted framework publication availability")

  private def _directive_authority(value: String, context: String): Either[String, PublicDirectiveAuthority] =
    PublicDirectiveAuthority.fromCode(value).toRight(s"$context is not an admitted public Directive authority")

  private def _directive_redaction(value: String, context: String): Either[String, PublicDirectiveRedaction] =
    PublicDirectiveRedaction.fromCode(value).toRight(s"$context is not an admitted public Directive redaction")

  private def _visibility(value: String, context: String): Either[String, PublicMetadataVisibility] =
    PublicMetadataVisibility.fromCode(value).toRight(s"$context is not an admitted public metadata visibility")

  private def _component_id(obj: JsonObject, field: String, context: String): Either[String, ComponentId] =
    _string(obj, field, context).flatMap { value =>
      ComponentId.parseC(value).toOption match {
        case Some(id) if id.name == value => Right(id)
        case _ => Left(s"$context.$field must be a canonical ComponentId")
      }
    }

  private def _optional_component_id(obj: JsonObject, field: String, context: String): Either[String, Option[ComponentId]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.toRight(s"$context.$field must be a ComponentId, null, or absent").flatMap { text =>
        ComponentId.parseC(text).toOption match {
          case Some(id) if id.name == text => Right(Some(id))
          case _ => Left(s"$context.$field must be a canonical ComponentId")
        }
      }
    }

  private def _optional_string(obj: JsonObject, field: String, context: String): Either[String, Option[String]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.map(Some(_)).toRight(s"$context.$field must be a string, null, or absent")
    }

  private def _string_vector(json: Json, context: String): Either[String, Vector[String]] =
    _array(json, context).flatMap { (values: Vector[Json]) =>
      _sequence(values.zipWithIndex.map { case (value, index) => value.asString.toRight(s"$context[$index] must be a string") })
    }

  private def _non_negative_long(obj: JsonObject, field: String, context: String): Either[String, Long] =
    _field(obj, field, context).flatMap(_.asNumber.flatMap(_.toLong).filter(_ >= 0).toRight(s"$context.$field must be a non-negative whole number"))

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be an object")

  private def _array(json: Json, context: String): Either[String, Vector[Json]] =
    json.asArray.map(_.toVector).toRight(s"$context must be an array")

  private def _field(obj: JsonObject, field: String, context: String): Either[String, Json] =
    obj(field).toRight(s"$context requires $field")

  private def _string(obj: JsonObject, field: String, context: String): Either[String, String] =
    _field(obj, field, context).flatMap(_.asString.toRight(s"$context.$field must be a string"))

  private def _boolean(obj: JsonObject, field: String, context: String): Either[String, Boolean] =
    _field(obj, field, context).flatMap(_.asBoolean.toRight(s"$context.$field must be a boolean"))

  private def _extensions(obj: JsonObject, known: Set[String]): Map[String, Json] =
    obj.toMap.filterNot { case (key, _) => known.contains(key) }

  private def _resource_order(value: ComponentKnowledgeManifestConsumerResourceEvidence): (String, String, String, String, String, String) = {
    val identity = value.logicalIdentity
    (identity.componentId.name, identity.logicalRelease, identity.parentComponentId.map(_.name).getOrElse(""), identity.childRole, identity.logicalResource, value.logicalPath)
  }

  private def _identity_order(value: ComponentResourceLogicalIdentity): (String, String, String, String, String) =
    (value.componentId.name, value.logicalRelease, value.parentComponentId.map(_.name).getOrElse(""), value.childRole, value.logicalResource)

  private def _availability_code(value: ComponentResourceAvailability): String =
    value match {
      case ComponentResourceAvailability.Available => "available"
      case ComponentResourceAvailability.Restricted => "restricted"
      case ComponentResourceAvailability.Unavailable => "unavailable"
      case ComponentResourceAvailability.Missing => "missing"
      case ComponentResourceAvailability.Stale => "stale"
      case ComponentResourceAvailability.Incompatible => "incompatible"
      case ComponentResourceAvailability.Corrupt => "corrupt"
    }

  private def _integrity_code(value: ComponentResourceIntegrity): String =
    value match {
      case ComponentResourceIntegrity.NotEvaluated => "not-evaluated"
      case ComponentResourceIntegrity.Verified => "verified"
      case ComponentResourceIntegrity.Unverified => "unverified"
    }

  private def _authorization_code(value: ComponentResourceAuthorization): String =
    value match {
      case ComponentResourceAuthorization.NotEvaluated => "not-evaluated"
      case ComponentResourceAuthorization.Granted => "granted"
      case ComponentResourceAuthorization.Denied => "denied"
    }

  private def _source_kind_code(value: ComponentResourceSourceKind): String =
    value match {
      case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary"
      case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory"
      case ComponentResourceSourceKind.ExpandedCar => "expanded-car"
      case ComponentResourceSourceKind.LocalRepository => "local-repository"
      case ComponentResourceSourceKind.ManagedCache => "managed-cache"
      case ComponentResourceSourceKind.OfflineBundle => "offline-bundle"
      case ComponentResourceSourceKind.RemoteRepository => "remote-repository"
    }

  private def _json_object(known: Vector[(String, Json)], extensions: Map[String, Json]): Json = {
    val knownkeys = known.map(_._1).toSet
    val unknown = extensions.iterator.filterNot { case (key, _) => knownkeys.contains(key) }.toVector.sortBy(_._1)
    Json.obj((known ++ unknown).map { case (key, value) => key -> _canonical_json(value) }*)
  }

  private def _canonical_json(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.arr(values.map(_canonical_json)*),
      obj => Json.fromJsonObject(JsonObject.fromIterable(obj.toIterable.toVector.sortBy(_._1).map { case (key, value) => key -> _canonical_json(value) }))
    )

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }
}
