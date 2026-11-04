package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec, ResolvedConfiguration}

/*
 * @since   Nov.  3, 2026
 * @version Nov.  3, 2026
 */
object IoDefaultParameters {
  val KEY = "cncf.io.default-maximum-bytes"
  val BUILTIN_MAXIMUM_BYTES: Int = 1048576

  val codec: ConfigurationValueCodec[Long] = new ConfigurationValueCodec[Long] {
    def decode(value: ConfigurationValue): Consequence[Long] = value match {
      case ConfigurationValue.NumberValue(number) if number.isWhole && number.isValidLong =>
        _positive_c(number.toLong)
      case ConfigurationValue.StringValue(text) if text != null && text.matches("[0-9]+") =>
        scala.util.Try(text.toLong).toOption.fold[Consequence[Long]](_invalid_c)(_positive_c)
      case _ => _invalid_c
    }

    def encode(value: Long): Consequence[ConfigurationValue] =
      _positive_c(value).map(number => ConfigurationValue.NumberValue(BigDecimal(number)))
  }

  def resolveC(configuration: ResolvedConfiguration): Consequence[Long] = {
    val values = configuration.configuration.values
    val declared = values.get(KEY).orElse(_lookup(values, KEY.split('.').toList))
    declared match {
      case Some(value) => codec.decode(value)
      case None => ConfigurationAccess.getString(configuration, KEY) match {
        case Some(text) => codec.decode(ConfigurationValue.StringValue(text))
        case None => Consequence.success(BUILTIN_MAXIMUM_BYTES.toLong)
      }
    }
  }

  private def _lookup(values: Map[String, ConfigurationValue], segments: List[String]): Option[ConfigurationValue] =
    segments match {
      case head :: Nil => values.get(head)
      case head :: tail => values.get(head) match {
        case Some(ConfigurationValue.ObjectValue(nested)) => _lookup(nested, tail)
        case _ => None
      }
      case Nil => None
    }

  private def _positive_c(value: Long): Consequence[Long] =
    if (value > 0L) Consequence.success(value) else _invalid_c

  private def _invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid(s"$KEY requires a positive integral Long")
}
