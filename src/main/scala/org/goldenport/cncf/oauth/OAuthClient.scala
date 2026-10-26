package org.goldenport.cncf.oauth

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import cats.free.Free
import cats.syntax.all.*
import io.circe.{JsonObject, parser}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
/** Fixed, trusted endpoints; construction never discovers or contacts a provider. */
final class OAuthProvider private (
  val name: String,
  val authorizationEndpoint: String,
  val tokenEndpoint: String,
  private[oauth] val _authorization_parameters: Vector[(String, String)]
) {
  override def toString: String = s"OAuthProvider($name)"
}

object OAuthProvider {
  def createC(name: String, authorizationEndpoint: String, tokenEndpoint: String): Consequence[OAuthProvider] =
    if (Option(name).exists(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) &&
        OAuthValidation.endpoint(authorizationEndpoint) && OAuthValidation.endpoint(tokenEndpoint))
      Consequence.success(new OAuthProvider(name, authorizationEndpoint, tokenEndpoint, Vector.empty))
    else OAuthValidation.configurationInvalid

  val googleDrive: OAuthProvider = new OAuthProvider("google-drive",
    "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
    Vector("access_type" -> "offline", "prompt" -> "consent"))
}

/** Pure authorization-code/PKCE programs. The runtime owns entropy, time and flow lifetime. */
final class OAuthClient private (
  private val _provider: OAuthProvider,
  private val _client_id: String,
  private val _client_secret: Option[String],
  private val _redirect_uri: String,
  private val _scopes: Vector[String]
) {
  override def toString: String = "OAuthClient(<redacted>)"

  private[oauth] def _connection_binding: io.circe.Json = io.circe.Json.obj(
    "providerId" -> io.circe.Json.fromString(_provider.name),
    "authorizationEndpoint" -> io.circe.Json.fromString(_provider.authorizationEndpoint),
    "tokenEndpoint" -> io.circe.Json.fromString(_provider.tokenEndpoint),
    "clientId" -> io.circe.Json.fromString(_client_id),
    "redirectUri" -> io.circe.Json.fromString(_redirect_uri),
    "requestedScopes" -> io.circe.Json.fromValues(_scopes.map(io.circe.Json.fromString)))

  private[oauth] def _restore_credential_c(access: String, refresh: Option[String],
    scopes: Vector[String], expiry: Instant): Consequence[OAuthCredential] =
    if (OAuthValidation.accessToken(access) &&
        Option(refresh).exists(_.forall(OAuthValidation.printable(_, 16384))) &&
        OAuthValidation.scopes(scopes) && scopes.forall(_scopes.contains) && expiry != null)
      Consequence.success(OAuthCredential._create(access, refresh, scopes, expiry, this))
    else Consequence.resourceInvalid("OAuth credential storage record is invalid")

  def beginC(state: String, codeVerifier: String): Consequence[OAuthAuthorizationRequest] = {
    if (!OAuthValidation.state(state) || !OAuthValidation.verifier(codeVerifier))
      OAuthValidation.requestInvalid
    else {
      val challenge = Base64.getUrlEncoder.withoutPadding.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)))
      val fields = Vector("response_type" -> "code", "client_id" -> _client_id,
        "redirect_uri" -> _redirect_uri, "scope" -> _scopes.mkString(" "), "state" -> state,
        "code_challenge" -> challenge, "code_challenge_method" -> "S256") ++ _provider._authorization_parameters
      Consequence.success(OAuthAuthorizationRequest._create(
        _provider.authorizationEndpoint + "?" + OAuthValidation.form(fields), state, codeVerifier, this))
    }
  }

  def complete(request: OAuthAuthorizationRequest, returnedState: String, code: String,
    observedAt: Instant): ExecUowM[OAuthCredential] = {
    if (request == null || !(request._client eq this) || !OAuthValidation.state(returnedState) ||
        observedAt == null || !OAuthValidation.printable(code, 8192))
      ConsequenceT.fromConsequence(OAuthValidation.requestInvalid)
    else if (!MessageDigest.isEqual(request._state.getBytes(StandardCharsets.US_ASCII),
        returnedState.getBytes(StandardCharsets.US_ASCII)))
      ConsequenceT.fromConsequence(OAuthValidation.requestInvalid)
    else _exchange(Vector("grant_type" -> "authorization_code", "code" -> code,
      "redirect_uri" -> _redirect_uri, "client_id" -> _client_id,
      "code_verifier" -> request._verifier) ++ _client_secret.map("client_secret" -> _), observedAt, None)
  }

  def refresh(credential: OAuthCredential, observedAt: Instant): ExecUowM[OAuthCredential] = {
    if (credential == null || !(credential._client eq this) || observedAt == null)
      ConsequenceT.fromConsequence(OAuthValidation.requestInvalid)
    else credential.refreshToken match {
      case None => ConsequenceT.fromConsequence(
        Consequence.securityPermissionDenied("OAuth connection requires authorization"))
      case Some(token) => _exchange(Vector("grant_type" -> "refresh_token", "refresh_token" -> token,
        "client_id" -> _client_id) ++ _client_secret.map("client_secret" -> _), observedAt, Some(credential))
    }
  }

  private def _exchange(fields: Vector[(String, String)], observedat: Instant,
    previous: Option[OAuthCredential]): ExecUowM[OAuthCredential] = {
    val operation = UnitOfWorkOp.HttpPost(_provider.tokenEndpoint, Some(OAuthValidation.form(fields)),
      Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json"),
      Vector(Property("http.max-response-bytes", "16384", None)))
    ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, HttpResponse](operation)).flatMap { response =>
      ConsequenceT.fromConsequence(_response(response, observedat, previous))
    }
  }

  private def _response(response: HttpResponse, observedat: Instant,
    previous: Option[OAuthCredential]): Consequence[OAuthCredential] = response.code match {
    case 200 =>
      val credential = try {
        if (!response.mime.value.equalsIgnoreCase("application/json")) None
        else response.getString.flatMap(text => parser.parse(text).toOption).flatMap(_.asObject).flatMap { fields =>
          for {
            access <- fields("access_token").flatMap(_.asString).filter(OAuthValidation.accessToken)
            _ <- fields("token_type").flatMap(_.asString).filter(_.equalsIgnoreCase("Bearer"))
            seconds <- fields("expires_in").flatMap(_.asNumber).flatMap(_.toBigDecimal)
              .filter(value => value > 0 && value.isWhole && value.isValidLong).map(_.toLong)
            expiry <- _expiry(observedat, seconds)
            refresh <- _refresh_token(fields, previous)
            scopes <- _granted_scopes(fields, previous)
          } yield OAuthCredential._create(access, refresh, scopes, expiry, this)
        }
      } catch { case NonFatal(_) => None }
      credential.fold(OAuthValidation.responseInvalid[OAuthCredential])(Consequence.success)
    case 400 | 401 | 403 => Consequence.securityPermissionDenied("OAuth authorization was denied")
    case status if status == 429 || status >= 500 && status <= 599 =>
      Consequence.serviceUnavailable("OAuth authorization service is unavailable")
    case _ => OAuthValidation.responseInvalid
  }

  private def _expiry(observedat: Instant, seconds: Long): Option[Instant] =
    try Some(observedat.plusSeconds(seconds)).filter(_.isAfter(observedat))
    catch { case NonFatal(_) => None }

  private def _refresh_token(fields: JsonObject, previous: Option[OAuthCredential]): Option[Option[String]] =
    fields("refresh_token") match {
      case None => Some(previous.flatMap(_.refreshToken))
      case Some(value) => value.asString.filter(OAuthValidation.printable(_, 16384)).map(Some(_))
    }

  private def _granted_scopes(fields: JsonObject, previous: Option[OAuthCredential]): Option[Vector[String]] = {
    val allowed = previous.map(_.grantedScopes).getOrElse(_scopes)
    fields("scope") match {
      case None => Some(allowed)
      case Some(value) => value.asString.map(_.split(" ", -1).toVector)
        .filter(OAuthValidation.scopes).filter(_.forall(allowed.contains))
    }
  }
}

