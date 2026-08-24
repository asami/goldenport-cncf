package org.goldenport.cncf.knowledge

import java.util.Locale

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Typed, read-only portable model and diagram evidence. Every entry is an
 * exact reference to existing manifest resource evidence; this context never
 * constructs identities, reads resources, or grants resolver authority.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PortableModelResource(
  entry: ComponentKnowledgeResourceEntry,
  extensions: Map[String, Json] = Map.empty
)

final case class PortableDiagramGeneratedFrom(
  sourceIdentity: ComponentResourceLogicalIdentity,
  sourceSha256: String,
  extensions: Map[String, Json] = Map.empty
)

final case class PortableDiagramResource(
  entry: ComponentKnowledgeResourceEntry,
  generatedFrom: Vector[PortableDiagramGeneratedFrom],
  extensions: Map[String, Json] = Map.empty
)

final case class PortableModelResourceContext(
  models: Vector[PortableModelResource],
  diagrams: Vector[PortableDiagramResource],
  extensions: Map[String, Json] = Map.empty
)

object PortableModelResourceContext {
  def validateC(
    context: PortableModelResourceContext,
    manifestResources: Vector[ComponentKnowledgeResourceEntry]
  ): Consequence[PortableModelResourceContext] =
    _validate(context, manifestResources).fold(Consequence.argumentInvalid, Consequence.success)

  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _protected_extension_key_aliases = Set(
    "repository", "repositoryurl", "repositoryuri", "repositorylocation",
    "location", "physicallocation", "normalizedlocation", "normalizedrelativepath",
    "normalizedpath", "hostpath", "physicalsource", "physicalpath", "path",
    "bytes", "content", "authorization", "credential", "credentials",
    "credentialtoken", "activation", "operation", "mcp", "deployment",
    "disclosureauthority", "activationauthority", "operationauthority",
    "mcpauthority", "deploymentauthority", "resourcebinding",
    "componentknowledgeresourcebinding", "resolver", "scan", "read"
  )
  private val _protected_extension_key_words = Set(
    "repository", "location", "physical", "normalized", "path", "content",
    "bytes", "credential", "token", "authorization", "activation", "operation",
    "mcp", "deployment", "resolver", "scan", "read"
  )

  private def _validate(
    context: PortableModelResourceContext,
    manifestresources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, PortableModelResourceContext] = {
    val entries = context.models.map(_.entry) ++ context.diagrams.map(_.entry)
    val identities = entries.map(_.binding.logicalIdentity)
    val paths = entries.map(_.logicalPath)
    for {
      _ <- Either.cond(entries.nonEmpty, (), "manifest.modelResources must not be empty")
      _ <- Either.cond(identities.distinct.size == identities.size, (), "manifest.modelResources must not repeat a logical identity")
      _ <- Either.cond(paths.distinct.size == paths.size, (), "manifest.modelResources must not repeat a logical path")
      _ <- _validate_extensions(context.extensions, "manifest.modelResources.extensions")
      _ <- _sequence(context.models.zipWithIndex.map { case (value, index) =>
        _model(value, manifestresources, s"manifest.modelResources.models[$index]")
      })
      _ <- _sequence(context.diagrams.zipWithIndex.map { case (value, index) =>
        _diagram(value, context.models, manifestresources, s"manifest.modelResources.diagrams[$index]")
      })
    } yield context
  }

  private def _model(
    value: PortableModelResource,
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _manifest_entry(value.entry, manifestresources, s"$context.entry")
      _ <- Either.cond(_is_model(value.entry), (), s"$context.entry must be an admitted portable model resource")
      _ <- _validate_extensions(value.extensions, s"$context.extensions")
    } yield ()

