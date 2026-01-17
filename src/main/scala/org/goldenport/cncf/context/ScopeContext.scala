package org.goldenport.cncf.context

/*
 * @since   Jan.  7, 2026
 * @version Jan. 18, 2026
 * @author  ASAMI, Tomoharu
 */
enum ScopeKind {
  case Runtime
  case Subsystem
  case Component
  case Service
  case Action
}

abstract class ScopeContext() extends ObservationDsl with ScopeContext.Core.Holder {
  def core: ScopeContext.Core

  def createChildScope(kind: ScopeKind, name: String): ScopeContext =
    ScopeContext(
      kind = kind,
      name = name,
      parent = Some(this),
      observabilityContext = observabilityContext.createChild(kind, name)
    )

  protected def observability_Context: ObservabilityContext =
    observabilityContext

  override protected def scope_context: Option[ScopeContext] =
    Some(this)
}

object ScopeContext {
  final case class Core(
    kind: ScopeKind,
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext
  )
  object Core {
    trait Holder {
      def core: Core

      def kind: ScopeKind = core.kind
      def name: String = core.name
      def parent: Option[ScopeContext] = core.parent
      def observabilityContext: ObservabilityContext = core.observabilityContext
    }
  }

  final case class Instance(core: ScopeContext.Core) extends ScopeContext() {
  }

  def apply(
    kind: ScopeKind,
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext
  ): ScopeContext = {
    new Instance(
      ScopeContext.Core(
        kind = kind,
        name = name,
        parent = parent,
        observabilityContext = observabilityContext
      )
    )
  }

  def unapply(x: ScopeContext): Option[ScopeContext.Core] = {
    Some(x.core)
  }
}
