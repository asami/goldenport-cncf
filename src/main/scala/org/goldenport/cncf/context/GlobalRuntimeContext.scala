package org.goldenport.cncf.context

import org.goldenport.cncf.http.HttpDriver

/*
 * @since   Jan. 17, 2026
 * @version Jan. 17, 2026
 * @author  ASAMI, Tomoharu
 */
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
