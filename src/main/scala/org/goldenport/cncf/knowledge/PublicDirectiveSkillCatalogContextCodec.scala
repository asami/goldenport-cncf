package org.goldenport.cncf.knowledge

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Deterministic codec for descriptive public Directive and Skill Catalog
 * metadata. Referenced entries are selected only from already decoded manifest
 * resources; this codec neither reads them nor changes their authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object PublicDirectiveSkillCatalogContextCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)

  def decodeDirectiveC(
    json: String,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): Consequence[PublicDirectiveProjection] =
    _decode(json, "publicDirective").flatMap(_decode_directive_json(_, manifestResources, "publicDirective")) match {
      case Right(value) => Consequence.success(value)
      case Left(message) => Consequence.argumentInvalid(message)
    }

  def encodeDirective(
    projection: PublicDirectiveProjection,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): String = {
    PublicDirectiveProjection.validateC(projection, manifestResources).toOption match {
      case Some(_) => _encode_directive_json(projection).noSpaces
      case None => throw new IllegalArgumentException("Public Directive projection failed validation")
    }
  }

  def decodeSkillCatalogC(
    json: String,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): Consequence[PublicSkillCatalog] =
    _decode(json, "skillCatalog").flatMap(_decode_skill_catalog_json(_, manifestResources, "skillCatalog")) match {
      case Right(value) => Consequence.success(value)
      case Left(message) => Consequence.argumentInvalid(message)
    }

  def encodeSkillCatalog(
    catalog: PublicSkillCatalog,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): String = {
    PublicSkillCatalog.validateC(catalog, manifestResources).toOption match {
      case Some(_) => _encode_skill_catalog_json(catalog).noSpaces
      case None => throw new IllegalArgumentException("Public Skill Catalog failed validation")
    }
  }

  private[knowledge] def _decode_directive_json(
    json: Json,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, PublicDirectiveProjection] =
    try {
      for {
        obj <- _object(json, context)
        identityjson <- _field(obj, "logicalIdentity", context)
        identity <- _identity(identityjson, s"$context.logicalIdentity")
        entry <- _resource_for(identity, resources, s"$context.logicalIdentity")
        directiveid <- _string(obj, "directiveId", context)
        profileid <- _string(obj, "profileId", context)
        ruleid <- _string(obj, "ruleId", context)
        origin <- _string(obj, "origin", context)
        version <- _string(obj, "version", context)
        authorityvalue <- _string(obj, "authority", context)
        authority <- _authority(authorityvalue, s"$context.authority")
        visibilityvalue <- _string(obj, "visibility", context)
        visibility <- _visibility(visibilityvalue, s"$context.visibility")
        digest <- _string(obj, "sourceSha256", context)
        redactionvalue <- _string(obj, "redaction", context)
        redaction <- _redaction(redactionvalue, s"$context.redaction")
        guide <- _string(obj, "guideReference", context)
        projection = PublicDirectiveProjection(
          entry = entry,
          directiveId = directiveid,
          profileId = profileid,
          ruleId = ruleid,
          origin = origin,
          version = version,
          authority = authority,
          visibility = visibility,
          sourceSha256 = digest,
          redaction = redaction,
          guideReference = guide,
          extensions = _extensions(obj, Set("logicalIdentity", "directiveId", "profileId", "ruleId", "origin", "version", "authority", "visibility", "sourceSha256", "redaction", "guideReference"))
        )
        _ <- PublicDirectiveProjection.validateC(projection, resources).toOption.toRight(s"$context violates public Directive validation")
      } yield projection
    } catch {
      case NonFatal(error) => Left(s"Invalid public Directive metadata: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private[knowledge] def _decode_skill_catalog_json(
    json: Json,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, PublicSkillCatalog] =
    try {
      for {
        obj <- _object(json, context)
        identityjson <- _field(obj, "logicalIdentity", context)
        identity <- _identity(identityjson, s"$context.logicalIdentity")
        entry <- _resource_for(identity, resources, s"$context.logicalIdentity")
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
        catalog = PublicSkillCatalog(
          entry = entry,
          catalogId = catalogid,
          owner = owner,
          purpose = purpose,
          trigger = trigger,
          requirements = requirements,
          permissions = permissions,
          sideEffects = sideeffects,
          mcpRequirements = mcprequirements,
          installationReference = installationreference,
          visibility = visibility,
          version = version,
          sourceSha256 = digest,
          extensions = _extensions(obj, Set("logicalIdentity", "catalogId", "owner", "purpose", "trigger", "requirements", "permissions", "sideEffects", "mcpRequirements", "installationReference", "visibility", "version", "sourceSha256"))
        )
        _ <- PublicSkillCatalog.validateC(catalog, resources).toOption.toRight(s"$context violates public Skill Catalog validation")
      } yield catalog
    } catch {
      case NonFatal(error) => Left(s"Invalid public Skill Catalog metadata: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private[knowledge] def _encode_directive_json(projection: PublicDirectiveProjection): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(projection.entry.binding.logicalIdentity),
        "directiveId" -> Json.fromString(projection.directiveId),
        "profileId" -> Json.fromString(projection.profileId),
        "ruleId" -> Json.fromString(projection.ruleId),
        "origin" -> Json.fromString(projection.origin),
        "version" -> Json.fromString(projection.version),
        "authority" -> Json.fromString(projection.authority.code),
        "visibility" -> Json.fromString(projection.visibility.code),
        "sourceSha256" -> Json.fromString(projection.sourceSha256),
        "redaction" -> Json.fromString(projection.redaction.code),
        "guideReference" -> Json.fromString(projection.guideReference)
      ),
      projection.extensions
    )

  private[knowledge] def _encode_skill_catalog_json(catalog: PublicSkillCatalog): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(catalog.entry.binding.logicalIdentity),
        "catalogId" -> Json.fromString(catalog.catalogId),
        "owner" -> Json.fromString(catalog.owner),
        "purpose" -> Json.fromString(catalog.purpose),
        "trigger" -> Json.fromString(catalog.trigger),
        "requirements" -> Json.arr(catalog.requirements.map(Json.fromString)*),
        "permissions" -> Json.arr(catalog.permissions.map(Json.fromString)*),
        "sideEffects" -> Json.arr(catalog.sideEffects.map(Json.fromString)*),
        "mcpRequirements" -> Json.arr(catalog.mcpRequirements.map(Json.fromString)*),
        "installationReference" -> Json.fromString(catalog.installationReference),
        "visibility" -> Json.fromString(catalog.visibility.code),
        "version" -> Json.fromString(catalog.version),
        "sourceSha256" -> Json.fromString(catalog.sourceSha256)
      ),
      catalog.extensions
    )

  private def _decode(text: String, context: String): Either[String, Json] =
    try {
      _strict_json_parser.parse(text).left.map(error => s"Invalid $context JSON: ${error.message}")
    } catch {
      case NonFatal(error) => Left(s"Invalid $context JSON: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _identity(json: Json, context: String): Either[String, ComponentResourceLogicalIdentity] =
    for {
      obj <- _object(json, context)
      _ <- Either.cond(
        obj.keys.toSet == Set("componentId", "logicalRelease", "parentComponentId", "childRole", "logicalResource"),
        (),
        s"$context must contain only a canonical logical identity"
      )
      componentid <- _component_id(obj, "componentId", context)
      release <- _string(obj, "logicalRelease", context)
      parentid <- _optional_component_id(obj, "parentComponentId", context)
      childrole <- _string(obj, "childRole", context)
      logicalresource <- _string(obj, "logicalResource", context)
    } yield ComponentResourceLogicalIdentity(componentid, release, parentid, childrole, logicalresource)

  private def _resource_for(
    identity: ComponentResourceLogicalIdentity,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, ComponentKnowledgeResourceEntry] =
    resources.filter(_.binding.logicalIdentity == identity) match {
      case Vector(entry) => Right(entry)
      case Vector() => Left(s"$context must identify exactly one existing manifest resource")
      case _ => Left(s"$context matches more than one manifest resource")
    }

  private def _authority(value: String, context: String): Either[String, PublicDirectiveAuthority] =
    PublicDirectiveAuthority.fromCode(value).toRight(s"$context is not an admitted public Directive authority")

  private def _visibility(value: String, context: String): Either[String, PublicMetadataVisibility] =
    PublicMetadataVisibility.fromCode(value).toRight(s"$context is not an admitted public metadata visibility")

  private def _redaction(value: String, context: String): Either[String, PublicDirectiveRedaction] =
    PublicDirectiveRedaction.fromCode(value).toRight(s"$context is not an admitted public Directive redaction")

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

  private def _string_vector(json: Json, context: String): Either[String, Vector[String]] =
    _array(json, context).flatMap { (values: Vector[Json]) =>
      _sequence(values.zipWithIndex.map { case (value, index) =>
        value.asString.toRight(s"$context[$index] must be a string")
      })
    }

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be an object")

  private def _array(json: Json, context: String): Either[String, Vector[Json]] =
    json.asArray.map(_.toVector).toRight(s"$context must be an array")

  private def _field(obj: JsonObject, field: String, context: String): Either[String, Json] =
    obj(field).toRight(s"$context requires $field")

  private def _string(obj: JsonObject, field: String, context: String): Either[String, String] =
    _field(obj, field, context).flatMap(_.asString.toRight(s"$context.$field must be a string"))

  private def _extensions(obj: JsonObject, known: Set[String]): Map[String, Json] =
    obj.toMap.filterNot { case (key, _) => known.contains(key) }

  private def _identity_json(value: ComponentResourceLogicalIdentity): Json =
    Json.obj(
      "componentId" -> Json.fromString(value.componentId.name),
      "logicalRelease" -> Json.fromString(value.logicalRelease),
      "parentComponentId" -> value.parentComponentId.map(id => Json.fromString(id.name)).getOrElse(Json.Null),
      "childRole" -> Json.fromString(value.childRole),
      "logicalResource" -> Json.fromString(value.logicalResource)
    )

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
