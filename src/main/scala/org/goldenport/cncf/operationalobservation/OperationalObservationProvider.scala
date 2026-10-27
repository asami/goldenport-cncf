package org.goldenport.cncf.operationalobservation

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.net.SocketTimeoutException
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property
import scala.util.control.NonFatal

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
private[cncf] trait OperationalObservationProvider {
  def readC(
    source: ObservationSource, request: ObservationRead,
    effects: UnitOfWorkOp ~> Consequence
  ): AxisObservation
}

private[cncf] final case class ObservationRead(
  context: ExecutionContext, timeoutmillis: Int, maximumbytes: Int
) {
  def now: Instant = context.clock.instant()
}

/** Only the interpreter of the active UoW supplies effects. No driver is exposed. */
private[cncf] object OperationalObservationProvider {
  def fetchC(
    url: String, request: ObservationRead, effects: UnitOfWorkOp ~> Consequence
  ): Either[ObservationReason, HttpResponse] = {
    val properties = Vector(
      Property("http.total-timeout-ms", request.timeoutmillis.toString, None),
      Property("http.connect-timeout-ms", request.timeoutmillis.toString, None),
      Property("http.read-timeout-ms", request.timeoutmillis.toString, None),
      Property("http.max-response-bytes", request.maximumbytes.toString, None),
      Property("http.follow-redirects", "false", None))
    val thread = Thread.currentThread()
    if (thread.isInterrupted || request.context.jobContext.cancellationScope.exists(_.isCancelled))
      Left(ObservationReason.BudgetExhausted)
    else {
      val registration = request.context.jobContext.cancellationScope.map(_.register {
        thread.interrupt()
        Consequence.unit
      })
      try effects(UnitOfWorkOp.HttpGet(url, properties = properties)) match {
        case Consequence.Success(response) =>
          if (response.getString.exists(_.getBytes(StandardCharsets.UTF_8).length > request.maximumbytes))
            Left(ObservationReason.InvalidSource)
          else Right(response)
        case Consequence.Failure(conclusion) =>
          val code = conclusion.status.webCode.code
          if (thread.isInterrupted || request.context.jobContext.cancellationScope.exists(_.isCancelled))
            Left(ObservationReason.BudgetExhausted)
          else if (code == 401 || code == 403) Left(ObservationReason.PermissionDenied)
          else if (conclusion.getException.exists(_.isInstanceOf[SocketTimeoutException])) Left(ObservationReason.Timeout)
          else Left(ObservationReason.SourceUnavailable)
      } catch {
        case _: SocketTimeoutException => Left(ObservationReason.Timeout)
        case _: InterruptedException =>
          thread.interrupt()
          Left(ObservationReason.BudgetExhausted)
        case NonFatal(_) => Left(if (thread.isInterrupted) ObservationReason.BudgetExhausted else ObservationReason.SourceUnavailable)
      } finally registration.foreach(_.close())
    }
  }
}