  private def _diagram(
    value: PortableDiagramResource,
    models: Vector[PortableModelResource],
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] = {
    val sources = value.generatedFrom.map(_.sourceIdentity)
    for {
      _ <- _manifest_entry(value.entry, manifestresources, s"$context.entry")
      _ <- Either.cond(_is_diagram(value.entry), (), s"$context.entry must be an admitted portable diagram resource")
      _ <- Either.cond(value.generatedFrom.nonEmpty, (), s"$context.generatedFrom must not be empty")
      _ <- Either.cond(sources.distinct.size == sources.size, (), s"$context.generatedFrom must not repeat a model logical identity")
      _ <- _sequence(value.generatedFrom.zipWithIndex.map { case (source, index) =>
        _generated_from(source, models, s"$context.generatedFrom[$index]")
      })
      _ <- Either.cond(
        value.entry.kind != ComponentKnowledgeResourceKind.StateDiagram ||
          value.generatedFrom.exists(source => _model_for(source.sourceIdentity, models).exists(_.entry.kind == ComponentKnowledgeResourceKind.StateMachine)),
        (),
        s"$context StateDiagram requires a StateMachine generated-from source"
      )
      _ <- _validate_extensions(value.extensions, s"$context.extensions")
    } yield ()
  }

  private def _generated_from(
    value: PortableDiagramGeneratedFrom,
    models: Vector[PortableModelResource],
    context: String
  ): Either[String, Unit] =
    _model_for(value.sourceIdentity, models) match {
      case Some(model) =>
        for {
          _ <- Either.cond(value.sourceSha256 == model.entry.sha256, (), s"$context.sourceSha256 must match the admitted model digest")
          _ <- _sha256(value.sourceSha256, s"$context.sourceSha256")
          _ <- _validate_extensions(value.extensions, s"$context.extensions")
        } yield ()
      case None => Left(s"$context.sourceIdentity must identify an admitted portable model resource")
    }

  private def _manifest_entry(
    entry: ComponentKnowledgeResourceEntry,
    manifestresources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Unit] =
    Either.cond(manifestresources.contains(entry), (), s"$context must exactly reference an existing manifest resource entry")

  private def _model_for(
    identity: ComponentResourceLogicalIdentity,
    models: Vector[PortableModelResource]
  ): Option[PortableModelResource] =
    models.find(_.entry.binding.logicalIdentity == identity)

  private def _is_model(entry: ComponentKnowledgeResourceEntry): Boolean =
    entry.role == ComponentKnowledgeResourceRole.Model &&
      entry.mediaType == ComponentKnowledgeMediaType.ApplicationJson &&
      (entry.kind == ComponentKnowledgeResourceKind.Entity ||
        entry.kind == ComponentKnowledgeResourceKind.Powertype ||
        entry.kind == ComponentKnowledgeResourceKind.StateMachine ||
        entry.kind == ComponentKnowledgeResourceKind.Value ||
        entry.kind == ComponentKnowledgeResourceKind.Datatype ||
        entry.kind == ComponentKnowledgeResourceKind.Relationship)

  private def _is_diagram(entry: ComponentKnowledgeResourceEntry): Boolean =
    entry.role == ComponentKnowledgeResourceRole.Diagram &&
      entry.mediaType == ComponentKnowledgeMediaType.ImageSvgXml &&
      (entry.kind == ComponentKnowledgeResourceKind.ClassDiagram || entry.kind == ComponentKnowledgeResourceKind.StateDiagram)

  private def _sha256(value: String, context: String): Either[String, Unit] =
    Either.cond(Option(value).exists(_sha256_pattern.matches), (), s"$context must be a lowercase SHA-256 digest")

  private def _validate_extensions(extensions: Map[String, Json], context: String): Either[String, Unit] =
    _sequence(extensions.toVector.map { case (key, value) => _validate_extension(key, value, context) })

  private def _validate_extension(key: String, value: Json, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(!_protected_extension_key(key), (), s"$context must not expose protected model-resource evidence: $key")
      _ <- _validate_extension_json(value, s"$context.$key")
    } yield ()

  private def _protected_extension_key(key: String): Boolean = {
    val normalized = Option(key).map(_.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)).getOrElse("")
    val words = Option(key).toVector.flatMap { value =>
      value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
        .split("[^A-Za-z0-9]+").toVector.map(_.toLowerCase(Locale.ROOT))
    }.filter(_.nonEmpty).toSet
    _protected_extension_key_aliases.contains(normalized) ||
      words.exists(_protected_extension_key_words.contains) ||
      Set("resolver", "scan", "read").exists(normalized.startsWith) ||
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
