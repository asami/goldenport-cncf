package org.goldenport.cncf.display

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Auxiliary, server-owned support and continuation capability. */
trait PresentationContextResolver {
  def resolve(request: PresentationContextResolver.Request)(using ctx: ExecutionContext):
    Consequence[PresentationContextResolver.Result]
}

object PresentationContextResolver {
  final case class Request(
    binding: PresentationContextRuntime.Binding,
    continuation: Option[PresentationContextRuntime.References],
    now: Instant
  )
  enum Result {
    case Unavailable
    case Supported(resolution: PresentationContextRuntime.Resolution)
  }
  val disabled: PresentationContextResolver = new PresentationContextResolver {
    def resolve(request: Request)(using ctx: ExecutionContext): Consequence[Result] =
      Consequence.success(Result.Unavailable)
  }
}
