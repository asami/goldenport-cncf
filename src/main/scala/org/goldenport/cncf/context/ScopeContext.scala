package org.goldenport.cncf.context

import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.http.HttpDriver

/*
 * @since   Jan.  7, 2026
 *  version Jan. 20, 2026
 * @version Feb.  3, 2026
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

  def workAreaSpace: WorkAreaSpace = parent match {
    case Some(s) => s.workAreaSpace
    case None => GlobalContext.globalContext.workAreaSpace
  }

  def httpDriver: HttpDriver = {
    core.httpDriverOption match {
      case Some(driver) =>
        driver
      case None =>
        parent match {
          case Some(p) => p.httpDriver
          case None => throw new IllegalStateException("ScopeContext has no HttpDriver")
        }
    }
  }

  def formatPing: String =
    parent match {
      case Some(p) => p.formatPing
      case None => GlobalRuntimeContext.defaultPing
    }

  def createChildScope(kind: ScopeKind, name: String): ScopeContext =
    ScopeContext(
      kind = kind,
      name = name,
      parent = Some(this),
      observabilityContext = observabilityContext.createChild(this, kind, name)
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
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver]
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
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver] = None
  ): ScopeContext = {
    Instance(
      ScopeContext.Core(
        kind = kind,
        name = name,
        parent = parent,
        observabilityContext = observabilityContext,
        httpDriverOption = httpDriverOption
      )
    )
  }

  def unapply(x: ScopeContext): Option[ScopeContext.Core] = {
    Some(x.core)
  }
}
