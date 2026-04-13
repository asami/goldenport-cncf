package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence

/*
 * @since   Mar. 22, 2026
 *  version Apr.  8, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ArchiveManifest(
  archiveType: String,
  name: String,
  version: String,
  component: Option[String],
  subsystem: Option[String],
  extensions: Map[String, String],
  config: Map[String, String]
)

object ArchiveManifest {
  // Transitional compatibility loader. CAR/SAR packaging is planned to
  // converge on top-level descriptors, so manifest-specific handling is
  // expected to be retired after descriptor-based archive loading lands.
  private val _manifest_relative_path = "meta/manifest.json"

  def load(
    root: Path,
    archiveType: String
  ): Consequence[ArchiveManifest] = {
    val path = root.resolve(_manifest_relative_path)
    if (!Files.exists(path)) {
      return Consequence.resourceNotFound(s"${archiveType}.manifest-missing path=${path}")
    }
    val text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    _parse(text, archiveType)
  }

  def mergeSarPrecedence(
    car: ArchiveManifest,
    sar: ArchiveManifest
  ): (Map[String, String], Map[String, String]) = {
    val extensions = car.extensions ++ sar.extensions
    val config = car.config ++ sar.config
    (extensions, config)
  }

  private def _parse(
    text: String,
    archiveType: String
  ): Consequence[ArchiveManifest] =
    parse(text) match {
      case Left(err) =>
        Consequence.resourceInvalid(s"${archiveType}.manifest-invalid-json: ${err.getMessage}")
      case Right(json) =>
        _to_manifest(json, archiveType)
    }

  private def _to_manifest(
    json: Json,
    archiveType: String
  ): Consequence[ArchiveManifest] = {
    val cursor = json.hcursor
    val name = cursor.get[String]("name").toOption.map(_.trim).getOrElse("")
    val version = cursor.get[String]("version").toOption.map(_.trim).getOrElse("")
    val component = cursor.get[String]("component").toOption.map(_.trim).filter(_.nonEmpty)
    val subsystem = cursor.get[String]("subsystem").toOption.map(_.trim).filter(_.nonEmpty)
    val extensions = _read_string_map(cursor.get[Json]("extension").toOption)
    val config = _read_string_map(cursor.get[Json]("config").toOption)
    if (name.isEmpty) {
      Consequence.argumentMissing("name")
    } else if (version.isEmpty) {
      Consequence.argumentMissing("version")
    } else if (archiveType == "car" && component.isEmpty) {
      Consequence.argumentMissing("component")
    } else if (archiveType == "sar" && subsystem.isEmpty) {
      Consequence.argumentMissing("subsystem")
    } else {
      Consequence.success(
        ArchiveManifest(
          archiveType = archiveType,
          name = name,
          version = version,
          component = component,
          subsystem = subsystem,
          extensions = extensions,
          config = config
        )
      )
    }
  }

  private def _read_string_map(
    jsonOpt: Option[Json]
  ): Map[String, String] =
    jsonOpt
      .flatMap(_.asObject)
      .map(_to_string_map)
      .getOrElse(Map.empty)

  private def _to_string_map(
    jsonObject: io.circe.JsonObject
  ): Map[String, String] =
    jsonObject.toMap.collect {
      case (k, v) if v.isString =>
        k -> v.asString.getOrElse("")
    }
}
