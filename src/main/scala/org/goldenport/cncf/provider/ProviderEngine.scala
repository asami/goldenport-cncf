package org.goldenport.cncf.provider

import org.goldenport.{Conclusion, Consequence}

/*
 * @since   May. 23, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProviderEngine {
  def execute[A](
    call: ProviderCall[A]
  ): Consequence[A] = {
    val calltree = call.executionContext.observability.callTreeContext
    val started = System.nanoTime()
    val baseattributes = Map(
      "calltree_kind" -> "provider",
      "provider" -> call.providerRequest.provider,
      "operation" -> call.providerRequest.operation
    ) ++ call.providerRequest.attributes
    if (calltree.isEnabled) {
      calltree.enter(call.providerRequest.label, baseattributes)
      try {
        val result = call.run()
        val elapsed = ((System.nanoTime() - started) / 1000000L).toString
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success", "duration_ms" -> elapsed) ++ call.calltreeResultAttributes(success.result))
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "duration_ms" -> elapsed,
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
        }
        result
      } catch {
        case e: Throwable =>
          val conclusion = Conclusion.from(e)
          val elapsed = ((System.nanoTime() - started) / 1000000L).toString
          calltree.leave(Map(
            "outcome" -> "failure",
            "duration_ms" -> elapsed,
            "error" -> conclusion.display
          ))
          Consequence.Failure(conclusion)
      }
    } else {
      call.run()
    }
  }
}

object ProviderEngine {
  def execute[A](
    call: ProviderCall[A]
  ): Consequence[A] =
    new ProviderEngine().execute(call)
}
