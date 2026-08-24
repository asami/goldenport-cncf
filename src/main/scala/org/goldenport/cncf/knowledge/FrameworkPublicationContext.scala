package org.goldenport.cncf.knowledge

import java.net.URI
import java.util.Locale
import scala.util.control.NonFatal

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId

/*
 * Typed, read-only publication evidence for the framework. This evidence is
 * deliberately independent from Phase 58 Component-resource binding and does
 * not create a resolver, resource identity, authority, or execution input.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FrameworkProductVersion(
  product: String,
  version: String
)

enum FrameworkPublicationReferenceAvailability(val code: String) {
  case Local extends FrameworkPublicationReferenceAvailability("local")
  case Installed extends FrameworkPublicationReferenceAvailability("installed")
  case Cached extends FrameworkPublicationReferenceAvailability("cached")
  case Online extends FrameworkPublicationReferenceAvailability("online")
  case Unavailable extends FrameworkPublicationReferenceAvailability("unavailable")
}

object FrameworkPublicationReferenceAvailability {
  def fromCode(code: String): Option[FrameworkPublicationReferenceAvailability] =
    FrameworkPublicationReferenceAvailability.values.find(_.code == code)
}

final case class FrameworkPublicationGeneratedFrom(
  sourceIdentity: String,
  sourceSha256: String,
  extensions: Map[String, Json] = Map.empty
)

final case class FrameworkDocumentationComponentSnapshot(
  componentId: ComponentId,
  logicalRelease: String,
  publicationSha256: String,
  availability: FrameworkPublicationReferenceAvailability,
  extensions: Map[String, Json] = Map.empty
)

enum FrameworkPublicationProjectionFreshness(val code: String) {
  case Current extends FrameworkPublicationProjectionFreshness("current")
  case Stale extends FrameworkPublicationProjectionFreshness("stale")
}

final case class FrameworkPublicationContext(
  productVersion: FrameworkProductVersion,
  canonicalUrl: String,
  publicationGeneration: String,
  documentId: String,
  sectionId: Option[String],
  sha256: String,
  availability: FrameworkPublicationReferenceAvailability,
  generatedFrom: FrameworkPublicationGeneratedFrom,
  documentationComponentSnapshot: Option[FrameworkDocumentationComponentSnapshot] = None,
  extensions: Map[String, Json] = Map.empty
) {
  def projectionFreshnessFor(suppliedSource: FrameworkPublicationGeneratedFrom): FrameworkPublicationProjectionFreshness =
    if (
      generatedFrom.sourceIdentity == suppliedSource.sourceIdentity &&
        generatedFrom.sourceSha256 == suppliedSource.sourceSha256
    ) FrameworkPublicationProjectionFreshness.Current
    else FrameworkPublicationProjectionFreshness.Stale
}

object FrameworkPublicationContext {
  def validateC(context: FrameworkPublicationContext): Consequence[FrameworkPublicationContext] =
    _validate(context).fold(Consequence.argumentInvalid, Consequence.success)

  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _product_token_pattern = "[a-z][a-z0-9-]*".r
  private val _protected_extension_key_aliases = Set(
    "repository",
    "repositoryurl",
    "repositoryuri",
    "repositorylocation",
    "location",
    "physicallocation",
    "normalizedlocation",
    "normalizedrelativepath",
    "normalizedpath",
    "hostpath",
    "physicalsource",
    "physicalpath",
    "path",
    "bytes",
    "content",
    "authorization",
    "credential",
    "credentials",
    "credentialtoken",
    "activation",
    "operation",
    "mcp",
    "deployment",
    "disclosureauthority",
    "activationauthority",
    "operationauthority",
    "mcpauthority",
    "deploymentauthority",
    "resourcebinding",
    "componentknowledgeresourcebinding",
    "resolver",
    "scan",
    "read"
  )
  private val _protected_extension_key_words = Set(
    "repository",
    "location",
    "physical",
    "normalized",
    "path",
    "content",
    "bytes",
    "credential",
    "token",
    "authorization",
    "activation",
    "operation",
    "mcp",
    "deployment",
    "resolver",
    "scan",
    "read"
  )
  private val _protected_extension_key_compound_prefixes = Set(
    "resolver",
    "scan",
    "read"
  )

  private def _validate(context: FrameworkPublicationContext): Either[String, FrameworkPublicationContext] =
    for {
      _ <- _product_version(context.productVersion, "frameworkPublication.productVersion")
      _ <- _canonical_url(context.canonicalUrl, "frameworkPublication.canonicalUrl")
      _ <- _safe_text(context.publicationGeneration, "frameworkPublication.publicationGeneration")
      _ <- _absolute_uri(context.documentId, "frameworkPublication.documentId")
      _ <- context.sectionId.map(_absolute_uri(_, "frameworkPublication.sectionId")).getOrElse(Right(()))
      _ <- _sha256(context.sha256, "frameworkPublication.sha256")
      _ <- _generated_from(context.generatedFrom, "frameworkPublication.generatedFrom")
      _ <- context.documentationComponentSnapshot.map(_snapshot(_, context.sha256, "frameworkPublication.documentationComponentSnapshot")).getOrElse(Right(()))
      _ <- _validate_extensions(context.extensions, "frameworkPublication.extensions")
    } yield context

  private def _product_version(value: FrameworkProductVersion, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(Option(value.product).exists(_product_token_pattern.matches), (), s"$context.product must be a safe product token")
      _ <- _safe_text(value.version, s"$context.version")
    } yield ()

  private def _generated_from(value: FrameworkPublicationGeneratedFrom, context: String): Either[String, Unit] =
    for {
      _ <- _absolute_uri(value.sourceIdentity, s"$context.sourceIdentity")
      _ <- _sha256(value.sourceSha256, s"$context.sourceSha256")
      _ <- _validate_extensions(value.extensions, s"$context.extensions")
    } yield ()

  private def _snapshot(
    value: FrameworkDocumentationComponentSnapshot,
    contextdigest: String,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _component_id(value.componentId.name, s"$context.componentId")
      _ <- _safe_text(value.logicalRelease, s"$context.logicalRelease")
      _ <- _sha256(value.publicationSha256, s"$context.publicationSha256")
      _ <- Either.cond(value.publicationSha256 == contextdigest, (), s"$context.publicationSha256 must equal frameworkPublication.sha256")
      _ <- _validate_extensions(value.extensions, s"$context.extensions")
    } yield ()

  private def _canonical_url(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      val host = Option(uri.getHost)
      Either.cond(
        uri.isAbsolute &&
          uri.getScheme == "https" &&
          host.exists(name => name.nonEmpty && name == name.toLowerCase(Locale.ROOT)) &&
          uri.getRawUserInfo == null &&
          uri.getRawQuery == null &&
          uri.getRawFragment == null &&
          (uri.getPort == -1 || uri.getPort == 443),
        (),
        s"$context must be a canonical absolute HTTPS URL"
      )
    } catch {
      case NonFatal(_) => Left(s"$context must be a canonical absolute HTTPS URL")
    }

  private def _absolute_uri(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond(
        uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty),
        (),
        s"$context must be an absolute URI"
      )
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute URI")
    }

  private def _component_id(value: String, context: String): Either[String, Unit] =
    ComponentId.parseC(value).toOption match {
      case Some(id) if id.name == value => Right(())
      case _ => Left(s"$context must be a canonical ComponentId")
    }

  private def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(text => text.nonEmpty && text == text.trim && !text.exists(_.isControl)), (), s"$context must be non-empty trimmed text")

  private def _sha256(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_sha256_pattern.matches), (), s"$context must be a lowercase SHA-256 digest")

  private def _validate_extensions(extensions: Map[String, Json], context: String): Either[String, Unit] =
    _sequence(extensions.toVector.map { case (key, value) => _validate_extension(key, value, context) })

  private def _validate_extension(key: String, value: Json, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(!_protected_extension_key(key), (), s"$context must not expose protected framework-publication evidence: $key")
      _ <- _validate_extension_json(value, s"$context.$key")
    } yield ()

  private def _protected_extension_key(key: String): Boolean = {
    val normalized = Option(key).map(_.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)).getOrElse("")
    val words = Option(key).toVector.flatMap { value =>
      value
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
        .split("[^A-Za-z0-9]+")
        .toVector
        .map(_.toLowerCase(Locale.ROOT))
    }.filter(_.nonEmpty).toSet
    _protected_extension_key_aliases.contains(normalized) ||
      words.exists(_protected_extension_key_words.contains) ||
      _protected_extension_key_compound_prefixes.exists(normalized.startsWith) ||
      (words.contains("disclosure") && words.contains("authority")) ||
      (normalized.contains("disclosure") && normalized.contains("authority")) ||
      (words.contains("resource") && words.contains("binding")) ||
      (normalized.contains("resource") && normalized.contains("binding"))
  }

  private def _validate_extension_json(value: Json, context: String): Either[String, Unit] =
    value.arrayOrObject(
      Right(()),
      values => _sequence(values.toVector.map(_validate_extension_json(_, context))),
      obj => _validate_extensions(obj.toMap, context)
    )

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft(Right(()): Either[String, Unit]) { (z, x) => z.flatMap(_ => x) }
}
