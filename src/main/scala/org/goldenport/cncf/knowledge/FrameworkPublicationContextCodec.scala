package org.goldenport.cncf.knowledge

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId

/*
 * Deterministic codec for optional framework-publication evidence. It neither
 * interprets nor acquires a Component resource, resolver, or authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object FrameworkPublicationContextCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)

  def decodeC(json: String): Consequence[FrameworkPublicationContext] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(context: FrameworkPublicationContext): String = {
    val encoded = _encode_json(context).noSpaces
    decodeC(encoded).toOption match {
      case Some(_) => encoded
      case None => throw new IllegalArgumentException("Framework publication context failed codec self-validation")
    }
  }

  private[knowledge] def _decode_json(json: Json, context: String): Either[String, FrameworkPublicationContext] =
    for {
      obj <- _object(json, context)
      productversionjson <- _field(obj, "productVersion", context)
      productversion <- _product_version(productversionjson, s"$context.productVersion")
      canonicalurl <- _string(obj, "canonicalUrl", context)
      generation <- _string(obj, "publicationGeneration", context)
      documentid <- _string(obj, "documentId", context)
      sectionid <- _optional_string(obj, "sectionId", context)
      sha256 <- _string(obj, "sha256", context)
      availabilityvalue <- _string(obj, "availability", context)
      availability <- _availability(availabilityvalue, s"$context.availability")
      generatedfromjson <- _field(obj, "generatedFrom", context)
      generatedfrom <- _generated_from(generatedfromjson, s"$context.generatedFrom")
      snapshot <- _optional_snapshot(obj, "documentationComponentSnapshot", context)
      publication = FrameworkPublicationContext(
        productVersion = productversion,
        canonicalUrl = canonicalurl,
        publicationGeneration = generation,
        documentId = documentid,
        sectionId = sectionid,
        sha256 = sha256,
        availability = availability,
        generatedFrom = generatedfrom,
        documentationComponentSnapshot = snapshot,
        extensions = _extensions(
          obj,
          Set("productVersion", "canonicalUrl", "publicationGeneration", "documentId", "sectionId", "sha256", "availability", "generatedFrom", "documentationComponentSnapshot")
        )
      )
      _ <- FrameworkPublicationContext.validateC(publication).toOption.toRight(s"$context violates framework-publication validation")
    } yield publication

  private[knowledge] def _encode_json(context: FrameworkPublicationContext): Json =
    _json_object(
      Vector(
        "productVersion" -> _product_version_json(context.productVersion),
        "canonicalUrl" -> Json.fromString(context.canonicalUrl),
        "publicationGeneration" -> Json.fromString(context.publicationGeneration),
        "documentId" -> Json.fromString(context.documentId),
        "sectionId" -> context.sectionId.map(Json.fromString).getOrElse(Json.Null),
        "sha256" -> Json.fromString(context.sha256),
        "availability" -> Json.fromString(context.availability.code),
        "generatedFrom" -> _generated_from_json(context.generatedFrom),
        "documentationComponentSnapshot" -> context.documentationComponentSnapshot.map(_snapshot_json).getOrElse(Json.Null)
      ),
      context.extensions
    )

  private def _decode(text: String): Either[String, FrameworkPublicationContext] =
    try {
      _strict_json_parser.parse(text).left.map(error => s"Invalid framework publication context JSON: ${error.message}").flatMap(_decode_json(_, "frameworkPublication"))
    } catch {
      case NonFatal(error) => Left(s"Invalid framework publication context: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _product_version(json: Json, context: String): Either[String, FrameworkProductVersion] =
    for {
      obj <- _object(json, context)
      product <- _string(obj, "product", context)
      version <- _string(obj, "version", context)
    } yield FrameworkProductVersion(product, version)

  private def _generated_from(json: Json, context: String): Either[String, FrameworkPublicationGeneratedFrom] =
    for {
      obj <- _object(json, context)
      sourceidentity <- _string(obj, "sourceIdentity", context)
      sourcesha256 <- _string(obj, "sourceSha256", context)
    } yield FrameworkPublicationGeneratedFrom(
      sourceIdentity = sourceidentity,
      sourceSha256 = sourcesha256,
      extensions = _extensions(obj, Set("sourceIdentity", "sourceSha256"))
    )

  private def _optional_snapshot(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[FrameworkDocumentationComponentSnapshot]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) => _snapshot(value, s"$context.$field").map(Some(_))
    }

  private def _snapshot(json: Json, context: String): Either[String, FrameworkDocumentationComponentSnapshot] =
    for {
      obj <- _object(json, context)
      componentid <- _component_id(obj, "componentId", context)
      release <- _string(obj, "logicalRelease", context)
      digest <- _string(obj, "publicationSha256", context)
      availabilityvalue <- _string(obj, "availability", context)
      availability <- _availability(availabilityvalue, s"$context.availability")
    } yield FrameworkDocumentationComponentSnapshot(
      componentId = componentid,
      logicalRelease = release,
      publicationSha256 = digest,
      availability = availability,
      extensions = _extensions(obj, Set("componentId", "logicalRelease", "publicationSha256", "availability"))
    )

  private def _availability(value: String, context: String): Either[String, FrameworkPublicationReferenceAvailability] =
    FrameworkPublicationReferenceAvailability.fromCode(value).toRight(s"$context is not an admitted framework publication reference availability")

  private def _component_id(obj: JsonObject, field: String, context: String): Either[String, ComponentId] =
    _string(obj, field, context).flatMap { value =>
      ComponentId.parseC(value).toOption match {
        case Some(id) if id.name == value => Right(id)
        case _ => Left(s"$context.$field must be a canonical ComponentId")
      }
    }

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be an object")

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

  private def _extensions(obj: JsonObject, known: Set[String]): Map[String, Json] =
    obj.toMap.filterNot { case (key, _) => known.contains(key) }

  private def _product_version_json(value: FrameworkProductVersion): Json =
    Json.obj(
      "product" -> Json.fromString(value.product),
      "version" -> Json.fromString(value.version)
    )

  private def _generated_from_json(value: FrameworkPublicationGeneratedFrom): Json =
    _json_object(
      Vector(
        "sourceIdentity" -> Json.fromString(value.sourceIdentity),
        "sourceSha256" -> Json.fromString(value.sourceSha256)
      ),
      value.extensions
    )

  private def _snapshot_json(value: FrameworkDocumentationComponentSnapshot): Json =
    _json_object(
      Vector(
        "componentId" -> Json.fromString(value.componentId.name),
        "logicalRelease" -> Json.fromString(value.logicalRelease),
        "publicationSha256" -> Json.fromString(value.publicationSha256),
        "availability" -> Json.fromString(value.availability.code)
      ),
      value.extensions
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
}
