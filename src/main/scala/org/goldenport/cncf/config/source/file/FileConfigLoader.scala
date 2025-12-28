package org.goldenport.cncf.config.source.file

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.config.model.{Config, ConfigValue}

/**
 * FileConfigLoader loads configuration from a file.
 *
 * Responsibilities:
 *   - check file existence
 *   - read file contents
 *   - convert simple key=value pairs into Config
 *
 * Non-responsibilities:
 *   - merge semantics
 *   - origin / rank handling
 *   - validation
 *   - defaults
 *   - format semantics (HOCON/JSON/YAML)
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
trait FileConfigLoader {
  def load(
    path: Path
  ): Consequence[Config]
}

/**
 * SimpleFileConfigLoader is a minimal reference implementation.
 *
 * Supported format:
 *   key = value
 *
 * Blank lines and lines starting with '#' are ignored.
 */
final class SimpleFileConfigLoader
  extends FileConfigLoader {

  override def load(
    path: Path
  ): Consequence[Config] = {

    if (!Files.exists(path))
      Consequence.Success(Config.empty)
    else {
      val lines = Files.readAllLines(path).asScala

      val values =
        lines
          .map(_.trim)
          .filterNot(_.isEmpty)
          .filterNot(_.startsWith("#"))
          .flatMap { line =>
            line.split("=", 2) match {
              case Array(k, v) =>
                Some(k.trim -> ConfigValue.StringValue(v.trim))
              case _ =>
                None
            }
          }
          .toMap

      Consequence.Success(
        Config(values)
      )
    }
  }
}

