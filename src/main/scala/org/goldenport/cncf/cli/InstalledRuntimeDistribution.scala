package org.goldenport.cncf.cli

import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.util.control.NonFatal
import io.circe.{HCursor, Json}
import io.circe.parser.parse
import org.goldenport.Consequence

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InstalledRuntimeDistribution private (
  manifest: Path,
  installationId: UUID,
  updateId: UUID,
  repositories: Vector[String],
  modules: Vector[InstalledRuntimeDistribution.Module],
  component: InstalledRuntimeDistribution.Component
)

object InstalledRuntimeDistribution {
  val KIND = "cncf.installed-runtime.v1"
  val APPLICATION = "sm-workflow"
  val ROOT_COORDINATE = "org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT"
  val COMPONENT_IDENTITY = "org.simplemodeling.textus.SmWorkflow"
  val COMPONENT_COORDINATE = "org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT"

  final case class Module(coordinate: String, path: Path, source: Path)
  final case class Component(identity: String, coordinate: String, path: Path, source: Path)

  /** Pure historical metadata decoding. The locator and referenced binaries
    * need not exist. This value grants no bootstrap or SBT execution binding.
    */
  def readHistoricalMetadataC(manifest: Path, text: String): Consequence[InstalledRuntimeDistribution] = {
    val result = try _read_metadata(manifest, text) catch {
      case NonFatal(_) => Left("Historical installed runtime metadata is invalid")
    }
    result.fold(Consequence.resourceInvalid(_), Consequence.success)
  }

  private def _read_metadata(manifest: Path, text: String): Either[String, InstalledRuntimeDistribution] =
    for {
      _ <- _require(manifest != null && manifest.isAbsolute, "Installed runtime manifest must be absolute")
      concrete = manifest.normalize()
      root = concrete.getParent
      _ <- _require(root != null && root.getFileName != null, "Historical installed runtime locator has no installation directory")
      json <- parse(text).left.map(_ => "Installed runtime manifest is invalid JSON")
      cursor = json.hcursor
      kind <- _string(cursor, "kind")
      _ <- _require(kind == KIND, "Unsupported installed runtime manifest kind")
      installation <- _uuid(cursor, "installation_id")
      update <- _uuid(cursor, "update_id")
      _ <- _require(root.getFileName.toString == installation.toString,
        "Installed runtime directory must be its installation UUID")
      application <- _string(cursor, "application")
      _ <- _require(application == APPLICATION, "Unsupported installed runtime application")
      coordinate <- _string(cursor, "root_coordinate")
      _ <- _require(coordinate == ROOT_COORDINATE, "Unsupported installed runtime root coordinate")
      repositories <- cursor.get[Vector[String]]("repositories").left.map(_ => "Invalid installed runtime repositories")
      _ <- _require(repositories.nonEmpty && repositories.forall(_valid_text), "Invalid installed runtime repositories")
      entries <- cursor.get[Vector[Json]]("modules").left.map(_ => "Invalid installed runtime modules")
      modules <- _sequence(entries.map(entry => _module(root, entry.hcursor)))
      _ <- _require(modules.nonEmpty && modules.map(_.coordinate).distinct.size == modules.size &&
        modules.map(_.path).distinct.size == modules.size && modules.exists(_.coordinate == ROOT_COORDINATE),
        "Installed runtime requires distinct modules and its root coordinate")
      componentjson <- cursor.get[Json]("component").left.map(_ => "Invalid installed runtime component")
      component <- _component(root, componentjson.hcursor)
    } yield InstalledRuntimeDistribution(concrete, installation, update, repositories, modules, component)

  private def _module(root: Path, cursor: HCursor): Either[String, Module] =
    for {
      coordinate <- _string(cursor, "coordinate")
      _ <- _require(_valid_coordinate(coordinate), "Invalid installed runtime module coordinate")
      relative <- _string(cursor, "path")
      _ <- _require(relative.matches("runtime/[0-9]{4}\\.jar"), "Invalid installed runtime module path")
      path = root.resolve(relative).normalize()
      source <- _source(cursor)
    } yield Module(coordinate, path, source)

  private def _component(root: Path, cursor: HCursor): Either[String, Component] =
    for {
      identity <- _string(cursor, "identity")
      coordinate <- _string(cursor, "coordinate")
      relative <- _string(cursor, "path")
      _ <- _require(identity == COMPONENT_IDENTITY && coordinate == COMPONENT_COORDINATE &&
        relative == "components/textus-sm-workflow.car", "Unsupported installed runtime component")
      path = root.resolve(relative).normalize()
      source <- _source(cursor)
    } yield Component(identity, coordinate, path, source)

  private def _source(cursor: HCursor): Either[String, Path] =
    for {
      value <- _string(cursor, "source")
      path = Paths.get(value)
      _ <- _require(path.isAbsolute, "Installed runtime source provenance must be absolute")
    } yield path

  private def _valid_coordinate(value: String): Boolean = {
    val parts = value.split(":", -1).toVector
    (parts.size == 3 || (parts.size == 5 && parts(2) == "jar")) && parts.forall(_valid_text) &&
      !value.exists(_.isWhitespace)
  }

  private def _valid_text(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)

  private def _string(cursor: HCursor, key: String): Either[String, String] =
    cursor.get[String](key).left.map(_ => s"Invalid installed runtime $key")
      .flatMap(value => _require(_valid_text(value), s"Invalid installed runtime $key").map(_ => value))

  private def _uuid(cursor: HCursor, key: String): Either[String, UUID] =
    _string(cursor, key).flatMap { value =>
      try {
        val uuid = UUID.fromString(value)
        _require(uuid.toString == value, s"Invalid installed runtime $key").map(_ => uuid)
      } catch {
        case NonFatal(_) => Left(s"Invalid installed runtime $key")
      }
    }

  private def _require(condition: Boolean, message: String): Either[String, Unit] =
    if (condition) Right(()) else Left(message)

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (result, value) =>
      for { accumulated <- result; item <- value } yield accumulated :+ item
    }
}
