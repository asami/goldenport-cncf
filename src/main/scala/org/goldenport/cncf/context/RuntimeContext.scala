package org.goldenport.cncf.context

import scala.util.Try
import cats.~>
import cats.Id
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}

/*
 * @since   Dec. 21, 2025
 * @version Jan. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeContext(
  val core: ScopeContext.Core,
  unitOfWorkSupplier: () => UnitOfWork,
  unitOfWorkInterpreterFn: UnitOfWorkOp ~> Id,
  unitOfWorkTryInterpreterFn: UnitOfWorkOp ~> Try,
  unitOfWorkEitherInterpreterFn: UnitOfWorkOp ~> RuntimeContext.EitherThrowable,
  commitAction: UnitOfWork => Unit,
  abortAction: UnitOfWork => Unit,
  disposeAction: UnitOfWork => Unit,
  token: String
) extends ScopeContext() {

  lazy val unitOfWork: UnitOfWork = unitOfWorkSupplier()

  def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id) = unitOfWorkInterpreterFn

  def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> Try) = unitOfWorkTryInterpreterFn

  def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
    unitOfWorkEitherInterpreterFn(op)

  def commit(): Unit = commitAction(unitOfWork)

  def abort(): Unit = abortAction(unitOfWork)

  def dispose(): Unit = disposeAction(unitOfWork)

  def toToken: String = token
}

object RuntimeContext {
  type EitherThrowable[A] = Either[Throwable, A]

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
