package org.goldenport.cncf.oauth

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Path, Paths}
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{RuntimeSecretResolver, SecretReference}
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
private[cncf] enum OAuthConnectionProviderKind {
  case GoogleDrive, Generic
}

/** Trusted bootstrap configuration; no component-visible secret or host-path projection. */
private[cncf] final class OAuthConnectionConfiguration private (
  val componentId: ComponentId,
  val instanceId: ComponentInstanceId,
  val connectionId: String,
  val ownerId: String,
  val providerKind: OAuthConnectionProviderKind,
  val redirectUri: String,
  val configuredOrigin: String,
  val callbackPath: String,
  val returnPath: String,
  val scopes: Vector[String],
  private[oauth] val _client: OAuthClient,
  private[oauth] val _store: OAuthCredentialStore
) {
  override def toString: String = "OAuthConnectionConfiguration(<redacted>)"
}

private[cncf] object OAuthConnectionConfiguration {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _required_keys = Set("schema", "connectionId", "ownerId", "provider", "clientId",
    "redirectUri", "scopes", "credentialFile", "returnPath")
  private val _provider_keys = Set("name", "authorizationEndpoint", "tokenEndpoint")

  def loadC(reference: SecretReference, componentId: ComponentId,
    instanceId: ComponentInstanceId): Consequence[OAuthConnectionConfiguration] = {
    if (reference == null || componentId == null || instanceId == null || instanceId.componentId != componentId)
      _invalid_c
    else RuntimeSecretResolver.privateFile.resolveSecret(reference).flatMap { material =>
      _decode(material._copy_bytes, componentId).fold(_invalid_c[OAuthConnectionConfiguration]) {
        case (connection, owner, kind, client, uri, callback, returnpath, scopes, file) =>
          PrivateFileOAuthCredentialStore.createC(file, connection, owner, client).map { store =>
            new OAuthConnectionConfiguration(componentId, instanceId, connection, owner, kind,
              uri.toString, uri.getScheme + "://" + uri.getRawAuthority, callback, returnpath, scopes, client, store)
          }
      }
    }
  }

  private def _decode(bytes: Array[Byte], componentid: ComponentId):
    Option[(String, String, OAuthConnectionProviderKind, OAuthClient, URI, String, String, Vector[String], Path)] = {
    try {
      val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
      val segment = NamingConventions.toNormalizedSegment(componentid.localId.value())
      for {
        fields <- _parser.parse(text).toOption.flatMap(_.asObject)
        if fields.keys.toSet == _required_keys || fields.keys.toSet == _required_keys + "clientSecret"
        if fields("schema").contains(Json.fromString("cncf.oauth-connection/1"))
        if segment.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
        connection <- _string(fields, "connectionId").filter(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
        owner <- _string(fields, "ownerId").filter(OAuthValidation.printable(_, 256))
        provider <- fields("provider").flatMap(_provider)
        clientid <- _string(fields, "clientId")
        secret <- fields("clientSecret") match {
          case None => Some(None)
          case Some(value) => value.asString.map(Some(_))
        }
        redirect <- _string(fields, "redirectUri")
        scopes <- fields("scopes").flatMap(_.asArray).flatMap { values =>
          val strings = values.flatMap(_.asString)
          Option.when(strings.size == values.size)(strings)
        }
        client <- OAuthClient.createC(provider._1, clientid, secret, redirect, scopes).toOption
        uri = new URI(redirect)
        callback = s"/web/$segment/connections/$connection/callback"
        if uri.getRawPath == callback && uri.getRawQuery == null && uri.getRawFragment == null
        returnpath <- _string(fields, "returnPath").filter(_return_path)
        filepath <- _string(fields, "credentialFile")
        file = Paths.get(filepath)
        if file.isAbsolute && file == file.normalize() && file.getFileName != null && file.getParent != null
      } yield (connection, owner, provider._2, client, uri, callback, returnpath, scopes, file)
    } catch { case NonFatal(_) => None }
  }

  private def _provider(value: Json): Option[(OAuthProvider, OAuthConnectionProviderKind)] = {
    if (value.asString.contains("google-drive")) Some(OAuthProvider.googleDrive -> OAuthConnectionProviderKind.GoogleDrive)
    else value.asObject.filter(_.keys.toSet == _provider_keys).flatMap { fields =>
      for {
        name <- _string(fields, "name")
        authorization <- _string(fields, "authorizationEndpoint")
        token <- _string(fields, "tokenEndpoint")
        provider <- OAuthProvider.createC(name, authorization, token).toOption
      } yield provider -> OAuthConnectionProviderKind.Generic
    }
  }

  private def _string(fields: JsonObject, key: String): Option[String] = fields(key).flatMap(_.asString)

  private def _return_path(value: String): Boolean = {
    val segments = value.split("/", -1).toVector
    value.startsWith("/") && segments.size > 1 && segments.head.isEmpty &&
      segments.tail.forall(segment => segment.matches("[A-Za-z0-9._~-]+") && segment != "." && segment != "..")
  }

  private def _invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid("OAuth connection configuration is invalid")
}
