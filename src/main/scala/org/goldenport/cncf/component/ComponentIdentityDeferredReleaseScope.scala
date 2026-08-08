package org.goldenport.cncf.component

/*
 * Thread-confined expected identity for exact deferred-release generated code.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentIdentityDeferredReleaseScope {
  private val _current = new ThreadLocal[List[ComponentIdentityDeferredReleaseEntry]] {
    override def initialValue(): List[ComponentIdentityDeferredReleaseEntry] = Nil
  }

  def withExpected[A](
    entry: ComponentIdentityDeferredReleaseEntry
  )(
    body: => A
  ): A = {
    val previous = _current.get()
    _current.set(entry :: previous)
    try body
    finally {
      if (previous.isEmpty) _current.remove()
      else _current.set(previous)
    }
  }

  def resolveExact(
    legacyid: String
  ): Option[ComponentId] =
    Option(legacyid).flatMap { value =>
      _current.get().headOption.collect {
        case entry if value == entry.legacylocalid => entry.componentid
      }
    }

}
