package org.goldenport.cncf.component

import java.net.URI
import java.util.Base64
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate

/*
 * Versioned parent/Subcomponent identity and membership evidence.  This is a
 * registry codec only: decoding it neither resolves an artifact nor grants a
 * runtime, deployment, activation, operation, MCP, or disclosure capability.
 *
 * @since   Aug. 20, 2026
 * @version Aug. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentSubcomponentComposition(
  parent: ComponentSubcomponentParent,
  members: Vector[ComponentSubcomponentMember],
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentParent(
  componentId: ComponentId,
  logicalRelease: String,
  primaryCar: ComponentSubcomponentCar,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentMember(
  componentId: ComponentId,
  logicalRelease: String,
  required: Boolean,
  role: String,
  implementationTechnology: String,
  logicalResource: String,
  logicalPath: String,
  subcomponentCar: ComponentSubcomponentCar,
  payload: ComponentSubcomponentPayload,
  authorization: ComponentSubcomponentState,
  integrity: ComponentSubcomponentState,
  availability: ComponentSubcomponentState,
  deployment: ComponentSubcomponentDeployment,
  access: ComponentSubcomponentAccess,
  disclosure: ComponentSubcomponentDisclosure,
  license: ComponentSubcomponentLicense,
  media: ComponentSubcomponentMedia,
  profile: ComponentSubcomponentProfile,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentCar(
  classification: String,
  artifact: ComponentSubcomponentArtifact,
  provenance: ComponentSubcomponentProvenance,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentArtifact(
  coordinate: String,
  sha256: String,
  signature: String,
  repository: String,
  physicalPath: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentProvenance(
  logicalSource: String,
  physicalSource: String,
  physicalPath: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentPayload(
  authoritative: Boolean,
  executable: Boolean,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentState(
  state: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentDeployment(
  platform: String,
  mode: String,
  requiresExplicitPlatformAction: Boolean,
  authority: ComponentSubcomponentAuthority,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentAuthority(
  activation: Boolean,
  operation: Boolean,
  mcp: Boolean,
  disclosure: Boolean,
  deployment: Boolean,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentAccess(
  visibility: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentDisclosure(
  mode: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentLicense(
  spdx: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentMedia(
  `type`: String,
  extensions: Map[String, Json] = Map.empty
)

final case class ComponentSubcomponentProfile(
  id: String,
  extensions: Map[String, Json] = Map.empty
)

object ComponentSubcomponentCompositionCodec {
  private val _schema = "cncf.component-subcomponent-composition.v1"
  private val _membership_kind = "parent-child"

  private val _primary_car_classification = "primary"
  private val _subcomponent_car_classification = "subcomponent"
  private val _authorization_states = Set("not-granted", "granted")
  private val _integrity_states = Set("verified", "unverified")
  private val _availability_states = Set("available", "unavailable")
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _artifact_coordinate_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _source_pattern = "[A-Za-z][A-Za-z0-9+._-]*:[A-Za-z0-9][A-Za-z0-9+._/:=-]*".r
  private val _role_pattern = "[A-Za-z][A-Za-z0-9._-]*".r
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)

  def decodeC(json: String): Consequence[ComponentSubcomponentComposition] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(composition: ComponentSubcomponentComposition): String = {
    val encoded = _composition_json(composition).noSpaces
    _decode(encoded).fold(
      reason => throw new IllegalArgumentException(reason),
      _ => encoded
    )
  }

  private def _decode(text: String): Either[String, ComponentSubcomponentComposition] =
    try {
      for {
        json <- _strict_json_parser.parse(text).left.map(error => s"Invalid component-subcomponent composition JSON: ${error.message}")
        root <- _object(json, "composition")
        schema <- _string(root, "schema", "composition")
        _ <- Either.cond(schema == _schema, (), s"Unsupported component-subcomponent composition schema: $schema")
        membershipkind <- _string(root, "membershipKind", "composition")
        _ <- Either.cond(membershipkind == _membership_kind, (), s"Unsupported component-subcomponent membership kind: $membershipkind")
        parentjson <- _field(root, "parent", "composition")
        parent <- _parent(parentjson)
        memberjson <- _field(root, "members", "composition")
        members <- _array(memberjson, "composition.members").flatMap(_members)
        _ <- _validate_members(parent, members)
      } yield ComponentSubcomponentComposition(
        parent = parent,
        members = members,
        extensions = _extensions(root, Set("schema", "membershipKind", "parent", "members"))
      )
    } catch {
      case NonFatal(error) => Left(s"Invalid component-subcomponent composition: ${error.getMessage}")
    }

  private def _parent(json: Json): Either[String, ComponentSubcomponentParent] =
    for {
      obj <- _object(json, "parent")
      componentid <- _component_id(obj, "parent")
      release <- _logical_release(obj, componentid, "parent")
      carjson <- _field(obj, "primaryCar", "parent")
      car <- _car(carjson, _primary_car_classification, "parent.primaryCar")
    } yield ComponentSubcomponentParent(
      componentId = componentid,
      logicalRelease = release,
      primaryCar = car,
      extensions = _extensions(obj, Set("componentId", "logicalRelease", "primaryCar"))
    )

  private def _members(values: Vector[Json]): Either[String, Vector[ComponentSubcomponentMember]] =
    _sequence(values.zipWithIndex.map { case (json, index) => _member(json, s"members[$index]") })

  private def _member(json: Json, context: String): Either[String, ComponentSubcomponentMember] =
    for {
      obj <- _object(json, context)
      componentid <- _component_id(obj, context)
      release <- _logical_release(obj, componentid, context)
      required <- _boolean(obj, "required", context)
      role <- _safe_role(_string(obj, "role", context), s"$context.role")
      technology <- _safe_text(_string(obj, "implementationTechnology", context), s"$context.implementationTechnology")
      resource <- _logical_resource(_string(obj, "logicalResource", context), s"$context.logicalResource")
      logicalpath <- _safe_path(_string(obj, "logicalPath", context), s"$context.logicalPath")
      carjson <- _field(obj, "subcomponentCar", context)
      car <- _car(carjson, _subcomponent_car_classification, s"$context.subcomponentCar")
      payloadjson <- _field(obj, "payload", context)
      payload <- _payload(payloadjson, s"$context.payload")
      authorizationjson <- _field(obj, "authorization", context)
      authorization <- _state(authorizationjson, _authorization_states, s"$context.authorization")
      integrityjson <- _field(obj, "integrity", context)
      integrity <- _state(integrityjson, _integrity_states, s"$context.integrity")
      availabilityjson <- _field(obj, "availability", context)
      availability <- _state(availabilityjson, _availability_states, s"$context.availability")
      deploymentjson <- _field(obj, "deployment", context)
      deployment <- _deployment(deploymentjson, s"$context.deployment")
      accessjson <- _field(obj, "access", context)
      access <- _access(accessjson, s"$context.access")
      disclosurejson <- _field(obj, "disclosure", context)
      disclosure <- _disclosure(disclosurejson, s"$context.disclosure")
      licensejson <- _field(obj, "license", context)
      license <- _license(licensejson, s"$context.license")
      mediajson <- _field(obj, "media", context)
      media <- _media(mediajson, s"$context.media")
      profilejson <- _field(obj, "profile", context)
      profile <- _profile(profilejson, s"$context.profile")
    } yield ComponentSubcomponentMember(
      componentId = componentid,
      logicalRelease = release,
      required = required,
      role = role,
      implementationTechnology = technology,
      logicalResource = resource,
      logicalPath = logicalpath,
      subcomponentCar = car,
      payload = payload,
      authorization = authorization,
      integrity = integrity,
      availability = availability,
      deployment = deployment,
      access = access,
      disclosure = disclosure,
      license = license,
      media = media,
      profile = profile,
      extensions = _extensions(
        obj,
        Set(
          "componentId", "logicalRelease", "required", "role", "implementationTechnology", "logicalResource", "logicalPath",
          "subcomponentCar", "payload", "authorization", "integrity", "availability", "deployment", "access",
          "disclosure", "license", "media", "profile"
        )
      )
    )

  private def _car(json: Json, expectedclassification: String, context: String): Either[String, ComponentSubcomponentCar] =
    for {
      obj <- _object(json, context)
      classification <- _string(obj, "classification", context)
      _ <- Either.cond(
        classification == expectedclassification,
        (),
        s"$context classification must be $expectedclassification"
      )
      artifactjson <- _field(obj, "artifact", context)
      artifact <- _artifact(artifactjson, s"$context.artifact")
      provenancejson <- _field(obj, "provenance", context)
      provenance <- _provenance(provenancejson, s"$context.provenance")
    } yield ComponentSubcomponentCar(
      classification = classification,
      artifact = artifact,
      provenance = provenance,
      extensions = _extensions(obj, Set("classification", "artifact", "provenance"))
    )

  private def _artifact(json: Json, context: String): Either[String, ComponentSubcomponentArtifact] =
    for {
      obj <- _object(json, context)
      coordinate <- _artifact_coordinate(_string(obj, "coordinate", context), s"$context.coordinate")
      sha256 <- _sha256(_string(obj, "sha256", context), s"$context.sha256")
      signature <- _signature(_string(obj, "signature", context), s"$context.signature")
      repository <- _repository(_string(obj, "repository", context), s"$context.repository")
      physicalpath <- _safe_path(_string(obj, "physicalPath", context), s"$context.physicalPath")
    } yield ComponentSubcomponentArtifact(
      coordinate = coordinate,
      sha256 = sha256,
      signature = signature,
      repository = repository,
      physicalPath = physicalpath,
      extensions = _extensions(obj, Set("coordinate", "sha256", "signature", "repository", "physicalPath"))
    )

  private def _provenance(json: Json, context: String): Either[String, ComponentSubcomponentProvenance] =
    for {
      obj <- _object(json, context)
      logicalsource <- _source(_string(obj, "logicalSource", context), s"$context.logicalSource")
      physicalsource <- _source(_string(obj, "physicalSource", context), s"$context.physicalSource")
      physicalpath <- _safe_path(_string(obj, "physicalPath", context), s"$context.physicalPath")
    } yield ComponentSubcomponentProvenance(
      logicalSource = logicalsource,
      physicalSource = physicalsource,
      physicalPath = physicalpath,
      extensions = _extensions(obj, Set("logicalSource", "physicalSource", "physicalPath"))
    )

  private def _payload(json: Json, context: String): Either[String, ComponentSubcomponentPayload] =
    for {
      obj <- _object(json, context)
      authoritative <- _boolean(obj, "authoritative", context)
      executable <- _boolean(obj, "executable", context)
      _ <- Either.cond(!authoritative && !executable, (), s"$context must be non-authoritative and non-executable")
    } yield ComponentSubcomponentPayload(
      authoritative = authoritative,
      executable = executable,
      extensions = _extensions(obj, Set("authoritative", "executable"))
    )

  private def _state(json: Json, allowedstates: Set[String], context: String): Either[String, ComponentSubcomponentState] =
    for {
      obj <- _object(json, context)
      state <- _safe_text(_string(obj, "state", context), s"$context.state")
      _ <- Either.cond(allowedstates.contains(state), (), s"$context.state is unsupported: $state")
    } yield ComponentSubcomponentState(state, _extensions(obj, Set("state")))

  private def _deployment(json: Json, context: String): Either[String, ComponentSubcomponentDeployment] =
    for {
      obj <- _object(json, context)
      platform <- _safe_text(_string(obj, "platform", context), s"$context.platform")
      mode <- _string(obj, "mode", context)
      _ <- Either.cond(mode == "external", (), s"$context.mode must be external")
      explicit <- _boolean(obj, "requiresExplicitPlatformAction", context)
      _ <- Either.cond(explicit, (), s"$context requires explicit platform action")
      authorityjson <- _field(obj, "authority", context)
      authority <- _authority(authorityjson, s"$context.authority")
    } yield ComponentSubcomponentDeployment(
      platform = platform,
      mode = mode,
      requiresExplicitPlatformAction = explicit,
      authority = authority,
      extensions = _extensions(obj, Set("platform", "mode", "requiresExplicitPlatformAction", "authority"))
    )

  private def _authority(json: Json, context: String): Either[String, ComponentSubcomponentAuthority] =
    for {
      obj <- _object(json, context)
      activation <- _boolean(obj, "activation", context)
      operation <- _boolean(obj, "operation", context)
      mcp <- _boolean(obj, "mcp", context)
      disclosure <- _boolean(obj, "disclosure", context)
      deployment <- _boolean(obj, "deployment", context)
      _ <- Either.cond(
        !activation && !operation && !mcp && !disclosure && !deployment,
        (),
        s"$context must not grant activation, operation, MCP, disclosure, or deployment authority"
      )
    } yield ComponentSubcomponentAuthority(
      activation = activation,
      operation = operation,
      mcp = mcp,
      disclosure = disclosure,
      deployment = deployment,
      extensions = _extensions(obj, Set("activation", "operation", "mcp", "disclosure", "deployment"))
    )

  private def _access(json: Json, context: String): Either[String, ComponentSubcomponentAccess] =
    for {
      obj <- _object(json, context)
      visibility <- _safe_text(_string(obj, "visibility", context), s"$context.visibility")
    } yield ComponentSubcomponentAccess(visibility, _extensions(obj, Set("visibility")))

  private def _disclosure(json: Json, context: String): Either[String, ComponentSubcomponentDisclosure] =
    for {
      obj <- _object(json, context)
      mode <- _safe_text(_string(obj, "mode", context), s"$context.mode")
    } yield ComponentSubcomponentDisclosure(mode, _extensions(obj, Set("mode")))

  private def _license(json: Json, context: String): Either[String, ComponentSubcomponentLicense] =
    for {
      obj <- _object(json, context)
      spdx <- _safe_text(_string(obj, "spdx", context), s"$context.spdx")
    } yield ComponentSubcomponentLicense(spdx, _extensions(obj, Set("spdx")))

  private def _media(json: Json, context: String): Either[String, ComponentSubcomponentMedia] =
    for {
      obj <- _object(json, context)
      mediatype <- _safe_text(_string(obj, "type", context), s"$context.type")
    } yield ComponentSubcomponentMedia(mediatype, _extensions(obj, Set("type")))

  private def _profile(json: Json, context: String): Either[String, ComponentSubcomponentProfile] =
    for {
      obj <- _object(json, context)
      id <- _safe_text(_string(obj, "id", context), s"$context.id")
    } yield ComponentSubcomponentProfile(id, _extensions(obj, Set("id")))

  private def _validate_members(
    parent: ComponentSubcomponentParent,
    members: Vector[ComponentSubcomponentMember]
  ): Either[String, Unit] = {
    val identities = members.map(_.componentId.name)
    val roles = members.map(_.role)
    val resources = members.map(_.logicalResource)
    for {
      _ <- Either.cond(members.nonEmpty, (), "composition requires at least one parent-child membership")
      _ <- Either.cond(
        members.forall(_.componentId != parent.componentId),
        (),
        "composition member must not reference its parent component identity"
      )
      _ <- Either.cond(identities.distinct.size == identities.size, (), "composition contains a duplicate child component identity")
      _ <- Either.cond(roles.distinct.size == roles.size, (), "composition contains a duplicate child role")
      _ <- Either.cond(resources.distinct.size == resources.size, (), "composition contains a logical resource conflict")
    } yield ()
  }

  private def _component_id(obj: JsonObject, context: String): Either[String, ComponentId] =
    for {
      text <- _string(obj, "componentId", context)
      id <- ComponentId.parseC(text).toOption.toRight(s"$context.componentId must be a canonical ComponentId")
      _ <- Either.cond(id.name == text, (), s"$context.componentId must be canonical")
    } yield id

  private def _logical_release(
    obj: JsonObject,
    componentid: ComponentId,
    context: String
  ): Either[String, String] =
    for {
      release <- _safe_text(_string(obj, "logicalRelease", context), s"$context.logicalRelease")
      coordinate = ComponentReleaseCoordinate.create(componentid.sharedIdentity, release)
      _ <- Either.cond(coordinate.isSuccess(), (), s"$context.logicalRelease is not valid for ${componentid.name}")
    } yield release

  private def _logical_resource(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      _safe_text(Right(text), context).flatMap { resource =>
        try {
          val uri = URI.create(resource)
          Either.cond(uri.isAbsolute && uri.getScheme != null && uri.getSchemeSpecificPart != null && uri.getSchemeSpecificPart.nonEmpty, resource, s"$context must be an absolute logical resource URI")
        } catch {
          case NonFatal(_) => Left(s"$context must be an absolute logical resource URI")
        }
      }
    }

  private def _safe_role(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(_role_pattern.matches(text), text, s"$context must be a role token distinct from implementation technology")
    }

  private def _artifact_coordinate(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(_artifact_coordinate_pattern.matches(text), text, s"$context must be a three-part artifact coordinate")
    }

  private def _sha256(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(_sha256_pattern.matches(text), text, s"$context must be a lowercase 64-character SHA-256 digest")
    }

  private def _signature(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      try {
        val decoded = Base64.getDecoder.decode(text)
        Either.cond(decoded.nonEmpty, text, s"$context must be non-empty Base64 signature material")
      } catch {
        case NonFatal(_) => Left(s"$context must be Base64 signature material")
      }
    }

  private def _repository(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      try {
        val uri = URI.create(text)
        Either.cond(
          (uri.getScheme == "https" || uri.getScheme == "http") && Option(uri.getHost).exists(_.nonEmpty) && uri.getUserInfo == null,
          text,
          s"$context must be an absolute HTTP(S) repository URI without user information"
        )
      } catch {
        case NonFatal(_) => Left(s"$context must be an absolute HTTP(S) repository URI")
      }
    }

  private def _source(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(_source_pattern.matches(text), text, s"$context must be safe logical or physical provenance evidence")
    }

  private def _safe_path(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      val segments = text.split("/", -1).toVector
      Either.cond(
        text.nonEmpty && text == text.trim &&
          !text.startsWith("/") && !text.startsWith("\\") && !text.matches("^[A-Za-z]:.*") &&
          !text.contains('\\') && segments.forall(segment => segment.nonEmpty && segment != "." && segment != ".."),
        text,
        s"$context must be a non-empty safe relative path"
      )
    }

  private def _safe_text(value: Either[String, String], context: String): Either[String, String] =
    value.flatMap { text =>
      Either.cond(
        text.nonEmpty && text == text.trim && !text.exists(_.isControl),
        text,
        s"$context must be a non-empty trimmed text value"
      )
    }

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

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }

  private def _composition_json(composition: ComponentSubcomponentComposition): Json =
    _json_object(
      Vector(
        "schema" -> Json.fromString(_schema),
        "membershipKind" -> Json.fromString(_membership_kind),
        "parent" -> _parent_json(composition.parent),
        "members" -> Json.arr(composition.members.sortBy(member => (member.componentId.name, member.logicalRelease, member.role)).map(_member_json)*)
      ),
      composition.extensions
    )

  private def _parent_json(parent: ComponentSubcomponentParent): Json =
    _json_object(
      Vector(
        "componentId" -> Json.fromString(parent.componentId.name),
        "logicalRelease" -> Json.fromString(parent.logicalRelease),
        "primaryCar" -> _car_json(parent.primaryCar)
      ),
      parent.extensions
    )

  private def _member_json(member: ComponentSubcomponentMember): Json =
    _json_object(
      Vector(
        "componentId" -> Json.fromString(member.componentId.name),
        "logicalRelease" -> Json.fromString(member.logicalRelease),
        "required" -> Json.fromBoolean(member.required),
        "role" -> Json.fromString(member.role),
        "implementationTechnology" -> Json.fromString(member.implementationTechnology),
        "logicalResource" -> Json.fromString(member.logicalResource),
        "logicalPath" -> Json.fromString(member.logicalPath),
        "subcomponentCar" -> _car_json(member.subcomponentCar),
        "payload" -> _payload_json(member.payload),
        "authorization" -> _state_json(member.authorization),
        "integrity" -> _state_json(member.integrity),
        "availability" -> _state_json(member.availability),
        "deployment" -> _deployment_json(member.deployment),
        "access" -> _access_json(member.access),
        "disclosure" -> _disclosure_json(member.disclosure),
        "license" -> _license_json(member.license),
        "media" -> _media_json(member.media),
        "profile" -> _profile_json(member.profile)
      ),
      member.extensions
    )

  private def _car_json(car: ComponentSubcomponentCar): Json =
    _json_object(
      Vector(
        "classification" -> Json.fromString(car.classification),
        "artifact" -> _artifact_json(car.artifact),
        "provenance" -> _provenance_json(car.provenance)
      ),
      car.extensions
    )

  private def _artifact_json(artifact: ComponentSubcomponentArtifact): Json =
    _json_object(
      Vector(
        "coordinate" -> Json.fromString(artifact.coordinate),
        "sha256" -> Json.fromString(artifact.sha256),
        "signature" -> Json.fromString(artifact.signature),
        "repository" -> Json.fromString(artifact.repository),
        "physicalPath" -> Json.fromString(artifact.physicalPath)
      ),
      artifact.extensions
    )

  private def _provenance_json(provenance: ComponentSubcomponentProvenance): Json =
    _json_object(
      Vector(
        "logicalSource" -> Json.fromString(provenance.logicalSource),
        "physicalSource" -> Json.fromString(provenance.physicalSource),
        "physicalPath" -> Json.fromString(provenance.physicalPath)
      ),
      provenance.extensions
    )

  private def _payload_json(payload: ComponentSubcomponentPayload): Json =
    _json_object(
      Vector(
        "authoritative" -> Json.fromBoolean(payload.authoritative),
        "executable" -> Json.fromBoolean(payload.executable)
      ),
      payload.extensions
    )

  private def _state_json(state: ComponentSubcomponentState): Json =
    _json_object(Vector("state" -> Json.fromString(state.state)), state.extensions)

  private def _deployment_json(deployment: ComponentSubcomponentDeployment): Json =
    _json_object(
      Vector(
        "platform" -> Json.fromString(deployment.platform),
        "mode" -> Json.fromString(deployment.mode),
        "requiresExplicitPlatformAction" -> Json.fromBoolean(deployment.requiresExplicitPlatformAction),
        "authority" -> _authority_json(deployment.authority)
      ),
      deployment.extensions
    )

  private def _authority_json(authority: ComponentSubcomponentAuthority): Json =
    _json_object(
      Vector(
        "activation" -> Json.fromBoolean(authority.activation),
        "operation" -> Json.fromBoolean(authority.operation),
        "mcp" -> Json.fromBoolean(authority.mcp),
        "disclosure" -> Json.fromBoolean(authority.disclosure),
        "deployment" -> Json.fromBoolean(authority.deployment)
      ),
      authority.extensions
    )

  private def _access_json(access: ComponentSubcomponentAccess): Json =
    _json_object(Vector("visibility" -> Json.fromString(access.visibility)), access.extensions)

  private def _disclosure_json(disclosure: ComponentSubcomponentDisclosure): Json =
    _json_object(Vector("mode" -> Json.fromString(disclosure.mode)), disclosure.extensions)

  private def _license_json(license: ComponentSubcomponentLicense): Json =
    _json_object(Vector("spdx" -> Json.fromString(license.spdx)), license.extensions)

  private def _media_json(media: ComponentSubcomponentMedia): Json =
    _json_object(Vector("type" -> Json.fromString(media.`type`)), media.extensions)

  private def _profile_json(profile: ComponentSubcomponentProfile): Json =
    _json_object(Vector("id" -> Json.fromString(profile.id)), profile.extensions)

  private def _json_object(known: Vector[(String, Json)], extensions: Map[String, Json]): Json = {
    val knownkeys = known.map(_._1).toSet
    Json.obj((known ++ extensions.iterator.filterNot { case (key, _) => knownkeys.contains(key) }.toVector.sortBy(_._1))*)
  }
}
