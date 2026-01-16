package org.goldenport.cncf.context

final class GlobalRuntimeContext(
  name: String,
  observabilityContext: ObservabilityContext
) extends ScopeContext(
  kind = ScopeKind.Runtime,
  name = name,
  parent = None,
  observabilityContext = observabilityContext
)