object OAuthClient {
  def createC(provider: OAuthProvider, clientId: String, clientSecret: Option[String],
    redirectUri: String, scopes: Vector[String]): Consequence[OAuthClient] =
    if (provider != null && OAuthValidation.printable(clientId, 1024) &&
        Option(clientSecret).exists(_.forall(OAuthValidation.printable(_, 16384))) &&
        OAuthValidation.redirect(redirectUri) && OAuthValidation.scopes(scopes))
      Consequence.success(new OAuthClient(provider, clientId, clientSecret, redirectUri, scopes))
    else OAuthValidation.configurationInvalid
}

/** Only authorizationUri is an intentional browser handoff; display is always redacted. */
final class OAuthAuthorizationRequest private (
  val authorizationUri: String,
  private[oauth] val _state: String,
  private[oauth] val _verifier: String,
  private[oauth] val _client: OAuthClient
) {
  override def toString: String = "OAuthAuthorizationRequest(<redacted>)"
}

object OAuthAuthorizationRequest {
  private[oauth] def _create(uri: String, state: String, verifier: String,
    client: OAuthClient): OAuthAuthorizationRequest = new OAuthAuthorizationRequest(uri, state, verifier, client)
}

/** Secret values are available only to trusted CNCF runtime/provider/store code. */
final class OAuthCredential private (
  private val _access_token: String,
  private val _refresh_token: Option[String],
  val grantedScopes: Vector[String],
  val expiresAt: Instant,
  private[oauth] val _client: OAuthClient
) {
  def canRefresh: Boolean = _refresh_token.nonEmpty
  private[cncf] def accessToken: String = _access_token
  private[cncf] def refreshToken: Option[String] = _refresh_token
  override def toString: String = "OAuthCredential(<redacted>)"
}

