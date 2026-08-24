package org.goldenport.cncf.knowledge

import java.net.URI
import java.util.Locale
import scala.util.control.NonFatal

import io.circe.Json
import org.goldenport.Consequence

/*
 * Typed, descriptive public metadata for existing Directive and Skill Catalog
 * resources. These values never expose source or rule/profile content, and
 * never create installation, execution, MCP, or disclosure authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
enum PublicMetadataVisibility(val code: String) {
  case Public extends PublicMetadataVisibility("public")
  case Ecosystem extends PublicMetadataVisibility("ecosystem")
  case Project extends PublicMetadataVisibility("project")
  case Internal extends PublicMetadataVisibility("internal")
  case Restricted extends PublicMetadataVisibility("restricted")
}

object PublicMetadataVisibility {
  def fromCode(code: String): Option[PublicMetadataVisibility] =
    PublicMetadataVisibility.values.find(_.code == code)
}

enum PublicDirectiveAuthority(val code: String) {
  case MountedDirectiveRemainsAuthoritative extends PublicDirectiveAuthority("mounted-directive-remains-authoritative")
}

object PublicDirectiveAuthority {
  def fromCode(code: String): Option[PublicDirectiveAuthority] =
    PublicDirectiveAuthority.values.find(_.code == code)
}

enum PublicDirectiveRedaction(val code: String) {
  case SourceAndRuleContentWithheld extends PublicDirectiveRedaction("source-and-rule-content-withheld")
}

object PublicDirectiveRedaction {
  def fromCode(code: String): Option[PublicDirectiveRedaction] =
    PublicDirectiveRedaction.values.find(_.code == code)
}

final case class PublicDirectiveProjection(
  entry: ComponentKnowledgeResourceEntry,
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

object PublicDirectiveProjection {
  def validateC(
    projection: PublicDirectiveProjection,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): Consequence[PublicDirectiveProjection] =
    _validate(projection, manifestResources).fold(Consequence.argumentInvalid, Consequence.success)

  private def _validate(
    projection: PublicDirectiveProjection,
    manifestresources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, PublicDirectiveProjection] =
    for {
      _ <- PublicDirectiveSkillCatalogContextValidation._directive_entry(projection.entry, manifestresources, "manifest.publicDirective.entry")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(projection.directiveId, "manifest.publicDirective.directiveId")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(projection.profileId, "manifest.publicDirective.profileId")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(projection.ruleId, "manifest.publicDirective.ruleId")
      _ <- PublicDirectiveSkillCatalogContextValidation._absolute_uri(projection.origin, "manifest.publicDirective.origin")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(projection.version, "manifest.publicDirective.version")
      _ <- PublicDirectiveSkillCatalogContextValidation._sha256(projection.sourceSha256, "manifest.publicDirective.sourceSha256")
      _ <- Either.cond(projection.sourceSha256 == projection.entry.sha256, (), "manifest.publicDirective.sourceSha256 must match the referenced entry digest")
      _ <- PublicDirectiveSkillCatalogContextValidation._canonical_https(projection.guideReference, "manifest.publicDirective.guideReference")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(projection.extensions, "manifest.publicDirective.extensions")
    } yield projection
}

final case class PublicSkillCatalog(
  entry: ComponentKnowledgeResourceEntry,
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

object PublicSkillCatalog {
  def validateC(
    catalog: PublicSkillCatalog,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): Consequence[PublicSkillCatalog] =
    _validate(catalog, manifestResources).fold(Consequence.argumentInvalid, Consequence.success)

  private def _validate(
    catalog: PublicSkillCatalog,
    manifestresources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, PublicSkillCatalog] =
    for {
      _ <- PublicDirectiveSkillCatalogContextValidation._skill_catalog_entry(catalog.entry, manifestresources, "manifest.skillCatalog.entry")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(catalog.catalogId, "manifest.skillCatalog.catalogId")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(catalog.owner, "manifest.skillCatalog.owner")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(catalog.purpose, "manifest.skillCatalog.purpose")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(catalog.trigger, "manifest.skillCatalog.trigger")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text_vector(catalog.requirements, "manifest.skillCatalog.requirements")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text_vector(catalog.permissions, "manifest.skillCatalog.permissions")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text_vector(catalog.sideEffects, "manifest.skillCatalog.sideEffects")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text_vector(catalog.mcpRequirements, "manifest.skillCatalog.mcpRequirements")
      _ <- PublicDirectiveSkillCatalogContextValidation._canonical_https(catalog.installationReference, "manifest.skillCatalog.installationReference")
      _ <- PublicDirectiveSkillCatalogContextValidation._safe_text(catalog.version, "manifest.skillCatalog.version")
      _ <- PublicDirectiveSkillCatalogContextValidation._sha256(catalog.sourceSha256, "manifest.skillCatalog.sourceSha256")
      _ <- Either.cond(catalog.sourceSha256 == catalog.entry.sha256, (), "manifest.skillCatalog.sourceSha256 must match the referenced entry digest")
      _ <- PublicDirectiveSkillCatalogContextValidation._validate_extensions(catalog.extensions, "manifest.skillCatalog.extensions")
    } yield catalog
}

private[knowledge] object PublicDirectiveSkillCatalogContextValidation {
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _protected_extension_key_aliases = Set(
    "repository", "repositoryurl", "repositoryuri", "repositorylocation",
    "location", "physicallocation", "normalizedlocation", "normalizedrelativepath",
    "normalizedpath", "hostpath", "physicalsource", "physicalpath", "path",
    "bytes", "content", "authorization", "credential", "credentials",
    "credentialtoken", "approval", "approvals", "configuration", "config",
    "install", "installation", "activation", "execution", "operation", "mcp",
    "deployment", "disclosureauthority", "activationauthority", "executionauthority",
    "operationauthority", "mcpauthority", "deploymentauthority", "resourcebinding",
    "componentknowledgeresourcebinding", "resolver", "scan", "read",
    "source", "sourcetext", "rawsource", "rawsourcetext",
    "profile", "profiletext", "rawprofile", "rawprofiletext",
    "rule", "ruletext", "rawrule", "rawruletext",
    "prompt", "prompttext", "rawprompt", "rawprompttext"
  )
  private val _protected_extension_key_prefixes = Set(
    "repository", "location", "physical", "normalized", "path", "content",
    "bytes", "credential", "token", "authorization", "approval", "config",
    "install", "activation", "execution", "operation", "mcp", "deployment",
    "resolver", "scan", "read",
    "source", "rawsource", "profile", "rawprofile", "rule", "rawrule",
    "prompt", "rawprompt"
  )

  def _directive_entry(
    entry: ComponentKnowledgeResourceEntry,
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _manifest_entry(entry, manifestresources, context)
      _ <- Either.cond(
        entry.kind == ComponentKnowledgeResourceKind.Directive &&
          entry.role == ComponentKnowledgeResourceRole.Directive &&
          entry.mediaType == ComponentKnowledgeMediaType.ApplicationYaml,
        (),
        s"$context must be an admitted Directive / directive / application-yaml entry"
      )
    } yield ()

  def _skill_catalog_entry(
    entry: ComponentKnowledgeResourceEntry,
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _manifest_entry(entry, manifestresources, context)
      _ <- Either.cond(
        entry.kind == ComponentKnowledgeResourceKind.SkillCatalog &&
          entry.role == ComponentKnowledgeResourceRole.SkillCatalog &&
          entry.mediaType == ComponentKnowledgeMediaType.ApplicationJson,
        (),
        s"$context must be an admitted SkillCatalog / skill-catalog / application-json entry"
      )
    } yield ()

  def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(
      Option(value).exists(text => text.nonEmpty && text == text.trim && !text.exists(_.isControl)),
      (),
      s"$context must be non-empty trimmed safe text"
    )

  def _safe_text_vector(values: Vector[String], context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(values.nonEmpty, (), s"$context must not be empty")
      _ <- Either.cond(values.distinct.size == values.size, (), s"$context must not repeat values")
      _ <- _sequence(values.zipWithIndex.map { case (value, index) => _safe_text(value, s"$context[$index]") })
    } yield ()

  def _sha256(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_sha256_pattern.matches), (), s"$context must be a lowercase SHA-256 digest")

  def _absolute_uri(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond(
        Option(value).exists(text => text == text.trim && !text.exists(_.isControl)) &&
          uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty),
        (),
        s"$context must be an absolute URI"
      )
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute URI")
    }

  def _canonical_https(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      val host = Option(uri.getHost)
      Either.cond(
        Option(value).exists(text => text == text.trim && !text.exists(_.isControl)) &&
          uri.isAbsolute &&
          uri.getScheme == "https" &&
          host.exists(name => name.nonEmpty && name == name.toLowerCase(Locale.ROOT)) &&
          uri.getRawUserInfo == null &&
          uri.getRawQuery == null &&
          uri.getRawFragment == null &&
          (uri.getPort == -1 || uri.getPort == 443),
        (),
        s"$context must be a canonical absolute HTTPS reference"
      )
    } catch {
      case NonFatal(_) => Left(s"$context must be a canonical absolute HTTPS reference")
    }

  def _validate_extensions(extensions: Map[String, Json], context: String): Either[String, Unit] =
    _sequence(extensions.toVector.map { case (key, value) => _validate_extension(key, value, context) })

  private def _manifest_entry(
    entry: ComponentKnowledgeResourceEntry,
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    manifestresources.filter(_ == entry) match {
      case Vector(_) => Right(())
      case Vector() => Left(s"$context must exactly reference one existing manifest resource entry")
      case _ => Left(s"$context must not match more than one manifest resource entry")
    }

  private def _validate_extension(key: String, value: Json, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(!_protected_extension_key(key), (), s"$context must not expose protected Directive, Skill, authority, or resolver evidence: $key")
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
      _protected_extension_key_prefixes.exists(prefix => normalized.startsWith(prefix)) ||
      words.exists(word => _protected_extension_key_prefixes.exists(prefix => word.startsWith(prefix))) ||
      (words.contains("disclosure") && words.contains("authority")) ||
      normalized.contains("disclosureauthority") ||
      (words.contains("resource") && words.contains("binding")) ||
      normalized.contains("resourcebinding")
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
