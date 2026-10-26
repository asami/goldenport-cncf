package org.goldenport.cncf.oauth

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.security.{KeyFactory, PrivateKey, Signature}
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import cats.free.Free
import cats.syntax.all.*
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 */
private[cncf] final class GoogleServiceAccount private (
  private val _email: String,
  private val _key_id: String,
  private val _key: PrivateKey,
  private val _scopes: Vector[String]
) {
  import GoogleServiceAccount.*
  override def toString: String = "GoogleServiceAccount(<redacted>)"

  def acquire(now: Instant): ExecUowM[GoogleServiceAccountToken] =
    ConsequenceT.fromConsequence[org.goldenport.cncf.unitofwork.ExecProgram, String](assertionC(now)).flatMap { assertion =>
      val fields = OAuthValidation.form(Vector("grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion" -> assertion))
      val operation = UnitOfWorkOp.HttpPost(tokenEndpoint, Some(fields),
        Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json"),
        Vector(Property("http.max-response-bytes", "16384", None)))
      ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation)).flatMap { response =>
        ConsequenceT.fromConsequence(_response(response, now))
      }
    }

  private[oauth] def assertionC(now: Instant): Consequence[String] = {
    try {
      if (now == null || now.getEpochSecond < 0) _clock_invalid
      else {
        val expiry = now.plusSeconds(3600)
        val header = Json.obj("alg" -> Json.fromString("RS256"), "typ" -> Json.fromString("JWT"), "kid" -> Json.fromString(_key_id))
        val claims = Json.obj("iss" -> Json.fromString(_email), "scope" -> Json.fromString(_scopes.mkString(" ")),
          "aud" -> Json.fromString(tokenEndpoint), "iat" -> Json.fromLong(now.getEpochSecond), "exp" -> Json.fromLong(expiry.getEpochSecond))
        val unsigned = _encode(header.noSpaces.getBytes(StandardCharsets.UTF_8)) + "." + _encode(claims.noSpaces.getBytes(StandardCharsets.UTF_8))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(_key)
        signer.update(unsigned.getBytes(StandardCharsets.US_ASCII))
        Consequence.success(unsigned + "." + _encode(signer.sign()))
      }
    } catch { case NonFatal(_) => _clock_invalid }
  }

  private def _response(response: HttpResponse, now: Instant): Consequence[GoogleServiceAccountToken] = response.code match {
    case 200 =>
      val parsed = try {
        for {
          text <- response.getString.filter(text => text.getBytes(StandardCharsets.UTF_8).length <= 16384)
          if response.mime.value.equalsIgnoreCase("application/json")
          fields <- _parser.parse(text).toOption.flatMap(_.asObject)
          access <- fields("access_token").flatMap(_.asString).filter(OAuthValidation.accessToken)
          if fields("token_type").flatMap(_.asString).exists(_.equalsIgnoreCase("Bearer"))
          seconds <- fields("expires_in").flatMap(_.asNumber).flatMap(_.toLong).filter(value => value > 0 && value <= 3600)
          if fields("scope").forall(value => value.asString.exists(_.split(" ", -1).toSet == _scopes.toSet))
        } yield new GoogleServiceAccountToken(access, now, now.plusSeconds(seconds))
      } catch { case NonFatal(_) => None }
      parsed.fold[Consequence[GoogleServiceAccountToken]](Consequence.resourceInvalid("Google service account token response is invalid"))(Consequence.success)
    case 400 | 401 | 403 => Consequence.securityPermissionDenied("Google service account authorization was denied")
    case value if value == 429 || value >= 500 && value <= 599 => Consequence.serviceUnavailable("Google service account authorization is unavailable")
    case _ => Consequence.resourceInvalid("Google service account token response is invalid")
  }
}

private[cncf] object GoogleServiceAccount {
  val tokenEndpoint: String = "https://oauth2.googleapis.com/token"
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _key_fields = Set("type", "project_id", "private_key_id", "private_key", "client_email", "client_id",
    "auth_uri", "token_uri", "auth_provider_x509_cert_url", "client_x509_cert_url", "universe_domain")
  private val _scope_values = Set("https://www.googleapis.com/auth/drive", "https://www.googleapis.com/auth/drive.readonly",
    "https://www.googleapis.com/auth/drive.file")

  def validScopes(scopes: Vector[String]): Boolean = scopes != null && scopes.nonEmpty && scopes.distinct == scopes && scopes.forall(_scope_values)

  def parseC(bytes: Array[Byte], scopes: Vector[String]): Consequence[GoogleServiceAccount] = {
    val parsed = try {
      if (bytes == null || bytes.isEmpty || bytes.length > 131072 || !validScopes(scopes)) None
      else {
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
        for {
          fields <- _parser.parse(text).toOption.flatMap(_.asObject)
          if fields.keys.toSet.subsetOf(_key_fields) && fields.values.forall(_.isString)
          if fields("type").contains(Json.fromString("service_account"))
          if fields("token_uri").contains(Json.fromString(tokenEndpoint))
          if fields("universe_domain").forall(_.asString.contains("googleapis.com"))
          email <- fields("client_email").flatMap(_.asString).filter(_.matches("[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.iam\\.gserviceaccount\\.com"))
          keyid <- fields("private_key_id").flatMap(_.asString).filter(_.matches("[A-Za-z0-9_-]{1,256}"))
          pem <- fields("private_key").flatMap(_.asString)
          if pem.startsWith("-----BEGIN PRIVATE KEY-----\n") && pem.trim.endsWith("-----END PRIVATE KEY-----")
          encoded = pem.stripPrefix("-----BEGIN PRIVATE KEY-----\n").trim.stripSuffix("-----END PRIVATE KEY-----").replaceAll("\\s", "")
          key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(encoded)))
          if key.isInstanceOf[RSAPrivateKey] && key.asInstanceOf[RSAPrivateKey].getModulus.bitLength >= 2048
        } yield new GoogleServiceAccount(email, keyid, key, scopes)
      }
    } catch { case NonFatal(_) => None }
    parsed.fold[Consequence[GoogleServiceAccount]](Consequence.configurationInvalid("Google service account credentials are invalid"))(Consequence.success)
  }

  private def _encode(bytes: Array[Byte]): String = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  private def _clock_invalid[A]: Consequence[A] = Consequence.stateConflict("Google service account clock or signing operation is invalid")
}

private[cncf] final class GoogleServiceAccountToken(private val _access: String, val issuedAt: Instant, val expiresAt: Instant) {
  def authorization: Map[String, String] = Map("Authorization" -> s"Bearer ${_access}")
  override def toString: String = "GoogleServiceAccountToken(<redacted>)"
}
