package org.goldenport.cncf.context

// Temporary adapter for migration to core-owned EnvironmentContext.
@deprecated(
  "Use org.goldenport.context.EnvironmentContext (core-owned abstraction)",
  "0.x.y"
)
type CncfEnvironmentContext = org.goldenport.context.EnvironmentContext
