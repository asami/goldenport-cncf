package org.goldenport.cncf.context

/*
 * @since   Jan. 17, 2026
 * @version Jan. 17, 2026
 * @author  ASAMI, Tomoharu
 */
import org.goldenport.cncf.http.HttpDriver

final class GlobalRuntimeContext(
  name: String,
  observabilityContext: ObservabilityContext,
  val httpDriver: HttpDriver
) extends ScopeContext(
  kind = ScopeKind.Runtime,
  name = name,
  parent = None,
  observabilityContext = observabilityContext
)
