package org.goldenport.cncf.context

import org.goldenport.Consequence
import org.goldenport.cncf.workarea.WorkAreaSpace

/*
 * @since   Feb.  3, 2026
 * @version Feb.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class GlobalContext(
  val workAreaSpace: WorkAreaSpace
)

object GlobalContext {
  private var _global_context: Option[GlobalContext] = None

  def globalContext: GlobalContext = _global_context getOrElse {
    Consequence.RAISE.UninitializedState
  }

  def set(p: GlobalContext): Unit = {
    _global_context = Some(p)
  }
}