object OAuthCredential {
  private[oauth] def _create(access: String, refresh: Option[String], scopes: Vector[String],
    expiry: Instant, client: OAuthClient): OAuthCredential = new OAuthCredential(access, refresh, scopes, expiry, client)
}

private[oauth] object OAuthValidation {
  def configurationInvalid[A]: Consequence[A] =
    Consequence.configurationInvalid("OAuth client configuration is invalid")
  def requestInvalid[A]: Consequence[A] =
    Consequence.argumentInvalid("OAuth authorization request is invalid")
  def responseInvalid[A]: Consequence[A] =
    Consequence.resourceInvalid("OAuth authorization response is invalid")
  def printable(value: String, limit: Int): Boolean =
    Option(value).exists(text => text.nonEmpty && text.length <= limit && text.forall(c => c >= '!' && c <= '~'))
  def accessToken(value: String): Boolean =
    Option(value).exists(text => text.nonEmpty && text.length <= 16384 && text.matches("[A-Za-z0-9._~+/\\-]+=*"))
  def state(value: String): Boolean = Option(value).exists(_.matches("[A-Za-z0-9_-]{43,128}"))
  def verifier(value: String): Boolean = Option(value).exists(_.matches("[A-Za-z0-9._~\\-]{43,128}"))
  def scopes(values: Vector[String]): Boolean = Option(values).exists { tokens =>
    tokens.nonEmpty && tokens.size <= 16 && tokens.distinct.size == tokens.size && tokens.forall { token =>
      Option(token).exists(text => text.nonEmpty && text.length <= 1024 &&
        text.forall(c => c == '!' || c >= '#' && c <= '[' || c >= ']' && c <= '~'))
    }
  }
  def endpoint(value: String): Boolean = _uri(value).exists(_https)
  def redirect(value: String): Boolean = _uri(value).exists { uri =>
    _https(uri) || (uri.getScheme.equalsIgnoreCase("http") && uri.getHost == "127.0.0.1" &&
      uri.getPort >= 1 && uri.getPort <= 65535)
  }
  def form(fields: Vector[(String, String)]): String = fields.map { case (key, value) =>
    URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
  }.mkString("&")

  private def _https(uri: URI): Boolean = uri.getScheme.equalsIgnoreCase("https") &&
    (uri.getPort == -1 || uri.getPort == 443)
  private def _uri(value: String): Option[URI] = {
    if (!printable(value, 2048) || value.contains('\\')) None
    else try {
      val uri = new URI(value)
      Option(uri).filter(item => item.isAbsolute && !item.isOpaque &&
        Option(item.getHost).exists(_.nonEmpty) && item.getRawUserInfo == null &&
        item.getRawQuery == null && item.getRawFragment == null &&
        (if (item.getPort == -1) item.getRawAuthority == item.getHost
         else item.getRawAuthority.startsWith(item.getHost + ":") &&
           item.getRawAuthority.drop(item.getHost.length + 1).matches("[0-9]+")))
    } catch { case NonFatal(_) => None }
  }
}
