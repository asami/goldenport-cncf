package org.goldenport.cncf.context

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.slf4j.LoggerFactory
import org.goldenport.cncf.cli.LogBackendHolder

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait ObservationDsl {
  private val _logger = LoggerFactory.getLogger(classOf[ObservationDsl])

  protected def observability_Context: ObservabilityContext
  protected def scope_context: Option[ScopeContext] = None

  def observe_infoC(
    message: String,
    attributes: Record = Record.empty
  ): Consequence[Unit] =
    _observe("info", message, None)

  def observe_errorC(
    message: String,
    attributes: Record = Record.empty
  ): Consequence[Unit] =
    _observe("error", message, None)

  private def _observe(
    level: String,
    message: String,
    cause: Option[Throwable]
  ): Consequence[Unit] = {
    val ctx = _context()
    val text = if (ctx.isEmpty) message else s"$message $ctx"
    _log_backend_(level, text)
    try {
      level match {
        case "error" =>
          cause match {
            case Some(c) => _logger.error(text, c)
            case None => _logger.error(text)
          }
        case _ =>
          _logger.info(text)
      }
    } catch {
      case _: Throwable =>
        ()
    }
    Consequence.Success(())
  }

  private def _log_backend_(level: String, message: String): Unit = {
    LogBackendHolder.backend.foreach { backend =>
      val scope = scope_context.map { sc =>
        s"scope=${sc.kind} name=${sc.name}"
      }
      val prefix = scope.map(s => s"event=$level $s ").getOrElse(s"event=$level ")
      backend.log(level, s"$prefix$message")
    }
  }

  private def _context(): String = {
    val traceid = Option(observability_Context.traceId.value).map(t => s"traceId=$t")
    val correlationid =
      observability_Context.correlationId.map(id => s"correlationId=${id.value}")
    val parts = Vector(traceid, correlationid).flatten
    if (parts.isEmpty) "" else parts.mkString("[", " ", "]")
  }
}
