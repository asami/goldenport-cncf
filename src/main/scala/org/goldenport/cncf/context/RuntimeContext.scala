package org.goldenport.cncf.context

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext}

/*
 * @since   Dec. 21, 2025
 *  version Jan. 18, 2026
 * @version Mar. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeContext(
  val core: ScopeContext.Core,
  unitOfWorkSupplier: () => UnitOfWork,
  unitOfWorkInterpreterFn: UnitOfWorkOp ~> Consequence,
  commitAction: UnitOfWork => Unit,
  abortAction: UnitOfWork => Unit,
  disposeAction: UnitOfWork => Unit,
  token: String,
  val transitionValidationHook: TransitionValidationHook = TransitionValidationHook.noop
) extends ScopeContext() {
  private var _resolved_parameters: Option[ResolvedParameters] = None

  lazy val unitOfWork: UnitOfWork = unitOfWorkSupplier()

  def unitOfWorkInterpreter: UnitOfWorkOp ~> Consequence = unitOfWorkInterpreterFn

  def commit(): Unit = commitAction(unitOfWork)

  def abort(): Unit = abortAction(unitOfWork)

  def dispose(): Unit = disposeAction(unitOfWork)

  def toToken: String = token

  def resolvedParameters: ResolvedParameters =
    _resolved_parameters.getOrElse(
      ResolvedParameters.empty(GlobalRuntimeContext.current.map(_.resolvedParameters))
    )

  def setResolvedParameters(
    params: ResolvedParameters
  ): Unit =
    _resolved_parameters = Some(params)

  def clearResolvedParameters(): Unit =
    _resolved_parameters = None
}

object RuntimeContext {
  def core(
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver] = None,
    datastore: Option[DataStoreContext] = None,
    entitystore: Option[EntityStoreContext] = None,
    entityspace: Option[EntitySpaceContext] = None
  ): ScopeContext.Core =
    ScopeContext.Core(
      kind = ScopeKind.Runtime,
      name = name,
      parent = parent,
      observabilityContext = observabilityContext,
      httpDriverOption = httpDriverOption,
      datastore = datastore,
      entitystore = entitystore,
      entityspace = entityspace
    )
}
