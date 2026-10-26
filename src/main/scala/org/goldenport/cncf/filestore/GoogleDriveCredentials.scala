package org.goldenport.cncf.filestore

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import cats.free.Free
import cats.syntax.all.*
import io.circe.parser
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.oauth.OAuthConnectionRequest
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property

/*
 * @since   Oct. 20, 2026
 *  version Oct. 20, 2026
 * @version Oct. 21, 2026
 */
/** Outbound credentials supplied by trusted runtime wiring, never ingress authority.
  * Legacy refresh material is neither cached nor replaced by provider response values.
  * Managed connections delegate cached grants and rotation to their native runtime owner.
  */
final class GoogleDriveCredentials private (
  private val _fixed: Option[String],
  private val _client_id: Option[String],
  private val _client_secret: Option[String],
  private val _refresh_token: Option[String],
  private val _managed_connection: Option[String]
) {
  override def toString: String = "GoogleDriveCredentials(<redacted>)"

  private[filestore] def _authorization: ExecUowM[Map[String, String]] = {
    _managed_connection match {
      case Some(connection) => ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, Map[String, String]](
        UnitOfWorkOp.OAuthConnectionExecute(OAuthConnectionRequest.driveAuthorization(connection))))
      case None => _fixed match {
      case Some(token) => ConsequenceT.fromConsequence(Consequence.success(Map("Authorization" -> s"Bearer $token")))
      case None =>
        val fields = Vector("client_id" -> _client_id.get) ++
          _client_secret.map("client_secret" -> _).toVector ++
          Vector("refresh_token" -> _refresh_token.get, "grant_type" -> "refresh_token")
        val body = fields.map { case (key, value) =>
          URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }.mkString("&")
        val operation = UnitOfWorkOp.HttpPost("https://oauth2.googleapis.com/token", Some(body),
          Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json"),
          Vector(Property("http.max-response-bytes", "16384", None)))
        ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation)).flatMap { response =>
          ConsequenceT.fromConsequence(GoogleDriveCredentials._refresh_response(response))
        }
      }
    }
  }
}

object GoogleDriveCredentials {
  def managedConnectionC(connectionId: String): Consequence[GoogleDriveCredentials] = {
    if (Option(connectionId).exists(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")))
      Consequence.success(new GoogleDriveCredentials(None, None, None, None, Some(connectionId)))
    else _invalid_configuration
  }

  def accessTokenC(accessToken: String): Consequence[GoogleDriveCredentials] = {
    if (_valid_access_token(accessToken))
      Consequence.success(new GoogleDriveCredentials(Some(accessToken), None, None, None, None))
    else _invalid_configuration
  }

  def refreshTokenC(clientId: String, clientSecret: Option[String], refreshToken: String): Consequence[GoogleDriveCredentials] = {
    if (_valid_configuration_value(clientId) && _valid_configuration_value(refreshToken) &&
        Option(clientSecret).exists(_.forall(_valid_configuration_value)))
      Consequence.success(new GoogleDriveCredentials(None, Some(clientId), clientSecret, Some(refreshToken), None))
    else _invalid_configuration
  }

  private def _invalid_configuration: Consequence[GoogleDriveCredentials] =
    Consequence.argumentInvalid("Invalid Google Drive credential configuration")

  private def _valid_access_token(token: String): Boolean =
    Option(token).exists(value => value.nonEmpty && value.length <= 16384 && value.matches("[A-Za-z0-9._~+/\\-]+=*"))

  private def _valid_configuration_value(value: String): Boolean =
    Option(value).exists(text => text.nonEmpty && text.length <= 16384 && text.forall(c => c >= '!' && c <= '~'))

  private def _invalid_response: Consequence[Map[String, String]] =
    Consequence.resourceInvalid("Google Drive credential response is invalid")

  private def _refresh_response(response: HttpResponse): Consequence[Map[String, String]] = {
    response.code match {
      case 200 =>
        val token = try {
          if (!response.mime.value.equalsIgnoreCase("application/json")) None
          else response.getString.flatMap(text => parser.parse(text).toOption).flatMap(_.asObject).flatMap { fields =>
            val access = fields("access_token").flatMap(_.asString).filter(_valid_access_token)
            val bearer = fields("token_type").flatMap(_.asString).exists(_.equalsIgnoreCase("Bearer"))
            val expiry = fields("expires_in").flatMap(_.asNumber).flatMap(_.toBigDecimal)
              .exists(value => value > 0 && value.isWhole)
            access.filter(_ => bearer && expiry)
          }
        } catch { case NonFatal(_) => None }
        token.fold(_invalid_response)(value => Consequence.success(Map("Authorization" -> s"Bearer $value")))
      case 400 | 401 | 403 => Consequence.securityPermissionDenied("Google Drive credential refresh was denied")
      case status if status == 429 || status >= 500 && status <= 599 =>
        Consequence.serviceUnavailable("Google Drive credential refresh is unavailable")
      case _ => _invalid_response
    }
  }
}
