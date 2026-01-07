package org.goldenport.cncf.context

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
enum ScopeKind {
  case Subsystem
  case Component
  case Service
  case Action
}

final case class ScopeContext(
  kind: ScopeKind,
  name: String,
  parent: Option[ScopeContext],
  observabilityContext: ObservabilityContext
) extends ObservationDsl {
  def createChildScope(kind: ScopeKind, name: String): ScopeContext =
    ScopeContext(
      kind = kind,
      name = name,
      parent = Some(this),
      observabilityContext = observabilityContext.createChild(kind, name)
    )

  protected def observability_Context: ObservabilityContext =
    observabilityContext
}
