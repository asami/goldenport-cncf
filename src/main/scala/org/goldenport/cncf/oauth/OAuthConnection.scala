package org.goldenport.cncf.oauth

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
private[cncf] enum OAuthConnectionState {
  case Connected, AuthorizationRequired, Expired, Uncertain
}

private[cncf] enum OAuthConnectionOutcome {
  case Authorized, Denied, Expired, Failed, Disconnected
}

/** Safe runtime facts; these do not assert Google-account identity or ingress authority. */
private[cncf] final case class OAuthConnectionStatus(
  state: OAuthConnectionState,
  canRefresh: Boolean,
  expiresAt: Option[Instant],
  authorizationPending: Boolean,
  updating: Boolean,
  lastOutcome: Option[OAuthConnectionOutcome]
)

/** Only the following framework HTTP adapter may deliver the URI and HttpOnly cookie proof. */
private[cncf] final class OAuthAuthorizationHandoff private[oauth] (
  val authorizationUri: String,
  val browserProof: String
) {
  override def toString: String = "OAuthAuthorizationHandoff(<redacted>)"
}

/** Trusted host capability; never an application entropy or custody injection API. */
private[oauth] trait OAuthEntropy {
  def tokenBytes(): Array[Byte]
}

/**
 * Private synchronous effects, invoked only by the following typed native interpreter or host.
 * Component program construction must not invoke this owner. HTTP uses the caller's active UoW;
 * its commit/abort, ingress authentication and service grants remain outside this class.
 */
