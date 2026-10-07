package org.goldenport.cncf.subsystem

import org.goldenport.cncf.event.EventStore

/** Trusted bootstrap service capture, independent of storage declarations.
  * No sender may supply a policy/service to an ordinary event operation.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] object EventStoreConstruction {
  private val _service = new ThreadLocal[EventStore]()
  def withService[A](service: EventStore)(f: => A): A = {
    require(service != null, "EventStore service is required")
    val previous = Option(_service.get())
    _service.set(service)
    try f
    finally previous match {
      case Some(value) => _service.set(value)
      case None => _service.remove()
    }
  }
  def current: Option[EventStore] = Option(_service.get())
}
