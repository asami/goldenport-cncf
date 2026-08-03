package org.goldenport.cncf.config

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.observation.Taxonomy
import org.goldenport.configuration.{Configuration, ConfigurationSourceLoad, ConfigurationValue}
import org.goldenport.configuration.source.file.{ConfigTextDecoder, FileConfigLoader}

/*
 * @since   Apr. 15, 2026
 *  version May. 11, 2026
 * @version Jul.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeFileConfigLoader extends FileConfigLoader {
  override def load(
    path: Path
  ): Consequence[Configuration] =
    loadSnapshot(path).map(_.value)

  override def loadSnapshot(
    path: Path
  ): Consequence[ConfigurationSourceLoad] =
    if (!Files.exists(path)) {
      Consequence.success(ConfigurationSourceLoad(Configuration.empty))
    } else {
      _load_record_config_snapshot(path)
    }

  private def _load_record_config_snapshot(
    path: Path
  ): Consequence[ConfigurationSourceLoad] =
    try {
      ConfigTextDecoder.decodeSnapshot(path, Files.readString(path, StandardCharsets.UTF_8)) match {
        case Consequence.Success(loaded) =>
          Consequence.success(ConfigurationSourceLoad(
            _with_flattened_objects(loaded.value),
            loaded.rawDocument
          ))
        case Consequence.Failure(conclusion) =>
          RuntimeFileConfigLoader.configurationFileParseInvalid(
            path,
            new IllegalArgumentException(conclusion.show)
          )
      }
    } catch {
      case e: Exception =>
        RuntimeFileConfigLoader.configurationFileParseInvalid(path, e)
    }

  private def _with_flattened_objects(
    config: Configuration
  ): Configuration =
    Configuration(config.values ++ _flatten_values(config.values))

  private def _flatten_values(
    values: Map[String, ConfigurationValue]
  ): Map[String, ConfigurationValue] =
    values.toVector.flatMap {
      case (key, obj: ConfigurationValue.ObjectValue) => _flatten_object(key, obj)
      case _ => Vector.empty
    }.toMap

  private def _flatten_object(
    value: ConfigurationValue.ObjectValue
  ): Map[String, ConfigurationValue] =
    value.values.toVector.flatMap {
      case (key, obj: ConfigurationValue.ObjectValue) => _flatten_object(key, obj)
      case _ => Vector.empty
    }.toMap

  private def _flatten_object(
    prefix: String,
    value: ConfigurationValue.ObjectValue
  ): Vector[(String, ConfigurationValue)] =
    value.values.toVector.flatMap {
      case (key, obj: ConfigurationValue.ObjectValue) =>
        val path = s"$prefix.$key"
        (path -> obj) +: _flatten_object(path, obj)
      case (key, v) =>
        Vector(s"$prefix.$key" -> v)
    }
}

object RuntimeFileConfigLoader {
  def configurationFileParseInvalid[A](
    path: Path,
    cause: Throwable
  ): Consequence.Failure[A] = {
    val filetype = fileType(path)
    Consequence.fail(
      Taxonomy.resourceInvalid,
      cause,
      Seq(
        Descriptor.Facet.Message(s"configuration file parse failed: ${path}: ${cause.getMessage}"),
        Descriptor.Facet.Resource(Descriptor.Facet.Resource.Kind.File, path.toUri),
        Descriptor.Facet.Properties(Map(
          "fileType" -> filetype,
          "path" -> path.toString,
          "cause" -> Option(cause.getMessage).getOrElse(cause.getClass.getName)
        ))
      )
    )
  }

  def fileType(path: Path): String = {
    val name = path.getFileName.toString
    val i = name.lastIndexOf('.')
    if (i >= 0 && i + 1 < name.length)
      name.substring(i + 1).toLowerCase
    else
      "unknown"
  }
}
