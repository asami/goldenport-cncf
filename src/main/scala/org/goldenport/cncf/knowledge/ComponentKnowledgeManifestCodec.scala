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
 * Deterministic codec for the Component knowledge manifest v1 boundary. The
 * codec accepts no schema other than ComponentKnowledgeManifest.SCHEMA and
 * retains unknown JSON object fields without granting resource authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentKnowledgeManifestCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)

  def decodeC(json: String): Consequence[ComponentKnowledgeManifest] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(manifest: ComponentKnowledgeManifest): String = {
    val encoded = _manifest_json(manifest).noSpaces
    decodeC(encoded).toOption match {
      case Some(_) => encoded
      case None => throw new IllegalArgumentException("Component knowledge manifest failed codec self-validation")
    }
  }

  private def _decode(text: String): Either[String, ComponentKnowledgeManifest] =
    try {
      for {
        json <- _strict_json_parser.parse(text).left.map(error => s"Invalid Component knowledge manifest JSON: ${error.message}")
        root <- _object(json, "manifest")
        schema <- _string(root, "schema", "manifest")
        _ <- Either.cond(schema == ComponentKnowledgeManifest.SCHEMA, (), s"Unsupported Component knowledge manifest schema: $schema")
        componentid <- _component_id(root, "componentId", "manifest")
        release <- _safe_text(_string(root, "logicalRelease", "manifest"), "manifest.logicalRelease")
        resourcesjson <- _field(root, "resources", "manifest")
        resources <- _array(resourcesjson, "manifest.resources").flatMap(_resources)
        manifest = ComponentKnowledgeManifest(
          componentId = componentid,
          logicalRelease = release,
          resources = resources,
          extensions = _extensions(root, Set("schema", "componentId", "logicalRelease", "resources"))
        )
        _ <- ComponentKnowledgeManifest.validateC(manifest).toOption.toRight("Component knowledge manifest violates v1 validation")
      } yield manifest
    } catch {
      case NonFatal(error) => Left(s"Invalid Component knowledge manifest: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _resources(values: Vector[Json]): Either[String, Vector[ComponentKnowledgeResourceEntry]] =
    _sequence(values.zipWithIndex.map { case (json, index) => _resource(json, s"manifest.resources[$index]") })

  private def _resource(json: Json, context: String): Either[String, ComponentKnowledgeResourceEntry] =
    for {
      obj <- _object(json, context)
      bindingjson <- _field(obj, "binding", context)
      binding <- _binding(bindingjson, s"$context.binding")
      pathvalue <- _string(obj, "logicalPath", context)
      path <- _safe_path(pathvalue, s"$context.logicalPath")
      kindvalue <- _string(obj, "kind", context)
      kind <- _kind(kindvalue, s"$context.kind")
      rolevalue <- _string(obj, "role", context)
      role <- _role(rolevalue, s"$context.role")
      language <- _optional_string(obj, "language", context).flatMap {
        case Some(value) => _language(value, s"$context.language").map(Some(_))
        case None => Right(None)
      }
      mediavalue <- _string(obj, "mediaType", context)
      media <- _media_type(mediavalue, s"$context.mediaType")
      size <- _non_negative_long(obj, "size", context)
      sha256value <- _string(obj, "sha256", context)
      sha256 <- _sha256(sha256value, s"$context.sha256")
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
    } yield ComponentKnowledgeResourceEntry(
      binding = binding,
      logicalPath = path,
      kind = kind,
      role = role,
      language = language,
      mediaType = media,
      size = size,
      sha256 = sha256,
      metadata = metadata,
      availability = availability,
      integrity = integrity,
      authorization = authorization,
      provenance = provenance,
      extensions = _extensions(
        obj,
        Set("binding", "logicalPath", "kind", "role", "language", "mediaType", "size", "sha256", "metadata", "availability", "integrity", "authorization", "provenance")
      )
    )

  private def _binding(json: Json, context: String): Either[String, ComponentKnowledgeResourceBinding] =
    for {
      obj <- _object(json, context)
      componentid <- _component_id(obj, "componentId", context)
      release <- _safe_text(_string(obj, "logicalRelease", context), s"$context.logicalRelease")
      parentid <- _optional_component_id(obj, "parentComponentId", context)
      rolevalue <- _string(obj, "childRole", context)
      role <- _safe_role(rolevalue, s"$context.childRole")
      resourcevalue <- _string(obj, "logicalResource", context)
      resource <- _logical_resource(resourcevalue, s"$context.logicalResource")
    } yield ComponentKnowledgeResourceBinding(
      logicalIdentity = ComponentResourceLogicalIdentity(componentid, release, parentid, role, resource),
      extensions = _extensions(obj, Set("componentId", "logicalRelease", "parentComponentId", "childRole", "logicalResource"))
    )

  private def _metadata(json: Json, context: String): Either[String, ComponentKnowledgeMetadata] =
    for {
      obj <- _object(json, context)
      authorityvalue <- _string(obj, "authority", context)
      authority <- _authority(authorityvalue, s"$context.authority")
      stabilityvalue <- _string(obj, "stability", context)
      stability <- _stability(stabilityvalue, s"$context.stability")
      sourcevalue <- _string(obj, "source", context)
      source <- _source(sourcevalue, s"$context.source")
      licensevalue <- _string(obj, "license", context)
      license <- _license(licensevalue, s"$context.license")
      disclosurevalue <- _string(obj, "disclosure", context)
      disclosure <- _disclosure(disclosurevalue, s"$context.disclosure")
    } yield ComponentKnowledgeMetadata(
      authority = authority,
      stability = stability,
      source = source,
      license = license,
      disclosure = disclosure,
      extensions = _extensions(obj, Set("authority", "stability", "source", "license", "disclosure"))
    )

  private def _provenance(json: Json, context: String): Either[String, ComponentKnowledgeSafeProvenance] =
    for {
      obj <- _object(json, context)
      sourcekindvalue <- _string(obj, "sourceKind", context)
      sourcekind <- _source_kind(sourcekindvalue, s"$context.sourceKind")
      coordinatevalue <- _string(obj, "artifactCoordinate", context)
      coordinate <- _artifact_coordinate(coordinatevalue, s"$context.artifactCoordinate")
      logicalsourcevalue <- _string(obj, "logicalSource", context)
      logicalsource <- _logical_source(logicalsourcevalue, s"$context.logicalSource")
      step <- _safe_text(_string(obj, "resolutionStep", context), s"$context.resolutionStep")
      external <- _boolean(obj, "externalDeploymentRequired", context)
      digestvalue <- _string(obj, "matchingDigest", context)
      digest <- _sha256(digestvalue, s"$context.matchingDigest")
      _ <- Either.cond(step == _resolution_step(sourcekind), (), s"$context.resolutionStep does not match sourceKind")
    } yield ComponentKnowledgeSafeProvenance(
      sourceKind = sourcekind,
      artifactCoordinate = coordinate,
      logicalSource = logicalsource,
      resolutionStep = step,
      externalDeploymentRequired = external,
      matchingDigest = digest,
      extensions = _extensions(obj, Set("sourceKind", "artifactCoordinate", "logicalSource", "resolutionStep", "externalDeploymentRequired", "matchingDigest"))
    )

  private def _kind(value: String, context: String): Either[String, ComponentKnowledgeResourceKind] =
    ComponentKnowledgeResourceKind.fromCode(value).toRight(s"$context is not an admitted v1 resource kind")

  private def _role(value: String, context: String): Either[String, ComponentKnowledgeResourceRole] =
    ComponentKnowledgeResourceRole.fromCode(value).toRight(s"$context is not an admitted v1 resource role")

  private def _media_type(value: String, context: String): Either[String, ComponentKnowledgeMediaType] =
    ComponentKnowledgeMediaType.fromValue(value).toRight(s"$context is not an admitted v1 media type")

  private def _authority(value: String, context: String): Either[String, ComponentKnowledgeAuthority] =
    ComponentKnowledgeAuthority.fromCode(value).toRight(s"$context is not an admitted v1 authority")

  private def _stability(value: String, context: String): Either[String, ComponentKnowledgeStability] =
    ComponentKnowledgeStability.fromCode(value).toRight(s"$context is not an admitted v1 stability")

  private def _source(value: String, context: String): Either[String, ComponentKnowledgeSource] =
    ComponentKnowledgeSource.fromCode(value).toRight(s"$context is not an admitted v1 source")

  private def _disclosure(value: String, context: String): Either[String, ComponentKnowledgeDisclosure] =
    ComponentKnowledgeDisclosure.fromCode(value).toRight(s"$context is not an admitted v1 disclosure")

  private def _availability(value: String, context: String): Either[String, ComponentResourceAvailability] =
    value match {
      case "available" => Right(ComponentResourceAvailability.Available)
      case "restricted" => Right(ComponentResourceAvailability.Restricted)
      case "unavailable" => Right(ComponentResourceAvailability.Unavailable)
      case "missing" => Right(ComponentResourceAvailability.Missing)
      case "stale" => Right(ComponentResourceAvailability.Stale)
      case "incompatible" => Right(ComponentResourceAvailability.Incompatible)
      case "corrupt" => Right(ComponentResourceAvailability.Corrupt)
      case _ => Left(s"$context is not an admitted Phase 58 availability state")
    }

  private def _integrity(value: String, context: String): Either[String, ComponentResourceIntegrity] =
    value match {
      case "not-evaluated" => Right(ComponentResourceIntegrity.NotEvaluated)
      case "verified" => Right(ComponentResourceIntegrity.Verified)
      case "unverified" => Right(ComponentResourceIntegrity.Unverified)
      case _ => Left(s"$context is not an admitted Phase 58 integrity state")
    }

  private def _authorization(value: String, context: String): Either[String, ComponentResourceAuthorization] =
    value match {
      case "not-evaluated" => Right(ComponentResourceAuthorization.NotEvaluated)
      case "granted" => Right(ComponentResourceAuthorization.Granted)
      case "denied" => Right(ComponentResourceAuthorization.Denied)
      case _ => Left(s"$context is not an admitted Phase 58 authorization state")
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
      case _ => Left(s"$context is not an admitted Phase 58 source kind")
    }

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
      case Some(value) =>
        value.asString.toRight(s"$context.$field must be a ComponentId, null, or absent").flatMap { text =>
          ComponentId.parseC(text).toOption match {
            case Some(id) if id.name == text => Right(Some(id))
            case _ => Left(s"$context.$field must be a canonical ComponentId")
          }
        }
    }

  private def _logical_resource(value: String, context: String): Either[String, String] =
    try {
      val uri = java.net.URI.create(value)
      Either.cond(uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty), value, s"$context must be an absolute logical resource URI")
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute logical resource URI")
    }

  private def _safe_path(value: String, context: String): Either[String, String] = {
    val segments = Option(value).map(_.split("/", -1).toVector).getOrElse(Vector.empty)
    Either.cond(
      Option(value).exists(text => text.nonEmpty && text == text.trim && !text.startsWith("/") && !text.startsWith("\\") && !text.matches("^[A-Za-z]:.*") && !text.contains('\\') && !text.exists(_.isControl)) &&
        segments.forall(segment => segment.nonEmpty && segment != "." && segment != ".." && !segment.exists(_.isControl)),
      value,
      s"$context must be a safe relative canonical path"
    )
  }

  private def _safe_role(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[A-Za-z][A-Za-z0-9._-]*")), value, s"$context must be a safe role token")

  private def _language(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[a-z][a-z0-9-]*")), value, s"$context must be a safe language token")

  private def _license(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[A-Za-z0-9][A-Za-z0-9.+-]*")), value, s"$context must be an SPDX-like license token")

  private def _sha256(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[0-9a-f]{64}")), value, s"$context must be a lowercase SHA-256 digest")

  private def _artifact_coordinate(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*")), value, s"$context must be a three-part artifact coordinate")

  private def _logical_source(value: String, context: String): Either[String, String] =
    Either.cond(Option(value).exists(_.matches("[A-Za-z][A-Za-z0-9+._-]*:[A-Za-z0-9][A-Za-z0-9+._/:=-]*")), value, s"$context must be safe logical provenance evidence")

  private def _safe_text(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(Option(text).exists(value => value.nonEmpty && value == value.trim && !value.exists(_.isControl)), text, s"$context must be non-empty trimmed text")
    }

  private def _non_negative_long(obj: JsonObject, field: String, context: String): Either[String, Long] =
    _field(obj, field, context).flatMap { value =>
      value.asNumber.flatMap(_.toLong).filter(_ >= 0).toRight(s"$context.$field must be a non-negative whole number")
    }

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be an object")

  private def _array(json: Json, context: String): Either[String, Vector[Json]] =
    json.asArray.map(_.toVector).toRight(s"$context must be an array")

  private def _field(obj: JsonObject, field: String, context: String): Either[String, Json] =
    obj(field).toRight(s"$context requires $field")

  private def _string(obj: JsonObject, field: String, context: String): Either[String, String] =
    _field(obj, field, context).flatMap(_.asString.toRight(s"$context.$field must be a string"))

  private def _optional_string(obj: JsonObject, field: String, context: String): Either[String, Option[String]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => value.asString.map(Some(_)).toRight(s"$context.$field must be a string, null, or absent")
    }

  private def _boolean(obj: JsonObject, field: String, context: String): Either[String, Boolean] =
    _field(obj, field, context).flatMap(_.asBoolean.toRight(s"$context.$field must be a boolean"))

  private def _extensions(obj: JsonObject, known: Set[String]): Map[String, Json] =
    obj.toMap.filterNot { case (key, _) => known.contains(key) }

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }

  private def _manifest_json(manifest: ComponentKnowledgeManifest): Json =
    _json_object(
      Vector(
        "schema" -> Json.fromString(ComponentKnowledgeManifest.SCHEMA),
        "componentId" -> Json.fromString(manifest.componentId.name),
        "logicalRelease" -> Json.fromString(manifest.logicalRelease),
        "resources" -> Json.arr(manifest.resources.sortBy(_resource_order).map(_resource_json)*)
      ),
      manifest.extensions
    )

  private def _resource_json(entry: ComponentKnowledgeResourceEntry): Json =
    _json_object(
      Vector(
        "binding" -> _binding_json(entry.binding),
        "logicalPath" -> Json.fromString(entry.logicalPath),
        "kind" -> Json.fromString(entry.kind.code),
        "role" -> Json.fromString(entry.role.code),
        "language" -> entry.language.map(Json.fromString).getOrElse(Json.Null),
        "mediaType" -> Json.fromString(entry.mediaType.value),
        "size" -> Json.fromLong(entry.size),
        "sha256" -> Json.fromString(entry.sha256),
        "metadata" -> _metadata_json(entry.metadata),
        "availability" -> Json.fromString(_availability_code(entry.availability)),
        "integrity" -> Json.fromString(_integrity_code(entry.integrity)),
        "authorization" -> Json.fromString(_authorization_code(entry.authorization)),
        "provenance" -> _provenance_json(entry.provenance)
      ),
      entry.extensions
    )

  private def _binding_json(binding: ComponentKnowledgeResourceBinding): Json = {
    val identity = binding.logicalIdentity
    _json_object(
      Vector(
        "componentId" -> Json.fromString(identity.componentId.name),
        "logicalRelease" -> Json.fromString(identity.logicalRelease),
        "parentComponentId" -> identity.parentComponentId.map(id => Json.fromString(id.name)).getOrElse(Json.Null),
        "childRole" -> Json.fromString(identity.childRole),
        "logicalResource" -> Json.fromString(identity.logicalResource)
      ),
      binding.extensions
    )
  }

  private def _metadata_json(metadata: ComponentKnowledgeMetadata): Json =
    _json_object(
      Vector(
        "authority" -> Json.fromString(metadata.authority.code),
        "stability" -> Json.fromString(metadata.stability.code),
        "source" -> Json.fromString(metadata.source.code),
        "license" -> Json.fromString(metadata.license),
        "disclosure" -> Json.fromString(metadata.disclosure.code)
      ),
      metadata.extensions
    )

  private def _provenance_json(provenance: ComponentKnowledgeSafeProvenance): Json =
    _json_object(
      Vector(
        "sourceKind" -> Json.fromString(_source_kind_code(provenance.sourceKind)),
        "artifactCoordinate" -> Json.fromString(provenance.artifactCoordinate),
        "logicalSource" -> Json.fromString(provenance.logicalSource),
        "resolutionStep" -> Json.fromString(provenance.resolutionStep),
        "externalDeploymentRequired" -> Json.fromBoolean(provenance.externalDeploymentRequired),
        "matchingDigest" -> Json.fromString(provenance.matchingDigest)
      ),
      provenance.extensions
    )

  private def _resource_order(entry: ComponentKnowledgeResourceEntry): (String, String, String, String, String, String) = {
    val identity = entry.binding.logicalIdentity
    (
      identity.componentId.name,
      identity.logicalRelease,
      identity.parentComponentId.map(_.name).getOrElse(""),
      identity.childRole,
      identity.logicalResource,
      entry.logicalPath
    )
  }

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
}
