package org.goldenport.cncf.repository

import java.nio.file.Paths
import java.time.Instant
import scala.util.Try
import io.circe.{ACursor, Decoder, Json}

/*
 * CNCF-owned discovery contract for explicit CAR/SAR repository indexes.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
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
  latestSnapshot: Option[String]
) {
  def identity: (String, String) = kind.name -> artifactId

  def validate: Either[String, Unit] =
    for {
      _ <- ComponentRepositoryIndexValidation.validateArtifactId(artifactId)
      _ <- ComponentRepositoryIndexValidation.validateStatus(status)
      _ <- ComponentRepositoryIndexValidation.validateCatalogPath(kind, artifactId, catalog)
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
  val SCHEMA_VERSION = "cncf.component-repository-index.v1"
  val PUBLIC_PATH = "repository/catalog/index.json"
  val SCHEMA_RESOURCE_PATH = "META-INF/cncf/component-repository-index.schema.json"

  def parse(text: String): Either[String, ComponentRepositoryIndex] =
    for {
      json <- io.circe.parser.parse(text).left.map(x => s"Invalid component repository index JSON: ${x.message}")
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

  private def _entry(cursor: io.circe.HCursor, index: Int): Either[String, ComponentRepositoryIndexEntry] = {
    val context = s"artifact[$index]"
    for {
      _ <- _only_fields(
        cursor,
        Set("kind", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"),
        context
      )
      kindname <- _required[String](cursor, "kind", context)
      kind <- ComponentArtifactKind.parse(kindname)
      artifactid <- _required[String](cursor, "artifactId", context)
      catalog <- _required[String](cursor, "catalog", context)
      status <- _required[String](cursor, "status", context)
      recommended <- _optional[String](cursor, "recommended", context)
      lateststable <- _optional[String](cursor, "latestStable", context)
      latestsnapshot <- _optional[String](cursor, "latestSnapshot", context)
      result = ComponentRepositoryIndexEntry(kind, artifactid, catalog, status, recommended, lateststable, latestsnapshot)
      _ <- result.validate
    } yield result
  }

  private def _entry_json(entry: ComponentRepositoryIndexEntry): Json = {
    val fields = Vector(
      Some("kind" -> Json.fromString(entry.kind.name)),
      Some("artifactId" -> Json.fromString(entry.artifactId)),
      Some("catalog" -> Json.fromString(entry.catalog)),
      Some("status" -> Json.fromString(entry.status)),
      entry.recommended.map(x => "recommended" -> Json.fromString(x)),
      entry.latestStable.map(x => "latestStable" -> Json.fromString(x)),
      entry.latestSnapshot.map(x => "latestSnapshot" -> Json.fromString(x))
    ).flatten
    Json.obj(fields*)
  }

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, A] =
    cursor.get[A](field).left.map(x => s"Component repository index $context requires $field: ${x.message}")

  private def _optional[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, Option[A]] =
    cursor.get[Option[A]](field).left.map(x => s"Invalid component repository index $context $field: ${x.message}")

  private def _only_fields(cursor: io.circe.HCursor, expected: Set[String], context: String): Either[String, Unit] = {
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
}

private object ComponentRepositoryIndexValidation {
  private val _artifact_id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _statuses = Set("active", "deprecated", "disabled")
  private val _catalog_extensions = Vector(".yaml", ".yml", ".json")

  def validateArtifactId(value: String): Either[String, Unit] =
    Either.cond(
      _artifact_id_pattern.matches(value),
      (),
      s"Invalid component repository artifactId: $value"
    )

  def validateStatus(value: String): Either[String, Unit] =
    Either.cond(
      _statuses.contains(value),
      (),
      s"Invalid component repository artifact status: $value"
    )

  def validateCatalogPath(kind: ComponentArtifactKind, artifactid: String, value: String): Either[String, Unit] = {
    val normalized = value.replace('\\', '/')
    val path = Try(Paths.get(value)).toOption
    val segments = normalized.split('/').toVector
    val filename = segments.lastOption.getOrElse("")
    val extension = _catalog_extensions.find(filename.endsWith)
    val stem = extension.map(filename.stripSuffix).getOrElse("")
    val valid =
      value == normalized &&
        path.exists(x => !x.isAbsolute && x.normalize.toString.replace('\\', '/') == value) &&
        !segments.contains("..") &&
        segments == Vector(kind.name, filename) &&
        stem == artifactid
    Either.cond(valid, (), s"Invalid component repository catalog path for ${kind.name}:$artifactid: $value")
  }

  def validateSelector(name: String, value: Option[String]): Either[String, Unit] =
    Either.cond(value.forall(_.trim.nonEmpty), (), s"Component repository index $name must not be empty")

  def validateEntries(entries: Vector[ComponentRepositoryIndexEntry]): Either[String, Unit] = {
    val identities = entries.map(_.identity)
    val duplicates = identities.groupBy(identity).collect { case (identity, xs) if xs.size > 1 => s"${identity._1}:${identity._2}" }.toVector.sorted
    for {
      _ <- Either.cond(duplicates.isEmpty, (), s"Duplicate component repository artifacts: ${duplicates.mkString(", ")}")
      _ <- entries.foldLeft(Right(()): Either[String, Unit]) { (z, entry) => z.flatMap(_ => entry.validate) }
    } yield ()
  }
}
