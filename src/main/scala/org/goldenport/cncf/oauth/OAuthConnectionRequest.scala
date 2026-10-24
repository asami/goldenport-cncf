package org.goldenport.cncf.oauth

import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Oct. 21, 2026
 * @version Oct. 24, 2026
 */
/** Closed, inert framework intent; only the canonical native interpreter executes it. */
private[cncf] sealed trait OAuthConnectionRequest[A] {
  def connectionId: String
  private[cncf] def isDriveAuthorization: Boolean = false
  private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[A]
  override final def toString: String = "OAuthConnectionRequest(<redacted>)"
}

private[cncf] object OAuthConnectionRequest {
  def status(connectionId: String): OAuthConnectionRequest[OAuthConnectionStatus] = new Status(connectionId)

  def begin(connectionId: String): OAuthConnectionRequest[OAuthAuthorizationHandoff] = new Begin(connectionId)

  def complete(connectionId: String, returnedState: String, browserProof: String, code: Option[String],
    error: Option[String]): OAuthConnectionRequest[OAuthConnectionStatus] =
    new Complete(connectionId, returnedState, browserProof, code, error)

  def forget(connectionId: String): OAuthConnectionRequest[OAuthConnectionStatus] = new Forget(connectionId)

  def driveAuthorization(connectionId: String): OAuthConnectionRequest[Map[String, String]] =
    new DriveAuthorization(connectionId)

  private final class Status(val connectionId: String) extends OAuthConnectionRequest[OAuthConnectionStatus] {
    private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[OAuthConnectionStatus] =
      connection.statusC(uow.executionContext)
  }

  private final class Begin(val connectionId: String) extends OAuthConnectionRequest[OAuthAuthorizationHandoff] {
    private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[OAuthAuthorizationHandoff] =
      connection.beginC(uow.executionContext)
  }

  private final class Complete(val connectionId: String, returnedstate: String, browserproof: String,
    code: Option[String], error: Option[String]) extends OAuthConnectionRequest[OAuthConnectionStatus] {
    private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[OAuthConnectionStatus] =
      connection.completeC(returnedstate, browserproof, code, error, uow)
  }

  private final class Forget(val connectionId: String) extends OAuthConnectionRequest[OAuthConnectionStatus] {
    private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[OAuthConnectionStatus] =
      connection.forgetC(uow.executionContext)
  }

  private final class DriveAuthorization(val connectionId: String) extends OAuthConnectionRequest[Map[String, String]] {
    override private[cncf] def isDriveAuthorization: Boolean = true
    private[cncf] def executeC(connection: OAuthConnection, uow: UnitOfWork): Consequence[Map[String, String]] =
      connection.authorizationC(uow, OAuthConnectionProviderKind.GoogleDrive)
  }
}
