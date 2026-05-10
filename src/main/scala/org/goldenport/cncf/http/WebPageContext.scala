package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.composite.{CompositeQueryEngine, CompositeQueryRequest, CompositeQueryResponse, NamedQuery}
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
  def providedKeys: Vector[String] = Vector.empty

  def queries(request: WebPageContextRequest): Vector[NamedQuery] =
    Vector.empty

  def resolve(
    request: WebPageContextRequest,
    response: CompositeQueryResponse
  )(using ExecutionContext): Consequence[WebPageContext] =
    resolve(request)

  def resolve(request: WebPageContextRequest)(using ExecutionContext): Consequence[WebPageContext] =
    Consequence.success(WebPageContext.empty)
}

object WebPageContextProviderRuntime {
  def providers(subsystem: Subsystem): Vector[WebPageContextProvider] =
    subsystem.components.flatMap(_.webPageContextProviders)

  def resolve(
    subsystem: Subsystem,
    request: WebPageContextRequest
  )(using ExecutionContext): WebPageContext = {
    val ps = providers(subsystem)
    val queries = ps.flatMap(_.queries(request))
    val response =
      if (queries.isEmpty)
        CompositeQueryResponse(Vector.empty)
      else
        CompositeQueryEngine(subsystem).execute(CompositeQueryRequest(queries)) match {
          case Consequence.Success(response) =>
            response
          case Consequence.Failure(conclusion) =>
            CompositeQueryResponse(
              Vector.empty,
              Vector(org.goldenport.cncf.composite.CompositeQueryDiagnostic(
                "pageContext",
                false,
                conclusion.toString
              ))
            )
        }
    val context0 =
      if (response.diagnostics.isEmpty)
        WebPageContext.empty
      else
        WebPageContext(diagnostics = response.diagnostics.map(_.message))
    ps.foldLeft(context0) { (acc, provider) =>
      provider.resolve(request, response) match {
        case Consequence.Success(context) =>
          acc.merge(context)
        case Consequence.Failure(conclusion) =>
          acc.merge(WebPageContext(diagnostics = Vector(conclusion.toString)))
      }
    }
  }
}
