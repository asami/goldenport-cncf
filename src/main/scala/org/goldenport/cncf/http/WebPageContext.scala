package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   May. 10, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebPageContextRequest(
  app: String,
  page: Vector[String],
  routePath: String,
  values: Map[String, String] = Map.empty,
  sessionId: Option[String] = None,
  authenticated: Boolean = false
)

final case class WebPageContext(
  values: Map[String, String] = Map.empty,
  diagnostics: Vector[String] = Vector.empty
) {
  def merge(rhs: WebPageContext): WebPageContext =
    WebPageContext(values ++ rhs.values, diagnostics ++ rhs.diagnostics)
}

object WebPageContext {
  val empty: WebPageContext = WebPageContext()
}

trait WebPageContextProvider {
  def resolve(request: WebPageContextRequest)(using ExecutionContext): Consequence[WebPageContext]
}

object WebPageContextProviderRuntime {
  def providers(subsystem: Subsystem): Vector[WebPageContextProvider] =
    subsystem.components.flatMap(_.webPageContextProviders)

  def resolve(
    subsystem: Subsystem,
    request: WebPageContextRequest
  )(using ExecutionContext): WebPageContext =
    providers(subsystem).foldLeft(WebPageContext.empty) { (acc, provider) =>
      provider.resolve(request) match {
        case Consequence.Success(context) =>
          acc.merge(context)
        case Consequence.Failure(conclusion) =>
          acc.merge(WebPageContext(diagnostics = Vector(conclusion.toString)))
      }
    }
}
