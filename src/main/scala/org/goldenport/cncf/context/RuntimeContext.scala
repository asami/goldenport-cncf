package org.goldenport.cncf.context

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}

/*
 * @since   Dec. 21, 2025
 *  version Jan. 18, 2026
 * @version Mar. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeContext(
  val core: ScopeContext.Core,
  unitOfWorkSupplier: () => UnitOfWork,
  unitOfWorkInterpreterFn: UnitOfWorkOp ~> Consequence,
  commitAction: UnitOfWork => Unit,
  abortAction: UnitOfWork => Unit,
  disposeAction: UnitOfWork => Unit,
  token: String
) extends ScopeContext() {

  lazy val unitOfWork: UnitOfWork = unitOfWorkSupplier()

  def unitOfWorkInterpreter: UnitOfWorkOp ~> Consequence = unitOfWorkInterpreterFn

  def commit(): Unit = commitAction(unitOfWork)

  def abort(): Unit = abortAction(unitOfWork)

  def dispose(): Unit = disposeAction(unitOfWork)

  def toToken: String = token
}

object RuntimeContext {
  def core(
    name: String,
    parent: Option[ScopeContext],
    observabilityContext: ObservabilityContext,
    httpDriverOption: Option[HttpDriver] = None
  ): ScopeContext.Core =
    ScopeContext.Core(
      kind = ScopeKind.Runtime,
      name = name,
      parent = parent,
      observabilityContext = observabilityContext,
      httpDriverOption = httpDriverOption
    )
}
