package org.goldenport.cncf.security

import java.lang.ref.WeakReference
import org.goldenport.cncf.context.SecurityContext.CredentialTransports

/** In-process admitted origin, detached from public products and their equality.
  * This proves neither explicit human consent nor authorization for an effect.
  * @since   Oct.  1, 2026
  *  version Oct.  8, 2026
  * @version Nov.  6, 2026
  */
private[cncf] object SecurityAuthenticationProvenance {
  private enum Origin {
    case Provider, TrustedLocal
  }

  private final case class Entry(
    reference: WeakReference[AnyRef],
    origin: Option[Origin],
    transports: Option[CredentialTransports]
  )
  private val _lock = new AnyRef
  private var _entries = Vector.empty[Entry]

  def providerAuthenticated[A <: AnyRef](value: A): A =
    _mark(value, Origin.Provider)

  def trustedLocalAuthenticated[A <: AnyRef](value: A): A =
    _mark(value, Origin.TrustedLocal)

  def isProviderAuthenticated(value: AnyRef): Boolean =
    _origin(value).contains(Origin.Provider)

  def isTrustedLocalAuthenticated(value: AnyRef): Boolean =
    _origin(value).contains(Origin.TrustedLocal)

  def credentialTransports(value: AnyRef): Option[CredentialTransports] =
    _entry(value).flatMap(_.transports)

  def withCredentialTransports[A <: AnyRef](value: A, transports: CredentialTransports): A =
    _set_transports(value, Some(transports))

  /** Copies input-shape facts only; a physical request grants no authentication. */
  def inheritCredentialTransports[A <: AnyRef](source: AnyRef, target: A): A =
    _set_transports(target, credentialTransports(source))

  def inherit(source: AnyRef, target: AnyRef): Unit =
    _entry(source).foreach { entry =>
      entry.origin.foreach(origin => _mark(target, origin))
      _set_transports(target, entry.transports)
    }

  private def _mark[A <: AnyRef](value: A, origin: Origin): A = _lock.synchronized {
    require(value != null, "Authentication provenance requires an object")
    val transports = _entry(value).flatMap(_.transports)
    _replace(value, Some(origin), transports)
  }

  private def _set_transports[A <: AnyRef](value: A, transports: Option[CredentialTransports]): A = _lock.synchronized {
    require(value != null, "Credential transport provenance requires an object")
    val origin = _entry(value).flatMap(_.origin)
    _replace(value, origin, transports)
  }

  private def _replace[A <: AnyRef](
    value: A,
    origin: Option[Origin],
    transports: Option[CredentialTransports]
  ): A = {
    _entries = _entries.filter { entry =>
      val retained = entry.reference.get
      retained != null && !(retained eq value)
    }
    if (origin.nonEmpty || transports.nonEmpty)
      _entries = _entries :+ Entry(new WeakReference[AnyRef](value), origin, transports)
    value
  }

  private def _origin(value: AnyRef): Option[Origin] =
    _entry(value).flatMap(_.origin)

  private def _entry(value: AnyRef): Option[Entry] = _lock.synchronized {
    _entries = _entries.filter(_.reference.get != null)
    if (value == null) None
    else _entries.find(entry => entry.reference.get eq value)
  }
}
