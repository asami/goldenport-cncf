package org.goldenport.cncf.context

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait ObservationDsl {
  protected def observability_Context: ObservabilityContext
  protected def scope_context: Option[ScopeContext] = None

  def observe_infoC(
    message: String,
    attributes: Record = Record.empty
  ): Consequence[Unit] =
    _observeC("info", message, attributes)

  def observe_errorC(
    message: String,
    attributes: Record = Record.empty
  ): Consequence[Unit] =
    _observeC("error", message, attributes)

  def observe_info(
    message: String,
    attributes: Record = Record.empty
  ): Unit = {
    val _ = observe_infoC(message, attributes)
  }

  def observe_error(
    message: String,
    attributes: Record = Record.empty
  ): Unit = {
    val _ = observe_errorC(message, attributes)
  }

  private def _observeC(
    level: String,
    message: String,
    attributes: Record
  ): Consequence[Unit] = {
    scope_context match {
      case Some(sc) =>
        level match {
          case "error" =>
            val _ = observability_Context.emitError(sc, message, attributes)
          case "warn" =>
            val _ = observability_Context.emitWarn(sc, message, attributes)
          case _ =>
            val _ = observability_Context.emitInfo(sc, message, attributes)
        }
      case None =>
        ()
    }
    Consequence.Success(())
  }
}
