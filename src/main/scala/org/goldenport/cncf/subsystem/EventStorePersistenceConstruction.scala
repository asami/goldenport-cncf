package org.goldenport.cncf.subsystem

import org.goldenport.cncf.event.EventStorePersistenceDeclaration

/**
 * Trusted runtime assembly scope, captured once by a newly built Subsystem.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[cncf] object EventStorePersistenceConstruction {
  private val _declaration = new ThreadLocal[EventStorePersistenceDeclaration]()

  def withDeclaration[A](declaration: EventStorePersistenceDeclaration)(f: => A): A = {
    if (declaration == null)
      throw new IllegalArgumentException("EventStore persistence declaration is required")
    val previous = Option(_declaration.get())
    _declaration.set(declaration)
    try f
    finally previous match {
      case Some(value) => _declaration.set(value)
      case None => _declaration.remove()
    }
  }

  def current: Option[EventStorePersistenceDeclaration] = Option(_declaration.get())
}
