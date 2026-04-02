package org.goldenport.cncf.context

import org.goldenport.Consequence
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.context.DataStoreContext
import org.goldenport.cncf.context.EntityStoreContext
import org.goldenport.cncf.context.EntitySpaceContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.entity.runtime.EntitySpace

/*
 * @since   Jan.  7, 2026
 *  version Jan. 20, 2026
 *  version Feb. 25, 2026
 * @version Apr.  3, 2026
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
    httpDriverOption: Option[HttpDriver],
    datastore: Option[DataStoreContext] = None,
    entitystore: Option[EntityStoreContext] = None,
    entityspace: Option[EntitySpaceContext] = None,
    aggregateInternalRead: Boolean = false
  )
  object Core {
    trait Holder {
      def core: Core

      def kind: ScopeKind = core.kind
      def name: String = core.name
      def parent: Option[ScopeContext] = core.parent
      def observabilityContext: ObservabilityContext = core.observabilityContext
      def isAggregateInternalRead: Boolean =
        core.aggregateInternalRead || parent.exists(_.isAggregateInternalRead)

      def dataStoreSpace: DataStoreSpace =
        core.datastore.map(_.dataStoreSpace) orElse
          parent.map(_.dataStoreSpace) getOrElse
          ScopeContext.defaultDataStoreSpace
      def entityStoreSpace: EntityStoreSpace =
        core.entitystore.map(_.entityStoreSpace) orElse
          parent.map(_.entityStoreSpace) getOrElse
          ScopeContext.defaultEntityStoreSpace
      def entitySpace: EntitySpace =
        core.entityspace.map(_.entitySpace) orElse
          parent.map(_.entitySpace) getOrElse
          ScopeContext.defaultEntitySpace
    }
  }

  final case class Instance(core: ScopeContext.Core) extends ScopeContext() {
  }

  def withAggregateInternalRead(
    scope: ScopeContext,
    enabled: Boolean
  ): ScopeContext =
    scope match {
      case Instance(core) => Instance(core.copy(aggregateInternalRead = enabled))
      case other => Instance(other.core.copy(aggregateInternalRead = enabled))
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

  private lazy val defaultDataStoreSpace: DataStoreSpace =
    DataStoreSpace.default()

  private lazy val defaultEntityStoreSpace: EntityStoreSpace =
    EntityStoreSpace.create(
      org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    )

  private lazy val defaultEntitySpace: EntitySpace =
    new EntitySpace()
}
