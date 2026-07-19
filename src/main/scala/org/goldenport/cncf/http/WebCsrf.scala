package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 * Stateless CSRF tokens for server-rendered Web forms.
 *
 * @since   Jul. 19, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] object WebCsrf {
  val cookieName = "textus-csrf"
  private val _random = new SecureRandom()
  private val _encoder = Base64.getUrlEncoder.withoutPadding()
  private val _nonce_pattern = "[A-Za-z0-9_-]{43}".r
  private val _signature_pattern = "[A-Za-z0-9_-]{43}".r

  def issue(
    sessionid: Option[String]
  ): String = {
    val nonce = _nonce()
    sessionid.map(_.trim).filter(_.nonEmpty) match {
      case Some(session) =>
        s"v1.s.${nonce}.${_signature(session, nonce)}"
      case None =>
        s"v1.a.${nonce}"
    }
  }

  def isValid(
    sessionid: Option[String],
    token: String
  ): Boolean =
    _parts(token) match {
      case Some(Vector("v1", "s", nonce, signature)) =>
        sessionid.map(_.trim).filter(_.nonEmpty).exists { session =>
          _valid_nonce(nonce) &&
            _valid_signature(signature) &&
            _constant_time_equal(signature, _signature(session, nonce))
        }
      case Some(Vector("v1", "a", nonce)) =>
        sessionid.forall(_.trim.isEmpty) && _valid_nonce(nonce)
      case _ =>
        false
    }

  def verify(
    sessionid: Option[String],
    cookietoken: Option[String],
    formtoken: Option[String]
  ): Boolean =
    (cookietoken.map(_.trim).filter(_.nonEmpty), formtoken.map(_.trim).filter(_.nonEmpty)) match {
      case (Some(cookie), Some(form)) =>
        _constant_time_equal(cookie, form) && isValid(sessionid, cookie)
      case _ =>
        false
    }

  private def _nonce(): String = {
    val bytes = new Array[Byte](32)
    _random.nextBytes(bytes)
    _encoder.encodeToString(bytes)
  }

  private def _signature(
    sessionid: String,
    nonce: String
  ): String = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(sessionid.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
    _encoder.encodeToString(mac.doFinal(s"cncf-web:${nonce}".getBytes(StandardCharsets.UTF_8)))
  }

  private def _parts(token: String): Option[Vector[String]] =
    Option(token)
      .map(_.trim)
      .filter(text => text.nonEmpty && text.length <= 140)
      .map(_.split("\\.", -1).toVector)

  private def _valid_nonce(nonce: String): Boolean =
    _nonce_pattern.matches(nonce)

  private def _valid_signature(signature: String): Boolean =
    _signature_pattern.matches(signature)

  private def _constant_time_equal(lhs: String, rhs: String): Boolean =
    MessageDigest.isEqual(
      lhs.getBytes(StandardCharsets.UTF_8),
      rhs.getBytes(StandardCharsets.UTF_8)
    )
}