private[cncf] final class OAuthConnection private[oauth] (
  val configuration: OAuthConnectionConfiguration,
  private[oauth] val _store: OAuthCredentialStore,
  private val _entropy: OAuthEntropy,
  loaded: Option[OAuthCredential]
) {
  import OAuthConnection.*

  private[oauth] val _client: OAuthClient = configuration._client
  private val _gate = new AnyRef
  private var _credential = loaded
  private var _pending = Option.empty[Pending]
  private var _uncertain = false
  private var _busy = false
  private var _last_outcome = Option.empty[OAuthConnectionOutcome]

  override def toString: String = "OAuthConnection(<redacted>)"

  def statusC(context: ExecutionContext): Consequence[OAuthConnectionStatus] = _local_c {
    _admit_owner_c(context).flatMap { _ =>
      _now_c(context).map(now => _gate.synchronized(_status(now)))
    }
  }

  def beginC(context: ExecutionContext): Consequence[OAuthAuthorizationHandoff] = _local_c {
    _admit_owner_c(context).flatMap { _ =>
      _gate.synchronized {
        if (_busy) _busy_c
        else for {
          now <- _now_c(context)
          expiry <- _flow_expiry_c(now)
          state <- _token_c()
          verifier <- _token_c()
          proof <- _token_c()
          request <- _client.beginC(state, verifier)
        } yield {
          _pending = Some(new Pending(request, proof, now, expiry))
          new OAuthAuthorizationHandoff(request.authorizationUri, proof)
        }
      }
    }
  }

  def completeC(returnedState: String, browserProof: String, code: Option[String],
    error: Option[String], uow: UnitOfWork): Consequence[OAuthConnectionStatus] = _local_c {
    _uow_context_c(uow).flatMap { context =>
      _admit_scope_c(context).flatMap { _ =>
        _gate.synchronized(_prepare_complete_c(returnedState, browserProof, code, error, context)).flatMap {
          case prepared: CallbackStatus => Consequence.success(prepared.status)
          case prepared: CallbackExchange =>
            _mutate_c {
              new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(
                _client.complete(prepared.flow.request, returnedState, prepared.code, prepared.observedat))
                .flatMap(credential => _save_c(credential, Some(OAuthConnectionOutcome.Authorized)))
            }.flatMap { _ =>
              _now_c(context).map(now => _gate.synchronized(_status(now)))
            }
        }
      }
    }
  }

  /** Private native provider consumption only; never project this header to component code. */
  def authorizationC(uow: UnitOfWork, expectedProvider: OAuthConnectionProviderKind):
    Consequence[Map[String, String]] = _local_c {
    _uow_context_c(uow).flatMap { context =>
      _admit_owner_c(context).flatMap { _ =>
        _gate.synchronized(_prepare_authorization_c(context, expectedProvider)).flatMap { prepared =>
          if (!prepared.refresh) Consequence.success(_header(prepared.credential))
          else _mutate_c {
            new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(
              _client.refresh(prepared.credential, prepared.observedat)).flatMap(credential => _save_c(credential, None))
          }.flatMap { credential =>
            _now_c(context).flatMap { now =>
              if (credential.expiresAt.isAfter(now)) Consequence.success(_header(credential))
              else _authorization_required_c
            }
          }
        }
      }
    }
  }

  def forgetC(context: ExecutionContext): Consequence[OAuthConnectionStatus] = _local_c {
    _admit_owner_c(context).flatMap { _ =>
      val reservation = _gate.synchronized {
        if (_busy) _busy_c[Unit]
        else _now_c(context).map { _ =>
          // This explicit local disconnect intent must never restore cached credential use.
          _pending = None
          _credential = None
          _busy = true
        }
      }
      reservation.flatMap { _ =>
        _mutate_c {
          _store.forgetC().map { _ =>
            _gate.synchronized {
              _uncertain = false
              _last_outcome = Some(OAuthConnectionOutcome.Disconnected)
            }
          }
        }.flatMap { _ =>
          _now_c(context).map(now => _gate.synchronized(_status(now)))
        }
      }
    }
  }

  private def _admit_scope_c(context: ExecutionContext): Consequence[Unit] = {
    @annotation.tailrec
    def _component_(scope: ScopeContext): Option[Component] = {
      if (scope == null) None
      else scope match {
        case current: Component.Context => Option(current.component)
        case current => current.parent match {
          case Some(parent) => _component_(parent)
          case None => None
        }
      }
    }
    if (context == null) _scope_denied_c
    else _component_(context.cncfCore.scope) match {
      case Some(component) if component.componentId == configuration.componentId &&
          component.instanceId == configuration.instanceId => Consequence.unit
      case _ => _scope_denied_c
    }
  }

  private def _admit_owner_c(context: ExecutionContext): Consequence[Unit] =
    _admit_scope_c(context).flatMap { _ =>
      if (context.security == null) _owner_denied_c
      else {
        val subject = SecuritySubject.from(context.security)
        if (subject.isAuthenticated && subject.isProviderAuthenticated && subject.subjectId == configuration.ownerId)
          Consequence.unit
        else _owner_denied_c
      }
    }

  private def _token_c(): Consequence[String] = {
    val bytes = _entropy.tokenBytes()
    if (bytes == null || bytes.length != 32) _unavailable_c
    else Consequence.success(Base64.getUrlEncoder.withoutPadding.encodeToString(bytes))
  }

  /** Called only under the brief state gate; all matching decisions precede HTTP. */
  private def _prepare_complete_c(returnedstate: String, browserproof: String, code: Option[String],
    error: Option[String], context: ExecutionContext): Consequence[Callback] = {
    if (_busy) _busy_c
    else _pending match {
      case Some(flow) if _matches(flow.request._state, returnedstate) && _matches(flow.proof, browserproof) =>
        // The matched capability is one-shot even for denial, malformed results and failed exchange.
        _pending = None
        _now_c(context) match {
          case Consequence.Failure(conclusion) =>
            _last_outcome = Some(OAuthConnectionOutcome.Expired)
            Consequence.Failure(conclusion)
          case Consequence.Success(now) =>
            if (now.isBefore(flow.issuedat) || !now.isBefore(flow.expiresat)) {
              _last_outcome = Some(OAuthConnectionOutcome.Expired)
              Consequence.stateConflict("OAuth authorization flow has expired")
            } else if (code == null || error == null) _invalid_response_c
            else if (code.isEmpty && error.exists(OAuthValidation.printable(_, 128))) {
              _last_outcome = Some(OAuthConnectionOutcome.Denied)
              Consequence.success(new CallbackStatus(_status(now)))
            } else if (error.isEmpty && code.exists(OAuthValidation.printable(_, 8192))) {
              _busy = true
              Consequence.success(new CallbackExchange(flow, code.get, now))
            } else _invalid_response_c
        }
      case _ => _flow_invalid_c
    }
  }

  private def _invalid_response_c[A]: Consequence[A] = {
    _last_outcome = Some(OAuthConnectionOutcome.Failed)
    Consequence.argumentInvalid("OAuth authorization response is invalid")
  }

  private def _prepare_authorization_c(context: ExecutionContext,
    expectedprovider: OAuthConnectionProviderKind): Consequence[Authorization] = {
    if (_busy) _busy_c
    else if (expectedprovider == null || expectedprovider != configuration.providerKind)
      Consequence.securityPermissionDenied("OAuth connection provider is not allowed")
    else if (_uncertain) Consequence.stateConflict("OAuth connection requires reconciliation")
    else _now_c(context).flatMap { now =>
      _credential match {
        case Some(credential) if credential.expiresAt.isAfter(now) =>
          Consequence.success(new Authorization(credential, now, false))
        case Some(credential) if credential.canRefresh =>
          _busy = true
          Consequence.success(new Authorization(credential, now, true))
        case _ => _authorization_required_c
      }
    }
  }

  private def _save_c(credential: OAuthCredential,
    outcome: Option[OAuthConnectionOutcome]): Consequence[OAuthCredential] =
    _store.saveC(credential).map { _ =>
      _gate.synchronized {
        _credential = Some(credential)
        _uncertain = false
        outcome.foreach(value => _last_outcome = Some(value))
      }
      credential
    }

  /** Caller already reserved busy under the gate; no state monitor crosses native HTTP. */
  private def _mutate_c[A](body: => Consequence[A]): Consequence[A] = {
    try {
      val result = body
      result match {
        case Consequence.Failure(_) => _mark_uncertain()
        case _ => ()
      }
      result
    } catch {
      case NonFatal(_) =>
        _mark_uncertain()
        _unavailable_c
    } finally {
      _gate.synchronized { _busy = false }
    }
  }

  private def _mark_uncertain(): Unit = _gate.synchronized {
    _uncertain = true
    _last_outcome = Some(OAuthConnectionOutcome.Failed)
  }

  private def _status(now: Instant): OAuthConnectionStatus = {
    val state = if (_uncertain) OAuthConnectionState.Uncertain
      else _credential match {
        case Some(credential) if credential.expiresAt.isAfter(now) => OAuthConnectionState.Connected
        case Some(_) => OAuthConnectionState.Expired
        case None => OAuthConnectionState.AuthorizationRequired
      }
    OAuthConnectionStatus(state, _credential.exists(_.canRefresh), _credential.map(_.expiresAt),
      _pending.exists(flow => !now.isBefore(flow.issuedat) && now.isBefore(flow.expiresat)), _busy, _last_outcome)
  }
}

