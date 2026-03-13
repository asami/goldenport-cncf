package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Mar. 13, 2026
 * @version Mar. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object ConfigurationAccess {
  def getString(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] =
    _from_flat_key(conf, key).orElse(_from_object_path(conf, key))

  private def _from_flat_key(
    conf: ResolvedConfiguration,
    key: String
  ): Option[String] =
    conf.get[String](key) match {
      case Consequence.Success(v) => v
      case Consequence.Failure(_) => None
    }

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
}
