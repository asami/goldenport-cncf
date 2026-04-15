package org.goldenport.cncf.config

import java.nio.file.{Files, Paths}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Mar. 13, 2026
 *  version Mar. 24, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object ConfigurationAccess {
  def getString(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] =
    _from_flat_key(conf, key)
      .orElse(_from_object_path(conf, key))
      .orElse(_from_system_property(key))
      .orElse(_from_system_config_file(key))
      .map(_normalize_string)

  private def _from_system_property(
    key: String
  ): Option[String] =
    Option(System.getProperty(key)).map(_.trim).filter(_.nonEmpty)

  private def _from_system_config_file(
    key: String
  ): Option[String] =
    _system_config_files().iterator.flatMap(_read_simple_config(_, key)).toSeq.headOption

  private def _system_config_files(): Vector[java.nio.file.Path] =
    Vector("cncf.config.file", "textus.config.file")
      .flatMap(k => Option(System.getProperty(k)))
      .flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
      .map(Paths.get(_).normalize)

  private def _read_simple_config(
    path: java.nio.file.Path,
    key: String
  ): Option[String] =
    if (!Files.isRegularFile(path)) {
      None
    } else {
      import scala.jdk.CollectionConverters.*
      Files.readAllLines(path).asScala.iterator
        .map(_.trim)
        .filterNot(line => line.isEmpty || line.startsWith("#"))
        .flatMap(_parse_simple_line)
        .collectFirst {
          case (`key`, value) => value
        }
    }

  private def _parse_simple_line(
    line: String
  ): Option[(String, String)] =
    line.split("=", 2) match {
      case Array(k, v) =>
        Some(k.trim -> v.trim)
      case _ =>
        None
    }

  private def _from_flat_key(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] =
    conf.get[String](key).toOption.flatten

  private def _from_object_path(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] = {
    val segments = key.split('.').toList.filter(_.nonEmpty)
    _lookup(conf.configuration.values, segments).flatMap(_as_string)
  }

  private def _lookup(
    values: Map[String, ConfigurationValue],
    segments: List[String]
  ): Option[ConfigurationValue] =
    segments match {
      case Nil => None
      case head :: Nil =>
        values.get(head)
      case head :: tail =>
        values.get(head) match {
          case Some(ConfigurationValue.ObjectValue(vs)) =>
            _lookup(vs, tail)
          case _ =>
            None
        }
    }

  private def _as_string(
    value: ConfigurationValue
  ): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }

  // Keep compatibility with older flat-key resolution that may surface
  // configuration wrapper text like StringValue(path).
  private def _normalize_string(
    value: String
  ): String =
    if (value.startsWith("StringValue(") && value.endsWith(")"))
      value.substring("StringValue(".length, value.length - 1)
    else
      value
}