private[cncf] object OAuthConnection {
  /** Every secret-bearing runtime model has instance-independent ownership and redacted display. */
  private final class Pending(val request: OAuthAuthorizationRequest, val proof: String,
    val issuedat: Instant, val expiresat: Instant) {
    override def toString: String = "OAuthPending(<redacted>)"
  }
  private sealed abstract class Callback
  private final class CallbackStatus(val status: OAuthConnectionStatus) extends Callback {
    override def toString: String = "OAuthCallbackStatus(<redacted>)"
  }
  private final class CallbackExchange(val flow: Pending, val code: String,
    val observedat: Instant) extends Callback {
    override def toString: String = "OAuthCallbackExchange(<redacted>)"
  }
  private final class Authorization(val credential: OAuthCredential, val observedat: Instant,
    val refresh: Boolean) {
    override def toString: String = "OAuthPrivateAuthorization(<redacted>)"
  }
  private final class SecureEntropy extends OAuthEntropy {
    private val _random = new SecureRandom
    def tokenBytes(): Array[Byte] = {
      val bytes = new Array[Byte](32)
      _random.nextBytes(bytes)
      bytes
    }
    override def toString: String = "OAuthEntropy(<redacted>)"
  }

  def createC(configuration: OAuthConnectionConfiguration): Consequence[OAuthConnection] = _local_c {
    if (configuration == null) _configuration_invalid_c
    else createC(configuration, configuration._store, new SecureEntropy)
  }

  private[oauth] def createC(configuration: OAuthConnectionConfiguration, store: OAuthCredentialStore,
    entropy: OAuthEntropy): Consequence[OAuthConnection] = _local_c {
    if (configuration == null || store == null || entropy == null) _configuration_invalid_c
    else store.loadC().flatMap { loaded =>
      _loaded_c(configuration._client, loaded).map { valid =>
        new OAuthConnection(configuration, store, entropy, valid)
      }
    }
  }

  private def _loaded_c(client: OAuthClient, loaded: Option[OAuthCredential]): Consequence[Option[OAuthCredential]] = {
    if (loaded == null) _record_invalid_c
    else loaded match {
      case None => Consequence.success(None)
      case Some(credential) if credential != null && (credential._client eq client) =>
        client._restore_credential_c(credential.accessToken, credential.refreshToken,
          credential.grantedScopes, credential.expiresAt).map(_ => loaded)
      case _ => _record_invalid_c
    }
  }

  private def _uow_context_c(uow: UnitOfWork): Consequence[ExecutionContext] =
    if (uow == null || uow.executionContext == null) _scope_denied_c
    else Consequence.success(uow.executionContext)

  private def _now_c(context: ExecutionContext): Consequence[Instant] = {
    try {
      val now = context.clock.instant()
      if (now == null) _clock_invalid_c else Consequence.success(now)
    } catch { case NonFatal(_) => _clock_invalid_c }
  }

  private def _flow_expiry_c(now: Instant): Consequence[Instant] = {
    try Consequence.success(now.plusSeconds(300)) catch { case NonFatal(_) => _clock_invalid_c }
  }

  private def _matches(expected: String, returned: String): Boolean = OAuthValidation.state(returned) &&
    MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), returned.getBytes(StandardCharsets.US_ASCII))

  private def _header(credential: OAuthCredential): Map[String, String] =
    Map("Authorization" -> ("Bearer " + credential.accessToken))

  private def _configuration_invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid("OAuth connection configuration is invalid")
  private def _record_invalid_c[A]: Consequence[A] =
    Consequence.resourceInvalid("OAuth credential storage record is invalid")
  private def _scope_denied_c[A]: Consequence[A] =
    Consequence.securityPermissionDenied("OAuth connection scope is not allowed")
  private def _owner_denied_c[A]: Consequence[A] =
    Consequence.securityPermissionDenied("OAuth connection owner is not allowed")
  private def _busy_c[A]: Consequence[A] = Consequence.stateConflict("OAuth connection update is in progress")
  private def _clock_invalid_c[A]: Consequence[A] = Consequence.stateConflict("OAuth connection clock is invalid")
  private def _flow_invalid_c[A]: Consequence[A] = Consequence.securityPermissionDenied("OAuth authorization flow is invalid")
  private def _authorization_required_c[A]: Consequence[A] =
    Consequence.securityPermissionDenied("OAuth connection requires authorization")
  private def _unavailable_c[A]: Consequence[A] =
    Consequence.serviceUnavailable("OAuth connection operation is unavailable")
  private def _local_c[A](body: => Consequence[A]): Consequence[A] = {
    try body catch { case NonFatal(_) => _unavailable_c }
  }
}
