package org.goldenport.cncf.repository

import java.nio.file.Paths
import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal
import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.jawn.JawnParser
import org.goldenport.cncf.component.identity.{ComponentId => SharedComponentId, ComponentIdentityResult, ComponentLocalId, ComponentNamespace, ComponentReleaseCoordinate}

/*
 * CNCF-owned discovery contract for explicit CAR/SAR repository indexes.
 *
 * @since   Jul. 21, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait ComponentArtifactKind {
  def name: String
}

object ComponentArtifactKind {
  case object Car extends ComponentArtifactKind {
    val name = "car"
  }

  case object Sar extends ComponentArtifactKind {
    val name = "sar"
  }

  val values: Vector[ComponentArtifactKind] = Vector(Car, Sar)

  def parse(value: String): Either[String, ComponentArtifactKind] =
    values.find(_.name == value).toRight(s"Unsupported component artifact kind: $value")
}

final case class ComponentRepositoryIndexEntry(
  kind: ComponentArtifactKind,
  artifactId: String,
  catalog: String,
  status: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String],
  namespace: Option[String] = None,
  id: Option[String] = None
) {
  def identity: (String, String, String) =
    if (kind == ComponentArtifactKind.Car)
      (kind.name, namespace.getOrElse(""), id.getOrElse(""))
    else
      (kind.name, "", artifactId)

  def validate: Either[String, Unit] =
    for {
      _ <- ComponentRepositoryIndexValidation.validateArtifactId(artifactId)
      _ <- ComponentRepositoryIndexValidation.validateStatus(status)
      coordinate <- ComponentRepositoryIndexValidation.validateIdentity(this)
      _ <- ComponentRepositoryIndexValidation.validateCatalogPath(this, coordinate)
      _ <- ComponentRepositoryIndexValidation.validateSelector("recommended", recommended)
      _ <- ComponentRepositoryIndexValidation.validateSelector("latestStable", latestStable)
      _ <- ComponentRepositoryIndexValidation.validateSelector("latestSnapshot", latestSnapshot)
    } yield ()
}

final case class ComponentRepositoryIndex(
  schemaVersion: String,
  generatedAt: Instant,
  artifacts: Vector[ComponentRepositoryIndexEntry]
) {
  def normalized: ComponentRepositoryIndex =
    copy(artifacts = artifacts.sortBy(_.identity))

  def validate: Either[String, Unit] =
    for {
      _ <- Either.cond(
        schemaVersion == ComponentRepositoryIndex.SCHEMA_VERSION,
        (),
        s"Unsupported component repository index schemaVersion: $schemaVersion"
      )
      _ <- ComponentRepositoryIndexValidation.validateEntries(artifacts)
    } yield ()

  def toJson: Json = ComponentRepositoryIndex.toJson(this)

  def render: String = ComponentRepositoryIndex.render(this)
}

object ComponentRepositoryIndex {
  val SCHEMA_VERSION = "cncf.component-repository-index.v2"
  val PUBLIC_PATH = "repository/catalog/index.json"
  val SCHEMA_RESOURCE_PATH = "META-INF/cncf/component-repository-index.schema.json"

  def parse(text: String): Either[String, ComponentRepositoryIndex] =
    for {
      json <- _parse_json(text)
      cursor = json.hcursor
      _ <- _only_fields(cursor, Set("schemaVersion", "generatedAt", "artifacts"), "index")
      schemaversion <- _required[String](cursor, "schemaVersion", "index")
      generatedtext <- _required[String](cursor, "generatedAt", "index")
      generatedat <- Try(Instant.parse(generatedtext)).toEither.left.map(_ => s"Invalid component repository index generatedAt: $generatedtext")
      entryjsons <- _required[Vector[Json]](cursor, "artifacts", "index")
      entries <- _sequence(entryjsons.zipWithIndex.map { case (entry, index) => _entry(entry.hcursor, index) })
      result = ComponentRepositoryIndex(schemaversion, generatedat, entries).normalized
      _ <- result.validate
    } yield result

  def toJson(index: ComponentRepositoryIndex): Json = {
    val normalized = index.normalized
    normalized.validate.fold(message => throw new IllegalArgumentException(message), identity)
    Json.obj(
      "schemaVersion" -> Json.fromString(normalized.schemaVersion),
      "generatedAt" -> Json.fromString(normalized.generatedAt.toString),
      "artifacts" -> Json.arr(normalized.artifacts.map(_entry_json)*)
    )
  }

  def render(index: ComponentRepositoryIndex): String =
    toJson(index).spaces2 + "\n"

  private def _parse_json(text: String): Either[String, Json] =
    try _strict_json_parser.parse(text).left.map(_json_failure)
    catch {
      case NonFatal(error) => Left(_json_failure(error))
    }

  private def _json_failure(error: Throwable): String = {
    val messages = _json_error_messages(error)
    val detail = messages.headOption.getOrElse(error.getClass.getSimpleName)
    _duplicate_json_field(messages.mkString(" ")).fold(s"Invalid component repository index JSON: $detail") { field =>
      s"component.repository-index.duplicate-field source=index path=json field=$field"
    }
  }

  private def _json_error_messages(error: Throwable): Vector[String] =
    Option(error).toVector.flatMap { value =>
      Option(value.getMessage).toVector ++ Option(value.getCause).filterNot(_ == value).toVector.flatMap(_json_error_messages)
    }

  private def _entry(cursor: HCursor, index: Int): Either[String, ComponentRepositoryIndexEntry] = {
    val context = s"artifact[$index]"
    for {
      _ <- _only_fields(
        cursor,
        Set("kind", "namespace", "id", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"),
        context
      )
      kindname <- _required[String](cursor, "kind", context)
      kind <- ComponentArtifactKind.parse(kindname)
      namespace <- _optional[String](cursor, "namespace", context)
      id <- _optional[String](cursor, "id", context)
      artifactid <- _required[String](cursor, "artifactId", context)
      catalog <- _required[String](cursor, "catalog", context)
      status <- _required[String](cursor, "status", context)
      recommended <- _optional[String](cursor, "recommended", context)
      lateststable <- _optional[String](cursor, "latestStable", context)
      latestsnapshot <- _optional[String](cursor, "latestSnapshot", context)
      result = ComponentRepositoryIndexEntry(kind, artifactid, catalog, status, recommended, lateststable, latestsnapshot, namespace, id)
      _ <- result.validate
    } yield result
  }

  private def _entry_json(entry: ComponentRepositoryIndexEntry): Json = {
    val fields = Vector(
      Some("kind" -> Json.fromString(entry.kind.name)),
      entry.namespace.map(value => "namespace" -> Json.fromString(value)),
      entry.id.map(value => "id" -> Json.fromString(value)),
      Some("artifactId" -> Json.fromString(entry.artifactId)),
      Some("catalog" -> Json.fromString(entry.catalog)),
      Some("status" -> Json.fromString(entry.status)),
      entry.recommended.map(value => "recommended" -> Json.fromString(value)),
      entry.latestStable.map(value => "latestStable" -> Json.fromString(value)),
      entry.latestSnapshot.map(value => "latestSnapshot" -> Json.fromString(value))
    ).flatten
    Json.obj(fields*)
  }

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, A] =
    cursor.get[A](field).left.map(error => s"Component repository index $context requires $field: ${error.message}")

  private def _optional[A: Decoder](cursor: HCursor, field: String, context: String): Either[String, Option[A]] =
    cursor.downField(field).focus match {
      case None => Right(None)
      case Some(json) if json.isNull => Left(s"Component repository index $context $field must not be null")
      case Some(_) => cursor.get[A](field).left.map(error => s"Invalid component repository index $context $field: ${error.message}").map(Some(_))
    }

  private def _only_fields(cursor: HCursor, expected: Set[String], context: String): Either[String, Unit] = {
    val unknown = cursor.keys.toVector.flatten.filterNot(expected).sorted
    Either.cond(unknown.isEmpty, (), s"Unknown component repository index $context fields: ${unknown.mkString(", ")}")
  }

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }

  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
  private val _duplicate_field_patterns = Vector(
    """(?i)duplicate\s+key\s+name\s+found\s*:\s*[\"']?([^\"'\s,}\]]+)""".r,
    """(?i)duplicate(?:\s+json)?\s+(?:field|key)(?:\s+in\s+object)?\s*[:=]\s*[\"']?([^\"'\s,}\]]+)""".r,
    """(?i)duplicate(?:\s+json)?\s+(?:field|key)\s+[\"']([^\"']+)[\"']""".r,
    """(?i)duplicate.*?(?:field|key).*?[\"']([^\"']+)[\"']""".r
  )

  private def _duplicate_json_field(message: String): Option[String] =
    _duplicate_field_patterns.iterator.flatMap(_.findFirstMatchIn(message).map(_.group(1))).map(_.trim).find(_.nonEmpty)
}

private object ComponentRepositoryIndexValidation {
  private val _artifact_id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _statuses = Set("active", "deprecated", "disabled")
  private val _catalog_extensions = Vector(".yaml", ".yml", ".json")

  def validateArtifactId(value: String): Either[String, Unit] =
    Either.cond(
      value != null && _artifact_id_pattern.matches(value),
      (),
      s"Invalid component repository artifactId: $value"
    )

  def validateStatus(value: String): Either[String, Unit] =
    Either.cond(
      value != null && _statuses.contains(value),
      (),
      s"Invalid component repository artifact status: $value"
    )

  def validateIdentity(entry: ComponentRepositoryIndexEntry): Either[String, Option[ComponentReleaseCoordinate]] =
    entry.kind match {
      case ComponentArtifactKind.Car =>
        for {
          namespace <- entry.namespace.toRight("component.release-coordinate.mismatch source=index expected=namespace actual=missing")
          id <- entry.id.toRight("component.release-coordinate.mismatch source=index expected=id actual=missing")
          sharednamespace <- _shared(ComponentNamespace.parse(namespace))
          sharedid <- _shared(ComponentLocalId.parse(id))
          coordinate <- _shared(ComponentReleaseCoordinate.create(SharedComponentId.of(sharednamespace, sharedid), "0.0.0"))
          _ <- Either.cond(
            coordinate.mavenArtifactId() == entry.artifactId,
            (),
            s"component.release-coordinate.mismatch source=index expected=${coordinate.mavenArtifactId()} actual=${entry.artifactId} field=artifactId"
          )
        } yield Some(coordinate)
      case ComponentArtifactKind.Sar =>
        Either.cond(
          entry.namespace.isEmpty && entry.id.isEmpty,
          None,
          "SAR index entry must not carry component namespace or id"
        )
    }

  def validateCatalogPath(entry: ComponentRepositoryIndexEntry, coordinate: Option[ComponentReleaseCoordinate]): Either[String, Unit] = {
    val normalized = Option(entry.catalog).map(_.replace('\\', '/')).getOrElse("")
    val path = Try(Paths.get(entry.catalog)).toOption
    val segments = normalized.split('/').toVector
    val filename = segments.lastOption.getOrElse("")
    val extension = _catalog_extensions.find(filename.endsWith)
    val stem = extension.map(filename.stripSuffix).getOrElse("")
    val valid = entry.kind match {
      case ComponentArtifactKind.Car => coordinate.exists { value =>
        normalized == entry.catalog &&
          path.exists(candidate => !candidate.isAbsolute && candidate.normalize.toString.replace('\\', '/') == entry.catalog) &&
          !segments.contains("..") &&
          entry.catalog == value.carCatalogRelativePath()
      }
      case ComponentArtifactKind.Sar =>
        normalized == entry.catalog &&
          path.exists(candidate => !candidate.isAbsolute && candidate.normalize.toString.replace('\\', '/') == entry.catalog) &&
          !segments.contains("..") &&
          segments == Vector(entry.kind.name, filename) &&
          stem == entry.artifactId
    }
    Either.cond(valid, (), s"Invalid component repository catalog path for ${entry.kind.name}:${entry.artifactId}: ${entry.catalog}")
  }

  def validateSelector(name: String, value: Option[String]): Either[String, Unit] =
    Either.cond(value.forall(_.trim.nonEmpty), (), s"Component repository index $name must not be empty")

  def validateEntries(entries: Vector[ComponentRepositoryIndexEntry]): Either[String, Unit] = {
    val identities = entries.map(_.identity)
    val duplicates = identities.groupBy(identity).collect {
      case (identity, xs) if xs.size > 1 => identity.productIterator.mkString(":")
    }.toVector.sorted
    for {
      _ <- Either.cond(duplicates.isEmpty, (), s"Duplicate component repository artifacts: ${duplicates.mkString(", ")}")
      _ <- entries.foldLeft(Right(()): Either[String, Unit]) { (z, entry) => z.flatMap(_ => entry.validate) }
    } yield ()
  }

  private def _shared[A](result: ComponentIdentityResult[A]): Either[String, A] =
    if (result.isSuccess()) Right(result.value().get())
    else {
      val error = result.error().get()
      Left(s"${error.code()}: ${error.message()}")
    }
}
