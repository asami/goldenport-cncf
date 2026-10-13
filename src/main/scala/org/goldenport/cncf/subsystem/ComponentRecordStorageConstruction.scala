package org.goldenport.cncf.subsystem

import org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration

/**
 * Trusted construction scope only; ordinary components cannot install grants.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[cncf] object ComponentRecordStorageConstruction {
  private val _declaration = new ThreadLocal[ComponentRecordStorageDeclaration]()

  def withDeclaration[A](declaration: ComponentRecordStorageDeclaration)(f: => A): A = {
    require(declaration != null, "Component record storage declaration is required")
    val previous = Option(_declaration.get())
    _declaration.set(declaration)
    try f
    finally previous match {
      case Some(value) => _declaration.set(value)
      case None => _declaration.remove()
    }
  }

  def current: Option[ComponentRecordStorageDeclaration] = Option(_declaration.get())
}
