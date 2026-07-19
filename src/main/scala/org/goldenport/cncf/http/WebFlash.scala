package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] object WebFlash {
  final case class Value(
    variant: String,
    messageKey: String
  )

  val MaxAgeSeconds: Long = 120L

  private val _max_encoded_length = 512
  private val _message_key_pattern = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}".r
  private val _variants = Set("success", "danger", "warning", "info", "primary", "secondary")

  def cookieName(componentName: String): String =
    s"textus-flash-${NamingConventions.toNormalizedSegment(componentName)}"

  def encode(value: Value): Option[String] =
    normalized(value).map { normalized =>
      Base64.getUrlEncoder.withoutPadding.encodeToString(
        s"${normalized.variant}:${normalized.messageKey}".getBytes(StandardCharsets.UTF_8)
      )
    }

  def decode(value: String): Option[Value] =
    Option(value).map(_.trim).filter(x => x.nonEmpty && x.length <= _max_encoded_length).flatMap { encoded =>
      scala.util.Try {
        new String(Base64.getUrlDecoder.decode(encoded), StandardCharsets.UTF_8)
      }.toOption.flatMap { decoded =>
        decoded.split(":", 2).toVector match {
          case Vector(variant, messageKey) => normalized(Value(variant, messageKey))
          case _ => None
        }
      }
    }

  def normalized(value: Value): Option[Value] = {
    val variant = Option(value.variant).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    val key = Option(value.messageKey).getOrElse("").trim
    Option.when(_variants.contains(variant) && _message_key_pattern.matches(key))(
      Value(variant, key)
    )
  }
}
