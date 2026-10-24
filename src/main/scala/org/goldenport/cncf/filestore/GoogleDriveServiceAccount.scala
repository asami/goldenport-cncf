package org.goldenport.cncf.filestore

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.Path
import java.time.Instant
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{RuntimeSecretResolver, SecretReference}
import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.oauth.{GoogleServiceAccount, GoogleServiceAccountToken}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 */
private[cncf] final class GoogleDriveServiceAccount private (
  val connectionId: String,
  private val _component_id: ComponentId,
  private val _instance_id: ComponentInstanceId,
  private val _subjects: Vector[String],
  private val _account: GoogleServiceAccount
) {
  @volatile private var _cached: Option[GoogleServiceAccountToken] = None
  private var _latest_observation: Option[Instant] = None
  override def toString: String = "GoogleDriveServiceAccount(<redacted>)"

  def authorizationC(uow: UnitOfWork): Consequence[Map[String, String]] = {
    try {
      if (uow == null || uow.executionContext == null) _denied
      else {
        val context = uow.executionContext
        val subject = SecuritySubject.from(context.security)
        @annotation.tailrec
        def _component_(scope: ScopeContext): Option[Component] = scope match {
          case current: Component.Context => Option(current.component)
          case current if current != null => current.parent match {
            case Some(parent) => _component_(parent)
            case None => None
          }
          case _ => None
        }
        val admitted = _component_(context.cncfCore.scope).exists(component =>
          component.componentId == _component_id && component.instanceId == _instance_id)
        if (!admitted || !subject.isAuthenticated || !subject.isProviderAuthenticated || !_subjects.contains(subject.subjectId)) _denied
        else {
          val now = context.clock.instant()
          _observe_c(now).flatMap { _ => _cached.filter(token => !now.isBefore(token.issuedAt) && now.isBefore(token.expiresAt.minusSeconds(30))) match {
            case Some(token) => Consequence.success(token.authorization)
            case None => new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(_account.acquire(now)).flatMap { token =>
              val observed = context.clock.instant()
              _observe_c(observed).flatMap { _ =>
                if (!observed.isBefore(token.expiresAt)) _clock_invalid
                else {
                _cached = Some(token)
                Consequence.success(token.authorization)
                }
              }
            }
          }}
        }
      }
    } catch { case NonFatal(_) => Consequence.serviceUnavailable("Google service account operation is unavailable") }
  }

  private def _observe_c(now: Instant): Consequence[Unit] = synchronized {
    if (now == null || now.getEpochSecond < 0 || _latest_observation.exists(now.isBefore)) _clock_invalid
    else { _latest_observation = Some(now); Consequence.unit }
  }

  private def _denied[A]: Consequence[A] = Consequence.securityPermissionDenied("Google service account caller or scope is not allowed")
  private def _clock_invalid[A]: Consequence[A] = Consequence.stateConflict("Google service account clock or token lifetime is invalid")
}

private[cncf] object GoogleDriveServiceAccount {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _required = Set("schema", "connectionId", "allowedSubjects", "scopes")

  def loadC(reference: SecretReference, componentId: ComponentId, instanceId: ComponentInstanceId): Consequence[GoogleDriveServiceAccount] = {
    if (componentId == null || instanceId == null || instanceId.componentId != componentId) _invalid
    else RuntimeSecretResolver.privateFile.resolveSecret(reference).flatMap { material =>
      val decoded = try {
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(material._copy_bytes)).toString
        for {
          fields <- _parser.parse(text).toOption.flatMap(_.asObject)
          if fields.keys.toSet == _required + "credentialFile" || fields.keys.toSet == _required + "credentialEnvironment"
          if fields("schema").contains(Json.fromString("cncf.google-drive-service-account/1"))
          connection <- fields("connectionId").flatMap(_.asString).filter(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
          subjects <- fields("allowedSubjects").flatMap(_.asArray).filter(values => values.nonEmpty && values.size <= 1024)
          if subjects.forall(_.asString.exists(value => value.nonEmpty && value.length <= 256 && value.forall(c => c >= '!' && c <= '~')))
          names = subjects.flatMap(_.asString)
          if names.distinct == names
          values <- fields("scopes").flatMap(_.asArray)
          if values.forall(_.isString)
          scopes = values.flatMap(_.asString)
          if GoogleServiceAccount.validScopes(scopes)
          file <- fields("credentialFile") match {
            case Some(value) => value.asString
            case None => fields("credentialEnvironment").filter(_.asString.contains("GOOGLE_APPLICATION_CREDENTIALS"))
              .flatMap(_ => sys.env.get("GOOGLE_APPLICATION_CREDENTIALS"))
          }
          path = Path.of(file)
          if path.isAbsolute && path == path.normalize() && path.getFileName != null && path.getParent != null
        } yield (connection, names, scopes, path)
      } catch { case NonFatal(_) => None }
      decoded.fold[Consequence[GoogleDriveServiceAccount]](_invalid) { case (connection, subjects, scopes, path) =>
        SecretReference.fromConfiguration(path.toUri.toString).flatMap(RuntimeSecretResolver.privateFile.resolveSecret)
          .flatMap(key => GoogleServiceAccount.parseC(key._copy_bytes, scopes)).map { account =>
            new GoogleDriveServiceAccount(connection, componentId, instanceId, subjects, account)
          }
      }
    }
  }

  private def _invalid[A]: Consequence[A] = Consequence.configurationInvalid("Google Drive service account configuration is invalid")
}
