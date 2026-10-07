package org.goldenport.cncf.security

import java.lang.ref.WeakReference

/** In-process admitted origin, detached from public products and their equality.
  * This proves neither explicit human consent nor authorization for an effect.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] object SecurityAuthenticationProvenance {
  private enum Origin {
    case Provider, TrustedLocal
  }

  private final case class Entry(reference: WeakReference[AnyRef], origin: Origin)
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

  def inherit(source: AnyRef, target: AnyRef): Unit =
    _origin(source).foreach(origin => _mark(target, origin))

  private def _mark[A <: AnyRef](value: A, origin: Origin): A = _lock.synchronized {
    require(value != null, "Authentication provenance requires an object")
    _entries = _entries.filter { entry =>
      val retained = entry.reference.get
      retained != null && !(retained eq value)
    } :+ Entry(new WeakReference[AnyRef](value), origin)
    value
  }

  private def _origin(value: AnyRef): Option[Origin] = _lock.synchronized {
    _entries = _entries.filter(_.reference.get != null)
    if (value == null) None
    else _entries.find(entry => entry.reference.get eq value).map(_.origin)
  }
}
